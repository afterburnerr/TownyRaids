package gg.afterburner.townyRaids.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CooldownRepository {

    public enum Kind {
        ATTACKER_COOLDOWN,
        DEFENDER_PROTECTION;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind from(String code) {
            return Kind.valueOf(code.toUpperCase(Locale.ROOT));
        }
    }

    public record Entry(UUID townId, Kind kind, Instant expiresAt) {}

    private final DataSource dataSource;

    public CooldownRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void upsert(UUID townId, Kind kind, Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO townyraids_cooldowns(town_id, kind, expires_at) VALUES (?, ?, ?)"
                + " ON CONFLICT(town_id, kind) DO UPDATE SET expires_at = excluded.expires_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, townId.toString());
            ps.setString(2, kind.code());
            ps.setLong(3, expiresAt.toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException ex) {
            if (isUnknownOnConflict(ex)) {
                upsertMySql(townId, kind, expiresAt);
                return;
            }
            throw ex;
        }
    }

    private void upsertMySql(UUID townId, Kind kind, Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO townyraids_cooldowns(town_id, kind, expires_at) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, townId.toString());
            ps.setString(2, kind.code());
            ps.setLong(3, expiresAt.toEpochMilli());
            ps.executeUpdate();
        }
    }

    public void delete(UUID townId, Kind kind) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM townyraids_cooldowns WHERE town_id = ? AND kind = ?")) {
            ps.setString(1, townId.toString());
            ps.setString(2, kind.code());
            ps.executeUpdate();
        }
    }

    public void deleteAllFor(UUID townId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM townyraids_cooldowns WHERE town_id = ?")) {
            ps.setString(1, townId.toString());
            ps.executeUpdate();
        }
    }

    public Map<UUID, Entry> loadAll(Kind kind) throws SQLException {
        Map<UUID, Entry> entries = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT town_id, kind, expires_at FROM townyraids_cooldowns WHERE kind = ?")) {
            ps.setString(1, kind.code());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID townId = UUID.fromString(rs.getString("town_id"));
                    Instant expiresAt = Instant.ofEpochMilli(rs.getLong("expires_at"));
                    entries.put(townId, new Entry(townId, kind, expiresAt));
                }
            }
        }
        return entries;
    }

    public int purgeExpired(Instant now) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM townyraids_cooldowns WHERE expires_at <= ?")) {
            ps.setLong(1, now.toEpochMilli());
            return ps.executeUpdate();
        }
    }

    private boolean isUnknownOnConflict(SQLException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("on conflict");
    }
}
