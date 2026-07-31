package com.nightsta69.delvefold.reset;

public record WorldOperationResult(boolean success, String message) {
    public static WorldOperationResult success(String message) {
        return new WorldOperationResult(true, message);
    }

    public static WorldOperationResult failure(String message) {
        return new WorldOperationResult(false, message);
    }
}
