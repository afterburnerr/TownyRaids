package gg.afterburner.townyRaids.raid.world;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.config.RaidSettings;
import gg.afterburner.townyRaids.raid.model.RaidFlagsSnapshot;
import gg.afterburner.townyRaids.util.TownyUtil;

import java.util.Objects;

public final class RaidFlagApplier {

    public RaidFlagsSnapshot capture(Town town) {
        Objects.requireNonNull(town, "town");
        return TownyUtil.captureFlags(town);
    }

    public void applyBattleFlags(Town defender, RaidSettings.BattleFlags flags) {
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(flags, "flags");
        TownyUtil.applyFlags(defender, new RaidFlagsSnapshot(
                flags.pvp(), flags.explosion(), flags.fire(), flags.mobs()
        ), true);
    }

    public void restore(Town defender, RaidFlagsSnapshot snapshot) {
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(snapshot, "snapshot");
        TownyUtil.applyFlags(defender, snapshot, true);
    }
}
