package gg.afterburner.townyRaids.raid.lifecycle;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.database.RaidRepository;
import gg.afterburner.townyRaids.hud.RaidBossBarManager;
import gg.afterburner.townyRaids.raid.model.Raid;
import gg.afterburner.townyRaids.raid.schedule.RaidScheduler;
import gg.afterburner.townyRaids.util.TownyUtil;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

public final class RaidStateRestorer {

    private final Plugin plugin;
    private final RaidRepository repository;
    private final RaidRegistry registry;
    private final RaidPhaseTransitionService transitions;
    private final RaidScheduler scheduler;
    private final RaidBossBarManager bossBarManager;

    public RaidStateRestorer(Plugin plugin,
                             RaidRepository repository,
                             RaidRegistry registry,
                             RaidPhaseTransitionService transitions,
                             RaidScheduler scheduler,
                             RaidBossBarManager bossBarManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.repository = Objects.requireNonNull(repository);
        this.registry = Objects.requireNonNull(registry);
        this.transitions = Objects.requireNonNull(transitions);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.bossBarManager = Objects.requireNonNull(bossBarManager);
    }

    public void restore() throws SQLException {
        List<Raid> persisted = repository.findAllActive();
        registry.hydrateFromStorage(persisted);
        Instant now = Instant.now();
        for (Raid raid : persisted) {
            Optional<Town> attacker = TownyUtil.townByUuid(raid.attackerTownId());
            Optional<Town> defender = TownyUtil.townByUuid(raid.defenderTownId());
            if (attacker.isEmpty() || defender.isEmpty()) {
                dropRaid(raid);
                continue;
            }
            if (!raid.phaseEndsAt().isAfter(now)) {
                transitions.advancePhase(raid);
                continue;
            }
            bossBarManager.attach(raid, attacker.get(), defender.get());
            scheduler.schedulePhaseEnd(raid.id(), raid.phaseEndsAt(), () -> transitions.advancePhase(raid));
        }
    }

    private void dropRaid(Raid raid) {
        plugin.getLogger().warning("Dropping raid " + raid.id() + " because a referenced town no longer exists.");
        try {
            registry.removeAndDelete(raid.id());
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to drop raid " + raid.id(), ex);
        }
    }
}
