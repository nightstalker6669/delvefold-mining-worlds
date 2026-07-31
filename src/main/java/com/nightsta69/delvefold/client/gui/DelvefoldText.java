package com.nightsta69.delvefold.client.gui;

import net.minecraft.network.chat.Component;

/** Centralized translatable labels shared by the administration screens. */
final class DelvefoldText {
    private DelvefoldText() {
    }

    static Component option(String group, String id) {
        return Component.translatable("option.delvefold." + group + '.' + id);
    }

    static Component toggle(boolean enabled, Component label) {
        return Component.translatable(enabled ? "screen.delvefold.toggle.on" : "screen.delvefold.toggle.off", label);
    }
}
