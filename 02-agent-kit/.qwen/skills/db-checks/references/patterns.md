# Паттерны работы с БД в UI e2e

## Почему не откат транзакции
В unit/интеграционных тестах данные убирают откатом транзакции. В UI e2e так нельзя: приложение на стенде
коммитит изменения в своих соединениях, тест к ним доступа не имеет. Поэтому — уникальные данные + уборка по id.

## Два пользователя БД
| Пул | Пользователь | Права | Для чего |
|---|---|---|---|
| `Db.readOnly()` | `db.ro.user` | только SELECT (в PostgreSQL 14+ — роль `pg_read_all_data`) | все проверки |
| `Db.readWrite()` | `db.rw.user` | INSERT/UPDATE/DELETE на тестовые таблицы | подготовка и уборка |

`setReadOnly(true)` в пуле — только подсказка драйверу. Защищают права в БД.

## AssertJ-DB Changes: снимок «до/после»
Библиотека `org.assertj:assertj-db` (3.x). Подходит, когда важно доказать, что действие изменило
**ровно** ожидаемые строки и ничего лишнего (откат, массовые операции).

```java
import static org.assertj.core.api.Assertions.assertThat; // в 3.0.2+ — единый assertThat

AssertDbConnection adb = AssertDbConnectionFactory.of(dataSource).create();
Changes changes = adb.changes()
        // ВСЕГДА ограничивай запросом по своей сущности: на общем стенде без фильтра
        // в изменения попадут строки других тестов и людей
        .request("select id, entity_id, action, actor from change_history where entity_id = ?",
                 r -> r.parameters(pilotId).pksName("id"))
        .build();

changes.setStartPointNow();
pilotsPage.rollback(pilotName);                      // действие в UI

await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
    changes.setEndPointNow();                        // перечитывает данные на каждой итерации
    assertThat(changes).hasNumberOfChanges(1)
            .change().isCreation()
            .rowAtEndPoint()
            .value("action").isEqualTo("ROLLBACK")
            .value("actor").isEqualTo("autotest_user");
});
```
Нужен `DataSource` — добавь в `Db` метод доступа к пулу. API 3.x несовместим с 2.x (другая точка входа).

## Database Rider / DBUnit — осторожно
Удобны для компонентных тестов на своей БД. На общем стенде опасны: стратегия по умолчанию `CLEAN_INSERT`
очищает все таблицы из датасета. Если используете — только `strategy = INSERT` и `compareOperation = CONTAINS`.

## Testcontainers — не для e2e
Testcontainers поднимает свою БД в Docker. Для UI e2e бесполезно: проверять нужно БД, в которую пишет
приложение стенда. Полезно для другого: прогнать ваши `.sql` и DAO против схемы, накатив миграции приложения
(Liquibase/Flyway) в контейнер. В закрытом контуре: `hub.image.name.prefix=<ваш-registry>/` в
`testcontainers.properties`, и в зеркале должны быть служебные образы `testcontainers/ryuk` и `alpine`.

## Схема для ИИ (`docs/db-schema.md`)
Модель пишет правильный SQL, только если видит схему. Давайте ей:
- `CREATE TABLE` или таблицу колонок только для таблиц, которые трогают тесты, + соседей через внешний ключ;
- комментарии к колонкам статусов с перечнем значений (`status: DRAFT | ACTIVE | ROLLED_BACK`);
- 2–3 строки-примера без персональных данных.
Выгрузка для PostgreSQL — `scripts/db-schema-to-md.sql`. Для Oracle — `DBMS_METADATA.GET_DDL`.
Обновляйте файл после каждого релиза с миграциями.

## Типичные ошибки
| Ошибка | Как правильно |
|---|---|
| `SELECT *` | явный список колонок |
| Проверка «count > 0» | проверка значений конкретной строки по уникальному ключу |
| Поиск «последней записи» по времени | поиск по уникальному имени/id своей сущности |
| `Thread.sleep(3000)` перед SELECT | `await().untilAsserted(...)` |
| SQL-строка в тесте | SQL-файл + метод DAO |
| Удаление по префиксу всего прогона в каждом тесте | удаление по id своей сущности; уборка прогона — отдельной ночной задачей |
