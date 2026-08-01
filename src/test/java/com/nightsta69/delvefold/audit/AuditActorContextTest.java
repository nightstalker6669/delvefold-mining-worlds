package com.nightsta69.delvefold.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AuditActorContextTest {
    @Test
    // Both resources intentionally exist only for their LIFO close behavior, which is the scope
    // restoration contract exercised after each try block.
    @SuppressWarnings("try")
    void nestedScopesCaptureTheInnermostActorBeforeAsyncHandoff() {
        AuditActorContext context = new AuditActorContext();
        AuditMutation original = mutation("server");

        assertEquals("server", context.currentActorOrServer());
        try (DelvefoldAuditService.ActorScope outer = context.push("Alice")) {
            assertEquals("Alice", context.currentActorOrServer());
            assertEquals("Alice", context.capture(original).actor());
            try (DelvefoldAuditService.ActorScope inner = context.push("Bob")) {
                assertEquals("Bob", context.currentActorOrServer());
                assertEquals("Bob", context.capture(original).actor());
            }
            assertEquals("Alice", context.capture(original).actor());
        }
        assertEquals("server", context.currentActorOrServer());
        assertEquals("server", context.capture(original).actor());
    }

    @Test
    void invalidActorsAndOutOfOrderClosureCannotCorruptTheScopeStack() {
        AuditActorContext context = new AuditActorContext();
        assertThrows(IllegalArgumentException.class, () -> context.push("actor\nsecret"));

        DelvefoldAuditService.ActorScope outer = context.push("Alice");
        DelvefoldAuditService.ActorScope inner = context.push("Bob");
        assertThrows(IllegalStateException.class, outer::close);
        assertEquals("Bob", context.currentActorOrServer());
        inner.close();
        outer.close();
        outer.close();
        assertEquals("server", context.currentActorOrServer());
    }

    private static AuditMutation mutation(String actor) {
        return new AuditMutation(
                actor,
                AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                AuditMutation.ObjectType.SETTINGS,
                "world_settings",
                0L,
                1L);
    }
}
