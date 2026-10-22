# Qwen Code, AGENTS.md и Agent Skills в закрытом контуре

## Qwen Code (QwenLM/qwen-code)
- Актуальная версия `0.25.1` (11.10.2026), ночные сборки почти ежедневно, нужен **Node.js 22+**. Бесплатный Qwen OAuth закрыт с 15.04.2026 — для своего endpoint не важно.
- **Своя модель (OpenAI-совместимый API, vLLM и т.п.)**: `modelProviders.openai[]` с полями `id`, `name`, `envKey`, `baseUrl`, `generationConfig` (`timeout`, `maxRetries`, `contextWindowSize`, `samplingParams`); `security.auth.selectedType: "openai"`. Простой вариант — переменные `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`. Пример — `instructions/.qwen/settings.example.json`.
- **Для закрытого контура**: `general.enableAutoUpdate: false`, `privacy.usageStatisticsEnabled: false`; общие настройки на машину — `/etc/qwen-code/settings.json` (перекрывают пользовательские). Невалидный JSON переименовывается в `settings.json.corrupted`. Низкая температура (0–0,1) уместна для обычных моделей; для рассуждающих (DeepSeek V4.1 Flash, GLM-5.3-Flash) — рекомендации вендора, см. `07-deepseek-glm.md`.
- **Контекст-файлы**: `QWEN.md` (глобальный, в корне проекта и выше по дереву) и **`AGENTS.md`** — по умолчанию читаются оба; имена задаются `context.fileName`. В файлы можно включать другие через `@путь`.
- **Скиллы**: `.qwen/skills/<имя>/SKILL.md` (проект) и `~/.qwen/skills/` (пользователь); обязательные поля `name`, `description`; вызываются моделью по описанию или явно `/<имя>`. Команда `/learn` делает скилл из документации, папки или видео.
- **Субагенты**: `.qwen/agents/*.md` с YAML (`name`, `description`, `model`, `approvalMode`, `tools`, `disallowedTools`); у субагента может быть своя модель (`model: fast` или `authType:modelId`). Понимает формат агентов Claude Code.
- **Команды**: `.qwen/commands/*.md` (Markdown + необязательный YAML, TOML устарел); подстановки `{{args}}`, `!{shell}` (с подтверждением), `@{файл}`; подпапка → пространство имён (`git/commit.md` → `/git:commit`).
- **MCP**: секция `mcpServers` (`command/args`, `httpUrl`, `url`), фильтры `includeTools/excludeTools`; `qwen mcp add`.
- **Хуки**: `PreToolUse`, `PostToolUse`, `Stop`, `SubagentStop` — можно, например, блокировать `Thread.sleep` в коде тестов до записи файла.
- **Расширения**: `qwen extensions install <путь|архив>` — работает без интернета; плагины Claude Code и расширения Gemini CLI конвертируются автоматически.
- **Headless/CI**: `qwen -p "..." --output-format json --approval-mode auto-edit --max-session-turns N`; `--yolo` не включает песочницу.
- **Скриншоты**: `visionModel` — модель-«мост», которая описывает изображение для текстовой основной модели.

## Модели Qwen для кода (открытые веса)
| Модель | Размер | Контекст | Примечание |
|---|---|---|---|
| Qwen3-Coder-30B-A3B | 30,5B, 3,3B активных | 262K (до 1M с YaRN) | лёгкая, для массовых задач |
| Qwen3-Coder-Next (фев. 2026) | 80B, 3B активных | 262K | vLLM ≥ 0.15, `--tool-call-parser qwen3_coder`, Apache-2.0 |
| Qwen3-Coder-480B-A35B | 480B, 35B активных | 262K (до 1M) | тяжёлая |
| Qwen3.6-27B (апр. 2026) | 27B dense | 262K | SWE-bench Verified 77,2 |
| Qwen3.8-27B (авг. 2026) | 27B dense | 262K (до 1M) | **принимает изображения**, Apache-2.0 |

Если в контуре стоит модель с поддержкой изображений, скриншоты для ручных кейсов можно давать напрямую.
Точную модель и окно контекста уточните у владельцев внутренней платформы: от этого зависит, сколько
файлов можно давать агенту за раз (`contextWindowSize` в настройках).

## Открытые стандарты
- **AGENTS.md** — «README для агентов», под управлением Agentic AI Foundation (Linux Foundation, с декабря 2025); 60k+ проектов. Поддерживают Codex, Gemini CLI, Copilot, Cursor, VS Code, Zed, Aider, goose, opencode, Junie, Windsurf, Devin и др. Рекомендуемое содержимое: обзор проекта, команды сборки и тестов, стиль кода, правила тестирования, безопасность, правила коммитов. Ближайший к файлу AGENTS.md главнее; запрос пользователя главнее всех. [agents.md](https://agents.md/)
- **Agent Skills (SKILL.md)** — открытый стандарт от Anthropic: папка со `SKILL.md` (+ `scripts/`, `references/`, `assets/`); `name` до 64 символов, совпадает с папкой; `description` до 1024 символов — что делает и **когда** использовать; тело до ~5000 токенов / 500 строк; справочники на один уровень вглубь. В официальном списке клиентов Qwen Code нет, но формат у него тот же. [agentskills.io/specification](https://agentskills.io/specification)

Совет из Superpowers (writing-skills) по полю `description`: только условия срабатывания («Используй, когда…»), без пересказа шагов —
иначе модель выполнит пересказ вместо чтения скилла.

Источники: [npm @qwen-code/qwen-code](https://registry.npmjs.org/@qwen-code/qwen-code), [документация Qwen Code](https://qwenlm.github.io/qwen-code-docs/en/) (configuration/settings, model-providers, features/skills, sub-agents, commands, mcp, hooks, headless, extension), [Qwen3-Coder-30B-A3B](https://huggingface.co/Qwen/Qwen3-Coder-30B-A3B-Instruct), [Qwen3-Coder-Next](https://huggingface.co/qwen/qwen3-coder-next), [Qwen3.8-27B](https://huggingface.co/Qwen/Qwen3.8-27B), [AGENTS.md](https://agents.md/), [Agentic AI Foundation](https://openai.com/index/agentic-ai-foundation/).
