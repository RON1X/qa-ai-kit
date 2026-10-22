package ru.bank.admin.at.mock;

import java.time.Instant;

/** Запрос, который наша система отправила во внешнюю систему и который записал мок-сервер. */
public record RecordedRequest(String method, String path, String body, Instant receivedAt) {

    @Override
    public String toString() {
        return receivedAt + " " + method + " " + path + "\n" + body;
    }
}
