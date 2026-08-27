-- Local-only adjustments for records created by the optional bootstrap data.

BEGIN;

UPDATE institution
SET
  platform_name = 'Behavior Bridge',
  platform_email_image_url = 'https://cdn-prod.cobalt.care/logos/dh-behavior-bridge-hero.png'
WHERE institution_id = 'COBALT_COURSES';

-- Match the shared COBALT_COURSES institution palette to the Behavior Bridge
-- web theme. Existing values are preserved.
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
