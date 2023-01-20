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

import com.cobaltplatform.api.model.db.Institution;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.service.NavigationItem;
import com.cobaltplatform.api.service.TopicCenterService;
import com.cobaltplatform.api.util.Formatter;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.lokalized.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class InstitutionApiResponse {
	@Nonnull
	private final InstitutionId institutionId;
	@Nullable
	private final UUID providerTriageScreeningFlowId;
	@Nullable
	private final UUID contentScreeningFlowId;
	@Nullable
	private final UUID groupSessionsScreeningFlowId;
	@Nullable
	private final UUID integratedCareScreeningFlowId;
	@Nonnull
	private final String name;
	@Nullable
	@Deprecated
	private final String crisisContent;
	@Nullable
	@Deprecated
	private final String privacyContent;
	@Nullable
	@Deprecated
	private final String covidContent;
	@Nullable
	private final Boolean requireConsentForm;
	@Nullable
	@Deprecated
	private final String consentFormContent;
	@Nullable
	private final String calendarDescription;
	@Nullable
	private final Boolean supportEnabled;
	@Nullable
	@Deprecated
	private final String wellBeingContent;
	@Nullable
	@Deprecated
	private final Boolean ssoEnabled;
	@Nullable
	@Deprecated
	private final Boolean emailEnabled;
	@Nonnull
	private final Boolean emailSignupEnabled;
	@Nullable
	@Deprecated
	private final Boolean anonymousEnabled;
	@Nonnull
	private final Boolean integratedCareEnabled;
	@Nonnull
	private final String supportEmailAddress;
	@Nonnull
	private final Boolean immediateAccessEnabled;
	@Nonnull
	private final Boolean contactUsEnabled;
	@Nonnull
	private final Boolean recommendedContentEnabled;
	@Nonnull
	private final Boolean userSubmittedContentEnabled;
	@Nonnull
	private final Boolean userSubmittedGroupSessionEnabled;
	@Nonnull
	private final Boolean userSubmittedGroupSessionRequestEnabled;
	@Nullable
	private final String ga4MeasurementId;
	@Nonnull
	private final List<NavigationItem> additionalNavigationItems;

	// Note: requires FactoryModuleBuilder entry in AppModule
	@ThreadSafe
	public interface InstitutionApiResponseFactory {
		@Nonnull
		InstitutionApiResponse create(@Nonnull Institution institution);
	}

	@AssistedInject
	public InstitutionApiResponse(@Nonnull TopicCenterService topicCenterService,
																@Nonnull Formatter formatter,
																@Nonnull Strings strings,
																@Assisted @Nonnull Institution institution) {
		requireNonNull(topicCenterService);
		requireNonNull(formatter);
		requireNonNull(strings);
		requireNonNull(institution);

		// TODO: we are "blanking out" some fields until FE can transition away from using them.
		// This is to provide backwards compatibility for JS clients, so they don't blow up when BE is updated.
		// In the future, we will remove these entirely.

		this.institutionId = institution.getInstitutionId();
		this.providerTriageScreeningFlowId = institution.getProviderTriageScreeningFlowId();
		this.contentScreeningFlowId = institution.getContentScreeningFlowId();
		this.groupSessionsScreeningFlowId = institution.getGroupSessionsScreeningFlowId();
		this.integratedCareScreeningFlowId = institution.getIntegratedCareScreeningFlowId();
		this.name = institution.getName();
		this.crisisContent = ""; // institution.getCrisisContent();
		this.privacyContent = ""; // institution.getPrivacyContent();
		this.covidContent = ""; // institution.getCovidContent();
		this.requireConsentForm = institution.getRequireConsentForm();
		this.consentFormContent = ""; // institution.getConsentFormContent();
		this.calendarDescription = institution.getCalendarDescription();
		this.supportEnabled = institution.getSupportEnabled();
		this.wellBeingContent = ""; // institution.getWellBeingContent();
		this.ssoEnabled = false; // institution.getSsoEnabled();
		this.anonymousEnabled = false; // institution.getAnonymousEnabled();
		this.emailEnabled = false; // institution.getEmailEnabled();
		this.emailSignupEnabled = institution.getEmailSignupEnabled();
		this.supportEmailAddress = institution.getSupportEmailAddress();
		this.immediateAccessEnabled = institution.getImmediateAccessEnabled();
		this.contactUsEnabled = institution.getContactUsEnabled();
		this.recommendedContentEnabled = institution.getRecommendedContentEnabled();
		this.userSubmittedContentEnabled = institution.getUserSubmittedContentEnabled();
		this.userSubmittedGroupSessionEnabled = institution.getUserSubmittedGroupSessionEnabled();
		this.userSubmittedGroupSessionRequestEnabled = institution.getUserSubmittedGroupSessionRequestEnabled();
		this.integratedCareEnabled = institution.getIntegratedCareEnabled();
		this.ga4MeasurementId = institution.getGa4MeasurementId();
		this.additionalNavigationItems = topicCenterService.findTopicCenterNavigationItemsByInstitutionId(institutionId);
	}

	@Nonnull
	public InstitutionId getInstitutionId() {
		return this.institutionId;
	}

	@Nullable
	public UUID getProviderTriageScreeningFlowId() {
		return this.providerTriageScreeningFlowId;
	}

	@Nullable
	public UUID getContentScreeningFlowId() {
		return this.contentScreeningFlowId;
	}

	@Nullable
	public UUID getGroupSessionsScreeningFlowId() {
		return this.groupSessionsScreeningFlowId;
	}

	@Nullable
	public UUID getIntegratedCareScreeningFlowId() {
		return this.integratedCareScreeningFlowId;
	}

	@Nonnull
	public String getName() {
		return this.name;
	}

	@Nullable
	@Deprecated
	public String getCrisisContent() {
		return this.crisisContent;
	}

	@Nullable
	@Deprecated
	public String getPrivacyContent() {
		return this.privacyContent;
	}

	@Nullable
	@Deprecated
	public String getCovidContent() {
		return this.covidContent;
	}

	@Nullable
	public Boolean getRequireConsentForm() {
		return this.requireConsentForm;
	}

	@Nullable
	@Deprecated
	public String getConsentFormContent() {
		return this.consentFormContent;
	}

	@Nullable
	public String getCalendarDescription() {
		return this.calendarDescription;
	}

	@Nullable
	public Boolean getSupportEnabled() {
		return this.supportEnabled;
	}

	@Nullable
	@Deprecated
	public String getWellBeingContent() {
		return this.wellBeingContent;
	}

	@Nullable
	@Deprecated
	public Boolean getSsoEnabled() {
		return this.ssoEnabled;
	}

	@Nullable
	@Deprecated
	public Boolean getEmailEnabled() {
		return this.emailEnabled;
	}

	@Nullable
	@Deprecated
	public Boolean getAnonymousEnabled() {
		return this.anonymousEnabled;
	}

	@Nonnull
	public Boolean getEmailSignupEnabled() {
		return this.emailSignupEnabled;
	}

	@Nonnull
	public String getSupportEmailAddress() {
		return this.supportEmailAddress;
	}

	@Nonnull
	public Boolean getImmediateAccessEnabled() {
		return this.immediateAccessEnabled;
	}

	@Nonnull
	public Boolean getContactUsEnabled() {
		return this.contactUsEnabled;
	}

	@Nonnull
	public Boolean getRecommendedContentEnabled() {
		return this.recommendedContentEnabled;
	}

	@Nonnull
	public Boolean getUserSubmittedContentEnabled() {
		return this.userSubmittedContentEnabled;
	}

	@Nonnull
	public Boolean getUserSubmittedGroupSessionEnabled() {
		return this.userSubmittedGroupSessionEnabled;
	}

	@Nonnull
	public Boolean getUserSubmittedGroupSessionRequestEnabled() {
		return this.userSubmittedGroupSessionRequestEnabled;
	}

	@Nonnull
	public Boolean getIntegratedCareEnabled() {
		return this.integratedCareEnabled;
	}

	@Nonnull
	public Optional<String> getGa4MeasurementId() {
		return Optional.ofNullable(this.ga4MeasurementId);
	}

	@Nonnull
	public List<NavigationItem> getAdditionalNavigationItems() {
		return this.additionalNavigationItems;
	}
}