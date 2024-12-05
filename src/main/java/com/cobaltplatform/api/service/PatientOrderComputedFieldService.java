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

import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.lokalized.Strings;
import com.pyranid.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author Transmogrify, LLC.
 */
@Singleton
@ThreadSafe
public class PatientOrderComputedFieldService {
	@Nonnull
	private final Provider<PatientOrderService> patientOrderServiceProvider;
	@Nonnull
	private final DatabaseProvider databaseProvider;
	@Nonnull
	private final Strings strings;
	@Nonnull
	private final Logger logger;

	@Inject
	public PatientOrderComputedFieldService(@Nonnull Provider<PatientOrderService> patientOrderServiceProvider,
																					@Nonnull DatabaseProvider databaseProvider,
																					@Nonnull Strings strings) {
		requireNonNull(patientOrderServiceProvider);
		requireNonNull(databaseProvider);
		requireNonNull(strings);

		this.patientOrderServiceProvider = patientOrderServiceProvider;
		this.databaseProvider = databaseProvider;
		this.strings = strings;
		this.logger = LoggerFactory.getLogger(getClass());
	}

	public void refreshAllPatientOrderComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// TODO: figure out dependency order and invoke all calculation methods

		// Queries to convert to computed:
		//
		// XXX left outer join poo_query pooq ON poq.patient_order_id = pooq.patient_order_id
		// XXX left outer join poomax_query poomaxq ON poq.patient_order_id = poomaxq.patient_order_id
		// XXX left outer join reason_for_referral_query rfrq on poq.patient_order_id=rfrq.patient_order_id
		// XXX left outer join smg_query smgq ON poq.patient_order_id = smgq.patient_order_id
		// XXX left outer join smgmax_query smgmaxq ON poq.patient_order_id = smgmaxq.patient_order_id
		// XXX left outer join next_resource_check_in_scheduled_message_group_query nrcismgq on poq.patient_order_id=nrcismgq.patient_order_id
		// XXX left outer join next_appt_query naq on poq.patient_order_id=naq.patient_order_id
		// XXX left outer join recent_voicemail_task_query rvtq on poq.patient_order_id=rvtq.patient_order_id
		// XXX left outer join next_scheduled_outreach_query nsoq ON poq.patient_order_id=nsoq.patient_order_id
		// XXX left outer join most_recent_message_delivered_query mrmdq on poq.patient_order_id=mrmdq.patient_order_id
		// XXX left outer join ss_query ssq ON poq.patient_order_id = ssq.patient_order_id
		// XXX left outer join ss_intake_query ssiq ON poq.patient_order_id = ssiq.patient_order_id
		// (not computing this one) left join permitted_regions_query prq ON poq.institution_id = prq.institution_id
		// XXX left outer join recent_scheduled_screening_query rssq ON poq.patient_order_id = rssq.patient_order_id
		// XXX left outer join recent_po_query rpq ON poq.patient_order_id = rpq.patient_order_id

