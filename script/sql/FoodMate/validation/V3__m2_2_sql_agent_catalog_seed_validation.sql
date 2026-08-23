-- V3 SQL Agent local catalog seed validation. Read-only.

SELECT datasource_id, name, db_type, status, readonly, is_deleted
FROM data_sources
WHERE datasource_id = 730001;

SELECT table_name, COUNT(*) AS field_count
FROM schema_catalogs
WHERE datasource_id = 730001
  AND is_deleted = FALSE
GROUP BY table_name
ORDER BY table_name;

SELECT COUNT(*) AS invalid_catalog_rows
FROM schema_catalogs
WHERE datasource_id = 730001
  AND is_deleted = FALSE
  AND (schema_name <> 'public' OR table_name NOT IN (
      'food_logs', 'food_log_items', 'meal_plans', 'shopping_lists',
      'nutrition_foods', 'knowledge_documents'
  ));
