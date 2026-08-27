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
public class MessageServiceEmailBrandingTests {
	@Test
	public void explicitImageOverrideTakesPrecedence() {
		Assert.assertEquals("https://example.com/override.png", MessageService.resolvePlatformEmailImageUrl(
				"https://example.com/override.png",
				"https://example.com/institution.png",
				"https://example.com/fallback.png"
		));
	}

	@Test
	public void institutionImageTakesPrecedenceOverFallback() {
		Assert.assertEquals("https://example.com/institution.png", MessageService.resolvePlatformEmailImageUrl(
				null,
				"https://example.com/institution.png",
				"https://example.com/fallback.png"
		));
	}

	@Test
	public void blankConfiguredImagesUseFallback() {
		Assert.assertEquals("https://example.com/fallback.png", MessageService.resolvePlatformEmailImageUrl(
				" ",
				"\t",
				"https://example.com/fallback.png"
		));
	}
}
