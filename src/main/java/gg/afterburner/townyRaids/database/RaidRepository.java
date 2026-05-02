package gg.afterburner.townyRaids.database;

import gg.afterburner.townyRaids.raid.model.Raid;
import gg.afterburner.townyRaids.raid.model.RaidFlagsSnapshot;
import gg.afterburner.townyRaids.raid.model.RaidPhase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RaidRepository {

    private final DataSource dataSource;

    public RaidRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void upsert(Raid raid) throws SQLException {
        String sql = "INSERT INTO townyraids_raids(id, attacker_town, defender_town, phase, created_at, phase_end_at,"
                + " original_pvp, original_explosion, original_fire, original_mobs)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT(id) DO UPDATE SET"
                + "   attacker_town = excluded.attacker_town,"
                + "   defender_town = excluded.defender_town,"
                + "   phase = excluded.phase,"
                + "   created_at = excluded.created_at,"
                + "   phase_end_at = excluded.phase_end_at,"
                + "   original_pvp = excluded.original_pvp,"
                + "   original_explosion = excluded.original_explosion,"
                + "   original_fire = excluded.original_fire,"
                + "   original_mobs = excluded.original_mobs";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bindRaid(ps, raid);
            ps.executeUpdate();
        } catch (SQLException ex) {
            if (isUnknownOnConflict(ex)) {
                upsertMySqlStyle(raid);
                return;
            }
            throw ex;
        }
    }

    private void upsertMySqlStyle(Raid raid) throws SQLException {
        String sql = "INSERT INTO townyraids_raids(id, attacker_town, defender_town, phase, created_at, phase_end_at,"
                + " original_pvp, original_explosion, original_fire, original_mobs)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE"
                + "   attacker_town = VALUES(attacker_town),"
                + "   defender_town = VALUES(defender_town),"
                + "   phase = VALUES(phase),"
                + "   created_at = VALUES(created_at),"
                + "   phase_end_at = VALUES(phase_end_at),"
                + "   original_pvp = VALUES(original_pvp),"
                + "   original_explosion = VALUES(original_explosion),"
                + "   original_fire = VALUES(original_fire),"
                + "   original_mobs = VALUES(original_mobs)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bindRaid(ps, raid);
            ps.executeUpdate();
        }
    }

    public void delete(UUID raidId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM townyraids_raids WHERE id = ?")) {
            ps.setString(1, raidId.toString());
            ps.executeUpdate();
        }
    }

    public Optional<Raid> findById(UUID raidId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM townyraids_raids WHERE id = ?")) {
            ps.setString(1, raidId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRaid(rs)) : Optional.empty();
            }
        }
    }

    public List<Raid> findAllActive() throws SQLException {
        List<Raid> raids = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM townyraids_raids WHERE phase <> ?")) {
            ps.setString(1, RaidPhase.ENDED.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    raids.add(mapRaid(rs));
                }
            }
        }
        return raids;
    }

    private void bindRaid(PreparedStatement ps, Raid raid) throws SQLException {
        ps.setString(1, raid.id().toString());
        ps.setString(2, raid.attackerTownId().toString());
        ps.setString(3, raid.defenderTownId().toString());
        ps.setString(4, raid.phase().name());
        ps.setLong(5, raid.createdAt().toEpochMilli());
        ps.setLong(6, raid.phaseEndsAt().toEpochMilli());
        ps.setInt(7, raid.originalFlags().pvp() ? 1 : 0);
        ps.setInt(8, raid.originalFlags().explosion() ? 1 : 0);
        ps.setInt(9, raid.originalFlags().fire() ? 1 : 0);
        ps.setInt(10, raid.originalFlags().mobs() ? 1 : 0);
    }

    private Raid mapRaid(ResultSet rs) throws SQLException {
        RaidFlagsSnapshot flags = new RaidFlagsSnapshot(
                rs.getInt("original_pvp") == 1,
                rs.getInt("original_explosion") == 1,
                rs.getInt("original_fire") == 1,
                rs.getInt("original_mobs") == 1
        );
        return new Raid(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("attacker_town")),
                UUID.fromString(rs.getString("defender_town")),
                RaidPhase.valueOf(rs.getString("phase")),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("phase_end_at")),
                flags
        );
    }

    private boolean isUnknownOnConflict(SQLException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("on conflict");
    }
}
