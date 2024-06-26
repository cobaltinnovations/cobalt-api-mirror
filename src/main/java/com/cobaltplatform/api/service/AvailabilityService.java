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
import com.cobaltplatform.api.model.api.request.CreateLogicalAvailabilityRequest;
import com.cobaltplatform.api.model.api.request.ProviderFindRequest;
import com.cobaltplatform.api.model.api.request.UpdateLogicalAvailabilityRequest;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.Appointment;
import com.cobaltplatform.api.model.db.AppointmentType;
import com.cobaltplatform.api.model.db.CalendarPermission.CalendarPermissionId;
import com.cobaltplatform.api.model.db.Followup;
import com.cobaltplatform.api.model.db.Institution;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.LogicalAvailability;
import com.cobaltplatform.api.model.db.LogicalAvailabilityType.LogicalAvailabilityTypeId;
import com.cobaltplatform.api.model.db.Provider;
import com.cobaltplatform.api.model.db.RecurrenceType.RecurrenceTypeId;
import com.cobaltplatform.api.model.db.SchedulingSystem.SchedulingSystemId;
import com.cobaltplatform.api.model.service.AdvisoryLock;
import com.cobaltplatform.api.model.service.AppointmentTypeWithLogicalAvailabilityId;
import com.cobaltplatform.api.model.service.Availability;
import com.cobaltplatform.api.model.service.Block;
import com.cobaltplatform.api.model.service.ProviderCalendar;
import com.cobaltplatform.api.model.service.ProviderFind;
import com.cobaltplatform.api.model.service.ProviderFind.AvailabilityDate;
import com.cobaltplatform.api.model.service.ProviderFind.AvailabilityStatus;
import com.cobaltplatform.api.util.ValidationException;
import com.cobaltplatform.api.util.ValidationException.FieldError;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lokalized.Strings;
import com.pyranid.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.cobaltplatform.api.util.DatabaseUtility.sqlVaragsParameters;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class AvailabilityService implements AutoCloseable {
	@Nonnull
	private static final LocalDate DISTANT_FUTURE_DATE;
	@Nonnull
	private static final Long HISTORY_BACKGROUND_TASK_INTERVAL_IN_SECONDS;
	@Nonnull
	private static final Long HISTORY_BACKGROUND_TASK_INITIAL_DELAY_IN_SECONDS;
	@Nonnull
	private static final LocalTime HISTORY_BACKGROUND_TASK_RUN_START_TIME_WINDOW;
	@Nonnull
	private static final LocalTime HISTORY_BACKGROUND_TASK_RUN_END_TIME_WINDOW;

	static {
		DISTANT_FUTURE_DATE = LocalDate.of(9999, 1, 1);
		HISTORY_BACKGROUND_TASK_INTERVAL_IN_SECONDS = 60L * 20L;
		HISTORY_BACKGROUND_TASK_INITIAL_DELAY_IN_SECONDS = 10L;

		// History task is runnable during this time window in each institution's time zone
		HISTORY_BACKGROUND_TASK_RUN_START_TIME_WINDOW = LocalTime.of(22, 0);
		HISTORY_BACKGROUND_TASK_RUN_END_TIME_WINDOW = LocalTime.of(23, 0);
	}

	@Nonnull
	private final javax.inject.Provider<AppointmentService> appointmentServiceProvider;
	@Nonnull
	private final javax.inject.Provider<ProviderService> providerServiceProvider;
	@Nonnull
	private final javax.inject.Provider<FollowupService> followupServiceProvider;
	@Nonnull
	private final javax.inject.Provider<HistoryBackgroundTask> historyBackgroundTaskProvider;
	@Nonnull
	private final DatabaseProvider databaseProvider;
	@Nonnull
	private final Configuration configuration;
	@Nonnull
	private final Strings strings;
	@Nonnull
	private final Logger logger;

	@Nonnull
	private final Object historyBackgroundTaskLock;
	@Nonnull
	private Boolean historyBackgroundTaskStarted;
	@Nullable
	private ScheduledExecutorService historyBackgroundTaskExecutorService;

	@Inject
	public AvailabilityService(@Nonnull javax.inject.Provider<AppointmentService> appointmentServiceProvider,
														 @Nonnull javax.inject.Provider<ProviderService> providerServiceProvider,
														 @Nonnull javax.inject.Provider<FollowupService> followupServiceProvider,
														 @Nonnull javax.inject.Provider<HistoryBackgroundTask> historyBackgroundTaskProvider,
														 @Nonnull DatabaseProvider databaseProvider,
														 @Nonnull Configuration configuration,
														 @Nonnull Strings strings) {
		requireNonNull(appointmentServiceProvider);
		requireNonNull(providerServiceProvider);
		requireNonNull(followupServiceProvider);
		requireNonNull(historyBackgroundTaskProvider);
		requireNonNull(databaseProvider);
		requireNonNull(configuration);
		requireNonNull(strings);

		this.appointmentServiceProvider = appointmentServiceProvider;
		this.providerServiceProvider = providerServiceProvider;
		this.followupServiceProvider = followupServiceProvider;
		this.historyBackgroundTaskProvider = historyBackgroundTaskProvider;
		this.databaseProvider = databaseProvider;
		this.configuration = configuration;
		this.strings = strings;
		this.historyBackgroundTaskLock = new Object();
		this.historyBackgroundTaskStarted = false;
		this.logger = LoggerFactory.getLogger(getClass());
	}

	@Override
	public void close() throws Exception {
		stopHistoryBackgroundTask();
	}

	@Nonnull
	public Boolean startHistoryBackgroundTask() {
		synchronized (getHistoryBackgroundTaskLock()) {
			if (isHistoryBackgroundTaskStarted())
				return false;

			getLogger().trace("Starting availability history background task...");

			this.historyBackgroundTaskExecutorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("availability-history-background-task").build());
			this.historyBackgroundTaskStarted = true;

			getHistoryBackgroundTaskExecutorService().get().scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					try {
						getHistoryBackgroundTaskProvider().get().run();
					} catch (Exception e) {
						getLogger().warn(format("Unable to complete availability history background task - will retry in %s seconds", String.valueOf(getHistoryBackgroundTaskIntervalInSeconds())), e);
					}
				}
			}, getHistoryBackgroundTaskInitialDelayInSeconds(), getHistoryBackgroundTaskIntervalInSeconds(), TimeUnit.SECONDS);

			getLogger().trace("Availability history background task started.");

			return true;
		}
	}

	@Nonnull
	public Boolean stopHistoryBackgroundTask() {
		synchronized (getHistoryBackgroundTaskLock()) {
			if (!isHistoryBackgroundTaskStarted())
				return false;

			getLogger().trace("Stopping availability history background task...");

			getHistoryBackgroundTaskExecutorService().get().shutdownNow();
			this.historyBackgroundTaskExecutorService = null;
			this.historyBackgroundTaskStarted = false;

			getLogger().trace("Availability history background task stopped.");

			return true;
		}
	}

	@Nonnull
	public Optional<LogicalAvailability> findLogicalAvailabilityById(@Nullable UUID logicalAvailabilityId) {
		if (logicalAvailabilityId == null)
			return Optional.empty();

		return getDatabase().queryForObject("SELECT * FROM logical_availability WHERE logical_availability_id=?",
				LogicalAvailability.class, logicalAvailabilityId);
	}

	@Nonnull
	public UUID createLogicalAvailability(@Nonnull CreateLogicalAvailabilityRequest request) {
		requireNonNull(request);

		UUID providerId = request.getProviderId();
		UUID accountId = request.getAccountId();
		LocalDateTime startDateTime = request.getStartDateTime();
		LocalDate endDate = request.getEndDate();
		LocalTime endTime = request.getEndTime();
		List<UUID> appointmentTypeIds = request.getAppointmentTypeIds() == null ? Collections.emptyList() : request.getAppointmentTypeIds();
		LogicalAvailabilityTypeId logicalAvailabilityTypeId = request.getLogicalAvailabilityTypeId();
		RecurrenceTypeId recurrenceTypeId = request.getRecurrenceTypeId();
		boolean recurSunday = request.getRecurSunday() == null ? false : request.getRecurSunday();
		boolean recurMonday = request.getRecurMonday() == null ? false : request.getRecurMonday();
		boolean recurTuesday = request.getRecurTuesday() == null ? false : request.getRecurTuesday();
		boolean recurWednesday = request.getRecurWednesday() == null ? false : request.getRecurWednesday();
		boolean recurThursday = request.getRecurThursday() == null ? false : request.getRecurThursday();
		boolean recurFriday = request.getRecurFriday() == null ? false : request.getRecurFriday();
		boolean recurSaturday = request.getRecurSaturday() == null ? false : request.getRecurSaturday();

		ValidationException validationException = new ValidationException();

		if (providerId == null) {
			validationException.add(new FieldError("providerId", getStrings().get("Provider ID is required.")));
		} else {
			Provider provider = getProviderService().findProviderById(providerId).orElse(null);

			if (provider == null)
				validationException.add(new FieldError("providerId", getStrings().get("Provider ID is invalid.")));
		}

		if (accountId == null)
			validationException.add(new FieldError("accountId", getStrings().get("Account ID is required.")));

		if (startDateTime == null)
			validationException.add(new FieldError("startDateTime", getStrings().get("Start date/time is required.")));

		if (endTime == null)
			validationException.add(new FieldError("endTime", getStrings().get("End time is required.")));

		if (endDate == null && recurrenceTypeId == RecurrenceTypeId.NONE)
			validationException.add(new FieldError("endDate", getStrings().get("End date is required.")));

		List<AppointmentType> appointmentTypes = appointmentTypeIds.stream()
				.filter(appointmentTypeId -> appointmentTypeId != null)
				.distinct()
				.map(appointmentTypeId -> getAppointmentService().findAppointmentTypeById(appointmentTypeId).get())
				.collect(Collectors.toList());

		if (startDateTime != null && endDate != null && endTime != null && !LocalDateTime.of(endDate, endTime).isAfter(startDateTime))
			validationException.add(getStrings().get("End time must be after start time."));

		if (logicalAvailabilityTypeId == null)
			validationException.add(new FieldError("logicalAvailabilityTypeId", getStrings().get("Availability type is required.")));

		if (recurrenceTypeId == null) {
			validationException.add(new FieldError("recurrenceTypeId", getStrings().get("Recurrence type is required.")));
		} else {
			if (recurrenceTypeId == RecurrenceTypeId.NONE) {
				recurSunday = false;
				recurMonday = false;
				recurTuesday = false;
				recurWednesday = false;
				recurThursday = false;
				recurFriday = false;
				recurSaturday = false;
			} else if (recurrenceTypeId == RecurrenceTypeId.DAILY) {
				if (!recurSunday
						&& !recurMonday
						&& !recurTuesday
						&& !recurWednesday
						&& !recurThursday
						&& !recurFriday
						&& !recurSaturday)
					validationException.add(new FieldError("recurrenceTypeId", getStrings().get("You must specify at least one recurrence day.")));
			} else {
				validationException.add(new FieldError("recurrenceTypeId", getStrings().get("Unsupported recurrence type was specified.")));
			}
		}

		if (validationException.hasErrors())
			throw validationException;

		UUID logicalAvailabilityId = UUID.randomUUID();

		// We have an arbitrary distant future date we use to indicate a recurrence with no practical end (recurs "forever").
		// From an API perspective - they see end date as null, but internally we store a non-null value
		LocalDateTime endDateTime = LocalDateTime.of(endDate == null ? getDistantFutureDate() : endDate, endTime);

		getDatabase().execute("INSERT INTO logical_availability(logical_availability_id, provider_id, start_date_time, " +
						"end_date_time, logical_availability_type_id, recurrence_type_id, recur_sunday, recur_monday, recur_tuesday, " +
						"recur_wednesday, recur_thursday, recur_friday, recur_saturday, created_by_account_id, last_updated_by_account_id) " +
						"VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
				logicalAvailabilityId, providerId, startDateTime, endDateTime, logicalAvailabilityTypeId, recurrenceTypeId,
				recurSunday, recurMonday, recurTuesday, recurWednesday, recurThursday, recurFriday, recurSaturday, accountId, accountId);

		// Note: if no appointment types, any active appointment type for the provider is bookable
		for (AppointmentType appointmentType : appointmentTypes)
			getDatabase().execute("INSERT INTO logical_availability_appointment_type(logical_availability_id, appointment_type_id) VALUES (?,?)",
					logicalAvailabilityId, appointmentType.getAppointmentTypeId());

		return logicalAvailabilityId;
	}

	@Nonnull
	public Boolean updateLogicalAvailability(@Nonnull UpdateLogicalAvailabilityRequest request) {
		requireNonNull(request);

		UUID logicalAvailabilityId = request.getLogicalAvailabilityId();
		UUID providerId = request.getProviderId();
		UUID accountId = request.getAccountId();
		LocalDateTime startDateTime = request.getStartDateTime();
		LocalDate endDate = request.getEndDate();
		LocalTime endTime = request.getEndTime();
		List<UUID> appointmentTypeIds = request.getAppointmentTypeIds() == null ? Collections.emptyList() : request.getAppointmentTypeIds();
		LogicalAvailabilityTypeId logicalAvailabilityTypeId = request.getLogicalAvailabilityTypeId();
		RecurrenceTypeId recurrenceTypeId = request.getRecurrenceTypeId();
		boolean recurSunday = request.getRecurSunday() == null ? false : request.getRecurSunday();
		boolean recurMonday = request.getRecurMonday() == null ? false : request.getRecurMonday();
		boolean recurTuesday = request.getRecurTuesday() == null ? false : request.getRecurTuesday();
		boolean recurWednesday = request.getRecurWednesday() == null ? false : request.getRecurWednesday();
		boolean recurThursday = request.getRecurThursday() == null ? false : request.getRecurThursday();
		boolean recurFriday = request.getRecurFriday() == null ? false : request.getRecurFriday();
		boolean recurSaturday = request.getRecurSaturday() == null ? false : request.getRecurSaturday();

		ValidationException validationException = new ValidationException();

		if (logicalAvailabilityId == null)
			validationException.add(new FieldError("logicalAvailabilityId", getStrings().get("Logical Availability ID is required.")));

		if (providerId == null) {
			validationException.add(new FieldError("providerId", getStrings().get("Provider ID is required.")));
		} else {
			Provider provider = getProviderService().findProviderById(providerId).orElse(null);

			if (provider == null)
				validationException.add(new FieldError("providerId", getStrings().get("Provider ID is invalid.")));
		}

		if (accountId == null)
			validationException.add(new FieldError("accountId", getStrings().get("Account ID is required.")));

		if (startDateTime == null)
			validationException.add(new FieldError("startDateTime", getStrings().get("Start date/time is required.")));

		if (endTime == null)
			validationException.add(new FieldError("endTime", getStrings().get("End time is required.")));

		if (endDate == null && recurrenceTypeId == RecurrenceTypeId.NONE)
			validationException.add(new FieldError("endDate", getStrings().get("End date is required.")));

		List<AppointmentType> appointmentTypes = appointmentTypeIds.stream()
				.filter(appointmentTypeId -> appointmentTypeId != null)
				.distinct()
				.map(appointmentTypeId -> getAppointmentService().findAppointmentTypeById(appointmentTypeId).get())
				.collect(Collectors.toList());

		if (startDateTime != null && endDate != null && endTime != null && !LocalDateTime.of(endDate, endTime).isAfter(startDateTime))
			validationException.add(getStrings().get("End time must be after start time."));

		if (logicalAvailabilityTypeId == null)
			validationException.add(new FieldError("logicalAvailabilityTypeId", getStrings().get("Availability type is required.")));

		if (recurrenceTypeId == null) {
			validationException.add(new FieldError("recurrenceTypeId", getStrings().get("Recurrence type is required.")));
		} else {
			if (recurrenceTypeId == RecurrenceTypeId.NONE) {
				recurSunday = false;
				recurMonday = false;
				recurTuesday = false;
				recurWednesday = false;
				recurThursday = false;
				recurFriday = false;
				recurSaturday = false;
			} else if (recurrenceTypeId == RecurrenceTypeId.DAILY) {
				if (!recurSunday
						&& !recurMonday
						&& !recurTuesday
						&& !recurWednesday
						&& !recurThursday
						&& !recurFriday
						&& !recurSaturday)
					validationException.add(new FieldError("recurrenceTypeId", getStrings().get("You must specify at least one recurrence day.")));
			} else {
				validationException.add(new FieldError("recurrenceTypeId", getStrings().get("Unsupported recurrence type was specified.")));
			}
		}

		if (validationException.hasErrors())
			throw validationException;

		// We have an arbitrary distant future date we use to indicate a recurrence with no practical end (recurs "forever").
		// From an API perspective - they see end date as null, but internally we store a non-null value
		LocalDateTime endDateTime = LocalDateTime.of(endDate == null ? getDistantFutureDate() : endDate, endTime);

		getDatabase().execute("UPDATE logical_availability SET provider_id=?, start_date_time=?, " +
						"end_date_time=?, logical_availability_type_id=?, recurrence_type_id=?, recur_sunday=?, recur_monday=?, recur_tuesday=?, " +
						"recur_wednesday=?, recur_thursday=?, recur_friday=?, recur_saturday=?, last_updated_by_account_id=? WHERE logical_availability_id=?",
				providerId, startDateTime, endDateTime, logicalAvailabilityTypeId, recurrenceTypeId,
				recurSunday, recurMonday, recurTuesday, recurWednesday, recurThursday, recurFriday, recurSaturday, accountId, logicalAvailabilityId);

		getDatabase().execute("DELETE FROM logical_availability_appointment_type WHERE logical_availability_id=?", logicalAvailabilityId);

		// Note: if no appointment types, any active appointment type for the provider is bookable
		for (AppointmentType appointmentType : appointmentTypes)
			getDatabase().execute("INSERT INTO logical_availability_appointment_type(logical_availability_id, appointment_type_id) VALUES (?,?)",
					logicalAvailabilityId, appointmentType.getAppointmentTypeId());

		return true;
	}

	@Nonnull
	public List<AppointmentType> findAppointmentTypesByLogicalAvailabilityId(@Nullable UUID logicalAvailabilityId) {
		if (logicalAvailabilityId == null)
			return Collections.emptyList();

		return getDatabase().queryForList("SELECT apt.* FROM v_appointment_type apt, logical_availability_appointment_type laat "
				+ "WHERE laat.logical_availability_id=? AND apt.appointment_type_id=laat.appointment_type_id", AppointmentType.class, logicalAvailabilityId);
	}

	@Nonnull
	public Boolean deleteLogicalAvailability(@Nullable UUID logicalAvailabilityId) {
		if (logicalAvailabilityId == null)
			return false;

		boolean deleted = false;

		deleted = deleted || getDatabase().execute("DELETE FROM logical_availability_appointment_type WHERE logical_availability_id=?", logicalAvailabilityId) > 0;
		deleted = deleted || getDatabase().execute("DELETE FROM logical_availability WHERE logical_availability_id=?", logicalAvailabilityId) > 0;

		return deleted;
	}

	@Nonnull
	public List<LogicalAvailability> findLogicalAvailabilities(@Nullable UUID providerId,
																														 @Nullable LogicalAvailabilityTypeId logicalAvailabilityTypeId,
																														 @Nullable LocalDate startDate,
																														 @Nullable LocalDate endDate) {
		if (providerId == null)
			return Collections.emptyList();

		StringBuilder sql = new StringBuilder("SELECT * FROM logical_availability WHERE 1=1 ");
		List<Object> parameters = new ArrayList<>();

		sql.append("AND provider_id=? ");
		parameters.add(providerId);

		if (startDate != null) {
			sql.append("AND start_date_time >= ? ");
			parameters.add(startDate.atStartOfDay());
		}

		if (endDate != null) {
			sql.append("AND end_date_time <= ? ");
			parameters.add(endDate.atTime(LocalTime.MAX));
		}

		if (logicalAvailabilityTypeId != null) {
			sql.append("AND logical_availability_type_id = ? ");
			parameters.add(logicalAvailabilityTypeId);
		}

		sql.append("ORDER BY start_date_time, logical_availability_id");

		return getDatabase().queryForList(sql.toString(), LogicalAvailability.class, sqlVaragsParameters(parameters));
	}

	@Nonnull
	public Optional<CalendarPermissionId> findCalendarPermissionByAccountId(@Nullable UUID providerId,
																																					@Nullable UUID grantedToAccountId) {
		if (providerId == null || grantedToAccountId == null)
			return Optional.empty();

		return getDatabase().queryForObject("SELECT calendar_permission_id " +
						"FROM account_calendar_permission WHERE provider_id=? AND granted_to_account_id=?",
				CalendarPermissionId.class, providerId, grantedToAccountId);
	}

	@Nonnull
	public ProviderCalendar findProviderCalendar(@Nonnull UUID providerId,
																							 @Nonnull LocalDate startDate,
																							 @Nonnull LocalDate endDate) {
		requireNonNull(providerId);
		requireNonNull(startDate);
		requireNonNull(endDate);

		ValidationException validationException = new ValidationException();

		if (startDate.isAfter(endDate))
			validationException.add(getStrings().get("Start date cannot be after end date."));

		if (validationException.hasErrors())
			throw validationException;

		LocalDateTime startDateTime = startDate.atTime(LocalTime.MIN);
		List<Availability> availabilities = new ArrayList<>();
		List<Block> blocks = new ArrayList<>();
		List<Followup> followups = getFollowupService().findFollowupsByProviderId(providerId, startDate, endDate);
		List<Appointment> appointments = getAppointmentService().findAppointmentsByProviderId(providerId, startDate, endDate);

		// Pull relevant logical availabilities
		List<LogicalAvailability> logicalAvailabilities = getDatabase().queryForList("SELECT la.* FROM logical_availability la, provider p " +
				"WHERE p.provider_id=la.provider_id AND p.active=TRUE AND p.provider_id = ? " +
				"AND la.end_date_time > ?", LogicalAvailability.class, providerId, startDateTime);

		// Pull appointment types associated with logical availabilities
		List<AppointmentTypeWithLogicalAvailabilityId> logicalAvailabilityAppointmentTypes = getDatabase().queryForList("SELECT apt.*, la.logical_availability_id " +
				"FROM v_appointment_type apt, logical_availability la, logical_availability_appointment_type laat, provider p " +
				"WHERE laat.appointment_type_id=apt.appointment_type_id AND laat.logical_availability_id=la.logical_availability_id " +
				"AND la.provider_id=p.provider_id AND p.active=TRUE AND p.provider_id=? " +
				"AND la.end_date_time > ?", AppointmentTypeWithLogicalAvailabilityId.class, providerId, startDateTime);

		Map<UUID, List<AppointmentTypeWithLogicalAvailabilityId>> appointmentTypesByLogicalAvailabilityId = logicalAvailabilityAppointmentTypes.stream()
				.collect(Collectors.groupingBy(AppointmentTypeWithLogicalAvailabilityId::getLogicalAvailabilityId));

		for (LogicalAvailability logicalAvailability : logicalAvailabilities) {
			List<AppointmentTypeWithLogicalAvailabilityId> appointmentTypes = appointmentTypesByLogicalAvailabilityId.get(logicalAvailability.getLogicalAvailabilityId());

			// TODO: see if UI prefers the set of all active appointment types if none specified, or if the empty list is OK
			if (appointmentTypes == null)
				appointmentTypes = Collections.emptyList();

			if (logicalAvailability.getRecurrenceTypeId() == RecurrenceTypeId.NONE) {
				// Simple case: no recurrence
				if (logicalAvailability.getLogicalAvailabilityTypeId() == LogicalAvailabilityTypeId.OPEN) {
					Availability availability = new Availability();
					availability.setLogicalAvailabilityId(logicalAvailability.getLogicalAvailabilityId());
					availability.setStartDateTime(logicalAvailability.getStartDateTime());
					availability.setEndDateTime(logicalAvailability.getEndDateTime());
					availability.setAppointmentTypes(new ArrayList<>(appointmentTypes));
					availabilities.add(availability);
				} else if (logicalAvailability.getLogicalAvailabilityTypeId() == LogicalAvailabilityTypeId.BLOCK) {
					Block block = new Block();
					block.setLogicalAvailabilityId(logicalAvailability.getLogicalAvailabilityId());
					block.setStartDateTime(logicalAvailability.getStartDateTime());
					block.setEndDateTime(logicalAvailability.getEndDateTime());
					blocks.add(block);
				} else {
					throw new IllegalStateException(format("Not sure how to handle %s.%s", LogicalAvailabilityTypeId.class.getSimpleName(),
							logicalAvailability.getLogicalAvailabilityTypeId().name()));
				}
			} else if (logicalAvailability.getRecurrenceTypeId() == RecurrenceTypeId.DAILY) {
				// Figure out the first and last dates of the range we're getting availability for
				LocalDate currentDate = startDate;

				// For each date within the range...
				while (currentDate.isEqual(endDate) || currentDate.isBefore(endDate)) {
					if ((currentDate.isEqual(logicalAvailability.getStartDateTime().toLocalDate()) || currentDate.isAfter(logicalAvailability.getStartDateTime().toLocalDate()))
							&& (currentDate.isEqual(logicalAvailability.getEndDateTime().toLocalDate()) || currentDate.isBefore(logicalAvailability.getEndDateTime().toLocalDate()))) {
						// If recurrence rule is enabled for the day...
						if ((currentDate.getDayOfWeek() == DayOfWeek.MONDAY && logicalAvailability.getRecurMonday())
								|| (currentDate.getDayOfWeek() == DayOfWeek.TUESDAY && logicalAvailability.getRecurTuesday())
								|| (currentDate.getDayOfWeek() == DayOfWeek.WEDNESDAY && logicalAvailability.getRecurWednesday())
								|| (currentDate.getDayOfWeek() == DayOfWeek.THURSDAY && logicalAvailability.getRecurThursday())
								|| (currentDate.getDayOfWeek() == DayOfWeek.FRIDAY && logicalAvailability.getRecurFriday())
								|| (currentDate.getDayOfWeek() == DayOfWeek.SATURDAY && logicalAvailability.getRecurSaturday())
								|| (currentDate.getDayOfWeek() == DayOfWeek.SUNDAY && logicalAvailability.getRecurSunday())) {
							// ...normalize the logical availability's start and end times to be "today"
							LocalDateTime currentStartDateTime = LocalDateTime.of(currentDate, logicalAvailability.getStartDateTime().toLocalTime());
							LocalDateTime currentEndDateTime = LocalDateTime.of(currentDate, logicalAvailability.getEndDateTime().toLocalTime());

							if (logicalAvailability.getLogicalAvailabilityTypeId() == LogicalAvailabilityTypeId.OPEN) {
								Availability availability = new Availability();
								availability.setLogicalAvailabilityId(logicalAvailability.getLogicalAvailabilityId());
								availability.setStartDateTime(currentStartDateTime);
								availability.setEndDateTime(currentEndDateTime);
								availability.setAppointmentTypes(new ArrayList<>(appointmentTypes));
								availabilities.add(availability);
							} else if (logicalAvailability.getLogicalAvailabilityTypeId() == LogicalAvailabilityTypeId.BLOCK) {
								Block block = new Block();
								block.setLogicalAvailabilityId(logicalAvailability.getLogicalAvailabilityId());
								block.setStartDateTime(currentStartDateTime);
								block.setEndDateTime(currentEndDateTime);
								blocks.add(block);
							} else {
								throw new IllegalStateException(format("Not sure how to handle %s.%s", LogicalAvailabilityTypeId.class.getSimpleName(),
										logicalAvailability.getLogicalAvailabilityTypeId().name()));
							}
						}
					}
					currentDate = currentDate.plusDays(1);
				}
			} else {
				throw new IllegalStateException(format("Not sure how to handle %s.%s", RecurrenceTypeId.class.getSimpleName(),
						logicalAvailability.getRecurrenceTypeId().name()));
			}
		}

		Collections.sort(availabilities, (availability1, availability2) -> availability1.getStartDateTime().compareTo(availability2.getStartDateTime()));
		Collections.sort(blocks, (block1, block2) -> block1.getStartDateTime().compareTo(block2.getStartDateTime()));
		Collections.sort(followups, (followup1, followup2) -> followup1.getFollowupDate().compareTo(followup2.getFollowupDate()));
		Collections.sort(appointments, (appointment1, appointment2) -> appointment1.getStartTime().compareTo(appointment2.getStartTime()));

		ProviderCalendar providerCalendar = new ProviderCalendar();
		providerCalendar.setProviderId(providerId);
		providerCalendar.setAvailabilities(availabilities);
		providerCalendar.setBlocks(blocks);
		providerCalendar.setFollowups(followups);
		providerCalendar.setAppointments(appointments);

		return providerCalendar;
	}

	@Nonnull
	public Optional<LocalDate> normalizedEndDate(@Nonnull LogicalAvailability logicalAvailability) {
		requireNonNull(logicalAvailability);

		LocalDate endDate = logicalAvailability.getEndDateTime().toLocalDate();

		// If end date is "distant future", treat it like there is no end at all (null)
		if (endDate.equals(getDistantFutureDate()))
			return Optional.empty();

		return Optional.of(endDate);
	}

	@Nonnull
	public LocalDate getDistantFutureDate() {
		return DISTANT_FUTURE_DATE;
	}

	@Nonnull
	protected Long getHistoryBackgroundTaskIntervalInSeconds() {
		return HISTORY_BACKGROUND_TASK_INTERVAL_IN_SECONDS;
	}

	@Nonnull
	protected Long getHistoryBackgroundTaskInitialDelayInSeconds() {
		return HISTORY_BACKGROUND_TASK_INITIAL_DELAY_IN_SECONDS;
	}

	@Nonnull
	protected AppointmentService getAppointmentService() {
		return this.appointmentServiceProvider.get();
	}

	@Nonnull
	protected ProviderService getProviderService() {
		return this.providerServiceProvider.get();
	}

	@Nonnull
	protected FollowupService getFollowupService() {
		return this.followupServiceProvider.get();
	}

	@Nonnull
	protected HistoryBackgroundTask getHistoryBackgroundTask() {
		return this.historyBackgroundTaskProvider.get();
	}

	@Nonnull
	protected Database getDatabase() {
		return this.databaseProvider.get();
	}

	@Nonnull
	protected Configuration getConfiguration() {
		return this.configuration;
	}

	@Nonnull
	protected Strings getStrings() {
		return this.strings;
	}

	@Nonnull
	protected Logger getLogger() {
		return this.logger;
	}

	@Nonnull
	public Boolean isHistoryBackgroundTaskStarted() {
		synchronized (getHistoryBackgroundTaskLock()) {
			return this.historyBackgroundTaskStarted;
		}
	}

	@Nonnull
	protected Object getHistoryBackgroundTaskLock() {
		return this.historyBackgroundTaskLock;
	}

	@Nonnull
	protected Optional<ScheduledExecutorService> getHistoryBackgroundTaskExecutorService() {
		return Optional.ofNullable(this.historyBackgroundTaskExecutorService);
	}

	@Nonnull
	protected javax.inject.Provider<HistoryBackgroundTask> getHistoryBackgroundTaskProvider() {
		return this.historyBackgroundTaskProvider;
	}

	@ThreadSafe
	protected static class HistoryBackgroundTask implements Runnable {
		@Nonnull
		private final SystemService systemService;
		@Nonnull
		private final ProviderService providerService;
		@Nonnull
		private final InstitutionService institutionService;
		@Nonnull
		private final AppointmentService appointmentService;
		@Nonnull
		private final CurrentContextExecutor currentContextExecutor;
		@Nonnull
		private final ErrorReporter errorReporter;
		@Nonnull
		private final DatabaseProvider databaseProvider;
		@Nonnull
		private final Configuration configuration;
		@Nonnull
		private final Logger logger;

		@Inject
		public HistoryBackgroundTask(@Nonnull SystemService systemService,
																 @Nonnull ProviderService providerService,
																 @Nonnull InstitutionService institutionService,
																 @Nonnull AppointmentService appointmentService,
																 @Nonnull CurrentContextExecutor currentContextExecutor,
																 @Nonnull ErrorReporter errorReporter,
																 @Nonnull DatabaseProvider databaseProvider,
																 @Nonnull Configuration configuration) {
			requireNonNull(systemService);
			requireNonNull(providerService);
			requireNonNull(institutionService);
			requireNonNull(appointmentService);
			requireNonNull(currentContextExecutor);
			requireNonNull(errorReporter);
			requireNonNull(databaseProvider);
			requireNonNull(configuration);

			this.systemService = systemService;
			this.providerService = providerService;
			this.institutionService = institutionService;
			this.appointmentService = appointmentService;
			this.currentContextExecutor = currentContextExecutor;
			this.errorReporter = errorReporter;
			this.databaseProvider = databaseProvider;
			this.configuration = configuration;
			this.logger = LoggerFactory.getLogger(getClass());
		}

		@Override
		public void run() {
			CurrentContext currentContext = new CurrentContext.Builder(InstitutionId.COBALT, getConfiguration().getDefaultLocale(), getConfiguration().getDefaultTimeZone()).build();

			getCurrentContextExecutor().execute(currentContext, () -> {
				try {
					getDatabase().transaction(() -> {
						getSystemService().performAdvisoryLockOperationIfAvailable(AdvisoryLock.PROVIDER_AVAILABILITY_HISTORY_STORAGE, () -> {
							storeProviderAvailabilityHistoryForCurrentDate();
						});
					});
				} catch (Exception e) {
					getLogger().error("Unable to store provider availability slots for reporting", e);
					getErrorReporter().report(e);
				}
			});
		}

		protected void storeProviderAvailabilityHistoryForCurrentDate() {
			getLogger().trace("Starting provider availability history storage task...");

			for (Institution institution : getInstitutionService().findInstitutions()) {
				Account account = new Account();
				account.setTimeZone(institution.getTimeZone());

				LocalDateTime currentDateTimeForInstitution = LocalDateTime.now(institution.getTimeZone());
				LocalDateTime startOfDay = currentDateTimeForInstitution.with(LocalTime.MIN);
				LocalDateTime endOfDay = currentDateTimeForInstitution.with(LocalTime.MAX);

				boolean withinTimeWindow = currentDateTimeForInstitution.toLocalTime().isAfter(getHistoryBackgroundTaskRunStartTimeWindow())
						&& currentDateTimeForInstitution.toLocalTime().isBefore(getHistoryBackgroundTaskRunEndTimeWindow());

				// Only do the sync within the specified time window
				if (!withinTimeWindow)
					continue;

				List<ProviderFind> providerFinds = getProviderService().findProviders(new ProviderFindRequest() {{
					setStartDate(startOfDay.toLocalDate());
					setStartTime(startOfDay.toLocalTime());
					setEndDate(endOfDay.toLocalDate());
					setEndTime(endOfDay.toLocalTime());
					setInstitutionId(institution.getInstitutionId());
					setIncludePastAvailability(true);
				}}, account);

				for (ProviderFind providerFind : providerFinds) {
					// For now - only tracking history for native scheduling.
					// It will be additional effort to track ACUITY and EPIC slots (we will likely want to do that in
					// AcuitySyncManager, EpicSyncManager)
					if (providerFind.getSchedulingSystemId() != SchedulingSystemId.COBALT)
						continue;

					// Don't write records if these are special "phone number required for appointment" providers
					// because they don't have visible appointment slots
					if (providerFind.getPhoneNumberRequiredForAppointment() != null && providerFind.getPhoneNumberRequiredForAppointment())
						continue;

					for (AvailabilityDate availabilityDate : providerFind.getDates()) {
						for (ProviderFind.AvailabilityTime availabilityTime : availabilityDate.getTimes()) {
							// Throw out any slots that already have appointments booked
							if (availabilityTime.getStatus() == AvailabilityStatus.BOOKED)
								continue;

							LocalDateTime slotDateTime = LocalDateTime.of(availabilityDate.getDate(), availabilityTime.getTime());

							UUID providerAvailabilityHistoryId = getDatabase().queryForObject("""
									SELECT provider_availability_history_id
									FROM provider_availability_history
									WHERE provider_id=?
									AND slot_date_time=?
									AND time_zone=?
									""", UUID.class, providerFind.getProviderId(), slotDateTime, institution.getTimeZone()).orElse(null);

							if (providerAvailabilityHistoryId == null) {
								providerAvailabilityHistoryId = UUID.randomUUID();

								getDatabase().execute("""
												INSERT INTO provider_availability_history (
												  provider_availability_history_id, provider_id, scheduling_system_id, 
												  name, slot_date_time, time_zone
												)
												VALUES (?,?,?,?,?,?)
												""", providerAvailabilityHistoryId, providerFind.getProviderId(), providerFind.getSchedulingSystemId(),
										providerFind.getName(), slotDateTime, institution.getTimeZone());
							}

							getDatabase().execute("""
									DELETE FROM provider_availability_appointment_type_history
									WHERE provider_availability_history_id=?
									""", providerAvailabilityHistoryId);

							for (UUID appointmentTypeId : availabilityTime.getAppointmentTypeIds()) {
								AppointmentType appointmentType = getAppointmentService().findAppointmentTypeByIdEvenIfDeleted(appointmentTypeId).get();

								getDatabase().execute("""
												INSERT INTO provider_availability_appointment_type_history (
													provider_availability_history_id, appointment_type_id, visit_type_id, name, duration_in_minutes
												)
												VALUES (?,?,?,?,?)
												""", providerAvailabilityHistoryId, appointmentTypeId, appointmentType.getVisitTypeId(),
										appointmentType.getName(), appointmentType.getDurationInMinutes());
							}
						}
					}
				}
			}

			getLogger().trace("Finished provider availability history storage task.");
		}

		@Nonnull
		protected LocalTime getHistoryBackgroundTaskRunStartTimeWindow() {
			return HISTORY_BACKGROUND_TASK_RUN_START_TIME_WINDOW;
		}

		@Nonnull
		protected LocalTime getHistoryBackgroundTaskRunEndTimeWindow() {
			return HISTORY_BACKGROUND_TASK_RUN_END_TIME_WINDOW;
		}

		@Nonnull
		protected SystemService getSystemService() {
			return this.systemService;
		}

		@Nonnull
		protected ProviderService getProviderService() {
			return this.providerService;
		}

		@Nonnull
		protected InstitutionService getInstitutionService() {
			return this.institutionService;
		}

		@Nonnull
		protected AppointmentService getAppointmentService() {
			return this.appointmentService;
		}

		@Nonnull
		protected CurrentContextExecutor getCurrentContextExecutor() {
			return this.currentContextExecutor;
		}

		@Nonnull
		protected ErrorReporter getErrorReporter() {
			return this.errorReporter;
		}

		@Nonnull
		protected Database getDatabase() {
			return this.databaseProvider.get();
		}

		@Nonnull
		protected Configuration getConfiguration() {
			return this.configuration;
		}

		@Nonnull
		protected Logger getLogger() {
			return this.logger;
		}
	}
}
