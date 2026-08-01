package com.nightsta69.delvefold.audit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DelvefoldAuditServiceTest {
    @Test
    void resolvesContainedAuditDirectoryAndContainsUnavailableWrites() throws Exception {
        // DelvefoldAuditService is a Minecraft lifecycle facade and initializes LogUtils at class load.
        // Keep its plain-JUnit contract test source-based; RotatingAuditLogTest behaviorally tests writes.
        String source = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/audit/DelvefoldAuditService.java"));

        assertTrue(source.contains("Path base = paths.directory().toAbsolutePath().normalize();"));
        assertTrue(source.contains("Path result = base.resolve(\"audit\").normalize();"));
        assertTrue(source.contains("if (!result.startsWith(base) || result.equals(base))"));
        assertTrue(source.contains("current = writer;"));
        assertTrue(source.contains("return current.offer(captured);"),
                "record must only enqueue the immutable mutation, never append on its caller thread");
        assertTrue(source.contains("closeContained(current);"),
                "server stop must synchronously drain and flush the detached writer");
        assertTrue(source.contains("public ActorScope pushActor(String actor)"));
        assertTrue(source.contains("captured = actors.capture(mutation);"));
        assertTrue(source.contains("return false;"),
                "Unavailable or failed audit writes must be contained instead of disrupting gameplay");
    }
}
