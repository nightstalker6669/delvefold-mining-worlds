package com.nightsta69.delvefold.config.validation;

import com.nightsta69.delvefold.admin.AdminLocalizedComponents;
import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** Localized display projection that keeps issue codes and JSON paths machine-stable. */
public final class ConfigIssueMessages {
    private static final Set<String> TECHNICAL_DETAILS = Set.of(
            "json.invalid", "profile.invalid", "initialize.failed", "ores.save_failed",
            "settings.save_failed", "profile.activate_failed");

    private ConfigIssueMessages() {
    }

    public static String encode(ConfigIssue issue) {
        ConfigIssue safe = java.util.Objects.requireNonNull(issue, "issue");
        String severity = AdminLocalizedMessage.encode(
                "message.delvefold.config_issue.severity."
                        + safe.severity().name().toLowerCase(java.util.Locale.ROOT));
        return AdminLocalizedMessage.encode("message.delvefold.config_issue.row",
                severity, safe.code(), safe.path(), detail(safe));
    }

    public static Component component(ConfigIssue issue) {
        return AdminLocalizedComponents.resolve(encode(issue));
    }

    private static String detail(ConfigIssue issue) {
        String code = issue.code() == null ? "" : issue.code();
        if (TECHNICAL_DETAILS.contains(code)) {
            return localized("message.delvefold.config_issue.detail.technical", issue.message());
        }
        String key;
        if (code.endsWith(".missing") || code.equals("document.missing") || code.equals("settings.missing")) {
            key = "message.delvefold.config_issue.detail.missing";
        } else if (code.contains("schema.unsupported")) {
            key = "message.delvefold.config_issue.detail.schema";
        } else if (code.endsWith(".negative")) {
            key = "message.delvefold.config_issue.detail.non_negative";
        } else if (code.equals("settings.generation_salt.uninitialized")) {
            key = "message.delvefold.config_issue.detail.generation_salt";
        } else if (code.endsWith(".too_long") || code.equals("settings.operation_id.too_long")
                || code.equals("settings.identity.name")) {
            key = "message.delvefold.config_issue.detail.length";
        } else if (code.contains("duplicate")) {
            key = "message.delvefold.config_issue.detail.duplicate";
        } else if (code.contains("too_many") || code.startsWith("budget.")) {
            key = code.startsWith("budget.")
                    ? "message.delvefold.config_issue.detail.budget"
                    : "message.delvefold.config_issue.detail.limit";
        } else if (code.contains("missing_block_tag") || code.contains("missing_replace_tag")) {
            key = "message.delvefold.config_issue.detail.missing_tag";
        } else if (code.contains("missing_block")) {
            key = issue.severity() == IssueSeverity.ERROR
                    ? "message.delvefold.config_issue.detail.missing_required_block"
                    : "message.delvefold.config_issue.detail.missing_optional_block";
        } else if (code.contains("invalid_block_tag") || code.contains("invalid_replace_tag")) {
            key = "message.delvefold.config_issue.detail.invalid_tag";
        } else if (code.contains("invalid_block")) {
            key = "message.delvefold.config_issue.detail.invalid_registry_id";
        } else if (code.contains("state")) {
            key = "message.delvefold.config_issue.detail.invalid_state";
        } else if (code.contains("biome") || code.contains("selector")) {
            key = "message.delvefold.config_issue.detail.invalid_selector";
        } else if (code.contains("height") || code.contains("peak") || code.contains("plateau")) {
            key = "message.delvefold.config_issue.detail.invalid_height";
        } else if (code.contains("province")) {
            key = code.contains("missing")
                    ? "message.delvefold.config_issue.detail.province_required"
                    : code.contains("unexpected")
                            ? "message.delvefold.config_issue.detail.province_unexpected"
                            : "message.delvefold.config_issue.detail.range";
        } else if (code.startsWith("fallback.last_good")) {
            key = "message.delvefold.config_issue.detail.fallback_last_good";
        } else if (code.startsWith("fallback.defaults")) {
            key = "message.delvefold.config_issue.detail.fallback_defaults";
        } else if (code.equals("transaction.pending")) {
            key = "message.delvefold.config_issue.detail.transaction_pending";
        } else if (code.equals("profile.active_mismatch")) {
            key = "message.delvefold.config_issue.detail.profile_mismatch";
        } else if (code.equals("revision.stale")) {
            key = "message.delvefold.config_issue.detail.revision_stale";
        } else if (code.contains("no_") || code.contains("terrain.missing")) {
            key = "message.delvefold.config_issue.detail.selection_required";
        } else if (code.contains("unexpected")) {
            key = "message.delvefold.config_issue.detail.unexpected";
        } else if (code.contains("invalid") || code.contains("interval") || code.contains("warning")
                || code.contains("time") || code.contains("retention")) {
            key = "message.delvefold.config_issue.detail.range";
        } else {
            key = "message.delvefold.config_issue.detail.invalid";
        }
        return localized(key);
    }

    private static String localized(String translationKey, Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }
}
