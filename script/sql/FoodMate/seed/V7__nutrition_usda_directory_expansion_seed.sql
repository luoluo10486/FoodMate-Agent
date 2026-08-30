-- M2 nutrition directory expansion seed.
-- Manual execution only. Flyway remains disabled by project policy.
--
-- Source: USDA FoodData Central SR Legacy, published 2019-04-01.
-- Nutrition values are copied from food_nutrient.csv (per 100 g) for the
-- FDC IDs below. Portion multipliers are normalized from food_portion.csv and
-- retain the original portion sequence in source_version.
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
    (510026, 'Strawberries, raw', U&'\8349\8393', jsonb_build_array('strawberry', 'strawberries', U&'\8349\8393'), 'fruit', 'g', 32.0000, 0.6700, 0.3000, 7.6800, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-167762', 'approved'),
    (510027, 'Pork, fresh, loin, tenderloin, separable lean and fat, cooked, broiled', U&'\732A\91CC\810A\8089', jsonb_build_array('pork tenderloin', 'pork', U&'\732A\91CC\810A\8089'), 'meat', 'g', 201.0000, 29.8600, 8.1100, 0.0000, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-167904', 'approved'),
    (510028, 'Kale, raw', U&'\7FBD\8863\7518\84DD', jsonb_build_array('kale', U&'\7FBD\8863\7518\84DD'), 'vegetable', 'g', 35.0000, 2.9200, 1.4900, 4.4200, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-168421', 'approved'),
    (510029, 'Quinoa, cooked', U&'\85DC\9EA6', jsonb_build_array('quinoa', 'cooked quinoa', U&'\85DC\9EA6'), 'grain', 'g', 120.0000, 4.4000, 1.9200, 21.3000, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-168917', 'approved'),
    (510030, 'Pears, raw', U&'\68A8', jsonb_build_array('pear', 'pears', U&'\68A8'), 'fruit', 'g', 57.0000, 0.3600, 0.1400, 15.2300, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169118', 'approved'),
    (510031, 'Pineapple, raw, all varieties', U&'\83E0\841D', jsonb_build_array('pineapple', U&'\83E0\841D'), 'fruit', 'g', 50.0000, 0.5400, 0.1200, 13.1200, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169124', 'approved'),
    (510032, 'Beans, snap, green, cooked, boiled, drained, without salt', U&'\56DB\5B63\8C46', jsonb_build_array('green beans', 'string beans', U&'\56DB\5B63\8C46'), 'vegetable', 'g', 35.0000, 1.8900, 0.2800, 7.8800, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169141', 'approved'),
    (510033, 'Eggplant, raw', U&'\8304\5B50', jsonb_build_array('eggplant', U&'\8304\5B50'), 'vegetable', 'g', 25.0000, 0.9800, 0.1800, 5.8800, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169228', 'approved'),
    (510034, 'Garlic, raw', U&'\5927\849C', jsonb_build_array('garlic', U&'\5927\849C'), 'vegetable', 'g', 149.0000, 6.3600, 0.5000, 33.0600, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169230', 'approved'),
    (510035, 'Lettuce, green leaf, raw', U&'\751F\83DC', jsonb_build_array('lettuce', 'green leaf lettuce', U&'\751F\83DC'), 'vegetable', 'g', 15.0000, 1.3600, 0.1500, 2.8700, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169249', 'approved'),
    (510036, 'Mushrooms, white, raw', U&'\8611\83C7', jsonb_build_array('mushroom', 'white mushrooms', U&'\8611\83C7'), 'vegetable', 'g', 22.0000, 3.0900, 0.3400, 3.2600, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169251', 'approved'),
    (510037, 'Rice, brown, long-grain, cooked (Includes foods for USDA''s Food Distribution Program)', U&'\7CD9\7C73', jsonb_build_array('brown rice', 'cooked brown rice', U&'\7CD9\7C73'), 'grain', 'g', 123.0000, 2.7400, 0.9700, 25.5800, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169704', 'approved'),
    (510038, 'Mangos, raw', U&'\8292\679C', jsonb_build_array('mango', 'mangos', U&'\8292\679C'), 'fruit', 'g', 60.0000, 0.8200, 0.3800, 14.9800, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169910', 'approved'),
    (510039, 'Cabbage, raw', U&'\5377\5FC3\83DC', jsonb_build_array('cabbage', U&'\5377\5FC3\83DC'), 'vegetable', 'g', 25.0000, 1.2800, 0.1000, 5.8000, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169975', 'approved'),
    (510040, 'Cauliflower, raw', U&'\83DC\82B1', jsonb_build_array('cauliflower', U&'\83DC\82B1'), 'vegetable', 'g', 25.0000, 1.9200, 0.2800, 4.9700, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169986', 'approved'),
    (510041, 'Celery, raw', U&'\82B9\83DC', jsonb_build_array('celery', U&'\82B9\83DC'), 'vegetable', 'g', 14.0000, 0.6900, 0.1700, 2.9700, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169988', 'approved'),
    (510042, 'Corn, sweet, yellow, cooked, boiled, drained, without salt', U&'\7389\7C73', jsonb_build_array('corn', 'sweet corn', U&'\7389\7C73'), 'grain', 'g', 96.0000, 3.4100, 1.5000, 20.9800, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-169999', 'approved'),
    (510043, 'Nuts, almonds, dry roasted, without salt added', U&'\674F\4EC1', jsonb_build_array('almond', 'almonds', U&'\674F\4EC1'), 'nut', 'g', 598.0000, 20.9600, 52.5400, 21.0100, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-170158', 'approved'),
    (510044, 'Peas, green, cooked, boiled, drained, without salt', U&'\8C4C\8C46', jsonb_build_array('peas', 'green peas', U&'\8C4C\8C46'), 'vegetable', 'g', 84.0000, 5.3600, 0.2200, 15.6300, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-170420', 'approved'),
    (510045, 'Turkey, whole, light meat, meat and skin, cooked, roasted', U&'\706B\9E21\8089', jsonb_build_array('turkey', 'turkey meat', U&'\706B\9E21\8089'), 'meat', 'g', 177.0000, 29.5500, 5.5700, 0.0500, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-171089', 'approved'),
    (510046, 'Blueberries, raw', U&'\84DD\8393', jsonb_build_array('blueberry', 'blueberries', U&'\84DD\8393'), 'fruit', 'g', 57.0000, 0.7400, 0.3300, 14.4900, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-171711', 'approved'),
    (510047, 'Fish, tuna, light, canned in water, without salt, drained solids', U&'\91D1\67AA\9C7C', jsonb_build_array('tuna', 'canned tuna', U&'\91D1\67AA\9C7C'), 'fish', 'g', 116.0000, 25.5100, 0.8200, 0.0000, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-171986', 'approved'),
    (510048, 'Peanuts, all types, raw', U&'\82B1\751F', jsonb_build_array('peanut', 'peanuts', U&'\82B1\751F'), 'nut', 'g', 567.0000, 25.8000, 49.2400, 16.1300, 'USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-172430', 'approved')
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
    (520026, 510026, 'cup', 'g', 152.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-167762 portion-1', 'approved'),
    (520027, 510027, 'oz', 'g', 28.3333, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-167904 portion-1', 'approved'),
    (520028, 510028, 'cup', 'g', 21.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-168421 portion-1', 'approved'),
    (520029, 510029, 'cup', 'g', 185.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-168917 portion-1', 'approved'),
    (520030, 510030, 'cup', 'g', 140.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169118 portion-1', 'approved'),
    (520031, 510031, 'cup', 'g', 165.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169124 portion-1', 'approved'),
    (520032, 510032, 'cup', 'g', 125.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169141 portion-1', 'approved'),
    (520033, 510033, 'cup', 'g', 82.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169228 portion-1', 'approved'),
    (520034, 510034, 'clove', 'g', 3.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169230 portion-3', 'approved'),
    (520035, 510035, 'cup', 'g', 36.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169249 portion-1', 'approved'),
    (520036, 510036, 'cup', 'g', 70.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169251 portion-1', 'approved'),
    (520037, 510037, 'cup', 'g', 202.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169704 portion-1', 'approved'),
    (520038, 510038, 'cup', 'g', 165.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169910 portion-1', 'approved'),
    (520039, 510039, 'cup', 'g', 89.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169975 portion-1', 'approved'),
    (520040, 510040, 'cup', 'g', 107.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169986 portion-1', 'approved'),
    (520041, 510041, 'cup', 'g', 101.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169988 portion-1', 'approved'),
    (520042, 510042, 'cup', 'g', 149.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-169999 portion-4', 'approved'),
    (520043, 510043, 'cup', 'g', 138.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-170158 portion-1', 'approved'),
    (520044, 510044, 'cup', 'g', 160.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-170420 portion-1', 'approved'),
    (520045, 510045, 'oz', 'g', 28.3333, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-171089 portion-1', 'approved'),
    (520046, 510046, 'cup', 'g', 148.0000, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-171711 portion-1', 'approved'),
    (520047, 510047, 'oz', 'g', 28.3333, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-171986 portion-1', 'approved'),
    (520048, 510048, 'oz', 'g', 28.3500, 'USDA FoodData Central API foodPortions', 'SR Legacy 2019-04-01 FDC-172430 portion-1', 'approved')
ON CONFLICT (conversion_id) DO NOTHING;

COMMIT;
