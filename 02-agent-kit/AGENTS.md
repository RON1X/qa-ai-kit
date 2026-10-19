# AGENTS.md — UI e2e автотесты админки стратегий

> Файл читает ИИ-агент (Qwen Code, Cursor, Codex и др.) в начале каждой сессии.
> Держите его коротким: каждая строка съедает контекст локальной модели.
> Всё, что помечено **[ЗАПОЛНИТЬ]**, заполните до первого запуска.

## Проект
- Автотесты веб-админки управления банковскими стратегиями. Разделы: Пилоты → Симуляции → Внедрения → История изменения.
- Стек: Java 17, Maven, JUnit 5, Playwright for Java, Allure, Jdbi + HikariCP (БД), SberMock (моки внешних систем).
- ~30% функциональности — интеграции. Внешние системы на стенде заменены стабами SberMock.
- Стенд общий: параллельно с тобой работают другие тесты и люди.

## Команды
- Компиляция: `mvn -q test-compile`
- Один тест: `mvn test -Dtest=CreatePilotTest#createsPilotWithValidData`
- Проверка на флаки (3 прогона подряд): `scripts/flaky-check.sh CreatePilotTest 3`
- Открыть трассу упавшего теста: `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="show-trace target/traces/<Класс>.<метод>.zip"`
- Параметры стенда: **[ЗАПОЛНИТЬ: где лежат, например `.env` / профиль Maven `-Pstand-ift`]**. Пароли не выводить и не коммитить.

## Карта репозитория
```
src/test/java/ru/bank/admin/at/        [ЗАПОЛНИТЬ: ваш пакет]
  core/    Config, TestData, Attach, PlaywrightExtension, AuthState, Cleanup — инфраструктура, не менять без просьбы
  db/      Db (пулы ro/rw) + DAO по разделам (PilotDb ...) с record-ами строк
  mock/    MockServer, Stub, Mocks, SberMockClient + стабы по интеграциям (SegmentServiceStubs ...)
  model/   данные форм (PilotData.valid().withX(...))
  pages/   Page Object-ы и компоненты
  steps/   бизнес-шаги с @Step
  tests/   тесты по разделам
src/test/resources/sql/   SQL-файлы с :параметрами
test-cases/               ручные тест-кейсы (.md) — источник для автотестов
docs/                     architecture.md, db-schema.md, integrations.md — читать перед работой
                          framework-index.md — оглавление готовых методов (scripts/index-framework.sh)
```
Эталон, на который равняться: `tests/pilots/CreatePilotTest.java`.
Перед созданием нового метода Page Object / DAO / стаба найди готовый в `docs/framework-index.md`.

## Железные правила
1. **Локаторы только настоящие.** Источник — существующий Page Object, aria-снимок страницы или codegen. Не нашёл — пиши `// TODO(locator): <что ищем>` и сообщи. Выдумывать локатор запрещено.
2. **Никаких пауз.** Запрещены `Thread.sleep`, `page.waitForTimeout`, `waitForLoadState(NETWORKIDLE)`. UI — `assertThat(locator)` (ждёт сам), БД и моки — `await(...).untilAsserted(...)`.
3. **Не ослаблять тест ради зелёного.** Нельзя: удалять проверку, менять точную проверку на `contains`/`isNotNull`, ставить `@Disabled`, поднимать таймаут без доказанной причины. Если тест честно падает на правильной проверке — возможно, это баг продукта: остановись и опиши.
4. **Свои данные.** Каждая сущность — `TestData.unique(...)`. Уборка (`Cleanup.add`) регистрируется ДО действия, которое создаёт данные.
5. **БД.** Проверки — через `Db.readOnly()`. Запись — только подготовка и уборка через `Db.readWrite()`. SQL — в `src/test/resources/sql/*.sql`. Запрещены `TRUNCATE`, а также `DELETE`/`UPDATE` без условия по id и префикса `AT-`. Колонки бери из `docs/db-schema.md`, не угадывай.
6. **Моки.** Только через `Mocks` и классы `*Stubs`. Каждый стаб с correlationKey из данных теста. Глобальный reset запрещён. API SberMock не угадывать — только `docs/integrations.md` и `SberMockClient`.
7. **Каждый тест знает, что ловит.** В javadoc строка `Ловит: <какой дефект>`. Не можешь сформулировать — тест не нужен.
8. **Проверки — в тесте.** Page Object возвращает локаторы и выполняет действия, но не проверяет бизнес-результат.
9. **Ожидаемые значения — литералы** из кейса и требований, а не значения, посчитанные тем же кодом.
10. **Без просьбы не трогать:** `core/*`, `SberMockClient`, `pom.xml`, чужие тесты.

## Конвенции
- Класс: `<Сущность><Действие>Test`, метод — поведение по-английски (`showsErrorWhenSegmentServiceFails`).
- `@DisplayName` — название ручного кейса по-русски, `@TmsLink` — путь к кейсу (`Пилоты/Добавление/TC-001`).
- `assertThat(...)` — проверки UI (Playwright); `then(...)` — проверки данных (AssertJ).
- Один тест — одно поведение. Больше 15 шагов — разбей кейс на несколько тестов.
- Новый Page Object — по образцу `pages/PilotsPage.java`; новая проверка БД — по образцу `db/PilotDb.java`.

## Какой скилл когда
| Задача | Скилл / команда |
|---|---|
| Ручные кейсы из документации/скриншотов | `test-case-generator`, `/tc` |
| Автотест по ручному кейсу | `autotest-writer`, `/automate` |
| Проверка или подготовка данных в БД | `db-checks` |
| Стаб внешней системы, негатив по интеграции | `integration-mocks` |
| Тест упал или флакует | `test-debugging`, `/debug-test` |
| Перед сдачей любого теста | `test-review` (субагент `test-reviewer`), `/review-test` |

## Определение «готово»
- [ ] `mvn -q test-compile` без ошибок
- [ ] `scripts/guard-tests.sh` без ❌ (паузы, `@Disabled`, опасный SQL)
- [ ] тест прошёл 3 раза подряд на стенде
- [ ] доказано, что тест может упасть (шаг «Докажи, что тест ловит дефект» в `autotest-writer`)
- [ ] пройден чек-лист `test-review`
- [ ] отчёт: что сделано; команда запуска и её итог; что не удалось; открытые вопросы

Слова «должно работать», «вроде прошло» без вывода команды — не отчёт.
