> Пример: сгенерировано `scripts/index-framework.sh` по коду `examples/`. В вашем репозитории перегенерируйте.

# Оглавление фреймворка (сгенерировано 2026-10-22, не править руками)

## pages

### PilotForm
Модальное окно «Новый пилот». Все локаторы ищутся внутри диалога, а не по всей странице.
```
public Locator root()
public PilotForm fill(PilotData data)
public PilotForm fillCoverage(String value)
public Locator coverageField()
public Locator coverageError()
public void save()
```

### PilotsPage
Раздел «Пилоты». Правила Page Object в проекте:
```
public PilotsPage open()
public Locator heading()
public PilotForm clickCreate()
public Locator rowByName(String name)
public Locator notification()
```

### DataTable
Компонент «таблица с данными». Один класс на все таблицы админки: страница передаёт корень,
```
public Locator row(String uniqueText)
public Locator cell(String uniqueText, int columnIndex)
public Locator dataRows()
```

## steps

### PilotSteps
Бизнес-шаги для повторяющихся сценариев. Название шага = строка из ручного тест-кейса,
```
public void createPilot(PilotData data)
```

## api

### ApiClient
База для клиентов API разделов (PilotApi, StrategyApi ...). Делает три вещи:
```
protected ApiResponse get(String path)
protected ApiResponse get(String path, Map<String, String> query)
protected ApiResponse post(String path, Object body)
protected ApiResponse put(String path, Object body)
protected ApiResponse delete(String path)
protected ApiResponse postRaw(String path, String rawBody)
```

### ApiExtension
Фикстура для API-тестов: новый APIRequestContext на тест, с baseURL из конфига и сессией из {@link AuthState}.
```
public boolean supportsParameter(ParameterContext param, ExtensionContext ctx)
public Object resolveParameter(ParameterContext param, ExtensionContext ctx)
public void afterEach(ExtensionContext ctx)
```

### ApiResponse
Ответ API, отвязанный от Playwright: статус и тело читаются сразу, поэтому объект живёт
```
public <T> T as(Class<T> type)
public <T> T as(TypeReference<T> type)
public boolean isSuccessful()
public ApiResponse requireSuccess()
```

### Json
Один настроенный ObjectMapper на весь проект (после настройки он потокобезопасен).
```
public static String toJson(Object value)
public static <T> T fromJson(String json, Class<T> type)
public static <T> T fromJson(String json, TypeReference<T> type)
public static JsonNode tree(String json)
public static String pretty(String json)
```

### PilotApi
Клиент API раздела «Пилоты». Один метод = один эндпоинт, возвращает {@link ApiResponse} без проверок.
```
public static CreatePilotRequest validRequest()
public ApiResponse create(CreatePilotRequest body)
public ApiResponse get(long id)
public ApiResponse delete(long id)
public PilotResponse createOk(CreatePilotRequest body)
```

### ApiError
Тело ошибки API. [ЗАПОЛНИТЬ] Сверьте поля с реальным форматом ошибок вашего API.
```
поля: String errorCode, String message, List<FieldError> fieldErrors, String field, String message
```

### CreatePilotRequest
Тело POST /pilots. Неизменяемая модель: @Value + @Builder(toBuilder = true).
```
поля: String name, String strategyCode, String productCode, String segmentCode, BigDecimal coveragePct
```

### PilotResponse
Ответ GET/POST /pilots. Поля — ровно как в контракте API (см. docs/api.md проекта).
```
поля: Long id, String name, String strategyCode, String productCode, String segmentCode, BigDecimal coveragePct, String status, String createdBy, String createdAt
```

## db

### DbClient
Доступ к PostgreSQL стенда на чистом JDBC (без ORM и без лишних зависимостей).
```
public static DbClient readOnly()
public static DbClient readWrite()
public <T> List<T> query(String sqlFile, RowMapper<T> mapper, Object... params)
public <T> Optional<T> queryOne(String sqlFile, RowMapper<T> mapper, Object... params)
public int update(String sqlFile, Object... params)
```

### PilotDb
Запросы по разделу «Пилоты». Тест работает с типизированными строками, а не с ResultSet и SQL:
```
public Optional<PilotRow> findByName(String name)
public List<HistoryRow> history(long pilotId)
public static void deleteTestPilot(long pilotId)
```

## mock

### MockServer
Контракт фреймворка с мок-сервером. Тесты и стабы зависят только от этого интерфейса,
```
String register(Stub stub);
void remove(String stubId);
List<RecordedRequest> requests(String path, String correlationKey);
```

### Mocks
Стабы одного теста. Помнит, что создал, и в конце теста удаляет только своё.
```
public void stub(Stub stub)
public List<RecordedRequest> requests(String path, String correlationKey)
public RecordedRequest awaitSingleRequest(String path, String correlationKey)
public void afterEach(ExtensionContext ctx)
public boolean supportsParameter(ParameterContext param, ExtensionContext ctx)
public Object resolveParameter(ParameterContext param, ExtensionContext ctx)
```

### RecordedRequest
Запрос, который наша система отправила во внешнюю систему и который записал мок-сервер.
```
public String toString()
```

### SberMockClient
ШАБЛОН адаптера к SberMock. Структура рабочая, но ПУТИ API И ИМЕНА ПОЛЕЙ ВЫДУМАНЫ
```
public String register(Stub stub)
public void remove(String stubId)
public List<RecordedRequest> requests(String path, String correlationKey)
```

### SegmentServiceStubs
Готовые ответы внешней системы "Сервис сегментов" (пример интеграции).
```
public static Stub valid(String segmentCode, int clientsCount)
public static Stub notFound(String segmentCode)
public static Stub serverError(String segmentCode)
public static Stub timeout(String segmentCode)
```

### Stub
Описание стаба, не зависящее от инструмента.
```
public static Builder post(String path)
public static Builder get(String path)
public Builder whenBodyContains(String correlationKey)
public Builder withDelay(Duration delay)
public Builder priority(int priority)
public Stub respond(int status, String responseBody)
```

## model

### PilotData
Данные формы «Новый пилот» (как их видит пользователь: подписи справочников, текст в полях).
```
поля: String name, String strategy, String product, String segmentCode, String coveragePct
public static PilotData valid()
```

## config

### Cfg
Единственная точка доступа к настройкам. Экземпляр создаётся один раз и кешируется Owner.
```
public static ProjectConfig get()
public static String required(String value, String key)
```

### ProjectConfig
Все настройки проекта — один интерфейс Owner. Порядок источников (MERGE: первый найденный ключ побеждает):
```
String baseUrl();
String apiUrl();
boolean headless();
String locale();
String adminLogin();
String adminPassword();
String dbUrl();
String dbRoUser();
String dbRoPassword();
String dbRwUser();
String dbRwPassword();
String mockAdminUrl();
String runId();
```

