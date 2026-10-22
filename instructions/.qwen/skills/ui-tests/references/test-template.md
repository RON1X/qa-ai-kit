# Каркас UI-теста

Полный рабочий пример — `tests/ui/pilots/CreatePilotTest.java`. Удаляй блоки, которые не нужны сценарию.

```java
package <пакет>.tests.ui.<раздел>;

// ... импорты

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat; // UI
import static org.assertj.core.api.BDDAssertions.then;                             // данные
import static org.awaitility.Awaitility.await;

@Epic("<Продукт>")
@Feature("<Раздел>")
@Tag("ui")
// Порядок важен: расширения, объявленные позже, выполняют afterEach раньше —
// уборка и журнал моков отрабатывают до закрытия браузера
@ExtendWith({PlaywrightExtension.class, Cleanup.Extension.class, Mocks.Extension.class})
class <Сущность><Действие>Test {

    private final <Сущность>Db db = new <Сущность>Db(DbClient.readOnly());

    /**
     * Ловит: <какой дефект продукта поймает тест>.
     */
    @Test
    @Severity(SeverityLevel.<CRITICAL|NORMAL|MINOR>)   // из приоритета кейса
    @TmsLink("<Раздел>/<Подраздел>/TC-<NNN>")
    @DisplayName("<название ручного кейса>")
    void <поведениеПоАнглийски>(Page page, Mocks mocks, Cleanup cleanup) {
        // 1. Подготовка: уникальные данные, уборка ДО действия, стабы внешних систем
        <Сущность>Data data = <Сущность>Data.valid();
        cleanup.add("удалить <сущность> " + data.getName(),
                () -> db.findByName(data.getName()).ifPresent(row -> <Сущность>Db.deleteTest<Сущность>(row.getId())));
        mocks.stub(<Система>Stubs.<сценарий>(data.get<Ключ>()));

        // 2. Действие (шаги кейса)
        new <Сущность>Steps(page).<действие>(data);

        // 3. UI
        assertThat(<страница>.notification()).containsText("<текст из кейса>");

        // 4. БД (запись может появиться асинхронно)
        var row = await("<что ждём>").atMost(Duration.ofSeconds(15))
                .until(() -> db.findByName(data.getName()), Optional::isPresent)
                .orElseThrow();
        then(row.getStatus()).isEqualTo("<литерал из кейса>");

        // 5. Что система отправила во внешнюю систему
        RecordedRequest sent = mocks.awaitSingleRequest(<Система>Stubs.<PATH>, data.get<Ключ>());
        then(Json.tree(sent.body()).path("<поле>").asText()).isEqualTo("<значение>");
    }
}
```

## Параметризованный тест (границы, классы эквивалентности)
```java
@ParameterizedTest(name = "[{index}] охват «{0}» -> ошибка «{1}»")
@CsvSource(delimiter = '|', value = {
        "0    | Значение от 1 до 100",   // нижняя граница - 1
        "101  | Значение от 1 до 100",   // верхняя граница + 1
})
void rejectsInvalidCoverage(String value, String expectedError, Page page) { ... }
```
Аргументы из источника идут первыми, параметры расширений (`Page`, `Cleanup`) — после них.

## Перед запуском
- Нет `Thread.sleep`, `waitForTimeout`, `NETWORKIDLE`.
- Каждый новый локатор подтверждён снимком/кодом/codegen или помечен `TODO(locator)`.
- Ожидания — литералы, а не значения, прочитанные из той же системы.
- Уборка зарегистрирована до действия; стабы — с ключом теста.
