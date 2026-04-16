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

import com.cobaltplatform.api.util.JsonMapper;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Transmogrify, LLC.
 */
public class EpicErrorMessageExtractorTests {
	@Test
	public void testExtractDetailedEpicMessage() {
		String epicExceptionMessage = """
				Bad HTTP response 400 for EPIC endpoint POST https://example.org/api/epic/2011/Clinical/Patient/ADDFLOWSHEETVALUE/FlowsheetValue with query params [none] and request body [none]. Response body was
				{"Message":"An error occurred while executing the command: CONTACT_IS_INVALID details: The contact found with the patient ID and DAT was invalid. It may be a closed encounter..","ExceptionMessage":"An error occurred while executing the command: CONTACT_IS_INVALID details: The contact found with the patient ID and DAT was invalid. It may be a closed encounter..","ExceptionType":"System.Web.HttpException","StackTrace":null}
				""".trim();

		Optional<EpicErrorMessageExtractor.EpicErrorMessageDetails> details = new EpicErrorMessageExtractor(new JsonMapper()).extract(epicExceptionMessage);

		assertTrue(details.isPresent());
		assertEquals("An error occurred while executing the command: CONTACT_IS_INVALID details: The contact found with the patient ID and DAT was invalid. It may be a closed encounter..", details.get().getMessage());
		assertEquals("An error occurred while executing the command: CONTACT_IS_INVALID details: The contact found with the patient ID and DAT was invalid. It may be a closed encounter..", details.get().getExceptionMessage());
		assertEquals("An error occurred while executing the command: CONTACT_IS_INVALID details: The contact found with the patient ID and DAT was invalid. It may be a closed encounter..", details.get().preferredMessage().orElse(null));
	}

	@Test
	public void testPreferExceptionMessageWhenTopLevelMessageIsGeneric() {
		String epicExceptionMessage = """
				Bad HTTP response 400 for EPIC endpoint POST https://example.org/api/epic/2012/EMPI/PatientCreate with query params [none] and request body [none]. Response body was
				{"Message":"An error has occurred.","ExceptionMessage":"An error occurred while executing the command: NO-DATE-OF-BIRTH details: Date of birth is required.","ExceptionType":"Epic.ServiceModel.Internal.ServiceCommandException","StackTrace":null}
				""".trim();

		Optional<EpicErrorMessageExtractor.EpicErrorMessageDetails> details = new EpicErrorMessageExtractor(new JsonMapper()).extract(epicExceptionMessage);

		assertTrue(details.isPresent());
		assertEquals("An error has occurred.", details.get().getMessage());
		assertEquals("An error occurred while executing the command: NO-DATE-OF-BIRTH details: Date of birth is required.", details.get().getExceptionMessage());
		assertEquals("An error occurred while executing the command: NO-DATE-OF-BIRTH details: Date of birth is required.", details.get().preferredMessage().orElse(null));
	}
}
