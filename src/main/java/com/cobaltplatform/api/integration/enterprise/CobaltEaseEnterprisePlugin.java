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

package com.cobaltplatform.api.integration.enterprise;

import com.cobaltplatform.api.Configuration;
import com.cobaltplatform.api.model.api.request.CreateAccountRequest;
import com.cobaltplatform.api.model.api.request.EmailPasswordAccessTokenRequest;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.AccountSource.AccountSourceId;
import com.cobaltplatform.api.model.db.Institution;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.Role.RoleId;
import com.cobaltplatform.api.service.AccountService;
import com.cobaltplatform.api.service.InstitutionService;
import com.cobaltplatform.api.service.PatientOrderService;
import com.cobaltplatform.api.service.PatientOrderService.CreatePatientOrderSelfReferralMode;
import com.cobaltplatform.api.util.Authenticator;
import com.cobaltplatform.api.util.AwsSecretManagerClient;
import com.cobaltplatform.api.util.ValidationUtility;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class CobaltEaseEnterprisePlugin extends DefaultEnterprisePlugin implements EnterprisePlugin {
	@Nonnull
	private final PatientOrderService patientOrderService;
	@Nonnull
	private final AccountService accountService;
	@Nonnull
	private final Authenticator authenticator;

	@Inject
	public CobaltEaseEnterprisePlugin(@Nonnull InstitutionService institutionService,
																			@Nonnull AwsSecretManagerClient awsSecretManagerClient,
																			@Nonnull Configuration configuration,
																			@Nonnull PatientOrderService patientOrderService,
																			@Nonnull AccountService accountService,
																			@Nonnull Authenticator authenticator) {
		super(institutionService, awsSecretManagerClient, configuration);

		requireNonNull(patientOrderService);
		requireNonNull(accountService);
		requireNonNull(authenticator);

		this.patientOrderService = patientOrderService;
		this.accountService = accountService;
		this.authenticator = authenticator;
	}

	@Nonnull
	@Override
	public InstitutionId getInstitutionId() {
		return InstitutionId.COBALT_EASE;
	}

	@Override
	public void applyCustomProcessingForEmailPasswordAccessTokenRequest(@Nonnull EmailPasswordAccessTokenRequest request) {
		requireNonNull(request);

		Institution institution = getInstitutionService().findInstitutionById(getInstitutionId()).get();

		if (!getConfiguration().isProduction()
				&& ValidationUtility.isValidEmailAddress(request.getEmailAddress())
				&& trimToNull(request.getPassword()) != null
				&& institution.getIntegratedCareEnabled()) {
			Account existingAccount = getAccountService().findAccountByEmailAddressAndAccountSourceId(request.getEmailAddress(), AccountSourceId.EMAIL_PASSWORD, getInstitutionId()).orElse(null);

			if (existingAccount == null) {
				getLogger().info("An account with email address '{}' does not exist, creating and self-referring an EASE order...", request.getEmailAddress());

				UUID accountId = getAccountService().createAccount(new CreateAccountRequest() {{
					setRoleId(RoleId.PATIENT);
					setInstitutionId(institution.getInstitutionId());
					setAccountSourceId(AccountSourceId.EMAIL_PASSWORD);
					setEmailAddress(request.getEmailAddress());
					setPassword(getAuthenticator().hashPassword(request.getPassword()));
				}});

				getPatientOrderService().createPatientOrderForSelfReferral(accountId, CreatePatientOrderSelfReferralMode.TEST_ORDER);
			}
		}
	}

	@Override
	public void applyCustomProcessingForAnonymousAccountCreation(@Nonnull Account account) {
		requireNonNull(account);

		Institution institution = getInstitutionService().findInstitutionById(getInstitutionId()).get();

		if (account.getInstitutionId() == getInstitutionId()
				&& account.getAccountSourceId() == AccountSourceId.ANONYMOUS
				&& institution.getIntegratedCareEnabled()) {
			getLogger().info("Anonymous account ID '{}' created, creating self-referred EASE test order...", account.getAccountId());
			getPatientOrderService().createPatientOrderForSelfReferral(account.getAccountId(), CreatePatientOrderSelfReferralMode.TEST_ORDER);
		}
	}

	@Nonnull
	protected PatientOrderService getPatientOrderService() {
		return this.patientOrderService;
	}

	@Nonnull
	protected AccountService getAccountService() {
		return this.accountService;
	}

	@Nonnull
	protected Authenticator getAuthenticator() {
		return this.authenticator;
	}
}
