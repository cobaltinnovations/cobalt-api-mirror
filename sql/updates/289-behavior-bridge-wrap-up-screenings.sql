BEGIN;
SELECT _v.register_patch(
  '289-behavior-bridge-wrap-up-screenings',
  ARRAY['288-provider-eligibility-pools'],
  NULL
);

-- Evaluation Questions worksheet in 2026_08_23 _ Cobalt BB Data Dictionary - Evaluation.xlsx.
CREATE TEMPORARY TABLE bb_wrap_up_survey (
  survey_key TEXT PRIMARY KEY,
  screening_name TEXT NOT NULL,
  course_title TEXT NOT NULL,
  course_unit_title TEXT NOT NULL,
  screening_id UUID NOT NULL UNIQUE,
  screening_version_id UUID NOT NULL UNIQUE,
  screening_flow_id UUID NOT NULL UNIQUE,
  screening_flow_version_id UUID NOT NULL UNIQUE,
  expected_question_count INTEGER NOT NULL,
  expected_option_count INTEGER NOT NULL
) ON COMMIT DROP;

INSERT INTO bb_wrap_up_survey (
  survey_key, screening_name, course_title, course_unit_title,
  screening_id, screening_version_id, screening_flow_id, screening_flow_version_id,
  expected_question_count, expected_option_count
) VALUES
  ('PARENTING_TEENS', 'Parenting Teens Wrap Up', 'Parenting Teens', 'Wrap up',
    'bb289000-0000-4000-8000-000000000001',
    'bb289000-0000-4000-8000-000000000002',
    'bb289000-0000-4000-8000-000000000003',
    'bb289000-0000-4000-8000-000000000004', 8, 36),
  ('UNDERSTANDING_TRAUMA', 'Understanding Trauma Wrap Up', 'Understanding Trauma', 'Wrap up',
    'bb289000-0000-4000-8000-000000000011',
    'bb289000-0000-4000-8000-000000000012',
    'bb289000-0000-4000-8000-000000000013',
    'bb289000-0000-4000-8000-000000000014', 9, 43);

CREATE TEMPORARY TABLE bb_wrap_up_question (
  survey_key TEXT NOT NULL REFERENCES bb_wrap_up_survey(survey_key),
  question_key TEXT NOT NULL,
  display_order INTEGER NOT NULL,
  screening_answer_format_id TEXT NOT NULL,
  question_text TEXT NOT NULL,
  maximum_answer_count INTEGER NOT NULL,
  reporting_key TEXT NOT NULL,
  open_text_key TEXT,
  PRIMARY KEY (survey_key, question_key),
  UNIQUE (survey_key, display_order)
) ON COMMIT DROP;

