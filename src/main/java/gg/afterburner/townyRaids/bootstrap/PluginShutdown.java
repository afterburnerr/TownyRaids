package gg.afterburner.townyRaids.bootstrap;

import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.logging.Level;

public final class PluginShutdown {

    private final Plugin plugin;

    public PluginShutdown(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public void stop(PluginRuntime runtime) {
        if (runtime == null) return;
        safely("raid manager shutdown", () -> runtime.raidManager().shutdown());
        safely("boss bar manager close", runtime.bossBarManager()::close);
        safely("database close", runtime.databaseManager()::close);
        plugin.getLogger().info("TownyRaids disabled cleanly.");
    }

    private void safely(String description, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed during: " + description, ex);
        }
    }
}
