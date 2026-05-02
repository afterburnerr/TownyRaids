package gg.afterburner.townyRaids.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.afterburner.townyRaids.config.RaidSettings;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class DatabaseManager implements AutoCloseable {

    private final Plugin plugin;
    private final RaidSettings.Storage storage;
    private volatile HikariDataSource dataSource;
    private final Dialect dialect;

    public DatabaseManager(Plugin plugin, RaidSettings.Storage storage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.dialect = (storage instanceof RaidSettings.Storage.MySql) ? Dialect.MYSQL : Dialect.SQLITE;
    }

    public DataSource dataSource() {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            throw new IllegalStateException("DatabaseManager is not initialised");
        }
        return ds;
    }

    public Dialect dialect() {
        return dialect;
    }

    public synchronized void initialise() throws SQLException {
        if (dataSource != null) {
            return;
        }
        HikariConfig hikariConfig = switch (storage) {
            case RaidSettings.Storage.Sqlite sqlite -> sqliteConfig(sqlite);
            case RaidSettings.Storage.MySql mysql -> mysqlConfig(mysql);
        };
        this.dataSource = new HikariDataSource(hikariConfig);
        runMigrations();
    }

    private HikariConfig sqliteConfig(RaidSettings.Storage.Sqlite sqlite) {
        File dbFile = new File(plugin.getDataFolder(), sqlite.fileName());
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Failed to create data folder for SQLite DB");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("TownyRaids-SQLite");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTestQuery("SELECT 1");
        return config;
    }

    private HikariConfig mysqlConfig(RaidSettings.Storage.MySql mysql) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("TownyRaids-MySQL");
        config.setJdbcUrl("jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database()
                + "?useSSL=" + mysql.useSsl() + "&useUnicode=true&characterEncoding=UTF-8");
        config.setUsername(mysql.username());
        config.setPassword(mysql.password());
        config.setMaximumPoolSize(Math.max(1, mysql.poolSize()));
        config.setMinimumIdle(1);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return config;
    }

    private void runMigrations() throws SQLException {
        String raidsTable = dialect == Dialect.MYSQL ? MYSQL_RAIDS_TABLE : SQLITE_RAIDS_TABLE;
        String cooldownsTable = dialect == Dialect.MYSQL ? MYSQL_COOLDOWNS_TABLE : SQLITE_COOLDOWNS_TABLE;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(raidsTable);
            statement.execute(cooldownsTable);
        }
    }

    @Override
    public synchronized void close() {
        HikariDataSource ds = dataSource;
        if (ds != null) {
            ds.close();
            dataSource = null;
        }
    }

    public enum Dialect { SQLITE, MYSQL }

    private static final String SQLITE_RAIDS_TABLE = """
            CREATE TABLE IF NOT EXISTS townyraids_raids (
                id TEXT PRIMARY KEY,
                attacker_town TEXT NOT NULL,
                defender_town TEXT NOT NULL,
                phase TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                phase_end_at BIGINT NOT NULL,
                original_pvp INTEGER NOT NULL,
                original_explosion INTEGER NOT NULL,
                original_fire INTEGER NOT NULL,
                original_mobs INTEGER NOT NULL
            )""";

    private static final String SQLITE_COOLDOWNS_TABLE = """
            CREATE TABLE IF NOT EXISTS townyraids_cooldowns (
                town_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                expires_at BIGINT NOT NULL,
                PRIMARY KEY(town_id, kind)
            )""";

    private static final String MYSQL_RAIDS_TABLE = """
            CREATE TABLE IF NOT EXISTS townyraids_raids (
                id VARCHAR(36) PRIMARY KEY,
                attacker_town VARCHAR(36) NOT NULL,
                defender_town VARCHAR(36) NOT NULL,
                phase VARCHAR(16) NOT NULL,
                created_at BIGINT NOT NULL,
                phase_end_at BIGINT NOT NULL,
                original_pvp TINYINT(1) NOT NULL,
                original_explosion TINYINT(1) NOT NULL,
                original_fire TINYINT(1) NOT NULL,
                original_mobs TINYINT(1) NOT NULL,
                INDEX idx_raids_attacker(attacker_town),
                INDEX idx_raids_defender(defender_town)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""";

    private static final String MYSQL_COOLDOWNS_TABLE = """
            CREATE TABLE IF NOT EXISTS townyraids_cooldowns (
                town_id VARCHAR(36) NOT NULL,
                kind VARCHAR(16) NOT NULL,
                expires_at BIGINT NOT NULL,
                PRIMARY KEY(town_id, kind)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""";
}
