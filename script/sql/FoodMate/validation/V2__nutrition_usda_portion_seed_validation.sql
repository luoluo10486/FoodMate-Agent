-- V2 nutrition unit conversion seed validation. Read-only.

SELECT conversion_id,
       nutrition_food_id,
       source_unit,
       target_unit,
       multiplier,
       source_name,
       source_version,
       review_status
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 520001 AND 520005
ORDER BY conversion_id;

SELECT COUNT(*) AS nutrition_unit_conversion_seed_rows
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 520001 AND 520005
  AND is_deleted = FALSE;

SELECT COUNT(*) AS invalid_nutrition_unit_conversion_seed_rows
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 520001 AND 520005
  AND (
      nutrition_food_id NOT BETWEEN 510001 AND 510005
      OR target_unit <> 'g'
      OR multiplier <= 0
      OR source_name <> 'USDA FoodData Central API foodPortions'
      OR source_version !~ '^SR Legacy 2019-04-01 FDC-[0-9]+ portion-[0-9]+'
      OR review_status <> 'approved'
      OR is_deleted = TRUE
  );
