-- M2-2 local business seed: register the approved read-only FoodMate catalog.
-- Manual execution only. This seed adds metadata and never changes business rows.
BEGIN;

INSERT INTO data_sources
    (datasource_id, name, db_type, purpose, visibility, status, readonly, connection_ref, created_by, updated_by)
VALUES
    (730001, 'foodmate-local-readonly', 'postgresql', 'FoodMate local SQL Agent read-only catalog', 'restricted', 'active', TRUE, 'foodmate-primary-local', 0, 0)
ON CONFLICT (datasource_id) DO NOTHING;

INSERT INTO schema_catalogs
    (schema_catalog_id, datasource_id, schema_name, table_name, field_name, field_desc, data_type, is_sensitive, sample_sql, created_by, updated_by)
VALUES
    (730101, 730001, 'public', 'food_logs', 'food_log_id', 'Food log identifier', 'bigint', FALSE, NULL, 0, 0),
    (730102, 730001, 'public', 'food_logs', 'user_id', 'Owner user identifier used by the Java scope guard', 'bigint', FALSE, NULL, 0, 0),
    (730103, 730001, 'public', 'food_logs', 'meal_time', 'Meal timestamp', 'timestamptz', FALSE, NULL, 0, 0),
    (730104, 730001, 'public', 'food_logs', 'meal_type', 'Meal category', 'varchar', FALSE, NULL, 0, 0),
    (730105, 730001, 'public', 'food_logs', 'is_deleted', 'Soft-delete marker', 'boolean', FALSE, NULL, 0, 0),
    (730111, 730001, 'public', 'food_log_items', 'food_log_item_id', 'Food log item identifier', 'bigint', FALSE, NULL, 0, 0),
    (730112, 730001, 'public', 'food_log_items', 'food_log_id', 'Parent food log identifier', 'bigint', FALSE, NULL, 0, 0),
    (730113, 730001, 'public', 'food_log_items', 'raw_name', 'User-provided food name', 'varchar', FALSE, NULL, 0, 0),
    (730114, 730001, 'public', 'food_log_items', 'amount', 'Recorded amount', 'numeric', FALSE, NULL, 0, 0),
    (730115, 730001, 'public', 'food_log_items', 'unit', 'Recorded unit', 'varchar', FALSE, NULL, 0, 0),
    (730116, 730001, 'public', 'food_log_items', 'calories_kcal', 'Calculated calories', 'numeric', FALSE, NULL, 0, 0),
    (730117, 730001, 'public', 'food_log_items', 'protein_g', 'Calculated protein', 'numeric', FALSE, NULL, 0, 0),
    (730118, 730001, 'public', 'food_log_items', 'fat_g', 'Calculated fat', 'numeric', FALSE, NULL, 0, 0),
    (730119, 730001, 'public', 'food_log_items', 'carbs_g', 'Calculated carbohydrates', 'numeric', FALSE, NULL, 0, 0),
    (730120, 730001, 'public', 'food_log_items', 'nutrition_status', 'Nutrition matching status', 'varchar', FALSE, NULL, 0, 0),
    (730121, 730001, 'public', 'food_log_items', 'is_deleted', 'Soft-delete marker', 'boolean', FALSE, NULL, 0, 0),
    (730131, 730001, 'public', 'meal_plans', 'meal_plan_id', 'Meal plan identifier', 'bigint', FALSE, NULL, 0, 0),
    (730132, 730001, 'public', 'meal_plans', 'user_id', 'Owner user identifier used by the Java scope guard', 'bigint', FALSE, NULL, 0, 0),
    (730133, 730001, 'public', 'meal_plans', 'plan_name', 'Meal plan name', 'varchar', FALSE, NULL, 0, 0),
    (730134, 730001, 'public', 'meal_plans', 'days', 'Number of plan days', 'integer', FALSE, NULL, 0, 0),
    (730135, 730001, 'public', 'meal_plans', 'status', 'Meal plan status', 'varchar', FALSE, NULL, 0, 0),
    (730136, 730001, 'public', 'meal_plans', 'updated_at', 'Last update timestamp', 'timestamptz', FALSE, NULL, 0, 0),
    (730137, 730001, 'public', 'meal_plans', 'is_deleted', 'Soft-delete marker', 'boolean', FALSE, NULL, 0, 0),
    (730141, 730001, 'public', 'shopping_lists', 'shopping_list_id', 'Shopping list identifier', 'bigint', FALSE, NULL, 0, 0),
    (730142, 730001, 'public', 'shopping_lists', 'meal_plan_id', 'Parent meal plan identifier', 'bigint', FALSE, NULL, 0, 0),
    (730143, 730001, 'public', 'shopping_lists', 'user_id', 'Owner user identifier used by the Java scope guard', 'bigint', FALSE, NULL, 0, 0),
    (730144, 730001, 'public', 'shopping_lists', 'status', 'Shopping list status', 'varchar', FALSE, NULL, 0, 0),
    (730145, 730001, 'public', 'shopping_lists', 'is_deleted', 'Soft-delete marker', 'boolean', FALSE, NULL, 0, 0),
    (730151, 730001, 'public', 'nutrition_foods', 'nutrition_food_id', 'Nutrition food identifier', 'bigint', FALSE, NULL, 0, 0),
    (730152, 730001, 'public', 'nutrition_foods', 'standard_name', 'Standard food name', 'varchar', FALSE, NULL, 0, 0),
    (730153, 730001, 'public', 'nutrition_foods', 'chinese_name', 'Chinese food name', 'varchar', FALSE, NULL, 0, 0),
    (730154, 730001, 'public', 'nutrition_foods', 'basis_unit', 'Nutrition basis unit', 'varchar', FALSE, NULL, 0, 0),
    (730155, 730001, 'public', 'nutrition_foods', 'calories_kcal_per_100', 'Calories per 100 basis units', 'numeric', FALSE, NULL, 0, 0),
    (730156, 730001, 'public', 'nutrition_foods', 'protein_g_per_100', 'Protein per 100 basis units', 'numeric', FALSE, NULL, 0, 0),
    (730157, 730001, 'public', 'nutrition_foods', 'fat_g_per_100', 'Fat per 100 basis units', 'numeric', FALSE, NULL, 0, 0),
    (730158, 730001, 'public', 'nutrition_foods', 'carbs_g_per_100', 'Carbohydrates per 100 basis units', 'numeric', FALSE, NULL, 0, 0),
    (730159, 730001, 'public', 'nutrition_foods', 'review_status', 'Nutrition review status', 'varchar', FALSE, NULL, 0, 0),
    (730160, 730001, 'public', 'nutrition_foods', 'is_deleted', 'Soft-delete marker', 'boolean', FALSE, NULL, 0, 0),
    (730171, 730001, 'public', 'knowledge_documents', 'document_id', 'Knowledge document identifier', 'bigint', FALSE, NULL, 0, 0),
    (730172, 730001, 'public', 'knowledge_documents', 'tenant_id', 'Public tenant scope identifier', 'bigint', FALSE, NULL, 0, 0),
    (730173, 730001, 'public', 'knowledge_documents', 'title', 'Published document title', 'varchar', FALSE, NULL, 0, 0),
    (730174, 730001, 'public', 'knowledge_documents', 'status', 'Knowledge document visibility status', 'varchar', FALSE, NULL, 0, 0),
    (730175, 730001, 'public', 'knowledge_documents', 'version', 'Published document version', 'varchar', FALSE, NULL, 0, 0),
    (730176, 730001, 'public', 'knowledge_documents', 'is_deleted', 'Soft-delete marker', 'boolean', FALSE, NULL, 0, 0)
ON CONFLICT (schema_catalog_id) DO NOTHING;

COMMIT;
