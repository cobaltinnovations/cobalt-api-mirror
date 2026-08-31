BEGIN;
SELECT _v.register_patch('263-care-navigator-screening', ARRAY['262-care-navigator'], NULL);

-- Supplements historically required text whenever they were selected. Preserve
-- that behavior by default while allowing an individual option to opt out.
ALTER TABLE screening_answer_option
ADD COLUMN freeform_supplement_text_required BOOLEAN NOT NULL DEFAULT TRUE;

-- Active Care Navigator intake no longer asks for an email address, so retain
-- the appointment's booking contact when there is no historical EMAIL_ADDRESS
-- screening answer to prefer.
CREATE OR REPLACE FUNCTION seed_care_encounter_contact_from_screening()
RETURNS TRIGGER AS $$
DECLARE
	v_care_encounter_id UUID;
	v_screening_session_id UUID;
	v_appointment_email_address TEXT;
	v_appointment_count BIGINT;
BEGIN
	IF TG_OP='UPDATE'
		AND OLD.care_encounter_id IS NOT DISTINCT FROM NEW.care_encounter_id
		AND (OLD.screening_session_id IS NOT NULL OR NEW.screening_session_id IS NULL) THEN
		RETURN NEW;
	END IF;

	SELECT appointment.care_encounter_id,
		appointment.screening_session_id,
		appointment.email_address
	INTO v_care_encounter_id, v_screening_session_id, v_appointment_email_address
	FROM appointment
	WHERE appointment.appointment_id=NEW.appointment_id;

	IF v_care_encounter_id IS NULL THEN
		RETURN NEW;
	END IF;

	SELECT COUNT(*)
	INTO v_appointment_count
	FROM appointment
	WHERE appointment.care_encounter_id=v_care_encounter_id;

	IF v_appointment_count=1 THEN
		UPDATE care_encounter
		SET email_address=COALESCE(
				screening_session_contact_email_address(v_screening_session_id),
				v_appointment_email_address,
				care_encounter.email_address),
			last_updated_by_account_id=NEW.created_by_account_id
		WHERE care_encounter_id=v_care_encounter_id
		AND care_encounter_status_id='OPEN';
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
	v_institution_id CONSTANT TEXT := 'COBALT';
	v_screening_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000005';
	v_screening_version_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000001';
	v_screening_flow_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-00000000000b';
	v_screening_flow_version_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000002';

	v_support_for_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000010';
	v_employer_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000011';
	v_health_insurance_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000012';
	v_behavioral_health_insurance_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000013';
	v_support_type_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000014';

	v_created_by_account_id UUID;
	v_provider_id UUID;
	v_screening_version_number INTEGER;
	v_screening_flow_version_number INTEGER;
	v_scoring_function TEXT;
	v_orchestration_function CONSTANT TEXT := $orchestration$
const screeningSessionScreening = (input.screeningSessionScreenings || [])[0];
const screeningResults = screeningSessionScreening
  ? (input.screeningResultsByScreeningSessionScreeningId[screeningSessionScreening.screeningSessionScreeningId] || [])
  : [];

output.completed = screeningSessionScreening ? Boolean(screeningSessionScreening.completed) : false;
output.crisisIndicated = screeningResults.some((screeningResult) => {
  return (screeningResult.screeningResponses || []).some((screeningResponse) => {
    return screeningResponse.screeningAnswerOption && screeningResponse.screeningAnswerOption.indicatesCrisis;
  });
});
$orchestration$;
	v_results_function CONSTANT TEXT := $results$
output.supportRoleRecommendations = [];
output.recommendLegacyContentAnswerIds = false;
output.legacyContentAnswerIds = [];
output.recommendedTagIds = [];
output.recommendedFeatureIds = [];
output.integratedCareTriages = [];
$results$;
	v_destination_function CONSTANT TEXT := $destination$
const screeningSessionScreening = (input.screeningSessionScreenings || [])[0];
const belowScoringThreshold = screeningSessionScreening
  ? Boolean(screeningSessionScreening.belowScoringThreshold)
  : true;

output.screeningSessionDestinationId = null;
output.context = {};

