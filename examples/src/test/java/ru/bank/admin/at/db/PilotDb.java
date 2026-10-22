package ru.bank.admin.at.db;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Запросы по разделу «Пилоты». Тест работает с типизированными строками, а не с ResultSet и SQL:
 * так проверки читаются как требования.
 *
 * ВНИМАНИЕ: таблицы и колонки — пример. Сверяйтесь с docs/db-schema.md своего проекта.
 */
public final class PilotDb {

    private final DbClient db;

    public PilotDb(DbClient db) {
        this.db = db;
    }

    public Optional<PilotRow> findByName(String name) {
        return db.queryOne("pilot/find_by_name.sql", PilotRow::from, name);
    }

    public List<HistoryRow> history(long pilotId) {
        return db.query("history/by_entity.sql", HistoryRow::from, "PILOT", pilotId);
    }

    /** Аварийная уборка через БД, если у продукта нет API удаления. SQL защищён префиксом AT-. */
    public static void deleteTestPilot(long pilotId) {
        DbClient.readWrite().update("pilot/delete_test_pilot.sql", pilotId);
    }

    @Value
    @Builder
    public static class PilotRow {
        long id;
        String name;
        String strategyCode;
        String productCode;
        String segmentCode;
        BigDecimal coveragePct;
        String status;
        String createdBy;

        static PilotRow from(ResultSet rs) throws SQLException {
            return PilotRow.builder()
                    .id(rs.getLong("id"))
                    .name(rs.getString("name"))
                    .strategyCode(rs.getString("strategy_code"))
                    .productCode(rs.getString("product_code"))
                    .segmentCode(rs.getString("segment_code"))
                    .coveragePct(rs.getBigDecimal("coverage_pct"))
                    .status(rs.getString("status"))
                    .createdBy(rs.getString("created_by"))
                    .build();
        }
    }

    @Value
    @Builder
    public static class HistoryRow {
        long id;
        String action;
        String actor;
        LocalDateTime createdAt;

        static HistoryRow from(ResultSet rs) throws SQLException {
            return HistoryRow.builder()
                    .id(rs.getLong("id"))
                    .action(rs.getString("action"))
                    .actor(rs.getString("actor"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .build();
        }
    }
}
