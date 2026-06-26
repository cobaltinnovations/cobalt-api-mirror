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
import com.cobaltplatform.api.model.service.MediaImageDetails;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class MediaImageDetailsApiResponse {
	@Nonnull
	private final MediaImageApiResponse image;
	@Nonnull
	private final List<MediaImageApiResponse> variants;

	// Note: requires FactoryModuleBuilder entry in AppModule
	@ThreadSafe
	public interface MediaImageDetailsApiResponseFactory {
		@Nonnull
		MediaImageDetailsApiResponse create(@Nonnull MediaImageDetails mediaImageDetails);
	}

	@AssistedInject
	public MediaImageDetailsApiResponse(@Nonnull MediaImageApiResponseFactory mediaImageApiResponseFactory,
																			@Assisted @Nonnull MediaImageDetails mediaImageDetails) {
		requireNonNull(mediaImageApiResponseFactory);
		requireNonNull(mediaImageDetails);

		this.image = mediaImageApiResponseFactory.create(mediaImageDetails.getImage());
		this.variants = mediaImageDetails.getVariants().stream()
				.map(image -> mediaImageApiResponseFactory.create(image))
				.collect(Collectors.toList());
	}

	@Nonnull
	public MediaImageApiResponse getImage() {
		return this.image;
	}

	@Nonnull
	public List<MediaImageApiResponse> getVariants() {
		return this.variants;
	}
}
