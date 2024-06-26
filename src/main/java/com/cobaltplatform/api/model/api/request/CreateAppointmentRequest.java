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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * @author Transmogrify LLC.
 */
@NotThreadSafe
public class CreateAppointmentRequest {
	@Nullable
	private UUID accountId;
	@Nullable
	private UUID createdByAcountId;
	@Nullable
	private UUID providerId;
	@Nullable
	private UUID appointmentReasonId;
	@Nullable
	private UUID patientOrderId;
	@Nullable
	private LocalDate date;
	@Nullable
	private LocalTime time;
	@Nullable
	private UUID appointmentTypeId;
	@Nullable
	private UUID intakeAssessmentId;
	@Nullable
	private String emailAddress; // only required for anon accounts currently
	@Nullable
	private String phoneNumber;
	@Nullable
	private String comment;
	@Nullable
	private String epicAppointmentFhirId; // only required for Epic FHIR scheduling system

	@Nullable
	public UUID getAccountId() {
		return accountId;
	}

	public void setAccountId(@Nullable UUID accountId) {
		this.accountId = accountId;
	}

	@Nullable
	public UUID getCreatedByAcountId() {
		return createdByAcountId;
	}

	public void setCreatedByAcountId(@Nullable UUID createdByAcountId) {
		this.createdByAcountId = createdByAcountId;
	}

	@Nullable
	public UUID getPatientOrderId() {
		return this.patientOrderId;
	}

	public void setPatientOrderId(@Nullable UUID patientOrderId) {
		this.patientOrderId = patientOrderId;
	}

	@Nullable
	public UUID getProviderId() {
		return providerId;
	}

	public void setProviderId(@Nullable UUID providerId) {
		this.providerId = providerId;
	}

	@Nullable
	public LocalDate getDate() {
		return date;
	}

	public void setDate(@Nullable LocalDate date) {
		this.date = date;
	}

	@Nullable
	public LocalTime getTime() {
		return time;
	}

	public void setTime(@Nullable LocalTime time) {
		this.time = time;
	}

	@Nullable
	public UUID getAppointmentTypeId() {
		return appointmentTypeId;
	}

	public void setAppointmentTypeId(@Nullable UUID appointmentTypeId) {
		this.appointmentTypeId = appointmentTypeId;
	}

	@Nullable
	public UUID getIntakeAssessmentId() {
		return intakeAssessmentId;
	}

	public void setIntakeAssessmentId(@Nullable UUID intakeAssessmentId) {
		this.intakeAssessmentId = intakeAssessmentId;
	}

	@Nullable
	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(@Nullable String emailAddress) {
		this.emailAddress = emailAddress;
	}

	@Nullable
	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(@Nullable String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	@Nullable
	public UUID getAppointmentReasonId() {
		return appointmentReasonId;
	}

	public void setAppointmentReasonId(@Nullable UUID appointmentReasonId) {
		this.appointmentReasonId = appointmentReasonId;
	}

	@Nullable
	public String getComment() {
		return comment;
	}

	public void setComment(@Nullable String comment) {
		this.comment = comment;
	}

	@Nullable
	public String getEpicAppointmentFhirId() {
		return this.epicAppointmentFhirId;
	}

	public void setEpicAppointmentFhirId(@Nullable String epicAppointmentFhirId) {
		this.epicAppointmentFhirId = epicAppointmentFhirId;
	}
}
