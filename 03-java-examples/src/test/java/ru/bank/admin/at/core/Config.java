package ru.bank.admin.at.core;

/**
 * Единая точка чтения настроек. Порядок: -Dключ=значение, затем переменная окружения
 * (ключ в верхнем регистре, точки заменены на "_": db.ro.user -> DB_RO_USER).
 * Пароли и токены никогда не коммитятся: передаются из секретов CI или локального .env.
 */
public final class Config {

    private Config() {
    }

    public static String baseUrl() {
        return required("base.url");
    }

    public static boolean headless() {
        return Boolean.parseBoolean(get("headless", "true"));
    }

    public static String adminLogin() {
        return required("admin.login");
    }

    public static String adminPassword() {
        return required("admin.password");
    }

    public static String dbUrl() {
        return required("db.url");
    }

    /** Пользователь только с SELECT: для проверок. */
    public static String dbRoUser() {
        return required("db.ro.user");
    }

    public static String dbRoPassword() {
        return required("db.ro.password");
    }

    /** Пользователь с правом записи: только подготовка данных и очистка. */
    public static String dbRwUser() {
        return required("db.rw.user");
    }

    public static String dbRwPassword() {
        return required("db.rw.password");
    }

    /** Адрес API управления мок-сервером (SberMock). */
    public static String mockAdminUrl() {
        return required("mock.admin.url");
    }

    public static String get(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(envName(key));
        }
        return value != null ? value : defaultValue;
    }

    public static String required(String key) {
        String value = get(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Не задан параметр '" + key + "': передайте -D" + key
                    + "=... или переменную окружения " + envName(key));
        }
        return value;
    }

    private static String envName(String key) {
        return key.toUpperCase().replace('.', '_');
    }
}
