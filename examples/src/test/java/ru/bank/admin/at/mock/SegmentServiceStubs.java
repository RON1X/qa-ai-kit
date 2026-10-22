package ru.bank.admin.at.mock;

import java.time.Duration;

/**
 * Готовые ответы внешней системы "Сервис сегментов" (пример интеграции).
 * Один класс на интеграцию. Тело ответа — полная копия реального ответа, а не только
 * поля, которые читает тест: урезанный мок прячет ошибки разбора на стороне админки.
 *
 * Путь, поля и коды ошибок — пример. Берите из docs/integrations.md своего проекта.
 */
public final class SegmentServiceStubs {

    public static final String VALIDATE_PATH = "/segment-service/api/v1/segments/validate";

    /** Таймаут клиента админки к сервису сегментов. Узнать у разработки; стаб должен его превышать. */
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(30);

    private SegmentServiceStubs() {
    }

    public static Stub valid(String segmentCode, int clientsCount) {
        return Stub.post(VALIDATE_PATH)
                .whenBodyContains(segmentCode)
                .respond(200, """
                        {
                          "segmentCode": "%s",
                          "status": "VALID",
                          "clientsCount": %d,
                          "checkedAt": "2026-10-15T10:00:00Z",
                          "warnings": []
                        }""".formatted(segmentCode, clientsCount));
    }

    public static Stub notFound(String segmentCode) {
        return Stub.post(VALIDATE_PATH)
                .whenBodyContains(segmentCode)
                .respond(404, """
                        {"errorCode": "SEGMENT_NOT_FOUND", "message": "Segment %s not found"}"""
                        .formatted(segmentCode));
    }

    public static Stub serverError(String segmentCode) {
        return Stub.post(VALIDATE_PATH)
                .whenBodyContains(segmentCode)
                .respond(500, """
                        {"errorCode": "INTERNAL_ERROR", "message": "Unexpected error"}""");
    }

    public static Stub timeout(String segmentCode) {
        return Stub.post(VALIDATE_PATH)
                .whenBodyContains(segmentCode)
                .withDelay(CLIENT_TIMEOUT.plusSeconds(5))
                .respond(200, "{}");
    }
}
