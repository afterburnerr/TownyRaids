package gg.afterburner.townyRaids.tribute;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TributeRequest(
        UUID raidId,
        UUID attackerTownId,
        UUID defenderTownId,
        double amount,
        Instant expiresAt
) {
    public TributeRequest {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(attackerTownId, "attackerTownId");
        Objects.requireNonNull(defenderTownId, "defenderTownId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
