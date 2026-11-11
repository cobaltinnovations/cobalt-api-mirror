BEGIN;
SELECT _v.register_patch(
  '292-behavior-bridge-sleep-wrap-up-evaluation-report',
  ARRAY['291-behavior-bridge-sleep-wrap-up-screening'],
  NULL
);

-- Deploy the API first so the report type enum is recognized by every instance.
INSERT INTO report_type (report_type_id, description, display_order)
VALUES ('COURSE_BUILDING_HEALTHY_SLEEP_WRAP_UP', 'Analytics - Building Healthy Sleep Wrap Up', 125);

COMMIT;
