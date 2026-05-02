package gg.afterburner.townyRaids.command;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {

    private final Map<UUID, Instant> lastUsed = new ConcurrentHashMap<>();

    public boolean tryAcquire(UUID owner, Duration cooldown) {
        if (cooldown.isZero() || cooldown.isNegative()) return true;
        Instant now = Instant.now();
        Instant previous = lastUsed.get(owner);
        if (previous != null && previous.plus(cooldown).isAfter(now)) {
            return false;
        }
        lastUsed.put(owner, now);
        return true;
    }
}
