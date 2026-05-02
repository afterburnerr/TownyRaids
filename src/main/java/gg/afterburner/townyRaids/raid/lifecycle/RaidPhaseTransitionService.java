package gg.afterburner.townyRaids.raid.lifecycle;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.config.ConfigManager;
import gg.afterburner.townyRaids.config.RaidSettings;
import gg.afterburner.townyRaids.cooldown.CooldownStore;
import gg.afterburner.townyRaids.hud.RaidBossBarManager;
import gg.afterburner.townyRaids.hud.RaidTitleManager;
import gg.afterburner.townyRaids.raid.model.Raid;
import gg.afterburner.townyRaids.raid.model.RaidFlagsSnapshot;
import gg.afterburner.townyRaids.raid.model.RaidPhase;
import gg.afterburner.townyRaids.raid.notification.RaidBroadcaster;
import gg.afterburner.townyRaids.raid.schedule.RaidScheduler;
import gg.afterburner.townyRaids.raid.world.RaidFlagApplier;
import gg.afterburner.townyRaids.util.TownyUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class RaidPhaseTransitionService {

    private final ConfigManager configManager;
    private final RaidRegistry registry;
    private final RaidFlagApplier flagApplier;
    private final RaidScheduler scheduler;
    private final RaidBroadcaster broadcaster;
    private final RaidBossBarManager bossBarManager;
    private final RaidTitleManager titleManager;
    private final CooldownStore attackerCooldowns;
    private final CooldownStore defenderProtections;

    public RaidPhaseTransitionService(ConfigManager configManager,
                                      RaidRegistry registry,
                                      RaidFlagApplier flagApplier,
                                      RaidScheduler scheduler,
                                      RaidBroadcaster broadcaster,
                                      RaidBossBarManager bossBarManager,
                                      RaidTitleManager titleManager,
                                      CooldownStore attackerCooldowns,
                                      CooldownStore defenderProtections) {
        this.configManager = Objects.requireNonNull(configManager);
        this.registry = Objects.requireNonNull(registry);
        this.flagApplier = Objects.requireNonNull(flagApplier);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.broadcaster = Objects.requireNonNull(broadcaster);
        this.bossBarManager = Objects.requireNonNull(bossBarManager);
        this.titleManager = Objects.requireNonNull(titleManager);
        this.attackerCooldowns = Objects.requireNonNull(attackerCooldowns);
        this.defenderProtections = Objects.requireNonNull(defenderProtections);
    }

    public void advancePhase(Raid raid) {
        Optional<Raid> current = registry.byId(raid.id());
        if (current.isEmpty()) return;
        switch (current.get().phase()) {
            case PENDING -> startBattle(current.get());
            case BATTLE -> completeBattle(current.get());
            case ENDED -> {}
        }
    }

    public Raid startBattle(Raid raid) {
        Optional<Town> defenderMaybe = TownyUtil.townByUuid(raid.defenderTownId());
        if (defenderMaybe.isEmpty()) {
            cancel(raid);
            return raid;
        }
        Town defender = defenderMaybe.get();
        RaidSettings settings = configManager.settings();
        RaidFlagsSnapshot snapshot = flagApplier.capture(defender);
        flagApplier.applyBattleFlags(defender, settings.battleFlags());
        Instant phaseEnd = Instant.now().plusSeconds(settings.timings().battleSeconds());
        Raid updated = raid.withFlags(snapshot).withPhase(RaidPhase.BATTLE, phaseEnd);
        registry.putAndPersist(updated);
        bossBarManager.updatePhase(updated);
        TownyUtil.townByUuid(raid.attackerTownId()).ifPresent(attacker ->
                broadcaster.battleStarted(attacker, defender));
        scheduler.schedulePhaseEnd(updated.id(), phaseEnd, () -> advancePhase(updated));
        return updated;
    }

    public void completeBattle(Raid raid) {
        RaidSettings settings = configManager.settings();
        Optional<Town> defenderMaybe = TownyUtil.townByUuid(raid.defenderTownId());
        Optional<Town> attackerMaybe = TownyUtil.townByUuid(raid.attackerTownId());
        defenderMaybe.ifPresent(defender -> flagApplier.restore(defender, raid.originalFlags()));
        attackerCooldowns.apply(raid.attackerTownId(),
                Duration.ofSeconds(settings.timings().attackerCooldownSeconds()));
        defenderProtections.apply(raid.defenderTownId(),
                Duration.ofSeconds(settings.timings().defenderProtectionSeconds()));
        scheduler.cancel(raid.id());
        bossBarManager.detach(raid.id());
        registry.removeAndDelete(raid.id());
        if (attackerMaybe.isPresent() && defenderMaybe.isPresent()) {
            broadcaster.battleEnded(attackerMaybe.get(), defenderMaybe.get());
            titleManager.sendBattleEnded(attackerMaybe.get(), defenderMaybe.get());
        }
    }

    public void cancel(Raid raid) {
        Optional<Town> defenderMaybe = TownyUtil.townByUuid(raid.defenderTownId());
        Optional<Town> attackerMaybe = TownyUtil.townByUuid(raid.attackerTownId());
        if (raid.phase() == RaidPhase.BATTLE) {
            defenderMaybe.ifPresent(defender -> flagApplier.restore(defender, raid.originalFlags()));
        }
        scheduler.cancel(raid.id());
        bossBarManager.detach(raid.id());
        registry.removeAndDelete(raid.id());
        if (attackerMaybe.isPresent() && defenderMaybe.isPresent()) {
            broadcaster.cancelled(attackerMaybe.get(), defenderMaybe.get());
            titleManager.sendCancelled(attackerMaybe.get(), defenderMaybe.get());
        }
    }

    public Raid resetToPending(Raid raid) {
        if (raid.phase() == RaidPhase.BATTLE) {
            TownyUtil.townByUuid(raid.defenderTownId())
                    .ifPresent(defender -> flagApplier.restore(defender, raid.originalFlags()));
        }
        Instant phaseEnd = Instant.now().plusSeconds(configManager.settings().timings().preparationSeconds());
        Raid updated = raid.withPhase(RaidPhase.PENDING, phaseEnd);
        registry.putAndPersist(updated);
        bossBarManager.updatePhase(updated);
        scheduler.schedulePhaseEnd(updated.id(), phaseEnd, () -> advancePhase(updated));
        return updated;
    }

    public Raid extendBattle(Raid raid) {
        Instant phaseEnd = Instant.now().plusSeconds(configManager.settings().timings().battleSeconds());
        Raid updated = raid.withPhase(RaidPhase.BATTLE, phaseEnd);
        registry.putAndPersist(updated);
        bossBarManager.updatePhase(updated);
        scheduler.schedulePhaseEnd(updated.id(), phaseEnd, () -> advancePhase(updated));
        return updated;
    }
}
