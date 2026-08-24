BEGIN;
SELECT _v.register_patch('265-care-encounter-scheduled-messages', ARRAY['264-care-navigator-screening-contact'], NULL);

CREATE TABLE care_encounter_scheduled_message_type (
	care_encounter_scheduled_message_type_id VARCHAR PRIMARY KEY,
	description VARCHAR NOT NULL,
	display_order INTEGER NOT NULL
);

INSERT INTO care_encounter_scheduled_message_type (
	care_encounter_scheduled_message_type_id,
	description,
	display_order
) VALUES ('FOLLOW_UP', 'Follow-up', 1);

CREATE TABLE care_encounter_scheduled_message (
	care_encounter_scheduled_message_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
	care_encounter_id UUID NOT NULL REFERENCES care_encounter(care_encounter_id),
	care_encounter_scheduled_message_type_id VARCHAR NOT NULL
		REFERENCES care_encounter_scheduled_message_type(care_encounter_scheduled_message_type_id),
	scheduled_message_id UUID NOT NULL UNIQUE REFERENCES scheduled_message(scheduled_message_id),
	recipient_email_address VARCHAR NOT NULL,
	custom_email_text TEXT NOT NULL,
	email_subject TEXT NOT NULL,
	email_body TEXT NOT NULL,
	deleted BOOLEAN NOT NULL DEFAULT FALSE,
	deleted_at TIMESTAMPTZ,
	deleted_by_account_id UUID REFERENCES account(account_id),
	created_by_account_id UUID NOT NULL REFERENCES account(account_id),
	last_updated_by_account_id UUID NOT NULL REFERENCES account(account_id),
	created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	CONSTRAINT care_encounter_scheduled_message_custom_text_not_blank_check
		CHECK (NULLIF(BTRIM(custom_email_text), '') IS NOT NULL),
	CONSTRAINT care_encounter_scheduled_message_subject_not_blank_check
		CHECK (NULLIF(BTRIM(email_subject), '') IS NOT NULL),
	CONSTRAINT care_encounter_scheduled_message_body_not_blank_check
		CHECK (NULLIF(BTRIM(email_body), '') IS NOT NULL),
	CONSTRAINT care_encounter_scheduled_message_deleted_check CHECK (
		(deleted=FALSE AND deleted_at IS NULL AND deleted_by_account_id IS NULL)
		OR (deleted=TRUE AND deleted_at IS NOT NULL AND deleted_by_account_id IS NOT NULL)
	)
);

CREATE INDEX care_encounter_scheduled_message_encounter_created_idx
ON care_encounter_scheduled_message(care_encounter_id, created DESC,
	care_encounter_scheduled_message_id DESC);

ALTER TABLE care_encounter_note
ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN deleted_at TIMESTAMPTZ,
ADD COLUMN deleted_by_account_id UUID REFERENCES account(account_id),
ADD CONSTRAINT care_encounter_note_deleted_check CHECK (
	(deleted=FALSE AND deleted_at IS NULL AND deleted_by_account_id IS NULL)
	OR (deleted=TRUE AND deleted_at IS NOT NULL AND deleted_by_account_id IS NOT NULL)
);

CREATE TRIGGER set_last_updated
BEFORE INSERT OR UPDATE ON care_encounter_scheduled_message
FOR EACH ROW EXECUTE PROCEDURE set_last_updated();

CREATE TRIGGER care_encounter_scheduled_message_footprint
AFTER INSERT OR UPDATE OR DELETE ON care_encounter_scheduled_message
FOR EACH ROW EXECUTE PROCEDURE perform_footprint();

COMMIT;
