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
import com.cobaltplatform.api.error.ConsoleErrorReporter;
import com.cobaltplatform.api.integration.enterprise.CobaltEnterprisePlugin;
import com.cobaltplatform.api.integration.ipstack.IpstackClient;
import com.cobaltplatform.api.integration.ipstack.IpstackStandardLookupRequest;
import com.cobaltplatform.api.integration.ipstack.IpstackStandardLookupResponse;
import com.cobaltplatform.api.integration.ipstack.IpstackStandardLookupResponse.ErrorDetails;
import com.cobaltplatform.api.model.db.CronJob;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.IpGeolocationStatus.IpGeolocationStatusId;
import com.cobaltplatform.api.util.JsonMapper;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ThreadSafe
public class IpGeolocationServiceTests {
	@Test
	public void disabledCronDoesNotDiscoverIpAddresses() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			CobaltEnterprisePlugin enterprisePlugin = app.getInjector().getInstance(CobaltEnterprisePlugin.class);
			UUID clientDeviceId = createClientDevice(database);
			String ipAddress = "203.0.113.223";

			database.execute("DELETE FROM ip_geolocation WHERE ip_address=CAST(? AS INET)", ipAddress);
			createAnalyticsNativeEvent(database, clientDeviceId, ipAddress);

			CronJob cronJob = new CronJob();
			cronJob.setCallbackType(CobaltEnterprisePlugin.PROCESS_IP_GEOLOCATIONS_CRON_CALLBACK_TYPE);
			enterprisePlugin.runCronJob(cronJob);

