BEGIN;
SELECT _v.register_patch('202-courses', NULL, NULL);

-- Introduce "course" feature
INSERT INTO feature (feature_id, navigation_header_id, name, url_name) VALUES
	('COURSE', 'CONNECT_WITH_SUPPORT', 'Complete self-guided coursework', '/courses');

CREATE TABLE course (
	course_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
	title TEXT NOT NULL,
	description TEXT NOT NULL, -- Can include HTML
	focus TEXT NOT NULL, -- Can include HTML
	image_url TEXT NOT NULL,
	created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER set_last_updated BEFORE INSERT OR UPDATE ON course FOR EACH ROW EXECUTE PROCEDURE set_last_updated();

CREATE TABLE course_module (
	course_module_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
	title TEXT NOT NULL,
	description TEXT NOT NULL, -- Can include HTML
	focus TEXT NOT NULL, -- Can include HTML
	image_url TEXT NOT NULL,
	created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER set_last_updated BEFORE INSERT OR UPDATE ON course_module FOR EACH ROW EXECUTE PROCEDURE set_last_updated();



-- course_unit

-- Associate courses with institutions
CREATE TABLE institution_course (
  institution_id VARCHAR NOT NULL REFERENCES institution,
	course_id UUID NOT NULL REFERENCES course,
	url_name TEXT NOT NULL,
	display_order INTEGER NOT NULL,
	created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	PRIMARY KEY (course_id, institution_id)
);

-- Can't duplicate display orders per-institution
CREATE UNIQUE INDEX idx_institution_course_display_order ON institution_course (institution_id, display_order);

-- Can't duplicate URL names per-institution
CREATE UNIQUE INDEX idx_institution_course_url_name ON institution_course (institution_id, url_name);

CREATE TRIGGER set_last_updated BEFORE INSERT OR UPDATE ON institution_course FOR EACH ROW EXECUTE PROCEDURE set_last_updated();

COMMIT;