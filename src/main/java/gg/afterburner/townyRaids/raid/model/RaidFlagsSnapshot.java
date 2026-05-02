package gg.afterburner.townyRaids.raid.model;

public record RaidFlagsSnapshot(boolean pvp, boolean explosion, boolean fire, boolean mobs) {
    public static final RaidFlagsSnapshot DEFAULT_FALSE = new RaidFlagsSnapshot(false, false, false, false);
}
