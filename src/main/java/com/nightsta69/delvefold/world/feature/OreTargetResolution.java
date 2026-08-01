package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.validation.IssueSeverity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Resolves editable ore targets with the exact ordering, state application, weighting, and first-wins deduplication
 * used by runtime generation.
 */
public final class OreTargetResolution {
    /** Maximum target/state/shadowing issues retained per resolved rule. */
    public static final int MAX_DIAGNOSTIC_ISSUES = 256;

    private OreTargetResolution() {}

    /**
     * Resolves one editable rule into runtime target groups and bounded diagnostics.
     *
     * <p>Targets are visited in declaration order and grouped by replacement tag in first-seen order. Output-tag
     * members are sorted by registry ID. Identical resulting block states use first-wins deduplication, exact targets
     * retain their configured weight, and members of one output tag divide that target's weight equally. This method
     * reads frozen built-in block registries but performs no world mutation.
     *
     * @param rule immutable ore rule to resolve
     * @return immutable groups, per-target results, and at most {@link #MAX_DIAGNOSTIC_ISSUES} issues
     */
    public static Result resolve(OreRule rule) {
        Objects.requireNonNull(rule, "rule");
        Map<TagKey<Block>, MutableTargetGroup> grouped = new LinkedHashMap<>();
        List<TargetResult> targets = new ArrayList<>(rule.targets().size());
        IssueCollector issues = new IssueCollector();

        for (int targetIndex = 0; targetIndex < rule.targets().size(); targetIndex++) {
            OreTarget target = rule.targets().get(targetIndex);
            ResourceLocation hostId = ResourceLocation.tryParse(stripHash(target.replaceTag()));
            if (hostId == null) {
                issues.add(issue(
                        rule,
                        targetIndex,
                        target,
                        IssueKind.INVALID_HOST_TAG,
                        IssueSeverity.ERROR,
                        target.replaceTag(),
                        0));
                targets.add(new TargetResult(
                        targetIndex, target.sourceId(), target.replaceTag(), 0, 0, 0, TargetStatus.INVALID_HOST));
                continue;
            }
            if (target.weight() < OreTarget.MIN_WEIGHT || target.weight() > OreTarget.MAX_WEIGHT) {
                issues.add(issue(
                        rule,
                        targetIndex,
                        target,
                        IssueKind.INVALID_WEIGHT,
                        IssueSeverity.ERROR,
                        Integer.toString(target.weight()),
                        0));
                targets.add(new TargetResult(
                        targetIndex, target.sourceId(), target.replaceTag(), 0, 0, 0, TargetStatus.INVALID_WEIGHT));
                continue;
            }

            TagKey<Block> replaceable = TagKey.create(Registries.BLOCK, hostId);
            boolean hostTagPresent = BuiltInRegistries.BLOCK
                    .getTag(replaceable)
                    .map(holders -> holders.size() > 0)
                    .orElse(false);
            if (!hostTagPresent) {
                issues.add(issue(
                        rule,
                        targetIndex,
                        target,
                        IssueKind.MISSING_HOST_TAG,
                        IssueSeverity.ERROR,
                        hostId.toString(),
                        0));
            }

            List<Map.Entry<ResourceLocation, Block>> outputs = outputBlocks(target);
            if (outputs.isEmpty()) {
                IssueKind kind = target.tagDriven() ? IssueKind.MISSING_OUTPUT_TAG : IssueKind.MISSING_BLOCK;
                IssueSeverity severity = rule.required() ? IssueSeverity.ERROR : IssueSeverity.WARNING;
                String reference = target.tagDriven() ? target.blockTag() : target.block();
                issues.add(issue(rule, targetIndex, target, kind, severity, reference, 0));
                targets.add(new TargetResult(
                        targetIndex, target.sourceId(), target.replaceTag(), 0, 0, 0, TargetStatus.MISSING_OUTPUT));
                continue;
            }

            MutableTargetGroup group =
                    grouped.computeIfAbsent(replaceable, ignored -> new MutableTargetGroup(hostTagPresent));
            double memberWeight = target.tagDriven() ? (double) target.weight() / outputs.size() : target.weight();
            int acceptedOutputs = 0;
            int shadowedOutputs = 0;
            for (Map.Entry<ResourceLocation, Block> output : outputs) {
                StateResult state = applyProperties(output.getValue().defaultBlockState(), target.state());
                for (StateIssue stateIssue : state.issues()) {
                    issues.add(issue(
                            rule,
                            targetIndex,
                            target,
                            stateIssue.kind(),
                            IssueSeverity.WARNING,
                            output.getKey().toString(),
                            0));
                }
                if (group.add(
                        output.getKey(), state.state(), memberWeight, target.weight() == OreTarget.DEFAULT_WEIGHT)) {
                    acceptedOutputs++;
                } else {
                    shadowedOutputs++;
                    issues.add(issue(
                            rule,
                            targetIndex,
                            target,
                            IssueKind.SHADOWED_OUTPUT,
                            IssueSeverity.WARNING,
                            output.getKey().toString(),
                            1));
                }
            }
            TargetStatus status;
            if (acceptedOutputs == 0) {
                status = TargetStatus.SHADOWED;
                issues.add(issue(
                        rule,
                        targetIndex,
                        target,
                        IssueKind.SHADOWED_TARGET,
                        IssueSeverity.WARNING,
                        hostId.toString(),
                        shadowedOutputs));
            } else if (shadowedOutputs > 0) {
                status = TargetStatus.PARTIALLY_SHADOWED;
            } else {
                status = TargetStatus.EFFECTIVE;
            }
            targets.add(new TargetResult(
                    targetIndex,
                    target.sourceId(),
                    target.replaceTag(),
                    outputs.size(),
                    acceptedOutputs,
                    shadowedOutputs,
                    status));
        }

        List<TargetGroup> groups = grouped.entrySet().stream()
                .filter(entry -> !entry.getValue().candidates.isEmpty())
                .map(entry -> entry.getValue().compile(entry.getKey()))
                .toList();
        return new Result(groups, targets, issues.issues(), issues.truncated());
    }

