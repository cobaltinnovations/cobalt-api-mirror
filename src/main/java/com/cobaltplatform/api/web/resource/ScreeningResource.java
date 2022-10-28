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

package com.cobaltplatform.api.web.resource;

import com.cobaltplatform.api.context.CurrentContext;
import com.cobaltplatform.api.model.api.request.CreateScreeningAnswersRequest;
import com.cobaltplatform.api.model.api.request.CreateScreeningSessionRequest;
import com.cobaltplatform.api.model.api.request.SkipScreeningSessionRequest;
import com.cobaltplatform.api.model.api.response.ScreeningAnswerApiResponse.ScreeningAnswerApiResponseFactory;
import com.cobaltplatform.api.model.api.response.ScreeningAnswerOptionApiResponse.ScreeningAnswerOptionApiResponseFactory;
import com.cobaltplatform.api.model.api.response.ScreeningConfirmationPromptApiResponse;
import com.cobaltplatform.api.model.api.response.ScreeningConfirmationPromptApiResponse.ScreeningConfirmationPromptApiResponseFactory;
import com.cobaltplatform.api.model.api.response.ScreeningFlowVersionApiResponse.ScreeningFlowVersionApiResponseFactory;
import com.cobaltplatform.api.model.api.response.ScreeningQuestionApiResponse.ScreeningQuestionApiResponseFactory;
import com.cobaltplatform.api.model.api.response.ScreeningSessionApiResponse.ScreeningSessionApiResponseFactory;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.ScreeningAnswer;
import com.cobaltplatform.api.model.db.ScreeningAnswerOption;
import com.cobaltplatform.api.model.db.ScreeningConfirmationPrompt;
import com.cobaltplatform.api.model.db.ScreeningFlow;
import com.cobaltplatform.api.model.db.ScreeningFlowVersion;
import com.cobaltplatform.api.model.db.ScreeningQuestion;
import com.cobaltplatform.api.model.db.ScreeningSession;
import com.cobaltplatform.api.model.db.ScreeningSessionScreening;
import com.cobaltplatform.api.model.security.AuthenticationRequired;
import com.cobaltplatform.api.model.service.ScreeningQuestionContext;
import com.cobaltplatform.api.model.service.ScreeningQuestionContextId;
import com.cobaltplatform.api.model.service.ScreeningSessionDestination;
import com.cobaltplatform.api.service.AccountService;
import com.cobaltplatform.api.service.AuthorizationService;
import com.cobaltplatform.api.service.ScreeningService;
import com.cobaltplatform.api.web.request.RequestBodyParser;
import com.soklet.web.annotation.GET;
import com.soklet.web.annotation.POST;
import com.soklet.web.annotation.PathParameter;
import com.soklet.web.annotation.QueryParameter;
import com.soklet.web.annotation.RequestBody;
import com.soklet.web.annotation.Resource;
import com.soklet.web.exception.AuthorizationException;
import com.soklet.web.exception.NotFoundException;
import com.soklet.web.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Resource
@Singleton
@ThreadSafe
public class ScreeningResource {
	@Nonnull
	private final ScreeningService screeningService;
	@Nonnull
	private final AccountService accountService;
	@Nonnull
	private final AuthorizationService authorizationService;
	@Nonnull
	private final RequestBodyParser requestBodyParser;
	@Nonnull
	private final ScreeningSessionApiResponseFactory screeningSessionApiResponseFactory;
	@Nonnull
	private final ScreeningQuestionApiResponseFactory screeningQuestionApiResponseFactory;
	@Nonnull
	private final ScreeningAnswerOptionApiResponseFactory screeningAnswerOptionApiResponseFactory;
	@Nonnull
	private final ScreeningAnswerApiResponseFactory screeningAnswerApiResponseFactory;
	@Nonnull
	private final ScreeningFlowVersionApiResponseFactory screeningFlowVersionApiResponseFactory;
	@Nonnull
	private final ScreeningConfirmationPromptApiResponseFactory screeningConfirmationPromptApiResponseFactory;
	@Nonnull
	private final Provider<CurrentContext> currentContextProvider;
	@Nonnull
	private final Logger logger;

