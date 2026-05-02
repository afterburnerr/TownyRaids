package gg.afterburner.townyRaids.cooldown;

import gg.afterburner.townyRaids.database.CooldownRepository;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CooldownStore {

    private final CooldownRepository repository;
    private final CooldownRepository.Kind kind;
    private final Logger logger;
    private final Map<UUID, Instant> cache = new ConcurrentHashMap<>();

    public CooldownStore(CooldownRepository repository, CooldownRepository.Kind kind, Logger logger) {
        this.repository = Objects.requireNonNull(repository);
        this.kind = Objects.requireNonNull(kind);
        this.logger = Objects.requireNonNull(logger);
    }

    public void loadAll() throws SQLException {
        cache.clear();
        Map<UUID, CooldownRepository.Entry> entries = repository.loadAll(kind);
        Instant now = Instant.now();
        entries.forEach((townId, entry) -> {
            if (entry.expiresAt().isAfter(now)) {
                cache.put(townId, entry.expiresAt());
            }
        });
    }

    public Optional<Instant> expiry(UUID townId) {
        Instant expiresAt = cache.get(townId);
        if (expiresAt == null) return Optional.empty();
        if (expiresAt.isBefore(Instant.now())) {
            remove(townId);
            return Optional.empty();
        }
        return Optional.of(expiresAt);
    }

    public boolean isActive(UUID townId) {
        return expiry(townId).isPresent();
    }

    public long remainingSeconds(UUID townId) {
        return expiry(townId)
                .map(expiry -> Math.max(0, Duration.between(Instant.now(), expiry).getSeconds()))
                .orElse(0L);
    }

    public void apply(UUID townId, Duration duration) {
        Instant expiresAt = Instant.now().plus(duration);
        cache.put(townId, expiresAt);
        try {
            repository.upsert(townId, kind, expiresAt);
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to persist " + kind + " for town " + townId, ex);
        }
    }

    public void remove(UUID townId) {
        cache.remove(townId);
        try {
            repository.delete(townId, kind);
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to delete " + kind + " for town " + townId, ex);
        }
    }

    public void purgeExpired() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        try {
            repository.purgeExpired(now);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to purge expired " + kind + " entries", ex);
        }
    }
}
