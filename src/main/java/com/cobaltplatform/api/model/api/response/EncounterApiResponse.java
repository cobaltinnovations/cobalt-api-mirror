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

import com.cobaltplatform.api.model.service.Encounter;
import com.cobaltplatform.api.util.Formatter;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.lokalized.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.LocalDateTime;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Immutable
public class EncounterApiResponse {
	@Nonnull
	private final String csn;
	@Nonnull
	private final String status;
	@Nullable
	private final String subjectDisplay;
	@Nullable
	private final String serviceTypeText;
	@Nullable
	private final LocalDateTime periodStart;
	@Nullable
	private final String periodStartDescription;
	@Nullable
	private final LocalDateTime periodEnd;
	@Nullable
	private final String periodEndDescription;
	@Nonnull
	private final String description;

	// Note: requires FactoryModuleBuilder entry in AppModule
	@ThreadSafe
	public interface EncounterApiResponseFactory {
		@Nonnull
		EncounterApiResponse create(@Nonnull Encounter encounter);
	}

	@AssistedInject
	public EncounterApiResponse(@Nonnull Formatter formatter,
															@Nonnull Strings strings,
															@Assisted @Nonnull Encounter encounter) {
		requireNonNull(formatter);
		requireNonNull(strings);
		requireNonNull(encounter);

		this.csn = encounter.getCsn();
		this.status = encounter.getStatus();
		this.subjectDisplay = encounter.getSubjectDisplay();
		this.serviceTypeText = encounter.getServiceTypeText();
		this.periodStart = encounter.getPeriodStart();
		this.periodStartDescription = encounter.getPeriodStart() == null ? null : formatter.formatDateTime(encounter.getPeriodStart(), FormatStyle.MEDIUM, FormatStyle.MEDIUM);
		this.periodEnd = encounter.getPeriodEnd();
		this.periodEndDescription = encounter.getPeriodEnd() == null ? null : formatter.formatDateTime(encounter.getPeriodEnd(), FormatStyle.MEDIUM, FormatStyle.MEDIUM);

		List<String> descriptions = new ArrayList<>();
		descriptions.add(strings.get("CSN {{csn}}", Map.of("csn", encounter.getCsn())));

		if (getServiceTypeText() != null)
			descriptions.add(serviceTypeText);

		if (getPeriodStartDescription() != null)
			descriptions.add(getPeriodStartDescription());

		this.description = descriptions.stream().collect(Collectors.joining(", "));
	}

	@Nonnull
	public String getCsn() {
		return this.csn;
	}

	@Nonnull
	public String getStatus() {
		return this.status;
	}

	@Nullable
	public String getSubjectDisplay() {
		return this.subjectDisplay;
	}

	@Nullable
	public String getServiceTypeText() {
		return this.serviceTypeText;
	}

	@Nullable
	public LocalDateTime getPeriodStart() {
		return this.periodStart;
	}

	@Nullable
	public String getPeriodStartDescription() {
		return this.periodStartDescription;
	}

	@Nullable
	public LocalDateTime getPeriodEnd() {
		return this.periodEnd;
	}

	@Nullable
	public String getPeriodEndDescription() {
		return this.periodEndDescription;
	}

	@Nonnull
	public String getDescription() {
		return this.description;
	}
}