BEGIN;
SELECT _v.register_patch('264-care-navigator-screening-contact', ARRAY['263-care-encounter-notes'], NULL);

-- Care Navigator contact details come from the screening completed for the
-- appointment.  A screening flow is expected to have at most one freeform
-- email-address question.
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
	IF v_appointment_count=1 THEN
		UPDATE care_encounter
		SET email_address=screening_session_contact_email_address(v_screening_session_id),
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

COMMIT;
