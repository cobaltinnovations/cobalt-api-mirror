# Image Repository Migration

This document describes how legacy image uploads are migrated into the new image repository model, how quality issues are handled, and how to test or roll the migration out incrementally by institution.

## Goals

- Preserve existing image assets as `IMAGE_RAW` when possible.
- Generate required crop and thumbnail variants only when the source image has enough fidelity.
- Avoid rewiring application records until a complete required image family exists.
- Track every attempted migration with a durable audit row.
- Support institution-scoped rollout because the new image repository behavior is controlled by `institution.image_repository_enabled`.
- Make migration safe to retry and safe to run in small batches.

## Relevant Schema

The media migration schema is introduced in `sql/updates/259-media-uploads.sql`.

Key tables and columns:

- `institution.image_repository_enabled`: institution-level feature flag for the new image repository behavior.
- `file_upload.file_upload_status_id`: historical rows are marked `UPLOADED`; new presigned uploads still default to `CREATED`.
- `file_upload_type.storage_key`: stable storage directory segment for each upload type.
- `image`: repository image record. It points at a `file_upload`, optionally points at a source `image`, and records dimensions, active state, alt text, and raw image hash.
- `legacy_image_migration_status`: status reference data for migration outcomes.
- `legacy_image_migration`: audit and crosswalk table from a legacy `file_upload` to generated `image` rows.

Migration statuses:

| Status | Meaning |
|---|---|
| `RAW_IMPORTED` | The source image was imported as raw, but no crop variants were requested. |
| `VARIANTS_GENERATED` | All requested crop and thumbnail variants were generated. |
| `LOW_FIDELITY` | The source was readable, but too small to generate at least one required crop at acceptable quality. |
| `UNMIGRATABLE` | The source could not be migrated, for example missing storage key, non-image content type, missing object, or decode failure. |
| `NEEDS_REVIEW` | Reserved for manual review workflows. |
| `REPLACED` | Reserved for cases where the legacy image is replaced by a newly uploaded source. |

## Current Scope

The implemented service migrates institution-scoped legacy image references for:

- Content: `content.image_file_upload_id` where the legacy upload type is `CONTENT_IMAGE`.
- Group sessions: `group_session.image_file_upload_id` where the legacy upload type is `GROUP_SESSION_IMAGE`.
- Page heroes: `page.image_file_upload_id` where the legacy upload type is `PAGE_IMAGE`.
- Page row columns: `page_row_column.image_file_upload_id` on active rows and non-deleted pages.
- Page row calls to action: `page_row_call_to_action.image_file_upload_id` on active rows and non-deleted pages.

Content and group-session references remain fixed at:

- Required crop: `IMAGE_16X9`
- Required thumbnail: `IMAGE_THUMBNAIL_16X9`

Page-builder references choose one crop from `IMAGE_16X9`, `IMAGE_4X3`, and `IMAGE_1X1`. Crops are ranked by the fraction of source pixels retained by a centered crop. The closest crop that passes its minimum-size quality gate is selected; ties are resolved in `16:9`, `4:3`, then `1:1` order. Only the selected crop and its matching thumbnail are generated.

Successful rewires:

- `content.image_id` or `group_session.image_id` is set to the generated `IMAGE_16X9` crop.
- Page-builder `image_id` is set to the selected qualifying crop.
- The legacy `image_file_upload_id` fallback column is updated to the generated crop file upload.

The audit table remains keyed by the legacy `file_upload`, not by each consumer record. If multiple references point at the same legacy upload, the image family is generated once and each reference can be rewired independently.

## Recrop Replacement

Confirming a thumbnail makes its crop the current variant for that raw image and aspect ratio. In the same transaction, the service:

- Replaces references to every older crop of that raw image and aspect ratio.
- Updates both `image_id` and the compatibility `image_file_upload_id`.
- Rewires non-deleted content, non-deleted group sessions, page heroes, page-row columns, and page-row calls to action.
- Leaves deleted content, deleted group sessions, deleted page structures, other aspect ratios, and other image families unchanged.
- Deactivates the superseded crop/thumbnail records and activates the confirmed pair.

