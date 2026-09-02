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

import com.cobaltplatform.api.model.api.response.ProviderListDetailsApiResponse.ProviderAppointmentModalityId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.UUID;

/**
 * A provider profile whose booking action is delegated to an existing institution referrer.
 *
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class ProviderReferralBooking {
	@Nullable
	private UUID providerId;
	@Nullable
	private UUID institutionReferrerId;
	@Nullable
	private String urlName;
	@Nullable
	private UUID intakeScreeningFlowId;
	@Nullable
	private ProviderAppointmentModalityId appointmentModalityId;

	@Nullable
	public UUID getProviderId() {
		return this.providerId;
	}

	public void setProviderId(@Nullable UUID providerId) {
		this.providerId = providerId;
	}

	@Nullable
	public UUID getInstitutionReferrerId() {
		return this.institutionReferrerId;
	}

	public void setInstitutionReferrerId(@Nullable UUID institutionReferrerId) {
		this.institutionReferrerId = institutionReferrerId;
	}

	@Nullable
	public String getUrlName() {
		return this.urlName;
	}

	public void setUrlName(@Nullable String urlName) {
		this.urlName = urlName;
	}

	@Nullable
	public UUID getIntakeScreeningFlowId() {
		return this.intakeScreeningFlowId;
	}

	public void setIntakeScreeningFlowId(@Nullable UUID intakeScreeningFlowId) {
		this.intakeScreeningFlowId = intakeScreeningFlowId;
	}

	@Nullable
	public ProviderAppointmentModalityId getAppointmentModalityId() {
		return this.appointmentModalityId;
	}

	public void setAppointmentModalityId(@Nullable ProviderAppointmentModalityId appointmentModalityId) {
		this.appointmentModalityId = appointmentModalityId;
	}
}
