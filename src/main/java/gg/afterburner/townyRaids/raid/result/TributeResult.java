package gg.afterburner.townyRaids.raid.result;

public sealed interface TributeResult {

    record Requested(double amount) implements TributeResult {}

    record Accepted(double amount, java.util.UUID attackerTownId) implements TributeResult {}

    record Denied() implements TributeResult {}

    record Expired() implements TributeResult {}

    record Disabled() implements TributeResult {}

    record NotInPendingPhase() implements TributeResult {}

    record AmountOutOfBounds(double min, double max, boolean tooLow) implements TributeResult {}

    record AlreadyPending() implements TributeResult {}

    record NoPendingRequest() implements TributeResult {}

    record InsufficientFunds() implements TributeResult {}

    record NoActiveRaid() implements TributeResult {}

    record NotAllowed(String reason) implements TributeResult {}
}