INSERT INTO bb_wrap_up_question (
  survey_key, question_key, display_order, screening_answer_format_id,
  question_text, maximum_answer_count, reporting_key, open_text_key
) VALUES
  ('PARENTING_TEENS', 'needs', 1, 'MULTI_SELECT', 'Which of the following topics within Parenting Teens best addressed your needs? Please select all that apply.', 7, 'bb_teens_postcourse_needs', NULL),
  ('PARENTING_TEENS', 'match', 2, 'SINGLE_SELECT', 'How good of a match was Parenting Teens to your needs?', 1, 'bb_teens_postcourse_match', NULL),
  ('PARENTING_TEENS', 'confidence', 3, 'SINGLE_SELECT', 'How confident do you feel applying the skills you learned in this course?', 1, 'bb_teens_postcourse_confidence', NULL),
  ('PARENTING_TEENS', 'apply', 4, 'SINGLE_SELECT', 'How likely are you to apply at least one new technique/approach from this course within two weeks?', 1, 'bb_teens_postcourse_apply', NULL),
  ('PARENTING_TEENS', 'navigate', 5, 'SINGLE_SELECT', 'How easy or difficult was it to navigate this online format?', 1, 'bb_teens_postcourse_navigate', NULL),
  ('PARENTING_TEENS', 'likes', 6, 'MULTI_SELECT', 'What, if anything, did you like about this platform? Please select all that apply.', 5, 'bb_teens_postcourse_likes', 'bb_teens_postcourse_likesopen'),
  ('PARENTING_TEENS', 'goal', 7, 'SINGLE_SELECT', 'One goal of Behavior Bridge was to improve access to parenting support. To what extent do you think this course helped achieve that goal?', 1, 'bb_teens_postcourse_goal', NULL),
  ('PARENTING_TEENS', 'recommend', 8, 'SINGLE_SELECT', 'How likely are you to recommend Parenting Teens?', 1, 'bb_teens_postcourse_recommend', NULL),
  ('UNDERSTANDING_TRAUMA', 'needs', 1, 'MULTI_SELECT', 'Which of the following topics discussed in Understanding Trauma best addressed your needs? Please select all that apply.', 7, 'bb_trauma_postcourse_needs', NULL),
  ('UNDERSTANDING_TRAUMA', 'match', 2, 'SINGLE_SELECT', 'How good of a match was Understanding Trauma to your needs?', 1, 'bb_trauma_postcourse_match', NULL),
  ('UNDERSTANDING_TRAUMA', 'confidence', 3, 'SINGLE_SELECT', 'How confident do you feel applying the skills you learned in this course?', 1, 'bb_trauma_postcourse_confidence', NULL),
  ('UNDERSTANDING_TRAUMA', 'apply', 4, 'SINGLE_SELECT', 'How likely are you to apply at least one new technique/approach from this course within two weeks?', 1, 'bb_trauma_postcourse_apply', NULL),
  ('UNDERSTANDING_TRAUMA', 'actions', 5, 'MULTI_SELECT', 'Do you plan to take any of the following steps as a result of participating in this course? Please select all that apply.', 7, 'bb_trauma_postcourse_actions', 'bb_trauma_postcourse_actionsopen'),
  ('UNDERSTANDING_TRAUMA', 'navigate', 6, 'SINGLE_SELECT', 'How easy or difficult was it to navigate this online format?', 1, 'bb_trauma_postcourse_navigate', NULL),
  ('UNDERSTANDING_TRAUMA', 'likes', 7, 'MULTI_SELECT', 'What, if anything, did you like about this platform? Please select all that apply.', 5, 'bb_trauma_postcourse_likes', 'bb_trauma_postcourse_likesopen'),
  ('UNDERSTANDING_TRAUMA', 'goal', 8, 'SINGLE_SELECT', 'One goal of Behavior Bridge was to improve access to parenting support. To what extent do you think this course helped achieve that goal?', 1, 'bb_trauma_postcourse_goal', NULL),
  ('UNDERSTANDING_TRAUMA', 'recommend', 9, 'SINGLE_SELECT', 'How likely are you to recommend Understanding Trauma?', 1, 'bb_trauma_postcourse_recommend', NULL);

CREATE TEMPORARY TABLE bb_wrap_up_option (
  survey_key TEXT NOT NULL,
  question_key TEXT NOT NULL,
  display_order INTEGER NOT NULL,
  backend_value INTEGER NOT NULL,
  answer_option_text TEXT NOT NULL,
  freeform_supplement BOOLEAN NOT NULL,
  freeform_supplement_text TEXT,
  freeform_supplement_text_auto_show BOOLEAN NOT NULL,
  freeform_supplement_text_required BOOLEAN NOT NULL,
  PRIMARY KEY (survey_key, question_key, display_order),
  FOREIGN KEY (survey_key, question_key)
    REFERENCES bb_wrap_up_question(survey_key, question_key)
) ON COMMIT DROP;

