# Шаблоны для API

Рабочие образцы — `api/ApiClient.java`, `api/PilotApi.java`, `api/model/*`, `tests/api/pilots/PilotApiTest.java`.

## Модель запроса
```java
@Value
@Builder(toBuilder = true)
@Jacksonized
public class Create<Сущность>Request {
    String name;
    String <поле>;
    BigDecimal <сумма или процент>;
}
```
Имя поля в JSON отличается от Java — `@JsonProperty("snake_case_name")` на поле.

## Модель ответа
```java
@Value
@Builder(toBuilder = true)
@Jacksonized
public class <Сущность>Response {
    Long id;
    String name;
    String status;
    String createdAt;   // строка: модуль jackson-datatype-jsr310 в проекте не подключён
}
```
Список в ответе — `response.as(new TypeReference<List<<Сущность>Response>>() {})`.

## Клиент раздела
```java
public final class <Сущность>Api extends ApiClient {

    public static final String PATH = "/<ресурс>";

    public <Сущность>Api(APIRequestContext request) {
        super(request);
    }

    public static Create<Сущность>Request validRequest() {
        return Create<Сущность>Request.builder()
                .name(TestData.unique("<префикс>"))
                // остальные поля — валидные значения, существующие на стенде
                .build();
    }

    @Step("API: создать <сущность>")
    public ApiResponse create(Create<Сущность>Request body) {
        return post(PATH, body);
    }

    @Step("API: получить <сущность> {id}")
    public ApiResponse get(long id) {
        return get(PATH + "/" + id);
    }

    @Step("Подготовка: создать <сущность> через API")
    public <Сущность>Response createOk(Create<Сущность>Request body) {
        return create(body).requireSuccess().as(<Сущность>Response.class);
    }
}
```

## Тест
```java
@Epic("<Продукт>")
@Feature("<Раздел>")
@Tag("api")
@ExtendWith({ApiExtension.class, Cleanup.Extension.class})
class <Сущность>ApiTest {

    /** Ловит: ... */
    @Test
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("<Раздел>/API/TC-<NNN>")
    @DisplayName("POST /<ресурс>: ...")
    void creates<Сущность>(APIRequestContext request, Cleanup cleanup) {
        <Сущность>Api api = new <Сущность>Api(request);
        Create<Сущность>Request body = <Сущность>Api.validRequest();

        ApiResponse response = api.create(body);

        then(response.status()).as("статус ответа").isEqualTo(201);
        <Сущность>Response created = response.as(<Сущность>Response.class);
        cleanup.add("удалить " + created.getId(), () -> api.delete(created.getId()));
        then(created).usingRecursiveComparison().comparingOnlyFields("name", "<поле>").isEqualTo(body);
        then(created.getStatus()).isEqualTo("<литерал>");
    }

    /** Ловит: API принимает недопустимые значения поля <поле>. */
    @ParameterizedTest(name = "[{index}] <поле>={0} -> 400")
    @MethodSource("invalid<Поле>")
    void rejectsInvalid<Поле>(<Тип> value, APIRequestContext request) {
        ApiResponse response = new <Сущность>Api(request)
                .create(<Сущность>Api.validRequest().toBuilder().<поле>(value).build());

        then(response.status()).as("статус ответа").isEqualTo(400);
        then(response.as(ApiError.class).getFieldErrors())
                .extracting(ApiError.FieldError::getField)
                .containsExactly("<поле>");
    }

    static Stream<Arguments> invalid<Поле>() {
        return Stream.of(
                Arguments.of(<min - 1>),   // граница - 1
                Arguments.of(<max + 1>),   // граница + 1
                Arguments.of((<Тип>) null)); // не передано
    }
}
```

## Авторизация
`ApiExtension` берёт куки входа из `AuthState`. Если API работает по токену — в `ApiExtension` замените
`setStorageStatePath(...)` на `setExtraHTTPHeaders(Map.of("Authorization", "Bearer " + token))`; токен — отдельным
запросом логина один раз на поток. Тест «без авторизации → 401» — отдельный `APIRequestContext` без кук и заголовка.
