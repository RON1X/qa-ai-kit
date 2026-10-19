-- Записи "Истории изменения" по одной сущности, в порядке появления.
select id,
       action,
       actor,
       created_at
  from change_history
 where entity_type = :entityType
   and entity_id = :entityId
 order by id
