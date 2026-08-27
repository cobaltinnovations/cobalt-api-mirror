BEGIN;
SELECT _v.register_patch('259-course-feedback-report', NULL, NULL);

-- Deploy the API before applying this patch so all instances recognize the new enum value.
INSERT INTO report_type (report_type_id, description, display_order)
VALUES ('COURSE_FEEDBACK', 'Analytics - Course Feedback', 122);

-- Mark the four existing production Behavior Bridge course-feedback questions semantically so
-- future wording changes do not make their answers disappear from the report.
UPDATE screening_question
SET metadata = COALESCE(metadata, '{}'::JSONB) || JSONB_BUILD_OBJECT(
  'reporting',
  CASE
    WHEN JSONB_TYPEOF(metadata->'reporting') = 'object' THEN metadata->'reporting'
    ELSE '{}'::JSONB
  END || '{"courseFeedback": true}'::JSONB
)
WHERE screening_question_id IN (
  '26249ac8-1efa-43e3-ba87-3957f58ff7f6',
  '42040513-626a-4f1b-8b01-0a5f02d3a505',
  '93d1a645-7e8a-4b6b-a09a-06f3a9962f68',
  'e246790c-5046-4dca-b628-edb0fdc57576'
);

COMMIT;
