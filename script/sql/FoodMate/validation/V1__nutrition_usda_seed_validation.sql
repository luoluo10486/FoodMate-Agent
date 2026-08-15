-- V1 营养目录 seed 执行后校验。只读，不创建或修改对象。

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
WHERE nutrition_food_id BETWEEN 510001 AND 510005
ORDER BY nutrition_food_id;

SELECT COUNT(*) AS nutrition_seed_rows
FROM nutrition_foods
WHERE nutrition_food_id BETWEEN 510001 AND 510005
  AND is_deleted = FALSE;

SELECT COUNT(*) AS invalid_nutrition_seed_rows
FROM nutrition_foods
WHERE nutrition_food_id BETWEEN 510001 AND 510005
  AND (
      basis_unit <> 'g'
      OR review_status <> 'approved'
      OR source_name <> 'USDA FoodData Central'
      OR source_version !~ '^SR Legacy 2019-04-01 FDC-[0-9]+$'
      OR calories_kcal_per_100 < 0
      OR protein_g_per_100 < 0
      OR fat_g_per_100 < 0
      OR carbs_g_per_100 < 0
  );

SELECT nutrition_food_id,
       jsonb_array_length(aliases_json) AS alias_count
FROM nutrition_foods
WHERE nutrition_food_id BETWEEN 510001 AND 510005
ORDER BY nutrition_food_id;

SELECT COUNT(*) AS seed_unit_conversion_rows
FROM nutrition_unit_conversions
WHERE nutrition_food_id BETWEEN 510001 AND 510005
  AND is_deleted = FALSE;
