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

package com.cobaltplatform.api.model.service;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class PatientOrderActivity {
	@Nullable
	private PatientOrderActivityTypeId patientOrderActivityTypeId;
	@Nullable
	private UUID initiatedByAccountId;
	@Nullable
	private String description;
	@Nullable
	private List<PatientOrderActivityMessage> patientOrderActivityMessages;
	@Nullable
	private Map<String, Object> metadata;

	@Nullable
	public PatientOrderActivityTypeId getPatientOrderActivityTypeId() {
		return this.patientOrderActivityTypeId;
	}

	public void setPatientOrderActivityTypeId(@Nullable PatientOrderActivityTypeId patientOrderActivityTypeId) {
		this.patientOrderActivityTypeId = patientOrderActivityTypeId;
	}

	@Nullable
	public UUID getInitiatedByAccountId() {
		return this.initiatedByAccountId;
	}

	public void setInitiatedByAccountId(@Nullable UUID initiatedByAccountId) {
		this.initiatedByAccountId = initiatedByAccountId;
	}

	@Nullable
	public String getDescription() {
		return this.description;
	}

	public void setDescription(@Nullable String description) {
		this.description = description;
	}

	@Nullable
	public List<PatientOrderActivityMessage> getPatientOrderActivityMessages() {
		return this.patientOrderActivityMessages;
	}

	public void setPatientOrderActivityMessages(@Nullable List<PatientOrderActivityMessage> patientOrderActivityMessages) {
		this.patientOrderActivityMessages = patientOrderActivityMessages;
	}

	@Nullable
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	public void setMetadata(@Nullable Map<String, Object> metadata) {
		this.metadata = metadata;
	}
}
