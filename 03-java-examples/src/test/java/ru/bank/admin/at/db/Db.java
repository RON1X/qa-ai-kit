package ru.bank.admin.at.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import ru.bank.admin.at.core.Attach;
import ru.bank.admin.at.core.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Доступ к БД стенда. Два пула:
 * - readOnly()  — для проверок; пользователь БД с одним SELECT (read-only в пуле — лишь подсказка драйверу);
 * - readWrite() — только для подготовки данных и уборки.
 *
 * SQL живёт в src/test/resources/sql/*.sql с именованными параметрами (:pilotId).
 * Каждый запрос с параметрами прикладывается в Allure — упавшую проверку можно повторить руками.
 * Ключи строк в Map приходят в нижнем регистре (поведение Jdbi по умолчанию).
 */
public final class Db implements AutoCloseable {

    private static final Map<String, Db> POOLS = new ConcurrentHashMap<>();
    private static final Map<String, String> SQL_CACHE = new ConcurrentHashMap<>();

    private final HikariDataSource dataSource;
    private final Jdbi jdbi;

    private Db(String url, String user, String password, boolean readOnly) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(4);
        config.setReadOnly(readOnly);
        config.setPoolName(readOnly ? "e2e-db-ro" : "e2e-db-rw");
        this.dataSource = new HikariDataSource(config);
        this.jdbi = Jdbi.create(dataSource);
    }

    public static Db readOnly() {
        return POOLS.computeIfAbsent("ro", key -> register(
                new Db(Config.dbUrl(), Config.dbRoUser(), Config.dbRoPassword(), true)));
    }

    public static Db readWrite() {
        return POOLS.computeIfAbsent("rw", key -> register(
                new Db(Config.dbUrl(), Config.dbRwUser(), Config.dbRwPassword(), false)));
    }

    public List<Map<String, Object>> query(String sqlFile, Map<String, ?> params) {
        String sql = sql(sqlFile);
        Attach.text("SQL " + sqlFile, sql + "\n\n-- params: " + params);
        return jdbi.withHandle(handle -> handle.createQuery(sql).bindMap(params).mapToMap().list());
    }

    /** Ноль строк — пустой Optional; больше одной — ошибка (тест ищет конкретную запись). */
    public Optional<Map<String, Object>> queryOne(String sqlFile, Map<String, ?> params) {
        List<Map<String, Object>> rows = query(sqlFile, params);
        if (rows.size() > 1) {
            throw new IllegalStateException("Ожидали не больше одной строки из " + sqlFile + ", получили " + rows.size());
        }
        return rows.stream().findFirst();
    }

    public int update(String sqlFile, Map<String, ?> params) {
        String sql = sql(sqlFile);
        Attach.text("SQL " + sqlFile, sql + "\n\n-- params: " + params);
        return jdbi.withHandle(handle -> handle.createUpdate(sql).bindMap(params).execute());
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static Db register(Db db) {
        Runtime.getRuntime().addShutdownHook(new Thread(db::close));
        return db;
    }

    private static String sql(String file) {
        return SQL_CACHE.computeIfAbsent(file, path -> {
            try (InputStream in = Db.class.getResourceAsStream("/sql/" + path)) {
                if (in == null) {
                    throw new IllegalArgumentException("Нет файла src/test/resources/sql/" + path);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
