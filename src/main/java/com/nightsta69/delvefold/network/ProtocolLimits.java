package com.nightsta69.delvefold.network;

/**
 * Hard protocol bounds. Client-to-server payloads must remain comfortably
 * below Minecraft's 32 KiB custom payload ceiling.
 */
public final class ProtocolLimits {
    public static final int ID_LENGTH = 128;
    public static final int SHORT_TEXT_LENGTH = 256;
    public static final int MESSAGE_LENGTH = 1024;
    public static final int MAX_ORE_RULES = 512;
    public static final int MAX_ORE_RULES_PER_PAGE = 32;
    public static final int GUI_ORE_RULES_PER_PAGE = 7;
    public static final int MAX_VARIANTS = 16;
    public static final int MAX_STATE_PROPERTIES = 32;
    public static final int MAX_BIOME_SELECTORS_PER_LIST = 32;
    public static final int MAX_BANDS = 16;
    public static final int MAX_TERRAIN_MODES = 3;
    public static final int MAX_DIAGNOSTICS = 64;
    public static final int MAX_PROFILES = 128;
    public static final int MAX_PROFILE_JSON_BYTES = 256 * 1024;
    public static final int MAX_PROFILE_CLIPBOARD_CHARS = 24 * 1024;
    public static final int MAX_BACKUPS = 64;

    private ProtocolLimits() {
    }
}
