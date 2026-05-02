package gg.afterburner.townyRaids.raid.result;

public sealed interface RaidCancellationResult {
    record Success() implements RaidCancellationResult {}
    record NoActiveRaid() implements RaidCancellationResult {}
    record NotCancellableNow() implements RaidCancellationResult {}
    record NotAttacker() implements RaidCancellationResult {}
}
