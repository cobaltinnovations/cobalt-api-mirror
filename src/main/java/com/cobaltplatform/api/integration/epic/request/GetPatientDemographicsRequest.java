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

package com.cobaltplatform.api.integration.epic.request;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author Transmogrify, LLC.
 */
@NotThreadSafe
public class GetPatientDemographicsRequest {
	@Nullable
	private String PatientID;  // e.g. "8330009978"
	@Nullable
	private String PatientIDType;  // e.g. "UID"
	@Nullable
	private String UserID;
	@Nullable
	private String UserIDType;

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.DEFAULT_STYLE);
	}

	@Nullable
	public String getPatientID() {
		return PatientID;
	}

	public void setPatientID(@Nullable String patientID) {
		PatientID = patientID;
	}

	@Nullable
	public String getPatientIDType() {
		return PatientIDType;
	}

	public void setPatientIDType(@Nullable String patientIDType) {
		PatientIDType = patientIDType;
	}

	@Nullable
	public String getUserID() {
		return UserID;
	}

	public void setUserID(@Nullable String userID) {
		UserID = userID;
	}

	@Nullable
	public String getUserIDType() {
		return UserIDType;
	}

	public void setUserIDType(@Nullable String userIDType) {
		UserIDType = userIDType;
	}
}
