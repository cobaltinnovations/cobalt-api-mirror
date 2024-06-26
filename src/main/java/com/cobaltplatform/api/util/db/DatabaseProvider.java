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

package com.cobaltplatform.api.util.db;

import com.pyranid.Database;
import com.soklet.web.request.RequestContext;
import com.soklet.web.routing.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@ThreadSafe
@Singleton
public class DatabaseProvider {
	@Nonnull
	private final Database writableMasterDatabase;
	@Nonnull
	private final Database readReplicaDatabase;
	@Nonnull
	private final Logger logger;

	@Inject
	public DatabaseProvider(@Nonnull @WritableMaster Database writableMasterDatabase,
													@Nonnull @ReadReplica Database readReplicaDatabase) {
		requireNonNull(writableMasterDatabase);
		requireNonNull(readReplicaDatabase);

		this.writableMasterDatabase = writableMasterDatabase;
		this.readReplicaDatabase = readReplicaDatabase;
		this.logger = LoggerFactory.getLogger(getClass());
	}

	/**
	 * Provides a context-appropriate Database instance (e.g. read-replica for reporting API calls).
	 * <p>
	 * This is almost always what you want instead of explicit references to a read-replica or writable-master.
	 * <p>
	 * We can then think of concepts like "use read-replica" at the API call level by marking resource methods as @ReadReplica
	 * as opposed to specific Service methods "hardcoding" whether they talk to a read-replica or writable-master.
	 * This permits building Service methods in a generic way, usable in both read-only vs. writable contexts
	 * and they "just work" - the appropriate database/transaction is already selected upstream.
	 * <p>
	 * Suppose we did not take this approach and instead have explicit references to read-replica and writable-master
	 * databases in our Service methods.  A scenario like this could occur:
	 * <ul>
	 * <li>API call comes in, we start a transaction against writable master</li>
	 * <li>Service method A, which directly references writable master, creates a new record in the database</li>
	 * <li>Service method B, which directly references read-replica, attempts to pull data inserted by service method A</li>
	 * <li>Potentially subtle errors occur because service method B is not participating in the transaction and cannot see
	 *   the data inserted by Service method A (it is pointing to a different database!)</li>
	 * </ul>
	 */
	@Nonnull
	public Database get() {
		Route route;

		// Not in request context?  Fall back to writable master
		try {
			route = RequestContext.get().route().orElse(null);
		} catch (IllegalStateException e) {
			getLogger().trace("Not in request context, using writable master database");
			return getWritableMasterDatabase();
		}

		// Special case: are we serving a static file and sidestepping normal routing?  Fall back to read replica
		RequestContext requestContext = RequestContext.get();
		HttpServletRequest httpServletRequest = requestContext.httpServletRequest();
		String requestUri = httpServletRequest.getRequestURI();
		boolean staticFile = requestUri.startsWith("/static/");

		if (staticFile) {
			getLogger().trace("Static file requested at {} {}, using read replica database", httpServletRequest.getMethod(), requestUri);
			return getReadReplicaDatabase();
		}

		// Not a static file and we don't know our request's route (e.g. strange error flow)?  Fall back to writable master
		if (route == null) {
			getLogger().warn("Unable to determine route, using writable master database");
			return getWritableMasterDatabase();
		}

		// If request header indicates this is an "autorefresh" polling call to keep the UI up-to-date, use the read replica
		if (Objects.equals(httpServletRequest.getHeader("X-Cobalt-Autorefresh"), "true")) {
			getLogger().trace("Route {} is being invoked as an 'autorefresh' call, use read replica", route);
			return getReadReplicaDatabase();
		}

		// See if a preference was indicated on the resource method to use writable master or read replica
		boolean routePrefersWritableMaster = route.resourceMethod().getAnnotation(WritableMaster.class) != null;
		boolean routePrefersReadReplica = route.resourceMethod().getAnnotation(ReadReplica.class) != null;

		// If illegally configured, error out right away
		if (routePrefersWritableMaster && routePrefersReadReplica)
			throw new IllegalStateException(format("Resource method %s is annotated with both %s and %s - you cannot specify both.",
					WritableMaster.class.getSimpleName(), ReadReplica.class.getSimpleName()));

		if (routePrefersWritableMaster) {
			getLogger().trace("Route {} is marked as preferring writable master database, using that", route);
			return getWritableMasterDatabase();
		}

		if (routePrefersReadReplica) {
			getLogger().trace("Route {} is marked as preferring read replica database, using that", route);
			return getReadReplicaDatabase();
		}

		// Default behavior? Fall back to writable master
		getLogger().trace("No explicit behavior is specified for {}, so using writable master database", route);
		return getWritableMasterDatabase();
	}

	@Nonnull
	public Database getWritableMasterDatabase() {
		return this.writableMasterDatabase;
	}

	@Nonnull
	public Database getReadReplicaDatabase() {
		return this.readReplicaDatabase;
	}

	@Nonnull
	protected Logger getLogger() {
		return this.logger;
	}
}