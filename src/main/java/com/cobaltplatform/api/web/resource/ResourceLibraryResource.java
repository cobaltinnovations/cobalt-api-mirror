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

package com.cobaltplatform.api.web.resource;

import com.cobaltplatform.api.context.CurrentContext;
import com.cobaltplatform.api.integration.enterprise.EnterprisePlugin;
import com.cobaltplatform.api.integration.enterprise.EnterprisePluginProvider;
import com.cobaltplatform.api.model.api.request.FindResourceLibraryContentRequest;
import com.cobaltplatform.api.model.api.response.ContentApiResponse;
import com.cobaltplatform.api.model.api.response.ContentApiResponse.ContentApiResponseFactory;
import com.cobaltplatform.api.model.api.response.TagApiResponse;
import com.cobaltplatform.api.model.api.response.TagApiResponse.TagApiResponseFactory;
import com.cobaltplatform.api.model.api.response.TagGroupApiResponse;
import com.cobaltplatform.api.model.api.response.TagGroupApiResponse.TagGroupApiResponseFactory;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.Content;
import com.cobaltplatform.api.model.db.Tag;
import com.cobaltplatform.api.model.db.TagGroup;
import com.cobaltplatform.api.model.security.AuthenticationRequired;
import com.cobaltplatform.api.model.service.FindResult;
import com.cobaltplatform.api.service.AuthorizationService;
import com.cobaltplatform.api.service.ContentService;
import com.cobaltplatform.api.service.TagService;
import com.cobaltplatform.api.util.Formatter;
import com.soklet.web.annotation.GET;
import com.soklet.web.annotation.PathParameter;
import com.soklet.web.annotation.QueryParameter;
import com.soklet.web.annotation.Resource;
import com.soklet.web.exception.NotFoundException;
import com.soklet.web.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Resource
@Singleton
@ThreadSafe
public class ResourceLibraryResource {
	@Nonnull
	private final ContentService contentService;
	@Nonnull
	private final TagService tagService;
	@Nonnull
	private final AuthorizationService authorizationService;
	@Nonnull
	private final EnterprisePluginProvider enterprisePluginProvider;
	@Nonnull
	private final Formatter formatter;
	@Nonnull
	private final Provider<CurrentContext> currentContextProvider;
	@Nonnull
	private final ContentApiResponseFactory contentApiResponseFactory;
	@Nonnull
	private final TagApiResponseFactory tagApiResponseFactory;
	@Nonnull
	private final TagGroupApiResponseFactory tagGroupApiResponseFactory;
	@Nonnull
	private final Logger logger;

	@Inject
	public ResourceLibraryResource(@Nonnull ContentService contentService,
																 @Nonnull TagService tagService,
																 @Nonnull AuthorizationService authorizationService,
																 @Nonnull EnterprisePluginProvider enterprisePluginProvider,
																 @Nonnull Formatter formatter,
																 @Nonnull Provider<CurrentContext> currentContextProvider,
																 @Nonnull ContentApiResponseFactory contentApiResponseFactory,
																 @Nonnull TagApiResponseFactory tagApiResponseFactory,
																 @Nonnull TagGroupApiResponseFactory tagGroupApiResponseFactory) {
		requireNonNull(contentService);
		requireNonNull(tagService);
		requireNonNull(authorizationService);
		requireNonNull(enterprisePluginProvider);
		requireNonNull(formatter);
		requireNonNull(currentContextProvider);
		requireNonNull(contentApiResponseFactory);
		requireNonNull(tagApiResponseFactory);
		requireNonNull(tagGroupApiResponseFactory);

		this.contentService = contentService;
		this.tagService = tagService;
		this.authorizationService = authorizationService;
		this.enterprisePluginProvider = enterprisePluginProvider;
		this.formatter = formatter;
		this.currentContextProvider = currentContextProvider;
		this.contentApiResponseFactory = contentApiResponseFactory;
		this.tagApiResponseFactory = tagApiResponseFactory;
		this.tagGroupApiResponseFactory = tagGroupApiResponseFactory;
		this.logger = LoggerFactory.getLogger(getClass());
	}

