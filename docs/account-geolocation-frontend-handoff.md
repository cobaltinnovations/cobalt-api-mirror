# Account Geolocation Report: Frontend Handoff

## Goal

Expose the existing Account Geolocation report as a self-service CSV download for eligible users. IP discovery and geolocation now run automatically every night, so the frontend must not ask an administrator to prepare the report or invoke IP processing.

The backend implementation is complete in this workspace. Frontend work is limited to making the existing reporting APIs available through the reporting UI.

## User experience

- Show **Analytics - Account Geolocation** wherever the application lists downloadable reports.
- Let the user choose a start and end date/time using the same controls and conventions as other date-ranged reports.
- Download the report as a CSV when the user submits the range.
- Optional explanatory copy: “IP geolocation enrichment begins nightly at 11:00 PM Eastern. Newly observed addresses may remain pending until the next nightly run finishes.”
- Do not add a “process,” “refresh geolocation,” or “retry failed addresses” action. The administrator-only processing endpoint remains an operational tool and is not part of this frontend feature.

## Authorization and report discovery

Use the authenticated reporting API as the source of truth for visibility:

```http
GET /reporting/report-types
```

An eligible response includes:

```json
{
  "reportTypes": [
    {
      "reportTypeId": "ACCOUNT_GEOLOCATION",
      "description": "Analytics - Account Geolocation"
    }
  ]
}
```

Only accounts with the `ANALYTICS_VIEWER` capability receive this report type. Render the option only when `reportTypes` contains `ACCOUNT_GEOLOCATION`; do not independently infer access from a role name or expose the option and rely on a later authorization failure.

The download endpoint also enforces the same authorization and returns `403` if the current account is not permitted to run the report.

## Download contract

```http
GET /reporting/run-report
  ?reportTypeId=ACCOUNT_GEOLOCATION
  &startDateTime=2026-09-01T00:00:00
  &endDateTime=2026-09-10T23:59:59
```

Requirements and behavior:

- `reportTypeId` must be `ACCOUNT_GEOLOCATION`.
- Always supply both `startDateTime` and `endDateTime` as ISO-8601 local date-times without a UTC suffix, matching the existing reporting UI conventions.
- The range is inclusive and is interpreted using the institution's timezone.
- Validate that the start is not after the end before requesting the report.
- Continue using the application's established authenticated API client.
- Optional `timeZone` and `locale` query parameters already exist, but are not required for this feature; the backend defaults them from the current account/institution.

A successful response has:

```text
Content-Type: text/csv
Content-Encoding: gzip
Content-Disposition: attachment; filename="Cobalt ACCOUNT_GEOLOCATION 2026-09-01 to 2026-09-10.csv"
```

Treat the response as a blob and use the filename from `Content-Disposition`. That header is exposed through CORS. Browsers normally decompress `Content-Encoding: gzip` automatically, so the downloaded blob should retain the `.csv` filename and must not be renamed to `.gz`.

Framework-agnostic TypeScript example:

```ts
const query = new URLSearchParams({
  reportTypeId: "ACCOUNT_GEOLOCATION",
  startDateTime,
  endDateTime,
});

const response = await authenticatedFetch(
  `/reporting/run-report?${query.toString()}`,
);

if (!response.ok) {
  // Use the application's standard API error handling.
  throw await apiErrorFromResponse(response);
}

const disposition = response.headers.get("Content-Disposition") ?? "";
const filename = disposition.match(/filename="([^"]+)"/)?.[1]
  ?? "Cobalt ACCOUNT_GEOLOCATION.csv";
const blob = await response.blob();
const url = URL.createObjectURL(blob);
const link = document.createElement("a");

link.href = url;
link.download = filename;
link.click();
URL.revokeObjectURL(url);
```

Reuse an existing download helper instead of adding this code directly if the frontend already has one for `/reporting/run-report`.

## CSV contract

The existing CSV schema is unchanged:

```text
account_id
email_address
account_created_at
role_id
ip_address
first_analytics_event_at
last_analytics_event_at
ip_geolocation_status_id
ip_type
continent_code
continent_name
country_code
country_name
region_code
region_name
city
postal_code
latitude
longitude
msa
dma
radius
ip_routing_type
connection_type
connection_asn
connection_isp
connection_organization_type
connection_home
hostname
time_zone_id
time_zone_gmt_offset
time_zone_code
location_geoname_id
location_is_eu
provider_error_code
provider_error_type
provider_error_message
last_lookup_attempted_at
last_lookup_succeeded_at
```

Rows are institution-scoped by the backend. Each row represents an account/IP pair with analytics activity in the requested range. Geolocation fields can be blank when an address has not completed processing. `ip_geolocation_status_id` communicates the enrichment result, including `PENDING`, `SUCCEEDED`, `FAILED`, and private/reserved outcomes.

The frontend does not need to parse, transform, filter, or preview the CSV for this scope.

## Loading and error states

- Disable repeated submissions while a download request is active.
- Show the existing report-download loading treatment; large date ranges may take longer.
- Use standard authentication handling for `401`.
- If a stale UI exposes the report to an unauthorized user and the API returns `403`, show the standard authorization error and refresh report types when appropriate.
- For other failures, show the standard downloadable-report error rather than saving the JSON error body as a CSV.

## Acceptance criteria

- An account with `ANALYTICS_VIEWER` sees `ACCOUNT_GEOLOCATION` after report types load.
- An account without `ANALYTICS_VIEWER` does not see it.
- The user can select a valid date/time range and download the server-provided `.csv` filename.
- The downloaded file opens as CSV and contains the existing header schema.
- Start/end values are both sent and retain the user's intended local wall-clock values.
- Loading, invalid-range, authentication, authorization, and server-error states follow existing report UI behavior.
- No frontend action invokes `POST /system/ip-geolocations/process`.
- No frontend changes attempt to manage the nightly schedule or expose cross-institution data.

## Backend references

- Report routes: `src/main/java/com/cobaltplatform/api/web/resource/ReportingResource.java`
- Report availability and CSV generation: `src/main/java/com/cobaltplatform/api/service/ReportingService.java`
- API response model: `src/main/java/com/cobaltplatform/api/model/api/response/ReportTypeApiResponse.java`
- Backend integration coverage: `src/test/java/com/cobaltplatform/api/service/AccountGeolocationReportingTests.java`

