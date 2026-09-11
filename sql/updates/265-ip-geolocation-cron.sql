BEGIN;
SELECT _v.register_patch('265-ip-geolocation-cron', NULL, NULL);

-- Queue new analytics IP addresses at 11:00 PM America/New_York each night.
-- Processing is dispatched after this cron transaction commits and is gated by application configuration.
INSERT INTO cron_job (
  institution_id,
  cron_expression,
  time_zone,
  callback_type,
  next_run_at
)
VALUES (
  'COBALT',
  '0 23 * * *',
  'America/New_York',
  'PROCESS_IP_GEOLOCATIONS',
  (
    (now() AT TIME ZONE 'America/New_York')::DATE
    + CASE
        WHEN (now() AT TIME ZONE 'America/New_York')::TIME < TIME '23:00' THEN 0
        ELSE 1
      END
    + TIME '23:00'
  ) AT TIME ZONE 'America/New_York'
);

COMMIT;
