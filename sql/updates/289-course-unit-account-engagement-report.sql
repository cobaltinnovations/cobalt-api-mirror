BEGIN;
SELECT _v.register_patch('289-course-unit-account-engagement-report', NULL, NULL);

-- Deploy the API before this patch so every instance recognizes the enum value.
INSERT INTO report_type (report_type_id, description, display_order)
VALUES ('COURSE_UNIT_ACCOUNT_ENGAGEMENT', 'Course Unit Engagement by Account', 123);

COMMIT;
