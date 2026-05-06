BEGIN;
SELECT _v.register_patch('255-ease', NULL, NULL);

INSERT INTO screening_type (screening_type_id, description, overall_score_maximum)
SELECT 'PHQ2_BRANCHING', 'PHQ-2 Branching to PHQ-9', 27
WHERE NOT EXISTS (SELECT 1 FROM screening_type WHERE screening_type_id = 'PHQ2_BRANCHING');

INSERT INTO screening_type (screening_type_id, description, overall_score_maximum)
SELECT 'PROMIS_8A', 'PROMIS Satisfaction with Social Roles and Activities 8a', 40
WHERE NOT EXISTS (SELECT 1 FROM screening_type WHERE screening_type_id = 'PROMIS_8A');

DO $$
DECLARE
  v_institution_id CONSTANT TEXT := 'COBALT_EASE';
  v_created_by_email_address CONSTANT TEXT := 'admin@cobaltinnovations.org';
  v_created_by_account_id UUID;
  v_staff_account_id UUID := uuid_generate_v4();

  v_intake_screening_id UUID := uuid_generate_v4();
  v_intake_screening_version_id UUID := uuid_generate_v4();
  v_intake_screening_flow_id UUID := uuid_generate_v4();
  v_intake_screening_flow_version_id UUID := uuid_generate_v4();
  v_intake_prompt_id UUID := uuid_generate_v4();

  v_intake_q1_id UUID := uuid_generate_v4();
  v_intake_q2_id UUID := uuid_generate_v4();
  v_intake_q3_id UUID := uuid_generate_v4();
  v_intake_q4_id UUID := uuid_generate_v4();
  v_intake_q5_id UUID := uuid_generate_v4();
  v_intake_q6_id UUID := uuid_generate_v4();

  v_phq_screening_id UUID := uuid_generate_v4();
  v_phq_screening_version_id UUID := uuid_generate_v4();
  v_phq_prompt_id UUID := uuid_generate_v4();

  v_promis_screening_id UUID := uuid_generate_v4();
  v_promis_screening_version_id UUID := uuid_generate_v4();
  v_promis_prompt_id UUID := uuid_generate_v4();

  v_clinical_screening_flow_id UUID := uuid_generate_v4();
  v_clinical_screening_flow_version_id UUID := uuid_generate_v4();
  v_clinical_completion_prompt_id UUID := uuid_generate_v4();

  v_epic_department_id UUID := uuid_generate_v4();
  v_appointment_type_id UUID := uuid_generate_v4();
  v_provider_id UUID := uuid_generate_v4();
  v_provider_clinic_id UUID := uuid_generate_v4();
  v_clinic_id UUID := uuid_generate_v4();
  v_morning_logical_availability_id UUID := uuid_generate_v4();
  v_afternoon_logical_availability_id UUID := uuid_generate_v4();

  v_intake_scoring_function TEXT := $jscode$
const allQuestionsAnswered = input.answeredScreeningQuestionCount === 6;

const question1 = input.screeningQuestionsWithAnswerOptions[0].screeningQuestion;
const question4 = input.screeningQuestionsWithAnswerOptions[3].screeningQuestion;
const question5 = input.screeningQuestionsWithAnswerOptions[4].screeningQuestion;

function selectedScore(screeningQuestionId) {
  const answerIds = input.screeningAnswerIdsByScreeningQuestionId[screeningQuestionId] || [];
  if (answerIds.length === 0)
    return 0;

  return input.screeningAnswerOptionsByScreeningAnswerId[answerIds[0]].score;
}

const question1Score = selectedScore(question1.screeningQuestionId);
const question4Score = selectedScore(question4.screeningQuestionId);
const question5Score = selectedScore(question5.screeningQuestionId);

output.completed = allQuestionsAnswered
  || (input.answeredScreeningQuestionCount >= 1 && question1Score > 0)
  || (input.answeredScreeningQuestionCount >= 4 && question4Score > 0)
  || (input.answeredScreeningQuestionCount >= 5 && question5Score > 0);

output.score = { overallScore: 0 };

input.screeningAnswers.forEach(function(screeningAnswer) {
  const screeningAnswerOption = input.screeningAnswerOptionsByScreeningAnswerId[screeningAnswer.screeningAnswerId];
  output.score.overallScore += screeningAnswerOption.score;
});

output.belowScoringThreshold = output.score.overallScore < 1;
$jscode$;

  v_intake_orchestration_function TEXT := $jscode$
output.crisisIndicated = false;
output.completed = false;
output.nextScreeningId = null;

const easeIntake = input.screeningSessionScreenings[0];

