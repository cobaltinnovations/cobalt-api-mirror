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

package com.cobaltplatform.api.integration.hl7.model.segment;

import ca.uhn.hl7v2.model.v251.segment.PID;
import com.cobaltplatform.api.integration.hl7.model.Hl7Object;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7CodedElement;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7CodedWithExceptions;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7DriverLicenseNumber;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedAddress;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedCompositeIdWithCheckDigit;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedPersonName;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedTelecommunicationNumber;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7HierarchicDesignator;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7TimeStamp;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * See https://hl7-definition.caristix.com/v2/hl7v2.5.1/Segments/PID
 *
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class Hl7PatientIdentificationSegment extends Hl7Object {
	@Nullable
	private Integer setId; // PID.1 - Set ID - PID
	@Nullable
	private Hl7ExtendedCompositeIdWithCheckDigit patientId; // PID.2 - Patient ID
	@Nullable
	private List<Hl7ExtendedCompositeIdWithCheckDigit> patientIdentifierList; // PID.3 - Patient Identifier List
	@Nullable
	private List<Hl7ExtendedCompositeIdWithCheckDigit> alternatePatientId; // PID.4 - Alternate Patient ID - PID
	@Nullable
	private List<Hl7ExtendedPersonName> patientName; // PID.5 - Patient Name
	@Nullable
	private List<Hl7ExtendedPersonName> mothersMaidenName; // PID.6 - Mother's Maiden Name
	@Nullable
	private Hl7TimeStamp dateTimeOfBirth; // PID.7 - Date/Time of Birth
	@Nullable
	private String administrativeSex; // PID.8 - Administrative Sex
	@Nullable
	private List<Hl7ExtendedPersonName> patientAlias; // PID.9 - Patient Alias
	@Nullable
	private List<Hl7CodedElement> race; // PID.10 - Race
	@Nullable
	private List<Hl7ExtendedAddress> patientAddress; // PID.11 - Patient Address
	@Nullable
	private String countyCode; // PID.12 - County Code
	@Nullable
	private List<Hl7ExtendedTelecommunicationNumber> phoneNumberHome; // PID.13 - Phone Number - Home
	@Nullable
	private List<Hl7ExtendedTelecommunicationNumber> phoneNumberBusiness; // PID.14 - Phone Number - Business
	@Nullable
	private Hl7CodedElement primaryLanguage; // PID.15 - Primary Language
	@Nullable
	private Hl7CodedElement maritalStatus; // PID.16 - Marital Status
	@Nullable
	private Hl7CodedElement religion; // PID.17 - Religion
	@Nullable
	private Hl7ExtendedCompositeIdWithCheckDigit patientAccountNumber; // PID.18 - Patient Account Number
	@Nullable
	private String ssnNumberPatient; // PID.19 - SSN Number - Patient
	@Nullable
	private Hl7DriverLicenseNumber driversLicenseNumberPatient; // PID.20 - Driver's License Number - Patient
	@Nullable
	private List<Hl7ExtendedCompositeIdWithCheckDigit> mothersIdentifier; // PID.21 - Mother's Identifier
	@Nullable
	private List<Hl7CodedElement> ethnicGroup; // PID.22 - Ethnic Group
	@Nullable
	private String birthPlace; // PID.23 - Birth Place
	@Nullable
	private String multipleBirthIndicator; // PID.24 - Multiple Birth Indicator
	@Nullable
	private Integer birthOrder; // PID.25 - Birth Order
	@Nullable
	private List<Hl7CodedElement> citizenship; // PID.26 - Citizenship
	@Nullable
	private Hl7CodedElement veteransMilitaryStatus; // PID.27 - Veterans Military Status
	@Nullable
	private Hl7CodedElement nationality; // PID.28 - Nationality
	@Nullable
	private Hl7TimeStamp patientDeathDateAndTime; // PID.29 - Patient Death Date and Time
	@Nullable
	private String patientDeathIndicator; // PID.30 - Patient Death Indicator
	@Nullable
	private String identityUnknownIndicator; // PID.31 - Identity Unknown Indicator
	@Nullable
	private List<String> identityReliabilityCode; // PID.32 - Identity Reliability Code
	@Nullable
	private Hl7TimeStamp lastUpdateDateTime; // PID.33 - Last Update Date/Time
	@Nullable
	private Hl7HierarchicDesignator lastUpdateFacility; // PID.34 - Last Update Facility
	@Nullable
	private Hl7CodedElement speciesCode; // PID.35 - Species Code
	@Nullable
	private Hl7CodedElement breedCode; // PID.36 - Breed Code
	@Nullable
	private String strain; // PID.37 - Strain
	@Nullable
	private Hl7CodedElement productionClassCode; // PID.38 - Production Class Code
	@Nullable
	private List<Hl7CodedWithExceptions> tribalCitizenship; // PID.39 - Tribal Citizenship

	@Nonnull
	public static Boolean isPresent(@Nullable PID pid) {
		if (pid == null)
			return false;

		return trimToNull(pid.getSetIDPID().getValue()) != null
				|| Hl7ExtendedCompositeIdWithCheckDigit.isPresent(pid.getPatientID())
				|| (pid.getPatientIdentifierList() != null && pid.getPatientIdentifierList().length > 0)
				|| (pid.getAlternatePatientIDPID() != null && pid.getAlternatePatientIDPID().length > 0)
				|| (pid.getPatientName() != null && pid.getPatientName().length > 0)
				|| (pid.getMotherSMaidenName() != null && pid.getMotherSMaidenName().length > 0)
				|| Hl7TimeStamp.isPresent(pid.getDateTimeOfBirth())
				|| trimToNull(pid.getAdministrativeSex().getValueOrEmpty()) != null
				|| (pid.getPatientAlias() != null && pid.getPatientAlias().length > 0)
				|| (pid.getRace() != null && pid.getRace().length > 0)
				|| (pid.getPatientAddress() != null && pid.getPatientAddress().length > 0)
				|| trimToNull(pid.getCountyCode().getValueOrEmpty()) != null
				|| (pid.getPhoneNumberHome() != null && pid.getPhoneNumberHome().length > 0)
				|| (pid.getPhoneNumberBusiness() != null && pid.getPhoneNumberBusiness().length > 0)
				|| Hl7CodedElement.isPresent(pid.getPrimaryLanguage())
				|| Hl7CodedElement.isPresent(pid.getMaritalStatus())
				|| Hl7CodedElement.isPresent(pid.getReligion())
				|| Hl7ExtendedCompositeIdWithCheckDigit.isPresent(pid.getPatientAccountNumber())
				|| trimToNull(pid.getSSNNumberPatient().getValueOrEmpty()) != null
				|| Hl7DriverLicenseNumber.isPresent(pid.getDriverSLicenseNumberPatient())
				|| (pid.getMotherSIdentifier() != null && pid.getMotherSIdentifier().length > 0)
				|| (pid.getEthnicGroup() != null && pid.getEthnicGroup().length > 0)
				|| trimToNull(pid.getBirthPlace().getValueOrEmpty()) != null
				|| trimToNull(pid.getMultipleBirthIndicator().getValueOrEmpty()) != null
				|| trimToNull(pid.getBirthOrder().getValue()) != null
				|| (pid.getCitizenship() != null && pid.getCitizenship().length > 0)
				|| Hl7CodedElement.isPresent(pid.getVeteransMilitaryStatus())
				|| Hl7CodedElement.isPresent(pid.getNationality())
				|| Hl7TimeStamp.isPresent(pid.getPatientDeathDateAndTime())
				|| trimToNull(pid.getPatientDeathIndicator().getValue()) != null
				|| trimToNull(pid.getIdentityUnknownIndicator().getValue()) != null
				|| (pid.getIdentityReliabilityCode() != null && pid.getIdentityReliabilityCode().length > 0)
				|| Hl7TimeStamp.isPresent(pid.getLastUpdateDateTime())
				|| Hl7HierarchicDesignator.isPresent(pid.getLastUpdateFacility())
				|| Hl7CodedElement.isPresent(pid.getSpeciesCode())
				|| Hl7CodedElement.isPresent(pid.getBreedCode())
				|| trimToNull(pid.getStrain().getValue()) != null
				|| Hl7CodedElement.isPresent(pid.getProductionClassCode())
				|| (pid.getTribalCitizenship() != null && pid.getTribalCitizenship().length > 0);
	}

	public Hl7PatientIdentificationSegment() {
		// Nothing to do
	}

	public Hl7PatientIdentificationSegment(@Nullable PID pid) {
		if (pid != null) {
			String setIdAsString = trimToNull(pid.getSetIDPID().getValue());
			if (setIdAsString != null)
				this.setId = Integer.parseInt(setIdAsString, 10);

			this.patientId = Hl7ExtendedCompositeIdWithCheckDigit.isPresent(pid.getPatientID()) ? new Hl7ExtendedCompositeIdWithCheckDigit(pid.getPatientID()) : null;

			if (pid.getPatientIdentifierList() != null && pid.getPatientIdentifierList().length > 0)
				this.patientIdentifierList = Arrays.stream(pid.getPatientIdentifierList())
						.map(cx -> Hl7ExtendedCompositeIdWithCheckDigit.isPresent(cx) ? new Hl7ExtendedCompositeIdWithCheckDigit(cx) : null)
						.filter(patientIdentifier -> patientIdentifier != null)
						.collect(Collectors.toList());

			if (pid.getAlternatePatientIDPID() != null && pid.getAlternatePatientIDPID().length > 0)
				this.alternatePatientId = Arrays.stream(pid.getAlternatePatientIDPID())
						.map(cx -> Hl7ExtendedCompositeIdWithCheckDigit.isPresent(cx) ? new Hl7ExtendedCompositeIdWithCheckDigit(cx) : null)
						.filter(patientIdentifier -> patientIdentifier != null)
						.collect(Collectors.toList());

			if (pid.getPatientName() != null && pid.getPatientName().length > 0)
				this.patientName = Arrays.stream(pid.getPatientName())
						.map(xpn -> Hl7ExtendedPersonName.isPresent(xpn) ? new Hl7ExtendedPersonName(xpn) : null)
						.filter(patientName -> patientName != null)
						.collect(Collectors.toList());

			if (pid.getMotherSMaidenName() != null && pid.getMotherSMaidenName().length > 0)
				this.mothersMaidenName = Arrays.stream(pid.getMotherSMaidenName())
						.map(xpn -> Hl7ExtendedPersonName.isPresent(xpn) ? new Hl7ExtendedPersonName(xpn) : null)
						.filter(mothersMaidenName -> mothersMaidenName != null)
						.collect(Collectors.toList());

			if (Hl7TimeStamp.isPresent(pid.getDateTimeOfBirth()))
				this.dateTimeOfBirth = new Hl7TimeStamp(pid.getDateTimeOfBirth());

			this.administrativeSex = trimToNull(pid.getAdministrativeSex().getValueOrEmpty());

			if (pid.getPatientAlias() != null && pid.getPatientAlias().length > 0)
				this.patientAlias = Arrays.stream(pid.getPatientAlias())
						.map(xpn -> Hl7ExtendedPersonName.isPresent(xpn) ? new Hl7ExtendedPersonName(xpn) : null)
						.filter(patientAlias -> patientAlias != null)
						.collect(Collectors.toList());

			if (pid.getRace() != null && pid.getRace().length > 0)
				this.race = Arrays.stream(pid.getRace())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(race -> race != null)
						.collect(Collectors.toList());

			if (pid.getPatientAddress() != null && pid.getPatientAddress().length > 0)
				this.patientAddress = Arrays.stream(pid.getPatientAddress())
						.map(xad -> Hl7ExtendedAddress.isPresent(xad) ? new Hl7ExtendedAddress(xad) : null)
						.filter(patientAddress -> patientAddress != null)
						.collect(Collectors.toList());

			this.countyCode = trimToNull(pid.getCountyCode().getValueOrEmpty());

			if (pid.getPhoneNumberHome() != null && pid.getPhoneNumberHome().length > 0)
				this.phoneNumberHome = Arrays.stream(pid.getPhoneNumberHome())
						.map(xtn -> Hl7ExtendedTelecommunicationNumber.isPresent(xtn) ? new Hl7ExtendedTelecommunicationNumber(xtn) : null)
						.filter(phoneNumberHome -> phoneNumberHome != null)
						.collect(Collectors.toList());

			if (pid.getPhoneNumberBusiness() != null && pid.getPhoneNumberBusiness().length > 0)
				this.phoneNumberBusiness = Arrays.stream(pid.getPhoneNumberBusiness())
						.map(xtn -> Hl7ExtendedTelecommunicationNumber.isPresent(xtn) ? new Hl7ExtendedTelecommunicationNumber(xtn) : null)
						.filter(phoneNumberBusiness -> phoneNumberBusiness != null)
						.collect(Collectors.toList());

			if (Hl7CodedElement.isPresent(pid.getPrimaryLanguage()))
				this.primaryLanguage = new Hl7CodedElement(pid.getPrimaryLanguage());

			if (Hl7CodedElement.isPresent(pid.getMaritalStatus()))
				this.maritalStatus = new Hl7CodedElement(pid.getMaritalStatus());

			if (Hl7CodedElement.isPresent(pid.getReligion()))
				this.religion = new Hl7CodedElement(pid.getReligion());

			if (Hl7ExtendedCompositeIdWithCheckDigit.isPresent(pid.getPatientAccountNumber()))
				this.patientAccountNumber = new Hl7ExtendedCompositeIdWithCheckDigit(pid.getPatientAccountNumber());

			this.ssnNumberPatient = trimToNull(pid.getSSNNumberPatient().getValueOrEmpty());

			if (Hl7DriverLicenseNumber.isPresent(pid.getDriverSLicenseNumberPatient()))
				this.driversLicenseNumberPatient = new Hl7DriverLicenseNumber(pid.getDriverSLicenseNumberPatient());

			if (pid.getMotherSIdentifier() != null && pid.getMotherSIdentifier().length > 0)
				this.mothersIdentifier = Arrays.stream(pid.getMotherSIdentifier())
						.map(cx -> Hl7ExtendedCompositeIdWithCheckDigit.isPresent(cx) ? new Hl7ExtendedCompositeIdWithCheckDigit(cx) : null)
						.filter(mothersIdentifier -> mothersIdentifier != null)
						.collect(Collectors.toList());

			if (pid.getEthnicGroup() != null && pid.getEthnicGroup().length > 0)
				this.ethnicGroup = Arrays.stream(pid.getEthnicGroup())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(ethnicGroup -> ethnicGroup != null)
						.collect(Collectors.toList());

			this.birthPlace = trimToNull(pid.getBirthPlace().getValueOrEmpty());
			this.multipleBirthIndicator = trimToNull(pid.getMultipleBirthIndicator().getValueOrEmpty());

			String birthOrderAsString = trimToNull(pid.getBirthOrder().getValue());
			if (birthOrderAsString != null)
				this.birthOrder = Integer.parseInt(birthOrderAsString, 10);

			if (pid.getCitizenship() != null && pid.getCitizenship().length > 0)
				this.citizenship = Arrays.stream(pid.getCitizenship())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(citizenship -> citizenship != null)
						.collect(Collectors.toList());

			if (Hl7CodedElement.isPresent(pid.getVeteransMilitaryStatus()))
				this.veteransMilitaryStatus = new Hl7CodedElement(pid.getVeteransMilitaryStatus());

			if (Hl7CodedElement.isPresent(pid.getNationality()))
				this.nationality = new Hl7CodedElement(pid.getNationality());

			if (Hl7TimeStamp.isPresent(pid.getPatientDeathDateAndTime()))
				this.patientDeathDateAndTime = new Hl7TimeStamp(pid.getPatientDeathDateAndTime());

			this.patientDeathIndicator = trimToNull(pid.getPatientDeathIndicator().getValueOrEmpty());
			this.identityUnknownIndicator = trimToNull(pid.getIdentityUnknownIndicator().getValueOrEmpty());

			if (pid.getIdentityReliabilityCode() != null && pid.getIdentityReliabilityCode().length > 0)
				this.identityReliabilityCode = Arrays.stream(pid.getIdentityReliabilityCode())
						.map(is -> trimToNull(is.getValueOrEmpty()))
						.filter(identityReliabilityCode -> identityReliabilityCode != null)
						.collect(Collectors.toList());

			if (Hl7TimeStamp.isPresent(pid.getLastUpdateDateTime()))
				this.lastUpdateDateTime = new Hl7TimeStamp(pid.getLastUpdateDateTime());

			if (Hl7HierarchicDesignator.isPresent(pid.getLastUpdateFacility()))
				this.lastUpdateFacility = new Hl7HierarchicDesignator(pid.getLastUpdateFacility());

			if (Hl7CodedElement.isPresent(pid.getSpeciesCode()))
				this.speciesCode = new Hl7CodedElement(pid.getSpeciesCode());

			if (Hl7CodedElement.isPresent(pid.getBreedCode()))
				this.breedCode = new Hl7CodedElement(pid.getBreedCode());

			this.strain = trimToNull(pid.getStrain().getValueOrEmpty());

			if (Hl7CodedElement.isPresent(pid.getProductionClassCode()))
				this.productionClassCode = new Hl7CodedElement(pid.getProductionClassCode());

			if (pid.getTribalCitizenship() != null && pid.getTribalCitizenship().length > 0)
				this.tribalCitizenship = Arrays.stream(pid.getTribalCitizenship())
						.map(cwe -> Hl7CodedWithExceptions.isPresent(cwe) ? new Hl7CodedWithExceptions(cwe) : null)
						.filter(tribalCitizenship -> tribalCitizenship != null)
						.collect(Collectors.toList());
		}
	}

	@Nullable
	public Integer getSetId() {
		return this.setId;
	}

	public void setSetId(@Nullable Integer setId) {
		this.setId = setId;
	}

	@Nullable
	public Hl7ExtendedCompositeIdWithCheckDigit getPatientId() {
		return this.patientId;
	}

	public void setPatientId(@Nullable Hl7ExtendedCompositeIdWithCheckDigit patientId) {
		this.patientId = patientId;
	}

	@Nullable
	public List<Hl7ExtendedCompositeIdWithCheckDigit> getPatientIdentifierList() {
		return this.patientIdentifierList;
	}

	public void setPatientIdentifierList(@Nullable List<Hl7ExtendedCompositeIdWithCheckDigit> patientIdentifierList) {
		this.patientIdentifierList = patientIdentifierList;
	}

	@Nullable
	public List<Hl7ExtendedCompositeIdWithCheckDigit> getAlternatePatientId() {
		return this.alternatePatientId;
	}

	public void setAlternatePatientId(@Nullable List<Hl7ExtendedCompositeIdWithCheckDigit> alternatePatientId) {
		this.alternatePatientId = alternatePatientId;
	}

	@Nullable
	public List<Hl7ExtendedPersonName> getPatientName() {
		return this.patientName;
	}

	public void setPatientName(@Nullable List<Hl7ExtendedPersonName> patientName) {
		this.patientName = patientName;
	}

	@Nullable
	public List<Hl7ExtendedPersonName> getMothersMaidenName() {
		return this.mothersMaidenName;
	}

	public void setMothersMaidenName(@Nullable List<Hl7ExtendedPersonName> mothersMaidenName) {
		this.mothersMaidenName = mothersMaidenName;
	}

	@Nullable
	public Hl7TimeStamp getDateTimeOfBirth() {
		return this.dateTimeOfBirth;
	}

	public void setDateTimeOfBirth(@Nullable Hl7TimeStamp dateTimeOfBirth) {
		this.dateTimeOfBirth = dateTimeOfBirth;
	}

	@Nullable
	public String getAdministrativeSex() {
		return this.administrativeSex;
	}

	public void setAdministrativeSex(@Nullable String administrativeSex) {
		this.administrativeSex = administrativeSex;
	}

	@Nullable
	public List<Hl7ExtendedPersonName> getPatientAlias() {
		return this.patientAlias;
	}

	public void setPatientAlias(@Nullable List<Hl7ExtendedPersonName> patientAlias) {
		this.patientAlias = patientAlias;
	}

	@Nullable
	public List<Hl7CodedElement> getRace() {
		return this.race;
	}

	public void setRace(@Nullable List<Hl7CodedElement> race) {
		this.race = race;
	}

	@Nullable
	public List<Hl7ExtendedAddress> getPatientAddress() {
		return this.patientAddress;
	}

	public void setPatientAddress(@Nullable List<Hl7ExtendedAddress> patientAddress) {
		this.patientAddress = patientAddress;
	}

	@Nullable
	public String getCountyCode() {
		return this.countyCode;
	}

	public void setCountyCode(@Nullable String countyCode) {
		this.countyCode = countyCode;
	}

	@Nullable
	public List<Hl7ExtendedTelecommunicationNumber> getPhoneNumberHome() {
		return this.phoneNumberHome;
	}

	public void setPhoneNumberHome(@Nullable List<Hl7ExtendedTelecommunicationNumber> phoneNumberHome) {
		this.phoneNumberHome = phoneNumberHome;
	}

	@Nullable
	public List<Hl7ExtendedTelecommunicationNumber> getPhoneNumberBusiness() {
		return this.phoneNumberBusiness;
	}

	public void setPhoneNumberBusiness(@Nullable List<Hl7ExtendedTelecommunicationNumber> phoneNumberBusiness) {
		this.phoneNumberBusiness = phoneNumberBusiness;
	}

	@Nullable
	public Hl7CodedElement getPrimaryLanguage() {
		return this.primaryLanguage;
	}

	public void setPrimaryLanguage(@Nullable Hl7CodedElement primaryLanguage) {
		this.primaryLanguage = primaryLanguage;
	}

	@Nullable
	public Hl7CodedElement getMaritalStatus() {
		return this.maritalStatus;
	}

	public void setMaritalStatus(@Nullable Hl7CodedElement maritalStatus) {
		this.maritalStatus = maritalStatus;
	}

	@Nullable
	public Hl7CodedElement getReligion() {
		return this.religion;
	}

	public void setReligion(@Nullable Hl7CodedElement religion) {
		this.religion = religion;
	}

	@Nullable
	public Hl7ExtendedCompositeIdWithCheckDigit getPatientAccountNumber() {
		return this.patientAccountNumber;
	}

	public void setPatientAccountNumber(@Nullable Hl7ExtendedCompositeIdWithCheckDigit patientAccountNumber) {
		this.patientAccountNumber = patientAccountNumber;
	}

	@Nullable
	public String getSsnNumberPatient() {
		return this.ssnNumberPatient;
	}

	public void setSsnNumberPatient(@Nullable String ssnNumberPatient) {
		this.ssnNumberPatient = ssnNumberPatient;
	}

	@Nullable
	public Hl7DriverLicenseNumber getDriversLicenseNumberPatient() {
		return this.driversLicenseNumberPatient;
	}

	public void setDriversLicenseNumberPatient(@Nullable Hl7DriverLicenseNumber driversLicenseNumberPatient) {
		this.driversLicenseNumberPatient = driversLicenseNumberPatient;
	}

	@Nullable
	public List<Hl7ExtendedCompositeIdWithCheckDigit> getMothersIdentifier() {
		return this.mothersIdentifier;
	}

	public void setMothersIdentifier(@Nullable List<Hl7ExtendedCompositeIdWithCheckDigit> mothersIdentifier) {
		this.mothersIdentifier = mothersIdentifier;
	}

	@Nullable
	public List<Hl7CodedElement> getEthnicGroup() {
		return this.ethnicGroup;
	}

	public void setEthnicGroup(@Nullable List<Hl7CodedElement> ethnicGroup) {
		this.ethnicGroup = ethnicGroup;
	}

	@Nullable
	public String getBirthPlace() {
		return this.birthPlace;
	}

	public void setBirthPlace(@Nullable String birthPlace) {
		this.birthPlace = birthPlace;
	}

	@Nullable
	public String getMultipleBirthIndicator() {
		return this.multipleBirthIndicator;
	}

	public void setMultipleBirthIndicator(@Nullable String multipleBirthIndicator) {
		this.multipleBirthIndicator = multipleBirthIndicator;
	}

	@Nullable
	public Integer getBirthOrder() {
		return this.birthOrder;
	}

	public void setBirthOrder(@Nullable Integer birthOrder) {
		this.birthOrder = birthOrder;
	}

	@Nullable
	public List<Hl7CodedElement> getCitizenship() {
		return this.citizenship;
	}

	public void setCitizenship(@Nullable List<Hl7CodedElement> citizenship) {
		this.citizenship = citizenship;
	}

	@Nullable
	public Hl7CodedElement getVeteransMilitaryStatus() {
		return this.veteransMilitaryStatus;
	}

	public void setVeteransMilitaryStatus(@Nullable Hl7CodedElement veteransMilitaryStatus) {
		this.veteransMilitaryStatus = veteransMilitaryStatus;
	}

	@Nullable
	public Hl7CodedElement getNationality() {
		return this.nationality;
	}

	public void setNationality(@Nullable Hl7CodedElement nationality) {
		this.nationality = nationality;
	}

	@Nullable
	public Hl7TimeStamp getPatientDeathDateAndTime() {
		return this.patientDeathDateAndTime;
	}

	public void setPatientDeathDateAndTime(@Nullable Hl7TimeStamp patientDeathDateAndTime) {
		this.patientDeathDateAndTime = patientDeathDateAndTime;
	}

	@Nullable
	public String getPatientDeathIndicator() {
		return this.patientDeathIndicator;
	}

	public void setPatientDeathIndicator(@Nullable String patientDeathIndicator) {
		this.patientDeathIndicator = patientDeathIndicator;
	}

	@Nullable
	public String getIdentityUnknownIndicator() {
		return this.identityUnknownIndicator;
	}

	public void setIdentityUnknownIndicator(@Nullable String identityUnknownIndicator) {
		this.identityUnknownIndicator = identityUnknownIndicator;
	}

	@Nullable
	public List<String> getIdentityReliabilityCode() {
		return this.identityReliabilityCode;
	}

	public void setIdentityReliabilityCode(@Nullable List<String> identityReliabilityCode) {
		this.identityReliabilityCode = identityReliabilityCode;
	}

	@Nullable
	public Hl7TimeStamp getLastUpdateDateTime() {
		return this.lastUpdateDateTime;
	}

	public void setLastUpdateDateTime(@Nullable Hl7TimeStamp lastUpdateDateTime) {
		this.lastUpdateDateTime = lastUpdateDateTime;
	}

	@Nullable
	public Hl7HierarchicDesignator getLastUpdateFacility() {
		return this.lastUpdateFacility;
	}

	public void setLastUpdateFacility(@Nullable Hl7HierarchicDesignator lastUpdateFacility) {
		this.lastUpdateFacility = lastUpdateFacility;
	}

	@Nullable
	public Hl7CodedElement getSpeciesCode() {
		return this.speciesCode;
	}

	public void setSpeciesCode(@Nullable Hl7CodedElement speciesCode) {
		this.speciesCode = speciesCode;
	}

	@Nullable
	public Hl7CodedElement getBreedCode() {
		return this.breedCode;
	}

	public void setBreedCode(@Nullable Hl7CodedElement breedCode) {
		this.breedCode = breedCode;
	}

	@Nullable
	public String getStrain() {
		return this.strain;
	}

	public void setStrain(@Nullable String strain) {
		this.strain = strain;
	}

	@Nullable
	public Hl7CodedElement getProductionClassCode() {
		return this.productionClassCode;
	}

	public void setProductionClassCode(@Nullable Hl7CodedElement productionClassCode) {
		this.productionClassCode = productionClassCode;
	}

	@Nullable
	public List<Hl7CodedWithExceptions> getTribalCitizenship() {
		return this.tribalCitizenship;
	}

	public void setTribalCitizenship(@Nullable List<Hl7CodedWithExceptions> tribalCitizenship) {
		this.tribalCitizenship = tribalCitizenship;
	}
}