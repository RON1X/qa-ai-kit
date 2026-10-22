# Playwright for Java: актуальное состояние и практики (октябрь 2026)

Последний тег Playwright Java — **v1.63.0** (14.09.2026); Node-версия уже 1.64, Java 1.64 ожидается.
Требование — Java 8+, но остальной стек (Allure 3, Jdbi 3.50+, JUnit 6) просит Java 17.
Источник фактов — исходники документации playwright.dev и код playwright-java.

## Что появилось в 2024–2026 и полезно вам
| Версия | API |
|---|---|
| 1.42 | `@UsePlaywright` — встроенная JUnit-интеграция (экспериментальная), `OptionsFactory`; `page.addLocatorHandler()` для внезапных оверлеев |
| 1.44 | `assertThat(locator).hasAccessibleName()/hasRole()` |
| 1.45 | Clock API: `page.clock().setFixedTime()/install()/fastForward()` — тесты дат без ожиданий |
| 1.46 | `setClientCertificates(...)` — для стендов с mTLS |
| 1.47 | JUnit `Options.setTrace(Options.Trace.RETAIN_ON_FAILURE)` (есть в коде, в документации не описано) |
| 1.49 | `locator.ariaSnapshot()`, `assertThat(locator).matchesAriaSnapshot(...)` |
| 1.53 | `locator.describe("...")` — читаемые имена шагов в трассе |
| 1.59 | `page.ariaSnapshot()`, `locator.normalize()` (переписывает локатор в рекомендуемую форму), `browser.bind()` (подключить playwright-cli/MCP к браузеру, запущенному из Java) |
| 1.60 | `assertThat(page).matchesAriaSnapshot()`, `boxes` у ariaSnapshot «для ИИ» |
| 1.63 | `locator.visible()`, `Tracing.StartOptions.setAriaSnapshots(true)` — aria-снимок на каждом шаге трассы |

## Практики (официальные рекомендации)
- **Локаторы**: `getByRole` → `getByLabel`/`getByText`/`getByPlaceholder` → `getByTestId`. CSS и XPath не рекомендуются: DOM меняется. Локаторы строгие; `first()/nth()` не рекомендуются. Атрибут test id настраивается: `playwright.selectors().setTestIdAttribute("data-qa")`.
- **Веб-ассерты** `PlaywrightAssertions.assertThat(locator)` повторяют проверку до успеха (по умолчанию 5 с; `setDefaultAssertionTimeout`). `locator.isVisible()` возвращает ответ сразу — для проверок не годится.
- **Никаких пауз**: `waitForTimeout` помечен как нежелательный — тесты, ждущие время, по определению нестабильны; `NETWORKIDLE` тоже не рекомендован. Действия сами ждут видимости, стабильности, доступности элемента.
- **Изоляция**: `BrowserContext` на тест; `Playwright` и `Browser` переиспользуются. **Playwright Java не потокобезопасен** — по экземпляру на поток.
- **Авторизация один раз**: `context.storageState(...setPath(...))` → `NewContextOptions().setStorageStatePath(...)`. Файл содержит куки — не коммитить.
- **Трассы** вместо видео для разбора падений в CI; хранить только для упавших (`show-trace` открывает DOM, сеть и консоль на каждом шаге).
- **Параллельность JUnit 5**: классы параллельно, методы внутри класса последовательно (файл `junit-platform.properties` в эталоне).
- **Ретраев нет** во встроенной Java-интеграции: `-Dsurefire.rerunFailingTestsCount=N` (Surefire показывает `Flakes: N`) или `@RetryingTest` из JUnit Pioneer. Ретраи — учёт флаков, а не их лечение.
- **Подготовка данных через API**: `context.request()` — `APIRequestContext` с куками браузера; создавать данные API быстрее и надёжнее, чем кликать.
- **`page.route()`** перехватывает только запросы самой страницы (XHR/fetch). Вызовы бэкенда во внешние системы так не замокать — это задача SberMock.

## Структура фреймворка
Page Object (локаторы + действия) → компоненты (таблицы, модальные окна) → шаги с `@Step` → тесты.
Фикстуры — JUnit 5 расширения (`BeforeEachCallback`, `ParameterResolver`), конфиг — системные свойства/переменные окружения.
Allure: артефакт с 2.35 называется **`allure-jupiter`** (`allure-junit5` в 3.x не публикуется); `@Step` требует AspectJ-агент в Surefire;
модуль `allure-playwright` (2.35+) умеет прикладывать скриншоты и трассы и превращать действия Playwright в шаги.

Примеры структуры: [Moe511/playwright-java-framework](https://github.com/Moe511/playwright-java-framework) (JUnit 5, POM, API-клиенты, Allure, трассы),
[статья про Allure + Playwright Java + JUnit](https://qaautomation.expert/2026/03/02/integrate-allure-report-with-playwright-with-java-and-junit5/).

## Нестабильность: причины и лечение
| Причина | Лечение |
|---|---|
| Паузы, `NETWORKIDLE`, мгновенные `isVisible()` | `assertThat(...)`, `page.waitForURL(...)`, `page.waitForResponse(..., () -> click)` |
| CSS/XPath, `nth()` | роли, подписи, `data-qa` |
| Общие данные и учётки, зависимость от порядка | контекст на тест, уникальные данные, учётка на поток, уборка |
| Общие объекты Playwright между потоками | экземпляр на поток, `Page` не шарить |
| Клик до гидратации (обработчики ещё не навешаны) | на стороне продукта: блокировать кнопки до готовности |
| Случайные баннеры | `page.addLocatorHandler(...)` |
| Даты и таймеры | `page.clock()` |
| Медленный стенд | поднять таймаут ассертов, оставить трассы для разбора — но не паузы |

## ИИ-инструменты Playwright и Java
- **Playwright MCP** даёт модели браузер через aria-снимки с ссылками на элементы (`textbox "Название" [ref=e5]`); опции `--codegen java`, `--isolated`, `--storage-state`, `--test-id-attribute`.
- **playwright-cli + скиллы** — рекомендуемый Microsoft путь для кодовых агентов: не грузит в контекст схемы инструментов и деревья.
- **Test Agents** (planner/generator/healer) — только TypeScript. Аналог для Java в наборе: ручной кейс = план, `ui-tests` = генератор, `test-debugging` + трасса = «лечение» с одобрением человека.
- **Codegen для Java**: `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="codegen --target java-junit <url>"`; с сессией — `--load-storage=auth.json`.

Источники: [playwright.dev/java/docs/release-notes](https://playwright.dev/java/docs/release-notes), [locators](https://playwright.dev/java/docs/locators), [test-assertions](https://playwright.dev/java/docs/test-assertions), [auth](https://playwright.dev/java/docs/auth), [junit](https://playwright.dev/java/docs/junit), [multithreading](https://playwright.dev/java/docs/multithreading), [trace-viewer-intro](https://playwright.dev/java/docs/trace-viewer-intro), [api-testing](https://playwright.dev/java/docs/api-testing), [mock](https://playwright.dev/java/docs/mock), [codegen](https://playwright.dev/java/docs/codegen-intro), [best-practices](https://playwright.dev/docs/best-practices), [test-agents](https://playwright.dev/docs/test-agents), [Surefire rerun](https://maven.apache.org/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html), [allure-jupiter](https://github.com/allure-framework/allure-java/blob/3.0.0/allure-jupiter/README.md).
