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
import com.cobaltplatform.api.model.db.Content;
import com.cobaltplatform.api.model.db.Image;
import com.cobaltplatform.api.model.service.AdminContent;
import com.cobaltplatform.api.util.Formatter;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class ContentImageApiResponse extends MediaImageApiResponse {
	@Nullable
	private final MediaImageApiResponse thumbnail;

	// Note: requires FactoryModuleBuilder entry in AppModule
	@ThreadSafe
	public interface ContentImageApiResponseFactory {
		@Nonnull
		ContentImageApiResponse create(@Nonnull Content content);

		@Nonnull
		ContentImageApiResponse create(@Nonnull AdminContent adminContent);
	}

	@AssistedInject
	public ContentImageApiResponse(@Nonnull Formatter formatter,
																 @Nonnull MediaImageApiResponseFactory mediaImageApiResponseFactory,
																 @Assisted @Nonnull Content content) {
		super(formatter, requireImage(content));
		requireNonNull(mediaImageApiResponseFactory);
		requireNonNull(content);

		this.thumbnail = content.getImageThumbnail() == null ? null : mediaImageApiResponseFactory.create(content.getImageThumbnail());
	}

	@AssistedInject
	public ContentImageApiResponse(@Nonnull Formatter formatter,
																 @Nonnull MediaImageApiResponseFactory mediaImageApiResponseFactory,
																 @Assisted @Nonnull AdminContent adminContent) {
		super(formatter, requireImage(adminContent));
		requireNonNull(mediaImageApiResponseFactory);
		requireNonNull(adminContent);

		this.thumbnail = adminContent.getImageThumbnail() == null ? null : mediaImageApiResponseFactory.create(adminContent.getImageThumbnail());
	}

	@Nullable
	public MediaImageApiResponse getThumbnail() {
		return this.thumbnail;
	}

	@Nonnull
	protected static Image requireImage(@Nonnull Content content) {
		requireNonNull(content);
		return requireNonNull(content.getImage());
	}

	@Nonnull
	protected static Image requireImage(@Nonnull AdminContent adminContent) {
		requireNonNull(adminContent);
		return requireNonNull(adminContent.getImage());
	}
}
