BEGIN;
SELECT _v.register_patch(
	'264-provider-institution-referrer',
	ARRAY['261-provider-booking-database'],
	NULL
);

-- Referral-backed provider profiles intentionally do not participate in the
-- provider's native scheduling workflow. Keep the legacy defaults for existing
-- insert callers, while permitting these profiles to opt out explicitly.
ALTER TABLE provider
	ALTER COLUMN email_address DROP NOT NULL,
	ALTER COLUMN scheduling_system_id DROP NOT NULL,
	ALTER COLUMN videoconference_platform_id DROP NOT NULL;

-- A referral-backed provider is presentation data owned by the source
-- institution. Its booking action delegates to an existing institution
-- referrer, whose screening flow and destination remain authoritative.
CREATE TABLE IF NOT EXISTS provider_institution_referrer (
	provider_id UUID PRIMARY KEY REFERENCES provider(provider_id) ON DELETE CASCADE,
	institution_referrer_id UUID NOT NULL UNIQUE REFERENCES institution_referrer(institution_referrer_id),
	appointment_modality_id TEXT NOT NULL,
	created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	CONSTRAINT provider_institution_referrer_appointment_modality_id_check
		CHECK (appointment_modality_id IN ('PHONE', 'VIRTUAL', 'IN_PERSON'))
);

CREATE OR REPLACE FUNCTION validate_provider_institution_referrer()
RETURNS TRIGGER AS $$
BEGIN
	IF NOT EXISTS (
		SELECT 1
		FROM provider
		JOIN institution_referrer
			ON institution_referrer.institution_referrer_id=NEW.institution_referrer_id
		WHERE provider.provider_id=NEW.provider_id
		AND provider.institution_id=institution_referrer.from_institution_id
		AND institution_referrer.intake_screening_flow_id IS NOT NULL
	) THEN
		RAISE EXCEPTION 'Referral-backed providers must belong to the referrer source institution and the referrer must have an intake screening flow.';
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS validate_provider_institution_referrer ON provider_institution_referrer;
CREATE TRIGGER validate_provider_institution_referrer
BEFORE INSERT OR UPDATE OF provider_id, institution_referrer_id ON provider_institution_referrer
FOR EACH ROW
EXECUTE FUNCTION validate_provider_institution_referrer();

DO $$
BEGIN
	IF NOT EXISTS (
		SELECT 1
		FROM pg_trigger
		WHERE tgrelid='provider_institution_referrer'::REGCLASS
		AND tgname='set_last_updated'
	) THEN
		CREATE TRIGGER set_last_updated
		BEFORE INSERT OR UPDATE ON provider_institution_referrer
		FOR EACH ROW
		EXECUTE PROCEDURE set_last_updated();
	END IF;
END $$;

COMMIT;