`sql/updates/260-media-image-recrop-references.sql` repairs references left stale by recrops completed before this behavior was deployed. If inconsistent data contains more than one active uploaded crop/thumbnail pair for a raw image and aspect ratio, the backfill treats the pair with the newest completed thumbnail as current.

## How A Single Migration Works

Entry points:

```java
mediaImageMigrationService.migrateLegacyContentImage(account, contentId);
mediaImageMigrationService.migrateLegacyGroupSessionImage(account, groupSessionId);
mediaImageMigrationService.migrateLegacyPageImage(account, pageId);
mediaImageMigrationService.migrateLegacyPageRowColumnImage(account, pageRowColumnId);
mediaImageMigrationService.migrateLegacyPageRowCallToActionImage(account, pageRowCallToActionId);
```

Processing flow:

1. Load the content or group session for the caller's institution.
2. Resolve the legacy file upload ID.
   - If the record is already migrated, the service resolves the original legacy file upload through `legacy_image_migration`.
   - This makes retries idempotent.
3. Validate the legacy `file_upload`.
   - Must belong to the same institution.
   - Must have a `storage_key`.
   - Must have an image content type. Legacy content image values like `application/png`, `application/jpg`, and `application/jpeg` are normalized.
4. Download and decode the source image.
5. Compute the source SHA-256 hash.
6. Import or reuse the `IMAGE_RAW` record.
7. Generate missing requested crop/thumbnail variants if fidelity gates pass.
8. Upsert the `legacy_image_migration` audit row.
9. Rewire the content or group session only when all required variants are generated.

Retry behavior:

- Existing raw/crop/thumbnail image IDs are reused.
- Missing variants can be generated later.
- A previously migrated reference can be passed to the runner again without duplicating image families. Page-builder retries resolve the legacy upload through any of the three audit crop columns.

## Quality Gates

Legacy uploads did not enforce aspect ratio or thumbnail generation. The migration therefore uses a conservative centered crop and minimum source fidelity thresholds.

Current thresholds:

| Crop type | Crop ratio | Minimum crop size | Thumbnail size |
|---|---:|---:|---:|
| `IMAGE_16X9` | 16:9 | `1280x720` | `320x180` |
| `IMAGE_4X3` | 4:3 | `1200x900` | `240x180` |
| `IMAGE_1X1` | 1:1 | `800x800` | `200x200` |

If a source can be decoded but cannot satisfy the required crop size after centered cropping:

- The raw image is still imported.
- The missing crop/thumbnail pair is not generated.
- The migration status is `LOW_FIDELITY`.
- `quality_report` explains the failed requirement.
- The application record is not rewired, so the legacy fallback remains in place.

This prevents low-quality thumbnails from silently replacing acceptable legacy displays.

## Institution Report

Institution-scoped reporting is handled by:

```java
mediaImageMigrationService.findLegacyImageMigrationReport(institutionId);
```

The report includes:

- `institutionId`
- `imageRepositoryEnabled`
- `totalCount`: unique legacy image uploads in scope, including audited rows.
- `currentLegacyReferenceCount`: current legacy references across content, group sessions, and page-builder placements.
- `currentLegacyContentReferenceCount`
- `currentLegacyGroupSessionReferenceCount`
- `currentLegacyPageReferenceCount`
- `currentLegacyPageRowColumnReferenceCount`
- `currentLegacyPageRowCallToActionReferenceCount`
- `pendingCount`: current legacy references that still need migration or rewire.
- `pendingContentReferenceCount`
- `pendingGroupSessionReferenceCount`
- `pendingPageReferenceCount`
- `pendingPageRowColumnReferenceCount`
- `pendingPageRowCallToActionReferenceCount`
- `attemptedCount`: rows with a migration audit record.
- `rawImportedCount`
- `variantsGeneratedCount`
- `needsReviewCount`
- `lowFidelityCount`
- `unmigratableCount`
- `replacedCount`
- `rewiredContentCount`
- `rewiredGroupSessionCount`
- `rewiredPageCount`
- `rewiredPageRowColumnCount`
- `rewiredPageRowCallToActionCount`

Use this report before and after each batch. It is the safest way to understand migration progress without scanning S3 or manually joining image tables.

## Incremental Batch Migration

Institution batch migration is handled by:

```java
mediaImageMigrationService.migratePendingLegacyImagesForInstitution(account, limit);
```

Behavior:

- Uses the account's institution as the scope.
- Selects pending current legacy references for content, group sessions, page heroes, row columns, and row calls to action.
- Processes at most `limit` references.
- Captures a before report and after report.
- Returns one result per processed reference, including reference type and reference ID.

A reference is pending when it still points at a legacy upload and either:

- No audit row exists yet.
- The audit row is `RAW_IMPORTED`.
- The audit row is `VARIANTS_GENERATED`, but that specific consumer record still needs to be rewired.

Rows already marked `LOW_FIDELITY`, `UNMIGRATABLE`, `NEEDS_REVIEW`, or `REPLACED` are reported but not retried by the default batch selector.

Recommended rollout:

1. Confirm `institution.image_repository_enabled` for the institution.
2. Run report-only mode.
3. Run a small batch, for example `limit=5`.
4. Review report deltas.
5. Inspect low-fidelity and unmigratable rows.
6. Increase batch size gradually.
7. Repeat until `pendingCount=0`.
8. Hand off `LOW_FIDELITY` and `UNMIGRATABLE` rows for replacement or manual review.

Do not treat `pendingCount=0` as "all images are ready." Check status counts. A complete automated migration should primarily move rows into `VARIANTS_GENERATED`; low-fidelity and unmigratable rows are expected exceptions that require follow-up.

## Local Runner

Local testing uses `src/test/java/com/cobaltplatform/api/util/MediaImageMigrationLocalRunner.java`.

The runner is test-scoped and does not add a production HTTP endpoint.

Safety behavior:

- Report-only mode does not require `-Dcommit=true`.
- Any write mode requires `-Dcommit=true`.
- Institution batch mode refuses to run when `image_repository_enabled=false` unless `-DignoreImageRepositoryEnabled=true` is supplied.

### Report An Institution

```bash
mvn -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.cobaltplatform.api.util.MediaImageMigrationLocalRunner \
  -DreportOnly=true \
  -DinstitutionId=COBALT
```

### Migrate A Small Institution Batch

```bash
mvn -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.cobaltplatform.api.util.MediaImageMigrationLocalRunner \
  -Dcommit=true \
  -DinstitutionId=COBALT \
  -Dlimit=25
```

This processes content, group-session, and page-builder legacy image references for the institution.

### Migrate One Existing Content Item

```bash
mvn -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.cobaltplatform.api.util.MediaImageMigrationLocalRunner \
  -Dcommit=true \
  -DcontentId=<content-id>
```

### Migrate One Existing Group Session

```bash
mvn -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.cobaltplatform.api.util.MediaImageMigrationLocalRunner \
  -Dcommit=true \
  -DgroupSessionId=<group-session-id>
```

### Migrate One Page-Builder Placement

Use exactly one of `pageId`, `pageRowColumnId`, or `pageRowCallToActionId`:

```bash
mvn -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.cobaltplatform.api.util.MediaImageMigrationLocalRunner \
  -Dcommit=true \
  -DpageRowColumnId=<page-row-column-id>
```

### Seed And Migrate A Local Test Group Session

This creates a local legacy `GROUP_SESSION_IMAGE` object and group session, then migrates it.

```bash
mvn -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.cobaltplatform.api.util.MediaImageMigrationLocalRunner \
  -Dcommit=true \
  -DseedLegacyGroupSession=true \
  -DinstitutionId=COBALT \
  -DsourceWidth=1600 \
  -DsourceHeight=900
```

To test low-fidelity handling:

```bash
mvn -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.cobaltplatform.api.util.MediaImageMigrationLocalRunner \
  -Dcommit=true \
  -DseedLegacyGroupSession=true \
  -DinstitutionId=COBALT \
  -DsourceWidth=640 \
  -DsourceHeight=360
```

Expected high-fidelity result:

- Status: `VARIANTS_GENERATED`
- Raw image created or reused.
- The required crop and matching thumbnail are created or reused. Content and group sessions use 16:9; page-builder placements use the closest qualifying crop.
- The consumer is rewired to the crop. A page-builder migration also rewires every active page-builder placement sharing that legacy upload.