INSERT INTO bb_wrap_up_option (
  survey_key, question_key, display_order, backend_value, answer_option_text,
  freeform_supplement, freeform_supplement_text,
  freeform_supplement_text_auto_show, freeform_supplement_text_required
) VALUES
  ('PARENTING_TEENS', 'needs', 1, 1, 'Understanding teen development', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'needs', 2, 2, 'Identifying typical vs. atypical teen behavior', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'needs', 3, 3, 'Navigating mental health and getting help', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'needs', 4, 4, 'Building a positive parent - teen relationship', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'needs', 5, 5, 'Having tough conversations with your teen', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'needs', 6, 6, 'Staying calm during tough conversations', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'needs', 7, 7, 'None of the above', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'match', 1, 1, 'Poor match', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'match', 2, 2, 'Fair match', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'match', 3, 3, 'Good match', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'match', 4, 4, 'Great match', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'confidence', 1, 1, 'Not at all confident', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'confidence', 2, 2, 'A little confident', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'confidence', 3, 3, 'Moderately confident', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'confidence', 4, 4, 'Very confident', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'apply', 1, 1, 'Very unlikely', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'apply', 2, 2, 'Somewhat unlikely', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'apply', 3, 3, 'Somewhat likely', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'apply', 4, 4, 'Very likely', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'navigate', 1, 1, 'Very difficult', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'navigate', 2, 2, 'Somewhat difficult', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'navigate', 3, 3, 'Somewhat easy', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'navigate', 4, 4, 'Very easy', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'goal', 1, 1, 'Not at all', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'goal', 2, 2, 'A little', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'goal', 3, 3, 'Moderately', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'goal', 4, 4, 'Very much', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'recommend', 1, 1, 'Very unlikely', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'recommend', 2, 2, 'Somewhat unlikely', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'recommend', 3, 3, 'Somewhat likely', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'recommend', 4, 4, 'Very likely', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'likes', 1, 1, 'Easy to access', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'likes', 2, 2, 'Information came from a trusted source', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'likes', 3, 3, 'Combination of videos and written information', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'likes', 4, 4, 'Level of detail', FALSE, NULL, FALSE, FALSE),
  ('PARENTING_TEENS', 'likes', 5, 5, 'Other, please describe:', TRUE, 'Please describe', TRUE, TRUE),
  ('UNDERSTANDING_TRAUMA', 'needs', 1, 1, 'Connecting with your child (ex: showing interest, being fully present)', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'needs', 2, 2, 'Building safety (ex: doing a safety check, building a safety team)', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'needs', 3, 3, 'Relaxation techniques (ex: deep breathing, muscle relaxation)', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'needs', 4, 4, 'Navigating feelings (ex: identifying feelings, using positive affirmations)', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'needs', 5, 5, 'Coping with triggers (ex: journaling, deep breathing)', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'needs', 6, 6, 'Building your Support System (ex: talking to your child''s teacher)', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'needs', 7, 7, 'None of the above', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'match', 1, 1, 'Poor match', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'match', 2, 2, 'Fair match', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'match', 3, 3, 'Good match', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'match', 4, 4, 'Great match', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'confidence', 1, 1, 'Not at all confident', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'confidence', 2, 2, 'A little confident', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'confidence', 3, 3, 'Moderately confident', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'confidence', 4, 4, 'Very confident', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'apply', 1, 1, 'Very unlikely', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'apply', 2, 2, 'Somewhat unlikely', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'apply', 3, 3, 'Somewhat likely', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'apply', 4, 4, 'Very likely', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'actions', 1, 1, 'Use the skills', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'actions', 2, 2, 'Have conversations with my child', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'actions', 3, 3, 'Therapy for my child', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'actions', 4, 4, 'Therapy for myself', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'actions', 5, 5, 'Build my support systems and networks', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'actions', 6, 6, 'Seek more information', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'actions', 7, 7, 'Other, please describe:', TRUE, 'Please describe', TRUE, TRUE),
  ('UNDERSTANDING_TRAUMA', 'navigate', 1, 1, 'Very difficult', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'navigate', 2, 2, 'Somewhat difficult', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'navigate', 3, 3, 'Somewhat easy', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'navigate', 4, 4, 'Very easy', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'likes', 1, 1, 'Easy to access', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'likes', 2, 2, 'Information came from a trusted source', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'likes', 3, 3, 'Combination of videos and written information', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'likes', 4, 4, 'Level of detail', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'likes', 5, 5, 'Other, please describe:', TRUE, 'Please describe', TRUE, TRUE),
  ('UNDERSTANDING_TRAUMA', 'goal', 1, 1, 'Not at all', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'goal', 2, 2, 'A little', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'goal', 3, 3, 'Moderately', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'goal', 4, 4, 'Very much', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'recommend', 1, 1, 'Very unlikely', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'recommend', 2, 2, 'Somewhat unlikely', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'recommend', 3, 3, 'Somewhat likely', FALSE, NULL, FALSE, FALSE),
  ('UNDERSTANDING_TRAUMA', 'recommend', 4, 4, 'Very likely', FALSE, NULL, FALSE, FALSE);

