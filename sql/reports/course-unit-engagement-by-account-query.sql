WITH input AS (
    SELECT ?::TEXT AS institution_id,
        ?::TIMESTAMP AS start_local,
        ?::TIMESTAMP AS end_local,
        ?::TEXT AS report_time_zone
),
params AS (
    SELECT i.institution_id, COALESCE(i.time_zone, input.report_time_zone) AS time_zone,
        input.start_local AT TIME ZONE COALESCE(i.time_zone, input.report_time_zone) AS start_at,
        input.end_local AT TIME ZONE COALESCE(i.time_zone, input.report_time_zone) AS end_at,
        GREATEST(
            TIMESTAMP '2026-03-23 00:00:00' AT TIME ZONE COALESCE(i.time_zone, input.report_time_zone),
            input.start_local AT TIME ZONE COALESCE(i.time_zone, input.report_time_zone)
        ) AS video_playback_start_at
    FROM input
    JOIN institution i ON i.institution_id = input.institution_id
),
eligible_accounts AS MATERIALIZED (
    SELECT a.account_id
    FROM account a
    JOIN params p ON p.institution_id = a.institution_id
    WHERE a.role_id = 'PATIENT' AND a.test_account = FALSE
),
scoped_units AS MATERIALIZED (
    SELECT c.course_id, c.title AS course_title,
        cm.course_module_id, cm.title AS module_title,
        cu.course_unit_id, cu.title AS unit_title,
        cu.course_unit_type_id AS unit_type, cu.reporting_key AS unit_reporting_key,
        ic.display_order AS course_display_order,
        cm.display_order AS module_display_order,
        cu.display_order AS unit_display_order
    FROM params p
    JOIN institution_course ic ON ic.institution_id = p.institution_id
    JOIN course c ON c.course_id = ic.course_id
    JOIN course_module cm ON cm.course_id = c.course_id
    JOIN course_unit cu ON cu.course_module_id = cm.course_module_id
),
account_unit_dwell AS (
    SELECT mv.account_id, mv.course_unit_id,
        COUNT(*) AS page_view_count,
        COUNT(DISTINCT mv.session_id) AS session_count,
        SUM(mv.dwell_time_seconds) AS foreground_page_time_seconds,
        MIN(mv.page_viewed_at) AS first_page_view_at,
        MAX(mv.page_viewed_at) AS last_page_view_at
    FROM params p
    JOIN mv_analytics_dwell_time mv ON mv.institution_id = p.institution_id
        AND mv.page_view_type = 'PAGE_VIEW_COURSE_UNIT'
        AND (p.start_at IS NULL OR mv.page_viewed_at >= p.start_at)
        AND (p.end_at IS NULL OR mv.page_viewed_at <= p.end_at)
    JOIN eligible_accounts a ON a.account_id = mv.account_id
    JOIN scoped_units u ON u.course_unit_id = mv.course_unit_id
    GROUP BY mv.account_id, mv.course_unit_id
),
account_unit_playback AS (
    SELECT vs.account_id, vs.course_unit_id,
        SUM(vs.watched_seconds_delta) FILTER (
            WHERE vs.watched_interval_start_seconds IS NOT NULL
                AND vs.watched_interval_end_seconds IS NOT NULL
        )::NUMERIC AS video_playback_seconds
    FROM params p
    JOIN mv_analytics_course_unit_video_segment vs ON vs.institution_id = p.institution_id
        AND vs.event_at >= p.video_playback_start_at
        AND (p.end_at IS NULL OR vs.event_at <= p.end_at)
        -- Exclude boundary-crossing segments: no pre-cutoff/pre-window event
        -- may contribute duration or playback state to the exported total.
        AND (vs.previous_event_at IS NULL OR vs.previous_event_at >= p.video_playback_start_at)
    JOIN eligible_accounts a ON a.account_id = vs.account_id
    JOIN scoped_units u ON u.course_unit_id = vs.course_unit_id AND u.unit_type = 'VIDEO'
    GROUP BY vs.account_id, vs.course_unit_id
)
SELECT d.account_id,
    u.course_id, CASE WHEN LEFT(u.course_title, 1) = ANY(ARRAY['=', '+', '-', '@', CHR(9), CHR(10), CHR(13)])
        THEN CHR(39) || u.course_title ELSE u.course_title END AS course_title, u.course_module_id, CASE WHEN LEFT(u.module_title, 1) = ANY(ARRAY['=', '+', '-', '@', CHR(9), CHR(10), CHR(13)])
        THEN CHR(39) || u.module_title ELSE u.module_title END AS module_title,
    u.course_unit_id, CASE WHEN LEFT(u.unit_title, 1) = ANY(ARRAY['=', '+', '-', '@', CHR(9), CHR(10), CHR(13)])
        THEN CHR(39) || u.unit_title ELSE u.unit_title END AS unit_title, u.unit_type, CASE WHEN LEFT(u.unit_reporting_key, 1) = ANY(ARRAY['=', '+', '-', '@', CHR(9), CHR(10), CHR(13)])
        THEN CHR(39) || u.unit_reporting_key ELSE u.unit_reporting_key END AS unit_reporting_key,
    d.page_view_count, d.session_count,
    d.foreground_page_time_seconds,
    ROUND(d.foreground_page_time_seconds / 60.0, 2) AS foreground_page_time_minutes,
    -- Institution-local ISO timestamps, with the offset at each instant.
    TO_CHAR(d.first_page_view_at AT TIME ZONE p.time_zone, 'YYYY-MM-DD"T"HH24:MI:SS.US')
        || CASE WHEN offsets.first_seconds < 0 THEN '-' ELSE '+' END
        || LPAD((ABS(offsets.first_seconds) / 3600)::TEXT, 2, '0') || ':'
        || LPAD(((ABS(offsets.first_seconds) % 3600) / 60)::TEXT, 2, '0') AS first_page_view_at,
    TO_CHAR(d.last_page_view_at AT TIME ZONE p.time_zone, 'YYYY-MM-DD"T"HH24:MI:SS.US')
        || CASE WHEN offsets.last_seconds < 0 THEN '-' ELSE '+' END
        || LPAD((ABS(offsets.last_seconds) / 3600)::TEXT, 2, '0') || ':'
        || LPAD(((ABS(offsets.last_seconds) % 3600) / 60)::TEXT, 2, '0') AS last_page_view_at,
    v.video_playback_seconds
FROM account_unit_dwell d
JOIN scoped_units u ON u.course_unit_id = d.course_unit_id
CROSS JOIN params p
CROSS JOIN LATERAL (
    SELECT EXTRACT(EPOCH FROM (
        (d.first_page_view_at AT TIME ZONE p.time_zone) - (d.first_page_view_at AT TIME ZONE 'UTC')
    ))::INTEGER AS first_seconds,
    EXTRACT(EPOCH FROM (
        (d.last_page_view_at AT TIME ZONE p.time_zone) - (d.last_page_view_at AT TIME ZONE 'UTC')
    ))::INTEGER AS last_seconds
) offsets
LEFT JOIN account_unit_playback v ON v.account_id = d.account_id AND v.course_unit_id = d.course_unit_id
ORDER BY d.account_id, u.course_display_order, u.module_display_order,
    u.unit_display_order, u.course_unit_id
