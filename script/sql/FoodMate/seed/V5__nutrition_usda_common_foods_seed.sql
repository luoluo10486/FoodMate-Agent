-- M2 nutrition directory extension seed.
-- Manual execution only. Flyway remains disabled by project policy.
--
-- Source: USDA FoodData Central SR Legacy, published 2019-04-01.
-- The values below are the USDA values per 100 g. The FDC ID is retained in
-- source_version so every catalog row can be reviewed against the source.
-- Portion multipliers are normalized to one source unit and only come from
-- the corresponding USDA foodPortions rows.
--
-- Official references:
-- https://fdc.nal.usda.gov/api-guide.html
-- https://fdc.nal.usda.gov/data-documentation.html
-- https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip

BEGIN;

INSERT INTO nutrition_foods (
    nutrition_food_id,
    standard_name,
    chinese_name,
    aliases_json,
    category,
    basis_unit,
    calories_kcal_per_100,
    protein_g_per_100,
    fat_g_per_100,
    carbs_g_per_100,
    source_name,
    source_version,
    review_status
)
VALUES
    (
        510017,
        'Spinach, raw',
        U&'\83E0\83DC',
        jsonb_build_array('spinach', U&'\83E0\83DC'),
        'vegetable',
        'g',
        23.0000,
        2.8600,
        0.3900,
        3.6300,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-168462',
        'approved'
    ),
    (
        510018,
        'Sweet potato, cooked, baked in skin, flesh, without salt',
        U&'\70E4\85AF',
        jsonb_build_array('sweet potato', 'baked sweet potato', U&'\70E4\85AF'),
        'vegetable',
        'g',
        90.0000,
        2.0100,
        0.1500,
        20.7100,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-168483',
        'approved'
    ),
    (
        510019,
        'Oranges, raw, all commercial varieties',
        U&'\6A59\5B50',
        jsonb_build_array('orange', 'oranges', U&'\6A59\5B50'),
        'fruit',
        'g',
        47.0000,
        0.9400,
        0.1200,
        11.7500,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-169097',
        'approved'
    ),
    (
        510020,
        'Carrots, raw',
        U&'\80E1\841D',
        jsonb_build_array('carrot', 'carrots', U&'\80E1\841D'),
        'vegetable',
        'g',
        41.0000,
        0.9300,
        0.2400,
        9.5800,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-170393',
        'approved'
    ),
    (
        510021,
        'Tomatoes, red, ripe, raw, year round average',
        U&'\897F\7EA2\67FF',
        jsonb_build_array('tomato', 'tomatoes', U&'\897F\7EA2\67FF'),
        'vegetable',
        'g',
        18.0000,
        0.8800,
        0.2000,
        3.8900,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-170457',
        'approved'
    ),
    (
        510022,
        'Yogurt, plain, whole milk',
        U&'\539F\5473\9178',
        jsonb_build_array('plain yogurt', 'whole milk yogurt', 'yogurt', U&'\539F\5473\9178'),
        'dairy',
        'g',
        61.0000,
        3.4700,
        3.2500,
        4.6600,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-171284',
        'approved'
    ),
    (
        510023,
        'Avocados, raw, all commercial varieties',
        U&'\725B\6CB9\679C',
        jsonb_build_array('avocado', 'avocados', U&'\725B\6CB9\679C'),
        'fruit',
        'g',
        160.0000,
        2.0000,
        14.6600,
        8.5300,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-171705',
        'approved'
    ),
    (
        510024,
        'Crustaceans, shrimp, mixed species, cooked, moist heat (may contain additives to retain moisture)',
        U&'\867E\4EC1',
        jsonb_build_array('shrimp', 'cooked shrimp', U&'\867E\4EC1'),
        'seafood',
        'g',
        119.0000,
        22.7800,
        1.7000,
        1.5200,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-171971',
        'approved'
    ),
    (
        510025,
        'Chickpeas (garbanzo beans, bengal gram), mature seeds, cooked, boiled, without salt',
        U&'\9E7F\8C46',
        jsonb_build_array('chickpeas', 'chickpea', 'garbanzo beans', U&'\9E7F\8C46'),
        'legume',
        'g',
        164.0000,
        8.8600,
        2.5900,
        27.4200,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-173757',
        'approved'
    )
ON CONFLICT (nutrition_food_id) DO NOTHING;

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
    (520017, 510017, 'cup', 'g', 30.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-168462 portion-1 (1 cup=30g)', 'approved'),
    (520018, 510018, 'cup', 'g', 200.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-168483 portion-1 (1 cup=200g)', 'approved'),
    (520019, 510019, 'cup', 'g', 180.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169097 portion-1 (1 cup sections=180g)', 'approved'),
    (520020, 510020, 'cup', 'g', 128.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-170393 portion-1 (1 cup chopped=128g)', 'approved'),
    (520021, 510021, 'cup', 'g', 180.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-170457 portion-2', 'approved'),
    (520022, 510022, 'cup', 'g', 245.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-171284 portion-4 (1 cup=245g)', 'approved'),
    (520023, 510023, 'cup', 'g', 150.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-171705 portion-1 (1 cup cubes=150g)', 'approved'),
    (520024, 510024, 'oz', 'g', 28.3333, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-171971 portion-1 (3 oz=85g norm)', 'approved'),
    (520025, 510025, 'cup', 'g', 164.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-173757 portion-1 (1 cup=164g)', 'approved')
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
