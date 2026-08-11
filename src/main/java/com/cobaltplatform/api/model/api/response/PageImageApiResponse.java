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
import com.cobaltplatform.api.model.db.Image;
import com.cobaltplatform.api.util.Formatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.Objects.requireNonNull;

/**
 * Repository image data for a page-builder image placement.
 *
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class PageImageApiResponse extends MediaImageApiResponse {
	@Nullable
	private final MediaImageApiResponse thumbnail;

	public PageImageApiResponse(@Nonnull Formatter formatter,
																@Nonnull MediaImageApiResponseFactory mediaImageApiResponseFactory,
																@Nonnull Image image,
																@Nullable Image thumbnail) {
		super(formatter, requireNonNull(image));
		requireNonNull(mediaImageApiResponseFactory);

		this.thumbnail = thumbnail == null ? null : mediaImageApiResponseFactory.create(thumbnail);
	}

	@Nullable
	public MediaImageApiResponse getThumbnail() {
		return this.thumbnail;
	}
}
