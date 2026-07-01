BEGIN;
SELECT _v.register_patch('257-media-uploads', NULL, NULL);

ALTER TABLE institution ADD COLUMN image_repository_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE institution
SET image_repository_enabled=TRUE
WHERE institution_id='COBALT';

CREATE TABLE file_upload_status (
  file_upload_status_id TEXT PRIMARY KEY,
  description TEXT NOT NULL
);

INSERT INTO file_upload_status (file_upload_status_id, description) VALUES
  ('CREATED', 'Created'),
  ('UPLOADED', 'Uploaded'),
  ('ABANDONED', 'Abandoned');

ALTER TABLE file_upload ADD COLUMN file_upload_status_id TEXT NOT NULL REFERENCES file_upload_status DEFAULT 'CREATED';
ALTER TABLE file_upload ADD COLUMN institution_id VARCHAR REFERENCES institution;
ALTER TABLE file_upload ADD COLUMN storage_bucket TEXT;
ALTER TABLE file_upload ADD COLUMN storage_region TEXT;

UPDATE file_upload fu
SET institution_id = a.institution_id
FROM account a
WHERE fu.account_id = a.account_id
AND fu.institution_id IS NULL;

ALTER TABLE file_upload ALTER COLUMN institution_id SET NOT NULL;

UPDATE file_upload
SET storage_bucket = COALESCE(
  substring(url FROM '^https?://([^./]+)\.s3[.-][^/]+\.amazonaws\.com/'),
  substring(url FROM '^https?://([^./]+)\.s3\.amazonaws\.com/'),
  substring(url FROM '^https?://s3[.-][^/]+\.amazonaws\.com/([^/]+)/')
)
WHERE storage_bucket IS NULL;

UPDATE file_upload
SET storage_region = COALESCE(
  substring(url FROM '^https?://[^./]+\.s3[.-]([a-z0-9-]+)\.amazonaws\.com/'),
  substring(url FROM '^https?://s3[.-]([a-z0-9-]+)\.amazonaws\.com/[^/]+/')
)
WHERE storage_region IS NULL;

CREATE INDEX idx_file_upload_file_upload_status_id ON file_upload(file_upload_status_id);
CREATE INDEX idx_file_upload_institution_id ON file_upload(institution_id);

ALTER TABLE file_upload_type ADD COLUMN storage_key TEXT;

UPDATE file_upload_type
SET storage_key = LOWER(REPLACE(file_upload_type_id, '_', '-'))
WHERE storage_key IS NULL;

ALTER TABLE file_upload_type ALTER COLUMN storage_key SET NOT NULL;
CREATE UNIQUE INDEX file_upload_type_storage_key_unique_idx ON file_upload_type(storage_key);

COMMENT ON COLUMN file_upload_type.storage_key IS 'Directory segment appended under media-uploads/<institution_id>/ for media upload objects.';

INSERT INTO file_upload_type (file_upload_type_id, description, storage_key) VALUES
  ('IMAGE_RAW', 'Image Raw', 'image-raw'),
  ('IMAGE_4X3', 'Image 4x3', 'image-4x3'),
  ('IMAGE_16X9', 'Image 16x9', 'image-16x9'),
  ('IMAGE_1X1', 'Image 1x1', 'image-1x1'),
  ('IMAGE_THUMBNAIL_4X3', 'Image Thumbnail 4x3', 'image-thumbnail-4x3'),
  ('IMAGE_THUMBNAIL_16X9', 'Image Thumbnail 16x9', 'image-thumbnail-16x9'),
  ('IMAGE_THUMBNAIL_1X1', 'Image Thumbnail 1x1', 'image-thumbnail-1x1');

CREATE TABLE image (
  image_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  file_upload_id UUID NOT NULL REFERENCES file_upload,
  source_image_id UUID REFERENCES image(image_id),
  created_by_account_id UUID NOT NULL REFERENCES account,
  width INTEGER NOT NULL,
  height INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  image_alt_text TEXT,
  image_hash TEXT,
  created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_updated TIMESTAMPTZ NOT NULL,
  CHECK (width > 0),
  CHECK (height > 0),
  CHECK (image_hash IS NULL OR image_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_image_file_upload_id ON image(file_upload_id);
CREATE INDEX idx_image_source_image_id ON image(source_image_id);
CREATE INDEX idx_image_active ON image(active);
CREATE INDEX idx_image_image_hash ON image(image_hash) WHERE image_hash IS NOT NULL;
CREATE INDEX idx_image_source_image_id_active ON image(source_image_id, active);
CREATE TRIGGER set_last_updated BEFORE INSERT OR UPDATE ON image FOR EACH ROW EXECUTE PROCEDURE set_last_updated();

CREATE VIEW v_image AS
SELECT
  i.image_id,
  i.file_upload_id,
  i.source_image_id,
  fu.institution_id,
  i.created_by_account_id,
  fu.account_id AS file_upload_account_id,
  fu.file_upload_status_id,
  fu.file_upload_type_id,
  i.width,
  i.height,
  fu.filename,
  fu.filesize AS filesize_in_bytes,
  fu.content_type,
  fu.url,
  fu.storage_bucket,
  fu.storage_key,
  fu.storage_region,
  i.created,
  i.last_updated,
  i.active,
  i.image_alt_text,
  i.image_hash
FROM
  image i,
  file_upload fu
WHERE
  i.file_upload_id=fu.file_upload_id;

ALTER TABLE group_session ADD COLUMN image_id UUID REFERENCES image(image_id);
CREATE INDEX idx_group_session_image_id ON group_session(image_id) WHERE image_id IS NOT NULL;

UPDATE group_session gs
SET image_id=i.image_id
FROM image i
WHERE gs.image_file_upload_id=i.file_upload_id
AND gs.image_id IS NULL;

DROP VIEW v_group_session;

CREATE VIEW v_group_session AS
 SELECT gs.group_session_id,
    gs.institution_id,
    gs.group_session_status_id,
    gs.assessment_id,
    gs.group_session_location_type_id,
    gs.group_session_visibility_type_id,
    gs.in_person_location,
    gs.title,
    gs.description,
    gs.facilitator_account_id,
    gs.facilitator_name,
    gs.facilitator_email_address,
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
    gs.override_platform_support_email_address,
    gsc.url_name AS group_session_collection_url_name,
    gs.registration_end_date_time,
    gs.image_id,
    gs.image_file_upload_id,
    COALESCE(i.url, fu.url) as image_file_upload_url
   FROM group_session gs LEFT OUTER JOIN group_session_collection gsc on gs.group_session_collection_id=gsc.group_session_collection_id
   LEFT OUTER JOIN file_upload fu ON gs.image_file_upload_id = fu.file_upload_id
   LEFT OUTER JOIN v_image i ON gs.image_id = i.image_id
  WHERE gs.group_session_status_id::text <> 'DELETED'::text;

COMMIT;
