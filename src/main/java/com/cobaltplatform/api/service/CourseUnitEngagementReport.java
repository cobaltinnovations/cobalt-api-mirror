/*
 * Copyright 2021 The University of Pennsylvania and Penn Medicine
 *
 * Originally created at the University of Pennsylvania and Penn Medicine by:
 * Dr. David Asch; Dr. Lisa Bellini; Dr. Cecilia Livesey; Kelley Kugler; and Dr. Matthew Press.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cobaltplatform.api.service;

import com.pyranid.Database;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/** CSV contract for Course Unit Engagement by Account. */
@ThreadSafe
public final class CourseUnitEngagementReport {
	private static final String SQL = """
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
			""";
	private static final List<String> HEADERS = List.of(
			"account_id",
			"course_id",
			"course_title",
			"course_module_id",
			"module_title",
			"course_unit_id",
			"unit_title",
			"unit_type",
			"unit_reporting_key",
			"page_view_count",
			"session_count",
			"foreground_page_time_seconds",
			"foreground_page_time_minutes",
			"first_page_view_at",
			"last_page_view_at",
			"video_playback_seconds"
	);

	private CourseUnitEngagementReport() {}

	public static void writeCsv(@Nonnull Database database, @Nonnull String institutionId,
			@Nullable LocalDateTime startDateTime, @Nullable LocalDateTime endDateTime,
			@Nonnull ZoneId reportTimeZone, @Nonnull Writer writer) {
		requireNonNull(database);
		requireNonNull(institutionId);
		requireNonNull(reportTimeZone);
		requireNonNull(writer);
		if (startDateTime != null && endDateTime != null && startDateTime.isAfter(endDateTime))
			throw new IllegalArgumentException("Start Date must be on or before End Date.");

		List<Row> rows = database.queryForList(SQL, Row.class, institutionId, startDateTime, endDateTime, reportTimeZone.getId());
		try (CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(HEADERS.toArray(new String[0])))) {
			for (Row row : rows)
				csv.printRecord(
					row.getAccountId(),
					row.getCourseId(),
					row.getCourseTitle(),
					row.getCourseModuleId(),
					row.getModuleTitle(),
					row.getCourseUnitId(),
					row.getUnitTitle(),
					row.getUnitType(),
					row.getUnitReportingKey(),
					row.getPageViewCount(),
					row.getSessionCount(),
					row.getForegroundPageTimeSeconds(),
					row.getForegroundPageTimeMinutes(),
					row.getFirstPageViewAt(),
					row.getLastPageViewAt(),
					row.getVideoPlaybackSeconds()
				);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@NotThreadSafe
	public static class Row {
		private UUID accountId;
		private UUID courseId;
		private String courseTitle;
		private UUID courseModuleId;
		private String moduleTitle;
		private UUID courseUnitId;
		private String unitTitle;
		private String unitType;
		private String unitReportingKey;
		private Long pageViewCount;
		private Long sessionCount;
		private BigDecimal foregroundPageTimeSeconds;
		private BigDecimal foregroundPageTimeMinutes;
		private String firstPageViewAt;
		private String lastPageViewAt;
		private BigDecimal videoPlaybackSeconds;

		public UUID getAccountId() { return this.accountId; }
		public void setAccountId(UUID accountId) { this.accountId = accountId; }

		public UUID getCourseId() { return this.courseId; }
		public void setCourseId(UUID courseId) { this.courseId = courseId; }

		public String getCourseTitle() { return this.courseTitle; }
		public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }

		public UUID getCourseModuleId() { return this.courseModuleId; }
		public void setCourseModuleId(UUID courseModuleId) { this.courseModuleId = courseModuleId; }

		public String getModuleTitle() { return this.moduleTitle; }
		public void setModuleTitle(String moduleTitle) { this.moduleTitle = moduleTitle; }

		public UUID getCourseUnitId() { return this.courseUnitId; }
		public void setCourseUnitId(UUID courseUnitId) { this.courseUnitId = courseUnitId; }

		public String getUnitTitle() { return this.unitTitle; }
		public void setUnitTitle(String unitTitle) { this.unitTitle = unitTitle; }

		public String getUnitType() { return this.unitType; }
		public void setUnitType(String unitType) { this.unitType = unitType; }

		public String getUnitReportingKey() { return this.unitReportingKey; }
		public void setUnitReportingKey(String unitReportingKey) { this.unitReportingKey = unitReportingKey; }

		public Long getPageViewCount() { return this.pageViewCount; }
		public void setPageViewCount(Long pageViewCount) { this.pageViewCount = pageViewCount; }

		public Long getSessionCount() { return this.sessionCount; }
		public void setSessionCount(Long sessionCount) { this.sessionCount = sessionCount; }

		public BigDecimal getForegroundPageTimeSeconds() { return this.foregroundPageTimeSeconds; }
		public void setForegroundPageTimeSeconds(BigDecimal foregroundPageTimeSeconds) { this.foregroundPageTimeSeconds = foregroundPageTimeSeconds; }

		public BigDecimal getForegroundPageTimeMinutes() { return this.foregroundPageTimeMinutes; }
		public void setForegroundPageTimeMinutes(BigDecimal foregroundPageTimeMinutes) { this.foregroundPageTimeMinutes = foregroundPageTimeMinutes; }

		public String getFirstPageViewAt() { return this.firstPageViewAt; }
		public void setFirstPageViewAt(String firstPageViewAt) { this.firstPageViewAt = firstPageViewAt; }

		public String getLastPageViewAt() { return this.lastPageViewAt; }
		public void setLastPageViewAt(String lastPageViewAt) { this.lastPageViewAt = lastPageViewAt; }

		public BigDecimal getVideoPlaybackSeconds() { return this.videoPlaybackSeconds; }
		public void setVideoPlaybackSeconds(BigDecimal videoPlaybackSeconds) { this.videoPlaybackSeconds = videoPlaybackSeconds; }

	}
}
