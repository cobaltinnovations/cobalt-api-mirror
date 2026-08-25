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

import com.cobaltplatform.api.UnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.annotation.concurrent.ThreadSafe;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
@Category(UnitTest.class)
public class ReportingServiceTests {
	@Test
	public void neutralizesSpreadsheetFormulaPrefixes() {
		String[] unsafeValues = {
				"=1+1",
				"+1+1",
				"-1+1",
				"@SUM(1,1)",
				"\t=1+1",
				"\r=1+1",
				"\n=1+1"
		};

		for (String unsafeValue : unsafeValues)
			Assert.assertEquals("'" + unsafeValue, ReportingService.neutralizeSpreadsheetFormula(unsafeValue));
	}

	@Test
	public void preservesOrdinarySpreadsheetValues() {
		String[] safeValues = {
				null,
				"",
				"Helpful course",
				"  =not a formula",
				"'already text"
		};

		for (String safeValue : safeValues)
			Assert.assertEquals(safeValue, ReportingService.neutralizeSpreadsheetFormula(safeValue));
	}
}
