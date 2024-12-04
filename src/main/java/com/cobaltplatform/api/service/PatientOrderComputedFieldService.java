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
		// left outer join poo_query pooq ON poq.patient_order_id = pooq.patient_order_id
		// left outer join poomax_query poomaxq ON poq.patient_order_id = poomaxq.patient_order_id
		// left outer join reason_for_referral_query rfrq on poq.patient_order_id=rfrq.patient_order_id
		// left outer join smg_query smgq ON poq.patient_order_id = smgq.patient_order_id
		// left outer join smgmax_query smgmaxq ON poq.patient_order_id = smgmaxq.patient_order_id
		// left outer join next_resource_check_in_scheduled_message_group_query nrcismgq on poq.patient_order_id=nrcismgq.patient_order_id
		// left outer join next_appt_query naq on poq.patient_order_id=naq.patient_order_id
		// left outer join recent_voicemail_task_query rvtq on poq.patient_order_id=rvtq.patient_order_id
		// left outer join next_scheduled_outreach_query nsoq ON poq.patient_order_id=nsoq.patient_order_id
		// left outer join most_recent_message_delivered_query mrmdq on poq.patient_order_id=mrmdq.patient_order_id
		// left outer join ss_query ssq ON poq.patient_order_id = ssq.patient_order_id
		// left outer join ss_intake_query ssiq ON poq.patient_order_id = ssiq.patient_order_id
		// left join permitted_regions_query prq ON poq.institution_id = prq.institution_id
		// left outer join recent_scheduled_screening_query rssq ON poq.patient_order_id = rssq.patient_order_id
		// left outer join recent_po_query rpq ON poq.patient_order_id = rpq.patient_order_id

		refreshPatientOrderOutreachCountComputedFields(patientOrderId);
		refreshPatientOrderReasonForReferralCalculatedField(patientOrderId);

		// TODO: other calls

		throw new UnsupportedOperationException();
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
