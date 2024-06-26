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

package com.cobaltplatform.api.model.api.response;

import com.cobaltplatform.api.model.db.PatientOrderCareType;
import com.cobaltplatform.api.model.db.PatientOrderCareType.PatientOrderCareTypeId;
import com.cobaltplatform.api.model.db.PatientOrderFocusType;
import com.cobaltplatform.api.model.db.PatientOrderFocusType.PatientOrderFocusTypeId;
import com.cobaltplatform.api.model.db.PatientOrderTriageSource.PatientOrderTriageSourceId;
import com.google.inject.assistedinject.AssistedInject;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Immutable
public class PatientOrderTriageGroupApiResponse {
	@Nonnull
	private final PatientOrderTriageSourceId patientOrderTriageSourceId;
	@Nonnull
	private final PatientOrderCareTypeId patientOrderCareTypeId;
	@Nonnull
	private final String patientOrderCareTypeDescription;
	@Nonnull
	private final List<PatientOrderTriageGroupFocusApiResponse> patientOrderFocusTypes;

	@AssistedInject
	public PatientOrderTriageGroupApiResponse(@Nonnull PatientOrderTriageSourceId patientOrderTriageSourceId,
																						@Nonnull PatientOrderCareType patientOrderCareType,
																						@Nonnull List<PatientOrderTriageGroupFocusApiResponse> patientOrderFocusTypes) {
		requireNonNull(patientOrderTriageSourceId);
		requireNonNull(patientOrderCareType);
		requireNonNull(patientOrderFocusTypes);

		this.patientOrderTriageSourceId = patientOrderTriageSourceId;
		this.patientOrderCareTypeId = patientOrderCareType.getPatientOrderCareTypeId();
		this.patientOrderCareTypeDescription = patientOrderCareType.getDescription();
		this.patientOrderFocusTypes = patientOrderFocusTypes;
	}

	@ThreadSafe
	public static class PatientOrderTriageGroupFocusApiResponse {
		@Nonnull
		private final PatientOrderFocusTypeId patientOrderFocusTypeId;
		@Nonnull
		private final String patientOrderFocusTypeDescription;
		@Nonnull
		private final List<String> reasons;

		public PatientOrderTriageGroupFocusApiResponse(@Nonnull PatientOrderFocusType patientOrderFocusType,
																									 @Nonnull List<String> reasons) {
			requireNonNull(patientOrderFocusType);
			requireNonNull(reasons);

			this.patientOrderFocusTypeId = patientOrderFocusType.getPatientOrderFocusTypeId();
			this.patientOrderFocusTypeDescription = patientOrderFocusType.getDescription();
			this.reasons = reasons;
		}

		@Nonnull
		public PatientOrderFocusTypeId getPatientOrderFocusTypeId() {
			return this.patientOrderFocusTypeId;
		}

		@Nonnull
		public String getPatientOrderFocusTypeDescription() {
			return this.patientOrderFocusTypeDescription;
		}

		@Nonnull
		public List<String> getReasons() {
			return this.reasons;
		}
	}

	@Nonnull
	public PatientOrderTriageSourceId getPatientOrderTriageSourceId() {
		return this.patientOrderTriageSourceId;
	}

	@Nonnull
	public PatientOrderCareTypeId getPatientOrderCareTypeId() {
		return this.patientOrderCareTypeId;
	}

	@Nonnull
	public String getPatientOrderCareTypeDescription() {
		return this.patientOrderCareTypeDescription;
	}

	@Nonnull
	public List<PatientOrderTriageGroupFocusApiResponse> getPatientOrderFocusTypes() {
		return this.patientOrderFocusTypes;
	}
}