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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.Instant;
import java.util.UUID;

/**
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class ResourcePacketCareResourceLocation {
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
	private String phoneNumber;
	@Nullable
	private String careResourceNotes;
	@Nullable
	private String notes;
	@Nullable
	private String websiteUrl;
	@Nullable
	private String emailAddress;
	@Nullable
	private UUID addressId;
	@Nullable
	private Instant created;
	@Nullable
	private Instant lastUpdated;

	@Nullable
	public UUID getResourcePacketCareResourceLocationId() {
		return resourcePacketCareResourceLocationId;
	}

	public void setResourcePacketCareResourceLocationId(@Nullable UUID resourcePacketCareResourceLocationId) {
		this.resourcePacketCareResourceLocationId = resourcePacketCareResourceLocationId;
	}

	@Nullable
	public UUID getResourcePacketId() {
		return resourcePacketId;
	}

	public void setResourcePacketId(@Nullable UUID resourcePacketId) {
		this.resourcePacketId = resourcePacketId;
	}

	@Nullable
	public UUID getCareResourceLocationId() {
		return careResourceLocationId;
	}

	public void setCareResourceLocationId(@Nullable UUID careResourceLocationId) {
		this.careResourceLocationId = careResourceLocationId;
	}

	@Nullable
	public UUID getCreatedByAccountId() {
		return createdByAccountId;
	}

	public void setCreatedByAccountId(@Nullable UUID createdByAccountId) {
		this.createdByAccountId = createdByAccountId;
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

	@Nullable
	public Integer getDisplayOrder() {
		return displayOrder;
	}

	public void setDisplayOrder(@Nullable Integer displayOrder) {
		this.displayOrder = displayOrder;
	}

	@Nullable
	public String getCreatedByAccountFirstName() {
		return createdByAccountFirstName;
	}

	public void setCreatedByAccountFirstName(@Nullable String createdByAccountFirstName) {
		this.createdByAccountFirstName = createdByAccountFirstName;
	}

	@Nullable
	public String getCreatedByAccountLastName() {
		return createdByAccountLastName;
	}

	public void setCreatedByAccountLastName(@Nullable String createdByAccountLastName) {
		this.createdByAccountLastName = createdByAccountLastName;
	}

	@Nullable
	public String getCareResourceLocationName() {
		return careResourceLocationName;
	}

	public void setCareResourceLocationName(@Nullable String careResourceLocationName) {
		this.careResourceLocationName = careResourceLocationName;
	}

	@Nullable
	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(@Nullable String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	@Nullable
	public String getCareResourceNotes() {
		return careResourceNotes;
	}

	public void setCareResourceNotes(@Nullable String careResourceNotes) {
		this.careResourceNotes = careResourceNotes;
	}

	@Nullable
	public String getWebsiteUrl() {
		return websiteUrl;
	}

	public void setWebsiteUrl(@Nullable String websiteUrl) {
		this.websiteUrl = websiteUrl;
	}

	@Nullable
	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(@Nullable String emailAddress) {
		this.emailAddress = emailAddress;
	}

	@Nullable
	public UUID getAddressId() {
		return addressId;
	}

	public void setAddressId(@Nullable UUID addressId) {
		this.addressId = addressId;
	}

	@Nullable
	public String getNotes() {
		return notes;
	}

	public void setNotes(@Nullable String notes) {
		this.notes = notes;
	}
}