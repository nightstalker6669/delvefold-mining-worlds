package com.nightsta69.delvefold.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DelvefoldCommandsTest {
    @Test
    void registersDelvefoldAsTheOnlyCommandRoot() {
        assertEquals(List.of("delvefold"), DelvefoldCommandNames.REGISTERED_ROOTS);
    }
}
