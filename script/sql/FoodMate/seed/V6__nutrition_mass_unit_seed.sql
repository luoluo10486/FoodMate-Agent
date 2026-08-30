-- M2 nutrition exact mass-unit conversion seed.
-- Manual execution only. This seed does not infer food density.
--
-- kg, mg and lb are unambiguous mass units for the existing gram-based
-- catalog. Food-specific household portions and oz rules remain in V2-V5.
-- The stable source version makes the conversion snapshot auditable.

BEGIN;

WITH foods AS (
    SELECT nutrition_food_id
    FROM nutrition_foods
    WHERE nutrition_food_id BETWEEN 510001 AND 510025
      AND basis_unit = 'g'
      AND review_status = 'approved'
      AND is_deleted = FALSE
),
rules(rule_no, source_unit, target_unit, multiplier, source_version) AS (
    VALUES
        (1, 'kg', 'g', 1000.000000, 'mass-v1-kg'),
        (2, 'mg', 'g', 0.001000, 'mass-v1-mg'),
        (3, 'lb', 'g', 453.592370, 'mass-v1-lb')
)
INSERT INTO nutrition_unit_conversions (
    conversion_id,
    nutrition_food_id,
    source_unit,
    target_unit,
    multiplier,
    source_name,
    source_version,
    review_status
)
SELECT
    530000 + ((foods.nutrition_food_id - 510001) * 3) + rules.rule_no,
    foods.nutrition_food_id,
    rules.source_unit,
    rules.target_unit,
    rules.multiplier,
    'FoodMate reviewed exact mass conversion',
    rules.source_version,
    'approved'
FROM foods
CROSS JOIN rules
ON CONFLICT (nutrition_food_id, source_unit, target_unit) WHERE is_deleted = FALSE DO UPDATE SET
    multiplier = EXCLUDED.multiplier,
    source_name = EXCLUDED.source_name,
    source_version = EXCLUDED.source_version,
    review_status = EXCLUDED.review_status,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = CURRENT_TIMESTAMP;

COMMIT;