if (input.screeningSessionScreenings.length !== 1) {
  throw "There is an unexpected number of screening session screenings";
}

if (easeIntake.completed) {
  output.completed = true;
}
$jscode$;

  v_intake_destination_function TEXT := $jscode$
output.screeningSessionDestinationId = null;
output.context = {};

const easeIntake = input.screeningSessionScreenings[0];
const easeIntakeIneligible = easeIntake.scoreAsObject.overallScore > 0;

if (input.screeningSession.completed) {
  if (easeIntakeIneligible) {
    output.screeningSessionDestinationId = input.selfAdministered ? 'IC_PATIENT_SCREENING_SESSION_RESULTS' : 'IC_MHIC_SCREENING_SESSION_RESULTS';
  } else {
    output.screeningSessionDestinationId = input.selfAdministered ? 'IC_PATIENT_CLINICAL_SCREENING' : 'IC_MHIC_CLINICAL_SCREENING';
  }

  output.context = {
    patientOrderId: input.additionalContext.patientOrderId
  };
}
$jscode$;

  v_phq_scoring_function TEXT := $jscode$
function scoreAt(index) {
  const question = input.screeningQuestionsWithAnswerOptions[index].screeningQuestion;
  const answerIds = input.screeningAnswerIdsByScreeningQuestionId[question.screeningQuestionId] || [];
  if (answerIds.length === 0)
    return 0;

  return input.screeningAnswerOptionsByScreeningAnswerId[answerIds[0]].score;
}

const phq2Score = scoreAt(0) + scoreAt(1);
let overallScore = phq2Score;

for (let index = 2; index < 9; ++index)
  overallScore += scoreAt(index);

output.completed = (input.answeredScreeningQuestionCount >= 2 && phq2Score <= 2) || input.answeredScreeningQuestionCount === 9;
output.score = { overallScore };
output.belowScoringThreshold = overallScore < 1;
$jscode$;

  v_promis_scoring_function TEXT := $jscode$
output.completed = input.answeredScreeningQuestionCount === 8;
output.score = { overallScore: 0 };

input.screeningAnswers.forEach(function(screeningAnswer) {
  const screeningAnswerOption = input.screeningAnswerOptionsByScreeningAnswerId[screeningAnswer.screeningAnswerId];
  output.score.overallScore += screeningAnswerOption.score;
});

output.belowScoringThreshold = false;
$jscode$;

  v_clinical_orchestration_function TEXT := $jscode$
output.crisisIndicated = false;
output.completed = false;
output.nextScreeningId = null;

const phqBranching = input.screeningSessionScreenings[0];
const promis = input.screeningSessionScreenings.length > 1 ? input.screeningSessionScreenings[1] : null;
const phqQuestions = input.screeningResultsByScreeningSessionScreeningId[phqBranching.screeningSessionScreeningId] || [];
const phqQuestion9 = phqQuestions.length > 8 ? phqQuestions[8] : null;
const phqQuestion9Response = phqQuestion9 && phqQuestion9.screeningResponses.length > 0 ? phqQuestion9.screeningResponses[0] : null;

if (phqQuestion9Response && phqQuestion9Response.screeningAnswerOption.score > 0) {
  output.crisisIndicated = true;
}

if (input.screeningSessionScreenings.length === 1) {
  if (phqBranching.completed) {
    output.nextScreeningId = input.screeningsByName['EASE PROMIS Social Roles 8a'].screeningId;
  }
} else if (input.screeningSessionScreenings.length === 2) {
  if (promis.completed) {
    output.completed = true;
  }
} else {
  throw "There is an unexpected number of screening session screenings";
}
$jscode$;

  v_clinical_destination_function TEXT := $jscode$
output.screeningSessionDestinationId = null;
output.context = {};

if (input.screeningSession.completed) {
  output.screeningSessionDestinationId = input.selfAdministered ? 'IC_PATIENT_SCREENING_SESSION_RESULTS' : 'IC_MHIC_SCREENING_SESSION_RESULTS';
  output.context = {
    patientOrderId: input.additionalContext.patientOrderId,
    screeningSessionId: input.screeningSession.screeningSessionId
  };
}
$jscode$;

  v_clinical_results_function TEXT := $jscode$
output.supportRoleRecommendations = [];
output.recommendedTagIds = [];
output.integratedCareTriages = [];

const phqBranching = input.screeningSessionScreenings[0];
const phqQuestions = input.screeningResultsByScreeningSessionScreeningId[phqBranching.screeningSessionScreeningId] || [];
const phqQuestion9 = phqQuestions.length > 8 ? phqQuestions[8] : null;
const phqQuestion9Response = phqQuestion9 && phqQuestion9.screeningResponses.length > 0 ? phqQuestion9.screeningResponses[0] : null;

