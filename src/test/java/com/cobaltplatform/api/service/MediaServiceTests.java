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
import com.cobaltplatform.api.model.api.request.CreateContentRequest;
import com.cobaltplatform.api.model.api.request.CreateGroupSessionRequest;
import com.cobaltplatform.api.model.api.request.CreateMediaImagePresignedUploadRequest;
import com.cobaltplatform.api.model.api.response.MediaImageApiResponse.MediaImageApiResponseFactory;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.AccountSource.AccountSourceId;
import com.cobaltplatform.api.model.db.ContentAudienceType.ContentAudienceTypeId;
import com.cobaltplatform.api.model.db.ContentType.ContentTypeId;
import com.cobaltplatform.api.model.db.ContentVisibilityType.ContentVisibilityTypeId;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.GroupSessionLocationType.GroupSessionLocationTypeId;
import com.cobaltplatform.api.model.db.GroupSessionSchedulingSystem.GroupSessionSchedulingSystemId;
import com.cobaltplatform.api.model.db.GroupSessionStatus.GroupSessionStatusId;
import com.cobaltplatform.api.model.db.GroupSessionVisibilityType.GroupSessionVisibilityTypeId;
import com.cobaltplatform.api.model.db.Image;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.service.FindResult;
import com.cobaltplatform.api.model.service.MediaImageDetails;
import com.cobaltplatform.api.model.service.MediaImageGalleryItem;
import com.cobaltplatform.api.model.service.MediaImageScopeId;
import com.cobaltplatform.api.model.service.MediaImageUploadResult;
import com.cobaltplatform.api.util.ValidationException;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
	public void mediaImageGallerySearchMatchesFilenameAndAltText() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));
			String filenameToken = format("filename-%s", UUID.randomUUID());
			String altTextToken = format("alt-%s", UUID.randomUUID());

			UUID filenameMatchRawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, format("raw-%s.jpg", filenameToken), "Plain raw image.");
			UUID filenameMatchCropImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, filenameMatchRawImageId, 1600, 900, "filename-match-crop.jpg", "Plain crop image.");
			createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, filenameMatchCropImageId, 320, 180, "filename-match-thumbnail.jpg", "Plain thumbnail image.");

			UUID altTextMatchRawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, "alt-text-match-raw.jpg", "Plain raw image.");
			UUID altTextMatchCropImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, altTextMatchRawImageId, 1600, 900, "alt-text-match-crop.jpg", format("Crop alt text containing %s.", altTextToken));
			createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, altTextMatchCropImageId, 320, 180, "alt-text-match-thumbnail.jpg", "Plain thumbnail image.");

			FindResult<MediaImageGalleryItem> filenameResults = mediaService.findMediaImageGalleryItems(account, 0, 10, filenameToken.toUpperCase());
			FindResult<MediaImageGalleryItem> altTextResults = mediaService.findMediaImageGalleryItems(account, 0, 10, altTextToken.toUpperCase());

			Assert.assertEquals("Filename search should return the matching raw source", List.of(filenameMatchRawImageId), sourceImageIds(filenameResults));
			Assert.assertEquals("Filename search total count should reflect filtered results", Integer.valueOf(1), filenameResults.getTotalCount());
			Assert.assertEquals("Alt text search should return the matching raw source", List.of(altTextMatchRawImageId), sourceImageIds(altTextResults));
			Assert.assertEquals("Alt text search total count should reflect filtered results", Integer.valueOf(1), altTextResults.getTotalCount());
		});
	}

	@Test
	public void mediaImageGallerySearchSurfacesMatchingCropThumbnail() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));
			String rawToken = format("raw-%s", UUID.randomUUID());
			String cropToken = format("crop-%s", UUID.randomUUID());

			UUID rawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, format("%s.jpg", rawToken), "Raw source image.");
			UUID crop16x9ImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1600, 900, "default-16x9-crop.jpg", "Default crop.");
			UUID thumbnail16x9ImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, crop16x9ImageId, 320, 180, "default-16x9-thumbnail.jpg", "Default thumbnail.");
			UUID crop1x1ImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_1X1, rawImageId, 800, 800, "matching-1x1-crop.jpg", format("Square crop containing %s.", cropToken));
			UUID thumbnail1x1ImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_1X1, crop1x1ImageId, 200, 200, "matching-1x1-thumbnail.jpg", "Square thumbnail.");

			MediaImageGalleryItem cropMatchGalleryItem = mediaService.findMediaImageGalleryItems(account, 0, 10, cropToken.toUpperCase()).getResults().get(0);
			MediaImageGalleryItem rawMatchGalleryItem = mediaService.findMediaImageGalleryItems(account, 0, 10, rawToken.toUpperCase()).getResults().get(0);
			MediaImageGalleryItem blankSearchGalleryItem = findGalleryItem(mediaService, account, rawImageId, "   ").get();

			Assert.assertEquals("Crop match should surface the crop's thumbnail", thumbnail1x1ImageId, cropMatchGalleryItem.getThumbnailImage().getImageId());
			Assert.assertEquals("Raw match should use default thumbnail preference", thumbnail16x9ImageId, rawMatchGalleryItem.getThumbnailImage().getImageId());
			Assert.assertEquals("Blank search should use default thumbnail preference", thumbnail16x9ImageId, blankSearchGalleryItem.getThumbnailImage().getImageId());
		});
	}

	@Test
	public void mediaImageGallerySearchSurfacesMatchingThumbnailBeforeCropMatch() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));
			String searchToken = format("thumb-%s", UUID.randomUUID());

			UUID rawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, "thumbnail-priority-raw.jpg", "Raw image.");
			UUID crop16x9ImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1600, 900, "thumbnail-priority-16x9-crop.jpg", "Default crop.");
			createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, crop16x9ImageId, 320, 180, "thumbnail-priority-16x9-thumbnail.jpg", "Default thumbnail.");
			UUID crop4x3ImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_4X3, rawImageId, 1200, 900, "thumbnail-priority-4x3-crop.jpg", "Four by three crop.");
			UUID thumbnail4x3ImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_4X3, crop4x3ImageId, 240, 180, format("%s-match.jpg", searchToken), "Matching thumbnail.");
			UUID crop1x1ImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_1X1, rawImageId, 800, 800, "thumbnail-priority-1x1-crop.jpg", format("Crop alt text also containing %s.", searchToken));
			createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_1X1, crop1x1ImageId, 200, 200, "thumbnail-priority-1x1-thumbnail.jpg", "Square thumbnail.");

			MediaImageGalleryItem galleryItem = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken.toUpperCase()).getResults().get(0);

			Assert.assertEquals("Direct thumbnail match should outrank crop match and default preference", thumbnail4x3ImageId, galleryItem.getThumbnailImage().getImageId());
		});
	}

	@Test
	public void mediaImageGallerySearchIgnoresUnavailableRows() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));
			String searchToken = format("unavailable-%s", UUID.randomUUID());

			UUID pendingRawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, "pending-raw.jpg", "Pending raw.");
			UUID pendingCropImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, pendingRawImageId, 1600, 900, "pending-crop.jpg", "Pending crop.");
			UUID pendingThumbnailImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, pendingCropImageId, 320, 180, format("%s-pending-thumbnail.jpg", searchToken), "Pending thumbnail.");

			UUID inactiveRawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, "inactive-raw.jpg", "Inactive raw.");
			UUID inactiveCropImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, inactiveRawImageId, 1600, 900, "inactive-crop.jpg", format("Inactive crop with %s.", searchToken));
			createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, inactiveCropImageId, 320, 180, "inactive-thumbnail.jpg", "Inactive thumbnail.");

			UUID crossInstitutionRawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, format("%s-cross-raw.jpg", searchToken), "Cross-institution raw.");
			UUID crossInstitutionCropImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, crossInstitutionRawImageId, 1600, 900, "cross-crop.jpg", "Cross-institution crop.");
			UUID crossInstitutionThumbnailImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, crossInstitutionCropImageId, 320, 180, "cross-thumbnail.jpg", "Cross-institution thumbnail.");

			database.execute("UPDATE file_upload SET file_upload_status_id=? WHERE file_upload_id=(SELECT file_upload_id FROM image WHERE image_id=?)", FileUploadStatusId.CREATED, pendingThumbnailImageId);
			database.execute("UPDATE image SET active=FALSE WHERE image_id=?", inactiveCropImageId);
			database.execute("""
					UPDATE file_upload
					SET institution_id=?
					WHERE file_upload_id IN (
					  SELECT file_upload_id
					  FROM image
					  WHERE image_id IN (?,?,?)
					)
					""", InstitutionId.COBALT_IC, crossInstitutionRawImageId, crossInstitutionCropImageId, crossInstitutionThumbnailImageId);

			FindResult<MediaImageGalleryItem> results = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken);

			Assert.assertEquals("Unavailable search matches should be ignored", List.of(), sourceImageIds(results));
			Assert.assertEquals("Unavailable search matches should not count", Integer.valueOf(0), results.getTotalCount());
		});
	}

	@Test
	public void mediaImageGallerySearchPaginatesFilteredResults() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));
			String searchToken = format("page-%s", UUID.randomUUID());

			UUID firstRawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, "pagination-first-raw.jpg", format("First %s.", searchToken));
			UUID firstCropImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, firstRawImageId, 1600, 900, "pagination-first-crop.jpg", "First crop.");
			createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, firstCropImageId, 320, 180, "pagination-first-thumbnail.jpg", "First thumbnail.");

			UUID secondRawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, "pagination-second-raw.jpg", format("Second %s.", searchToken));
			UUID secondCropImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, secondRawImageId, 1600, 900, "pagination-second-crop.jpg", "Second crop.");
			createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, secondCropImageId, 320, 180, "pagination-second-thumbnail.jpg", "Second thumbnail.");

			UUID thirdRawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, "pagination-third-raw.jpg", format("Third %s.", searchToken));
			UUID thirdCropImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, thirdRawImageId, 1600, 900, "pagination-third-crop.jpg", "Third crop.");
			createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, thirdCropImageId, 320, 180, "pagination-third-thumbnail.jpg", "Third thumbnail.");

			UUID nonMatchingRawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900, "pagination-unmatched-raw.jpg", "Unmatched raw.");
			UUID nonMatchingCropImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_16X9, nonMatchingRawImageId, 1600, 900, "pagination-unmatched-crop.jpg", "Unmatched crop.");
			createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, nonMatchingCropImageId, 320, 180, "pagination-unmatched-thumbnail.jpg", "Unmatched thumbnail.");

			FindResult<MediaImageGalleryItem> firstPage = mediaService.findMediaImageGalleryItems(account, 0, 2, searchToken);
			FindResult<MediaImageGalleryItem> secondPage = mediaService.findMediaImageGalleryItems(account, 1, 2, searchToken);
			List<UUID> resultSourceImageIds = sourceImageIds(firstPage);
			resultSourceImageIds.addAll(sourceImageIds(secondPage));

			Assert.assertEquals("First page total count should reflect filtered results", Integer.valueOf(3), firstPage.getTotalCount());
			Assert.assertEquals("Second page total count should reflect filtered results", Integer.valueOf(3), secondPage.getTotalCount());
			Assert.assertEquals("First page should honor requested page size", 2, firstPage.getResults().size());
			Assert.assertEquals("Second page should contain the remaining filtered result", 1, secondPage.getResults().size());
			Assert.assertEquals("Pagination should return only matching source images", Set.of(firstRawImageId, secondRawImageId, thirdRawImageId), Set.copyOf(resultSourceImageIds));
		});
	}

	@Test
	public void mediaImageGalleryFiltersByCropFileUploadTypeId() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));
			String searchToken = format("crop-filter-%s", UUID.randomUUID());

			MediaImageFamily matching16x9Family = createUploadedMediaImageFamily(database, account, searchToken, "matching-16x9", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily nonMatching4x3Family = createUploadedMediaImageFamily(database, account, searchToken, "nonmatching-4x3", FileUploadTypeId.IMAGE_4X3);
			MediaImageFamily matchingSecond16x9Family = createUploadedMediaImageFamily(database, account, searchToken, "matching-second-16x9", FileUploadTypeId.IMAGE_16X9);

			FindResult<MediaImageGalleryItem> results = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken, FileUploadTypeId.IMAGE_16X9, null);

			Assert.assertEquals("Filtered total count should include only families with the requested crop", Integer.valueOf(2), results.getTotalCount());
			Assert.assertEquals("Filtered results should include only 16x9 crop families",
					Set.of(matching16x9Family.getRawImageId(), matchingSecond16x9Family.getRawImageId()), Set.copyOf(sourceImageIds(results)));
			Assert.assertFalse("4x3-only family should not be returned", sourceImageIds(results).contains(nonMatching4x3Family.getRawImageId()));
		});
	}

	@Test
	public void mediaImageGalleryFiltersByMultipleCropFileUploadTypeIds() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));
			String searchToken = format("multi-crop-filter-%s", UUID.randomUUID());

			MediaImageFamily matching16x9Family = createUploadedMediaImageFamily(database, account, searchToken, "multi-matching-16x9", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily matching4x3Family = createUploadedMediaImageFamily(database, account, searchToken, "multi-matching-4x3", FileUploadTypeId.IMAGE_4X3);
			MediaImageFamily nonMatching1x1Family = createUploadedMediaImageFamily(database, account, searchToken, "multi-nonmatching-1x1", FileUploadTypeId.IMAGE_1X1);

			FindResult<MediaImageGalleryItem> results = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken,
					List.of(FileUploadTypeId.IMAGE_16X9, FileUploadTypeId.IMAGE_4X3), null);

			Assert.assertEquals("Filtered total count should include only families with one of the requested crops", Integer.valueOf(2), results.getTotalCount());
			Assert.assertEquals("Filtered results should include both requested crop families",
					Set.of(matching16x9Family.getRawImageId(), matching4x3Family.getRawImageId()), Set.copyOf(sourceImageIds(results)));
			Assert.assertFalse("1x1-only family should not be returned", sourceImageIds(results).contains(nonMatching1x1Family.getRawImageId()));
		});
	}

	@Test
	public void mediaImageGalleryRejectsNonCropFileUploadTypeIdFilters() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Account account = createAccount(app.getInjector().getInstance(AccountService.class));

			Assert.assertThrows(ValidationException.class, () -> mediaService.findMediaImageGalleryItems(account, 0, 10, null, FileUploadTypeId.IMAGE_RAW, null));
			Assert.assertThrows(ValidationException.class, () -> mediaService.findMediaImageGalleryItems(account, 0, 10, null, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, null));
			Assert.assertThrows(ValidationException.class, () -> mediaService.findMediaImageGalleryItems(account, 0, 10, null,
					List.of(FileUploadTypeId.IMAGE_16X9, FileUploadTypeId.IMAGE_RAW), null));
		});
	}

	@Test
	public void pageBuilderImageLookupAcceptsOnlyActiveUploadedInstitutionCrops() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			Account account = findExistingAccount(database);

			MediaImageFamily fourByThreeFamily = createUploadedMediaImageFamily(database, account, "page-validation", "4x3", FileUploadTypeId.IMAGE_4X3);
			MediaImageFamily sixteenByNineFamily = createUploadedMediaImageFamily(database, account, "page-validation", "16x9", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily squareFamily = createUploadedMediaImageFamily(database, account, "page-validation", "1x1", FileUploadTypeId.IMAGE_1X1);

			Assert.assertTrue(mediaService.findActiveUploadedMediaCropImageById(account.getInstitutionId(), fourByThreeFamily.getCropImageId()).isPresent());
			Assert.assertTrue(mediaService.findActiveUploadedMediaCropImageById(account.getInstitutionId(), sixteenByNineFamily.getCropImageId()).isPresent());
			Assert.assertTrue(mediaService.findActiveUploadedMediaCropImageById(account.getInstitutionId(), squareFamily.getCropImageId()).isPresent());
			Assert.assertFalse("Raw images are not valid page-builder selections",
					mediaService.findActiveUploadedMediaCropImageById(account.getInstitutionId(), fourByThreeFamily.getRawImageId()).isPresent());
			Assert.assertFalse("Thumbnails are not valid page-builder selections",
					mediaService.findActiveUploadedMediaCropImageById(account.getInstitutionId(), fourByThreeFamily.getThumbnailImageId()).isPresent());
			Assert.assertFalse("Crops from another institution are invalid",
					mediaService.findActiveUploadedMediaCropImageById(InstitutionId.COBALT_IC, squareFamily.getCropImageId()).isPresent());

			database.execute("UPDATE image SET active=FALSE WHERE image_id=?", fourByThreeFamily.getCropImageId());
			database.execute("""
					UPDATE file_upload SET file_upload_status_id=?
					WHERE file_upload_id=(SELECT file_upload_id FROM image WHERE image_id=?)
					""", FileUploadStatusId.CREATED, sixteenByNineFamily.getCropImageId());
			Assert.assertFalse("Inactive crops are invalid",
					mediaService.findActiveUploadedMediaCropImageById(account.getInstitutionId(), fourByThreeFamily.getCropImageId()).isPresent());
			Assert.assertFalse("Pending crops are invalid",
					mediaService.findActiveUploadedMediaCropImageById(account.getInstitutionId(), sixteenByNineFamily.getCropImageId()).isPresent());
		});
	}

	@Test
	public void mediaImageGalleryResourceScopeReturnsLiveResourceContentAssociations() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeContentImageIdColumnExists(database);
			Account account = findExistingAdministratorAccount(database);
			String searchToken = format("resource-scope-%s", UUID.randomUUID());

			MediaImageFamily liveResourceFamily = createUploadedMediaImageFamily(database, account, searchToken, "live-resource", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily scheduledResourceFamily = createUploadedMediaImageFamily(database, account, searchToken, "scheduled-resource", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily unassociatedFamily = createUploadedMediaImageFamily(database, account, searchToken, "unassociated-resource", FileUploadTypeId.IMAGE_16X9);

			createResourceContentWithImage(adminContentService, account, liveResourceFamily.getCropImageId(), LocalDate.now());
			createResourceContentWithImage(adminContentService, account, scheduledResourceFamily.getCropImageId(), LocalDate.now().plusDays(1));

			FindResult<MediaImageGalleryItem> results = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken, null, MediaImageScopeId.RESOURCE);

			Assert.assertEquals("Resource scope should include only live resource content associations", Integer.valueOf(1), results.getTotalCount());
			Assert.assertEquals("Resource scope should return only the live associated image family", List.of(liveResourceFamily.getRawImageId()), sourceImageIds(results));
			Assert.assertFalse("Scheduled resource content should not count as currently associated", sourceImageIds(results).contains(scheduledResourceFamily.getRawImageId()));
			Assert.assertFalse("Unassociated images should not be returned", sourceImageIds(results).contains(unassociatedFamily.getRawImageId()));
		});
	}

	@Test
	public void mediaImageGalleryGroupSessionScopeReturnsNonDeletedGroupSessionAssociations() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeGroupSessionImageIdColumnExists(database);
			Account account = findExistingAccount(database);
			String searchToken = format("group-session-scope-%s", UUID.randomUUID());

			MediaImageFamily canceledGroupSessionFamily = createUploadedMediaImageFamily(database, account, searchToken, "canceled-group-session", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily deletedGroupSessionFamily = createUploadedMediaImageFamily(database, account, searchToken, "deleted-group-session", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily unassociatedFamily = createUploadedMediaImageFamily(database, account, searchToken, "unassociated-group-session", FileUploadTypeId.IMAGE_16X9);

			UUID canceledGroupSessionId = createGroupSessionWithImage(groupSessionService, account, canceledGroupSessionFamily.getCropImageId(), "canceled-scope");
			UUID deletedGroupSessionId = createGroupSessionWithImage(groupSessionService, account, deletedGroupSessionFamily.getCropImageId(), "deleted-scope");
			database.execute("UPDATE group_session SET group_session_status_id=? WHERE group_session_id=?", GroupSessionStatusId.CANCELED, canceledGroupSessionId);
			database.execute("UPDATE group_session SET group_session_status_id=? WHERE group_session_id=?", GroupSessionStatusId.DELETED, deletedGroupSessionId);

			FindResult<MediaImageGalleryItem> results = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken, null, MediaImageScopeId.GROUP_SESSION);

			Assert.assertEquals("Group-session scope should include non-deleted associations only", Integer.valueOf(1), results.getTotalCount());
			Assert.assertEquals("Canceled group sessions are still non-deleted and should count", List.of(canceledGroupSessionFamily.getRawImageId()), sourceImageIds(results));
			Assert.assertFalse("Deleted group sessions should not count", sourceImageIds(results).contains(deletedGroupSessionFamily.getRawImageId()));
			Assert.assertFalse("Unassociated images should not be returned", sourceImageIds(results).contains(unassociatedFamily.getRawImageId()));
		});
	}

	@Test
	public void mediaImageGalleryPageScopeIncludesDraftHeroColumnAndCallToActionAssociations() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumePageBuilderImageIdColumnsExist(database);
			Account account = findExistingAdministratorAccount(database);
			String searchToken = format("page-scope-%s", UUID.randomUUID());

			MediaImageFamily heroFamily = createUploadedMediaImageFamily(database, account, searchToken, "draft-page-hero", FileUploadTypeId.IMAGE_4X3);
			MediaImageFamily columnFamily = createUploadedMediaImageFamily(database, account, searchToken, "draft-page-column", FileUploadTypeId.IMAGE_1X1);
			MediaImageFamily callToActionFamily = createUploadedMediaImageFamily(database, account, searchToken, "draft-page-cta", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily deletedRowFamily = createUploadedMediaImageFamily(database, account, searchToken, "deleted-page-row", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily deletedPageFamily = createUploadedMediaImageFamily(database, account, searchToken, "deleted-page", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily unassociatedFamily = createUploadedMediaImageFamily(database, account, searchToken, "unassociated-page", FileUploadTypeId.IMAGE_16X9);

			createPageBuilderImageAssociations(database, account, heroFamily.getCropImageId(), columnFamily.getCropImageId(),
					callToActionFamily.getCropImageId(), deletedRowFamily.getCropImageId(), deletedPageFamily.getCropImageId());

			FindResult<MediaImageGalleryItem> results = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken,
					null, MediaImageScopeId.PAGE);
			Assert.assertEquals("Page scope should include hero, column, and CTA associations on active draft pages",
					Set.of(heroFamily.getRawImageId(), columnFamily.getRawImageId(), callToActionFamily.getRawImageId()),
					Set.copyOf(sourceImageIds(results)));
			Assert.assertFalse(sourceImageIds(results).contains(deletedRowFamily.getRawImageId()));
			Assert.assertFalse(sourceImageIds(results).contains(deletedPageFamily.getRawImageId()));
			Assert.assertFalse(sourceImageIds(results).contains(unassociatedFamily.getRawImageId()));

			FindResult<MediaImageGalleryItem> squareResults = mediaService.findMediaImageGalleryItems(account, 0, 10,
					searchToken, FileUploadTypeId.IMAGE_1X1, MediaImageScopeId.PAGE);
			Assert.assertEquals("Page scope should compose with crop filters", List.of(columnFamily.getRawImageId()),
					sourceImageIds(squareResults));
		});
	}

	@Test
	public void mediaImageGalleryFiltersByMultipleMediaImageScopeIds() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeContentImageIdColumnExists(database);
			assumeGroupSessionImageIdColumnExists(database);
			assumePageBuilderImageIdColumnsExist(database);
			Account account = findExistingAdministratorAccount(database);
			String searchToken = format("multi-scope-%s", UUID.randomUUID());

			MediaImageFamily resourceFamily = createUploadedMediaImageFamily(database, account, searchToken, "multi-resource", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily groupSessionFamily = createUploadedMediaImageFamily(database, account, searchToken, "multi-group-session", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily pageFamily = createUploadedMediaImageFamily(database, account, searchToken, "multi-page", FileUploadTypeId.IMAGE_4X3);
			MediaImageFamily deletedGroupSessionFamily = createUploadedMediaImageFamily(database, account, searchToken, "multi-deleted-group-session", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily unassociatedFamily = createUploadedMediaImageFamily(database, account, searchToken, "multi-unassociated", FileUploadTypeId.IMAGE_16X9);

			createResourceContentWithImage(adminContentService, account, resourceFamily.getCropImageId(), LocalDate.now());
			createGroupSessionWithImage(groupSessionService, account, groupSessionFamily.getCropImageId(), "multi-scope-active");
			UUID deletedGroupSessionId = createGroupSessionWithImage(groupSessionService, account, deletedGroupSessionFamily.getCropImageId(), "multi-scope-deleted");
			database.execute("UPDATE group_session SET group_session_status_id=? WHERE group_session_id=?", GroupSessionStatusId.DELETED, deletedGroupSessionId);
			createPageBuilderImageAssociations(database, account, pageFamily.getCropImageId(), pageFamily.getCropImageId(),
					pageFamily.getCropImageId(), pageFamily.getCropImageId(), pageFamily.getCropImageId());

			FindResult<MediaImageGalleryItem> results = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken,
					null, List.of(MediaImageScopeId.RESOURCE, MediaImageScopeId.GROUP_SESSION, MediaImageScopeId.PAGE));

			Assert.assertEquals("Multiple scopes should include matching resource, group-session, and page associations", Integer.valueOf(3), results.getTotalCount());
			Assert.assertEquals("Multiple scopes should return families associated to either requested scope",
					Set.of(resourceFamily.getRawImageId(), groupSessionFamily.getRawImageId(), pageFamily.getRawImageId()),
					Set.copyOf(sourceImageIds(results)));
			Assert.assertFalse("Deleted group sessions should not count for multi-scope filtering", sourceImageIds(results).contains(deletedGroupSessionFamily.getRawImageId()));
			Assert.assertFalse("Unassociated images should not be returned", sourceImageIds(results).contains(unassociatedFamily.getRawImageId()));
		});
	}

	@Test
	public void mediaImageGalleryScopeWithCropFilterMatchesExactCropImageId() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeContentImageIdColumnExists(database);
			Account account = findExistingAdministratorAccount(database);
			String searchToken = format("exact-crop-scope-%s", UUID.randomUUID());

			MediaImageFamily rawAssociatedFamily = createUploadedMediaImageFamily(database, account, searchToken, "raw-associated", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily cropAssociatedFamily = createUploadedMediaImageFamily(database, account, searchToken, "crop-associated", FileUploadTypeId.IMAGE_16X9);

			createResourceContentWithImage(adminContentService, account, rawAssociatedFamily.getRawImageId(), LocalDate.now());
			createResourceContentWithImage(adminContentService, account, cropAssociatedFamily.getCropImageId(), LocalDate.now());

			FindResult<MediaImageGalleryItem> results = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken, FileUploadTypeId.IMAGE_16X9, MediaImageScopeId.RESOURCE);

			Assert.assertEquals("Scope with crop filter should match the exact associated crop image ID", Integer.valueOf(1), results.getTotalCount());
			Assert.assertEquals("Raw-associated families should not match crop-filtered scope results", List.of(cropAssociatedFamily.getRawImageId()), sourceImageIds(results));
		});
	}

	@Test
	public void mediaImageGalleryMultipleScopesWithMultipleCropFiltersMatchesExactCropImageIds() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeContentImageIdColumnExists(database);
			assumeGroupSessionImageIdColumnExists(database);
			Account account = findExistingAdministratorAccount(database);
			String searchToken = format("multi-exact-crop-scope-%s", UUID.randomUUID());

			MediaImageFamily resourceCropAssociated16x9Family = createUploadedMediaImageFamily(database, account, searchToken, "multi-resource-crop-16x9", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily groupSessionCropAssociated4x3Family = createUploadedMediaImageFamily(database, account, searchToken, "multi-group-session-crop-4x3", FileUploadTypeId.IMAGE_4X3);
			MediaImageFamily rawAssociatedFamily = createUploadedMediaImageFamily(database, account, searchToken, "multi-raw-associated", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily unrequestedCropAssociatedFamily = createUploadedMediaImageFamily(database, account, searchToken, "multi-unrequested-crop", FileUploadTypeId.IMAGE_1X1);

			createResourceContentWithImage(adminContentService, account, resourceCropAssociated16x9Family.getCropImageId(), LocalDate.now());
			createGroupSessionWithImage(groupSessionService, account, groupSessionCropAssociated4x3Family.getCropImageId(), "multi-exact-crop-scope");
			createResourceContentWithImage(adminContentService, account, rawAssociatedFamily.getRawImageId(), LocalDate.now());
			createResourceContentWithImage(adminContentService, account, unrequestedCropAssociatedFamily.getCropImageId(), LocalDate.now());

			FindResult<MediaImageGalleryItem> results = mediaService.findMediaImageGalleryItems(account, 0, 10, searchToken,
					List.of(FileUploadTypeId.IMAGE_16X9, FileUploadTypeId.IMAGE_4X3),
					List.of(MediaImageScopeId.RESOURCE, MediaImageScopeId.GROUP_SESSION));

			Assert.assertEquals("Multiple scope and crop filters should match exact requested crop image IDs", Integer.valueOf(2), results.getTotalCount());
			Assert.assertEquals("Only crop-associated families with requested crop ratios should be returned",
					Set.of(resourceCropAssociated16x9Family.getRawImageId(), groupSessionCropAssociated4x3Family.getRawImageId()), Set.copyOf(sourceImageIds(results)));
			Assert.assertFalse("Raw-associated families should not match crop-filtered scope results", sourceImageIds(results).contains(rawAssociatedFamily.getRawImageId()));
			Assert.assertFalse("Crop associations outside the requested ratios should not match", sourceImageIds(results).contains(unrequestedCropAssociatedFamily.getRawImageId()));
		});
	}

	@Test
	public void mediaImageGallerySearchPaginatesScopeFilteredResults() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeContentImageIdColumnExists(database);
			Account account = findExistingAdministratorAccount(database);
			String searchToken = format("scope-page-%s", UUID.randomUUID());

			MediaImageFamily firstResourceFamily = createUploadedMediaImageFamily(database, account, searchToken, "first-resource", FileUploadTypeId.IMAGE_16X9);
			MediaImageFamily secondResourceFamily = createUploadedMediaImageFamily(database, account, searchToken, "second-resource", FileUploadTypeId.IMAGE_16X9);
			createUploadedMediaImageFamily(database, account, searchToken, "unassociated-resource", FileUploadTypeId.IMAGE_16X9);

			createResourceContentWithImage(adminContentService, account, firstResourceFamily.getCropImageId(), LocalDate.now());
			createResourceContentWithImage(adminContentService, account, secondResourceFamily.getCropImageId(), LocalDate.now());

			FindResult<MediaImageGalleryItem> firstPage = mediaService.findMediaImageGalleryItems(account, 0, 1, searchToken, null, MediaImageScopeId.RESOURCE);
			FindResult<MediaImageGalleryItem> secondPage = mediaService.findMediaImageGalleryItems(account, 1, 1, searchToken, null, MediaImageScopeId.RESOURCE);
			List<UUID> resultSourceImageIds = sourceImageIds(firstPage);
			resultSourceImageIds.addAll(sourceImageIds(secondPage));

			Assert.assertEquals("First page total count should reflect scope-filtered results", Integer.valueOf(2), firstPage.getTotalCount());
			Assert.assertEquals("Second page total count should reflect scope-filtered results", Integer.valueOf(2), secondPage.getTotalCount());
			Assert.assertEquals("First page should honor requested page size", 1, firstPage.getResults().size());
			Assert.assertEquals("Second page should honor requested page size", 1, secondPage.getResults().size());
			Assert.assertEquals("Pagination should return only associated resource images", Set.of(firstResourceFamily.getRawImageId(), secondResourceFamily.getRawImageId()), Set.copyOf(resultSourceImageIds));
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
	public void replacingExistingAspectRatioInactivatesPreviousPairAndRewiresLiveConsumers() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			MediaService mediaService = app.getInjector().getInstance(MediaService.class);
			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeContentImageIdColumnExists(database);
			assumeGroupSessionImageIdColumnExists(database);
			assumePageBuilderImageIdColumnsExist(database);
			Account account = findExistingAdministratorAccount(database);

			UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID previousCropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1600, 900);
			UUID previousThumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, previousCropImageId, 320, 180);
			UUID otherRatioCropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_4X3, rawImageId, 1200, 900);
			createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_4X3, otherRatioCropImageId, 240, 180);
			UUID newCropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_16X9, rawImageId, 1280, 720);
			UUID newThumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, newCropImageId, 320, 180);
			MediaImageFamily unrelatedFamily = createUploadedMediaImageFamily(database, account, "recrop", "unrelated", FileUploadTypeId.IMAGE_16X9);
			UUID previousCropFileUploadId = findFileUploadIdByImageId(database, previousCropImageId);
			UUID newCropFileUploadId = findFileUploadIdByImageId(database, newCropImageId);
			UUID otherRatioCropFileUploadId = findFileUploadIdByImageId(database, otherRatioCropImageId);
			UUID unrelatedCropFileUploadId = findFileUploadIdByImageId(database, unrelatedFamily.getCropImageId());

			UUID liveContentId = createResourceContentWithImage(adminContentService, account, previousCropImageId, LocalDate.now());
			UUID deletedContentId = createResourceContentWithImage(adminContentService, account, previousCropImageId, LocalDate.now());
			database.execute("UPDATE content SET deleted_flag=TRUE WHERE content_id=?", deletedContentId);
			UUID otherRatioContentId = createResourceContentWithImage(adminContentService, account, otherRatioCropImageId, LocalDate.now());
			UUID unrelatedContentId = createResourceContentWithImage(adminContentService, account, unrelatedFamily.getCropImageId(), LocalDate.now());

			UUID liveGroupSessionId = createGroupSessionWithImage(groupSessionService, account, previousCropImageId, "recrop-live");
			UUID deletedGroupSessionId = createGroupSessionWithImage(groupSessionService, account, previousCropImageId, "recrop-deleted");
			database.execute("UPDATE group_session SET group_session_status_id=? WHERE group_session_id=?",
					GroupSessionStatusId.DELETED, deletedGroupSessionId);

			PageBuilderImageAssociations pageBuilderAssociations = createPageBuilderImageAssociations(database, account,
					previousCropImageId, previousCropImageId, previousCropImageId, previousCropImageId, previousCropImageId);

			mediaService.confirmMediaImageUploaded(account, newThumbnailImageId);

			Assert.assertFalse("Previous crop should be inactive", mediaService.findImageById(previousCropImageId).get().getActive());
			Assert.assertFalse("Previous thumbnail should be inactive", mediaService.findImageById(previousThumbnailImageId).get().getActive());
			Assert.assertTrue("New crop should be active", mediaService.findImageById(newCropImageId).get().getActive());
			Assert.assertTrue("New thumbnail should be active", mediaService.findImageById(newThumbnailImageId).get().getActive());
			assertImageAssociation(database, "content", "content_id", liveContentId, newCropImageId, newCropFileUploadId);
			assertImageAssociation(database, "group_session", "group_session_id", liveGroupSessionId, newCropImageId, newCropFileUploadId);
			assertImageAssociation(database, "page", "page_id", pageBuilderAssociations.getPageId(), newCropImageId, newCropFileUploadId);
			assertImageAssociation(database, "page_row_column", "page_row_id", pageBuilderAssociations.getColumnPageRowId(), newCropImageId, newCropFileUploadId);
			assertImageAssociation(database, "page_row_call_to_action", "page_row_id", pageBuilderAssociations.getCallToActionPageRowId(), newCropImageId, newCropFileUploadId);

			assertImageAssociation(database, "content", "content_id", deletedContentId, previousCropImageId, previousCropFileUploadId);
			assertImageAssociation(database, "group_session", "group_session_id", deletedGroupSessionId, previousCropImageId, previousCropFileUploadId);
			assertImageAssociation(database, "page_row_column", "page_row_id", pageBuilderAssociations.getDeletedPageRowId(), previousCropImageId, previousCropFileUploadId);
			assertImageAssociation(database, "page", "page_id", pageBuilderAssociations.getDeletedPageId(), previousCropImageId, previousCropFileUploadId);
			assertImageAssociation(database, "content", "content_id", otherRatioContentId, otherRatioCropImageId, otherRatioCropFileUploadId);
			assertImageAssociation(database, "content", "content_id", unrelatedContentId, unrelatedFamily.getCropImageId(), unrelatedCropFileUploadId);

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
			assertImageAssociation(database, "content", "content_id", liveContentId, newCropImageId, newCropFileUploadId);
			assertImageAssociation(database, "page_row_call_to_action", "page_row_id", pageBuilderAssociations.getCallToActionPageRowId(), newCropImageId, newCropFileUploadId);

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

	protected void assumeContentImageIdColumnExists(@Nonnull Database database) {
		requireNonNull(database);

		Boolean contentImageIdColumnExists = database.queryForObject("""
				SELECT EXISTS (
				  SELECT 1
				  FROM information_schema.columns
				  WHERE table_schema='cobalt'
				  AND table_name='content'
				  AND column_name='image_id'
				)
				""", Boolean.class).get();

		Assume.assumeTrue("Branch schema must include content.image_id", contentImageIdColumnExists);
	}

	protected void assumeGroupSessionImageIdColumnExists(@Nonnull Database database) {
		requireNonNull(database);

		Boolean groupSessionImageIdColumnExists = database.queryForObject("""
				SELECT EXISTS (
				  SELECT 1
				  FROM information_schema.columns
				  WHERE table_schema='cobalt'
				  AND table_name='group_session'
				  AND column_name='image_id'
				)
				""", Boolean.class).get();

		Assume.assumeTrue("Branch schema must include group_session.image_id", groupSessionImageIdColumnExists);
	}

	protected void assumePageBuilderImageIdColumnsExist(@Nonnull Database database) {
		requireNonNull(database);

		Long imageIdColumnCount = database.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema='cobalt'
				AND table_name IN ('page','page_row_column','page_row_call_to_action')
				AND column_name='image_id'
				""", Long.class).get();

		Assume.assumeTrue("Branch schema must include page-builder image_id columns", imageIdColumnCount == 3L);
	}

	@Nonnull
	protected PageBuilderImageAssociations createPageBuilderImageAssociations(@Nonnull Database database,
																														 @Nonnull Account account,
																														 @Nonnull UUID heroImageId,
																														 @Nonnull UUID columnImageId,
																														 @Nonnull UUID callToActionImageId,
																														 @Nonnull UUID deletedRowImageId,
																														 @Nonnull UUID deletedPageImageId) {
		UUID pageId = UUID.randomUUID();
		UUID pageSectionId = UUID.randomUUID();
		UUID customRowId = UUID.randomUUID();
		UUID callToActionRowId = UUID.randomUUID();
		UUID deletedRowId = UUID.randomUUID();
		UUID deletedPageId = UUID.randomUUID();

		database.execute("INSERT INTO page_group (page_group_id) VALUES (?),(?)", pageId, deletedPageId);
		database.execute("""
				INSERT INTO page (page_id,name,url_name,page_status_id,image_id,image_file_upload_id,institution_id,
				  created_by_account_id,page_group_id)
				SELECT ?,?,?, 'DRAFT', i.image_id, i.file_upload_id, ?, ?, ? FROM image i WHERE i.image_id=?
				""", pageId, "Page scope draft", format("page-scope-%s", pageId), account.getInstitutionId(),
				account.getAccountId(), pageId, heroImageId);
		database.execute("""
				INSERT INTO page_section (page_section_id,page_id,name,background_color_id,display_order,created_by_account_id)
				VALUES (?,?,'Page Scope','WHITE',0,?)
				""", pageSectionId, pageId, account.getAccountId());
		database.execute("""
				INSERT INTO page_row (page_row_id,page_section_id,row_type_id,display_order,created_by_account_id)
				VALUES (?,?,'CUSTOM_ROW',0,?),(?,?,'CALL_TO_ACTION_BLOCK',1,?),(?,?,'CUSTOM_ROW',2,?)
				""", customRowId, pageSectionId, account.getAccountId(), callToActionRowId, pageSectionId,
				account.getAccountId(), deletedRowId, pageSectionId, account.getAccountId());
		database.execute("""
				INSERT INTO page_row_column (page_row_id,image_id,image_file_upload_id,column_display_order)
				SELECT ?,i.image_id,i.file_upload_id,0 FROM image i WHERE i.image_id=?
				""", customRowId, columnImageId);
		database.execute("""
				INSERT INTO page_row_call_to_action
				  (page_row_id,headline,description,button_text,button_url,image_id,image_file_upload_id)
				SELECT ?,'Headline','Description','Button','https://example.com',i.image_id,i.file_upload_id
				FROM image i WHERE i.image_id=?
				""", callToActionRowId, callToActionImageId);
		database.execute("""
				INSERT INTO page_row_column (page_row_id,image_id,image_file_upload_id,column_display_order)
				SELECT ?,i.image_id,i.file_upload_id,0 FROM image i WHERE i.image_id=?
				""", deletedRowId, deletedRowImageId);
		database.execute("UPDATE page_row SET deleted_flag=TRUE WHERE page_row_id=?", deletedRowId);

		database.execute("""
				INSERT INTO page (page_id,name,url_name,page_status_id,image_id,image_file_upload_id,deleted_flag,institution_id,
				  created_by_account_id,page_group_id)
				SELECT ?,?,?, 'DRAFT', i.image_id, i.file_upload_id, TRUE, ?, ?, ? FROM image i WHERE i.image_id=?
				""", deletedPageId, "Deleted page scope", format("deleted-page-scope-%s", deletedPageId),
				account.getInstitutionId(), account.getAccountId(), deletedPageId, deletedPageImageId);

		return new PageBuilderImageAssociations(pageId, customRowId, callToActionRowId, deletedRowId, deletedPageId);
	}

	@Nonnull
	protected MediaImageFamily createUploadedMediaImageFamily(@Nonnull Database database,
																													 @Nonnull Account account,
																													 @Nonnull String searchToken,
																													 @Nonnull String label,
																													 @Nonnull FileUploadTypeId cropFileUploadTypeId) {
		requireNonNull(database);
		requireNonNull(account);
		requireNonNull(searchToken);
		requireNonNull(label);
		requireNonNull(cropFileUploadTypeId);

		Integer cropWidth;
		Integer cropHeight;
		Integer thumbnailWidth;
		Integer thumbnailHeight;
		FileUploadTypeId thumbnailFileUploadTypeId = thumbnailFileUploadTypeIdForCropFileUploadTypeId(cropFileUploadTypeId);
		String filenamePrefix = format("%s-%s", label, searchToken);

		if (cropFileUploadTypeId == FileUploadTypeId.IMAGE_16X9) {
			cropWidth = 1600;
			cropHeight = 900;
			thumbnailWidth = 320;
			thumbnailHeight = 180;
		} else if (cropFileUploadTypeId == FileUploadTypeId.IMAGE_4X3) {
			cropWidth = 1200;
			cropHeight = 900;
			thumbnailWidth = 240;
			thumbnailHeight = 180;
		} else if (cropFileUploadTypeId == FileUploadTypeId.IMAGE_1X1) {
			cropWidth = 800;
			cropHeight = 800;
			thumbnailWidth = 200;
			thumbnailHeight = 200;
		} else {
			throw new IllegalArgumentException(format("Unsupported crop file upload type ID '%s'.", cropFileUploadTypeId));
		}

		UUID rawImageId = createUploadedImageWithFilename(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900,
				format("%s-raw.jpg", filenamePrefix), format("Raw image for %s.", searchToken));
		UUID cropImageId = createUploadedImageWithFilename(database, account, cropFileUploadTypeId, rawImageId, cropWidth, cropHeight,
				format("%s-crop.jpg", filenamePrefix), format("Cropped image for %s.", searchToken));
		UUID thumbnailImageId = createUploadedImageWithFilename(database, account, thumbnailFileUploadTypeId, cropImageId, thumbnailWidth, thumbnailHeight,
				format("%s-thumbnail.jpg", filenamePrefix), format("Thumbnail image for %s.", searchToken));

		return new MediaImageFamily(rawImageId, cropImageId, thumbnailImageId);
	}

	@Nonnull
	protected FileUploadTypeId thumbnailFileUploadTypeIdForCropFileUploadTypeId(@Nonnull FileUploadTypeId cropFileUploadTypeId) {
		requireNonNull(cropFileUploadTypeId);

		return switch (cropFileUploadTypeId) {
			case IMAGE_16X9 -> FileUploadTypeId.IMAGE_THUMBNAIL_16X9;
			case IMAGE_4X3 -> FileUploadTypeId.IMAGE_THUMBNAIL_4X3;
			case IMAGE_1X1 -> FileUploadTypeId.IMAGE_THUMBNAIL_1X1;
			default -> throw new IllegalArgumentException(format("Unsupported crop file upload type ID '%s'.", cropFileUploadTypeId));
		};
	}

	@Nonnull
	protected UUID createResourceContentWithImage(@Nonnull AdminContentService adminContentService,
																										 @Nonnull Account account,
																										 @Nonnull UUID imageId,
																										 @Nonnull LocalDate publishStartDate) {
		requireNonNull(adminContentService);
		requireNonNull(account);
		requireNonNull(imageId);
		requireNonNull(publishStartDate);

		CreateContentRequest request = createContentRequest("media-image-gallery-scope", publishStartDate);
		request.setImageId(imageId);
		UUID contentId = adminContentService.createContent(account, request).getContentId();
		adminContentService.publishContent(contentId, account);
		return contentId;
	}

	@Nonnull
	protected CreateContentRequest createContentRequest(@Nonnull String titleSuffix,
																											@Nonnull LocalDate publishStartDate) {
		requireNonNull(titleSuffix);
		requireNonNull(publishStartDate);

		return new CreateContentRequest() {{
			setContentTypeId(ContentTypeId.ARTICLE);
			setTitle(format("Media image gallery test %s %s", titleSuffix, UUID.randomUUID()));
			setAuthor("Test Author");
			setDescription("Media image gallery test description.");
			setPublishStartDate(publishStartDate);
			setPublishRecurring(false);
			setSharedFlag(false);
			setContentVisibilityTypeId(ContentVisibilityTypeId.PUBLIC);
			setContentAudienceTypeIds(Set.of(ContentAudienceTypeId.MYSELF));
		}};
	}

	@Nonnull
	protected UUID createGroupSessionWithImage(@Nonnull GroupSessionService groupSessionService,
																						 @Nonnull Account account,
																						 @Nonnull UUID imageId,
																						 @Nonnull String urlNameSuffix) {
		requireNonNull(groupSessionService);
		requireNonNull(account);
		requireNonNull(imageId);
		requireNonNull(urlNameSuffix);

		CreateGroupSessionRequest request = createGroupSessionRequest(account, urlNameSuffix);
		request.setImageId(imageId);
		return groupSessionService.createGroupSession(request, account);
	}

	@Nonnull
	protected CreateGroupSessionRequest createGroupSessionRequest(@Nonnull Account account,
																																@Nonnull String urlNameSuffix) {
		requireNonNull(account);
		requireNonNull(urlNameSuffix);

		return new CreateGroupSessionRequest() {{
			setInstitutionId(InstitutionId.COBALT);
			setGroupSessionSchedulingSystemId(GroupSessionSchedulingSystemId.COBALT);
			setGroupSessionLocationTypeId(GroupSessionLocationTypeId.IN_PERSON);
			setSubmitterAccountId(account.getAccountId());
			setTitle("Media image gallery test");
			setDescription("Media image gallery test description.");
			setUrlName(format("media-image-gallery-test-%s-%s", urlNameSuffix, UUID.randomUUID()));
			setInPersonLocation("Test location");
			setFacilitatorName("Test Facilitator");
			setFacilitatorEmailAddress("facilitator@example.com");
			setStartDateTime(LocalDateTime.now().plusDays(7));
			setEndDateTime(LocalDateTime.now().plusDays(7).plusHours(1));
			setGroupSessionVisibilityTypeId(GroupSessionVisibilityTypeId.PUBLIC);
			setDifferentEmailAddressForNotifications(false);
			setSingleSessionFlag(true);
			setSendFollowupEmail(false);
			setSendReminderEmail(false);
		}};
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
		return createUploadedImageWithFilename(database, account, fileUploadTypeId, sourceImageId, width, height, null, imageAltText, imageHash);
	}

	@Nonnull
	protected UUID createUploadedImageWithFilename(@Nonnull Database database,
																								 @Nonnull Account account,
																								 @Nonnull FileUploadTypeId fileUploadTypeId,
																								 @Nullable UUID sourceImageId,
																								 @Nonnull Integer width,
																								 @Nonnull Integer height,
																								 @Nullable String filename,
																								 @Nullable String imageAltText) {
		return createUploadedImageWithFilename(database, account, fileUploadTypeId, sourceImageId, width, height, filename, imageAltText, null);
	}

	@Nonnull
	protected UUID createUploadedImageWithFilename(@Nonnull Database database,
																								 @Nonnull Account account,
																								 @Nonnull FileUploadTypeId fileUploadTypeId,
																								 @Nullable UUID sourceImageId,
																								 @Nonnull Integer width,
																								 @Nonnull Integer height,
																								 @Nullable String filename,
																								 @Nullable String imageAltText,
																								 @Nullable String imageHash) {
		UUID fileUploadId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		filename = filename == null ? format("%s.jpg", imageId) : filename;
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
	protected UUID findFileUploadIdByImageId(@Nonnull Database database,
																					 @Nonnull UUID imageId) {
		requireNonNull(database);
		requireNonNull(imageId);

		return database.queryForObject("SELECT file_upload_id FROM image WHERE image_id=?", UUID.class, imageId).get();
	}

	protected void assertImageAssociation(@Nonnull Database database,
																			 @Nonnull String tableName,
																			 @Nonnull String idColumnName,
																			 @Nonnull UUID id,
																			 @Nonnull UUID expectedImageId,
																			 @Nonnull UUID expectedImageFileUploadId) {
		requireNonNull(database);
		requireNonNull(tableName);
		requireNonNull(idColumnName);
		requireNonNull(id);
		requireNonNull(expectedImageId);
		requireNonNull(expectedImageFileUploadId);

		ImageAssociation imageAssociation = database.queryForObject(format("""
				SELECT image_id, image_file_upload_id
				FROM %s
				WHERE %s=?
				""", tableName, idColumnName), ImageAssociation.class, id).get();
		Assert.assertEquals(expectedImageId, imageAssociation.getImageId());
		Assert.assertEquals(expectedImageFileUploadId, imageAssociation.getImageFileUploadId());
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
		return findGalleryItem(mediaService, account, sourceImageId, null);
	}

	@Nonnull
	protected Optional<MediaImageGalleryItem> findGalleryItem(@Nonnull MediaService mediaService,
																														@Nonnull Account account,
																														@Nonnull UUID sourceImageId,
																														@Nullable String searchQuery) {
		return mediaService.findMediaImageGalleryItems(account, 0, 100, searchQuery).getResults().stream()
				.filter(mediaImageGalleryItem -> sourceImageId.equals(mediaImageGalleryItem.getSourceImageId()))
				.findFirst();
	}

	@Nonnull
	protected List<UUID> sourceImageIds(@Nonnull FindResult<MediaImageGalleryItem> findResult) {
		requireNonNull(findResult);

		return findResult.getResults().stream()
				.map(MediaImageGalleryItem::getSourceImageId)
				.collect(Collectors.toList());
	}

	protected static class ImageAssociation {
		@Nullable
		private UUID imageId;
		@Nullable
		private UUID imageFileUploadId;

		@Nullable
		public UUID getImageId() {
			return this.imageId;
		}

		public void setImageId(@Nullable UUID imageId) {
			this.imageId = imageId;
		}

		@Nullable
		public UUID getImageFileUploadId() {
			return this.imageFileUploadId;
		}

		public void setImageFileUploadId(@Nullable UUID imageFileUploadId) {
			this.imageFileUploadId = imageFileUploadId;
		}
	}

	protected static class PageBuilderImageAssociations {
		@Nonnull
		private final UUID pageId;
		@Nonnull
		private final UUID columnPageRowId;
		@Nonnull
		private final UUID callToActionPageRowId;
		@Nonnull
		private final UUID deletedPageRowId;
		@Nonnull
		private final UUID deletedPageId;

		public PageBuilderImageAssociations(@Nonnull UUID pageId,
																				@Nonnull UUID columnPageRowId,
																				@Nonnull UUID callToActionPageRowId,
																				@Nonnull UUID deletedPageRowId,
																				@Nonnull UUID deletedPageId) {
			this.pageId = requireNonNull(pageId);
			this.columnPageRowId = requireNonNull(columnPageRowId);
			this.callToActionPageRowId = requireNonNull(callToActionPageRowId);
			this.deletedPageRowId = requireNonNull(deletedPageRowId);
			this.deletedPageId = requireNonNull(deletedPageId);
		}

		@Nonnull
		public UUID getPageId() {
			return this.pageId;
		}

		@Nonnull
		public UUID getColumnPageRowId() {
			return this.columnPageRowId;
		}

		@Nonnull
		public UUID getCallToActionPageRowId() {
			return this.callToActionPageRowId;
		}

		@Nonnull
		public UUID getDeletedPageRowId() {
			return this.deletedPageRowId;
		}

		@Nonnull
		public UUID getDeletedPageId() {
			return this.deletedPageId;
		}
	}

	protected static class MediaImageFamily {
		@Nonnull
		private final UUID rawImageId;
		@Nonnull
		private final UUID cropImageId;
		@Nonnull
		private final UUID thumbnailImageId;

		public MediaImageFamily(@Nonnull UUID rawImageId,
														@Nonnull UUID cropImageId,
														@Nonnull UUID thumbnailImageId) {
			requireNonNull(rawImageId);
			requireNonNull(cropImageId);
			requireNonNull(thumbnailImageId);

			this.rawImageId = rawImageId;
			this.cropImageId = cropImageId;
			this.thumbnailImageId = thumbnailImageId;
		}

		@Nonnull
		public UUID getRawImageId() {
			return this.rawImageId;
		}

		@Nonnull
		public UUID getCropImageId() {
			return this.cropImageId;
		}

		@Nonnull
		public UUID getThumbnailImageId() {
			return this.thumbnailImageId;
		}
	}
}
