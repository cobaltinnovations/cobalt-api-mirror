/* Copyright 2021 The University of Pennsylvania and Penn Medicine */
package com.cobaltplatform.api.model.api.request;

import com.cobaltplatform.api.model.db.CareEncounterScheduledMessageType.CareEncounterScheduledMessageTypeId;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public class PreviewCareEncounterScheduledMessageRequest {
	@Nullable private CareEncounterScheduledMessageTypeId careEncounterScheduledMessageTypeId;
	@Nullable private String customEmailText;
	@Nullable public CareEncounterScheduledMessageTypeId getCareEncounterScheduledMessageTypeId() { return careEncounterScheduledMessageTypeId; }
	public void setCareEncounterScheduledMessageTypeId(@Nullable CareEncounterScheduledMessageTypeId v) { careEncounterScheduledMessageTypeId = v; }
	@Nullable public String getCustomEmailText() { return customEmailText; }
	public void setCustomEmailText(@Nullable String v) { customEmailText = v; }
}
