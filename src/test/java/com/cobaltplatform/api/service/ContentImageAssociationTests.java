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

import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.model.api.request.CreateContentRequest;
import com.cobaltplatform.api.model.api.request.UpdateContentRequest;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.ContentAudienceType.ContentAudienceTypeId;
import com.cobaltplatform.api.model.db.ContentType.ContentTypeId;
import com.cobaltplatform.api.model.db.ContentVisibilityType.ContentVisibilityTypeId;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.service.AdminContent;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class ContentImageAssociationTests {
	@Test
	public void createContentIgnoresLegacyImageFileUploadIdWhenValidImageIdIsProvided() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeContentImageIdColumnExists(database);
			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			Account account = findExistingAdministratorAccount(database);

			UUID selectedImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID staleImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1200, 900);
			UUID selectedFileUploadId = findFileUploadIdByImageId(database, selectedImageId);
			UUID staleFileUploadId = findFileUploadIdByImageId(database, staleImageId);

			CreateContentRequest request = createContentRequest("create-mismatched-image-upload");
			request.setImageId(selectedImageId);
			request.setImageFileUploadId(staleFileUploadId);

			AdminContent adminContent = adminContentService.createContent(account, request);

			Assert.assertEquals("Valid image ID should be stored", selectedImageId, adminContent.getImageId());
			Assert.assertEquals("Legacy file upload ID should be derived from the valid image", selectedFileUploadId, adminContent.getImageFileUploadId());
		});
	}

	@Test
	public void updateContentIgnoresLegacyImageFileUploadIdWhenValidImageIdIsProvided() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeContentImageIdColumnExists(database);
			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			Account account = findExistingAdministratorAccount(database);

			UUID originalImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID newImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1200, 900);
			UUID originalFileUploadId = findFileUploadIdByImageId(database, originalImageId);
			UUID newFileUploadId = findFileUploadIdByImageId(database, newImageId);

			CreateContentRequest createRequest = createContentRequest("update-mismatched-image-upload");
			createRequest.setImageId(originalImageId);
			AdminContent createdContent = adminContentService.createContent(account, createRequest);

			UpdateContentRequest updateRequest = updateContentRequest(createdContent.getContentId(), "update-mismatched-image-upload");
			updateRequest.setImageId(newImageId);
			updateRequest.setImageFileUploadId(originalFileUploadId);

			AdminContent updatedContent = adminContentService.updateContent(account, updateRequest);

			Assert.assertEquals("Valid updated image ID should be stored", newImageId, updatedContent.getImageId());
			Assert.assertEquals("Legacy file upload ID should be derived from the updated image", newFileUploadId, updatedContent.getImageFileUploadId());
		});
	}

	protected void assumeContentImageIdColumnExists(@Nonnull Database database) {
		requireNonNull(database);

		Boolean contentImageIdColumnExists = database.queryForObject("""
				SELECT EXISTS (
				  SELECT 1
				  FROM information_schema.columns
				  WHERE table_schema='public'
				  AND table_name='content'
				  AND column_name='image_id'
				)
				""", Boolean.class).get();

		Assume.assumeTrue("Branch schema must include content.image_id", contentImageIdColumnExists);
	}

	@Nonnull
	protected CreateContentRequest createContentRequest(@Nonnull String titleSuffix) {
		requireNonNull(titleSuffix);

		return new CreateContentRequest() {{
			setContentTypeId(ContentTypeId.ARTICLE);
			setTitle(format("Image association test %s %s", titleSuffix, UUID.randomUUID()));
			setAuthor("Test Author");
			setDescription("Image association test description.");
			setPublishStartDate(LocalDate.now());
			setPublishRecurring(false);
			setSharedFlag(false);
			setContentVisibilityTypeId(ContentVisibilityTypeId.PUBLIC);
			setContentAudienceTypeIds(Set.of(ContentAudienceTypeId.MYSELF));
		}};
	}

	@Nonnull
	protected UpdateContentRequest updateContentRequest(@Nonnull UUID contentId,
																										 @Nonnull String titleSuffix) {
		requireNonNull(contentId);
		requireNonNull(titleSuffix);

		return new UpdateContentRequest() {{
			setContentId(contentId);
			setContentTypeId(ContentTypeId.ARTICLE);
			setTitle(format("Updated image association test %s %s", titleSuffix, UUID.randomUUID()));
			setAuthor("Updated Test Author");
			setDescription("Updated image association test description.");
			setPublishStartDate(LocalDate.now());
			setPublishRecurring(false);
			setSharedFlag(false);
			setContentVisibilityTypeId(ContentVisibilityTypeId.PUBLIC);
			setContentAudienceTypeIds(Set.of(ContentAudienceTypeId.MYSELF));
		}};
	}

	@Nonnull
	protected Account findExistingAdministratorAccount(@Nonnull Database database) {
		requireNonNull(database);

		return database.queryForObject("""
				SELECT *
				FROM v_account
				WHERE institution_id=?
				AND role_id='ADMINISTRATOR'
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
		String storageKey = format("content-image-association-test/%s/%s", fileUploadTypeId.name().toLowerCase(), filename);

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
