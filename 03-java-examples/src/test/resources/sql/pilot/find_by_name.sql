-- Пилот по уникальному имени (имя теста AT-<прогон>-...).
-- Колонки перечислены явно: SELECT * ломает маппинг при изменении схемы.
select id,
       name,
       strategy_code,
       product_code,
       segment_code,
       coverage_pct,
       status,
       created_by
  from pilot
 where name = :name
