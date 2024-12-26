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


import com.cobaltplatform.api.context.CurrentContext;
import com.cobaltplatform.api.model.db.ResourcePacketCareResourceLocation;
import com.cobaltplatform.api.util.Formatter;
import com.cobaltplatform.api.util.Normalizer;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.FormatStyle;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Immutable
public class ResourcePacketCareResourceLocationApiResponse {
	@Nullable
	private UUID resourcePacketCareResourceLocationId;
	@Nullable
	private UUID resourcePacketId;
	@Nullable
	private UUID careResourceLocationId;
	@Nullable
	private UUID createdByAccountId;
	@Nullable
	private Integer displayOrder;
	@Nullable
	private String createdByAccountFirstName;
	@Nullable
	private String createdByAccountLastName;
	@Nullable
	private String careResourceLocationName;
	@Nullable
	private String addedByDisplayName;
	@Nullable
	private String addedDateDescription;

	// Note: requires FactoryModuleBuilder entry in AppModule
	@ThreadSafe
	public interface ResourcePacketCareResourceLocationApiResponseFactory {
		@Nonnull
		ResourcePacketCareResourceLocationApiResponse create(@Nonnull ResourcePacketCareResourceLocation resourcePacketCareResourceLocation);
	}

	@AssistedInject
	public ResourcePacketCareResourceLocationApiResponse(@Assisted @Nonnull ResourcePacketCareResourceLocation resourcePacketCareResourceLocation,
																											 @Nonnull Formatter formatter,
																											 @Nonnull javax.inject.Provider<CurrentContext> currentContextProvider) {
		requireNonNull(resourcePacketCareResourceLocation);
		LocalDate updatedDate = LocalDate.ofInstant(resourcePacketCareResourceLocation.getLastUpdated(), currentContextProvider.get().getTimeZone());
		this.resourcePacketCareResourceLocationId = resourcePacketCareResourceLocation.getResourcePacketCareResourceLocationId();
		this.resourcePacketId = resourcePacketCareResourceLocation.getResourcePacketId();
		this.careResourceLocationId = resourcePacketCareResourceLocation.getCareResourceLocationId();
		this.createdByAccountId = resourcePacketCareResourceLocation.getCreatedByAccountId();
		this.displayOrder = resourcePacketCareResourceLocation.getDisplayOrder();
		this.createdByAccountFirstName = resourcePacketCareResourceLocation.getCreatedByAccountFirstName();
		this.createdByAccountLastName = resourcePacketCareResourceLocation.getCreatedByAccountLastName();
		this.careResourceLocationName = resourcePacketCareResourceLocation.getCareResourceLocationName();
		this.addedDateDescription = formatter.formatDate(updatedDate);
		this.addedByDisplayName = Normalizer.normalizeName(resourcePacketCareResourceLocation.getCreatedByAccountFirstName(), resourcePacketCareResourceLocation.getCreatedByAccountLastName()).orElse(null);
	}

	@Nullable
	public UUID getResourcePacketCareResourceLocationId() {
		return resourcePacketCareResourceLocationId;
	}

	@Nullable
	public UUID getResourcePacketId() {
		return resourcePacketId;
	}

	@Nullable
	public UUID getCareResourceLocationId() {
		return careResourceLocationId;
	}

	@Nullable
	public UUID getCreatedByAccountId() {
		return createdByAccountId;
	}

	@Nullable
	public Integer getDisplayOrder() {
		return displayOrder;
	}

	@Nullable
	public String getCreatedByAccountFirstName() {
		return createdByAccountFirstName;
	}

	@Nullable
	public String getCreatedByAccountLastName() {
		return createdByAccountLastName;
	}

	@Nullable
	public String getCareResourceLocationName() {
		return careResourceLocationName;
	}
}