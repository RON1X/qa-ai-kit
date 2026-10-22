package ru.bank.admin.at.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Один настроенный ObjectMapper на весь проект (после настройки он потокобезопасен).
 *
 * Почему не RequestOptions.setData(объект): Playwright сериализует объекты своим встроенным Gson
 * и игнорирует аннотации Jackson (@JsonProperty, @JsonInclude) и Lombok-билдеры с @Jacksonized.
 * Поэтому тело запроса всегда готовим здесь и передаём строкой.
 */
public final class Json {

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            // Новые поля в ответе не должны ронять функциональные тесты. Контракт проверяйте отдельно.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // null-поля не отправляем: так негативный тест «поле не передано» пишется через билдер без поля
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private Json() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать " + value.getClass().getSimpleName(), e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ответ не разбирается как " + type.getSimpleName() + ":\n" + json, e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ответ не разбирается как " + type.getType() + ":\n" + json, e);
        }
    }

    public static JsonNode tree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не JSON:\n" + json, e);
        }
    }

    /** Для вложений в Allure; не-JSON возвращается как есть. */
    public static String pretty(String json) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