	@Inject
	public ScreeningResource(@Nonnull ScreeningService screeningService,
													 @Nonnull AccountService accountService,
													 @Nonnull AuthorizationService authorizationService,
													 @Nonnull RequestBodyParser requestBodyParser,
													 @Nonnull ScreeningSessionApiResponseFactory screeningSessionApiResponseFactory,
													 @Nonnull ScreeningQuestionApiResponseFactory screeningQuestionApiResponseFactory,
													 @Nonnull ScreeningAnswerOptionApiResponseFactory screeningAnswerOptionApiResponseFactory,
													 @Nonnull ScreeningAnswerApiResponseFactory screeningAnswerApiResponseFactory,
													 @Nonnull ScreeningFlowVersionApiResponseFactory screeningFlowVersionApiResponseFactory,
													 @Nonnull ScreeningConfirmationPromptApiResponseFactory screeningConfirmationPromptApiResponseFactory,
													 @Nonnull Provider<CurrentContext> currentContextProvider) {
		requireNonNull(screeningService);
		requireNonNull(accountService);
		requireNonNull(authorizationService);
		requireNonNull(requestBodyParser);
		requireNonNull(screeningSessionApiResponseFactory);
		requireNonNull(screeningQuestionApiResponseFactory);
		requireNonNull(screeningAnswerOptionApiResponseFactory);
		requireNonNull(screeningAnswerApiResponseFactory);
		requireNonNull(screeningFlowVersionApiResponseFactory);
		requireNonNull(screeningConfirmationPromptApiResponseFactory);
		requireNonNull(currentContextProvider);

		this.screeningService = screeningService;
		this.accountService = accountService;
		this.authorizationService = authorizationService;
		this.requestBodyParser = requestBodyParser;
		this.screeningSessionApiResponseFactory = screeningSessionApiResponseFactory;
		this.screeningQuestionApiResponseFactory = screeningQuestionApiResponseFactory;
		this.screeningAnswerOptionApiResponseFactory = screeningAnswerOptionApiResponseFactory;
		this.screeningAnswerApiResponseFactory = screeningAnswerApiResponseFactory;
		this.screeningFlowVersionApiResponseFactory = screeningFlowVersionApiResponseFactory;
		this.screeningConfirmationPromptApiResponseFactory = screeningConfirmationPromptApiResponseFactory;
		this.currentContextProvider = currentContextProvider;
		this.logger = LoggerFactory.getLogger(getClass());
	}

	@Nonnull
	@POST("/screening-sessions")
	@AuthenticationRequired
	public ApiResponse createScreeningSession(@Nonnull @RequestBody String requestBody) {
		requireNonNull(requestBody);

		Account account = getCurrentContext().getAccount().get();
		Account targetAccount = null;

		CreateScreeningSessionRequest request = getRequestBodyParser().parse(requestBody, CreateScreeningSessionRequest.class);
		request.setCreatedByAccountId(account.getAccountId());

		// If you don't supply a target account, we assume you are starting a session for yourself
		if (request.getTargetAccountId() == null)
			request.setTargetAccountId(account.getAccountId());

		// Ensure you are permitted to start a screening session for the specified account
		if (request.getTargetAccountId() != null) {
			targetAccount = getAccountService().findAccountById(request.getTargetAccountId()).orElse(null);

			if (!getAuthorizationService().canPerformScreening(account, targetAccount))
				throw new AuthorizationException();
		}

		UUID screeningSessionId = getScreeningService().createScreeningSession(request);
		ScreeningSession screeningSession = getScreeningService().findScreeningSessionById(screeningSessionId).get();
		Account pinnedTargetAccount = targetAccount;

		return new ApiResponse(new HashMap<String, Object>() {{
			put("screeningSession", getScreeningSessionApiResponseFactory().create(screeningSession, pinnedTargetAccount));
		}});
	}

	@Nonnull
	@GET("/screening-sessions")
	@AuthenticationRequired
	public ApiResponse screeningSessions(@Nonnull @QueryParameter UUID screeningFlowId,
																			 @Nonnull @QueryParameter Optional<UUID> targetAccountId) {
		requireNonNull(screeningFlowId);
		requireNonNull(targetAccountId);

		Account account = getCurrentContext().getAccount().get();

		List<ScreeningSession> screeningSessions = getScreeningService().findScreeningSessionsByScreeningFlowId(screeningFlowId, account.getAccountId()).stream()
				.filter(screeningSession -> targetAccountId.isEmpty() ? true : screeningSession.getTargetAccountId().equals(targetAccountId.get()))
				.filter(screeningSession -> getAuthorizationService().canViewScreeningSession(screeningSession, account, getAccountService().findAccountById(screeningSession.getTargetAccountId()).get()))
				.collect(Collectors.toList());

		return new ApiResponse(new HashMap<String, Object>() {{
			put("screeningSessions", screeningSessions.stream()
					.map(screeningSession -> {
						Account targetAccount = getAccountService().findAccountById(screeningSession.getTargetAccountId()).get();
						return getScreeningSessionApiResponseFactory().create(screeningSession, targetAccount);
					}).collect(Collectors.toList()));
		}});
	}

