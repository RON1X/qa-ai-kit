package ru.bank.admin.at.tests.ui.pilots;

import com.microsoft.playwright.Page;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.TmsLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.bank.admin.at.core.PlaywrightExtension;
import ru.bank.admin.at.pages.PilotForm;
import ru.bank.admin.at.pages.PilotsPage;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Граничные значения и классы эквивалентности поля "Процент охвата".
 * Требование (пример): целое число от 1 до 100.
 *   Классы: < 1 | 1..100 | > 100 | не целое | не число.
 *   Границы: 0, 1, 100, 101 (+ -1 как представитель отрицательных).
 * Только клиентская валидация: форма не отправляется, поэтому ни БД, ни моки не нужны.
 */
@Epic("Админка стратегий")
@Feature("Пилоты")
@Tag("ui")
@ExtendWith(PlaywrightExtension.class)
class PilotCoverageValidationTest {

    /** Ловит: форма принимает охват вне диапазона или дробный охват. */
    @ParameterizedTest(name = "[{index}] охват «{0}» -> ошибка «{1}»")
    @CsvSource(delimiter = '|', value = {
            "0    | Значение от 1 до 100",
            "101  | Значение от 1 до 100",
            "-1   | Значение от 1 до 100",
            "12.5 | Только целое число",
            "abc  | Только целое число",
    })
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("Пилоты/Добавление/TC-004")
    @DisplayName("Недопустимый процент охвата отклоняется")
    void rejectsInvalidCoverage(String value, String expectedError, Page page) {
        PilotForm form = new PilotsPage(page).open().clickCreate().fillCoverage(value);
        form.save();

        assertThat(form.coverageError()).hasText(expectedError);
        assertThat(form.root()).isVisible();
    }

    /** Ловит: ошибка "на единицу" в проверке границ (строгое сравнение вместо нестрогого). */
    @ParameterizedTest(name = "[{index}] охват «{0}» принимается")
    @ValueSource(strings = {"1", "100"})
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("Пилоты/Добавление/TC-003")
    @DisplayName("Граничные значения процента охвата принимаются")
    void acceptsBoundaryCoverage(String value, Page page) {
        PilotForm form = new PilotsPage(page).open().clickCreate().fillCoverage("0");
        form.save();
        // Сначала доказываем, что валидация в этом сценарии вообще срабатывает. Без этого шага
        // "ошибки нет" было бы правдой и при отключённой валидации — тест не смог бы упасть.
        assertThat(form.coverageError()).isVisible();

        form.fillCoverage(value);

        assertThat(form.coverageError()).isHidden();
    }
}
