BEGIN;
SELECT _v.register_patch('257-media-uploads', NULL, NULL);

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
  created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_updated TIMESTAMPTZ NOT NULL,
  CHECK (width > 0),
  CHECK (height > 0)
);

CREATE INDEX idx_image_file_upload_id ON image(file_upload_id);
CREATE INDEX idx_image_source_image_id ON image(source_image_id);
CREATE INDEX idx_image_active ON image(active);
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
  i.image_alt_text
FROM
  image i,
  file_upload fu
WHERE
  i.file_upload_id=fu.file_upload_id;

COMMIT;
