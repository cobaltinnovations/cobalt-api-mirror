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

import com.cobaltplatform.api.model.db.PatientOrderScheduledFollowupContactType.PatientOrderScheduledFollowupContactTypeId;
import com.cobaltplatform.api.model.db.PatientOrderScheduledFollowupStatus.PatientOrderScheduledFollowupStatusId;
import com.cobaltplatform.api.model.db.PatientOrderScheduledFollowupType.PatientOrderScheduledFollowupTypeId;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class PatientOrderScheduledFollowup {
	@Nullable
	private UUID patientOrderScheduledFollowupId;
	@Nullable
	private UUID patientOrderId;
	@Nullable
	private PatientOrderScheduledFollowupStatusId patientOrderScheduledFollowupStatusId;
	@Nullable
	private PatientOrderScheduledFollowupTypeId patientOrderScheduledFollowupTypeId;
	@Nullable
	private PatientOrderScheduledFollowupContactTypeId patientOrderScheduledFollowupContactTypeId;
	@Nullable
	private UUID createdByAccountId;
	@Nullable
	private UUID updatedByAccountId;
	@Nullable
	private UUID completedByAccountId;
	@Nullable
	private UUID canceledByAccountId;
	@Nonnull
	private LocalDateTime scheduledAtDateTime;
	@Nullable
	private String message;
	@Nullable
	private Instant completedAt;
	@Nullable
	private Instant canceledAt;
	@Nullable
	private Instant created;
	@Nullable
	private Instant lastUpdated;

	// Joined from v_patient_order_scheduled_followup

	@Nullable
	private String createdByAccountFirstName;
	@Nullable
	private String createdByAccountLastName;
	@Nullable
	private String completedByAccountFirstName;
	@Nullable
	private String completedByAccountLastName;

	@Nullable
	public UUID getPatientOrderScheduledFollowupId() {
		return this.patientOrderScheduledFollowupId;
	}

	public void setPatientOrderScheduledFollowupId(@Nullable UUID patientOrderScheduledFollowupId) {
		this.patientOrderScheduledFollowupId = patientOrderScheduledFollowupId;
	}

	@Nullable
	public UUID getPatientOrderId() {
		return this.patientOrderId;
	}

	public void setPatientOrderId(@Nullable UUID patientOrderId) {
		this.patientOrderId = patientOrderId;
	}

	@Nullable
	public PatientOrderScheduledFollowupStatusId getPatientOrderScheduledFollowupStatusId() {
		return this.patientOrderScheduledFollowupStatusId;
	}

	public void setPatientOrderScheduledFollowupStatusId(@Nullable PatientOrderScheduledFollowupStatusId patientOrderScheduledFollowupStatusId) {
		this.patientOrderScheduledFollowupStatusId = patientOrderScheduledFollowupStatusId;
	}

	@Nullable
	public PatientOrderScheduledFollowupTypeId getPatientOrderScheduledFollowupTypeId() {
		return this.patientOrderScheduledFollowupTypeId;
	}

	public void setPatientOrderScheduledFollowupTypeId(@Nullable PatientOrderScheduledFollowupTypeId patientOrderScheduledFollowupTypeId) {
		this.patientOrderScheduledFollowupTypeId = patientOrderScheduledFollowupTypeId;
	}

	@Nullable
	public PatientOrderScheduledFollowupContactTypeId getPatientOrderScheduledFollowupContactTypeId() {
		return this.patientOrderScheduledFollowupContactTypeId;
	}

	public void setPatientOrderScheduledFollowupContactTypeId(@Nullable PatientOrderScheduledFollowupContactTypeId patientOrderScheduledFollowupContactTypeId) {
		this.patientOrderScheduledFollowupContactTypeId = patientOrderScheduledFollowupContactTypeId;
	}

	@Nullable
	public UUID getCreatedByAccountId() {
		return this.createdByAccountId;
	}

	public void setCreatedByAccountId(@Nullable UUID createdByAccountId) {
		this.createdByAccountId = createdByAccountId;
	}

	@Nullable
	public UUID getUpdatedByAccountId() {
		return this.updatedByAccountId;
	}

	public void setUpdatedByAccountId(@Nullable UUID updatedByAccountId) {
		this.updatedByAccountId = updatedByAccountId;
	}

	@Nullable
	public UUID getCompletedByAccountId() {
		return this.completedByAccountId;
	}

	public void setCompletedByAccountId(@Nullable UUID completedByAccountId) {
		this.completedByAccountId = completedByAccountId;
	}

	@Nullable
	public UUID getCanceledByAccountId() {
		return this.canceledByAccountId;
	}

	public void setCanceledByAccountId(@Nullable UUID canceledByAccountId) {
		this.canceledByAccountId = canceledByAccountId;
	}

	@Nonnull
	public LocalDateTime getScheduledAtDateTime() {
		return this.scheduledAtDateTime;
	}

	public void setScheduledAtDateTime(@Nonnull LocalDateTime scheduledAtDateTime) {
		this.scheduledAtDateTime = scheduledAtDateTime;
	}

	@Nullable
	public String getMessage() {
		return this.message;
	}

	public void setMessage(@Nullable String message) {
		this.message = message;
	}

	@Nullable
	public Instant getCompletedAt() {
		return this.completedAt;
	}

	public void setCompletedAt(@Nullable Instant completedAt) {
		this.completedAt = completedAt;
	}

	@Nullable
	public Instant getCanceledAt() {
		return this.canceledAt;
	}

	public void setCanceledAt(@Nullable Instant canceledAt) {
		this.canceledAt = canceledAt;
	}

	@Nullable
	public Instant getCreated() {
		return this.created;
	}

	public void setCreated(@Nullable Instant created) {
		this.created = created;
	}

	@Nullable
	public Instant getLastUpdated() {
		return this.lastUpdated;
	}

	public void setLastUpdated(@Nullable Instant lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	@Nullable
	public String getCreatedByAccountFirstName() {
		return this.createdByAccountFirstName;
	}

	public void setCreatedByAccountFirstName(@Nullable String createdByAccountFirstName) {
		this.createdByAccountFirstName = createdByAccountFirstName;
	}

	@Nullable
	public String getCreatedByAccountLastName() {
		return this.createdByAccountLastName;
	}

	public void setCreatedByAccountLastName(@Nullable String createdByAccountLastName) {
		this.createdByAccountLastName = createdByAccountLastName;
	}

	@Nullable
	public String getCompletedByAccountFirstName() {
		return this.completedByAccountFirstName;
	}

	public void setCompletedByAccountFirstName(@Nullable String completedByAccountFirstName) {
		this.completedByAccountFirstName = completedByAccountFirstName;
	}

	@Nullable
	public String getCompletedByAccountLastName() {
		return this.completedByAccountLastName;
	}

	public void setCompletedByAccountLastName(@Nullable String completedByAccountLastName) {
		this.completedByAccountLastName = completedByAccountLastName;
	}
}