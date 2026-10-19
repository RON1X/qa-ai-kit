# Схема БД для автотестов

> Только таблицы, которые трогают тесты, и их соседи через внешний ключ.
> Генерируется `scripts/db-schema-to-md.sql` (PostgreSQL), затем человек дописывает значения статусов.
> Обновлять после релизов с миграциями. Версия миграций на момент выгрузки: **[ЗАПОЛНИТЬ]**.
> Ниже — пример формата. Таблицы вымышлены.

## pilot — пилоты стратегий
| Колонка | Тип | NULL | Описание / значения |
|---|---|---|---|
| id | bigint | нет | PK |
| name | varchar(100) | нет | уникально; тестовые — с префиксом `AT-` |
| strategy_code | varchar(50) | нет | FK → strategy.code |
| product_code | varchar(50) | нет | `CONSUMER_LOAN`, `CREDIT_CARD`, ... |
| segment_code | varchar(50) | нет | код сегмента, уходит в сервис сегментов |
| coverage_pct | numeric(5,2) | нет | 1–100 |
| status | varchar(20) | нет | `DRAFT` → `SIMULATION` → `ROLLOUT` → `ROLLED_BACK` |
| created_by | varchar(50) | нет | логин автора |
| created_at | timestamp | нет | |

Пример строки: `42 | AT-1015-pilot-1a2b3c4d | CREDIT_V2 | CONSUMER_LOAN | AT_SEG_1A2B3C4D | 15.00 | DRAFT | autotest_admin | 2026-10-15 10:00`

## change_history — история изменения (аудит, только дописывается)
| Колонка | Тип | NULL | Описание / значения |
|---|---|---|---|
| id | bigint | нет | PK, растёт по времени |
| entity_type | varchar(30) | нет | `PILOT`, `SIMULATION`, `ROLLOUT`, `STRATEGY` |
| entity_id | bigint | нет | id сущности |
| action | varchar(30) | нет | `CREATE`, `UPDATE`, `STATUS_CHANGE`, `ROLLBACK` |
| actor | varchar(50) | нет | логин |
| created_at | timestamp | нет | |
| details | jsonb | да | `{"field": "...", "old": ..., "new": ...}` |

Пример строки: `1001 | PILOT | 42 | CREATE | autotest_admin | 2026-10-15 10:00 | null`
