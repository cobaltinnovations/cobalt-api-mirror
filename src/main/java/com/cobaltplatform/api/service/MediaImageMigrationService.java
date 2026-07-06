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
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.FileUpload;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.service.FileUploadResult;
import com.cobaltplatform.api.util.UploadManager;
import com.cobaltplatform.api.util.ValidationException;
import com.cobaltplatform.api.util.ValidationException.FieldError;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Imports legacy image file uploads into the media image repository.
 * <p>
 * A legacy image is only attached to consumers after all requested crop and thumbnail variants pass quality gates.
 *
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class MediaImageMigrationService {
	@Nonnull
	private static final Map<FileUploadTypeId, CropSpec> CROP_SPECS_BY_FILE_UPLOAD_TYPE_ID;
	@Nonnull
	private final Provider<SystemService> systemServiceProvider;
	@Nonnull
	private final DatabaseProvider databaseProvider;
	@Nonnull
	private final UploadManager uploadManager;

	static {
		CROP_SPECS_BY_FILE_UPLOAD_TYPE_ID = new EnumMap<>(FileUploadTypeId.class);
		CROP_SPECS_BY_FILE_UPLOAD_TYPE_ID.put(FileUploadTypeId.IMAGE_16X9,
				new CropSpec(FileUploadTypeId.IMAGE_16X9, FileUploadTypeId.IMAGE_THUMBNAIL_16X9, 16, 9, 1280, 720, 320, 180));
		CROP_SPECS_BY_FILE_UPLOAD_TYPE_ID.put(FileUploadTypeId.IMAGE_4X3,
				new CropSpec(FileUploadTypeId.IMAGE_4X3, FileUploadTypeId.IMAGE_THUMBNAIL_4X3, 4, 3, 1200, 900, 240, 180));
		CROP_SPECS_BY_FILE_UPLOAD_TYPE_ID.put(FileUploadTypeId.IMAGE_1X1,
				new CropSpec(FileUploadTypeId.IMAGE_1X1, FileUploadTypeId.IMAGE_THUMBNAIL_1X1, 1, 1, 800, 800, 200, 200));
	}

	@Inject
	public MediaImageMigrationService(@Nonnull Provider<SystemService> systemServiceProvider,
																		@Nonnull DatabaseProvider databaseProvider,
																		@Nonnull UploadManager uploadManager) {
		requireNonNull(systemServiceProvider);
		requireNonNull(databaseProvider);
		requireNonNull(uploadManager);

		this.systemServiceProvider = systemServiceProvider;
		this.databaseProvider = databaseProvider;
		this.uploadManager = uploadManager;
	}

	@Nonnull
	public LegacyImageMigrationResult migrateLegacyContentImage(@Nonnull Account account,
																														 @Nonnull UUID contentId) {
		requireNonNull(account);
		requireNonNull(contentId);

		ContentLegacyImage contentLegacyImage = getDatabase().queryForObject("""
				SELECT content_id, image_id, image_file_upload_id
				FROM v_admin_content
				WHERE content_id=?
				AND owner_institution_id=?
				""", ContentLegacyImage.class, contentId, account.getInstitutionId()).orElse(null);

		if (contentLegacyImage == null)
			throw new ValidationException(new FieldError("contentId", "Content ID is invalid."));

		UUID legacyFileUploadId = contentLegacyImage.getImageId() == null
				? contentLegacyImage.getImageFileUploadId()
				: findLegacyFileUploadIdForMigratedCrop(account, contentLegacyImage.getImageId(), FileUploadTypeId.IMAGE_16X9)
				.orElse(contentLegacyImage.getImageFileUploadId());

		if (legacyFileUploadId == null)
			throw new ValidationException(new FieldError("imageFileUploadId", "Content has no legacy image."));

		LegacyImageMigrationResult migrationResult = migrateLegacyImageFileUpload(account, legacyFileUploadId,
				Set.of(FileUploadTypeId.IMAGE_16X9), null);

		if (migrationResult.getMigrationStatusId() == LegacyImageMigrationStatusId.VARIANTS_GENERATED) {
			UUID cropImageId = migrationResult.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);
			UUID cropFileUploadId = migrationResult.getCropFileUploadIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);

			getDatabase().execute("""
					UPDATE content
					SET image_id=?,
					    image_file_upload_id=?
					WHERE content_id=?
					AND owner_institution_id=?
					""", cropImageId, cropFileUploadId, contentId, account.getInstitutionId());
		}

		return migrationResult;
	}

	@Nonnull
	public LegacyImageMigrationResult migrateLegacyGroupSessionImage(@Nonnull Account account,
																																	 @Nonnull UUID groupSessionId) {
		requireNonNull(account);
		requireNonNull(groupSessionId);

		GroupSessionLegacyImage groupSessionLegacyImage = getDatabase().queryForObject("""
				SELECT group_session_id, image_id, image_file_upload_id
				FROM v_group_session
				WHERE group_session_id=?
				AND institution_id=?
				""", GroupSessionLegacyImage.class, groupSessionId, account.getInstitutionId()).orElse(null);

		if (groupSessionLegacyImage == null)
			throw new ValidationException(new FieldError("groupSessionId", "Group Session ID is invalid."));

		UUID legacyFileUploadId = groupSessionLegacyImage.getImageId() == null
				? groupSessionLegacyImage.getImageFileUploadId()
				: findLegacyFileUploadIdForMigratedCrop(account, groupSessionLegacyImage.getImageId(), FileUploadTypeId.IMAGE_16X9)
				.orElse(groupSessionLegacyImage.getImageFileUploadId());

		if (legacyFileUploadId == null)
			throw new ValidationException(new FieldError("imageFileUploadId", "Group Session has no legacy image."));

		LegacyImageMigrationResult migrationResult = migrateLegacyImageFileUpload(account, legacyFileUploadId,
				Set.of(FileUploadTypeId.IMAGE_16X9), null);

		if (migrationResult.getMigrationStatusId() == LegacyImageMigrationStatusId.VARIANTS_GENERATED) {
			UUID cropImageId = migrationResult.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);
			UUID cropFileUploadId = migrationResult.getCropFileUploadIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);

			getDatabase().execute("""
					UPDATE group_session
					SET image_id=?,
					    image_file_upload_id=?
					WHERE group_session_id=?
					""", cropImageId, cropFileUploadId, groupSessionId);
		}

		return migrationResult;
	}

	@Nonnull
	protected Optional<UUID> findLegacyFileUploadIdForMigratedCrop(@Nonnull Account account,
																																 @Nonnull UUID imageId) {
		return findLegacyFileUploadIdForMigratedCrop(account, imageId, FileUploadTypeId.IMAGE_16X9);
	}

	@Nonnull
	protected Optional<UUID> findLegacyFileUploadIdForMigratedCrop(@Nonnull Account account,
																																 @Nonnull UUID imageId,
																																 @Nonnull FileUploadTypeId cropFileUploadTypeId) {
		requireNonNull(account);
		requireNonNull(imageId);
		requireNonNull(cropFileUploadTypeId);

		return getDatabase().queryForObject(format("""
				SELECT legacy_file_upload_id
				FROM legacy_image_migration
				WHERE institution_id=?
				AND %s=?
				""", legacyMigrationCropImageIdColumnName(cropFileUploadTypeId)), UUID.class, account.getInstitutionId(), imageId);
	}

	@Nonnull
	public LegacyImageMigrationInstitutionReport findLegacyImageMigrationReport(@Nonnull InstitutionId institutionId) {
		requireNonNull(institutionId);

		return getDatabase().queryForObject("""
				WITH content_refs AS (
				  SELECT
				    'CONTENT' AS reference_type_id,
				    c.content_id AS reference_id,
				    c.image_file_upload_id AS legacy_file_upload_id,
				    c.created
				  FROM content c
				  JOIN file_upload fu ON fu.file_upload_id=c.image_file_upload_id
				  WHERE c.owner_institution_id=?
				  AND c.deleted_flag=FALSE
				  AND fu.file_upload_type_id=?
				), group_session_refs AS (
				  SELECT
				    'GROUP_SESSION' AS reference_type_id,
				    gs.group_session_id AS reference_id,
				    gs.image_file_upload_id AS legacy_file_upload_id,
				    gs.created
				  FROM group_session gs
				  JOIN file_upload fu ON fu.file_upload_id=gs.image_file_upload_id
				  WHERE gs.institution_id=?
				  AND fu.file_upload_type_id=?
				  AND gs.group_session_status_id<>'DELETED'
				), legacy_refs AS (
				  SELECT * FROM content_refs
				  UNION ALL
				  SELECT * FROM group_session_refs
				), legacy_candidates AS (
				  SELECT DISTINCT legacy_file_upload_id
				  FROM legacy_refs
				), audited AS (
				  SELECT DISTINCT lim.legacy_file_upload_id
				  FROM legacy_image_migration lim
				  JOIN file_upload fu ON fu.file_upload_id=lim.legacy_file_upload_id
				  WHERE lim.institution_id=?
				  AND fu.file_upload_type_id IN (?,?)
				), migration_scope AS (
				  SELECT legacy_file_upload_id FROM legacy_candidates
				  UNION
				  SELECT legacy_file_upload_id FROM audited
				), pending_refs AS (
				  SELECT lr.*
				  FROM legacy_refs lr
				  LEFT JOIN legacy_image_migration lim ON lim.legacy_file_upload_id=lr.legacy_file_upload_id
				  WHERE lim.legacy_file_upload_id IS NULL
				  OR lim.legacy_image_migration_status_id IN ('RAW_IMPORTED','VARIANTS_GENERATED')
				)
				SELECT
				  i.institution_id,
				  i.image_repository_enabled,
				  (SELECT COUNT(*) FROM migration_scope) AS total_count,
				  (SELECT COUNT(*) FROM legacy_refs) AS current_legacy_reference_count,
				  (SELECT COUNT(*) FROM content_refs) AS current_legacy_content_reference_count,
				  (SELECT COUNT(*) FROM group_session_refs) AS current_legacy_group_session_reference_count,
				  (SELECT COUNT(*) FROM pending_refs) AS pending_count,
				  (SELECT COUNT(*) FROM pending_refs WHERE reference_type_id='CONTENT') AS pending_content_reference_count,
				  (SELECT COUNT(*) FROM pending_refs WHERE reference_type_id='GROUP_SESSION') AS pending_group_session_reference_count,
				  (SELECT COUNT(*)
				   FROM legacy_image_migration lim
				   JOIN migration_scope ms ON ms.legacy_file_upload_id=lim.legacy_file_upload_id) AS attempted_count,
				  (SELECT COUNT(*)
				   FROM legacy_image_migration lim
				   JOIN migration_scope ms ON ms.legacy_file_upload_id=lim.legacy_file_upload_id
				   WHERE lim.legacy_image_migration_status_id='RAW_IMPORTED') AS raw_imported_count,
				  (SELECT COUNT(*)
				   FROM legacy_image_migration lim
				   JOIN migration_scope ms ON ms.legacy_file_upload_id=lim.legacy_file_upload_id
				   WHERE lim.legacy_image_migration_status_id='VARIANTS_GENERATED') AS variants_generated_count,
				  (SELECT COUNT(*)
				   FROM legacy_image_migration lim
				   JOIN migration_scope ms ON ms.legacy_file_upload_id=lim.legacy_file_upload_id
				   WHERE lim.legacy_image_migration_status_id='NEEDS_REVIEW') AS needs_review_count,
				  (SELECT COUNT(*)
				   FROM legacy_image_migration lim
				   JOIN migration_scope ms ON ms.legacy_file_upload_id=lim.legacy_file_upload_id
				   WHERE lim.legacy_image_migration_status_id='LOW_FIDELITY') AS low_fidelity_count,
				  (SELECT COUNT(*)
				   FROM legacy_image_migration lim
				   JOIN migration_scope ms ON ms.legacy_file_upload_id=lim.legacy_file_upload_id
				   WHERE lim.legacy_image_migration_status_id='UNMIGRATABLE') AS unmigratable_count,
				  (SELECT COUNT(*)
				   FROM legacy_image_migration lim
				   JOIN migration_scope ms ON ms.legacy_file_upload_id=lim.legacy_file_upload_id
				   WHERE lim.legacy_image_migration_status_id='REPLACED') AS replaced_count,
				  (SELECT COUNT(*)
				   FROM content c
				   JOIN legacy_image_migration lim ON lim.crop_16x9_image_id=c.image_id
				   JOIN migration_scope ms ON ms.legacy_file_upload_id=lim.legacy_file_upload_id
				   WHERE c.owner_institution_id=?
				   AND c.deleted_flag=FALSE) AS rewired_content_count,
				  (SELECT COUNT(*)
				   FROM group_session gs
				   JOIN legacy_image_migration lim ON lim.crop_16x9_image_id=gs.image_id
				   JOIN migration_scope ms ON ms.legacy_file_upload_id=lim.legacy_file_upload_id
				   WHERE gs.institution_id=?
				   AND gs.group_session_status_id<>'DELETED') AS rewired_group_session_count
				FROM institution i
				WHERE i.institution_id=?
				""", LegacyImageMigrationInstitutionReport.class,
				institutionId, FileUploadTypeId.CONTENT_IMAGE,
				institutionId, FileUploadTypeId.GROUP_SESSION_IMAGE,
				institutionId, FileUploadTypeId.CONTENT_IMAGE, FileUploadTypeId.GROUP_SESSION_IMAGE,
				institutionId, institutionId, institutionId).orElseThrow(() -> new ValidationException(new FieldError("institutionId", "Institution ID is invalid.")));
	}

	@Nonnull
	public LegacyImageMigrationInstitutionReport findLegacyGroupSessionImageMigrationReport(@Nonnull InstitutionId institutionId) {
		return findLegacyImageMigrationReport(institutionId);
	}

	@Nonnull
	public List<LegacyImageReference> findPendingLegacyImageReferencesForInstitution(@Nonnull InstitutionId institutionId,
																																								 @Nonnull Integer limit) {
		requireNonNull(institutionId);
		requireNonNull(limit);

		if (limit < 1)
			throw new ValidationException(new FieldError("limit", "Limit must be at least 1."));

		return getDatabase().queryForList("""
				WITH content_refs AS (
				  SELECT
				    'CONTENT' AS reference_type_id,
				    c.content_id AS reference_id,
				    c.image_file_upload_id AS legacy_file_upload_id,
				    c.created
				  FROM content c
				  JOIN file_upload fu ON fu.file_upload_id=c.image_file_upload_id
				  WHERE c.owner_institution_id=?
				  AND c.deleted_flag=FALSE
				  AND fu.file_upload_type_id=?
				), group_session_refs AS (
				  SELECT
				    'GROUP_SESSION' AS reference_type_id,
				    gs.group_session_id AS reference_id,
				    gs.image_file_upload_id AS legacy_file_upload_id,
				    gs.created
				  FROM group_session gs
				  JOIN file_upload fu ON fu.file_upload_id=gs.image_file_upload_id
				  WHERE gs.institution_id=?
				  AND fu.file_upload_type_id=?
				  AND gs.group_session_status_id<>'DELETED'
				), legacy_refs AS (
				  SELECT * FROM content_refs
				  UNION ALL
				  SELECT * FROM group_session_refs
				)
				SELECT
				  lr.reference_type_id,
				  lr.reference_id,
				  lr.legacy_file_upload_id
				FROM legacy_refs lr
				LEFT JOIN legacy_image_migration lim ON lim.legacy_file_upload_id=lr.legacy_file_upload_id
				WHERE lim.legacy_file_upload_id IS NULL
				OR lim.legacy_image_migration_status_id IN ('RAW_IMPORTED','VARIANTS_GENERATED')
				ORDER BY lr.created, lr.reference_type_id, lr.reference_id
				LIMIT ?
				""", LegacyImageReference.class,
				institutionId, FileUploadTypeId.CONTENT_IMAGE,
				institutionId, FileUploadTypeId.GROUP_SESSION_IMAGE,
				limit);
	}

	@Nonnull
	public List<UUID> findPendingLegacyGroupSessionImageIdsForInstitution(@Nonnull InstitutionId institutionId,
																																			 @Nonnull Integer limit) {
		requireNonNull(institutionId);
		requireNonNull(limit);

		if (limit < 1)
			throw new ValidationException(new FieldError("limit", "Limit must be at least 1."));

		return getDatabase().queryForList("""
				SELECT gs.group_session_id
				FROM group_session gs
				JOIN file_upload fu ON fu.file_upload_id=gs.image_file_upload_id
				LEFT JOIN legacy_image_migration lim ON lim.legacy_file_upload_id=gs.image_file_upload_id
				WHERE gs.institution_id=?
				AND fu.file_upload_type_id=?
				AND (
				  lim.legacy_file_upload_id IS NULL
				  OR lim.legacy_image_migration_status_id IN ('RAW_IMPORTED','VARIANTS_GENERATED')
				)
				ORDER BY gs.created, gs.group_session_id
				LIMIT ?
				""", UUID.class, institutionId, FileUploadTypeId.GROUP_SESSION_IMAGE, limit);
	}

	@Nonnull
	public LegacyImageMigrationBatchResult migratePendingLegacyImagesForInstitution(@Nonnull Account account,
																																								 @Nonnull Integer limit) {
		requireNonNull(account);
		requireNonNull(limit);

		LegacyImageMigrationInstitutionReport beforeReport = findLegacyImageMigrationReport(account.getInstitutionId());
		List<LegacyImageReference> legacyImageReferences = findPendingLegacyImageReferencesForInstitution(account.getInstitutionId(), limit);
		List<LegacyImageReferenceMigrationResult> migrationResults = new ArrayList<>(legacyImageReferences.size());

		for (LegacyImageReference legacyImageReference : legacyImageReferences) {
			LegacyImageMigrationResult migrationResult = migrateLegacyImageReference(account, legacyImageReference);
			migrationResults.add(new LegacyImageReferenceMigrationResult(legacyImageReference, migrationResult));
		}

		LegacyImageMigrationInstitutionReport afterReport = findLegacyImageMigrationReport(account.getInstitutionId());
		return new LegacyImageMigrationBatchResult(account.getInstitutionId(), limit, beforeReport, afterReport, migrationResults);
	}

	@Nonnull
	public LegacyImageMigrationBatchResult migratePendingLegacyGroupSessionImagesForInstitution(@Nonnull Account account,
																																															 @Nonnull Integer limit) {
		requireNonNull(account);
		requireNonNull(limit);

		LegacyImageMigrationInstitutionReport beforeReport = findLegacyImageMigrationReport(account.getInstitutionId());
		List<UUID> groupSessionIds = findPendingLegacyGroupSessionImageIdsForInstitution(account.getInstitutionId(), limit);
		List<LegacyImageReferenceMigrationResult> migrationResults = new ArrayList<>(groupSessionIds.size());

		for (UUID groupSessionId : groupSessionIds) {
			LegacyImageReference legacyImageReference = new LegacyImageReference(LegacyImageMigrationReferenceTypeId.GROUP_SESSION, groupSessionId, null);
			LegacyImageMigrationResult migrationResult = migrateLegacyGroupSessionImage(account, groupSessionId);
			migrationResults.add(new LegacyImageReferenceMigrationResult(legacyImageReference, migrationResult));
		}

		LegacyImageMigrationInstitutionReport afterReport = findLegacyImageMigrationReport(account.getInstitutionId());
		return new LegacyImageMigrationBatchResult(account.getInstitutionId(), limit, beforeReport, afterReport, migrationResults);
	}

	@Nonnull
	protected LegacyImageMigrationResult migrateLegacyImageReference(@Nonnull Account account,
																																	 @Nonnull LegacyImageReference legacyImageReference) {
		requireNonNull(account);
		requireNonNull(legacyImageReference);

		if (legacyImageReference.getReferenceTypeId() == LegacyImageMigrationReferenceTypeId.CONTENT)
			return migrateLegacyContentImage(account, legacyImageReference.getReferenceId());

		if (legacyImageReference.getReferenceTypeId() == LegacyImageMigrationReferenceTypeId.GROUP_SESSION)
			return migrateLegacyGroupSessionImage(account, legacyImageReference.getReferenceId());

		throw new IllegalArgumentException(format("Unsupported legacy image reference type ID '%s'.", legacyImageReference.getReferenceTypeId()));
	}

	@Nonnull
	public LegacyImageMigrationResult migrateLegacyImageFileUpload(@Nonnull Account account,
																																 @Nonnull UUID legacyFileUploadId,
																																 @Nonnull Set<FileUploadTypeId> requiredCropFileUploadTypeIds,
																																 @Nullable String imageAltText) {
		requireNonNull(account);
		requireNonNull(legacyFileUploadId);
		requireNonNull(requiredCropFileUploadTypeIds);

		EnumSet<FileUploadTypeId> requestedCropFileUploadTypeIds = normalizeRequestedCropFileUploadTypeIds(requiredCropFileUploadTypeIds);
		LegacyImageMigrationResult[] migrationResult = new LegacyImageMigrationResult[1];

		Runnable migration = () -> migrationResult[0] = migrateLegacyImageFileUploadInternal(account, legacyFileUploadId,
				requestedCropFileUploadTypeIds, imageAltText);

		if (getDatabase().currentTransaction().isPresent())
			migration.run();
		else
			getDatabase().transaction(() -> migration.run());

		return migrationResult[0];
	}

	@Nonnull
	protected LegacyImageMigrationResult migrateLegacyImageFileUploadInternal(@Nonnull Account account,
																																						@Nonnull UUID legacyFileUploadId,
																																						@Nonnull EnumSet<FileUploadTypeId> requestedCropFileUploadTypeIds,
																																						@Nullable String imageAltText) {
		requireNonNull(account);
		requireNonNull(legacyFileUploadId);
		requireNonNull(requestedCropFileUploadTypeIds);

		FileUpload legacyFileUpload = getSystemService().findFileUploadById(legacyFileUploadId).orElse(null);

		if (legacyFileUpload == null)
			throw new ValidationException(new FieldError("legacyFileUploadId", "Legacy File Upload ID is invalid."));

		if (legacyFileUpload.getInstitutionId() != account.getInstitutionId())
			throw new ValidationException(new FieldError("legacyFileUploadId", "Legacy File Upload ID is invalid."));

		String legacyStorageKey = trimToNull(legacyFileUpload.getStorageKey());
		String legacyContentType = normalizeLegacyImageContentType(legacyFileUpload).orElse(null);

		if (legacyStorageKey == null)
			return recordUnmigratable(account, legacyFileUpload, "Legacy image has no storage key.");

		if (legacyContentType == null)
			return recordUnmigratable(account, legacyFileUpload, "Legacy file upload is not an image.");

		byte[] sourceImageData;

		try {
			sourceImageData = getSystemService().downloadFileUploadToByteArray(legacyFileUploadId);
		} catch (RuntimeException e) {
			return recordUnmigratable(account, legacyFileUpload, e.getMessage());
		}

		BufferedImage sourceImage = decodeImage(sourceImageData).orElse(null);

		if (sourceImage == null)
			return recordUnmigratable(account, legacyFileUpload, "Legacy file upload could not be decoded as an image.");

		String imageHash = sha256Hex(sourceImageData);
		String normalizedImageAltText = trimToNull(imageAltText);
		ExistingLegacyImageMigration existingLegacyImageMigration = findExistingLegacyImageMigration(account, legacyFileUploadId).orElse(null);
		UploadedImage rawUploadedImage = existingLegacyImageMigration == null || existingLegacyImageMigration.getRawImageId() == null
				|| existingLegacyImageMigration.getRawFileUploadId() == null
				? createUploadedImage(account, legacyFileUpload, FileUploadTypeId.IMAGE_RAW, null,
				sourceImageData, legacyContentType, legacyFilename(legacyFileUpload, FileUploadTypeId.IMAGE_RAW),
				sourceImage.getWidth(), sourceImage.getHeight(), normalizedImageAltText, imageHash)
				: new UploadedImage(existingLegacyImageMigration.getRawImageId(), existingLegacyImageMigration.getRawFileUploadId());

		EnumMap<FileUploadTypeId, UUID> cropImageIdsByFileUploadTypeId = new EnumMap<>(FileUploadTypeId.class);
		EnumMap<FileUploadTypeId, UUID> cropFileUploadIdsByFileUploadTypeId = new EnumMap<>(FileUploadTypeId.class);
		EnumMap<FileUploadTypeId, UUID> thumbnailImageIdsByCropFileUploadTypeId = new EnumMap<>(FileUploadTypeId.class);
		EnumMap<FileUploadTypeId, UUID> thumbnailFileUploadIdsByCropFileUploadTypeId = new EnumMap<>(FileUploadTypeId.class);
		List<String> qualityMessages = new ArrayList<>();

		if (existingLegacyImageMigration != null)
			seedExistingVariants(existingLegacyImageMigration, cropImageIdsByFileUploadTypeId, cropFileUploadIdsByFileUploadTypeId,
					thumbnailImageIdsByCropFileUploadTypeId, thumbnailFileUploadIdsByCropFileUploadTypeId);

		for (FileUploadTypeId cropFileUploadTypeId : requestedCropFileUploadTypeIds) {
			if (cropImageIdsByFileUploadTypeId.containsKey(cropFileUploadTypeId)
					&& thumbnailImageIdsByCropFileUploadTypeId.containsKey(cropFileUploadTypeId))
				continue;

			CropSpec cropSpec = CROP_SPECS_BY_FILE_UPLOAD_TYPE_ID.get(cropFileUploadTypeId);
			CropWindow cropWindow = cropWindowFor(sourceImage.getWidth(), sourceImage.getHeight(), cropSpec);

			if (cropWindow.getWidth() < cropSpec.getMinimumWidth() || cropWindow.getHeight() < cropSpec.getMinimumHeight()) {
				qualityMessages.add(format("%s requires at least %dx%d; source can provide only %dx%d after crop.",
						cropFileUploadTypeId.name(), cropSpec.getMinimumWidth(), cropSpec.getMinimumHeight(),
						cropWindow.getWidth(), cropWindow.getHeight()));
				continue;
			}

			BufferedImage cropImage = crop(sourceImage, cropWindow);
			byte[] cropImageData = encodeJpeg(cropImage);
			UploadedImage cropUploadedImage = createUploadedImage(account, legacyFileUpload, cropFileUploadTypeId,
					rawUploadedImage.getImageId(), cropImageData, "image/jpeg", legacyFilename(legacyFileUpload, cropFileUploadTypeId),
					cropWindow.getWidth(), cropWindow.getHeight(), normalizedImageAltText, null);

			BufferedImage thumbnailImage = resize(cropImage, cropSpec.getThumbnailWidth(), cropSpec.getThumbnailHeight());
			byte[] thumbnailImageData = encodeJpeg(thumbnailImage);
			UploadedImage thumbnailUploadedImage = createUploadedImage(account, legacyFileUpload, cropSpec.getThumbnailFileUploadTypeId(),
					cropUploadedImage.getImageId(), thumbnailImageData, "image/jpeg", legacyFilename(legacyFileUpload, cropSpec.getThumbnailFileUploadTypeId()),
					cropSpec.getThumbnailWidth(), cropSpec.getThumbnailHeight(), normalizedImageAltText, null);

			cropImageIdsByFileUploadTypeId.put(cropFileUploadTypeId, cropUploadedImage.getImageId());
			cropFileUploadIdsByFileUploadTypeId.put(cropFileUploadTypeId, cropUploadedImage.getFileUploadId());
			thumbnailImageIdsByCropFileUploadTypeId.put(cropFileUploadTypeId, thumbnailUploadedImage.getImageId());
			thumbnailFileUploadIdsByCropFileUploadTypeId.put(cropFileUploadTypeId, thumbnailUploadedImage.getFileUploadId());
		}

		LegacyImageMigrationStatusId migrationStatusId = requestedCropFileUploadTypeIds.size() == 0
				? LegacyImageMigrationStatusId.RAW_IMPORTED
				: cropImageIdsByFileUploadTypeId.keySet().containsAll(requestedCropFileUploadTypeIds)
				&& thumbnailImageIdsByCropFileUploadTypeId.keySet().containsAll(requestedCropFileUploadTypeIds)
				? LegacyImageMigrationStatusId.VARIANTS_GENERATED
				: LegacyImageMigrationStatusId.LOW_FIDELITY;

		LegacyImageMigrationResult result = new LegacyImageMigrationResult(migrationStatusId, legacyFileUploadId,
				rawUploadedImage.getImageId(), rawUploadedImage.getFileUploadId(), cropImageIdsByFileUploadTypeId,
				cropFileUploadIdsByFileUploadTypeId, thumbnailImageIdsByCropFileUploadTypeId,
				thumbnailFileUploadIdsByCropFileUploadTypeId, sourceImage.getWidth(), sourceImage.getHeight(),
				imageHash, qualityMessages);

		upsertLegacyImageMigration(account, legacyFileUpload, result, null);
		return result;
	}

	@Nonnull
	protected Optional<ExistingLegacyImageMigration> findExistingLegacyImageMigration(@Nonnull Account account,
																																									 @Nonnull UUID legacyFileUploadId) {
		requireNonNull(account);
		requireNonNull(legacyFileUploadId);

		ExistingLegacyImageMigration existingLegacyImageMigration = getDatabase().queryForObject("""
				SELECT
				  lim.legacy_image_migration_status_id,
				  lim.source_width,
				  lim.source_height,
				  lim.source_image_hash,
				  lim.raw_image_id,
				  raw_image.file_upload_id AS raw_file_upload_id
				FROM legacy_image_migration lim
				LEFT JOIN image raw_image ON raw_image.image_id=lim.raw_image_id
				WHERE lim.legacy_file_upload_id=?
				AND lim.institution_id=?
				""", ExistingLegacyImageMigration.class, legacyFileUploadId, account.getInstitutionId()).orElse(null);

		if (existingLegacyImageMigration == null)
			return Optional.empty();

		hydrateExistingVariant(account, legacyFileUploadId, FileUploadTypeId.IMAGE_16X9, "crop_16x9_image_id",
				"thumbnail_16x9_image_id", existingLegacyImageMigration);
		hydrateExistingVariant(account, legacyFileUploadId, FileUploadTypeId.IMAGE_4X3, "crop_4x3_image_id",
				"thumbnail_4x3_image_id", existingLegacyImageMigration);
		hydrateExistingVariant(account, legacyFileUploadId, FileUploadTypeId.IMAGE_1X1, "crop_1x1_image_id",
				"thumbnail_1x1_image_id", existingLegacyImageMigration);

		return Optional.of(existingLegacyImageMigration);
	}

	protected void hydrateExistingVariant(@Nonnull Account account,
																				@Nonnull UUID legacyFileUploadId,
																				@Nonnull FileUploadTypeId cropFileUploadTypeId,
																				@Nonnull String cropImageIdColumnName,
																				@Nonnull String thumbnailImageIdColumnName,
																				@Nonnull ExistingLegacyImageMigration existingLegacyImageMigration) {
		requireNonNull(account);
		requireNonNull(legacyFileUploadId);
		requireNonNull(cropFileUploadTypeId);
		requireNonNull(cropImageIdColumnName);
		requireNonNull(thumbnailImageIdColumnName);
		requireNonNull(existingLegacyImageMigration);

		UUID cropImageId = findExistingMigrationImageId(account, legacyFileUploadId, cropImageIdColumnName).orElse(null);
		UUID cropFileUploadId = cropImageId == null ? null : findFileUploadIdForImageId(cropImageId).orElse(null);
		UUID thumbnailImageId = findExistingMigrationImageId(account, legacyFileUploadId, thumbnailImageIdColumnName).orElse(null);
		UUID thumbnailFileUploadId = thumbnailImageId == null ? null : findFileUploadIdForImageId(thumbnailImageId).orElse(null);

		if (cropFileUploadTypeId == FileUploadTypeId.IMAGE_16X9) {
			existingLegacyImageMigration.setCrop16X9ImageId(cropImageId);
			existingLegacyImageMigration.setCrop16X9FileUploadId(cropFileUploadId);
			existingLegacyImageMigration.setThumbnail16X9ImageId(thumbnailImageId);
			existingLegacyImageMigration.setThumbnail16X9FileUploadId(thumbnailFileUploadId);
		} else if (cropFileUploadTypeId == FileUploadTypeId.IMAGE_4X3) {
			existingLegacyImageMigration.setCrop4X3ImageId(cropImageId);
			existingLegacyImageMigration.setCrop4X3FileUploadId(cropFileUploadId);
			existingLegacyImageMigration.setThumbnail4X3ImageId(thumbnailImageId);
			existingLegacyImageMigration.setThumbnail4X3FileUploadId(thumbnailFileUploadId);
		} else if (cropFileUploadTypeId == FileUploadTypeId.IMAGE_1X1) {
			existingLegacyImageMigration.setCrop1X1ImageId(cropImageId);
			existingLegacyImageMigration.setCrop1X1FileUploadId(cropFileUploadId);
			existingLegacyImageMigration.setThumbnail1X1ImageId(thumbnailImageId);
			existingLegacyImageMigration.setThumbnail1X1FileUploadId(thumbnailFileUploadId);
		} else {
			throw new IllegalArgumentException(format("Unsupported crop file upload type ID '%s'.", cropFileUploadTypeId));
		}
	}

	@Nonnull
	protected Optional<UUID> findExistingMigrationImageId(@Nonnull Account account,
																												@Nonnull UUID legacyFileUploadId,
																												@Nonnull String imageIdColumnName) {
		requireNonNull(account);
		requireNonNull(legacyFileUploadId);
		requireNonNull(imageIdColumnName);

		return getDatabase().queryForObject(format("""
				SELECT %s
				FROM legacy_image_migration
				WHERE legacy_file_upload_id=?
				AND institution_id=?
				""", imageIdColumnName), UUID.class, legacyFileUploadId, account.getInstitutionId());
	}

	@Nonnull
	protected Optional<UUID> findFileUploadIdForImageId(@Nonnull UUID imageId) {
		requireNonNull(imageId);

		return getDatabase().queryForObject("""
				SELECT file_upload_id
				FROM image
				WHERE image_id=?
				""", UUID.class, imageId);
	}

	protected void seedExistingVariants(@Nonnull ExistingLegacyImageMigration existingLegacyImageMigration,
																			@Nonnull EnumMap<FileUploadTypeId, UUID> cropImageIdsByFileUploadTypeId,
																			@Nonnull EnumMap<FileUploadTypeId, UUID> cropFileUploadIdsByFileUploadTypeId,
																			@Nonnull EnumMap<FileUploadTypeId, UUID> thumbnailImageIdsByCropFileUploadTypeId,
																			@Nonnull EnumMap<FileUploadTypeId, UUID> thumbnailFileUploadIdsByCropFileUploadTypeId) {
		requireNonNull(existingLegacyImageMigration);
		requireNonNull(cropImageIdsByFileUploadTypeId);
		requireNonNull(cropFileUploadIdsByFileUploadTypeId);
		requireNonNull(thumbnailImageIdsByCropFileUploadTypeId);
		requireNonNull(thumbnailFileUploadIdsByCropFileUploadTypeId);

		seedExistingVariant(FileUploadTypeId.IMAGE_16X9, existingLegacyImageMigration.getCrop16X9ImageId(),
				existingLegacyImageMigration.getCrop16X9FileUploadId(), existingLegacyImageMigration.getThumbnail16X9ImageId(),
				existingLegacyImageMigration.getThumbnail16X9FileUploadId(), cropImageIdsByFileUploadTypeId, cropFileUploadIdsByFileUploadTypeId,
				thumbnailImageIdsByCropFileUploadTypeId, thumbnailFileUploadIdsByCropFileUploadTypeId);
		seedExistingVariant(FileUploadTypeId.IMAGE_4X3, existingLegacyImageMigration.getCrop4X3ImageId(),
				existingLegacyImageMigration.getCrop4X3FileUploadId(), existingLegacyImageMigration.getThumbnail4X3ImageId(),
				existingLegacyImageMigration.getThumbnail4X3FileUploadId(), cropImageIdsByFileUploadTypeId, cropFileUploadIdsByFileUploadTypeId,
				thumbnailImageIdsByCropFileUploadTypeId, thumbnailFileUploadIdsByCropFileUploadTypeId);
		seedExistingVariant(FileUploadTypeId.IMAGE_1X1, existingLegacyImageMigration.getCrop1X1ImageId(),
				existingLegacyImageMigration.getCrop1X1FileUploadId(), existingLegacyImageMigration.getThumbnail1X1ImageId(),
				existingLegacyImageMigration.getThumbnail1X1FileUploadId(), cropImageIdsByFileUploadTypeId, cropFileUploadIdsByFileUploadTypeId,
				thumbnailImageIdsByCropFileUploadTypeId, thumbnailFileUploadIdsByCropFileUploadTypeId);
	}

	protected void seedExistingVariant(@Nonnull FileUploadTypeId cropFileUploadTypeId,
																		 @Nullable UUID cropImageId,
																		 @Nullable UUID cropFileUploadId,
																		 @Nullable UUID thumbnailImageId,
																		 @Nullable UUID thumbnailFileUploadId,
																		 @Nonnull EnumMap<FileUploadTypeId, UUID> cropImageIdsByFileUploadTypeId,
																		 @Nonnull EnumMap<FileUploadTypeId, UUID> cropFileUploadIdsByFileUploadTypeId,
																		 @Nonnull EnumMap<FileUploadTypeId, UUID> thumbnailImageIdsByCropFileUploadTypeId,
																		 @Nonnull EnumMap<FileUploadTypeId, UUID> thumbnailFileUploadIdsByCropFileUploadTypeId) {
		requireNonNull(cropFileUploadTypeId);
		requireNonNull(cropImageIdsByFileUploadTypeId);
		requireNonNull(cropFileUploadIdsByFileUploadTypeId);
		requireNonNull(thumbnailImageIdsByCropFileUploadTypeId);
		requireNonNull(thumbnailFileUploadIdsByCropFileUploadTypeId);

		if (cropImageId != null && cropFileUploadId != null) {
			cropImageIdsByFileUploadTypeId.put(cropFileUploadTypeId, cropImageId);
			cropFileUploadIdsByFileUploadTypeId.put(cropFileUploadTypeId, cropFileUploadId);
		}

		if (thumbnailImageId != null && thumbnailFileUploadId != null) {
			thumbnailImageIdsByCropFileUploadTypeId.put(cropFileUploadTypeId, thumbnailImageId);
			thumbnailFileUploadIdsByCropFileUploadTypeId.put(cropFileUploadTypeId, thumbnailFileUploadId);
		}
	}

	@Nonnull
	protected LegacyImageMigrationResult recordUnmigratable(@Nonnull Account account,
																													@Nonnull FileUpload legacyFileUpload,
																													@Nullable String errorMessage) {
		requireNonNull(account);
		requireNonNull(legacyFileUpload);

		LegacyImageMigrationResult result = new LegacyImageMigrationResult(LegacyImageMigrationStatusId.UNMIGRATABLE,
				legacyFileUpload.getFileUploadId(), null, null, Map.of(), Map.of(), Map.of(), Map.of(),
				null, null, null, List.of());
		upsertLegacyImageMigration(account, legacyFileUpload, result, errorMessage);
		return result;
	}

	@Nonnull
	protected UploadedImage createUploadedImage(@Nonnull Account account,
																							@Nonnull FileUpload legacyFileUpload,
																							@Nonnull FileUploadTypeId fileUploadTypeId,
																							@Nullable UUID sourceImageId,
																							@Nonnull byte[] imageData,
																							@Nonnull String contentType,
																							@Nonnull String filename,
																							@Nonnull Integer width,
																							@Nonnull Integer height,
																							@Nullable String imageAltText,
																							@Nullable String imageHash) {
		requireNonNull(account);
		requireNonNull(legacyFileUpload);
		requireNonNull(fileUploadTypeId);
		requireNonNull(imageData);
		requireNonNull(contentType);
		requireNonNull(filename);
		requireNonNull(width);
		requireNonNull(height);

		UUID imageId = UUID.randomUUID();
		UUID fileUploadId = UUID.randomUUID();
		String fileUploadTypeStorageKey = findStorageKeyForFileUploadTypeId(fileUploadTypeId).orElseThrow();
		String storageKey = format("media-uploads/%s/%s/%s/%s", account.getInstitutionId(), fileUploadTypeStorageKey, fileUploadId, filename);
		Map<String, String> metadata = new java.util.HashMap<>();
		metadata.put("account-id", account.getAccountId().toString());
		metadata.put("image-id", imageId.toString());
		metadata.put("legacy-file-upload-id", legacyFileUpload.getFileUploadId().toString());

		if (sourceImageId != null)
			metadata.put("source-image-id", sourceImageId.toString());

		CreateFileUploadRequest fileUploadRequest = new CreateFileUploadRequest();
		fileUploadRequest.setAccountId(account.getAccountId());
		fileUploadRequest.setFileUploadTypeId(fileUploadTypeId);
		fileUploadRequest.setFilename(filename);
		fileUploadRequest.setContentType(contentType);
		fileUploadRequest.setFilesize(imageData.length);
		fileUploadRequest.setPublicRead(true);
		fileUploadRequest.setMetadata(metadata);

		FileUploadResult fileUploadResult = getSystemService().createFileUploadAtStorageKey(fileUploadId, fileUploadRequest, storageKey);
		getUploadManager().uploadFileLocatedByStorageKey(storageKey, contentType, imageData, true, metadata);

		getDatabase().execute("""
				UPDATE file_upload
				SET file_upload_status_id=?
				WHERE file_upload_id=?
				""", FileUploadStatusId.UPLOADED, fileUploadResult.getFileUploadId());

		getDatabase().execute("""
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

		return new UploadedImage(imageId, fileUploadId);
	}

	protected void upsertLegacyImageMigration(@Nonnull Account account,
																						@Nonnull FileUpload legacyFileUpload,
																						@Nonnull LegacyImageMigrationResult result,
																						@Nullable String errorMessage) {
		requireNonNull(account);
		requireNonNull(legacyFileUpload);
		requireNonNull(result);

		getDatabase().execute("""
				INSERT INTO legacy_image_migration (
				  legacy_file_upload_id,
				  institution_id,
				  created_by_account_id,
				  legacy_url,
				  legacy_storage_key,
				  legacy_content_type,
				  legacy_filename,
				  legacy_image_migration_status_id,
				  source_width,
				  source_height,
				  source_filesize,
				  source_image_hash,
				  raw_image_id,
				  crop_16x9_image_id,
				  thumbnail_16x9_image_id,
				  crop_4x3_image_id,
				  thumbnail_4x3_image_id,
				  crop_1x1_image_id,
				  thumbnail_1x1_image_id,
				  quality_report,
				  error_message
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				ON CONFLICT (legacy_file_upload_id) DO UPDATE SET
				  legacy_image_migration_status_id=EXCLUDED.legacy_image_migration_status_id,
				  source_width=EXCLUDED.source_width,
				  source_height=EXCLUDED.source_height,
				  source_filesize=EXCLUDED.source_filesize,
				  source_image_hash=EXCLUDED.source_image_hash,
				  raw_image_id=EXCLUDED.raw_image_id,
				  crop_16x9_image_id=EXCLUDED.crop_16x9_image_id,
				  thumbnail_16x9_image_id=EXCLUDED.thumbnail_16x9_image_id,
				  crop_4x3_image_id=EXCLUDED.crop_4x3_image_id,
				  thumbnail_4x3_image_id=EXCLUDED.thumbnail_4x3_image_id,
				  crop_1x1_image_id=EXCLUDED.crop_1x1_image_id,
				  thumbnail_1x1_image_id=EXCLUDED.thumbnail_1x1_image_id,
				  quality_report=EXCLUDED.quality_report,
				  error_message=EXCLUDED.error_message
				""",
				legacyFileUpload.getFileUploadId(),
				account.getInstitutionId(),
				account.getAccountId(),
				legacyFileUpload.getUrl(),
				legacyFileUpload.getStorageKey(),
				legacyFileUpload.getContentType(),
				legacyFileUpload.getFilename(),
				result.getMigrationStatusId(),
				result.getSourceWidth(),
				result.getSourceHeight(),
				legacyFileUpload.getFilesize() == null ? null : legacyFileUpload.getFilesize().longValue(),
				result.getSourceImageHash(),
				result.getRawImageId(),
				result.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9),
				result.getThumbnailImageIdsByCropFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9),
				result.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_4X3),
				result.getThumbnailImageIdsByCropFileUploadTypeId().get(FileUploadTypeId.IMAGE_4X3),
				result.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_1X1),
				result.getThumbnailImageIdsByCropFileUploadTypeId().get(FileUploadTypeId.IMAGE_1X1),
				result.getQualityMessages().size() == 0 ? null : String.join("\n", result.getQualityMessages()),
				errorMessage);
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

	@Nonnull
	protected Optional<String> normalizeLegacyImageContentType(@Nonnull FileUpload legacyFileUpload) {
		requireNonNull(legacyFileUpload);

		String contentType = trimToNull(legacyFileUpload.getContentType());

		if (contentType != null) {
			int parameterIndex = contentType.indexOf(';');

			if (parameterIndex >= 0)
				contentType = contentType.substring(0, parameterIndex);

			contentType = trimToNull(contentType);
		}

		if (contentType != null) {
			String normalizedContentType = contentType.toLowerCase(Locale.US);

			if ("image/jpg".equals(normalizedContentType))
				return Optional.of("image/jpeg");

			if (normalizedContentType.startsWith("image/"))
				return Optional.of(normalizedContentType);

			if ("application/jpg".equals(normalizedContentType) || "application/jpeg".equals(normalizedContentType))
				return Optional.of("image/jpeg");

			if ("application/png".equals(normalizedContentType))
				return Optional.of("image/png");

			if ("application/gif".equals(normalizedContentType))
				return Optional.of("image/gif");
		}

		String filename = trimToNull(legacyFileUpload.getFilename());

		if (filename == null)
			return Optional.empty();

		String lowercaseFilename = filename.toLowerCase(Locale.US);

		if (lowercaseFilename.endsWith(".jpg") || lowercaseFilename.endsWith(".jpeg"))
			return Optional.of("image/jpeg");

		if (lowercaseFilename.endsWith(".png"))
			return Optional.of("image/png");

		if (lowercaseFilename.endsWith(".gif"))
			return Optional.of("image/gif");

		return Optional.empty();
	}

	@Nonnull
	protected String legacyMigrationCropImageIdColumnName(@Nonnull FileUploadTypeId cropFileUploadTypeId) {
		requireNonNull(cropFileUploadTypeId);

		if (cropFileUploadTypeId == FileUploadTypeId.IMAGE_16X9)
			return "crop_16x9_image_id";

		if (cropFileUploadTypeId == FileUploadTypeId.IMAGE_4X3)
			return "crop_4x3_image_id";

		if (cropFileUploadTypeId == FileUploadTypeId.IMAGE_1X1)
			return "crop_1x1_image_id";

		throw new IllegalArgumentException(format("Unsupported crop file upload type ID '%s'.", cropFileUploadTypeId));
	}

	@Nonnull
	protected EnumSet<FileUploadTypeId> normalizeRequestedCropFileUploadTypeIds(@Nonnull Set<FileUploadTypeId> requiredCropFileUploadTypeIds) {
		requireNonNull(requiredCropFileUploadTypeIds);

		EnumSet<FileUploadTypeId> requestedCropFileUploadTypeIds = EnumSet.noneOf(FileUploadTypeId.class);
		ValidationException validationException = new ValidationException();

		for (FileUploadTypeId fileUploadTypeId : requiredCropFileUploadTypeIds) {
			if (fileUploadTypeId == null)
				continue;

			if (!CROP_SPECS_BY_FILE_UPLOAD_TYPE_ID.containsKey(fileUploadTypeId)) {
				validationException.add(new FieldError("fileUploadTypeId", "File Upload Type ID is invalid."));
				continue;
			}

			requestedCropFileUploadTypeIds.add(fileUploadTypeId);
		}

		if (validationException.hasErrors())
			throw validationException;

		return requestedCropFileUploadTypeIds;
	}

	@Nonnull
	protected Optional<BufferedImage> decodeImage(@Nonnull byte[] data) {
		requireNonNull(data);

		try {
			return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(data)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Nonnull
	protected byte[] encodeJpeg(@Nonnull BufferedImage image) {
		requireNonNull(image);

		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			if (!ImageIO.write(image, "jpg", byteArrayOutputStream))
				throw new IllegalStateException("No JPEG writer is available.");

			return byteArrayOutputStream.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Nonnull
	protected BufferedImage crop(@Nonnull BufferedImage sourceImage,
															 @Nonnull CropWindow cropWindow) {
		requireNonNull(sourceImage);
		requireNonNull(cropWindow);

		BufferedImage cropImage = new BufferedImage(cropWindow.getWidth(), cropWindow.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = cropImage.createGraphics();

		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, cropWindow.getWidth(), cropWindow.getHeight());
			graphics.drawImage(sourceImage, 0, 0, cropWindow.getWidth(), cropWindow.getHeight(),
					cropWindow.getX(), cropWindow.getY(), cropWindow.getX() + cropWindow.getWidth(), cropWindow.getY() + cropWindow.getHeight(), null);
		} finally {
			graphics.dispose();
		}

		return cropImage;
	}

	@Nonnull
	protected BufferedImage resize(@Nonnull BufferedImage sourceImage,
																 @Nonnull Integer width,
																 @Nonnull Integer height) {
		requireNonNull(sourceImage);
		requireNonNull(width);
		requireNonNull(height);

		BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = resizedImage.createGraphics();

		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(sourceImage, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}

		return resizedImage;
	}

	@Nonnull
	protected CropWindow cropWindowFor(@Nonnull Integer sourceWidth,
																		 @Nonnull Integer sourceHeight,
																		 @Nonnull CropSpec cropSpec) {
		requireNonNull(sourceWidth);
		requireNonNull(sourceHeight);
		requireNonNull(cropSpec);

		Integer unitsByWidth = sourceWidth / cropSpec.getAspectRatioWidth();
		Integer unitsByHeight = sourceHeight / cropSpec.getAspectRatioHeight();
		Integer units = Math.min(unitsByWidth, unitsByHeight);
		Integer cropWidth = units * cropSpec.getAspectRatioWidth();
		Integer cropHeight = units * cropSpec.getAspectRatioHeight();
		Integer cropX = (sourceWidth - cropWidth) / 2;
		Integer cropY = (sourceHeight - cropHeight) / 2;

		return new CropWindow(cropX, cropY, cropWidth, cropHeight);
	}

	@Nonnull
	protected String sha256Hex(@Nonnull byte[] data) {
		requireNonNull(data);

		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			byte[] digest = messageDigest.digest(data);
			StringBuilder stringBuilder = new StringBuilder(digest.length * 2);

			for (byte b : digest)
				stringBuilder.append(format("%02x", b));

			return stringBuilder.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Nonnull
	protected String legacyFilename(@Nonnull FileUpload legacyFileUpload,
																	@Nonnull FileUploadTypeId fileUploadTypeId) {
		requireNonNull(legacyFileUpload);
		requireNonNull(fileUploadTypeId);

		String filename = trimToNull(legacyFileUpload.getFilename());

		if (filename == null)
			filename = legacyFileUpload.getFileUploadId().toString();

		if (fileUploadTypeId == FileUploadTypeId.IMAGE_RAW)
			return filename;

		String baseFilename = filename;
		int extensionIndex = baseFilename.lastIndexOf('.');

		if (extensionIndex > 0)
			baseFilename = baseFilename.substring(0, extensionIndex);

		return format("%s-%s.jpg", baseFilename, fileUploadTypeId.name().toLowerCase(Locale.US));
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

	public enum LegacyImageMigrationStatusId {
		RAW_IMPORTED,
		VARIANTS_GENERATED,
		NEEDS_REVIEW,
		LOW_FIDELITY,
		UNMIGRATABLE,
		REPLACED
	}

	@Immutable
	public static class LegacyImageMigrationResult {
		@Nonnull
		private final LegacyImageMigrationStatusId migrationStatusId;
		@Nonnull
		private final UUID legacyFileUploadId;
		@Nullable
		private final UUID rawImageId;
		@Nullable
		private final UUID rawFileUploadId;
		@Nonnull
		private final Map<FileUploadTypeId, UUID> cropImageIdsByFileUploadTypeId;
		@Nonnull
		private final Map<FileUploadTypeId, UUID> cropFileUploadIdsByFileUploadTypeId;
		@Nonnull
		private final Map<FileUploadTypeId, UUID> thumbnailImageIdsByCropFileUploadTypeId;
		@Nonnull
		private final Map<FileUploadTypeId, UUID> thumbnailFileUploadIdsByCropFileUploadTypeId;
		@Nullable
		private final Integer sourceWidth;
		@Nullable
		private final Integer sourceHeight;
		@Nullable
		private final String sourceImageHash;
		@Nonnull
		private final List<String> qualityMessages;

		public LegacyImageMigrationResult(@Nonnull LegacyImageMigrationStatusId migrationStatusId,
																			@Nonnull UUID legacyFileUploadId,
																			@Nullable UUID rawImageId,
																			@Nullable UUID rawFileUploadId,
																			@Nonnull Map<FileUploadTypeId, UUID> cropImageIdsByFileUploadTypeId,
																			@Nonnull Map<FileUploadTypeId, UUID> cropFileUploadIdsByFileUploadTypeId,
																			@Nonnull Map<FileUploadTypeId, UUID> thumbnailImageIdsByCropFileUploadTypeId,
																			@Nonnull Map<FileUploadTypeId, UUID> thumbnailFileUploadIdsByCropFileUploadTypeId,
																			@Nullable Integer sourceWidth,
																			@Nullable Integer sourceHeight,
																			@Nullable String sourceImageHash,
																			@Nonnull List<String> qualityMessages) {
			requireNonNull(migrationStatusId);
			requireNonNull(legacyFileUploadId);
			requireNonNull(cropImageIdsByFileUploadTypeId);
			requireNonNull(cropFileUploadIdsByFileUploadTypeId);
			requireNonNull(thumbnailImageIdsByCropFileUploadTypeId);
			requireNonNull(thumbnailFileUploadIdsByCropFileUploadTypeId);
			requireNonNull(qualityMessages);

			this.migrationStatusId = migrationStatusId;
			this.legacyFileUploadId = legacyFileUploadId;
			this.rawImageId = rawImageId;
			this.rawFileUploadId = rawFileUploadId;
			this.cropImageIdsByFileUploadTypeId = Map.copyOf(cropImageIdsByFileUploadTypeId);
			this.cropFileUploadIdsByFileUploadTypeId = Map.copyOf(cropFileUploadIdsByFileUploadTypeId);
			this.thumbnailImageIdsByCropFileUploadTypeId = Map.copyOf(thumbnailImageIdsByCropFileUploadTypeId);
			this.thumbnailFileUploadIdsByCropFileUploadTypeId = Map.copyOf(thumbnailFileUploadIdsByCropFileUploadTypeId);
			this.sourceWidth = sourceWidth;
			this.sourceHeight = sourceHeight;
			this.sourceImageHash = sourceImageHash;
			this.qualityMessages = List.copyOf(qualityMessages);
		}

		@Nonnull
		public LegacyImageMigrationStatusId getMigrationStatusId() {
			return this.migrationStatusId;
		}

		@Nonnull
		public UUID getLegacyFileUploadId() {
			return this.legacyFileUploadId;
		}

		@Nullable
		public UUID getRawImageId() {
			return this.rawImageId;
		}

		@Nullable
		public UUID getRawFileUploadId() {
			return this.rawFileUploadId;
		}

		@Nonnull
		public Map<FileUploadTypeId, UUID> getCropImageIdsByFileUploadTypeId() {
			return this.cropImageIdsByFileUploadTypeId;
		}

		@Nonnull
		public Map<FileUploadTypeId, UUID> getCropFileUploadIdsByFileUploadTypeId() {
			return this.cropFileUploadIdsByFileUploadTypeId;
		}

		@Nonnull
		public Map<FileUploadTypeId, UUID> getThumbnailImageIdsByCropFileUploadTypeId() {
			return this.thumbnailImageIdsByCropFileUploadTypeId;
		}

		@Nonnull
		public Map<FileUploadTypeId, UUID> getThumbnailFileUploadIdsByCropFileUploadTypeId() {
			return this.thumbnailFileUploadIdsByCropFileUploadTypeId;
		}

		@Nullable
		public Integer getSourceWidth() {
			return this.sourceWidth;
		}

		@Nullable
		public Integer getSourceHeight() {
			return this.sourceHeight;
		}

		@Nullable
		public String getSourceImageHash() {
			return this.sourceImageHash;
		}

		@Nonnull
		public List<String> getQualityMessages() {
			return this.qualityMessages;
		}
	}

	public static class LegacyImageMigrationInstitutionReport {
		@Nullable
		private InstitutionId institutionId;
		@Nullable
		private Boolean imageRepositoryEnabled;
		@Nullable
		private Long totalCount;
		@Nullable
		private Long currentLegacyReferenceCount;
		@Nullable
		private Long currentLegacyContentReferenceCount;
		@Nullable
		private Long currentLegacyGroupSessionReferenceCount;
		@Nullable
		private Long pendingCount;
		@Nullable
		private Long pendingContentReferenceCount;
		@Nullable
		private Long pendingGroupSessionReferenceCount;
		@Nullable
		private Long attemptedCount;
		@Nullable
		private Long rawImportedCount;
		@Nullable
		private Long variantsGeneratedCount;
		@Nullable
		private Long needsReviewCount;
		@Nullable
		private Long lowFidelityCount;
		@Nullable
		private Long unmigratableCount;
		@Nullable
		private Long replacedCount;
		@Nullable
		private Long rewiredContentCount;
		@Nullable
		private Long rewiredGroupSessionCount;

		@Nullable
		public InstitutionId getInstitutionId() {
			return this.institutionId;
		}

		public void setInstitutionId(@Nullable InstitutionId institutionId) {
			this.institutionId = institutionId;
		}

		@Nullable
		public Boolean getImageRepositoryEnabled() {
			return this.imageRepositoryEnabled;
		}

		public void setImageRepositoryEnabled(@Nullable Boolean imageRepositoryEnabled) {
			this.imageRepositoryEnabled = imageRepositoryEnabled;
		}

		@Nullable
		public Long getTotalCount() {
			return this.totalCount;
		}

		public void setTotalCount(@Nullable Long totalCount) {
			this.totalCount = totalCount;
		}

		@Nullable
		public Long getCurrentLegacyReferenceCount() {
			return this.currentLegacyReferenceCount;
		}

		public void setCurrentLegacyReferenceCount(@Nullable Long currentLegacyReferenceCount) {
			this.currentLegacyReferenceCount = currentLegacyReferenceCount;
		}

		@Nullable
		public Long getCurrentLegacyContentReferenceCount() {
			return this.currentLegacyContentReferenceCount;
		}

		public void setCurrentLegacyContentReferenceCount(@Nullable Long currentLegacyContentReferenceCount) {
			this.currentLegacyContentReferenceCount = currentLegacyContentReferenceCount;
		}

		@Nullable
		public Long getCurrentLegacyGroupSessionReferenceCount() {
			return this.currentLegacyGroupSessionReferenceCount;
		}

		public void setCurrentLegacyGroupSessionReferenceCount(@Nullable Long currentLegacyGroupSessionReferenceCount) {
			this.currentLegacyGroupSessionReferenceCount = currentLegacyGroupSessionReferenceCount;
		}

		@Nullable
		public Long getPendingCount() {
			return this.pendingCount;
		}

		public void setPendingCount(@Nullable Long pendingCount) {
			this.pendingCount = pendingCount;
		}

		@Nullable
		public Long getPendingContentReferenceCount() {
			return this.pendingContentReferenceCount;
		}

		public void setPendingContentReferenceCount(@Nullable Long pendingContentReferenceCount) {
			this.pendingContentReferenceCount = pendingContentReferenceCount;
		}

		@Nullable
		public Long getPendingGroupSessionReferenceCount() {
			return this.pendingGroupSessionReferenceCount;
		}

		public void setPendingGroupSessionReferenceCount(@Nullable Long pendingGroupSessionReferenceCount) {
			this.pendingGroupSessionReferenceCount = pendingGroupSessionReferenceCount;
		}

		@Nullable
		public Long getAttemptedCount() {
			return this.attemptedCount;
		}

		public void setAttemptedCount(@Nullable Long attemptedCount) {
			this.attemptedCount = attemptedCount;
		}

		@Nullable
		public Long getRawImportedCount() {
			return this.rawImportedCount;
		}

		public void setRawImportedCount(@Nullable Long rawImportedCount) {
			this.rawImportedCount = rawImportedCount;
		}

		@Nullable
		public Long getVariantsGeneratedCount() {
			return this.variantsGeneratedCount;
		}

		public void setVariantsGeneratedCount(@Nullable Long variantsGeneratedCount) {
			this.variantsGeneratedCount = variantsGeneratedCount;
		}

		@Nullable
		public Long getNeedsReviewCount() {
			return this.needsReviewCount;
		}

		public void setNeedsReviewCount(@Nullable Long needsReviewCount) {
			this.needsReviewCount = needsReviewCount;
		}

		@Nullable
		public Long getLowFidelityCount() {
			return this.lowFidelityCount;
		}

		public void setLowFidelityCount(@Nullable Long lowFidelityCount) {
			this.lowFidelityCount = lowFidelityCount;
		}

		@Nullable
		public Long getUnmigratableCount() {
			return this.unmigratableCount;
		}

		public void setUnmigratableCount(@Nullable Long unmigratableCount) {
			this.unmigratableCount = unmigratableCount;
		}

		@Nullable
		public Long getReplacedCount() {
			return this.replacedCount;
		}

		public void setReplacedCount(@Nullable Long replacedCount) {
			this.replacedCount = replacedCount;
		}

		@Nullable
		public Long getRewiredContentCount() {
			return this.rewiredContentCount;
		}

		public void setRewiredContentCount(@Nullable Long rewiredContentCount) {
			this.rewiredContentCount = rewiredContentCount;
		}

		@Nullable
		public Long getRewiredGroupSessionCount() {
			return this.rewiredGroupSessionCount;
		}

		public void setRewiredGroupSessionCount(@Nullable Long rewiredGroupSessionCount) {
			this.rewiredGroupSessionCount = rewiredGroupSessionCount;
		}
	}

	public enum LegacyImageMigrationReferenceTypeId {
		CONTENT,
		GROUP_SESSION
	}

	public static class LegacyImageReference {
		@Nullable
		private LegacyImageMigrationReferenceTypeId referenceTypeId;
		@Nullable
		private UUID referenceId;
		@Nullable
		private UUID legacyFileUploadId;

		public LegacyImageReference() {
			// Required for reflective database hydration.
		}

		public LegacyImageReference(@Nonnull LegacyImageMigrationReferenceTypeId referenceTypeId,
																@Nonnull UUID referenceId,
																@Nullable UUID legacyFileUploadId) {
			requireNonNull(referenceTypeId);
			requireNonNull(referenceId);

			this.referenceTypeId = referenceTypeId;
			this.referenceId = referenceId;
			this.legacyFileUploadId = legacyFileUploadId;
		}

		@Nonnull
		public LegacyImageMigrationReferenceTypeId getReferenceTypeId() {
			return this.referenceTypeId;
		}

		public void setReferenceTypeId(@Nullable LegacyImageMigrationReferenceTypeId referenceTypeId) {
			this.referenceTypeId = referenceTypeId;
		}

		@Nonnull
		public UUID getReferenceId() {
			return this.referenceId;
		}

		public void setReferenceId(@Nullable UUID referenceId) {
			this.referenceId = referenceId;
		}

		@Nullable
		public UUID getLegacyFileUploadId() {
			return this.legacyFileUploadId;
		}

		public void setLegacyFileUploadId(@Nullable UUID legacyFileUploadId) {
			this.legacyFileUploadId = legacyFileUploadId;
		}
	}

	@Immutable
	public static class LegacyImageReferenceMigrationResult {
		@Nonnull
		private final LegacyImageReference legacyImageReference;
		@Nonnull
		private final LegacyImageMigrationResult legacyImageMigrationResult;

		public LegacyImageReferenceMigrationResult(@Nonnull LegacyImageReference legacyImageReference,
																							 @Nonnull LegacyImageMigrationResult legacyImageMigrationResult) {
			requireNonNull(legacyImageReference);
			requireNonNull(legacyImageMigrationResult);

			this.legacyImageReference = legacyImageReference;
			this.legacyImageMigrationResult = legacyImageMigrationResult;
		}

		@Nonnull
		public LegacyImageReference getLegacyImageReference() {
			return this.legacyImageReference;
		}

		@Nonnull
		public LegacyImageMigrationResult getLegacyImageMigrationResult() {
			return this.legacyImageMigrationResult;
		}
	}

	@Immutable
	public static class LegacyImageMigrationBatchResult {
		@Nonnull
		private final InstitutionId institutionId;
		@Nonnull
		private final Integer requestedLimit;
		@Nonnull
		private final LegacyImageMigrationInstitutionReport beforeReport;
		@Nonnull
		private final LegacyImageMigrationInstitutionReport afterReport;
		@Nonnull
		private final List<LegacyImageReferenceMigrationResult> migrationResults;

		public LegacyImageMigrationBatchResult(@Nonnull InstitutionId institutionId,
																					 @Nonnull Integer requestedLimit,
																					 @Nonnull LegacyImageMigrationInstitutionReport beforeReport,
																					 @Nonnull LegacyImageMigrationInstitutionReport afterReport,
																					 @Nonnull List<LegacyImageReferenceMigrationResult> migrationResults) {
			requireNonNull(institutionId);
			requireNonNull(requestedLimit);
			requireNonNull(beforeReport);
			requireNonNull(afterReport);
			requireNonNull(migrationResults);

			this.institutionId = institutionId;
			this.requestedLimit = requestedLimit;
			this.beforeReport = beforeReport;
			this.afterReport = afterReport;
			this.migrationResults = List.copyOf(migrationResults);
		}

		@Nonnull
		public InstitutionId getInstitutionId() {
			return this.institutionId;
		}

		@Nonnull
		public Integer getRequestedLimit() {
			return this.requestedLimit;
		}

		@Nonnull
		public LegacyImageMigrationInstitutionReport getBeforeReport() {
			return this.beforeReport;
		}

		@Nonnull
		public LegacyImageMigrationInstitutionReport getAfterReport() {
			return this.afterReport;
		}

		@Nonnull
		public List<LegacyImageReferenceMigrationResult> getMigrationResults() {
			return this.migrationResults;
		}

		@Nonnull
		public Integer getProcessedCount() {
			return this.migrationResults.size();
		}
	}

	@Immutable
	protected static class UploadedImage {
		@Nonnull
		private final UUID imageId;
		@Nonnull
		private final UUID fileUploadId;

		public UploadedImage(@Nonnull UUID imageId,
												 @Nonnull UUID fileUploadId) {
			requireNonNull(imageId);
			requireNonNull(fileUploadId);

			this.imageId = imageId;
			this.fileUploadId = fileUploadId;
		}

		@Nonnull
		public UUID getImageId() {
			return this.imageId;
		}

		@Nonnull
		public UUID getFileUploadId() {
			return this.fileUploadId;
		}
	}

	@Immutable
	protected static class CropSpec {
		@Nonnull
		private final FileUploadTypeId cropFileUploadTypeId;
		@Nonnull
		private final FileUploadTypeId thumbnailFileUploadTypeId;
		@Nonnull
		private final Integer aspectRatioWidth;
		@Nonnull
		private final Integer aspectRatioHeight;
		@Nonnull
		private final Integer minimumWidth;
		@Nonnull
		private final Integer minimumHeight;
		@Nonnull
		private final Integer thumbnailWidth;
		@Nonnull
		private final Integer thumbnailHeight;

		public CropSpec(@Nonnull FileUploadTypeId cropFileUploadTypeId,
										@Nonnull FileUploadTypeId thumbnailFileUploadTypeId,
										@Nonnull Integer aspectRatioWidth,
										@Nonnull Integer aspectRatioHeight,
										@Nonnull Integer minimumWidth,
										@Nonnull Integer minimumHeight,
										@Nonnull Integer thumbnailWidth,
										@Nonnull Integer thumbnailHeight) {
			requireNonNull(cropFileUploadTypeId);
			requireNonNull(thumbnailFileUploadTypeId);
			requireNonNull(aspectRatioWidth);
			requireNonNull(aspectRatioHeight);
			requireNonNull(minimumWidth);
			requireNonNull(minimumHeight);
			requireNonNull(thumbnailWidth);
			requireNonNull(thumbnailHeight);

			this.cropFileUploadTypeId = cropFileUploadTypeId;
			this.thumbnailFileUploadTypeId = thumbnailFileUploadTypeId;
			this.aspectRatioWidth = aspectRatioWidth;
			this.aspectRatioHeight = aspectRatioHeight;
			this.minimumWidth = minimumWidth;
			this.minimumHeight = minimumHeight;
			this.thumbnailWidth = thumbnailWidth;
			this.thumbnailHeight = thumbnailHeight;
		}

		@Nonnull
		public FileUploadTypeId getCropFileUploadTypeId() {
			return this.cropFileUploadTypeId;
		}

		@Nonnull
		public FileUploadTypeId getThumbnailFileUploadTypeId() {
			return this.thumbnailFileUploadTypeId;
		}

		@Nonnull
		public Integer getAspectRatioWidth() {
			return this.aspectRatioWidth;
		}

		@Nonnull
		public Integer getAspectRatioHeight() {
			return this.aspectRatioHeight;
		}

		@Nonnull
		public Integer getMinimumWidth() {
			return this.minimumWidth;
		}

		@Nonnull
		public Integer getMinimumHeight() {
			return this.minimumHeight;
		}

		@Nonnull
		public Integer getThumbnailWidth() {
			return this.thumbnailWidth;
		}

		@Nonnull
		public Integer getThumbnailHeight() {
			return this.thumbnailHeight;
		}
	}

	@Immutable
	protected static class CropWindow {
		@Nonnull
		private final Integer x;
		@Nonnull
		private final Integer y;
		@Nonnull
		private final Integer width;
		@Nonnull
		private final Integer height;

		public CropWindow(@Nonnull Integer x,
											@Nonnull Integer y,
											@Nonnull Integer width,
											@Nonnull Integer height) {
			requireNonNull(x);
			requireNonNull(y);
			requireNonNull(width);
			requireNonNull(height);

			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		@Nonnull
		public Integer getX() {
			return this.x;
		}

		@Nonnull
		public Integer getY() {
			return this.y;
		}

		@Nonnull
		public Integer getWidth() {
			return this.width;
		}

		@Nonnull
		public Integer getHeight() {
			return this.height;
		}
	}

	protected static class ContentLegacyImage {
		@Nullable
		private UUID contentId;
		@Nullable
		private UUID imageId;
		@Nullable
		private UUID imageFileUploadId;

		@Nullable
		public UUID getContentId() {
			return this.contentId;
		}

		public void setContentId(@Nullable UUID contentId) {
			this.contentId = contentId;
		}

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

	protected static class GroupSessionLegacyImage {
		@Nullable
		private UUID groupSessionId;
		@Nullable
		private UUID imageId;
		@Nullable
		private UUID imageFileUploadId;

		@Nullable
		public UUID getGroupSessionId() {
			return this.groupSessionId;
		}

		public void setGroupSessionId(@Nullable UUID groupSessionId) {
			this.groupSessionId = groupSessionId;
		}

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

	protected static class ExistingLegacyImageMigration {
		@Nullable
		private LegacyImageMigrationStatusId legacyImageMigrationStatusId;
		@Nullable
		private Integer sourceWidth;
		@Nullable
		private Integer sourceHeight;
		@Nullable
		private String sourceImageHash;
		@Nullable
		private UUID rawImageId;
		@Nullable
		private UUID rawFileUploadId;
		@Nullable
		private UUID crop16X9ImageId;
		@Nullable
		private UUID crop16X9FileUploadId;
		@Nullable
		private UUID thumbnail16X9ImageId;
		@Nullable
		private UUID thumbnail16X9FileUploadId;
		@Nullable
		private UUID crop4X3ImageId;
		@Nullable
		private UUID crop4X3FileUploadId;
		@Nullable
		private UUID thumbnail4X3ImageId;
		@Nullable
		private UUID thumbnail4X3FileUploadId;
		@Nullable
		private UUID crop1X1ImageId;
		@Nullable
		private UUID crop1X1FileUploadId;
		@Nullable
		private UUID thumbnail1X1ImageId;
		@Nullable
		private UUID thumbnail1X1FileUploadId;

		@Nullable
		public LegacyImageMigrationStatusId getLegacyImageMigrationStatusId() {
			return this.legacyImageMigrationStatusId;
		}

		public void setLegacyImageMigrationStatusId(@Nullable LegacyImageMigrationStatusId legacyImageMigrationStatusId) {
			this.legacyImageMigrationStatusId = legacyImageMigrationStatusId;
		}

		@Nullable
		public Integer getSourceWidth() {
			return this.sourceWidth;
		}

		public void setSourceWidth(@Nullable Integer sourceWidth) {
			this.sourceWidth = sourceWidth;
		}

		@Nullable
		public Integer getSourceHeight() {
			return this.sourceHeight;
		}

		public void setSourceHeight(@Nullable Integer sourceHeight) {
			this.sourceHeight = sourceHeight;
		}

		@Nullable
		public String getSourceImageHash() {
			return this.sourceImageHash;
		}

		public void setSourceImageHash(@Nullable String sourceImageHash) {
			this.sourceImageHash = sourceImageHash;
		}

		@Nullable
		public UUID getRawImageId() {
			return this.rawImageId;
		}

		public void setRawImageId(@Nullable UUID rawImageId) {
			this.rawImageId = rawImageId;
		}

		@Nullable
		public UUID getRawFileUploadId() {
			return this.rawFileUploadId;
		}

		public void setRawFileUploadId(@Nullable UUID rawFileUploadId) {
			this.rawFileUploadId = rawFileUploadId;
		}

		@Nullable
		public UUID getCrop16X9ImageId() {
			return this.crop16X9ImageId;
		}

		public void setCrop16X9ImageId(@Nullable UUID crop16X9ImageId) {
			this.crop16X9ImageId = crop16X9ImageId;
		}

		@Nullable
		public UUID getCrop16X9FileUploadId() {
			return this.crop16X9FileUploadId;
		}

		public void setCrop16X9FileUploadId(@Nullable UUID crop16X9FileUploadId) {
			this.crop16X9FileUploadId = crop16X9FileUploadId;
		}

		@Nullable
		public UUID getThumbnail16X9ImageId() {
			return this.thumbnail16X9ImageId;
		}

		public void setThumbnail16X9ImageId(@Nullable UUID thumbnail16X9ImageId) {
			this.thumbnail16X9ImageId = thumbnail16X9ImageId;
		}

		@Nullable
		public UUID getThumbnail16X9FileUploadId() {
			return this.thumbnail16X9FileUploadId;
		}

		public void setThumbnail16X9FileUploadId(@Nullable UUID thumbnail16X9FileUploadId) {
			this.thumbnail16X9FileUploadId = thumbnail16X9FileUploadId;
		}

		@Nullable
		public UUID getCrop4X3ImageId() {
			return this.crop4X3ImageId;
		}

		public void setCrop4X3ImageId(@Nullable UUID crop4X3ImageId) {
			this.crop4X3ImageId = crop4X3ImageId;
		}

		@Nullable
		public UUID getCrop4X3FileUploadId() {
			return this.crop4X3FileUploadId;
		}

		public void setCrop4X3FileUploadId(@Nullable UUID crop4X3FileUploadId) {
			this.crop4X3FileUploadId = crop4X3FileUploadId;
		}

		@Nullable
		public UUID getThumbnail4X3ImageId() {
			return this.thumbnail4X3ImageId;
		}

		public void setThumbnail4X3ImageId(@Nullable UUID thumbnail4X3ImageId) {
			this.thumbnail4X3ImageId = thumbnail4X3ImageId;
		}

		@Nullable
		public UUID getThumbnail4X3FileUploadId() {
			return this.thumbnail4X3FileUploadId;
		}

		public void setThumbnail4X3FileUploadId(@Nullable UUID thumbnail4X3FileUploadId) {
			this.thumbnail4X3FileUploadId = thumbnail4X3FileUploadId;
		}

		@Nullable
		public UUID getCrop1X1ImageId() {
			return this.crop1X1ImageId;
		}

		public void setCrop1X1ImageId(@Nullable UUID crop1X1ImageId) {
			this.crop1X1ImageId = crop1X1ImageId;
		}

		@Nullable
		public UUID getCrop1X1FileUploadId() {
			return this.crop1X1FileUploadId;
		}

		public void setCrop1X1FileUploadId(@Nullable UUID crop1X1FileUploadId) {
			this.crop1X1FileUploadId = crop1X1FileUploadId;
		}

		@Nullable
		public UUID getThumbnail1X1ImageId() {
			return this.thumbnail1X1ImageId;
		}

		public void setThumbnail1X1ImageId(@Nullable UUID thumbnail1X1ImageId) {
			this.thumbnail1X1ImageId = thumbnail1X1ImageId;
		}

		@Nullable
		public UUID getThumbnail1X1FileUploadId() {
			return this.thumbnail1X1FileUploadId;
		}

		public void setThumbnail1X1FileUploadId(@Nullable UUID thumbnail1X1FileUploadId) {
			this.thumbnail1X1FileUploadId = thumbnail1X1FileUploadId;
		}
	}
}