			Assert.assertEquals(Long.valueOf(0L), database.queryForObject("""
					SELECT COUNT(*)
					FROM ip_geolocation
					WHERE ip_address=CAST(? AS INET)
					""", Long.class, ipAddress).orElseThrow());
		});
	}

	@Test
	public void discoversOnlyUniqueMissingAnalyticsIpAddresses() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			DatabaseProvider databaseProvider = app.getInjector().getInstance(DatabaseProvider.class);
			Database database = databaseProvider.getWritableMasterDatabase();
			IpGeolocationService ipGeolocationService = app.getInjector().getInstance(IpGeolocationService.class);
			UUID clientDeviceId = createClientDevice(database);
			String newIpAddress = "198.51.100.211";
			String existingIpAddress = "198.51.100.212";

			// Make discovery deterministic even when the local fixture already contains analytics events.
			database.execute("""
					INSERT INTO ip_geolocation (ip_address)
					SELECT DISTINCT ip_address
					FROM analytics_native_event
					WHERE ip_address IS NOT NULL
					ON CONFLICT (ip_address) DO NOTHING
					""");
			database.execute("DELETE FROM ip_geolocation WHERE ip_address IN (CAST(? AS INET), CAST(? AS INET))",
					newIpAddress, existingIpAddress);
			database.execute("""
					INSERT INTO ip_geolocation (ip_address, ip_geolocation_status_id, country_code)
					VALUES (CAST(? AS INET), ?, 'US')
					""", existingIpAddress, IpGeolocationStatusId.SUCCEEDED);

			createAnalyticsNativeEvent(database, clientDeviceId, newIpAddress);
			createAnalyticsNativeEvent(database, clientDeviceId, newIpAddress);
			createAnalyticsNativeEvent(database, clientDeviceId, existingIpAddress);
			createAnalyticsNativeEvent(database, clientDeviceId, null);

			long enqueuedCount = ipGeolocationService.enqueueAnalyticsNativeEventIpAddressesAndProcessAfterCommit();

			Assert.assertEquals(1L, enqueuedCount);
			Assert.assertEquals(IpGeolocationStatusId.PENDING, statusFor(database, newIpAddress));
			Assert.assertEquals(IpGeolocationStatusId.SUCCEEDED, statusFor(database, existingIpAddress));
			Assert.assertEquals("US", database.queryForObject("""
					SELECT country_code
					FROM ip_geolocation
					WHERE ip_address=CAST(? AS INET)
					""", String.class, existingIpAddress).orElseThrow());
		});
	}

	@Test
	public void drainsAllPendingBatchesWithoutRetryingFailedRows() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			DatabaseProvider databaseProvider = app.getInjector().getInstance(DatabaseProvider.class);
			Database database = databaseProvider.getWritableMasterDatabase();
			String failedLookupIpAddress = "8.123.0.100";
			String previouslyFailedIpAddress = "8.123.1.1";
			String privateIpAddress = "10.234.0.1";
			RecordingIpstackClient ipstackClient = new RecordingIpstackClient(failedLookupIpAddress);
			IpGeolocationService ipGeolocationService = new IpGeolocationService(databaseProvider, ipstackClient,
					new JsonMapper(), new ConsoleErrorReporter());

			// Isolate this drain from any fixture data while retaining rollback-safe behavior.
			database.execute("""
					UPDATE ip_geolocation
					SET ip_geolocation_status_id=?
					WHERE ip_geolocation_status_id IN (?, ?)
					""", IpGeolocationStatusId.FAILED, IpGeolocationStatusId.PENDING,
					IpGeolocationStatusId.IN_PROGRESS);
			database.execute("""
					DELETE FROM ip_geolocation
					WHERE ip_address << '8.123.0.0/16'::CIDR
					OR ip_address << '10.234.0.0/16'::CIDR
					""");

			for (int i = 1; i <= 101; ++i)
				database.execute("INSERT INTO ip_geolocation (ip_address) VALUES (CAST(? AS INET))",
						"8.123.0." + i);

			database.execute("INSERT INTO ip_geolocation (ip_address) VALUES (CAST(? AS INET))", privateIpAddress);
			database.execute("""
					INSERT INTO ip_geolocation (ip_address, ip_geolocation_status_id, provider_error_type)
					VALUES (CAST(? AS INET), ?, 'PREVIOUS_FAILURE')
					""", previouslyFailedIpAddress, IpGeolocationStatusId.FAILED);

			IpGeolocationService.ProcessingResult result = ipGeolocationService.processAllPendingIpGeolocations();

			Assert.assertEquals(102, result.getClaimedCount());
			Assert.assertEquals(100, result.getSucceededCount());
			Assert.assertEquals(1, result.getFailedCount());
			Assert.assertEquals(0, result.getSkippedInvalidCount());
			Assert.assertEquals(1, result.getSkippedPrivateCount());
			Assert.assertEquals(101, ipstackClient.getRequestedIpAddresses().size());
			Assert.assertFalse(ipstackClient.getRequestedIpAddresses().contains(previouslyFailedIpAddress));
			Assert.assertEquals(IpGeolocationStatusId.FAILED, statusFor(database, failedLookupIpAddress));
			Assert.assertEquals(IpGeolocationStatusId.FAILED, statusFor(database, previouslyFailedIpAddress));
			Assert.assertEquals(IpGeolocationStatusId.SKIPPED_PRIVATE, statusFor(database, privateIpAddress));
			Assert.assertEquals(Long.valueOf(100L), database.queryForObject("""
					SELECT COUNT(*)
					FROM ip_geolocation
					WHERE ip_address << '8.123.0.0/24'::CIDR
					AND ip_geolocation_status_id=?
					""", Long.class, IpGeolocationStatusId.SUCCEEDED).orElseThrow());
		});
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
																										String ipAddress) {
		Instant timestamp = Instant.now();
		database.execute("""
				INSERT INTO analytics_native_event (
					analytics_native_event_id,
					analytics_native_event_type_id,
					institution_id,
					client_device_id,
					session_id,
					timestamp,
					timestamp_epoch_second,
					timestamp_epoch_second_nano_offset,
					data,
					app_name,
					app_version,
					client_device_supported_locales,
					ip_address
				) VALUES (?, 'SESSION_STARTED', ?, ?, ?, ?, ?, ?, '{}'::JSONB, 'Test', '1', '[]'::JSONB,
					CAST(? AS INET))
				""", UUID.randomUUID(), InstitutionId.COBALT, clientDeviceId, UUID.randomUUID(),
				Timestamp.from(timestamp), timestamp.getEpochSecond(), timestamp.getNano(), ipAddress);
	}

	@Nonnull
	private static IpGeolocationStatusId statusFor(@Nonnull Database database, @Nonnull String ipAddress) {
		return database.queryForObject("""
				SELECT ip_geolocation_status_id
				FROM ip_geolocation
				WHERE ip_address=CAST(? AS INET)
				""", IpGeolocationStatusId.class, ipAddress).orElseThrow();
	}

	@ThreadSafe
	private static class RecordingIpstackClient implements IpstackClient {
		@Nonnull
		private final String failedIpAddress;
		@Nonnull
		private final List<String> requestedIpAddresses;

		private RecordingIpstackClient(@Nonnull String failedIpAddress) {
			this.failedIpAddress = failedIpAddress;
			this.requestedIpAddresses = new ArrayList<>();
		}

		@Nonnull
		@Override
		public synchronized IpstackStandardLookupResponse performStandardLookup(
				@Nonnull IpstackStandardLookupRequest request) {
			String ipAddress = request.getIpAddress();
			getRequestedIpAddresses().add(ipAddress);

			IpstackStandardLookupResponse response = new IpstackStandardLookupResponse();
			response.setIp(ipAddress);
			response.setRawJson("{}");

			if (getFailedIpAddress().equals(ipAddress)) {
				ErrorDetails errorDetails = new ErrorDetails();
				errorDetails.setCode(500);
				errorDetails.setType("test_failure");
				errorDetails.setMessage("Test failure");
				response.setSuccess(false);
				response.setError(errorDetails);
			} else {
				response.setSuccess(true);
				response.setType("ipv4");
				response.setCountryCode("US");
				response.setCountryName("United States");
			}

			return response;
		}

		@Nonnull
		private String getFailedIpAddress() {
			return this.failedIpAddress;
		}

		@Nonnull
		private List<String> getRequestedIpAddresses() {
			return this.requestedIpAddresses;
		}
	}
}
