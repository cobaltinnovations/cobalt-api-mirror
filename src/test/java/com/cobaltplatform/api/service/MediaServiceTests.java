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
import com.cobaltplatform.api.model.api.response.MediaImageApiResponse.MediaImageApiResponseFactory;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.AccountSource.AccountSourceId;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.Image;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.service.MediaImageDetails;
import com.cobaltplatform.api.model.service.MediaImageGalleryItem;
import com.cobaltplatform.api.model.service.MediaImageUploadResult;
import com.cobaltplatform.api.util.ValidationException;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class MediaServiceTests {
	@Nonnull
	private static final String IMAGE_HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	@Nonnull
	private static final String IMAGE_HASH_A_UPPERCASE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
	@Nonnull
	private static final String IMAGE_HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
	@Nonnull
	private static final String IMAGE_HASH_C = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
	@Nonnull
	private static final String IMAGE_HASH_D = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
	@Nonnull
	private static final String IMAGE_HASH_E = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";

	@Test
	public void createMediaImagePresignedUploadPersistsTrimmedAltText() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			MediaImageApiResponseFactory mediaImageApiResponseFactory = app.getInjector().getInstance(MediaImageApiResponseFactory.class);
			Account account = findExistingAccount(database);

			MediaImageUploadResult mediaImageUploadResult = mediaService.createMediaImagePresignedUpload(account, new CreateMediaImagePresignedUploadRequest() {{
				setFileUploadTypeId(FileUploadTypeId.IMAGE_RAW);
				setFilename("raw-alt-text.jpg");
				setContentType("image/jpeg");
				setWidth(1600);
				setHeight(900);
				setImageAltText("  A calm lake at sunrise.  ");
				setImageHash(IMAGE_HASH_A_UPPERCASE);
			}});

			Image image = mediaService.findImageById(mediaImageUploadResult.getImageId()).get();

			Assert.assertEquals("Image should store trimmed alt text", "A calm lake at sunrise.", image.getImageAltText());
			Assert.assertEquals("Image should store normalized image hash", IMAGE_HASH_A, image.getImageHash());
			Assert.assertEquals("API response should expose alt text", "A calm lake at sunrise.", mediaImageApiResponseFactory.create(image).getImageAltText());
		});
	}

	@Test
	public void createMediaImagePresignedUploadStoresBlankAltTextAsNull() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			MediaImageApiResponseFactory mediaImageApiResponseFactory = app.getInjector().getInstance(MediaImageApiResponseFactory.class);
			Account account = findExistingAccount(database);

			MediaImageUploadResult mediaImageUploadResult = mediaService.createMediaImagePresignedUpload(account, new CreateMediaImagePresignedUploadRequest() {{
				setFileUploadTypeId(FileUploadTypeId.IMAGE_RAW);
				setFilename("blank-alt-text.jpg");
				setContentType("image/jpeg");
				setWidth(1600);
				setHeight(900);
				setImageAltText("   ");
				setImageHash(IMAGE_HASH_B);
			}});

			Image image = mediaService.findImageById(mediaImageUploadResult.getImageId()).get();

			Assert.assertNull("Image should store blank alt text as null", image.getImageAltText());
			Assert.assertNull("API response should expose null alt text", mediaImageApiResponseFactory.create(image).getImageAltText());
		});
	}

	@Test
	public void rawMediaImagePresignedUploadsRequireValidImageHash() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = findExistingAccount(database);

			Assert.assertThrows(ValidationException.class, () -> mediaService.createMediaImagePresignedUpload(account, rawUploadRequest(null)));
			Assert.assertThrows(ValidationException.class, () -> mediaService.createMediaImagePresignedUpload(account, rawUploadRequest("   ")));
			Assert.assertThrows(ValidationException.class, () -> mediaService.createMediaImagePresignedUpload(account, rawUploadRequest("abc")));
			Assert.assertThrows(ValidationException.class, () -> mediaService.createMediaImagePresignedUpload(account, rawUploadRequest("gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggg")));
		});
	}

	@Test
	public void duplicateRawImageHashesDoNotBlockPresignedUploads() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = findExistingAccount(database);

			MediaImageUploadResult firstUploadResult = mediaService.createMediaImagePresignedUpload(account, rawUploadRequest(IMAGE_HASH_C));
			MediaImageUploadResult secondUploadResult = mediaService.createMediaImagePresignedUpload(account, rawUploadRequest(IMAGE_HASH_C));

			Assert.assertNotEquals("Duplicate raw uploads should create distinct images", firstUploadResult.getImageId(), secondUploadResult.getImageId());
			Assert.assertEquals("First image should store duplicate hash", IMAGE_HASH_C, mediaService.findImageById(firstUploadResult.getImageId()).get().getImageHash());
			Assert.assertEquals("Second image should store duplicate hash", IMAGE_HASH_C, mediaService.findImageById(secondUploadResult.getImageId()).get().getImageHash());
		});
	}

	@Test
	public void derivedMediaImagePresignedUploadsDoNotRequireOrStoreImageHash() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = findExistingAccount(database);

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, null, IMAGE_HASH_D);
			MediaImageUploadResult cropImageUploadResult = mediaService.createMediaImagePresignedUpload(account, new CreateMediaImagePresignedUploadRequest() {{
				setFileUploadTypeId(FileUploadTypeId.IMAGE_16X9);
				setSourceImageId(rawImageId);
				setFilename("crop-no-hash.jpg");
				setContentType("image/jpeg");
				setWidth(1600);
				setHeight(900);
			}});

			Image cropImage = mediaService.findImageById(cropImageUploadResult.getImageId()).get();

			Assert.assertNull("Crop image should not store image hash", cropImage.getImageHash());
		});
	}

	@Test
	public void duplicateRawMediaImageLookupReturnsUploadedActiveSameInstitutionRawImages() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));

			UUID firstDuplicateImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, null, IMAGE_HASH_E);
			UUID secondDuplicateImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1200, 800, null, IMAGE_HASH_E);
			UUID pendingImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 900, 600, null, IMAGE_HASH_E);
			UUID inactiveImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 800, 600, null, IMAGE_HASH_E);
			UUID crossInstitutionImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 700, 500, null, IMAGE_HASH_E);
			UUID rawImageForDerivedImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, null, IMAGE_HASH_A);
			UUID derivedImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageForDerivedImageId, 1600, 900, null, IMAGE_HASH_E);

			Instant created = Instant.parse("2026-01-01T00:00:00Z");
			database.execute("UPDATE image SET created=? WHERE image_id=?", created, firstDuplicateImageId);
			database.execute("UPDATE image SET created=? WHERE image_id=?", created.plusSeconds(60), secondDuplicateImageId);
			database.execute("UPDATE file_upload SET file_upload_status_id=? WHERE file_upload_id=(SELECT file_upload_id FROM image WHERE image_id=?)", FileUploadStatusId.CREATED, pendingImageId);
			database.execute("UPDATE image SET active=FALSE WHERE image_id=?", inactiveImageId);
			database.execute("UPDATE file_upload SET institution_id=? WHERE file_upload_id=(SELECT file_upload_id FROM image WHERE image_id=?)", InstitutionId.COBALT_IC, crossInstitutionImageId);

			List<UUID> duplicateImageIds = mediaService.findDuplicateRawMediaImageIds(account, IMAGE_HASH_E.toUpperCase());

			Assert.assertEquals("Lookup should return active uploaded raw duplicates in deterministic order", List.of(firstDuplicateImageId, secondDuplicateImageId), duplicateImageIds);
			Assert.assertFalse("Pending image should be ignored", duplicateImageIds.contains(pendingImageId));
			Assert.assertFalse("Inactive image should be ignored", duplicateImageIds.contains(inactiveImageId));
			Assert.assertFalse("Cross-institution image should be ignored", duplicateImageIds.contains(crossInstitutionImageId));
			Assert.assertFalse("Derived image should be ignored", duplicateImageIds.contains(derivedImageId));
			Assert.assertEquals("Unknown hash should have no duplicates", List.of(), mediaService.findDuplicateRawMediaImageIds(account, IMAGE_HASH_B));
			Assert.assertThrows(ValidationException.class, () -> mediaService.findDuplicateRawMediaImageIds(account, "not-a-sha-256"));
		});
	}

	@Test
	public void derivedMediaImagePresignedUploadsInheritSourceAltTextWhenOmitted() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = findExistingAccount(database);
			String imageAltText = "A facilitator speaking with a small group.";

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, imageAltText);
			MediaImageUploadResult cropImageUploadResult = mediaService.createMediaImagePresignedUpload(account, new CreateMediaImagePresignedUploadRequest() {{
				setFileUploadTypeId(FileUploadTypeId.IMAGE_16X9);
				setSourceImageId(rawImageId);
				setFilename("crop-alt-text.jpg");
				setContentType("image/jpeg");
				setWidth(1600);
				setHeight(900);
			}});

			Image cropImage = mediaService.findImageById(cropImageUploadResult.getImageId()).get();

			Assert.assertEquals("Crop should inherit raw image alt text", imageAltText, cropImage.getImageAltText());

			database.execute("""
					UPDATE file_upload
					SET file_upload_status_id=?
					WHERE file_upload_id=?
					""", FileUploadStatusId.UPLOADED, cropImage.getFileUploadId());

			MediaImageUploadResult thumbnailImageUploadResult = mediaService.createMediaImagePresignedUpload(account, new CreateMediaImagePresignedUploadRequest() {{
				setFileUploadTypeId(FileUploadTypeId.IMAGE_THUMBNAIL_16X9);
				setSourceImageId(cropImageUploadResult.getImageId());
				setFilename("thumbnail-alt-text.jpg");
				setContentType("image/jpeg");
				setWidth(320);
				setHeight(180);
			}});

			Image thumbnailImage = mediaService.findImageById(thumbnailImageUploadResult.getImageId()).get();

			Assert.assertEquals("Thumbnail should inherit crop image alt text", imageAltText, thumbnailImage.getImageAltText());
		});
	}

	@Test
	public void mediaImageReadPathsExposeAltText() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));
			String imageAltText = "A group seated in a sunny room.";

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, imageAltText);
			createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1600, 900, imageAltText);
			UUID cropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_1X1, rawImageId, 800, 800, imageAltText);
			UUID thumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_1X1, cropImageId, 200, 200, imageAltText);

			MediaImageDetails mediaImageDetails = mediaService.findMediaImageDetails(account, rawImageId).get();

			Assert.assertEquals("Details image should expose alt text", imageAltText, mediaImageDetails.getImage().getImageAltText());

			for (Image variant : mediaImageDetails.getVariants())
				Assert.assertEquals("Details variants should expose alt text", imageAltText, variant.getImageAltText());

			MediaImageGalleryItem galleryItem = findGalleryItem(mediaService, account, rawImageId).get();

			Assert.assertEquals("Gallery thumbnail should expose alt text", thumbnailImageId, galleryItem.getThumbnailImage().getImageId());
			Assert.assertEquals("Gallery thumbnail should expose alt text", imageAltText, galleryItem.getThumbnailImage().getImageAltText());
		});
	}

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
	protected CreateMediaImagePresignedUploadRequest rawUploadRequest(@Nullable String imageHash) {
		return new CreateMediaImagePresignedUploadRequest() {{
			setFileUploadTypeId(FileUploadTypeId.IMAGE_RAW);
			setFilename("raw-image.jpg");
			setContentType("image/jpeg");
			setWidth(1600);
			setHeight(900);
			setImageHash(imageHash);
		}};
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
		return createUploadedImage(database, account, fileUploadTypeId, sourceImageId, width, height, null);
	}

	@Nonnull
	protected UUID createUploadedImage(@Nonnull Database database,
																		 @Nonnull Account account,
																		 @Nonnull FileUploadTypeId fileUploadTypeId,
																		 @Nullable UUID sourceImageId,
																		 @Nonnull Integer width,
																		 @Nonnull Integer height,
																		 @Nullable String imageAltText) {
		return createUploadedImage(database, account, fileUploadTypeId, sourceImageId, width, height, imageAltText, null);
	}

	@Nonnull
	protected UUID createUploadedImage(@Nonnull Database database,
																		 @Nonnull Account account,
																		 @Nonnull FileUploadTypeId fileUploadTypeId,
																		 @Nullable UUID sourceImageId,
																		 @Nonnull Integer width,
																		 @Nonnull Integer height,
																		 @Nullable String imageAltText,
																		 @Nullable String imageHash) {
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
				  height,
				  image_alt_text,
				  image_hash
				) VALUES (?,?,?,?,?,?,?,?)
				""", imageId, fileUploadId, sourceImageId, account.getAccountId(), width, height, imageAltText, imageHash);

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
