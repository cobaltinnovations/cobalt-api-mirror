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

package com.cobaltplatform.api.web.resource;

import com.cobaltplatform.api.context.CurrentContext;
import com.cobaltplatform.api.integration.enterprise.EnterprisePlugin;
import com.cobaltplatform.api.integration.enterprise.EnterprisePluginProvider;
import com.cobaltplatform.api.model.api.response.CallToActionApiResponse.CallToActionApiResponseFactory;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.security.AuthenticationRequired;
import com.cobaltplatform.api.model.service.CallToAction;
import com.cobaltplatform.api.model.service.CallToActionDisplayAreaId;
import com.soklet.web.annotation.GET;
import com.soklet.web.annotation.QueryParameter;
import com.soklet.web.annotation.Resource;
import com.soklet.web.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Resource
@Singleton
@ThreadSafe
public class CallToActionResource {
	@Nonnull
	private final CallToActionApiResponseFactory callToActionApiResponseFactory;
	@Nonnull
	private final EnterprisePluginProvider enterprisePluginProvider;
	@Nonnull
	private final Provider<CurrentContext> currentContextProvider;
	@Nonnull
	private final Logger logger;

	@Inject
	public CallToActionResource(@Nonnull CallToActionApiResponseFactory callToActionApiResponseFactory,
															@Nonnull EnterprisePluginProvider enterprisePluginProvider,
															@Nonnull Provider<CurrentContext> currentContextProvider) {
		requireNonNull(callToActionApiResponseFactory);
		requireNonNull(enterprisePluginProvider);
		requireNonNull(currentContextProvider);

		this.callToActionApiResponseFactory = callToActionApiResponseFactory;
		this.enterprisePluginProvider = enterprisePluginProvider;
		this.currentContextProvider = currentContextProvider;
		this.logger = LoggerFactory.getLogger(getClass());
	}

	@Nonnull
	@GET("/calls-to-action")
	@AuthenticationRequired
	public Object callsToAction(@Nonnull @QueryParameter CallToActionDisplayAreaId callToActionDisplayAreaId) {
		requireNonNull(callToActionDisplayAreaId);

		Account account = getCurrentContext().getAccount().get();
		EnterprisePlugin enterprisePlugin = getEnterprisePluginProvider().enterprisePluginForInstitutionId(account.getInstitutionId());

		List<CallToAction> callsToAction = enterprisePlugin.determineCallsToAction(account, callToActionDisplayAreaId);

		return new ApiResponse(new HashMap<String, Object>() {{
			put("callsToAction", callsToAction.stream()
					.map(callToAction -> getCallToActionApiResponseFactory().create(callToAction))
					.collect(Collectors.toList()));
		}});
	}

	@Nonnull
	protected CallToActionApiResponseFactory getCallToActionApiResponseFactory() {
		return this.callToActionApiResponseFactory;
	}

	@Nonnull
	protected EnterprisePluginProvider getEnterprisePluginProvider() {
		return this.enterprisePluginProvider;
	}

	@Nonnull
	protected CurrentContext getCurrentContext() {
		return this.currentContextProvider.get();
	}

	@Nonnull
	protected Logger getLogger() {
		return this.logger;
	}
}
