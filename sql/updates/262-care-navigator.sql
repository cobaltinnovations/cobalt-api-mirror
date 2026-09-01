BEGIN;
SELECT _v.register_patch('262-care-navigator', ARRAY['261-provider-booking-database'], NULL);

-- Home-page features may link directly to a specific provider details view.
-- Existing features remain unchanged because this association is optional.
ALTER TABLE institution_feature
ADD COLUMN IF NOT EXISTS provider_id UUID REFERENCES provider(provider_id);

-- RESOURCE_NAVIGATOR is a legacy internal identifier. Use Care Navigator in
-- every user-facing feature response.
UPDATE feature
SET name='Connect with a Care Navigator'
WHERE feature_id='RESOURCE_NAVIGATOR';

-- Care Navigators use the existing provider login role. Their provider-search
-- identity and signed-in experience are modeled separately so future
-- administrative capabilities do not require another account role.
INSERT INTO support_role (
	support_role_id,
	description,
	display_order
)
VALUES ('CARE_NAVIGATOR', 'Care Navigator', 12)
ON CONFLICT (support_role_id) DO UPDATE
SET description=EXCLUDED.description,
	display_order=EXCLUDED.display_order;

INSERT INTO account_capability_type (
	account_capability_type_id,
	description
)
VALUES ('NAVIGATOR', 'Care Navigator')
ON CONFLICT (account_capability_type_id) DO UPDATE
SET description=EXCLUDED.description;

-- The Cobalt Innovations administrator also participates in organization-wide
-- Care Navigator administration. The NAVIGATOR capability is additive to the
-- account's existing ADMINISTRATOR role.
INSERT INTO account_capability (
	account_id,
	account_capability_type_id
)
SELECT
	account.account_id,
	'NAVIGATOR'
FROM account
WHERE account.institution_id='COBALT'
AND account.role_id='ADMINISTRATOR'
AND LOWER(account.email_address)=LOWER('admin@cobaltinnovations.org')
ON CONFLICT (account_id, account_capability_type_id) DO NOTHING;

-- Navigator accounts may serve one or more public Care Navigator booking
-- providers.  account.provider_id remains the account's primary provider
-- identity; this mapping is used for encounter routing and appointment access.
CREATE TABLE care_navigator_provider_account (
	provider_id UUID NOT NULL REFERENCES provider(provider_id),
	account_id UUID NOT NULL REFERENCES account(account_id),
	display_order INTEGER NOT NULL DEFAULT 1 CHECK (display_order > 0),
	created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	PRIMARY KEY (provider_id, account_id)
);

CREATE INDEX care_navigator_provider_account_account_id_idx
ON care_navigator_provider_account(account_id);

CREATE TRIGGER set_last_updated
BEFORE INSERT OR UPDATE ON care_navigator_provider_account
FOR EACH ROW EXECUTE PROCEDURE set_last_updated();

CREATE OR REPLACE FUNCTION validate_care_navigator_provider_account()
RETURNS TRIGGER AS $$
BEGIN
	IF NOT EXISTS (
		SELECT 1
		FROM provider
		JOIN account ON account.account_id=NEW.account_id
		WHERE provider.provider_id=NEW.provider_id
		AND provider.institution_id=account.institution_id
		AND provider.active=TRUE
		AND account.active=TRUE
		AND account.role_id IN ('ADMINISTRATOR', 'PROVIDER')
		AND EXISTS (
			SELECT 1
			FROM provider_support_role
			WHERE provider_support_role.provider_id=provider.provider_id
			AND provider_support_role.support_role_id='CARE_NAVIGATOR'
		)
		AND EXISTS (
			SELECT 1
			FROM account_capability
			WHERE account_capability.account_id=account.account_id
			AND account_capability.account_capability_type_id='NAVIGATOR'
		)
	) THEN
		RAISE EXCEPTION 'Care Navigator provider mappings require an active Care Navigator provider and an active Navigator-capable Administrator or Provider account in the same institution.';
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_care_navigator_provider_account
BEFORE INSERT OR UPDATE OF provider_id, account_id ON care_navigator_provider_account
FOR EACH ROW EXECUTE FUNCTION validate_care_navigator_provider_account();

