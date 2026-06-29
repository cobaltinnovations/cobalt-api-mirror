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
import com.cobaltplatform.api.model.api.request.CreateAccountRequest;
import com.cobaltplatform.api.model.api.request.CreateMediaImagePresignedUploadRequest;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.AccountSource.AccountSourceId;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.Image;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.service.MediaImageDetails;
import com.cobaltplatform.api.model.service.MediaImageGalleryItem;
import com.cobaltplatform.api.util.ValidationException;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class MediaServiceTests {
	@Test
	public void addingMissingAspectRatioCreatesActiveCurrentPair() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID cropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_1X1, rawImageId, 800, 800);
			UUID thumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_1X1, cropImageId, 200, 200);

			mediaService.confirmMediaImageUploaded(account, thumbnailImageId);

			Assert.assertTrue("Crop should be active", mediaService.findImageById(cropImageId).get().getActive());
			Assert.assertTrue("Thumbnail should be active", mediaService.findImageById(thumbnailImageId).get().getActive());

			MediaImageDetails mediaImageDetails = mediaService.findMediaImageDetails(account, rawImageId).get();
			Set<UUID> variantImageIds = imageIds(mediaImageDetails);

			Assert.assertTrue("Details should include the new crop", variantImageIds.contains(cropImageId));
			Assert.assertTrue("Details should include the new thumbnail", variantImageIds.contains(thumbnailImageId));

			MediaImageGalleryItem galleryItem = findGalleryItem(mediaService, account, rawImageId).get();

			Assert.assertEquals("Gallery thumbnail should be the new thumbnail", thumbnailImageId, galleryItem.getThumbnailImage().getImageId());
		});
	}

	@Test
	public void replacingExistingAspectRatioInactivatesPreviousCropAndThumbnail() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID previousCropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1600, 900);
			UUID previousThumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, previousCropImageId, 320, 180);
			UUID newCropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1280, 720);
			UUID newThumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, newCropImageId, 320, 180);

			mediaService.confirmMediaImageUploaded(account, newThumbnailImageId);

			Assert.assertFalse("Previous crop should be inactive", mediaService.findImageById(previousCropImageId).get().getActive());
			Assert.assertFalse("Previous thumbnail should be inactive", mediaService.findImageById(previousThumbnailImageId).get().getActive());
			Assert.assertTrue("New crop should be active", mediaService.findImageById(newCropImageId).get().getActive());
			Assert.assertTrue("New thumbnail should be active", mediaService.findImageById(newThumbnailImageId).get().getActive());

			MediaImageDetails mediaImageDetails = mediaService.findMediaImageDetails(account, rawImageId).get();
			Set<UUID> variantImageIds = imageIds(mediaImageDetails);

			Assert.assertFalse("Details should exclude the previous crop", variantImageIds.contains(previousCropImageId));
			Assert.assertFalse("Details should exclude the previous thumbnail", variantImageIds.contains(previousThumbnailImageId));
			Assert.assertTrue("Details should include the new crop", variantImageIds.contains(newCropImageId));
			Assert.assertTrue("Details should include the new thumbnail", variantImageIds.contains(newThumbnailImageId));
			Assert.assertFalse("Direct inactive crop lookup should not return details", mediaService.findMediaImageDetails(account, previousCropImageId).isPresent());
			Assert.assertFalse("Direct inactive thumbnail lookup should not return details", mediaService.findMediaImageDetails(account, previousThumbnailImageId).isPresent());

			MediaImageGalleryItem galleryItem = findGalleryItem(mediaService, account, rawImageId).get();
			Set<UUID> galleryVariantImageIds = galleryItem.getVariants().stream()
					.map(mediaImageVariant -> mediaImageVariant.getImageId())
					.collect(Collectors.toSet());

			Assert.assertEquals("Gallery thumbnail should be the new thumbnail", newThumbnailImageId, galleryItem.getThumbnailImage().getImageId());
			Assert.assertFalse("Gallery variants should exclude the previous crop", galleryVariantImageIds.contains(previousCropImageId));
			Assert.assertFalse("Gallery variants should exclude the previous thumbnail", galleryVariantImageIds.contains(previousThumbnailImageId));
			Assert.assertTrue("Gallery variants should include the new crop", galleryVariantImageIds.contains(newCropImageId));
			Assert.assertTrue("Gallery variants should include the new thumbnail", galleryVariantImageIds.contains(newThumbnailImageId));

			mediaService.confirmMediaImageUploaded(account, newThumbnailImageId);

			Assert.assertFalse("Previous crop should remain inactive", mediaService.findImageById(previousCropImageId).get().getActive());
			Assert.assertFalse("Previous thumbnail should remain inactive", mediaService.findImageById(previousThumbnailImageId).get().getActive());
			Assert.assertTrue("New crop should remain active", mediaService.findImageById(newCropImageId).get().getActive());
			Assert.assertTrue("New thumbnail should remain active", mediaService.findImageById(newThumbnailImageId).get().getActive());

			Assert.assertThrows(ValidationException.class, () -> mediaService.confirmMediaImageUploaded(account, previousThumbnailImageId));
			Assert.assertThrows(ValidationException.class, () -> mediaService.createMediaImagePresignedUpload(account, new CreateMediaImagePresignedUploadRequest() {{
				setFileUploadTypeId(FileUploadTypeId.IMAGE_THUMBNAIL_16X9);
				setSourceImageId(previousCropImageId);
				setFilename("stale-thumbnail.jpg");
				setContentType("image/jpeg");
				setWidth(320);
				setHeight(180);
			}}));
		});
	}

	@Nonnull
	protected Account createAccount(@Nonnull AccountService accountService) {
		UUID accountId = accountService.createAccount(new CreateAccountRequest() {{
			setAccountSourceId(AccountSourceId.ANONYMOUS);
			setInstitutionId(InstitutionId.COBALT);
		}});

		return accountService.findAccountById(accountId).get();
	}

	@Nonnull
	protected UUID createUploadedImage(@Nonnull Database database,
																		 @Nonnull Account account,
																		 @Nonnull FileUploadTypeId fileUploadTypeId,
																		 @Nullable UUID sourceImageId,
																		 @Nonnull Integer width,
																		 @Nonnull Integer height) {
		UUID fileUploadId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		String filename = format("%s.jpg", imageId);
		String storageKey = format("media-test/%s/%s", fileUploadTypeId.name().toLowerCase(), filename);

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
	protected Set<UUID> imageIds(@Nonnull MediaImageDetails mediaImageDetails) {
		return mediaImageDetails.getVariants().stream()
				.map(Image::getImageId)
				.collect(Collectors.toSet());
	}

	@Nonnull
	protected Optional<MediaImageGalleryItem> findGalleryItem(@Nonnull MediaService mediaService,
																														@Nonnull Account account,
																														@Nonnull UUID sourceImageId) {
		return mediaService.findMediaImageGalleryItems(account, 0, 100).getResults().stream()
				.filter(mediaImageGalleryItem -> sourceImageId.equals(mediaImageGalleryItem.getSourceImageId()))
				.findFirst();
	}
}
