package com.nightsta69.delvefold.network.model;

import org.jspecify.annotations.Nullable;

/**
 * General server-side administration operations, including their minimum permission and confirmation policy.
 *
 * <p>These values describe request requirements but never confer authority; handlers and service implementations must
 * independently authorize the acting player.
 */
public enum AdminOperation {
    /** Requests a fresh authoritative administration snapshot with configure permission. */
    REFRESH(2, ""),
    /** Validates the loaded configuration without persisting changes, with configure permission. */
    VALIDATE_CONFIG(2, ""),
    /** Reloads configuration from persistent storage with configure permission. */
    RELOAD_CONFIG(2, ""),
    /** Schedules deletion of the mining world with world-management permission and explicit confirmation. */
    DELETE_WORLD(4, "DELETE"),
    /** Schedules recreation using confirmed terrain parameters and world-management permission. */
    RECREATE_WORLD(4, "RECREATE"),
    /** Cancels a pending mining-world reset with world-management permission and explicit confirmation. */
    CANCEL_PENDING_RESET(4, "CANCEL");

    private final int permissionLevel;
    private final String confirmation;

    AdminOperation(int permissionLevel, String confirmation) {
        this.permissionLevel = permissionLevel;
        this.confirmation = confirmation;
    }

    /**
     * Returns the command-style permission level used to select configure or world-management authorization.
     *
     * @return minimum permission level for this operation
     */
    public int permissionLevel() {
        return permissionLevel;
    }

    /**
     * Checks normalized confirmation text against this operation's confirmation contract.
     *
     * <p>A {@code null} value is treated as empty text. Recreation requires its structured terrain, variant, and
     * geology suffix; operations without confirmation text accept any normalized value. This check is not
     * authorization.
     *
     * @param supplied client-supplied confirmation text, or {@code null}
     * @return whether the text satisfies this operation's confirmation contract
     */
    public boolean confirmationMatches(@Nullable String supplied) {
        String normalized = supplied == null ? "" : supplied.trim();
        if (this == RECREATE_WORLD) {
            return normalized.matches(
                    "(?i)RECREATE:(FLAT|CAVERN|WILD):(CLASSIC|EXPANSIVE):(CLASSIC|VOLCANIC|DRIPSTONE|LUSH|CRYSTAL)");
        }
        return confirmation.isEmpty() || confirmation.equalsIgnoreCase(normalized);
    }
}
