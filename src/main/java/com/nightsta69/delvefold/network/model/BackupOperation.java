package com.nightsta69.delvefold.network.model;

/** World-management operations available for server-owned backup catalog entries. */
public enum BackupOperation {
    /** Schedules restoration of a validated backup. */
    RESTORE,
    /** Marks a backup as retained by automatic cleanup. */
    PIN,
    /** Removes a backup's retained marker. */
    UNPIN,
    /** Starts deletion of a backup; completion may be reported asynchronously. */
    DELETE,
    /** Starts integrity verification, or legacy-manifest creation, with asynchronous completion reporting. */
    VERIFY,
    /** Cancels a pending backup restore. */
    CANCEL_RESTORE
}
