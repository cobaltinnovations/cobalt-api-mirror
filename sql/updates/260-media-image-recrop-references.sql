BEGIN;
SELECT _v.register_patch('260-media-image-recrop-references', NULL, NULL);

CREATE TEMPORARY TABLE media_image_crop_replacement_260 ON COMMIT DROP AS
WITH current_crop_candidates AS (
  SELECT
    raw.image_id AS raw_image_id,
    raw_file_upload.institution_id,
    crop.image_id AS current_crop_image_id,
    crop.file_upload_id AS current_crop_file_upload_id,
    crop_file_upload.file_upload_type_id AS crop_file_upload_type_id,
    ROW_NUMBER() OVER (
      PARTITION BY raw.image_id, crop_file_upload.file_upload_type_id
      ORDER BY thumbnail_file_upload.last_updated DESC, thumbnail.image_id DESC
    ) AS candidate_order
  FROM image raw
  JOIN file_upload raw_file_upload ON raw_file_upload.file_upload_id=raw.file_upload_id
  JOIN image crop ON crop.source_image_id=raw.image_id
  JOIN file_upload crop_file_upload ON crop_file_upload.file_upload_id=crop.file_upload_id
  JOIN image thumbnail ON thumbnail.source_image_id=crop.image_id
  JOIN file_upload thumbnail_file_upload ON thumbnail_file_upload.file_upload_id=thumbnail.file_upload_id
  WHERE raw.active=TRUE
  AND raw_file_upload.file_upload_status_id='UPLOADED'
  AND raw_file_upload.file_upload_type_id='IMAGE_RAW'
  AND crop.active=TRUE
  AND crop_file_upload.file_upload_status_id='UPLOADED'
  AND thumbnail.active=TRUE
  AND thumbnail_file_upload.file_upload_status_id='UPLOADED'
  AND crop_file_upload.institution_id=raw_file_upload.institution_id
  AND thumbnail_file_upload.institution_id=raw_file_upload.institution_id
  AND (
    (crop_file_upload.file_upload_type_id='IMAGE_16X9'
      AND thumbnail_file_upload.file_upload_type_id='IMAGE_THUMBNAIL_16X9')
    OR (crop_file_upload.file_upload_type_id='IMAGE_4X3'
      AND thumbnail_file_upload.file_upload_type_id='IMAGE_THUMBNAIL_4X3')
    OR (crop_file_upload.file_upload_type_id='IMAGE_1X1'
      AND thumbnail_file_upload.file_upload_type_id='IMAGE_THUMBNAIL_1X1')
  )
), current_crops AS (
  SELECT *
  FROM current_crop_candidates
  WHERE candidate_order=1
)
SELECT
  old_crop.image_id AS superseded_crop_image_id,
  current_crop.current_crop_image_id,
  current_crop.current_crop_file_upload_id,
  current_crop.institution_id
FROM current_crops current_crop
JOIN image old_crop ON old_crop.source_image_id=current_crop.raw_image_id
JOIN file_upload old_crop_file_upload ON old_crop_file_upload.file_upload_id=old_crop.file_upload_id
WHERE old_crop.image_id<>current_crop.current_crop_image_id
AND old_crop_file_upload.institution_id=current_crop.institution_id
AND old_crop_file_upload.file_upload_status_id='UPLOADED'
AND old_crop_file_upload.file_upload_type_id=current_crop.crop_file_upload_type_id;

CREATE UNIQUE INDEX media_image_crop_replacement_260_superseded_idx
ON media_image_crop_replacement_260(superseded_crop_image_id);

UPDATE content c
SET image_id=replacement.current_crop_image_id,
    image_file_upload_id=replacement.current_crop_file_upload_id
FROM media_image_crop_replacement_260 replacement
WHERE c.image_id=replacement.superseded_crop_image_id
AND c.owner_institution_id=replacement.institution_id
AND c.deleted_flag=FALSE;

UPDATE group_session gs
SET image_id=replacement.current_crop_image_id,
    image_file_upload_id=replacement.current_crop_file_upload_id
FROM media_image_crop_replacement_260 replacement
WHERE gs.image_id=replacement.superseded_crop_image_id
AND gs.institution_id=replacement.institution_id
AND gs.group_session_status_id<>'DELETED';

UPDATE page p
SET image_id=replacement.current_crop_image_id,
    image_file_upload_id=replacement.current_crop_file_upload_id
FROM media_image_crop_replacement_260 replacement
WHERE p.image_id=replacement.superseded_crop_image_id
AND p.institution_id=replacement.institution_id
AND p.deleted_flag=FALSE;

UPDATE page_row_column prc
SET image_id=replacement.current_crop_image_id,
    image_file_upload_id=replacement.current_crop_file_upload_id
FROM media_image_crop_replacement_260 replacement,
     page_row pr,
     page_section ps,
     page p
WHERE prc.image_id=replacement.superseded_crop_image_id
AND prc.page_row_id=pr.page_row_id
AND pr.page_section_id=ps.page_section_id
AND ps.page_id=p.page_id
AND p.institution_id=replacement.institution_id
AND p.deleted_flag=FALSE
AND ps.deleted_flag=FALSE
AND pr.deleted_flag=FALSE;

UPDATE page_row_call_to_action prcta
SET image_id=replacement.current_crop_image_id,
    image_file_upload_id=replacement.current_crop_file_upload_id
FROM media_image_crop_replacement_260 replacement,
     page_row pr,
     page_section ps,
     page p
WHERE prcta.image_id=replacement.superseded_crop_image_id
AND prcta.page_row_id=pr.page_row_id
AND pr.page_section_id=ps.page_section_id
AND ps.page_id=p.page_id
AND p.institution_id=replacement.institution_id
AND p.deleted_flag=FALSE
AND ps.deleted_flag=FALSE
AND pr.deleted_flag=FALSE;

COMMIT;
