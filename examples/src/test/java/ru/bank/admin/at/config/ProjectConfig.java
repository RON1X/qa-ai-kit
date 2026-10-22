package ru.bank.admin.at.config;

import org.aeonbits.owner.Config;

/**
 * Все настройки проекта — один интерфейс Owner. Порядок источников (MERGE: первый найденный ключ побеждает):
 * 1) -Dключ=значение (CI, локальный запуск), 2) файл стенда config/${env}.properties (-Denv=ift),
 * 3) config/default.properties.
 *
 * Пароли в файлы не пишем: только -D из секретов CI (Jenkins Credentials) или локальный незакоммиченный файл.
 * Доступ из кода — только через {@link Cfg#get()}.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "classpath:config/${env}.properties",
        "classpath:config/default.properties"
})
public interface ProjectConfig extends Config {

    @Key("base.url")
    String baseUrl();

    @Key("api.url")
    String apiUrl();

    @Key("headless")
    @DefaultValue("true")
    boolean headless();

    @Key("browser.locale")
    @DefaultValue("ru-RU")
    String locale();

    @Key("admin.login")
    String adminLogin();

    @Key("admin.password")
    String adminPassword();

    @Key("db.url")
    String dbUrl();

    /** Пользователь только с SELECT — для проверок. */
    @Key("db.ro.user")
    String dbRoUser();

    @Key("db.ro.password")
    String dbRoPassword();

    /** Пользователь с правом записи — только подготовка данных и уборка. */
    @Key("db.rw.user")
    String dbRwUser();

    @Key("db.rw.password")
    String dbRwPassword();

    @Key("mock.admin.url")
    String mockAdminUrl();

    /** Метка прогона в именах тестовых данных. В CI: -Drun.id=${BUILD_NUMBER}. */
    @Key("run.id")
    @DefaultValue("local")
    String runId();
}
