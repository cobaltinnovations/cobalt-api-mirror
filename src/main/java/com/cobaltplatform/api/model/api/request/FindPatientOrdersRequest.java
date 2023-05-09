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

package com.cobaltplatform.api.model.api.request;

import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.PatientOrderConsentStatus.PatientOrderConsentStatusId;
import com.cobaltplatform.api.model.db.PatientOrderDisposition.PatientOrderDispositionId;
import com.cobaltplatform.api.model.db.PatientOrderResourceCheckInResponseStatus.PatientOrderResourceCheckInResponseStatusId;
import com.cobaltplatform.api.model.db.PatientOrderResourcingStatus.PatientOrderResourcingStatusId;
import com.cobaltplatform.api.model.db.PatientOrderSafetyPlanningStatus.PatientOrderSafetyPlanningStatusId;
import com.cobaltplatform.api.model.db.PatientOrderScreeningStatus.PatientOrderScreeningStatusId;
import com.cobaltplatform.api.model.db.PatientOrderTriageStatus.PatientOrderTriageStatusId;
import com.cobaltplatform.api.model.service.PatientOrderAssignmentStatusId;
import com.cobaltplatform.api.model.service.PatientOrderOutreachStatusId;
import com.cobaltplatform.api.model.service.PatientOrderResponseStatusId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Set;
import java.util.UUID;