Expected low-fidelity result:

- Status: `LOW_FIDELITY`
- Raw image created or reused.
- No qualifying required crop/thumbnail.
- The consumer remains on the legacy fallback.
- `quality_report` contains the reason.

## Useful Verification Queries

Replace IDs as needed.

Check one migrated group session:

```sql
SELECT
  gs.group_session_id,
  gs.image_id,
  gs.image_file_upload_id,
  lim.legacy_image_migration_status_id,
  lim.raw_image_id,
  lim.crop_16x9_image_id,
  lim.thumbnail_16x9_image_id,
  crop.file_upload_type_id AS crop_type,
  thumb.file_upload_type_id AS thumbnail_type
FROM group_session gs
JOIN legacy_image_migration lim
  ON lim.crop_16x9_image_id=gs.image_id
LEFT JOIN v_image crop
  ON crop.image_id=lim.crop_16x9_image_id
LEFT JOIN v_image thumb
  ON thumb.image_id=lim.thumbnail_16x9_image_id
WHERE gs.group_session_id='<group-session-id>';
```

Check one migrated content item:

```sql
SELECT
  c.content_id,
  c.image_id,
  c.image_file_upload_id,
  lim.legacy_image_migration_status_id,
  lim.raw_image_id,
  lim.crop_16x9_image_id,
  lim.thumbnail_16x9_image_id,
  crop.file_upload_type_id AS crop_type,
  thumb.file_upload_type_id AS thumbnail_type
FROM content c
JOIN legacy_image_migration lim
  ON lim.crop_16x9_image_id=c.image_id
LEFT JOIN v_image crop
  ON crop.image_id=lim.crop_16x9_image_id
LEFT JOIN v_image thumb
  ON thumb.image_id=lim.thumbnail_16x9_image_id
WHERE c.content_id='<content-id>';
```

Find outstanding low-fidelity or unmigratable rows:

```sql
SELECT
  legacy_file_upload_id,
  legacy_image_migration_status_id,
  source_width,
  source_height,
  quality_report,
  error_message
FROM legacy_image_migration
WHERE institution_id='<institution-id>'
AND legacy_image_migration_status_id IN ('LOW_FIDELITY', 'UNMIGRATABLE', 'NEEDS_REVIEW')
ORDER BY last_updated DESC;
```

Find currently pending legacy image references:

```sql
WITH legacy_refs AS (
  SELECT
    'CONTENT' AS reference_type,
    c.content_id AS reference_id,
    c.image_file_upload_id,
    c.created
  FROM content c
  JOIN file_upload fu
    ON fu.file_upload_id=c.image_file_upload_id
  WHERE c.owner_institution_id='<institution-id>'
  AND c.deleted_flag=FALSE
  AND fu.file_upload_type_id='CONTENT_IMAGE'
  UNION ALL
  SELECT
    'GROUP_SESSION' AS reference_type,
    gs.group_session_id AS reference_id,
    gs.image_file_upload_id,
    gs.created
  FROM group_session gs
  JOIN file_upload fu
    ON fu.file_upload_id=gs.image_file_upload_id
  WHERE gs.institution_id='<institution-id>'
  AND gs.group_session_status_id<>'DELETED'
  AND fu.file_upload_type_id='GROUP_SESSION_IMAGE'
	UNION ALL
	SELECT 'PAGE', p.page_id, p.image_file_upload_id, p.created
	FROM page p JOIN file_upload fu ON fu.file_upload_id=p.image_file_upload_id
	WHERE p.institution_id='<institution-id>' AND p.deleted_flag=FALSE
	AND fu.file_upload_type_id='PAGE_IMAGE'
	UNION ALL
	SELECT 'PAGE_ROW_COLUMN', prc.page_row_column_id, prc.image_file_upload_id, prc.created
	FROM page_row_column prc
	JOIN file_upload fu ON fu.file_upload_id=prc.image_file_upload_id
	JOIN page_row pr ON pr.page_row_id=prc.page_row_id
	JOIN page_section ps ON ps.page_section_id=pr.page_section_id
	JOIN page p ON p.page_id=ps.page_id
	WHERE p.institution_id='<institution-id>' AND p.deleted_flag=FALSE
	AND ps.deleted_flag=FALSE AND pr.deleted_flag=FALSE AND fu.file_upload_type_id='PAGE_IMAGE'
	UNION ALL
	SELECT 'PAGE_ROW_CALL_TO_ACTION', prcta.page_row_call_to_action_id,
	  prcta.image_file_upload_id, prcta.created
	FROM page_row_call_to_action prcta
	JOIN file_upload fu ON fu.file_upload_id=prcta.image_file_upload_id
	JOIN page_row pr ON pr.page_row_id=prcta.page_row_id
	JOIN page_section ps ON ps.page_section_id=pr.page_section_id
	JOIN page p ON p.page_id=ps.page_id
	WHERE p.institution_id='<institution-id>' AND p.deleted_flag=FALSE
	AND ps.deleted_flag=FALSE AND pr.deleted_flag=FALSE AND fu.file_upload_type_id='PAGE_IMAGE'
)
SELECT
  lr.reference_type,
  lr.reference_id,
  lr.image_file_upload_id,
  fu.storage_key,
  fu.content_type,
  fu.filesize
FROM legacy_refs lr
JOIN file_upload fu
  ON fu.file_upload_id=lr.image_file_upload_id
LEFT JOIN legacy_image_migration lim
  ON lim.legacy_file_upload_id=lr.image_file_upload_id
WHERE lim.legacy_file_upload_id IS NULL
OR lim.legacy_image_migration_status_id IN ('RAW_IMPORTED','VARIANTS_GENERATED')
ORDER BY lr.created, lr.reference_type, lr.reference_id;
```

