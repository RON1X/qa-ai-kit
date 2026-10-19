package ru.bank.admin.at.db;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Типизированные запросы по разделу "Пилоты". Тест работает с record-ами,
 * а не с Map и не с SQL-строками: так проверки читаются как требования.
 *
 * ВНИМАНИЕ: имена таблиц и колонок — пример. Сверяйтесь с docs/db-schema.md своего проекта.
 */
public final class PilotDb {

    private final Db db;

    public PilotDb(Db db) {
        this.db = db;
    }

    public Optional<PilotRow> findByName(String name) {
        return db.queryOne("pilot/find_by_name.sql", Map.of("name", name)).map(PilotRow::from);
    }

    public List<HistoryRow> history(long pilotId) {
        return db.query("history/by_entity.sql", Map.of("entityType", "PILOT", "entityId", pilotId))
                .stream().map(HistoryRow::from).toList();
    }

    /** Аварийная уборка через БД, если у админки нет API удаления. SQL защищён префиксом AT-. */
    public static void deleteTestPilot(long pilotId) {
        Db.readWrite().update("pilot/delete_test_pilot.sql", Map.of("id", pilotId));
    }

    public record PilotRow(long id, String name, String strategyCode, String productCode,
                           String segmentCode, BigDecimal coveragePct, String status, String createdBy) {

        static PilotRow from(Map<String, Object> row) {
            return new PilotRow(
                    ((Number) row.get("id")).longValue(),
                    (String) row.get("name"),
                    (String) row.get("strategy_code"),
                    (String) row.get("product_code"),
                    (String) row.get("segment_code"),
                    new BigDecimal(row.get("coverage_pct").toString()),
                    (String) row.get("status"),
                    (String) row.get("created_by"));
        }
    }

    public record HistoryRow(long id, String action, String actor, LocalDateTime createdAt) {

        static HistoryRow from(Map<String, Object> row) {
            return new HistoryRow(
                    ((Number) row.get("id")).longValue(),
                    (String) row.get("action"),
                    (String) row.get("actor"),
                    ((Timestamp) row.get("created_at")).toLocalDateTime());
        }
    }
}
