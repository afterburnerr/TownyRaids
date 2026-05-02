package gg.afterburner.townyRaids.config;

import net.kyori.adventure.bossbar.BossBar;

public record RaidSettings(
        String language,
        Timings timings,
        Requirements requirements,
        BattleFlags battleFlags,
        Tribute tribute,
        BossBarSettings preparationBar,
        BossBarSettings battleBar,
        Storage storage,
        Runtime runtime
) {
    public record Timings(
            long preparationSeconds,
            long battleSeconds,
            long attackerCooldownSeconds,
            long defenderProtectionSeconds,
            long tributeRequestSeconds,
            long confirmationSeconds
    ) {}

    public record Requirements(
            int defenderMinResidents,
            int attackerMinResidents,
            boolean requireDifferentNations,
            boolean blockDeclaringWhileUnderRaid
    ) {}

    public record BattleFlags(boolean pvp, boolean explosion, boolean fire, boolean mobs) {}

    public record Tribute(boolean enabled, double minAmount, double maxAmount) {
        public boolean hasMaxLimit() {
            return maxAmount > 0;
        }
    }

    public record BossBarSettings(BossBar.Color color, BossBar.Overlay overlay) {}

    public sealed interface Storage permits Storage.Sqlite, Storage.MySql {
        record Sqlite(String fileName) implements Storage {}
        record MySql(
                String host,
                int port,
                String database,
                String username,
                String password,
                boolean useSsl,
                int poolSize
        ) implements Storage {}
    }

    public record Runtime(
            boolean restoreFlagsOnShutdown,
            boolean lenientRestore,
            long commandRateLimitSeconds
    ) {}
}
