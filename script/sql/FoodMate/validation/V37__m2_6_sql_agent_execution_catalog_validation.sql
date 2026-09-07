-- V37 SQL Agent catalog 增量种子校验，只读。

SELECT schema_catalog_id, table_name, field_name, is_sensitive
FROM schema_catalogs
WHERE datasource_id = 730001
  AND schema_catalog_id IN (
      730106,
      730138, 730139, 730140, 730146, 730147, 730148, 730149,
      730150, 730161, 730162, 730163, 730164, 730165, 730166, 730167, 730168
  )
ORDER BY schema_catalog_id;

SELECT COUNT(*) AS missing_execution_catalog_fields
FROM (
    VALUES
        ('food_logs', 'meal_plan_meal_id'),
        ('meal_plan_meals', 'meal_plan_meal_id'),
        ('meal_plan_meals', 'meal_plan_id'),
        ('meal_plan_meals', 'user_id'),
        ('meal_plan_meals', 'day_index'),
        ('meal_plan_meals', 'meal_type'),
        ('meal_plan_meals', 'meal_name'),
        ('meal_plan_meals', 'is_deleted'),
        ('shopping_list_items', 'shopping_list_item_id'),
        ('shopping_list_items', 'shopping_list_id'),
        ('shopping_list_items', 'meal_plan_id'),
        ('shopping_list_items', 'user_id'),
        ('shopping_list_items', 'item_name'),
        ('shopping_list_items', 'amount'),
        ('shopping_list_items', 'unit'),
        ('shopping_list_items', 'purchased'),
        ('shopping_list_items', 'is_deleted')
) AS required(table_name, field_name)
LEFT JOIN schema_catalogs catalog
       ON catalog.datasource_id = 730001
      AND catalog.table_name = required.table_name
      AND catalog.field_name = required.field_name
      AND catalog.is_deleted = FALSE
WHERE catalog.schema_catalog_id IS NULL;

SELECT COUNT(*) AS sensitive_execution_catalog_fields
FROM schema_catalogs
WHERE datasource_id = 730001
  AND table_name IN ('meal_plan_meals', 'shopping_list_items')
  AND is_deleted = FALSE
  AND is_sensitive = TRUE;
