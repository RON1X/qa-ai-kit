package ru.bank.admin.at.api.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * Ответ GET/POST /pilots. Поля — ровно как в контракте API (см. docs/api.md проекта).
 * Неизвестные поля игнорируются глобально (Json.MAPPER), поэтому новое поле в API тест не уронит.
 * Даты — строкой: без модуля jackson-datatype-jsr310 Jackson не разбирает java.time.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class PilotResponse {
    Long id;
    String name;
    String strategyCode;
    String productCode;
    String segmentCode;
    BigDecimal coveragePct;
    String status;
    String createdBy;
    String createdAt;
}