	@Nonnull
	@GET("/screening-flow-versions")
	@AuthenticationRequired
	public ApiResponse screeningFlowVersions(@Nonnull @QueryParameter UUID screeningFlowId,
																					 @Nonnull @QueryParameter Optional<Boolean> activeOnly) {
		requireNonNull(screeningFlowId);
		requireNonNull(activeOnly);

		ScreeningFlow screeningFlow = getScreeningService().findScreeningFlowById(screeningFlowId).orElse(null);

		if (screeningFlow == null)
			throw new NotFoundException();

		List<ScreeningFlowVersion> screeningFlowVersions = getScreeningService().findScreeningFlowVersionsByScreeningFlowId(screeningFlowId);
		UUID activeScreeningFlowVersionId = screeningFlow.getActiveScreeningFlowVersionId();

		boolean filterActiveOnly = activeOnly.orElse(false);
		return new ApiResponse(new HashMap<String, Object>() {{
			put("screeningFlowVersions", screeningFlowVersions.stream()
					.filter(screeningFlow -> filterActiveOnly ? activeScreeningFlowVersionId.equals(screeningFlow.getScreeningFlowId()) : true)
					.map(screeningFlowVersion -> getScreeningFlowVersionApiResponseFactory().create(screeningFlowVersion))
					.collect(Collectors.toList()));
			put("activeScreeningFlowVersionId", activeScreeningFlowVersionId);
		}});
	}

	/**
	 * Skips an entire screening flow version, e.g. user clicks "Skip for now" on 1:1 triage flow before even starting the
	 * screening.  In this scenario, we create a session for the provided version, and immediately
	 * mark as completed/skipped.
	 */
	@Nonnull
	@POST("/screening-flow-versions/{screeningFlowVersionId}/skip")
	@AuthenticationRequired
	public ApiResponse skipEntireScreeningFlowVersion(@Nonnull @PathParameter UUID screeningFlowVersionId) {
		requireNonNull(screeningFlowVersionId);

		Account account = getCurrentContext().getAccount().get();
		UUID screeningSessionId = getScreeningService().createScreeningSession(new CreateScreeningSessionRequest() {{
			setScreeningFlowVersionId(screeningFlowVersionId);
			setCreatedByAccountId(account.getAccountId());
			setTargetAccountId(account.getAccountId());
			setImmediatelySkip(true);
		}});

		ScreeningSession screeningSession = getScreeningService().findScreeningSessionById(screeningSessionId).get();
		return new ApiResponse(new HashMap<String, Object>() {{
			put("screeningSession", getScreeningSessionApiResponseFactory().create(screeningSession, account));
		}});
	}

	/**
	 * Skips a screening flow (marks complete/skipped) after a screening session has been created.
	 * For example, you might be a few answers into a screening and decide to skip instead.
	 * <p>
	 * This is just a convenience for FE to call us with a screeningQuestionContextId.  This method just hands off to
	 * the more "direct" POST /screening-sessions/{screeningSessionId}/skip.
	 */
	@Nonnull
	@POST("/screening-question-contexts/{screeningQuestionContextId}/skip")
	@AuthenticationRequired
	public ApiResponse skipRemainingScreeningFlow(@Nonnull @PathParameter ScreeningQuestionContextId screeningQuestionContextId) {
		requireNonNull(screeningQuestionContextId);

		ScreeningSessionScreening screeningSessionScreening = getScreeningService().findScreeningSessionScreeningById(screeningQuestionContextId.getScreeningSessionScreeningId()).orElse(null);

		if (screeningSessionScreening == null)
			throw new NotFoundException();

		return skipRemainingScreeningFlow(screeningSessionScreening.getScreeningSessionId());
	}

