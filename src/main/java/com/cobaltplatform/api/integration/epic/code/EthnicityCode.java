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

package com.cobaltplatform.api.integration.epic.code;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;


/**
 * @author Transmogrify, LLC.
 */
public enum EthnicityCode {
	// https://build.fhir.org/ig/HL7/UTG/ValueSet-v3-Ethnicity.html
	// https://terminology.hl7.org/5.0.0/CodeSystem-v3-Ethnicity.html

	NOT_HISPANIC_OR_LATINO("Not Hispanic or Latino", "2186-5"),
	HISPANIC_OR_LATINO("Hispanic or Latino", "2135-2");

	@Nonnull
	public static final String EXTENSION_URL;
	@Nonnull
	public static final String DSTU2_EXTENSION_URL;
	@Nonnull
	private static final Map<String, EthnicityCode> ETHNICITY_CODES_BY_FHIR_VALUE;
	@Nonnull
	private static final Map<String, EthnicityCode> ETHNICITY_CODES_BY_DSTU2_VALUE;

	@Nonnull
	private final String fhirValue;
	@Nonnull
	private final String dstu2Value;

	static {
		EXTENSION_URL = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity";
		DSTU2_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/us-core-ethnicity";

		Map<String, EthnicityCode> ethnicityCodesByFhirValue = new HashMap<>();
		Map<String, EthnicityCode> ethnicityCodesByDstu2Value = new HashMap<>();

		for (EthnicityCode ethnicityCode : EthnicityCode.values()) {
			ethnicityCodesByFhirValue.put(ethnicityCode.getFhirValue(), ethnicityCode);
			ethnicityCodesByDstu2Value.put(ethnicityCode.getDstu2Value(), ethnicityCode);
		}

		ETHNICITY_CODES_BY_FHIR_VALUE = Collections.unmodifiableMap(ethnicityCodesByFhirValue);
		ETHNICITY_CODES_BY_DSTU2_VALUE = Collections.unmodifiableMap(ethnicityCodesByDstu2Value);
	}

	private EthnicityCode(@Nonnull String fhirValue,
												@Nonnull String dstu2Value) {
		requireNonNull(fhirValue);
		requireNonNull(dstu2Value);

		this.fhirValue = fhirValue;
		this.dstu2Value = dstu2Value;
	}

	@Nonnull
	public String getFhirValue() {
		return this.fhirValue;
	}

	@Nonnull
	public static Optional<EthnicityCode> fromFhirValue(@Nullable String fhirValue) {
		fhirValue = trimToNull(fhirValue);
		return Optional.ofNullable(ETHNICITY_CODES_BY_FHIR_VALUE.get(fhirValue));
	}

	@Nonnull
	public String getDstu2Value() {
		return this.dstu2Value;
	}

	@Nonnull
	public static Optional<EthnicityCode> fromDstu2Value(@Nullable String dstu2Value) {
		dstu2Value = trimToNull(dstu2Value);
		return Optional.ofNullable(ETHNICITY_CODES_BY_DSTU2_VALUE.get(dstu2Value));
	}
}
