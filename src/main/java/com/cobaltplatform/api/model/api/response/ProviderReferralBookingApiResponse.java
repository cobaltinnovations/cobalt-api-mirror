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

import com.cobaltplatform.api.model.service.ProviderReferralBooking;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Describes the referral action for a provider-shaped profile.  Clients start the referrer's
 * screening flow instead of requesting or booking provider availability.
 *
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class ProviderReferralBookingApiResponse {
	@Nonnull
	private final UUID institutionReferrerId;
	@Nonnull
	private final String urlName;
	@Nullable
	private final UUID intakeScreeningFlowId;

	public ProviderReferralBookingApiResponse(@Nonnull ProviderReferralBooking providerReferralBooking) {
		requireNonNull(providerReferralBooking);

		this.institutionReferrerId = requireNonNull(providerReferralBooking.getInstitutionReferrerId());
		this.urlName = requireNonNull(providerReferralBooking.getUrlName());
		this.intakeScreeningFlowId = providerReferralBooking.getIntakeScreeningFlowId();
	}

	@Nonnull
	public UUID getInstitutionReferrerId() {
		return this.institutionReferrerId;
	}

	@Nonnull
	public String getUrlName() {
		return this.urlName;
	}

	@Nullable
	public UUID getIntakeScreeningFlowId() {
		return this.intakeScreeningFlowId;
	}
}
