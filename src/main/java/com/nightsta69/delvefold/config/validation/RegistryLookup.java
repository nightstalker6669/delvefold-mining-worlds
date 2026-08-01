package com.nightsta69.delvefold.config.validation;

/**
 * Read-only block and block-tag existence boundary used by configuration validation.
 *
 * <p>Implementations must use the registry snapshot associated with the current server/reload context and must not
 * retain mutable registry internals beyond that context.
 */
public interface RegistryLookup {
    /**
     * Permissive lookup for offline structural tests and contexts with no live registry.
     *
     * <p>This lookup reports every block and block tag as present and therefore must not authorize publication of an
     * administrator-supplied profile on a running server.
     */
    RegistryLookup SKIP = new RegistryLookup() {
        @Override
        public boolean blockExists(String id) {
            return true;
        }

        @Override
        public boolean blockTagExists(String id) {
            return true;
        }
    };

    /**
     * Reports whether an exact block registry ID is installed.
     *
     * @param id normalized block registry ID
     * @return {@code true} when the current registry contains the block
     */
    boolean blockExists(String id);

    /**
     * Reports whether a block tag exists and has at least one installed member.
     *
     * @param id normalized block-tag ID without a leading {@code #}
     * @return {@code true} when the current registry resolves a nonempty tag
     */
    boolean blockTagExists(String id);
}
