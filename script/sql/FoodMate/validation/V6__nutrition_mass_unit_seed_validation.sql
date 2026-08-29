-- V6 exact mass-unit conversion seed validation. Read-only.

SELECT conversion_id,
       nutrition_food_id,
       source_unit,
       target_unit,
       multiplier,
       source_name,
       source_version,
       review_status
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 530001 AND 530075
ORDER BY conversion_id;

SELECT COUNT(*) AS mass_unit_conversion_seed_rows
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 530001 AND 530075
  AND is_deleted = FALSE;

SELECT COUNT(*) AS invalid_mass_unit_conversion_seed_rows
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 530001 AND 530075
  AND (
      nutrition_food_id NOT BETWEEN 510001 AND 510025
      OR source_unit NOT IN ('kg', 'mg', 'lb')
      OR target_unit <> 'g'
      OR multiplier <= 0
      OR source_name <> 'FoodMate reviewed exact mass conversion'
      OR source_version NOT IN ('mass-v1-kg', 'mass-v1-mg', 'mass-v1-lb')
      OR review_status <> 'approved'
      OR is_deleted = TRUE
  );

SELECT COUNT(*) AS mass_unit_conversion_food_mismatch_rows
FROM nutrition_unit_conversions c
LEFT JOIN nutrition_foods f ON f.nutrition_food_id = c.nutrition_food_id
WHERE c.conversion_id BETWEEN 530001 AND 530075
  AND (
      f.nutrition_food_id IS NULL
      OR f.basis_unit <> 'g'
      OR f.review_status <> 'approved'
      OR f.is_deleted = TRUE
  );

SELECT COUNT(*) AS mass_unit_conversion_rule_shape_errors
FROM nutrition_unit_conversions
WHERE conversion_id BETWEEN 530001 AND 530075
  AND (
      (source_unit = 'kg' AND multiplier <> 1000.000000)
      OR (source_unit = 'mg' AND multiplier <> 0.001000)
      OR (source_unit = 'lb' AND multiplier <> 453.592370)
  );
