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

import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.model.api.request.CreateAccountRequest;
import com.cobaltplatform.api.model.api.request.CreateScreeningAnswersRequest;
import com.cobaltplatform.api.model.api.request.CreateScreeningSessionRequest;
import com.cobaltplatform.api.model.db.AccountSource.AccountSourceId;
import com.cobaltplatform.api.model.db.Institution;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.model.db.ScreeningFlow;
import com.cobaltplatform.api.model.db.ScreeningSession;
import com.cobaltplatform.api.model.service.ScreeningQuestionContext;
import com.cobaltplatform.api.model.service.ScreeningQuestionContextId;
import com.cobaltplatform.api.model.service.ScreeningSessionDestination;
import com.cobaltplatform.api.model.service.ScreeningSessionDestination.ScreeningSessionDestinationId;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class ScreeningServiceTests {
	@Test
	public void basicScreening() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback((app) -> {
			InstitutionId institutionId = InstitutionId.COBALT;
			InstitutionService institutionService = app.getInjector().getInstance(InstitutionService.class);
			ScreeningService screeningService = app.getInjector().getInstance(ScreeningService.class);
			AccountService accountService = app.getInjector().getInstance(AccountService.class);

			Institution institution = institutionService.findInstitutionById(institutionId).get();

			// Make an anonymous account to test the flow
			UUID accountId = accountService.createAccount(new CreateAccountRequest() {{
				setAccountSourceId(AccountSourceId.ANONYMOUS);
				setInstitutionId(institutionId);
			}});

			// Find the provider triage flow for the account's institution
			ScreeningFlow providerTriageScreeningFlow = screeningService.findScreeningFlowById(institution.getFeatureScreeningFlowId()).get();

			// Confirm there are no existing screening sessions for that flow
			List<ScreeningSession> screeningSessions = screeningService.findScreeningSessionsByScreeningFlowId(
					providerTriageScreeningFlow.getScreeningFlowId(), accountId);

			assertEquals("Account already has a provider triage screening session", 0, screeningSessions.size());

			// Start a new screening session for that flow
			UUID screeningSessionId = screeningService.createScreeningSession(new CreateScreeningSessionRequest() {{
				setScreeningFlowId(providerTriageScreeningFlow.getScreeningFlowId());
				setTargetAccountId(accountId);
				setCreatedByAccountId(accountId);
			}});

			// Ensure the screening session was created
			screeningSessions = screeningService.findScreeningSessionsByScreeningFlowId(
					providerTriageScreeningFlow.getScreeningFlowId(), accountId);

			assertEquals("Account is missing a provider triage screening session", 1, screeningSessions.size());

			// Keep track of some things so we can answer questions from a few screenings, then "reset" to an earlier
			// screening by jumping back and changing our answer to an earlier question
			int screeningQuestionIndexToResetTo = 2;
			int screeningQuestionIndexToResetAt = 12;
			boolean reset = false;
			ScreeningQuestionContext screeningQuestionContextToResetTo = null;
			int i = 0;

			while (true) {
				ScreeningQuestionContext screeningQuestionContext = screeningService.findNextUnansweredScreeningQuestionContextByScreeningSessionId(screeningSessionId).orElse(null);

				// No more questions in the session, we're done.
				if (screeningQuestionContext == null)
					break;

				// Store off so we can reset to this question later
				if (i == screeningQuestionIndexToResetTo)
					screeningQuestionContextToResetTo = screeningQuestionContext;

				// If it's time to reset, restore the old question context
				if (i == screeningQuestionIndexToResetAt && !reset)
					screeningQuestionContext = screeningQuestionContextToResetTo;

				// Pick the last answer option...
				UUID screeningAnswerOptionId = screeningQuestionContext.getScreeningAnswerOptions().get(
						screeningQuestionContext.getScreeningAnswerOptions().size() - 1).getScreeningAnswerOptionId();

				// ...or, if we are resetting, change the previous answer to a different one (the first option)
				if (i == screeningQuestionIndexToResetAt) {
					screeningAnswerOptionId = screeningQuestionContext.getScreeningAnswerOptions().get(0).getScreeningAnswerOptionId();
					reset = true;
				}

				ScreeningQuestionContextId screeningQuestionContextId = new ScreeningQuestionContextId(
						screeningQuestionContext.getScreeningSessionScreening().getScreeningSessionScreeningId(),
						screeningQuestionContext.getScreeningQuestion().getScreeningQuestionId());

				UUID pinnedScreeningAnswerOptionId = screeningAnswerOptionId;

				// ...and answer it.
				screeningService.createScreeningAnswers(new CreateScreeningAnswersRequest() {{
					setScreeningQuestionContextId(screeningQuestionContextId);
					setCreatedByAccountId(accountId);
					setAnswers(List.of(new CreateAnswerRequest() {{
						setScreeningAnswerOptionId(pinnedScreeningAnswerOptionId);
					}}));
				}});

				++i;
			}

			ScreeningSessionDestination screeningSessionDestination = screeningService.determineDestinationForScreeningSessionId(screeningSessionId).get();

			Assert.assertEquals("Post-session destination should have been the crisis screen",
					ScreeningSessionDestinationId.CRISIS, screeningSessionDestination.getScreeningSessionDestinationId());
		});
	}
}