		// GraphViz representation of dependencies for computed fields
		//
		// dot -Gstart=5 -Tsvg -Kneato test.dot > test.svg
		//
		//		digraph {
		//			overlap=false; // scalexy, compress, ...
		//			sep="0.1"; // 0.1, +1
		//			splines = true;
		//
		//			outreach_count -> pooq
		//			total_outreach_count -> pooq
		//			total_outreach_count -> smgq
		//			outreach_followup_needed -> pooq
		//			outreach_followup_needed -> ssq
		//			outreach_followup_needed -> rssq
		//			outreach_followup_needed -> smgq
		//			outreach_followup_needed -> poomaxq
		//			outreach_followup_needed -> smgmaxq
		//			most_recent_outreach_date_time -> poomaxq
		//			most_recent_total_outreach_date_time -> poomaxq
		//			most_recent_total_outreach_date_time -> smgmaxq
		//			last_contacted_at -> mrmdq
		//			last_contacted_at -> poomaxq
		//			last_contacted_at -> ssq
		//			next_contact_type_id -> ssq
		//			next_contact_type_id -> rssq
		//			next_contact_type_id -> nsoq
		//			next_contact_type_id -> poomaxq
		//			next_contact_type_id -> smgmaxq
		//			next_contact_type_id -> ssiq
		//			next_contact_type_id -> nrcismgq
		//			next_contact_scheduled_at -> ssq
		//			next_contact_scheduled_at -> rssq
		//			next_contact_scheduled_at -> nsoq
		//			next_contact_scheduled_at -> poomaxq
		//			next_contact_scheduled_at -> smgmaxq
		//			next_contact_scheduled_at -> ssiq
		//			next_contact_scheduled_at -> nrcismgq
		//			reason_for_referral -> rfrq
		//			scheduled_message_group_delivered_count -> smgq
		//			most_recent_delivered_scheduled_message_group_date_time -> smgmaxq
		//			appointment_start_time -> naq
		//			provider_id -> naq
		//			provider_name -> naq
		//			appointment_id -> naq
		//			appointment_scheduled -> naq
		//			appointment_scheduled_by_patient -> naq
		//			most_recent_patient_order_voicemail_task_id -> rvtq
		//			most_recent_patient_order_voicemail_task_completed -> rvtq
		//			next_scheduled_outreach_id -> nsoq
		//			next_scheduled_outreach_scheduled_at_date_time -> nsoq
		//			next_scheduled_outreach_type_id -> nsoq
		//			next_scheduled_outreach_reason_id -> nsoq
		//			most_recent_message_delivered_at -> mrmdq
		//			most_recent_screening_session_id -> ssq
		//			most_recent_screening_session_created_at -> ssq
		//			most_recent_screening_session_created_by_account_id -> ssq
		//			most_recent_screening_session_created_by_account_role_id -> ssq
		//			most_recent_screening_session_created_by_account_first_name -> ssq
		//			most_recent_screening_session_created_by_account_last_name -> ssq
		//			most_recent_screening_session_completed -> ssq
		//			most_recent_screening_session_completed_at -> ssq
		//			patient_order_screening_status_id -> ssq
		//			patient_order_screening_status_id -> rssq
		//			patient_order_screening_status_description -> ssq
		//			patient_order_screening_status_description -> rssq
		//			most_recent_screening_session_by_patient -> ssq
		//			most_recent_screening_session_appears_abandoned -> ssq
		//			patient_order_encounter_documentation_status_id -> ssq
		//			most_recent_intake_and_clinical_screenings_satisfied -> ssiq
		//			most_recent_intake_and_clinical_screenings_satisfied -> ssq
		//			most_recent_intake_screening_session_id -> ssiq
		//			most_recent_intake_screening_session_created_at -> ssiq
		//			most_recent_intake_screening_session_created_by_account_id -> ssiq
		//			most_recent_intake_screening_session_created_by_account_role_id -> ssiq
		//			most_recent_intake_screening_session_created_by_account_fn -> ssiq
		//			most_recent_intake_screening_session_created_by_account_ln -> ssiq
		//			most_recent_intake_screening_session_completed -> ssiq
		//			most_recent_intake_screening_session_completed_at -> ssiq
		//			patient_order_intake_screening_status_id -> ssiq
		//			patient_order_intake_screening_status_description -> ssiq
		//			most_recent_intake_screening_session_by_patient -> ssiq
		//			most_recent_intake_screening_session_appears_abandoned -> ssiq
		//			patient_order_scheduled_screening_id -> rssq
		//			patient_order_scheduled_screening_scheduled_date_time -> rssq
		//			patient_order_scheduled_screening_calendar_url -> rssq
		//			most_recent_episode_closed_at -> rpq
		//			most_recent_episode_closed_within_date_threshold -> rpq
		//		}

		// poo_query (pooq)
		// poomax_query (poomaxq)
		refreshPatientOrderOutreachCountComputedFields(patientOrderId);

		// reason_for_referral_query (rfrq)
		refreshPatientOrderReasonForReferralCalculatedField(patientOrderId);

		// smg_query (smgq)
		// smgmax_query (smgmaxq)
		// next_resource_check_in_scheduled_message_group_query (nrcismgq)
		refreshPatientOrderScheduledMessageComputedFields(patientOrderId);

		// next_appt_query (naq)
		refreshPatientOrderAppointmentComputedFields(patientOrderId);

		// recent_voicemail_task_query (rvtq)
		refreshPatientOrderVoicemailTaskComputedFields(patientOrderId);

