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

import com.cobaltplatform.api.model.db.AccountSource.AccountSourceId;
import com.cobaltplatform.api.model.db.BetaStatus.BetaStatusId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.Role.RoleId;
import com.cobaltplatform.api.model.db.SourceSystem.SourceSystemId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/**
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class Account {
	@Nullable
	private UUID accountId;
	@Nullable
	private RoleId roleId;
	@Nullable
	private InstitutionId institutionId;
	@Nullable
	private AccountSourceId accountSourceId;
	@Nullable
	private SourceSystemId sourceSystemId;
	@Nullable
	private BetaStatusId betaStatusId;
	@Nullable
	private UUID providerId;
	@Nullable
	private String emailAddress;
	@Nullable
	private String password;
	@Nullable
	private String firstName;
	@Nullable
	private String lastName;
	@Nullable
	private String displayName;
	@Nullable
	private String phoneNumber;
	@Nullable
	private String ssoAttributes;
	@Nullable
	private String epicPatientId;
	@Nullable
	private String epicPatientIdType;
	@Nullable
	private Boolean epicPatientCreatedByCobalt;
	@Nullable
	private ZoneId timeZone;
	@Nullable
	private Locale locale;
	@Nullable
	private Boolean consentFormAccepted;
	@Nullable
	private Instant consentFormAcceptedDate;
	@Nullable
	private Instant created;
	@Nullable
	private Instant lastUpdated;

	@Nullable
	public UUID getAccountId() {
		return accountId;
	}

	public void setAccountId(@Nullable UUID accountId) {
		this.accountId = accountId;
	}

	@Nullable
	public RoleId getRoleId() {
		return roleId;
	}

	public void setRoleId(@Nullable RoleId roleId) {
		this.roleId = roleId;
	}

	@Nullable
	public InstitutionId getInstitutionId() {
		return institutionId;
	}

	public void setInstitutionId(@Nullable InstitutionId institutionId) {
		this.institutionId = institutionId;
	}

	@Nullable
	public AccountSourceId getAccountSourceId() {
		return accountSourceId;
	}

	public void setAccountSourceId(@Nullable AccountSourceId accountSourceId) {
		this.accountSourceId = accountSourceId;
	}

	@Nullable
	public SourceSystemId getSourceSystemId() {
		return sourceSystemId;
	}

	public void setSourceSystemId(@Nullable SourceSystemId sourceSystemId) {
		this.sourceSystemId = sourceSystemId;
	}

	@Nullable
	public BetaStatusId getBetaStatusId() {
		return betaStatusId;
	}

	public void setBetaStatusId(@Nullable BetaStatusId betaStatusId) {
		this.betaStatusId = betaStatusId;
	}

	@Nullable
	public UUID getProviderId() {
		return providerId;
	}

	public void setProviderId(@Nullable UUID providerId) {
		this.providerId = providerId;
	}

	@Nullable
	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(@Nullable String emailAddress) {
		this.emailAddress = emailAddress;
	}

	@Nullable
	public String getPassword() {
		return password;
	}

	public void setPassword(@Nullable String password) {
		this.password = password;
	}

	@Nullable
	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(@Nullable String firstName) {
		this.firstName = firstName;
	}

	@Nullable
	public String getLastName() {
		return lastName;
	}

	public void setLastName(@Nullable String lastName) {
		this.lastName = lastName;
	}

	@Nullable
	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(@Nullable String displayName) {
		this.displayName = displayName;
	}

	@Nullable
	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(@Nullable String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	@Nullable
	public String getSsoAttributes() {
		return ssoAttributes;
	}

	public void setSsoAttributes(@Nullable String ssoAttributes) {
		this.ssoAttributes = ssoAttributes;
	}

	@Nullable
	public ZoneId getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(@Nullable ZoneId timeZone) {
		this.timeZone = timeZone;
	}

	@Nullable
	public Locale getLocale() {
		return locale;
	}

	public void setLocale(@Nullable Locale locale) {
		this.locale = locale;
	}

	@Nullable
	public Boolean getConsentFormAccepted() {
		return consentFormAccepted;
	}

	public void setConsentFormAccepted(@Nullable Boolean consentFormAccepted) {
		this.consentFormAccepted = consentFormAccepted;
	}

	@Nullable
	public Instant getConsentFormAcceptedDate() {
		return consentFormAcceptedDate;
	}

	public void setConsentFormAcceptedDate(@Nullable Instant consentFormAcceptedDate) {
		this.consentFormAcceptedDate = consentFormAcceptedDate;
	}

	@Nullable
	public String getEpicPatientId() {
		return epicPatientId;
	}

	public void setEpicPatientId(@Nullable String epicPatientId) {
		this.epicPatientId = epicPatientId;
	}

	@Nullable
	public String getEpicPatientIdType() {
		return epicPatientIdType;
	}

	public void setEpicPatientIdType(@Nullable String epicPatientIdType) {
		this.epicPatientIdType = epicPatientIdType;
	}

	@Nullable
	public Boolean getEpicPatientCreatedByCobalt() {
		return epicPatientCreatedByCobalt;
	}

	public void setEpicPatientCreatedByCobalt(@Nullable Boolean epicPatientCreatedByCobalt) {
		this.epicPatientCreatedByCobalt = epicPatientCreatedByCobalt;
	}

	@Nullable
	public Instant getCreated() {
		return created;
	}

	public void setCreated(@Nullable Instant created) {
		this.created = created;
	}

	@Nullable
	public Instant getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(@Nullable Instant lastUpdated) {
		this.lastUpdated = lastUpdated;
	}
}