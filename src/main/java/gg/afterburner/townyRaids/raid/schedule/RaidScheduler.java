package gg.afterburner.townyRaids.raid.schedule;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RaidScheduler {

    private final Plugin plugin;
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    public RaidScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void schedulePhaseEnd(UUID raidId, Instant endsAt, Runnable onEnd) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(onEnd, "onEnd");
        cancel(raidId);
        long delayTicks = computeDelayTicks(endsAt);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(raidId);
            onEnd.run();
        }, delayTicks);
        tasks.put(raidId, task);
    }

    public void cancel(UUID raidId) {
        BukkitTask existing = tasks.remove(raidId);
        if (existing != null) existing.cancel();
    }

    public void cancelAll() {
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private long computeDelayTicks(Instant endsAt) {
        long delayMillis = Math.max(0, endsAt.toEpochMilli() - System.currentTimeMillis());
        return Math.max(1L, delayMillis / 50L);
    }
}
