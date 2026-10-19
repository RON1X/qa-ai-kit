package ru.bank.admin.at.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
 * Если API нет, а стабы настраиваются только через UI или файлы — см. 02-agent-kit/docs/integrations.md.
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
        JsonObject body = toSberMockFormat(stub);
        JsonObject created = JsonParser.parseString(send("POST", STUBS_PATH, body.toString())).getAsJsonObject();
        return created.get("id").getAsString(); // TODO(SberMock): поле с идентификатором созданного стаба
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
        for (JsonElement element : JsonParser.parseString(response).getAsJsonArray()) {
            JsonObject r = element.getAsJsonObject();
            String body = r.get("body").getAsString();
            if (body.contains(correlationKey)) {
                result.add(new RecordedRequest(
                        r.get("method").getAsString(),
                        r.get("path").getAsString(),
                        body,
                        Instant.parse(r.get("receivedAt").getAsString())));
            }
        }
        return result;
    }

    /** TODO(SberMock): единственное место, где описание стаба переводится в формат инструмента. */
    private static JsonObject toSberMockFormat(Stub stub) {
        JsonObject request = new JsonObject();
        request.addProperty("method", stub.method());
        request.addProperty("path", stub.path());
        JsonArray bodyContains = new JsonArray();
        bodyContains.add(stub.correlationKey());
        request.add("bodyContains", bodyContains);

        JsonObject response = new JsonObject();
        response.addProperty("status", stub.status());
        response.addProperty("body", stub.responseBody());
        response.addProperty("delayMs", stub.delay().toMillis());

        JsonObject json = new JsonObject();
        json.add("request", request);
        json.add("response", response);
        json.addProperty("priority", stub.priority());
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