/**
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class FindPatientOrdersRequest {
	@Nullable
	private InstitutionId institutionId;
	@Nullable
	private PatientOrderConsentStatusId patientOrderConsentStatusId;
	@Nullable
	private PatientOrderDispositionId patientOrderDispositionId;
	@Nullable
	private Set<PatientOrderTriageStatusId> patientOrderTriageStatusIds;
	@Nullable
	private PatientOrderAssignmentStatusId patientOrderAssignmentStatusId;
	@Nullable
	private PatientOrderOutreachStatusId patientOrderOutreachStatusId;
	@Nullable
	private PatientOrderResponseStatusId patientOrderResponseStatusId;
	@Nullable
	private PatientOrderSafetyPlanningStatusId patientOrderSafetyPlanningStatusId;
	@Nullable
	private Set<PatientOrderFilterFlagTypeId> patientOrderFilterFlagTypeIds;
	@Nullable
	private Set<String> referringPracticeNames;
	@Nullable
	private Set<String> reasonsForReferral;
	@Nullable
	private Set<PatientOrderScreeningStatusId> patientOrderScreeningStatusIds;
	@Nullable
	private Set<PatientOrderResourcingStatusId> patientOrderResourcingStatusIds;
	@Nullable
	private Set<PatientOrderResourceCheckInResponseStatusId> patientOrderResourceCheckInResponseStatusIds;

	@Nullable
	private UUID panelAccountId;
	@Nullable
	private String patientMrn;
	@Nullable
	private String searchQuery;
	@Nullable
	private Integer pageNumber;
	@Nullable
	private Integer pageSize;

	public enum PatientOrderFilterFlagTypeId {
		NONE,
		PATIENT_BELOW_AGE_THRESHOLD,
		MOST_RECENT_EPISODE_CLOSED_WITHIN_DATE_THRESHOLD,
		ADDRESS_REGION_NOT_ACCEPTED,
		INSURANCE_NOT_ACCEPTED
		// We don't include safety planning here because it's already filterable via PatientOrderSafetyPlanningStatusId
	}

	@Nullable
	public InstitutionId getInstitutionId() {
		return this.institutionId;
	}

	public void setInstitutionId(@Nullable InstitutionId institutionId) {
		this.institutionId = institutionId;
	}

	@Nullable
	public PatientOrderConsentStatusId getPatientOrderConsentStatusId() {
		return this.patientOrderConsentStatusId;
	}

	public void setPatientOrderConsentStatusId(@Nullable PatientOrderConsentStatusId patientOrderConsentStatusId) {
		this.patientOrderConsentStatusId = patientOrderConsentStatusId;
	}

	@Nullable
	public PatientOrderDispositionId getPatientOrderDispositionId() {
		return this.patientOrderDispositionId;
	}

	public void setPatientOrderDispositionId(@Nullable PatientOrderDispositionId patientOrderDispositionId) {
		this.patientOrderDispositionId = patientOrderDispositionId;
	}

	@Nullable
	public Set<PatientOrderTriageStatusId> getPatientOrderTriageStatusIds() {
		return this.patientOrderTriageStatusIds;
	}

	public void setPatientOrderTriageStatusIds(@Nullable Set<PatientOrderTriageStatusId> patientOrderTriageStatusIds) {
		this.patientOrderTriageStatusIds = patientOrderTriageStatusIds;
	}

	@Nullable
	public PatientOrderAssignmentStatusId getPatientOrderAssignmentStatusId() {
		return this.patientOrderAssignmentStatusId;
	}

	public void setPatientOrderAssignmentStatusId(@Nullable PatientOrderAssignmentStatusId patientOrderAssignmentStatusId) {
		this.patientOrderAssignmentStatusId = patientOrderAssignmentStatusId;
	}

	@Nullable
	public PatientOrderOutreachStatusId getPatientOrderOutreachStatusId() {
		return this.patientOrderOutreachStatusId;
	}

	public void setPatientOrderOutreachStatusId(@Nullable PatientOrderOutreachStatusId patientOrderOutreachStatusId) {
		this.patientOrderOutreachStatusId = patientOrderOutreachStatusId;
	}

	@Nullable
	public PatientOrderResponseStatusId getPatientOrderResponseStatusId() {
		return this.patientOrderResponseStatusId;
	}

	public void setPatientOrderResponseStatusId(@Nullable PatientOrderResponseStatusId patientOrderResponseStatusId) {
		this.patientOrderResponseStatusId = patientOrderResponseStatusId;
	}

	@Nullable
	public PatientOrderSafetyPlanningStatusId getPatientOrderSafetyPlanningStatusId() {
		return this.patientOrderSafetyPlanningStatusId;
	}

	public void setPatientOrderSafetyPlanningStatusId(@Nullable PatientOrderSafetyPlanningStatusId patientOrderSafetyPlanningStatusId) {
		this.patientOrderSafetyPlanningStatusId = patientOrderSafetyPlanningStatusId;
	}

	@Nullable
	public Set<PatientOrderFilterFlagTypeId> getPatientOrderFilterFlagTypeIds() {
		return this.patientOrderFilterFlagTypeIds;
	}

	public void setPatientOrderFilterFlagTypeIds(@Nullable Set<PatientOrderFilterFlagTypeId> patientOrderFilterFlagTypeIds) {
		this.patientOrderFilterFlagTypeIds = patientOrderFilterFlagTypeIds;
	}

	@Nullable
	public Set<String> getReferringPracticeNames() {
		return this.referringPracticeNames;
	}

	public void setReferringPracticeNames(@Nullable Set<String> referringPracticeNames) {
		this.referringPracticeNames = referringPracticeNames;
	}

	@Nullable
	public Set<String> getReasonsForReferral() {
		return this.reasonsForReferral;
	}

	public void setReasonsForReferral(@Nullable Set<String> reasonsForReferral) {
		this.reasonsForReferral = reasonsForReferral;
	}

	@Nullable
	public Set<PatientOrderScreeningStatusId> getPatientOrderScreeningStatusIds() {
		return this.patientOrderScreeningStatusIds;
	}

	public void setPatientOrderScreeningStatusIds(@Nullable Set<PatientOrderScreeningStatusId> patientOrderScreeningStatusIds) {
		this.patientOrderScreeningStatusIds = patientOrderScreeningStatusIds;
	}

	@Nullable
	public Set<PatientOrderResourcingStatusId> getPatientOrderResourcingStatusIds() {
		return this.patientOrderResourcingStatusIds;
	}

	public void setPatientOrderResourcingStatusIds(@Nullable Set<PatientOrderResourcingStatusId> patientOrderResourcingStatusIds) {
		this.patientOrderResourcingStatusIds = patientOrderResourcingStatusIds;
	}

	@Nullable
	public Set<PatientOrderResourceCheckInResponseStatusId> getPatientOrderResourceCheckInResponseStatusIds() {
		return this.patientOrderResourceCheckInResponseStatusIds;
	}

	public void setPatientOrderResourceCheckInResponseStatusIds(@Nullable Set<PatientOrderResourceCheckInResponseStatusId> patientOrderResourceCheckInResponseStatusIds) {
		this.patientOrderResourceCheckInResponseStatusIds = patientOrderResourceCheckInResponseStatusIds;
	}

	@Nullable
	public UUID getPanelAccountId() {
		return this.panelAccountId;
	}

	public void setPanelAccountId(@Nullable UUID panelAccountId) {
		this.panelAccountId = panelAccountId;
	}

	@Nullable
	public String getPatientMrn() {
		return this.patientMrn;
	}

	public void setPatientMrn(@Nullable String patientMrn) {
		this.patientMrn = patientMrn;
	}

	@Nullable
	public String getSearchQuery() {
		return this.searchQuery;
	}

	public void setSearchQuery(@Nullable String searchQuery) {
		this.searchQuery = searchQuery;
	}

	@Nullable
	public Integer getPageNumber() {
		return this.pageNumber;
	}

	public void setPageNumber(@Nullable Integer pageNumber) {
		this.pageNumber = pageNumber;
	}

	@Nullable
	public Integer getPageSize() {
		return this.pageSize;
	}

	public void setPageSize(@Nullable Integer pageSize) {
		this.pageSize = pageSize;
	}
}