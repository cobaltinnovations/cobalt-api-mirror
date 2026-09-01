BEGIN;
SELECT _v.register_patch('260-v2-account-email-templates', NULL, NULL);

-- Optional institution-specific plain text that replaces the default footer
-- copy in V2 transactional emails.
ALTER TABLE institution
  ADD COLUMN email_footer_text TEXT;

-- Optional institution-specific image used in the email header.  Explicit
-- per-message overrides continue to take precedence over this value.
ALTER TABLE institution
  ADD COLUMN platform_email_image_url TEXT;

-- Local development uses COBALT_COURSES for Behavior Bridge.
UPDATE institution
SET platform_name = 'Behavior Bridge'
WHERE institution_id = 'COBALT_COURSES';

-- Production uses BEHAVIOR_BRIDGE; local development uses COBALT_COURSES.
UPDATE institution
SET platform_email_image_url = 'https://cdn-prod.cobalt.care/logos/behavior-bridge-email-logo.png'
WHERE institution_id IN ('COBALT_COURSES', 'BEHAVIOR_BRIDGE');

-- Populate missing institution colors from the Behavior Bridge web theme.
-- V2 emails consume this same institution palette; existing values are preserved.
WITH target_institution AS (
  SELECT institution_id
  FROM institution
  WHERE institution_id = 'COBALT_COURSES'
),
palette (color_value_id, css_representation) AS (
  VALUES
    ('N0',   '#FFFFFF'),
    ('N50',  '#F7F8F7'),
    ('N75',  '#EBEBEB'),
    ('N100', '#D1D5D4'),
    ('N300', '#B5BBBA'),
    ('N500', '#6B7371'),
    ('N700', '#474D4B'),
    ('N900', '#2D3030'),

    ('P50',  '#E0EBE8'),
    ('P100', '#B3CCC6'),
    ('P300', '#66998D'),
    ('P500', '#2F7F61'),
    ('P700', '#00664F'),
    ('P900', '#00382B'),

    ('A50',  '#FAEDF6'),
    ('A100', '#F7B7E6'),
    ('A300', '#FA87DA'),
    ('A500', '#E45DBF'),
    ('A700', '#961573'),
    ('A900', '#47153A'),

    ('D50',  '#FFEDED'),
    ('D100', '#FFCDCE'),
    ('D300', '#FF575A'),
    ('D500', '#D63638'),
    ('D700', '#850E10'),
    ('D900', '#670A0C'),

    ('W50',  '#FFEFD6'),
    ('W100', '#FFE0AD'),
    ('W300', '#FFB336'),
    ('W500', '#F29500'),
    ('W700', '#955C00'),
    ('W900', '#724600'),

    ('S50',  '#EBF6E0'),
    ('S100', '#D0EEB2'),
    ('S300', '#78BE20'),
    ('S500', '#4A8044'),
    ('S700', '#355C31'),
    ('S900', '#233D21'),

    ('I50',  '#E3F3EF'),
    ('I100', '#CAF0E6'),
    ('I300', '#6ECEB2'),
    ('I500', '#128082'),
    ('I700', '#0C5859'),
    ('I900', '#083C3D')
)
INSERT INTO institution_color_value (
  institution_id,
  color_value_id,
  css_representation
)
SELECT
  target_institution.institution_id,
  palette.color_value_id,
  palette.css_representation
FROM target_institution
CROSS JOIN palette
ON CONFLICT (institution_id, color_value_id)
DO NOTHING;

COMMIT;
