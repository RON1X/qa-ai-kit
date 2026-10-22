package ru.bank.admin.at.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;

/**
 * Компонент «таблица с данными». Один класс на все таблицы админки: страница передаёт корень,
 * компонент знает, как найти строку и ячейку. Это композиция, а не наследование от BasePage.
 */
public final class DataTable {

    private final Locator root;

    public DataTable(Locator root) {
        this.root = root;
    }

    /** Строка по уникальному тексту (имени тестовой сущности). */
    public Locator row(String uniqueText) {
        return root.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(uniqueText));
    }

    /** Ячейка строки по номеру колонки (с 0). Колонку по заголовку ищите через data-qa ячейки, если он есть. */
    public Locator cell(String uniqueText, int columnIndex) {
        return row(uniqueText).getByRole(AriaRole.CELL).nth(columnIndex);
    }

    /** Все строки с данными (без заголовка). Для проверок количества: assertThat(table.dataRows()).hasCount(n). */
    public Locator dataRows() {
        return root.getByRole(AriaRole.ROWGROUP).last().getByRole(AriaRole.ROW);
    }
}
