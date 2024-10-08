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

package com.cobaltplatform.api.model.db;

import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.RecordingPreference.RecordingPreferenceId;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * @author Transmogrify LLC.
 */
@NotThreadSafe
public class AccountStudy {
	@Nullable
	private UUID accountStudyId;
	@Nullable
	private UUID accountId;
	@Nullable
	private UUID studyId;
	@Nullable
	private UUID encryptionKeypairId;
	@Nullable
	private InstitutionId institutionId;
	@Nullable
	private ZoneId timeZone;
	@Nullable
	private Boolean studyStarted;
	@Nonnull
	private Instant created;
	@Nonnull
	private Instant lastUpdated;

	@Nonnull
	private Boolean deleted;

	@Nonnull
	private RecordingPreferenceId recordingPreferenceId;

	@Nullable
	public UUID getAccountStudyId() {
		return this.accountStudyId;
	}

	public void setAccountStudyId(@Nullable UUID accountStudyId) {
		this.accountStudyId = accountStudyId;
	}

	@Nullable
	public UUID getAccountId() {
		return this.accountId;
	}

	public void setAccountId(@Nullable UUID accountId) {
		this.accountId = accountId;
	}

	@Nullable
	public UUID getStudyId() {
		return this.studyId;
	}

	public void setStudyId(@Nullable UUID studyId) {
		this.studyId = studyId;
	}

	@Nullable
	public UUID getEncryptionKeypairId() {
		return this.encryptionKeypairId;
	}

	public void setEncryptionKeypairId(@Nullable UUID encryptionKeypairId) {
		this.encryptionKeypairId = encryptionKeypairId;
	}

	@Nonnull
	public Instant getCreated() {
		return this.created;
	}

	public void setCreated(@Nonnull Instant created) {
		this.created = created;
	}

	@Nonnull
	public Instant getLastUpdated() {
		return this.lastUpdated;
	}

	public void setLastUpdated(@Nonnull Instant lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	@Nullable
	public InstitutionId getInstitutionId() {
		return institutionId;
	}

	public void setInstitutionId(@Nullable InstitutionId institutionId) {
		this.institutionId = institutionId;
	}

	@Nullable
	public ZoneId getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(@Nullable ZoneId timeZone) {
		this.timeZone = timeZone;
	}

	@Nonnull
	public Boolean getDeleted() {
		return deleted;
	}

	public void setDeleted(@Nonnull Boolean deleted) {
		this.deleted = deleted;
	}

	@Nullable
	public Boolean getStudyStarted() {
		return studyStarted;
	}

	public void setStudyStarted(@Nullable Boolean studyStarted) {
		this.studyStarted = studyStarted;
	}

	@Nonnull
	public RecordingPreferenceId getRecordingPreferenceId() {
		return recordingPreferenceId;
	}

	public void setRecordingPreferenceId(@Nonnull RecordingPreferenceId recordingPreferenceId) {
		this.recordingPreferenceId = recordingPreferenceId;
	}
}