BEGIN;
SELECT _v.register_patch(
	'263-local-only-care-navigator-seed',
	ARRAY[
		'262-local-only-provider-booking-seed',
		'263-care-navigator-screening'
	],
	NULL
);

-- Local/bootstrap-only Care Navigator fixture. Production receives the support
-- role, capability type, feature mapping, and screening configuration from the
-- update migrations, but never receives this account, provider, or availability.
DO $$
DECLARE
	v_institution_id CONSTANT TEXT := 'COBALT';
	v_account_email_address CONSTANT TEXT := 'care-navigator@cobaltinnovations.org';
	v_account_password_hash CONSTANT TEXT := '$2a$10$M2tPoJ8eQr55OW4iOfpbBOpgqFWt0LxnvVBnW1a/1LhKNA6SuUN42';
	v_provider_name CONSTANT TEXT := 'Care Navigator';
	v_provider_url_name CONSTANT TEXT := 'cobalt-care-navigator';
	v_appointment_type_name CONSTANT TEXT := 'Care Navigation Consultation';
	v_provider_bio CONSTANT TEXT := 'Our Care Navigator is here to help you identify and connect with mental health and wellness resources that best fit your needs. During the video call, they''ll listen to your concerns, answer questions about available benefits and services, and help connect you with resources.';
	v_provider_description CONSTANT TEXT := 'Our Care Navigator is here to help you identify and connect with mental health and wellness resources that best fit your needs.';
	v_appointment_type_description CONSTANT TEXT := 'Your appointment is a 30 minute video call with a Care Navigator to discuss potential resources.';
	v_provider_details_html CONSTANT TEXT := $details_html$
<section class="mb-8">
  <h2 class="mb-4">What is a Care Navigator</h2>
  <p class="mb-4 fs-large">Our Care Navigator is here to help you identify and connect with mental health and wellness resources that best fit your needs. During the video call, they'll listen to your concerns, answer questions about available benefits and services, and help connect you with resources.</p>
  <p class="mb-4 fs-large"><strong>Care Navigators are not licensed clinicians and do not provide medical or mental health treatment, diagnoses, therapy, or clinical recommendations.</strong> Their role is to help you understand your options and navigate available resources.</p>
  <p class="mb-2 fs-large">Below are some examples of how a Care Navigator can help:</p>
  <ul class="mb-4 fs-large">
    <li>Connect you with free, rapid access benefits like your Employee Assistance Program (EAP)</li>
    <li>Navigate available services for dependents</li>
    <li>Identify support groups and wellness resources</li>
    <li>Compare available behavioral health services</li>
    <li>Assist in navigating Cobalt website</li>
  </ul>
  <p class="mb-4 fs-large"><strong>Whether you're looking for care for yourself or someone you care about, a Care Navigator can help you identify resources and determine the next best steps.</strong></p>
  <p class="mb-4 fs-large">Care Navigator conversations are not intended to address emergency or crisis situations. <strong>If you are experiencing a medical or mental health emergency, or are concerned about your immediate safety or the safety of someone else, please call 911 or 988 immediately.</strong> Penn Medicine employees may also contact the EAP 24/7 Crisis Line.</p>
  <p class="mb-0 fs-large">Your privacy is important to us. When you schedule with a Care Navigator, we'll ask for your name and email address so we can contact you about your call and provide Care Navigator services. Your information is accessible only to authorized Penn Cobalt personnel and is protected in accordance with applicable privacy laws, including HIPAA when applicable. We only use or share your information as needed to provide services or as required by law. Any information shared is not documented in PennChart or linked to from your electronic health record.</p>
</section>
$details_html$;

	v_fixture_account_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000001';
	v_fixture_provider_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000002';
	v_appointment_type_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000003';
	v_logical_availability_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000004';
	v_screening_flow_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-00000000000b';
	v_institution_feature_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-00000000000d';
	v_provider_location_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-00000000000e';

	v_account_id UUID;
	v_provider_id UUID;
