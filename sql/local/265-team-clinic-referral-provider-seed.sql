BEGIN;
SELECT _v.register_patch(
	'265-local-only-team-clinic-referral-provider-seed',
	ARRAY[
		'262-local-only-provider-booking-seed',
		'264-provider-institution-referrer'
	],
	NULL
);

-- Local/bootstrap-only. This provider-shaped TEAM Clinic profile delegates its
-- action to the existing COBALT -> COBALT_IC_SELF_REFERRAL institution
-- referrer. It deliberately has no native scheduling, contact, location, or
-- availability data. Real tenants receive their own reviewed fixture patch.
DO $$
DECLARE
	v_institution_id CONSTANT TEXT := 'COBALT';
	v_destination_institution_id CONSTANT TEXT := 'COBALT_IC_SELF_REFERRAL';
	v_referrer_url_name CONSTANT TEXT := 'team-clinic-pilot';
	v_provider_name CONSTANT TEXT := 'TEAM Clinic';
	v_provider_url_name CONSTANT TEXT := 'team-clinic';
	v_provider_id_seed CONSTANT UUID := 'c0ba1f00-0000-4000-8000-000000000001';
	v_referrer_id UUID;
	v_provider_id UUID;
	v_referrer_intake_screening_flow_id UUID;
	v_referrer_description TEXT;
	v_referrer_page_content TEXT;