output.integratedCareTriages.push({
  patientOrderFocusTypeId: phqQuestion9Response && phqQuestion9Response.screeningAnswerOption.score > 0 ? "CRISIS_CARE" : "EVALUATION",
  patientOrderCareTypeId: "COLLABORATIVE",
  reason: "EASE self-scheduling pilot assessment completed; patient may self-schedule with EASE Clinic."
});

output.integratedCareTriagedCareTypeId = "COLLABORATIVE";
$jscode$;
BEGIN
  SELECT account.account_id
  INTO v_created_by_account_id
  FROM account
  WHERE LOWER(TRIM(account.email_address)) = LOWER(TRIM(v_created_by_email_address))
  ORDER BY account.created
  LIMIT 1;

  IF v_created_by_account_id IS NULL THEN
    RAISE EXCEPTION 'Account with email "%" was not found', v_created_by_email_address;
  END IF;

  INSERT INTO institution (
    institution_id,
    name,
    metadata,
    anonymous_enabled,
    email_enabled,
    email_signup_enabled,
    immediate_access_enabled,
    contact_us_enabled,
    integrated_care_enabled,
    integrated_care_phone_number,
    clinical_support_phone_number,
    integrated_care_availability_description,
    integrated_care_outreach_followup_day_offset,
    integrated_care_sent_resources_followup_day_offset,
    integrated_care_sent_resources_followup_week_offset,
    integrated_care_program_name,
    integrated_care_primary_care_name,
    integrated_care_clinical_report_disclaimer,
    epic_patient_unique_id_type,
    epic_patient_unique_id_system,
    integrated_care_order_import_start_time_window,
    integrated_care_order_import_end_time_window,
    integrated_care_patient_demographics_required,
    integrated_care_patient_care_preference_visible,
    integrated_care_call_center_name,
    integrated_care_mhp_triage_overview_override,
    integrated_care_booking_insurance_requirements,
    landing_page_tagline_override,
    integrated_care_patient_intro_override
  ) VALUES (
    v_institution_id,
    'EASE Clinic',
    '{"way2HealthIncidentTrackingConfigs": []}'::jsonb,
    TRUE,
    FALSE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    '+12157466701',
    '+12157466701',
    'Monday through Friday, 8:30 AM to 4:30 PM',
    4,
    5,
    4,
    'EASE Clinic',
    'Hall Mercer Psychiatry',
    'TODO: disclaimer',
    'UID',
    'urn:oid:1.3.6.1.4.1.22812.19.44324.0',
    '8:00',
    '21:00',
    TRUE,
    FALSE,
    'Psychiatry Department Call Center',
    'Based on your responses, <strong>you are eligible to self-schedule an EASE Clinic consultation</strong>.',
    $html$
      <p className="mb-5">
        Staff will confirm insurance prior to appointment, and you may be contacted if any issues are identified.
        The Department of Psychiatry and/or Cobalt are not responsible for any fees that may arise from incomplete
        or inaccurate insurance information.
      </p>
    $html$,
    'Sign in to determine your eligibility to self-schedule',
    'Thank you for your interest in this pilot. Follow the steps below to determine if you are eligible to self-schedule with EASE Clinic.'
  );

  INSERT INTO institution_url (
    institution_id,
    url,
    hostname,
    preferred,
    user_experience_type_id
  ) VALUES (
    v_institution_id,
    'http://ease.cobalt.local:3000',
    'ease.cobalt.local',
    TRUE,
    'PATIENT'
  );

  INSERT INTO institution_url (
    institution_id,
    url,
    hostname,
    preferred,
    user_experience_type_id
  ) VALUES (
    v_institution_id,
    'http://ease-staff.cobalt.local:3000',
    'ease-staff.cobalt.local',
    TRUE,
    'STAFF'
  );

  INSERT INTO account (
    account_id,
    role_id,
    institution_id,
    account_source_id,
    email_address,
    display_name
  ) VALUES (
    uuid_generate_v4(),
    'SERVICE_ACCOUNT',
    v_institution_id,
    'EMAIL_PASSWORD',
    'service+ease@cobaltinnovations.org',
    'EASE Clinic Service Account'
  );

  -- password - test1234
  INSERT INTO account (
    account_id,
    role_id,
    institution_id,
    account_source_id,
    email_address,
    password,
    first_name,
    last_name,
    display_name
  ) VALUES (
    v_staff_account_id,
    'MHIC',
    v_institution_id,
    'EMAIL_PASSWORD',
    'maa+ease@xmog.com',
    '$2a$10$M2tPoJ8eQr55OW4iOfpbBOpgqFWt0LxnvVBnW1a/1LhKNA6SuUN42',
    'EASE',
    'Staff',
    'EASE Staff'
  );

  INSERT INTO account_capability (
    account_id,
    account_capability_type_id
  ) VALUES
    (v_staff_account_id, 'MHIC_ADMIN'),
    (v_staff_account_id, 'MHIC_ORDER_SERVICER'),
    (v_staff_account_id, 'MHIC_RESOURCE_MANAGER');

  INSERT INTO institution_account_source (
    institution_account_source_id,
    institution_id,
    account_source_id,
    account_source_display_style_id,
    display_order,
    authentication_description,
    visible
  ) VALUES (
    uuid_generate_v4(),
    v_institution_id,
    'EMAIL_PASSWORD',
    'PRIMARY',
    1,
    'Sign In With Email',
    TRUE
  );

  INSERT INTO institution_account_source (
    institution_account_source_id,
    institution_id,
    account_source_id,
    account_source_display_style_id,
    display_order,
    authentication_description,
    requires_user_experience_type_id,
    visible
  ) VALUES (
    uuid_generate_v4(),
    v_institution_id,
    'ANONYMOUS',
    'SECONDARY',
    2,
    'Continue Anonymously',
    'PATIENT',
    TRUE
  );

  INSERT INTO institution_patient_order_referral_source (institution_id, patient_order_referral_source_id)
  VALUES (v_institution_id, 'SELF');

  INSERT INTO institution_feature (
    institution_feature_id,
    institution_id,
    feature_id,
    nav_description,
    description,
    display_order,
    nav_visible,
    landing_page_visible
  ) VALUES (
    uuid_generate_v4(),
    v_institution_id,
    'MHP',
    'Connect to a Mental Health Provider',
    'Please select a timeslot below to schedule an appointment with an EASE Clinic clinician. These timeslots are for 60-minute initial appointments only.',
    1,
    TRUE,
    TRUE
  );

  INSERT INTO appointment_reason (
    appointment_reason_id,
    appointment_reason_type_id,
    institution_id,
    description,
    color,
    display_order
  ) VALUES (
    uuid_generate_v4(),
    'NOT_SPECIFIED',
    v_institution_id,
    'Not Specified',
    '#53123A',
    1
  );

  EXECUTE format('CREATE SEQUENCE IF NOT EXISTS %I START 1', LOWER('po_reference_number_seq_' || v_institution_id));

  INSERT INTO epic_department (
    epic_department_id,
    institution_id,
    department_id,
    department_id_type,
    name
  ) VALUES (
    v_epic_department_id,
    v_institution_id,
    '1366',
    'INTERNAL',
    'Hall Mercer Psychiatry'
  );

  INSERT INTO clinic (
    clinic_id,
    description,
    treatment_description,
    institution_id
  ) VALUES (
    v_clinic_id,
    'EASE Clinic',
    'Rapid mental health support consultations for UPHS employees.',
    v_institution_id
  );

  INSERT INTO appointment_type (
    appointment_type_id,
    acuity_appointment_type_id,
    name,
    description,
    duration_in_minutes,
    visit_type_id,
    scheduling_system_id,
    epic_visit_type_id,
    epic_visit_type_id_type
  ) VALUES (
    v_appointment_type_id,
    NULL,
    'EASE Clinic Consultation',
    '60-minute initial EASE Clinic consultation.',
    60,
    'INITIAL',
    'COBALT',
    NULL,
    NULL
  );

  INSERT INTO provider (
    provider_id,
    institution_id,
    name,
    title,
    email_address,
    locale,
    time_zone,
    entity,
    clinic,
    specialty,
    active,
    scheduling_system_id,
    epic_provider_id,
    epic_provider_id_type,
    system_affinity_id,
    url_name,
    description
  ) VALUES (
    v_provider_id,
    v_institution_id,
    'Dr. Olga Barg',
    'Psychiatrist',
    'olga.barg@example.com',
    'en-US',
    'America/New_York',
    'EASE Clinic',
    'EASE Clinic',
    'Psychiatry',
    TRUE,
    'COBALT',
    NULL,
    NULL,
    'COBALT',
    'olga-barg',
    'Dr. Olga Barg provides EASE Clinic consultations for UPHS employees.'
  );

  INSERT INTO provider_clinic (
    provider_clinic_id,
    provider_id,
    clinic_id,
    primary_clinic
  ) VALUES (
    v_provider_clinic_id,
    v_provider_id,
    v_clinic_id,
    TRUE
  );

  INSERT INTO provider_support_role (provider_id, support_role_id)
  VALUES (v_provider_id, 'MHP');

  INSERT INTO provider_appointment_type (
    provider_id,
    appointment_type_id,
    display_order
  ) VALUES (
    v_provider_id,
    v_appointment_type_id,
    1
  );

  INSERT INTO provider_epic_department (
    provider_id,
    epic_department_id,
    display_order
  ) VALUES (
    v_provider_id,
    v_epic_department_id,
    1
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
    v_morning_logical_availability_id,
    v_provider_id,
    TIMESTAMP '2026-05-07 09:00:00',
    TIMESTAMP '2099-12-31 12:00:00',
    'OPEN',
    'DAILY',
    FALSE,
    FALSE,
    FALSE,
    FALSE,
    TRUE,
    FALSE,
    FALSE,
    v_created_by_account_id,
    v_created_by_account_id
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
    v_afternoon_logical_availability_id,
    v_provider_id,
    TIMESTAMP '2026-05-07 13:00:00',
    TIMESTAMP '2099-12-31 16:00:00',
    'OPEN',
    'DAILY',
    FALSE,
    FALSE,
    FALSE,
    FALSE,
    TRUE,
    FALSE,
    FALSE,
    v_created_by_account_id,
    v_created_by_account_id
  );

  INSERT INTO logical_availability_appointment_type (
    logical_availability_id,
    appointment_type_id
  ) VALUES (
    v_morning_logical_availability_id,
    v_appointment_type_id
  );

  INSERT INTO logical_availability_appointment_type (
    logical_availability_id,
    appointment_type_id
  ) VALUES (
    v_afternoon_logical_availability_id,
    v_appointment_type_id
  );

  INSERT INTO screening (
    screening_id,
    name,
    created_by_account_id
  ) VALUES (
    v_intake_screening_id,
    'EASE Eligibility',
    v_created_by_account_id
  );

  INSERT INTO screening_version (
    screening_version_id,
    screening_id,
    screening_type_id,
    created_by_account_id,
    version_number,
    scoring_function
  ) VALUES (
    v_intake_screening_version_id,
    v_intake_screening_id,
    'IC_INTAKE',
    v_created_by_account_id,
    1,
    v_intake_scoring_function
  );

  UPDATE screening
  SET active_screening_version_id = v_intake_screening_version_id
  WHERE screening_id = v_intake_screening_id;

  INSERT INTO screening_institution (screening_id, institution_id)
  VALUES (v_intake_screening_id, v_institution_id);

  INSERT INTO screening_confirmation_prompt (
    screening_confirmation_prompt_id,
    screening_image_id,
    text,
    action_text
  ) VALUES (
    v_intake_prompt_id,
    'SAFETY',
    'To get started, we will ask you some qualifying questions to determine your eligibility for EASE Clinic.',
    'Let''s Go'
  );

  INSERT INTO screening_question (
    screening_question_id,
    screening_version_id,
    pre_question_screening_confirmation_prompt_id,
    screening_answer_format_id,
    intro_text,
    footer_text,
    question_text,
    minimum_answer_count,
    maximum_answer_count,
    display_order
  ) VALUES (
    v_intake_q1_id,
    v_intake_screening_version_id,
    v_intake_prompt_id,
    'SINGLE_SELECT',
    'Employment status',
    NULL,
    'This pilot program is currently only open to UPHS employees. Are you a UPHS employee?',
    1,
    1,
    1
  );

  INSERT INTO screening_answer_option (screening_question_id, answer_option_text, score, display_order)
  VALUES
    (v_intake_q1_id, 'Yes', 0, 1),
    (v_intake_q1_id, 'No', 1, 2);

  INSERT INTO screening_question (
    screening_question_id,
    screening_version_id,
    screening_answer_format_id,
    intro_text,
    footer_text,
    question_text,
    minimum_answer_count,
    maximum_answer_count,
    display_order
  ) VALUES (
    v_intake_q2_id,
    v_intake_screening_version_id,
    'SINGLE_SELECT',
    'Services provided in EASE',
    NULL,
    $html$
      These are the services provided in EASE:
      <ul class="mt-3">
        <li class="h4 mb-2">Quick evaluation of your current symptoms to determine the best immediate steps for your safety and well-being.</li>
        <li class="h4 mb-2">Immediate psychiatric intervention by a psychiatrist to stabilize a crisis, including starting or adjusting medications if needed.</li>
        <li class="h4 mb-2">Brief psychotherapeutic support to help you manage acute distress right now.</li>
        <li class="h4 mb-2">Warm hand-off or facilitated scheduling to longer-term psychiatric or therapeutic care.</li>
        <li class="h4 mb-2">Follow-up planning until bridged to ongoing care, if needed.</li>
      </ul>
    $html$,
    1,
    1,
    2
  );

  INSERT INTO screening_answer_option (screening_question_id, answer_option_text, score, display_order)
  VALUES (v_intake_q2_id, 'I understand', 0, 1);

  INSERT INTO screening_question (
    screening_question_id,
    screening_version_id,
    screening_answer_format_id,
    intro_text,
    footer_text,
    question_text,
    minimum_answer_count,
    maximum_answer_count,
    display_order
  ) VALUES (
    v_intake_q3_id,
    v_intake_screening_version_id,
    'SINGLE_SELECT',
    'Additional service provisions',
    NULL,
    'This service is typically not appropriate for administrative paperwork (FMLA, Disability, Workers'' Comp, or ESA requests), legal/forensic evaluations, or neuropsychological testing (for example, ADHD).',
    1,
    1,
    3
  );

  INSERT INTO screening_answer_option (screening_question_id, answer_option_text, score, display_order)
  VALUES (v_intake_q3_id, 'I understand', 0, 1);

  INSERT INTO screening_question (
    screening_question_id,
    screening_version_id,
    screening_answer_format_id,
    intro_text,
    footer_text,
    question_text,
    minimum_answer_count,
    maximum_answer_count,
    display_order
  ) VALUES (
    v_intake_q4_id,
    v_intake_screening_version_id,
    'SINGLE_SELECT',
    'Health insurance',
    NULL,
    'How do you currently get your health insurance?',
    1,
    1,
    4
  );

  INSERT INTO screening_answer_option (screening_question_id, answer_option_text, score, display_order)
  VALUES
    (v_intake_q4_id, 'Through UPHS', 0, 1),
    (v_intake_q4_id, 'Through a family member''s employer', 1, 2),
    (v_intake_q4_id, 'Through the marketplace or privately purchased', 1, 3),
    (v_intake_q4_id, 'I do not currently have health insurance', 1, 4);

  INSERT INTO screening_question (
    screening_question_id,
    screening_version_id,
    screening_answer_format_id,
    intro_text,
    footer_text,
    question_text,
    minimum_answer_count,
    maximum_answer_count,
    display_order
  ) VALUES (
    v_intake_q5_id,
    v_intake_screening_version_id,
    'SINGLE_SELECT',
    'Accepted insurance',
    NULL,
    'Select your current behavioral health insurance plan from the list below.',
    1,
    1,
    5
  );

  INSERT INTO screening_answer_option (screening_question_id, answer_option_text, score, display_order)
  VALUES
    (v_intake_q5_id, 'Aetna', 0, 1),
    (v_intake_q5_id, 'Independence Personal Choice', 0, 2),
    (v_intake_q5_id, 'Quest Behavioral Health (listed on the back of your PennCare PPO card)', 0, 3),
    (v_intake_q5_id, 'Keystone Health Plan East', 0, 4),
    (v_intake_q5_id, 'Blue Cross Blue Shield', 0, 5),
    (v_intake_q5_id, 'Horizon', 0, 6),
    (v_intake_q5_id, 'Tricare', 0, 7),
    (v_intake_q5_id, 'Medicaid (Philadelphia County)', 0, 8),
    (v_intake_q5_id, 'Other', 1, 9);

  INSERT INTO screening_question (
    screening_question_id,
    screening_version_id,
    screening_answer_format_id,
    intro_text,
    footer_text,
    question_text,
    minimum_answer_count,
    maximum_answer_count,
    display_order
  ) VALUES (
    v_intake_q6_id,
    v_intake_screening_version_id,
    'SINGLE_SELECT',
    'Insurance confirmation',
    NULL,
    'Staff will confirm insurance prior to appointment, and you may be contacted if any issues are identified.<br/><br/>The Department of Psychiatry and/or Cobalt are not responsible for any fees that may arise from incomplete or inaccurate insurance information.',
    1,
    1,
    6
  );

  INSERT INTO screening_answer_option (screening_question_id, answer_option_text, score, display_order)
  VALUES (v_intake_q6_id, 'I understand', 0, 1);

  INSERT INTO screening_flow (
    screening_flow_id,
    institution_id,
    screening_flow_type_id,
    name,
    created_by_account_id
  ) VALUES (
    v_intake_screening_flow_id,
    v_institution_id,
    'INTEGRATED_CARE_INTAKE',
    'EASE Eligibility',
    v_created_by_account_id
  );

  INSERT INTO screening_flow_version (
    screening_flow_version_id,
    screening_flow_id,
    initial_screening_id,
    pre_completion_screening_confirmation_prompt_id,
    phone_number_required,
    skippable,
    orchestration_function,
    results_function,
    destination_function,
    created_by_account_id
  ) VALUES (
    v_intake_screening_flow_version_id,
    v_intake_screening_flow_id,
    v_intake_screening_id,
    NULL,
    FALSE,
    FALSE,
    v_intake_orchestration_function,
    'console.log("Nothing to do for EASE eligibility results function.");',
    v_intake_destination_function,
    v_created_by_account_id
  );

  UPDATE screening_flow
  SET active_screening_flow_version_id = v_intake_screening_flow_version_id
  WHERE screening_flow_id = v_intake_screening_flow_id;

  UPDATE institution
  SET integrated_care_intake_screening_flow_id = v_intake_screening_flow_id
  WHERE institution_id = v_institution_id;

  INSERT INTO screening (
    screening_id,
    name,
    created_by_account_id
  ) VALUES (
    v_phq_screening_id,
    'EASE PHQ2 Branching',
    v_created_by_account_id
  );

  INSERT INTO screening_version (
    screening_version_id,
    screening_id,
    screening_type_id,
    created_by_account_id,
    version_number,
    scoring_function
  ) VALUES (
    v_phq_screening_version_id,
    v_phq_screening_id,
    'PHQ2_BRANCHING',
    v_created_by_account_id,
    1,
    v_phq_scoring_function
  );

  UPDATE screening
  SET active_screening_version_id = v_phq_screening_version_id
  WHERE screening_id = v_phq_screening_id;

  INSERT INTO screening_institution (screening_id, institution_id)
  VALUES (v_phq_screening_id, v_institution_id);

  INSERT INTO screening_confirmation_prompt (
    screening_confirmation_prompt_id,
    screening_image_id,
    text,
    action_text
  ) VALUES (
    v_phq_prompt_id,
    'FEELING_RECENTLY',
    'First, we will ask about depression symptoms over the last 2 weeks.',
    'Keep Going'
  );

  INSERT INTO screening_question (
    screening_question_id,
    screening_version_id,
    pre_question_screening_confirmation_prompt_id,
    screening_answer_format_id,
    intro_text,
    question_text,
    minimum_answer_count,
    maximum_answer_count,
    display_order
  )
  SELECT
    uuid_generate_v4(),
    v_phq_screening_version_id,
    CASE WHEN phq_questions.display_order = 1 THEN v_phq_prompt_id ELSE NULL END,
    'SINGLE_SELECT',
    'Over the last 2 weeks, how often have you been bothered by any of the following problems?',
    phq_questions.question_text,
    1,
    1,
    phq_questions.display_order
  FROM (VALUES
    (1, 'Little interest or pleasure in doing things.'),
    (2, 'Feeling down, depressed, or hopeless.'),
    (3, 'Trouble falling or staying asleep, or sleeping too much.'),
    (4, 'Feeling tired or having little energy.'),
    (5, 'Poor appetite or overeating.'),
    (6, 'Feeling bad about yourself, or that you are a failure or have let yourself or your family down.'),
    (7, 'Trouble concentrating on things, such as reading the newspaper or watching television.'),
    (8, 'Moving or speaking so slowly that other people could have noticed. Or the opposite: being so fidgety or restless that you have been moving around a lot more than usual.'),
    (9, 'Thoughts that you would be better off dead, or of hurting yourself in some way.')
  ) AS phq_questions(display_order, question_text);

  INSERT INTO screening_answer_option (
    screening_question_id,
    answer_option_text,
    score,
    indicates_crisis,
    display_order
  )
  SELECT
    screening_question.screening_question_id,
    answer_options.answer_option_text,
    answer_options.score,
    screening_question.display_order = 9 AND answer_options.score > 0,
    answer_options.display_order
  FROM screening_question
  CROSS JOIN (VALUES
    ('Not at all', 0, 1),
    ('Several days', 1, 2),
    ('More than half the days', 2, 3),
    ('Nearly every day', 3, 4)
  ) AS answer_options(answer_option_text, score, display_order)
  WHERE screening_question.screening_version_id = v_phq_screening_version_id;

  INSERT INTO screening (
    screening_id,
    name,
    created_by_account_id
  ) VALUES (
    v_promis_screening_id,
    'EASE PROMIS Social Roles 8a',
    v_created_by_account_id
  );

  INSERT INTO screening_version (
    screening_version_id,
    screening_id,
    screening_type_id,
    created_by_account_id,
    version_number,
    scoring_function
  ) VALUES (
    v_promis_screening_version_id,
    v_promis_screening_id,
    'PROMIS_8A',
    v_created_by_account_id,
    1,
    v_promis_scoring_function
  );

  UPDATE screening
  SET active_screening_version_id = v_promis_screening_version_id
  WHERE screening_id = v_promis_screening_id;

  INSERT INTO screening_institution (screening_id, institution_id)
  VALUES (v_promis_screening_id, v_institution_id);

  INSERT INTO screening_confirmation_prompt (
    screening_confirmation_prompt_id,
    screening_image_id,
    text,
    action_text
  ) VALUES (
    v_promis_prompt_id,
    'GOALS',
    'Next, we will ask about satisfaction with participation in social roles and activities over the past 7 days.',
    'Keep Going'
  );

  INSERT INTO screening_question (
    screening_question_id,
    screening_version_id,
    pre_question_screening_confirmation_prompt_id,
    screening_answer_format_id,
    intro_text,
    question_text,
    minimum_answer_count,
    maximum_answer_count,
    display_order
  )
  SELECT
    uuid_generate_v4(),
    v_promis_screening_version_id,
    CASE WHEN promis_questions.display_order = 1 THEN v_promis_prompt_id ELSE NULL END,
    'SINGLE_SELECT',
    'In the past 7 days...',
    promis_questions.question_text,
    1,
    1,
    promis_questions.display_order
  FROM (VALUES
    (1, 'I am satisfied with how much work I can do (include work at home).'),
    (2, 'I am satisfied with my ability to work (include work at home).'),
    (3, 'I am satisfied with my ability to do regular personal and household responsibilities.'),
    (4, 'I am satisfied with my ability to perform my daily routines.'),
    (5, 'I am satisfied with my ability to meet the needs of those who depend on me.'),
    (6, 'I am satisfied with my ability to do household chores/tasks.'),
    (7, 'I am satisfied with my ability to do things for my family.'),
    (8, 'I am satisfied with the amount of time I spend performing my daily routines.')
  ) AS promis_questions(display_order, question_text);

  INSERT INTO screening_answer_option (
    screening_question_id,
    answer_option_text,
    score,
    display_order
  )
  SELECT
    screening_question.screening_question_id,
    answer_options.answer_option_text,
    answer_options.score,
    answer_options.display_order
  FROM screening_question
  CROSS JOIN (VALUES
    ('Not at all satisfied', 1, 1),
    ('A little bit satisfied', 2, 2),
    ('Somewhat satisfied', 3, 3),
    ('Quite satisfied', 4, 4),
    ('Very satisfied', 5, 5)
  ) AS answer_options(answer_option_text, score, display_order)
  WHERE screening_question.screening_version_id = v_promis_screening_version_id;

  INSERT INTO screening_flow (
    screening_flow_id,
    institution_id,
    screening_flow_type_id,
    name,
    created_by_account_id
  ) VALUES (
    v_clinical_screening_flow_id,
    v_institution_id,
    'INTEGRATED_CARE',
    'EASE Clinical Screening Flow',
    v_created_by_account_id
  );

  INSERT INTO screening_confirmation_prompt (
    screening_confirmation_prompt_id,
    screening_image_id,
    text,
    action_text
  ) VALUES (
    v_clinical_completion_prompt_id,
    NULL,
    'Almost done...please confirm you are happy with your answers before we mark your assessment complete.',
    'OK, mark it complete!'
  );

  INSERT INTO screening_flow_version (
    screening_flow_version_id,
    screening_flow_id,
    initial_screening_id,
    pre_completion_screening_confirmation_prompt_id,
    phone_number_required,
    skippable,
    orchestration_function,
    results_function,
    destination_function,
    created_by_account_id
  ) VALUES (
    v_clinical_screening_flow_version_id,
    v_clinical_screening_flow_id,
    v_phq_screening_id,
    v_clinical_completion_prompt_id,
    FALSE,
    FALSE,
    v_clinical_orchestration_function,
    v_clinical_results_function,
    v_clinical_destination_function,
    v_created_by_account_id
  );

  UPDATE screening_flow
  SET active_screening_flow_version_id = v_clinical_screening_flow_version_id
  WHERE screening_flow_id = v_clinical_screening_flow_id;

  INSERT INTO screening_flow_version_screening_type (screening_flow_version_id, screening_type_id)
  VALUES
    (v_clinical_screening_flow_version_id, 'PHQ2_BRANCHING'),
    (v_clinical_screening_flow_version_id, 'PROMIS_8A');

  UPDATE institution
  SET integrated_care_screening_flow_id = v_clinical_screening_flow_id
  WHERE institution_id = v_institution_id;
END $$;

COMMIT;
