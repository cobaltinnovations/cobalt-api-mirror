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
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Run against a disposable PostgreSQL database via COBALT_REPORT_TEST_JDBC_URL. */
public class CourseUnitEngagementReportTests {
	private PGSimpleDataSource dataSource() {
		String url = System.getenv("COBALT_REPORT_TEST_JDBC_URL");
		Assume.assumeTrue("Set COBALT_REPORT_TEST_JDBC_URL to a disposable PostgreSQL database", url != null);
		PGSimpleDataSource source = new PGSimpleDataSource();
		source.setUrl(url);
		return source;
	}

	private void withFixture(Consumer<Database> test) {
		Database database = Database.forDataSource(dataSource()).timeZone(ZoneOffset.UTC).build();
		database.transaction(() -> {
			database.execute(read("src/test/resources/reports/course-unit-engagement-fixture.sql"));
			test.accept(database);
			database.currentTransaction().get().setRollbackOnly(true);
		});
	}

	private static String read(String path) {
		try { return Files.readString(Path.of(path)); }
		catch (IOException e) { throw new UncheckedIOException(e); }
	}

	private List<CSVRecord> export(Database database, LocalDateTime start, LocalDateTime end) {
		StringWriter writer = new StringWriter();
		CourseUnitEngagementReport.writeCsv(database, "BEHAVIOR_BRIDGE", start, end, ZoneId.of("America/Los_Angeles"), writer);
		try (CSVParser csv = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(new StringReader(writer.toString()))) {
			Assert.assertEquals(16, csv.getHeaderMap().size());
			Assert.assertEquals(List.of("video_playback_seconds"), csv.getHeaderMap().keySet().stream()
					.filter(header -> header.startsWith("video_")).collect(Collectors.toList()));
			return csv.getRecords();
		} catch (IOException e) { throw new UncheckedIOException(e); }
	}

	private Map<String, CSVRecord> byTitle(List<CSVRecord> rows) {
		return rows.stream().collect(Collectors.toMap(row -> row.get("unit_title"), row -> row));
	}

	@Test
	public void allTimeAccumulatesDwellAndSeparatesPostCutoffPlayback() {
		withFixture(database -> {
			Map<String, CSVRecord> rows = byTitle(export(database, null, null));
			Assert.assertEquals(5, rows.size()); // No test/staff/other-institution or unvisited rows.
			CSVRecord info = rows.get("Information");
			Assert.assertEquals("7.5", info.get("foreground_page_time_seconds"));
			Assert.assertEquals("0.13", info.get("foreground_page_time_minutes"));
			Assert.assertEquals("2", info.get("page_view_count"));
			Assert.assertEquals("2", info.get("session_count"));
			Assert.assertEquals("", info.get("unit_reporting_key"));
			Assert.assertEquals("'=Course", info.get("course_title"));
			Assert.assertEquals("Module, one", info.get("module_title"));
			Assert.assertEquals("", info.get("video_playback_seconds"));
			CSVRecord video = rows.get("Video");
			Assert.assertEquals("55", video.get("foreground_page_time_seconds"));
			Assert.assertEquals("45", video.get("video_playback_seconds")); // Rewatch is additive.
			Assert.assertEquals("2024-12-31T19:00:00.000000-05:00", info.get("first_page_view_at"));
			Assert.assertEquals("2026-03-23T00:00:02.000000-04:00", info.get("last_page_view_at"));
		});
	}

	@Test
	public void timestampsUseConfiguredTimezoneAndDisambiguateDaylightSavingChanges() {
		withFixture(database -> {
			database.execute("""
				SET LOCAL TIME ZONE 'Asia/Tokyo';
				INSERT INTO mv_analytics_dwell_time VALUES
				('BEHAVIOR_BRIDGE', '00000000-0000-0000-0000-000000000001',
				 '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001',
				 'PAGE_VIEW_COURSE_UNIT', 5, '2026-11-01T05:30:00.123456Z'),
				('BEHAVIOR_BRIDGE', '00000000-0000-0000-0000-000000000001',
				 '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001',
				 'PAGE_VIEW_COURSE_UNIT', 5, '2026-11-01T06:30:00.654321Z');
				""");
			LocalDateTime start = LocalDateTime.parse("2026-11-01T00:00:00");
			LocalDateTime end = LocalDateTime.parse("2026-11-01T23:59:59");
			CSVRecord info = byTitle(export(database, start, end)).get("Information");
			Assert.assertEquals("2026-11-01T01:30:00.123456-04:00", info.get("first_page_view_at"));
			Assert.assertEquals("2026-11-01T01:30:00.654321-05:00", info.get("last_page_view_at"));
			Assert.assertEquals("10", info.get("foreground_page_time_seconds"));

			// The institution setting determines output, including fractional-hour offsets.
			database.execute("UPDATE institution SET time_zone = 'Asia/Kolkata' WHERE institution_id = 'BEHAVIOR_BRIDGE'");
			info = byTitle(export(database, start, end)).get("Information");
			Assert.assertEquals("2026-11-01T11:00:00.123456+05:30", info.get("first_page_view_at"));
			Assert.assertEquals("2026-11-01T12:00:00.654321+05:30", info.get("last_page_view_at"));
			Assert.assertEquals("10", info.get("foreground_page_time_seconds"));
		});
	}

