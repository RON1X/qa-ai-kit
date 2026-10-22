package ru.bank.admin.at.db;

import ru.bank.admin.at.config.Cfg;
import ru.bank.admin.at.config.ProjectConfig;
import ru.bank.admin.at.core.Attach;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Доступ к PostgreSQL стенда на чистом JDBC (без ORM и без лишних зависимостей).
 * - readOnly()  — для проверок; пользователь БД только с SELECT;
 * - readWrite() — только подготовка данных и уборка.
 * SQL лежит в src/test/resources/sql/<раздел>/<что_делает>.sql с параметрами "?".
 * Каждый запрос с параметрами прикладывается в Allure — упавшую проверку можно повторить руками.
 *
 * Соединение открывается на каждый запрос: для e2e это быстро и просто. Если запросов сотни — подключите HikariCP.
 */
public final class DbClient {

    private static final Map<String, String> SQL_CACHE = new ConcurrentHashMap<>();

    private final String user;
    private final String password;
    private final boolean readOnly;

    private DbClient(String user, String password, boolean readOnly) {
        this.user = user;
        this.password = password;
        this.readOnly = readOnly;
    }

    public static DbClient readOnly() {
        ProjectConfig cfg = Cfg.get();
        return new DbClient(cfg.dbRoUser(), Cfg.required(cfg.dbRoPassword(), "db.ro.password"), true);
    }

    public static DbClient readWrite() {
        ProjectConfig cfg = Cfg.get();
        return new DbClient(cfg.dbRwUser(), Cfg.required(cfg.dbRwPassword(), "db.rw.password"), false);
    }

    public <T> List<T> query(String sqlFile, RowMapper<T> mapper, Object... params) {
        String sql = sql(sqlFile);
        attach(sqlFile, sql, params);
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setReadOnly(readOnly);
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Ошибка SQL в " + sqlFile + ": " + e.getMessage(), e);
        }
    }

    /** Ноль строк — пустой Optional; больше одной — ошибка: тест ищет конкретную запись. */
    public <T> Optional<T> queryOne(String sqlFile, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sqlFile, mapper, params);
        if (rows.size() > 1) {
            throw new IllegalStateException("Ожидали не больше одной строки из " + sqlFile + ", получили " + rows.size());
        }
        return rows.stream().findFirst();
    }

    public int update(String sqlFile, Object... params) {
        if (readOnly) {
            throw new IllegalStateException("update через readOnly-клиент: для записи используйте DbClient.readWrite()");
        }
        String sql = sql(sqlFile);
        attach(sqlFile, sql, params);
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Ошибка SQL в " + sqlFile + ": " + e.getMessage(), e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(Cfg.get().dbUrl(), user, password);
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private static void attach(String file, String sql, Object... params) {
        Attach.text("SQL " + file, sql + "\n\n-- params: " + Arrays.toString(params));
    }

    private static String sql(String file) {
        return SQL_CACHE.computeIfAbsent(file, path -> {
            try (InputStream in = DbClient.class.getResourceAsStream("/sql/" + path)) {
                if (in == null) {
                    throw new IllegalArgumentException("Нет файла src/test/resources/sql/" + path);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
