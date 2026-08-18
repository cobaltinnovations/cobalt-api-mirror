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

package com.cobaltplatform.api.messaging.email;

import com.cobaltplatform.api.UnitTest;
import com.cobaltplatform.api.util.HandlebarsTemplater;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
@Category(UnitTest.class)
public class AccountEmailTemplateTests {
	@Nonnull
	private static final String PLATFORM_NAME = "Behavior Bridge";
	@Nonnull
	private static final String RECIPIENT_EMAIL_ADDRESS = "patient@example.com";
	@Nonnull
	private static final String SUPPORT_EMAIL_ADDRESS = "support@example.com";
	@Nonnull
	private static final String PRIVACY_POLICY_URL = "https://example.com/privacy";
	@Nonnull
	private static final String VERIFICATION_URL = "https://example.com/verify?token=verification-token";
	@Nonnull
	private static final String PASSWORD_RESET_LINK = "https://example.com/reset?token=password-reset-token";

	@Test
	public void rendersV2AccountVerificationEmail() {
		Map<String, Object> context = baseContext();
		context.put("verificationUrl", VERIFICATION_URL);

		String subject = render(EmailMessageTemplate.V2_ACCOUNT_VERIFICATION, "subject", context).trim();
		String body = render(EmailMessageTemplate.V2_ACCOUNT_VERIFICATION, "body", context);

		Assert.assertEquals("Behavior Bridge: Confirm your email address", subject);
		Assert.assertTrue(body.contains("Please confirm your email address"));
		Assert.assertTrue(body.contains("Thank you for joining Behavior Bridge!"));
		Assert.assertTrue(body.contains("href=\"" + VERIFICATION_URL + "\""));
		Assert.assertTrue(body.contains("Confirm email address"));
		Assert.assertTrue(body.contains("mailto:" + SUPPORT_EMAIL_ADDRESS));
		Assert.assertTrue(body.contains("You are receiving this email because you signed up to Behavior Bridge."));
		Assert.assertTrue(body.contains("href=\"" + PRIVACY_POLICY_URL + "\""));
		Assert.assertTrue(body.contains("Privacy Policy"));
		assertBrandingRendered(body);
	}

	@Test
	public void rendersV2PasswordResetEmail() {
		Map<String, Object> context = baseContext();
		context.put("passwordResetLink", PASSWORD_RESET_LINK);

		String subject = render(EmailMessageTemplate.V2_PASSWORD_RESET, "subject", context).trim();
		String body = render(EmailMessageTemplate.V2_PASSWORD_RESET, "body", context);

		Assert.assertEquals("Behavior Bridge: Reset your password", subject);
		Assert.assertTrue(body.contains("Reset your password"));
		Assert.assertTrue(body.contains("mailto:" + RECIPIENT_EMAIL_ADDRESS));
		Assert.assertTrue(body.contains("href=\"" + PASSWORD_RESET_LINK + "\""));
		Assert.assertTrue(body.contains("Reset password"));
		Assert.assertTrue(body.contains("If you did not request a password reset"));
		Assert.assertTrue(body.contains("Team Behavior Bridge"));
		Assert.assertTrue(body.contains("has an active Behavior Bridge account"));
		Assert.assertTrue(body.contains("Privacy Policy"));
		assertBrandingRendered(body);
	}

