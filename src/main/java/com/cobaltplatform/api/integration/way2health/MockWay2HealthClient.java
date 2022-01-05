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

package com.cobaltplatform.api.integration.way2health;

import com.cobaltplatform.api.integration.way2health.model.entity.Incident;
import com.cobaltplatform.api.integration.way2health.model.request.GetIncidentRequest;
import com.cobaltplatform.api.integration.way2health.model.request.GetIncidentsRequest;
import com.cobaltplatform.api.integration.way2health.model.request.UpdateIncidentRequest;
import com.cobaltplatform.api.integration.way2health.model.request.UpdateIncidentsRequest;
import com.cobaltplatform.api.integration.way2health.model.response.ListResponse;
import com.cobaltplatform.api.integration.way2health.model.response.ObjectResponse;
import com.cobaltplatform.api.integration.way2health.model.response.PagedResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
public class MockWay2HealthClient implements Way2HealthClient {
	@Nonnull
	private final Gson gson;

	public MockWay2HealthClient() {
		this.gson = Way2HealthGsonSupport.sharedGson();
	}

	@Nonnull
	@Override
	public ObjectResponse<Incident> getIncident(@Nonnull GetIncidentRequest request) throws Way2HealthException {
		requireNonNull(request);
		return new ObjectResponse<>() {{
			setData(getIncidents(new GetIncidentsRequest()).getData().get(0));
			setRawResponseBody(getIncidents(new GetIncidentsRequest()).getRawResponseBody());
			setErrors(null);
		}};
	}

	@Nonnull
	@Override
	public PagedResponse<Incident> getIncidents(@Nonnull GetIncidentsRequest request) throws Way2HealthException {
		requireNonNull(request);

		try (FileReader fileReader = new FileReader(Path.of("src/test/resources/way2health-incidents-fetch-response.json").toFile())) {
			return getGson().fromJson(fileReader, new TypeToken<PagedResponse<Incident>>() {
			}.getType());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Nonnull
	@Override
	public PagedResponse<Incident> getIncidents(@Nonnull String pageLink) throws Way2HealthException {
		requireNonNull(pageLink);
		return getIncidents(new GetIncidentsRequest());
	}

	@Nonnull
	@Override
	public ObjectResponse<Incident> updateIncident(@Nonnull UpdateIncidentRequest request) throws Way2HealthException {
		requireNonNull(request);
		return new ObjectResponse<>() {{
			setData(getIncidents(new GetIncidentsRequest()).getData().get(0));
			setRawResponseBody(getIncidents(new GetIncidentsRequest()).getRawResponseBody());
			setErrors(null);
		}};
	}

	@Nonnull
	@Override
	public ListResponse<Incident> updateIncidents(@Nonnull UpdateIncidentsRequest request) throws Way2HealthException {
		requireNonNull(request);
		return new ListResponse<>() {{
			setData(getIncidents(new GetIncidentsRequest()).getData());
			setRawResponseBody(getIncidents(new GetIncidentsRequest()).getRawResponseBody());
			setErrors(null);
		}};
	}

	@Nonnull
	protected Gson getGson() {
		return gson;
	}
}