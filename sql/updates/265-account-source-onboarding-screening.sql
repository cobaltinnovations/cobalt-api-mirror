BEGIN;
SELECT _v.register_patch('265-account-source-onboarding-screening', ARRAY['264-provider-institution-referrer'], NULL);

ALTER TABLE account_source
ADD COLUMN use_alternative_onboarding_screening BOOLEAN NOT NULL DEFAULT FALSE;

-- Initial enterprise configuration; future treatment changes are data updates.
UPDATE account_source
SET use_alternative_onboarding_screening = TRUE
WHERE account_source_id IN ('PENN_SSO', 'PENN_KEY_SSO');

COMMIT;
