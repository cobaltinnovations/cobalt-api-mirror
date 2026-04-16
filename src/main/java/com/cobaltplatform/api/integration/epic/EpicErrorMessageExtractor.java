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

package com.cobaltplatform.api.integration.epic;

import com.cobaltplatform.api.util.JsonMapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Extracts user-facing message content from EPIC error payloads that are embedded in {@link EpicException} messages.
 *
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class EpicErrorMessageExtractor {
	@Nonnull
	private static final String EPIC_RESPONSE_BODY_MARKER;
	@Nonnull
	private final JsonMapper jsonMapper;

	static {
		EPIC_RESPONSE_BODY_MARKER = "Response body was\n";
	}

	public EpicErrorMessageExtractor() {
		this(new JsonMapper());
	}

	public EpicErrorMessageExtractor(@Nonnull JsonMapper jsonMapper) {
		requireNonNull(jsonMapper);
		this.jsonMapper = jsonMapper;
	}

	@Nonnull
	public Optional<EpicErrorMessageDetails> extract(@Nullable EpicException epicException) {
		return extract(epicException == null ? null : epicException.getMessage());
	}

	@Nonnull
	public Optional<EpicErrorMessageDetails> extract(@Nullable String epicExceptionMessage) {
		String responseBody = extractResponseBody(epicExceptionMessage).orElse(null);

		if (responseBody == null)
			return Optional.empty();

		try {
			Map<String, Object> responseBodyAsMap = getJsonMapper().fromJson(responseBody, Map.class);
			String message = stringValue(responseBodyAsMap, "Message");
			String exceptionMessage = stringValue(responseBodyAsMap, "ExceptionMessage");

			if (message == null && exceptionMessage == null)
				return Optional.empty();

			return Optional.of(new EpicErrorMessageDetails(message, exceptionMessage, responseBody));
		} catch (Exception ignored) {
			return Optional.empty();
		}
	}

	@Nonnull
	protected Optional<String> extractResponseBody(@Nullable String epicExceptionMessage) {
		if (trimToNull(epicExceptionMessage) == null)
			return Optional.empty();

		int markerIndex = epicExceptionMessage.indexOf(EPIC_RESPONSE_BODY_MARKER);

		if (markerIndex < 0)
			return Optional.empty();

		String responseBody = trimToNull(epicExceptionMessage.substring(markerIndex + EPIC_RESPONSE_BODY_MARKER.length()));
		return Optional.ofNullable(responseBody);
	}

	@Nullable
	protected String stringValue(@Nullable Map<String, Object> map,
															 @Nonnull String key) {
		requireNonNull(key);

		if (map == null)
			return null;

		Object value = map.get(key);
		if (value == null)
			return null;

		return trimToNull(value.toString());
	}

	@Nonnull
	protected JsonMapper getJsonMapper() {
		return this.jsonMapper;
	}

	@ThreadSafe
	public static class EpicErrorMessageDetails {
		@Nullable
		private final String message;
		@Nullable
		private final String exceptionMessage;
		@Nullable
		private final String responseBody;

		public EpicErrorMessageDetails(@Nullable String message,
																 @Nullable String exceptionMessage,
																 @Nullable String responseBody) {
			this.message = trimToNull(message);
			this.exceptionMessage = trimToNull(exceptionMessage);
			this.responseBody = trimToNull(responseBody);
		}

		@Nonnull
		public Optional<String> preferredMessage() {
			String message = trimToNull(getMessage());

			if (message != null && !isGenericEpicMessage(message))
				return Optional.of(message);

			return Optional.ofNullable(firstNonNull(trimToNull(getExceptionMessage()), message));
		}

		protected boolean isGenericEpicMessage(@Nullable String message) {
			String normalizedMessage = trimToNull(message);

			if (normalizedMessage == null)
				return false;

			return "An error has occurred.".equalsIgnoreCase(normalizedMessage)
					|| "An error occurred.".equalsIgnoreCase(normalizedMessage);
		}

		@Nullable
		public String getMessage() {
			return this.message;
		}

		@Nullable
		public String getExceptionMessage() {
			return this.exceptionMessage;
		}

		@Nullable
		public String getResponseBody() {
			return this.responseBody;
		}
	}
}
