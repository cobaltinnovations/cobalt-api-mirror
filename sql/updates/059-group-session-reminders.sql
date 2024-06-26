BEGIN;
SELECT _v.register_patch('059-group-session-reminders', NULL, NULL);

-- Two fields so you can say "9:30AM on the same day as the group session" ('09:30', 0).
-- Day before is 1, 2 days before is 2, etc. (or for followups, day after is 1, 2 days after is 2, ...)
-- These are assumed to be used for scheduling in conjunction with the institution's/provider's timezone
ALTER TABLE institution ADD COLUMN group_session_reservation_default_reminder_time_of_day TIME NOT NULL DEFAULT '09:30';
ALTER TABLE institution ADD COLUMN group_session_reservation_default_reminder_day_offset INTEGER NOT NULL DEFAULT 0;
ALTER TABLE institution ADD COLUMN group_session_reservation_default_followup_time_of_day TIME NOT NULL DEFAULT '11:30';
ALTER TABLE institution ADD COLUMN group_session_reservation_default_followup_day_offset INTEGER NOT NULL DEFAULT 1;
ALTER TABLE institution ADD COLUMN appointment_reservation_default_reminder_time_of_day TIME NOT NULL DEFAULT '12:00';
ALTER TABLE institution ADD COLUMN appointment_reservation_default_reminder_day_offset INTEGER NOT NULL DEFAULT 1;

ALTER TABLE group_session_reservation ADD COLUMN attendee_reminder_scheduled_message_id UUID REFERENCES scheduled_message(scheduled_message_id);
ALTER TABLE group_session_reservation ADD COLUMN attendee_followup_scheduled_message_id UUID REFERENCES scheduled_message(scheduled_message_id);
ALTER TABLE appointment ADD COLUMN patient_reminder_scheduled_message_id UUID REFERENCES scheduled_message(scheduled_message_id);

-- Recreate affected view
DROP VIEW v_group_session_reservation;

CREATE VIEW v_group_session_reservation AS
 SELECT gsr.group_session_reservation_id,
    gsr.group_session_id,
    gsr.account_id,
    gsr.canceled,
    gsr.created,
    gsr.last_updated,
    gsr.followup_email_status_id,
    gsr.followup_email_sent_timestamp,
    gsr.attendee_reminder_scheduled_message_id,
    gsr.attendee_followup_scheduled_message_id,
    a.first_name,
    a.last_name,
    a.email_address,
    a.phone_number
   FROM group_session_reservation gsr,
    account a
  WHERE gsr.canceled = false AND a.account_id = gsr.account_id;

-- Add new fields, temporarily nullable
ALTER TABLE group_session ADD COLUMN submitter_name TEXT;
ALTER TABLE group_session ADD COLUMN submitter_email_address TEXT;

-- Migrate existing data
UPDATE group_session SET submitter_name = facilitator_name;
UPDATE group_session SET submitter_email_address = facilitator_email_address;

-- Make new fields non-nullable
ALTER TABLE group_session ALTER COLUMN submitter_name SET NOT NULL;
ALTER TABLE group_session ALTER COLUMN submitter_email_address SET NOT NULL;

-- Recreate affected view
DROP VIEW v_group_session;

CREATE VIEW v_group_session AS
 SELECT gs.group_session_id,
    gs.institution_id,
    gs.group_session_status_id,
    gs.assessment_id,
    gs.title,
    gs.description,
    gs.facilitator_account_id,
    gs.facilitator_name,
    gs.facilitator_email_address,
    gs.image_url,
    gs.videoconference_url,
    gs.start_date_time,
    gs.end_date_time,
    gs.seats,
    gs.url_name,
    gs.confirmation_email_content,
    gs.locale,
    gs.time_zone,
    gs.created,
    gs.last_updated,
    gs.group_session_scheduling_system_id,
    gs.schedule_url,
    gs.send_followup_email,
    gs.followup_email_content,
    gs.followup_email_survey_url,
    gs.submitter_account_id,
    gs.submitter_name,
    gs.submitter_email_address,
    gs.en_search_vector,
    ( SELECT count(*) AS count
           FROM group_session_reservation gsr
          WHERE gsr.group_session_id = gs.group_session_id AND gsr.canceled = false) AS seats_reserved,
    gs.seats - (( SELECT count(*) AS count
           FROM group_session_reservation gsr
          WHERE gsr.group_session_id = gs.group_session_id AND gsr.canceled = false)) AS seats_available
   FROM group_session gs
  WHERE gs.group_session_status_id::text <> 'DELETED'::text;

COMMIT;