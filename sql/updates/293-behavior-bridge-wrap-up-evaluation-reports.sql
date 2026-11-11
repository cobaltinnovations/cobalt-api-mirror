BEGIN;
SELECT _v.register_patch(
  '290-behavior-bridge-wrap-up-evaluation-reports',
  ARRAY['289-behavior-bridge-wrap-up-screenings'],
  NULL
);

-- Deploy the API first so every instance recognizes both report type enum values.
INSERT INTO report_type (report_type_id, description, display_order)
VALUES
  ('COURSE_PARENTING_TEENS_WRAP_UP', 'Analytics - Parenting Teens Wrap Up', 123),
  ('COURSE_UNDERSTANDING_TRAUMA_WRAP_UP', 'Analytics - Understanding Trauma Wrap Up', 124);

COMMIT;
