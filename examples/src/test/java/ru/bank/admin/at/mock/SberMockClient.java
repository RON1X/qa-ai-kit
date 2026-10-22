package ru.bank.admin.at.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.bank.admin.at.api.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ШАБЛОН адаптера к SberMock. Структура рабочая, но ПУТИ API И ИМЕНА ПОЛЕЙ ВЫДУМАНЫ
 * и помечены TODO(SberMock). Заполните их один раз по внутренней документации SberMock,
 * проверьте на стенде и закоммитьте. Агенту запрещено "угадывать" этот API.
 *
 * Что нужно узнать в документации SberMock:
 *  1. Как создать стаб через API (метод, путь, формат тела) и что возвращается (id стаба).
 *  2. Как удалить один стаб по id.
 *  3. Как прочитать журнал полученных запросов с фильтром по пути и/или телу.
 *  4. Как задать условие на тело запроса (contains / JSONPath / regex), задержку и приоритет.
 *  5. Есть ли изоляция по проектам/командам (префикс, namespace, отдельный инстанс).
 * Если API нет, а стабы настраиваются только через UI или файлы — см. docs/integrations.md (в наборе: instructions/docs/integrations.md).
 */
public final class SberMockClient implements MockServer {

    // TODO(SberMock): реальные пути API управления
    private static final String STUBS_PATH = "/TODO-sbermock/stubs";
    private static final String JOURNAL_PATH = "/TODO-sbermock/requests";

    private final String adminUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SberMockClient(String adminUrl) {
        this.adminUrl = adminUrl.endsWith("/") ? adminUrl.substring(0, adminUrl.length() - 1) : adminUrl;
    }

    @Override
    public String register(Stub stub) {
        ObjectNode body = toSberMockFormat(stub);
        JsonNode created = Json.tree(send("POST", STUBS_PATH, body.toString()));
        return created.path("id").asText(); // TODO(SberMock): поле с идентификатором созданного стаба
    }

    @Override
    public void remove(String stubId) {
        send("DELETE", STUBS_PATH + "/" + encode(stubId), null); // TODO(SberMock): удаление одного стаба
    }

    @Override
    public List<RecordedRequest> requests(String path, String correlationKey) {
        // TODO(SberMock): параметры фильтрации журнала. Если сервер не умеет фильтровать — фильтруем здесь.
        String response = send("GET", JOURNAL_PATH + "?path=" + encode(path), null);
        List<RecordedRequest> result = new ArrayList<>();
        for (JsonNode r : Json.tree(response)) {
            String body = r.path("body").asText();
            if (body.contains(correlationKey)) {
                result.add(new RecordedRequest(
                        r.path("method").asText(),
                        r.path("path").asText(),
                        body,
                        Instant.parse(r.path("receivedAt").asText())));
            }
        }
        return result;
    }

    /** TODO(SberMock): единственное место, где описание стаба переводится в формат инструмента. */
    private static ObjectNode toSberMockFormat(Stub stub) {
        ObjectNode json = Json.MAPPER.createObjectNode();
        ObjectNode request = json.putObject("request");
        request.put("method", stub.method());
        request.put("path", stub.path());
        request.putArray("bodyContains").add(stub.correlationKey());

        ObjectNode response = json.putObject("response");
        response.put("status", stub.status());
        response.put("body", stub.responseBody());
        response.put("delayMs", stub.delay().toMillis());

        json.put("priority", stub.priority());
        return json;
    }

    private String send(String method, String path, String jsonBody) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(adminUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        request.method(method, jsonBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("SberMock " + method + " " + path + " -> HTTP "
                        + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("SberMock недоступен: " + adminUrl + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
