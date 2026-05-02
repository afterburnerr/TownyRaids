package gg.afterburner.townyRaids.raid.lifecycle;

import gg.afterburner.townyRaids.database.RaidRepository;
import gg.afterburner.townyRaids.raid.model.Raid;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class RaidRegistry {

    private final Plugin plugin;
    private final RaidRepository repository;
    private final Map<UUID, Raid> raids = new ConcurrentHashMap<>();

    public RaidRegistry(Plugin plugin, RaidRepository repository) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Collection<Raid> all() {
        return Collections.unmodifiableCollection(raids.values());
    }

    public Optional<Raid> byId(UUID raidId) {
        return Optional.ofNullable(raids.get(raidId));
    }

    public Optional<Raid> activeFor(UUID townId) {
        return raids.values().stream().filter(r -> r.involves(townId)).findFirst();
    }

    public boolean anyInvolvingEither(UUID townA, UUID townB) {
        return raids.values().stream().anyMatch(r -> r.involves(townA) || r.involves(townB));
    }

    public void hydrateFromStorage(Collection<Raid> loaded) {
        raids.clear();
        loaded.forEach(r -> raids.put(r.id(), r));
    }

    public void putAndPersist(Raid raid) {
        raids.put(raid.id(), raid);
        persistQuietly(raid);
    }

    public void removeAndDelete(UUID raidId) {
        Raid removed = raids.remove(raidId);
        if (removed == null) return;
        deleteQuietly(removed);
    }

    public void removeInMemory(UUID raidId) {
        raids.remove(raidId);
    }

    private void persistQuietly(Raid raid) {
        try {
            repository.upsert(raid);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to persist raid " + raid.id(), ex);
        }
    }

    private void deleteQuietly(Raid raid) {
        try {
            repository.delete(raid.id());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete raid " + raid.id(), ex);
        }
    }
}