-- RESOURCE_NAVIGATOR already exists as a global feature. Connect it to the new
-- provider support role without changing its route or tenant visibility.
INSERT INTO feature_support_role (
	feature_support_role_id,
	feature_id,
	support_role_id
)
SELECT
	'67ab92eb-fb06-4d83-9103-5b97fdb10007'::UUID,
	'RESOURCE_NAVIGATOR',
	'CARE_NAVIGATOR'
WHERE EXISTS (
	SELECT 1
	FROM feature
	WHERE feature_id='RESOURCE_NAVIGATOR'
)
ON CONFLICT (feature_id, support_role_id) DO NOTHING;

-- An institution only has the Care Navigator capability when its feature is
-- connected to an active Care Navigator provider in the same institution.
-- The provider is the booking entity; Navigator staff accounts remain
-- independently assignable through the NAVIGATOR account capability.
CREATE OR REPLACE FUNCTION validate_care_navigator_booking_provider()
RETURNS TRIGGER AS $$
BEGIN
	IF NEW.feature_id='RESOURCE_NAVIGATOR' THEN
		IF NEW.provider_id IS NULL THEN
			RAISE EXCEPTION 'Care Navigator feature requires a booking provider.';
		END IF;

		IF NOT EXISTS (
			SELECT 1
			FROM provider
			JOIN provider_support_role
				ON provider_support_role.provider_id=provider.provider_id
				AND provider_support_role.support_role_id='CARE_NAVIGATOR'
			WHERE provider.provider_id=NEW.provider_id
			AND provider.institution_id=NEW.institution_id
			AND provider.active=TRUE
		) THEN
			RAISE EXCEPTION 'Care Navigator booking provider must be active, belong to the institution, and have the Care Navigator support role.';
		END IF;
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_care_navigator_booking_provider
BEFORE INSERT OR UPDATE OF feature_id, institution_id, provider_id ON institution_feature
FOR EACH ROW
EXECUTE FUNCTION validate_care_navigator_booking_provider();

-- A provider may publish a contact phone number without offering phone
-- appointments. Care Navigator appointments are always virtual.
ALTER TABLE provider
ADD COLUMN IF NOT EXISTS virtual_appointments_only BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE provider
SET virtual_appointments_only=TRUE
WHERE EXISTS (
	SELECT 1
	FROM provider_support_role
	WHERE provider_support_role.provider_id=provider.provider_id
	AND provider_support_role.support_role_id='CARE_NAVIGATOR'
);

CREATE TABLE care_encounter_status (
	care_encounter_status_id TEXT PRIMARY KEY,
	description TEXT NOT NULL,
	terminal BOOLEAN NOT NULL
);

INSERT INTO care_encounter_status VALUES ('OPEN', 'Open', FALSE);
INSERT INTO care_encounter_status VALUES ('CLOSED', 'Closed', TRUE);
INSERT INTO care_encounter_status VALUES ('CANCELED', 'Canceled by Care Navigator', TRUE);