	/**
	 * Skips a screening flow (marks complete/skipped) after a screening session has been created.
	 * For example, you might be a few answers into a screening and decide to skip instead.
	 */
	@Nonnull
	@POST("/screening-sessions/{screeningSessionId}/skip")
	@AuthenticationRequired
	public ApiResponse skipRemainingScreeningFlow(@Nonnull @PathParameter UUID screeningSessionId) {
		requireNonNull(screeningSessionId);

		ScreeningSession screeningSession = getScreeningService().findScreeningSessionById(screeningSessionId).orElse(null);

		if (screeningSession == null)
			throw new NotFoundException();

		Account account = getCurrentContext().getAccount().get();
		Account targetAccount = getAccountService().findAccountById(screeningSession.getTargetAccountId()).orElse(null);

		// Ensure you have permission to skip for this screening session
		if (!getAuthorizationService().canPerformScreening(account, targetAccount))
			throw new AuthorizationException();

		getScreeningService().skipScreeningSession(new SkipScreeningSessionRequest() {{
			setScreeningSessionId(screeningSessionId);
		}});

		ScreeningSession skippedScreeningSession = getScreeningService().findScreeningSessionById(screeningSessionId).get();
		return new ApiResponse(new HashMap<String, Object>() {{
			put("screeningSession", getScreeningSessionApiResponseFactory().create(skippedScreeningSession, account));
		}});
	}

	@Nonnull
	@GET("/screening-question-contexts/{screeningQuestionContextId}")
	@AuthenticationRequired
	public ApiResponse screeningQuestionContext(@Nonnull @PathParameter ScreeningQuestionContextId screeningQuestionContextId) {
		requireNonNull(screeningQuestionContextId);

		ScreeningQuestionContext screeningQuestionContext = getScreeningService().findScreeningQuestionContextById(
				screeningQuestionContextId).orElse(null);

		if (screeningQuestionContext == null)
			throw new NotFoundException();

		ScreeningSession screeningSession = getScreeningService().findScreeningSessionById(screeningQuestionContext.getScreeningSessionScreening().getScreeningSessionId()).get();
		Account account = getCurrentContext().getAccount().get();
		Account targetAccount = getAccountService().findAccountById(screeningSession.getTargetAccountId()).get();

		// Ensure you have permission to pull data for this screening session
		if (!getAuthorizationService().canPerformScreening(account, targetAccount))
			throw new AuthorizationException();

		// Questions, answer options, and any answers already given for this screening session screening
		ScreeningQuestion screeningQuestion = screeningQuestionContext.getScreeningQuestion();
		List<ScreeningAnswerOption> screeningAnswerOptions = screeningQuestionContext.getScreeningAnswerOptions();
		List<ScreeningAnswer> screeningAnswers = getScreeningService().findScreeningAnswersByScreeningQuestionContextId(screeningQuestionContextId);

		// Generate a link back to the previously-answered question in the same screening session.
		// This might be a question in a previous screening session screening.
		// This will not exist at all if we have not answered any questions yet.
		ScreeningQuestionContext previousScreeningQuestionContext =
				getScreeningService().findPreviousScreeningQuestionContextByScreeningQuestionContextId(screeningQuestionContextId).orElse(null);

		ScreeningSessionDestination screeningSessionDestination = getScreeningService().determineDestinationForScreeningSessionId(screeningQuestionContext.getScreeningSessionScreening().getScreeningSessionId()).orElse(null);
		ScreeningFlowVersion screeningFlowVersion = getScreeningService().findScreeningFlowVersionById(screeningSession.getScreeningFlowVersionId()).get();

		// Is there a prompt we should show before displaying this question?  If so, provide it.
		UUID preQuestionScreeningConfirmationPromptId = screeningQuestion.getPreQuestionScreeningConfirmationPromptId();
		ScreeningConfirmationPrompt preQuestionScreeningConfirmationPrompt = null;

		if (preQuestionScreeningConfirmationPromptId != null)
			preQuestionScreeningConfirmationPrompt = getScreeningService().findScreeningConfirmationPromptById(preQuestionScreeningConfirmationPromptId).get();

		ScreeningConfirmationPromptApiResponse preQuestionScreeningConfirmationPromptApiResponse = preQuestionScreeningConfirmationPrompt == null ? null
				: getScreeningConfirmationPromptApiResponseFactory().create(preQuestionScreeningConfirmationPrompt);

		return new ApiResponse(new HashMap<String, Object>() {{
			put("previousScreeningQuestionContextId", previousScreeningQuestionContext == null ? null
					: previousScreeningQuestionContext.getScreeningQuestionContextId());
			put("screeningQuestion", getScreeningQuestionApiResponseFactory().create(screeningQuestion));
			put("screeningAnswerOptions", screeningAnswerOptions.stream()
					.map(screeningAnswerOption -> getScreeningAnswerOptionApiResponseFactory().create(screeningAnswerOption))
					.collect(Collectors.toList()));
			put("screeningAnswers", screeningAnswers.stream()
					.map(screeningAnswer -> getScreeningAnswerApiResponseFactory().create(screeningAnswer))
					.collect(Collectors.toList()));
			put("screeningSessionDestination", screeningSessionDestination);
			put("screeningFlowVersion", getScreeningFlowVersionApiResponseFactory().create(screeningFlowVersion));
			put("preQuestionScreeningConfirmationPrompt", preQuestionScreeningConfirmationPromptApiResponse);
		}});
	}

