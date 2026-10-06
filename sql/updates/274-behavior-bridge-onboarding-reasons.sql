BEGIN;
SELECT _v.register_patch(
	'274-behavior-bridge-onboarding-reasons',
	ARRAY['273-provider-search-display-order'],
	NULL
);

-- Publish the reasons question as a new version so active sessions retain
-- their original question and answer IDs.
DO $publish$
DECLARE
	v_flow_id UUID;
	v_old_flow_version_id UUID;
	v_new_flow_version_id UUID := uuid_generate_v4();
	v_screening_id UUID;
	v_old_screening_version_id UUID;
	v_new_screening_version_id UUID := uuid_generate_v4();
	v_role_question_id UUID;
	v_role_display_order INTEGER;
	v_professional_option_ids UUID[];
	v_new_professional_option_ids UUID[];
	v_following_question_id UUID;
	v_reason_question_id UUID := uuid_generate_v4();
	v_reason_question_text CONSTANT TEXT := 'Please select all the reasons that led you to try Behavior Bridge.';
	v_flow_count INTEGER;
	v_question_count INTEGER;
	v_new_screening_version_number INTEGER;
	v_new_flow_version_number INTEGER;
	v_created_by_account_id UUID;
	v_scoring_function TEXT;
	v_id_map RECORD;
