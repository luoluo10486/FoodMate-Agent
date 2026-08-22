-- V19 read-only validation. The old v1 schema remains available for audit history.
SELECT t.name,
       t.current_version,
       s.version,
       s.input_schema->'required' AS required_fields,
       s.input_schema->'properties' ? 'candidate_sql' AS has_candidate_sql,
       s.output_schema->'properties' ? 'sql_audit_id' AS has_sql_audit_id
  FROM tool_registries t
  JOIN tool_schema_versions s
    ON s.tool_id = t.tool_id
   AND s.version = t.current_version
   AND s.is_deleted = FALSE
 WHERE t.tool_id = 720004
   AND t.name = 'database_query'
   AND t.is_deleted = FALSE;

SELECT version, is_deleted
  FROM tool_schema_versions
 WHERE tool_id = 720004
   AND version IN ('v1', 'v2')
 ORDER BY version;

SELECT COUNT(*) AS invalid_current_database_query
  FROM tool_registries t
 WHERE t.tool_id = 720004
   AND (t.current_version <> 'v2' OR t.status <> 'active' OR t.is_deleted = TRUE);
