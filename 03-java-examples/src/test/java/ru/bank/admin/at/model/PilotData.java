package ru.bank.admin.at.model;

import ru.bank.admin.at.core.TestData;

/**
 * Данные формы "Новый пилот". valid() даёт полностью валидный набор с уникальными
 * именем и кодом сегмента; тест меняет только то поле, которое проверяет.
 *
 * strategy и product — справочные значения, которые должны существовать на стенде.
 * Если справочники разные на стендах — вынесите значения в конфиг.
 */
public record PilotData(String name, String strategy, String product, String segmentCode, String coveragePct) {

    public static PilotData valid() {
        return new PilotData(
                TestData.unique("pilot"),
                "Кредитная стратегия v2",
                "Потребительский кредит",
                TestData.uniqueCode("SEG"),
                "15");
    }

    public PilotData withCoveragePct(String value) {
        return new PilotData(name, strategy, product, segmentCode, value);
    }
}
