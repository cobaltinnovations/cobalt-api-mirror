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
import com.cobaltplatform.api.model.api.response.AdminContentApiResponse.AdminContentApiResponseFactory;
import com.cobaltplatform.api.model.api.response.AdminContentApiResponse.AdminContentDisplayType;
import com.cobaltplatform.api.model.api.response.ContentApiResponse.ContentApiResponseFactory;
import com.cobaltplatform.api.model.api.response.ContentImageApiResponse.ContentImageApiResponseFactory;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.Content;
import com.cobaltplatform.api.model.db.ContentStatus.ContentStatusId;
import com.cobaltplatform.api.model.db.ContentType.ContentTypeId;
import com.cobaltplatform.api.model.db.ContentVisibilityType.ContentVisibilityTypeId;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.service.AdminContent;
import com.cobaltplatform.api.service.ContentService;
import com.cobaltplatform.api.util.JsonMapper;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class ContentImageApiResponseTests {
	@Test
	public void contentImageResponseIncludesDirectThumbnailForCropImage() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			ContentService contentService = app.getInjector().getInstance(ContentService.class);
			ContentImageApiResponseFactory contentImageApiResponseFactory = app.getInjector().getInstance(ContentImageApiResponseFactory.class);
			ContentApiResponseFactory contentApiResponseFactory = app.getInjector().getInstance(ContentApiResponseFactory.class);
			AdminContentApiResponseFactory adminContentApiResponseFactory = app.getInjector().getInstance(AdminContentApiResponseFactory.class);
			JsonMapper jsonMapper = app.getInjector().getInstance(JsonMapper.class);
			Account account = findExistingAccount(database);

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID cropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1600, 900);
			UUID thumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, cropImageId, 320, 180);
			UUID cropImageFileUploadId = findFileUploadIdByImageId(database, cropImageId);

			Content content = hydrateContentImage(contentService, cropImageId);
			AdminContent adminContent = hydrateAdminContentImage(contentService, account, cropImageId, cropImageFileUploadId);
			ContentImageApiResponse imageResponse = contentImageApiResponseFactory.create(content);
			ContentApiResponse contentResponse = contentApiResponseFactory.create(content);
			AdminContentApiResponse adminContentResponse = adminContentApiResponseFactory.create(account, adminContent, AdminContentDisplayType.DETAIL, List.of());
			Map<String, Object> contentResponseAsMap = jsonMapper.toMap(contentResponse);
			Map<String, Object> adminContentResponseAsMap = jsonMapper.toMap(adminContentResponse);
			Map<String, Object> imageResponseAsMap = jsonMapper.toMap(imageResponse);

			Assert.assertEquals("Selected crop image should be hydrated", cropImageId, content.getImage().getImageId());
			Assert.assertEquals("Direct thumbnail should be hydrated", thumbnailImageId, content.getImageThumbnail().getImageId());
			Assert.assertEquals("Response should expose the selected crop image", cropImageId, imageResponse.getImageId());
			Assert.assertNotNull("Response should include a thumbnail object", imageResponse.getThumbnail());
			Assert.assertEquals("Response thumbnail should be the direct thumbnail child", thumbnailImageId, imageResponse.getThumbnail().getImageId());
			Assert.assertTrue("Serialized image response should include a thumbnail field", imageResponseAsMap.containsKey("thumbnail"));
			Assert.assertTrue("Serialized thumbnail should be an object", imageResponseAsMap.get("thumbnail") instanceof Map);
			Assert.assertEquals("Content response should expose the selected image ID", cropImageId, contentResponse.getImageId());
			Assert.assertNotNull("Content response should expose an image object", contentResponse.getImage());
			Assert.assertEquals("Deprecated imageUrl should prefer the new image URL", content.getImage().getUrl(), contentResponse.getImageUrl());
			Assert.assertTrue("Serialized content response should include imageId", contentResponseAsMap.containsKey("imageId"));
			Assert.assertTrue("Serialized content response should include image", contentResponseAsMap.containsKey("image"));
			Assert.assertTrue("Serialized content response should keep deprecated imageUrl", contentResponseAsMap.containsKey("imageUrl"));
			Assert.assertEquals("Admin content response should expose the selected image ID", cropImageId, adminContentResponse.getImageId());
			Assert.assertNotNull("Admin content response should expose an image object", adminContentResponse.getImage());
			Assert.assertEquals("Admin deprecated imageUrl should prefer the new image URL", adminContent.getImage().getUrl(), adminContentResponse.getImageUrl());
			Assert.assertEquals("Admin deprecated imageFileUploadId should be preserved", cropImageFileUploadId, adminContentResponse.getImageFileUploadId());
			Assert.assertTrue("Serialized admin response should include imageId", adminContentResponseAsMap.containsKey("imageId"));
			Assert.assertTrue("Serialized admin response should include image", adminContentResponseAsMap.containsKey("image"));
			Assert.assertTrue("Serialized admin response should keep deprecated imageUrl", adminContentResponseAsMap.containsKey("imageUrl"));
			Assert.assertTrue("Serialized admin response should keep deprecated imageFileUploadId", adminContentResponseAsMap.containsKey("imageFileUploadId"));
		});
	}

	@Test
	public void contentImageResponseOmitsThumbnailForRawThumbnailAndCropWithoutDirectThumbnail() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			ContentService contentService = app.getInjector().getInstance(ContentService.class);
			ContentImageApiResponseFactory contentImageApiResponseFactory = app.getInjector().getInstance(ContentImageApiResponseFactory.class);
			JsonMapper jsonMapper = app.getInjector().getInstance(JsonMapper.class);
			Account account = findExistingAccount(database);

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID cropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1600, 900);
			UUID thumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, cropImageId, 320, 180);
			UUID rawWithoutThumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1200, 900);
			UUID cropWithoutThumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_4X3, rawWithoutThumbnailImageId, 1200, 900);

			assertNoThumbnail(contentService, contentImageApiResponseFactory, jsonMapper, rawImageId);
			assertNoThumbnail(contentService, contentImageApiResponseFactory, jsonMapper, thumbnailImageId);
			assertNoThumbnail(contentService, contentImageApiResponseFactory, jsonMapper, cropWithoutThumbnailImageId);
		});
	}

	protected void assertNoThumbnail(@Nonnull ContentService contentService,
																	 @Nonnull ContentImageApiResponseFactory contentImageApiResponseFactory,
																	 @Nonnull JsonMapper jsonMapper,
																	 @Nonnull UUID imageId) {
		requireNonNull(contentService);
		requireNonNull(contentImageApiResponseFactory);
		requireNonNull(jsonMapper);
		requireNonNull(imageId);

		Content content = hydrateContentImage(contentService, imageId);
		ContentImageApiResponse response = contentImageApiResponseFactory.create(content);
		Map<String, Object> responseAsMap = jsonMapper.toMap(response);

		Assert.assertEquals("Selected image should be hydrated", imageId, content.getImage().getImageId());
		Assert.assertNull("No direct thumbnail should be hydrated", content.getImageThumbnail());
		Assert.assertNull("Response should not include a thumbnail object", response.getThumbnail());
		Assert.assertFalse("Serialized response should omit null thumbnail fields", responseAsMap.containsKey("thumbnail"));
	}

	@Nonnull
	protected Content hydrateContentImage(@Nonnull ContentService contentService,
																				@Nonnull UUID imageId) {
		requireNonNull(contentService);
		requireNonNull(imageId);

		Content content = new Content();
		content.setContentId(UUID.randomUUID());
		content.setContentTypeId(ContentTypeId.ARTICLE);
		content.setContentVisibilityTypeId(ContentVisibilityTypeId.PUBLIC);
		content.setTitle("Content image response test");
		content.setUrl("https://example.com/content");
		content.setNeverEmbed(false);
		content.setImageId(imageId);
		content.setImageUrl("https://example.com/legacy-content-image.jpg");
		content.setDescription("Content image response test description.");
		content.setAuthor("Test Author");
		content.setCreated(Instant.now());
		content.setLastUpdated(Instant.now());
		content.setOwnerInstitutionId(InstitutionId.COBALT);
		content.setDurationInMinutes(5);
		contentService.applyImageToContent(content);

		return content;
	}

	@Nonnull
	protected AdminContent hydrateAdminContentImage(@Nonnull ContentService contentService,
																								 @Nonnull Account account,
																								 @Nonnull UUID imageId,
																								 @Nonnull UUID imageFileUploadId) {
		requireNonNull(contentService);
		requireNonNull(account);
		requireNonNull(imageId);
		requireNonNull(imageFileUploadId);

		AdminContent adminContent = new AdminContent();
		adminContent.setContentId(UUID.randomUUID());
		adminContent.setContentTypeId(ContentTypeId.ARTICLE);
		adminContent.setContentVisibilityTypeId(ContentVisibilityTypeId.PUBLIC);
		adminContent.setTitle("Admin content image response test");
		adminContent.setAuthor("Test Author");
		adminContent.setDescription("Admin content image response test description.");
		adminContent.setImageId(imageId);
		adminContent.setImageFileUploadId(imageFileUploadId);
		adminContent.setImageUrl("https://example.com/legacy-admin-content-image.jpg");
		adminContent.setDurationInMinutes(5);
		adminContent.setOwnerInstitution("Cobalt");
		adminContent.setOwnerInstitutionId(account.getInstitutionId());
		adminContent.setViews(0);
		adminContent.setPublishStartDate(LocalDate.now());
		adminContent.setDateCreated(LocalDate.now());
		adminContent.setPublishRecurring(false);
		adminContent.setSharedFlag(false);
		adminContent.setContentStatusId(ContentStatusId.DRAFT);
		adminContent.setContentStatusDescription("Draft");
		contentService.applyImageToAdminContent(adminContent);

		return adminContent;
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
		String storageKey = format("content-image-response-test/%s/%s", fileUploadTypeId.name().toLowerCase(), filename);

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

	@Nonnull
	protected UUID findFileUploadIdByImageId(@Nonnull Database database,
																					 @Nonnull UUID imageId) {
		requireNonNull(database);
		requireNonNull(imageId);

		return database.queryForObject("""
				SELECT file_upload_id
				FROM image
				WHERE image_id=?
				""", UUID.class, imageId).get();
	}
}
