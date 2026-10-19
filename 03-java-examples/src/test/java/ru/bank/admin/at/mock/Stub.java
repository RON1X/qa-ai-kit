package ru.bank.admin.at.mock;

import java.time.Duration;
import java.util.Objects;

/**
 * Описание стаба, не зависящее от инструмента.
 *
 * correlationKey — уникальное значение из данных теста (код сегмента, номер заявки), которое
 * наша система пересылает во внешнюю систему. Стаб срабатывает только на запросы с этим ключом,
 * поэтому параллельные тесты не перехватывают ответы друг друга. Стаб без ключа на общем
 * мок-сервере запрещён.
 */
public record Stub(String method, String path, String correlationKey,
                   int status, String responseBody, Duration delay, int priority) {

    public Stub {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(correlationKey, "correlationKey: стаб без ключа сломает параллельные тесты");
        Objects.requireNonNull(delay, "delay");
    }

    public static Builder post(String path) {
        return new Builder("POST", path);
    }

    public static Builder get(String path) {
        return new Builder("GET", path);
    }

    public static final class Builder {
        private final String method;
        private final String path;
        private String correlationKey;
        private Duration delay = Duration.ZERO;
        /** Выше приоритет (меньше число) — стаб теста побеждает стабы по умолчанию. */
        private int priority = 1;

        private Builder(String method, String path) {
            this.method = method;
            this.path = path;
        }

        public Builder whenBodyContains(String correlationKey) {
            this.correlationKey = correlationKey;
            return this;
        }

        public Builder withDelay(Duration delay) {
            this.delay = delay;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Stub respond(int status, String responseBody) {
            return new Stub(method, path, correlationKey, status, responseBody, delay, priority);
        }
    }
}
