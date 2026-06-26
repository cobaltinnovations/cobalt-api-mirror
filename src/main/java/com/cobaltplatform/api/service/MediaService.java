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

import com.cobaltplatform.api.model.api.request.CreateFileUploadRequest;
import com.cobaltplatform.api.model.api.request.CreateMediaImagePresignedUploadRequest;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.Image;
import com.cobaltplatform.api.model.service.FindResult;
import com.cobaltplatform.api.model.service.FileUploadResult;
import com.cobaltplatform.api.model.service.MediaImageDetails;
import com.cobaltplatform.api.model.service.MediaImageGalleryItem;
import com.cobaltplatform.api.model.service.MediaImageUploadResult;
import com.cobaltplatform.api.model.service.MediaImageVariant;
import com.cobaltplatform.api.model.service.MediaImageVariant.ImageType;
import com.cobaltplatform.api.util.UploadManager;
import com.cobaltplatform.api.util.ValidationException;
import com.cobaltplatform.api.util.ValidationException.FieldError;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.lokalized.Strings;
import com.pyranid.Database;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.cobaltplatform.api.util.DatabaseUtility.sqlInListPlaceholders;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class MediaService {
	@Nonnull
	private static final Integer DEFAULT_MEDIA_IMAGE_GALLERY_PAGE_SIZE;
	@Nonnull
	private static final Integer MAXIMUM_MEDIA_IMAGE_GALLERY_PAGE_SIZE;
	@Nonnull
	private static final EnumSet<FileUploadTypeId> MEDIA_IMAGE_FILE_UPLOAD_TYPE_IDS;
	@Nonnull
	private static final EnumSet<FileUploadTypeId> MEDIA_IMAGE_CROP_FILE_UPLOAD_TYPE_IDS;
	@Nonnull
	private static final EnumSet<FileUploadTypeId> MEDIA_IMAGE_THUMBNAIL_FILE_UPLOAD_TYPE_IDS;
	@Nonnull
	private static final Map<FileUploadTypeId, FileUploadTypeId> REQUIRED_SOURCE_FILE_UPLOAD_TYPE_IDS_BY_FILE_UPLOAD_TYPE_ID;
	@Nonnull
	private final Provider<SystemService> systemServiceProvider;
	@Nonnull
	private final DatabaseProvider databaseProvider;
	@Nonnull
	private final UploadManager uploadManager;
	@Nonnull
	private final Strings strings;

	static {
		DEFAULT_MEDIA_IMAGE_GALLERY_PAGE_SIZE = 50;
		MAXIMUM_MEDIA_IMAGE_GALLERY_PAGE_SIZE = 100;

		MEDIA_IMAGE_CROP_FILE_UPLOAD_TYPE_IDS = EnumSet.of(
				FileUploadTypeId.IMAGE_4X3,
				FileUploadTypeId.IMAGE_16X9,
				FileUploadTypeId.IMAGE_1X1
		);

		MEDIA_IMAGE_THUMBNAIL_FILE_UPLOAD_TYPE_IDS = EnumSet.of(
				FileUploadTypeId.IMAGE_THUMBNAIL_4X3,
				FileUploadTypeId.IMAGE_THUMBNAIL_16X9,
				FileUploadTypeId.IMAGE_THUMBNAIL_1X1
		);

		MEDIA_IMAGE_FILE_UPLOAD_TYPE_IDS = EnumSet.of(
				FileUploadTypeId.IMAGE_RAW,
				FileUploadTypeId.IMAGE_4X3,
				FileUploadTypeId.IMAGE_16X9,
				FileUploadTypeId.IMAGE_1X1,
				FileUploadTypeId.IMAGE_THUMBNAIL_4X3,
				FileUploadTypeId.IMAGE_THUMBNAIL_16X9,
				FileUploadTypeId.IMAGE_THUMBNAIL_1X1
		);

		REQUIRED_SOURCE_FILE_UPLOAD_TYPE_IDS_BY_FILE_UPLOAD_TYPE_ID = new EnumMap<>(FileUploadTypeId.class);
		REQUIRED_SOURCE_FILE_UPLOAD_TYPE_IDS_BY_FILE_UPLOAD_TYPE_ID.put(FileUploadTypeId.IMAGE_4X3, FileUploadTypeId.IMAGE_RAW);
		REQUIRED_SOURCE_FILE_UPLOAD_TYPE_IDS_BY_FILE_UPLOAD_TYPE_ID.put(FileUploadTypeId.IMAGE_16X9, FileUploadTypeId.IMAGE_RAW);
		REQUIRED_SOURCE_FILE_UPLOAD_TYPE_IDS_BY_FILE_UPLOAD_TYPE_ID.put(FileUploadTypeId.IMAGE_1X1, FileUploadTypeId.IMAGE_RAW);
		REQUIRED_SOURCE_FILE_UPLOAD_TYPE_IDS_BY_FILE_UPLOAD_TYPE_ID.put(FileUploadTypeId.IMAGE_THUMBNAIL_4X3, FileUploadTypeId.IMAGE_4X3);
		REQUIRED_SOURCE_FILE_UPLOAD_TYPE_IDS_BY_FILE_UPLOAD_TYPE_ID.put(FileUploadTypeId.IMAGE_THUMBNAIL_16X9, FileUploadTypeId.IMAGE_16X9);
		REQUIRED_SOURCE_FILE_UPLOAD_TYPE_IDS_BY_FILE_UPLOAD_TYPE_ID.put(FileUploadTypeId.IMAGE_THUMBNAIL_1X1, FileUploadTypeId.IMAGE_1X1);
	}

	@Inject
	public MediaService(@Nonnull Provider<SystemService> systemServiceProvider,
											@Nonnull DatabaseProvider databaseProvider,
											@Nonnull UploadManager uploadManager,
											@Nonnull Strings strings) {
		requireNonNull(systemServiceProvider);
		requireNonNull(databaseProvider);
		requireNonNull(uploadManager);
		requireNonNull(strings);

		this.systemServiceProvider = systemServiceProvider;
		this.databaseProvider = databaseProvider;
		this.uploadManager = uploadManager;
		this.strings = strings;
	}

	@Nonnull
	public Optional<Image> findImageById(@Nullable UUID imageId) {
		if (imageId == null)
			return Optional.empty();

		return getDatabase().queryForObject("""
				SELECT *
				FROM v_image
				WHERE image_id=?
				""", Image.class, imageId);
	}

	@Nonnull
	public Optional<MediaImageDetails> findMediaImageDetails(@Nonnull Account account,
																													@Nonnull UUID imageId) {
		requireNonNull(account);
		requireNonNull(imageId);

		Image image = getDatabase().queryForObject("""
				SELECT *
				FROM v_image
				WHERE image_id=?
				AND institution_id=?
				AND file_upload_status_id=?
				AND file_upload_type_id IN (?,?,?,?,?,?,?)
				""", Image.class,
				imageId,
				account.getInstitutionId(),
				FileUploadStatusId.UPLOADED,
				FileUploadTypeId.IMAGE_RAW,
				FileUploadTypeId.IMAGE_4X3,
				FileUploadTypeId.IMAGE_16X9,
				FileUploadTypeId.IMAGE_1X1,
				FileUploadTypeId.IMAGE_THUMBNAIL_4X3,
				FileUploadTypeId.IMAGE_THUMBNAIL_16X9,
				FileUploadTypeId.IMAGE_THUMBNAIL_1X1).orElse(null);

		if (image == null)
			return Optional.empty();

		UUID sourceImageId = findMediaImageFamilySourceImageId(account, image).orElse(null);

		if (sourceImageId == null)
			return Optional.empty();

		List<Image> variants = findMediaImagesByFamilySourceImageId(account, sourceImageId, imageId);
		return Optional.of(new MediaImageDetails(image, variants));
	}

	@Nonnull
	public FindResult<MediaImageGalleryItem> findMediaImageGalleryItems(@Nonnull Account account,
																																		 @Nullable Integer pageNumber,
																																		 @Nullable Integer pageSize) {
		requireNonNull(account);

		if (pageNumber == null || pageNumber < 0)
			pageNumber = 0;

		if (pageSize == null || pageSize <= 0)
			pageSize = DEFAULT_MEDIA_IMAGE_GALLERY_PAGE_SIZE;
		else if (pageSize > MAXIMUM_MEDIA_IMAGE_GALLERY_PAGE_SIZE)
			pageSize = MAXIMUM_MEDIA_IMAGE_GALLERY_PAGE_SIZE;

		List<MediaImageGalleryItemWithTotalCount> thumbnailImages = getDatabase().queryForList("""
				WITH thumbnail_candidates AS (
				  SELECT
				    raw.image_id AS gallery_source_image_id,
				    thumbnail_fu.last_updated AS gallery_uploaded_date,
				    thumbnail.*,
				    CASE thumbnail.file_upload_type_id
				      WHEN 'IMAGE_THUMBNAIL_16X9' THEN 1
				      WHEN 'IMAGE_THUMBNAIL_4X3' THEN 2
				      WHEN 'IMAGE_THUMBNAIL_1X1' THEN 3
				      ELSE 4
				    END AS thumbnail_preference
				  FROM
				    v_image thumbnail
				    JOIN v_image crop ON crop.image_id=thumbnail.source_image_id
				    JOIN v_image raw ON raw.image_id=crop.source_image_id
				    JOIN file_upload thumbnail_fu ON thumbnail_fu.file_upload_id=thumbnail.file_upload_id
				  WHERE
				    raw.institution_id=?
				    AND crop.institution_id=raw.institution_id
				    AND thumbnail.institution_id=raw.institution_id
				    AND raw.file_upload_status_id=?
				    AND crop.file_upload_status_id=?
				    AND thumbnail.file_upload_status_id=?
				    AND raw.file_upload_type_id=?
				    AND crop.file_upload_type_id IN (?,?,?)
				    AND thumbnail.file_upload_type_id IN (?,?,?)
				), ranked_gallery_images AS (
				  SELECT
				    thumbnail_candidates.*,
				    ROW_NUMBER() OVER (
				      PARTITION BY gallery_source_image_id
				      ORDER BY thumbnail_preference, created DESC, image_id
				    ) AS gallery_rank
				  FROM
				    thumbnail_candidates
				), gallery_images AS (
				  SELECT *
				  FROM ranked_gallery_images
				  WHERE gallery_rank=1
				), counted_gallery_images AS (
				  SELECT
				    gallery_images.*,
				    COUNT(*) OVER() AS total_count
				  FROM
				    gallery_images
				)
				SELECT *
				FROM counted_gallery_images
				ORDER BY gallery_uploaded_date DESC NULLS LAST, gallery_source_image_id
				LIMIT ?
				OFFSET ?
				""", MediaImageGalleryItemWithTotalCount.class,
				account.getInstitutionId(),
				FileUploadStatusId.UPLOADED,
				FileUploadStatusId.UPLOADED,
				FileUploadStatusId.UPLOADED,
				FileUploadTypeId.IMAGE_RAW,
				FileUploadTypeId.IMAGE_4X3,
				FileUploadTypeId.IMAGE_16X9,
				FileUploadTypeId.IMAGE_1X1,
				FileUploadTypeId.IMAGE_THUMBNAIL_4X3,
				FileUploadTypeId.IMAGE_THUMBNAIL_16X9,
				FileUploadTypeId.IMAGE_THUMBNAIL_1X1,
				pageSize,
				pageNumber * pageSize);

		if (thumbnailImages.size() == 0)
			return new FindResult<>(List.of(), 0);

		List<UUID> sourceImageIds = thumbnailImages.stream()
				.map(MediaImageGalleryItemWithTotalCount::getGallerySourceImageId)
				.collect(Collectors.toList());
		Map<UUID, List<MediaImageVariant>> variantsBySourceImageId = findMediaImageVariantsBySourceImageIds(account, sourceImageIds);
		List<MediaImageGalleryItem> mediaImageGalleryItems = new ArrayList<>(thumbnailImages.size());

		for (MediaImageGalleryItemWithTotalCount thumbnailImage : thumbnailImages) {
			mediaImageGalleryItems.add(new MediaImageGalleryItem(
					thumbnailImage,
					thumbnailImage.getGallerySourceImageId(),
					variantsBySourceImageId.getOrDefault(thumbnailImage.getGallerySourceImageId(), List.of())
			));
		}

		return new FindResult<>(mediaImageGalleryItems, thumbnailImages.get(0).getTotalCount());
	}

	@Nonnull
	public MediaImageUploadResult createMediaImagePresignedUpload(@Nonnull Account account,
																																@Nonnull CreateMediaImagePresignedUploadRequest request) {
		requireNonNull(account);
		requireNonNull(request);

		FileUploadTypeId fileUploadTypeId = request.getFileUploadTypeId();
		UUID sourceImageId = request.getSourceImageId();
		String filename = trimToNull(request.getFilename());
		String contentType = trimToNull(request.getContentType());
		Integer width = request.getWidth();
		Integer height = request.getHeight();
		Image sourceImage = null;
		String fileUploadTypeStorageKey = null;

		ValidationException validationException = new ValidationException();

		if (fileUploadTypeId == null) {
			validationException.add(new FieldError("fileUploadTypeId", getStrings().get("File Upload Type ID is required.")));
		} else if (!MEDIA_IMAGE_FILE_UPLOAD_TYPE_IDS.contains(fileUploadTypeId)) {
			validationException.add(new FieldError("fileUploadTypeId", getStrings().get("File Upload Type ID is invalid.")));
		} else {
			fileUploadTypeStorageKey = findStorageKeyForFileUploadTypeId(fileUploadTypeId).orElse(null);

			if (fileUploadTypeStorageKey == null)
				validationException.add(new FieldError("fileUploadTypeId", getStrings().get("File Upload Type ID is not configured for media uploads.")));
		}

		if (filename == null)
			validationException.add(new FieldError("filename", getStrings().get("Filename is required.")));

		if (contentType == null) {
			validationException.add(new FieldError("contentType", getStrings().get("Content type is required.")));
		} else if (!contentType.toLowerCase().startsWith("image/")) {
			validationException.add(new FieldError("contentType", getStrings().get("Content type must be an image.")));
		}

		if (width == null)
			validationException.add(new FieldError("width", getStrings().get("Width is required.")));
		else if (width <= 0)
			validationException.add(new FieldError("width", getStrings().get("Width must be greater than 0.")));

		if (height == null)
			validationException.add(new FieldError("height", getStrings().get("Height is required.")));
		else if (height <= 0)
			validationException.add(new FieldError("height", getStrings().get("Height must be greater than 0.")));

		if (fileUploadTypeId == FileUploadTypeId.IMAGE_RAW) {
			if (sourceImageId != null)
				validationException.add(new FieldError("sourceImageId", getStrings().get("Raw image uploads cannot specify a source image.")));
		} else if (fileUploadTypeId != null && MEDIA_IMAGE_FILE_UPLOAD_TYPE_IDS.contains(fileUploadTypeId)) {
			if (sourceImageId == null) {
				validationException.add(new FieldError("sourceImageId", getStrings().get("Source image ID is required.")));
			} else {
				sourceImage = findImageById(sourceImageId).orElse(null);

				if (sourceImage == null) {
					validationException.add(new FieldError("sourceImageId", getStrings().get("Source image ID is invalid.")));
				} else {
					FileUploadTypeId requiredSourceFileUploadTypeId = REQUIRED_SOURCE_FILE_UPLOAD_TYPE_IDS_BY_FILE_UPLOAD_TYPE_ID.get(fileUploadTypeId);

					if (sourceImage.getInstitutionId() != account.getInstitutionId())
						validationException.add(new FieldError("sourceImageId", getStrings().get("Source image ID is invalid.")));

					if (sourceImage.getFileUploadStatusId() == FileUploadStatusId.ABANDONED)
						validationException.add(new FieldError("sourceImageId", getStrings().get("Source image has been abandoned.")));

					if (sourceImage.getFileUploadTypeId() != requiredSourceFileUploadTypeId)
						validationException.add(new FieldError("sourceImageId", getStrings().get("Source image type is invalid.")));
				}
			}
		}

		if (width != null && height != null && width > 0 && height > 0 && fileUploadTypeId != null)
			validateAspectRatio(fileUploadTypeId, width, height, validationException);

		if (validationException.hasErrors())
			throw validationException;

		UUID imageId = UUID.randomUUID();
		UUID fileUploadId = UUID.randomUUID();
		String storageKey = format("media-uploads/%s/%s/%s/%s", account.getInstitutionId(), fileUploadTypeStorageKey, fileUploadId, filename);
		Map<String, String> metadata = new HashMap<>();
		metadata.put("account-id", account.getAccountId().toString());
		metadata.put("image-id", imageId.toString());

		if (sourceImageId != null)
			metadata.put("source-image-id", sourceImageId.toString());

		CreateFileUploadRequest fileUploadRequest = new CreateFileUploadRequest();
		fileUploadRequest.setAccountId(account.getAccountId());
		fileUploadRequest.setFileUploadTypeId(fileUploadTypeId);
		fileUploadRequest.setFilename(filename);
		fileUploadRequest.setContentType(contentType);
		fileUploadRequest.setFilesize(request.getFilesize());
		fileUploadRequest.setPublicRead(true);
		fileUploadRequest.setMetadata(metadata);

		MediaImageUploadResult[] mediaImageUploadResult = new MediaImageUploadResult[1];

		getDatabase().transaction(() -> {
			FileUploadResult fileUploadResult = getSystemService().createFileUploadAtStorageKey(fileUploadId, fileUploadRequest, storageKey);

			getDatabase().execute("""
					INSERT INTO image (
					  image_id,
					  file_upload_id,
					  source_image_id,
					  created_by_account_id,
					  width,
					  height
					) VALUES (?,?,?,?,?,?)
					""", imageId, fileUploadId, sourceImageId, account.getAccountId(), width, height);

			mediaImageUploadResult[0] = new MediaImageUploadResult(imageId, fileUploadResult);
		});

		return mediaImageUploadResult[0];
	}

	@Nonnull
	public Image confirmMediaImageUploaded(@Nonnull Account account,
																				 @Nonnull UUID imageId) {
		requireNonNull(account);
		requireNonNull(imageId);

		Image image = findImageById(imageId).orElse(null);

		if (image == null)
			throw new ValidationException(new FieldError("imageId", getStrings().get("Image ID is invalid.")));

		if (image.getInstitutionId() != account.getInstitutionId() || !account.getAccountId().equals(image.getCreatedByAccountId()))
			throw new ValidationException(new FieldError("imageId", getStrings().get("Image ID is invalid.")));

		if (image.getFileUploadStatusId() == FileUploadStatusId.ABANDONED)
			throw new ValidationException(new FieldError("imageId", getStrings().get("Image upload has been abandoned.")));

		if (image.getFileUploadStatusId() == FileUploadStatusId.UPLOADED)
			return image;

		String storageBucket = trimToNull(image.getStorageBucket());
		String storageKey = trimToNull(image.getStorageKey());

		ValidationException validationException = new ValidationException();

		if (storageBucket == null)
			validationException.add(new FieldError("storageBucket", getStrings().get("Storage bucket is required.")));

		if (storageKey == null)
			validationException.add(new FieldError("storageKey", getStrings().get("Storage key is required.")));

		if (validationException.hasErrors())
			throw validationException;

		if (!getUploadManager().objectExists(storageBucket, storageKey))
			throw new ValidationException(new FieldError("imageId", getStrings().get("Uploaded file was not found.")));

		getDatabase().execute("""
				UPDATE file_upload
				SET file_upload_status_id=?
				WHERE file_upload_id=?
				""", FileUploadStatusId.UPLOADED, image.getFileUploadId());

		return findImageById(imageId).get();
	}

	@Nonnull
	protected Optional<String> findStorageKeyForFileUploadTypeId(@Nullable FileUploadTypeId fileUploadTypeId) {
		if (fileUploadTypeId == null)
			return Optional.empty();

		return getDatabase().queryForObject("""
				SELECT storage_key
				FROM file_upload_type
				WHERE file_upload_type_id=?
				""", String.class, fileUploadTypeId);
	}

	protected void validateAspectRatio(@Nonnull FileUploadTypeId fileUploadTypeId,
																		 @Nonnull Integer width,
																		 @Nonnull Integer height,
																		 @Nonnull ValidationException validationException) {
		requireNonNull(fileUploadTypeId);
		requireNonNull(width);
		requireNonNull(height);
		requireNonNull(validationException);

		switch (fileUploadTypeId) {
			case IMAGE_4X3, IMAGE_THUMBNAIL_4X3 -> {
				if (!hasAspectRatio(width, height, 4, 3))
					validationException.add(new FieldError("width", getStrings().get("Image dimensions must use a 4:3 aspect ratio.")));
			}
			case IMAGE_16X9, IMAGE_THUMBNAIL_16X9 -> {
				if (!hasAspectRatio(width, height, 16, 9))
					validationException.add(new FieldError("width", getStrings().get("Image dimensions must use a 16:9 aspect ratio.")));
			}
			case IMAGE_1X1, IMAGE_THUMBNAIL_1X1 -> {
				if (!hasAspectRatio(width, height, 1, 1))
					validationException.add(new FieldError("width", getStrings().get("Image dimensions must use a 1:1 aspect ratio.")));
			}
			default -> {
			}
		}
	}

	protected Boolean hasAspectRatio(@Nonnull Integer width,
																	 @Nonnull Integer height,
																	 @Nonnull Integer ratioWidth,
																	 @Nonnull Integer ratioHeight) {
		requireNonNull(width);
		requireNonNull(height);
		requireNonNull(ratioWidth);
		requireNonNull(ratioHeight);

		return (long) width * ratioHeight == (long) height * ratioWidth;
	}

	@Nonnull
	protected Optional<UUID> findMediaImageFamilySourceImageId(@Nonnull Account account,
																														 @Nonnull Image image) {
		requireNonNull(account);
		requireNonNull(image);

		FileUploadTypeId fileUploadTypeId = image.getFileUploadTypeId();

		if (fileUploadTypeId == FileUploadTypeId.IMAGE_RAW)
			return Optional.of(image.getImageId());

		if (MEDIA_IMAGE_CROP_FILE_UPLOAD_TYPE_IDS.contains(fileUploadTypeId)) {
			UUID sourceImageId = image.getSourceImageId();

			if (sourceImageId == null)
				return Optional.empty();

			return getDatabase().queryForObject("""
					SELECT image_id
					FROM v_image
					WHERE image_id=?
					AND institution_id=?
					AND file_upload_status_id=?
					AND file_upload_type_id=?
					""", UUID.class, sourceImageId, account.getInstitutionId(), FileUploadStatusId.UPLOADED, FileUploadTypeId.IMAGE_RAW);
		}

		if (MEDIA_IMAGE_THUMBNAIL_FILE_UPLOAD_TYPE_IDS.contains(fileUploadTypeId)) {
			UUID cropImageId = image.getSourceImageId();

			if (cropImageId == null)
				return Optional.empty();

			return getDatabase().queryForObject("""
					SELECT raw.image_id
					FROM
					  v_image crop
					  JOIN v_image raw ON raw.image_id=crop.source_image_id
					WHERE
					  crop.image_id=?
					  AND crop.institution_id=?
					  AND raw.institution_id=crop.institution_id
					  AND crop.file_upload_status_id=?
					  AND raw.file_upload_status_id=?
					  AND crop.file_upload_type_id IN (?,?,?)
					  AND raw.file_upload_type_id=?
					""", UUID.class,
					cropImageId,
					account.getInstitutionId(),
					FileUploadStatusId.UPLOADED,
					FileUploadStatusId.UPLOADED,
					FileUploadTypeId.IMAGE_4X3,
					FileUploadTypeId.IMAGE_16X9,
					FileUploadTypeId.IMAGE_1X1,
					FileUploadTypeId.IMAGE_RAW);
		}

		return Optional.empty();
	}

	@Nonnull
	protected List<Image> findMediaImagesByFamilySourceImageId(@Nonnull Account account,
																														 @Nonnull UUID sourceImageId,
																														 @Nonnull UUID excludedImageId) {
		requireNonNull(account);
		requireNonNull(sourceImageId);
		requireNonNull(excludedImageId);

		return getDatabase().queryForList("""
				SELECT *
				FROM (
				  SELECT
				    raw.*
				  FROM
				    v_image raw
				  WHERE
				    raw.institution_id=?
				    AND raw.image_id=?
				    AND raw.file_upload_status_id=?
				    AND raw.file_upload_type_id=?
				  UNION ALL
				  SELECT
				    crop.*
				  FROM
				    v_image crop
				  WHERE
				    crop.institution_id=?
				    AND crop.source_image_id=?
				    AND crop.file_upload_status_id=?
				    AND crop.file_upload_type_id IN (?,?,?)
				  UNION ALL
				  SELECT
				    thumbnail.*
				  FROM
				    v_image thumbnail
				    JOIN v_image crop ON crop.image_id=thumbnail.source_image_id
				  WHERE
				    crop.institution_id=?
				    AND thumbnail.institution_id=crop.institution_id
				    AND crop.source_image_id=?
				    AND crop.file_upload_status_id=?
				    AND thumbnail.file_upload_status_id=?
				    AND crop.file_upload_type_id IN (?,?,?)
				    AND thumbnail.file_upload_type_id IN (?,?,?)
				) media_images
				WHERE image_id<>?
				ORDER BY
				  CASE file_upload_type_id
				    WHEN 'IMAGE_RAW' THEN 1
				    WHEN 'IMAGE_16X9' THEN 2
				    WHEN 'IMAGE_4X3' THEN 3
				    WHEN 'IMAGE_1X1' THEN 4
				    WHEN 'IMAGE_THUMBNAIL_16X9' THEN 5
				    WHEN 'IMAGE_THUMBNAIL_4X3' THEN 6
				    WHEN 'IMAGE_THUMBNAIL_1X1' THEN 7
				    ELSE 8
				  END,
				  image_id
				""", Image.class,
				account.getInstitutionId(),
				sourceImageId,
				FileUploadStatusId.UPLOADED,
				FileUploadTypeId.IMAGE_RAW,
				account.getInstitutionId(),
				sourceImageId,
				FileUploadStatusId.UPLOADED,
				FileUploadTypeId.IMAGE_4X3,
				FileUploadTypeId.IMAGE_16X9,
				FileUploadTypeId.IMAGE_1X1,
				account.getInstitutionId(),
				sourceImageId,
				FileUploadStatusId.UPLOADED,
				FileUploadStatusId.UPLOADED,
				FileUploadTypeId.IMAGE_4X3,
				FileUploadTypeId.IMAGE_16X9,
				FileUploadTypeId.IMAGE_1X1,
				FileUploadTypeId.IMAGE_THUMBNAIL_4X3,
				FileUploadTypeId.IMAGE_THUMBNAIL_16X9,
				FileUploadTypeId.IMAGE_THUMBNAIL_1X1,
				excludedImageId);
	}

	@Nonnull
	protected Map<UUID, List<MediaImageVariant>> findMediaImageVariantsBySourceImageIds(@Nonnull Account account,
																																										 @Nonnull List<UUID> sourceImageIds) {
		requireNonNull(account);
		requireNonNull(sourceImageIds);

		if (sourceImageIds.size() == 0)
			return Map.of();

		String sourceImageIdPlaceholders = sqlInListPlaceholders(sourceImageIds);
		List<Object> parameters = new ArrayList<>();

		parameters.add(account.getInstitutionId());
		parameters.addAll(sourceImageIds);
		parameters.add(FileUploadStatusId.UPLOADED);
		parameters.add(FileUploadTypeId.IMAGE_RAW);

		parameters.add(account.getInstitutionId());
		parameters.addAll(sourceImageIds);
		parameters.add(FileUploadStatusId.UPLOADED);
		parameters.add(FileUploadTypeId.IMAGE_4X3);
		parameters.add(FileUploadTypeId.IMAGE_16X9);
		parameters.add(FileUploadTypeId.IMAGE_1X1);

		parameters.add(account.getInstitutionId());
		parameters.addAll(sourceImageIds);
		parameters.add(FileUploadStatusId.UPLOADED);
		parameters.add(FileUploadStatusId.UPLOADED);
		parameters.add(FileUploadTypeId.IMAGE_THUMBNAIL_4X3);
		parameters.add(FileUploadTypeId.IMAGE_THUMBNAIL_16X9);
		parameters.add(FileUploadTypeId.IMAGE_THUMBNAIL_1X1);

		List<MediaImageVariantRow> mediaImageVariantRows = getDatabase().queryForList(format("""
				SELECT *
				FROM (
				  SELECT
				    raw.image_id AS gallery_source_image_id,
				    raw.image_id,
				    raw.source_image_id,
				    raw.file_upload_type_id
				  FROM
				    v_image raw
				  WHERE
				    raw.institution_id=?
				    AND raw.image_id IN %s
				    AND raw.file_upload_status_id=?
				    AND raw.file_upload_type_id=?
				  UNION ALL
				  SELECT
				    crop.source_image_id AS gallery_source_image_id,
				    crop.image_id,
				    crop.source_image_id,
				    crop.file_upload_type_id
				  FROM
				    v_image crop
				  WHERE
				    crop.institution_id=?
				    AND crop.source_image_id IN %s
				    AND crop.file_upload_status_id=?
				    AND crop.file_upload_type_id IN (?,?,?)
				  UNION ALL
				  SELECT
				    crop.source_image_id AS gallery_source_image_id,
				    thumbnail.image_id,
				    thumbnail.source_image_id,
				    thumbnail.file_upload_type_id
				  FROM
				    v_image thumbnail
				    JOIN v_image crop ON crop.image_id=thumbnail.source_image_id
				  WHERE
				    crop.institution_id=?
				    AND thumbnail.institution_id=crop.institution_id
				    AND crop.source_image_id IN %s
				    AND crop.file_upload_status_id=?
				    AND thumbnail.file_upload_status_id=?
				    AND thumbnail.file_upload_type_id IN (?,?,?)
				) media_image_variants
				ORDER BY
				  gallery_source_image_id,
				  CASE file_upload_type_id
				    WHEN 'IMAGE_RAW' THEN 1
				    WHEN 'IMAGE_16X9' THEN 2
				    WHEN 'IMAGE_4X3' THEN 3
				    WHEN 'IMAGE_1X1' THEN 4
				    WHEN 'IMAGE_THUMBNAIL_16X9' THEN 5
				    WHEN 'IMAGE_THUMBNAIL_4X3' THEN 6
				    WHEN 'IMAGE_THUMBNAIL_1X1' THEN 7
				    ELSE 8
				  END
				""", sourceImageIdPlaceholders, sourceImageIdPlaceholders, sourceImageIdPlaceholders),
				MediaImageVariantRow.class, parameters.toArray());

		Map<UUID, List<MediaImageVariant>> mediaImageVariantsBySourceImageId = new LinkedHashMap<>();

		for (MediaImageVariantRow mediaImageVariantRow : mediaImageVariantRows) {
			List<MediaImageVariant> mediaImageVariants = mediaImageVariantsBySourceImageId.get(mediaImageVariantRow.getGallerySourceImageId());

			if (mediaImageVariants == null) {
				mediaImageVariants = new ArrayList<>();
				mediaImageVariantsBySourceImageId.put(mediaImageVariantRow.getGallerySourceImageId(), mediaImageVariants);
			}

			mediaImageVariants.add(new MediaImageVariant(
					mediaImageVariantRow.getImageId(),
					mediaImageVariantRow.getSourceImageId(),
					mediaImageVariantRow.getFileUploadTypeId(),
					imageTypeForFileUploadTypeId(mediaImageVariantRow.getFileUploadTypeId()),
					aspectRatioForFileUploadTypeId(mediaImageVariantRow.getFileUploadTypeId()),
					MEDIA_IMAGE_THUMBNAIL_FILE_UPLOAD_TYPE_IDS.contains(mediaImageVariantRow.getFileUploadTypeId())
			));
		}

		return mediaImageVariantsBySourceImageId;
	}

	@Nonnull
	protected ImageType imageTypeForFileUploadTypeId(@Nonnull FileUploadTypeId fileUploadTypeId) {
		requireNonNull(fileUploadTypeId);

		if (fileUploadTypeId == FileUploadTypeId.IMAGE_RAW)
			return ImageType.RAW;

		if (MEDIA_IMAGE_CROP_FILE_UPLOAD_TYPE_IDS.contains(fileUploadTypeId))
			return ImageType.CROP;

		if (MEDIA_IMAGE_THUMBNAIL_FILE_UPLOAD_TYPE_IDS.contains(fileUploadTypeId))
			return ImageType.THUMBNAIL;

		throw new IllegalArgumentException(format("Unsupported media image file upload type ID '%s'.", fileUploadTypeId));
	}

	@Nullable
	protected String aspectRatioForFileUploadTypeId(@Nonnull FileUploadTypeId fileUploadTypeId) {
		requireNonNull(fileUploadTypeId);

		return switch (fileUploadTypeId) {
			case IMAGE_16X9, IMAGE_THUMBNAIL_16X9 -> "16x9";
			case IMAGE_4X3, IMAGE_THUMBNAIL_4X3 -> "4x3";
			case IMAGE_1X1, IMAGE_THUMBNAIL_1X1 -> "1x1";
			case IMAGE_RAW -> null;
			default -> throw new IllegalArgumentException(format("Unsupported media image file upload type ID '%s'.", fileUploadTypeId));
		};
	}

	@Nonnull
	protected SystemService getSystemService() {
		return this.systemServiceProvider.get();
	}

	@Nonnull
	protected Database getDatabase() {
		return this.databaseProvider.get();
	}

	@Nonnull
	protected UploadManager getUploadManager() {
		return this.uploadManager;
	}

	@Nonnull
	protected Strings getStrings() {
		return this.strings;
	}

	@NotThreadSafe
	protected static class MediaImageGalleryItemWithTotalCount extends Image {
		@Nullable
		private UUID gallerySourceImageId;
		@Nullable
		private Integer totalCount;

		@Nullable
		public UUID getGallerySourceImageId() {
			return this.gallerySourceImageId;
		}

		public void setGallerySourceImageId(@Nullable UUID gallerySourceImageId) {
			this.gallerySourceImageId = gallerySourceImageId;
		}

		@Nullable
		public Integer getTotalCount() {
			return this.totalCount;
		}

		public void setTotalCount(@Nullable Integer totalCount) {
			this.totalCount = totalCount;
		}
	}

	@NotThreadSafe
	protected static class MediaImageVariantRow {
		@Nullable
		private UUID gallerySourceImageId;
		@Nullable
		private UUID imageId;
		@Nullable
		private UUID sourceImageId;
		@Nullable
		private FileUploadTypeId fileUploadTypeId;

		@Nullable
		public UUID getGallerySourceImageId() {
			return this.gallerySourceImageId;
		}

		public void setGallerySourceImageId(@Nullable UUID gallerySourceImageId) {
			this.gallerySourceImageId = gallerySourceImageId;
		}

		@Nullable
		public UUID getImageId() {
			return this.imageId;
		}

		public void setImageId(@Nullable UUID imageId) {
			this.imageId = imageId;
		}

		@Nullable
		public UUID getSourceImageId() {
			return this.sourceImageId;
		}

		public void setSourceImageId(@Nullable UUID sourceImageId) {
			this.sourceImageId = sourceImageId;
		}

		@Nullable
		public FileUploadTypeId getFileUploadTypeId() {
			return this.fileUploadTypeId;
		}

		public void setFileUploadTypeId(@Nullable FileUploadTypeId fileUploadTypeId) {
			this.fileUploadTypeId = fileUploadTypeId;
		}
	}
}
