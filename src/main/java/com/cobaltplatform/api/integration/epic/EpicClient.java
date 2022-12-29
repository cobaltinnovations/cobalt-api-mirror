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

package com.cobaltplatform.api.integration.epic;

import com.cobaltplatform.api.integration.epic.request.AppointmentBookFhirStu3Request;
import com.cobaltplatform.api.integration.epic.request.AppointmentFindFhirStu3Request;
import com.cobaltplatform.api.integration.epic.request.AppointmentSearchFhirStu3Request;
import com.cobaltplatform.api.integration.epic.request.CancelAppointmentRequest;
import com.cobaltplatform.api.integration.epic.request.GetPatientAppointmentsRequest;
import com.cobaltplatform.api.integration.epic.request.GetPatientDemographicsRequest;
import com.cobaltplatform.api.integration.epic.request.GetProviderScheduleRequest;
import com.cobaltplatform.api.integration.epic.request.PatientCreateRequest;
import com.cobaltplatform.api.integration.epic.request.PatientSearchRequest;
import com.cobaltplatform.api.integration.epic.request.ScheduleAppointmentWithInsuranceRequest;
import com.cobaltplatform.api.integration.epic.response.AppointmentBookFhirStu3Response;
import com.cobaltplatform.api.integration.epic.response.AppointmentFindFhirStu3Response;
import com.cobaltplatform.api.integration.epic.response.AppointmentSearchFhirStu3Response;
import com.cobaltplatform.api.integration.epic.response.CancelAppointmentResponse;
import com.cobaltplatform.api.integration.epic.response.GetPatientAppointmentsResponse;
import com.cobaltplatform.api.integration.epic.response.GetPatientDemographicsResponse;
import com.cobaltplatform.api.integration.epic.response.GetProviderScheduleResponse;
import com.cobaltplatform.api.integration.epic.response.PatientCreateResponse;
import com.cobaltplatform.api.integration.epic.response.PatientReadFhirR4Response;
import com.cobaltplatform.api.integration.epic.response.PatientSearchResponse;
import com.cobaltplatform.api.integration.epic.response.ScheduleAppointmentWithInsuranceResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public interface EpicClient {
	/**
	 * <a href="https://fhir.epic.com/Specifications?api=931">FHIR Patient.Read (R4)</a>
	 * <p>
	 * The FHIR Patient resource defines demographics, care providers, and other administrative information about a person
	 * receiving care at a health organization.
	 * <p>
	 * A user or staff member accessing this FHIR resource must have appropriate security to have search details,
	 * demographic details, and/or PCP details included in the Patient resource.
	 * <p>
	 * A MyChart user accessing this FHIR resource must be authorized to view patient information in MyChart.
	 * Additionally, this FHIR resource returns patient identifiers only to MyChart users with security to see patient
	 * IDs.
	 * <p>
	 * Additionally, if an Epic organization has configured service area restrictions, the user accessing this FHIR
	 * resource must be authorized to view patients in the target service area.
	 * <p>
	 * This API may behave differently when used in a patient-facing context. See the
	 * <a href="https://fhir.epic.com/Documentation?docId=patientfacingfhirapps">Patient-Facing Apps Using FHIR document</a>
	 * for more information.
	 *
	 * @param patientId The patient FHIR ID
	 * @return the patient, or an empty value if none was found for the given FHIR ID
	 */
	@Nonnull
	Optional<PatientReadFhirR4Response> patientReadFhirR4(@Nullable String patientId);

	/**
	 * <a href="https://fhir.epic.com/Specifications?api=840">FHIR Appointment $find (STU3)</a>
	 * <p>
	 * The FHIR Appointment $find operation finds a list of potential appointments slots.
	 * The Appointment IDs in the returned list can be used by the Appointment $book operation to schedule that
	 * appointment.
	 * <p>
	 * A typical workflow would involve an end user, who is intending to book an appointment for patient, entering in some
	 * basic criteria, such as when the appointment would occur, what specialty or visit type is desired, and optional
	 * information about the patient, such as date of birth, sex, or any provider preferences the patient has.
	 * The user app would then collect those criteria and submit them using the $find operation.
	 * The scheduling system would use organization-defined rules to identify potential appointments slots, and returns
	 * those for the user to review. The user would review the possible options, and in collaboration with the patient,
	 * select the best option. For new patients, the app would perform a Patient.Create interaction.
	 * The app would then book the appointment using the $book operation.
	 * <p>
	 * Note: The $find operation requires pre-coordination with the healthcare organization.
	 * The organization must build out specific rules in the Cadence scheduling system that accept the provided inputs
	 * and determine which providers and slots will be returned. The request elements described here are the list of
	 * *potential* elements that might be used by an organizations scheduling rules. Some organizations may ignore or
	 * require any of these properties based on their organizational rules.
	 *
	 * @param request data to send in the request
	 * @return the Epic response data
	 */
	@Nonnull
	AppointmentFindFhirStu3Response appointmentFindFhirStu3(@Nonnull AppointmentFindFhirStu3Request request);

	/**
	 * <a href="https://fhir.epic.com/Specifications?api=839">FHIR Appointment $book (STU3)</a>
	 * <p>
	 *
	 * @param request data to send in the request
	 * @return the Epic response data
	 */
	@Nonnull
	AppointmentBookFhirStu3Response appointmentBookFhirStu3(@Nonnull AppointmentBookFhirStu3Request request);

	/**
	 * <a href="https://fhir.epic.com/Specifications?api=10189">FHIR Appointment.Search (STU3)</a>
	 * <p>
	 * This web service allows searching for appointments and returns up-to-date appointment information.
	 * It includes appointments scheduled in Epic using Cadence, OpTime, Cupid, or Radiant workflows, but it does not
	 * include external appointments, appointment requests, or surgical procedures.
	 *
	 * @param request data to send in the request
	 * @return the Epic response data
	 */
	@Nonnull
	AppointmentSearchFhirStu3Response appointmentSearchFhirStu3(@Nonnull AppointmentSearchFhirStu3Request request);

	@Nonnull
	PatientSearchResponse performPatientSearch(@Nonnull PatientSearchRequest request);

	@Nonnull
	GetPatientDemographicsResponse performGetPatientDemographics(@Nonnull GetPatientDemographicsRequest request);

	@Nonnull
	GetProviderScheduleResponse performGetProviderSchedule(@Nonnull GetProviderScheduleRequest request);

	@Nonnull
	GetPatientAppointmentsResponse performGetPatientAppointments(@Nonnull GetPatientAppointmentsRequest request);

	@Nonnull
	PatientCreateResponse performPatientCreate(@Nonnull PatientCreateRequest request);

	@Nonnull
	ScheduleAppointmentWithInsuranceResponse performScheduleAppointmentWithInsurance(@Nonnull ScheduleAppointmentWithInsuranceRequest request);

	@Nonnull
	CancelAppointmentResponse performCancelAppointment(@Nonnull CancelAppointmentRequest request);

	@Nonnull
	LocalDate parseDateWithHyphens(@Nonnull String date);

	@Nonnull
	String formatDateWithHyphens(@Nonnull LocalDate date);

	@Nonnull
	LocalDate parseDateWithSlashes(@Nonnull String date);

	@Nonnull
	String formatDateWithSlashes(@Nonnull LocalDate date);

	@Nonnull
	String formatTimeInMilitary(@Nonnull LocalTime time);

	@Nonnull
	LocalTime parseTimeAmPm(@Nonnull String time);

	@Nonnull
	String formatPhoneNumber(@Nonnull String phoneNumber);

	@Nonnull
	@Deprecated
	default Optional<String> determineLatestUIDForPatientIdentifier(@Nonnull String oldIdentifierId,
																																	@Nonnull String oldIdentifierTypeId) {
		requireNonNull(oldIdentifierId);
		requireNonNull(oldIdentifierTypeId);

		// If we already have a UID, we don't need to requery for it
		if (oldIdentifierTypeId.equals("UID"))
			return Optional.of(oldIdentifierId);

		PatientSearchRequest searchRequest = new PatientSearchRequest();
		searchRequest.setIdentifier(oldIdentifierId);

		PatientSearchResponse response = performPatientSearch(searchRequest);

		if (response.getEntry().size() == 0)
			return Optional.empty();

		return extractUIDFromPatientEntry(response.getEntry().get(0));
	}


	@Nonnull
	default Optional<String> extractUIDFromPatientEntry(@Nonnull PatientSearchResponse.Entry patientEntry) {
		requireNonNull(patientEntry);

		if (patientEntry.getResource().getIdentifier() != null)
			for (PatientSearchResponse.Entry.Resource.Identifier identifier : patientEntry.getResource().getIdentifier())
				if ("urn:oid:1.3.6.1.4.1.22812.19.44324.0".equals(identifier.getSystem()))
					return Optional.of(identifier.getValue());

		return Optional.empty();
	}
}