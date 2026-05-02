package gg.afterburner.townyRaids.listener;

import gg.afterburner.townyRaids.hud.RaidBossBarManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class PlayerConnectionListener implements Listener {

    private final RaidBossBarManager bossBarManager;

    public PlayerConnectionListener(RaidBossBarManager bossBarManager) {
        this.bossBarManager = Objects.requireNonNull(bossBarManager);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        bossBarManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bossBarManager.onPlayerQuit(event.getPlayer());
    }
}
