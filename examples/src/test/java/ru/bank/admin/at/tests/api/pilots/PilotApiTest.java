package ru.bank.admin.at.tests.api.pilots;

import com.microsoft.playwright.APIRequestContext;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.TmsLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.bank.admin.at.api.ApiExtension;
import ru.bank.admin.at.api.ApiResponse;
import ru.bank.admin.at.api.PilotApi;
import ru.bank.admin.at.api.model.ApiError;
import ru.bank.admin.at.api.model.CreatePilotRequest;
import ru.bank.admin.at.api.model.PilotResponse;
import ru.bank.admin.at.config.Cfg;
import ru.bank.admin.at.core.Cleanup;
import ru.bank.admin.at.db.DbClient;
import ru.bank.admin.at.db.PilotDb;
import ru.bank.admin.at.db.PilotDb.PilotRow;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * ЭТАЛОННЫЙ API-ТЕСТ. Порядок проверок: статус -> тело ответа -> состояние в БД.
 * Клиент (PilotApi) ничего не проверяет и не падает на 4xx — это делает тест.
 * Ожидания — литералы и значения из запроса, а не значения, прочитанные из того же ответа.
 */
@Epic("Админка стратегий")
@Feature("Пилоты")
@Tag("api")
@ExtendWith({ApiExtension.class, Cleanup.Extension.class})
class PilotApiTest {

    private final PilotDb pilotDb = new PilotDb(DbClient.readOnly());

    /** Ловит: API теряет или искажает поля при создании; неверный статус или автор; ответ расходится с БД. */
    @Test
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("Пилоты/API/TC-101")
    @DisplayName("POST /pilots: пилот создаётся со статусом DRAFT")
    void createsPilot(APIRequestContext request, Cleanup cleanup) {
        PilotApi api = new PilotApi(request);
        CreatePilotRequest body = PilotApi.validRequest();

        ApiResponse response = api.create(body);
        then(response.status()).as("статус ответа").isEqualTo(201);
        PilotResponse created = response.as(PilotResponse.class);
        cleanup.add("удалить пилот " + created.getId(), () -> api.delete(created.getId()));

        // Всё, что отправили, вернулось как есть; служебные поля проверяем отдельно
        then(created)
                .usingRecursiveComparison()
                .comparingOnlyFields("name", "strategyCode", "productCode", "segmentCode")
                .isEqualTo(body);
        then(created.getCoveragePct()).isEqualByComparingTo("15");
        then(created.getStatus()).isEqualTo("DRAFT");
        then(created.getCreatedBy()).isEqualTo(Cfg.get().adminLogin());

        PilotRow row = pilotDb.findByName(body.getName()).orElseThrow();
        then(row.getId()).isEqualTo(created.getId());
        then(row.getStatus()).isEqualTo("DRAFT");
    }

    /** Ловит: API принимает охват вне диапазона 1..100 или пустой; ошибка не указывает поле. */
    @ParameterizedTest(name = "[{index}] coveragePct={0} -> 400")
    @MethodSource("invalidCoverage")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("Пилоты/API/TC-102")
    @DisplayName("POST /pilots: недопустимый процент охвата отклоняется")
    void rejectsInvalidCoverage(BigDecimal coverage, APIRequestContext request, Cleanup cleanup) {
        PilotApi api = new PilotApi(request);
        CreatePilotRequest body = PilotApi.validRequest().toBuilder().coveragePct(coverage).build();
        // Если API по ошибке примет запрос — пилот всё равно будет убран
        cleanup.add("удалить пилот " + body.getName(),
                () -> pilotDb.findByName(body.getName()).ifPresent(row -> PilotDb.deleteTestPilot(row.getId())));

        ApiResponse response = api.create(body);

        then(response.status()).as("статус ответа").isEqualTo(400);
        then(response.as(ApiError.class).getFieldErrors())
                .extracting(ApiError.FieldError::getField)
                .containsExactly("coveragePct");
        // Негатив без записи: отказ не должен оставлять «полупилот» в БД
        then(pilotDb.findByName(body.getName())).isEmpty();
    }

    static Stream<Arguments> invalidCoverage() {
        return Stream.of(
                Arguments.of(new BigDecimal("0")),      // нижняя граница - 1
                Arguments.of(new BigDecimal("101")),    // верхняя граница + 1
                Arguments.of(new BigDecimal("-5")),     // отрицательные
                Arguments.of(new BigDecimal("12.5")),   // не целое
                Arguments.of((BigDecimal) null));       // поле не передано (null не сериализуется)
    }
}
