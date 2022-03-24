BEGIN;
SELECT _v.register_patch('020-provider-slot-reporting', NULL, NULL);

-- Captures information about provider availability "slots" at a particular point in time so we can report over it.
-- In practice, a job wakes up nightly in the institution's time zone and looks at what the open slots were for that day.
CREATE TABLE provider_open_slot_reporting (
	provider_slot_reporting_id SERIAL PRIMARY KEY,
	provider_id UUID NOT NULL REFERENCES provider,
	scheduling_system_id TEXT NOT NULL REFERENCES scheduling_system,
	visit_type_id TEXT NOT NULL REFERENCES visit_type,
	appointment_type_id UUID NOT NULL references appointment_type,
	provider_name TEXT NOT NULL,
	appointment_type_name TEXT NOT NULL,
	time_zone TEXT NOT NULL,
	start_date_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
	end_date_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
	epic_visit_type_id TEXT,
	epic_visit_type_id_type TEXT,
	recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE institution ADD COLUMN provider_slot_reporting_last_run TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT '1970-01-01 00:00:00'::TIMESTAMP;
ALTER TABLE institution ADD COLUMN provider_slot_reporting_time_of_day TIME NOT NULL DEFAULT '23:00:00'::TIME;

END;