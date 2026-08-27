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
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
@Category(UnitTest.class)
public class V2EmailLayoutContractTests {
	@Nonnull
	private static final Path V2_LAYOUT_PATH = Paths.get("messages/email/layouts/en/v2.hbs");

	@Test
	public void sharedLayoutOwnsOrganizationBrandingContentSlotAndFooter() throws IOException {
		String layout = Files.readString(V2_LAYOUT_PATH, StandardCharsets.UTF_8);

		Assert.assertTrue(layout.contains("platformEmailImageUrl"));
		Assert.assertTrue(layout.contains("institutionId"));
		Assert.assertTrue(layout.contains("{{#block \"content\"}}"));
		Assert.assertTrue(layout.contains("{{#if emailFooterText}}"));
		Assert.assertTrue(layout.contains("{{emailFooterText}}"));
		Assert.assertTrue(layout.contains("{{#block \"footer\"}}"));
		Assert.assertTrue(layout.contains("{{#if privacyPolicyUrl}}"));
	}

	@Test
	public void everyV2EmailUsesSharedLayoutAndKeepsShellFieldsOutOfItsContent() {
		Arrays.stream(EmailMessageTemplate.values())
				.filter(messageTemplate -> messageTemplate.name().startsWith("V2_"))
				.forEach(this::assertUsesSharedLayout);
	}

	protected void assertUsesSharedLayout(@Nonnull EmailMessageTemplate messageTemplate) {
		Path bodyPath = Paths.get("messages/email/views", messageTemplate.name(), "en/body.hbs");
		Assert.assertTrue("Expected V2 body template at " + bodyPath, Files.isRegularFile(bodyPath));

		try {
			String body = Files.readString(bodyPath, StandardCharsets.UTF_8);

			Assert.assertTrue(messageTemplate + " must provide the shared central content block",
					body.contains("{{#partial \"content\"}}"));
			Assert.assertTrue(messageTemplate + " must render through the shared V2 layout",
					body.contains("{{> layouts/en/v2}}"));
			Assert.assertFalse(messageTemplate + " must leave institution footer handling to the V2 layout",
					body.contains("emailFooterText"));
			Assert.assertFalse(messageTemplate + " must leave Privacy Policy handling to the V2 layout",
					body.contains("privacyPolicyUrl"));
			Assert.assertFalse(messageTemplate + " must leave organization header handling to the V2 layout",
					body.contains("platformEmailImageUrl"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
