package com.nightsta69.delvefold.config.validation;

/** Registry checks stay behind this interface so validators are unit-testable. */
public interface RegistryLookup {
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

    boolean blockExists(String id);

    boolean blockTagExists(String id);
}
