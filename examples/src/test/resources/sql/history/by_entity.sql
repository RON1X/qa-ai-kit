-- Записи "Истории изменения" по одной сущности, в порядке появления.
-- Параметры: 1 = entity_type, 2 = entity_id.
select id,
       action,
       actor,
       created_at
  from change_history
 where entity_type = ?
   and entity_id = ?
 order by id
