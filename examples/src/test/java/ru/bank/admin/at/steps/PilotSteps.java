package ru.bank.admin.at.steps;

import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import ru.bank.admin.at.model.PilotData;
import ru.bank.admin.at.pages.PilotsPage;

/**
 * Бизнес-шаги для повторяющихся сценариев. Название шага = строка из ручного тест-кейса,
 * поэтому Allure-отчёт читается как кейс. Шаг не проверяет результат — это делает тест.
 */
public final class PilotSteps {

    private final Page page;

    public PilotSteps(Page page) {
        this.page = page;
    }

    @Step("Создать пилот «{data.name}» через форму")
    public void createPilot(PilotData data) {
        new PilotsPage(page).open().clickCreate().fill(data).save();
    }
}
