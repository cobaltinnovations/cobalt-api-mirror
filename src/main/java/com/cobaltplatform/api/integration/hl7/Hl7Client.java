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

package com.cobaltplatform.api.integration.hl7;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v251.group.ORM_O01_PATIENT;
import ca.uhn.hl7v2.model.v251.message.ORM_O01;
import ca.uhn.hl7v2.model.v251.segment.MSH;
import ca.uhn.hl7v2.model.v251.segment.ORC;
import ca.uhn.hl7v2.parser.Parser;
import com.cobaltplatform.api.integration.hl7.model.event.Hl7GeneralOrderTriggerEvent;
import com.cobaltplatform.api.integration.hl7.model.section.Hl7OrderSection;
import com.cobaltplatform.api.integration.hl7.model.section.Hl7PatientSection;
import com.cobaltplatform.api.integration.hl7.model.segment.Hl7CommonOrderSegment;
import com.cobaltplatform.api.integration.hl7.model.segment.Hl7MessageHeaderSegment;
import com.cobaltplatform.api.integration.hl7.model.segment.Hl7NotesAndCommentsSegment;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7CodedElement;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7CodedWithExceptions;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7CodedWithNoExceptions;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7EntityIdentifier;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7EntityIdentifierPair;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedAddress;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedCompositeIdNumberAndNameForPersons;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedCompositeNameAndIdentificationNumberForOrganizations;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ExtendedTelecommunicationNumber;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7HierarchicDesignator;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7MessageType;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7PersonLocation;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7ProcessingType;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7TimeStamp;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7TimingQuantity;
import com.cobaltplatform.api.integration.hl7.model.type.Hl7VersionId;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * See https://github.com/hapifhir/hapi-hl7v2 for details.
 *
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class Hl7Client {
	@Nonnull
	public String messageFromBytes(@Nonnull byte[] bytes) {
		requireNonNull(bytes);
		// Messages are in ASCII and generally contain only carriage returns (not CRLF). Here we force newlines
		return new String(bytes, StandardCharsets.US_ASCII).replace("\r", "\r\n").trim();
	}

	@Nonnull
	public Hl7GeneralOrderTriggerEvent parseGeneralOrder(@Nonnull byte[] generalOrderHl7) throws Hl7ParsingException {
		requireNonNull(generalOrderHl7);
		return parseGeneralOrder(messageFromBytes(generalOrderHl7));
	}

	@Nonnull
	public Hl7GeneralOrderTriggerEvent parseGeneralOrder(@Nonnull String generalOrderHl7AsString) throws Hl7ParsingException {
		requireNonNull(generalOrderHl7AsString);

		// TODO: determine if HapiContext/Parser instances are threadsafe, would be nice to share them across threads
		try (HapiContext hapiContext = new DefaultHapiContext()) {
			Parser parser = hapiContext.getGenericParser();
			Message hapiMessage;

			// Patient order messages must have CRLF endings, otherwise parsing will fail.  Ensure that here.
			generalOrderHl7AsString = generalOrderHl7AsString.trim().lines().collect(Collectors.joining("\r\n"));

			try {
				hapiMessage = parser.parse(generalOrderHl7AsString);
			} catch (Exception e) {
				throw new Hl7ParsingException(format("Unable to parse HL7 message:\n%s", generalOrderHl7AsString), e);
			}

			try {
				Hl7GeneralOrderTriggerEvent generalOrder = new Hl7GeneralOrderTriggerEvent();

				// See https://hl7-definition.caristix.com/v2/hl7v2.5.1/TriggerEvents/ORM_O01
				ORM_O01 ormMessage = (ORM_O01) hapiMessage;

				// See https://hl7-definition.caristix.com/v2/hl7v2.5.1/Segments/MSH
				MSH msh = ormMessage.getMSH();

				Hl7MessageHeaderSegment messageHeader = new Hl7MessageHeaderSegment();
				messageHeader.setFieldSeparator(trimToNull(msh.getFieldSeparator().getValueOrEmpty()));
				messageHeader.setEncodingCharacters(trimToNull(msh.getEncodingCharacters().getValueOrEmpty()));

				if (Hl7HierarchicDesignator.isPresent(msh.getSendingApplication()))
					messageHeader.setSendingApplication(new Hl7HierarchicDesignator(msh.getSendingApplication()));

				if (Hl7HierarchicDesignator.isPresent(msh.getSendingFacility()))
					messageHeader.setSendingFacility(new Hl7HierarchicDesignator(msh.getSendingFacility()));

				if (Hl7HierarchicDesignator.isPresent(msh.getReceivingApplication()))
					messageHeader.setReceivingApplication(new Hl7HierarchicDesignator(msh.getReceivingApplication()));

				if (Hl7HierarchicDesignator.isPresent(msh.getReceivingFacility()))
					messageHeader.setReceivingFacility(new Hl7HierarchicDesignator(msh.getReceivingFacility()));

				Date dateTimeOfMessage = (msh.getDateTimeOfMessage() != null && msh.getDateTimeOfMessage().getTime() != null) ?
						msh.getDateTimeOfMessage().getTime().getValueAsDate() : null;

				if (dateTimeOfMessage != null)
					messageHeader.setDateTimeOfMessage(dateTimeOfMessage.toInstant());

				messageHeader.setSecurity(trimToNull(msh.getSecurity().getValueOrEmpty()));

				if (Hl7MessageType.isPresent(msh.getMessageType()))
					messageHeader.setMessageType(new Hl7MessageType(msh.getMessageType()));

				messageHeader.setMessageControlId(trimToNull(msh.getMessageControlID().getValueOrEmpty()));

				if (Hl7ProcessingType.isPresent(msh.getProcessingID()))
					messageHeader.setProcessingId(new Hl7ProcessingType(msh.getProcessingID()));

				if (Hl7VersionId.isPresent(msh.getVersionID()))
					messageHeader.setVersionId(new Hl7VersionId(msh.getVersionID()));

				String sequenceNumberAsString = trimToNull(msh.getSequenceNumber().getValue());

				if (sequenceNumberAsString != null)
					messageHeader.setSequenceNumber(Double.parseDouble(sequenceNumberAsString));

				messageHeader.setContinuationPointer(trimToNull(msh.getContinuationPointer().getValue()));
				messageHeader.setAcceptAcknowledgementType(trimToNull(msh.getAcceptAcknowledgmentType().getValueOrEmpty()));
				messageHeader.setApplicationAcknowledgementType(trimToNull(msh.getApplicationAcknowledgmentType().getValueOrEmpty()));
				messageHeader.setCountryCode(trimToNull(msh.getCountryCode().getValueOrEmpty()));

				if (msh.getCharacterSet() != null && msh.getCharacterSet().length > 0)
					messageHeader.setCharacterSet(Arrays.stream(msh.getCharacterSet())
							.map(cs -> trimToNull(cs.getValueOrEmpty()))
							.filter(cs -> cs != null)
							.collect(Collectors.toList()));

				if (Hl7CodedElement.isPresent(msh.getPrincipalLanguageOfMessage()))
					messageHeader.setPrincipalLanguageOfMessage(new Hl7CodedElement(msh.getPrincipalLanguageOfMessage()));

				messageHeader.setAlternateCharacterSetHandlingScheme(trimToNull(msh.getAlternateCharacterSetHandlingScheme().getValueOrEmpty()));

				if (msh.getMessageProfileIdentifier() != null && msh.getMessageProfileIdentifier().length > 0)
					messageHeader.setMessageProfileIdentifier(Arrays.stream(msh.getMessageProfileIdentifier())
							.map(mpi -> Hl7EntityIdentifier.isPresent(mpi) ? new Hl7EntityIdentifier(mpi) : null)
							.filter(mpi -> mpi != null)
							.collect(Collectors.toList()));

				generalOrder.setMessageHeader(messageHeader);

				// See https://hl7-definition.caristix.com/v2/hl7v2.5.1/Segments/NTE

				if (ormMessage.getNTEAll() != null && ormMessage.getNTEAll().size() > 0)
					generalOrder.setNotesAndComments(ormMessage.getNTEAll().stream()
							.map(nte -> {
								Hl7NotesAndCommentsSegment notesAndComments = new Hl7NotesAndCommentsSegment();

								String setIdAsString = trimToNull(nte.getSetIDNTE().getValue());

								if (setIdAsString != null)
									notesAndComments.setSetId(Integer.parseInt(setIdAsString, 10));

								notesAndComments.setSourceOfComment(trimToNull(nte.getSourceOfComment().getValueOrEmpty()));

								if (nte.getComment() != null && nte.getComment().length > 0)
									notesAndComments.setComment(Arrays.stream(nte.getComment())
											.map(nteComment -> trimToNull(nteComment.getValueOrEmpty()))
											.filter(comment -> comment != null)
											.collect(Collectors.toList())
									);

								if (Hl7CodedElement.isPresent(nte.getCommentType()))
									notesAndComments.setCommentType(new Hl7CodedElement(nte.getCommentType()));

								return notesAndComments;
							})
							.collect(Collectors.toList())
					);

				// See https://hl7-definition.caristix.com/v2/hl7v2.5.1/TriggerEvents/ORM_O01
				if (ormMessage.getORDERAll() != null && ormMessage.getORDERAll().size() > 0) {
					generalOrder.setOrders(ormMessage.getORDERAll().stream()
							.map(ormOrder -> {
								Hl7OrderSection order = new Hl7OrderSection();
								ORC orc = ormOrder.getORC();

								// See https://hl7-definition.caristix.com/v2/hl7v2.5.1/Segments/ORC
								Hl7CommonOrderSegment commonOrder = new Hl7CommonOrderSegment();

								order.setCommonOrder(commonOrder);

								return order;
							})
							.collect(Collectors.toList()));
				}

				ORM_O01_PATIENT patient = ormMessage.getPATIENT();

				if (Hl7PatientSection.isPresent(patient))
					generalOrder.setPatient(new Hl7PatientSection(patient));
				
				return generalOrder;
			} catch (Exception e) {
				throw new Hl7ParsingException(format("Encountered an unexpected problem while processing HL7 message:\n%s", generalOrderHl7AsString), e);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
