# БД в UI e2e: выбор инструментов и практики

## Чем ходить в БД
| Вариант | Плюсы | Минусы | Вывод |
|---|---|---|---|
| **Чистый JDBC + свой `DbClient`** | ноль новых зависимостей (только драйвер PostgreSQL), сырой SQL, параметры `?` | маппинг строк руками (`RowMapper`) | **выбрано в наборе**: в стеке проекта нет Jdbi |
| HikariCP + Jdbi 3 | именованные параметры, `mapToMap()`, пул соединений | Jdbi 3.50+ требует Java 17; две зависимости | если можно добавить зависимости и запросов много |
| Spring `JdbcClient` (6.1+) | то же самое, если Spring уже есть в тестах | тянет spring-jdbc | равноценная замена |
| jOOQ | типобезопасность | кодогенерация; Oracle только в платных редакциях | избыточно |
| JPA/MyBatis | — | привязывает тесты к объектной модели приложения | нет |

Версии: HikariCP `7.1.0`, Jdbi `3.55.0`, PostgreSQL JDBC `42.7.13`, Oracle `ojdbc17:23.26.3.0.0`, Awaitility `4.3.x`, AssertJ-DB `3.0.2`.

## Как проверять
- **Ожидание асинхронной записи** — Awaitility: `await("...").atMost(...).pollInterval(...).untilAsserted(...)` или `until(callable, predicate)`, который возвращает дождавшееся значение. Системное свойство `-Dawaitility.defaultTimeout=PT5S` (4.3.1) задаёт таймаут по умолчанию.
- **Что изменилось после действия** — AssertJ-DB `Changes`: снимок до/после, сравнение по первичному ключу. Версия 3.0 сломала API 2.x (вход через `AssertDbConnectionFactory.of(dataSource).create()`). `setEndPointNow()` перечитывает данные — работает внутри Awaitility. На общем стенде — **только** с фильтром по своей сущности, иначе поймаете чужие строки.
- **Database Rider / DBUnit** — для своей БД. Стратегия по умолчанию `CLEAN_INSERT` очищает таблицы датасета — на общем стенде опасно. Последний релиз Rider — июль 2024.
- Свои assert-классы на сущность и единый DAO-слой с логированием SQL — практика РСХБ-Интех ([habr.com/ru/post/916148](https://habr.com/ru/post/916148/), [habr.com/ru/articles/859364](https://habr.com/ru/articles/859364)): типизированные критерии вместо разбросанных SQL, DTO вместо голых id, проверка значимых полей, а не только наличия.

## Данные на общем стенде
- **Уникальное пространство имён на тест**: префикс + UUID (`AT-<прогон>-...`). Общие пользователи — только на чтение; тесты, меняющие глобальные настройки, — в отдельный последовательный набор.
- **Уборка**: регистрировать сразу после создания; удалять по сохранённым id, дети раньше родителей; никаких `TRUNCATE` и широких префиксов; ночной «уборщик» — только страховка (требует маркер автотестов, не трогает свежие записи, ограничивает объём, умеет dry-run). [oneuptime.com: очистка тестовых данных](https://oneuptime.com/blog/post/2026-08-23-clean-api-test-data-parallel-runs/view)
- **API или SQL для подготовки**: API проходит валидации, значения по умолчанию и **аудит**, SQL быстрее, но обходит инварианты. По умолчанию — API; SQL-загрузчики документировать: что они обходят. Для вас критично: вставка стратегии через SQL не создаст записей «Истории изменения». [oneuptime.com: API vs DB fixtures](https://oneuptime.com/blog/post/2026-08-23-api-test-fixtures-api-vs-database/view)
- **Откат транзакции не работает**: приложение на стенде коммитит в своих соединениях (то же в документации Spring Boot про тесты с реальным сервером).
- **Read-only пользователь** для проверок: в PostgreSQL 14+ роль `pg_read_all_data`; `readOnly` в Hikari — лишь подсказка драйверу.
- **Секреты** — из CI/Vault через `-D`/переменные окружения, маскировать в логах и Allure.

## Testcontainers в закрытом контуре
Testcontainers 2.x: модули с префиксом `testcontainers-` (`testcontainers-postgresql`, `testcontainers-oracle-free`), новые пакеты.
Внутренний registry: `hub.image.name.prefix=registry.bank.local/mirror/` в `testcontainers.properties` или `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX`; в зеркале нужны `testcontainers/ryuk` и `alpine` (или `TESTCONTAINERS_RYUK_DISABLED=true`).
**Для UI e2e не подходит** — проверять нужно БД стенда. Полезно, чтобы прогонять свои `.sql` и DAO против схемы, накатив миграции приложения в контейнер. Требует Docker на агентах CI.

## Схема для ИИ
- PostgreSQL: `pg_dump --schema-only -t 'шаблон'` или скрипт `scripts/db-schema-to-md.sql` из набора.
- Oracle: `DBMS_METADATA.GET_DDL('TABLE', 'X', 'SCHEMA')`.
- `tbls` генерирует Markdown по таблицам (Oracle в списке нет), SchemaSpy — HTML через любой JDBC-драйвер.
- Модели давать: компактные `CREATE TABLE` нужных таблиц, комментарии к колонкам статусов с перечнем значений, соседей через FK, 2–3 строки примеров без ПДн. [draxlr.com: schema-aware prompting](https://www.draxlr.com/blogs/schema-aware-prompting-text-to-sql/)

Источники: [HikariCP](https://github.com/brettwooldridge/HikariCP), [Jdbi](https://jdbi.org/), [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html), [jOOQ download](https://www.jooq.org/download/), [AssertJ-DB releases](https://github.com/assertj/assertj-db/releases), [Database Rider](https://github.com/database-rider/database-rider), [Awaitility](https://github.com/awaitility/awaitility/wiki/Usage), [PostgreSQL roles](https://www.postgresql.org/docs/14/predefined-roles.html), [Spring Boot testing](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html), [Testcontainers image substitution](https://github.com/testcontainers/testcontainers-java/blob/main/docs/features/image_name_substitution.md), [OWASP Secrets](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html), [tbls](https://github.com/k1LoW/tbls), [SchemaSpy](https://github.com/schemaspy/schemaspy).
