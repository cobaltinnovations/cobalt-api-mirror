BEGIN;
SELECT _v.register_patch('189-analytics', NULL, NULL);

INSERT INTO client_device_type VALUES ('WEB_CRAWLER', 'Web Crawler');
INSERT INTO client_device_type VALUES ('UNKNOWN', 'Unknown');

-- Cobalt native analytics event types
CREATE TABLE analytics_native_event_type (
	analytics_native_event_type_id TEXT PRIMARY KEY,
  description VARCHAR NOT NULL
);

-- The start of a web "session" (lifespan of a browser tab).
-- Fired when a new browser tab opens.
-- When the browser tab is closed, the session ends (there is no corresponding SESSION_ENDED event for this currently).
-- See https://developer.mozilla.org/en-US/docs/Web/API/Window/sessionStorage for details
-- In a native app, this would be when the app first launches
-- There is no additional data associated with this event type.
INSERT INTO analytics_native_event_type (analytics_native_event_type_id, description) VALUES ('SESSION_STARTED', 'Session Started');
-- When the user brings the browser tab into focus (unminimizes the window, switches into it from another tab)
-- See https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilitychange_event for details
-- In a native app, this would be fired when the app is brought to the foreground
-- There is no additional data associated with this event type.
INSERT INTO analytics_native_event_type (analytics_native_event_type_id, description) VALUES ('BROUGHT_TO_FOREGROUND', 'Brought to Foreground');
-- When the user brings the browser tab out of focus (minimizes the window, switches to another tab)
-- See https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilitychange_event for details
-- In a native app, this would be fired when the app is sent to the background
-- There is no additional data associated with this event type.
INSERT INTO analytics_native_event_type (analytics_native_event_type_id, description) VALUES ('SENT_TO_BACKGROUND', 'Sent to Background');
-- When an API call returns a status >= 400.
-- Additional data:
-- * statusCode (Integer, e.g. 422, 500, ...)
-- * responseBody (String)
-- * errorCode (String, if "errorCode" field is available from API response, e.g. "VALIDATION_FAILED")
INSERT INTO analytics_native_event_type (analytics_native_event_type_id, description) VALUES ('API_CALL_ERROR', 'API Call Error');
-- On the web, when the "sign-in" page is rendered.
-- There is no additional data associated with this event type.
INSERT INTO analytics_native_event_type (analytics_native_event_type_id, description) VALUES ('PAGE_VIEW_SIGN_IN', 'Page View (Sign In)');
-- On the web, when the "sign-in with email" page is rendered.
-- There is no additional data associated with this event type.
INSERT INTO analytics_native_event_type (analytics_native_event_type_id, description) VALUES ('PAGE_VIEW_SIGN_IN_EMAIL', 'Page View (Sign In - Email)');
-- On the web, when the "home" page is rendered (default landing screen after sign-in).
-- There is no additional data associated with this event type.
INSERT INTO analytics_native_event_type (analytics_native_event_type_id, description) VALUES ('PAGE_VIEW_HOME', 'Page View (Home)');
-- On the web, when a "topic center" page is rendered.
-- Additional data:
-- * topicCenterId (UUID)
INSERT INTO analytics_native_event_type (analytics_native_event_type_id, description) VALUES ('PAGE_VIEW_TOPIC_CENTER', 'Page View (Topic Center)');

-- Cobalt native analytics events
CREATE TABLE analytics_native_event (
  analytics_native_event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  analytics_native_event_type_id TEXT NOT NULL REFERENCES analytics_native_event_type,
  institution_id VARCHAR NOT NULL REFERENCES institution,
  client_device_id UUID NOT NULL REFERENCES client_device,
  account_id UUID,
  -- A session-specific identifier that follows the lifecycle defined by JS sessionStorage.
  -- For example, a session is created when a browser tab is first opened and destroyed once it's closed.
  -- Session storage will survive navigations/reloads/etc.
  -- See https://developer.mozilla.org/en-US/docs/Web/API/Window/sessionStorage for details
  -- In a native app, this would be generated and stored when the app first launches
  session_id UUID NOT NULL,
  -- The client-specified timestamp for this event
  timestamp TIMESTAMPTZ NOT NULL,
  -- The value of the browser's URL bar at the time this event was captured (window.location.href)
  url TEXT,
  -- Bag of data that corresponds to the event type.
  data JSONB NOT NULL DEFAULT '{}'::JSONB,
  -- If explicitly specified by client. Example for native app: iPhone13,2
  client_device_model TEXT,
  -- If explicitly specified by client. Example for native app: Apple
  client_device_brand TEXT,
  -- If explicitly specified by client.  Example for native app: iOS
  client_device_operating_system TEXT,
  -- The value of window.navigator.userAgent
  user_agent TEXT,
  -- Parsed from User-Agent
  user_agent_device_family TEXT,
  -- Parsed from User-Agent
  user_agent_browser_family TEXT,
  -- Parsed from User-Agent
  user_agent_browser_version TEXT,
  -- Parsed from User-Agent
  user_agent_operating_system_name TEXT,
  -- Parsed from User-Agent
  user_agent_operating_system_version TEXT,
  -- Provided by JS window.screen object on web
	screen_color_depth INTEGER,
	-- Provided by JS window.screen object on web
	screen_pixel_depth INTEGER,
	-- Provided by JS window.screen object on web
	screen_width NUMERIC(8,2),
	-- Provided by JS window.screen object on web
	screen_height NUMERIC(8,2),
	-- Provided by JS window.screen object on web
	screen_orientation TEXT,
	-- Provided by JS window object on web
	window_device_pixel_ratio INTEGER,
	-- Provided by JS window object on web
	window_width NUMERIC(8,2),
	-- Provided by JS window object on web
	window_height NUMERIC(8,2),
  created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER set_last_updated BEFORE INSERT OR UPDATE ON analytics_native_event FOR EACH ROW EXECUTE PROCEDURE set_last_updated();

COMMIT;