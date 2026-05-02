package gg.afterburner.townyRaids.bootstrap;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.AddonCommand;
import gg.afterburner.townyRaids.command.CommandContext;
import gg.afterburner.townyRaids.command.RaidCommand;
import gg.afterburner.townyRaids.command.RateLimiter;
import gg.afterburner.townyRaids.command.TributeCommand;
import gg.afterburner.townyRaids.command.admin.AdminRaidsCommand;
import gg.afterburner.townyRaids.config.ConfigLoadException;
import gg.afterburner.townyRaids.config.ConfigManager;
import gg.afterburner.townyRaids.config.MessagesManager;
import gg.afterburner.townyRaids.config.RaidSettings;
import gg.afterburner.townyRaids.cooldown.CooldownStore;
import gg.afterburner.townyRaids.database.CooldownRepository;
import gg.afterburner.townyRaids.database.DatabaseManager;
import gg.afterburner.townyRaids.database.RaidRepository;
import gg.afterburner.townyRaids.hud.RaidBossBarManager;
import gg.afterburner.townyRaids.hud.RaidTitleManager;
import gg.afterburner.townyRaids.listener.PlayerConnectionListener;
import gg.afterburner.townyRaids.listener.TownyTownLifecycleListener;
import gg.afterburner.townyRaids.raid.RaidManager;
import gg.afterburner.townyRaids.raid.lifecycle.RaidDeclarationValidator;
import gg.afterburner.townyRaids.raid.lifecycle.RaidPhaseTransitionService;
import gg.afterburner.townyRaids.raid.lifecycle.RaidRegistry;
import gg.afterburner.townyRaids.raid.lifecycle.RaidStateRestorer;
import gg.afterburner.townyRaids.raid.notification.RaidBroadcaster;
import gg.afterburner.townyRaids.raid.schedule.RaidScheduler;
import gg.afterburner.townyRaids.raid.world.RaidFlagApplier;
import gg.afterburner.townyRaids.tribute.TributeManager;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;

public final class PluginBootstrap {

    private final Plugin plugin;

    public PluginBootstrap(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public PluginRuntime start() throws ConfigLoadException, SQLException {
        plugin.getDataFolder().mkdirs();
        ConfigManager configManager = new ConfigManager(plugin);
        RaidSettings settings = configManager.reload();

        MessagesManager messagesManager = new MessagesManager(plugin);
        messagesManager.reload(settings.language());

        DatabaseManager databaseManager = new DatabaseManager(plugin, settings.storage());
        databaseManager.initialise();

        RaidRepository raidRepository = new RaidRepository(databaseManager.dataSource());
        CooldownRepository cooldownRepository = new CooldownRepository(databaseManager.dataSource());

        CooldownStore attackerCooldowns = new CooldownStore(cooldownRepository,
                CooldownRepository.Kind.ATTACKER_COOLDOWN, plugin.getLogger());
        CooldownStore defenderProtections = new CooldownStore(cooldownRepository,
                CooldownRepository.Kind.DEFENDER_PROTECTION, plugin.getLogger());
        attackerCooldowns.loadAll();
        defenderProtections.loadAll();

        RaidRegistry registry = new RaidRegistry(plugin, raidRepository);
        RaidFlagApplier flagApplier = new RaidFlagApplier();
        RaidScheduler scheduler = new RaidScheduler(plugin);
        RaidBroadcaster broadcaster = new RaidBroadcaster(messagesManager);
        RaidTitleManager titleManager = new RaidTitleManager(messagesManager);
        RaidBossBarManager bossBarManager = new RaidBossBarManager(plugin, configManager, messagesManager);
        bossBarManager.start();

        RaidPhaseTransitionService transitions = new RaidPhaseTransitionService(
                configManager, registry, flagApplier, scheduler, broadcaster,
                bossBarManager, titleManager, attackerCooldowns, defenderProtections);
        RaidDeclarationValidator validator = new RaidDeclarationValidator(
                configManager, registry, attackerCooldowns, defenderProtections);
        RaidStateRestorer restorer = new RaidStateRestorer(
                plugin, raidRepository, registry, transitions, scheduler, bossBarManager);

        RaidManager raidManager = new RaidManager(
                configManager, registry, validator, transitions, restorer,
                scheduler, broadcaster, flagApplier, bossBarManager, titleManager);
        raidManager.restoreFromStorage();

        TributeManager tributeManager = new TributeManager(plugin, configManager, messagesManager, raidManager);

        CommandContext commandContext = new CommandContext(configManager, messagesManager, raidManager,
                tributeManager, new RateLimiter());

        registerListeners(bossBarManager, raidManager, attackerCooldowns, defenderProtections);
        registerCommands(commandContext, configManager, messagesManager);

        plugin.getLogger().log(Level.INFO, "TownyRaids enabled (language: {0}, storage: {1})",
                new Object[]{messagesManager.activeLanguage(), settings.storage().getClass().getSimpleName()});

        return new PluginRuntime(configManager, messagesManager, databaseManager,
                attackerCooldowns, defenderProtections, raidManager, tributeManager, bossBarManager);
    }

    private void registerListeners(RaidBossBarManager bossBarManager,
                                   RaidManager raidManager,
                                   CooldownStore attackerCooldowns,
                                   CooldownStore defenderProtections) {
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(bossBarManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new TownyTownLifecycleListener(raidManager, attackerCooldowns, defenderProtections), plugin);
    }

    private void registerCommands(CommandContext context,
                                  ConfigManager configManager,
                                  MessagesManager messagesManager) {
        RaidCommand raidCommand = new RaidCommand(context);
        TributeCommand tributeCommand = new TributeCommand(context);
        AdminRaidsCommand adminCommand = new AdminRaidsCommand(context, () -> reloadConfigs(configManager, messagesManager));

        TownyCommandAddonAPI.addSubCommand(buildSubCommand(
                TownyCommandAddonAPI.CommandType.TOWN, "raid", raidCommand, raidCommand));
        TownyCommandAddonAPI.addSubCommand(buildSubCommand(
                TownyCommandAddonAPI.CommandType.TOWN, "tribute", tributeCommand, tributeCommand));
        TownyCommandAddonAPI.addSubCommand(buildSubCommand(
                TownyCommandAddonAPI.CommandType.TOWNYADMIN, "raids", adminCommand, adminCommand));
    }

    private AddonCommand buildSubCommand(TownyCommandAddonAPI.CommandType type,
                                         String name,
                                         org.bukkit.command.CommandExecutor executor,
                                         org.bukkit.command.TabCompleter tabCompleter) {
        AddonCommand command = new AddonCommand(type, name, executor);
        command.setTabCompleter(tabCompleter);
        return command;
    }

    private void reloadConfigs(ConfigManager configManager, MessagesManager messagesManager) {
        try {
            RaidSettings reloaded = configManager.reload();
            messagesManager.reload(reloaded.language());
        } catch (ConfigLoadException ex) {
            throw new RuntimeException(ex);
        }
    }
}