BEGIN
	-- Reuse a matching local row when one already exists, while retaining fixed
	-- UUIDs for clean database recreations.
	SELECT provider_id
	INTO v_provider_id
	FROM provider
	WHERE institution_id=v_institution_id
	AND LOWER(url_name)=LOWER(v_provider_url_name)
	ORDER BY provider_id
	LIMIT 1;

	IF v_provider_id IS NULL THEN
		v_provider_id := v_fixture_provider_id;

		INSERT INTO provider (
			provider_id,
			institution_id,
			name,
			title,
			entity,
			clinic,
			specialty,
			email_address,
			image_url,
			bio_url,
			website_url,
			locale,
			time_zone,
			active,
			scheduling_system_id,
			videoconference_platform_id,
			system_affinity_id,
			url_name,
			bio,
			description,
			tags,
			phone_number,
			display_phone_number_only_for_booking,
			details_html
		) VALUES (
			v_provider_id,
			v_institution_id,
			v_provider_name,
			'Care Navigator',
			'Cobalt',
			'Cobalt Care Navigation',
			'Care Navigation',
			v_account_email_address,
			'https://placehold.co/320x320/png?text=Care+Navigator',
			'https://fixtures.cobalt.care/providers/cobalt-care-navigator/bio',
			NULL,
			'en-US',
			'America/New_York',
			TRUE,
			'COBALT',
			'SWITCHBOARD',
			'COBALT',
			v_provider_url_name,
			v_provider_bio,
			v_provider_description,
			'["Provider matching", "Care options", "Mental health navigation"]',
			NULL,
			FALSE,
			v_provider_details_html
		)
		ON CONFLICT (provider_id) DO NOTHING;
	ELSE
		UPDATE provider
		SET name=v_provider_name,
			title='Care Navigator',
			entity='Cobalt',
			clinic='Cobalt Care Navigation',
			specialty='Care Navigation',
			email_address=v_account_email_address,
			image_url='https://placehold.co/320x320/png?text=Care+Navigator',
			bio_url='https://fixtures.cobalt.care/providers/cobalt-care-navigator/bio',
			website_url=NULL,
			locale='en-US',
			time_zone='America/New_York',
			active=TRUE,
			scheduling_system_id='COBALT',
			videoconference_platform_id='SWITCHBOARD',
			system_affinity_id='COBALT',
			bio=v_provider_bio,
			description=v_provider_description,
			tags='["Provider matching", "Care options", "Mental health navigation"]',
			phone_number=NULL,
			display_phone_number_only_for_booking=FALSE,
			details_html=v_provider_details_html
		WHERE provider_id=v_provider_id;
	END IF;

	SELECT account_id
	INTO v_account_id
	FROM account
	WHERE institution_id=v_institution_id
	AND account_source_id='EMAIL_PASSWORD'
	AND active=TRUE
	AND LOWER(email_address)=LOWER(v_account_email_address)
	ORDER BY account_id
	LIMIT 1;

	IF v_account_id IS NULL THEN
		v_account_id := v_fixture_account_id;

		INSERT INTO account (
			account_id,
			role_id,
			institution_id,
			account_source_id,
			email_address,
			password,
			first_name,
			last_name,
			display_name,
			provider_id,
			locale,
			time_zone,
			active,
			test_account
		) VALUES (
			v_account_id,
			'ADMINISTRATOR',
			v_institution_id,
			'EMAIL_PASSWORD',
			v_account_email_address,
			v_account_password_hash,
			'Cobalt',
			'Care Navigator',
			v_provider_name,
			v_provider_id,
			'en-US',
			'America/New_York',
			TRUE,
			TRUE
		)
		ON CONFLICT (account_id) DO NOTHING;
	ELSE
		UPDATE account
		SET role_id='ADMINISTRATOR',
			provider_id=v_provider_id,
			password=v_account_password_hash,
			first_name='Cobalt',
			last_name='Care Navigator',
			display_name=v_provider_name,
			locale='en-US',
			time_zone='America/New_York',
			active=TRUE,
			test_account=TRUE
		WHERE account_id=v_account_id;
	END IF;

	INSERT INTO provider_support_role (provider_id, support_role_id)
	VALUES (v_provider_id, 'CARE_NAVIGATOR')
	ON CONFLICT (provider_id, support_role_id) DO NOTHING;

	INSERT INTO account_capability (account_id, account_capability_type_id)
	VALUES (v_account_id, 'NAVIGATOR')
	ON CONFLICT (account_id, account_capability_type_id) DO NOTHING;

	INSERT INTO care_navigator_provider_account (
		provider_id,
		account_id,
		display_order
	) VALUES (
		v_provider_id,
		v_account_id,
		1
	)
	ON CONFLICT (provider_id, account_id) DO UPDATE
	SET display_order=EXCLUDED.display_order;

	INSERT INTO provider_payment_type (provider_id, payment_type_id)
	SELECT v_provider_id, 'NO_FEE'
	WHERE EXISTS (
		SELECT 1
		FROM payment_type
		WHERE payment_type_id='NO_FEE'
	)
	ON CONFLICT (provider_id, payment_type_id) DO NOTHING;

	INSERT INTO provider_location (
		provider_location_id,
		provider_id,
		address_id,
		name,
		short_name,
		display_order
	) VALUES (
		v_provider_location_id,
		v_provider_id,
		NULL,
		'Cobalt Virtual Care',
		'Virtual Care',
		1
	)
	ON CONFLICT (provider_location_id) DO UPDATE
	SET provider_id=EXCLUDED.provider_id,
		address_id=EXCLUDED.address_id,
		name=EXCLUDED.name,
		short_name=EXCLUDED.short_name,
		display_order=EXCLUDED.display_order;

	INSERT INTO provider_institution_location (
		provider_id,
		institution_location_id
	)
	SELECT
		v_provider_id,
		institution_location.institution_location_id
	FROM institution_location
	WHERE institution_location.institution_id=v_institution_id
	AND institution_location.name='Cobalt Virtual Care'
	AND NOT EXISTS (
		SELECT 1
		FROM provider_institution_location existing
		WHERE existing.provider_id=v_provider_id
		AND existing.institution_location_id=institution_location.institution_location_id
	);

	-- Make the existing feature discoverable in local/bootstrap environments.
	INSERT INTO institution_feature (
		institution_feature_id,
		institution_id,
		feature_id,
		nav_description,
		description,
		display_order,
		nav_visible,
		landing_page_visible,
		treatment_description,
		provider_id
	) VALUES (
		v_institution_feature_id,
		v_institution_id,
		'RESOURCE_NAVIGATOR',
		'Connect with a Care Navigator',
		'Find help understanding care options and connecting with a mental health provider.',
		12,
		TRUE,
		TRUE,
		'Care navigation consultations',
		v_provider_id
	)
	ON CONFLICT (institution_id, feature_id) DO UPDATE
	SET nav_description=EXCLUDED.nav_description,
		description=EXCLUDED.description,
		display_order=EXCLUDED.display_order,
		nav_visible=EXCLUDED.nav_visible,
		landing_page_visible=EXCLUDED.landing_page_visible,
		treatment_description=EXCLUDED.treatment_description,
		provider_id=EXCLUDED.provider_id;

	INSERT INTO appointment_type (
		appointment_type_id,
		name,
		description,
		duration_in_minutes,
		deleted,
		scheduling_system_id,
		visit_type_id,
		screening_flow_id
	) VALUES (
		v_appointment_type_id,
		v_appointment_type_name,
		v_appointment_type_description,
		30,
		FALSE,
		'COBALT',
		'INITIAL',
		v_screening_flow_id
	)
	ON CONFLICT (appointment_type_id) DO UPDATE
	SET name=EXCLUDED.name,
		description=EXCLUDED.description,
		duration_in_minutes=EXCLUDED.duration_in_minutes,
		deleted=EXCLUDED.deleted,
		scheduling_system_id=EXCLUDED.scheduling_system_id,
		visit_type_id=EXCLUDED.visit_type_id,
		screening_flow_id=EXCLUDED.screening_flow_id;

	INSERT INTO provider_appointment_type (
		provider_id,
		appointment_type_id,
		display_order
	)
	SELECT
		v_provider_id,
		v_appointment_type_id,
		COALESCE((
			SELECT MAX(existing.display_order) + 1
			FROM provider_appointment_type existing
			WHERE existing.provider_id=v_provider_id
		), 1)
	WHERE NOT EXISTS (
		SELECT 1
		FROM provider_appointment_type existing
		WHERE existing.provider_id=v_provider_id
		AND existing.appointment_type_id=v_appointment_type_id
	);

	INSERT INTO logical_availability (
		logical_availability_id,
		provider_id,
		start_date_time,
		end_date_time,
		logical_availability_type_id,
		recurrence_type_id,
		recur_sunday,
		recur_monday,
		recur_tuesday,
		recur_wednesday,
		recur_thursday,
		recur_friday,
		recur_saturday,
		created_by_account_id,
		last_updated_by_account_id
	) VALUES (
		v_logical_availability_id,
		v_provider_id,
		TIMESTAMP '2026-01-05 09:00:00',
		TIMESTAMP '2099-12-31 17:00:00',
		'OPEN',
		'DAILY',
		FALSE,
		TRUE,
		TRUE,
		TRUE,
		TRUE,
		TRUE,
		FALSE,
		v_account_id,
		v_account_id
	)
	ON CONFLICT (logical_availability_id) DO UPDATE
	SET provider_id=EXCLUDED.provider_id,
		start_date_time=EXCLUDED.start_date_time,
		end_date_time=EXCLUDED.end_date_time,
		logical_availability_type_id=EXCLUDED.logical_availability_type_id,
		recurrence_type_id=EXCLUDED.recurrence_type_id,
		recur_sunday=EXCLUDED.recur_sunday,
		recur_monday=EXCLUDED.recur_monday,
		recur_tuesday=EXCLUDED.recur_tuesday,
		recur_wednesday=EXCLUDED.recur_wednesday,
		recur_thursday=EXCLUDED.recur_thursday,
		recur_friday=EXCLUDED.recur_friday,
		recur_saturday=EXCLUDED.recur_saturday,
		last_updated_by_account_id=EXCLUDED.last_updated_by_account_id;

	INSERT INTO logical_availability_appointment_type (
		logical_availability_id,
		appointment_type_id
	) VALUES (
		v_logical_availability_id,
		v_appointment_type_id
	)
	ON CONFLICT (logical_availability_id, appointment_type_id) DO NOTHING;
