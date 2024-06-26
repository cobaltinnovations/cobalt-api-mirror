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

import ca.uhn.hl7v2.model.v251.segment.OBR;
import com.cobaltplatform.api.integration.hl7.model.Hl7Object;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7CodedElement;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7CodedWithExceptions;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7CompositeQuantityWithUnits;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7EntityIdentifier;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7EntityIdentifierPair;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedCompositeIdNumberAndNameForPersons;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedTelecommunicationNumber;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7MoneyAndCode;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7NameWithDateAndLocation;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ParentResultLink;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7SpecimenSource;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7TimeStamp;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7TimingQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * See https://hl7-definition.caristix.com/v2/hl7v2.5.1/Segments/OBR
 *
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class Hl7ObservationRequestSegment extends Hl7Object {
	@Nullable
	private Integer setId; // OBR.1 - Set ID - OBR
	@Nullable
	private Hl7EntityIdentifier placerOrderNumber; // OBR.2 - Placer Order Number
	@Nullable
	private Hl7EntityIdentifier fillerOrderNumber; /// OBR.3 - Filler Order Number
	@Nullable
	private Hl7CodedElement universalServiceIdentifier; // OBR.4 - Universal Service Identifier
	@Nullable
	private String priority; // OBR.5 - Priority - OBR
	@Nullable
	private Hl7TimeStamp requestedDateTime; // OBR.6 - Requested Date/Time
	@Nullable
	private Hl7TimeStamp observationDateTime; // OBR.7 - Observation Date/Time
	@Nullable
	private Hl7TimeStamp observationEndDateTime; // OBR.8 - Observation End Date/Time
	@Nullable
	private Hl7CompositeQuantityWithUnits collectionVolume; // OBR.9 - Collection Volume
	@Nullable
	private List<Hl7ExtendedCompositeIdNumberAndNameForPersons> collectorIdentifier; // OBR.10 - Collector Identifier
	@Nullable
	private String specimenActionCode; // OBR.11 - Specimen Action Code
	@Nullable
	private Hl7CodedElement dangerCode; // OBR.12 - Danger Code
	@Nullable
	private String relevantClinicalInformation; // OBR.13 - Relevant Clinical Information
	@Nullable
	private Hl7TimeStamp specimenReceivedDateTime; // OBR.14 - Specimen Received Date/Time
	@Nullable
	private Hl7SpecimenSource specimenSource; // OBR.15 - Specimen Source
	@Nullable
	private List<Hl7ExtendedCompositeIdNumberAndNameForPersons> orderingProvider; // OBR.16 - Ordering Provider
	@Nullable
	private List<Hl7ExtendedTelecommunicationNumber> orderCallbackPhoneNumber; // OBR.17 - Order Callback Phone Number
	@Nullable
	private String placerField1; // OBR.18 - Placer Field 1
	@Nullable
	private String placerField2; // OBR.19 - Placer Field 2
	@Nullable
	private String fillerField1; // OBR.20 - Filler Field 1
	@Nullable
	private String fillerField2; // OBR.21 - Filler Field 2
	@Nullable
	private Hl7TimeStamp resultsRptStatusChngDateTime; // OBR.22 - Results Rpt/Status Chng - Date/Time
	@Nullable
	private Hl7MoneyAndCode chargeToPractice; // OBR.23 - Charge to Practice
	@Nullable
	private String diagnosticServSectId; // OBR.24 - Diagnostic Serv Sect ID
	@Nullable
	private String resultStatus; // OBR.25 - Result Status
	@Nullable
	private Hl7ParentResultLink parentResult; // OBR.26 - Parent Result
	@Nullable
	private List<Hl7TimingQuantity> quantityTiming; // OBR.27 - Quantity/Timing
	@Nullable
	private List<Hl7ExtendedCompositeIdNumberAndNameForPersons> resultCopiesTo; // OBR.28 - Result Copies To
	@Nullable
	private Hl7EntityIdentifierPair parent; // OBR.29 - Parent
	@Nullable
	private String transportationMode; // OBR.30 - Transportation Mode
	@Nullable
	private List<Hl7CodedElement> reasonForStudy; // OBR.31 - Reason for Study
	@Nullable
	private Hl7NameWithDateAndLocation principalResultInterpreter; // OBR.32 - Principal Result Interpreter
	@Nullable
	private List<Hl7NameWithDateAndLocation> assistantResultInterpreter; // OBR.33 - Assistant Result Interpreter
	@Nullable
	private List<Hl7NameWithDateAndLocation> technician; // OBR.34 - Technician
	@Nullable
	private List<Hl7NameWithDateAndLocation> transcriptionist; // OBR.35 - Transcriptionist
	@Nullable
	private Hl7TimeStamp scheduledDateTime; // OBR.36 - Scheduled Date/Time
	@Nullable
	private Integer numberOfSampleContainers; // OBR.37 - Number of Sample Containers
	@Nullable
	private List<Hl7CodedElement> transportLogisticsOfCollectedSample; // OBR.38 - Transport Logistics of Collected Sample
	@Nullable
	private List<Hl7CodedElement> collectorsComment; // OBR.39 - Collector's Comment
	@Nullable
	private Hl7CodedElement transportArrangementResponsibility; // OBR.40 - Transport Arrangement Responsibility
	@Nullable
	private String transportArranged; // OBR.41 - Transport Arranged
	@Nullable
	String escortRequired; // OBR.42 - Escort Required
	@Nullable
	private List<Hl7CodedElement> plannedPatientTransportComment; // OBR.43 - Planned Patient Transport Comment
	@Nullable
	private Hl7CodedElement procedureCode; // OBR.44 - Procedure Code
	@Nullable
	private List<Hl7CodedElement> procedureCodeModifier; // OBR.45 - Procedure Code Modifier
	@Nullable
	private List<Hl7CodedElement> placerSupplementalServiceInformation; // OBR.46 - Placer Supplemental Service Information
	@Nullable
	private List<Hl7CodedElement> fillerSupplementalServiceInformation; //  OBR.47 - Filler Supplemental Service Information
	@Nullable
	private Hl7CodedWithExceptions medicallyNecessaryDuplicateProcedureReason; // OBR.48 - Medically Necessary Duplicate Procedure Reason
	@Nullable
	private String resultHandling; // OBR.49 - Result Handling
	@Nullable
	private Hl7CodedWithExceptions parentUniversalServiceIdentifier; // OBR.50 - Parent Universal Service Identifier

	@Nonnull
	public static Boolean isPresent(@Nullable OBR obr) {
		if (obr == null)
			return false;

		return Hl7CodedElement.isPresent(obr.getUniversalServiceIdentifier());
	}

	public Hl7ObservationRequestSegment() {
		// Nothing to do
	}

	public Hl7ObservationRequestSegment(@Nullable OBR obr) {
		if (obr != null) {
			String setIdAsString = trimToNull(obr.getSetIDOBR().getValue());
			if (setIdAsString != null)
				this.setId = Integer.parseInt(setIdAsString, 10);

			if (Hl7EntityIdentifier.isPresent(obr.getPlacerOrderNumber()))
				this.placerOrderNumber = new Hl7EntityIdentifier(obr.getPlacerOrderNumber());

			if (Hl7EntityIdentifier.isPresent(obr.getFillerOrderNumber()))
				this.fillerOrderNumber = new Hl7EntityIdentifier(obr.getFillerOrderNumber());

			if (Hl7CodedElement.isPresent(obr.getUniversalServiceIdentifier()))
				this.universalServiceIdentifier = new Hl7CodedElement(obr.getUniversalServiceIdentifier());

			this.priority = trimToNull(obr.getPriorityOBR().getValueOrEmpty());

			if (Hl7TimeStamp.isPresent(obr.getRequestedDateTime()))
				this.requestedDateTime = new Hl7TimeStamp(obr.getRequestedDateTime());

			if (Hl7TimeStamp.isPresent(obr.getObservationDateTime()))
				this.observationDateTime = new Hl7TimeStamp(obr.getObservationDateTime());

			if (Hl7TimeStamp.isPresent(obr.getObservationEndDateTime()))
				this.observationEndDateTime = new Hl7TimeStamp(obr.getObservationEndDateTime());

			if (Hl7CompositeQuantityWithUnits.isPresent(obr.getCollectionVolume()))
				this.collectionVolume = new Hl7CompositeQuantityWithUnits(obr.getCollectionVolume());

			if (obr.getCollectorIdentifier() != null && obr.getCollectorIdentifier().length > 0)
				this.collectorIdentifier = Arrays.stream(obr.getCollectorIdentifier())
						.map(xcn -> Hl7ExtendedCompositeIdNumberAndNameForPersons.isPresent(xcn) ? new Hl7ExtendedCompositeIdNumberAndNameForPersons(xcn) : null)
						.filter(collectorIdentifier -> collectorIdentifier != null)
						.collect(Collectors.toList());

			this.specimenActionCode = trimToNull(obr.getSpecimenActionCode().getValueOrEmpty());

			if (Hl7CodedElement.isPresent(obr.getDangerCode()))
				this.dangerCode = new Hl7CodedElement(obr.getDangerCode());

			this.relevantClinicalInformation = trimToNull(obr.getRelevantClinicalInformation().getValueOrEmpty());

			if (Hl7TimeStamp.isPresent(obr.getSpecimenReceivedDateTime()))
				this.specimenReceivedDateTime = new Hl7TimeStamp(obr.getSpecimenReceivedDateTime());

			if (Hl7SpecimenSource.isPresent(obr.getSpecimenSource()))
				this.specimenSource = new Hl7SpecimenSource(obr.getSpecimenSource());

			if (obr.getOrderingProvider() != null && obr.getOrderingProvider().length > 0)
				this.orderingProvider = Arrays.stream(obr.getOrderingProvider())
						.map(xcn -> Hl7ExtendedCompositeIdNumberAndNameForPersons.isPresent(xcn) ? new Hl7ExtendedCompositeIdNumberAndNameForPersons(xcn) : null)
						.filter(orderingProvider -> orderingProvider != null)
						.collect(Collectors.toList());

			if (obr.getOrderCallbackPhoneNumber() != null && obr.getOrderCallbackPhoneNumber().length > 0)
				this.orderCallbackPhoneNumber = Arrays.stream(obr.getOrderCallbackPhoneNumber())
						.map(xtn -> Hl7ExtendedTelecommunicationNumber.isPresent(xtn) ? new Hl7ExtendedTelecommunicationNumber(xtn) : null)
						.filter(orderCallbackPhoneNumber -> orderCallbackPhoneNumber != null)
						.collect(Collectors.toList());

			this.placerField1 = trimToNull(obr.getPlacerField1().getValueOrEmpty());
			this.placerField2 = trimToNull(obr.getPlacerField2().getValueOrEmpty());
			this.fillerField1 = trimToNull(obr.getFillerField1().getValueOrEmpty());
			this.fillerField2 = trimToNull(obr.getFillerField2().getValueOrEmpty());

			if (Hl7TimeStamp.isPresent(obr.getResultsRptStatusChngDateTime()))
				this.resultsRptStatusChngDateTime = new Hl7TimeStamp(obr.getResultsRptStatusChngDateTime());

			if (Hl7MoneyAndCode.isPresent(obr.getChargeToPractice()))
				this.chargeToPractice = new Hl7MoneyAndCode(obr.getChargeToPractice());

			this.diagnosticServSectId = trimToNull(obr.getDiagnosticServSectID().getValueOrEmpty());
			this.resultStatus = trimToNull(obr.getResultStatus().getValueOrEmpty());

			if (Hl7ParentResultLink.isPresent(obr.getParentResult()))
				this.parentResult = new Hl7ParentResultLink(obr.getParentResult());

			if (obr.getQuantityTiming() != null && obr.getQuantityTiming().length > 0)
				this.quantityTiming = Arrays.stream(obr.getQuantityTiming())
						.map(tq -> Hl7TimingQuantity.isPresent(tq) ? new Hl7TimingQuantity(tq) : null)
						.filter(quantityTiming -> quantityTiming != null)
						.collect(Collectors.toList());

			if (obr.getResultCopiesTo() != null && obr.getResultCopiesTo().length > 0)
				this.resultCopiesTo = Arrays.stream(obr.getResultCopiesTo())
						.map(xcn -> Hl7ExtendedCompositeIdNumberAndNameForPersons.isPresent(xcn) ? new Hl7ExtendedCompositeIdNumberAndNameForPersons(xcn) : null)
						.filter(resultCopiesTo -> resultCopiesTo != null)
						.collect(Collectors.toList());

			if (Hl7EntityIdentifierPair.isPresent(obr.getObr29_Parent()))
				this.parent = new Hl7EntityIdentifierPair(obr.getObr29_Parent());

			this.transportationMode = trimToNull(obr.getTransportationMode().getValueOrEmpty());

			if (obr.getReasonForStudy() != null && obr.getReasonForStudy().length > 0)
				this.reasonForStudy = Arrays.stream(obr.getReasonForStudy())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(reasonForStudy -> reasonForStudy != null)
						.collect(Collectors.toList());

			if (Hl7NameWithDateAndLocation.isPresent(obr.getPrincipalResultInterpreter()))
				this.principalResultInterpreter = new Hl7NameWithDateAndLocation(obr.getPrincipalResultInterpreter());

			if (obr.getAssistantResultInterpreter() != null && obr.getAssistantResultInterpreter().length > 0)
				this.assistantResultInterpreter = Arrays.stream(obr.getAssistantResultInterpreter())
						.map(ndl -> Hl7NameWithDateAndLocation.isPresent(ndl) ? new Hl7NameWithDateAndLocation(ndl) : null)
						.filter(assistantResultInterpreter -> assistantResultInterpreter != null)
						.collect(Collectors.toList());

			if (obr.getTechnician() != null && obr.getTechnician().length > 0)
				this.technician = Arrays.stream(obr.getTechnician())
						.map(ndl -> Hl7NameWithDateAndLocation.isPresent(ndl) ? new Hl7NameWithDateAndLocation(ndl) : null)
						.filter(technician -> technician != null)
						.collect(Collectors.toList());

			if (obr.getTranscriptionist() != null && obr.getTranscriptionist().length > 0)
				this.transcriptionist = Arrays.stream(obr.getTranscriptionist())
						.map(ndl -> Hl7NameWithDateAndLocation.isPresent(ndl) ? new Hl7NameWithDateAndLocation(ndl) : null)
						.filter(transcriptionist -> transcriptionist != null)
						.collect(Collectors.toList());

			if (Hl7TimeStamp.isPresent(obr.getScheduledDateTime()))
				this.scheduledDateTime = new Hl7TimeStamp(obr.getScheduledDateTime());

			String numberOfSampleContainersAsString = trimToNull(obr.getNumberOfSampleContainers().getValue());
			if (numberOfSampleContainersAsString != null)
				this.numberOfSampleContainers = Integer.parseInt(numberOfSampleContainersAsString, 10);

			if (obr.getTransportLogisticsOfCollectedSample() != null && obr.getTransportLogisticsOfCollectedSample().length > 0)
				this.transportLogisticsOfCollectedSample = Arrays.stream(obr.getTransportLogisticsOfCollectedSample())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(transportLogisticsOfCollectedSample -> transportLogisticsOfCollectedSample != null)
						.collect(Collectors.toList());

			if (obr.getCollectorSComment() != null && obr.getCollectorSComment().length > 0)
				this.collectorsComment = Arrays.stream(obr.getCollectorSComment())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(collectorsComment -> collectorsComment != null)
						.collect(Collectors.toList());

			if (Hl7CodedElement.isPresent(obr.getTransportArrangementResponsibility()))
				this.transportArrangementResponsibility = new Hl7CodedElement(obr.getTransportArrangementResponsibility());

			this.transportArranged = trimToNull(obr.getTransportArranged().getValueOrEmpty());
			this.escortRequired = trimToNull(obr.getEscortRequired().getValueOrEmpty());

			if (obr.getPlannedPatientTransportComment() != null && obr.getPlannedPatientTransportComment().length > 0)
				this.plannedPatientTransportComment = Arrays.stream(obr.getPlannedPatientTransportComment())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(plannedPatientTransportComment -> plannedPatientTransportComment != null)
						.collect(Collectors.toList());

			if (Hl7CodedElement.isPresent(obr.getProcedureCode()))
				this.procedureCode = new Hl7CodedElement(obr.getProcedureCode());

			if (obr.getProcedureCodeModifier() != null && obr.getProcedureCodeModifier().length > 0)
				this.procedureCodeModifier = Arrays.stream(obr.getProcedureCodeModifier())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(procedureCodeModifier -> procedureCodeModifier != null)
						.collect(Collectors.toList());

			if (obr.getPlacerSupplementalServiceInformation() != null && obr.getPlacerSupplementalServiceInformation().length > 0)
				this.placerSupplementalServiceInformation = Arrays.stream(obr.getPlacerSupplementalServiceInformation())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(placerSupplementalServiceInformation -> placerSupplementalServiceInformation != null)
						.collect(Collectors.toList());

			if (obr.getFillerSupplementalServiceInformation() != null && obr.getFillerSupplementalServiceInformation().length > 0)
				this.fillerSupplementalServiceInformation = Arrays.stream(obr.getFillerSupplementalServiceInformation())
						.map(ce -> Hl7CodedElement.isPresent(ce) ? new Hl7CodedElement(ce) : null)
						.filter(fillerSupplementalServiceInformation -> fillerSupplementalServiceInformation != null)
						.collect(Collectors.toList());

			if (Hl7CodedWithExceptions.isPresent(obr.getMedicallyNecessaryDuplicateProcedureReason()))
				this.medicallyNecessaryDuplicateProcedureReason = new Hl7CodedWithExceptions(obr.getMedicallyNecessaryDuplicateProcedureReason());

			this.resultHandling = trimToNull(obr.getResultHandling().getValueOrEmpty());

			if (Hl7CodedWithExceptions.isPresent(obr.getParentUniversalServiceIdentifier()))
				this.parentUniversalServiceIdentifier = new Hl7CodedWithExceptions(obr.getParentUniversalServiceIdentifier());
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
	public Hl7EntityIdentifier getPlacerOrderNumber() {
		return this.placerOrderNumber;
	}

	public void setPlacerOrderNumber(@Nullable Hl7EntityIdentifier placerOrderNumber) {
		this.placerOrderNumber = placerOrderNumber;
	}

	@Nullable
	public Hl7EntityIdentifier getFillerOrderNumber() {
		return this.fillerOrderNumber;
	}

	public void setFillerOrderNumber(@Nullable Hl7EntityIdentifier fillerOrderNumber) {
		this.fillerOrderNumber = fillerOrderNumber;
	}

	@Nullable
	public Hl7CodedElement getUniversalServiceIdentifier() {
		return this.universalServiceIdentifier;
	}

	public void setUniversalServiceIdentifier(@Nullable Hl7CodedElement universalServiceIdentifier) {
		this.universalServiceIdentifier = universalServiceIdentifier;
	}

	@Nullable
	public String getPriority() {
		return this.priority;
	}

	public void setPriority(@Nullable String priority) {
		this.priority = priority;
	}

	@Nullable
	public Hl7TimeStamp getRequestedDateTime() {
		return this.requestedDateTime;
	}

	public void setRequestedDateTime(@Nullable Hl7TimeStamp requestedDateTime) {
		this.requestedDateTime = requestedDateTime;
	}

	@Nullable
	public Hl7TimeStamp getObservationDateTime() {
		return this.observationDateTime;
	}

	public void setObservationDateTime(@Nullable Hl7TimeStamp observationDateTime) {
		this.observationDateTime = observationDateTime;
	}

	@Nullable
	public Hl7TimeStamp getObservationEndDateTime() {
		return this.observationEndDateTime;
	}

	public void setObservationEndDateTime(@Nullable Hl7TimeStamp observationEndDateTime) {
		this.observationEndDateTime = observationEndDateTime;
	}

	@Nullable
	public Hl7CompositeQuantityWithUnits getCollectionVolume() {
		return this.collectionVolume;
	}

	public void setCollectionVolume(@Nullable Hl7CompositeQuantityWithUnits collectionVolume) {
		this.collectionVolume = collectionVolume;
	}

	@Nullable
	public List<Hl7ExtendedCompositeIdNumberAndNameForPersons> getCollectorIdentifier() {
		return this.collectorIdentifier;
	}

	public void setCollectorIdentifier(@Nullable List<Hl7ExtendedCompositeIdNumberAndNameForPersons> collectorIdentifier) {
		this.collectorIdentifier = collectorIdentifier;
	}

	@Nullable
	public String getSpecimenActionCode() {
		return this.specimenActionCode;
	}

	public void setSpecimenActionCode(@Nullable String specimenActionCode) {
		this.specimenActionCode = specimenActionCode;
	}

	@Nullable
	public Hl7CodedElement getDangerCode() {
		return this.dangerCode;
	}

	public void setDangerCode(@Nullable Hl7CodedElement dangerCode) {
		this.dangerCode = dangerCode;
	}

	@Nullable
	public String getRelevantClinicalInformation() {
		return this.relevantClinicalInformation;
	}

	public void setRelevantClinicalInformation(@Nullable String relevantClinicalInformation) {
		this.relevantClinicalInformation = relevantClinicalInformation;
	}

	@Nullable
	public Hl7TimeStamp getSpecimenReceivedDateTime() {
		return this.specimenReceivedDateTime;
	}

	public void setSpecimenReceivedDateTime(@Nullable Hl7TimeStamp specimenReceivedDateTime) {
		this.specimenReceivedDateTime = specimenReceivedDateTime;
	}

	@Nullable
	public Hl7SpecimenSource getSpecimenSource() {
		return this.specimenSource;
	}

	public void setSpecimenSource(@Nullable Hl7SpecimenSource specimenSource) {
		this.specimenSource = specimenSource;
	}

	@Nullable
	public List<Hl7ExtendedCompositeIdNumberAndNameForPersons> getOrderingProvider() {
		return this.orderingProvider;
	}

	public void setOrderingProvider(@Nullable List<Hl7ExtendedCompositeIdNumberAndNameForPersons> orderingProvider) {
		this.orderingProvider = orderingProvider;
	}

	@Nullable
	public List<Hl7ExtendedTelecommunicationNumber> getOrderCallbackPhoneNumber() {
		return this.orderCallbackPhoneNumber;
	}

	public void setOrderCallbackPhoneNumber(@Nullable List<Hl7ExtendedTelecommunicationNumber> orderCallbackPhoneNumber) {
		this.orderCallbackPhoneNumber = orderCallbackPhoneNumber;
	}

	@Nullable
	public String getPlacerField1() {
		return this.placerField1;
	}

	public void setPlacerField1(@Nullable String placerField1) {
		this.placerField1 = placerField1;
	}

	@Nullable
	public String getPlacerField2() {
		return this.placerField2;
	}

	public void setPlacerField2(@Nullable String placerField2) {
		this.placerField2 = placerField2;
	}

	@Nullable
	public String getFillerField1() {
		return this.fillerField1;
	}

	public void setFillerField1(@Nullable String fillerField1) {
		this.fillerField1 = fillerField1;
	}

	@Nullable
	public String getFillerField2() {
		return this.fillerField2;
	}

	public void setFillerField2(@Nullable String fillerField2) {
		this.fillerField2 = fillerField2;
	}

	@Nullable
	public Hl7TimeStamp getResultsRptStatusChngDateTime() {
		return this.resultsRptStatusChngDateTime;
	}

	public void setResultsRptStatusChngDateTime(@Nullable Hl7TimeStamp resultsRptStatusChngDateTime) {
		this.resultsRptStatusChngDateTime = resultsRptStatusChngDateTime;
	}

	@Nullable
	public Hl7MoneyAndCode getChargeToPractice() {
		return this.chargeToPractice;
	}

	public void setChargeToPractice(@Nullable Hl7MoneyAndCode chargeToPractice) {
		this.chargeToPractice = chargeToPractice;
	}

	@Nullable
	public String getDiagnosticServSectId() {
		return this.diagnosticServSectId;
	}

	public void setDiagnosticServSectId(@Nullable String diagnosticServSectId) {
		this.diagnosticServSectId = diagnosticServSectId;
	}

	@Nullable
	public String getResultStatus() {
		return this.resultStatus;
	}

	public void setResultStatus(@Nullable String resultStatus) {
		this.resultStatus = resultStatus;
	}

	@Nullable
	public Hl7ParentResultLink getParentResult() {
		return this.parentResult;
	}

	public void setParentResult(@Nullable Hl7ParentResultLink parentResult) {
		this.parentResult = parentResult;
	}

	@Nullable
	public List<Hl7TimingQuantity> getQuantityTiming() {
		return this.quantityTiming;
	}

	public void setQuantityTiming(@Nullable List<Hl7TimingQuantity> quantityTiming) {
		this.quantityTiming = quantityTiming;
	}

	@Nullable
	public List<Hl7ExtendedCompositeIdNumberAndNameForPersons> getResultCopiesTo() {
		return this.resultCopiesTo;
	}

	public void setResultCopiesTo(@Nullable List<Hl7ExtendedCompositeIdNumberAndNameForPersons> resultCopiesTo) {
		this.resultCopiesTo = resultCopiesTo;
	}

	@Nullable
	public Hl7EntityIdentifierPair getParent() {
		return this.parent;
	}

	public void setParent(@Nullable Hl7EntityIdentifierPair parent) {
		this.parent = parent;
	}

	@Nullable
	public String getTransportationMode() {
		return this.transportationMode;
	}

	public void setTransportationMode(@Nullable String transportationMode) {
		this.transportationMode = transportationMode;
	}

	@Nullable
	public List<Hl7CodedElement> getReasonForStudy() {
		return this.reasonForStudy;
	}

	public void setReasonForStudy(@Nullable List<Hl7CodedElement> reasonForStudy) {
		this.reasonForStudy = reasonForStudy;
	}

	@Nullable
	public Hl7NameWithDateAndLocation getPrincipalResultInterpreter() {
		return this.principalResultInterpreter;
	}

	public void setPrincipalResultInterpreter(@Nullable Hl7NameWithDateAndLocation principalResultInterpreter) {
		this.principalResultInterpreter = principalResultInterpreter;
	}

	@Nullable
	public List<Hl7NameWithDateAndLocation> getAssistantResultInterpreter() {
		return this.assistantResultInterpreter;
	}

	public void setAssistantResultInterpreter(@Nullable List<Hl7NameWithDateAndLocation> assistantResultInterpreter) {
		this.assistantResultInterpreter = assistantResultInterpreter;
	}

	@Nullable
	public List<Hl7NameWithDateAndLocation> getTechnician() {
		return this.technician;
	}

	public void setTechnician(@Nullable List<Hl7NameWithDateAndLocation> technician) {
		this.technician = technician;
	}

	@Nullable
	public List<Hl7NameWithDateAndLocation> getTranscriptionist() {
		return this.transcriptionist;
	}

	public void setTranscriptionist(@Nullable List<Hl7NameWithDateAndLocation> transcriptionist) {
		this.transcriptionist = transcriptionist;
	}

	@Nullable
	public Hl7TimeStamp getScheduledDateTime() {
		return this.scheduledDateTime;
	}

	public void setScheduledDateTime(@Nullable Hl7TimeStamp scheduledDateTime) {
		this.scheduledDateTime = scheduledDateTime;
	}

	@Nullable
	public Integer getNumberOfSampleContainers() {
		return this.numberOfSampleContainers;
	}

	public void setNumberOfSampleContainers(@Nullable Integer numberOfSampleContainers) {
		this.numberOfSampleContainers = numberOfSampleContainers;
	}

	@Nullable
	public List<Hl7CodedElement> getTransportLogisticsOfCollectedSample() {
		return this.transportLogisticsOfCollectedSample;
	}

	public void setTransportLogisticsOfCollectedSample(@Nullable List<Hl7CodedElement> transportLogisticsOfCollectedSample) {
		this.transportLogisticsOfCollectedSample = transportLogisticsOfCollectedSample;
	}

	@Nullable
	public List<Hl7CodedElement> getCollectorsComment() {
		return this.collectorsComment;
	}

	public void setCollectorsComment(@Nullable List<Hl7CodedElement> collectorsComment) {
		this.collectorsComment = collectorsComment;
	}

	@Nullable
	public Hl7CodedElement getTransportArrangementResponsibility() {
		return this.transportArrangementResponsibility;
	}

	public void setTransportArrangementResponsibility(@Nullable Hl7CodedElement transportArrangementResponsibility) {
		this.transportArrangementResponsibility = transportArrangementResponsibility;
	}

	@Nullable
	public String getTransportArranged() {
		return this.transportArranged;
	}

	public void setTransportArranged(@Nullable String transportArranged) {
		this.transportArranged = transportArranged;
	}

	@Nullable
	public String getEscortRequired() {
		return this.escortRequired;
	}

	public void setEscortRequired(@Nullable String escortRequired) {
		this.escortRequired = escortRequired;
	}

	@Nullable
	public List<Hl7CodedElement> getPlannedPatientTransportComment() {
		return this.plannedPatientTransportComment;
	}

	public void setPlannedPatientTransportComment(@Nullable List<Hl7CodedElement> plannedPatientTransportComment) {
		this.plannedPatientTransportComment = plannedPatientTransportComment;
	}

	@Nullable
	public Hl7CodedElement getProcedureCode() {
		return this.procedureCode;
	}

	public void setProcedureCode(@Nullable Hl7CodedElement procedureCode) {
		this.procedureCode = procedureCode;
	}

	@Nullable
	public List<Hl7CodedElement> getProcedureCodeModifier() {
		return this.procedureCodeModifier;
	}

	public void setProcedureCodeModifier(@Nullable List<Hl7CodedElement> procedureCodeModifier) {
		this.procedureCodeModifier = procedureCodeModifier;
	}

	@Nullable
	public List<Hl7CodedElement> getPlacerSupplementalServiceInformation() {
		return this.placerSupplementalServiceInformation;
	}

	public void setPlacerSupplementalServiceInformation(@Nullable List<Hl7CodedElement> placerSupplementalServiceInformation) {
		this.placerSupplementalServiceInformation = placerSupplementalServiceInformation;
	}

	@Nullable
	public List<Hl7CodedElement> getFillerSupplementalServiceInformation() {
		return this.fillerSupplementalServiceInformation;
	}

	public void setFillerSupplementalServiceInformation(@Nullable List<Hl7CodedElement> fillerSupplementalServiceInformation) {
		this.fillerSupplementalServiceInformation = fillerSupplementalServiceInformation;
	}

	@Nullable
	public Hl7CodedWithExceptions getMedicallyNecessaryDuplicateProcedureReason() {
		return this.medicallyNecessaryDuplicateProcedureReason;
	}

	public void setMedicallyNecessaryDuplicateProcedureReason(@Nullable Hl7CodedWithExceptions medicallyNecessaryDuplicateProcedureReason) {
		this.medicallyNecessaryDuplicateProcedureReason = medicallyNecessaryDuplicateProcedureReason;
	}

	@Nullable
	public String getResultHandling() {
		return this.resultHandling;
	}

	public void setResultHandling(@Nullable String resultHandling) {
		this.resultHandling = resultHandling;
	}

	@Nullable
	public Hl7CodedWithExceptions getParentUniversalServiceIdentifier() {
		return this.parentUniversalServiceIdentifier;
	}

	public void setParentUniversalServiceIdentifier(@Nullable Hl7CodedWithExceptions parentUniversalServiceIdentifier) {
		this.parentUniversalServiceIdentifier = parentUniversalServiceIdentifier;
	}
}