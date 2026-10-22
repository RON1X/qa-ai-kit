package ru.bank.admin.at.core;

import ru.bank.admin.at.config.Cfg;

import java.util.UUID;

/**
 * Уникальные тестовые данные. Каждая сущность, созданная тестом, получает имя
 * вида AT-<прогон>-<префикс>-<хвост>. По нему тест находит «свою» запись в общей БД
 * и в журнале мок-сервера, а ночной уборщик — всё, что оставили автотесты.
 */
public final class TestData {

    private TestData() {
    }

    public static String unique(String prefix) {
        return "AT-" + Cfg.get().runId() + "-" + prefix + "-" + shortId();
    }

    /** Код без дефисов, в верхнем регистре: для полей, где разрешены только [A-Z0-9_]. */
    public static String uniqueCode(String prefix) {
        return ("AT_" + prefix + "_" + shortId()).toUpperCase();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
