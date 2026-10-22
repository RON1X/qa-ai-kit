package ru.bank.admin.at.api;

import com.microsoft.playwright.APIRequestContext;
import io.qameta.allure.Step;
import ru.bank.admin.at.api.model.CreatePilotRequest;
import ru.bank.admin.at.api.model.PilotResponse;
import ru.bank.admin.at.core.TestData;

import java.math.BigDecimal;

/**
 * Клиент API раздела «Пилоты». Один метод = один эндпоинт, возвращает {@link ApiResponse} без проверок.
 * Для подготовки данных в других тестах есть «удобные» методы create...() — они требуют успеха.
 *
 * Пути и поля — пример. [ЗАПОЛНИТЬ] по контракту вашего API.
 */
public final class PilotApi extends ApiClient {

    public static final String PILOTS = "/pilots";

    public PilotApi(APIRequestContext request) {
        super(request);
    }

    /** Валидный запрос с уникальными именем и сегментом. Тест меняет только проверяемое поле через toBuilder(). */
    public static CreatePilotRequest validRequest() {
        return CreatePilotRequest.builder()
                .name(TestData.unique("pilot"))
                .strategyCode("CREDIT_V2")
                .productCode("CONSUMER_LOAN")
                .segmentCode(TestData.uniqueCode("SEG"))
                .coveragePct(new BigDecimal("15"))
                .build();
    }

    @Step("API: создать пилот")
    public ApiResponse create(CreatePilotRequest body) {
        return post(PILOTS, body);
    }

    @Step("API: получить пилот {id}")
    public ApiResponse get(long id) {
        return get(PILOTS + "/" + id);
    }

    @Step("API: удалить пилот {id}")
    public ApiResponse delete(long id) {
        return delete(PILOTS + "/" + id);
    }

    /** Подготовка данных для других тестов: создаёт пилот и возвращает его, иначе падает с понятной причиной. */
    @Step("Подготовка: создать пилот через API")
    public PilotResponse createOk(CreatePilotRequest body) {
        return create(body).requireSuccess().as(PilotResponse.class);
    }
}
