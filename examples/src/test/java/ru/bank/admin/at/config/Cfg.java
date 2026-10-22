package ru.bank.admin.at.config;

import org.aeonbits.owner.ConfigCache;

/** Единственная точка доступа к настройкам. Экземпляр создаётся один раз и кешируется Owner. */
public final class Cfg {

    private Cfg() {
    }

    public static ProjectConfig get() {
        return ConfigCache.getOrCreate(ProjectConfig.class);
    }

    /** Для обязательных параметров: понятная ошибка вместо NullPointerException в глубине теста. */
    public static String required(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Не задан параметр '" + key + "': передайте -D" + key
                    + "=... или добавьте в config/<env>.properties");
        }
        return value;
    }
}
