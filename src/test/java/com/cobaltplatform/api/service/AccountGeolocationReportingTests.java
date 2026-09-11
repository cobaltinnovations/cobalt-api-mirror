/*
 * Copyright 2026 Cobalt Innovations, Inc.
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

import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.AccountCapabilityType.AccountCapabilityTypeId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.ReportType.ReportTypeId;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ThreadSafe
public class AccountGeolocationReportingTests {
	@Test
	public void analyticsViewersCanDiscoverAndRunInstitutionScopedAccountGeolocationReport() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			AccountService accountService = app.getInjector().getInstance(AccountService.class);
			AuthorizationService authorizationService = app.getInjector().getInstance(AuthorizationService.class);
			ReportingService reportingService = app.getInjector().getInstance(ReportingService.class);
			UUID accountId = database.queryForObject("""
					SELECT account_id
					FROM account
					WHERE institution_id=?
					ORDER BY created
					LIMIT 1
					""", UUID.class, InstitutionId.COBALT).orElseThrow();

			// Keep the account capability set non-null while verifying the analytics-specific permission boundary.
			database.execute("""
					INSERT INTO account_capability (account_id, account_capability_type_id)
					VALUES (?, ?)
					ON CONFLICT (account_id, account_capability_type_id) DO NOTHING
					""", accountId, AccountCapabilityTypeId.CONTENT_ADMIN);
			database.execute("""
					DELETE FROM account_capability
					WHERE account_id=?
					AND account_capability_type_id=?
					""", accountId, AccountCapabilityTypeId.ANALYTICS_VIEWER);

			Account accountWithoutAnalyticsAccess = accountService.findAccountById(accountId).orElseThrow();
			Assert.assertFalse(authorizationService.canViewReportTypeId(accountWithoutAnalyticsAccess,
					ReportTypeId.ACCOUNT_GEOLOCATION));
			Assert.assertFalse(availableReportTypeIds(reportingService, accountWithoutAnalyticsAccess)
					.contains(ReportTypeId.ACCOUNT_GEOLOCATION));

			database.execute("""
					INSERT INTO account_capability (account_id, account_capability_type_id)
					VALUES (?, ?)
					""", accountId, AccountCapabilityTypeId.ANALYTICS_VIEWER);
			Account analyticsViewer = accountService.findAccountById(accountId).orElseThrow();
			Assert.assertTrue(authorizationService.canViewReportTypeId(analyticsViewer,
					ReportTypeId.ACCOUNT_GEOLOCATION));
			Assert.assertTrue(availableReportTypeIds(reportingService, analyticsViewer)
					.contains(ReportTypeId.ACCOUNT_GEOLOCATION));

			String reportIpAddress = "198.51.100.221";
			String otherInstitutionIpAddress = "198.51.100.222";
			UUID clientDeviceId = createClientDevice(database);
			Instant eventTimestamp = Instant.now();
			database.execute("DELETE FROM ip_geolocation WHERE ip_address IN (CAST(? AS INET), CAST(? AS INET))",
					reportIpAddress, otherInstitutionIpAddress);
			database.execute("""
					INSERT INTO ip_geolocation (
						ip_address,
						ip_geolocation_status_id,
						country_code,
						country_name,
						last_lookup_attempted_at,
						last_lookup_succeeded_at
					) VALUES (CAST(? AS INET), 'SUCCEEDED', 'TC', 'Test Country', now(), now())
					""", reportIpAddress);
			createAnalyticsNativeEvent(database, clientDeviceId, accountId, InstitutionId.COBALT,
					reportIpAddress, eventTimestamp);
			createAnalyticsNativeEvent(database, clientDeviceId, accountId, InstitutionId.COBALT_COURSES,
					otherInstitutionIpAddress, eventTimestamp);

			ZoneId reportTimeZone = ZoneId.of("America/New_York");
			LocalDateTime eventDateTime = LocalDateTime.ofInstant(eventTimestamp, reportTimeZone);
			StringWriter writer = new StringWriter();
			reportingService.runAccountGeolocationReportCsv(InstitutionId.COBALT,
					eventDateTime.minusMinutes(1), eventDateTime.plusMinutes(1), reportTimeZone, Locale.US, writer);

			String csv = writer.toString();
			Assert.assertTrue(csv.contains("ip_geolocation_status_id"));
			Assert.assertTrue(csv.contains(reportIpAddress));
			Assert.assertTrue(csv.contains("Test Country"));
			Assert.assertFalse(csv.contains(otherInstitutionIpAddress));
		});
	}

	@Nonnull
	private static List<ReportTypeId> availableReportTypeIds(@Nonnull ReportingService reportingService,
																										 @Nonnull Account account) {
		return reportingService.findReportTypesAvailableForAccount(account).stream()
				.map(reportType -> reportType.getReportTypeId())
				.toList();
	}

	@Nonnull
	private static UUID createClientDevice(@Nonnull Database database) {
		UUID clientDeviceId = UUID.randomUUID();
		database.execute("""
				INSERT INTO client_device (client_device_id, client_device_type_id, fingerprint, last_updated)
				VALUES (?, 'WEB_BROWSER', ?, now())
				""", clientDeviceId, UUID.randomUUID());
		return clientDeviceId;
	}

	private static void createAnalyticsNativeEvent(@Nonnull Database database,
																										@Nonnull UUID clientDeviceId,
																										@Nonnull UUID accountId,
																										@Nonnull InstitutionId institutionId,
																										@Nonnull String ipAddress,
																										@Nonnull Instant timestamp) {
		database.execute("""
				INSERT INTO analytics_native_event (
					analytics_native_event_id,
					analytics_native_event_type_id,
					institution_id,
					client_device_id,
					account_id,
					session_id,
					timestamp,
					timestamp_epoch_second,
					timestamp_epoch_second_nano_offset,
					data,
					app_name,
					app_version,
					client_device_supported_locales,
					ip_address
				) VALUES (?, 'SESSION_STARTED', ?, ?, ?, ?, ?, ?, ?, '{}'::JSONB, 'Test', '1', '[]'::JSONB,
					CAST(? AS INET))
				""", UUID.randomUUID(), institutionId, clientDeviceId, accountId, UUID.randomUUID(), Timestamp.from(timestamp),
				timestamp.getEpochSecond(), timestamp.getNano(), ipAddress);
	}
}
