package ru.bank.admin.at.api;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import ru.bank.admin.at.core.Attach;

import java.util.Map;

/**
 * База для клиентов API разделов (PilotApi, StrategyApi ...). Делает три вещи:
 * сериализует тело через Jackson, прикладывает запрос и ответ в Allure, возвращает {@link ApiResponse}.
 * Клиент не проверяет статус и не бросает исключений на 4xx/5xx: это решение теста.
 */
public abstract class ApiClient {

    private final APIRequestContext request;

    protected ApiClient(APIRequestContext request) {
        this.request = request;
    }

    protected ApiResponse get(String path) {
        return get(path, Map.of());
    }

    protected ApiResponse get(String path, Map<String, String> query) {
        RequestOptions options = RequestOptions.create();
        query.forEach(options::setQueryParam);
        return log("GET", path, null, request.get(path, options));
    }

    protected ApiResponse post(String path, Object body) {
        String json = Json.toJson(body);
        return log("POST", path, json, request.post(path, jsonBody(json)));
    }

    protected ApiResponse put(String path, Object body) {
        String json = Json.toJson(body);
        return log("PUT", path, json, request.put(path, jsonBody(json)));
    }

    protected ApiResponse delete(String path) {
        return log("DELETE", path, null, request.delete(path));
    }

    /** Для негативных тестов: отправить заведомо битый JSON или тело, которое не собрать моделью. */
    protected ApiResponse postRaw(String path, String rawBody) {
        return log("POST", path, rawBody, request.post(path, jsonBody(rawBody)));
    }

    private static RequestOptions jsonBody(String json) {
        return RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(json);
    }

    private static ApiResponse log(String method, String path, String requestBody, APIResponse response) {
        ApiResponse result = new ApiResponse(method, response.url(), response.status(), response.text());
        StringBuilder sb = new StringBuilder()
                .append(method).append(' ').append(response.url()).append('\n');
        if (requestBody != null) {
            sb.append("\n--- request ---\n").append(Json.pretty(requestBody)).append('\n');
        }
        sb.append("\n--- response ").append(response.status()).append(" ---\n").append(Json.pretty(result.body()));
        Attach.text(method + " " + path + " -> " + response.status(), sb.toString());
        response.dispose();
        return result;
    }
}