CREATE TEMPORARY TABLE bb_wrap_up_question_id (
  survey_key TEXT NOT NULL,
  question_key TEXT NOT NULL,
  display_order INTEGER NOT NULL,
  screening_question_id UUID NOT NULL,
  PRIMARY KEY (survey_key, question_key),
  UNIQUE (screening_question_id)
) ON COMMIT DROP;

DO $publish$
DECLARE
  v_institution_count INTEGER;
  v_institution_id TEXT;
  v_created_by_account_id UUID;
  v_survey RECORD;
  v_screening_id UUID;
  v_screening_version_id UUID;
  v_screening_flow_id UUID;
  v_screening_flow_version_id UUID;
  v_course_unit_id UUID;
  v_course_unit_count INTEGER;
  v_question_ids_json TEXT;
  v_scoring_function TEXT;
  v_question_count INTEGER;
  v_option_count INTEGER;
  v_updated_unit_count INTEGER;
BEGIN
  SELECT COUNT(*), MIN(institution_id)
  INTO v_institution_count, v_institution_id
  FROM institution
  WHERE institution_id IN ('BEHAVIOR_BRIDGE', 'COBALT_COURSES');

  IF v_institution_count <> 1 THEN
    RAISE EXCEPTION 'Expected exactly one Behavior Bridge production or local institution; found %.', v_institution_count;
  END IF;

  SELECT account_id
  INTO v_created_by_account_id
  FROM account
  WHERE institution_id=v_institution_id
    AND role_id='ADMINISTRATOR'
    AND active=TRUE
  ORDER BY account_id
  LIMIT 1;

  IF v_created_by_account_id IS NULL THEN
    RAISE EXCEPTION 'An active administrator account is required for institution %.', v_institution_id;
  END IF;

  FOR v_survey IN
    SELECT *
    FROM bb_wrap_up_survey
    ORDER BY survey_key
  LOOP
    SELECT COUNT(*)
    INTO v_course_unit_count
    FROM institution_course
    JOIN course
      ON course.course_id=institution_course.course_id
    JOIN course_module
      ON course_module.course_id=course.course_id
    JOIN course_unit
      ON course_unit.course_module_id=course_module.course_module_id
    WHERE institution_course.institution_id=v_institution_id
      AND course.title=v_survey.course_title
      AND BTRIM(course_unit.title)=v_survey.course_unit_title;

    IF v_course_unit_count=0 AND v_institution_id='COBALT_COURSES' THEN
      v_course_unit_id := NULL;
      RAISE NOTICE 'Creating % evaluation screening locally without a course link because course % is not seeded.',
        v_survey.screening_name, v_survey.course_title;
    ELSIF v_course_unit_count <> 1 THEN
      RAISE EXCEPTION 'Expected exactly one % unit in course % for institution %; found %.',
        v_survey.course_unit_title, v_survey.course_title, v_institution_id, v_course_unit_count;
    ELSE
      SELECT course_unit.course_unit_id
      INTO v_course_unit_id
      FROM institution_course
      JOIN course
        ON course.course_id=institution_course.course_id
      JOIN course_module
        ON course_module.course_id=course.course_id
      JOIN course_unit
        ON course_unit.course_module_id=course_module.course_module_id
      WHERE institution_course.institution_id=v_institution_id
        AND course.title=v_survey.course_title
        AND BTRIM(course_unit.title)=v_survey.course_unit_title;
    END IF;

    IF EXISTS (
      SELECT 1
      FROM screening_flow
      WHERE institution_id=v_institution_id
        AND LOWER(BTRIM(name))=LOWER(v_survey.screening_name || ' Evaluation')
    ) THEN
      RAISE EXCEPTION 'A screening flow named % Evaluation already exists for institution %.',
        v_survey.screening_name, v_institution_id;
    END IF;

    v_screening_id := v_survey.screening_id;
    v_screening_version_id := v_survey.screening_version_id;
    v_screening_flow_id := v_survey.screening_flow_id;
    v_screening_flow_version_id := v_survey.screening_flow_version_id;

    INSERT INTO bb_wrap_up_question_id (
      survey_key, question_key, display_order, screening_question_id
    )
    SELECT question.survey_key,
      question.question_key,
      question.display_order,
      uuid_generate_v4()
    FROM bb_wrap_up_question question
    WHERE question.survey_key=v_survey.survey_key;

    SELECT ARRAY_TO_JSON(ARRAY_AGG(screening_question_id::TEXT ORDER BY display_order))::TEXT
    INTO v_question_ids_json
    FROM bb_wrap_up_question_id
    WHERE survey_key=v_survey.survey_key;

    v_scoring_function := FORMAT($scoring$
const questionIds = %s;
const answeredQuestionIds = new Set((input.answeredScreeningQuestionIds || []).map(String));
const nextUnansweredQuestionId = questionIds.find((questionId) => {
  return !answeredQuestionIds.has(questionId);
});

output.completed = nextUnansweredQuestionId === undefined;
output.score = { overallScore: output.completed ? 1 : 0 };
output.belowScoringThreshold = !output.completed;
output.nextScreeningQuestionId = output.completed ? null : nextUnansweredQuestionId;
$scoring$, v_question_ids_json);

    INSERT INTO screening (
      screening_id, name, active_screening_version_id, created_by_account_id
    ) VALUES (
      v_screening_id, v_survey.screening_name, NULL, v_created_by_account_id
    );

    INSERT INTO screening_version (
      screening_version_id, screening_id, screening_type_id,
      created_by_account_id, version_number, scoring_function
    ) VALUES (
      v_screening_version_id, v_screening_id, 'CUSTOM',
      v_created_by_account_id, 1, v_scoring_function
    );

    INSERT INTO screening_question (
      screening_question_id, screening_version_id,
      screening_answer_format_id, screening_answer_content_hint_id,
      question_text, minimum_answer_count, maximum_answer_count,
      display_order, prefer_autosubmit,
      screening_question_submission_style_id, metadata
    )
    SELECT question_id.screening_question_id,
      v_screening_version_id,
      question.screening_answer_format_id,
      'NONE',
      question.question_text,
      0,
      question.maximum_answer_count,
      question.display_order,
      FALSE,
      'NEXT',
      JSONB_BUILD_OBJECT('reporting', JSONB_STRIP_NULLS(JSONB_BUILD_OBJECT(
        'key', question.reporting_key,
        'openTextKey', question.open_text_key
      )))
    FROM bb_wrap_up_question question
    JOIN bb_wrap_up_question_id question_id
      ON question_id.survey_key=question.survey_key
      AND question_id.question_key=question.question_key
    WHERE question.survey_key=v_survey.survey_key;

    INSERT INTO screening_answer_option (
      screening_answer_option_id, screening_question_id,
      answer_option_text, score, indicates_crisis, display_order,
      freeform_supplement, freeform_supplement_text,
      freeform_supplement_text_auto_show,
      freeform_supplement_content_hint_id,
      freeform_supplement_text_required, metadata
    )
    SELECT uuid_generate_v4(),
      question_id.screening_question_id,
      option.answer_option_text,
      option.backend_value,
      FALSE,
      option.display_order,
      option.freeform_supplement,
      option.freeform_supplement_text,
      option.freeform_supplement_text_auto_show,
      'NONE',
      option.freeform_supplement_text_required,
      JSONB_BUILD_OBJECT('backendValue', option.backend_value)
    FROM bb_wrap_up_option option
    JOIN bb_wrap_up_question_id question_id
      ON question_id.survey_key=option.survey_key
      AND question_id.question_key=option.question_key
    WHERE option.survey_key=v_survey.survey_key;

    UPDATE screening
    SET active_screening_version_id=v_screening_version_id
    WHERE screening_id=v_screening_id;

    INSERT INTO screening_institution (screening_id, institution_id)
    VALUES (v_screening_id, v_institution_id);

    INSERT INTO screening_flow (
      screening_flow_id, institution_id, active_screening_flow_version_id,
      screening_flow_type_id, created_by_account_id, name
    ) VALUES (
      v_screening_flow_id, v_institution_id, NULL,
      'COURSE_UNIT', v_created_by_account_id, v_survey.screening_name || ' Evaluation'
    );

    INSERT INTO screening_flow_version (
      screening_flow_version_id, screening_flow_id, initial_screening_id,
      phone_number_required, version_number, orchestration_function,
      results_function, destination_function, created_by_account_id,
      skippable, screening_flow_skip_type_id
    ) VALUES (
      v_screening_flow_version_id, v_screening_flow_id, v_screening_id,
      FALSE, 1,
      $orchestration$
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
$orchestration$,
      $results$
output.supportRoleRecommendations = [];
output.recommendLegacyContentAnswerIds = false;
output.legacyContentAnswerIds = [];
output.recommendedTagIds = [];
output.recommendedFeatureIds = [];
output.integratedCareTriages = [];
$results$,
      $destination$
output.screeningSessionDestinationId = null;
output.context = {};
$destination$,
      v_created_by_account_id, FALSE, 'SKIP'
    );

    UPDATE screening_flow
    SET active_screening_flow_version_id=v_screening_flow_version_id
    WHERE screening_flow_id=v_screening_flow_id;

    IF v_course_unit_id IS NOT NULL THEN
      UPDATE course_unit
      SET screening_flow_id=v_screening_flow_id
      WHERE course_unit_id=v_course_unit_id;
      GET DIAGNOSTICS v_updated_unit_count = ROW_COUNT;

      IF v_updated_unit_count <> 1 THEN
        RAISE EXCEPTION 'Expected to attach the % evaluation flow to one course unit; updated % rows.',
          v_survey.screening_name, v_updated_unit_count;
      END IF;
    END IF;

    SELECT COUNT(*)
    INTO v_question_count
    FROM screening_question
    WHERE screening_version_id=v_screening_version_id;

    SELECT COUNT(*)
    INTO v_option_count
    FROM screening_answer_option
    JOIN screening_question
      ON screening_question.screening_question_id=screening_answer_option.screening_question_id
    WHERE screening_question.screening_version_id=v_screening_version_id;

    IF v_question_count <> v_survey.expected_question_count OR v_option_count <> v_survey.expected_option_count THEN
      RAISE EXCEPTION '% evaluation screening validation failed (questions: %, options: %).',
        v_survey.screening_name, v_question_count, v_option_count;
    END IF;

    RAISE NOTICE 'Created % evaluation screening with % questions and % answer options.',
      v_survey.screening_name, v_question_count, v_option_count;
  END LOOP;
END
$publish$;

COMMIT;
