package gg.afterburner.townyRaids.config;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigManager {

    private static final String CONFIG_FILE = "config.yml";

    private final Plugin plugin;
    private final AtomicReference<RaidSettings> settingsRef = new AtomicReference<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public RaidSettings settings() {
        RaidSettings current = settingsRef.get();
        if (current == null) {
            throw new IllegalStateException("ConfigManager has not been loaded yet");
        }
        return current;
    }

    public synchronized RaidSettings reload() throws ConfigLoadException {
        try {
            File target = new File(plugin.getDataFolder(), CONFIG_FILE);
            if (!target.exists()) {
                plugin.saveResource(CONFIG_FILE, false);
            }
            FileConfiguration merged = new YamlConfiguration();
            try (InputStream defaultsStream = plugin.getResource(CONFIG_FILE)) {
                if (defaultsStream != null) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(defaultsStream, StandardCharsets.UTF_8));
                    merged.setDefaults(defaults);
                }
            }
            FileConfiguration disk = YamlConfiguration.loadConfiguration(target);
            for (String key : disk.getKeys(true)) {
                merged.set(key, disk.get(key));
            }
            RaidSettings parsed = parse(merged);
            settingsRef.set(parsed);
            return parsed;
        } catch (IOException ex) {
            throw new ConfigLoadException("Failed to load " + CONFIG_FILE, ex);
        } catch (RuntimeException ex) {
            throw new ConfigLoadException("Failed to parse " + CONFIG_FILE + ": " + ex.getMessage(), ex);
        }
    }

    private RaidSettings parse(FileConfiguration raw) {
        String language = raw.getString("language", "en");

        RaidSettings.Timings timings = new RaidSettings.Timings(
                raw.getLong("timings.preparation-seconds", 300),
                raw.getLong("timings.battle-seconds", 900),
                raw.getLong("timings.attacker-cooldown-seconds", 86_400),
                raw.getLong("timings.defender-protection-seconds", 172_800),
                raw.getLong("timings.tribute-request-seconds", 120),
                raw.getLong("timings.confirmation-seconds", 30)
        );

        RaidSettings.Requirements requirements = new RaidSettings.Requirements(
                raw.getInt("requirements.defender-min-residents", 3),
                raw.getInt("requirements.attacker-min-residents", 3),
                raw.getBoolean("requirements.require-different-nations", true),
                raw.getBoolean("requirements.block-declaring-while-under-raid", true)
        );

        RaidSettings.BattleFlags flags = new RaidSettings.BattleFlags(
                raw.getBoolean("battle-flags.pvp", true),
                raw.getBoolean("battle-flags.explosion", true),
                raw.getBoolean("battle-flags.fire", true),
                raw.getBoolean("battle-flags.mobs", true)
        );

        RaidSettings.Tribute tribute = new RaidSettings.Tribute(
                raw.getBoolean("tribute.enabled", true),
                raw.getDouble("tribute.min-amount", 100.0),
                raw.getDouble("tribute.max-amount", 0.0)
        );

        RaidSettings.BossBarSettings prepBar = parseBossBar(raw.getConfigurationSection("bossbar.preparation"),
                BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        RaidSettings.BossBarSettings battleBar = parseBossBar(raw.getConfigurationSection("bossbar.battle"),
                BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);

        RaidSettings.Storage storage = parseStorage(raw);

        RaidSettings.Runtime runtime = new RaidSettings.Runtime(
                raw.getBoolean("runtime.restore-flags-on-shutdown", true),
                raw.getBoolean("runtime.lenient-restore", true),
                raw.getLong("runtime.command-rate-limit-seconds", 2)
        );

        return new RaidSettings(language, timings, requirements, flags, tribute, prepBar, battleBar, storage, runtime);
    }

    private RaidSettings.BossBarSettings parseBossBar(ConfigurationSection section,
                                                      BossBar.Color fallbackColor,
                                                      BossBar.Overlay fallbackOverlay) {
        if (section == null) {
            return new RaidSettings.BossBarSettings(fallbackColor, fallbackOverlay);
        }
        BossBar.Color color = parseEnum(BossBar.Color.class, section.getString("color"), fallbackColor);
        BossBar.Overlay overlay = parseEnum(BossBar.Overlay.class, section.getString("overlay"), fallbackOverlay);
        return new RaidSettings.BossBarSettings(color, overlay);
    }

    private RaidSettings.Storage parseStorage(FileConfiguration raw) {
        String type = raw.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "mysql" -> new RaidSettings.Storage.MySql(
                    raw.getString("storage.mysql.host", "localhost"),
                    raw.getInt("storage.mysql.port", 3306),
                    raw.getString("storage.mysql.database", "townyraids"),
                    raw.getString("storage.mysql.username", "townyraids"),
                    raw.getString("storage.mysql.password", ""),
                    raw.getBoolean("storage.mysql.use-ssl", false),
                    raw.getInt("storage.mysql.pool-size", 6)
            );
            default -> new RaidSettings.Storage.Sqlite(raw.getString("storage.sqlite-file", "storage.db"));
        };
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
