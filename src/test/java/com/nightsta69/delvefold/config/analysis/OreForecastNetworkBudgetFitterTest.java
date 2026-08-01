package com.nightsta69.delvefold.config.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueKind;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueSeverity;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceIssue;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceSummary;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleForecast;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OreForecastNetworkBudgetFitterTest {
    private static final int MINIMUM_ISSUE_BYTES = 55;
    private static final int MAXIMUM_ISSUE_BYTES = 439;

    @Test
    void looseBudgetPreservesEverySourceListAndItsOrder() {
        List<ReferenceIssue> firstIssues = List.of(issue(0, 70), issue(0, 71));
        List<ReferenceIssue> secondIssues = List.of(issue(1, 72), issue(1, 73));
        List<ReferenceIssue> details = List.of(issue(0, 74), issue(1, 75));

        OreProfileForecast fitted =
                fit(List.of(rule(0, firstIssues), rule(1, secondIssues)), summary(details), 2, 2, false);

        assertEquals(firstIssues, fitted.rules().get(0).issues());
        assertEquals(secondIssues, fitted.rules().get(1).issues());
        assertEquals(details, fitted.references().details());
        assertFalse(fitted.rules().get(0).truncated());
        assertFalse(fitted.rules().get(1).truncated());
        assertFalse(fitted.references().truncated());
        assertFalse(fitted.truncated());
    }

    @Test
    void exactFinalByteIsAcceptedAfterFairRuleReservationAndReferencesPrecedeRemainingRuleIssues() {
        List<RuleForecast> reservedRules = new ArrayList<>();
        List<RuleForecast> sourceRules = new ArrayList<>();
        for (int index = 0; index < OreProfileForecast.MAX_RULES_PER_PAGE; index++) {
            ReferenceIssue first = issue(index, MAXIMUM_ISSUE_BYTES);
            reservedRules.add(rule(index, List.of(first)));
            sourceRules.add(rule(index, List.of(first, issue(index, MINIMUM_ISSUE_BYTES))));
        }
        OreProfileForecast reserved = fit(
                reservedRules,
                summary(List.of()),
                OreProfileForecast.MAX_RULES_PER_PAGE,
                OreProfileForecast.MAX_RULES_PER_PAGE,
                false);
        int remaining = OreProfileForecast.MAX_ESTIMATED_NETWORK_BYTES - reserved.estimatedNetworkBytes();

        List<ReferenceIssue> acceptedDetails = new ArrayList<>();
        while (remaining > MAXIMUM_ISSUE_BYTES) {
            acceptedDetails.add(issue(0, MAXIMUM_ISSUE_BYTES));
            remaining -= MAXIMUM_ISSUE_BYTES;
        }
        if (remaining < MINIMUM_ISSUE_BYTES) {
            acceptedDetails.removeLast();
            remaining += MAXIMUM_ISSUE_BYTES;
            int adjustedSize = remaining - MINIMUM_ISSUE_BYTES;
            acceptedDetails.add(issue(0, adjustedSize));
            remaining = MINIMUM_ISSUE_BYTES;
        }
        acceptedDetails.add(issue(0, remaining));
        List<ReferenceIssue> sourceDetails = new ArrayList<>(acceptedDetails);
        sourceDetails.add(issue(0, MINIMUM_ISSUE_BYTES));

        OreProfileForecast fitted = fit(
                sourceRules,
                summary(sourceDetails),
                OreProfileForecast.MAX_RULES_PER_PAGE,
                OreProfileForecast.MAX_RULES_PER_PAGE,
                false);

        assertEquals(OreProfileForecast.MAX_ESTIMATED_NETWORK_BYTES, fitted.estimatedNetworkBytes());
        assertEquals(acceptedDetails, fitted.references().details());
        assertTrue(fitted.references().truncated());
        assertTrue(fitted.truncated());
        for (int index = 0; index < fitted.rules().size(); index++) {
            assertEquals(
                    List.of(sourceRules.get(index).issues().getFirst()),
                    fitted.rules().get(index).issues());
            assertTrue(fitted.rules().get(index).truncated());
        }
    }

    private static OreProfileForecast fit(
            List<RuleForecast> rules,
            ReferenceSummary references,
            int totalRuleCount,
            int pageSize,
            boolean truncated) {
        return OreForecastNetworkBudgetFitter.fit(
                "profile",
                0L,
                null,
                List.of(),
                List.of(),
                totalRuleCount,
                0,
                pageSize,
                1,
                rules,
                references,
                truncated);
    }

    private static RuleForecast rule(int index, List<ReferenceIssue> issues) {
        return new RuleForecast(
                index,
                "rule_" + index,
                true,
                false,
                RuleStatus.INVALID,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                1,
                0,
                issues.size(),
                0,
                issues,
                false);
    }

    private static ReferenceSummary summary(List<ReferenceIssue> details) {
        return new ReferenceSummary(0, 0, 0, 0, 0, details.size(), details, false);
    }

    private static ReferenceIssue issue(int ruleIndex, int estimatedBytes) {
        if (estimatedBytes < MINIMUM_ISSUE_BYTES || estimatedBytes > MAXIMUM_ISSUE_BYTES) {
            throw new IllegalArgumentException("Unsupported test issue size: " + estimatedBytes);
        }
        int characters = estimatedBytes - MINIMUM_ISSUE_BYTES;
        int ruleCharacters = Math.min(OreProfileForecast.MAX_IDENTIFIER_LENGTH, characters);
        characters -= ruleCharacters;
        int sourceCharacters = Math.min(OreProfileForecast.MAX_IDENTIFIER_LENGTH, characters);
        characters -= sourceCharacters;
        String ruleId = "r".repeat(ruleCharacters);
        String sourceId = "s".repeat(sourceCharacters);
        String referenceId = "x".repeat(characters);
        ReferenceIssue result = new ReferenceIssue(
                IssueKind.MISSING_BLOCK, IssueSeverity.WARNING, ruleIndex, 0, ruleId, sourceId, referenceId, 0);
        assertEquals(estimatedBytes, result.estimatedNetworkBytes());
        return result;
    }
}
