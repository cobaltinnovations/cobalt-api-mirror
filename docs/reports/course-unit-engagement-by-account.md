# Course Unit Engagement by Account

Report type: `COURSE_UNIT_ACCOUNT_ENGAGEMENT`. Available in Admin → Reports to
accounts with `canViewAnalytics`. Export uses the existing authenticated,
read-replica CSV endpoint. API deployment must precede migration
`289-course-unit-account-engagement-report.sql`; deploy the web change afterward.

## Population and dates

One row per non-test `PATIENT` account × course unit with recorded page views.
Includes all currently associated courses and units, even without reporting
keys. Unvisited units and playback-only pairs without page views are omitted.
Account IDs are the same UUIDs used by other exports; email is not included.
No account-signup or onboarding-completion restriction applies.

The endpoint accepts `timeZone`, defaulting to the requesting account's time
zone. Like MCB Download and Account Timeline, this report prefers the
institution's configured time zone and uses that passed-in report zone only
when the institution setting is absent. The same effective zone controls
activity date bounds, playback cutoff, and exported timestamps.

Dates are optional, inclusive ACTIVITY timestamps in that effective time
zone. Blank bounds are unbounded; both blank means all recorded history.
Foreground dwell is attributed to the page-view timestamp, matching the
analytics dashboard, without splitting dwell at date boundaries. Playback
segments crossing the start or end of the playback window are excluded.
`first_page_view_at` and `last_page_view_at` use that effective
time zone, with an explicit UTC offset at each instant. For example, in
`America/New_York`, `2026-03-23T00:00:02.000000-04:00` is midnight local time
during daylight-saving time; winter timestamps use `-05:00`. Formatting is
independent of the database connection's session time zone.

The application query is embedded as a Java text block in
`CourseUnitEngagementReport.java`. It does not load a SQL resource at runtime.
The separate operational SQL used by the validation runner is tested against
the Java CSV output to detect drift.

## Measures

- `foreground_page_time_seconds`: sums the existing dwell materialized view
  for every unit type, including videos. Each foreground heartbeat contributes
  5 seconds, with a 2.5-second fallback for a page view without heartbeats.
  Visible-page time is an engagement proxy, not verified attention.
- `page_view_count`: recorded course-unit page-view/dwell rows, including
  repeats. `session_count`: distinct recorded session IDs for this account/unit.
- `video_playback_seconds`: content seconds consumed, including rewatches,
  using the existing `watched_seconds_delta` calculation. Never add playback
  to foreground time. Only intervals whose events are on/after March 23, 2026
  at institution-local midnight count. A later requested start replaces that
  lower bound. Segments referencing an earlier event are excluded entirely,
  so untrusted pre-cutoff events cannot contribute duration or state.
- Seconds preserve the recorded values; foreground minutes are rounded to 2 decimals.
- `video_playback_seconds` is the only exported video column. It is blank for
  non-video units, absent playback events, or missing/unusable timing intervals.
  A numeric zero means recorded timing intervals sum to zero. Playback before
  the cutoff is excluded even when foreground dwell covers all recorded history.
  The cutoff is based on Behavior Bridge's known instrumentation limitation,
  not a guarantee of complete later telemetry.

Participant-facing column description:

| Column | Description |
| --- | --- |
| `video_playback_seconds` | Recorded seconds of video content played, including repeated playback. Includes only eligible activity from March 23, 2026 onward. Blank when not applicable or usable timing data is unavailable; zero means recorded timing totals zero. This is separate from foreground page time and should not be added to it. |

The existing MCB Download report is unchanged: its video time fields still use
playback and its course time fields still mix dwell and playback. Its date
cohort differs. Compare this export to the dashboard only with matching
activity bounds, institution, account filters, and materialized-view freshness.
This report includes all participant accounts, including providers with the
`PATIENT` role, corresponding to the dashboard's All Users filter.

## Manual export before deployment

`sql/reports/behavior-bridge-sue-course-engagement.sql` runs against the existing
schema without migration 289. It defaults to all-time Behavior Bridge. Replace
the `NULL::TIMESTAMP` bounds in its `input` CTE to limit activity dates. Its
`report_time_zone` input defaults to `America/New_York`, used only if the
institution has no configured zone.

```sh
psql -X -q --csv -P footer=off -v ON_ERROR_STOP=1 \
  -f sql/reports/behavior-bridge-sue-course-engagement.sql > sue-report.csv
```

Use reporting/read-replica connection settings. Verify freshness of
`mv_analytics_dwell_time`, `mv_analytics_course_unit_video_event`, and
`mv_analytics_course_unit_video_segment` before exporting. The manual and API
queries have matching metric definitions; their parity is tested using
synthetic PostgreSQL data. Text columns neutralize spreadsheet formula prefixes.

The report performs separate dwell and playback aggregations before joining
account/unit totals. It still uses the existing synchronous CSV download path;
production-scale latency has not been measured. The manual export bypasses the
web gateway. If this narrower query exceeds the request budget, background
export generation is a separate follow-up.

## Validation

The focused UI tests cover access, API availability, all-time and bounded
requests, date clearing/validation, CSV filenames, and failed downloads.
Database tests use temporary tables and synthetic data in a disposable database:

```sh
COBALT_REPORT_TEST_JDBC_URL='jdbc:postgresql://127.0.0.1:PORT/postgres?user=USER' \
  mvn -Dtest=ReportingServiceTests,CourseUnitEngagementReportTests test
```

