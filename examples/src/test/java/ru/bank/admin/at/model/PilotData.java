package ru.bank.admin.at.model;

import lombok.Builder;
import lombok.Value;
import ru.bank.admin.at.core.TestData;

/**
 * Данные формы «Новый пилот» (как их видит пользователь: подписи справочников, текст в полях).
 * valid() даёт валидный набор с уникальными именем и кодом сегмента; тест меняет только проверяемое
 * поле: PilotData.valid().toBuilder().coveragePct("101").build().
 *
 * strategy и product — справочные значения, которые должны существовать на стенде.
 */
@Value
@Builder(toBuilder = true)
public class PilotData {
    String name;
    String strategy;
    String product;
    String segmentCode;
    String coveragePct;

    public static PilotData valid() {
        return PilotData.builder()
                .name(TestData.unique("pilot"))
                .strategy("Кредитная стратегия v2")
                .product("Потребительский кредит")
                .segmentCode(TestData.uniqueCode("SEG"))
                .coveragePct("15")
                .build();
    }
}
