package ru.bank.admin.at.api.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/** Тело ошибки API. [ЗАПОЛНИТЬ] Сверьте поля с реальным форматом ошибок вашего API. */
@Value
@Builder
@Jacksonized
public class ApiError {
    String errorCode;
    String message;
    List<FieldError> fieldErrors;

    @Value
    @Builder
    @Jacksonized
    public static class FieldError {
        String field;
        String message;
    }
}
