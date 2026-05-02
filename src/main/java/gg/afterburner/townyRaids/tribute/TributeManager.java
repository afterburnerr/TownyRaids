package gg.afterburner.townyRaids.tribute;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.config.ConfigManager;
import gg.afterburner.townyRaids.config.MessagesManager;
import gg.afterburner.townyRaids.config.RaidSettings;
import gg.afterburner.townyRaids.raid.RaidManager;
import gg.afterburner.townyRaids.raid.model.Raid;
import gg.afterburner.townyRaids.raid.model.RaidPhase;
import gg.afterburner.townyRaids.raid.result.TributeResult;
import gg.afterburner.townyRaids.util.TownyUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TributeManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MessagesManager messages;
    private final RaidManager raidManager;

    private final Map<UUID, TributeRequest> requestsByRaid = new ConcurrentHashMap<>();

    public TributeManager(Plugin plugin,
                          ConfigManager configManager,
                          MessagesManager messages,
                          RaidManager raidManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.configManager = Objects.requireNonNull(configManager);
        this.messages = Objects.requireNonNull(messages);
        this.raidManager = Objects.requireNonNull(raidManager);
    }

    public synchronized TributeResult request(Town attacker, double amount) {
        RaidSettings settings = configManager.settings();
        if (!settings.tribute().enabled()) return new TributeResult.Disabled();
        Optional<Raid> maybeRaid = raidManager.activeRaidOfTown(attacker.getUUID());
        if (maybeRaid.isEmpty() || !maybeRaid.get().attackerTownId().equals(attacker.getUUID())) {
            return new TributeResult.NotAllowed("only-attacker-request");
        }
        Raid raid = maybeRaid.get();
        if (raid.phase() != RaidPhase.PENDING) return new TributeResult.NotInPendingPhase();
        if (requestsByRaid.containsKey(raid.id())) return new TributeResult.AlreadyPending();
        if (amount < settings.tribute().minAmount()) {
            return new TributeResult.AmountOutOfBounds(settings.tribute().minAmount(), settings.tribute().maxAmount(), true);
        }
        if (settings.tribute().hasMaxLimit() && amount > settings.tribute().maxAmount()) {
            return new TributeResult.AmountOutOfBounds(settings.tribute().minAmount(), settings.tribute().maxAmount(), false);
        }
        Instant expiresAt = Instant.now().plusSeconds(settings.timings().tributeRequestSeconds());
        TributeRequest request = new TributeRequest(raid.id(), raid.attackerTownId(), raid.defenderTownId(),
                amount, expiresAt);
        requestsByRaid.put(raid.id(), request);
        scheduleExpiration(request);
        notifyDefender(request);
        return new TributeResult.Requested(amount);
    }

    public synchronized TributeResult accept(Town defender) {
        Optional<Raid> maybeRaid = raidManager.activeRaidOfTown(defender.getUUID());
        if (maybeRaid.isEmpty()) return new TributeResult.NoActiveRaid();
        Raid raid = maybeRaid.get();
        if (!raid.defenderTownId().equals(defender.getUUID())) {
            return new TributeResult.NotAllowed("only-defender-answer");
        }
        TributeRequest request = requestsByRaid.get(raid.id());
        if (request == null) return new TributeResult.NoPendingRequest();
        if (request.isExpired(Instant.now())) {
            requestsByRaid.remove(raid.id());
            return new TributeResult.Expired();
        }
        Optional<Town> attackerMaybe = TownyUtil.townByUuid(raid.attackerTownId());
        if (attackerMaybe.isEmpty()) {
            requestsByRaid.remove(raid.id());
            return new TributeResult.NoActiveRaid();
        }
        Town attacker = attackerMaybe.get();
        if (!defender.getAccount().canPayFromHoldings(request.amount())) {
            return new TributeResult.InsufficientFunds();
        }
        if (!defender.getAccount().withdraw(request.amount(), "TownyRaids tribute payment")) {
            return new TributeResult.InsufficientFunds();
        }
        attacker.getAccount().deposit(request.amount(), "TownyRaids tribute receipt");
        requestsByRaid.remove(raid.id());
        raidManager.cancelForTribute(raid);
        return new TributeResult.Accepted(request.amount(), attacker.getUUID());
    }

    public synchronized TributeResult deny(Town defender) {
        Optional<Raid> maybeRaid = raidManager.activeRaidOfTown(defender.getUUID());
        if (maybeRaid.isEmpty()) return new TributeResult.NoActiveRaid();
        Raid raid = maybeRaid.get();
        if (!raid.defenderTownId().equals(defender.getUUID())) {
            return new TributeResult.NotAllowed("only-defender-answer");
        }
        TributeRequest request = requestsByRaid.remove(raid.id());
        if (request == null) return new TributeResult.NoPendingRequest();
        notifyAttackerDenied(request);
        return new TributeResult.Denied();
    }

    public void invalidate(UUID raidId) {
        requestsByRaid.remove(raidId);
    }

    private void scheduleExpiration(TributeRequest request) {
        long delayTicks = Math.max(1L,
                (request.expiresAt().toEpochMilli() - System.currentTimeMillis()) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TributeRequest active = requestsByRaid.get(request.raidId());
            if (active == null) return;
            if (!active.isExpired(Instant.now())) return;
            requestsByRaid.remove(request.raidId());
            notifyExpired(active);
        }, delayTicks);
    }

    private void notifyDefender(TributeRequest request) {
        Optional<Town> defenderTown = TownyUtil.townByUuid(request.defenderTownId());
        Optional<Town> attackerTown = TownyUtil.townByUuid(request.attackerTownId());
        if (defenderTown.isEmpty() || attackerTown.isEmpty()) return;
        TagResolver resolver = TagResolver.resolver(
                MessagesManager.placeholder("attacker", attackerTown.get().getName()),
                MessagesManager.placeholder("defender", defenderTown.get().getName()),
                MessagesManager.placeholder("amount", formatAmount(request.amount()))
        );
        TownyUtil.onlineResidents(defenderTown.get())
                .forEach(p -> p.sendMessage(messages.render("tribute.request-received", resolver)));
    }

    private void notifyAttackerDenied(TributeRequest request) {
        Optional<Town> attackerTown = TownyUtil.townByUuid(request.attackerTownId());
        if (attackerTown.isEmpty()) return;
        TownyUtil.onlineResidents(attackerTown.get())
                .forEach(p -> p.sendMessage(messages.render("tribute.denied")));
    }

    private void notifyExpired(TributeRequest request) {
        Optional<Town> attackerTown = TownyUtil.townByUuid(request.attackerTownId());
        Optional<Town> defenderTown = TownyUtil.townByUuid(request.defenderTownId());
        attackerTown.ifPresent(t -> TownyUtil.onlineResidents(t)
                .forEach(p -> p.sendMessage(messages.render("tribute.expired"))));
        defenderTown.ifPresent(t -> TownyUtil.onlineResidents(t)
                .forEach(p -> p.sendMessage(messages.render("tribute.expired"))));
    }

    public String formatAmount(double amount) {
        return String.format(java.util.Locale.ROOT, "%.2f", amount);
    }
}
