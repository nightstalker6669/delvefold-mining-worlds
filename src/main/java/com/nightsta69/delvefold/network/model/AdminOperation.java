package com.nightsta69.delvefold.network.model;

public enum AdminOperation {
    REFRESH(2, ""),
    VALIDATE_CONFIG(2, ""),
    RELOAD_CONFIG(2, ""),
    DELETE_WORLD(4, "DELETE"),
    RECREATE_WORLD(4, "RECREATE"),
    CANCEL_PENDING_RESET(4, "CANCEL");

    private final int permissionLevel;
    private final String confirmation;

    AdminOperation(int permissionLevel, String confirmation) {
        this.permissionLevel = permissionLevel;
        this.confirmation = confirmation;
    }

    public int permissionLevel() {
        return permissionLevel;
    }

    public boolean confirmationMatches(String supplied) {
        String normalized = supplied == null ? "" : supplied.trim();
        if (this == RECREATE_WORLD) {
            return normalized.matches("(?i)RECREATE:(FLAT|CAVERN|WILD):(CLASSIC|EXPANSIVE)");
        }
        return confirmation.isEmpty() || confirmation.equalsIgnoreCase(normalized);
    }
}
