package com.nightsta69.delvefold.reset;

/**
 * Bounded result of confirming or cancelling a lifecycle operation.
 *
 * @param success whether the requested transition was accepted
 * @param message localized result envelope suitable for command or GUI presentation
 */
public record WorldOperationResult(boolean success, String message) {
    /**
     * Creates a successful lifecycle result.
     *
     * @param message localized success envelope
     * @return successful immutable result
     */
    public static WorldOperationResult success(String message) {
        return new WorldOperationResult(true, message);
    }

    /**
     * Creates a rejected or failed lifecycle result.
     *
     * @param message localized failure envelope
     * @return unsuccessful immutable result
     */
    public static WorldOperationResult failure(String message) {
        return new WorldOperationResult(false, message);
    }
}
