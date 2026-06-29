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

import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.Instant;
import java.util.UUID;

/**
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class Image {
	@Nullable
	private UUID imageId;
	@Nullable
	private UUID fileUploadId;
	@Nullable
	private UUID sourceImageId;
	@Nullable
	private InstitutionId institutionId;
	@Nullable
	private UUID createdByAccountId;
	@Nullable
	private UUID fileUploadAccountId;
	@Nullable
	private FileUploadStatusId fileUploadStatusId;
	@Nullable
	private FileUploadTypeId fileUploadTypeId;
	@Nullable
	private Integer width;
	@Nullable
	private Integer height;
	@Nullable
	private Boolean active;
	@Nullable
	private String imageAltText;
	@Nullable
	private String filename;
	@Nullable
	private Number filesizeInBytes;
	@Nullable
	private String contentType;
	@Nullable
	private String url;
	@Nullable
	private String storageBucket;
	@Nullable
	private String storageKey;
	@Nullable
	private String storageRegion;
	@Nullable
	private Instant created;
	@Nullable
	private Instant lastUpdated;

	@Nullable
	public UUID getImageId() {
		return this.imageId;
	}

	public void setImageId(@Nullable UUID imageId) {
		this.imageId = imageId;
	}

	@Nullable
	public UUID getFileUploadId() {
		return this.fileUploadId;
	}

	public void setFileUploadId(@Nullable UUID fileUploadId) {
		this.fileUploadId = fileUploadId;
	}

	@Nullable
	public UUID getSourceImageId() {
		return this.sourceImageId;
	}

	public void setSourceImageId(@Nullable UUID sourceImageId) {
		this.sourceImageId = sourceImageId;
	}

	@Nullable
	public InstitutionId getInstitutionId() {
		return this.institutionId;
	}

	public void setInstitutionId(@Nullable InstitutionId institutionId) {
		this.institutionId = institutionId;
	}

	@Nullable
	public UUID getCreatedByAccountId() {
		return this.createdByAccountId;
	}

	public void setCreatedByAccountId(@Nullable UUID createdByAccountId) {
		this.createdByAccountId = createdByAccountId;
	}

	@Nullable
	public UUID getFileUploadAccountId() {
		return this.fileUploadAccountId;
	}

	public void setFileUploadAccountId(@Nullable UUID fileUploadAccountId) {
		this.fileUploadAccountId = fileUploadAccountId;
	}

	@Nullable
	public FileUploadStatusId getFileUploadStatusId() {
		return this.fileUploadStatusId;
	}

	public void setFileUploadStatusId(@Nullable FileUploadStatusId fileUploadStatusId) {
		this.fileUploadStatusId = fileUploadStatusId;
	}

	@Nullable
	public FileUploadTypeId getFileUploadTypeId() {
		return this.fileUploadTypeId;
	}

	public void setFileUploadTypeId(@Nullable FileUploadTypeId fileUploadTypeId) {
		this.fileUploadTypeId = fileUploadTypeId;
	}

	@Nullable
	public Integer getWidth() {
		return this.width;
	}

	public void setWidth(@Nullable Integer width) {
		this.width = width;
	}

	@Nullable
	public Integer getHeight() {
		return this.height;
	}

	public void setHeight(@Nullable Integer height) {
		this.height = height;
	}

	@Nullable
	public Boolean getActive() {
		return this.active;
	}

	public void setActive(@Nullable Boolean active) {
		this.active = active;
	}

	@Nullable
	public String getImageAltText() {
		return this.imageAltText;
	}

	public void setImageAltText(@Nullable String imageAltText) {
		this.imageAltText = imageAltText;
	}

	@Nullable
	public String getFilename() {
		return this.filename;
	}

	public void setFilename(@Nullable String filename) {
		this.filename = filename;
	}

	@Nullable
	public Number getFilesizeInBytes() {
		return this.filesizeInBytes;
	}

	public void setFilesizeInBytes(@Nullable Number filesizeInBytes) {
		this.filesizeInBytes = filesizeInBytes;
	}

	@Nullable
	public String getContentType() {
		return this.contentType;
	}

	public void setContentType(@Nullable String contentType) {
		this.contentType = contentType;
	}

	@Nullable
	public String getUrl() {
		return this.url;
	}

	public void setUrl(@Nullable String url) {
		this.url = url;
	}

	@Nullable
	public String getStorageBucket() {
		return this.storageBucket;
	}

	public void setStorageBucket(@Nullable String storageBucket) {
		this.storageBucket = storageBucket;
	}

	@Nullable
	public String getStorageKey() {
		return this.storageKey;
	}

	public void setStorageKey(@Nullable String storageKey) {
		this.storageKey = storageKey;
	}

	@Nullable
	public String getStorageRegion() {
		return this.storageRegion;
	}

	public void setStorageRegion(@Nullable String storageRegion) {
		this.storageRegion = storageRegion;
	}

	@Nullable
	public Instant getCreated() {
		return this.created;
	}

	public void setCreated(@Nullable Instant created) {
		this.created = created;
	}

	@Nullable
	public Instant getLastUpdated() {
		return this.lastUpdated;
	}

	public void setLastUpdated(@Nullable Instant lastUpdated) {
		this.lastUpdated = lastUpdated;
	}
}
