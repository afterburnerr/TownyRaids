package gg.afterburner.townyRaids.raid;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.config.ConfigManager;
import gg.afterburner.townyRaids.hud.RaidBossBarManager;
import gg.afterburner.townyRaids.hud.RaidTitleManager;
import gg.afterburner.townyRaids.raid.lifecycle.RaidDeclarationValidator;
import gg.afterburner.townyRaids.raid.lifecycle.RaidPhaseTransitionService;
import gg.afterburner.townyRaids.raid.lifecycle.RaidRegistry;
import gg.afterburner.townyRaids.raid.lifecycle.RaidStateRestorer;
import gg.afterburner.townyRaids.raid.model.Raid;
import gg.afterburner.townyRaids.raid.model.RaidFlagsSnapshot;
import gg.afterburner.townyRaids.raid.model.RaidPhase;
import gg.afterburner.townyRaids.raid.notification.RaidBroadcaster;
import gg.afterburner.townyRaids.raid.result.RaidCancellationResult;
import gg.afterburner.townyRaids.raid.result.RaidDeclarationResult;
import gg.afterburner.townyRaids.raid.schedule.RaidScheduler;
import gg.afterburner.townyRaids.raid.world.RaidFlagApplier;
import gg.afterburner.townyRaids.util.TownyUtil;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RaidManager {

    private final ConfigManager configManager;
    private final RaidRegistry registry;
    private final RaidDeclarationValidator validator;
    private final RaidPhaseTransitionService transitions;
    private final RaidStateRestorer restorer;
    private final RaidScheduler scheduler;
    private final RaidBroadcaster broadcaster;
    private final RaidFlagApplier flagApplier;
    private final RaidBossBarManager bossBarManager;
    private final RaidTitleManager titleManager;

    public RaidManager(ConfigManager configManager,
                       RaidRegistry registry,
                       RaidDeclarationValidator validator,
                       RaidPhaseTransitionService transitions,
                       RaidStateRestorer restorer,
                       RaidScheduler scheduler,
                       RaidBroadcaster broadcaster,
                       RaidFlagApplier flagApplier,
                       RaidBossBarManager bossBarManager,
                       RaidTitleManager titleManager) {
        this.configManager = Objects.requireNonNull(configManager);
        this.registry = Objects.requireNonNull(registry);
        this.validator = Objects.requireNonNull(validator);
        this.transitions = Objects.requireNonNull(transitions);
        this.restorer = Objects.requireNonNull(restorer);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.broadcaster = Objects.requireNonNull(broadcaster);
        this.flagApplier = Objects.requireNonNull(flagApplier);
        this.bossBarManager = Objects.requireNonNull(bossBarManager);
        this.titleManager = Objects.requireNonNull(titleManager);
    }

    public void restoreFromStorage() throws SQLException {
        restorer.restore();
    }

    public Collection<Raid> activeRaids() {
        return registry.all();
    }

    public Optional<Raid> byId(UUID raidId) {
        return registry.byId(raidId);
    }

    public Optional<Raid> activeRaidOfTown(UUID townId) {
        return registry.activeFor(townId);
    }

    public synchronized RaidDeclarationResult declare(Town attacker, Town defender) {
        Optional<RaidDeclarationResult> failure = validator.validate(attacker, defender);
        if (failure.isPresent()) return failure.get();

        RaidFlagsSnapshot capturedFlags = flagApplier.capture(defender);
        Instant now = Instant.now();
        Instant phaseEnd = now.plusSeconds(configManager.settings().timings().preparationSeconds());
        Raid raid = new Raid(UUID.randomUUID(), attacker.getUUID(), defender.getUUID(),
                RaidPhase.PENDING, now, phaseEnd, capturedFlags);

        registry.putAndPersist(raid);
        broadcaster.declared(attacker, defender);
        titleManager.sendDeclaration(attacker, defender);
        bossBarManager.attach(raid, attacker, defender);
        scheduler.schedulePhaseEnd(raid.id(), phaseEnd, () -> transitions.advancePhase(raid));
        return new RaidDeclarationResult.Success(raid);
    }

    public synchronized RaidCancellationResult cancelByAttacker(UUID attackerTownId) {
        Optional<Raid> maybe = registry.activeFor(attackerTownId)
                .filter(r -> r.attackerTownId().equals(attackerTownId));
        if (maybe.isEmpty()) return new RaidCancellationResult.NoActiveRaid();
        Raid raid = maybe.get();
        if (raid.phase() != RaidPhase.PENDING) return new RaidCancellationResult.NotCancellableNow();
        transitions.cancel(raid);
        return new RaidCancellationResult.Success();
    }

    public synchronized void cancelForTribute(Raid raid) {
        if (registry.byId(raid.id()).isEmpty()) return;
        transitions.cancel(raid);
    }

    public synchronized void forceEnd(Raid raid) {
        if (registry.byId(raid.id()).isEmpty()) return;
        if (raid.phase() == RaidPhase.BATTLE) {
            transitions.completeBattle(raid);
        } else {
            transitions.cancel(raid);
        }
    }

    public synchronized Optional<Raid> forcePhase(Raid raid, RaidPhase target) {
        if (registry.byId(raid.id()).isEmpty()) return Optional.empty();
        return switch (target) {
            case PENDING -> Optional.of(transitions.resetToPending(raid));
            case BATTLE -> Optional.of(raid.phase() == RaidPhase.PENDING
                    ? transitions.startBattle(raid)
                    : transitions.extendBattle(raid));
            case ENDED -> { forceEnd(raid); yield Optional.empty(); }
        };
    }

    public synchronized void shutdown() {
        if (configManager.settings().runtime().restoreFlagsOnShutdown()) {
            registry.all().stream()
                    .filter(r -> r.phase() == RaidPhase.BATTLE)
                    .forEach(r -> TownyUtil.townByUuid(r.defenderTownId())
                            .ifPresent(defender -> flagApplier.restore(defender, r.originalFlags())));
        }
        scheduler.cancelAll();
    }
}
