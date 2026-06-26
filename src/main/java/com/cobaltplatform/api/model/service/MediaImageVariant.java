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

package com.cobaltplatform.api.model.service;

import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Immutable
public class MediaImageVariant {
	@Nonnull
	private final UUID imageId;
	@Nullable
	private final UUID sourceImageId;
	@Nonnull
	private final FileUploadTypeId fileUploadTypeId;
	@Nonnull
	private final ImageType imageType;
	@Nullable
	private final String aspectRatio;
	@Nonnull
	private final Boolean thumbnail;

	public enum ImageType {
		RAW,
		CROP,
		THUMBNAIL
	}

	public MediaImageVariant(@Nonnull UUID imageId,
													 @Nullable UUID sourceImageId,
													 @Nonnull FileUploadTypeId fileUploadTypeId,
													 @Nonnull ImageType imageType,
													 @Nullable String aspectRatio,
													 @Nonnull Boolean thumbnail) {
		requireNonNull(imageId);
		requireNonNull(fileUploadTypeId);
		requireNonNull(imageType);
		requireNonNull(thumbnail);

		this.imageId = imageId;
		this.sourceImageId = sourceImageId;
		this.fileUploadTypeId = fileUploadTypeId;
		this.imageType = imageType;
		this.aspectRatio = aspectRatio;
		this.thumbnail = thumbnail;
	}

	@Nonnull
	public UUID getImageId() {
		return this.imageId;
	}

	@Nullable
	public UUID getSourceImageId() {
		return this.sourceImageId;
	}

	@Nonnull
	public FileUploadTypeId getFileUploadTypeId() {
		return this.fileUploadTypeId;
	}

	@Nonnull
	public ImageType getImageType() {
		return this.imageType;
	}

	@Nullable
	public String getAspectRatio() {
		return this.aspectRatio;
	}

	@Nonnull
	public Boolean getThumbnail() {
		return this.thumbnail;
	}
}
