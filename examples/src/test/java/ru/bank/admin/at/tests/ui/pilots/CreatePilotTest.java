package ru.bank.admin.at.tests.ui.pilots;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.TmsLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.bank.admin.at.api.Json;
import ru.bank.admin.at.config.Cfg;
import ru.bank.admin.at.core.Cleanup;
import ru.bank.admin.at.core.PlaywrightExtension;
import ru.bank.admin.at.db.DbClient;
import ru.bank.admin.at.db.PilotDb;
import ru.bank.admin.at.db.PilotDb.HistoryRow;
import ru.bank.admin.at.db.PilotDb.PilotRow;
import ru.bank.admin.at.mock.Mocks;
import ru.bank.admin.at.mock.RecordedRequest;
import ru.bank.admin.at.mock.SegmentServiceStubs;
import ru.bank.admin.at.model.PilotData;
import ru.bank.admin.at.pages.PilotForm;
import ru.bank.admin.at.pages.PilotsPage;
import ru.bank.admin.at.steps.PilotSteps;

import java.time.Duration;
import java.util.Optional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;

/**
 * ЭТАЛОННЫЙ UI-ТЕСТ: мок внешней системы по ключу теста -> действие в UI -> проверка UI ->
 * проверка БД -> проверка того, что система отправила во внешнюю систему -> уборка.
 *
 * Конвенция проверок: assertThat(...) — UI (Playwright, с автоожиданием); then(...) — данные (AssertJ).
 */
@Epic("Админка стратегий")
@Feature("Пилоты")
@Tag("ui")
@ExtendWith({PlaywrightExtension.class, Cleanup.Extension.class, Mocks.Extension.class})
class CreatePilotTest {

    private static final Duration DB_TIMEOUT = Duration.ofSeconds(15);

    private final PilotDb pilotDb = new PilotDb(DbClient.readOnly());

    /**
     * Ловит: пилот не сохраняется или сохраняется с другими полями; не пишется история;
     * в сервис сегментов уходит не тот сегмент или продукт.
     */
    @Test
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("Пилоты/Добавление/TC-001")
    @DisplayName("Создание пилота с валидными данными")
    void createsPilotWithValidData(Page page, Mocks mocks, Cleanup cleanup) {
        PilotData pilot = PilotData.valid();
        // Уборку регистрируем ДО действия и по уникальному имени: сработает, даже если тест упадёт на середине
        cleanup.add("удалить пилот " + pilot.getName(),
                () -> pilotDb.findByName(pilot.getName()).ifPresent(row -> PilotDb.deleteTestPilot(row.getId())));
        mocks.stub(SegmentServiceStubs.valid(pilot.getSegmentCode(), 120_000));

        new PilotSteps(page).createPilot(pilot);

        PilotsPage pilots = new PilotsPage(page);
        assertThat(pilots.notification()).containsText("Пилот создан");
        assertThat(pilots.rowByName(pilot.getName())).containsText("Черновик");

        PilotRow row = await("пилот появился в БД").atMost(DB_TIMEOUT)
                .until(() -> pilotDb.findByName(pilot.getName()), Optional::isPresent)
                .orElseThrow();
        then(row.getStatus()).isEqualTo("DRAFT");
        then(row.getSegmentCode()).isEqualTo(pilot.getSegmentCode());
        then(row.getCoveragePct()).isEqualByComparingTo("15");
        then(row.getCreatedBy()).isEqualTo(Cfg.get().adminLogin());

        await("запись в истории изменений").atMost(DB_TIMEOUT).untilAsserted(() ->
                then(pilotDb.history(row.getId()))
                        .extracting(HistoryRow::getAction, HistoryRow::getActor)
                        .containsExactly(tuple("CREATE", Cfg.get().adminLogin())));

        RecordedRequest sent = mocks.awaitSingleRequest(SegmentServiceStubs.VALIDATE_PATH, pilot.getSegmentCode());
        JsonNode body = Json.tree(sent.body());
        then(body.path("segmentCode").asText()).isEqualTo(pilot.getSegmentCode());
        then(body.path("productCode").asText()).isEqualTo("CONSUMER_LOAN");
    }

    /**
     * Ловит: пилот сохраняется «наполовину» при ошибке интеграции; пользователь не видит причину;
     * форма закрывается и введённые данные теряются.
     */
    @Test
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("Пилоты/Добавление/TC-007")
    @DisplayName("Сервис сегментов вернул 500: ошибка в UI, пилот не сохранён")
    void showsErrorAndDoesNotSaveWhenSegmentServiceFails(Page page, Mocks mocks, Cleanup cleanup) {
        PilotData pilot = PilotData.valid();
        cleanup.add("удалить пилот " + pilot.getName(),
                () -> pilotDb.findByName(pilot.getName()).ifPresent(row -> PilotDb.deleteTestPilot(row.getId())));
        mocks.stub(SegmentServiceStubs.serverError(pilot.getSegmentCode()));

        PilotForm form = new PilotsPage(page).open().clickCreate().fill(pilot);
        form.save();

        assertThat(new PilotsPage(page).notification()).containsText("Сервис сегментов недоступен");
        assertThat(form.root()).isVisible();

        // Сначала доказываем, что запрос во внешнюю систему был: иначе «пилота нет в БД»
        // окажется правдой и при сломанной интеграции, и при незавершённом сохранении.
        mocks.awaitSingleRequest(SegmentServiceStubs.VALIDATE_PATH, pilot.getSegmentCode());
        then(pilotDb.findByName(pilot.getName())).isEmpty();
    }
}
