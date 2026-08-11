package com.cobaltplatform.api.util;

import com.cobaltplatform.api.Configuration;
import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.model.api.request.CreateGroupSessionRequest;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.GroupSessionLocationType.GroupSessionLocationTypeId;
import com.cobaltplatform.api.model.db.GroupSessionSchedulingSystem.GroupSessionSchedulingSystemId;
import com.cobaltplatform.api.model.db.GroupSessionVisibilityType.GroupSessionVisibilityTypeId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.service.GroupSessionService;
import com.cobaltplatform.api.service.MediaImageMigrationService;
import com.cobaltplatform.api.service.MediaImageMigrationService.LegacyImageMigrationBatchResult;
import com.cobaltplatform.api.service.MediaImageMigrationService.LegacyImageMigrationInstitutionReport;
import com.cobaltplatform.api.service.MediaImageMigrationService.LegacyImageReferenceMigrationResult;
import com.cobaltplatform.api.service.MediaImageMigrationService.LegacyImageMigrationResult;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.soklet.util.LoggingUtils.initializeLogback;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Local-only runner for exercising legacy group-session image migration against local DB/S3.
 *
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class MediaImageMigrationLocalRunner {
	public static void main(@Nullable String[] args) throws Exception {
		initializeLogback(java.nio.file.Paths.get("config/local/logback.xml"));

		boolean reportOnly = parseBoolean(property("reportOnly").orElse("false"));

		if (!reportOnly && !parseBoolean(property("commit").orElse("false")))
			throw new IllegalStateException("Refusing to write local DB/S3 data. Re-run with -Dcommit=true.");

		IntegrationTestExecutor.runTransactionallyAndCommit((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			MediaImageMigrationService mediaImageMigrationService = app.getInjector().getInstance(MediaImageMigrationService.class);
			UploadManager uploadManager = app.getInjector().getInstance(UploadManager.class);
			Configuration configuration = app.getInjector().getInstance(Configuration.class);
			boolean institutionMode = property("institutionId").isPresent()
					&& !property("groupSessionId").isPresent()
					&& !property("contentId").isPresent()
					&& !property("pageId").isPresent()
					&& !property("pageRowColumnId").isPresent()
					&& !property("pageRowCallToActionId").isPresent()
					&& !parseBoolean(property("seedLegacyGroupSession").orElse("false"));

			if (reportOnly && !institutionMode)
				throw new IllegalArgumentException("Report-only mode requires -DinstitutionId=<id> without a single-reference ID or -DseedLegacyGroupSession.");

			if (institutionMode) {
				InstitutionId institutionId = InstitutionId.valueOf(property("institutionId").get());
				Account account = findAccount(database, property("accountId").map(UUID::fromString).orElse(null), institutionId, null, null);
				LegacyImageMigrationInstitutionReport report = mediaImageMigrationService.findLegacyImageMigrationReport(institutionId);

				printInstitutionReport("Report", report);

				if (reportOnly) {
					database.currentTransaction().get().setRollbackOnly(true);
					return;
				}

				if (!Boolean.TRUE.equals(report.getImageRepositoryEnabled())
						&& !parseBoolean(property("ignoreImageRepositoryEnabled").orElse("false")))
					throw new IllegalStateException(format("Institution %s has image_repository_enabled=false. Re-run with -DignoreImageRepositoryEnabled=true to override.", institutionId));

				Integer limit = parseInt(property("limit").orElseThrow(() -> new IllegalArgumentException("Provide -Dlimit=<count> for institution batch migration.")));
				LegacyImageMigrationBatchResult batchResult = mediaImageMigrationService.migratePendingLegacyImagesForInstitution(account, limit);

				printBatchResult(batchResult);
				return;
			}

			if (property("contentId").isPresent()) {
				UUID contentId = UUID.fromString(property("contentId").get());
				Account account = findAccount(database, property("accountId").map(UUID::fromString).orElse(null), null, null, contentId);
				ContentImageState beforeState = findContentImageState(database, contentId)
						.orElseThrow(() -> new IllegalArgumentException(format("Content ID '%s' was not found.", contentId)));

				System.out.printf("Migrating content %s%n", contentId);
				System.out.printf("Before: imageId=%s imageFileUploadId=%s%n", beforeState.getImageId(), beforeState.getImageFileUploadId());

				LegacyImageMigrationResult migrationResult = mediaImageMigrationService.migrateLegacyContentImage(account, contentId);
				ContentImageState afterState = findContentImageState(database, contentId).get();
				LegacyImageMigrationAudit audit = findLegacyImageMigrationAudit(database, migrationResult.getLegacyFileUploadId()).orElse(null);

				printSingleMigrationResult(migrationResult);
				System.out.printf("After: imageId=%s imageFileUploadId=%s%n", afterState.getImageId(), afterState.getImageFileUploadId());

				if (audit != null)
					printAudit(audit);

				return;
			}

			if (property("pageId").isPresent() || property("pageRowColumnId").isPresent()
					|| property("pageRowCallToActionId").isPresent()) {
				String referencePropertyName = property("pageId").isPresent() ? "pageId"
						: property("pageRowColumnId").isPresent() ? "pageRowColumnId" : "pageRowCallToActionId";
				UUID referenceId = UUID.fromString(property(referencePropertyName).get());
				PageBuilderImageState beforeState = findPageBuilderImageState(database, referencePropertyName, referenceId)
						.orElseThrow(() -> new IllegalArgumentException(format("Page-builder reference '%s' was not found.", referenceId)));
				Account account = findAccount(database, property("accountId").map(UUID::fromString).orElse(null),
						beforeState.getInstitutionId(), null, null);

				System.out.printf("Migrating %s %s%n", referencePropertyName, referenceId);
				System.out.printf("Before: imageId=%s imageFileUploadId=%s%n", beforeState.getImageId(), beforeState.getImageFileUploadId());

				LegacyImageMigrationResult migrationResult = switch (referencePropertyName) {
					case "pageId" -> mediaImageMigrationService.migrateLegacyPageImage(account, referenceId);
					case "pageRowColumnId" -> mediaImageMigrationService.migrateLegacyPageRowColumnImage(account, referenceId);
					case "pageRowCallToActionId" -> mediaImageMigrationService.migrateLegacyPageRowCallToActionImage(account, referenceId);
					default -> throw new IllegalArgumentException(format("Unsupported reference property '%s'.", referencePropertyName));
				};
				PageBuilderImageState afterState = findPageBuilderImageState(database, referencePropertyName, referenceId).get();
				LegacyImageMigrationAudit audit = findLegacyImageMigrationAudit(database, migrationResult.getLegacyFileUploadId()).orElse(null);

				printSingleMigrationResult(migrationResult);
				System.out.printf("After: imageId=%s imageFileUploadId=%s%n", afterState.getImageId(), afterState.getImageFileUploadId());
				if (audit != null)
					printAudit(audit);
				return;
			}

			UUID groupSessionId;
			Account account;

			if (parseBoolean(property("seedLegacyGroupSession").orElse("false"))) {
				InstitutionId institutionId = InstitutionId.valueOf(property("institutionId").orElse(InstitutionId.COBALT.name()));
				account = findAccount(database, property("accountId").map(UUID::fromString).orElse(null), institutionId, null, null);
				groupSessionId = seedLegacyGroupSession(database, groupSessionService, uploadManager, configuration, account);
			} else {
				groupSessionId = UUID.fromString(property("groupSessionId")
						.orElseThrow(() -> new IllegalArgumentException("Provide a supported single-reference ID or -DseedLegacyGroupSession=true.")));
				account = findAccount(database, property("accountId").map(UUID::fromString).orElse(null), null, groupSessionId, null);
			}

			GroupSessionImageState beforeState = findGroupSessionImageState(database, groupSessionId)
					.orElseThrow(() -> new IllegalArgumentException(format("Group Session ID '%s' was not found.", groupSessionId)));

			System.out.printf("Migrating group session %s%n", groupSessionId);
			System.out.printf("Before: imageId=%s imageFileUploadId=%s%n", beforeState.getImageId(), beforeState.getImageFileUploadId());

			LegacyImageMigrationResult migrationResult = mediaImageMigrationService.migrateLegacyGroupSessionImage(account, groupSessionId);
			GroupSessionImageState afterState = findGroupSessionImageState(database, groupSessionId).get();
			LegacyImageMigrationAudit audit = findLegacyImageMigrationAudit(database, migrationResult.getLegacyFileUploadId()).orElse(null);

			printSingleMigrationResult(migrationResult);
			System.out.printf("After: imageId=%s imageFileUploadId=%s%n", afterState.getImageId(), afterState.getImageFileUploadId());

			if (audit != null)
				printAudit(audit);
		});
	}

	protected static void printBatchResult(@Nonnull LegacyImageMigrationBatchResult batchResult) {
		requireNonNull(batchResult);

		System.out.printf("Batch migration: institutionId=%s requestedLimit=%s processed=%s%n",
				batchResult.getInstitutionId(), batchResult.getRequestedLimit(), batchResult.getProcessedCount());

		for (LegacyImageReferenceMigrationResult result : batchResult.getMigrationResults()) {
			LegacyImageMigrationResult migrationResult = result.getLegacyImageMigrationResult();
			System.out.printf("Processed referenceType=%s referenceId=%s legacyFileUploadId=%s status=%s rawImageId=%s crops=%s thumbnails=%s qualityMessages=%s%n",
					result.getLegacyImageReference().getReferenceTypeId(), result.getLegacyImageReference().getReferenceId(),
					migrationResult.getLegacyFileUploadId(), migrationResult.getMigrationStatusId(),
					migrationResult.getRawImageId(), migrationResult.getCropImageIdsByFileUploadTypeId(),
					migrationResult.getThumbnailImageIdsByCropFileUploadTypeId(), migrationResult.getQualityMessages());
		}

		printInstitutionReport("Before", batchResult.getBeforeReport());
		printInstitutionReport("After", batchResult.getAfterReport());
	}

	protected static void printInstitutionReport(@Nonnull String label,
																							 @Nonnull LegacyImageMigrationInstitutionReport report) {
		requireNonNull(label);
		requireNonNull(report);

		System.out.printf("%s: institutionId=%s imageRepositoryEnabled=%s total=%s currentLegacyReferences=%s currentLegacyContentReferences=%s currentLegacyGroupSessionReferences=%s currentLegacyPageReferences=%s currentLegacyPageRowColumnReferences=%s currentLegacyPageRowCallToActionReferences=%s pending=%s pendingContentReferences=%s pendingGroupSessionReferences=%s pendingPageReferences=%s pendingPageRowColumnReferences=%s pendingPageRowCallToActionReferences=%s attempted=%s rawImported=%s variantsGenerated=%s lowFidelity=%s unmigratable=%s needsReview=%s replaced=%s rewiredContent=%s rewiredGroupSessions=%s rewiredPages=%s rewiredPageRowColumns=%s rewiredPageRowCallToActions=%s%n",
				label,
				report.getInstitutionId(),
				report.getImageRepositoryEnabled(),
				report.getTotalCount(),
				report.getCurrentLegacyReferenceCount(),
				report.getCurrentLegacyContentReferenceCount(),
				report.getCurrentLegacyGroupSessionReferenceCount(),
				report.getCurrentLegacyPageReferenceCount(),
				report.getCurrentLegacyPageRowColumnReferenceCount(),
				report.getCurrentLegacyPageRowCallToActionReferenceCount(),
				report.getPendingCount(),
				report.getPendingContentReferenceCount(),
				report.getPendingGroupSessionReferenceCount(),
				report.getPendingPageReferenceCount(),
				report.getPendingPageRowColumnReferenceCount(),
				report.getPendingPageRowCallToActionReferenceCount(),
				report.getAttemptedCount(),
				report.getRawImportedCount(),
				report.getVariantsGeneratedCount(),
				report.getLowFidelityCount(),
				report.getUnmigratableCount(),
				report.getNeedsReviewCount(),
				report.getReplacedCount(),
				report.getRewiredContentCount(),
				report.getRewiredGroupSessionCount(),
				report.getRewiredPageCount(),
				report.getRewiredPageRowColumnCount(),
				report.getRewiredPageRowCallToActionCount());
	}

	protected static void printSingleMigrationResult(@Nonnull LegacyImageMigrationResult migrationResult) {
		requireNonNull(migrationResult);

		System.out.printf("Migration status: %s%n", migrationResult.getMigrationStatusId());
		System.out.printf("Source: %sx%s hash=%s%n", migrationResult.getSourceWidth(), migrationResult.getSourceHeight(), migrationResult.getSourceImageHash());
		System.out.printf("Raw image: imageId=%s fileUploadId=%s%n", migrationResult.getRawImageId(), migrationResult.getRawFileUploadId());
		System.out.printf("Crop images: %s%n", migrationResult.getCropImageIdsByFileUploadTypeId());
		System.out.printf("Thumbnail images: %s%n", migrationResult.getThumbnailImageIdsByCropFileUploadTypeId());
		System.out.printf("Quality messages: %s%n", migrationResult.getQualityMessages());
	}

	protected static void printAudit(@Nonnull LegacyImageMigrationAudit audit) {
		requireNonNull(audit);

		System.out.printf("Audit: legacyFileUploadId=%s status=%s rawImageId=%s crop16x9ImageId=%s thumbnail16x9ImageId=%s crop4x3ImageId=%s thumbnail4x3ImageId=%s crop1x1ImageId=%s thumbnail1x1ImageId=%s%n",
				audit.getLegacyFileUploadId(), audit.getLegacyImageMigrationStatusId(), audit.getRawImageId(),
				audit.getCrop16X9ImageId(), audit.getThumbnail16X9ImageId(), audit.getCrop4X3ImageId(),
				audit.getThumbnail4X3ImageId(), audit.getCrop1X1ImageId(), audit.getThumbnail1X1ImageId());
	}

	@Nonnull
	protected static UUID seedLegacyGroupSession(@Nonnull Database database,
																						 @Nonnull GroupSessionService groupSessionService,
																						 @Nonnull UploadManager uploadManager,
																						 @Nonnull Configuration configuration,
																						 @Nonnull Account account) {
		requireNonNull(database);
		requireNonNull(groupSessionService);
		requireNonNull(uploadManager);
		requireNonNull(configuration);
		requireNonNull(account);

		Integer sourceWidth = parseInt(property("sourceWidth").orElse("1600"));
		Integer sourceHeight = parseInt(property("sourceHeight").orElse("900"));
		byte[] imageData = createPngImage(sourceWidth, sourceHeight);
		UUID fileUploadId = UUID.randomUUID();
		String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		String filename = format("legacy-image-migration-local-%s-%sx%s.png", runId, sourceWidth, sourceHeight);
		String storageKey = format("legacy-image-migration/local/%s/%s", fileUploadId, filename);

		uploadManager.uploadFileLocatedByStorageKey(storageKey, "image/png", imageData, true, Map.of(
				"local-runner", "true",
				"legacy-file-upload-id", fileUploadId.toString()));

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
				FileUploadTypeId.GROUP_SESSION_IMAGE,
				FileUploadStatusId.UPLOADED,
				account.getAccountId(),
				account.getInstitutionId(),
				format("%s/%s/%s", configuration.getAmazonS3BaseUrl(), configuration.getAmazonS3BucketName(), storageKey),
				configuration.getAmazonS3BucketName(),
				storageKey,
				configuration.getAmazonS3Region().id(),
				filename,
				"image/png",
				imageData.length);

		CreateGroupSessionRequest request = new CreateGroupSessionRequest();
		request.setInstitutionId(account.getInstitutionId());
		request.setGroupSessionSchedulingSystemId(GroupSessionSchedulingSystemId.COBALT);
		request.setGroupSessionLocationTypeId(GroupSessionLocationTypeId.IN_PERSON);
		request.setSubmitterAccountId(account.getAccountId());
		request.setTitle(format("Image migration local runner %s", runId));
		request.setDescription("Local image migration runner seeded legacy group session.");
		request.setUrlName(format("image-migration-local-%s-%s", runId, UUID.randomUUID()));
		request.setInPersonLocation("Local migration runner");
		request.setFacilitatorName("Local Migration Runner");
		request.setFacilitatorEmailAddress("local-migration-runner@example.com");
		request.setStartDateTime(LocalDateTime.now().plusDays(7));
		request.setEndDateTime(LocalDateTime.now().plusDays(7).plusHours(1));
		request.setGroupSessionVisibilityTypeId(GroupSessionVisibilityTypeId.PUBLIC);
		request.setDifferentEmailAddressForNotifications(false);
		request.setSingleSessionFlag(true);
		request.setSendFollowupEmail(false);
		request.setSendReminderEmail(false);
		request.setImageFileUploadId(fileUploadId);

		UUID groupSessionId = groupSessionService.createGroupSession(request, account);

		System.out.printf("Seeded group session %s with legacy file upload %s (%sx%s)%n",
				groupSessionId, fileUploadId, sourceWidth, sourceHeight);

		return groupSessionId;
	}

	@Nonnull
	protected static Account findAccount(@Nonnull Database database,
																			 @Nullable UUID accountId,
																			 @Nullable InstitutionId institutionId,
																			 @Nullable UUID groupSessionId,
																			 @Nullable UUID contentId) {
		requireNonNull(database);

		if (accountId != null)
			return database.queryForObject("""
					SELECT *
					FROM v_account
					WHERE account_id=?
					""", Account.class, accountId).orElseThrow(() -> new IllegalArgumentException(format("Account ID '%s' was not found.", accountId)));

		if (groupSessionId != null)
			return database.queryForObject("""
					SELECT a.*
					FROM v_account a, v_group_session gs
					WHERE gs.group_session_id=?
					AND a.institution_id=gs.institution_id
					LIMIT 1
					""", Account.class, groupSessionId).orElseThrow(() -> new IllegalArgumentException(format("No account found for group session '%s'.", groupSessionId)));

		if (contentId != null)
			return database.queryForObject("""
					SELECT a.*
					FROM v_account a, v_admin_content c
					WHERE c.content_id=?
					AND a.institution_id=c.owner_institution_id
					LIMIT 1
					""", Account.class, contentId).orElseThrow(() -> new IllegalArgumentException(format("No account found for content '%s'.", contentId)));

		InstitutionId normalizedInstitutionId = institutionId == null ? InstitutionId.COBALT : institutionId;

		return database.queryForObject("""
				SELECT *
				FROM v_account
				WHERE institution_id=?
				LIMIT 1
				""", Account.class, normalizedInstitutionId)
				.orElseThrow(() -> new IllegalArgumentException(format("No account found for institution '%s'.", normalizedInstitutionId)));
	}

	@Nonnull
	protected static Optional<ContentImageState> findContentImageState(@Nonnull Database database,
																																		 @Nonnull UUID contentId) {
		requireNonNull(database);
		requireNonNull(contentId);

		return database.queryForObject("""
				SELECT content_id, image_id, image_file_upload_id
				FROM v_admin_content
				WHERE content_id=?
				""", ContentImageState.class, contentId);
	}

	@Nonnull
	protected static Optional<GroupSessionImageState> findGroupSessionImageState(@Nonnull Database database,
																																							@Nonnull UUID groupSessionId) {
		requireNonNull(database);
		requireNonNull(groupSessionId);

		return database.queryForObject("""
				SELECT group_session_id, image_id, image_file_upload_id
				FROM v_group_session
				WHERE group_session_id=?
				""", GroupSessionImageState.class, groupSessionId);
	}

	@Nonnull
	protected static Optional<PageBuilderImageState> findPageBuilderImageState(@Nonnull Database database,
																														 @Nonnull String referencePropertyName,
																														 @Nonnull UUID referenceId) {
		requireNonNull(database);
		requireNonNull(referencePropertyName);
		requireNonNull(referenceId);

		if ("pageId".equals(referencePropertyName))
			return database.queryForObject("""
					SELECT p.page_id AS reference_id, p.institution_id, p.image_id, p.image_file_upload_id
					FROM page p WHERE p.page_id=?
					""", PageBuilderImageState.class, referenceId);

		if ("pageRowColumnId".equals(referencePropertyName))
			return database.queryForObject("""
					SELECT prc.page_row_column_id AS reference_id, p.institution_id, prc.image_id, prc.image_file_upload_id
					FROM page_row_column prc
					JOIN page_row pr ON pr.page_row_id=prc.page_row_id
					JOIN page_section ps ON ps.page_section_id=pr.page_section_id
					JOIN page p ON p.page_id=ps.page_id
					WHERE prc.page_row_column_id=?
					""", PageBuilderImageState.class, referenceId);

		if ("pageRowCallToActionId".equals(referencePropertyName))
			return database.queryForObject("""
					SELECT prcta.page_row_call_to_action_id AS reference_id, p.institution_id,
					  prcta.image_id, prcta.image_file_upload_id
					FROM page_row_call_to_action prcta
					JOIN page_row pr ON pr.page_row_id=prcta.page_row_id
					JOIN page_section ps ON ps.page_section_id=pr.page_section_id
					JOIN page p ON p.page_id=ps.page_id
					WHERE prcta.page_row_call_to_action_id=?
					""", PageBuilderImageState.class, referenceId);

		throw new IllegalArgumentException(format("Unsupported reference property '%s'.", referencePropertyName));
	}

	@Nonnull
	protected static Optional<LegacyImageMigrationAudit> findLegacyImageMigrationAudit(@Nonnull Database database,
																																										@Nonnull UUID legacyFileUploadId) {
		requireNonNull(database);
		requireNonNull(legacyFileUploadId);

		LegacyImageMigrationAudit audit = database.queryForObject("""
				SELECT legacy_file_upload_id,
				       legacy_image_migration_status_id,
				       raw_image_id
				FROM legacy_image_migration
				WHERE legacy_file_upload_id=?
				""", LegacyImageMigrationAudit.class, legacyFileUploadId).orElse(null);

		if (audit == null)
			return Optional.empty();

		audit.setCrop16X9ImageId(findLegacyMigrationImageId(database, legacyFileUploadId, "crop_16x9_image_id").orElse(null));
		audit.setThumbnail16X9ImageId(findLegacyMigrationImageId(database, legacyFileUploadId, "thumbnail_16x9_image_id").orElse(null));
		audit.setCrop4X3ImageId(findLegacyMigrationImageId(database, legacyFileUploadId, "crop_4x3_image_id").orElse(null));
		audit.setThumbnail4X3ImageId(findLegacyMigrationImageId(database, legacyFileUploadId, "thumbnail_4x3_image_id").orElse(null));
		audit.setCrop1X1ImageId(findLegacyMigrationImageId(database, legacyFileUploadId, "crop_1x1_image_id").orElse(null));
		audit.setThumbnail1X1ImageId(findLegacyMigrationImageId(database, legacyFileUploadId, "thumbnail_1x1_image_id").orElse(null));

		return Optional.of(audit);
	}

	@Nonnull
	protected static Optional<UUID> findLegacyMigrationImageId(@Nonnull Database database,
																														 @Nonnull UUID legacyFileUploadId,
																														 @Nonnull String imageIdColumnName) {
		requireNonNull(database);
		requireNonNull(legacyFileUploadId);
		requireNonNull(imageIdColumnName);

		return database.queryForObject(format("""
				SELECT %s
				FROM legacy_image_migration
				WHERE legacy_file_upload_id=?
				""", imageIdColumnName), UUID.class, legacyFileUploadId);
	}

	@Nonnull
	protected static byte[] createPngImage(@Nonnull Integer width,
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

	@Nonnull
	protected static Optional<String> property(@Nonnull String name) {
		requireNonNull(name);

		String value = trimToNull(System.getProperty(name));

		if (value == null)
			value = trimToNull(System.getenv(name));

		return Optional.ofNullable(value);
	}

	public static class GroupSessionImageState {
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

	public static class ContentImageState {
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

	public static class PageBuilderImageState {
		@Nullable
		private UUID referenceId;
		@Nullable
		private InstitutionId institutionId;
		@Nullable
		private UUID imageId;
		@Nullable
		private UUID imageFileUploadId;

		@Nullable
		public UUID getReferenceId() {
			return this.referenceId;
		}

		public void setReferenceId(@Nullable UUID referenceId) {
			this.referenceId = referenceId;
		}

		@Nullable
		public InstitutionId getInstitutionId() {
			return this.institutionId;
		}

		public void setInstitutionId(@Nullable InstitutionId institutionId) {
			this.institutionId = institutionId;
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

	public static class LegacyImageMigrationAudit {
		@Nullable
		private UUID legacyFileUploadId;
		@Nullable
		private String legacyImageMigrationStatusId;
		@Nullable
		private UUID rawImageId;
		@Nullable
		private UUID crop16X9ImageId;
		@Nullable
		private UUID thumbnail16X9ImageId;
		@Nullable
		private UUID crop4X3ImageId;
		@Nullable
		private UUID thumbnail4X3ImageId;
		@Nullable
		private UUID crop1X1ImageId;
		@Nullable
		private UUID thumbnail1X1ImageId;

		@Nullable
		public UUID getLegacyFileUploadId() {
			return this.legacyFileUploadId;
		}

		public void setLegacyFileUploadId(@Nullable UUID legacyFileUploadId) {
			this.legacyFileUploadId = legacyFileUploadId;
		}

		@Nullable
		public String getLegacyImageMigrationStatusId() {
			return this.legacyImageMigrationStatusId;
		}

		public void setLegacyImageMigrationStatusId(@Nullable String legacyImageMigrationStatusId) {
			this.legacyImageMigrationStatusId = legacyImageMigrationStatusId;
		}

		@Nullable
		public UUID getRawImageId() {
			return this.rawImageId;
		}

		public void setRawImageId(@Nullable UUID rawImageId) {
			this.rawImageId = rawImageId;
		}

		@Nullable
		public UUID getCrop16X9ImageId() {
			return this.crop16X9ImageId;
		}

		public void setCrop16X9ImageId(@Nullable UUID crop16X9ImageId) {
			this.crop16X9ImageId = crop16X9ImageId;
		}

		@Nullable
		public UUID getThumbnail16X9ImageId() {
			return this.thumbnail16X9ImageId;
		}

		public void setThumbnail16X9ImageId(@Nullable UUID thumbnail16X9ImageId) {
			this.thumbnail16X9ImageId = thumbnail16X9ImageId;
		}

		@Nullable
		public UUID getCrop4X3ImageId() {
			return this.crop4X3ImageId;
		}

		public void setCrop4X3ImageId(@Nullable UUID crop4X3ImageId) {
			this.crop4X3ImageId = crop4X3ImageId;
		}

		@Nullable
		public UUID getThumbnail4X3ImageId() {
			return this.thumbnail4X3ImageId;
		}

		public void setThumbnail4X3ImageId(@Nullable UUID thumbnail4X3ImageId) {
			this.thumbnail4X3ImageId = thumbnail4X3ImageId;
		}

		@Nullable
		public UUID getCrop1X1ImageId() {
			return this.crop1X1ImageId;
		}

		public void setCrop1X1ImageId(@Nullable UUID crop1X1ImageId) {
			this.crop1X1ImageId = crop1X1ImageId;
		}

		@Nullable
		public UUID getThumbnail1X1ImageId() {
			return this.thumbnail1X1ImageId;
		}

		public void setThumbnail1X1ImageId(@Nullable UUID thumbnail1X1ImageId) {
			this.thumbnail1X1ImageId = thumbnail1X1ImageId;
		}
	}
}
