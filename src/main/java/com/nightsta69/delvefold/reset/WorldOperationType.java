package com.nightsta69.delvefold.reset;

import com.google.gson.annotations.SerializedName;

/** Administrator-visible lifecycle mutations supported for the active mining world. */
public enum WorldOperationType {
    /** Replaces managed dimension data and commits the requested recreation-locked settings after restart. */
    @SerializedName("recreate")
    RECREATE,

    /** Removes managed dimension data after restart without creating replacement terrain. */
    @SerializedName("delete")
    DELETE
}
