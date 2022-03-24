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

package com.cobaltplatform.api.model.api.response;

import com.cobaltplatform.api.context.CurrentContext;
import com.cobaltplatform.api.model.api.request.ProviderFindRequest;
import com.cobaltplatform.api.model.api.request.ProviderFindRequest.ProviderFindSupplement;
import com.cobaltplatform.api.model.api.response.AppointmentApiResponse.AppointmentApiResponseFactory;
import com.cobaltplatform.api.model.api.response.ClinicApiResponse.ClinicApiResponseFactory;
import com.cobaltplatform.api.model.api.response.FollowupApiResponse.FollowupApiResponseFactory;
import com.cobaltplatform.api.model.api.response.ProviderApiResponse.ProviderApiResponseFactory;
import com.cobaltplatform.api.model.api.response.ProviderApiResponse.ProviderApiResponseSupplement;
import com.cobaltplatform.api.model.api.response.SpecialtyApiResponse.SpecialtyApiResponseFactory;
import com.cobaltplatform.api.model.db.Appointment;
import com.cobaltplatform.api.model.db.Clinic;
import com.cobaltplatform.api.model.db.Followup;
import com.cobaltplatform.api.model.db.Provider;
import com.cobaltplatform.api.model.db.Specialty;
import com.cobaltplatform.api.model.service.ProviderFind;
import com.cobaltplatform.api.service.AppointmentService;
import com.cobaltplatform.api.service.ClinicService;
import com.cobaltplatform.api.service.FollowupService;
import com.cobaltplatform.api.service.ProviderService;
import com.cobaltplatform.api.util.Formatter;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.lokalized.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.LocalDate;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class ProviderFindsApiResponse {
	@Nonnull
	private final List<Object> sections;
	@Nonnull
	private final List<Map<String, Object>> appointmentTypes;
	@Nonnull
	private final List<Map<String, Object>> epicDepartments;
	@Nullable
	private final ProviderApiResponse provider;
	@Nullable
	private final List<ClinicApiResponse> clinics;
	@Nonnull
	private final Boolean showSpecialties;
	@Nullable
	private final List<SpecialtyApiResponse> specialties;
	@Nullable
	private final List<AppointmentApiResponse> appointments;
	@Nullable
	private final List<FollowupApiResponse> followups;

	// Note: requires FactoryModuleBuilder entry in AppModule
	@ThreadSafe
	public interface ProviderFindsApiResponseFactory {
		@Nonnull
		ProviderFindsApiResponse create(@Nonnull ProviderFindRequest request,
																		@Nonnull List<ProviderFind> providerFinds,
																		@Nonnull Set<ProviderFindSupplement> supplements);
	}

	@AssistedInject
	public ProviderFindsApiResponse(@Nonnull javax.inject.Provider<CurrentContext> currentContextProvider,
																	@Nonnull SpecialtyApiResponseFactory specialtyApiResponseFactory,
																	@Nonnull AppointmentApiResponseFactory appointmentApiResponseFactory,
																	@Nonnull FollowupApiResponseFactory followupApiResponseFactory,
																	@Nonnull ProviderApiResponseFactory providerApiResponseFactory,
																	@Nonnull ClinicApiResponseFactory clinicApiResponseFactory,
																	@Nonnull AppointmentService appointmentService,
																	@Nonnull ProviderService providerService,
																	@Nonnull ClinicService clinicService,
																	@Nonnull FollowupService followupService,
																	@Nonnull Formatter formatter,
																	@Nonnull Strings strings,
																	@Assisted @Nonnull ProviderFindRequest request,
																	@Assisted @Nonnull List<ProviderFind> providerFinds,
																	@Assisted @Nonnull Set<ProviderFindSupplement> supplements) {
		requireNonNull(currentContextProvider);
		requireNonNull(specialtyApiResponseFactory);
		requireNonNull(appointmentApiResponseFactory);
		requireNonNull(followupApiResponseFactory);
		requireNonNull(providerApiResponseFactory);
		requireNonNull(clinicApiResponseFactory);
		requireNonNull(appointmentService);
		requireNonNull(providerService);
		requireNonNull(clinicService);
		requireNonNull(followupService);
		requireNonNull(formatter);
		requireNonNull(strings);
		requireNonNull(request);
		requireNonNull(providerFinds);
		requireNonNull(supplements);

		CurrentContext currentContext = currentContextProvider.get();
		Locale locale = currentContext.getLocale();
		Set<UUID> providerIds = new HashSet<>();

		// Group by date
		SortedMap<LocalDate, List<ProviderFind>> providerFindsByDate = new TreeMap<>();

		for (ProviderFind providerFind : providerFinds) {
			providerIds.add(providerFind.getProviderId());

			for (ProviderFind.AvailabilityDate availabilityDate : providerFind.getDates()) {
				List<ProviderFind> providerFindsForDate = providerFindsByDate.get(availabilityDate.getDate());

				if (providerFindsForDate == null) {
					providerFindsForDate = new ArrayList<>();
					providerFindsByDate.put(availabilityDate.getDate(), providerFindsForDate);
				}

				providerFindsForDate.add(providerFind);
			}
		}

		// 3. Walk grouped dates to prepare for response
		List<Object> sections = new ArrayList<>(providerFindsByDate.size());

		for (Map.Entry<LocalDate, List<ProviderFind>> entry : providerFindsByDate.entrySet()) {
			LocalDate date = entry.getKey();
			List<ProviderFind> providerFindsForDate = entry.getValue();
			List<Object> normalizedProviderFinds = new ArrayList<>(providerFindsForDate.size());

			boolean allProvidersFullyBooked = true;

			for (ProviderFind providerFind : providerFindsForDate) {
				List<Object> normalizedTimes = new ArrayList<>(providerFindsForDate.size());

				boolean providerFullyBooked = false;

				for (ProviderFind.AvailabilityDate availabilityDate : providerFind.getDates()) {
					if (availabilityDate.getDate().equals(date)) {
						for (ProviderFind.AvailabilityTime availabilityTime : availabilityDate.getTimes()) {
							Map<String, Object> normalizedTime = new LinkedHashMap<>();
							normalizedTime.put("time", availabilityTime.getTime());
							normalizedTime.put("timeDescription", normalizeTimeFormat(formatter.formatTime(availabilityTime.getTime(), FormatStyle.SHORT), locale));
							normalizedTime.put("status", availabilityTime.getStatus());
							normalizedTime.put("epicDepartmentId", availabilityTime.getEpicDepartmentId());
							normalizedTime.put("appointmentTypeIds", availabilityTime.getAppointmentTypeIds());
							normalizedTimes.add(normalizedTime);
						}

						providerFullyBooked = availabilityDate.getFullyBooked();
						allProvidersFullyBooked = allProvidersFullyBooked && providerFullyBooked;
					}
				}

				Map<String, Object> normalizedProviderFind = new LinkedHashMap<>();
				normalizedProviderFind.put("providerId", providerFind.getProviderId());
				normalizedProviderFind.put("name", providerFind.getName());
				normalizedProviderFind.put("title", providerFind.getTitle());
				normalizedProviderFind.put("entity", providerFind.getEntity());
				normalizedProviderFind.put("clinic", providerFind.getClinic());
				normalizedProviderFind.put("license", providerFind.getLicense());
				normalizedProviderFind.put("specialty", providerFind.getSpecialty());
				normalizedProviderFind.put("supportRolesDescription", providerFind.getSupportRolesDescription());
				normalizedProviderFind.put("imageUrl", providerFind.getImageUrl());
				normalizedProviderFind.put("bioUrl", providerFind.getBioUrl());
				normalizedProviderFind.put("schedulingSystemId", providerFind.getSchedulingSystemId());
				normalizedProviderFind.put("phoneNumberRequiredForAppointment", providerFind.getPhoneNumberRequiredForAppointment());
				normalizedProviderFind.put("paymentFundingDescriptions", providerFind.getPaymentFundingDescriptions());
				normalizedProviderFind.put("fullyBooked", providerFullyBooked);
				normalizedProviderFind.put("treatmentDescription", providerFind.getTreatmentDescription());
				normalizedProviderFind.put("intakeAssessmentRequired", providerFind.getIntakeAssessmentRequired());
				normalizedProviderFind.put("intakeAssessmentIneligible", providerFind.getIntakeAssessmentIneligible());
				normalizedProviderFind.put("skipIntakePrompt", providerFind.getSkipIntakePrompt());
				normalizedProviderFind.put("appointmentTypeIds", providerFind.getAppointmentTypeIds());
				normalizedProviderFind.put("times", normalizedTimes);
				List<UUID> specialtyIds = providerFind.getSpecialties().stream()
						.map(specialty -> specialty.getSpecialtyId())
						.collect(Collectors.toList());
				normalizedProviderFind.put("specialtyIds", specialtyIds);

				normalizedProviderFinds.add(normalizedProviderFind);
			}

			Map<String, Object> section = new LinkedHashMap<>();
			section.put("date", date);
			section.put("dateDescription", formatter.formatDate(date, FormatStyle.FULL));
			section.put("fullyBooked", allProvidersFullyBooked);
			section.put("providers", normalizedProviderFinds);

			sections.add(section);
		}

		// Extract distinct appointment types and epic department IDs from raw results
		Set<UUID> appointmentTypeIds = new HashSet<>();
		Set<UUID> epicDepartmentIds = new HashSet<>();

		for (ProviderFind providerFind : providerFinds)
			if (providerFind.getAppointmentTypeIds() != null)
				appointmentTypeIds.addAll(providerFind.getAppointmentTypeIds());

		List<Map<String, Object>> appointmentTypesJson = appointmentService.findAppointmentTypesByInstitutionId(request.getInstitutionId()).stream()
				.filter((appointmentType -> appointmentTypeIds.contains(appointmentType.getAppointmentTypeId())))
				.map((appointmentType -> {
					Map<String, Object> appointmentTypeJson = new LinkedHashMap<>();
					appointmentTypeJson.put("appointmentTypeId", appointmentType.getAppointmentTypeId());
					appointmentTypeJson.put("schedulingSystemId", appointmentType.getSchedulingSystemId());
					appointmentTypeJson.put("visitTypeId", appointmentType.getVisitTypeId());
					appointmentTypeJson.put("acuityAppointmentTypeId", appointmentType.getAcuityAppointmentTypeId());
					appointmentTypeJson.put("assessmentId", appointmentType.getAssessmentId());
					appointmentTypeJson.put("epicVisitTypeId", appointmentType.getEpicVisitTypeId());
					appointmentTypeJson.put("epicVisitTypeIdType", appointmentType.getEpicVisitTypeIdType());
					appointmentTypeJson.put("name", appointmentType.getName());
					appointmentTypeJson.put("description", appointmentType.getDescription());
					appointmentTypeJson.put("durationInMinutes", appointmentType.getDurationInMinutes());
					appointmentTypeJson.put("durationInMinutesDescription", strings.get("{{duration}} minutes", new HashMap<String, Object>() {{
						put("duration", appointmentType.getDurationInMinutes());
					}}));

					return appointmentTypeJson;
				}))
				.collect(Collectors.toList());

		for (ProviderFind providerFind : providerFinds)
			if (providerFind.getEpicDepartmentIds() != null)
				epicDepartmentIds.addAll(providerFind.getEpicDepartmentIds());

		List<Map<String, Object>> epicDepartmentsJson = appointmentService.findEpicDepartmentsByInstitutionId(request.getInstitutionId()).stream()
				.filter((epicDepartment -> epicDepartmentIds.contains(epicDepartment.getEpicDepartmentId())))
				.map((epicDepartment -> {
					Map<String, Object> epicDepartmentJson = new LinkedHashMap<>();
					epicDepartmentJson.put("epicDepartmentId", epicDepartment.getEpicDepartmentId());
					epicDepartmentJson.put("departmentId", epicDepartment.getDepartmentId());
					epicDepartmentJson.put("departmentIdType", epicDepartment.getDepartmentIdType());
					epicDepartmentJson.put("name", epicDepartment.getName());

					return epicDepartmentJson;
				}))
				.collect(Collectors.toList());

		// Pull out distinct specialties from the provider data
		Map<UUID, Specialty> specialtiesById = providerFinds.stream()
				.map(providerFind -> providerFind.getSpecialties())
				.filter(providerSpecialties -> providerSpecialties != null && providerSpecialties.size() > 0)
				.flatMap(Collection::stream)
				.collect(Collectors.toMap(Specialty::getSpecialtyId, Function.identity(), (existing, replacement) -> existing));

		List<Specialty> specialties = new ArrayList<>(specialtiesById.values());

		// If caller filters on clinics, return the clinics that were filtered on
		List<Clinic> clinics = new ArrayList<>();

		if (request.getClinicIds() != null && request.getClinicIds().size() > 0)
			clinics.addAll(clinicService.findClinicsByInstitutionId(request.getInstitutionId()).stream()
					.filter(clinic -> request.getClinicIds().contains(clinic.getClinicId()))
					.collect(Collectors.toList()));

		// Same for provider
		Provider provider = request.getProviderId() == null ? null : providerService.findProviderById(request.getProviderId()).orElse(null);

		// If appointments are specified and requestor has permission, pull them too
		List<Appointment> appointments = new ArrayList<>();
		boolean includeAppointments = supplements.contains(ProviderFindSupplement.APPOINTMENTS);

		if (includeAppointments) {
			for (UUID providerId : providerIds) {
				List<Appointment> providerAppointments = appointmentService.findActiveAppointmentsForProviderId(providerId, request.getStartDate(), request.getEndDate());
				appointments.addAll(providerAppointments);
			}
		}

		List<Appointment> sortedAppointments = appointments.stream()
				.sorted(Comparator
						.comparing(Appointment::getStartTime)
						.thenComparing(Appointment::getAccountId))
				.collect(Collectors.toList());

		// If followups are specified and requestor has permission, pull them too
		List<Followup> followups = new ArrayList<>();
		boolean includeFollowups = supplements.contains(ProviderFindSupplement.FOLLOWUPS);

		if (includeFollowups) {
			for (UUID providerId : providerIds) {
				List<Followup> providerFollowups = followupService.findFollowupsByProviderId(providerId, request.getStartDate(), request.getEndDate());
				followups.addAll(providerFollowups);
			}
		}

		List<Followup> sortedFollowups = followups.stream()
				.sorted(Comparator
						.comparing(Followup::getFollowupDate)
						.thenComparing(Followup::getAccountId))
				.collect(Collectors.toList());

		this.sections = sections;
		this.appointmentTypes = appointmentTypesJson;
		this.epicDepartments = epicDepartmentsJson;

		this.provider = provider != null ? providerApiResponseFactory.create(provider, ProviderApiResponseSupplement.PAYMENT_FUNDING) : null;

		this.clinics = clinics.size() > 0 ? clinics.stream()
				.map(clinic -> clinicApiResponseFactory.create(clinic))
				.collect(Collectors.toList()) : null;

		if (specialties.size() > 0) {
			this.specialties = specialties.stream()
					.map(specialty -> specialtyApiResponseFactory.create(specialty))
					.collect(Collectors.toList());
			this.showSpecialties = true;
		} else {
			this.specialties = null;
			this.showSpecialties = false;
		}

		this.appointments = includeAppointments ? sortedAppointments.stream()
				.map(appointment -> appointmentApiResponseFactory.create(appointment, Set.of(AppointmentApiResponse.AppointmentApiResponseSupplement.ACCOUNT, AppointmentApiResponse.AppointmentApiResponseSupplement.APPOINTMENT_REASON)))
				.collect(Collectors.toList()) : null;

		this.followups = includeFollowups ? sortedFollowups.stream()
				.map(followup -> followupApiResponseFactory.create(followup, Set.of(FollowupApiResponse.FollowupApiResponseSupplement.ALL)))
				.collect(Collectors.toList()) : null;
	}

	@Nonnull
	protected String normalizeTimeFormat(@Nonnull String timeDescription,
																			 @Nonnull Locale locale) {
		requireNonNull(timeDescription);
		requireNonNull(locale);

		// Turns "10:00 AM" into "10:00am", for example
		return timeDescription.replace(" ", "").toLowerCase(locale);
	}

	@Nonnull
	public List<Object> getSections() {
		return sections;
	}

	@Nonnull
	public List<Map<String, Object>> getAppointmentTypes() {
		return appointmentTypes;
	}

	@Nonnull
	public List<Map<String, Object>> getEpicDepartments() {
		return epicDepartments;
	}

	@Nullable
	public ProviderApiResponse getProvider() {
		return provider;
	}

	@Nullable
	public List<ClinicApiResponse> getClinics() {
		return clinics;
	}

	@Nonnull
	public Boolean getShowSpecialties() {
		return showSpecialties;
	}

	@Nullable
	public List<SpecialtyApiResponse> getSpecialties() {
		return specialties;
	}

	@Nullable
	public List<AppointmentApiResponse> getAppointments() {
		return appointments;
	}

	@Nullable
	public List<FollowupApiResponse> getFollowups() {
		return followups;
	}
}