END $$;

-- Local/bootstrap-only Care Encounter fixtures. Dynamic dates keep the admin
-- screen populated with recent, upcoming, and canceled examples after every
-- database recreation.
DO $$
DECLARE
	v_institution_id CONSTANT TEXT := 'COBALT';
	v_provider_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000002';
	v_navigator_account_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000001';
	v_appointment_type_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000003';
	v_screening_version_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000001';
	v_screening_flow_version_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000002';
	v_support_for_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000010';
	v_employer_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000011';
	v_health_insurance_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000012';
	v_behavioral_health_insurance_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000013';
	v_support_type_question_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000014';
	v_myself_answer_option_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000100';
	v_uphs_answer_option_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000200';
	v_penncare_ppo_answer_option_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000300';
	v_aetna_behavioral_answer_option_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000400';
	v_therapist_support_answer_option_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000500';
	v_other_support_answer_option_id CONSTANT UUID := 'ca4e5000-0000-4000-8000-000000000508';
	v_patient_one_id CONSTANT UUID := 'ca4e1000-0000-4000-8000-000000000001';
	v_patient_two_id CONSTANT UUID := 'ca4e1000-0000-4000-8000-000000000002';
	v_patient_three_id CONSTANT UUID := 'ca4e1000-0000-4000-8000-000000000003';
	v_patient_four_id CONSTANT UUID := 'ca4e1000-0000-4000-8000-000000000004';
	v_completed_appointment_id CONSTANT UUID := 'ca4e2000-0000-4000-8000-000000000001';
	v_upcoming_appointment_id CONSTANT UUID := 'ca4e2000-0000-4000-8000-000000000002';
	v_canceled_appointment_id CONSTANT UUID := 'ca4e2000-0000-4000-8000-000000000003';
	v_rebooked_appointment_id CONSTANT UUID := 'ca4e2000-0000-4000-8000-000000000004';
	v_patient_canceled_appointment_id CONSTANT UUID := 'ca4e2000-0000-4000-8000-000000000005';
	v_upcoming_screening_session_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000001';
	v_upcoming_session_screening_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000002';
	v_upcoming_support_for_response_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000003';
	v_upcoming_employer_response_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000004';
	v_upcoming_health_insurance_response_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000005';
	v_upcoming_behavioral_insurance_response_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000006';
	v_upcoming_support_type_response_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-00000000000c';
	v_completed_note_id CONSTANT UUID := 'ca4e4000-0000-4000-8000-000000000001';
	v_upcoming_note_one_id CONSTANT UUID := 'ca4e4000-0000-4000-8000-000000000002';
	v_upcoming_note_two_id CONSTANT UUID := 'ca4e4000-0000-4000-8000-000000000003';
	v_rebooked_note_id CONSTANT UUID := 'ca4e4000-0000-4000-8000-000000000004';
	v_patient_canceled_note_id CONSTANT UUID := 'ca4e4000-0000-4000-8000-000000000005';
	v_upcoming_support_for_answer_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000007';
	v_upcoming_employer_answer_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000008';
	v_upcoming_health_insurance_answer_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000009';
	v_upcoming_behavioral_insurance_answer_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-00000000000a';
	v_upcoming_therapist_support_answer_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-00000000000b';
	v_upcoming_other_support_answer_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-00000000000d';
	v_upcoming_context_text CONSTANT TEXT := 'I would like help finding an in-network therapist with evening availability.';
	v_appointment_reason_id UUID;
	v_today TIMESTAMP := DATE_TRUNC('day', NOW() AT TIME ZONE 'America/New_York');
