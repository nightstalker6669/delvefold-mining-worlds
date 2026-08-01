package com.nightsta69.delvefold.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.guide.GuideSnapshot;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DelvefoldApiGuideContractTest {
    @Test
    void guideAccessIsAnAdditiveApiV1OptionalSnapshot() throws Exception {
        assertEquals(1, DelvefoldApi.API_VERSION);

        var method = DelvefoldApi.class.getMethod("activeGuide");
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(0, method.getParameterCount());
        assertTrue(method.getGenericReturnType() instanceof ParameterizedType);
        ParameterizedType optionalType = (ParameterizedType) method.getGenericReturnType();
        assertEquals(GuideSnapshot.class, optionalType.getActualTypeArguments()[0]);
    }
}
