package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.model.OreRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure planning step for an ore-rule save. The configuration service applies the returned plan while holding its
 * mutation lock.
 */
final class OreRuleSavePlanner {
    private OreRuleSavePlanner() {}

    static Plan plan(List<OreRule> existingRules, OreRule replacement, boolean createOnly) {
        Objects.requireNonNull(existingRules, "existingRules");
        Objects.requireNonNull(replacement, "replacement");
        List<OreRule> rules = new ArrayList<>(existingRules);
        int existingIndex = findRuleIndex(rules, replacement.id());
        if (createOnly && existingIndex >= 0) {
            return new Plan(true, rules);
        }
        if (existingIndex >= 0) {
            rules.set(existingIndex, replacement);
        } else {
            rules.add(replacement);
        }
        return new Plan(false, rules);
    }

    private static int findRuleIndex(List<OreRule> rules, String ruleId) {
        for (int index = 0; index < rules.size(); index++) {
            if (rules.get(index).id().equals(ruleId)) {
                return index;
            }
        }
        return -1;
    }

    record Plan(boolean collision, List<OreRule> rules) {
        Plan {
            rules = List.copyOf(rules);
        }
    }
}
