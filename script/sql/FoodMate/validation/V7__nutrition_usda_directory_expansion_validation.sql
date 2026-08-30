-- V7 nutrition directory expansion validation. Read-only.

SELECT nutrition_food_id,
       standard_name,
       chinese_name,
       basis_unit,
       calories_kcal_per_100,
       protein_g_per_100,
       fat_g_per_100,
       carbs_g_per_100,
       source_name,
       source_version,
       review_status
FROM nutrition_foods
WHERE nutrition_food_id BETWEEN 510026 AND 510048
ORDER BY nutrition_food_id;

SELECT COUNT(*) AS expansion_nutrition_seed_rows
FROM nutrition_foods
WHERE nutrition_food_id BETWEEN 510026 AND 510048
  AND is_deleted = FALSE;

SELECT COUNT(*) AS invalid_expansion_nutrition_seed_rows
FROM nutrition_foods
WHERE nutrition_food_id BETWEEN 510026 AND 510048
  AND (
      basis_unit <> 'g'
      OR review_status <> 'approved'
      OR source_name <> 'USDA FoodData Central'
      OR source_version !~ '^SR Legacy 2019-04-01 FDC-[0-9]+$'
      OR calories_kcal_per_100 < 0
      OR protein_g_per_100 < 0
      OR fat_g_per_100 < 0
      OR carbs_g_per_100 < 0
      OR is_deleted = TRUE
  );

SELECT conversion_id,
       nutrition_food_id,
       source_unit,
       target_unit,
       multiplier,
       source_name,
       source_version,
       review_status
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 520026 AND 520048
ORDER BY conversion_id;

SELECT COUNT(*) AS expansion_unit_conversion_seed_rows
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 520026 AND 520048
  AND is_deleted = FALSE;

SELECT COUNT(*) AS invalid_expansion_unit_conversion_seed_rows
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 520026 AND 520048
  AND (
      nutrition_food_id NOT BETWEEN 510026 AND 510048
      OR target_unit <> 'g'
      OR multiplier <= 0
      OR source_name <> 'USDA FoodData Central API foodPortions'
      OR source_version !~ '^SR Legacy 2019-04-01 FDC-[0-9]+ portion-[0-9]+'
      OR review_status <> 'approved'
      OR is_deleted = TRUE
  );

SELECT COUNT(*) AS expansion_conversion_food_mismatch_rows
FROM nutrition_unit_conversions c
LEFT JOIN nutrition_foods f ON f.nutrition_food_id = c.nutrition_food_id
WHERE c.conversion_id BETWEEN 520026 AND 520048
  AND (
      f.nutrition_food_id IS NULL
      OR f.is_deleted = TRUE
      OR f.review_status <> 'approved'
  );

SELECT COUNT(*) AS expansion_conversion_rule_shape_errors
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 520026 AND 520048
  AND (
      (conversion_id IN (520027, 520045, 520047) AND multiplier <> 28.3333)
      OR (conversion_id = 520048 AND multiplier <> 28.3500)
  );
