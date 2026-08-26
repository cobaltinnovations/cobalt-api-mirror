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

-- Follow-up content assumes that the current appointment was attended. If that
-- fact is corrected later, or any path makes the encounter terminal (including
-- the patient-cancellation trigger), pending delivery is no longer valid.
-- Preserve the Care Encounter message record for audit while canceling the
-- underlying scheduled message.
CREATE OR REPLACE FUNCTION cancel_pending_care_encounter_scheduled_messages(p_care_encounter_id UUID)
RETURNS VOID AS $$
	UPDATE scheduled_message
	SET scheduled_message_status_id='CANCELED',
		canceled_at=COALESCE(canceled_at, NOW())
	WHERE scheduled_message_status_id='PENDING'
	AND EXISTS (
		SELECT 1
		FROM care_encounter_scheduled_message
		WHERE care_encounter_scheduled_message.care_encounter_id=p_care_encounter_id
		AND care_encounter_scheduled_message.scheduled_message_id=scheduled_message.scheduled_message_id
	);
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION cancel_pending_messages_for_terminal_care_encounter()
RETURNS TRIGGER AS $$
BEGIN
	IF (OLD.care_encounter_status_id='OPEN' AND NEW.care_encounter_status_id<>'OPEN')
		OR (OLD.deleted=FALSE AND NEW.deleted=TRUE) THEN
		PERFORM cancel_pending_care_encounter_scheduled_messages(NEW.care_encounter_id);
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER cancel_pending_messages_for_terminal_care_encounter
AFTER UPDATE OF care_encounter_status_id, deleted ON care_encounter
FOR EACH ROW EXECUTE PROCEDURE cancel_pending_messages_for_terminal_care_encounter();

CREATE OR REPLACE FUNCTION cancel_pending_messages_for_invalidated_care_attendance()
RETURNS TRIGGER AS $$
BEGIN
	IF OLD.care_encounter_id IS NOT NULL
		AND OLD.attendance_status_id='ATTENDED'
		AND (NEW.attendance_status_id<>'ATTENDED'
			OR NEW.canceled=TRUE
			OR NEW.canceled_for_reschedule=TRUE) THEN
		PERFORM cancel_pending_care_encounter_scheduled_messages(OLD.care_encounter_id);
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER cancel_pending_messages_for_invalidated_care_attendance
AFTER UPDATE OF attendance_status_id, canceled, canceled_for_reschedule ON appointment
FOR EACH ROW EXECUTE PROCEDURE cancel_pending_messages_for_invalidated_care_attendance();

COMMIT;
