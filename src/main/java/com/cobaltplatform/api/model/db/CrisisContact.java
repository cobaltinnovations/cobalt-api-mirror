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

import javax.annotation.Nonnull;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/**
 * @author Transmogrify LLC.
 */
public class CrisisContact {

	@Nonnull
	private UUID crisisContactId;
	@Nonnull
	private String institutionID;
	@Nonnull
	private String emailAddress;
	@Nonnull
	private Boolean active;
	@Nonnull
	private Locale locale;
	@Nonnull
	private ZoneId timeZone;

	@Nonnull
	public UUID getCrisisContactId() {
		return crisisContactId;
	}

	public void setCrisisContactId(@Nonnull UUID crisisContactId) {
		this.crisisContactId = crisisContactId;
	}

	@Nonnull
	public String getInstitutionID() {
		return institutionID;
	}

	public void setInstitutionID(@Nonnull String institutionID) {
		this.institutionID = institutionID;
	}

	@Nonnull
	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(@Nonnull String emailAddress) {
		this.emailAddress = emailAddress;
	}

	@Nonnull
	public Boolean getActive() {
		return active;
	}

	public void setActive(@Nonnull Boolean active) {
		this.active = active;
	}

	@Nonnull
	public ZoneId getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(@Nonnull ZoneId timeZone) {
		this.timeZone = timeZone;
	}

	@Nonnull
	public Locale getLocale() {
		return locale;
	}

	public void setLocale(@Nonnull Locale locale) {
		this.locale = locale;
	}

}