The SQL tests are skipped when this environment variable is absent. Use only a
disposable database: the migration test supplies a minimal `_v.register_patch`
function and a temporary report catalog. Tests exercise the query and CSV
renderer, cutoff/window boundaries, repeat visits, separate accounts, exclusions,
missing versus zero telemetry, empty output, formula-prefix handling, manual
query parity, and report registration.

## Performance and real-data reconciliation

The read-only runner `sql/reports/validate-course-unit-engagement.py` uses the
operational query in `sql/reports/course-unit-engagement-by-account-query.sql`
and standard `psql` connection settings
(`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and your usual password mechanism).
It requires Python 3 and `psql`, with no additional Python packages.
`--time-zone` supplies the fallback report zone (default `America/New_York`);
the institution setting takes precedence, matching the in-app report. Use the
same reporting database/read replica that serves the admin UI.

For Sue's all-time case:

```sh
python3 sql/reports/validate-course-unit-engagement.py performance \
  --runs 3 --plan-output /tmp/course-unit-engagement-plan.json
python3 sql/reports/validate-course-unit-engagement.py reconcile
```

For the previously discussed January–July analysis:

```sh
python3 sql/reports/validate-course-unit-engagement.py performance \
  --start-date 2026-01-01 --end-date 2026-07-31 --runs 3
python3 sql/reports/validate-course-unit-engagement.py reconcile \
  --start-date 2026-01-01 --end-date 2026-07-31
```

Also exercise a small recent period and a pre-playback-cutoff period. March 23
changes playback eligibility only; foreground dwell should reconcile throughout
historical periods. The default statement timeout is 120 seconds, configurable
with `--statement-timeout`. Each invocation uses read-only transactions; no
materialized view refresh, schema change, or index operation is performed.

Performance mode measures full SQL-to-CSV wall time, exported row/byte counts,
and server planning/execution time via `EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)`.
It includes playback aggregation, CSV encoding/transfer and local writing.
Participant-level CSVs are temporary and removed afterward. The optional plan
file preserves the full query plan for inspecting repeated scans, row-estimate
errors, join loops and disk spills. First observed runs are not guaranteed to
be cold-cache; compare them to repeats without flushing database caches.
Three runs are a smoke test, not a meaningful P95 estimate.

SQL performance is only one part of the HTTP budget. After a staging deployment
against representative data, measure the complete authenticated CSV download
through the same gateway/proxy path, including Java mapping, CSV rendering,
gzip, and transfer. Confirm the real request timeout and require substantial
headroom below it (for example, aim below 30 seconds if the budget is 60).
Measure representative repeated requests under normal load before claiming a
latency percentile. If all-time downloads approach the budget, background
export generation is preferable to treating a larger timeout as the solution.
Synthetic fixture timings do not establish production performance.

Reconciliation compares the new query to independently grouped dashboard
inputs on the same snapshot. It checks account/unit, account/module and
account/course seconds; account counts, means and medians at all three levels;
and unique account/unit rows. The reference reproduces the dashboard's All
Users filters and local-day bounds. It does not run the widget Java methods.
It deliberately preserves the dashboard's assumption that an event's account
belongs to its institution; inconsistent cross-tenant data therefore produces
a visible mismatch rather than a false pass. Output contains aggregate check
results, not participant identifiers. Require `status: PASS`, zero mismatches,
and representative nonempty data. Failures return exit code 1.

To check the actual manually generated or deployed report CSV:

```sh
python3 sql/reports/validate-course-unit-engagement.py reconcile \
  --report-csv sue-report.csv
```

Supply matching `--start-date`/`--end-date` for bounded CSVs. Validate before
materialized views or account/course metadata change: the imported CSV and
reference otherwise represent different snapshots. The CSV checker validates
public identity/time columns and embeds them as read-only values; it performs
no database import writes. Large CSV imports add parsing overhead, so use
performance mode without `--report-csv` for the benchmark.

Comparison rules for other reports:

| Existing output | Expected agreement |
| --- | --- |
| Dashboard unit dwell | Raw mean/median/count match after grouping the new CSV by unit over the same activity dates and All Users cohort. |
| Dashboard module/course dwell | First sum units per account/module or account/course, then calculate mean/median/count. Do not average unit averages or count the same account once per unit. |
| Account Timeline | Sum `dwell_time_seconds` on `PAGE_VIEW_COURSE_UNIT` rows per account/unit, using the same period and associated units. Ignore separate playback events. |
| MCB non-video unit time | Same underlying dwell values, but align its signup-date cohort, lifetime-since-signup window, and normalized reporting keys first. Its exported duration strings round seconds. |
| MCB video/course time | Equality to foreground dwell is not expected: existing video fields use playback and course totals mix measures. |

Compare authoritative seconds before duration formatting. Do not sum or
average the rounded minute columns. Missing account/unit rows represent no
recorded visit, not zero observations to add to a dashboard mean. Spot-check
actual dashboard output and one or two Account Timeline downloads in staging
as a final check of the rendered/downloaded paths.

The validation runner itself has opt-in PostgreSQL tests that prove correct
results pass and wrong totals, duplicate/missing rows and inconsistent tenant
data fail. These also exercise actual CSV input and the performance mode:

```sh
COBALT_REPORT_VALIDATION_TEST=1 python3 -m unittest discover \
  -s sql/reports -p test_course_unit_engagement_validation.py -v
```

Use `psql` settings for a disposable database for that test command. The tests
create and remove a uniquely named schema containing only synthetic fixtures;
the production-facing runner remains read-only.
