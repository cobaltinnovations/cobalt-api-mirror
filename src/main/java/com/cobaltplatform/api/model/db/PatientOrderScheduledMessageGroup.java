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

import com.cobaltplatform.api.model.db.PatientOrderScheduledMessageType.PatientOrderScheduledMessageTypeId;

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
public class PatientOrderScheduledMessageGroup {
	@Nullable
	private UUID patientOrderScheduledMessageGroupId;
	@Nullable
	private PatientOrderScheduledMessageTypeId patientOrderScheduledMessageTypeId;
	@Nullable
	private UUID patientOrderId;
	@Nonnull
	private LocalDateTime scheduledAtDateTime;
	@Nullable
	private Boolean deleted;
	@Nullable
	private Instant deletedAt;
	@Nullable
	private Instant created;
	@Nullable
	private Instant lastUpdated;

	// Joined in from v_patient_order_scheduled_message_group

	@Nullable
	private Boolean scheduledAtDateTimeHasPassed;
	@Nullable
	private Boolean atLeastOneMessageDelivered;

	@Nullable
	public UUID getPatientOrderScheduledMessageGroupId() {
		return this.patientOrderScheduledMessageGroupId;
	}

	public void setPatientOrderScheduledMessageGroupId(@Nullable UUID patientOrderScheduledMessageGroupId) {
		this.patientOrderScheduledMessageGroupId = patientOrderScheduledMessageGroupId;
	}

	@Nullable
	public PatientOrderScheduledMessageTypeId getPatientOrderScheduledMessageTypeId() {
		return this.patientOrderScheduledMessageTypeId;
	}

	public void setPatientOrderScheduledMessageTypeId(@Nullable PatientOrderScheduledMessageTypeId patientOrderScheduledMessageTypeId) {
		this.patientOrderScheduledMessageTypeId = patientOrderScheduledMessageTypeId;
	}

	@Nullable
	public UUID getPatientOrderId() {
		return this.patientOrderId;
	}

	public void setPatientOrderId(@Nullable UUID patientOrderId) {
		this.patientOrderId = patientOrderId;
	}

	@Nonnull
	public LocalDateTime getScheduledAtDateTime() {
		return this.scheduledAtDateTime;
	}

	public void setScheduledAtDateTime(@Nonnull LocalDateTime scheduledAtDateTime) {
		this.scheduledAtDateTime = scheduledAtDateTime;
	}

	@Nullable
	public Boolean getDeleted() {
		return this.deleted;
	}

	public void setDeleted(@Nullable Boolean deleted) {
		this.deleted = deleted;
	}

	@Nullable
	public Instant getDeletedAt() {
		return this.deletedAt;
	}

	public void setDeletedAt(@Nullable Instant deletedAt) {
		this.deletedAt = deletedAt;
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
	public Boolean getScheduledAtDateTimeHasPassed() {
		return this.scheduledAtDateTimeHasPassed;
	}

	public void setScheduledAtDateTimeHasPassed(@Nullable Boolean scheduledAtDateTimeHasPassed) {
		this.scheduledAtDateTimeHasPassed = scheduledAtDateTimeHasPassed;
	}

	@Nullable
	public Boolean getAtLeastOneMessageDelivered() {
		return this.atLeastOneMessageDelivered;
	}

	public void setAtLeastOneMessageDelivered(@Nullable Boolean atLeastOneMessageDelivered) {
		this.atLeastOneMessageDelivered = atLeastOneMessageDelivered;
	}
}
