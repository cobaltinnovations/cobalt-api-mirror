BEGIN;
SELECT _v.register_patch('185-foreign-tables', NULL, NULL);

CREATE FOREIGN TABLE remote_content (
        content_id uuid NOT NULL,
	content_type_id varchar NOT NULL,
	title varchar NOT NULL,
	url varchar NULL,
	date_created timestamptz NOT NULL,
	description varchar NULL,
	author varchar NULL,
	created timestamptz DEFAULT now() NOT NULL,
	last_updated timestamptz DEFAULT now() NOT NULL,
	owner_institution_id varchar NOT NULL,
	deleted_flag bool DEFAULT false NOT NULL,
	duration_in_minutes int4 NULL,
	en_search_vector tsvector NOT NULL,
	never_embed bool DEFAULT false NOT NULL,
	shared_flag bool DEFAULT false NOT NULL,
	search_terms varchar NULL,
	publish_start_date timestamptz NOT NULL,
	publish_end_date timestamptz NULL,
	publish_recurring bool DEFAULT false NOT NULL,
	published bool DEFAULT false NOT NULL,
	file_upload_id uuid NULL,
	image_file_upload_id uuid NULL,
	remote_data_flag BOOLEAN NOT NULL)
SERVER cobalt_remote
OPTIONS (schema_name 'cobalt', table_name 'content');

CREATE FOREIGN TABLE remote_tag_content (
	tag_content_id UUID NOT NULL,
	tag_id varchar NOT NULL,
	content_id uuid NOT NULL,
	created timestamptz DEFAULT now() NOT NULL,
	last_updated timestamptz DEFAULT now() NOT NULL,
	remote_data_flag BOOLEAN NOT NULL)
SERVER cobalt_remote
OPTIONS (schema_name 'cobalt', table_name 'tag_content');

CREATE FOREIGN TABLE remote_file_upload (
	file_upload_id uuid NOT NULL,
	account_id uuid NOT NULL,
	url text NOT NULL,
	storage_key text NOT NULL,
	filename text NOT NULL,
	content_type text NOT NULL,
	created timestamptz DEFAULT now() NOT NULL,
	last_updated timestamptz DEFAULT now() NOT NULL,
	file_upload_type_id text DEFAULT 'UNSPECIFIED'::text NOT NULL,
	filesize numeric NULL,
	remote_data_flag BOOLEAN NOT NULL)
SERVER cobalt_remote
OPTIONS (schema_name 'cobalt', table_name 'file_upload');

CREATE FOREIGN TABLE remote_institution_content (
	institution_content_id uuid NOT NULL,
	institution_id varchar NOT NULL,
	content_id uuid NOT NULL,
	created timestamptz DEFAULT now() NOT NULL,
	last_updated timestamptz DEFAULT now() NOT NULL,
	remote_data_flag BOOLEAN NOT NULL)
SERVER cobalt_remote
OPTIONS (schema_name 'cobalt', table_name 'institution_content');

CREATE FOREIGN TABLE remote_tag (
	tag_id varchar NOT NULL,
	name varchar NOT NULL,
	url_name varchar NOT NULL,
	description varchar NOT NULL,
	en_search_vector tsvector NULL,
	tag_group_id varchar NOT NULL,
	created timestamptz DEFAULT now() NOT NULL,
	last_updated timestamptz DEFAULT now() NOT NULL,
	remote_data_flag BOOLEAN NOT NULL)
SERVER cobalt_remote
OPTIONS (schema_name 'cobalt', table_name 'tag');

