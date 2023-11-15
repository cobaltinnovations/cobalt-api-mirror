BEGIN;
SELECT _v.register_patch('130-content-admin', NULL, NULL);

CREATE TABLE content_status
(content_status_id VARCHAR NOT NULL PRIMARY KEY,
 description VARCHAR NOT NULL,
 display_order INTEGER NOT NULL);

INSERT INTO content_status
(content_status_id, description, display_order)
VALUES
('DRAFT', 'Draft',1),
('SCHEDULED', 'Scheduled', 2),
('LIVE', 'Live', 3),
('EXPIRED', 'Expired', 4);

ALTER TABLE content ADD COLUMN shared_flag BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE content ADD COLUMN search_terms VARCHAR NULL;
ALTER TABLE content ADD COLUMN publish_start_date timestamptz NULL;
ALTER TABLE content ADD COLUMN publish_end_date timestamptz NULL;
ALTER TABLE content ADD COLUMN publish_recurring BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE content ADD COLUMN published BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE content ADD COLUMN file_url VARCHAR NULL;

UPDATE content SET published = TRUE where owner_institution_approval_status_id='APPROVED';
UPDATE content SET shared_flag = TRUE WHERE visibility_id = 'PUBLIC';
UPDATE content SET publish_start_date = date_created;
UPDATE content SET publish_start_date = created WHERE publish_start_date IS NULL;
ALTER TABLE content ALTER COLUMN publish_start_date SET NOT NULL;

UPDATE content SET date_created = created WHERE date_created IS NULL;
ALTER TABLE content ALTER COLUMN date_created SET NOT NULL;

DROP VIEW v_admin_content;

ALTER TABLE content DROP COLUMN archived_flag;
ALTER TABLE content DROP COLUMN owner_institution_approval_status_id;
ALTER TABLE content DROP COLUMN other_institution_approval_status_id;
ALTER TABLE content DROP COLUMN visibility_id;
ALTER TABLE content DROP COLUMN content_type_label_id;

DROP TABLE visibility;
DROP TABLE approval_status;
DROP TABLE available_status;
DROP TABLE institution_network;
DROP TABLE content_type_label;

ALTER TABLE institution_content DROP COLUMN approved_flag;

CREATE OR REPLACE VIEW v_admin_content
AS SELECT c.content_id,
    c.content_type_id,
    c.title,
    c.url,
    c.publish_start_date,
    c.publish_end_date,
    c.publish_recurring,
    CASE WHEN c.published =  false THEN 'DRAFT'
    WHEN NOW() BETWEEN c.publish_start_date AND COALESCE(c.publish_end_date, NOW() + INTERVAL '1 DAY') THEN 'LIVE'
    WHEN COALESCE(c.publish_end_date, NOW() + INTERVAL '1 DAY') < NOW() THEN 'EXPIRED'
    WHEN c.publish_start_date > NOW() THEN 'SCHEDULED'
    END as content_status_id,
    CASE WHEN c.published =  false THEN 'Draft'
    WHEN NOW() BETWEEN c.publish_start_date AND c.publish_end_date THEN 'Live'
    WHEN c.publish_end_date < NOW() THEN 'Expired'
    WHEN c.publish_start_date > NOW() THEN 'Scheduled'
    END as content_status_description,
    c.shared_flag, 
    c.search_terms,
    c.duration_in_minutes,
    c.image_url,
    c.description,
    c.author,
    c.created,
    c.last_updated,
    c.en_search_vector,
    ct.description AS content_type_description,
    ct.call_to_action,
    c.owner_institution_id,
    i.name AS owner_institution,
    c.date_created
   FROM content_type ct,
    institution i,
    content c
  WHERE c.content_type_id::text = ct.content_type_id::text 
  AND c.owner_institution_id::text = i.institution_id::text
  AND c.deleted_flag = false;

CREATE OR REPLACE VIEW v_institution_content
AS SELECT vac.*,
    it.institution_id
   FROM v_admin_content vac,
    institution_content it
  WHERE vac.content_id = it.content_id;

COMMIT;