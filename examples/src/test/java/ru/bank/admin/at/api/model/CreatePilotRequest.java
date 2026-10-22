package ru.bank.admin.at.api.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * Тело POST /pilots. Неизменяемая модель: @Value + @Builder(toBuilder = true).
 * toBuilder позволяет в тесте поменять одно поле валидного запроса: valid().toBuilder().coveragePct(...).build().
 * @Jacksonized — чтобы Jackson умел собирать объект через билдер (нужно, если модель ещё и читается из JSON).
 * Деньги и проценты — BigDecimal, не double.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class CreatePilotRequest {
    String name;
    String strategyCode;
    String productCode;
    String segmentCode;
    BigDecimal coveragePct;
}