BEGIN
	-- Fail loudly if this local-only patch is invoked without its optional
	-- bootstrap data. Registering an empty fixture would prevent a later retry.
	IF (
		SELECT COUNT(*)
		FROM institution_referrer
		WHERE from_institution_id=v_institution_id
		AND to_institution_id=v_destination_institution_id
		AND url_name=v_referrer_url_name
	) <> 1 THEN
		RAISE EXCEPTION 'Expected exactly one local institution referrer from "%" to "%" with URL name "%".',
			v_institution_id,
			v_destination_institution_id,
			v_referrer_url_name;
	END IF;

	SELECT
		institution_referrer_id,
		intake_screening_flow_id,
		description,
		page_content
	INTO
		v_referrer_id,
		v_referrer_intake_screening_flow_id,
		v_referrer_description,
		v_referrer_page_content
	FROM institution_referrer
	WHERE from_institution_id=v_institution_id
	AND to_institution_id=v_destination_institution_id
	AND url_name=v_referrer_url_name;

	IF v_referrer_intake_screening_flow_id IS NULL THEN
		RAISE EXCEPTION 'The local TEAM Clinic institution referrer intake screening flow is required.';
	END IF;

	IF NULLIF(BTRIM(v_referrer_page_content), '') IS NULL THEN
		RAISE EXCEPTION 'The local TEAM Clinic institution referrer page content is required.';
	END IF;

	IF NOT EXISTS (
		SELECT 1
		FROM support_role
		WHERE support_role_id='CLINICIAN'
	) THEN
		RAISE EXCEPTION 'The CLINICIAN support role is required for the local TEAM Clinic provider.';
	END IF;

	IF NOT EXISTS (
		SELECT 1
		FROM institution_feature_institution_referrer
		JOIN institution_feature
			ON institution_feature.institution_feature_id=institution_feature_institution_referrer.institution_feature_id
		WHERE institution_feature_institution_referrer.institution_referrer_id=v_referrer_id
		AND institution_feature.institution_id=v_institution_id
		AND institution_feature.feature_id='THERAPY'
	) THEN
		RAISE EXCEPTION 'The local TEAM Clinic institution referrer must be associated with the COBALT THERAPY feature.';
	END IF;

	-- Reconcile an existing association first, then an existing canonical URL,
	-- while retaining a stable UUID for clean database recreations.
	SELECT provider_institution_referrer.provider_id
	INTO v_provider_id
	FROM provider_institution_referrer
	JOIN provider
		ON provider.provider_id=provider_institution_referrer.provider_id
	WHERE provider_institution_referrer.institution_referrer_id=v_referrer_id
	AND provider.institution_id=v_institution_id
	ORDER BY provider.provider_id
	LIMIT 1;

	IF v_provider_id IS NULL THEN
		SELECT provider_id
		INTO v_provider_id
		FROM provider
		WHERE institution_id=v_institution_id
		AND LOWER(url_name)=LOWER(v_provider_url_name)
		ORDER BY provider_id
		LIMIT 1;
	END IF;

	IF v_provider_id IS NULL THEN
		v_provider_id := v_provider_id_seed;

		INSERT INTO provider (
			provider_id,
			institution_id,
			name,
			title,
			email_address,
			image_url,
			bio_url,
			locale,
			time_zone,
			acuity_calendar_id,
			bluejeans_user_id,
			entity,
			clinic,
			license,
			specialty,
			intake_assessment_id,
			active,
			scheduling_system_id,
			videoconference_platform_id,
			videoconference_url,
			epic_provider_id,
			epic_provider_id_type,
			epic_practitioner_fhir_id,
			epic_appointment_filter_id,
			system_affinity_id,
			url_name,
			bio,
			description,
			tags,
			phone_number,
			display_phone_number_only_for_booking,
			website_url,
			details_html,
			virtual_appointments_only
		) VALUES (
			v_provider_id,
			v_institution_id,
			v_provider_name,
			NULL,
			NULL,
			NULL,
			NULL,
			'en-US',
			'America/New_York',
			NULL,
			NULL,
			NULL,
			NULL,
			NULL,
			NULL,
			NULL,
			TRUE,
			NULL,
			NULL,
			NULL,
			NULL,
			NULL,
			NULL,
			'NONE',
			'COBALT',
			v_provider_url_name,
			NULL,
			v_referrer_description,
			NULL,
			NULL,
			FALSE,
			NULL,
			v_referrer_page_content,
			FALSE
		);
	ELSE
		UPDATE provider
		SET name=v_provider_name,
			title=NULL,
			email_address=NULL,
			image_url=NULL,
			bio_url=NULL,
			locale='en-US',
			time_zone='America/New_York',
			acuity_calendar_id=NULL,
			bluejeans_user_id=NULL,
			entity=NULL,
			clinic=NULL,
			license=NULL,
			specialty=NULL,
			intake_assessment_id=NULL,
			active=TRUE,
			scheduling_system_id=NULL,
			videoconference_platform_id=NULL,
			videoconference_url=NULL,
			epic_provider_id=NULL,
			epic_provider_id_type=NULL,
			epic_practitioner_fhir_id=NULL,
			epic_appointment_filter_id='NONE',
			system_affinity_id='COBALT',
			url_name=v_provider_url_name,
			bio=NULL,
			description=v_referrer_description,
			tags=NULL,
			phone_number=NULL,
			display_phone_number_only_for_booking=FALSE,
			website_url=NULL,
			details_html=v_referrer_page_content,
			virtual_appointments_only=FALSE
		WHERE provider_id=v_provider_id;
	END IF;

	INSERT INTO provider_support_role (
		provider_id,
		support_role_id
	) VALUES (
		v_provider_id,
		'CLINICIAN'
	)
	ON CONFLICT (provider_id, support_role_id) DO NOTHING;

	INSERT INTO provider_institution_referrer (
		provider_id,
		institution_referrer_id,
		appointment_modality_id
	) VALUES (
		v_provider_id,
		v_referrer_id,
		'IN_PERSON'
	)
	ON CONFLICT (provider_id) DO UPDATE
	SET institution_referrer_id=EXCLUDED.institution_referrer_id,
		appointment_modality_id=EXCLUDED.appointment_modality_id;

	IF NOT EXISTS (
		SELECT 1
		FROM provider
		JOIN provider_institution_referrer
			ON provider_institution_referrer.provider_id=provider.provider_id
		JOIN institution_referrer
			ON institution_referrer.institution_referrer_id=provider_institution_referrer.institution_referrer_id
		JOIN provider_support_role
			ON provider_support_role.provider_id=provider.provider_id
			AND provider_support_role.support_role_id='CLINICIAN'
		WHERE provider.provider_id=v_provider_id
		AND provider.institution_id=v_institution_id
		AND provider.name=v_provider_name
		AND provider.url_name=v_provider_url_name
		AND provider.active=TRUE
		AND provider.email_address IS NULL
		AND provider.image_url IS NULL
		AND provider.phone_number IS NULL
		AND provider.website_url IS NULL
		AND provider.scheduling_system_id IS NULL
		AND provider.acuity_calendar_id IS NULL
		AND provider.bluejeans_user_id IS NULL
		AND provider.intake_assessment_id IS NULL
		AND provider.epic_provider_id IS NULL
		AND provider.epic_practitioner_fhir_id IS NULL
		AND provider.videoconference_platform_id IS NULL
		AND provider.videoconference_url IS NULL
		AND provider.details_html=v_referrer_page_content
		AND provider_institution_referrer.institution_referrer_id=v_referrer_id
		AND provider_institution_referrer.appointment_modality_id='IN_PERSON'
		AND institution_referrer.intake_screening_flow_id=v_referrer_intake_screening_flow_id
	) THEN
		RAISE EXCEPTION 'Local TEAM Clinic referral-backed provider verification failed.';
	END IF;

	IF EXISTS (
		SELECT 1 FROM provider_appointment_type WHERE provider_id=v_provider_id
		UNION ALL
		SELECT 1 FROM provider_institution_location WHERE provider_id=v_provider_id
		UNION ALL
		SELECT 1 FROM provider_location WHERE provider_id=v_provider_id
		UNION ALL
		SELECT 1 FROM provider_clinic WHERE provider_id=v_provider_id
		UNION ALL
		SELECT 1 FROM provider_epic_department WHERE provider_id=v_provider_id
		UNION ALL
		SELECT 1 FROM logical_availability WHERE provider_id=v_provider_id
		UNION ALL
		SELECT 1 FROM provider_availability WHERE provider_id=v_provider_id
	) THEN
		RAISE EXCEPTION 'Local TEAM Clinic referral-backed provider must not have native booking or location data.';
	END IF;
END $$;

COMMIT;
