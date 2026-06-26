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
import com.cobaltplatform.api.model.service.FileUploadResult;
import com.cobaltplatform.api.model.service.MediaImageUploadResult;
import com.cobaltplatform.api.util.UploadManager;
import com.cobaltplatform.api.util.ValidationException;
import com.cobaltplatform.api.util.ValidationException.FieldError;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.lokalized.Strings;
import com.pyranid.Database;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
	private static final EnumSet<FileUploadTypeId> MEDIA_IMAGE_FILE_UPLOAD_TYPE_IDS;
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
}
