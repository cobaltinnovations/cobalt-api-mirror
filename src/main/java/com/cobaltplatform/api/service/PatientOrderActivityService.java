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

import com.cobaltplatform.api.model.db.FootprintEventGroupType.FootprintEventGroupTypeId;
import com.cobaltplatform.api.model.db.FootprintEventOperationType.FootprintEventOperationTypeId;
import com.cobaltplatform.api.model.db.RawPatientOrder;
import com.cobaltplatform.api.model.service.PatientOrderActivity;
import com.cobaltplatform.api.model.service.PatientOrderActivityTypeId;
import com.cobaltplatform.api.model.service.SortDirectionId;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lokalized.Strings;
import com.pyranid.Database;
import com.pyranid.DatabaseColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class PatientOrderActivityService {
	@Nonnull
	private final DatabaseProvider databaseProvider;
	@Nonnull
	private final Strings strings;
	@Nonnull
	private final Logger logger;

	@Inject
	public PatientOrderActivityService(@Nonnull DatabaseProvider databaseProvider,
																		 @Nonnull Strings strings) {
		requireNonNull(databaseProvider);
		requireNonNull(strings);

		this.databaseProvider = databaseProvider;
		this.strings = strings;
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

		// Verify order exists
		RawPatientOrder rawPatientOrder = getReadReplicaDatabase().queryForObject("""
				SELECT *
				FROM patient_order
				WHERE patient_order_id=?
				""", RawPatientOrder.class, patientOrderId).orElse(null);

		if (rawPatientOrder == null)
			return List.of();

		String sortDirection = sortDirectionId == SortDirectionId.DESCENDING ? "DESC" : "ASC";

		// Pull all FootprintEventGroupTypeId.PATIENT_ORDER_* records for this order
		List<PatientOrderFootprintEvent> patientOrderFootprintEvents = getReadReplicaDatabase().queryForList("""
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
				ORDER BY feg.created {{sortDirection}}, fe.created {{sortDirection}}
				""".replace("{{sortDirection}}", sortDirection), PatientOrderFootprintEvent.class, patientOrderId.toString());

		// TODO: query for messages and other pieces of data for this order

		List<PatientOrderActivity> patientOrderActivities = new ArrayList<>(patientOrderFootprintEvents.size());

		for (PatientOrderFootprintEvent patientOrderFootprintEvent : patientOrderFootprintEvents) {
			PatientOrderActivity patientOrderActivity = null;

			if (patientOrderFootprintEvent.getFootprintEventGroupTypeId() == FootprintEventGroupTypeId.PATIENT_ORDER_IMPORT_CREATE)
				patientOrderActivity = patientOrderActivityForPatientOrderImportCreate(patientOrderFootprintEvent);

			if (patientOrderActivity != null)
				patientOrderActivities.add(patientOrderActivity);
			else
				getLogger().warn("Not implemented: Patient Order Activity for {}", patientOrderFootprintEvent.getFootprintEventGroupTypeId());
		}

		// TODO: sort all the activities

		return patientOrderActivities;
	}

	@Nonnull
	protected PatientOrderActivity patientOrderActivityForPatientOrderImportCreate(@Nonnull PatientOrderFootprintEvent patientOrderFootprintEvent) {
		requireNonNull(patientOrderFootprintEvent);

		PatientOrderActivity patientOrderActivity = new PatientOrderActivity();
		patientOrderActivity.setPatientOrderActivityTypeId(PatientOrderActivityTypeId.ORDER_IMPORTED);
		patientOrderActivity.setDescription(getStrings().get("TODO"));

		// 	@Nullable
		//	private PatientOrderActivityTypeId patientOrderActivityTypeId;
		//	@Nullable
		//	private UUID initiatedByAccountId;
		//	@Nullable
		//	private String description;
		//	@Nullable
		//	private List<PatientOrderActivityMessage> patientOrderActivityMessages;
		//	@Nullable
		//	private Map<String, Object> metadata;

		return patientOrderActivity;
	}

	@NotThreadSafe
	protected static class PatientOrderFootprintEvent {
		@Nonnull
		private static final Gson GSON;

		static {
			GsonBuilder gsonBuilder = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping();

			GSON = gsonBuilder.create();
		}

		@Nullable
		private FootprintEventGroupTypeId footprintEventGroupTypeId;
		@Nullable
		private UUID accountId;
		@Nullable
		private UUID footprintEventId;
		@Nullable
		private UUID footprintEventGroupId;
		@Nullable
		private FootprintEventOperationTypeId footprintEventOperationTypeId;
		@Nullable
		private Instant created;
		@Nullable
		private String tableName;
		@Nullable
		@DatabaseColumn("old_value")
		private String oldValueAsString;
		@Nonnull
		private Map<String, Object> oldValue = Map.of();
		@Nullable
		@DatabaseColumn("new_value")
		private String newValueAsString;
		@Nonnull
		private Map<String, Object> newValue = Map.of();
		@Nullable
		@DatabaseColumn("old_vs_new_diff")
		private String oldVsNewDiffAsString;
		@Nonnull
		private Map<String, Object> oldVsNewDiff = Map.of();

		@Nullable
		public String getOldValueAsString() {
			return this.oldValueAsString;
		}

		public void setOldValueAsString(@Nullable String oldValueAsString) {
			this.oldValueAsString = oldValueAsString;

			String oldValue = trimToNull(oldValueAsString);
			this.oldValue = oldValue == null ? Map.of() : GSON.fromJson(oldValue, new TypeToken<Map<String, Object>>() {
			}.getType());
		}

		@Nonnull
		public Map<String, Object> getOldValue() {
			return this.oldValue;
		}

		@Nullable
		public String getNewValueAsString() {
			return this.newValueAsString;
		}

		public void setNewValueAsString(@Nullable String newValueAsString) {
			this.newValueAsString = newValueAsString;

			String newValue = trimToNull(newValueAsString);
			this.newValue = newValue == null ? Map.of() : GSON.fromJson(newValue, new TypeToken<Map<String, Object>>() {
			}.getType());
		}

		@Nonnull
		public Map<String, Object> getNewValue() {
			return this.newValue;
		}

		@Nullable
		public String getOldVsNewDiffAsString() {
			return this.oldVsNewDiffAsString;
		}

		public void setOldVsNewDiffAsString(@Nullable String oldVsNewDiffAsString) {
			this.oldVsNewDiffAsString = oldVsNewDiffAsString;

			String oldVsNewDiff = trimToNull(oldVsNewDiffAsString);
			this.oldVsNewDiff = oldVsNewDiff == null ? Map.of() : GSON.fromJson(oldVsNewDiff, new TypeToken<Map<String, Object>>() {
			}.getType());
		}

		@Nonnull
		public Map<String, Object> getOldVsNewDiff() {
			return this.oldVsNewDiff;
		}

		@Nullable
		public FootprintEventGroupTypeId getFootprintEventGroupTypeId() {
			return this.footprintEventGroupTypeId;
		}

		public void setFootprintEventGroupTypeId(@Nullable FootprintEventGroupTypeId footprintEventGroupTypeId) {
			this.footprintEventGroupTypeId = footprintEventGroupTypeId;
		}

		@Nullable
		public UUID getAccountId() {
			return this.accountId;
		}

		public void setAccountId(@Nullable UUID accountId) {
			this.accountId = accountId;
		}

		@Nullable
		public UUID getFootprintEventId() {
			return this.footprintEventId;
		}

		public void setFootprintEventId(@Nullable UUID footprintEventId) {
			this.footprintEventId = footprintEventId;
		}

		@Nullable
		public UUID getFootprintEventGroupId() {
			return this.footprintEventGroupId;
		}

		public void setFootprintEventGroupId(@Nullable UUID footprintEventGroupId) {
			this.footprintEventGroupId = footprintEventGroupId;
		}

		@Nullable
		public FootprintEventOperationTypeId getFootprintEventOperationTypeId() {
			return this.footprintEventOperationTypeId;
		}

		public void setFootprintEventOperationTypeId(@Nullable FootprintEventOperationTypeId footprintEventOperationTypeId) {
			this.footprintEventOperationTypeId = footprintEventOperationTypeId;
		}

		@Nullable
		public Instant getCreated() {
			return this.created;
		}

		public void setCreated(@Nullable Instant created) {
			this.created = created;
		}

		@Nullable
		public String getTableName() {
			return this.tableName;
		}

		public void setTableName(@Nullable String tableName) {
			this.tableName = tableName;
		}
	}

	@Nonnull
	protected Database getReadReplicaDatabase() {
		return this.databaseProvider.getReadReplicaDatabase();
	}

	@Nonnull
	protected Strings getStrings() {
		return this.strings;
	}

	@Nonnull
	protected Logger getLogger() {
		return logger;
	}
}
