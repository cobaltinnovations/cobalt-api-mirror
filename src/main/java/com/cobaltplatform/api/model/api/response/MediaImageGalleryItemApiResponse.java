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

import com.cobaltplatform.api.model.api.response.MediaImageApiResponse.MediaImageApiResponseFactory;
import com.cobaltplatform.api.model.api.response.MediaImageVariantApiResponse.MediaImageVariantApiResponseFactory;
import com.cobaltplatform.api.model.service.MediaImageGalleryItem;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class MediaImageGalleryItemApiResponse {
	@Nonnull
	private final MediaImageApiResponse thumbnail;
	@Nonnull
	private final UUID sourceImageId;
	@Nonnull
	private final List<MediaImageVariantApiResponse> variants;

	// Note: requires FactoryModuleBuilder entry in AppModule
	@ThreadSafe
	public interface MediaImageGalleryItemApiResponseFactory {
		@Nonnull
		MediaImageGalleryItemApiResponse create(@Nonnull MediaImageGalleryItem mediaImageGalleryItem);
	}

	@AssistedInject
	public MediaImageGalleryItemApiResponse(@Nonnull MediaImageApiResponseFactory mediaImageApiResponseFactory,
																					@Nonnull MediaImageVariantApiResponseFactory mediaImageVariantApiResponseFactory,
																					@Assisted @Nonnull MediaImageGalleryItem mediaImageGalleryItem) {
		requireNonNull(mediaImageApiResponseFactory);
		requireNonNull(mediaImageVariantApiResponseFactory);
		requireNonNull(mediaImageGalleryItem);

		this.thumbnail = mediaImageApiResponseFactory.create(mediaImageGalleryItem.getThumbnailImage());
		this.sourceImageId = mediaImageGalleryItem.getSourceImageId();
		this.variants = mediaImageGalleryItem.getVariants().stream()
				.map(mediaImageVariant -> mediaImageVariantApiResponseFactory.create(mediaImageVariant))
				.collect(Collectors.toList());
	}

	@Nonnull
	public MediaImageApiResponse getThumbnail() {
		return this.thumbnail;
	}

	@Nonnull
	public UUID getSourceImageId() {
		return this.sourceImageId;
	}

	@Nonnull
	public List<MediaImageVariantApiResponse> getVariants() {
		return this.variants;
	}
}
