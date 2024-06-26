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

package com.cobaltplatform.api.messaging.push;

import com.cobaltplatform.api.http.DefaultHttpClient;
import com.cobaltplatform.api.http.HttpClient;
import com.cobaltplatform.api.http.HttpMethod;
import com.cobaltplatform.api.http.HttpRequest;
import com.cobaltplatform.api.http.HttpResponse;
import com.cobaltplatform.api.messaging.MessageSender;
import com.cobaltplatform.api.model.db.MessageType.MessageTypeId;
import com.cobaltplatform.api.model.db.MessageVendor.MessageVendorId;
import com.cobaltplatform.api.util.HandlebarsTemplater;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * @author Transmogrify LLC.
 */
@ThreadSafe
public class GoogleFcmPushMessageSender implements MessageSender<PushMessage> {
	@Nonnull
	private final HandlebarsTemplater handlebarsTemplater;
	@Nonnull
	private final String projectId;
	@Nonnull
	private final GoogleCredentials googleCredentials;
	@Nonnull
	private final HttpClient httpClient;
	@Nonnull
	private final Gson gson;
	@Nonnull
	private final Logger logger;

	public GoogleFcmPushMessageSender(@Nonnull String serviceAccountPrivateKeyJson) {
		// ByteArrayInputStream does not need to be closed
		this(new ByteArrayInputStream(serviceAccountPrivateKeyJson.getBytes(StandardCharsets.UTF_8)));
	}

	public GoogleFcmPushMessageSender(@Nonnull String serviceAccountPrivateKeyJson,
																		@Nullable HandlebarsTemplater handlebarsTemplater) {
		// ByteArrayInputStream does not need to be closed
		this(new ByteArrayInputStream(serviceAccountPrivateKeyJson.getBytes(StandardCharsets.UTF_8)), handlebarsTemplater);
	}

	public GoogleFcmPushMessageSender(@Nonnull InputStream serviceAccountPrivateKeyJsonInputStream) {
		this(serviceAccountPrivateKeyJsonInputStream, null);
	}

