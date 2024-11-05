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

import com.cobaltplatform.api.model.service.PatientOrderActivity;
import com.cobaltplatform.api.model.service.SortDirectionId;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class PatientOrderActivityService {
	@Nonnull
	private final DatabaseProvider databaseProvider;
	@Nonnull
	private final Logger logger;

	@Inject
	public PatientOrderActivityService(@Nonnull DatabaseProvider databaseProvider) {
		requireNonNull(databaseProvider);

		this.databaseProvider = databaseProvider;
		this.logger = LoggerFactory.getLogger(getClass());
	}

	@Nonnull
	public List<PatientOrderActivity> findPatientOrderActivitiesByPatientOrderId(@Nullable UUID patientOrderId) {
		return findPatientOrderActivitiesByPatientOrderId(patientOrderId, SortDirectionId.DESCENDING);
	}

	@Nonnull
	public List<PatientOrderActivity> findPatientOrderActivitiesByPatientOrderId(@Nullable UUID patientOrderId,
																																							 @Nonnull SortDirectionId sortDirectionId) {
		requireNonNull(sortDirectionId);

		if (patientOrderId == null)
			return List.of();

		getReadReplicaDatabase().queryForList("""
				SELECT
				feg.footprint_event_group_type_id,
				feg.created,
				feg.account_id,
				fe.footprint_event_id,
				fe.footprint_event_group_id,
				fe.footprint_event_operation_type_id,
				fe.table_name,
				fe.old_value,
				fe.new_value,
				jsonb_diff(fe.old_value, fe.new_value) as old_vs_new_diff
				FROM
				footprint_event fe, footprint_event_group feg
				WHERE fe.footprint_event_group_id=feg.footprint_event_group_id
				AND feg.footprint_event_group_type_id LIKE 'PATIENT_ORDER_%'
				AND fe.new_value->>'patient_order_id'=?
				ORDER BY feg.created desc, fe.created desc
				""", Object.class, patientOrderId, sortDirectionId);

		// TODO
		return null;
	}

	@Nonnull
	protected Database getReadReplicaDatabase() {
		return this.databaseProvider.getReadReplicaDatabase();
	}

	@Nonnull
	protected Logger getLogger() {
		return logger;
	}
}
