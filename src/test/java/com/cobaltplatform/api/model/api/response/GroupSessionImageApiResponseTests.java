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

import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.model.api.response.GroupSessionImageApiResponse.GroupSessionImageApiResponseFactory;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.GroupSession;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.service.GroupSessionService;
import com.cobaltplatform.api.util.JsonMapper;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class GroupSessionImageApiResponseTests {
	@Test
	public void groupSessionImageResponseIncludesDirectThumbnailForCropImage() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			GroupSessionImageApiResponseFactory groupSessionImageApiResponseFactory = app.getInjector().getInstance(GroupSessionImageApiResponseFactory.class);
			JsonMapper jsonMapper = app.getInjector().getInstance(JsonMapper.class);
			Account account = findExistingAccount(database);

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID cropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1600, 900);
			UUID thumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, cropImageId, 320, 180);

			GroupSession groupSession = hydrateGroupSessionImage(groupSessionService, cropImageId);
			GroupSessionImageApiResponse response = groupSessionImageApiResponseFactory.create(groupSession);
			Map<String, Object> responseAsMap = jsonMapper.toMap(response);

			Assert.assertEquals("Selected crop image should be hydrated", cropImageId, groupSession.getImage().getImageId());
			Assert.assertEquals("Direct thumbnail should be hydrated", thumbnailImageId, groupSession.getImageThumbnail().getImageId());
			Assert.assertEquals("Response should expose the selected crop image", cropImageId, response.getImageId());
			Assert.assertNotNull("Response should include a thumbnail object", response.getThumbnail());
			Assert.assertEquals("Response thumbnail should be the direct thumbnail child", thumbnailImageId, response.getThumbnail().getImageId());
			Assert.assertTrue("Serialized response should include a thumbnail field", responseAsMap.containsKey("thumbnail"));
			Assert.assertTrue("Serialized thumbnail should be an object", responseAsMap.get("thumbnail") instanceof Map);
		});
	}

	@Test
	public void groupSessionImageResponseOmitsThumbnailForRawThumbnailAndCropWithoutDirectThumbnail() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			GroupSessionImageApiResponseFactory groupSessionImageApiResponseFactory = app.getInjector().getInstance(GroupSessionImageApiResponseFactory.class);
			JsonMapper jsonMapper = app.getInjector().getInstance(JsonMapper.class);
			Account account = findExistingAccount(database);

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID cropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1600, 900);
			UUID thumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, cropImageId, 320, 180);
			UUID rawWithoutThumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1200, 900);
			UUID cropWithoutThumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_4X3, rawWithoutThumbnailImageId, 1200, 900);

			assertNoThumbnail(groupSessionService, groupSessionImageApiResponseFactory, jsonMapper, rawImageId);
			assertNoThumbnail(groupSessionService, groupSessionImageApiResponseFactory, jsonMapper, thumbnailImageId);
			assertNoThumbnail(groupSessionService, groupSessionImageApiResponseFactory, jsonMapper, cropWithoutThumbnailImageId);
		});
	}

	protected void assertNoThumbnail(@Nonnull GroupSessionService groupSessionService,
																	 @Nonnull GroupSessionImageApiResponseFactory groupSessionImageApiResponseFactory,
																	 @Nonnull JsonMapper jsonMapper,
																	 @Nonnull UUID imageId) {
		requireNonNull(groupSessionService);
		requireNonNull(groupSessionImageApiResponseFactory);
		requireNonNull(jsonMapper);
		requireNonNull(imageId);

		GroupSession groupSession = hydrateGroupSessionImage(groupSessionService, imageId);
		GroupSessionImageApiResponse response = groupSessionImageApiResponseFactory.create(groupSession);
		Map<String, Object> responseAsMap = jsonMapper.toMap(response);

		Assert.assertEquals("Selected image should be hydrated", imageId, groupSession.getImage().getImageId());
		Assert.assertNull("No direct thumbnail should be hydrated", groupSession.getImageThumbnail());
		Assert.assertNull("Response should not include a thumbnail object", response.getThumbnail());
		Assert.assertFalse("Serialized response should omit null thumbnail fields", responseAsMap.containsKey("thumbnail"));
	}

	@Nonnull
	protected GroupSession hydrateGroupSessionImage(@Nonnull GroupSessionService groupSessionService,
																									@Nonnull UUID imageId) {
		requireNonNull(groupSessionService);
		requireNonNull(imageId);

		GroupSession groupSession = new GroupSession();
		groupSession.setImageId(imageId);
		groupSessionService.applyImageToGroupSession(groupSession);
		return groupSession;
	}

	@Nonnull
	protected Account findExistingAccount(@Nonnull Database database) {
		requireNonNull(database);

		return database.queryForObject("""
				SELECT *
				FROM v_account
				WHERE institution_id=?
				LIMIT 1
				""", Account.class, InstitutionId.COBALT).get();
	}

	@Nonnull
	protected UUID createUploadedImage(@Nonnull Database database,
																		 @Nonnull Account account,
																		 @Nonnull FileUploadTypeId fileUploadTypeId,
																		 @Nullable UUID sourceImageId,
																		 @Nonnull Integer width,
																		 @Nonnull Integer height) {
		requireNonNull(database);
		requireNonNull(account);
		requireNonNull(fileUploadTypeId);
		requireNonNull(width);
		requireNonNull(height);

		UUID fileUploadId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		String filename = format("%s.jpg", imageId);
		String storageKey = format("group-session-image-response-test/%s/%s", fileUploadTypeId.name().toLowerCase(), filename);

		database.execute("""
				INSERT INTO file_upload (
				  file_upload_id,
				  file_upload_type_id,
				  file_upload_status_id,
				  account_id,
				  institution_id,
				  url,
				  storage_bucket,
				  storage_key,
				  storage_region,
				  filename,
				  content_type,
				  filesize
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
				""",
				fileUploadId,
				fileUploadTypeId,
				FileUploadStatusId.UPLOADED,
				account.getAccountId(),
				account.getInstitutionId(),
				format("https://example.com/%s", storageKey),
				"test-bucket",
				storageKey,
				"us-east-1",
				filename,
				"image/jpeg",
				100L);

		database.execute("""
				INSERT INTO image (
				  image_id,
				  file_upload_id,
				  source_image_id,
				  created_by_account_id,
				  width,
				  height
				) VALUES (?,?,?,?,?,?)
				""", imageId, fileUploadId, sourceImageId, account.getAccountId(), width, height);

		return imageId;
	}
}
