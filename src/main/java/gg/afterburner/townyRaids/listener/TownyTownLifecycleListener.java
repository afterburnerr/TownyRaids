package gg.afterburner.townyRaids.listener;

import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import gg.afterburner.townyRaids.cooldown.CooldownStore;
import gg.afterburner.townyRaids.raid.RaidManager;
import gg.afterburner.townyRaids.raid.model.Raid;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TownyTownLifecycleListener implements Listener {

    private final RaidManager raidManager;
    private final CooldownStore attackerCooldowns;
    private final CooldownStore defenderProtections;

    public TownyTownLifecycleListener(RaidManager raidManager,
                                      CooldownStore attackerCooldowns,
                                      CooldownStore defenderProtections) {
        this.raidManager = Objects.requireNonNull(raidManager);
        this.attackerCooldowns = Objects.requireNonNull(attackerCooldowns);
        this.defenderProtections = Objects.requireNonNull(defenderProtections);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTownDeleted(DeleteTownEvent event) {
        UUID townId = event.getTownUUID();
        if (townId == null) return;
        Optional<Raid> raid = raidManager.activeRaidOfTown(townId);
        raid.ifPresent(raidManager::forceEnd);
        attackerCooldowns.remove(townId);
        defenderProtections.remove(townId);
    }
}
