package gg.afterburner.townyRaids.raid.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Raid(
        UUID id,
        UUID attackerTownId,
        UUID defenderTownId,
        RaidPhase phase,
        Instant createdAt,
        Instant phaseEndsAt,
        RaidFlagsSnapshot originalFlags
) {
    public Raid {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(attackerTownId, "attackerTownId");
        Objects.requireNonNull(defenderTownId, "defenderTownId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(phaseEndsAt, "phaseEndsAt");
        Objects.requireNonNull(originalFlags, "originalFlags");
    }

    public Raid withPhase(RaidPhase newPhase, Instant newEnd) {
        return new Raid(id, attackerTownId, defenderTownId, newPhase, createdAt, newEnd, originalFlags);
    }

    public Raid withFlags(RaidFlagsSnapshot newFlags) {
        return new Raid(id, attackerTownId, defenderTownId, phase, createdAt, phaseEndsAt, newFlags);
    }

    public boolean involves(UUID townId) {
        return attackerTownId.equals(townId) || defenderTownId.equals(townId);
    }
}