CREATE TABLE care_encounter_cancellation_reason (
	care_encounter_cancellation_reason_id TEXT PRIMARY KEY,
	description TEXT NOT NULL,
	display_order INTEGER NOT NULL,
	freeform_text_required BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO care_encounter_cancellation_reason VALUES
	('PATIENT_REQUESTED', 'Patient requested cancellation', 1, FALSE),
	('NO_LONGER_NEEDED', 'Care navigation is no longer needed', 2, FALSE),
	('UNABLE_TO_REACH_PATIENT', 'Unable to reach patient', 3, FALSE),
	('SCHEDULING_CONFLICT', 'Scheduling conflict', 4, FALSE),
	('DUPLICATE_BOOKING', 'Duplicate booking', 5, FALSE),
	('OTHER', 'Other', 6, TRUE);

-- An encounter is the patient-level Care Navigator lifecycle. Appointments
-- point to it so a canceled or missed booking remains part of the history.
CREATE TABLE care_encounter (
	care_encounter_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
	account_id UUID NOT NULL REFERENCES account(account_id),
	care_navigator_account_id UUID REFERENCES account(account_id),
	care_encounter_status_id TEXT NOT NULL REFERENCES care_encounter_status(care_encounter_status_id) DEFAULT 'OPEN',
	email_address TEXT,
	closed_at TIMESTAMPTZ,
	closed_by_account_id UUID REFERENCES account(account_id),
	canceled_by_account_id UUID REFERENCES account(account_id),
	care_encounter_cancellation_reason_id TEXT REFERENCES care_encounter_cancellation_reason(care_encounter_cancellation_reason_id),
	care_encounter_cancellation_reason_other_text TEXT,
	deleted BOOLEAN NOT NULL DEFAULT FALSE,
	created_by_account_id UUID NOT NULL REFERENCES account(account_id),
	last_updated_by_account_id UUID NOT NULL REFERENCES account(account_id),
	created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	CONSTRAINT care_encounter_cancellation_reason_required_check CHECK (
		care_encounter_status_id<>'CANCELED'
		OR care_encounter_cancellation_reason_id IS NOT NULL
	),
	CONSTRAINT care_encounter_cancellation_reason_other_text_check CHECK (
		(care_encounter_cancellation_reason_id='OTHER'
			AND NULLIF(BTRIM(care_encounter_cancellation_reason_other_text), '') IS NOT NULL)
		OR (care_encounter_cancellation_reason_id IS DISTINCT FROM 'OTHER'
			AND care_encounter_cancellation_reason_other_text IS NULL)
	),
	CONSTRAINT care_encounter_deleted_terminal_check CHECK (
		deleted=FALSE OR care_encounter_status_id<>'OPEN'
	)
);

CREATE TRIGGER set_last_updated
BEFORE INSERT OR UPDATE ON care_encounter
FOR EACH ROW EXECUTE PROCEDURE set_last_updated();

ALTER TABLE appointment
ADD COLUMN IF NOT EXISTS care_encounter_id UUID REFERENCES care_encounter(care_encounter_id),
ADD COLUMN IF NOT EXISTS canceled_by_account_id UUID REFERENCES account(account_id),
ADD COLUMN IF NOT EXISTS cancellation_reason TEXT,
ADD COLUMN IF NOT EXISTS screening_session_id UUID REFERENCES screening_session(screening_session_id);

CREATE UNIQUE INDEX care_encounter_one_open_per_account_idx
ON care_encounter(account_id)
WHERE care_encounter_status_id='OPEN' AND deleted=FALSE;

-- UNKNOWN is the only scheduled/active attendance state. A pending reschedule
-- is excluded so its replacement can be linked in the same transaction.
CREATE UNIQUE INDEX care_encounter_one_active_appointment_idx
ON appointment(care_encounter_id)
WHERE care_encounter_id IS NOT NULL
AND canceled=FALSE
AND canceled_for_reschedule=FALSE
AND attendance_status_id='UNKNOWN';

CREATE INDEX appointment_care_encounter_start_time_idx
ON appointment(care_encounter_id, start_time DESC, appointment_id);

CREATE INDEX IF NOT EXISTS appointment_provider_start_time_idx
ON appointment(provider_id, start_time DESC);

CREATE OR REPLACE FUNCTION care_navigator_account_can_serve_provider(
	p_account_id UUID,
	p_provider_id UUID
)
RETURNS BOOLEAN AS $$
	SELECT EXISTS (
		SELECT 1
		FROM care_navigator_provider_account mapping
		JOIN account ON account.account_id=mapping.account_id
		JOIN provider ON provider.provider_id=mapping.provider_id
		JOIN account_capability
			ON account_capability.account_id=account.account_id
			AND account_capability.account_capability_type_id='NAVIGATOR'
		WHERE mapping.account_id=p_account_id
		AND mapping.provider_id=p_provider_id
		AND account.active=TRUE
		AND account.role_id IN ('ADMINISTRATOR', 'PROVIDER')
		AND provider.active=TRUE
		AND provider.institution_id=account.institution_id
		AND EXISTS (
			SELECT 1
			FROM provider_support_role
			WHERE provider_support_role.provider_id=provider.provider_id
			AND provider_support_role.support_role_id='CARE_NAVIGATOR'
		)
	)
$$ LANGUAGE SQL STABLE;

CREATE OR REPLACE FUNCTION first_care_navigator_account_for_provider(p_provider_id UUID)
RETURNS UUID AS $$
	SELECT mapping.account_id
	FROM care_navigator_provider_account mapping
	WHERE mapping.provider_id=p_provider_id
	AND care_navigator_account_can_serve_provider(mapping.account_id, mapping.provider_id)
	ORDER BY mapping.display_order, mapping.account_id
	LIMIT 1
$$ LANGUAGE SQL STABLE;

-- Attach every Care Navigator appointment, including import/sync inserts, to
-- the patient's open lifecycle or create the lifecycle when none exists.
CREATE OR REPLACE FUNCTION attach_care_navigator_appointment_to_encounter()
RETURNS TRIGGER AS $$
DECLARE
	v_care_encounter_id UUID;
	v_care_navigator_account_id UUID;
	v_care_encounter_status_id TEXT;
	v_new_appointment_is_active BOOLEAN;
BEGIN
	IF NEW.provider_id IS NULL OR NOT EXISTS (
		SELECT 1
		FROM provider_support_role
		WHERE provider_support_role.provider_id=NEW.provider_id
		AND provider_support_role.support_role_id='CARE_NAVIGATOR'
	) THEN
		RETURN NEW;
	END IF;

	PERFORM pg_advisory_xact_lock(hashtextextended(
		FORMAT('care-navigator-appointment|%s', NEW.account_id), 0));

	v_new_appointment_is_active := NEW.canceled=FALSE
		AND NEW.canceled_for_reschedule=FALSE
		AND NEW.attendance_status_id='UNKNOWN';

	IF NEW.care_encounter_id IS NOT NULL THEN
		SELECT care_encounter.care_navigator_account_id,
			care_encounter.care_encounter_status_id
		INTO v_care_navigator_account_id, v_care_encounter_status_id
		FROM care_encounter
		WHERE care_encounter.care_encounter_id=NEW.care_encounter_id
		AND care_encounter.account_id=NEW.account_id
		FOR UPDATE;

		IF NOT FOUND THEN
			RAISE EXCEPTION 'Care Navigator appointment encounter must belong to the appointment account.';
		END IF;

		IF v_care_encounter_status_id<>'OPEN' THEN
			IF TG_OP='INSERT' THEN
				RAISE EXCEPTION 'New Care Navigator appointments cannot be attached to a terminal encounter.';
			ELSIF OLD.care_encounter_id IS DISTINCT FROM NEW.care_encounter_id THEN
				RAISE EXCEPTION 'Care Navigator appointments cannot be moved to a terminal encounter.';
			END IF;
		END IF;

		IF v_new_appointment_is_active AND EXISTS (
			SELECT 1
			FROM appointment
			WHERE appointment.care_encounter_id=NEW.care_encounter_id
			AND appointment.appointment_id<>NEW.appointment_id
			AND appointment.attendance_status_id='ATTENDED'
		) THEN
			RAISE EXCEPTION 'Completed Care Navigator encounter must be closed before another appointment can be booked.';
		END IF;

		IF v_new_appointment_is_active AND EXISTS (
			SELECT 1
			FROM appointment
			WHERE appointment.care_encounter_id=NEW.care_encounter_id
			AND appointment.appointment_id<>NEW.appointment_id
			AND appointment.canceled=FALSE
			AND appointment.canceled_for_reschedule=FALSE
			AND appointment.attendance_status_id='UNKNOWN'
		) THEN
			RAISE EXCEPTION 'Care Navigator encounter already has an active appointment.';
		END IF;

		IF v_care_navigator_account_id IS NULL
			OR NOT care_navigator_account_can_serve_provider(v_care_navigator_account_id, NEW.provider_id) THEN
			UPDATE care_encounter
			SET care_navigator_account_id=first_care_navigator_account_for_provider(NEW.provider_id),
				last_updated_by_account_id=NEW.created_by_account_id
			WHERE care_encounter_id=NEW.care_encounter_id;
		END IF;

		RETURN NEW;
	END IF;

	SELECT care_encounter.care_encounter_id,
		care_encounter.care_navigator_account_id
	INTO v_care_encounter_id, v_care_navigator_account_id
	FROM care_encounter
	WHERE care_encounter.account_id=NEW.account_id
	AND care_encounter.care_encounter_status_id='OPEN'
	AND care_encounter.deleted=FALSE
	FOR UPDATE;

	IF v_care_encounter_id IS NOT NULL THEN
		IF v_new_appointment_is_active AND EXISTS (
			SELECT 1
			FROM appointment
			WHERE appointment.care_encounter_id=v_care_encounter_id
			AND appointment.attendance_status_id='ATTENDED'
		) THEN
			RAISE EXCEPTION 'Completed Care Navigator encounter must be closed before another appointment can be booked.';
		END IF;

		IF v_new_appointment_is_active AND EXISTS (
			SELECT 1
			FROM appointment
			WHERE appointment.care_encounter_id=v_care_encounter_id
			AND appointment.appointment_id<>NEW.appointment_id
			AND appointment.canceled=FALSE
			AND appointment.canceled_for_reschedule=FALSE
			AND appointment.attendance_status_id='UNKNOWN'
		) THEN
			RAISE EXCEPTION 'Care Navigator encounter already has an active appointment.';
		END IF;

		IF v_care_navigator_account_id IS NULL
			OR NOT care_navigator_account_can_serve_provider(v_care_navigator_account_id, NEW.provider_id) THEN
			v_care_navigator_account_id := first_care_navigator_account_for_provider(NEW.provider_id);
		END IF;

		UPDATE care_encounter
		SET care_navigator_account_id=v_care_navigator_account_id,
			last_updated_by_account_id=NEW.created_by_account_id
		WHERE care_encounter_id=v_care_encounter_id;
	ELSE
		INSERT INTO care_encounter (
			account_id,
			care_navigator_account_id,
			email_address,
			created_by_account_id,
			last_updated_by_account_id
		) VALUES (
			NEW.account_id,
			first_care_navigator_account_for_provider(NEW.provider_id),
			NEW.email_address,
			NEW.created_by_account_id,
			NEW.created_by_account_id
		)
		RETURNING care_encounter_id INTO v_care_encounter_id;
	END IF;

	UPDATE appointment
	SET care_encounter_id=v_care_encounter_id
	WHERE appointment_id=NEW.appointment_id;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS attach_care_navigator_appointment_to_encounter ON appointment;
CREATE TRIGGER attach_care_navigator_appointment_to_encounter
AFTER INSERT OR UPDATE OF provider_id, account_id, care_encounter_id ON appointment
FOR EACH ROW EXECUTE PROCEDURE attach_care_navigator_appointment_to_encounter();

-- Only an authenticated patient canceling their own non-reschedule booking
-- automatically closes the encounter. Unknown/external and staff actors leave
-- the lifecycle open for follow-up and rebooking.
CREATE OR REPLACE FUNCTION close_care_encounter_for_patient_cancellation()
RETURNS TRIGGER AS $$
DECLARE
	v_care_encounter_id UUID;
BEGIN
	IF NEW.canceled=TRUE
		AND NEW.canceled_by_account_id=NEW.account_id
		AND NEW.canceled_for_reschedule=FALSE THEN
		SELECT appointment.care_encounter_id
		INTO v_care_encounter_id
		FROM appointment
		WHERE appointment.appointment_id=NEW.appointment_id;

		UPDATE care_encounter
		SET care_encounter_status_id='CLOSED',
			closed_at=COALESCE(closed_at, NEW.canceled_at, NOW()),
			closed_by_account_id=NEW.canceled_by_account_id,
			last_updated_by_account_id=NEW.canceled_by_account_id
		WHERE care_encounter_id=COALESCE(NEW.care_encounter_id, v_care_encounter_id)
		AND care_encounter_status_id='OPEN'
		AND NOT EXISTS (
			SELECT 1
			FROM appointment active_appointment
			WHERE active_appointment.care_encounter_id=care_encounter.care_encounter_id
			AND active_appointment.appointment_id<>NEW.appointment_id
			AND active_appointment.canceled=FALSE
			AND active_appointment.canceled_for_reschedule=FALSE
			AND active_appointment.attendance_status_id='UNKNOWN'
		);
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS close_care_encounter_for_patient_cancellation ON appointment;
CREATE TRIGGER close_care_encounter_for_patient_cancellation
AFTER INSERT OR UPDATE OF canceled, canceled_by_account_id, canceled_for_reschedule ON appointment
FOR EACH ROW EXECUTE PROCEDURE close_care_encounter_for_patient_cancellation();

-- Providers assigned the role later receive the same modality default. Role
-- assignment intentionally leaves existing patient-triage appointments outside
-- care encounters. New appointments, and later explicit provider/account
-- reassignments, continue through the normal appointment attachment trigger.
CREATE OR REPLACE FUNCTION apply_care_navigator_provider_defaults()
RETURNS TRIGGER AS $$
BEGIN
	IF NEW.support_role_id='CARE_NAVIGATOR' THEN
		UPDATE provider
		SET virtual_appointments_only=TRUE
		WHERE provider_id=NEW.provider_id;
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS apply_care_navigator_provider_defaults ON provider_support_role;
CREATE TRIGGER apply_care_navigator_provider_defaults
AFTER INSERT OR UPDATE OF support_role_id ON provider_support_role
FOR EACH ROW EXECUTE PROCEDURE apply_care_navigator_provider_defaults();

CREATE TABLE care_encounter_note (
	care_encounter_note_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
	care_encounter_id UUID NOT NULL REFERENCES care_encounter(care_encounter_id),
	note TEXT NOT NULL,
	deleted BOOLEAN NOT NULL DEFAULT FALSE,
	deleted_at TIMESTAMPTZ,
	deleted_by_account_id UUID REFERENCES account(account_id),
	created_by_account_id UUID NOT NULL REFERENCES account(account_id),
	last_updated_by_account_id UUID NOT NULL REFERENCES account(account_id),
	created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	CONSTRAINT care_encounter_note_not_blank_check CHECK (NULLIF(BTRIM(note), '') IS NOT NULL),
	CONSTRAINT care_encounter_note_deleted_check CHECK (
		(deleted=FALSE AND deleted_at IS NULL AND deleted_by_account_id IS NULL)
		OR (deleted=TRUE AND deleted_at IS NOT NULL AND deleted_by_account_id IS NOT NULL)
	)
);

CREATE INDEX care_encounter_note_encounter_created_idx
ON care_encounter_note(care_encounter_id, created DESC, care_encounter_note_id DESC);

CREATE TRIGGER set_last_updated
BEFORE INSERT OR UPDATE ON care_encounter_note
FOR EACH ROW EXECUTE PROCEDURE set_last_updated();

CREATE TRIGGER care_encounter_note_footprint
AFTER INSERT OR UPDATE OR DELETE ON care_encounter_note
FOR EACH ROW EXECUTE PROCEDURE perform_footprint();

-- Care Navigator contact details come from the screening completed for the
-- appointment.  A screening flow is expected to have at most one freeform
-- email-address question.  When the screening supplies no address, the
-- encounter keeps the booking contact it was seeded with.
CREATE OR REPLACE FUNCTION screening_session_contact_email_address(p_screening_session_id UUID)
RETURNS TEXT AS $$
DECLARE
	v_email_addresses TEXT[];
BEGIN
	IF p_screening_session_id IS NULL THEN
		RETURN NULL;
	END IF;

	SELECT ARRAY_AGG(LOWER(BTRIM(screening_answer.text)) ORDER BY
			screening_session_screening.screening_order,
			screening_question.display_order,
			screening_answer.answer_order)
	INTO v_email_addresses
	FROM v_screening_session_screening screening_session_screening
	JOIN v_screening_session_answered_screening_question answered_question
		ON answered_question.screening_session_screening_id=
			screening_session_screening.screening_session_screening_id
	JOIN screening_question
		ON screening_question.screening_question_id=answered_question.screening_question_id
	JOIN v_screening_answer screening_answer
		ON screening_answer.screening_session_answered_screening_question_id=
			answered_question.screening_session_answered_screening_question_id
	WHERE screening_session_screening.screening_session_id=p_screening_session_id
	AND screening_question.screening_answer_format_id='FREEFORM_TEXT'
	AND screening_question.screening_answer_content_hint_id='EMAIL_ADDRESS'
	AND NULLIF(BTRIM(screening_answer.text), '') IS NOT NULL;

	IF CARDINALITY(v_email_addresses) > 1 THEN
		RAISE EXCEPTION 'Care Navigator appointment screening session % has multiple contact email addresses.',
			p_screening_session_id;
	END IF;

	RETURN v_email_addresses[1];
END;
$$ LANGUAGE plpgsql STABLE;

-- The encounter-linking trigger runs first and may assign care_encounter_id
-- through a nested appointment update.  Read the current appointment row so
-- both direct inserts and application bookings observe the final association.
CREATE OR REPLACE FUNCTION seed_care_encounter_contact_from_screening()
RETURNS TRIGGER AS $$
DECLARE
	v_care_encounter_id UUID;
	v_screening_session_id UUID;
	v_appointment_count BIGINT;
BEGIN
	-- Ignore unrelated re-touches and later screening removal.  The meaningful
	-- seed events are appointment insert, first encounter association, and the
	-- initial transition from no screening to a completed screening.
	IF TG_OP='UPDATE'
		AND OLD.care_encounter_id IS NOT DISTINCT FROM NEW.care_encounter_id
		AND (OLD.screening_session_id IS NOT NULL OR NEW.screening_session_id IS NULL) THEN
		RETURN NEW;
	END IF;

	SELECT appointment.care_encounter_id,
		appointment.screening_session_id
	INTO v_care_encounter_id, v_screening_session_id
	FROM appointment
	WHERE appointment.appointment_id=NEW.appointment_id;

	IF v_care_encounter_id IS NULL THEN
		RETURN NEW;
	END IF;

	SELECT COUNT(*)
	INTO v_appointment_count
	FROM appointment
	WHERE appointment.care_encounter_id=v_care_encounter_id;

	-- Seed only the encounter created for its first appointment.  Later
	-- appointments in the same lifecycle must not overwrite Navigator edits.
	-- A screening without a contact answer never clears an existing address:
	-- the booking contact seeded at encounter creation remains in place.
	IF v_appointment_count=1 THEN
		UPDATE care_encounter
		SET email_address=COALESCE(
				screening_session_contact_email_address(v_screening_session_id),
				care_encounter.email_address),
			last_updated_by_account_id=NEW.created_by_account_id
		WHERE care_encounter_id=v_care_encounter_id
		AND care_encounter_status_id='OPEN';
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS seed_care_encounter_contact_from_screening ON appointment;
CREATE TRIGGER seed_care_encounter_contact_from_screening
AFTER INSERT OR UPDATE OF care_encounter_id, screening_session_id ON appointment
FOR EACH ROW EXECUTE PROCEDURE seed_care_encounter_contact_from_screening();

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