	public GoogleFcmPushMessageSender(@Nonnull InputStream serviceAccountPrivateKeyJsonInputStream,
																		@Nullable HandlebarsTemplater handlebarsTemplater) {
		requireNonNull(serviceAccountPrivateKeyJsonInputStream);

		this.logger = LoggerFactory.getLogger(getClass());

		try {
			String serviceAccountPrivateKeyJson = CharStreams.toString(new InputStreamReader(serviceAccountPrivateKeyJsonInputStream, StandardCharsets.UTF_8));

			// Confirm that this is well-formed JSON and extract the project ID
			Map<String, Object> jsonObject = new Gson().fromJson(serviceAccountPrivateKeyJson, new TypeToken<Map<String, Object>>() {
			}.getType());

			this.handlebarsTemplater = handlebarsTemplater == null ? new HandlebarsTemplater.Builder(Paths.get("messages/push")).build() : handlebarsTemplater;
			this.projectId = requireNonNull((String) jsonObject.get("project_id"));
			this.googleCredentials = acquireGoogleCredentials(serviceAccountPrivateKeyJson);
			this.gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			this.httpClient = new DefaultHttpClient("google-fcm-message-sender");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public String sendMessage(@Nonnull PushMessage pushMessage) {
		requireNonNull(pushMessage);

		logger.debug("Sending push message via FCM: {}", pushMessage);

		HttpRequest httpRequest = createHttpRequest(pushMessage);
		String messageIdentifier = null;

		try {
			long time = System.currentTimeMillis();

			HttpResponse httpResponse = getHttpClient().execute(httpRequest);
			String responseBody = httpResponse.getBody().isPresent() ? new String(httpResponse.getBody().get(), StandardCharsets.UTF_8) : null;
			String errorStatus = null;
			String errorMessage = null;

			if (httpResponse.getStatus() > 299) {
				if (responseBody != null) {
					try {
						FcmErrorBody fcmErrorBody = getGson().fromJson(responseBody, FcmErrorBody.class);

						if (fcmErrorBody.getError() != null) {
							// This response means the client device is now inactive (token expired/uninstalled/etc.)
							// and should be removed from list of push-notifiable devices
							//
							// Example:
							//
							//{
							//  "error":{
							//    "code":404,
							//    "message":"Requested entity was not found.",
							//    "status":"NOT_FOUND",
							//    "details":[
							//      {
							//        "@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError",
							//        "errorCode":"UNREGISTERED"
							//      }
							//    ]
							//  }
							//}

							errorStatus = trimToNull(fcmErrorBody.getError().getStatus());
							errorMessage = trimToNull(fcmErrorBody.getError().getMessage());
						}
					} catch (Exception ignored) {
						// Can't parse JSON - not a big deal
					}
				}

				if (httpResponse.getStatus() == 404 || ("NOT_FOUND".equals(errorStatus) || "INVALID_ARGUMENT".equals(errorStatus)))
					throw new PushMessageInvalidDeviceException(format("FCM says push device with token '%s' is invalid (might have been refreshed or uninstalled).  Message was '%s'",
							pushMessage.getPushToken(), errorMessage), pushMessage);

				throw new PushMessageException(format("FCM says push device with push token '%s' is invalid (might have been refreshed or uninstalled)",
						pushMessage.getPushToken()), pushMessage);
			}

			FcmSuccessBody fcmSuccessBody = getGson().fromJson(responseBody, FcmSuccessBody.class);
			messageIdentifier = trimToNull(fcmSuccessBody.getName());

			// Should never occur
			if (messageIdentifier == null)
				throw new PushMessageException(format("FCM Push message appears to have been sent to device with push token '%s', but unable to extract an identifier from the response body: %s",
						pushMessage.getPushToken(), responseBody), pushMessage);

			getLogger().info("Successfully sent push notification in {} ms.", System.currentTimeMillis() - time);

			return messageIdentifier;
		} catch (IOException e) {
			throw new UncheckedIOException(format("Unable to send push notification %s", pushMessage), e);
		}
	}

	@Nonnull
	protected HttpRequest createHttpRequest(@Nonnull PushMessage pushMessage) {
		requireNonNull(pushMessage);

		Map<String, Object> messageContext = new HashMap<>(pushMessage.getMessageContext());
		String title = getHandlebarsTemplater().mergeTemplate(pushMessage.getMessageTemplate().name(), "title", pushMessage.getLocale(), messageContext).get();
		String body = getHandlebarsTemplater().mergeTemplate(pushMessage.getMessageTemplate().name(), "body", pushMessage.getLocale(), messageContext).get();

		// Relevant docs:
		// https://firebase.google.com/docs/cloud-messaging/js/first-message#http_post_request
		// https://firebase.google.com/docs/cloud-messaging/concept-options#data_messages
		//
		// Example:
		//
		// {
		//  "message":{
		//    "token":"bk3RNwTe3H0:CI2k_HHwgIpoDKCIZvvDMExUdFQ3P1...",
		//    "notification":{
		//      "body" : "This week's edition is now available.",
		//      "title" : "NewsMagazine.com",
		//    },
		//    "data" : {
		//      "volume" : "3.21.15",
		//      "contents" : "http://www.news-magazine.com/world-week/21659772"
		//    },
		//    "android":{
		//      "priority":"normal"
		//    },
		//    "apns":{
		//      "headers":{
		//        "apns-priority":"5"
		//      }
		//    },
		//    "webpush": {
		//      "headers": {
		//        "Urgency": "high"
		//      }
		//    }
		//  }
		//}
		Map<String, Object> notificationJson = new HashMap<>();

		if (title != null)
			notificationJson.put("title", title);

		if (body != null)
			notificationJson.put("body", body);

		Map<String, Object> dataJson = new HashMap<>(pushMessage.getMetadata());

		Map<String, Object> messageJson = new HashMap<>();
		messageJson.put("token", pushMessage.getPushToken());

		if (notificationJson.size() > 0)
			messageJson.put("notification", notificationJson);

		if (dataJson.size() > 0)
			messageJson.put("data", dataJson);

		Map<String, Object> bodyJson = new HashMap<>();
		bodyJson.put("message", messageJson);

		return new HttpRequest.Builder(HttpMethod.POST, format("https://fcm.googleapis.com/v1/projects/%s/messages:send", getProjectId()))
				.headers(new HashMap<String, Object>() {{
					put("Authorization", format("Bearer %s", acquireFcmAccessToken()));
				}})
				.body(getGson().toJson(bodyJson))
				.contentType("application/json")
				.build();
	}

	@Nonnull
	@Override
	public MessageVendorId getMessageVendorId() {
		return MessageVendorId.GOOGLE_FCM;
	}

	@Nonnull
	@Override
	public MessageTypeId getMessageTypeId() {
		return MessageTypeId.PUSH;
	}

	@Nonnull
	protected GoogleCredentials acquireGoogleCredentials(@Nonnull String serviceAccountPrivateKeyJson) {
		requireNonNull(serviceAccountPrivateKeyJson);

		try (InputStream inputStream = new ByteArrayInputStream(serviceAccountPrivateKeyJson.getBytes(StandardCharsets.UTF_8))) {
			// return ServiceAccountCredentials.fromStream(inputStream);
			return GoogleCredentials
					.fromStream(inputStream)
					.createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Nonnull
	protected String acquireFcmAccessToken() {
		try {
			long time = System.currentTimeMillis();

			getGoogleCredentials().refreshIfExpired();
			String accessToken = getGoogleCredentials().getAccessToken().getTokenValue();

			getLogger().trace("Acquired FCM access token in {} ms.", System.currentTimeMillis() - time);

			return accessToken;
		} catch (IOException e) {
			throw new UncheckedIOException("Unable to acquire FCM access token", e);
		}
	}

	@Nonnull
	protected HandlebarsTemplater getHandlebarsTemplater() {
		return this.handlebarsTemplater;
	}

	@Nonnull
	public String getProjectId() {
		return this.projectId;
	}

	@Nonnull
	protected GoogleCredentials getGoogleCredentials() {
		return this.googleCredentials;
	}

	@Nonnull
	protected HttpClient getHttpClient() {
		return this.httpClient;
	}

	@Nonnull
	protected Gson getGson() {
		return this.gson;
	}

	@Nonnull
	protected Logger getLogger() {
		return this.logger;
	}

	@NotThreadSafe
	protected static class FcmSuccessBody {
		@Nullable
		private String name;

		@Nullable
		public String getName() {
			return this.name;
		}

		public void setName(@Nullable String name) {
			this.name = name;
		}
	}

	// Example:
	//
	//{
	//  "error":{
	//    "code":404,
	//    "message":"Requested entity was not found.",
	//    "status":"NOT_FOUND",
	//    "details":[
	//      {
	//        "@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError",
	//        "errorCode":"UNREGISTERED"
	//      }
	//    ]
	//  }
	//}
	@NotThreadSafe
	protected static class FcmErrorBody {
		@Nullable
		private Error error;

		@NotThreadSafe
		protected static class Error {
			@Nullable
			private Integer code;
			@Nullable
			private String message;
			@Nullable
			private String status;
			@Nullable
			private List<Detail> details;

			@Nullable
			public Integer getCode() {
				return code;
			}

			public void setCode(@Nullable Integer code) {
				this.code = code;
			}

			@Nullable
			public String getMessage() {
				return message;
			}

			public void setMessage(@Nullable String message) {
				this.message = message;
			}

			@Nullable
			public String getStatus() {
				return status;
			}

			public void setStatus(@Nullable String status) {
				this.status = status;
			}

			@Nullable
			public List<Detail> getDetails() {
				return details;
			}

			public void setDetails(@Nullable List<Detail> details) {
				this.details = details;
			}
		}

		@NotThreadSafe
		protected static class Detail {
			@Nullable
			@SerializedName("@type")
			private String type;
			@Nullable
			private String errorCode;

			@Nullable
			public String getType() {
				return type;
			}

			public void setType(@Nullable String type) {
				this.type = type;
			}

			@Nullable
			public String getErrorCode() {
				return errorCode;
			}

			public void setErrorCode(@Nullable String errorCode) {
				this.errorCode = errorCode;
			}
		}

		@Nullable
		public Error getError() {
			return error;
		}

		public void setError(@Nullable Error error) {
			this.error = error;
		}
	}
}