if (input.screeningSession.completed) {
  output.screeningSessionDestinationId = 'APPOINTMENT_BOOKING_CONFIRMATION';
  output.context.result = belowScoringThreshold ? 'FAILURE' : 'SUCCESS';
}
$destination$;
BEGIN
	-- Updates-only database builds do not contain tenant data. The schema change
	-- above still applies; tenant configuration is installed after bootstrap.
	IF NOT EXISTS (
		SELECT 1
		FROM institution
		WHERE institution_id=v_institution_id
	) THEN
		RETURN;
	END IF;

	SELECT account_id
	INTO v_created_by_account_id
	FROM account
	WHERE institution_id=v_institution_id
	AND role_id='ADMINISTRATOR'
	AND active=TRUE
	AND LOWER(email_address)=LOWER('admin@cobaltinnovations.org')
	ORDER BY account_id
	LIMIT 1;

	IF v_created_by_account_id IS NULL THEN
		RAISE EXCEPTION 'The COBALT administrator account is required to configure the Care Navigator screening.';
	END IF;

	SELECT COALESCE(MAX(version_number), 0) + 1
	INTO v_screening_version_number
	FROM screening_version
	WHERE screening_id=v_screening_id;

	INSERT INTO screening (
		screening_id,
		name,
		active_screening_version_id,
		created_by_account_id
	) VALUES (
		v_screening_id,
		'Care Navigator Booking Assessment',
		NULL,
		v_created_by_account_id
	)
	ON CONFLICT (screening_id) DO UPDATE
	SET name=EXCLUDED.name;

	v_scoring_function := FORMAT($scoring$
const questionIds = ['%s', '%s', '%s', '%s', '%s'];
const nextUnansweredQuestionId = questionIds.find((questionId) => {
  const selectedAnswerIds = input.screeningAnswerIdsByScreeningQuestionId[questionId] || [];
  return selectedAnswerIds.length === 0;
});

output.completed = nextUnansweredQuestionId === undefined;
output.score = { overallScore: output.completed ? 1 : 0 };
output.belowScoringThreshold = !output.completed;
output.nextScreeningQuestionId = output.completed ? null : nextUnansweredQuestionId;
$scoring$, v_support_for_question_id, v_employer_question_id, v_health_insurance_question_id,
		v_behavioral_health_insurance_question_id, v_support_type_question_id);

	INSERT INTO screening_version (
		screening_version_id,
		screening_id,
		screening_type_id,
		created_by_account_id,
		version_number,
		scoring_function
	) VALUES (
		v_screening_version_id,
		v_screening_id,
		'CUSTOM',
		v_created_by_account_id,
		v_screening_version_number,
		v_scoring_function
	);

	INSERT INTO screening_institution (screening_id, institution_id)
	VALUES (v_screening_id, v_institution_id)
	ON CONFLICT (screening_id, institution_id) DO NOTHING;

	INSERT INTO screening_question (
		screening_question_id,
		screening_version_id,
		screening_answer_format_id,
		screening_answer_content_hint_id,
		question_text,
		minimum_answer_count,
		maximum_answer_count,
		display_order,
		prefer_autosubmit,
		screening_question_submission_style_id
	) VALUES
		(v_support_for_question_id, v_screening_version_id, 'MULTI_SELECT', 'NONE',
			'Who are you seeking support for?', 1, 4, 1, FALSE, 'NEXT'),
		(v_employer_question_id, v_screening_version_id, 'SINGLE_SELECT', 'NONE',
			'Who is your current employer?', 1, 1, 2, TRUE, 'NEXT'),
		(v_health_insurance_question_id, v_screening_version_id, 'SINGLE_SELECT', 'NONE',
			'Select your current health insurance plan from the list below.', 1, 1, 3, TRUE, 'NEXT'),
		(v_behavioral_health_insurance_question_id, v_screening_version_id, 'SINGLE_SELECT', 'NONE',
			'Select your current behavioral health insurance plan from the list below.', 1, 1, 4, TRUE, 'NEXT'),
		(v_support_type_question_id, v_screening_version_id, 'MULTI_SELECT', 'NONE',
			'What kind of support are you looking for today?', 1, 9, 5, FALSE, 'NEXT');

	INSERT INTO screening_answer_option (
		screening_answer_option_id,
		screening_question_id,
		answer_option_text,
		score,
		indicates_crisis,
		freeform_supplement,
		freeform_supplement_text,
		freeform_supplement_text_required,
		freeform_supplement_text_auto_show,
		freeform_supplement_content_hint_id,
		display_order
	) VALUES
		('ca4e5000-0000-4000-8000-000000000100', v_support_for_question_id, 'Myself', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 1),
		('ca4e5000-0000-4000-8000-000000000101', v_support_for_question_id, 'Spouse/partner', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 2),
		('ca4e5000-0000-4000-8000-000000000102', v_support_for_question_id, 'Child/children', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 3),
		('ca4e5000-0000-4000-8000-000000000103', v_support_for_question_id, 'Other', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 4),

		('ca4e5000-0000-4000-8000-000000000200', v_employer_question_id, 'UPHS (University Pennsylvania Health System)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 1),
		('ca4e5000-0000-4000-8000-000000000201', v_employer_question_id, 'UPenn (University of Pennsylvania)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 2),
		('ca4e5000-0000-4000-8000-000000000202', v_employer_question_id, 'LGH (Lancaster General Health)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 3),
		('ca4e5000-0000-4000-8000-000000000203', v_employer_question_id, 'Princeton (Princeton Health)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 4),
		('ca4e5000-0000-4000-8000-000000000204', v_employer_question_id, 'CCH (Chester County Hospital)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 5),
		('ca4e5000-0000-4000-8000-000000000205', v_employer_question_id, 'Doylestown (Doylestown Health)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 6),
		('ca4e5000-0000-4000-8000-000000000206', v_employer_question_id, 'I''m not sure / I''d rather not say', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 7),

		('ca4e5000-0000-4000-8000-000000000300', v_health_insurance_question_id, 'PennCare PPO (UPHS)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 1),
		('ca4e5000-0000-4000-8000-000000000301', v_health_insurance_question_id, 'PennCare HDHP (UPHS)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 2),
		('ca4e5000-0000-4000-8000-000000000302', v_health_insurance_question_id, 'Aetna POS (UPenn)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 3),
		('ca4e5000-0000-4000-8000-000000000303', v_health_insurance_question_id, 'Aetna HDHP (UPenn)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 4),
		('ca4e5000-0000-4000-8000-000000000304', v_health_insurance_question_id, 'PennCare PPO (UPenn)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 5),
		('ca4e5000-0000-4000-8000-000000000305', v_health_insurance_question_id, 'Keystone / AmeriHealth HMO (UPenn)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 6),
		('ca4e5000-0000-4000-8000-000000000306', v_health_insurance_question_id, 'Tricare', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 7),
		('ca4e5000-0000-4000-8000-000000000307', v_health_insurance_question_id, 'Medicaid', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 8),
		('ca4e5000-0000-4000-8000-000000000308', v_health_insurance_question_id, 'Medicare', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 9),
		('ca4e5000-0000-4000-8000-000000000309', v_health_insurance_question_id, 'I''m not sure / I don''t know', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 10),
		('ca4e5000-0000-4000-8000-00000000030a', v_health_insurance_question_id, 'Other', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 11),

		('ca4e5000-0000-4000-8000-000000000400', v_behavioral_health_insurance_question_id, 'Aetna Behavioral Health Network', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 1),
		('ca4e5000-0000-4000-8000-000000000401', v_behavioral_health_insurance_question_id, 'Independence Behavioral Health Network (IBX)', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 2),
		('ca4e5000-0000-4000-8000-000000000402', v_behavioral_health_insurance_question_id, 'Lyra | Carelon Behavioral Health', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 3),
		('ca4e5000-0000-4000-8000-000000000403', v_behavioral_health_insurance_question_id, 'I''m not sure / I don''t know', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 4),
		('ca4e5000-0000-4000-8000-000000000404', v_behavioral_health_insurance_question_id, 'Other', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 5),

		('ca4e5000-0000-4000-8000-000000000500', v_support_type_question_id, 'Finding a therapist or behavioral health provider', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 1),
		('ca4e5000-0000-4000-8000-000000000501', v_support_type_question_id, 'Stress, burnout, or work-life challenges', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 2),
		('ca4e5000-0000-4000-8000-000000000502', v_support_type_question_id, 'Anxiety, depression, or other emotional well-being concerns', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 3),
		('ca4e5000-0000-4000-8000-000000000503', v_support_type_question_id, 'Medication or psychiatry questions', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 4),
		('ca4e5000-0000-4000-8000-000000000504', v_support_type_question_id, 'Parenting, childcare or caregiving support', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 5),
		('ca4e5000-0000-4000-8000-000000000505', v_support_type_question_id, 'Help understanding available behavioral health services or benefits', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 6),
		('ca4e5000-0000-4000-8000-000000000506', v_support_type_question_id, 'Wellness resources or support groups', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 7),
		('ca4e5000-0000-4000-8000-000000000507', v_support_type_question_id, 'Help navigating the Cobalt website', 1, FALSE, FALSE, NULL, TRUE, FALSE, NULL, 8),
		('ca4e5000-0000-4000-8000-000000000508', v_support_type_question_id, 'Something else / I''m not sure', 1, FALSE, TRUE, 'Tell us more', FALSE, FALSE, 'NONE', 9);

	UPDATE screening
	SET active_screening_version_id=v_screening_version_id
	WHERE screening_id=v_screening_id;

	SELECT COALESCE(MAX(version_number), 0) + 1
	INTO v_screening_flow_version_number
	FROM screening_flow_version
	WHERE screening_flow_id=v_screening_flow_id;

	INSERT INTO screening_flow (
		screening_flow_id,
		institution_id,
		active_screening_flow_version_id,
		screening_flow_type_id,
		created_by_account_id,
		name
	) VALUES (
		v_screening_flow_id,
		v_institution_id,
		NULL,
		'PROVIDER_INTAKE',
		v_created_by_account_id,
		'Care Navigator Booking Intake'
	)
	ON CONFLICT (screening_flow_id) DO UPDATE
	SET institution_id=EXCLUDED.institution_id,
		screening_flow_type_id=EXCLUDED.screening_flow_type_id,
		name=EXCLUDED.name;

	INSERT INTO screening_flow_version (
		screening_flow_version_id,
		screening_flow_id,
		initial_screening_id,
		phone_number_required,
		version_number,
		orchestration_function,
		results_function,
		destination_function,
		created_by_account_id,
		skippable,
		screening_flow_skip_type_id
	) VALUES (
		v_screening_flow_version_id,
		v_screening_flow_id,
		v_screening_id,
		FALSE,
		v_screening_flow_version_number,
		v_orchestration_function,
		v_results_function,
		v_destination_function,
		v_created_by_account_id,
		FALSE,
		'EXIT'
	);

	UPDATE screening_flow
	SET active_screening_flow_version_id=v_screening_flow_version_id
	WHERE screening_flow_id=v_screening_flow_id;

	SELECT provider_id
	INTO v_provider_id
	FROM institution_feature
	WHERE institution_id=v_institution_id
	AND feature_id='RESOURCE_NAVIGATOR';

	-- A null provider means the tenant has not configured Care Navigator booking
	-- yet. Once a provider is named, fail loudly instead of silently attaching the
	-- intake to an invalid or incomplete configuration.
	IF v_provider_id IS NOT NULL THEN
		IF NOT EXISTS (
			SELECT 1
			FROM provider
			JOIN provider_support_role
				ON provider_support_role.provider_id=provider.provider_id
				AND provider_support_role.support_role_id='CARE_NAVIGATOR'
			WHERE provider.provider_id=v_provider_id
			AND provider.institution_id=v_institution_id
			AND provider.active=TRUE
		) THEN
			RAISE EXCEPTION 'The configured COBALT Care Navigator provider is invalid.';
		END IF;

		IF NOT EXISTS (
			SELECT 1
			FROM provider_appointment_type
			JOIN appointment_type
				ON appointment_type.appointment_type_id=provider_appointment_type.appointment_type_id
			WHERE provider_appointment_type.provider_id=v_provider_id
			AND appointment_type.deleted=FALSE
		) THEN
			RAISE EXCEPTION 'The configured COBALT Care Navigator provider has no active appointment types.';
		END IF;

		UPDATE appointment_type
		SET screening_flow_id=v_screening_flow_id
		WHERE deleted=FALSE
		AND EXISTS (
			SELECT 1
			FROM provider_appointment_type
			WHERE provider_appointment_type.provider_id=v_provider_id
			AND provider_appointment_type.appointment_type_id=appointment_type.appointment_type_id
		);
	END IF;
END $$;

COMMIT;
