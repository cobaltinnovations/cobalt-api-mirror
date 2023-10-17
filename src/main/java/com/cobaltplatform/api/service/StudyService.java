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


import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.AccountCheckIn;
import com.cobaltplatform.api.model.db.AccountCheckInAction;
import com.cobaltplatform.api.model.db.CheckInActionStatus;
import com.cobaltplatform.api.model.db.CheckInActionStatus.CheckInActionStatusId;
import com.cobaltplatform.api.model.db.Study;
import com.cobaltplatform.api.model.db.StudyCheckIn;
import com.cobaltplatform.api.util.ValidationException;
import com.lokalized.Strings;
import com.pyranid.Database;
import org.checkerframework.checker.units.qual.N;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.text.html.Option;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class StudyService {
	@Nonnull
	private final Database database;
	@Nonnull
	private final Strings strings;
	@Nonnull
	private final Logger logger;

	@Inject
	public StudyService(@Nonnull Database database,
											@Nonnull Strings strings) {
		requireNonNull(database);
		requireNonNull(strings);

		this.database = database;
		this.strings = strings;
		this.logger = LoggerFactory.getLogger(getClass());
	}

	@Nonnull
	public List<AccountCheckIn> getAccountCheckInsForAccountAndStudy(@Nonnull Account account,
																																	 @Nonnull UUID studyId,
																																	 @Nonnull Optional<Boolean> completed) {
		requireNonNull(account);
		requireNonNull(studyId);

		List<Object> sqlParams = new ArrayList<>();

		StringBuilder query = new StringBuilder("""
				SELECT * 
					FROM v_account_check_in 
					WHERE account_id = ? AND study_id = ?
					""");
		sqlParams.add(account.getAccountId());
		sqlParams.add(studyId);

		if (completed.isPresent()) {
			query.append("AND completed_flag = ? ");
			sqlParams.add(completed.get());
		}

		return getDatabase().queryForList(query.toString(), AccountCheckIn.class, sqlParams.toArray());
	}

	@Nonnull
	public List<AccountCheckInAction> getAccountCheckInActionsFoAccountAndStudy(@Nonnull UUID accountId,
																																							@Nonnull UUID accountCheckInId) {
		requireNonNull(accountId);
		requireNonNull(accountCheckInId);

		return getDatabase().queryForList("""
				SELECT * 
				FROM v_account_check_in_action
				WHERE account_id = ? AND account_check_in_id = ?
				""", AccountCheckInAction.class, accountId, accountCheckInId);
	}

	@Nonnull
	private Optional<Study> findStudyById(@Nonnull UUID studyId) {
		return getDatabase().queryForObject("""
				SELECT * 
				FROM study 
				WHERE study_id = ?
				""", Study.class, studyId);
	}

	@Nonnull
	public void addAccountToStudy(@Nonnull Account account,
																@Nonnull UUID studyId) {
		requireNonNull(account);
		requireNonNull(studyId);

		ValidationException validationException = new ValidationException();
		List<StudyCheckIn> studyCheckIns = getDatabase().queryForList("SELECT * FROM study_check_in WHERE study_id = ? ORDER BY check_in_number ASC", StudyCheckIn.class, studyId);
		LocalDateTime currentDate = LocalDateTime.now(account.getTimeZone());
		LocalDateTime checkInStartDateTime;
		LocalDateTime checkInEndDateTime;
		Integer minutesInDay = 1440;
		UUID accountStudyId = UUID.randomUUID();

		Boolean accountAlreadyInStudy = getDatabase().queryForObject("""
				SELECT COUNT(*) > 0
				FROM account_study 
				WHERE account_id = ? 
				AND study_id = ?
				""", Boolean.class, account.getAccountId(), studyId).get();

		if (accountAlreadyInStudy)
			validationException.add(new ValidationException.FieldError("account", getStrings().get("Account is already in this study.")));

		if (validationException.hasErrors())
			throw validationException;

		Study study = findStudyById(studyId).get();

		getDatabase().execute("""
					INSERT INTO account_study 
					  (account_study_id, account_id, study_id)
					VALUES
					  (?, ?, ?)
				""", accountStudyId, account.getAccountId(), studyId);

		int checkInCount = 0;
		for (StudyCheckIn studyCheckIn : studyCheckIns) {
			if (study.getMinutesBetweenCheckIns() >= minutesInDay) {
				//TODO: Validate that the minutes are a whole number of days
				Integer daysToAdd = checkInCount == 0 ? 0 : (study.getMinutesBetweenCheckIns() * checkInCount) / minutesInDay;
				LocalDate checkInStartDate = currentDate.toLocalDate().plusDays(daysToAdd);
				checkInStartDateTime = LocalDateTime.of(checkInStartDate, LocalTime.of(0, 0, 0));
			} else {
				checkInStartDateTime = currentDate.plus(study.getMinutesBetweenCheckIns() * checkInCount, ChronoUnit.MINUTES);
			}
			checkInEndDateTime = checkInStartDateTime.plusMinutes(study.getMinutesBetweenCheckIns());

			UUID accountCheckInId = UUID.randomUUID();
			getDatabase().execute("""
									INSERT INTO account_check_in 
									  (account_check_in_id, account_study_id, study_check_in_id, check_in_start_date_time, check_in_end_date_time)
									VALUES
									  (?, ?, ?, ?, ?)      				
					""", accountCheckInId, accountStudyId, studyCheckIn.getStudyCheckInId(), checkInStartDateTime, checkInEndDateTime);

			getDatabase().execute("""
					INSERT INTO account_check_in_action
					  (account_check_in_action_id, study_check_in_action_id, account_check_in_id, check_in_action_status_id)
					SELECT uuid_generate_v4(), study_check_in_action_id, ?, ?
					FROM study_check_in_action
					WHERE study_check_in_id = ?
					""", accountCheckInId, CheckInActionStatusId.INCOMPLETE, studyCheckIn.getStudyCheckInId());
			checkInCount++;
		}
	}

	@Nonnull
	public void rescheduleAccountCheckIn(@Nonnull Account account, @Nonnull UUID studyId) {
		//Adjust the start and end times for all future check-ins based on the current check-in. If there is no
		//current check-in then take the "next" check-in and make that active. If it's been longer than the
		//study.minutesBetweenCheckIns then start a check-in immediately

		//Find all of the check-ins
		List<AccountCheckIn> accountCheckIns = getAccountCheckInsForAccountAndStudy(account, studyId, Optional.empty());
		Study study = findStudyById(studyId).get();
		int checkInCount = 0;
		Integer minutesInDay = 1440;
		LocalDateTime checkInStartDateTime;
		LocalDateTime checkInEndDateTime;
		LocalDateTime startDateTime = LocalDateTime.now(account.getTimeZone());

		for (AccountCheckIn accountCheckIn : accountCheckIns) {
			getLogger().debug("checkInCount = " + checkInCount);
			if (checkInCount == 0 && accountCheckActive(account, accountCheckIn)) //If this is the first check-in and it is still active do nothing
					break;
			else if (accountCheckIn.getCompletedFlag()) {
				startDateTime = accountCheckIn.getCompletedDate();
				continue;
			}
			if (study.getMinutesBetweenCheckIns() >= minutesInDay) {
				//TODO: Validate that the minutes are a whole number of days
				Integer daysToAdd = checkInCount == 0 ? 0 : (study.getMinutesBetweenCheckIns() * checkInCount) / minutesInDay;
				LocalDate checkInStartDate = startDateTime.toLocalDate().plusDays(daysToAdd);
				checkInStartDateTime = LocalDateTime.of(checkInStartDate, LocalTime.of(0, 0, 0));
			} else {
				checkInStartDateTime = startDateTime.plus(study.getMinutesBetweenCheckIns() * checkInCount, ChronoUnit.MINUTES);
			}
			checkInEndDateTime = checkInStartDateTime.plusMinutes(study.getMinutesBetweenCheckIns());

			getDatabase().execute("""
					UPDATE account_check_in
					SET check_in_start_date_time = ?, check_in_end_date_time = ?
					WHERE account_check_in_id = ?
					""", checkInStartDateTime, checkInEndDateTime, accountCheckIn.getAccountCheckInId());

			checkInCount ++;
		}

	}

	@Nonnull
	public boolean accountCheckActive(Account account, AccountCheckIn accountCheckIn) {
		return !accountCheckIn.getCompletedFlag() &&
				(LocalDateTime.now(account.getTimeZone()).isAfter(accountCheckIn.getCheckInStartDateTime())
						&& LocalDateTime.now(account.getTimeZone()).isBefore(accountCheckIn.getCheckInEndDateTime()));
	}

	@Nonnull
	private Optional<AccountCheckInAction> findAccountCheckInActionById(@Nonnull UUID accountCheckInActionId) {
		requireNonNull(accountCheckInActionId);

		return getDatabase().queryForObject("""
				SELECT * 
				FROM account_check_in_action 
				WHERE account_check_in_action_id = ?  
				""", AccountCheckInAction.class, accountCheckInActionId);
	}

	@Nonnull
	private Optional<AccountCheckIn> findAccountCheckInById(@Nonnull UUID accountCheckInId) {
		requireNonNull(accountCheckInId);

		return getDatabase().queryForObject("""
				SELECT * 
				FROM account_check_in 
				WHERE account_check_in_id = ?  
				""", AccountCheckIn.class, accountCheckInId);
	}

	@Nonnull
	private Boolean checkInComplete(@Nonnull UUID accountCheckInId) {
		return getDatabase().queryForObject("""
				SELECT COUNT(*) = 0
				FROM account_check_in_action
				WHERE account_check_in_id = ?
				AND check_in_action_status_id != ?
				""", Boolean.class, accountCheckInId, CheckInActionStatusId.COMPLETE).get();
	}

	@Nonnull
	public void updateAccountCheckInAction(@Nonnull Account account,
																				 @Nonnull UUID accountCheckInActionId,
																				 @Nonnull CheckInActionStatusId checkInActionStatusId) {
		requireNonNull(account);
		requireNonNull(accountCheckInActionId);
		requireNonNull(checkInActionStatusId);

		ValidationException validationException = new ValidationException();
		Optional<AccountCheckInAction> accountCheckInAction = findAccountCheckInActionById(accountCheckInActionId);

		if (!accountCheckInAction.isPresent())
			validationException.add(new ValidationException.FieldError("accountCheckInAction", getStrings().get("Account check-in action not found.")));

		if (accountCheckInAction.isPresent()) {
			Optional<AccountCheckIn> accountCheckIn = findAccountCheckInById(accountCheckInAction.get().getAccountCheckInId());
			if (!accountCheckIn.isPresent())
				validationException.add(new ValidationException.FieldError("accountCheckIn", getStrings().get("Account check-in not found.")));
			else if (accountCheckIn.get().getCompletedFlag())
				validationException.add(new ValidationException.FieldError("accountCheckIn", getStrings().get("Account check-in is complete.")));
		}


		if (validationException.hasErrors())
			throw validationException;

		//TODO: validate account owns the check-in and validate status changes, should not be able to go from COMPLETE -> INCOMPLETE
		getDatabase().execute("""
				UPDATE account_check_in_action 
				SET check_in_action_status_id = ?
				WHERE account_check_in_action_id=?
				""", checkInActionStatusId, accountCheckInActionId);

		// If a check-in action is being completed, check if all the actions are complete
		// and set the check-in to complete if they are.
		if (checkInActionStatusId.equals(CheckInActionStatusId.COMPLETE))
			if (checkInComplete(accountCheckInAction.get().getAccountCheckInId()))
				getDatabase().execute("""
						UPDATE account_check_in 
						SET completed_flag = true, completed_date = now()
						WHERE account_check_in_id = ?
						""", accountCheckInAction.get().getAccountCheckInId());
	}

	@Nonnull
	protected Database getDatabase() {
		return this.database;
	}

	@Nonnull
	protected Strings getStrings() {
		return this.strings;
	}

	@Nonnull
	protected Logger getLogger() {
		return this.logger;
	}
}