BEGIN
	UPDATE provider
	SET virtual_appointments_only=TRUE
	WHERE provider_id=v_provider_id;

	SELECT appointment_reason_id
	INTO v_appointment_reason_id
	FROM appointment_reason
	WHERE institution_id=v_institution_id
	AND appointment_reason_type_id='NOT_SPECIFIED'
	ORDER BY appointment_reason_id
	LIMIT 1;

	IF v_appointment_reason_id IS NULL THEN
		RAISE EXCEPTION 'A NOT_SPECIFIED appointment reason is required for the Care Encounter fixture';
	END IF;

	INSERT INTO account (
		account_id,
		role_id,
		institution_id,
		account_source_id,
		email_address,
		first_name,
		last_name,
		display_name,
		locale,
		time_zone,
		active,
		test_account
	) VALUES
		(v_patient_one_id, 'PATIENT', v_institution_id, 'EMAIL_PASSWORD', 'care-encounter.alex@example.com', 'Alex', 'Morgan', 'Alex Morgan', 'en-US', 'America/New_York', TRUE, TRUE),
		(v_patient_two_id, 'PATIENT', v_institution_id, 'EMAIL_PASSWORD', 'care-encounter.jordan@example.com', 'Jordan', 'Lee', 'Jordan Lee', 'en-US', 'America/New_York', TRUE, TRUE),
		(v_patient_three_id, 'PATIENT', v_institution_id, 'EMAIL_PASSWORD', 'care-encounter.taylor@example.com', 'Taylor', 'Rivera', 'Taylor Rivera', 'en-US', 'America/New_York', TRUE, TRUE),
		(v_patient_four_id, 'PATIENT', v_institution_id, 'EMAIL_PASSWORD', 'care-encounter.casey@example.com', 'Casey', 'Nguyen', 'Casey Nguyen', 'en-US', 'America/New_York', TRUE, TRUE)
	ON CONFLICT (account_id) DO UPDATE
	SET first_name=EXCLUDED.first_name,
		last_name=EXCLUDED.last_name,
		display_name=EXCLUDED.display_name,
		active=EXCLUDED.active,
		test_account=EXCLUDED.test_account;

	INSERT INTO screening_session (
		screening_session_id,
		screening_flow_version_id,
		target_account_id,
		created_by_account_id,
		completed,
		crisis_indicated,
		completed_at
	) VALUES (
		v_upcoming_screening_session_id,
		v_screening_flow_version_id,
		v_patient_two_id,
		v_patient_two_id,
		TRUE,
		FALSE,
		NOW()
	)
	ON CONFLICT (screening_session_id) DO UPDATE
	SET screening_flow_version_id=EXCLUDED.screening_flow_version_id,
		target_account_id=EXCLUDED.target_account_id,
		created_by_account_id=EXCLUDED.created_by_account_id,
		completed=EXCLUDED.completed,
		crisis_indicated=EXCLUDED.crisis_indicated,
		completed_at=EXCLUDED.completed_at;

	INSERT INTO screening_session_screening (
		screening_session_screening_id,
		screening_session_id,
		screening_version_id,
		screening_order,
		completed,
		score,
		below_scoring_threshold
	) VALUES (
		v_upcoming_session_screening_id,
		v_upcoming_screening_session_id,
		v_screening_version_id,
		1,
		TRUE,
		'{"overallScore":1}'::JSONB,
		FALSE
	)
	ON CONFLICT (screening_session_screening_id) DO UPDATE
	SET screening_session_id=EXCLUDED.screening_session_id,
		screening_version_id=EXCLUDED.screening_version_id,
		screening_order=EXCLUDED.screening_order,
		completed=EXCLUDED.completed,
		score=EXCLUDED.score,
		below_scoring_threshold=EXCLUDED.below_scoring_threshold;

	INSERT INTO screening_session_answered_screening_question (
		screening_session_answered_screening_question_id,
		screening_session_screening_id,
		screening_question_id
	) VALUES
		(v_upcoming_support_for_response_id, v_upcoming_session_screening_id, v_support_for_question_id),
		(v_upcoming_employer_response_id, v_upcoming_session_screening_id, v_employer_question_id),
		(v_upcoming_health_insurance_response_id, v_upcoming_session_screening_id, v_health_insurance_question_id),
		(v_upcoming_behavioral_insurance_response_id, v_upcoming_session_screening_id, v_behavioral_health_insurance_question_id),
		(v_upcoming_support_type_response_id, v_upcoming_session_screening_id, v_support_type_question_id)
	ON CONFLICT (screening_session_answered_screening_question_id) DO UPDATE
	SET screening_session_screening_id=EXCLUDED.screening_session_screening_id,
		screening_question_id=EXCLUDED.screening_question_id,
		valid=TRUE;

	INSERT INTO screening_answer (
		screening_answer_id,
		screening_answer_option_id,
		screening_session_answered_screening_question_id,
		created_by_account_id,
		text,
		answer_order
	) VALUES
		(v_upcoming_support_for_answer_id, v_myself_answer_option_id, v_upcoming_support_for_response_id, v_patient_two_id, NULL, 1),
		(v_upcoming_employer_answer_id, v_uphs_answer_option_id, v_upcoming_employer_response_id, v_patient_two_id, NULL, 1),
		(v_upcoming_health_insurance_answer_id, v_penncare_ppo_answer_option_id, v_upcoming_health_insurance_response_id, v_patient_two_id, NULL, 1),
		(v_upcoming_behavioral_insurance_answer_id, v_aetna_behavioral_answer_option_id, v_upcoming_behavioral_insurance_response_id, v_patient_two_id, NULL, 1),
		(v_upcoming_therapist_support_answer_id, v_therapist_support_answer_option_id, v_upcoming_support_type_response_id, v_patient_two_id, NULL, 1),
		(v_upcoming_other_support_answer_id, v_other_support_answer_option_id, v_upcoming_support_type_response_id, v_patient_two_id, v_upcoming_context_text, 2)
	ON CONFLICT (screening_answer_id) DO UPDATE
	SET screening_answer_option_id=EXCLUDED.screening_answer_option_id,
		screening_session_answered_screening_question_id=EXCLUDED.screening_session_answered_screening_question_id,
		created_by_account_id=EXCLUDED.created_by_account_id,
		text=EXCLUDED.text,
		answer_order=EXCLUDED.answer_order,
		valid=TRUE;

	INSERT INTO appointment (
		appointment_id,
		provider_id,
		account_id,
		created_by_account_id,
		first_name,
		last_name,
		email_address,
		contact_phone_number,
		appointment_type_id,
		title,
		start_time,
		end_time,
		duration_in_minutes,
		time_zone,
		videoconference_url,
		videoconference_platform_id,
		scheduling_system_id,
		appointment_reason_id,
		attendance_status_id,
		screening_session_id,
		canceled,
		canceled_at,
		canceled_by_account_id
	) VALUES
		(v_completed_appointment_id, v_provider_id, v_patient_one_id, v_patient_one_id, 'Alex', 'Morgan', 'care-encounter.alex@example.com', '+12155553001', v_appointment_type_id, 'Care Navigation Consultation', v_today - INTERVAL '1 day' + INTERVAL '10 hours', v_today - INTERVAL '1 day' + INTERVAL '10 hours 30 minutes', 30, 'America/New_York', 'https://fixtures.cobalt.care/care-encounters/completed', 'SWITCHBOARD', 'COBALT', v_appointment_reason_id, 'ATTENDED', NULL, FALSE, NULL, NULL),
		(v_upcoming_appointment_id, v_provider_id, v_patient_two_id, v_patient_two_id, 'Jordan', 'Lee', 'care-encounter.jordan@example.com', '+12155553002', v_appointment_type_id, 'Care Navigation Consultation', v_today + INTERVAL '1 day 11 hours', v_today + INTERVAL '1 day 11 hours 30 minutes', 30, 'America/New_York', 'https://fixtures.cobalt.care/care-encounters/upcoming', 'SWITCHBOARD', 'COBALT', v_appointment_reason_id, 'UNKNOWN', v_upcoming_screening_session_id, FALSE, NULL, NULL),
		(v_canceled_appointment_id, v_provider_id, v_patient_three_id, v_patient_three_id, 'Taylor', 'Rivera', 'care-encounter.taylor@example.com', '+12155553003', v_appointment_type_id, 'Care Navigation Consultation', v_today + INTERVAL '2 days 14 hours', v_today + INTERVAL '2 days 14 hours 30 minutes', 30, 'America/New_York', 'https://fixtures.cobalt.care/care-encounters/canceled', 'SWITCHBOARD', 'COBALT', v_appointment_reason_id, 'CANCELED', NULL, TRUE, NOW(), v_navigator_account_id),
		(v_rebooked_appointment_id, v_provider_id, v_patient_three_id, v_patient_three_id, 'Taylor', 'Rivera', 'care-encounter.taylor@example.com', '+12155553003', v_appointment_type_id, 'Care Navigation Consultation', v_today + INTERVAL '4 days 14 hours', v_today + INTERVAL '4 days 14 hours 30 minutes', 30, 'America/New_York', 'https://fixtures.cobalt.care/care-encounters/rebooked', 'SWITCHBOARD', 'COBALT', v_appointment_reason_id, 'UNKNOWN', NULL, FALSE, NULL, NULL),
		(v_patient_canceled_appointment_id, v_provider_id, v_patient_four_id, v_patient_four_id, 'Casey', 'Nguyen', 'care-encounter.casey@example.com', '+12155553004', v_appointment_type_id, 'Care Navigation Consultation', v_today + INTERVAL '3 days 9 hours', v_today + INTERVAL '3 days 9 hours 30 minutes', 30, 'America/New_York', 'https://fixtures.cobalt.care/care-encounters/patient-canceled', 'SWITCHBOARD', 'COBALT', v_appointment_reason_id, 'CANCELED', NULL, TRUE, NOW(), v_patient_four_id)
	ON CONFLICT (appointment_id) DO UPDATE
	SET provider_id=EXCLUDED.provider_id,
		account_id=EXCLUDED.account_id,
		created_by_account_id=EXCLUDED.created_by_account_id,
		first_name=EXCLUDED.first_name,
		last_name=EXCLUDED.last_name,
		email_address=EXCLUDED.email_address,
		contact_phone_number=EXCLUDED.contact_phone_number,
		appointment_type_id=EXCLUDED.appointment_type_id,
		title=EXCLUDED.title,
		start_time=EXCLUDED.start_time,
		end_time=EXCLUDED.end_time,
		duration_in_minutes=EXCLUDED.duration_in_minutes,
		time_zone=EXCLUDED.time_zone,
		videoconference_url=EXCLUDED.videoconference_url,
		videoconference_platform_id=EXCLUDED.videoconference_platform_id,
		scheduling_system_id=EXCLUDED.scheduling_system_id,
		appointment_reason_id=EXCLUDED.appointment_reason_id,
		attendance_status_id=EXCLUDED.attendance_status_id,
		screening_session_id=EXCLUDED.screening_session_id,
		canceled=EXCLUDED.canceled,
		canceled_at=EXCLUDED.canceled_at,
		canceled_by_account_id=EXCLUDED.canceled_by_account_id;

	-- The canonical intake has no email-address question. Keep the synthetic
	-- encounter aligned with the appointment contact even when this fixture is
	-- applied over an older local seed that collected a screening email.
	UPDATE care_encounter
	SET email_address=(
			SELECT email_address
			FROM appointment
			WHERE appointment_id=v_upcoming_appointment_id
		),
		last_updated_by_account_id=v_patient_two_id
	WHERE care_encounter_id=(
		SELECT care_encounter_id
		FROM appointment
		WHERE appointment_id=v_upcoming_appointment_id
	);

	UPDATE care_encounter
	SET care_encounter_status_id='OPEN',
		closed_at=NULL,
		closed_by_account_id=NULL,
		last_updated_by_account_id=v_navigator_account_id
	WHERE care_encounter_id=(SELECT care_encounter_id FROM appointment WHERE appointment_id=v_completed_appointment_id);

	UPDATE care_encounter
	SET care_encounter_status_id='CLOSED',
		closed_at=COALESCE(closed_at, NOW()),
		closed_by_account_id=v_patient_four_id,
		last_updated_by_account_id=v_patient_four_id
	WHERE care_encounter_id=(SELECT care_encounter_id FROM appointment WHERE appointment_id=v_patient_canceled_appointment_id);

	INSERT INTO care_encounter_note (
		care_encounter_note_id,
		care_encounter_id,
		note,
		created_by_account_id,
		last_updated_by_account_id
	) VALUES
		(v_completed_note_id,
			(SELECT care_encounter_id FROM appointment WHERE appointment_id=v_completed_appointment_id),
			'Discussed provider preferences; awaiting navigator closure after the completed appointment.',
			v_navigator_account_id, v_navigator_account_id),
		(v_upcoming_note_one_id,
			(SELECT care_encounter_id FROM appointment WHERE appointment_id=v_upcoming_appointment_id),
			'Review intake goals before the scheduled encounter.',
			v_navigator_account_id, v_navigator_account_id),
		(v_upcoming_note_two_id,
			(SELECT care_encounter_id FROM appointment WHERE appointment_id=v_upcoming_appointment_id),
			'Patient prefers an evening appointment with an in-network therapist.',
			v_navigator_account_id, v_navigator_account_id),
		(v_rebooked_note_id,
			(SELECT care_encounter_id FROM appointment WHERE appointment_id=v_rebooked_appointment_id),
			'The Care Navigator canceled the original appointment; the replacement remains in the same encounter.',
			v_navigator_account_id, v_navigator_account_id),
		(v_patient_canceled_note_id,
			(SELECT care_encounter_id FROM appointment WHERE appointment_id=v_patient_canceled_appointment_id),
			'The patient canceled their appointment.',
			v_patient_four_id, v_patient_four_id)
	ON CONFLICT (care_encounter_note_id) DO UPDATE
	SET care_encounter_id=EXCLUDED.care_encounter_id,
		note=EXCLUDED.note,
		created_by_account_id=EXCLUDED.created_by_account_id,
		last_updated_by_account_id=EXCLUDED.last_updated_by_account_id;
END $$;
COMMIT;