	@Test
	public void passedReportTimezoneIsFallbackForOutputDatesAndPlaybackCutoff() {
		withFixture(database -> {
			database.execute("UPDATE institution SET time_zone = NULL WHERE institution_id = 'BEHAVIOR_BRIDGE'");
			Map<String, CSVRecord> rows = byTitle(export(database,
					LocalDateTime.parse("2026-03-22T21:00:00"), LocalDateTime.parse("2026-03-22T21:00:20")));
			CSVRecord info = rows.get("Information");
			Assert.assertEquals("5", info.get("foreground_page_time_seconds"));
			Assert.assertEquals("2026-03-22T21:00:02.000000-07:00", info.get("first_page_view_at"));
			Assert.assertEquals("2026-03-22T21:00:02.000000-07:00", info.get("last_page_view_at"));
			Assert.assertEquals("30", rows.get("Video").get("foreground_page_time_seconds"));
			// These events precede March 23 midnight in the fallback zone.
			Assert.assertEquals("", rows.get("Video").get("video_playback_seconds"));
		});
	}

	@Test
	public void keepsDifferentAccountsSeparate() {
		withFixture(database -> {
			database.execute("""
				INSERT INTO account VALUES ('00000000-0000-0000-0000-000000000005', 'BEHAVIOR_BRIDGE', 'PATIENT', FALSE, '2020-01-01Z');
				INSERT INTO mv_analytics_dwell_time VALUES ('BEHAVIOR_BRIDGE', '00000000-0000-0000-0000-000000000005',
				    '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001',
				    'PAGE_VIEW_COURSE_UNIT', 20, '2026-03-23T04:00:12Z');
				""");
			List<CSVRecord> rows = export(database, null, null);
			Assert.assertEquals(6, rows.size());
			List<String> infoTimes = rows.stream().filter(row -> row.get("unit_title").equals("Information"))
					.map(row -> row.get("foreground_page_time_seconds")).collect(Collectors.toList());
			Assert.assertEquals(List.of("7.5", "20"), infoTimes);
		});
	}

	@Test
	public void distinguishesMissingTimingFromRecordedZero() {
		withFixture(database -> {
			Map<String, CSVRecord> rows = byTitle(export(database, null, null));
			Assert.assertEquals("", rows.get("Missing timing").get("video_playback_seconds"));
			Assert.assertEquals("0", rows.get("Recorded zero").get("video_playback_seconds"));
			Assert.assertEquals("", rows.get("No telemetry").get("video_playback_seconds"));
		});
	}

	@Test
	public void activityDatesUseInstitutionTimezoneAndInclusiveBounds() {
		withFixture(database -> {
			Map<String, CSVRecord> rows = byTitle(export(database,
					LocalDateTime.parse("2026-03-23T00:00:00"), LocalDateTime.parse("2026-03-23T00:00:20")));
			Assert.assertEquals(4, rows.size());
			Assert.assertEquals("5", rows.get("Information").get("foreground_page_time_seconds"));
			Assert.assertEquals("30", rows.get("Video").get("foreground_page_time_seconds"));
			Assert.assertEquals("45", rows.get("Video").get("video_playback_seconds"));
			rows = byTitle(export(database, LocalDateTime.parse("2026-03-23T00:00:09"), null));
			Assert.assertEquals("15", rows.get("Video").get("video_playback_seconds")); // Excludes segment crossing start.
		});
	}

	@Test
	public void preCutoffRangeRetainsDwellAndHasNoPlayback() {
		withFixture(database -> {
			Map<String, CSVRecord> rows = byTitle(export(database, null, LocalDateTime.parse("2026-03-22T23:59:59")));
			Assert.assertEquals("25", rows.get("Video").get("foreground_page_time_seconds"));
			Assert.assertEquals("", rows.get("Video").get("video_playback_seconds"));
		});
	}

