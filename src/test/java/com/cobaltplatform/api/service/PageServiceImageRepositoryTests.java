/*
 * Copyright 2021 The University of Pennsylvania and Penn Medicine
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.cobaltplatform.api.service;

import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.model.api.request.CreatePageRequest;
import com.cobaltplatform.api.model.api.request.CreatePageRowCallToActionRequest;
import com.cobaltplatform.api.model.api.request.CreatePageRowColumnRequest;
import com.cobaltplatform.api.model.api.request.CreatePageRowRequest;
import com.cobaltplatform.api.model.api.request.DuplicatePageRequest;
import com.cobaltplatform.api.model.api.request.UpdatePageHeroRequest;
import com.cobaltplatform.api.model.api.request.UpdatePageRowCallToActionRequest;
import com.cobaltplatform.api.model.api.request.UpdatePageRowColumnRequest;
import com.cobaltplatform.api.model.api.response.PageApiResponse;
import com.cobaltplatform.api.model.api.response.PageApiResponse.PageApiResponseFactory;
import com.cobaltplatform.api.model.api.response.PageRowApiResponse;
import com.cobaltplatform.api.model.api.response.PageRowApiResponse.PageRowApiResponseFactory;
import com.cobaltplatform.api.model.api.response.PageRowColumnApiResponse;
import com.cobaltplatform.api.model.api.response.PageRowColumnApiResponse.PageRowImageApiResponseFactory;
import com.cobaltplatform.api.model.api.response.PageSiteLocationApiResponse;
import com.cobaltplatform.api.model.api.response.PageSiteLocationApiResponse.PageSiteLocationApiResponseFactory;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.Page;
import com.cobaltplatform.api.model.db.PageRowCallToAction;
import com.cobaltplatform.api.model.db.PageRowColumn;
import com.cobaltplatform.api.model.db.PageSection;
import com.cobaltplatform.api.model.db.PageStatus.PageStatusId;
import com.cobaltplatform.api.model.db.RowType.RowTypeId;
import com.cobaltplatform.api.model.db.SiteLocation.SiteLocationId;
import com.cobaltplatform.api.model.service.NavigationItem;
import com.cobaltplatform.api.model.service.PageSiteLocation;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class PageServiceImageRepositoryTests {
	@Test
	public void pageBuilderAssociationsDeriveLegacyFilesPreserveCompatibilityAndDuplicate() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumePageBuilderImageSchemaExists(database);
			PageService pageService = app.getInjector().getInstance(PageService.class);
			PageApiResponseFactory pageApiResponseFactory = app.getInjector().getInstance(PageApiResponseFactory.class);
			PageRowApiResponseFactory pageRowApiResponseFactory = app.getInjector().getInstance(PageRowApiResponseFactory.class);
			PageRowImageApiResponseFactory pageRowImageApiResponseFactory = app.getInjector().getInstance(PageRowImageApiResponseFactory.class);
			PageSiteLocationApiResponseFactory pageSiteLocationApiResponseFactory = app.getInjector().getInstance(PageSiteLocationApiResponseFactory.class);
			Account account = findExistingAdministratorAccount(database);
			ImageFixture imageFixture = createUploadedFourByThreeImageFamily(database, account);
			UUID staleLegacyFileUploadId = createLegacyPageImageFileUpload(database, account, "stale");
			UUID replacementLegacyFileUploadId = createLegacyPageImageFileUpload(database, account, "replacement");

			CreatePageRequest createPageRequest = new CreatePageRequest();
			createPageRequest.setName(format("Repository page %s", UUID.randomUUID()));
			createPageRequest.setUrlName(format("repository-page-%s", UUID.randomUUID()));
			createPageRequest.setHeadline("Hero headline");
			createPageRequest.setDescription("Hero description");
			createPageRequest.setImageId(imageFixture.getCropImageId());
			createPageRequest.setImageFileUploadId(staleLegacyFileUploadId);
			createPageRequest.setImageAltText("Placement hero alt text");
			createPageRequest.setInstitutionId(account.getInstitutionId());
			createPageRequest.setCreatedByAccountId(account.getAccountId());
			UUID pageId = pageService.createPage(createPageRequest);

			Page page = pageService.findPageById(pageId, account.getInstitutionId(), true).get();
			Assert.assertEquals(imageFixture.getCropImageId(), page.getImageId());
			Assert.assertEquals("A selected image must override a stale legacy upload ID", imageFixture.getCropFileUploadId(), page.getImageFileUploadId());
			Assert.assertEquals("Placement hero alt text", page.getImageAltText());
			Assert.assertEquals("Library alt text", page.getImage().getImageAltText());
			Assert.assertEquals(imageFixture.getThumbnailImageId(), page.getImageThumbnail().getImageId());

			PageSection pageSection = pageService.findPageSectionsByPageId(pageId, account.getInstitutionId()).get(0);
			CreatePageRowRequest createPageRowRequest = new CreatePageRowRequest();
			createPageRowRequest.setPageSectionId(pageSection.getPageSectionId());
			createPageRowRequest.setRowTypeId(RowTypeId.CUSTOM_ROW);
			createPageRowRequest.setCreatedByAccountId(account.getAccountId());
			UUID pageRowId = pageService.createPageRow(createPageRowRequest, account.getInstitutionId());

			CreatePageRowColumnRequest createColumnRequest = new CreatePageRowColumnRequest();
			createColumnRequest.setHeadline("Column headline");
			createColumnRequest.setDescription("Column description");
			createColumnRequest.setColumnDisplayOrder(0);
			createColumnRequest.setImageId(imageFixture.getCropImageId());
			createColumnRequest.setImageFileUploadId(staleLegacyFileUploadId.toString());
			createColumnRequest.setImageAltText("Placement column alt text");
			UUID pageRowColumnId = pageService.createPageRowColumn(createColumnRequest, pageRowId);

			PageRowColumn pageRowColumn = pageService.findPageRowColumnById(pageRowColumnId).get();
			Assert.assertEquals(imageFixture.getCropImageId(), pageRowColumn.getImageId());
			Assert.assertEquals(imageFixture.getCropFileUploadId(), pageRowColumn.getImageFileUploadId());
			Assert.assertEquals("Placement column alt text", pageRowColumn.getImageAltText());
			Assert.assertEquals(imageFixture.getThumbnailImageId(), pageRowColumn.getImageThumbnail().getImageId());

			CreatePageRowCallToActionRequest createCallToActionRequest = new CreatePageRowCallToActionRequest();
			createCallToActionRequest.setInstitutionId(account.getInstitutionId());
			createCallToActionRequest.setPageSectionId(pageSection.getPageSectionId());
			createCallToActionRequest.setCreatedByAccountId(account.getAccountId());
			createCallToActionRequest.setHeadline("CTA headline");
			createCallToActionRequest.setDescription("CTA description");
			createCallToActionRequest.setButtonText("Learn more");
			createCallToActionRequest.setButtonUrl("/learn-more");
			createCallToActionRequest.setImageId(imageFixture.getCropImageId());
			createCallToActionRequest.setImageFileUploadId(staleLegacyFileUploadId.toString());
			UUID callToActionPageRowId = pageService.createPageRowCallToAction(createCallToActionRequest, RowTypeId.CALL_TO_ACTION_BLOCK);

			PageRowCallToAction callToAction = pageService.findPageRowCallToActionByRowId(callToActionPageRowId).get();
			Assert.assertEquals(imageFixture.getCropImageId(), callToAction.getImageId());
			Assert.assertEquals(imageFixture.getCropFileUploadId(), callToAction.getImageFileUploadId());
			Assert.assertEquals(imageFixture.getThumbnailImageId(), callToAction.getImageThumbnail().getImageId());

			PageApiResponse pageApiResponse = pageApiResponseFactory.create(page, false);
			PageRowColumnApiResponse columnApiResponse = pageRowImageApiResponseFactory.create(pageRowColumn);
			PageRowApiResponse callToActionApiResponse = pageRowApiResponseFactory.create(
					pageService.findPageRowById(callToActionPageRowId, account.getInstitutionId()).get());
			Assert.assertEquals(imageFixture.getCropImageId(), pageApiResponse.getImage().getImageId());
			Assert.assertEquals(imageFixture.getThumbnailImageId(), pageApiResponse.getImage().getThumbnail().getImageId());
			Assert.assertEquals("Placement hero alt text", pageApiResponse.getImageAltText());
			Assert.assertEquals(imageFixture.getCropImageId(), columnApiResponse.getImage().getImageId());
			Assert.assertEquals(imageFixture.getThumbnailImageId(), columnApiResponse.getImage().getThumbnail().getImageId());
			Assert.assertEquals("Placement column alt text", columnApiResponse.getImageAltText());
			Assert.assertEquals(imageFixture.getCropImageId(), callToActionApiResponse.getImage().get().getImageId());
			Assert.assertEquals(imageFixture.getThumbnailImageId(), callToActionApiResponse.getImage().get().getThumbnail().getImageId());
			Assert.assertEquals(imageFixture.getCropFileUploadId(), callToActionApiResponse.getImageFileUploadId().get());

			database.execute("UPDATE page SET page_status_id='LIVE' WHERE page_id=?", pageId);
			database.execute("""
					INSERT INTO page_site_location
					  (page_id,site_location_id,display_order,call_to_action,created_by_account_id)
					VALUES (?,?::TEXT,0,'Learn more',?)
					""", pageId, SiteLocationId.RESOURCE, account.getAccountId());
			PageSiteLocation pageSiteLocation = pageService.findAllPagesBySiteLocation(SiteLocationId.RESOURCE,
					account.getInstitutionId()).stream().filter(location -> pageId.equals(location.getPageId())).findFirst().get();
			PageSiteLocationApiResponse pageSiteLocationApiResponse = pageSiteLocationApiResponseFactory.create(pageSiteLocation);
			NavigationItem navigationItem = pageService.findPageNavigationItemsBySiteLocationId(SiteLocationId.RESOURCE,
					account.getInstitutionId()).stream().filter(item -> pageId.equals(item.getPageId())).findFirst().get();
			Assert.assertEquals(imageFixture.getCropImageId(), pageSiteLocationApiResponse.getImageId());
			Assert.assertEquals(imageFixture.getThumbnailImageId(), pageSiteLocationApiResponse.getImage().getThumbnail().getImageId());
			Assert.assertEquals(page.getImage().getUrl(), pageSiteLocationApiResponse.getImageUrl());
			Assert.assertEquals(page.getImage().getUrl(), navigationItem.getImageUrl());

			DuplicatePageRequest duplicatePageRequest = new DuplicatePageRequest();
			duplicatePageRequest.setPageId(pageId);
			duplicatePageRequest.setName(format("Repository page duplicate %s", UUID.randomUUID()));
			duplicatePageRequest.setUrlName(format("repository-page-duplicate-%s", UUID.randomUUID()));
			duplicatePageRequest.setCreatedByAccountId(account.getAccountId());
			duplicatePageRequest.setCopyForEditing(false);
			duplicatePageRequest.setPageStatusId(PageStatusId.DRAFT);
			UUID duplicatePageId = pageService.duplicatePage(duplicatePageRequest, account.getInstitutionId());

			List<UUID> duplicateImageIds = database.queryForList("""
					SELECT image_id FROM page WHERE page_id=?
					UNION ALL
					SELECT prc.image_id FROM page_row_column prc
					JOIN page_row pr ON pr.page_row_id=prc.page_row_id
					JOIN page_section ps ON ps.page_section_id=pr.page_section_id WHERE ps.page_id=?
					UNION ALL
					SELECT prcta.image_id FROM page_row_call_to_action prcta
					JOIN page_row pr ON pr.page_row_id=prcta.page_row_id
					JOIN page_section ps ON ps.page_section_id=pr.page_section_id WHERE ps.page_id=?
					""", UUID.class, duplicatePageId, duplicatePageId, duplicatePageId);
			Assert.assertEquals(List.of(imageFixture.getCropImageId(), imageFixture.getCropImageId(), imageFixture.getCropImageId()), duplicateImageIds);

			updateAssociations(pageService, account, pageId, pageRowId, callToActionPageRowId, imageFixture.getCropFileUploadId());
			Assert.assertEquals("Unchanged legacy compatibility IDs preserve repository associations",
					imageFixture.getCropImageId(), pageService.findPageById(pageId, account.getInstitutionId(), true).get().getImageId());
			Assert.assertEquals(imageFixture.getCropImageId(), pageService.findPageRowColumnById(pageRowColumnId).get().getImageId());
			Assert.assertEquals(imageFixture.getCropImageId(), pageService.findPageRowCallToActionByRowId(callToActionPageRowId).get().getImageId());

			updateAssociations(pageService, account, pageId, pageRowId, callToActionPageRowId, replacementLegacyFileUploadId);
			assertLegacyAssociationState(pageService, account, pageId, pageRowColumnId, callToActionPageRowId,
					replacementLegacyFileUploadId);

			updateAssociations(pageService, account, pageId, pageRowId, callToActionPageRowId, null);
			assertLegacyAssociationState(pageService, account, pageId, pageRowColumnId, callToActionPageRowId, null);
		});
	}

	protected void updateAssociations(@Nonnull PageService pageService,
															 @Nonnull Account account,
															 @Nonnull UUID pageId,
															 @Nonnull UUID pageRowId,
															 @Nonnull UUID callToActionPageRowId,
															 @Nullable UUID imageFileUploadId) {
		UpdatePageHeroRequest heroRequest = new UpdatePageHeroRequest();
		heroRequest.setPageId(pageId);
		heroRequest.setInstitutionId(account.getInstitutionId());
		heroRequest.setHeadline("Hero headline");
		heroRequest.setDescription("Hero description");
		heroRequest.setImageFileUploadId(imageFileUploadId == null ? null : imageFileUploadId.toString());
		heroRequest.setImageAltText("Placement hero alt text");
		pageService.updatePageHero(heroRequest);

		UpdatePageRowColumnRequest columnRequest = new UpdatePageRowColumnRequest();
		columnRequest.setPageRowId(pageRowId);
		columnRequest.setColumnDisplayOrder(0);
		columnRequest.setHeadline("Column headline");
		columnRequest.setDescription("Column description");
		columnRequest.setImageFileUploadId(imageFileUploadId == null ? null : imageFileUploadId.toString());
		columnRequest.setImageAltText("Placement column alt text");
		pageService.updatePageRowColumn(columnRequest);

		UpdatePageRowCallToActionRequest callToActionRequest = new UpdatePageRowCallToActionRequest();
		callToActionRequest.setPageRowId(callToActionPageRowId);
		callToActionRequest.setHeadline("CTA headline");
		callToActionRequest.setDescription("CTA description");
		callToActionRequest.setButtonText("Learn more");
		callToActionRequest.setButtonUrl("/learn-more");
		callToActionRequest.setImageFileUploadId(imageFileUploadId == null ? null : imageFileUploadId.toString());
		pageService.updatePageRowCallToAction(callToActionRequest, account.getInstitutionId(), RowTypeId.CALL_TO_ACTION_BLOCK);
	}

	protected void assertLegacyAssociationState(@Nonnull PageService pageService,
																			 @Nonnull Account account,
																			 @Nonnull UUID pageId,
																			 @Nonnull UUID pageRowColumnId,
																			 @Nonnull UUID callToActionPageRowId,
																			 @Nullable UUID expectedFileUploadId) {
		Page page = pageService.findPageById(pageId, account.getInstitutionId(), true).get();
		PageRowColumn column = pageService.findPageRowColumnById(pageRowColumnId).get();
		PageRowCallToAction callToAction = pageService.findPageRowCallToActionByRowId(callToActionPageRowId).get();
		Assert.assertNull(page.getImageId());
		Assert.assertNull(column.getImageId());
		Assert.assertNull(callToAction.getImageId());
		Assert.assertEquals(expectedFileUploadId, page.getImageFileUploadId());
		Assert.assertEquals(expectedFileUploadId, column.getImageFileUploadId());
		Assert.assertEquals(expectedFileUploadId, callToAction.getImageFileUploadId());
	}

	protected void assumePageBuilderImageSchemaExists(@Nonnull Database database) {
		Long count = database.queryForObject("""
				SELECT COUNT(*) FROM information_schema.columns
				WHERE table_schema='cobalt'
				AND table_name IN ('page','page_row_column','page_row_call_to_action')
				AND column_name='image_id'
				""", Long.class).get();
		Assume.assumeTrue("Branch schema must include page-builder image_id columns", count == 3L);
	}

	@Nonnull
	protected Account findExistingAdministratorAccount(@Nonnull Database database) {
		return database.queryForObject("""
				SELECT * FROM v_account WHERE institution_id=? AND role_id='ADMINISTRATOR' LIMIT 1
				""", Account.class, InstitutionId.COBALT).get();
	}

	@Nonnull
	protected ImageFixture createUploadedFourByThreeImageFamily(@Nonnull Database database,
																										 @Nonnull Account account) {
		UUID rawImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, "raw", "Library alt text");
		UUID cropImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_4X3, rawImageId, "crop", "Library alt text");
		UUID thumbnailImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_THUMBNAIL_4X3, cropImageId,
				"thumbnail", "Library alt text");
		UUID cropFileUploadId = database.queryForObject("SELECT file_upload_id FROM image WHERE image_id=?", UUID.class, cropImageId).get();
		return new ImageFixture(cropImageId, cropFileUploadId, thumbnailImageId);
	}

	@Nonnull
	protected UUID createUploadedImage(@Nonnull Database database,
															 @Nonnull Account account,
															 @Nonnull FileUploadTypeId fileUploadTypeId,
															 @Nullable UUID sourceImageId,
															 @Nonnull String label,
															 @Nullable String imageAltText) {
		UUID imageId = UUID.randomUUID();
		UUID fileUploadId = UUID.randomUUID();
		Integer width = fileUploadTypeId == FileUploadTypeId.IMAGE_THUMBNAIL_4X3 ? 240 : 1200;
		Integer height = fileUploadTypeId == FileUploadTypeId.IMAGE_THUMBNAIL_4X3 ? 180 : 900;
		String filename = format("page-repository-%s-%s.jpg", label, imageId);
		insertFileUpload(database, account, fileUploadId, fileUploadTypeId, filename);
		database.execute("""
				INSERT INTO image (image_id,file_upload_id,source_image_id,created_by_account_id,width,height,image_alt_text)
				VALUES (?,?,?,?,?,?,?)
				""", imageId, fileUploadId, sourceImageId, account.getAccountId(), width, height, imageAltText);
		return imageId;
	}

	@Nonnull
	protected UUID createLegacyPageImageFileUpload(@Nonnull Database database,
																								 @Nonnull Account account,
																								 @Nonnull String label) {
		UUID fileUploadId = UUID.randomUUID();
		insertFileUpload(database, account, fileUploadId, FileUploadTypeId.PAGE_IMAGE,
				format("legacy-page-%s-%s.jpg", label, fileUploadId));
		return fileUploadId;
	}

	protected void insertFileUpload(@Nonnull Database database,
														@Nonnull Account account,
														@Nonnull UUID fileUploadId,
														@Nonnull FileUploadTypeId fileUploadTypeId,
														@Nonnull String filename) {
		database.execute("""
				INSERT INTO file_upload (file_upload_id,file_upload_type_id,file_upload_status_id,account_id,institution_id,
				  url,storage_bucket,storage_key,storage_region,filename,content_type,filesize)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
				""", fileUploadId, fileUploadTypeId, FileUploadStatusId.UPLOADED, account.getAccountId(), account.getInstitutionId(),
				format("https://example.com/%s", filename), "test-bucket", format("page-repository/%s", filename),
				"us-east-1", filename, "image/jpeg", 100L);
	}

	protected static class ImageFixture {
		@Nonnull
		private final UUID cropImageId;
		@Nonnull
		private final UUID cropFileUploadId;
		@Nonnull
		private final UUID thumbnailImageId;

		public ImageFixture(@Nonnull UUID cropImageId, @Nonnull UUID cropFileUploadId, @Nonnull UUID thumbnailImageId) {
			this.cropImageId = requireNonNull(cropImageId);
			this.cropFileUploadId = requireNonNull(cropFileUploadId);
			this.thumbnailImageId = requireNonNull(thumbnailImageId);
		}

		@Nonnull
		public UUID getCropImageId() { return this.cropImageId; }
		@Nonnull
		public UUID getCropFileUploadId() { return this.cropFileUploadId; }
		@Nonnull
		public UUID getThumbnailImageId() { return this.thumbnailImageId; }
	}
}
