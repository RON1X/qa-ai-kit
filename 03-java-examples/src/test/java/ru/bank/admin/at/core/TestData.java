package ru.bank.admin.at.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Уникальные тестовые данные. Каждая сущность, созданная тестом, получает имя
 * вида AT-<прогон>-<префикс>-<случайный хвост>. По нему тест находит "свою" запись
 * в общей БД и в журнале мок-сервера, а уборщик прогона — всё, что прогон создал.
 */
public final class TestData {

    /** Один идентификатор на весь прогон. В CI передавайте номер сборки: -Drun.id=$BUILD_NUMBER. */
    public static final String RUN_ID = Config.get("run.id",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));

    private TestData() {
    }

    public static String unique(String prefix) {
        return "AT-" + RUN_ID + "-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Код без дефисов и в верхнем регистре: для полей, где разрешены только [A-Z0-9_]. */
    public static String uniqueCode(String prefix) {
        return ("AT_" + prefix + "_" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();
    }
}
