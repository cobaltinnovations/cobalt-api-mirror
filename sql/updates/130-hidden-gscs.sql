BEGIN;
SELECT _v.register_patch('130-hidden-gscs', NULL, NULL);

ALTER TABLE group_session_collection ADD COLUMN visible_flag BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE group_session_collection ADD COLUMN url_name TEXT;

-- Populate URL names for collection with a variant of their title, e.g. "Test Collection 1" becomes 'test-collection-1'
UPDATE group_session_collection SET url_name = LOWER(REPLACE(title, ' ', '-'));

CREATE UNIQUE INDEX group_session_collection_unique_url_name_idx ON group_session_collection USING btree (institution_id, url_name);

ALTER TABLE group_session_collection ALTER COLUMN url_name SET NOT NULL;

ALTER TABLE topic_center ADD COLUMN nav_description TEXT;

UPDATE topic_center SET nav_description=featured_description;

ALTER TABLE study ADD COLUMN url_name TEXT;

-- Populate URL names for study with a variant of their name, e.g. "Cobalt Study" becomes 'cobalt-study'
UPDATE study SET url_name = LOWER(REPLACE(name, ' ', '-'));

CREATE UNIQUE INDEX study_unique_url_name_idx ON study USING btree (institution_id, url_name);

ALTER TABLE study ALTER COLUMN url_name SET NOT NULL;

ALTER TABLE study ADD COLUMN onboarding_destination_url TEXT;

CREATE TABLE study_account_source (
	study_id UUID NOT NULL REFERENCES study,
	account_source_id TEXT NOT NULL REFERENCES account_source,
	PRIMARY KEY (study_id, account_source_id)
);

-- Example account source data for Cobalt-institution test studies
INSERT INTO study_account_source (study_id, account_source_id)
SELECT study_id, 'ANONYMOUS'
FROM study
WHERE institution_id='COBALT';

-- Some group sessions have the ability to whitelabel parts of the system
ALTER TABLE group_session ADD COLUMN override_platform_name TEXT;
ALTER TABLE group_session ADD COLUMN override_platform_email_image_url TEXT;
ALTER TABLE group_session ADD COLUMN override_platform_support_email_address TEXT;

DROP VIEW v_group_session;

-- Add override_platform_name, override_platform_email_image_url, override_platform_support_email_address
CREATE VIEW v_group_session AS
 SELECT gs.group_session_id,
    gs.institution_id,
    gs.group_session_status_id,
    gs.assessment_id,
    gs.group_session_location_type_id,
    gs.in_person_location,
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
    gs.send_followup_email,
    gs.followup_email_content,
    gs.followup_email_survey_url,
    gs.submitter_account_id,
    gs.target_email_address,
    gs.en_search_vector,
    ( SELECT count(*) AS count
           FROM group_session_reservation gsr
          WHERE gsr.group_session_id = gs.group_session_id AND gsr.canceled = false) AS seats_reserved,
    gs.seats - (( SELECT count(*) AS count
           FROM group_session_reservation gsr
          WHERE gsr.group_session_id = gs.group_session_id AND gsr.canceled = false)) AS seats_available,
    gs.screening_flow_id,
	gs.visible_flag,
	gs.group_session_collection_id,
	gs.followup_time_of_day,
	gs.followup_day_offset,
	gs.send_reminder_email,
	gs.reminder_email_content,
	gs.single_session_flag,
	gs.date_time_description,
    gs.group_session_learn_more_method_id,
    gs.learn_more_description,
    gs.different_email_address_for_notifications,
    gs.override_platform_name,
    gs.override_platform_email_image_url,
    gs.override_platform_support_email_address
   FROM group_session gs
  WHERE gs.group_session_status_id::text <> 'DELETED'::text;

COMMIT;