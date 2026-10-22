---
name: playwright-browser
description: Используй, когда нужно открыть страницу приложения в браузере через Playwright MCP или playwright-cli — снять реальные локаторы и aria-снимок для Page Object, пройти сценарий руками, воспроизвести баг, посмотреть сетевые запросы и ошибки консоли, проверить, что элемент или текст есть на странице.
---

# Управление браузером: Playwright MCP и playwright-cli

Браузер нужен агенту, чтобы **видеть настоящую страницу**: брать локаторы из неё, а не придумывать.
Тесты при этом запускает Maven, а не агент кликами.

## Что выбрать
| | Playwright MCP | playwright-cli |
|---|---|---|
| Как работает | MCP-сервер, инструменты `browser_*` | команды в терминале: `playwright-cli snapshot` |
| Контекст модели | схемы инструментов и снимки идут в контекст | снимки пишутся в файлы, в контекст — только нужное |
| Когда | исследование страницы шаг за шагом, отладка | агент с маленьким контекстом, долгие задачи с кодом |

Microsoft рекомендует CLI для кодовых агентов: он экономнее по токенам. Для локальной модели с небольшим окном это важно.

## Подключение
MCP (в `.qwen/settings.json`, секция `mcpServers`):
```json
"playwright": {
  "command": "npx",
  "args": ["@playwright/mcp", "--headless", "--isolated",
           "--test-id-attribute=data-qa", "--codegen=java",
           "--caps=testing,network",
           "--storage-state=target/.auth/admin.json",
           "--ignore-https-errors", "--output-dir=target/mcp"]
}
```
- `--isolated` — профиль в памяти, параллельные агенты не мешают друг другу.
- `--storage-state` — вход под тестовой учёткой из сессии автотестов (файл создаёт `AuthState`); пароль в чат не передаём.
- `--test-id-attribute` — тот же атрибут, что в проекте, иначе `getByTestId` в подсказках будет не тот.
- `--codegen=java` — сгенерированные подсказки кода на Java.
- `--caps=testing` — инструменты `browser_generate_locator` и `browser_verify_*`; `network` — `browser_route` и управление сетью.
- В закрытом контуре: пакет из внутреннего зеркала npm, браузер — предустановленный или `--executable-path=<путь к chrome>`, прокси — `--proxy-server`.

CLI: `npm i -g @playwright/cli` (из внутреннего зеркала), `playwright-cli --help` — справка, которую агент читает сам.

## Как работать (цикл)
1. **Открыть**: MCP `browser_navigate` (`url`) / CLI `playwright-cli open <url>`.
2. **Снимок**: MCP `browser_snapshot` / CLI `playwright-cli snapshot --filename=docs/snapshots/<страница>.yml`.
   Снимок — дерево ролей и имён, у элементов есть ссылки `[ref=e12]`. На большой странице сначала ищи нужное:
   MCP `browser_find` (`text` или `regex`) / CLI `playwright-cli find <текст>` — дешевле полного снимка.
3. **Действие по ссылке из снимка**: `browser_click` / `browser_type` / `browser_fill_form` / `browser_select_option`
   с `target: "e12"`; CLI — `playwright-cli click e12`, `fill e12 "текст"`, `select e12 "значение"`.
4. **Новый снимок после каждого действия**: ссылки `ref` живут до следующего изменения страницы, старые не переиспользуй.
5. **Ожидание**: `browser_wait_for` с `text` или `textGone`, а не с `time`.
6. **Закрыть**: `browser_close` / `playwright-cli close`.

## Из снимка — в Java
```yaml
- dialog "Новый пилот":
  - textbox "Название" [ref=e21]
  - button "Сохранить" [ref=e25]
```
```java
Locator dialog = page.getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName("Новый пилот"));
dialog.getByLabel("Название");
dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Сохранить"));
```
- Роль и имя берутся из снимка дословно. `ref` в код не попадает — он только для текущей сессии.
- С `--caps=testing`: `browser_generate_locator` (`target: "e25"`) выдаёт локатор, который Playwright считает лучшим, — сверяй с приоритетом из `ui-tests/references/locators.md`.
- Проверить, что элемент есть: `browser_verify_element_visible` (`role`, `accessibleName`), текст — `browser_verify_text_visible`.

## Отладка и баги
- Сеть: `browser_network_requests` (`filter: "/api/.*pilot"`) → `browser_network_request` (`index`) — тело и заголовки конкретного запроса. Так видно, что UI отправил в API.
- Консоль: `browser_console_messages` (`level: "error"`).
- Скриншот для отчёта — `browser_take_screenshot` (`filename`). Действовать по скриншоту нельзя, только по снимку.
- Подмена ответа API самой админки (UI-край: пустой список, медленный ответ) — `browser_route` с `--caps=network`. Вызовы бэкенда во внешние системы так не подменить — это SberMock.

## Правила безопасности
- Только тестовый стенд и тестовые учётки. Реальные пароли в чат и в инструменты не вводим — вход через `--storage-state`.
- Не выполнять необратимые действия на общем стенде (удаление чужих сущностей, раскатка на всех) без явной просьбы человека.
- `browser_run_code_unsafe` и `browser_evaluate` выполняют произвольный код — только для чтения данных страницы, не для обхода UI.
- Playwright MCP — не граница безопасности: не подключайте его к браузеру с личной сессией.

## Результат работы со страницей
Сохрани снимок в `docs/snapshots/<страница>.yml` и выдай таблицу:
| Элемент | Роль и имя из снимка | Локатор Java | Метод Page Object |
|---|---|---|---|
