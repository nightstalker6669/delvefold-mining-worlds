package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.audit.AuditMutation;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Pure mutation planner kept separate from the Minecraft lifecycle facade for deterministic tests. */
final class ConfigAuditPlanner {
    private ConfigAuditPlanner() {}

    static List<AuditMutation> plan(@Nullable ConfigSnapshot before, @Nullable ConfigSnapshot saved, String actor) {
        if (before == null || saved == null) {
            return List.of();
        }
        List<AuditMutation> mutations = new ArrayList<>();
        if (!before.settings().equals(saved.settings())) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                    AuditMutation.ObjectType.SETTINGS,
                    "world_settings",
                    before.settings().revision(),
                    saved.settings().revision()));
        }
        if (!before.ores().equals(saved.ores())) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                    AuditMutation.ObjectType.PROFILE,
                    saved.ores().profile(),
                    before.ores().revision(),
                    saved.ores().revision()));
        }
        if (!before.settings().activeProfileId().equals(saved.settings().activeProfileId())) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.PROFILE_ACTIVATED,
                    AuditMutation.ObjectType.PROFILE,
                    saved.settings().activeProfileId(),
                    before.ores().revision(),
                    saved.ores().revision()));
        }
        if (before.settings().portal().routingMode()
                != saved.settings().portal().routingMode()) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.PORTAL_ROUTING_CHANGED,
                    AuditMutation.ObjectType.PORTAL,
                    "routing",
                    before.settings().revision(),
                    saved.settings().revision()));
        }
        if (!before.settings().portal().hub().equals(saved.settings().portal().hub())) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.HUB_PROTECTION_CHANGED,
                    AuditMutation.ObjectType.HUB,
                    "central_hub",
                    before.settings().revision(),
                    saved.settings().revision()));
        }
        return List.copyOf(mutations);
    }

    static List<AuditMutation> profileWrite(OreProfileCatalog.@Nullable ProfileWriteResult result, String actor) {
        if (result == null || !result.saved()) {
            return List.of();
        }
        var profile = result.profile();
        if (profile == null) {
            return List.of();
        }
        AuditMutation.Operation operation = result.previousRevision() < 0L
                ? AuditMutation.Operation.PROFILE_CREATED
                : AuditMutation.Operation.PROFILE_UPDATED;
        return List.of(new AuditMutation(
                actor,
                operation,
                AuditMutation.ObjectType.PROFILE,
                profile.profile(),
                result.previousRevision(),
                profile.revision()));
    }

    static List<AuditMutation> profileDelete(
            String id, OreProfileCatalog.@Nullable ProfileDeleteResult result, String actor) {
        if (result == null || !result.deleted()) {
            return List.of();
        }
        return List.of(new AuditMutation(
                actor,
                AuditMutation.Operation.PROFILE_DELETED,
                AuditMutation.ObjectType.PROFILE,
                id,
                result.previousRevision(),
                -1L));
    }
}
