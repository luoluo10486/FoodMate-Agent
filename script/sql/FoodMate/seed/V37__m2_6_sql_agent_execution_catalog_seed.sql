-- M2-6 SQL Agent catalog 增量种子：计划餐次、购物项和饮食记录关联字段。
-- 仅补充已批准的只读字段，不修改业务数据；人工执行前请先核对 datasource_id。
BEGIN;

INSERT INTO schema_catalogs
    (schema_catalog_id, datasource_id, schema_name, table_name, field_name, field_desc, data_type, is_sensitive, sample_sql, created_by, updated_by)
VALUES
    (730106, 730001, 'public', 'food_logs', 'meal_plan_meal_id', 'Linked meal plan slot identifier', 'bigint', FALSE, NULL, 0, 0),
    (730138, 730001, 'public', 'meal_plan_meals', 'meal_plan_meal_id', 'Stable meal slot identifier', 'bigint', FALSE, NULL, 0, 0),
    (730139, 730001, 'public', 'meal_plan_meals', 'meal_plan_id', 'Parent meal plan identifier', 'bigint', FALSE, NULL, 0, 0),
    (730140, 730001, 'public', 'meal_plan_meals', 'user_id', 'Owner user identifier used by the Java scope guard', 'bigint', FALSE, NULL, 0, 0),
    (730146, 730001, 'public', 'meal_plan_meals', 'day_index', 'Zero-based plan day index', 'integer', FALSE, NULL, 0, 0),
    (730147, 730001, 'public', 'meal_plan_meals', 'meal_type', 'Meal category', 'varchar', FALSE, NULL, 0, 0),
    (730148, 730001, 'public', 'meal_plan_meals', 'meal_name', 'Meal display name', 'varchar', FALSE, NULL, 0, 0),
    (730149, 730001, 'public', 'meal_plan_meals', 'is_deleted', 'Soft-delete marker', 'boolean', FALSE, NULL, 0, 0),
    (730150, 730001, 'public', 'shopping_list_items', 'shopping_list_item_id', 'Stable shopping item identifier', 'bigint', FALSE, NULL, 0, 0),
    (730161, 730001, 'public', 'shopping_list_items', 'shopping_list_id', 'Parent shopping list identifier', 'bigint', FALSE, NULL, 0, 0),
    (730162, 730001, 'public', 'shopping_list_items', 'meal_plan_id', 'Parent meal plan identifier', 'bigint', FALSE, NULL, 0, 0),
    (730163, 730001, 'public', 'shopping_list_items', 'user_id', 'Owner user identifier used by the Java scope guard', 'bigint', FALSE, NULL, 0, 0),
    (730164, 730001, 'public', 'shopping_list_items', 'item_name', 'Shopping item name', 'varchar', FALSE, NULL, 0, 0),
    (730165, 730001, 'public', 'shopping_list_items', 'amount', 'Required amount', 'numeric', FALSE, NULL, 0, 0),
    (730166, 730001, 'public', 'shopping_list_items', 'unit', 'Recorded unit', 'varchar', FALSE, NULL, 0, 0),
    (730167, 730001, 'public', 'shopping_list_items', 'purchased', 'Whether the item is checked as purchased', 'boolean', FALSE, NULL, 0, 0),
    (730168, 730001, 'public', 'shopping_list_items', 'is_deleted', 'Soft-delete marker', 'boolean', FALSE, NULL, 0, 0)
ON CONFLICT (schema_catalog_id) DO NOTHING;

COMMIT;
