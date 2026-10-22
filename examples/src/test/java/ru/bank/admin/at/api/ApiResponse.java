package ru.bank.admin.at.api;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Ответ API, отвязанный от Playwright: статус и тело читаются сразу, поэтому объект живёт
 * и после закрытия APIRequestContext. Клиент возвращает его без проверок — проверяет тест.
 */
public record ApiResponse(String method, String url, int status, String body) {

    public <T> T as(Class<T> type) {
        return Json.fromJson(body, type);
    }

    public <T> T as(TypeReference<T> type) {
        return Json.fromJson(body, type);
    }

    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }

    /** Для подготовки данных: если API не ответил успехом, дальше тест смысла не имеет. */
    public ApiResponse requireSuccess() {
        if (!isSuccessful()) {
            throw new IllegalStateException("Подготовка данных не удалась: " + method + " " + url
                    + " -> " + status + "\n" + body);
        }
        return this;
    }
}
