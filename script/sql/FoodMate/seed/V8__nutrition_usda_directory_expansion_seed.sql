-- M2 nutrition directory expansion seed (V8).
-- Manual execution only. Flyway remains disabled by project policy.
--
-- Source: USDA FoodData Central SR Legacy, published 2019-04-01.
-- Nutrition values are copied from food_nutrient.csv (per 100 g) for the
-- FDC IDs below. Portion multipliers are normalized from food_portion.csv;
-- source_version retains the FDC ID and original portion sequence.
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
    (510049, 'Onions, raw', U&'\6D0B\8471', jsonb_build_array('onion', 'onions', U&'\6D0B\8471'), 'vegetable', 'g', 40.0000, 1.1000, 0.1000, 9.3400, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-170000', 'approved'),
    (510050, 'Potatoes, flesh and skin, raw', U&'\571F\8C46', jsonb_build_array('potato', 'potatoes', U&'\571F\8C46'), 'vegetable', 'g', 77.0000, 2.0500, 0.0900, 17.4900, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-170026', 'approved'),
    (510051, 'Broccoli, flower clusters, raw', U&'\897F\5170\82B1', jsonb_build_array('broccoli', U&'\897F\5170\82B1'), 'vegetable', 'g', 28.0000, 2.9800, 0.3500, 5.0600, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169330', 'approved'),
    (510052, 'Beef, ground, 70% lean meat / 30% fat, crumbles, cooked, pan-browned', U&'\725B\8089', jsonb_build_array('ground beef', 'beef', U&'\725B\8089'), 'meat', 'g', 270.0000, 25.5600, 17.8600, 0.0000, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169473', 'approved'),
    (510053, 'Cheese, mozzarella, whole milk', U&'\9A6C\82CF\91CC\62C9\5976\916A', jsonb_build_array('mozzarella', 'mozzarella cheese', U&'\9A6C\82CF\91CC\62C9\5976\916A'), 'dairy', 'g', 299.0000, 22.1700, 22.1400, 2.4000, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-170845', 'approved'),
    (510054, 'Tofu, soft, prepared with calcium sulfate and magnesium chloride (nigari)', U&'\5AE9\8C46\8150', jsonb_build_array('soft tofu', 'tofu', U&'\5AE9\8C46\8150'), 'legume', 'g', 61.0000, 7.1700, 3.6900, 1.1800, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-172449', 'approved'),
    (510055, 'Fish, salmon, Atlantic, farmed, cooked, dry heat', U&'\517B\6B96\4E09\6587\9C7C', jsonb_build_array('farmed salmon', 'salmon', U&'\4E09\6587\9C7C'), 'fish', 'g', 206.0000, 22.1000, 12.3500, 0.0000, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-175168', 'approved'),
    (510056, 'Chicken, broilers or fryers, thigh, meat only, cooked, roasted', U&'\9E21\817F\8089', jsonb_build_array('chicken thigh', 'chicken', U&'\9E21\817F\8089'), 'meat', 'g', 179.0000, 24.7600, 8.1500, 0.0000, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-172388', 'approved'),
    (510057, 'Yogurt, Greek, plain, nonfat (Includes foods for USDA''s Food Distribution Program)', U&'\8131\8102\5E0C\814A\9178\5976', jsonb_build_array('greek yogurt', 'plain nonfat yogurt', U&'\8131\8102\5E0C\814A\9178\5976'), 'dairy', 'g', 59.0000, 10.1900, 0.3900, 3.6000, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-170894', 'approved'),
    (510058, 'Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)', U&'\5E26\76AE\82F9\679C', jsonb_build_array('apple with skin', 'apples', U&'\5E26\76AE\82F9\679C'), 'fruit', 'g', 52.0000, 0.2600, 0.1700, 13.8100, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-171688', 'approved'),
    (510059, 'Lentils, raw', U&'\751F\6241\8C46', jsonb_build_array('raw lentils', 'lentils', U&'\751F\6241\8C46'), 'legume', 'g', 352.0000, 24.6300, 1.0600, 63.3500, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-172420', 'approved'),
    (510060, 'Beans, black, mature seeds, raw', U&'\751F\9ED1\8C46', jsonb_build_array('raw black beans', 'black beans', U&'\751F\9ED1\8C46'), 'legume', 'g', 341.0000, 21.6000, 1.4200, 62.3600, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-173734', 'approved')
ON CONFLICT (nutrition_food_id) DO UPDATE SET
    standard_name = EXCLUDED.standard_name,
    chinese_name = EXCLUDED.chinese_name,
    aliases_json = EXCLUDED.aliases_json,
    category = EXCLUDED.category,
    basis_unit = EXCLUDED.basis_unit,
    calories_kcal_per_100 = EXCLUDED.calories_kcal_per_100,
    protein_g_per_100 = EXCLUDED.protein_g_per_100,
    fat_g_per_100 = EXCLUDED.fat_g_per_100,
    carbs_g_per_100 = EXCLUDED.carbs_g_per_100,
    source_name = EXCLUDED.source_name,
    source_version = EXCLUDED.source_version,
    review_status = EXCLUDED.review_status,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = CURRENT_TIMESTAMP;

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
    (520049, 510049, 'cup', 'g', 160.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-170000 portion-1', 'approved'),
    (520050, 510050, 'medium', 'g', 213.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-170026 portion-3', 'approved'),
    (520051, 510051, 'cup', 'g', 71.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169330 portion-1', 'approved'),
    (520052, 510052, 'oz', 'g', 28.3333, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169473 portion-1 (3 oz=85g normalized)', 'approved'),
    (520053, 510053, 'oz', 'g', 28.3500, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-170845 portion-2', 'approved'),
    (520054, 510054, 'cup', 'g', 248.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-172449 portion-2', 'approved'),
    (520055, 510055, 'oz', 'g', 28.3333, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-175168 portion-1 (3 oz=85g normalized)', 'approved'),
    (520056, 510056, 'oz', 'g', 28.3333, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-172388 portion-1 (3 oz=85g normalized)', 'approved'),
    (520057, 510057, 'container', 'g', 170.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-170894 portion-1', 'approved'),
    (520058, 510058, 'medium', 'g', 182.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-171688 portion-4', 'approved'),
    (520059, 510059, 'cup', 'g', 192.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-172420 portion-1', 'approved'),
    (520060, 510060, 'cup', 'g', 194.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-173734 portion-1', 'approved')
ON CONFLICT (conversion_id) DO NOTHING;

COMMIT;
