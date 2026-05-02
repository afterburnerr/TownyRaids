package gg.afterburner.townyRaids.util;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownyPermission;
import gg.afterburner.townyRaids.raid.model.RaidFlagsSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TownyUtil {

    private TownyUtil() {}

    public static Optional<Town> townByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        Town town = TownyAPI.getInstance().getTown(name.trim());
        return Optional.ofNullable(town);
    }

    public static Optional<Town> townByUuid(UUID uuid) {
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(TownyAPI.getInstance().getTown(uuid));
    }

    public static Optional<Resident> resident(Player player) {
        if (player == null) return Optional.empty();
        Resident resident = TownyAPI.getInstance().getResident(player.getUniqueId());
        return Optional.ofNullable(resident);
    }

    public static Optional<Town> townOf(Player player) {
        return resident(player).map(r -> {
            try {
                return r.hasTown() ? r.getTown() : null;
            } catch (NotRegisteredException ex) {
                return null;
            }
        });
    }

    public static boolean shareNation(Town a, Town b) {
        if (a == null || b == null) return false;
        try {
            if (!a.hasNation() || !b.hasNation()) return false;
            return a.getNation().getUUID().equals(b.getNation().getUUID());
        } catch (NotRegisteredException ex) {
            return false;
        }
    }

    public static boolean isMayorOrAssistant(Player player, Town town) {
        Optional<Resident> maybeResident = resident(player);
        if (maybeResident.isEmpty() || town == null) return false;
        Resident resident = maybeResident.get();
        try {
            if (!resident.hasTown() || !resident.getTown().getUUID().equals(town.getUUID())) {
                return false;
            }
        } catch (NotRegisteredException ex) {
            return false;
        }
        if (town.getMayor() != null && town.getMayor().getUUID().equals(resident.getUUID())) {
            return true;
        }
        return resident.getTownRanks().stream()
                .anyMatch(rank -> rank.equalsIgnoreCase("assistant") || rank.equalsIgnoreCase("co-mayor"));
    }

    public static List<Player> onlineResidents(Town town) {
        if (town == null) return List.of();
        return town.getResidents().stream()
                .map(Resident::getUUID)
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .collect(Collectors.toList());
    }

    public static RaidFlagsSnapshot captureFlags(Town town) {
        TownyPermission perm = town.getPermissions();
        return new RaidFlagsSnapshot(perm.pvp, perm.explosion, perm.fire, perm.mobs);
    }

    public static void applyFlags(Town town, RaidFlagsSnapshot snapshot, boolean save) {
        TownyPermission perm = town.getPermissions();
        perm.pvp = snapshot.pvp();
        perm.explosion = snapshot.explosion();
        perm.fire = snapshot.fire();
        perm.mobs = snapshot.mobs();
        if (save) town.save();
    }
}
