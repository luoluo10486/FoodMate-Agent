-- M1-5 nutrition household-unit conversion seed.
-- Manual execution only. Flyway remains disabled by project policy.
--
-- Source: USDA FoodData Central API foodPortions for the matching SR Legacy
-- records. The API returns the amount, modifier and gramWeight for each row.
-- The source_version keeps the FDC ID and portion sequence so every rule is
-- traceable without inferring a household measure from the food name.
--
-- Official reference:
-- https://api.nal.usda.gov/fdc/v1/foods?fdcIds=168880,171477,173424,171998,171689
-- https://fdc.nal.usda.gov/api-guide.html

BEGIN;

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
VALUES
    (
        520001,
        510001,
        'cup',
        'g',
        186.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-168880 portion-1',
        'approved'
    ),
    (
        520002,
        510002,
        'cup',
        'g',
        140.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-171477 portion-1',
        'approved'
    ),
    (
        520003,
        510003,
        'large',
        'g',
        50.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-173424 portion-3',
        'approved'
    ),
    (
        520004,
        510004,
        'oz',
        'g',
        28.3333,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-171998 portion-1 (3 oz=85 g)',
        'approved'
    ),
    (
        520005,
        510005,
        'medium',
        'g',
        161.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-171689 portion-3',
        'approved'
    )
ON CONFLICT (conversion_id) DO UPDATE SET
    nutrition_food_id = EXCLUDED.nutrition_food_id,
    source_unit = EXCLUDED.source_unit,
    target_unit = EXCLUDED.target_unit,
    multiplier = EXCLUDED.multiplier,
    source_name = EXCLUDED.source_name,
    source_version = EXCLUDED.source_version,
    review_status = EXCLUDED.review_status,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = CURRENT_TIMESTAMP;

COMMIT;
