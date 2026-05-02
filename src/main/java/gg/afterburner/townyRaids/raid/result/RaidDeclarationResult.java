package gg.afterburner.townyRaids.raid.result;

import gg.afterburner.townyRaids.raid.model.Raid;

public sealed interface RaidDeclarationResult {

    record Success(Raid raid) implements RaidDeclarationResult {}

    record AttackerOnCooldown(long remainingSeconds) implements RaidDeclarationResult {}

    record DefenderProtected(long remainingSeconds) implements RaidDeclarationResult {}

    record DefenderTooSmall(int required, int actual) implements RaidDeclarationResult {}

    record AttackerTooSmall(int required, int actual) implements RaidDeclarationResult {}

    record SameTown() implements RaidDeclarationResult {}

    record SameNation() implements RaidDeclarationResult {}

    record RaidAlreadyActive() implements RaidDeclarationResult {}

    record Failure(String reason) implements RaidDeclarationResult {}
}
