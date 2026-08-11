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

import com.cobaltplatform.api.Configuration;
import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.model.api.request.CreateContentRequest;
import com.cobaltplatform.api.model.api.request.CreateGroupSessionRequest;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.ContentAudienceType.ContentAudienceTypeId;
import com.cobaltplatform.api.model.db.ContentType.ContentTypeId;
import com.cobaltplatform.api.model.db.ContentVisibilityType.ContentVisibilityTypeId;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.GroupSession;
import com.cobaltplatform.api.model.db.GroupSessionLocationType.GroupSessionLocationTypeId;
import com.cobaltplatform.api.model.db.GroupSessionSchedulingSystem.GroupSessionSchedulingSystemId;
import com.cobaltplatform.api.model.db.GroupSessionVisibilityType.GroupSessionVisibilityTypeId;
import com.cobaltplatform.api.model.db.Image;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.service.AdminContent;
import com.cobaltplatform.api.model.service.PresignedUpload;
import com.cobaltplatform.api.service.MediaImageMigrationService.LegacyImageMigrationBatchResult;
import com.cobaltplatform.api.service.MediaImageMigrationService.LegacyImageMigrationInstitutionReport;
import com.cobaltplatform.api.service.MediaImageMigrationService.LegacyImageMigrationResult;
import com.cobaltplatform.api.service.MediaImageMigrationService.LegacyImageMigrationStatusId;
import com.cobaltplatform.api.util.UploadManager;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.lokalized.Strings;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class MediaImageMigrationServiceTests {
	@Test
	public void pageBuilderCropSelectionPreservesTheMostSourceArea() {
		Assert.assertEquals(FileUploadTypeId.IMAGE_1X1,
				MediaImageMigrationService.closestQualifiedPageBuilderCropFileUploadTypeId(1600, 1600).orElse(null));
		Assert.assertEquals(FileUploadTypeId.IMAGE_4X3,
				MediaImageMigrationService.closestQualifiedPageBuilderCropFileUploadTypeId(1600, 1200).orElse(null));
		Assert.assertEquals(FileUploadTypeId.IMAGE_16X9,
				MediaImageMigrationService.closestQualifiedPageBuilderCropFileUploadTypeId(1600, 900).orElse(null));
		Assert.assertEquals(FileUploadTypeId.IMAGE_1X1,
				MediaImageMigrationService.closestQualifiedPageBuilderCropFileUploadTypeId(1200, 1600).orElse(null));
		Assert.assertEquals(FileUploadTypeId.IMAGE_4X3,
				MediaImageMigrationService.closestQualifiedPageBuilderCropFileUploadTypeId(1500, 1000).orElse(null));
	}

	@Test
	public void pageBuilderCropSelectionFallsBackByQualityAndCanRejectEveryCrop() {
		Assert.assertEquals("The closest 4:3 crop is too small, so a viable square crop should be selected",
				FileUploadTypeId.IMAGE_1X1,
				MediaImageMigrationService.closestQualifiedPageBuilderCropFileUploadTypeId(1100, 900).orElse(null));
		Assert.assertFalse("No supported crop should pass for a small source",
				MediaImageMigrationService.closestQualifiedPageBuilderCropFileUploadTypeId(640, 480).isPresent());
	}

	@Test
	public void highFidelityLegacyGroupSessionImageMigratesAndRewiresToCrop() {
		FakeUploadManagerModule fakeUploadManagerModule = new FakeUploadManagerModule();

		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeImageMigrationSchemaExists(database);

			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			MediaImageMigrationService mediaImageMigrationService = app.getInjector().getInstance(MediaImageMigrationService.class);
			FakeUploadManager fakeUploadManager = (FakeUploadManager) app.getInjector().getInstance(UploadManager.class);
			Account account = findExistingAccount(database);

			byte[] imageData = createPngImage(1600, 900);
			UUID legacyFileUploadId = createLegacyImageFileUpload(database, fakeUploadManager, account, imageData, "legacy-high-fidelity.png");
			UUID groupSessionId = createGroupSession(groupSessionService, account, legacyFileUploadId, "high-fidelity");

			LegacyImageMigrationResult migrationResult = mediaImageMigrationService.migrateLegacyGroupSessionImage(account, groupSessionId);
			GroupSession groupSession = groupSessionService.findGroupSessionById(groupSessionId, account).get();
			UUID cropImageId = migrationResult.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);
			UUID cropFileUploadId = migrationResult.getCropFileUploadIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);
			UUID thumbnailImageId = migrationResult.getThumbnailImageIdsByCropFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);

			Assert.assertEquals("Migration should generate all required variants", LegacyImageMigrationStatusId.VARIANTS_GENERATED, migrationResult.getMigrationStatusId());
			Assert.assertNotNull("Raw image should be imported", migrationResult.getRawImageId());
			Assert.assertNotNull("16x9 crop should be generated", cropImageId);
			Assert.assertNotNull("16x9 thumbnail should be generated", thumbnailImageId);
			Assert.assertEquals("Group session should point at the generated crop", cropImageId, groupSession.getImageId());
			Assert.assertEquals("Legacy image file upload should be replaced with crop file upload", cropFileUploadId, groupSession.getImageFileUploadId());
			Assert.assertEquals("Migration should upload raw, crop, and thumbnail objects", Integer.valueOf(4), fakeUploadManager.getStoredObjectCount());

			Image cropImage = findImageById(database, cropImageId);
			Image thumbnailImage = findImageById(database, thumbnailImageId);

			Assert.assertEquals(FileUploadTypeId.IMAGE_16X9, cropImage.getFileUploadTypeId());
			Assert.assertEquals(Integer.valueOf(1600), cropImage.getWidth());
			Assert.assertEquals(Integer.valueOf(900), cropImage.getHeight());
			Assert.assertEquals(FileUploadTypeId.IMAGE_THUMBNAIL_16X9, thumbnailImage.getFileUploadTypeId());
			Assert.assertEquals(cropImageId, thumbnailImage.getSourceImageId());
			Assert.assertEquals(Integer.valueOf(320), thumbnailImage.getWidth());
			Assert.assertEquals(Integer.valueOf(180), thumbnailImage.getHeight());

			LegacyImageMigrationStatusId persistedStatusId = database.queryForObject("""
					SELECT legacy_image_migration_status_id
					FROM legacy_image_migration
					WHERE legacy_file_upload_id=?
					""", LegacyImageMigrationStatusId.class, legacyFileUploadId).get();

			Assert.assertEquals("Migration audit row should persist generated status", LegacyImageMigrationStatusId.VARIANTS_GENERATED, persistedStatusId);

			LegacyImageMigrationResult retryMigrationResult = mediaImageMigrationService.migrateLegacyGroupSessionImage(account, groupSessionId);

			Assert.assertEquals("Retry should still be generated", LegacyImageMigrationStatusId.VARIANTS_GENERATED, retryMigrationResult.getMigrationStatusId());
			Assert.assertEquals("Retry should reuse raw image", migrationResult.getRawImageId(), retryMigrationResult.getRawImageId());
			Assert.assertEquals("Retry should reuse crop image", cropImageId, retryMigrationResult.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9));
			Assert.assertEquals("Retry should reuse thumbnail image", thumbnailImageId, retryMigrationResult.getThumbnailImageIdsByCropFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9));
			Assert.assertEquals("Retry should not create duplicate stored objects", Integer.valueOf(4), fakeUploadManager.getStoredObjectCount());
		}, fakeUploadManagerModule);
	}

	@Test
	public void lowFidelityLegacyGroupSessionImageImportsRawButDoesNotRewire() {
		FakeUploadManagerModule fakeUploadManagerModule = new FakeUploadManagerModule();

		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeImageMigrationSchemaExists(database);

			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			MediaImageMigrationService mediaImageMigrationService = app.getInjector().getInstance(MediaImageMigrationService.class);
			FakeUploadManager fakeUploadManager = (FakeUploadManager) app.getInjector().getInstance(UploadManager.class);
			Account account = findExistingAccount(database);

			byte[] imageData = createPngImage(640, 360);
			UUID legacyFileUploadId = createLegacyImageFileUpload(database, fakeUploadManager, account, imageData, "legacy-low-fidelity.png");
			UUID groupSessionId = createGroupSession(groupSessionService, account, legacyFileUploadId, "low-fidelity");

			LegacyImageMigrationResult migrationResult = mediaImageMigrationService.migrateLegacyGroupSessionImage(account, groupSessionId);
			GroupSession groupSession = groupSessionService.findGroupSessionById(groupSessionId, account).get();

			Assert.assertEquals("Migration should preserve raw but refuse inadequate variants", LegacyImageMigrationStatusId.LOW_FIDELITY, migrationResult.getMigrationStatusId());
			Assert.assertNotNull("Raw image should still be imported", migrationResult.getRawImageId());
			Assert.assertTrue("Quality message should explain why variants were skipped", migrationResult.getQualityMessages().get(0).contains("requires at least"));
			Assert.assertNull("Group session should not point at an incomplete media family", groupSession.getImageId());
			Assert.assertEquals("Legacy file upload fallback should remain intact", legacyFileUploadId, groupSession.getImageFileUploadId());
			Assert.assertEquals("Only original seeded object and migrated raw object should exist", Integer.valueOf(2), fakeUploadManager.getStoredObjectCount());

			String qualityReport = database.queryForObject("""
					SELECT quality_report
					FROM legacy_image_migration
					WHERE legacy_file_upload_id=?
					""", String.class, legacyFileUploadId).get();

			Assert.assertTrue("Migration audit row should keep quality detail", qualityReport.contains("IMAGE_16X9"));

			LegacyImageMigrationResult retryMigrationResult = mediaImageMigrationService.migrateLegacyGroupSessionImage(account, groupSessionId);

			Assert.assertEquals("Retry should keep low-fidelity status", LegacyImageMigrationStatusId.LOW_FIDELITY, retryMigrationResult.getMigrationStatusId());
			Assert.assertEquals("Retry should reuse imported raw image", migrationResult.getRawImageId(), retryMigrationResult.getRawImageId());
			Assert.assertEquals("Retry should not create duplicate raw objects", Integer.valueOf(2), fakeUploadManager.getStoredObjectCount());
		}, fakeUploadManagerModule);
	}

	@Test
	public void legacyContentImageMigratesAndRewiresToCrop() {
		FakeUploadManagerModule fakeUploadManagerModule = new FakeUploadManagerModule();

		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeImageMigrationSchemaExists(database);

			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			MediaImageMigrationService mediaImageMigrationService = app.getInjector().getInstance(MediaImageMigrationService.class);
			FakeUploadManager fakeUploadManager = (FakeUploadManager) app.getInjector().getInstance(UploadManager.class);
			Account account = findExistingAdministratorAccount(database);

			byte[] imageData = createPngImage(1600, 900);
			UUID legacyFileUploadId = createLegacyImageFileUpload(database, fakeUploadManager, account, imageData,
					"legacy-content-high-fidelity.png", FileUploadTypeId.CONTENT_IMAGE, "application/png");
			AdminContent content = createContent(adminContentService, account, legacyFileUploadId, "content-high-fidelity");

			LegacyImageMigrationResult migrationResult = mediaImageMigrationService.migrateLegacyContentImage(account, content.getContentId());
			ContentImageState contentImageState = findContentImageState(database, content.getContentId());
			UUID cropImageId = migrationResult.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);
			UUID cropFileUploadId = migrationResult.getCropFileUploadIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);
			UUID thumbnailImageId = migrationResult.getThumbnailImageIdsByCropFileUploadTypeId().get(FileUploadTypeId.IMAGE_16X9);

			Assert.assertEquals("Migration should generate all required variants", LegacyImageMigrationStatusId.VARIANTS_GENERATED, migrationResult.getMigrationStatusId());
			Assert.assertNotNull("Raw image should be imported", migrationResult.getRawImageId());
			Assert.assertNotNull("16x9 crop should be generated", cropImageId);
			Assert.assertNotNull("16x9 thumbnail should be generated", thumbnailImageId);
			Assert.assertEquals("Content should point at the generated crop", cropImageId, contentImageState.getImageId());
			Assert.assertEquals("Legacy image file upload should be replaced with crop file upload", cropFileUploadId, contentImageState.getImageFileUploadId());
			Assert.assertEquals("Migration should upload raw, crop, and thumbnail objects", Integer.valueOf(4), fakeUploadManager.getStoredObjectCount());
		}, fakeUploadManagerModule);
	}

	@Test
	public void legacyPageBuilderImagesChooseClosestCropAndRewireSharedReferences() {
		FakeUploadManagerModule fakeUploadManagerModule = new FakeUploadManagerModule();

		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeImageMigrationSchemaExists(database);

			MediaImageMigrationService mediaImageMigrationService = app.getInjector().getInstance(MediaImageMigrationService.class);
			FakeUploadManager fakeUploadManager = (FakeUploadManager) app.getInjector().getInstance(UploadManager.class);
			Account account = findExistingAdministratorAccount(database);
			UUID legacyFileUploadId = createLegacyImageFileUpload(database, fakeUploadManager, account,
					createPngImage(1600, 1200), "legacy-page-builder-4x3.png", FileUploadTypeId.PAGE_IMAGE, "image/png");
			PageBuilderReferenceIds referenceIds = createPageBuilderImageReferences(database, account, legacyFileUploadId);

			LegacyImageMigrationResult migrationResult = mediaImageMigrationService.migrateLegacyPageImage(account, referenceIds.getPageId());
			UUID cropImageId = migrationResult.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_4X3);
			UUID cropFileUploadId = migrationResult.getCropFileUploadIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_4X3);

			Assert.assertEquals(LegacyImageMigrationStatusId.VARIANTS_GENERATED, migrationResult.getMigrationStatusId());
			Assert.assertNotNull("The closest 4:3 crop should be generated", cropImageId);
			Assert.assertNotNull("The matching 4:3 thumbnail should be generated",
					migrationResult.getThumbnailImageIdsByCropFileUploadTypeId().get(FileUploadTypeId.IMAGE_4X3));
			Assert.assertFalse("Unselected 16:9 crop should not be generated",
					migrationResult.getCropImageIdsByFileUploadTypeId().containsKey(FileUploadTypeId.IMAGE_16X9));
			Assert.assertFalse("Unselected square crop should not be generated",
					migrationResult.getCropImageIdsByFileUploadTypeId().containsKey(FileUploadTypeId.IMAGE_1X1));

			for (UUID rewiredImageId : database.queryForList("""
					SELECT image_id FROM page WHERE page_id=?
					UNION ALL SELECT image_id FROM page_row_column WHERE page_row_column_id=?
					UNION ALL SELECT image_id FROM page_row_call_to_action WHERE page_row_call_to_action_id=?
					""", UUID.class, referenceIds.getPageId(), referenceIds.getPageRowColumnId(), referenceIds.getPageRowCallToActionId()))
				Assert.assertEquals("Every page-builder reference sharing the upload should be rewired", cropImageId, rewiredImageId);

			for (UUID rewiredFileUploadId : database.queryForList("""
					SELECT image_file_upload_id FROM page WHERE page_id=?
					UNION ALL SELECT image_file_upload_id FROM page_row_column WHERE page_row_column_id=?
					UNION ALL SELECT image_file_upload_id FROM page_row_call_to_action WHERE page_row_call_to_action_id=?
					""", UUID.class, referenceIds.getPageId(), referenceIds.getPageRowColumnId(), referenceIds.getPageRowCallToActionId()))
				Assert.assertEquals(cropFileUploadId, rewiredFileUploadId);

			LegacyImageMigrationResult retryResult = mediaImageMigrationService.migrateLegacyPageRowColumnImage(account,
					referenceIds.getPageRowColumnId());
			Assert.assertEquals(cropImageId, retryResult.getCropImageIdsByFileUploadTypeId().get(FileUploadTypeId.IMAGE_4X3));
			Assert.assertEquals("Retry through a non-16:9 audit crop must not duplicate the family",
					Integer.valueOf(4), fakeUploadManager.getStoredObjectCount());
		}, fakeUploadManagerModule);
	}

	@Test
	public void institutionReportAndBatchMigrationAreScopedToContentAndGroupSessionsIncrementally() {
		FakeUploadManagerModule fakeUploadManagerModule = new FakeUploadManagerModule();

		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeImageMigrationSchemaExists(database);

			AdminContentService adminContentService = app.getInjector().getInstance(AdminContentService.class);
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			MediaImageMigrationService mediaImageMigrationService = app.getInjector().getInstance(MediaImageMigrationService.class);
			FakeUploadManager fakeUploadManager = (FakeUploadManager) app.getInjector().getInstance(UploadManager.class);
			Account account = findExistingAdministratorAccount(database);

			database.execute("""
					UPDATE institution
					SET image_repository_enabled=TRUE
					WHERE institution_id=?
					""", account.getInstitutionId());
			recordExistingPendingLegacyImageReferencesAsUnmigratable(database, account);

			LegacyImageMigrationInstitutionReport baselineReport =
					mediaImageMigrationService.findLegacyImageMigrationReport(account.getInstitutionId());

			Assert.assertEquals("Baseline should have no pending rows after test setup", Long.valueOf(0), baselineReport.getPendingCount());
			Assert.assertEquals("Report should expose institution-level feature flag", Boolean.TRUE, baselineReport.getImageRepositoryEnabled());

			UUID highFidelityContentLegacyFileUploadId = createLegacyImageFileUpload(database, fakeUploadManager, account,
					createPngImage(1600, 900), "legacy-institution-content-high-fidelity.png", FileUploadTypeId.CONTENT_IMAGE, "application/png");
			createContent(adminContentService, account, highFidelityContentLegacyFileUploadId, "institution-content-high-fidelity");

			UUID highFidelityLegacyFileUploadId = createLegacyImageFileUpload(database, fakeUploadManager, account,
					createPngImage(1600, 900), "legacy-institution-high-fidelity.png");
			createGroupSession(groupSessionService, account, highFidelityLegacyFileUploadId, "institution-high-fidelity");

			UUID lowFidelityLegacyFileUploadId = createLegacyImageFileUpload(database, fakeUploadManager, account,
					createPngImage(640, 360), "legacy-institution-low-fidelity.png");
			createGroupSession(groupSessionService, account, lowFidelityLegacyFileUploadId, "institution-low-fidelity");

			LegacyImageMigrationInstitutionReport seededReport =
					mediaImageMigrationService.findLegacyImageMigrationReport(account.getInstitutionId());

			Assert.assertEquals(Long.valueOf(baselineReport.getTotalCount() + 3), seededReport.getTotalCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getCurrentLegacyReferenceCount() + 3), seededReport.getCurrentLegacyReferenceCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getCurrentLegacyContentReferenceCount() + 1), seededReport.getCurrentLegacyContentReferenceCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getCurrentLegacyGroupSessionReferenceCount() + 2), seededReport.getCurrentLegacyGroupSessionReferenceCount());
			Assert.assertEquals(Long.valueOf(3), seededReport.getPendingCount());
			Assert.assertEquals(Long.valueOf(1), seededReport.getPendingContentReferenceCount());
			Assert.assertEquals(Long.valueOf(2), seededReport.getPendingGroupSessionReferenceCount());

			LegacyImageMigrationBatchResult firstBatch =
					mediaImageMigrationService.migratePendingLegacyImagesForInstitution(account, 1);

			Assert.assertEquals(Integer.valueOf(1), firstBatch.getProcessedCount());
			Assert.assertEquals(Long.valueOf(3), firstBatch.getBeforeReport().getPendingCount());
			Assert.assertEquals(Long.valueOf(2), firstBatch.getAfterReport().getPendingCount());

			LegacyImageMigrationBatchResult secondBatch =
					mediaImageMigrationService.migratePendingLegacyImagesForInstitution(account, 10);

			Assert.assertEquals(Integer.valueOf(2), secondBatch.getProcessedCount());
			Assert.assertEquals(Long.valueOf(2), secondBatch.getBeforeReport().getPendingCount());
			Assert.assertEquals(Long.valueOf(0), secondBatch.getAfterReport().getPendingCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getTotalCount() + 3), secondBatch.getAfterReport().getTotalCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getVariantsGeneratedCount() + 2), secondBatch.getAfterReport().getVariantsGeneratedCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getLowFidelityCount() + 1), secondBatch.getAfterReport().getLowFidelityCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getRewiredContentCount() + 1), secondBatch.getAfterReport().getRewiredContentCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getRewiredGroupSessionCount() + 1), secondBatch.getAfterReport().getRewiredGroupSessionCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getCurrentLegacyReferenceCount() + 1), secondBatch.getAfterReport().getCurrentLegacyReferenceCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getCurrentLegacyContentReferenceCount()), secondBatch.getAfterReport().getCurrentLegacyContentReferenceCount());
			Assert.assertEquals(Long.valueOf(baselineReport.getCurrentLegacyGroupSessionReferenceCount() + 1), secondBatch.getAfterReport().getCurrentLegacyGroupSessionReferenceCount());
			Assert.assertEquals("Batch should upload high-fidelity content/group raw/crop/thumb and low-fidelity raw", Integer.valueOf(10), fakeUploadManager.getStoredObjectCount());
		}, fakeUploadManagerModule);
	}

	protected void assumeImageMigrationSchemaExists(@Nonnull Database database) {
		requireNonNull(database);

		Boolean imageMigrationSchemaExists = database.queryForObject("""
				SELECT EXISTS (
				  SELECT 1
				  FROM information_schema.tables
				  WHERE table_schema=current_schema()
				  AND table_name='legacy_image_migration'
				)
				""", Boolean.class).get();

		Assume.assumeTrue("Branch schema must include legacy image migration tables", imageMigrationSchemaExists);
	}

	protected void recordExistingPendingLegacyImageReferencesAsUnmigratable(@Nonnull Database database,
																																				 @Nonnull Account account) {
		requireNonNull(database);
		requireNonNull(account);

		database.execute("""
				WITH legacy_refs AS (
				  SELECT DISTINCT c.image_file_upload_id AS legacy_file_upload_id
				  FROM content c
				  JOIN file_upload fu ON fu.file_upload_id=c.image_file_upload_id
				  WHERE c.owner_institution_id=?
				  AND c.deleted_flag=FALSE
				  AND fu.file_upload_type_id=?
				  UNION
				  SELECT DISTINCT gs.image_file_upload_id AS legacy_file_upload_id
				  FROM group_session gs
				  JOIN file_upload fu ON fu.file_upload_id=gs.image_file_upload_id
				  WHERE gs.institution_id=?
				  AND gs.group_session_status_id<>'DELETED'
				  AND fu.file_upload_type_id=?
				  UNION
				  SELECT DISTINCT p.image_file_upload_id AS legacy_file_upload_id
				  FROM page p
				  JOIN file_upload fu ON fu.file_upload_id=p.image_file_upload_id
				  WHERE p.institution_id=? AND p.deleted_flag=FALSE AND fu.file_upload_type_id=?
				  UNION
				  SELECT DISTINCT prc.image_file_upload_id AS legacy_file_upload_id
				  FROM page_row_column prc
				  JOIN file_upload fu ON fu.file_upload_id=prc.image_file_upload_id
				  JOIN page_row pr ON pr.page_row_id=prc.page_row_id
				  JOIN page_section ps ON ps.page_section_id=pr.page_section_id
				  JOIN page p ON p.page_id=ps.page_id
				  WHERE p.institution_id=? AND p.deleted_flag=FALSE AND ps.deleted_flag=FALSE AND pr.deleted_flag=FALSE
				  AND fu.file_upload_type_id=?
				  UNION
				  SELECT DISTINCT prcta.image_file_upload_id AS legacy_file_upload_id
				  FROM page_row_call_to_action prcta
				  JOIN file_upload fu ON fu.file_upload_id=prcta.image_file_upload_id
				  JOIN page_row pr ON pr.page_row_id=prcta.page_row_id
				  JOIN page_section ps ON ps.page_section_id=pr.page_section_id
				  JOIN page p ON p.page_id=ps.page_id
				  WHERE p.institution_id=? AND p.deleted_flag=FALSE AND ps.deleted_flag=FALSE AND pr.deleted_flag=FALSE
				  AND fu.file_upload_type_id=?
				)
				INSERT INTO legacy_image_migration (
				  legacy_file_upload_id,
				  institution_id,
				  created_by_account_id,
				  legacy_url,
				  legacy_storage_key,
				  legacy_content_type,
				  legacy_filename,
				  legacy_image_migration_status_id,
				  source_filesize,
				  error_message
				)
				SELECT DISTINCT
				  fu.file_upload_id,
				  fu.institution_id,
				  ?,
				  fu.url,
				  fu.storage_key,
				  fu.content_type,
				  fu.filename,
				  'UNMIGRATABLE',
				  fu.filesize,
				  'Marked by rollback-only test setup.'
				FROM legacy_refs lr
				JOIN file_upload fu ON fu.file_upload_id=lr.legacy_file_upload_id
				LEFT JOIN legacy_image_migration lim ON lim.legacy_file_upload_id=fu.file_upload_id
				WHERE lim.legacy_file_upload_id IS NULL
				ON CONFLICT (legacy_file_upload_id) DO NOTHING
				""", account.getInstitutionId(), FileUploadTypeId.CONTENT_IMAGE,
				account.getInstitutionId(), FileUploadTypeId.GROUP_SESSION_IMAGE,
				account.getInstitutionId(), FileUploadTypeId.PAGE_IMAGE,
				account.getInstitutionId(), FileUploadTypeId.PAGE_IMAGE,
				account.getInstitutionId(), FileUploadTypeId.PAGE_IMAGE,
				account.getAccountId());
	}

	@Nonnull
	protected PageBuilderReferenceIds createPageBuilderImageReferences(@Nonnull Database database,
																										 @Nonnull Account account,
																										 @Nonnull UUID legacyFileUploadId) {
		requireNonNull(database);
		requireNonNull(account);
		requireNonNull(legacyFileUploadId);

		UUID pageId = UUID.randomUUID();
		UUID pageSectionId = UUID.randomUUID();
		UUID pageRowColumnRowId = UUID.randomUUID();
		UUID pageRowColumnId = UUID.randomUUID();
		UUID pageRowCallToActionRowId = UUID.randomUUID();
		UUID pageRowCallToActionId = UUID.randomUUID();

		database.execute("INSERT INTO page_group (page_group_id) VALUES (?)", pageId);
		database.execute("""
				INSERT INTO page (page_id,name,url_name,page_status_id,image_file_upload_id,institution_id,
				  created_by_account_id,page_group_id)
				VALUES (?,?,?,'DRAFT',?,?,?,?)
				""", pageId, "Migration Test Page", format("migration-test-%s", pageId), legacyFileUploadId,
				account.getInstitutionId(), account.getAccountId(), pageId);
		database.execute("""
				INSERT INTO page_section (page_section_id,page_id,name,background_color_id,display_order,created_by_account_id)
				VALUES (?,?,'Migration Test','WHITE',0,?)
				""", pageSectionId, pageId, account.getAccountId());
		database.execute("""
				INSERT INTO page_row (page_row_id,page_section_id,row_type_id,display_order,created_by_account_id)
				VALUES (?,?,'CUSTOM_ROW',0,?)
				""", pageRowColumnRowId, pageSectionId, account.getAccountId());
		database.execute("""
				INSERT INTO page_row_column (page_row_column_id,page_row_id,image_file_upload_id,column_display_order)
				VALUES (?,?,?,0)
				""", pageRowColumnId, pageRowColumnRowId, legacyFileUploadId);
		database.execute("""
				INSERT INTO page_row (page_row_id,page_section_id,row_type_id,display_order,created_by_account_id)
				VALUES (?,?,'CALL_TO_ACTION_BLOCK',1,?)
				""", pageRowCallToActionRowId, pageSectionId, account.getAccountId());
		database.execute("""
				INSERT INTO page_row_call_to_action
				  (page_row_call_to_action_id,page_row_id,headline,description,button_text,button_url,image_file_upload_id)
				VALUES (?,?,'Headline','Description','Button','https://example.com',?)
				""", pageRowCallToActionId, pageRowCallToActionRowId, legacyFileUploadId);

		return new PageBuilderReferenceIds(pageId, pageRowColumnId, pageRowCallToActionId);
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

	@Nonnull
	protected UUID createLegacyImageFileUpload(@Nonnull Database database,
																						 @Nonnull FakeUploadManager fakeUploadManager,
																						 @Nonnull Account account,
																						 @Nonnull byte[] imageData,
																						 @Nonnull String filename) {
		return createLegacyImageFileUpload(database, fakeUploadManager, account, imageData, filename,
				FileUploadTypeId.GROUP_SESSION_IMAGE, "image/png");
	}

	@Nonnull
	protected UUID createLegacyImageFileUpload(@Nonnull Database database,
																						 @Nonnull FakeUploadManager fakeUploadManager,
																						 @Nonnull Account account,
																						 @Nonnull byte[] imageData,
																						 @Nonnull String filename,
																						 @Nonnull FileUploadTypeId fileUploadTypeId,
																						 @Nonnull String contentType) {
		requireNonNull(database);
		requireNonNull(fakeUploadManager);
		requireNonNull(account);
		requireNonNull(imageData);
		requireNonNull(filename);
		requireNonNull(fileUploadTypeId);
		requireNonNull(contentType);

		UUID fileUploadId = UUID.randomUUID();
		String storageKey = format("legacy-image-migration-test/%s/%s", fileUploadId, filename);
		fakeUploadManager.seed(storageKey, imageData);

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
				contentType,
				imageData.length);

		return fileUploadId;
	}

	@Nonnull
	protected AdminContent createContent(@Nonnull AdminContentService adminContentService,
																			 @Nonnull Account account,
																			 @Nonnull UUID legacyFileUploadId,
																			 @Nonnull String titleSuffix) {
		requireNonNull(adminContentService);
		requireNonNull(account);
		requireNonNull(legacyFileUploadId);
		requireNonNull(titleSuffix);

		CreateContentRequest request = new CreateContentRequest();
		request.setContentTypeId(ContentTypeId.ARTICLE);
		request.setTitle(format("Image migration test %s %s", titleSuffix, UUID.randomUUID()));
		request.setAuthor("Test Author");
		request.setDescription("Image migration test description.");
		request.setPublishStartDate(LocalDate.now());
		request.setPublishRecurring(false);
		request.setSharedFlag(false);
		request.setContentVisibilityTypeId(ContentVisibilityTypeId.PUBLIC);
		request.setContentAudienceTypeIds(Set.of(ContentAudienceTypeId.MYSELF));
		request.setImageFileUploadId(legacyFileUploadId);

		return adminContentService.createContent(account, request);
	}

	@Nonnull
	protected UUID createGroupSession(@Nonnull GroupSessionService groupSessionService,
																		@Nonnull Account account,
																		@Nonnull UUID legacyFileUploadId,
																		@Nonnull String urlNameSuffix) {
		requireNonNull(groupSessionService);
		requireNonNull(account);
		requireNonNull(legacyFileUploadId);
		requireNonNull(urlNameSuffix);

		CreateGroupSessionRequest request = new CreateGroupSessionRequest();
		request.setInstitutionId(InstitutionId.COBALT);
		request.setGroupSessionSchedulingSystemId(GroupSessionSchedulingSystemId.COBALT);
		request.setGroupSessionLocationTypeId(GroupSessionLocationTypeId.IN_PERSON);
		request.setSubmitterAccountId(account.getAccountId());
		request.setTitle("Image migration test");
		request.setDescription("Image migration test description.");
		request.setUrlName(format("image-migration-test-%s-%s", urlNameSuffix, UUID.randomUUID()));
		request.setInPersonLocation("Test location");
		request.setFacilitatorName("Test Facilitator");
		request.setFacilitatorEmailAddress("facilitator@example.com");
		request.setStartDateTime(LocalDateTime.now().plusDays(7));
		request.setEndDateTime(LocalDateTime.now().plusDays(7).plusHours(1));
		request.setGroupSessionVisibilityTypeId(GroupSessionVisibilityTypeId.PUBLIC);
		request.setDifferentEmailAddressForNotifications(false);
		request.setSingleSessionFlag(true);
		request.setSendFollowupEmail(false);
		request.setSendReminderEmail(false);
		request.setImageFileUploadId(legacyFileUploadId);

		return groupSessionService.createGroupSession(request, account);
	}

	@Nonnull
	protected Image findImageById(@Nonnull Database database,
																@Nonnull UUID imageId) {
		requireNonNull(database);
		requireNonNull(imageId);

		return database.queryForObject("""
				SELECT *
				FROM v_image
				WHERE image_id=?
				""", Image.class, imageId).get();
	}

	@Nonnull
	protected ContentImageState findContentImageState(@Nonnull Database database,
																										@Nonnull UUID contentId) {
		requireNonNull(database);
		requireNonNull(contentId);

		return database.queryForObject("""
				SELECT content_id, image_id, image_file_upload_id
				FROM v_admin_content
				WHERE content_id=?
				""", ContentImageState.class, contentId).get();
	}

	@Nonnull
	protected byte[] createPngImage(@Nonnull Integer width,
																	@Nonnull Integer height) {
		requireNonNull(width);
		requireNonNull(height);

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();

		try {
			graphics.setColor(new Color(30, 90, 140));
			graphics.fillRect(0, 0, width, height);
			graphics.setColor(new Color(230, 240, 210));
			graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
		} finally {
			graphics.dispose();
		}

		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", byteArrayOutputStream);
			return byteArrayOutputStream.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	protected static class FakeUploadManagerModule extends AbstractModule {
		@Provides
		@Singleton
		@Nonnull
		public UploadManager provideUploadManager(@Nonnull Configuration configuration,
																							@Nonnull Strings strings) {
			requireNonNull(configuration);
			requireNonNull(strings);

			return new FakeUploadManager(configuration, strings);
		}
	}

	protected static class ContentImageState {
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

	protected static class PageBuilderReferenceIds {
		@Nonnull
		private final UUID pageId;
		@Nonnull
		private final UUID pageRowColumnId;
		@Nonnull
		private final UUID pageRowCallToActionId;

		public PageBuilderReferenceIds(@Nonnull UUID pageId,
														 @Nonnull UUID pageRowColumnId,
														 @Nonnull UUID pageRowCallToActionId) {
			this.pageId = requireNonNull(pageId);
			this.pageRowColumnId = requireNonNull(pageRowColumnId);
			this.pageRowCallToActionId = requireNonNull(pageRowCallToActionId);
		}

		@Nonnull
		public UUID getPageId() {
			return this.pageId;
		}

		@Nonnull
		public UUID getPageRowColumnId() {
			return this.pageRowColumnId;
		}

		@Nonnull
		public UUID getPageRowCallToActionId() {
			return this.pageRowCallToActionId;
		}
	}

	protected static class FakeUploadManager extends UploadManager {
		@Nonnull
		private final Map<String, byte[]> objects;

		public FakeUploadManager(@Nonnull Configuration configuration,
														 @Nonnull Strings strings) {
			super(configuration, strings);

			this.objects = new HashMap<>();
		}

		public void seed(@Nonnull String storageKey,
										 @Nonnull byte[] data) {
			requireNonNull(storageKey);
			requireNonNull(data);

			this.objects.put(storageKey, data);
		}

		public Integer getStoredObjectCount() {
			return this.objects.size();
		}

		@Nonnull
		@Override
		public PresignedUpload createPresignedUpload(@Nonnull String key,
																								 @Nonnull String contentType,
																								 @Nonnull Boolean publicRead,
																								 @Nullable Map<String, String> metadata) {
			requireNonNull(key);
			requireNonNull(contentType);
			requireNonNull(publicRead);

			return new PresignedUpload("PUT", format("https://example.com/%s", key), contentType, Instant.now().plusSeconds(3600), Map.of());
		}

		@Nonnull
		@Override
		public void downloadFileLocatedByStorageKey(@Nonnull String storageKey,
																								@Nonnull BufferedOutputStream bufferedOutputStream) {
			requireNonNull(storageKey);
			requireNonNull(bufferedOutputStream);

			byte[] data = this.objects.get(storageKey);

			if (data == null)
				throw new IllegalArgumentException(format("No fake object exists for storage key '%s'.", storageKey));

			try {
				bufferedOutputStream.write(data);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Nonnull
		@Override
		public byte[] downloadFileLocatedByStorageKey(@Nonnull String storageKey) {
			requireNonNull(storageKey);

			byte[] data = this.objects.get(storageKey);

			if (data == null)
				throw new IllegalArgumentException(format("No fake object exists for storage key '%s'.", storageKey));

			return data;
		}

		@Override
		public void uploadFileLocatedByStorageKey(@Nonnull String storageKey,
																							@Nonnull String contentType,
																							@Nonnull byte[] data,
																							@Nonnull Boolean publicRead,
																							@Nullable Map<String, String> metadata) {
			requireNonNull(storageKey);
			requireNonNull(contentType);
			requireNonNull(data);
			requireNonNull(publicRead);

			this.objects.put(storageKey, data);
		}
	}
}
