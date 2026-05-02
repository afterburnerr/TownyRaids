package gg.afterburner.townyRaids.bootstrap;

import gg.afterburner.townyRaids.config.ConfigManager;
import gg.afterburner.townyRaids.config.MessagesManager;
import gg.afterburner.townyRaids.cooldown.CooldownStore;
import gg.afterburner.townyRaids.database.DatabaseManager;
import gg.afterburner.townyRaids.hud.RaidBossBarManager;
import gg.afterburner.townyRaids.raid.RaidManager;
import gg.afterburner.townyRaids.tribute.TributeManager;

public record PluginRuntime(
        ConfigManager configManager,
        MessagesManager messagesManager,
        DatabaseManager databaseManager,
        CooldownStore attackerCooldowns,
        CooldownStore defenderProtections,
        RaidManager raidManager,
        TributeManager tributeManager,
        RaidBossBarManager bossBarManager
) {}
