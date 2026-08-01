package com.nightsta69.delvefold.network.model;

/** Configure-permission operations on server-authoritative ore profiles. */
public enum ProfileOperation {
    /** Activates an existing profile using optimistic concurrency. */
    SELECT,
    /** Saves the current ore configuration as a named profile. */
    SAVE_CURRENT,
    /** Copies an existing profile to a new identifier. */
    DUPLICATE,
    /** Deletes an eligible profile. */
    DELETE,
    /** Parses and validates bounded clipboard JSON as a profile. */
    IMPORT_CLIPBOARD
}
