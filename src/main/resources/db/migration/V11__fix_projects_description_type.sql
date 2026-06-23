DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'projects'
          AND column_name = 'description'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE projects
            ALTER COLUMN description TYPE TEXT
            USING convert_from(description, 'UTF8');
    END IF;
END $$;