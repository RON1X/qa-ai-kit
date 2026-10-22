# Архитектура фреймворка автотестов

> Для людей. Модели этот файл не нужен в каждой сессии: обзоры структуры агентам не помогают,
> а удорожают работу (исследование ETH Zurich, 2026). Агенту достаточно `AGENTS.md`, скиллов и `docs/framework-index.md`.
> Ниже — пример для кода из `examples/`. [ЗАПОЛНИТЬ] под свой проект.

## Слои
| Слой | Пакет | Ответственность | Меняет агент |
|---|---|---|---|
| Конфиг | `config` | Owner: `ProjectConfig`, доступ через `Cfg.get()` | нет, по просьбе |
| Инфраструктура | `core` | браузер (`PlaywrightExtension`), вход (`AuthState`), данные (`TestData`), вложения (`Attach`), уборка (`Cleanup`) | нет, по просьбе |
| API | `api` | `ApiClient` (Jackson, Allure), клиенты разделов, `ApiExtension`, модели `api/model` | да |
| БД | `db` | `DbClient` (JDBC, ro/rw), DAO разделов, строки на Lombok | да |
| Моки | `mock` | `MockServer`, `Mocks`, адаптер `SberMockClient`, стабы `*Stubs` | стабы — да, адаптер — нет |
| Данные UI | `model` | данные форм, `valid()` + `toBuilder()` | да |
| Страницы | `pages`, `pages/components` | Page Object и компоненты | да |
| Шаги | `steps` | бизнес-шаги с `@Step` | да |
| Тесты | `tests/ui`, `tests/api` | сценарии и проверки | да |

## Жизненный цикл UI-теста
1. `PlaywrightExtension.beforeEach` — новый BrowserContext с сохранённой сессией, старт трассы.
2. Тест: подготовка (данные, уборка, стабы) → действия → проверки UI → БД → исходящие запросы.
3. `PlaywrightExtension.afterTestExecution` — при падении скриншот и трасса в Allure.
4. `Mocks.Extension.afterEach` — журнал мока при падении, удаление стабов теста.
5. `Cleanup.Extension.afterEach` — уборка в обратном порядке.
6. `PlaywrightExtension.afterEach` — закрытие контекста.

Порядок в `@ExtendWith({PlaywrightExtension.class, Cleanup.Extension.class, Mocks.Extension.class})` важен:
расширения, объявленные позже, выполняют afterEach раньше.

## Конфиг
`ProjectConfig` (Owner, MERGE): `-Dключ` → `config/${env}.properties` → `config/default.properties`.
Пароли — только `-D` из секретов CI. Запуск: `mvn test -Denv=ift -Dadmin.password=... -Ddb.ro.password=...`.

## Договорённости с разработкой
- Атрибут для тестов: [ЗАПОЛНИТЬ: data-qa]; где обязателен: [ЗАПОЛНИТЬ].
- Таймауты клиентов к внешним системам: [ЗАПОЛНИТЬ по интеграциям].
- Тестовые учётки и роли: [ЗАПОЛНИТЬ: роль → логин; пароли в секретах].

## Особенности стенда
- [ЗАПОЛНИТЬ: например, «после деплоя 2 минуты 502», «справочники обновляются ночью»]
