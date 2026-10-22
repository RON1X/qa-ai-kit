# Эталонный каркас под стек

Образец, на который равняются инструкции из `../instructions/`. Домен вымышленный (админка стратегий,
раздел «Пилоты»): локаторы, таблицы, пути API и интеграций замените своими. Переносите классы в свой проект
вручную, адаптируя пакеты, — это не библиотека.

```
config/      ProjectConfig (Owner, MERGE: -D → config/${env}.properties → default), Cfg
core/        PlaywrightExtension (Playwright на поток, контекст на тест, трасса при падении),
             AuthState (вход один раз), TestData (AT-<прогон>-...), Attach (Allure), Cleanup
api/         Json (Jackson), ApiClient (Jackson + Allure, без проверок), ApiResponse, ApiExtension,
             PilotApi; model/ — запросы и ответы на Lombok (@Value @Builder(toBuilder) @Jacksonized)
db/          DbClient (JDBC, readOnly/readWrite, SQL из resources/sql с ?), PilotDb (строки на Lombok)
mock/        MockServer, Stub, Mocks (JUnit-расширение), SberMockClient (шаблон, TODO), SegmentServiceStubs
model/       PilotData — данные формы
pages/       PilotsPage, PilotForm, components/DataTable
steps/       PilotSteps (@Step)
tests/ui/    CreatePilotTest (UI + БД + мок), PilotCoverageValidationTest (границы)
tests/api/   PilotApiTest (позитив + параметризованный негатив + БД)
resources/   config/*.properties, allure.properties, junit-platform.properties, sql/**
```

Запуск (после замены домена на свой): `mvn test -Denv=ift -Dadmin.password=... -Ddb.ro.password=... -Ddb.rw.password=...`

Код проверен компиляцией против исходников библиотек (см. корневой README), против живого приложения не запускался.
