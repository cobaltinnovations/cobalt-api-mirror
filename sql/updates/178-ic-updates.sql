BEGIN;
SELECT _v.register_patch('178-ic-updates', NULL, NULL);

CREATE TABLE day_of_week (
  day_of_week_id TEXT NOT NULL,
  description TEXT NOT NULL
);

INSERT INTO day_of_week (day_of_week_id, description) VALUES ('SUNDAY', 'Sunday');
INSERT INTO day_of_week (day_of_week_id, description) VALUES ('MONDAY', 'Monday');
INSERT INTO day_of_week (day_of_week_id, description) VALUES ('TUESDAY', 'Tuesday');
INSERT INTO day_of_week (day_of_week_id, description) VALUES ('WEDNESDAY', 'Wednesday');
INSERT INTO day_of_week (day_of_week_id, description) VALUES ('THURSDAY', 'Thursday');
INSERT INTO day_of_week (day_of_week_id, description) VALUES ('FRIDAY', 'Friday');
INSERT INTO day_of_week (day_of_week_id, description) VALUES ('SATURDAY', 'Saturday');

-- Some Epic providers don't permit appointments for a particular schedule/day-of-week combination.
-- For example: "I don't take appointments on Monday morning schedules and Wednesday wednesday afternoon schedules"
CREATE TABLE epic_provider_schedule_date_block (
	epic_provider_schedule_date_block_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
	epic_provider_schedule_id UUID NOT NULL REFERENCES epic_provider_schedule,
	provider_id UUID NOT NULL REFERENCES provider,
	day_of_week_id TEXT NOT NULL REFERENCES day_of_week,
	created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER set_last_updated BEFORE INSERT OR UPDATE ON epic_provider_schedule_date_block FOR EACH ROW EXECUTE PROCEDURE set_last_updated();
CREATE UNIQUE INDEX epic_provider_schedule_date_block_unique_idx ON epic_provider_schedule_date_block USING btree (epic_provider_schedule_id, provider_id, day_of_week_id);

COMMIT;