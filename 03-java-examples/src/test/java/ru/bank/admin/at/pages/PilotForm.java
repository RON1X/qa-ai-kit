package ru.bank.admin.at.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import ru.bank.admin.at.model.PilotData;

/** Модальное окно "Новый пилот". Все локаторы ищутся внутри диалога, а не по всей странице. */
public final class PilotForm {

    private final Locator dialog;

    PilotForm(Page page) {
        this.dialog = page.getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName("Новый пилот"));
    }

    public Locator root() {
        return dialog;
    }

    public PilotForm fill(PilotData data) {
        dialog.getByLabel("Название").fill(data.name());
        // selectOption работает только с нативным <select>. Для кастомного выпадающего списка:
        // click() по полю, затем page.getByRole(AriaRole.OPTION, ...setName(значение)).click()
        dialog.getByLabel("Стратегия").selectOption(data.strategy());
        dialog.getByLabel("Продукт").selectOption(data.product());
        dialog.getByLabel("Код сегмента").fill(data.segmentCode());
        return fillCoverage(data.coveragePct());
    }

    public PilotForm fillCoverage(String value) {
        coverageField().fill(value);
        return this;
    }

    public Locator coverageField() {
        return dialog.getByLabel("Процент охвата");
    }

    public Locator coverageError() {
        return dialog.getByTestId("coverage-pct-error");
    }

    public void save() {
        // Внутри локатора — Locator.GetByRoleOptions, на странице — Page.GetByRoleOptions
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Сохранить")).click();
    }
}