	@Test
	public void customFooterReplacesDefaultsAndIsEscaped() {
		Map<String, Object> context = baseContext();
		context.put("verificationUrl", VERIFICATION_URL);
		context.put("passwordResetLink", PASSWORD_RESET_LINK);
		context.put("emailFooterText", "<Custom & private>");

		for (EmailMessageTemplate messageTemplate : new EmailMessageTemplate[]{
				EmailMessageTemplate.V2_ACCOUNT_VERIFICATION,
				EmailMessageTemplate.V2_PASSWORD_RESET
		}) {
			String body = render(messageTemplate, "body", context);

			Assert.assertTrue("Expected custom footer text to be HTML-escaped",
					body.contains("&lt;Custom &amp; private&gt;"));
			Assert.assertFalse("Did not expect raw custom footer HTML", body.contains("<Custom & private>"));
			Assert.assertFalse("Did not expect account-verification default footer",
					body.contains("because you signed up to Behavior Bridge"));
			Assert.assertFalse("Did not expect password-reset default footer",
					body.contains("has an active Behavior Bridge account"));
			Assert.assertTrue("Expected privacy link to remain separate from custom footer",
					body.contains("Privacy Policy"));
		}
	}

	@Test
	public void defaultFootersRenderWithoutPrivacyLinkWhenNotConfigured() {
		Map<String, Object> context = baseContext();
		context.remove("privacyPolicyUrl");
		context.put("verificationUrl", VERIFICATION_URL);
		context.put("passwordResetLink", PASSWORD_RESET_LINK);

		String verificationBody = render(EmailMessageTemplate.V2_ACCOUNT_VERIFICATION, "body", context);
		String passwordResetBody = render(EmailMessageTemplate.V2_PASSWORD_RESET, "body", context);

		Assert.assertTrue(verificationBody.contains("because you signed up to Behavior Bridge"));
		Assert.assertTrue(passwordResetBody.contains("has an active Behavior Bridge account"));
		Assert.assertFalse(verificationBody.contains("Privacy Policy"));
		Assert.assertFalse(passwordResetBody.contains("Privacy Policy"));
	}

	@Test
	public void preservesV1AccountTemplates() {
		Map<String, Object> context = baseContext();
		context.put("verificationUrl", VERIFICATION_URL);
		context.put("passwordResetLink", PASSWORD_RESET_LINK);

		Assert.assertTrue(render(EmailMessageTemplate.ACCOUNT_VERIFICATION, "body", context)
				.contains("Simply click on the url below"));
		Assert.assertTrue(render(EmailMessageTemplate.PASSWORD_RESET, "body", context)
				.contains("to reset your password"));
	}

	protected void assertBrandingRendered(@Nonnull String body) {
		Assert.assertTrue(body.contains("src=\"https://example.com/logo.png\""));
		Assert.assertTrue(body.contains("background-color:#F7F8F7"));
		Assert.assertTrue(body.contains("background-color:#2F8868"));
		Assert.assertTrue(body.contains("@media only screen and (max-width: 639px)"));
		Assert.assertTrue(body.contains("width:600px; max-width:600px; background-color:#FFFFFF; border-radius:8px"));
	}

	@Nonnull
	protected Map<String, Object> baseContext() {
		Map<String, Object> context = new HashMap<>();
		context.put("colors", Map.of(
				"n50", "#F7F8F7",
				"n900", "#202020",
				"p500", "#2F8868"
		));
		context.put("institutionId", "COBALT_FHIR");
		context.put("platformName", PLATFORM_NAME);
		context.put("platformEmailImageUrl", "https://example.com/logo.png");
		context.put("recipientEmailAddress", RECIPIENT_EMAIL_ADDRESS);
		context.put("supportEmailAddress", SUPPORT_EMAIL_ADDRESS);
		context.put("privacyPolicyUrl", PRIVACY_POLICY_URL);
		return context;
	}

	@Nonnull
	protected String render(@Nonnull EmailMessageTemplate messageTemplate,
											 @Nonnull String templateChildName,
											 @Nonnull Map<String, Object> context) {
		HandlebarsTemplater handlebarsTemplater = new HandlebarsTemplater.Builder(Paths.get("messages/email"))
				.viewsDirectoryName("views")
				.shouldCacheTemplates(false)
				.build();

		return handlebarsTemplater.mergeTemplate(
				messageTemplate.name(),
				templateChildName,
				Locale.US,
				context
		).orElseThrow();
	}
}
