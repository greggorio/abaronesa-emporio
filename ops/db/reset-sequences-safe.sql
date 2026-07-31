DO $$
DECLARE r record;
DECLARE v_max bigint;
BEGIN
  FOR r IN
    SELECT n.nspname AS schema_name,
           t.relname AS table_name,
           a.attname AS column_name,
           s.relname AS seq_name
    FROM pg_class s
    JOIN pg_depend d ON d.objid = s.oid AND d.deptype = 'a'
    JOIN pg_class t ON d.refobjid = t.oid
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE s.relkind = 'S' AND n.nspname = 'public'
  LOOP
    -- Obtém o maior valor atual na coluna
    EXECUTE format('SELECT max(%I) FROM %I.%I', r.column_name, r.schema_name, r.table_name)
      INTO v_max;
    -- Tabela vazia: preparar próxima chave como 1 -> setval(1, false) => nextval() retorna 1
    IF v_max IS NULL OR v_max < 1 THEN
      EXECUTE format('SELECT setval(%L, 1, false);', r.schema_name || '.' || r.seq_name);
    ELSE
      -- Tabela com dados: setar para v_max (chamada=true) => próxima será v_max+1
      EXECUTE format('SELECT setval(%L, %s, true);', r.schema_name || '.' || r.seq_name, v_max);
    END IF;
  END LOOP;
END $$;
