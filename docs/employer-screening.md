# Employer screening

Apply `sql/local/266-cobalt-employer-onboarding-single-question.sql` after the
local employer-onboarding fixture (264). This is local/bootstrap configuration;
real tenants require their own configuration patch.
Both local database rebuild scripts include it. It removes the welcome and
completion prompt references from the COBALT employer screening while preserving
existing sessions and answers. The employer question and decline option remain;
submitting either completes the screening.

Apply production schema migrations 265 and
`266-account-source-onboarding-screening-treatment.sql` before running the updated
API. The latter converts the earlier boolean setting, preserving false as
`DEFAULT` and true as `MODAL`.

Account API responses expose `onboardingTreatmentId`, read from
`account_source.onboarding_treatment_id` on each response. This is a string,
so adding treatment identifiers requires no backend enum change. The database
default is `DEFAULT`. Existing UPHS/PennKey source rows start with `MODAL`.
If these sources are provisioned later, set the treatment when provisioning them.

Change a source's treatment with a data update; no backend release or restart is needed:

```sql
UPDATE account_source
SET onboarding_treatment_id = 'MODAL'
WHERE account_source_id IN ('PENN_SSO', 'PENN_KEY_SSO');
```

Use `DEFAULT` for the existing screening UI or `MODAL` for the onboarding modal.
This setting applies to every account using that source across institutions.
Clients should fall back to the existing UI for absent or unrecognized values.
New visual treatments require client support, but the backend passes their
identifiers through without code changes. The setting controls onboarding
presentation only, not eligibility, completion, or other screening flows.

Focused verification:

```sh
mvn -Dtest=AccountOnboardingScreeningTreatmentTests,CobaltEmployerOnboardingTests test
```

The service tests require the local services and a database rebuilt through
local patch 266.
