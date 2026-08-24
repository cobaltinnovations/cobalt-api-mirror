BEGIN;
SELECT _v.register_patch(
	'262-local-only-care-navigator-screening-contact-seed',
	ARRAY[
		'261-local-only-care-encounter-seed',
		'264-care-navigator-screening-contact'
	],
	NULL
);

-- Keep the local Care Navigator fixture aligned with environments whose
-- appointment screening collects a dedicated contact email address.
DO $$
DECLARE
	v_screening_version_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000006';
	v_navigation_question_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000007';
	v_support_question_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-00000000000f';
	v_follow_up_question_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000013';
	v_context_question_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000017';
	v_contact_email_question_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-000000000019';
	v_contact_email_answer_option_id CONSTANT UUID := 'ca4e0000-0000-4000-8000-00000000001a';
	v_upcoming_appointment_id CONSTANT UUID := 'ca4e2000-0000-4000-8000-000000000002';
	v_upcoming_screening_session_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000001';
	v_upcoming_session_screening_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-000000000002';
	v_upcoming_contact_email_response_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-00000000000c';
	v_upcoming_contact_email_answer_id CONSTANT UUID := 'ca4e3000-0000-4000-8000-00000000000d';
	v_upcoming_patient_id CONSTANT UUID := 'ca4e1000-0000-4000-8000-000000000002';
	v_upcoming_contact_email_address CONSTANT TEXT := 'care-encounter.screening.jordan@example.com';
	v_scoring_function TEXT;
BEGIN
	UPDATE screening_question
	SET display_order=5
	WHERE screening_question_id=v_context_question_id;

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
	) VALUES (
		v_contact_email_question_id,
		v_screening_version_id,
		'FREEFORM_TEXT',
		'EMAIL_ADDRESS',
		'What email address should your Care Navigator use to contact you?',
		1,
		1,
		4,
		FALSE,
		'NEXT'
	)
	ON CONFLICT (screening_question_id) DO UPDATE
	SET screening_version_id=EXCLUDED.screening_version_id,
		screening_answer_format_id=EXCLUDED.screening_answer_format_id,
		screening_answer_content_hint_id=EXCLUDED.screening_answer_content_hint_id,
		question_text=EXCLUDED.question_text,
		minimum_answer_count=EXCLUDED.minimum_answer_count,
		maximum_answer_count=EXCLUDED.maximum_answer_count,
		display_order=EXCLUDED.display_order,
		prefer_autosubmit=EXCLUDED.prefer_autosubmit,
		screening_question_submission_style_id=EXCLUDED.screening_question_submission_style_id;

	INSERT INTO screening_answer_option (
		screening_answer_option_id,
		screening_question_id,
		answer_option_text,
		score,
		indicates_crisis,
		display_order
	) VALUES (
		v_contact_email_answer_option_id,
		v_contact_email_question_id,
		'Email address',
		1,
		FALSE,
		1
	)
	ON CONFLICT (screening_answer_option_id) DO UPDATE
	SET screening_question_id=EXCLUDED.screening_question_id,
		answer_option_text=EXCLUDED.answer_option_text,
		score=EXCLUDED.score,
		indicates_crisis=EXCLUDED.indicates_crisis,
		display_order=EXCLUDED.display_order;

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
$scoring$, v_navigation_question_id, v_support_question_id, v_follow_up_question_id,
		v_contact_email_question_id, v_context_question_id);

	UPDATE screening_version
	SET scoring_function=v_scoring_function
	WHERE screening_version_id=v_screening_version_id;

	INSERT INTO screening_session_answered_screening_question (
		screening_session_answered_screening_question_id,
		screening_session_screening_id,
		screening_question_id
	) VALUES (
		v_upcoming_contact_email_response_id,
		v_upcoming_session_screening_id,
		v_contact_email_question_id
	)
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
	) VALUES (
		v_upcoming_contact_email_answer_id,
		v_contact_email_answer_option_id,
		v_upcoming_contact_email_response_id,
		v_upcoming_patient_id,
		v_upcoming_contact_email_address,
		1
	)
	ON CONFLICT (screening_answer_id) DO UPDATE
	SET screening_answer_option_id=EXCLUDED.screening_answer_option_id,
		screening_session_answered_screening_question_id=
			EXCLUDED.screening_session_answered_screening_question_id,
		created_by_account_id=EXCLUDED.created_by_account_id,
		text=EXCLUDED.text,
		answer_order=EXCLUDED.answer_order,
		valid=TRUE;

	-- Re-run the observable seed behavior for the pre-existing local fixture.
	UPDATE appointment
	SET screening_session_id=NULL
	WHERE appointment_id=v_upcoming_appointment_id;

	UPDATE appointment
	SET screening_session_id=v_upcoming_screening_session_id
	WHERE appointment_id=v_upcoming_appointment_id;
END $$;

COMMIT;
