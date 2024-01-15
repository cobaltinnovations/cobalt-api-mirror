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

package com.cobaltplatform.api.service;

import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.Tag;
import com.cobaltplatform.api.model.db.TagContent;
import com.cobaltplatform.api.model.db.TagGroup;
import com.cobaltplatform.api.model.db.TagGroupSession;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.lokalized.Strings;
import com.pyranid.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class TagService {
	@Nonnull
	private final DatabaseProvider databaseProvider;
	@Nonnull
	private final Strings strings;
	@Nonnull
	private final LoadingCache<InstitutionId, List<Tag>> tagsByInstitutionIdCache;
	@Nonnull
	private final LoadingCache<InstitutionId, List<TagGroup>> tagGroupsByInstitutionIdCache;
	@Nonnull
	private final Logger logger;

	@Inject
	public TagService(@Nonnull DatabaseProvider databaseProvider,
										@Nonnull Strings strings) {
		requireNonNull(databaseProvider);
		requireNonNull(strings);

		this.databaseProvider = databaseProvider;
		this.strings = strings;
		this.logger = LoggerFactory.getLogger(getClass());

		this.tagsByInstitutionIdCache = Caffeine.newBuilder()
				.maximumSize(100)
				.expireAfterWrite(Duration.ofMinutes(5))
				.refreshAfterWrite(Duration.ofMinutes(1))
				.build(institutionId -> findUncachedTagsByInstitutionId(institutionId));

		this.tagGroupsByInstitutionIdCache = Caffeine.newBuilder()
				.maximumSize(100)
				.expireAfterWrite(Duration.ofMinutes(5))
				.refreshAfterWrite(Duration.ofMinutes(1))
				.build(institutionId -> findUncachedTagGroupsByInstitutionId(institutionId));
	}

	@Nonnull
	public Optional<Tag> findTagById(@Nullable String tagId) {
		if (tagId == null)
			return Optional.empty();

		return getDatabase().queryForObject("SELECT * FROM tag WHERE tag_id=?", Tag.class, tagId);
	}

	@Nonnull
	public List<Tag> findTags() {
		return getDatabase().queryForList("SELECT * FROM tag ORDER BY name", Tag.class);
	}

	@Nonnull
	public List<Tag> findTagsByTagGroupId(@Nullable String tagGroupId) {
		if (tagGroupId == null)
			return Collections.emptyList();

		return getDatabase().queryForList("""
				SELECT *
				FROM tag
				WHERE tag_group_id=?
				ORDER BY tag.name
				""", Tag.class, tagGroupId);
	}

	@Nonnull
	public List<Tag> findTagsByInstitutionId(@Nullable InstitutionId institutionId) {
		if (institutionId == null)
			return Collections.emptyList();

		return getTagsByInstitutionIdCache().get(institutionId);
	}

	@Nonnull
	protected List<Tag> findUncachedTagsByInstitutionId(@Nullable InstitutionId institutionId) {
		if (institutionId == null)
			return Collections.emptyList();

		// Currently we don't have institution-specific tags.
		// But this method accepts an institution ID in case we do in the future...
		return getDatabase().queryForList("SELECT * FROM tag ORDER BY name", Tag.class);
	}

	@Nonnull
	public List<TagGroup> findTagGroupsByInstitutionId(@Nullable InstitutionId institutionId) {
		if (institutionId == null)
			return Collections.emptyList();

		return getTagGroupsByInstitutionIdCache().get(institutionId);
	}

	@Nonnull
	protected List<TagGroup> findUncachedTagGroupsByInstitutionId(@Nullable InstitutionId institutionId) {
		if (institutionId == null)
			return Collections.emptyList();

		// Currently we don't have institution-specific tag groups.
		// But this method accepts an institution ID in case we do in the future...
		return getDatabase().queryForList("SELECT * FROM tag_group ORDER BY display_order", TagGroup.class);
	}

	@Nonnull
	public Map<String, List<Tag>> findTagsByTagGroupIdForInstitutionId(@Nullable InstitutionId institutionId) {
		if (institutionId == null)
			return Map.of();

		List<Tag> tags = findTagsByInstitutionId(institutionId);
		Map<String, List<Tag>> tagsByTagGroupId = new LinkedHashMap<>();

		for (Tag tag : tags) {
			List<Tag> currentTags = tagsByTagGroupId.get(tag.getTagGroupId());

			if (currentTags == null) {
				currentTags = new ArrayList<>();
				tagsByTagGroupId.put(tag.getTagGroupId(), currentTags);
			}

			currentTags.add(tag);
		}

		return tagsByTagGroupId;
	}

	@Nonnull
	public List<Tag> findTagsByContentIdAndInstitutionId(@Nullable UUID contentId,
																											 @Nullable InstitutionId institutionId) {
		if (contentId == null || institutionId == null)
			return Collections.emptyList();

		return getDatabase().queryForList("""
				    SELECT t.*
				    FROM tag t, tag_content tc
				    WHERE tc.tag_id=t.tag_id
				    AND tc.content_id=?
				    AND tc.institution_id=?
				    ORDER BY t.name
				""", Tag.class, contentId, institutionId);
	}

	@Nonnull
	public List<TagContent> findTagContentsByInstitutionId(@Nullable InstitutionId institutionId) {
		if (institutionId == null)
			return Collections.emptyList();

		return getDatabase().queryForList("""
				SELECT *
				FROM tag_content
				WHERE institution_id=?
				""", TagContent.class, institutionId);
	}

	@Nonnull
	public List<Tag> findTagsByGroupSessionIdAndInstitutionId(@Nullable UUID groupSessionId,
																														@Nullable InstitutionId institutionId) {
		if (groupSessionId == null || institutionId == null)
			return Collections.emptyList();

		return getDatabase().queryForList("""
				    SELECT t.*
				    FROM tag t, tag_group_session tgs
				    WHERE tgs.tag_id=t.tag_id
				    AND tgs.group_session_id=?
				    AND tgs.institution_id=?
				    ORDER BY t.name
				""", Tag.class, groupSessionId, institutionId);
	}

	@Nonnull
	public List<TagGroupSession> findTagGroupSessionsByInstitutionId(@Nullable InstitutionId institutionId) {
		if (institutionId == null)
			return Collections.emptyList();

		return getDatabase().queryForList("""
				SELECT *
				FROM tag_group_session
				WHERE institution_id=?
				""", TagGroupSession.class, institutionId);
	}

	@Nonnull
	public List<Tag> findRecommendedTagsByScreeningSessionId(@Nullable UUID screeningSessionId) {
		if (screeningSessionId == null)
			return List.of();

		return getDatabase().queryForList("""
				    SELECT t.*
				    FROM tag t, tag_screening_session tss
				    WHERE tss.tag_id=t.tag_id
				    AND tss.screening_session_id=?
				""", Tag.class, screeningSessionId);
	}

	@Nonnull
	protected Database getDatabase() {
		return this.databaseProvider.get();
	}

	@Nonnull
	protected Strings getStrings() {
		return strings;
	}

	@Nonnull
	protected LoadingCache<InstitutionId, List<Tag>> getTagsByInstitutionIdCache() {
		return this.tagsByInstitutionIdCache;
	}

	@Nonnull
	protected LoadingCache<InstitutionId, List<TagGroup>> getTagGroupsByInstitutionIdCache() {
		return this.tagGroupsByInstitutionIdCache;
	}

	@Nonnull
	protected Logger getLogger() {
		return logger;
	}
}
