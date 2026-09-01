/* Copyright 2021 The University of Pennsylvania and Penn Medicine */
package com.cobaltplatform.api.model.service;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import static java.util.Objects.requireNonNull;

@Immutable
public class RenderedEmailMessage {
	@Nonnull private final String emailSubject;
	@Nonnull private final String emailBody;
	public RenderedEmailMessage(@Nonnull String emailSubject, @Nonnull String emailBody) {
		this.emailSubject = requireNonNull(emailSubject);
		this.emailBody = requireNonNull(emailBody);
	}
	@Nonnull public String getEmailSubject() { return emailSubject; }
	@Nonnull public String getEmailBody() { return emailBody; }
}