## Automated Tests

Focused migration tests:

```bash
mvn -q -Dtest=MediaImageMigrationServiceTests test
```

Broader media regression suite:

```bash
mvn -q -Dtest=MediaServiceTests,MediaImageMigrationServiceTests test
```

Covered scenarios:

- High-fidelity legacy group-session image migrates and rewires.
- Low-fidelity legacy group-session image imports raw but does not rewire.
- High-fidelity legacy content image migrates and rewires, including legacy MIME normalization.
- Retrying a migration reuses existing raw/crop/thumbnail records.
- Closest-crop ranking covers square, 4:3, 16:9, portrait, intermediate, quality-fallback, and no-qualifying-crop sources.
- Page-builder migrations rewire shared hero, row-column, and CTA references and retry through every audit crop column.
- Institution report includes the feature flag, status counts, and per-reference-type page-builder breakdowns.
- Institution batch migration processes all pending reference types incrementally by `limit`.
- Page-builder request compatibility covers selected-image precedence, unchanged legacy IDs, replacement, clearing, and duplication.
- `PAGE` gallery scope covers active draft hero, row-column, and CTA associations, crop filters, combined scopes, and deleted-row/page exclusions.

## Production Implementation Notes

The current local runner is intentionally not a production endpoint. For production rollout, prefer a controlled operational job or admin-only task with these properties:

- Required institution ID.
- Required explicit limit.
- Dry-run/report mode.
- Per-institution feature flag check.
- One legacy image per transaction or a small transaction boundary.
- Structured logging of reference type, reference ID, legacy file upload ID, result status, and generated image IDs.
- Safe retry behavior using `legacy_image_migration`.
- Metrics or dashboarding from the institution report.

Suggested production rollout sequence:

1. Deploy schema and service code.
2. Run report-only mode for each institution.
3. Enable `image_repository_enabled` for one institution.
4. Process a small batch.
5. Review generated variants and application rendering.
6. Continue batch processing until `pendingCount=0`.
7. Export `LOW_FIDELITY`, `UNMIGRATABLE`, and `NEEDS_REVIEW` rows for replacement workflows.
8. Repeat by institution.

## Known Follow-Ups

- Add page and page-row image references if the image repository model is extended to those tables.
- Decide whether low-fidelity rows should become `NEEDS_REVIEW` automatically after raw import, or remain `LOW_FIDELITY` until a human action.
- Add a replacement workflow that links a newly uploaded raw image back to `legacy_image_migration` and marks the row `REPLACED`.
- Add an admin-only production trigger or scheduled worker if this migration should be run outside local/test utilities.