		// next_scheduled_outreach_query (nsoq)
		refreshPatientOrderScheduledOutreachComputedFields(patientOrderId);

		// most_recent_message_delivered_query (mrmdq)
		refreshPatientOrderMostRecentMessageDeliveredComputedFields(patientOrderId);

		// ss_query (ssq)
		// ss_intake_query (ssiq)
		refreshPatientOrderScreeningSessionComputedFields(patientOrderId);

		// recent_scheduled_screening_query (rssq)
		refreshPatientOrderScheduledScreeningComputedFields(patientOrderId);

		// recent_po_query (rpq)
		refreshPatientOrderRecentPatientOrderComputedFields(patientOrderId);
	}

	public void refreshPatientOrderOutreachCountComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// poo_query (pooq)

		// Use in calculated fields:
		// coalesce(pooq.outreach_count, 0) AS outreach_count
		// coalesce(pooq.outreach_count, 0) + coalesce(smgq.scheduled_message_group_delivered_count, 0) as total_outreach_count
		// (...) outreach_followup_needed

		// Computed fields depend on:
		// ss_query (ssq)
		// recent_scheduled_screening_query (rssq)
		// poo_query (pooq)
		// smg_query (smgq)
		// smgmax_query (smgmaxq)

		/*

			poo_query AS (
			-- Count up the patient outreach attempts for each patient order
			select
					poq.patient_order_id,
					count(poo.*) AS outreach_count
			from
					patient_order_outreach poo,
					patient_order poq
			where
					poq.patient_order_id = poo.patient_order_id
					AND poo.deleted=FALSE
			group by
					poq.patient_order_id
			),

		 */

		// Query:
		// poomax_query (poomaxq)

		// Use in calculated fields:
		// poomaxq.max_outreach_date_time AS most_recent_outreach_date_time
		// GREATEST(poomaxq.max_outreach_date_time, smgmaxq.max_delivered_scheduled_message_group_date_time) AS most_recent_total_outreach_date_time
		// (...) outreach_followup_needed

		// Computed fields depend on:
		// ss_query (ssq)
		// recent_scheduled_screening_query (rssq)
		// poo_query (pooq)
		// smg_query (smgq)
		// smgmax_query (smgmaxq)

		/*

			poomax_query AS (
			-- Pick the most recent patient outreach attempt for each patient order
			select
					poo.patient_order_id, MAX(poo.outreach_date_time) as max_outreach_date_time
			from
					patient_order poq,
					patient_order_outreach poo
			where
					poq.patient_order_id = poo.patient_order_id
					and poo.deleted = false
			group by
					poo.patient_order_id
			),
		 */

		throw new UnsupportedOperationException();
	}

	public void refreshPatientOrderReasonForReferralCalculatedField(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// reason_for_referral_query (rfrq)

		// Use in calculated fields:
		// rfrq.reason_for_referral

		// Computed fields depend on:
		// (none)

		/*

			reason_for_referral_query AS (
					-- Pick reasons for referral for each patient order
					select
							poq.patient_order_id, string_agg(porr.description, ', ' order by por.display_order) AS reason_for_referral
					from
							patient_order poq,
							patient_order_referral_reason porr,
							patient_order_referral por
					where
							poq.patient_order_id = por.patient_order_id
							AND por.patient_order_referral_reason_id=porr.patient_order_referral_reason_id
					group by
							poq.patient_order_id
			),

		 */

		throw new UnsupportedOperationException();
	}

	public void refreshPatientOrderScheduledMessageComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// smg_query (smgq)

		// Use in computed fields:
		// coalesce(smgq.scheduled_message_group_delivered_count, 0) AS scheduled_message_group_delivered_count
		// coalesce(pooq.outreach_count, 0) + coalesce(smgq.scheduled_message_group_delivered_count, 0) as total_outreach_count
		// (...) outreach_followup_needed

		// Computed fields depend on:
		// poo_query (pooq)
		// ss_query (ssq)
		// recent_scheduled_screening_query (rssq)
		// smg_query (smgq)
		// smgmax_query (smgmaxq)
		// poomax_query (poomaxq)

		/*

			smg_query AS (
					-- Count up the scheduled message groups with a successful delivery for each patient order
					select
							poq.patient_order_id,
							count(posmg.*) AS scheduled_message_group_delivered_count
					from
							patient_order_scheduled_message_group posmg,
							patient_order poq
					where
							poq.patient_order_id = posmg.patient_order_id
							AND posmg.deleted=false
							and EXISTS (
							SELECT ml.message_id
							FROM patient_order_scheduled_message posm, scheduled_message sm, message_log ml
							WHERE posmg.patient_order_scheduled_message_group_id = posm.patient_order_scheduled_message_group_id
							AND posm.scheduled_message_id=sm.scheduled_message_id
							AND sm.message_id=ml.message_id
							AND ml.message_status_id='DELIVERED'
					)
					group by
							poq.patient_order_id
			)

		 */


		// Query:
		// smgmax_query (smgmaxq)

		// Use in computed fields:
		// smgmaxq.max_delivered_scheduled_message_group_date_time AS most_recent_delivered_scheduled_message_group_date_time
		// GREATEST(poomaxq.max_outreach_date_time, smgmaxq.max_delivered_scheduled_message_group_date_time) AS most_recent_total_outreach_date_time
		// (...) outreach_followup_needed
		// (...) next_contact_type_id
		// (...) next_contact_scheduled_at

		// Computed fields depend on:
		// poomax_query (poomaxq)
		// poo_query (pooq)
		// ss_query (ssq)
		// recent_scheduled_screening_query (rssq)
		// smg_query (smgq)
		// next_scheduled_outreach_query (nsoq)
		// ss_intake_query (ssiq)
		// next_resource_check_in_scheduled_message_group_query (nrcismgq)

		/*

		smgmax_query AS (
				-- Pick the most-distant scheduled message group with a successful delivery for each patient order
				select
						posmg.patient_order_id, MAX(posmg.scheduled_at_date_time) as max_delivered_scheduled_message_group_date_time
				from
						patient_order poq,
						patient_order_scheduled_message_group posmg
				where
						poq.patient_order_id = posmg.patient_order_id
						and posmg.deleted = false
						and EXISTS (
						SELECT ml.message_id
						FROM patient_order_scheduled_message posm, scheduled_message sm, message_log ml
						WHERE posmg.patient_order_scheduled_message_group_id = posm.patient_order_scheduled_message_group_id
						AND posm.scheduled_message_id=sm.scheduled_message_id
						AND sm.message_id=ml.message_id
						AND ml.message_status_id='DELIVERED'
				)
				group by
						posmg.patient_order_id
		)

		 */

		// Query:
		// next_resource_check_in_scheduled_message_group_query (nrcismgq)

		// Use in computed fields:
		// (...) next_contact_type_id
		// (...) next_contact_scheduled_at

		// Computed fields depend on:
		// ss_query (ssq)
		// recent_scheduled_screening_query (rssq)
		// next_scheduled_outreach_query (nsoq)
		// poomax_query (poomaxq)
		// smgmax_query (smgmaxq)
		// ss_intake_query (ssiq)

		/*

			next_resource_check_in_scheduled_message_group_query AS (
					-- Pick the next nondeleted scheduled message group in the future of type RESOURCE_CHECK_IN that has not yet been delivered
				select * from (
					select
						posmg.patient_order_id,
						posmg.patient_order_scheduled_message_group_id as next_resource_check_in_scheduled_message_group_id,
						posmg.scheduled_at_date_time as next_resource_check_in_scheduled_at_date_time,
						rank() OVER (PARTITION BY posmg.patient_order_id ORDER BY posmg.scheduled_at_date_time, posmg.patient_order_scheduled_message_group_id) as ranked_value
					from
						patient_order poq, patient_order_scheduled_message_group posmg, institution i
						where poq.patient_order_id = posmg.patient_order_id
						and posmg.patient_order_scheduled_message_type_id='RESOURCE_CHECK_IN'
							and posmg.deleted=false
							and poq.institution_id=i.institution_id
							and posmg.scheduled_at_date_time at time zone i.time_zone > now()
							and not EXISTS (
							SELECT ml.message_id
							FROM patient_order_scheduled_message posm, scheduled_message sm, message_log ml
							WHERE posmg.patient_order_scheduled_message_group_id = posm.patient_order_scheduled_message_group_id
							AND posm.scheduled_message_id=sm.scheduled_message_id
							AND sm.message_id=ml.message_id
							AND ml.message_status_id='DELIVERED'
					)
				) subquery where ranked_value=1
			)

		 */

		throw new UnsupportedOperationException();
	}

	public void refreshPatientOrderAppointmentComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// next_appt_query (naq)

		// Use in computed fields:
		// naq.appointment_start_time
		// naq.provider_id
		// naq.provider_name
		// naq.appointment_id
		// (...) appointment_scheduled
		// (...) appointment_scheduled_by_patient

		// Computed fields depend on:
		// (none)

		/*

			next_appt_query AS (
				select * from (
					select
						app.patient_order_id,
						app.appointment_id,
						app.canceled,
						p.provider_id,
						p.name as provider_name,
						app.start_time as appointment_start_time,
						app.created_by_account_id,
						rank() OVER (PARTITION BY app.patient_order_id ORDER BY app.start_time, app.appointment_id) as ranked_value
					from
						patient_order poq, appointment app, provider p
						where poq.patient_order_id = app.patient_order_id
						and app.provider_id=p.provider_id
						and app.canceled=false
						-- Not filtering on "> now()" because there should only ever be 1 uncanceled appointment per order.
						-- We also don't want the appointment to "disappear" in the UI as soon as it starts.
				) subquery where ranked_value=1
			)

		 */

		throw new UnsupportedOperationException();
	}

	public void refreshPatientOrderVoicemailTaskComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// recent_voicemail_task_query (rvtq)

		// Use in computed fields:
		// rvtq.patient_order_voicemail_task_id AS most_recent_patient_order_voicemail_task_id
		// rvtq.patient_order_voicemail_task_completed AS most_recent_patient_order_voicemail_task_completed

		// Computed fields depend on:
		// (none)

		/*

			recent_voicemail_task_query AS (
					-- Pick the most recent voicemail task for each patient order
				select * from (
					select
						povt.patient_order_id,
						povt.patient_order_voicemail_task_id,
						povt.completed as patient_order_voicemail_task_completed,
						rank() OVER (PARTITION BY povt.patient_order_id ORDER BY povt.created DESC, povt.patient_order_voicemail_task_id) as ranked_value
					from
						patient_order poq, patient_order_voicemail_task povt
						where poq.patient_order_id = povt.patient_order_id
							and povt.deleted = FALSE
				) subquery where ranked_value=1
			)

		 */

		throw new UnsupportedOperationException();
	}

	public void refreshPatientOrderScheduledOutreachComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// next_scheduled_outreach_query (nsoq)

		// Use in computed fields:
		// nsoq.next_scheduled_outreach_id
		// nsoq.next_scheduled_outreach_scheduled_at_date_time
		// nsoq.next_scheduled_outreach_type_id
		// nsoq.next_scheduled_outreach_reason_id
		// (...) next_contact_type_id
		// (...) next_contact_scheduled_at

		// Computed fields depend on:
		// ss_query (ssq)
		// recent_scheduled_screening_query (rssq)
		// poomax_query (poomaxq)
		// smgmax_query (smgmaxq)
		// ss_intake_query (ssiq)
		// next_resource_check_in_scheduled_message_group_query (nrcismgq)

		/*

			next_scheduled_outreach_query AS (
					-- Pick the next active scheduled outreach for each patient order
				select * from (
					select
						poso.patient_order_id,
						poso.patient_order_scheduled_outreach_id as next_scheduled_outreach_id,
						poso.scheduled_at_date_time as next_scheduled_outreach_scheduled_at_date_time,
							poso.patient_order_outreach_type_id as next_scheduled_outreach_type_id,
							poso.patient_order_scheduled_outreach_reason_id as next_scheduled_outreach_reason_id,
						rank() OVER (PARTITION BY poso.patient_order_id ORDER BY poso.scheduled_at_date_time, poso.patient_order_scheduled_outreach_id) as ranked_value
					from
						patient_order poq, patient_order_scheduled_outreach poso
						where poq.patient_order_id = poso.patient_order_id
							and poso.patient_order_scheduled_outreach_status_id = 'SCHEDULED'
				) subquery where ranked_value=1
			)

		 */

		throw new UnsupportedOperationException();
	}

	public void refreshPatientOrderMostRecentMessageDeliveredComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// most_recent_message_delivered_query (mrmdq)

		// Use in computed fields:
		// mrmdq.most_recent_message_delivered_at
		// (...) last_contacted_at

		// Computed fields depend on:
		// poomax_query (poomaxq)
		// ss_query (ssq)

	/*

			most_recent_message_delivered_query AS (
					-- Pick the message that has been most recently delivered to the patient
				select * from (
					select
						posmg.patient_order_id,
						ml.delivered as most_recent_message_delivered_at,
						rank() OVER (PARTITION BY posmg.patient_order_id ORDER BY ml.delivered DESC) as ranked_value
					from
						patient_order poq, patient_order_scheduled_message_group posmg, patient_order_scheduled_message posm, scheduled_message sm, message_log ml
						where poq.patient_order_id = posmg.patient_order_id
						and posmg.patient_order_scheduled_message_group_id=posm.patient_order_scheduled_message_group_id
							and posm.scheduled_message_id=sm.scheduled_message_id
							and sm.message_id=ml.message_id
							and ml.message_status_id='DELIVERED'
				) subquery where ranked_value=1
			)

	 */

		throw new UnsupportedOperationException();
	}

	public void refreshPatientOrderScreeningSessionComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// ss_query (ssq)

		// Use in computed fields:
		// ssq.screening_session_id AS most_recent_screening_session_id
		// ssq.created AS most_recent_screening_session_created_at
		// ssq.created_by_account_id AS most_recent_screening_session_created_by_account_id
		// ssq.role_id AS most_recent_screening_session_created_by_account_role_id
		// ssq.first_name AS most_recent_screening_session_created_by_account_first_name
		// ssq.last_name AS most_recent_screening_session_created_by_account_last_name
		// ssq.completed AS most_recent_screening_session_completed
		// ssq.completed_at AS most_recent_screening_session_completed_at
		// (...) patient_order_screening_status_id
		// (...) patient_order_screening_status_description
		// (...) most_recent_screening_session_by_patient
		// (...) most_recent_screening_session_appears_abandoned
		// (...) patient_order_encounter_documentation_status_id
		// (...) most_recent_intake_and_clinical_screenings_satisfied
		// (...) outreach_followup_needed
		// (...) last_contacted_at
		// (...) next_contact_type_id
		// (...) next_contact_scheduled_at

		// Computed fields depend on:
		// recent_scheduled_screening_query (rssq)
		// ss_intake_query (ssiq)
		// poo_query (pooq)
		// smg_query (smgq)
		// poomax_query (poomaxq)
		// smgmax_query (smgmaxq)
		// most_recent_message_delivered_query (mrmdq)
		// next_resource_check_in_scheduled_message_group_query (nrcismgq)

		/*

			ss_query AS (
					-- Pick the most recently-created clinical screening session for the patient order
				select * from (
					select
						ss.*,
						a.first_name,
						a.last_name,
						a.role_id,
						rank() OVER (PARTITION BY ss.patient_order_id ORDER BY ss.created DESC) as ranked_value
					from
						patient_order poq, screening_session ss, account a, institution i, screening_flow_version sfv
						where poq.patient_order_id = ss.patient_order_id
						and i.integrated_care_screening_flow_id=sfv.screening_flow_id
						and sfv.screening_flow_version_id =ss.screening_flow_version_id
						and ss.created_by_account_id =a.account_id
						and i.institution_id = a.institution_id
				) subquery where ranked_value=1
			)

		 */

		// Query:
		// ss_intake_query (ssiq)

		// Use in computed fields:
		// ssiq.screening_session_id AS most_recent_intake_screening_session_id
		// ssiq.created AS most_recent_intake_screening_session_created_at
		// ssiq.created_by_account_id AS most_recent_intake_screening_session_created_by_account_id
		// ssiq.role_id AS most_recent_intake_screening_session_created_by_account_role_id
		// ssiq.first_name AS most_recent_intake_screening_session_created_by_account_fn
		// ssiq.last_name AS most_recent_intake_screening_session_created_by_account_ln
		// ssiq.completed AS most_recent_intake_screening_session_completed
		// ssiq.completed_at AS most_recent_intake_screening_session_completed_at
		// (...) patient_order_intake_screening_status_id
		// (...) most_recent_intake_screening_session_by_patient
		// (...) most_recent_intake_screening_session_appears_abandoned
		// (...) most_recent_intake_and_clinical_screenings_satisfied
		// (...) next_contact_type_id
		// (...) next_contact_scheduled_at

		// Computed fields depend on:
		// ss_query (ssq)
		// recent_scheduled_screening_query (rssq)
		// next_scheduled_outreach_query (nsoq)
		// poomax_query (poomaxq)
		// smgmax_query (smgmaxq)

		/*

				ss_intake_query AS (
						-- Pick the most recently-created intake screening session for the patient order
					select * from (
						select
							ss.*,
							a.first_name,
							a.last_name,
							a.role_id,
							rank() OVER (PARTITION BY ss.patient_order_id ORDER BY ss.created DESC) as ranked_value
						from
							patient_order poq, screening_session ss, account a, institution i, screening_flow_version sfv
							where poq.patient_order_id = ss.patient_order_id
							and i.integrated_care_intake_screening_flow_id=sfv.screening_flow_id
							and sfv.screening_flow_version_id =ss.screening_flow_version_id
							and ss.created_by_account_id =a.account_id
							and i.institution_id = a.institution_id
					) subquery where ranked_value=1
				)

		 */

		throw new UnsupportedOperationException();
	}

	public void refreshPatientOrderScheduledScreeningComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// recent_scheduled_screening_query (rssq)

		// Use in computed fields:
		// (...) patient_order_screening_status_id
		// (...) patient_order_screening_status_description
		// rssq.patient_order_scheduled_screening_id
		// rssq.scheduled_date_time AS patient_order_scheduled_screening_scheduled_date_time
		// rssq.calendar_url AS patient_order_scheduled_screening_calendar_url
		// (...) outreach_followup_needed
		// (...) next_contact_type_id
		// (...) next_contact_scheduled_at

		// Computed fields depend on:
		// ss_query (ssq)
		// poo_query (pooq)
		// smg_query (smgq)
		// poomax_query (poomaxq)
		// smgmax_query (smgmaxq)
		// next_scheduled_outreach_query (nsoq)
		// ss_intake_query (ssiq)

		/*
			recent_scheduled_screening_query AS (
					-- Pick the most recently-scheduled screening for the patient order
				select * from (
					select
						poss.*,
						rank() OVER (PARTITION BY poss.patient_order_id ORDER BY poss.scheduled_date_time) as ranked_value
					from
						patient_order poq, patient_order_scheduled_screening poss
						where poq.patient_order_id = poss.patient_order_id
						and poss.canceled=FALSE
				) subquery where ranked_value=1
			)
		 */

		throw new UnsupportedOperationException();
	}

	public void refreshPatientOrderRecentPatientOrderComputedFields(@Nonnull UUID patientOrderId) {
		requireNonNull(patientOrderId);

		// Query:
		// recent_po_query (rpq)

		// Use in computed fields:
		// rpq.most_recent_episode_closed_at
		// DATE_PART('day', NOW() - rpq.most_recent_episode_closed_at)::INT < 30 AS most_recent_episode_closed_within_date_threshold

		// Computed fields depend on:
		// (none)

		/*

				recent_po_query AS (
						-- Get the last order based on the order date for this patient
						select
								poq.patient_order_id,
								lag(poq.episode_closed_at, 1) OVER
									 (PARTITION BY  patient_mrn ORDER BY poq.order_date) as most_recent_episode_closed_at
						from
								patient_order poq
				)

		 */

		throw new UnsupportedOperationException();
	}

	@Nonnull
	protected PatientOrderService getPatientOrderService() {
		return this.patientOrderServiceProvider.get();
	}

	@Nonnull
	protected Database getDatabase() {
		return this.databaseProvider.get();
	}

	@Nonnull
	protected Strings getStrings() {
		return this.strings;
	}

	@Nonnull
	protected Logger getLogger() {
		return this.logger;
	}
}
