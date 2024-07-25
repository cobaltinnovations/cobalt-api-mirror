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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class CompletePatientOrderScheduledOutreachRequest {
	@Nullable
	private UUID patientOrderScheduledOutreachId;
	@Nullable
	private UUID completedByAccountId;
	@Nullable
	private UUID patientOrderOutreachResultId;
	@Nullable
	private LocalDate completedAtDate;
	@Nullable
	private LocalTime completedAtTime;
	@Nullable
	private String message;

	@Nullable
	public UUID getPatientOrderScheduledOutreachId() {
		return this.patientOrderScheduledOutreachId;
	}

	public void setPatientOrderScheduledOutreachId(@Nullable UUID patientOrderScheduledOutreachId) {
		this.patientOrderScheduledOutreachId = patientOrderScheduledOutreachId;
	}

	@Nullable
	public UUID getCompletedByAccountId() {
		return this.completedByAccountId;
	}

	public void setCompletedByAccountId(@Nullable UUID completedByAccountId) {
		this.completedByAccountId = completedByAccountId;
	}

	@Nullable
	public UUID getPatientOrderOutreachResultId() {
		return this.patientOrderOutreachResultId;
	}

	public void setPatientOrderOutreachResultId(@Nullable UUID patientOrderOutreachResultId) {
		this.patientOrderOutreachResultId = patientOrderOutreachResultId;
	}

	@Nullable
	public LocalDate getCompletedAtDate() {
		return this.completedAtDate;
	}

	public void setCompletedAtDate(@Nullable LocalDate completedAtDate) {
		this.completedAtDate = completedAtDate;
	}

	@Nullable
	public LocalTime getCompletedAtTime() {
		return this.completedAtTime;
	}

	public void setCompletedAtTime(@Nullable LocalTime completedAtTime) {
		this.completedAtTime = completedAtTime;
	}

	@Nullable
	public String getMessage() {
		return this.message;
	}

	public void setMessage(@Nullable String message) {
		this.message = message;
	}
}