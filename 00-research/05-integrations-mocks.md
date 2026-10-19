# Интеграции и мок-сервер в UI e2e: общие паттерны

SberMock — внутренний инструмент, его API здесь не описывается. Ниже — общепринятые паттерны работы
с мок-сервером на общем стенде; за эталон взят WireMock 3.x как самый документированный. Сопоставьте
каждый пункт с возможностями SberMock и запишите ответы в `02-agent-kit/docs/integrations.md`.

## Что должен уметь мок-сервер для e2e (вопросы к SberMock)
| Возможность | Зачем | Как в WireMock 3.x |
|---|---|---|
| Создать стаб через API и получить его id | стаб под конкретный тест | `wm.register(...)` → `StubMapping.getId()` |
| Удалить **один** стаб | не трогать чужие | `removeStubMapping(id)`; по метке — `removeStubsByMetadataPattern` |
| Условие на тело запроса | корреляция по ключу теста | `matchingJsonPath("$.clientId", equalTo(key))`, `equalToJson(...)` |
| Приоритет стабов | стаб теста важнее стаба по умолчанию | `atPriority(1)` (1 — высший, по умолчанию 5) |
| Журнал полученных запросов | проверить, что система **отправила** | `verifyThat(...)`, `find(...)`, `getServeEvents(...)` |
| Задержка и сбои | таймауты и деградация | `withFixedDelay(ms)`, `withFault(Fault.CONNECTION_RESET_BY_PEER / MALFORMED_RESPONSE_CHUNK)` |
| Состояние (сценарии) | «503, потом 200» для ретраев | `inScenario(...).whenScenarioStateIs(...).willSetStateTo(...)` |
| Шаблонизация ответа | один стаб отвечает ключом из запроса | `{{jsonPath request.body '$.x'}}` + `withTransformers("response-template")` |
| Колбэки | асинхронные ответы внешней системы | webhooks (в ядре с 3.1) |
| Запись и воспроизведение | реалистичные ответы со стенда | `startRecording` / `snapshotRecord` |
| Стабы в файлах | стабы по умолчанию под версионным контролем | `mappings/*.json`, `__files/` |

WireMock: стабильная `org.wiremock:wiremock-standalone:3.13.2`; ветка 4.x в бете (Java 17, ломающие изменения).

