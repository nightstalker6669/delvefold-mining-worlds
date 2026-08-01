package com.nightsta69.delvefold.network;

/**
 * Hard protocol bounds. Client-to-server payloads must remain comfortably below Minecraft's 32 KiB custom payload
 * ceiling.
 */
public final class ProtocolLimits {
    /** Maximum generic identifier length in UTF-16 code units. */
    public static final int ID_LENGTH = 128;
    /** Maximum short free-text field length in UTF-16 code units. */
    public static final int SHORT_TEXT_LENGTH = 256;
    /** Maximum localized-message representation length in UTF-16 code units. */
    public static final int MESSAGE_LENGTH = 1024;
    /** Maximum ore rules retained in one profile or reported total. */
    public static final int MAX_ORE_RULES = 512;
    /** Maximum ore rules encoded in one administration page. */
    public static final int MAX_ORE_RULES_PER_PAGE = 32;
    /** Default number of ore rules requested for one dashboard page. */
    public static final int GUI_ORE_RULES_PER_PAGE = 7;
    /** Maximum exact-block or block-tag variants in one ore rule. */
    public static final int MAX_VARIANTS = 16;
    /** Maximum block-state properties in one output variant. */
    public static final int MAX_STATE_PROPERTIES = 32;
    /** Maximum biome selectors in either one include or exclude list. */
    public static final int MAX_BIOME_SELECTORS_PER_LIST = 32;
    /** Maximum spawn bands in one ore rule. */
    public static final int MAX_BANDS = 16;
    /** Maximum terrain modes in one ore-rule applicability list. */
    public static final int MAX_TERRAIN_MODES = 3;
    /** Maximum diagnostic messages in one administration snapshot. */
    public static final int MAX_DIAGNOSTICS = 64;
    /** Maximum named profiles in one administration snapshot. */
    public static final int MAX_PROFILES = 128;
    /** Maximum imported or exported profile JSON size in UTF-8 bytes. */
    public static final int MAX_PROFILE_JSON_BYTES = 256 * 1024;
    /** Maximum profile export size in UTF-16 code units accepted for the client clipboard. */
    public static final int MAX_PROFILE_CLIPBOARD_CHARS = 24 * 1024;
    /** Maximum backup metadata rows in one administration snapshot. */
    public static final int MAX_BACKUPS = 64;
    /** Maximum opaque import-token length in UTF-16 code units. */
    public static final int MAX_IMPORT_TOKEN_LENGTH = 64;
    /** Maximum discovered ore groups in one server-side import session. */
    public static final int MAX_IMPORT_GROUPS = 256;
    /** Four maximal registry-ID groups, including all 16 candidates, remain below 24 KiB. */
    public static final int MAX_IMPORT_GROUPS_PER_PAGE = 4;

    /** Maximum group identifiers accepted in one import-preview selection. */
    public static final int MAX_IMPORT_SELECTED_GROUPS = 128;
    /** Two maximal diff entries leave room for workloads and bounded validation details. */
    public static final int MAX_IMPORT_DIFF_PER_PAGE = 2;
    /** Four worst-case UTF-8 validation details fit alongside a maximal two-entry diff page. */
    public static final int MAX_IMPORT_ISSUES = 4;

    /** Maximum validation or diff message length in UTF-16 code units. */
    public static final int MAX_IMPORT_MESSAGE_LENGTH = 512;

    /** Aggregate import scan or preview payload budget in bytes. */
    public static final int MAX_IMPORT_NETWORK_BYTES = 24 * 1024;

    private ProtocolLimits() {}
}
