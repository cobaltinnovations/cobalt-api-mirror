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

package com.cobaltplatform.api.model.db;

import com.cobaltplatform.api.model.db.FileUploadType.FileUploadTypeId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.Instant;
import java.util.UUID;

/**
 * @author Transmogrify LLC.
 */
@NotThreadSafe
public class FileUpload {
	@Nullable
	private UUID fileUploadId;
	@Nullable
	private FileUploadTypeId fileUploadTypeId;
	@Nullable
	private UUID accountId;
	@Nullable
	private String url;
	@Nullable
	private String storageKey;
	@Nullable
	private String filename;
	@Nullable
	private String contentType;
	@Nullable
	private Instant created;
	@Nullable
	private Instant lastUpdated;

	@Nullable
	public UUID getFileUploadId() {
		return this.fileUploadId;
	}

	public void setFileUploadId(@Nullable UUID fileUploadId) {
		this.fileUploadId = fileUploadId;
	}

	@Nullable
	public FileUploadTypeId getFileUploadTypeId() {
		return this.fileUploadTypeId;
	}

	public void setFileUploadTypeId(@Nullable FileUploadTypeId fileUploadTypeId) {
		this.fileUploadTypeId = fileUploadTypeId;
	}

	@Nullable
	public UUID getAccountId() {
		return this.accountId;
	}

	public void setAccountId(@Nullable UUID accountId) {
		this.accountId = accountId;
	}

	@Nullable
	public String getUrl() {
		return this.url;
	}

	public void setUrl(@Nullable String url) {
		this.url = url;
	}

	@Nullable
	public String getStorageKey() {
		return this.storageKey;
	}

	public void setStorageKey(@Nullable String storageKey) {
		this.storageKey = storageKey;
	}

	@Nullable
	public String getFilename() {
		return this.filename;
	}

	public void setFilename(@Nullable String filename) {
		this.filename = filename;
	}

	@Nullable
	public String getContentType() {
		return this.contentType;
	}

	public void setContentType(@Nullable String contentType) {
		this.contentType = contentType;
	}

	@Nullable
	public Instant getCreated() {
		return this.created;
	}

	public void setCreated(@Nullable Instant created) {
		this.created = created;
	}

	@Nullable
	public Instant getLastUpdated() {
		return this.lastUpdated;
	}

	public void setLastUpdated(@Nullable Instant lastUpdated) {
		this.lastUpdated = lastUpdated;
	}
}