    private static Issue issue(
            OreRule rule,
            int targetIndex,
            OreTarget target,
            IssueKind kind,
            IssueSeverity severity,
            String referenceId,
            int affectedOutputs) {
        return new Issue(
                kind,
                severity,
                targetIndex,
                rule.id(),
                target.sourceId(),
                referenceId == null ? "" : referenceId,
                Math.max(0, affectedOutputs));
    }

    private static List<Map.Entry<ResourceLocation, Block>> outputBlocks(OreTarget target) {
        if (!target.tagDriven()) {
            ResourceLocation id = ResourceLocation.tryParse(target.block());
            Block block =
                    id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null) {
                return List.of();
            }
            return List.of(Map.entry(id, block));
        }
        ResourceLocation id = ResourceLocation.tryParse(target.blockTag());
        if (id == null) {
            return List.of();
        }
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
        return BuiltInRegistries.BLOCK.getTag(tag).stream()
                .flatMap(holders -> holders.stream())
                .map(holder -> Map.entry(BuiltInRegistries.BLOCK.getKey(holder.value()), holder.value()))
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }

    private static StateResult applyProperties(BlockState state, Map<String, String> properties) {
        BlockState result = state;
        List<StateIssue> issues = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = result.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                issues.add(new StateIssue(IssueKind.INVALID_STATE_PROPERTY));
                continue;
            }
            if (entry.getValue() == null) {
                issues.add(new StateIssue(IssueKind.INVALID_STATE_VALUE));
                continue;
            }
            Optional<BlockState> updated = setProperty(result, property, entry.getValue());
            if (updated.isEmpty()) {
                issues.add(new StateIssue(IssueKind.INVALID_STATE_VALUE));
            } else {
                result = updated.get();
            }
        }
        return new StateResult(result, issues);
    }

    private static <T extends Comparable<T>> Optional<BlockState> setProperty(
            BlockState state, Property<T> property, String serializedValue) {
        return property.getValue(serializedValue).map(value -> state.setValue(property, value));
    }

    private static String stripHash(String value) {
        return value != null && value.startsWith("#") ? value.substring(1) : value;
    }

    /**
     * Complete bounded resolution of one ore rule.
     *
     * @param groups effective host-tag groups in deterministic first-seen order
     * @param targets result for each configured target in declaration order
     * @param issues bounded validation and effectiveness issues
     * @param issuesTruncated whether additional issues exceeded the diagnostics cap
     */
    public record Result(
            List<TargetGroup> groups, List<TargetResult> targets, List<Issue> issues, boolean issuesTruncated) {
        /**
         * Defensively copies all result collections.
         *
         * @param groups effective host-tag groups
         * @param targets per-target results
         * @param issues bounded diagnostics
         * @param issuesTruncated issue-cap indicator
         */
        public Result {
            groups = groups == null ? List.of() : List.copyOf(groups);
            targets = targets == null ? List.of() : List.copyOf(targets);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        /**
         * Counts accepted outputs whose replacement tag exists and is nonempty.
         *
         * @return effective output count, saturated at {@link Integer#MAX_VALUE}
         */
        public int effectiveOutputCount() {
            long count = groups.stream()
                    .filter(TargetGroup::hostTagPresent)
                    .mapToLong(group -> group.outputs().size())
                    .sum();
            return (int) Math.min(Integer.MAX_VALUE, count);
        }

        /**
         * Counts outputs discarded by first-wins state deduplication.
         *
         * @return shadowed output count, saturated at {@link Integer#MAX_VALUE}
         */
        public int shadowedOutputCount() {
            long count =
                    targets.stream().mapToLong(TargetResult::shadowedOutputs).sum();
            return (int) Math.min(Integer.MAX_VALUE, count);
        }
    }

    /**
     * Runtime candidates sharing one replacement block tag.
     *
     * @param replaceable host block tag
     * @param hostTagPresent whether the host tag currently contains at least one registered block
     * @param outputs unique output states in deterministic first-wins order
     * @param legacyUniform whether every accepted source retained the compatibility default weight
     * @param totalWeight finite positive sum of output selection weights
     */
    public record TargetGroup(
            TagKey<Block> replaceable,
            boolean hostTagPresent,
            List<Output> outputs,
            boolean legacyUniform,
            double totalWeight) {
        /**
         * Copies outputs and validates weighted-selection invariants.
         *
         * @param replaceable host block tag
         * @param hostTagPresent host-tag availability
         * @param outputs unique ordered outputs
         * @param legacyUniform compatibility uniform-selection flag
         * @param totalWeight finite positive total selection weight
         * @throws NullPointerException when replaceable is null
         * @throws IllegalArgumentException when outputs are empty or totalWeight is not finite and positive
         */
        public TargetGroup {
            Objects.requireNonNull(replaceable, "replaceable");
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            if (outputs.isEmpty() || !(totalWeight > 0.0D) || !Double.isFinite(totalWeight)) {
                throw new IllegalArgumentException("Resolved ore target group requires finite positive output weight");
            }
        }
    }

    /**
     * One unique output state in a replacement group.
     *
     * @param blockId registered output block ID
     * @param state output state after configured properties are applied
     * @param selectionWeight positive exact or divided tag-member weight
     * @param cumulativeWeight inclusive upper boundary used by binary weighted selection
     */
    public record Output(ResourceLocation blockId, BlockState state, double selectionWeight, double cumulativeWeight) {
        /**
         * Validates required registry and state values.
         *
         * @param blockId registered output block ID
         * @param state resolved output state
         * @param selectionWeight output selection weight
         * @param cumulativeWeight cumulative group weight
         * @throws NullPointerException when blockId or state is null
         */
        public Output {
            Objects.requireNonNull(blockId, "blockId");
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * Effectiveness summary for one configured target.
     *
     * @param targetIndex zero-based declaration index
     * @param sourceId stable source identifier used by diagnostics
     * @param replaceTag configured replacement-tag text
     * @param resolvedOutputs number of registry outputs before state deduplication
     * @param acceptedOutputs number of unique states retained
     * @param shadowedOutputs number of duplicate states discarded
     * @param status overall target status
     */
    public record TargetResult(
            int targetIndex,
            String sourceId,
            String replaceTag,
            int resolvedOutputs,
            int acceptedOutputs,
            int shadowedOutputs,
            TargetStatus status) {
        /**
         * Normalizes diagnostic strings and validates status.
         *
         * @param targetIndex zero-based target index
         * @param sourceId source identifier
         * @param replaceTag replacement-tag text
         * @param resolvedOutputs pre-deduplication output count
         * @param acceptedOutputs retained output count
         * @param shadowedOutputs discarded duplicate count
         * @param status target status
         * @throws NullPointerException when status is null
         */
        public TargetResult {
            sourceId = sourceId == null ? "" : sourceId;
            replaceTag = replaceTag == null ? "" : replaceTag;
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * One bounded, redaction-safe ore target diagnostic.
     *
     * @param kind stable machine-readable issue kind
     * @param severity validation severity
     * @param targetIndex zero-based configured target index
     * @param ruleId owning rule ID
     * @param sourceId configured target source ID
     * @param referenceId affected registry ID, tag, property, or value
     * @param affectedOutputs nonnegative number of outputs affected
     */
    public record Issue(
            IssueKind kind,
            IssueSeverity severity,
            int targetIndex,
            String ruleId,
            String sourceId,
            String referenceId,
            int affectedOutputs) {
        /**
         * Normalizes text/count values and validates required classifications.
         *
         * @param kind issue kind
         * @param severity issue severity
         * @param targetIndex zero-based target index
         * @param ruleId owning rule ID
         * @param sourceId target source ID
         * @param referenceId affected reference
         * @param affectedOutputs affected output count
         * @throws NullPointerException when kind or severity is null
         */
        public Issue {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(severity, "severity");
            ruleId = ruleId == null ? "" : ruleId;
            sourceId = sourceId == null ? "" : sourceId;
            referenceId = referenceId == null ? "" : referenceId;
            affectedOutputs = Math.max(0, affectedOutputs);
        }
    }

    /** Stable diagnostic categories shared by forecast and runtime resolution. */
    public enum IssueKind {
        /** Replacement-tag text is not a valid resource location. */
        INVALID_HOST_TAG,
        /** Replacement tag is valid but absent or empty in the block registry. */
        MISSING_HOST_TAG,
        /** Configured target weight is outside its supported bounds. */
        INVALID_WEIGHT,
        /** Exact output block is absent from the registry. */
        MISSING_BLOCK,
        /** Output block tag is absent or contains no blocks. */
        MISSING_OUTPUT_TAG,
        /** Configured state property does not exist on an output block. */
        INVALID_STATE_PROPERTY,
        /** Configured property value cannot be parsed for an output block. */
        INVALID_STATE_VALUE,
        /** One output state duplicates an earlier state in the same host group. */
        SHADOWED_OUTPUT,
        /** Every output from the target was shadowed by earlier targets. */
        SHADOWED_TARGET
    }

    /** Overall resolution status for one configured target. */
    public enum TargetStatus {
        /** Every resolved output state remains selectable. */
        EFFECTIVE,
        /** At least one output remains selectable and at least one was shadowed. */
        PARTIALLY_SHADOWED,
        /** All resolved outputs duplicate earlier states. */
        SHADOWED,
        /** Exact block or output tag resolved no registered blocks. */
        MISSING_OUTPUT,
        /** Replacement-tag text is invalid. */
        INVALID_HOST,
        /** Configured target weight is outside supported bounds. */
        INVALID_WEIGHT
    }

    private record StateResult(BlockState state, List<StateIssue> issues) {
        private StateResult {
            issues = List.copyOf(issues);
        }
    }

    private record StateIssue(IssueKind kind) {}

    private static final class MutableTargetGroup {
        private final LinkedHashMap<BlockState, Candidate> candidates = new LinkedHashMap<>();
        private final boolean hostTagPresent;
        private boolean legacyUniform = true;

        private MutableTargetGroup(boolean hostTagPresent) {
            this.hostTagPresent = hostTagPresent;
        }

        boolean add(ResourceLocation blockId, BlockState state, double selectionWeight, boolean defaultWeight) {
            if (candidates.containsKey(state)) {
                return false;
            }
            candidates.put(state, new Candidate(blockId, selectionWeight));
            legacyUniform &= defaultWeight;
            return true;
        }

        TargetGroup compile(TagKey<Block> replaceable) {
            List<Output> outputs = new ArrayList<>(candidates.size());
            double cumulative = 0.0D;
            for (Map.Entry<BlockState, Candidate> candidate : candidates.entrySet()) {
                cumulative += candidate.getValue().selectionWeight();
                outputs.add(new Output(
                        candidate.getValue().blockId(),
                        candidate.getKey(),
                        candidate.getValue().selectionWeight(),
                        cumulative));
            }
            return new TargetGroup(replaceable, hostTagPresent, outputs, legacyUniform, cumulative);
        }
    }

    private record Candidate(ResourceLocation blockId, double selectionWeight) {}

    private static final class IssueCollector {
        private final List<Issue> issues = new ArrayList<>();
        private boolean truncated;

        private void add(Issue issue) {
            if (issues.size() < MAX_DIAGNOSTIC_ISSUES) {
                issues.add(issue);
            } else {
                truncated = true;
            }
        }

        private List<Issue> issues() {
            return List.copyOf(issues);
        }

        private boolean truncated() {
            return truncated;
        }
    }
}
