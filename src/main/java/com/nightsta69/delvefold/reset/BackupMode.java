package com.nightsta69.delvefold.reset;

import com.google.gson.annotations.SerializedName;

/** Persistence policy applied after a delete or recreation operation has been staged safely. */
public enum BackupMode {
    /** Retains the staged mining-world snapshot as a restorable backup. */
    @SerializedName("keep_backup")
    KEEP_BACKUP,

    /** Permanently removes the staged snapshot only after lifecycle finalization succeeds. */
    @SerializedName("permanent")
    PERMANENT
}
