package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EcosystemProfileRegistryTest {
    private static final String EMPTY_PROFILE = """
            {"schema_version":2,"revision":0,"profile":"ignored","rules":[]}
            """;

    @Test
    void scriptRegistrationIsNamespacedValidatedAndOwnerScoped() {
        String id = "testpack:empty_test";
        EcosystemProfileRegistry.registerScriptJson("test_owner", id, EMPTY_PROFILE);
        try {
            assertTrue(EcosystemProfileRegistry.find(id) != null);
            assertFalse(EcosystemProfileRegistry.unregisterScriptProfile("different_owner", id));
            assertTrue(EcosystemProfileRegistry.unregisterScriptProfile("test_owner", id));
            assertTrue(EcosystemProfileRegistry.find(id) == null);
        } finally {
            EcosystemProfileRegistry.unregisterScriptProfile("test_owner", id);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> EcosystemProfileRegistry.registerScriptJson("test_owner", "not_namespaced", EMPTY_PROFILE));
        assertThrows(
                IllegalArgumentException.class,
                () -> EcosystemProfileRegistry.registerScriptJson("test_owner", "testpack:bad", "{}"));
    }
}
