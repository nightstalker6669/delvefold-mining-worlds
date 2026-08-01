package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.admin.AdminLocalizedComponents;
import net.minecraft.network.chat.Component;

/** Centralized translatable labels shared by the administration screens. */
public final class DelvefoldText {
    private DelvefoldText() {}

    static Component option(String group, String id) {
        return Component.translatable("option.delvefold." + group + '.' + id);
    }

    static Component toggle(boolean enabled, Component label) {
        return Component.translatable(enabled ? "screen.delvefold.toggle.on" : "screen.delvefold.toggle.off", label);
    }

    static Component choice(boolean selected, Component label) {
        return selected
                ? Component.translatable("screen.delvefold.choice.selected", label)
                : Component.translatable("screen.delvefold.choice.available", label);
    }

    /**
     * Resolves the bounded translation representation sent by the server, retaining legacy text.
     *
     * @param message encoded translation representation or legacy literal text
     * @return a client-localized component safe for rendering
     */
    public static Component serverMessage(String message) {
        return AdminLocalizedComponents.resolve(message);
    }
}
