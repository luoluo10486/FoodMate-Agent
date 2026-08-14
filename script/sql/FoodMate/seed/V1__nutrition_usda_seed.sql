-- M1-5 营养目录首批 seed。
-- Manual execution only. Flyway remains disabled by project policy.
--
-- Source: USDA FoodData Central API, SR Legacy, published date 2019-04-01.
-- License: USDA FoodData Central API Guide states that the data is CC0 1.0
-- Universal and requests that FoodData Central be listed as the source.
-- Every value below is the USDA value per 100 g and is traceable by FDC ID
-- in source_version. No household-unit conversion is inferred here.
-- Chinese names use PostgreSQL Unicode escapes so manual execution does not
-- depend on the operator's terminal code page.
--
-- Official references:
-- https://fdc.nal.usda.gov/api-guide.html
-- https://fdc.nal.usda.gov/data-documentation.html

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
        510001,
        'Rice, white, medium-grain, enriched, cooked',
        U&'\7C73\996D',
        jsonb_build_array('rice', 'white rice', U&'\7C73\996D', U&'\767D\7C73\996D'),
        'grain',
        'g',
        130.0000,
        2.3800,
        0.2100,
        28.5900,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-168880',
        'approved'
    ),
    (
        510002,
        'Chicken, broilers or fryers, breast, meat only, cooked, roasted',
        U&'\9E21\80F8\8089',
        jsonb_build_array('chicken breast', 'chicken', U&'\9E21\80F8\8089', U&'\9E21\80F8', U&'\9E21\8089'),
        'meat',
        'g',
        165.0000,
        31.0200,
        3.5700,
        0.0000,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-171477',
        'approved'
    ),
    (
        510003,
        'Egg, whole, cooked, hard-boiled',
        U&'\9E21\86CB',
        jsonb_build_array('egg', 'whole egg', 'hard boiled egg', U&'\9E21\86CB', U&'\6C34\716E\86CB', U&'\716E\9E21\86CB'),
        'egg',
        'g',
        155.0000,
        12.5800,
        10.6100,
        1.1200,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-173424',
        'approved'
    ),
    (
        510004,
        'Fish, salmon, Atlantic, wild, cooked, dry heat',
        U&'\4E09\6587\9C7C',
        jsonb_build_array('salmon', 'atlantic salmon', 'fish salmon', U&'\4E09\6587\9C7C', U&'\9C91\9C7C'),
        'fish',
        'g',
        182.0000,
        25.4400,
        8.1300,
        0.0000,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-171998',
        'approved'
    ),
    (
        510005,
        'Apples, raw, without skin',
        U&'\82F9\679C',
        jsonb_build_array('apple', 'apples', U&'\82F9\679C'),
        'fruit',
        'g',
        48.0000,
        0.2700,
        0.1300,
        12.7600,
        'USDA FoodData Central',
        'SR Legacy 2019-04-01 FDC-171689',
        'approved'
    )
ON CONFLICT (nutrition_food_id) DO NOTHING;

COMMIT;
