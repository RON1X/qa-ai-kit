package ru.bank.admin.at.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;
import ru.bank.admin.at.pages.components.DataTable;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Раздел «Пилоты». Правила Page Object в проекте:
 * - отдаёт действия (возвращают следующую страницу/компонент) и локаторы для проверок;
 * - НЕ проверяет бизнес-результат — проверки живут в тесте. Исключение: open() ждёт признак загрузки;
 * - локаторы: роль + доступное имя > подпись поля > data-qa. CSS/XPath — только с комментарием почему.
 */
public final class PilotsPage {

    private final Page page;
    private final DataTable table;

    public PilotsPage(Page page) {
        this.page = page;
        this.table = new DataTable(page.getByTestId("pilots-table"));
    }

    @Step("Открыть раздел «Пилоты»")
    public PilotsPage open() {
        page.navigate("/pilots");
        assertThat(heading()).isVisible();
        return this;
    }

    public Locator heading() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Пилоты"));
    }

    @Step("Нажать «Создать пилот»")
    public PilotForm clickCreate() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Создать пилот")).click();
        return new PilotForm(page);
    }

    public Locator rowByName(String name) {
        return table.row(name);
    }

    /** Всплывающее уведомление (role="alert"). */
    public Locator notification() {
        return page.getByRole(AriaRole.ALERT);
    }
}
