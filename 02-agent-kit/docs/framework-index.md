> Пример: сгенерировано `scripts/index-framework.sh` по коду `03-java-examples`. В вашем репозитории перегенерируйте.

# Оглавление фреймворка (сгенерировано 2026-10-15, не править руками)

## pages

### PilotForm
Модальное окно "Новый пилот". Все локаторы ищутся внутри диалога, а не по всей странице.
```
public Locator root()
public PilotForm fill(PilotData data)
public PilotForm fillCoverage(String value)
public Locator coverageField()
public Locator coverageError()
public void save()
```

### PilotsPage
Раздел "Пилоты". Page Object отдаёт действия и локаторы, но НЕ проверяет бизнес-результат:
```
public PilotsPage open()
public Locator heading()
public PilotForm clickCreate()
public Locator rowByName(String name)
public Locator notification()
```

## steps

### PilotSteps
Бизнес-шаги для повторяющихся сценариев. Название шага = строка из ручного тест-кейса,
```
public void createPilot(PilotData data)
```

## db

### Db
Доступ к БД стенда. Два пула:
```
public static Db readOnly()
public static Db readWrite()
public List<Map<String, Object>> query(String sqlFile, Map<String, ?> params)
public Optional<Map<String, Object>> queryOne(String sqlFile, Map<String, ?> params)
public int update(String sqlFile, Map<String, ?> params)
public void close()
```

### PilotDb
Типизированные запросы по разделу "Пилоты". Тест работает с record-ами,
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
Данные формы "Новый пилот". valid() даёт полностью валидный набор с уникальными
```
public static PilotData valid()
public PilotData withCoveragePct(String value)
```

