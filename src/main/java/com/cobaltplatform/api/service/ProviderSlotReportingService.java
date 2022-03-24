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

import com.cobaltplatform.api.Configuration;
import com.cobaltplatform.api.context.CurrentContext;
import com.cobaltplatform.api.context.CurrentContextExecutor;
import com.cobaltplatform.api.error.ErrorReporter;
import com.cobaltplatform.api.model.db.Institution;
import com.cobaltplatform.api.model.service.AdvisoryLock;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.pyranid.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Singleton
public class ProviderSlotReportingService implements AutoCloseable {
	@Nonnull
	private static final Long BACKGROUND_TASK_INTERVAL_IN_SECONDS;
	@Nonnull
	private static final Long BACKGROUND_TASK_INITIAL_DELAY_IN_SECONDS;

	@Nonnull
	private final Provider<BackgroundTask> backgroundTaskProvider;
	@Nonnull
	private final Object backgroundTaskLock;
	@Nonnull
	private final Logger logger;

	@Nonnull
	private Boolean backgroundTaskStarted;
	@Nullable
	private ScheduledExecutorService backgroundTaskExecutorService;

	static {
		BACKGROUND_TASK_INTERVAL_IN_SECONDS = 60L;
		BACKGROUND_TASK_INITIAL_DELAY_IN_SECONDS = 10L;
	}

	@Inject
	public ProviderSlotReportingService(@Nonnull Provider<BackgroundTask> backgroundTaskProvider) {
		requireNonNull(backgroundTaskProvider);

		this.backgroundTaskLock = new Object();
		this.backgroundTaskStarted = false;
		this.backgroundTaskProvider = backgroundTaskProvider;
		this.logger = LoggerFactory.getLogger(getClass());
	}

	@Override
	public void close() throws Exception {
		stopBackgroundTask();
	}

