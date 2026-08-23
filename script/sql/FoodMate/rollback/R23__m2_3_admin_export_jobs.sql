-- R23 is a precondition check only. Do not drop export jobs while rows exist.

DO $$
BEGIN
    IF to_regclass('public.admin_export_jobs') IS NULL THEN
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM admin_export_jobs) THEN
        RAISE EXCEPTION
            'R23 requires admin_export_jobs to be empty; export or discard dependent data before rollback';
    END IF;
END $$;

DROP TABLE IF EXISTS admin_export_jobs;
