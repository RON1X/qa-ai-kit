# Каркас тестового класса

Заменяй `<...>`. Удаляй блоки, которые не нужны сценарию (например, моки для чисто UI-валидации).
Полный рабочий пример — `tests/pilots/CreatePilotTest.java`.

```java
package <пакет>.tests.<раздел>;

import com.microsoft.playwright.Page;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.TmsLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import <пакет>.core.Cleanup;
import <пакет>.core.PlaywrightExtension;
import <пакет>.mock.Mocks;
// + DAO, стабы, модель, страницы раздела

import java.time.Duration;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat; // UI
import static org.assertj.core.api.BDDAssertions.then;                             // данные
import static org.awaitility.Awaitility.await;

@Epic("Админка стратегий")
@Feature("<Раздел>")
@Tag("e2e")
// Порядок важен: PlaywrightExtension первым — уборка и журнал моков отработают до закрытия браузера
@ExtendWith({PlaywrightExtension.class, Cleanup.Extension.class, Mocks.Extension.class})
class <Сущность><Действие>Test {

    private final <Сущность>Db db = new <Сущность>Db(Db.readOnly());

    /**
     * Ловит: <какой дефект продукта поймает тест>.
     */
    @Test
    @TmsLink("<Раздел>/<Подраздел>/TC-<NNN>")
    @DisplayName("<название ручного кейса>")
    void <поведениеПоАнглийски>(Page page, Mocks mocks, Cleanup cleanup) {
        // 1. Подготовка: уникальные данные, уборка ДО действия, стабы внешних систем
        <Сущность>Data data = <Сущность>Data.valid();
        cleanup.add("удалить <сущность> " + data.name(),
                () -> db.findByName(data.name()).ifPresent(row -> <Сущность>Db.deleteTest<Сущность>(row.id())));
        mocks.stub(<Система>Stubs.<сценарий>(data.<ключ>()));

        // 2. Действие (шаги кейса)
        new <Сущность>Steps(page).<действие>(data);

        // 3. Проверки UI
        assertThat(<локатор>).hasText("<ожидаемый текст из кейса>");

        // 4. Проверки БД (запись может появиться асинхронно)
        var row = await("<что ждём>").atMost(Duration.ofSeconds(15))
                .until(() -> db.findByName(data.name()), java.util.Optional::isPresent)
                .orElseThrow();
        then(row.status()).isEqualTo("<литерал из кейса>");

        // 5. Что система отправила во внешнюю систему
        var sent = mocks.awaitSingleRequest(<Система>Stubs.<PATH>, data.<ключ>());
        // разбери JSON и проверь ключевые поля литералами
    }
}
```

## Чек перед запуском
- Нет `Thread.sleep`, `waitForTimeout`, `NETWORKIDLE`.
- Каждый новый локатор взят из снимка/кода/codegen (или помечен `TODO(locator)`).
- Каждое ожидание — литерал, а не результат того же кода.
- Уборка зарегистрирована до действия.
- Стабы — с ключом теста; глобального reset нет.