	@Test
	public void emptyRangeStillProducesHeadersAndRejectsInvertedDates() {
		withFixture(database -> {
			Assert.assertTrue(export(database, LocalDateTime.parse("2030-01-01T00:00:00"), null).isEmpty());
			Assert.assertThrows(IllegalArgumentException.class, () -> export(database,
					LocalDateTime.parse("2026-07-01T00:00:00"), LocalDateTime.parse("2026-01-01T00:00:00")));
		});
	}

	@Test
	public void manualSueQueryReturnsTheSameMetricsAsTheFormalReport() {
		withFixture(database -> {
			List<CourseUnitEngagementReport.Row> manual = database.queryForList(
					read("sql/reports/behavior-bridge-sue-course-engagement.sql"), CourseUnitEngagementReport.Row.class);
			Map<String, CSVRecord> formal = byTitle(export(database, null, null));
			Assert.assertEquals(formal.size(), manual.size());
			for (CourseUnitEngagementReport.Row row : manual) {
				CSVRecord csv = formal.get(row.getUnitTitle());
				Assert.assertEquals(row.getAccountId().toString(), csv.get("account_id"));
				Assert.assertEquals(row.getCourseTitle(), csv.get("course_title"));
				Assert.assertEquals(row.getModuleTitle(), csv.get("module_title"));
				Assert.assertEquals(row.getFirstPageViewAt(), csv.get("first_page_view_at"));
				Assert.assertEquals(row.getLastPageViewAt(), csv.get("last_page_view_at"));
				Assert.assertEquals(row.getForegroundPageTimeSeconds().toPlainString(), csv.get("foreground_page_time_seconds"));
				Assert.assertEquals(row.getVideoPlaybackSeconds() == null ? "" : row.getVideoPlaybackSeconds().toPlainString(), csv.get("video_playback_seconds"));
			}
		});
	}

	@Test
	public void operationalValidationQueryMatchesEmbeddedReportAcrossActivityWindows() {
		withFixture(database -> {
			LocalDateTime[][] windows = {
					{null, null},
					{LocalDateTime.parse("2026-03-23T00:00:00"), LocalDateTime.parse("2026-03-23T00:00:20")},
					{null, LocalDateTime.parse("2026-03-22T23:59:59")},
					{LocalDateTime.parse("2030-01-01T00:00:00"), null}
			};
			for (LocalDateTime[] window : windows) {
				List<CourseUnitEngagementReport.Row> operational = database.queryForList(
						read("sql/reports/course-unit-engagement-by-account-query.sql"), CourseUnitEngagementReport.Row.class,
						"BEHAVIOR_BRIDGE", window[0], window[1], "America/Los_Angeles");
				Map<String, CSVRecord> formal = byTitle(export(database, window[0], window[1]));
				Assert.assertEquals(formal.size(), operational.size());
				for (CourseUnitEngagementReport.Row row : operational) {
					CSVRecord csv = formal.get(row.getUnitTitle());
					Assert.assertEquals(row.getAccountId().toString(), csv.get("account_id"));
					Assert.assertEquals(row.getCourseUnitId().toString(), csv.get("course_unit_id"));
					Assert.assertEquals(row.getFirstPageViewAt(), csv.get("first_page_view_at"));
					Assert.assertEquals(row.getLastPageViewAt(), csv.get("last_page_view_at"));
					Assert.assertEquals(row.getForegroundPageTimeSeconds().toPlainString(), csv.get("foreground_page_time_seconds"));
					Assert.assertEquals(row.getForegroundPageTimeMinutes().toPlainString(), csv.get("foreground_page_time_minutes"));
					Assert.assertEquals(row.getVideoPlaybackSeconds() == null ? "" : row.getVideoPlaybackSeconds().toPlainString(), csv.get("video_playback_seconds"));
				}
			}
		});
	}

	@Test
	public void migrationRegistersTheNewReport() throws Exception {
		try (Connection connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("CREATE TEMP TABLE report_type (report_type_id text PRIMARY KEY, description text, display_order integer)");
			statement.execute("CREATE SCHEMA IF NOT EXISTS _v");
			statement.execute("CREATE OR REPLACE FUNCTION _v.register_patch(text, text, text) RETURNS void LANGUAGE SQL AS 'SELECT'");
			statement.execute(read("sql/updates/289-course-unit-account-engagement-report.sql"));
			try (var result = statement.executeQuery("SELECT description FROM report_type WHERE report_type_id = 'COURSE_UNIT_ACCOUNT_ENGAGEMENT'")) {
				Assert.assertTrue(result.next());
				Assert.assertEquals("Course Unit Engagement by Account", result.getString(1));
			}
		}
	}
}
