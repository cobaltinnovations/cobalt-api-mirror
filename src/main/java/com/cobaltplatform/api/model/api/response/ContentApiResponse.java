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

import com.cobaltplatform.api.model.api.response.ContentAudienceTypeApiResponse.ContentAudienceTypeApiResponseFactory;
import com.cobaltplatform.api.model.api.response.TagApiResponse.TagApiResponseFactory;
import com.cobaltplatform.api.model.db.Content;
import com.cobaltplatform.api.model.db.ContentStatus.ContentStatusId;
import com.cobaltplatform.api.model.db.ContentType.ContentTypeId;
import com.cobaltplatform.api.model.db.ContentVisibilityType.ContentVisibilityTypeId;
import com.cobaltplatform.api.service.ContentService;
import com.cobaltplatform.api.util.Formatter;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.lokalized.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class ContentApiResponse {
	@Nonnull
	private final UUID contentId;
	@Nonnull
	private final ContentTypeId contentTypeId;
	@Nonnull
	private final ContentVisibilityTypeId contentVisibilityTypeId;
	@Nonnull
	private final String title;
	@Nullable
	private final String url;
	@Nonnull
	private final Boolean neverEmbed;
	@Nullable
	private String imageUrl;
	@Nullable
	private final String description;
	@Nullable
	private final String author;
	@Nullable
	private final Instant created;
	@Nonnull
	private final String createdDescription;
	@Nullable
	private final Instant lastUpdated;
	@Nullable
	private final String lastUpdatedDescription;
	@Nullable
	private String contentTypeDescription;
	@Nullable
	private String callToAction;
	@Nullable
	private Boolean newFlag;
	@Nullable
	@Deprecated // prefer "durationInMinutesDescription"
	private String duration;
	@Nullable
	private Integer durationInMinutes;
	@Nullable
	private String durationInMinutesDescription;
	@Nonnull
	private final List<String> tagIds;
	@Nullable
	private final List<TagApiResponse> tags;
	@Nullable
	private List<ContentAudienceTypeApiResponse> contentAudienceTypes;

	@Nullable
	private ContentStatusId contentStatusId;

	public enum ContentApiResponseSupplement {
		TAGS,
		AUDIENCE_TYPES
	}

	// Note: requires FactoryModuleBuilder entry in AppModule
	@ThreadSafe
	public interface ContentApiResponseFactory {
		@Nonnull
		ContentApiResponse create(@Nonnull Content content);

		@Nonnull
		ContentApiResponse create(@Nonnull Content content,
															@Nonnull Set<ContentApiResponseSupplement> supplements);
	}

	@AssistedInject
	public ContentApiResponse(@Nonnull ContentService contentService,
														@Nonnull TagApiResponseFactory tagApiResponseFactory,
														@Nonnull ContentAudienceTypeApiResponseFactory contentAudienceTypeApiResponseFactory,
														@Nonnull Formatter formatter,
														@Nonnull Strings strings,
														@Assisted @Nonnull Content content) {
		this(contentService, tagApiResponseFactory, contentAudienceTypeApiResponseFactory, formatter, strings, content, Set.of());
	}

	@AssistedInject
	public ContentApiResponse(@Nonnull ContentService contentService,
														@Nonnull TagApiResponseFactory tagApiResponseFactory,
														@Nonnull ContentAudienceTypeApiResponseFactory contentAudienceTypeApiResponseFactory,
														@Nonnull Formatter formatter,
														@Nonnull Strings strings,
														@Assisted @Nonnull Content content,
														@Assisted @Nonnull Set<ContentApiResponseSupplement> supplements) {
		requireNonNull(contentService);
		requireNonNull(tagApiResponseFactory);
		requireNonNull(contentAudienceTypeApiResponseFactory);
		requireNonNull(formatter);
		requireNonNull(strings);
		requireNonNull(content);
		requireNonNull(supplements);

		this.contentId = content.getContentId();
		this.contentTypeId = content.getContentTypeId();
		this.contentVisibilityTypeId = content.getContentVisibilityTypeId();
		this.title = content.getTitle();
		this.url = content.getFileUploadId() != null ? content.getFileUrl() : content.getUrl();
		this.neverEmbed = content.getNeverEmbed();
		this.imageUrl = content.getImageUrl();
		this.description = content.getDescription();
		this.author = content.getAuthor();
		this.created = content.getCreated();
		this.createdDescription = formatter.formatTimestamp(content.getCreated());
		this.lastUpdated = content.getLastUpdated();
		this.lastUpdatedDescription = formatter.formatTimestamp(content.getLastUpdated());
		this.contentTypeDescription = content.getContentTypeDescription();
		this.callToAction = content.getCallToAction();
		this.newFlag = content.getNewFlag();

		// Deprecated field
		this.duration = content.getDurationInMinutes() != null ?
				strings.get("{{minutes}} min", new HashMap<>() {{
					put("minutes", formatter.formatNumber(content.getDurationInMinutes()));
				}}) : null;

		this.durationInMinutes = content.getDurationInMinutes();
		this.durationInMinutesDescription = content.getDurationInMinutes() != null ?
				strings.get("{{minutes}} min", new HashMap<>() {{
					put("minutes", formatter.formatNumber(content.getDurationInMinutes()));
				}}) : null;

		this.tagIds = content.getTags() == null ? Collections.emptyList() : content.getTags().stream()
				.map(tag -> tag.getTagId())
				.collect(Collectors.toList());

		List<TagApiResponse> tags = null;

		if (supplements.contains(ContentApiResponseSupplement.TAGS))
			tags = content.getTags() == null ? Collections.emptyList() : content.getTags().stream()
					.map(tag -> tagApiResponseFactory.create(tag))
					.collect(Collectors.toList());

		if (supplements.contains(ContentApiResponseSupplement.AUDIENCE_TYPES))
			this.contentAudienceTypes = contentService.findContentAudienceTypesByContentId(contentId).stream()
					.map(contentAudienceType -> contentAudienceTypeApiResponseFactory.create(contentAudienceType))
					.collect(Collectors.toList());

		this.tags = tags;

		this.contentStatusId =content.getContentStatusId();
	}

	@Nonnull
	public UUID getContentId() {
		return this.contentId;
	}

	@Nonnull
	public ContentTypeId getContentTypeId() {
		return this.contentTypeId;
	}

	@Nonnull
	public ContentVisibilityTypeId getContentVisibilityTypeId() {
		return this.contentVisibilityTypeId;
	}

	@Nonnull
	public String getTitle() {
		return this.title;
	}

	@Nullable
	public String getUrl() {
		return this.url;
	}

	@Nonnull
	public Boolean getNeverEmbed() {
		return this.neverEmbed;
	}

	@Nullable
	public String getImageUrl() {
		return this.imageUrl;
	}

	@Nullable
	public String getDescription() {
		return this.description;
	}

	@Nullable
	public String getAuthor() {
		return this.author;
	}

	@Nullable
	public Instant getCreated() {
		return this.created;
	}

	@Nonnull
	public String getCreatedDescription() {
		return this.createdDescription;
	}

	@Nullable
	public Instant getLastUpdated() {
		return this.lastUpdated;
	}

	@Nullable
	public String getLastUpdatedDescription() {
		return this.lastUpdatedDescription;
	}

	@Nullable
	public String getContentTypeDescription() {
		return this.contentTypeDescription;
	}

	@Nullable
	public String getCallToAction() {
		return this.callToAction;
	}

	@Nullable
	public Boolean getNewFlag() {
		return this.newFlag;
	}

	@Nullable
	public String getDuration() {
		return this.duration;
	}

	@Nullable
	public Integer getDurationInMinutes() {
		return this.durationInMinutes;
	}

	@Nullable
	public String getDurationInMinutesDescription() {
		return this.durationInMinutesDescription;
	}

	public void setDuration(@Nullable String duration) {
		this.duration = duration;
	}

	@Nonnull
	public List<String> getTagIds() {
		return this.tagIds;
	}

	@Nonnull
	public Optional<List<TagApiResponse>> getTags() {
		return Optional.ofNullable(this.tags);
	}

	@Nonnull
	public Optional<List<ContentAudienceTypeApiResponse>> getContentAudienceTypes() {
		return Optional.ofNullable(this.contentAudienceTypes);
	}

	@Nullable
	public ContentStatusId getContentStatusId() {
		return contentStatusId;
	}
}