	@Nonnull
	@POST("/screening-answers")
	@AuthenticationRequired
	public ApiResponse createScreeningAnswers(@Nonnull @RequestBody String requestBody) {
		requireNonNull(requestBody);

		Account account = getCurrentContext().getAccount().get();

		CreateScreeningAnswersRequest request = getRequestBodyParser().parse(requestBody, CreateScreeningAnswersRequest.class);
		request.setCreatedByAccountId(account.getAccountId());

		ScreeningSessionScreening screeningSessionScreening = request.getScreeningQuestionContextId() == null ? null :
				getScreeningService().findScreeningSessionScreeningById(request.getScreeningQuestionContextId().getScreeningSessionScreeningId()).orElse(null);

		if (screeningSessionScreening == null)
			throw new NotFoundException();

		ScreeningSession screeningSession = getScreeningService().findScreeningSessionById(screeningSessionScreening.getScreeningSessionId()).get();
		Account targetAccount = getAccountService().findAccountById(screeningSession.getTargetAccountId()).orElse(null);

		// Ensure you have permission to answer for this screening session
		if (!getAuthorizationService().canPerformScreening(account, targetAccount))
			throw new AuthorizationException();

		List<UUID> screeningAnswerIds = getScreeningService().createScreeningAnswers(request);
		List<ScreeningAnswer> screeningAnswers = screeningAnswerIds.stream()
				.map(screeningAnswerId -> getScreeningService().findScreeningAnswerById(screeningAnswerId).get())
				.collect(Collectors.toList());

		ScreeningQuestionContext nextScreeningQuestionContext =
				getScreeningService().findNextUnansweredScreeningQuestionContextByScreeningSessionId(screeningSession.getScreeningSessionId()).orElse(null);

		ScreeningSessionDestination screeningSessionDestination = getScreeningService().determineDestinationForScreeningSessionId(screeningSession.getScreeningSessionId()).orElse(null);

		return new ApiResponse(new HashMap<String, Object>() {{
			put("screeningAnswers", screeningAnswers.stream()
					.map(screeningAnswer -> getScreeningAnswerApiResponseFactory().create(screeningAnswer))
					.collect(Collectors.toList()));
			put("nextScreeningQuestionContextId", nextScreeningQuestionContext == null ? null : nextScreeningQuestionContext.getScreeningQuestionContextId());
			put("screeningSessionDestination", screeningSessionDestination);
		}});
	}

	@Nonnull
	protected ScreeningService getScreeningService() {
		return this.screeningService;
	}

	@Nonnull
	protected AccountService getAccountService() {
		return this.accountService;
	}

	@Nonnull
	protected AuthorizationService getAuthorizationService() {
		return this.authorizationService;
	}

	@Nonnull
	protected RequestBodyParser getRequestBodyParser() {
		return this.requestBodyParser;
	}

	@Nonnull
	protected ScreeningSessionApiResponseFactory getScreeningSessionApiResponseFactory() {
		return this.screeningSessionApiResponseFactory;
	}

	@Nonnull
	protected ScreeningQuestionApiResponseFactory getScreeningQuestionApiResponseFactory() {
		return this.screeningQuestionApiResponseFactory;
	}

	@Nonnull
	protected ScreeningAnswerOptionApiResponseFactory getScreeningAnswerOptionApiResponseFactory() {
		return this.screeningAnswerOptionApiResponseFactory;
	}

	@Nonnull
	protected ScreeningAnswerApiResponseFactory getScreeningAnswerApiResponseFactory() {
		return this.screeningAnswerApiResponseFactory;
	}

	@Nonnull
	protected ScreeningFlowVersionApiResponseFactory getScreeningFlowVersionApiResponseFactory() {
		return this.screeningFlowVersionApiResponseFactory;
	}

	@Nonnull
	protected ScreeningConfirmationPromptApiResponseFactory getScreeningConfirmationPromptApiResponseFactory() {
		return this.screeningConfirmationPromptApiResponseFactory;
	}

	@Nonnull
	protected CurrentContext getCurrentContext() {
		return this.currentContextProvider.get();
	}

	@Nonnull
	protected Logger getLogger() {
		return this.logger;
	}
}
