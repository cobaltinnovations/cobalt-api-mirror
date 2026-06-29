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

import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.Image;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.util.Formatter;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class MediaImageApiResponse {
	@Nonnull
	private final UUID imageId;
	@Nonnull
	private final UUID fileUploadId;
	@Nullable
	private final UUID sourceImageId;
	@Nonnull
	private final InstitutionId institutionId;
	@Nonnull
	private final UUID createdByAccountId;
	@Nonnull
	private final FileUploadStatusId fileUploadStatusId;
	@Nonnull
	private final FileUploadTypeId fileUploadTypeId;
	@Nonnull
	private final Integer width;
	@Nonnull
	private final Integer height;
	@Nullable
	private final String imageAltText;
	@Nonnull
	private final String filename;
	@Nullable
	private final Number filesizeInBytes;
	@Nonnull
	private final String contentType;
	@Nonnull
	private final String url;
	@Nonnull
	private final Instant created;
	@Nonnull
	private final String createdDescription;
	@Nonnull
	private final Instant lastUpdated;
	@Nonnull
	private final String lastUpdatedDescription;

	// Note: requires FactoryModuleBuilder entry in AppModule
	@ThreadSafe
	public interface MediaImageApiResponseFactory {
		@Nonnull
		MediaImageApiResponse create(@Nonnull Image image);
	}

	@AssistedInject
	public MediaImageApiResponse(@Nonnull Formatter formatter,
															 @Assisted @Nonnull Image image) {
		requireNonNull(formatter);
		requireNonNull(image);

		this.imageId = image.getImageId();
		this.fileUploadId = image.getFileUploadId();
		this.sourceImageId = image.getSourceImageId();
		this.institutionId = image.getInstitutionId();
		this.createdByAccountId = image.getCreatedByAccountId();
		this.fileUploadStatusId = image.getFileUploadStatusId();
		this.fileUploadTypeId = image.getFileUploadTypeId();
		this.width = image.getWidth();
		this.height = image.getHeight();
		this.imageAltText = image.getImageAltText();
		this.filename = image.getFilename();
		this.filesizeInBytes = image.getFilesizeInBytes();
		this.contentType = image.getContentType();
		this.url = image.getUrl();
		this.created = image.getCreated();
		this.createdDescription = formatter.formatTimestamp(image.getCreated());
		this.lastUpdated = image.getLastUpdated();
		this.lastUpdatedDescription = formatter.formatTimestamp(image.getLastUpdated());
	}

	@Nonnull
	public UUID getImageId() {
		return this.imageId;
	}

	@Nonnull
	public UUID getFileUploadId() {
		return this.fileUploadId;
	}

	@Nullable
	public UUID getSourceImageId() {
		return this.sourceImageId;
	}

	@Nonnull
	public InstitutionId getInstitutionId() {
		return this.institutionId;
	}

	@Nonnull
	public UUID getCreatedByAccountId() {
		return this.createdByAccountId;
	}

	@Nonnull
	public FileUploadStatusId getFileUploadStatusId() {
		return this.fileUploadStatusId;
	}

	@Nonnull
	public FileUploadTypeId getFileUploadTypeId() {
		return this.fileUploadTypeId;
	}

	@Nonnull
	public Integer getWidth() {
		return this.width;
	}

	@Nonnull
	public Integer getHeight() {
		return this.height;
	}

	@Nullable
	public String getImageAltText() {
		return this.imageAltText;
	}

	@Nonnull
	public String getFilename() {
		return this.filename;
	}

	@Nullable
	public Number getFilesizeInBytes() {
		return this.filesizeInBytes;
	}

	@Nonnull
	public String getContentType() {
		return this.contentType;
	}

	@Nonnull
	public String getUrl() {
		return this.url;
	}

	@Nonnull
	public Instant getCreated() {
		return this.created;
	}

	@Nonnull
	public String getCreatedDescription() {
		return this.createdDescription;
	}

	@Nonnull
	public Instant getLastUpdated() {
		return this.lastUpdated;
	}

	@Nonnull
	public String getLastUpdatedDescription() {
		return this.lastUpdatedDescription;
	}
}
