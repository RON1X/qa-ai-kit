# Контракт API для автотестов

> Источник правды для агента при написании API-тестов и моделей. Лучше всего — ссылка на OpenAPI/Swagger
> плюс выжимка ниже по эндпоинтам, которые покрываем. Нет эндпоинта здесь — агент спрашивает, а не угадывает.
> [ЗАПОЛНИТЬ]. Ниже — пример формата.

Базовый адрес: `api.url` из конфига. Авторизация: [ЗАПОЛНИТЬ: кука сессии / Bearer-токен].
Формат ошибок: `{"errorCode": "...", "message": "...", "fieldErrors": [{"field": "...", "message": "..."}]}`.

## POST /pilots — создать пилот
Запрос:
| Поле | Тип | Обяз. | Правила |
|---|---|---|---|
| name | string | да | 1–100 символов, уникально |
| strategyCode | string | да | существующая стратегия |
| productCode | string | да | `CONSUMER_LOAN`, `CREDIT_CARD`, ... |
| segmentCode | string | да | `[A-Z0-9_]`, проверяется в сервисе сегментов |
| coveragePct | number | да | целое 1–100 |

Ответы:
| Код | Когда | Тело |
|---|---|---|
| 201 | создан | пилот (`id`, все поля запроса, `status=DRAFT`, `createdBy`, `createdAt`) |
| 400 | ошибка валидации | ошибка с `fieldErrors` |
| 409 | имя уже занято | `errorCode=PILOT_NAME_EXISTS` |
| 502 | сервис сегментов недоступен | `errorCode=SEGMENT_SERVICE_UNAVAILABLE` |

Пример:
```json
{"name": "AT-1015-pilot-1a2b3c4d", "strategyCode": "CREDIT_V2", "productCode": "CONSUMER_LOAN",
 "segmentCode": "AT_SEG_1A2B3C4D", "coveragePct": 15}
```

## GET /pilots/{id}, DELETE /pilots/{id}
[ЗАПОЛНИТЬ по тому же формату]
