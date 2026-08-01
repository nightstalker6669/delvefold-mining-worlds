package com.nightsta69.delvefold.config.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import org.junit.jupiter.api.Test;

class ConfigIssueMessagesTest {
    @Test
    void preservesMachineCodeAndPathWhileLocalizingTheDisplayDetail() {
        ConfigIssue issue = ConfigIssue.error("rule.no_targets", "$.rules[2].targets", "legacy prose");

        AdminLocalizedMessage.Decoded row = AdminLocalizedMessage.decode(
                ConfigIssueMessages.encode(issue)).orElseThrow();

        assertEquals("message.delvefold.config_issue.row", row.translationKey());
        assertEquals("rule.no_targets", row.arguments().get(1));
        assertEquals("$.rules[2].targets", row.arguments().get(2));
        assertEquals("message.delvefold.config_issue.severity.error",
                AdminLocalizedMessage.decode(row.arguments().get(0)).orElseThrow().translationKey());
        assertEquals("message.delvefold.config_issue.detail.selection_required",
                AdminLocalizedMessage.decode(row.arguments().get(3)).orElseThrow().translationKey());
    }

    @Test
    void retainsTechnicalDetailsOnlyForOperationalLoadFailures() {
        ConfigIssue issue = ConfigIssue.error("json.invalid", "$", "Unexpected token at line 8");

        AdminLocalizedMessage.Decoded row = AdminLocalizedMessage.decode(
                ConfigIssueMessages.encode(issue)).orElseThrow();
        AdminLocalizedMessage.Decoded detail = AdminLocalizedMessage.decode(
                row.arguments().get(3)).orElseThrow();

        assertEquals("message.delvefold.config_issue.detail.technical", detail.translationKey());
        assertEquals("Unexpected token at line 8", detail.arguments().getFirst());
    }
}