BEGIN
	SELECT COUNT(*)
	INTO v_flow_count
	FROM institution i
	JOIN screening_flow sf
		ON sf.screening_flow_id=i.onboarding_screening_flow_id
	WHERE i.institution_id IN ('BEHAVIOR_BRIDGE', 'COBALT_COURSES');

	IF v_flow_count <> 1 THEN
		RAISE EXCEPTION 'Expected exactly one active Behavior Bridge onboarding flow across BEHAVIOR_BRIDGE and COBALT_COURSES; found %.', v_flow_count;
	END IF;

	SELECT sf.screening_flow_id,
		sf.active_screening_flow_version_id,
		sfv.initial_screening_id,
		s.active_screening_version_id
	INTO v_flow_id,
		v_old_flow_version_id,
		v_screening_id,
		v_old_screening_version_id
	FROM institution i
	JOIN screening_flow sf
		ON sf.screening_flow_id=i.onboarding_screening_flow_id
	JOIN screening_flow_version sfv
		ON sfv.screening_flow_version_id=sf.active_screening_flow_version_id
	JOIN screening s
		ON s.screening_id=sfv.initial_screening_id
	WHERE i.institution_id IN ('BEHAVIOR_BRIDGE', 'COBALT_COURSES');

	IF v_old_flow_version_id IS NULL OR v_old_screening_version_id IS NULL THEN
		RAISE EXCEPTION 'Behavior Bridge onboarding must have active screening and flow versions before applying this patch.';
	END IF;

	SELECT COUNT(*)
	INTO v_question_count
	FROM screening_question sq
	WHERE sq.screening_version_id=v_old_screening_version_id
		AND LOWER(REGEXP_REPLACE(BTRIM(sq.question_text), '[[:space:]?]+$', '')) =
			LOWER('What is your relationship to the child that you are seeking help for');

	IF v_question_count <> 1 THEN
		RAISE EXCEPTION 'Expected exactly one active relationship question in the Behavior Bridge onboarding screening; found %.', v_question_count;
	END IF;

	SELECT sq.screening_question_id, sq.display_order
	INTO v_role_question_id, v_role_display_order
	FROM screening_question sq
	WHERE sq.screening_version_id=v_old_screening_version_id
		AND LOWER(REGEXP_REPLACE(BTRIM(sq.question_text), '[[:space:]?]+$', '')) =
			LOWER('What is your relationship to the child that you are seeking help for');

	SELECT ARRAY_AGG(sao.screening_answer_option_id ORDER BY sao.display_order)
	INTO v_professional_option_ids
	FROM screening_answer_option sao
	WHERE sao.screening_question_id=v_role_question_id
		AND (
			LOWER(sao.answer_option_text) LIKE '%professional working with children and families%'
			OR LOWER(sao.answer_option_text) LIKE '%other professional working with the child%'
		);

	IF COALESCE(ARRAY_LENGTH(v_professional_option_ids, 1), 0) = 0 THEN
		RAISE EXCEPTION 'Expected the professional working with children and families relationship answer option.';
	END IF;

	IF NOT EXISTS (
		SELECT 1
		FROM screening_question sq
		WHERE sq.screening_version_id=v_old_screening_version_id
			AND sq.display_order=v_role_display_order + 1
	) THEN
		RAISE EXCEPTION 'The relationship question must have a following question so the professional branch can skip the new item.';
	END IF;

	SELECT sv.created_by_account_id,
		(SELECT COALESCE(MAX(version_number), 0) + 1 FROM screening_version WHERE screening_id=v_screening_id)
	INTO v_created_by_account_id, v_new_screening_version_number
	FROM screening_version sv
	WHERE sv.screening_version_id=v_old_screening_version_id;

	SELECT COALESCE(MAX(version_number), 0) + 1
	INTO v_new_flow_version_number
	FROM screening_flow_version
	WHERE screening_flow_id=v_flow_id;

	CREATE TEMPORARY TABLE bb_onboarding_reasons_question_id_map (
		old_id UUID PRIMARY KEY,
		new_id UUID NOT NULL UNIQUE,
		old_display_order INTEGER NOT NULL
	) ON COMMIT DROP;

	INSERT INTO bb_onboarding_reasons_question_id_map (old_id, new_id, old_display_order)
	SELECT sq.screening_question_id,
		uuid_generate_v4(),
		sq.display_order
	FROM screening_question sq
	WHERE sq.screening_version_id=v_old_screening_version_id;

	CREATE TEMPORARY TABLE bb_onboarding_reasons_option_id_map (
		old_id UUID PRIMARY KEY,
		new_id UUID NOT NULL UNIQUE,
		old_question_id UUID NOT NULL
	) ON COMMIT DROP;

	INSERT INTO bb_onboarding_reasons_option_id_map (old_id, new_id, old_question_id)
	SELECT sao.screening_answer_option_id,
		uuid_generate_v4(),
		sao.screening_question_id
	FROM screening_answer_option sao
	JOIN bb_onboarding_reasons_question_id_map qmap
		ON qmap.old_id=sao.screening_question_id;

	SELECT qmap.new_id
	INTO v_following_question_id
	FROM bb_onboarding_reasons_question_id_map qmap
	WHERE qmap.old_display_order=v_role_display_order + 1;

	SELECT ARRAY_AGG(omap.new_id ORDER BY sao.display_order)
	INTO v_new_professional_option_ids
	FROM bb_onboarding_reasons_option_id_map omap
	JOIN screening_answer_option sao
		ON sao.screening_answer_option_id=omap.old_id
	WHERE sao.screening_answer_option_id=ANY(v_professional_option_ids);

	IF v_following_question_id IS NULL OR v_new_professional_option_ids IS NULL THEN
		RAISE EXCEPTION 'Unable to map the existing question or professional answer option into the new screening version.';
	END IF;

	SELECT sv.scoring_function
	INTO v_scoring_function
	FROM screening_version sv
	WHERE sv.screening_version_id=v_old_screening_version_id;

	FOR v_id_map IN
		SELECT old_id, new_id FROM bb_onboarding_reasons_question_id_map
		UNION ALL
		SELECT old_id, new_id FROM bb_onboarding_reasons_option_id_map
	LOOP
		v_scoring_function := REPLACE(v_scoring_function, v_id_map.old_id::TEXT, v_id_map.new_id::TEXT);
	END LOOP;

	v_scoring_function := FORMAT($scoring$
const originalOutput = output;
{
	const output = originalOutput;
%s
}

if (String(input.screeningQuestionId) === '%s') {
	const professionalOptionIds = new Set(%s);
	const selectedProfessionalOption = (input.screeningAnswers || []).some((answer) => {
		return professionalOptionIds.has(String(answer.screeningAnswerOptionId));
	});
	output.completed = false;
	output.nextScreeningQuestionId = selectedProfessionalOption ? '%s' : '%s';
}
$scoring$,
		v_scoring_function,
		(SELECT new_id::TEXT FROM bb_onboarding_reasons_question_id_map WHERE old_id=v_role_question_id),
		ARRAY_TO_JSON(v_new_professional_option_ids)::TEXT,
		v_following_question_id::TEXT,
		v_reason_question_id::TEXT);

	INSERT INTO screening_version (
		screening_version_id,
		screening_id,
		screening_type_id,
		created_by_account_id,
		version_number,
		scoring_function
	)
	SELECT v_new_screening_version_id,
		sv.screening_id,
		sv.screening_type_id,
		v_created_by_account_id,
		v_new_screening_version_number,
		v_scoring_function
	FROM screening_version sv
	WHERE sv.screening_version_id=v_old_screening_version_id;

	INSERT INTO screening_question (
		screening_question_id,
		screening_version_id,
		screening_answer_format_id,
		screening_answer_content_hint_id,
		intro_text,
		question_text,
		minimum_answer_count,
		maximum_answer_count,
		display_order,
		created,
		last_updated,
		footer_text,
		pre_question_screening_confirmation_prompt_id,
		metadata,
		prefer_autosubmit,
		screening_question_submission_style_id,
		supplement_text
	)
	SELECT qmap.new_id,
		v_new_screening_version_id,
		sq.screening_answer_format_id,
		sq.screening_answer_content_hint_id,
		sq.intro_text,
		sq.question_text,
		sq.minimum_answer_count,
		sq.maximum_answer_count,
		CASE WHEN sq.display_order > v_role_display_order THEN sq.display_order + 1 ELSE sq.display_order END,
		NOW(),
		NOW(),
		sq.footer_text,
		sq.pre_question_screening_confirmation_prompt_id,
		sq.metadata,
		sq.prefer_autosubmit,
		sq.screening_question_submission_style_id,
		sq.supplement_text
	FROM screening_question sq
	JOIN bb_onboarding_reasons_question_id_map qmap
		ON qmap.old_id=sq.screening_question_id;

	INSERT INTO screening_question (
		screening_question_id,
		screening_version_id,
		screening_answer_format_id,
		screening_answer_content_hint_id,
		intro_text,
		question_text,
		minimum_answer_count,
		maximum_answer_count,
		display_order,
		footer_text,
		metadata,
		prefer_autosubmit,
		screening_question_submission_style_id,
		supplement_text
	) VALUES (
		v_reason_question_id,
		v_new_screening_version_id,
		'MULTI_SELECT',
		'NONE',
		NULL,
		v_reason_question_text,
		1,
		9,
		v_role_display_order + 1,
		NULL,
		'{"reporting":{"key":"bb_onboarding_reasons","openTextKey":"bb_onboarding_reasonsopen"}}'::JSONB,
		FALSE,
		'NEXT',
		NULL
	);

	INSERT INTO screening_answer_option (
		screening_answer_option_id,
		screening_question_id,
		answer_option_text,
		score,
		indicates_crisis,
		display_order,
		created,
		last_updated,
		freeform_supplement,
		freeform_supplement_text,
		metadata,
		freeform_supplement_text_auto_show,
		freeform_supplement_content_hint_id,
		freeform_supplement_text_required
	)
	SELECT omap.new_id,
		qmap.new_id,
		sao.answer_option_text,
		sao.score,
		sao.indicates_crisis,
		sao.display_order,
		NOW(),
		NOW(),
		sao.freeform_supplement,
		sao.freeform_supplement_text,
		sao.metadata,
		sao.freeform_supplement_text_auto_show,
		sao.freeform_supplement_content_hint_id,
		sao.freeform_supplement_text_required
	FROM screening_answer_option sao
	JOIN bb_onboarding_reasons_option_id_map omap
		ON omap.old_id=sao.screening_answer_option_id
	JOIN bb_onboarding_reasons_question_id_map qmap
		ON qmap.old_id=sao.screening_question_id;

	INSERT INTO screening_answer_option (
		screening_answer_option_id,
		screening_question_id,
		answer_option_text,
		score,
		indicates_crisis,
		display_order,
		freeform_supplement,
		freeform_supplement_text,
		freeform_supplement_text_auto_show,
		freeform_supplement_content_hint_id,
		freeform_supplement_text_required
	)
	SELECT uuid_generate_v4(),
		v_reason_question_id,
		reason.answer_option_text,
		0,
		FALSE,
		reason.display_order,
		reason.freeform_supplement,
		reason.freeform_supplement_text,
		reason.freeform_supplement_text_auto_show,
		'NONE',
		reason.freeform_supplement_text_required
	FROM (VALUES
		(1, 'Improve my relationship with my child', FALSE, NULL::TEXT, FALSE, FALSE),
		(2, 'Concerns with my child''s mental health', FALSE, NULL::TEXT, FALSE, FALSE),
		(3, 'My child experienced a traumatic event', FALSE, NULL::TEXT, FALSE, FALSE),
		(4, 'I am concerned about my child’s behavior', FALSE, NULL::TEXT, FALSE, FALSE),
		(5, 'My child''s relationship with peers', FALSE, NULL::TEXT, FALSE, FALSE),
		(6, 'My child''s success at school', FALSE, NULL::TEXT, FALSE, FALSE),
		(7, 'Help my child improve their sleep', FALSE, NULL::TEXT, FALSE, FALSE),
		(8, 'Generally more information about parenting', FALSE, NULL::TEXT, FALSE, FALSE),
		(9, 'Other, please describe:', TRUE, 'Please describe', TRUE, TRUE)
	) AS reason(display_order, answer_option_text, freeform_supplement, freeform_supplement_text, freeform_supplement_text_auto_show, freeform_supplement_text_required);

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
		created,
		last_updated,
		pre_completion_screening_confirmation_prompt_id,
		screening_flow_skip_type_id,
		skippable,
		minutes_until_retake,
		recommendation_expiration_minutes,
		initialization_function
	)
	SELECT v_new_flow_version_id,
		sfv.screening_flow_id,
		sfv.initial_screening_id,
		sfv.phone_number_required,
		v_new_flow_version_number,
		sfv.orchestration_function,
		sfv.results_function,
		sfv.destination_function,
		sfv.created_by_account_id,
		NOW(),
		NOW(),
		sfv.pre_completion_screening_confirmation_prompt_id,
		sfv.screening_flow_skip_type_id,
		sfv.skippable,
		sfv.minutes_until_retake,
		sfv.recommendation_expiration_minutes,
		sfv.initialization_function
	FROM screening_flow_version sfv
	WHERE sfv.screening_flow_version_id=v_old_flow_version_id;

	INSERT INTO screening_flow_version_account_source (
		screening_flow_version_id,
		account_source_id,
		display_order
	)
	SELECT v_new_flow_version_id,
		source.account_source_id,
		source.display_order
	FROM screening_flow_version_account_source source
	WHERE source.screening_flow_version_id=v_old_flow_version_id;

	INSERT INTO screening_flow_version_screening_type (
		screening_flow_version_id,
		screening_type_id
	)
	SELECT v_new_flow_version_id,
		flow_screening_type.screening_type_id
	FROM screening_flow_version_screening_type flow_screening_type
	WHERE flow_screening_type.screening_flow_version_id=v_old_flow_version_id;

	UPDATE screening
	SET active_screening_version_id=v_new_screening_version_id
	WHERE screening_id=v_screening_id;

	UPDATE screening_flow
	SET active_screening_flow_version_id=v_new_flow_version_id
	WHERE screening_flow_id=v_flow_id;

	IF NOT EXISTS (
		SELECT 1
		FROM screening_question
		WHERE screening_version_id=v_new_screening_version_id
			AND screening_question_id=v_reason_question_id
			AND display_order=v_role_display_order + 1
	) OR NOT EXISTS (
		SELECT 1
		FROM screening_answer_option
		WHERE screening_question_id=v_reason_question_id
			AND freeform_supplement=TRUE
			AND freeform_supplement_text_required=TRUE
	) THEN
		RAISE EXCEPTION 'Behavior Bridge onboarding reasons question failed its post-publication checks.';
	END IF;
END
$publish$;

COMMIT;
