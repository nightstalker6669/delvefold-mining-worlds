package com.nightsta69.delvefold.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level boundary checks for the Minecraft-coupled Unified Ores administration service. */
class OreLibraryAdminServiceSourceContractTest {
    private static final Path SOURCE =
            Path.of("src/main/java/com/nightsta69/delvefold/admin/OreLibraryAdminService.java");

    @Test
    void batchAddRetainsAuthorityAtomicityAndFullCatalogIdReservation() throws Exception {
        String source = Files.readString(SOURCE);
        int method = source.indexOf("public ServiceResult addFamilies(");
        int revisionCheck = source.indexOf("snapshot.ores().revision() != request.expectedRevision()", method);
        int ownerBoundAccess = source.indexOf(
                "sessions.accessScan(player.getUUID(), request.catalogToken(), context.binding())", method);
        int unknownSelection = source.indexOf("message.delvefold.ore_library.selection_unknown", ownerBoundAccess);
        int planner = source.indexOf("OreImportPlanner.plan(", unknownSelection);
        int completeCatalog = source.indexOf("scan.discovery().groups()", planner);
        int mutation = source.indexOf(".updateOres(", completeCatalog);
        int savedCheck = source.indexOf("if (!write.saved())", mutation);
        int invalidation = source.indexOf("sessions.invalidatePlayer(player.getUUID())", savedCheck);

        assertTrue(method >= 0);
        assertTrue(revisionCheck > method, "Stale revisions must be rejected before session access or planning");
        assertTrue(ownerBoundAccess > revisionCheck, "Catalog access must remain bound to the requesting player");
        assertTrue(unknownSelection > ownerBoundAccess, "Unknown family IDs must be rejected before planning");
        assertTrue(
                planner > unknownSelection && completeCatalog > planner,
                "Batch planning must reserve IDs against the complete retained discovery catalog");
        assertTrue(
                mutation > completeCatalog, "The active profile may mutate only after catalog resolution and planning");
        assertTrue(invalidation > savedCheck, "A successful write must invalidate the consumed player's catalog");
        assertEquals(
                1,
                occurrences(source.substring(method), ".updateOres("),
                "One accepted batch must use exactly one atomic configuration write");
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
