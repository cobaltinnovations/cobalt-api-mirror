BEGIN;
SELECT _v.register_patch('258-media-image-active', NULL, NULL);

ALTER TABLE image ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_image_active ON image(active);
CREATE INDEX idx_image_source_image_id_active ON image(source_image_id, active);

CREATE OR REPLACE VIEW v_image AS
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
  i.active
FROM
  image i,
  file_upload fu
WHERE
  i.file_upload_id=fu.file_upload_id;

COMMIT;
