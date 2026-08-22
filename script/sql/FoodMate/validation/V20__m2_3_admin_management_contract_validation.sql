-- V20 M2-3 执行后校验。只读，不创建或修改对象。

SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (
      (table_name = 'users' AND column_name = 'revision')
      OR (table_name = 'tool_registries' AND column_name = 'revision')
      OR (table_name = 'knowledge_documents' AND column_name = 'revision')
      OR (table_name = 'messages' AND column_name = 'revision')
  )
ORDER BY table_name;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'idx_users_revision',
      'idx_tool_registries_revision',
      'idx_knowledge_documents_revision',
      'idx_messages_revision'
  )
ORDER BY indexname;

SELECT 'users' AS table_name, COUNT(*) AS invalid_revision_rows
FROM users WHERE revision < 1
UNION ALL
SELECT 'tool_registries', COUNT(*) FROM tool_registries WHERE revision < 1
UNION ALL
SELECT 'knowledge_documents', COUNT(*) FROM knowledge_documents WHERE revision < 1
UNION ALL
SELECT 'messages', COUNT(*) FROM messages WHERE revision < 1;
