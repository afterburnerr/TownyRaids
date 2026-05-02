package gg.afterburner.townyRaids.command;

import gg.afterburner.townyRaids.config.ConfigManager;
import gg.afterburner.townyRaids.config.MessagesManager;
import gg.afterburner.townyRaids.raid.RaidManager;
import gg.afterburner.townyRaids.tribute.TributeManager;

public record CommandContext(
        ConfigManager configManager,
        MessagesManager messages,
        RaidManager raidManager,
        TributeManager tributeManager,
        RateLimiter rateLimiter
) {}
