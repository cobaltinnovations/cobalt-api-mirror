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
import com.cobaltplatform.api.model.api.request.CreateGroupSessionRequest;
import com.cobaltplatform.api.model.api.request.UpdateGroupSessionRequest;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.FileUploadStatus.FileUploadStatusId;
import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;
import com.cobaltplatform.api.model.db.GroupSession;
import com.cobaltplatform.api.model.db.GroupSessionLocationType.GroupSessionLocationTypeId;
import com.cobaltplatform.api.model.db.GroupSessionSchedulingSystem.GroupSessionSchedulingSystemId;
import com.cobaltplatform.api.model.db.GroupSessionVisibilityType.GroupSessionVisibilityTypeId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.LocalDateTime;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class GroupSessionImageAssociationTests {
	@Test
	public void createGroupSessionIgnoresLegacyImageFileUploadIdWhenValidImageIdIsProvided() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeGroupSessionImageIdColumnExists(database);
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			Account account = findExistingAccount(database);

			UUID selectedImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID staleImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1200, 900);
			UUID selectedFileUploadId = findFileUploadIdByImageId(database, selectedImageId);
			UUID staleFileUploadId = findFileUploadIdByImageId(database, staleImageId);

			CreateGroupSessionRequest request = createGroupSessionRequest(account, "create-mismatched-image-upload");
			request.setImageId(selectedImageId);
			request.setImageFileUploadId(staleFileUploadId);

			UUID groupSessionId = groupSessionService.createGroupSession(request, account);
			GroupSession groupSession = groupSessionService.findGroupSessionById(groupSessionId, account).get();

			Assert.assertEquals("Valid image ID should be stored", selectedImageId, groupSession.getImageId());
			Assert.assertEquals("Legacy file upload ID should be derived from the valid image", selectedFileUploadId, groupSession.getImageFileUploadId());
		});
	}

	@Test
	public void updateGroupSessionIgnoresLegacyImageFileUploadIdWhenValidImageIdIsProvided() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			assumeGroupSessionImageIdColumnExists(database);
			GroupSessionService groupSessionService = app.getInjector().getInstance(GroupSessionService.class);
			Account account = findExistingAccount(database);

			UUID originalImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1600, 900);
			UUID newImageId = createUploadedImage(database, account, FileUploadTypeId.IMAGE_RAW, null, 1200, 900);
			UUID originalFileUploadId = findFileUploadIdByImageId(database, originalImageId);
			UUID newFileUploadId = findFileUploadIdByImageId(database, newImageId);

			CreateGroupSessionRequest createRequest = createGroupSessionRequest(account, "update-mismatched-image-upload");
			createRequest.setImageId(originalImageId);
			UUID groupSessionId = groupSessionService.createGroupSession(createRequest, account);

			UpdateGroupSessionRequest updateRequest = updateGroupSessionRequest(groupSessionId, "update-mismatched-image-upload");
			updateRequest.setImageId(newImageId);
			updateRequest.setImageFileUploadId(originalFileUploadId);

			groupSessionService.updateGroupSession(updateRequest, account);
			GroupSession groupSession = groupSessionService.findGroupSessionById(groupSessionId, account).get();

			Assert.assertEquals("Valid updated image ID should be stored", newImageId, groupSession.getImageId());
			Assert.assertEquals("Legacy file upload ID should be derived from the updated image", newFileUploadId, groupSession.getImageFileUploadId());
		});
	}

	protected void assumeGroupSessionImageIdColumnExists(@Nonnull Database database) {
		requireNonNull(database);

		Boolean groupSessionImageIdColumnExists = database.queryForObject("""
				SELECT EXISTS (
				  SELECT 1
				  FROM information_schema.columns
				  WHERE table_schema='public'
				  AND table_name='group_session'
				  AND column_name='image_id'
				)
				""", Boolean.class).get();

		Assume.assumeTrue("Branch schema must include group_session.image_id", groupSessionImageIdColumnExists);
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
			setTitle("Image association test");
			setDescription("Image association test description.");
			setUrlName(format("image-association-test-%s-%s", urlNameSuffix, UUID.randomUUID()));
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
	protected UpdateGroupSessionRequest updateGroupSessionRequest(@Nonnull UUID groupSessionId,
																																@Nonnull String urlNameSuffix) {
		requireNonNull(groupSessionId);
		requireNonNull(urlNameSuffix);

		return new UpdateGroupSessionRequest() {{
			setGroupSessionId(groupSessionId);
			setGroupSessionSchedulingSystemId(GroupSessionSchedulingSystemId.COBALT);
			setGroupSessionLocationTypeId(GroupSessionLocationTypeId.IN_PERSON);
			setTitle("Updated image association test");
			setDescription("Updated image association test description.");
			setUrlName(format("updated-image-association-test-%s-%s", urlNameSuffix, UUID.randomUUID()));
			setInPersonLocation("Updated test location");
			setFacilitatorName("Updated Test Facilitator");
			setFacilitatorEmailAddress("updated-facilitator@example.com");
			setStartDateTime(LocalDateTime.now().plusDays(8));
			setEndDateTime(LocalDateTime.now().plusDays(8).plusHours(1));
			setGroupSessionVisibilityTypeId(GroupSessionVisibilityTypeId.PUBLIC);
			setDifferentEmailAddressForNotifications(false);
			setSingleSessionFlag(true);
			setSendFollowupEmail(false);
			setSendReminderEmail(false);
		}};
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
		requireNonNull(database);
		requireNonNull(account);
		requireNonNull(fileUploadTypeId);
		requireNonNull(width);
		requireNonNull(height);

		UUID fileUploadId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		String filename = format("%s.jpg", imageId);
		String storageKey = format("group-session-image-association-test/%s/%s", fileUploadTypeId.name().toLowerCase(), filename);

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