	@Nonnull
	public Boolean startBackgroundTask() {
		synchronized (getBackgroundTaskLock()) {
			if (isBackgroundTaskStarted())
				return false;

			getLogger().trace("Starting provider slot reporting background task...");

			this.backgroundTaskExecutorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("provider-slot-reporting-background-task").build());
			this.backgroundTaskStarted = true;

			getBackgroundTaskExecutorService().get().scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					try {
						getBackgroundTaskProvider().get().run();
					} catch (Exception e) {
						getLogger().warn(format("Unable to complete provider slot reporting background task - will retry in %s seconds", String.valueOf(getBackgroundTaskIntervalInSeconds())), e);
					}
				}
			}, getBackgroundTaskInitialDelayInSeconds(), getBackgroundTaskIntervalInSeconds(), TimeUnit.SECONDS);

			getLogger().trace("Provider slot reporting background task started.");

			return true;
		}
	}

	@Nonnull
	public Boolean stopBackgroundTask() {
		synchronized (getBackgroundTaskLock()) {
			if (!isBackgroundTaskStarted())
				return false;

			getLogger().trace("Stopping provider slot reporting background task...");

			getBackgroundTaskExecutorService().get().shutdownNow();
			this.backgroundTaskExecutorService = null;
			this.backgroundTaskStarted = false;

			getLogger().trace("Provider slot reporting background task stopped.");

			return true;
		}
	}

	@ThreadSafe
	public static class BackgroundTask implements Runnable {
		@Nonnull
		private final SystemService systemService;
		@Nonnull
		private final InstitutionService institutionService;
		@Nonnull
		private final CurrentContextExecutor currentContextExecutor;
		@Nonnull
		private final ErrorReporter errorReporter;
		@Nonnull
		private final Database database;
		@Nonnull
		private final Configuration configuration;
		@Nonnull
		private final Logger logger;

		@Inject
		public BackgroundTask(@Nonnull SystemService systemService,
													@Nonnull InstitutionService institutionService,
													@Nonnull CurrentContextExecutor currentContextExecutor,
													@Nonnull ErrorReporter errorReporter,
													@Nonnull Database database,
													@Nonnull Configuration configuration) {
			requireNonNull(systemService);
			requireNonNull(institutionService);
			requireNonNull(currentContextExecutor);
			requireNonNull(errorReporter);
			requireNonNull(database);
			requireNonNull(configuration);

			this.systemService = systemService;
			this.institutionService = institutionService;
			this.currentContextExecutor = currentContextExecutor;
			this.errorReporter = errorReporter;
			this.database = database;
			this.configuration = configuration;
			this.logger = LoggerFactory.getLogger(getClass());
		}

		@Override
		public void run() {
			// Use advisory lock to ensure we don't have multiple nodes working on provider slot reporting at one time
			getSystemService().performAdvisoryLockOperationIfAvailable(AdvisoryLock.PROVIDER_SLOT_REPORTING, () -> {
				CurrentContext currentContext = new CurrentContext.Builder(getConfiguration().getDefaultLocale(), getConfiguration().getDefaultTimeZone()).build();

				getCurrentContextExecutor().execute(currentContext, () -> {
					for (Institution institution : getInstitutionService().findInstitutions()) {
						ZoneId timeZone = institution.getTimeZone();
						LocalDateTime currentDateTime = LocalDateTime.now(timeZone);
						LocalDate currentDate = currentDateTime.toLocalDate();
						LocalTime currentTimeOfDay = currentDateTime.toLocalTime();
						LocalDateTime lastRunDateTime = LocalDateTime.ofInstant(institution.getProviderSlotReportingLastRun(), timeZone);
						LocalDate lastRunDate = lastRunDateTime.toLocalDate();
						LocalTime reportingTimeOfDay = institution.getProviderSlotReportingTimeOfDay();

						getLogger().trace("Institution {}: currently {} in time zone {}.  Last run of provider slot reporting task was {}. " +
								"Reporting time of day is on or after {}.", institution.getInstitutionId().name(), currentDateTime, timeZone.getId(), lastRunDateTime, reportingTimeOfDay);

						// Don't run twice on the same date
						if (!lastRunDate.isBefore(currentDate)) {
							getLogger().trace("Last run date in {} is {}, which is on or after current date {}. Skipping...", timeZone, lastRunDate, currentDate);
							continue;
						}

						// Don't run before it's the appropriate time of day
						if (currentTimeOfDay.isBefore(reportingTimeOfDay)) {
							getLogger().trace("Current time in {} is {}, which is before reporting time {}. Skipping...", timeZone, currentTimeOfDay, reportingTimeOfDay);
							continue;
						}

						getLogger().debug("Syncing provider open slots for institution {}. Last run was {}, current time in {} is {}, which is after reporting time {}.",
								institution.getInstitutionId().name(), lastRunDateTime, timeZone, currentTimeOfDay, reportingTimeOfDay);

						// TODO: query for open slots and insert data into this table:

						// CREATE TABLE provider_open_slot_reporting (
						//	provider_slot_reporting_id SERIAL PRIMARY KEY,
						//	provider_id UUID NOT NULL REFERENCES provider,
						//	scheduling_system_id TEXT NOT NULL REFERENCES scheduling_system,
						//	visit_type_id TEXT NOT NULL REFERENCES visit_type,
						//	appointment_type_id UUID NOT NULL references appointment_type,
						//	provider_name TEXT NOT NULL,
						//	appointment_type_name TEXT NOT NULL,
						//	time_zone TEXT NOT NULL,
						//	start_date_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
						//	end_date_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
						//	epic_visit_type_id TEXT,
						//	epic_visit_type_id_type TEXT,
						//	recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
						//);
					}
				});
			});
		}

		@Nonnull
		protected SystemService getSystemService() {
			return systemService;
		}

		@Nonnull
		protected InstitutionService getInstitutionService() {
			return institutionService;
		}

		@Nonnull
		protected CurrentContextExecutor getCurrentContextExecutor() {
			return currentContextExecutor;
		}

		@Nonnull
		protected ErrorReporter getErrorReporter() {
			return errorReporter;
		}

		@Nonnull
		protected Database getDatabase() {
			return database;
		}

		@Nonnull
		protected Configuration getConfiguration() {
			return configuration;
		}

		@Nonnull
		protected Logger getLogger() {
			return logger;
		}
	}

	@Nonnull
	public Boolean isBackgroundTaskStarted() {
		synchronized (getBackgroundTaskLock()) {
			return backgroundTaskStarted;
		}
	}

	@Nonnull
	protected Long getBackgroundTaskIntervalInSeconds() {
		return BACKGROUND_TASK_INTERVAL_IN_SECONDS;
	}

	@Nonnull
	protected Long getBackgroundTaskInitialDelayInSeconds() {
		return BACKGROUND_TASK_INITIAL_DELAY_IN_SECONDS;
	}

	@Nonnull
	protected Provider<BackgroundTask> getBackgroundTaskProvider() {
		return backgroundTaskProvider;
	}

	@Nonnull
	protected Object getBackgroundTaskLock() {
		return backgroundTaskLock;
	}

	@Nonnull
	protected Optional<ScheduledExecutorService> getBackgroundTaskExecutorService() {
		return Optional.ofNullable(backgroundTaskExecutorService);
	}

	@Nonnull
	protected Logger getLogger() {
		return logger;
	}
}
