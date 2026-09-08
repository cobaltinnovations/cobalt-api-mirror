BEGIN;
SELECT _v.register_patch('266-account-source-onboarding-screening-treatment', ARRAY['265-account-source-onboarding-screening'], NULL);

ALTER TABLE account_source
ALTER COLUMN use_alternative_onboarding_screening DROP DEFAULT;

ALTER TABLE account_source
ALTER COLUMN use_alternative_onboarding_screening TYPE TEXT
USING CASE WHEN use_alternative_onboarding_screening THEN 'MODAL' ELSE 'DEFAULT' END;

ALTER TABLE account_source
RENAME COLUMN use_alternative_onboarding_screening TO onboarding_treatment_id;

ALTER TABLE account_source
ALTER COLUMN onboarding_treatment_id SET DEFAULT 'DEFAULT';

COMMENT ON COLUMN account_source.onboarding_treatment_id IS
'Onboarding presentation identifier. DEFAULT uses the existing UI; clients should fall back to it for unrecognized identifiers.';

COMMIT;