## Изоляция на общем мок-сервере
1. **Уникальный ключ в каждом условии стаба.** Рекомендация MockServer для параллельных тестов — уникальное значение на тест в каждом матчере; полная изоляция — отдельный инстанс на тест ([mock-server.com](https://mock-server.com/mock_server/running_tests_in_parallel.html)).
2. **Ключ — из бизнес-данных.** Браузер не добавит заголовок в запрос бэкенда к внешней системе. Значит, ключ — то, что тест вводит в форму и что бэкенд пересылает: код сегмента, номер заявки. Заголовок `traceparent` работает, только если бэкенд его пробрасывает (по спецификации W3C Trace Context — обязан).
3. **Приоритеты слоями**: стабы по умолчанию — низкий приоритет, стабы теста — высокий.
4. **Никакого глобального reset**: `reset()` удаляет все стабы **и** журнал — параллельные тесты падают случайно. Встроенное JUnit-расширение WireMock делает reset перед каждым тестом — это правильно только для своего in-JVM сервера.
5. **Уникальные имена сценариев** (`"retry-" + key`): состояние сценария глобально.
6. **Проверка запросов — только по ключу**: `verify(1, postRequestedFor(url))` без ключа посчитает запросы соседей. Журнал ограничен по размеру — проверять сразу после действия.
7. **Задержки занимают потоки мок-сервера** (в WireMock по умолчанию 10): много параллельных «таймаутов по 30 с» могут заблокировать остальных — уменьшите таймауты клиента на тестовом стенде или включите асинхронные ответы.
8. **Уборка в `afterEach` в `finally`** + чистка «сирот» по метке прогона на старте набора.

## Какие сценарии интеграций проверять в UI e2e
Успех; бизнес-ошибка (4xx/код отказа) → понятное сообщение; 5xx/разрыв/битый ответ → деградация без
«половинчатого» сохранения; таймаут; пустые/неполные данные; ретраи и идемпотентность (один и тот же ключ
идемпотентности во всех попытках — без идемпотентности повтор операции с побочным эффектом небезопасен);
**что система отправила** (сопоставление полей формы и запроса); асинхронные колбэки.
Матрица — `02-agent-kit/.qwen/skills/integration-mocks/references/case-matrix.md`.

## Пирамида: где какая проверка
- Чем выше уровень, тем меньше тестов; ошибка, найденная наверху без падения внизу, — повод дописать тест внизу ([Fowler, Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html)). Google предлагает стартовое соотношение 70/20/10 и перечисляет беды e2e: медленные, нестабильные, плохо локализуют сбой ([Google Testing Blog](https://testing.googleblog.com/2015/04/just-say-no-to-more-end-to-end-tests.html)).
- **UI e2e**: успех, видимые пользователю деградации, ключевые поля исходящего запроса.
- **API-тесты против того же мок-сервера**: все коды ошибок и комбинации полей.
- **Контрактные тесты** защищают от расхождения стаба с реальной системой: упавший контракт — повод обновить дублёров и поговорить со смежниками ([Fowler, ContractTest](https://martinfowler.com/bliki/ContractTest.html)). Pact — для контракта, не для UI и бизнес-логики, и плохо подходит для публичных/внешних API ([docs.pact.io](https://docs.pact.io/getting_started/what_is_pact_good_for)).

## Паттерн в коде фреймворка
- Интерфейс `MockServer` (зарегистрировать / удалить / прочитать журнал) — тесты не знают про конкретный инструмент.
- Адаптер под инструмент (`SberMockClient`) — единственное место с его API.
- Классы `<Система>Stubs` с методами на каждый ответ.
- JUnit-расширение, которое помнит созданные стабы, удаляет только их и при падении прикладывает журнал в Allure. При падении журнал прикладывать в `afterEach` по `ctx.getExecutionException()`, а не в `TestWatcher`: ресурсы хранилища JUnit закрываются раньше вызова `TestWatcher`.
Реализация — `03-java-examples/src/test/java/ru/bank/admin/at/mock/`.

## Справка: то же на WireMock (если понадобится сравнить)
```java
WireMock wm = new WireMock("mock.test.local", 8080);   // экземпляр, а не статический configureFor: тот хранится в ThreadLocal
String clientId = "AT-" + UUID.randomUUID();

wm.register(post(urlPathEqualTo("/scoring/v1/check"))
    .atPriority(1)
    .withRequestBody(matchingJsonPath("$.clientId", equalTo(clientId)))
    .withMetadata(metadata().attr("testId", testId))
    .willReturn(okJson("{\"decision\":\"APPROVED\"}")));

// ... действие в UI ...

await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
    wm.verifyThat(exactly(1), postRequestedFor(urlPathEqualTo("/scoring/v1/check"))
        .withRequestBody(equalToJson(
            "{\"clientId\":\"" + clientId + "\",\"amount\":500000,\"currency\":\"RUB\"}",
            true, true))));   // ignoreArrayOrder, ignoreExtraElements
```
`VerificationException` наследует `AssertionError`, поэтому `untilAsserted` её повторяет.

## Kafka/MQ (кратко)
На общем стенде: сообщения с уникальным ключом, консьюмер с отдельным `group.id` на прогон, фильтр по ключу.
`auto.offset.reset` по умолчанию `latest`: дождитесь назначения партиций до действия в UI, иначе сообщение
можно пропустить. Ожидание — Awaitility. Локально — Testcontainers `org.testcontainers.kafka.KafkaContainer`.

Источники: [wiremock.org/docs](https://wiremock.org/docs/) (stubbing, verifying, request-matching, simulating-faults, stateful-behaviour, response-templating, webhooks-and-callbacks, record-playback, standalone), [WireMock 3.13.2 source](https://github.com/wiremock/wiremock/tree/3.13.2), [jvt.me: better WireMock stubbing](https://www.jvt.me/posts/2021/06/01/better-wiremock-stubbing), [W3C Trace Context](https://www.w3.org/TR/trace-context/), [JUnit extension order](https://docs.junit.org/6.0.2/extensions/relative-execution-order-of-user-code-and-extensions.html), [Kafka consumer configs](https://kafka.apache.org/40/configuration/consumer-configs/), [Testcontainers Kafka](https://java.testcontainers.org/modules/kafka/).
