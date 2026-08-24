/*
 * Copyright 2021 The University of Pennsylvania and Penn Medicine
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.cobaltplatform.api.model.db;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public class CareEncounterScheduledMessageType {
	public enum CareEncounterScheduledMessageTypeId { FOLLOW_UP }

	@Nullable private CareEncounterScheduledMessageTypeId careEncounterScheduledMessageTypeId;
	@Nullable private String description;
	@Nullable private Integer displayOrder;

	@Nullable public CareEncounterScheduledMessageTypeId getCareEncounterScheduledMessageTypeId() { return this.careEncounterScheduledMessageTypeId; }
	public void setCareEncounterScheduledMessageTypeId(@Nullable CareEncounterScheduledMessageTypeId value) { this.careEncounterScheduledMessageTypeId = value; }
	@Nullable public String getDescription() { return this.description; }
	public void setDescription(@Nullable String description) { this.description = description; }
	@Nullable public Integer getDisplayOrder() { return this.displayOrder; }
	public void setDisplayOrder(@Nullable Integer displayOrder) { this.displayOrder = displayOrder; }
}
