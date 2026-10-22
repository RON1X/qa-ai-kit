# Метрики проекта автотестов

## Какие метрики считать
| Метрика | Как считать | Зачем |
|---|---|---|
| Число тестов по слоям (UI / API / DB) | тестовые методы по `@Tag` или пакету | баланс пирамиды: UI дорогой и медленный, API дешевле |
| По критичности | `@Severity` (BLOCKER…TRIVIAL) | что упадёт важного |
| Покрытие ручных кейсов | кейсы, на которые есть `@TmsLink` / все кейсы, отдельно по приоритету | риск там, где он важен; покрытие высокого приоритета важнее общего |
| Флаки | тесты с разным исходом при перезапусках / метка `flaky` в Allure | флаки обесценивают красный прогон |
| Pass rate | passed / выполненные | только вместе с флаки |
| Длительность | сумма, медиана, 90-й перцентиль, самые долгие | что ускорять |
| Качество разметки | тесты без `@Severity`, без `@TmsLink` | без разметки не построить ни отчёт, ни покрытие |
| Техдолг | паузы, XPath, `TODO(locator)` | прямые причины нестабильности |

**Данные Google по флаки** (Google Testing Blog, май 2016): около 1,5% всех прогонов тестов дают «флаки»-результат;
почти 16% тестов имеют некоторую нестабильность; около **84% переходов «прошёл → упал» связаны с флаки-тестами**.
Меры у Google: перезапуск упавших, пометка флаки (падение засчитывается после 3 подряд), автокарантин с заведением бага
(с предупреждением, что карантин может спрятать реальную гонку), отслеживание изменения флакости.

## Что вводит в заблуждение
- Число тестов само по себе: сто тестов на одну форму ≠ десять на десять разделов.
- Pass rate без учёта флаки и отключённых тестов.
- «Покрытие кода» для UI e2e: e2e измеряют сценарии, а не строки.

## Откуда брать данные
**Статика (исходники)** — аннотации JUnit и Allure: `@Test`, `@ParameterizedTest`, `@Tag`, `@Severity`, `@Epic`, `@Feature`,
`@TmsLink`, `@Disabled`. Ограничение: число случаев `@MethodSource` видно только при прогоне.

**Результаты прогона (Allure 2, `allure-results/*-result.json`)**: `uuid`, `historyId` (одинаковый у перезапусков одного
теста), `fullName`, `status` (`passed`, `failed`, `broken`, `skipped`, `unknown`), `statusDetails` (`known`, `muted`,
`flaky`, `message`, `trace`), `start`/`stop` (мс), `labels` (`severity`, `epic`, `feature`, `story`, `tag`, `suite`,
`owner`…), `links` (`tms`, `issue`).

**История**: в Allure 2 папка `history` отчёта (`history.json`, `history-trend.json`, `duration-trend.json`,
`retry-trend.json`) — чтобы видеть тренды, копируйте её в `allure-results` перед генерацией следующего отчёта.
В Allure 3 история — один файл JSONL (`historyPath`).

**JUnit dry-run** (JUnit Platform 1.13.3+): `junit.platform.execution.dryRun.enabled=true` — тесты не выполняются,
слушатели получают события «пропущен» для всех тестов плана. Проверено по исходникам JUnit 5.14. Не проверено: попадут
ли такие результаты в Allure и раскрываются ли параметризованные тесты (скорее нет — их вызовы создаются при выполнении).

## Что сделано в наборе
`instructions/scripts/TestMetrics.java` — один файл, без зависимостей, Java 11+:
`java scripts/TestMetrics.java --src src/test/java --cases test-cases --allure target/allure-results --out target/metrics.md`.
Проверен на эталонном коде, на наборе граничных случаев (закомментированные тесты, `@CsvSource` с text block,
`@Disabled` на классе, строки с `@Test` внутри литералов) и на искусственных результатах Allure с перезапуском.
Скилл `test-metrics` запрещает модели придумывать числа: только вывод скрипта.

## Источники
- [Google Testing Blog, May 2016 — Flaky Tests at Google and How We Mitigate Them](https://testing.googleblog.com/2016/05/)
- [Allure: test result file](https://allurereport.org/docs/how-it-works-test-result-file/)
- [Allure: history files](https://allurereport.org/docs/how-it-works-history-files/)
- JUnit 5.14.1, исходники `LauncherConstants.DRY_RUN_PROPERTY_NAME` и `EngineExecutionOrchestrator` (github.com/junit-team/junit-framework, тег r5.14.1)
