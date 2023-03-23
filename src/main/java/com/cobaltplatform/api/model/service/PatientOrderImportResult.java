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

package com.cobaltplatform.api.model.service;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class PatientOrderImportResult {
	@Nonnull
	private final UUID patientOrderImportId;
	@Nonnull
	private final List<UUID> patientOrderIds;

	public PatientOrderImportResult(@Nonnull UUID patientOrderImportId,
																	@Nonnull List<UUID> patientOrderIds) {
		requireNonNull(patientOrderImportId);
		requireNonNull(patientOrderIds);

		this.patientOrderImportId = patientOrderImportId;
		this.patientOrderIds = patientOrderIds;
	}

	@Nonnull
	public UUID getPatientOrderImportId() {
		return this.patientOrderImportId;
	}

	@Nonnull
	public List<UUID> getPatientOrderIds() {
		return this.patientOrderIds;
	}
}