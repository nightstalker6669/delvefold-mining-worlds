package com.nightsta69.delvefold.network.model;

/** Server-assigned disposition of an administrative request or asynchronous completion. */
public enum ActionStatus {
    /** The operation completed successfully or was accepted for explicitly reported asynchronous work. */
    ACCEPTED,
    /** The server declined the request because its input, confirmation, or current policy was not acceptable. */
    REJECTED,
    /** The submitted optimistic-concurrency revision did not match authoritative state. */
    STALE,
    /** The server could not complete the request because of an operational failure. */
    ERROR
}