	@Nonnull
	@GET("/resource-library")
	@AuthenticationRequired
	public ApiResponse resourceLibrary() {
		CurrentContext currentContext = getCurrentContext();
		Account account = currentContext.getAccount().get();
		EnterprisePlugin enterprisePlugin = getEnterprisePluginProvider().enterprisePluginForInstitutionId(account.getInstitutionId());

		// Delegate content recommendations to enterprise plugin
		List<Content> contents = enterprisePlugin.recommendedContentForAccountId(account.getAccountId());

		// Pick out tags in the content
		Set<String> tagGroupIds = new HashSet<>();
		Map<String, TagApiResponse> tagsByTagId = new HashMap<>();

		for (Content content : contents) {
			for (Tag tag : content.getTags()) {
				if (tagsByTagId.containsKey(tag.getTagId()))
					continue;

				tagGroupIds.add(tag.getTagGroupId());
				tagsByTagId.put(tag.getTagId(), getTagApiResponseFactory().create(tag));
			}
		}

		// Only those tag groups associated with the tags in the content
		List<TagGroupApiResponse> tagGroups = getTagService().findTagGroupsByInstitutionId(currentContext.getInstitutionId()).stream()
				.filter(tagGroup -> tagGroupIds.contains(tagGroup.getTagGroupId()))
				.map(tagGroup -> getTagGroupApiResponseFactory().create(tagGroup))
				.collect(Collectors.toList());

		// Keep track of content IDs so we don't show the same content more than once
		Set<UUID> contentIds = new HashSet<>(contents.size());

		// Group content by tag group
		Map<String, List<ContentApiResponse>> contentsByTagGroupId = new HashMap<>(tagGroups.size());

		for (Content content : contents) {
			if (contentIds.contains(content.getContentId()))
				continue;

			contentIds.add(content.getContentId());

			String tagGroupId = content.getTags().stream()
					.map(tag -> tag.getTagGroupId())
					.findFirst()
					.orElse(null);

			if (tagGroupId != null) {
				List<ContentApiResponse> tagGroupContents = contentsByTagGroupId.get(tagGroupId);

				if (tagGroupContents == null) {
					tagGroupContents = new ArrayList<>();
					contentsByTagGroupId.put(tagGroupId, tagGroupContents);
				}

				tagGroupContents.add(getContentApiResponseFactory().create(content));
			}
		}

		return new ApiResponse(new HashMap<String, Object>() {{
			put("contentsByTagGroupId", contentsByTagGroupId);
			put("tagGroups", tagGroups);
			put("tagsByTagId", tagsByTagId);
		}});
	}

	@Nonnull
	@GET("/resource-library/search")
	@AuthenticationRequired
	public ApiResponse searchResourceLibrary(@Nonnull @QueryParameter Optional<String> searchQuery,
																					 @Nonnull @QueryParameter Optional<Integer> pageNumber,
																					 @Nonnull @QueryParameter Optional<Integer> pageSize) {
		requireNonNull(searchQuery);
		requireNonNull(pageNumber);
		requireNonNull(pageSize);

		CurrentContext currentContext = getCurrentContext();
		Account account = currentContext.getAccount().get();

		FindResult<Content> findResult = getContentService().findResourceLibraryContent(new FindResourceLibraryContentRequest() {
			{
				setInstitutionId(account.getInstitutionId());
				setSearchQuery(searchQuery.orElse(null));
				setPageNumber(pageNumber.orElse(0));
				setPageSize(pageSize.orElse(0));
			}
		});

		List<ContentApiResponse> contents = new ArrayList<>();
		Map<String, TagApiResponse> tagsByTagId = new HashMap<>();

		for (Tag tag : getTagService().findTagsByInstitutionId(account.getInstitutionId()))
			tagsByTagId.put(tag.getTagId(), getTagApiResponseFactory().create(tag));

		for (Content content : findResult.getResults())
			contents.add(getContentApiResponseFactory().create(content));

		Map<String, Object> findResultJson = new HashMap<>();
		findResultJson.put("contents", contents);
		findResultJson.put("totalCount", findResult.getTotalCount());
		findResultJson.put("totalCountDescription", getFormatter().formatNumber(findResult.getTotalCount()));

		return new ApiResponse(new HashMap<String, Object>() {{
			put("findResult", findResultJson);
			put("tagsByTagId", tagsByTagId);
		}});
	}

	@Nonnull
	@GET("/resource-library/tag-groups/{tagGroupId}")
	@AuthenticationRequired
	public ApiResponse resourceLibraryTagGroup(@Nonnull @PathParameter String tagGroupId,
																						 @Nonnull @QueryParameter Optional<String> searchQuery,
																						 @Nonnull @QueryParameter Optional<Integer> pageNumber,
																						 @Nonnull @QueryParameter Optional<Integer> pageSize) {
		requireNonNull(tagGroupId);
		requireNonNull(searchQuery);
		requireNonNull(pageNumber);
		requireNonNull(pageSize);

		CurrentContext currentContext = getCurrentContext();
		Account account = currentContext.getAccount().get();

		// Support both tag group ID and URL name
		TagGroup tagGroup = getTagService().findTagGroupsByInstitutionId(account.getInstitutionId()).stream()
				.filter(potentialTagGroup -> potentialTagGroup.getTagGroupId().equals(tagGroupId)
						|| potentialTagGroup.getUrlName().equals(tagGroupId))
				.findFirst()
				.orElse(null);

		if (tagGroup == null)
			throw new NotFoundException();

		FindResult<Content> findResult = getContentService().findResourceLibraryContent(new FindResourceLibraryContentRequest() {
			{
				setInstitutionId(account.getInstitutionId());
				setSearchQuery(searchQuery.orElse(null));
				setPageNumber(pageNumber.orElse(0));
				setPageSize(pageSize.orElse(0));
				setTagGroupId(tagGroup.getTagGroupId());
			}
		});

		List<ContentApiResponse> contents = new ArrayList<>();
		Map<String, TagApiResponse> tagsByTagId = new HashMap<>();

		for (Tag tag : getTagService().findTagsByInstitutionId(account.getInstitutionId()))
			tagsByTagId.put(tag.getTagId(), getTagApiResponseFactory().create(tag));

		for (Content content : findResult.getResults())
			contents.add(getContentApiResponseFactory().create(content));

		Map<String, Object> findResultJson = new HashMap<>();
		findResultJson.put("contents", contents);
		findResultJson.put("totalCount", findResult.getTotalCount());
		findResultJson.put("totalCountDescription", getFormatter().formatNumber(findResult.getTotalCount()));

		return new ApiResponse(new HashMap<String, Object>() {{
			put("findResult", findResultJson);
			put("tagsByTagId", tagsByTagId);
			put("tagGroup", getTagGroupApiResponseFactory().create(tagGroup));
		}});
	}

