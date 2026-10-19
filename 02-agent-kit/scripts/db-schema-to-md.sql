-- Выгрузка схемы выбранных таблиц PostgreSQL в Markdown для docs/db-schema.md.
-- Запуск (read-only пользователь достаточно):
--   psql "$DB_URL" -At -v schema=public -v tables="'pilot','change_history'" \
--        -f scripts/db-schema-to-md.sql > docs/db-schema.generated.md
-- Затем человек переносит нужное в docs/db-schema.md и дописывает значения статусов и примеры строк.
-- Описания берутся из COMMENT ON TABLE / COMMENT ON COLUMN — попросите разработку их завести.

select string_agg(md, E'\n\n' order by tbl)
  from (select c.relname as tbl,
               '## ' || c.relname
                 || coalesce(' — ' || obj_description(c.oid, 'pg_class'), '') || E'\n\n'
                 || '| Колонка | Тип | NULL | Описание / значения |' || E'\n'
                 || '|---|---|---|---|' || E'\n'
                 || string_agg('| ' || a.attname
                                 || ' | ' || format_type(a.atttypid, a.atttypmod)
                                 || ' | ' || case when a.attnotnull then 'нет' else 'да' end
                                 || ' | ' || replace(coalesce(col_description(c.oid, a.attnum), ''), '|', '\|')
                                 || ' |',
                               E'\n' order by a.attnum)
                 || coalesce(E'\n\nСвязи:\n' || (select string_agg('- ' || pg_get_constraintdef(f.oid), E'\n' order by f.conname)
                                                   from pg_constraint f
                                                  where f.conrelid = c.oid
                                                    and f.contype in ('p', 'f', 'u')), '') as md
          from pg_class c
          join pg_namespace n on n.oid = c.relnamespace
          join pg_attribute a on a.attrelid = c.oid and a.attnum > 0 and not a.attisdropped
         where c.relkind in ('r', 'p', 'v')
           and n.nspname = :'schema'
           and c.relname in (:tables)
         group by c.relname, c.oid) t;
