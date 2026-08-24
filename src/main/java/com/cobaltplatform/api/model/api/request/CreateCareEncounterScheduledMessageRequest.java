/* Copyright 2021 The University of Pennsylvania and Penn Medicine */
package com.cobaltplatform.api.model.api.request;

import com.cobaltplatform.api.model.db.CareEncounterScheduledMessageType.CareEncounterScheduledMessageTypeId;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.time.LocalDate;
import java.time.LocalTime;

@NotThreadSafe
public class CreateCareEncounterScheduledMessageRequest {
	@Nullable private CareEncounterScheduledMessageTypeId careEncounterScheduledMessageTypeId;
	@Nullable private LocalDate scheduledAtDate;
	@Nullable private LocalTime scheduledAtTime;
	@Nullable private String customEmailText;
	@Nullable public CareEncounterScheduledMessageTypeId getCareEncounterScheduledMessageTypeId() { return careEncounterScheduledMessageTypeId; }
	public void setCareEncounterScheduledMessageTypeId(@Nullable CareEncounterScheduledMessageTypeId v) { careEncounterScheduledMessageTypeId = v; }
	@Nullable public LocalDate getScheduledAtDate() { return scheduledAtDate; }
	public void setScheduledAtDate(@Nullable LocalDate v) { scheduledAtDate = v; }
	@Nullable public LocalTime getScheduledAtTime() { return scheduledAtTime; }
	public void setScheduledAtTime(@Nullable LocalTime v) { scheduledAtTime = v; }
	@Nullable public String getCustomEmailText() { return customEmailText; }
	public void setCustomEmailText(@Nullable String v) { customEmailText = v; }
}
