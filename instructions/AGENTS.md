# AGENTS.md — автотесты [ЗАПОЛНИТЬ: название проекта]

Стек: Java 17, Maven, JUnit 5, Playwright for Java, AssertJ, Allure, Owner, Lombok, Jackson, PostgreSQL, Page Object.
Тесты ходят на общий стенд: параллельно работают другие тесты и люди.

## Как отвечать
По умолчанию — коротко: сначала результат, потом детали, только если они нужны для решения.
- Без вступлений, пересказа задачи, похвалы и итогового «что я сделал» в конце.
- Вопрос → ответ 1–3 предложения. Правка кода → список изменённых файлов и команда проверки с её итогом.
- Код — только изменённые фрагменты, если не просили файл целиком.
- Неясно, что делать, — один уточняющий вопрос, а не несколько вариантов решения.
- Переключатели в начале сообщения: `кратко:` — максимум 3 строки; `подробно:` — с объяснением причин и альтернатив; `тех:` — только код, команды, имена классов, без пояснений.

Почему: ответ читают между прогонами тестов, длинный текст прячет главное.

## Команды
- Компиляция: `mvn -q test-compile`
- Один тест: `mvn test -Dtest=CreatePilotTest#createsPilotWithValidData -Denv=ift`
- По слою: `mvn test -Dgroups=api -Denv=ift` (теги `ui`, `api`, `db`)
- 3 прогона подряд (проверка на флаки): `scripts/flaky-check.sh CreatePilotTest 3`
- Трасса упавшего UI-теста: `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="show-trace target/traces/<Класс>.<метод>.zip"`
- Метрики проекта: `java scripts/TestMetrics.java --src src/test/java --cases test-cases --allure target/allure-results --out target/metrics.md`
- Механические запреты: `scripts/guard-tests.sh`
- Параметры стенда: [ЗАПОЛНИТЬ: -Denv=ift, где лежат пароли]. Пароли не выводить и не коммитить.

## Правила проекта (то, чего не видно из кода)
1. **Локаторы только из источника.** Существующий Page Object, aria-снимок страницы (скилл `playwright-browser`) или codegen. Не нашёл — `// TODO(locator): <что ищем>` и вопрос. Почему: выдуманный локатор выглядит правдоподобно, но тест на нём не работает.
2. **Без пауз.** UI — `assertThat(locator)` (ждёт сам), БД и моки — `await().untilAsserted(...)`. Не использовать `Thread.sleep`, `page.waitForTimeout`, `NETWORKIDLE`: пауза прячет гонку и делает тест медленным и нестабильным.
3. **Не ослаблять тест ради зелёного прогона.** Не удалять проверку, не заменять точную проверку на `contains`/`isNotNull`, не ставить `@Disabled`. Если тест честно падает на правильной проверке — возможно, это баг продукта: опиши и остановись.
4. **Свои данные.** Имена — `TestData.unique(...)`, уборка — `cleanup.add(...)` до действия, которое создаёт данные. Почему: стенд общий, «последняя запись» может быть чужой.
5. **JSON — через Jackson.** Тело запроса: `Json.toJson(...)` и `setData(String)`. `RequestOptions.setData(объект)` сериализует встроенным Gson и игнорирует аннотации Jackson.
6. **БД.** Проверки — `DbClient.readOnly()`, запись — только подготовка и уборка через `DbClient.readWrite()`. SQL — в `src/test/resources/sql/<раздел>/*.sql`. Запись только по id и с `name like 'AT-%'`. Колонки — из `docs/db-schema.md`.
7. **Конфиг — только Owner** (`Cfg.get()`), без `System.getProperty` в тестах и без адресов в коде.
8. **Модели — Lombok**: `@Value @Builder(toBuilder = true)`, для чтения из JSON ещё `@Jacksonized`. Деньги и проценты — `BigDecimal`.
9. **Моки** — через `Mocks` и классы `*Stubs`, каждый стаб с ключом из данных теста, без глобального reset. API SberMock не угадывать.
10. **Каждый тест знает, что ловит.** Строка `Ловит: <дефект>` в javadoc; ожидаемые значения — литералы из кейса, а не значения, посчитанные тем же кодом.
11. **Без просьбы не трогать:** `core/*`, `config/*`, `pom.xml`, чужие тесты.

## Где что
- Эталоны: `tests/ui/pilots/CreatePilotTest.java`, `tests/api/pilots/PilotApiTest.java`.
- Готовые методы Page Object / API / DAO / стабов: `docs/framework-index.md` — сначала ищи там, потом пиши новый.
- Схема БД — `docs/db-schema.md`, API — `docs/api.md`, интеграции — `docs/integrations.md`, ручные кейсы — `test-cases/`.

## Скиллы
| Задача | Скилл | Команда |
|---|---|---|
| Ручные кейсы из требований и скриншотов | `test-case-generator` | `/tc` |
| Спека большой задачи до кода | `test-spec` | `/spec` |
| UI-тест, Page Object | `ui-tests` | `/ui-test` |
| API-тест | `api-tests` | `/api-test` |
| Проверка или подготовка данных в БД | `db-checks` | — |
| Стаб внешней системы | `integration-mocks` | — |
| Посмотреть страницу, снять локаторы, воспроизвести баг | `playwright-browser` | `/explore` |
| Метрики проекта | `test-metrics` | `/metrics` |
| Тест упал или флакует | `test-debugging` | `/debug-test` |
| Перед сдачей теста | `test-review`, субагент `test-reviewer` | `/review-test` |

## Готово, когда
- `mvn -q test-compile` без ошибок, `scripts/guard-tests.sh` без ❌;
- тест прошёл 3 раза подряд на стенде;
- показано, что тест падает при испорченном ожидании (шаг из `ui-tests` / `api-tests`);
- пройден чек-лист `test-review`.

Отчёт: что сделано, команда запуска и её итог, что не получилось, вопросы. «Должно работать» без вывода команды — не отчёт.
