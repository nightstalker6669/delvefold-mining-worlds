package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("BackupRetentionRunState")
class BackupRetentionRunStateTest {
    @TempDir
    Path saveRoot;

    @Test
    void retainsOnlyBoundedLogicalDiagnosticsAndNeverTheSavePath() {
        BackupRetentionRunState state = BackupRetentionRunState.get();
        state.reset();
        try {
            List<BackupRetentionPlanner.Candidate> candidates = new ArrayList<>();
            for (int index = 0; index < 140; index++) {
                candidates.add(new BackupRetentionPlanner.Candidate(
                        "backup-" + String.format("%03d", index), index + 1L, 10, false));
            }
            BackupRetentionSettings settings = new BackupRetentionSettings(true, 2, 0, 0);
            BackupRetentionPlanner.Plan plan =
                    BackupRetentionPlanner.plan(settings, candidates, Set.of(), Instant.EPOCH);
            BackupRetentionService.Preview preview = new BackupRetentionService.Preview(
                    saveRoot, settings, Instant.EPOCH, Set.of("pending-logical-id"), plan);

            state.recordPreview(preview);
            BackupRetentionRunState.Snapshot previewSnapshot = state.current().orElseThrow();
            assertEquals(128, previewSnapshot.proposals().size());
            assertEquals(10, previewSnapshot.omittedProposalCount());
            assertEquals(List.of("pending-logical-id"), previewSnapshot.protectedBackupIds());
            assertFalse(previewSnapshot.toString().contains(saveRoot.toString()));

            List<String> pruned = new ArrayList<>();
            Map<String, String> failures = new LinkedHashMap<>();
            for (int index = 0; index < 140; index++) {
                pruned.add("pruned-" + index);
                failures.put("failed-" + index, "/private/path/must-not-be-retained");
            }
            state.recordResult(new BackupRetentionService.ApplyResult(pruned, failures));

            BackupRetentionRunState.Snapshot resultSnapshot = state.current().orElseThrow();
            assertTrue(resultSnapshot.applyRecorded());
            assertEquals(128, resultSnapshot.prunedBackupIds().size());
            assertEquals(12, resultSnapshot.omittedPrunedCount());
            assertEquals(128, resultSnapshot.failedBackupIds().size());
            assertEquals(12, resultSnapshot.omittedFailureCount());
            assertFalse(resultSnapshot.toString().contains("/private/path"));
        } finally {
            state.reset();
        }
        assertTrue(state.current().isEmpty());
    }
}
