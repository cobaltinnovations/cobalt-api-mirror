BEGIN;
SELECT _v.register_patch('259-v2-account-email-templates', NULL, NULL);

-- Optional institution-specific plain text that replaces the default footer
-- copy in V2 transactional emails.
ALTER TABLE institution
  ADD COLUMN email_footer_text TEXT;

COMMIT;
