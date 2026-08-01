package com.nightsta69.delvefold.client.gui;

/** Provides allocation-free declaration-order indexes for fixed GUI controls. */
final class GuiEnumOrder {
    private GuiEnumOrder() {}

    /**
     * Returns the declaration-order slot used to position or cycle an enum-backed control.
     *
     * <p>These screens intentionally present constants in their declaration order. Keeping the single ordinal access
     * here avoids allocating or searching a temporary collection at each layout or input event.
     */
    @SuppressWarnings("EnumOrdinal")
    static int index(Enum<?> value) {
        return value.ordinal();
    }
}
