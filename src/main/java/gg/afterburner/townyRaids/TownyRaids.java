package gg.afterburner.townyRaids;

import gg.afterburner.townyRaids.bootstrap.PluginBootstrap;
import gg.afterburner.townyRaids.bootstrap.PluginRuntime;
import gg.afterburner.townyRaids.bootstrap.PluginShutdown;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class TownyRaids extends JavaPlugin {

    private volatile PluginRuntime runtime;

    @Override
    public void onEnable() {
        try {
            runtime = new PluginBootstrap(this).start();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to start TownyRaids; disabling plugin.", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        new PluginShutdown(this).stop(runtime);
        runtime = null;
    }

    public PluginRuntime runtime() {
        return runtime;
    }
}