CREATE FOREIGN TABLE remote_institution (
	institution_id varchar NOT NULL,
	name varchar NOT NULL,
	created timestamptz DEFAULT now() NOT NULL,
	last_updated timestamptz DEFAULT now() NOT NULL,
	group_session_system_id varchar DEFAULT 'NATIVE'::character varying NOT NULL,
	time_zone varchar DEFAULT 'America/New_York'::character varying NOT NULL,
	locale varchar DEFAULT 'en-US'::character varying NOT NULL,
	require_consent_form bool DEFAULT false NOT NULL,
	support_enabled bool DEFAULT false NOT NULL,
	calendar_description varchar NULL,
	sso_enabled bool DEFAULT false NOT NULL,
	anonymous_enabled bool DEFAULT true NOT NULL,
	email_enabled bool DEFAULT false NOT NULL,
	access_token_expiration_in_minutes int8 DEFAULT 43200 NOT NULL,
	access_token_short_expiration_in_minutes int8 DEFAULT 43200 NOT NULL,
	anon_access_token_expiration_in_minutes int8 DEFAULT 525600 NOT NULL,
	anon_access_token_short_expiration_in_minutes int8 DEFAULT 525600 NOT NULL,
	metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
	provider_triage_screening_flow_id uuid NULL,
	content_screening_flow_id uuid NULL,
	email_signup_enabled bool DEFAULT false NOT NULL,
	support_email_address text DEFAULT 'support@cobaltinnovations.org'::text NOT NULL,
	group_sessions_screening_flow_id uuid NULL,
	recommend_group_session_requests bool DEFAULT false NOT NULL,
	immediate_access_enabled bool DEFAULT true NOT NULL,
	contact_us_enabled bool DEFAULT true NOT NULL,
	ga4_measurement_id text NULL,
	integrated_care_enabled bool DEFAULT false NOT NULL,
	epic_client_id text NULL,
	epic_user_id text NULL,
	epic_user_id_type text NULL,
	epic_username text NULL,
	epic_password text NULL,
	epic_base_url text NULL,
	mychart_client_id text NULL,
	mychart_scope text NULL,
	mychart_response_type text NULL,
	epic_token_url text NULL,
	epic_authorize_url text NULL,
	mychart_callback_url text NULL,
	integrated_care_screening_flow_id uuid NULL,
	mychart_aud text NULL,
	epic_backend_service_auth_type_id text DEFAULT 'UNSUPPORTED'::text NOT NULL,
	user_submitted_content_enabled bool DEFAULT false NOT NULL,
	user_submitted_group_session_enabled bool DEFAULT false NOT NULL,
	user_submitted_group_session_request_enabled bool DEFAULT false NOT NULL,
	microsoft_tenant_id text NULL,
	microsoft_client_id text NULL,
	recommended_content_enabled bool DEFAULT false NOT NULL,
	group_session_requests_enabled bool DEFAULT false NOT NULL,
	group_session_reservation_default_followup_time_of_day time DEFAULT '11:30:00'::time without time zone NOT NULL,
	group_session_reservation_default_followup_day_offset int4 DEFAULT 1 NOT NULL,
	appointment_reservation_default_reminder_time_of_day time DEFAULT '12:00:00'::time without time zone NOT NULL,
	appointment_reservation_default_reminder_day_offset int4 DEFAULT 1 NOT NULL,
	group_session_reservation_default_reminder_minutes_offset int4 DEFAULT 60 NOT NULL,
	feature_screening_flow_id uuid NULL,
	features_enabled bool DEFAULT false NOT NULL,
	mychart_name varchar DEFAULT 'MyChart'::character varying NOT NULL,
	integrated_care_sent_resources_followup_day_offset int4 DEFAULT 5 NULL,
	integrated_care_sent_resources_followup_week_offset int4 DEFAULT 4 NULL,
	default_from_email_address text NULL,
	integrated_care_phone_number text NULL,
	integrated_care_availability_description text NULL,
	integrated_care_outreach_followup_day_offset int4 DEFAULT 4 NULL,
	integrated_care_program_name varchar NULL,
	integrated_care_primary_care_name varchar NULL,
	mychart_default_url text NULL,
	ga4_patient_measurement_id text NULL,
	ga4_staff_measurement_id text NULL,
	clinical_support_phone_number text NULL,
	anonymous_account_expiration_strategy_id text DEFAULT 'DEFAULT'::text NOT NULL,
	epic_patient_mrn_type_name text DEFAULT 'MRN'::text NOT NULL,
	epic_patient_unique_id_type text NULL,
	epic_patient_unique_id_system text NULL,
	epic_patient_mrn_system text NULL,
	integrated_care_clinical_report_disclaimer text NULL,
	integrated_care_intake_screening_flow_id uuid NULL,
	group_session_default_intake_screening_flow_id uuid NULL,
	epic_fhir_appointment_find_cache_expiration_in_seconds int4 DEFAULT 60 NOT NULL,
	resource_groups_title text NULL,
	resource_groups_description text NULL,
	epic_fhir_enabled bool DEFAULT false NOT NULL,
	faq_enabled bool DEFAULT false NOT NULL,
	external_contact_us_url text NULL,
	mychart_instructions_url text NULL,
	featured_topic_center_id uuid NULL,
	tech_support_phone_number text NULL,
	privacy_policy_url text NULL,
	secure_filesharing_platform_name text NULL,
	secure_filesharing_platform_url text NULL,
	google_reporting_service_account_private_key varchar NULL,
	google_ga4_property_id varchar NULL,
	google_bigquery_resource_id varchar NULL,
	google_bigquery_sync_enabled bool DEFAULT false NOT NULL,
	google_bigquery_sync_starts_at date NULL,
	mixpanel_project_id int4 NULL,
	mixpanel_service_account_username varchar NULL,
	mixpanel_service_account_secret varchar NULL,
	mixpanel_sync_enabled bool DEFAULT false NOT NULL,
	mixpanel_sync_starts_at date NULL,
	sharing_content bool DEFAULT true NOT NULL,
	microsoft_teams_enabled bool DEFAULT false NOT NULL,
	microsoft_teams_tenant_id text NULL,
	microsoft_teams_client_id text NULL,
	microsoft_teams_user_id text NULL,
	featured_secondary_topic_center_id uuid NULL,
	tableau_enabled bool DEFAULT false NOT NULL,
	tableau_client_id text NULL,
	tableau_api_base_url text NULL,
	tableau_content_url text NULL,
	tableau_email_address text NULL,
	epic_patient_mrn_type_alternate_name text NULL,
	epic_patient_encounter_csn_system text NULL,
	integrated_care_order_import_bucket_name text NULL,
	google_fcm_push_notifications_enabled bool DEFAULT false NOT NULL,
	integrated_care_safety_planning_manager_account_id uuid NULL,
	call_messages_enabled bool DEFAULT false NOT NULL,
	sms_messages_enabled bool DEFAULT false NOT NULL,
	twilio_account_sid text NULL,
	twilio_from_number text NULL,
	tableau_view_name text NULL,
	tableau_report_name text NULL,
	integrated_care_order_import_start_time_window time NULL,
	integrated_care_order_import_end_time_window time NULL,
	epic_provider_slot_booking_sync_enabled bool DEFAULT false NOT NULL,
	epic_provider_slot_booking_sync_contact_id_type text NULL,
	epic_provider_slot_booking_sync_department_id_type text NULL,
	epic_provider_slot_booking_sync_visit_type_id_type text NULL,
	appointment_feedback_survey_enabled BOOLEAN NOT NULL DEFAULT FALSE,
	appointment_feedback_survey_url TEXT,
	appointment_feedback_survey_duration_description TEXT,
	appointment_feedback_survey_delay_in_minutes INTEGER NOT NULL DEFAULT 1440,
	remote_data_flag BOOLEAN NOT NULL,
	sync_data BOOLEAN)
SERVER cobalt_remote
OPTIONS (schema_name 'cobalt', table_name 'institution');

COMMIT;