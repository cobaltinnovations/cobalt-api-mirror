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

package com.cobaltplatform.api.model.api.response;

import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.context.CurrentContext;
import com.cobaltplatform.api.model.api.response.InstitutionApiResponse.InstitutionApiResponseFactory;
import com.cobaltplatform.api.model.db.Institution;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.UserExperienceType.UserExperienceTypeId;
import com.cobaltplatform.api.service.InstitutionService;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.time.ZoneId;
import java.util.Locale;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class InstitutionApiResponseTests {
	@Test
	public void institutionResponseExposesImageRepositoryEnabled() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			InstitutionService institutionService = app.getInjector().getInstance(InstitutionService.class);
			InstitutionApiResponseFactory institutionApiResponseFactory = app.getInjector().getInstance(InstitutionApiResponseFactory.class);
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			CurrentContext currentContext = currentContext();

			Institution institution = institutionService.findInstitutionById(InstitutionId.COBALT).get();

			Assert.assertTrue("COBALT should have image repository enabled for testing", institution.getImageRepositoryEnabled());
			Assert.assertTrue("Institution response should expose image repository enabled", institutionApiResponseFactory.create(institution, currentContext).getImageRepositoryEnabled());

			database.execute("""
					UPDATE institution
					SET image_repository_enabled=?
					WHERE institution_id=?
					""", false, InstitutionId.COBALT);

			institution = institutionService.findInstitutionById(InstitutionId.COBALT).get();

			Assert.assertFalse("Institution should reflect disabled image repository flag", institution.getImageRepositoryEnabled());
			Assert.assertFalse("Institution response should expose disabled image repository flag", institutionApiResponseFactory.create(institution, currentContext).getImageRepositoryEnabled());
		});
	}

	@Nonnull
	protected CurrentContext currentContext() {
		return new CurrentContext.Builder(InstitutionId.COBALT, Locale.US, ZoneId.of("America/New_York"))
				.userExperienceTypeId(UserExperienceTypeId.PATIENT)
				.build();
	}
}
