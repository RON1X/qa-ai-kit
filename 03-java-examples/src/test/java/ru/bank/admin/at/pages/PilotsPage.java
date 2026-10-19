package ru.bank.admin.at.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Раздел "Пилоты". Page Object отдаёт действия и локаторы, но НЕ проверяет бизнес-результат:
 * проверки живут в тесте. Исключение — open(): ждёт признак загруженной страницы.
 *
 * Локаторы: роль + доступное имя > подпись поля > data-qa. CSS/XPath — только с комментарием почему.
 */
public final class PilotsPage {

    private final Page page;

    public PilotsPage(Page page) {
        this.page = page;
    }

    public PilotsPage open() {
        page.navigate("/pilots");
        assertThat(heading()).isVisible();
        return this;
    }

    public Locator heading() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Пилоты"));
    }

    public PilotForm clickCreate() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Создать пилот")).click();
        return new PilotForm(page);
    }

    /** Строка таблицы по уникальному имени пилота. Таблица помечена data-qa="pilots-table". */
    public Locator rowByName(String name) {
        return page.getByTestId("pilots-table")
                .getByRole(AriaRole.ROW)
                .filter(new Locator.FilterOptions().setHasText(name));
    }

    /** Всплывающее уведомление (role="alert"). */
    public Locator notification() {
        return page.getByRole(AriaRole.ALERT);
    }
}
