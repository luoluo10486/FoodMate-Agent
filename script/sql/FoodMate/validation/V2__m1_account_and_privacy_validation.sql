SELECT current_database() AS database_name, current_user AS database_user, version();

SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'users'::regclass AND conname = 'chk_users_role';

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('password_reset_tokens', 'data_export_jobs', 'account_deletion_jobs')
ORDER BY table_name;

SELECT table_name, column_name, is_nullable, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND ((table_name = 'user_avatar_assets' AND column_name IN ('url', 'storage_key', 'content_sha256'))
       OR table_name IN ('password_reset_tokens', 'data_export_jobs', 'account_deletion_jobs'))
ORDER BY table_name, ordinal_position;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'uk_password_reset_tokens_hash',
      'idx_password_reset_tokens_user_expires',
      'idx_data_export_jobs_user_created',
      'idx_data_export_jobs_cleanup',
      'uk_account_deletion_jobs_user',
      'idx_account_deletion_jobs_status',
      'idx_user_auth_sessions_user_active'
  )
ORDER BY indexname;
