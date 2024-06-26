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

package com.cobaltplatform.api.model.api.request;

import com.cobaltplatform.api.model.db.AppointmentCancelationReason.AppointmentCancelationReasonId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.UUID;

/**
 * @author Transmogrify LLC.
 */
@NotThreadSafe
public class CancelAppointmentRequest {
	@Nullable
	private UUID appointmentId;
	@Nullable
	private UUID accountId;
	@Nullable
	private Boolean canceledByWebhook;
	@Nullable
	private Boolean canceledForReschedule;
	@Nullable
	private UUID rescheduleAppointmentId;
	@Nullable
	private AppointmentCancelationReasonId appointmentCancelationReasonId;
	private boolean force;

	@Nullable
	public UUID getAccountId() {
		return accountId;
	}

	public void setAccountId(@Nullable UUID accountId) {
		this.accountId = accountId;
	}

	@Nullable
	public UUID getAppointmentId() {
		return appointmentId;
	}

	public void setAppointmentId(@Nullable UUID appointmentId) {
		this.appointmentId = appointmentId;
	}

	@Nullable
	public Boolean getCanceledByWebhook() {
		return canceledByWebhook;
	}

	public void setCanceledByWebhook(@Nullable Boolean canceledByWebhook) {
		this.canceledByWebhook = canceledByWebhook;
	}

	@Nullable
	public Boolean getCanceledForReschedule() {
		return canceledForReschedule;
	}

	public void setCanceledForReschedule(@Nullable Boolean canceledForReschedule) {
		this.canceledForReschedule = canceledForReschedule;
	}

	@Nullable
	public UUID getRescheduleAppointmentId() {
		return rescheduleAppointmentId;
	}

	public void setRescheduleAppointmentId(@Nullable UUID rescheduleAppointmentId) {
		this.rescheduleAppointmentId = rescheduleAppointmentId;
	}

	@Nullable
	public AppointmentCancelationReasonId getAppointmentCancelationReasonId() {
		return this.appointmentCancelationReasonId;
	}

	public void setAppointmentCancelationReasonId(@Nullable AppointmentCancelationReasonId appointmentCancelationReasonId) {
		this.appointmentCancelationReasonId = appointmentCancelationReasonId;
	}

	public boolean isForce() {
		return this.force;
	}

	public void setForce(boolean force) {
		this.force = force;
	}
}