	@Nonnull
	@GET("/resource-library/tags/{tagId}")
	@AuthenticationRequired
	public ApiResponse resourceLibraryTag(@Nonnull @PathParameter String tagId,
																				@Nonnull @QueryParameter Optional<String> searchQuery,
																				@Nonnull @QueryParameter Optional<Integer> pageNumber,
																				@Nonnull @QueryParameter Optional<Integer> pageSize) {
		requireNonNull(tagId);
		requireNonNull(searchQuery);
		requireNonNull(pageNumber);
		requireNonNull(pageSize);

		CurrentContext currentContext = getCurrentContext();
		Account account = currentContext.getAccount().get();

		// Support both tag ID and URL name
		Tag tag = getTagService().findTagsByInstitutionId(account.getInstitutionId()).stream()
				.filter(potentialTag -> potentialTag.getTagId().equals(tagId)
						|| potentialTag.getUrlName().equals(tagId))
				.findFirst()
				.orElse(null);

		if (tag == null)
			throw new NotFoundException();

		TagGroup tagGroup = getTagService().findTagGroupsByInstitutionId(account.getInstitutionId()).stream()
				.filter(potentialTagGroup -> potentialTagGroup.getTagGroupId().equals(tag.getTagGroupId()))
				.findFirst()
				.get();

		FindResult<Content> findResult = getContentService().findResourceLibraryContent(new FindResourceLibraryContentRequest() {
			{
				setInstitutionId(account.getInstitutionId());
				setSearchQuery(searchQuery.orElse(null));
				setPageNumber(pageNumber.orElse(0));
				setPageSize(pageSize.orElse(0));
				setTagId(tag.getTagId());
			}
		});

		List<ContentApiResponse> contents = new ArrayList<>();
		Map<String, TagApiResponse> tagsByTagId = new HashMap<>();

		for (Tag currentTag : getTagService().findTagsByInstitutionId(account.getInstitutionId()))
			tagsByTagId.put(currentTag.getTagId(), getTagApiResponseFactory().create(currentTag));

		for (Content content : findResult.getResults())
			contents.add(getContentApiResponseFactory().create(content));

		Map<String, Object> findResultJson = new HashMap<>();
		findResultJson.put("contents", contents);
		findResultJson.put("totalCount", findResult.getTotalCount());
		findResultJson.put("totalCountDescription", getFormatter().formatNumber(findResult.getTotalCount()));

		return new ApiResponse(new HashMap<String, Object>() {{
			put("findResult", findResultJson);
			put("tagsByTagId", tagsByTagId);
			put("tagGroup", getTagGroupApiResponseFactory().create(tagGroup));
			put("tag", getTagApiResponseFactory().create(tag));
		}});
	}

	@Nonnull
	protected ContentService getContentService() {
		return this.contentService;
	}

	@Nonnull
	protected TagService getTagService() {
		return this.tagService;
	}

	@Nonnull
	protected AuthorizationService getAuthorizationService() {
		return this.authorizationService;
	}

	@Nonnull
	protected EnterprisePluginProvider getEnterprisePluginProvider() {
		return this.enterprisePluginProvider;
	}

	@Nonnull
	protected Formatter getFormatter() {
		return this.formatter;
	}

	@Nonnull
	protected ContentApiResponseFactory getContentApiResponseFactory() {
		return this.contentApiResponseFactory;
	}

	@Nonnull
	protected TagApiResponseFactory getTagApiResponseFactory() {
		return this.tagApiResponseFactory;
	}

	@Nonnull
	protected TagGroupApiResponseFactory getTagGroupApiResponseFactory() {
		return this.tagGroupApiResponseFactory;
	}

	@Nonnull
	protected CurrentContext getCurrentContext() {
		return this.currentContextProvider.get();
	}

	@Nonnull
	protected Logger getLogger() {
		return this.logger;
	}
}
