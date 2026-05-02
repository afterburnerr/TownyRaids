package gg.afterburner.townyRaids.raid.lifecycle;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.config.ConfigManager;
import gg.afterburner.townyRaids.config.RaidSettings;
import gg.afterburner.townyRaids.cooldown.CooldownStore;
import gg.afterburner.townyRaids.raid.result.RaidDeclarationResult;
import gg.afterburner.townyRaids.util.TownyUtil;

import java.util.Objects;
import java.util.Optional;

public final class RaidDeclarationValidator {

    private final ConfigManager configManager;
    private final RaidRegistry registry;
    private final CooldownStore attackerCooldowns;
    private final CooldownStore defenderProtections;

    public RaidDeclarationValidator(ConfigManager configManager,
                                    RaidRegistry registry,
                                    CooldownStore attackerCooldowns,
                                    CooldownStore defenderProtections) {
        this.configManager = Objects.requireNonNull(configManager);
        this.registry = Objects.requireNonNull(registry);
        this.attackerCooldowns = Objects.requireNonNull(attackerCooldowns);
        this.defenderProtections = Objects.requireNonNull(defenderProtections);
    }

    public Optional<RaidDeclarationResult> validate(Town attacker, Town defender) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        RaidSettings settings = configManager.settings();

        if (attacker.getUUID().equals(defender.getUUID())) {
            return Optional.of(new RaidDeclarationResult.SameTown());
        }
        if (registry.anyInvolvingEither(attacker.getUUID(), defender.getUUID())) {
            return Optional.of(new RaidDeclarationResult.RaidAlreadyActive());
        }
        if (settings.requirements().requireDifferentNations() && TownyUtil.shareNation(attacker, defender)) {
            return Optional.of(new RaidDeclarationResult.SameNation());
        }

        int defenderResidents = defender.getNumResidents();
        if (defenderResidents < settings.requirements().defenderMinResidents()) {
            return Optional.of(new RaidDeclarationResult.DefenderTooSmall(
                    settings.requirements().defenderMinResidents(), defenderResidents));
        }
        int attackerResidents = attacker.getNumResidents();
        if (attackerResidents < settings.requirements().attackerMinResidents()) {
            return Optional.of(new RaidDeclarationResult.AttackerTooSmall(
                    settings.requirements().attackerMinResidents(), attackerResidents));
        }
        if (attackerCooldowns.isActive(attacker.getUUID())) {
            return Optional.of(new RaidDeclarationResult.AttackerOnCooldown(
                    attackerCooldowns.remainingSeconds(attacker.getUUID())));
        }
        if (defenderProtections.isActive(defender.getUUID())) {
            return Optional.of(new RaidDeclarationResult.DefenderProtected(
                    defenderProtections.remainingSeconds(defender.getUUID())));
        }
        return Optional.empty();
    }
}
