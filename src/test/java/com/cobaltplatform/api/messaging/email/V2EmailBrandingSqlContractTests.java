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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
@Category(UnitTest.class)
public class V2EmailBrandingSqlContractTests {
	@Nonnull
	private static final Path MIGRATION_PATH = Paths.get("sql/updates/260-v2-account-email-templates.sql");
	@Nonnull
	private static final Path POST_BOOTSTRAP_PATH = Paths.get("sql/initial/post-bootstrap.sql");
	@Nonnull
	private static final String PLATFORM_EMAIL_IMAGE_URL = "https://cdn-prod.cobalt.care/logos/dh-behavior-bridge-hero.png";

	@Test
	public void migrationScopesBehaviorBridgeBrandingData() throws IOException {
		String migration = Files.readString(MIGRATION_PATH, StandardCharsets.UTF_8);

		Assert.assertTrue(migration.contains("ADD COLUMN platform_email_image_url TEXT"));
		Assert.assertTrue(migration.contains("SET platform_name = 'Behavior Bridge'\nWHERE institution_id = 'COBALT_COURSES'"));

		int imageAssignmentIndex = migration.indexOf("SET platform_email_image_url = '" + PLATFORM_EMAIL_IMAGE_URL + "'");
		int institutionColorSectionIndex = migration.indexOf("-- Populate missing institution colors");

		Assert.assertTrue(imageAssignmentIndex >= 0);
		Assert.assertTrue(institutionColorSectionIndex > imageAssignmentIndex);
		Assert.assertTrue(migration.substring(imageAssignmentIndex, institutionColorSectionIndex)
				.contains("WHERE institution_id IN ('COBALT_COURSES', 'BEHAVIOR_BRIDGE')"));

		String institutionColorSection = migration.substring(institutionColorSectionIndex);
		Assert.assertTrue(institutionColorSection.contains("WHERE institution_id = 'COBALT_COURSES'"));
		Assert.assertFalse(institutionColorSection.contains("BEHAVIOR_BRIDGE"));
	}

	@Test
	public void postBootstrapOnlySeedsLocalCoursesInstitution() throws IOException {
		String postBootstrap = Files.readString(POST_BOOTSTRAP_PATH, StandardCharsets.UTF_8);

		Assert.assertTrue(postBootstrap.contains("WHERE institution_id = 'COBALT_COURSES'"));
		Assert.assertTrue(postBootstrap.contains(PLATFORM_EMAIL_IMAGE_URL));
		Assert.assertFalse(postBootstrap.contains("BEHAVIOR_BRIDGE"));
		Assert.assertTrue(postBootstrap.contains("('N50',  '#F7F8F7')"));
		Assert.assertTrue(postBootstrap.contains("('N900', '#2D3030')"));
		Assert.assertTrue(postBootstrap.contains("('P500', '#2F7F61')"));
	}
}
