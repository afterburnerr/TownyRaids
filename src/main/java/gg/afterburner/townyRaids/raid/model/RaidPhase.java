package gg.afterburner.townyRaids.raid.model;

import java.util.Locale;
import java.util.Optional;

public enum RaidPhase {
    PENDING,
    BATTLE,
    ENDED;

    public static Optional<RaidPhase> fromInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(RaidPhase.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
