package ru.bank.admin.at.mock;

import java.util.List;

/**
 * Контракт фреймворка с мок-сервером. Тесты и стабы зависят только от этого интерфейса,
 * поэтому конкретный инструмент (SberMock, WireMock, свой) подключается одним адаптером.
 *
 * Правило общего стенда: никаких глобальных reset. Тест удаляет только стабы, которые создал сам.
 */
public interface MockServer {

    /** Регистрирует стаб и возвращает его идентификатор в мок-сервере. */
    String register(Stub stub);

    /** Удаляет один стаб по идентификатору. */
    void remove(String stubId);

    /**
     * Запросы, которые мок-сервер получил на path и в теле которых есть correlationKey.
     * Фильтр по ключу обязателен: на общем стенде в журнал пишут все параллельные тесты.
     */
    List<RecordedRequest> requests(String path, String correlationKey);
}
