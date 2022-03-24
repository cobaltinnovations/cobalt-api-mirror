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

import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.model.db.Institution;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.pyranid.Database;
import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class ProviderSlotReportingServiceTests {
	@Test
	public void testProviderSlotReportingTask() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(Database.class);
			InstitutionService institutionService = app.getInjector().getInstance(InstitutionService.class);

			Institution institution = institutionService.findInstitutionById(InstitutionId.COBALT).get();
			ZoneId timeZone = institution.getTimeZone();

			LocalDateTime currentDateTime = LocalDateTime.now(timeZone);

			// Update the institution so it's overdue for a slot reporting run and also on/after the "reporting time of day"...
			// this ensures the task will run
			database.execute("UPDATE institution SET provider_slot_reporting_last_run=?, provider_slot_reporting_time_of_day=? " +
							"WHERE institution_id=?", currentDateTime.minusDays(10).atZone(timeZone).toInstant(),
					currentDateTime.toLocalTime(), institution.getInstitutionId());

			ProviderSlotReportingService.BackgroundTask backgroundTask = app.getInjector().getInstance(ProviderSlotReportingService.BackgroundTask.class);
			backgroundTask.run();
		});
	}
}
