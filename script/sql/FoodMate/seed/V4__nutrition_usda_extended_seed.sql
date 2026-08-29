-- M1-5 nutrition directory extension seed.
-- Manual execution only. Flyway remains disabled by project policy.
--
-- Source: USDA FoodData Central SR Legacy, published 2019-04-01.
-- The values below are the USDA values per 100 g. Every row keeps its FDC ID
-- in source_version so the catalog remains traceable and reviewable.
-- Household-unit rules are kept in the separate portion section below and are
-- derived only from the matching USDA food_portion rows.
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
        510006,
        'Oats (Includes foods for USDA''s Food Distribution Program)',
        U&'\71D5\9EA6',
        jsonb_build_array('oats', 'oatmeal', U&'\71D5\9EA6'),
        'grain',
        'g',
        389.0000,
        16.8900,
        6.9000,
        66.2700,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-169705',
        'approved'
    ),
    (
        510007,
        'Pasta, cooked, enriched, without added salt',
        U&'\610F\5927\5229\9762',
        jsonb_build_array('pasta', 'cooked pasta', U&'\610F\5927\5229\9762'),
        'grain',
        'g',
        158.0000,
        5.8000,
        0.9300,
        30.8600,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-169737',
        'approved'
    ),
    (
        510008,
        'Broccoli, cooked, boiled, drained, without salt',
        U&'\897F\5170\82B1',
        jsonb_build_array('broccoli', 'cooked broccoli', U&'\897F\5170\82B1'),
        'vegetable',
        'g',
        35.0000,
        2.3800,
        0.4100,
        7.1800,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-169967',
        'approved'
    ),
    (
        510009,
        'Potatoes, baked, flesh and skin, without salt',
        U&'\571F\8C46',
        jsonb_build_array('potato', 'baked potato', U&'\571F\8C46'),
        'vegetable',
        'g',
        93.0000,
        2.5000,
        0.1300,
        21.1500,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-170093',
        'approved'
    ),
    (
        510010,
        'Milk, whole, 3.25% milkfat, with added vitamin D',
        U&'\725B\5976',
        jsonb_build_array('whole milk', 'milk', U&'\725B\5976'),
        'dairy',
        'g',
        61.0000,
        3.1500,
        3.2500,
        4.8000,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-171265',
        'approved'
    ),
    (
        510011,
        'Beef, ground, 90% lean meat / 10% fat, crumbles, cooked, pan-browned',
        U&'\725B\8089',
        jsonb_build_array('ground beef', 'beef', U&'\725B\8089'),
        'meat',
        'g',
        230.0000,
        28.4500,
        12.0400,
        0.0000,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-171794',
        'approved'
    ),
    (
        510012,
        'Lentils, mature seeds, cooked, boiled, without salt',
        U&'\5C0F\6241\8C46',
        jsonb_build_array('lentils', 'cooked lentils', U&'\5C0F\6241\8C46'),
        'legume',
        'g',
        116.0000,
        9.0200,
        0.3800,
        20.1300,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-172421',
        'approved'
    ),
    (
        510013,
        'Tofu, firm, prepared with calcium sulfate and magnesium chloride (nigari)',
        U&'\8C46\8150',
        jsonb_build_array('tofu', 'firm tofu', U&'\8C46\8150'),
        'soy',
        'g',
        78.0000,
        9.0400,
        4.1700,
        2.8500,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-172448',
        'approved'
    ),
    (
        510014,
        'Bread, whole-wheat, commercially prepared',
        U&'\5168\9EA6\9762\5305',
        jsonb_build_array('whole wheat bread', 'wheat bread', U&'\5168\9EA6\9762\5305'),
        'grain',
        'g',
        252.0000,
        12.4500,
        3.5000,
        42.7100,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-172688',
        'approved'
    ),
    (
        510015,
        'Beans, black, mature seeds, cooked, boiled, without salt',
        U&'\9ED1\8C46',
        jsonb_build_array('black beans', 'black bean', U&'\9ED1\8C46'),
        'legume',
        'g',
        132.0000,
        8.8600,
        0.5400,
        23.7100,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-173735',
        'approved'
    ),
    (
        510016,
        'Bananas, raw',
        U&'\9999\8549',
        jsonb_build_array('banana', 'bananas', U&'\9999\8549'),
        'fruit',
        'g',
        89.0000,
        1.0900,
        0.3300,
        22.8400,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-173944',
        'approved'
    )
ON CONFLICT (nutrition_food_id) DO NOTHING;

-- Each multiplier is normalized to one source unit. For USDA portions whose
-- amount is below one, the source amount and normalized multiplier are kept in
-- source_version rather than silently treating the original amount as one.
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
        520006,
        510006,
        'cup',
        'g',
        156.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-169705 portion-1',
        'approved'
    ),
    (
        520007,
        510007,
        'cup',
        'g',
        124.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-169737 portion-1',
        'approved'
    ),
    (
        520008,
        510008,
        'cup',
        'g',
        156.0000,
        'USDA FoodData Central API foodPortions',
    'SR Legacy 2019-04-01 FDC-169967 portion-1 0.5cup=78g norm',
        'approved'
    ),
    (
        520009,
        510009,
        'medium',
        'g',
        173.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-170093 portion-3',
        'approved'
    ),
    (
        520010,
        510010,
        'cup',
        'g',
        244.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-171265 portion-1',
        'approved'
    ),
    (
        520011,
        510011,
        'oz',
        'g',
        28.3333,
        'USDA FoodData Central API foodPortions',
    'SR Legacy 2019-04-01 FDC-171794 portion-1 3oz=85g norm',
        'approved'
    ),
    (
        520012,
        510012,
        'cup',
        'g',
        198.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-172421 portion-1',
        'approved'
    ),
    (
        520013,
        510013,
        'cup',
        'g',
        252.0000,
        'USDA FoodData Central API foodPortions',
    'SR Legacy 2019-04-01 FDC-172448 portion-1 0.5cup=126g norm',
        'approved'
    ),
    (
        520014,
        510014,
        'slice',
        'g',
        32.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-172688 portion-1',
        'approved'
    ),
    (
        520015,
        510015,
        'cup',
        'g',
        172.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-173735 portion-1',
        'approved'
    ),
    (
        520016,
        510016,
        'medium',
        'g',
        118.0000,
        'USDA FoodData Central API foodPortions',
        'SR Legacy 2019-04-01 FDC-173944 portion-5',
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
