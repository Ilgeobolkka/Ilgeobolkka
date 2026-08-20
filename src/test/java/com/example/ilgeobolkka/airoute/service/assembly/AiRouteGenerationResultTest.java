package com.example.ilgeobolkka.airoute.service.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult.NoRouteReason;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiRouteGenerationResultTest {

    @Test
    void 깊이_부족은_최소_잉크가_없는_NO_ROUTE다() {
        AiRouteGenerationResult result = AiRouteGenerationResult.insufficientDepth();

        assertEquals(Status.NO_ROUTE, result.status());
        assertEquals(NoRouteReason.INSUFFICIENT_DEPTH, result.noRouteReason());
        assertNull(result.minimumRequiredInk());
    }

    @Test
    void 경로_조립_결과의_상태는_필수다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiRouteGenerationResult(null, List.of(), null, null));
    }

    @Test
    void 경로_조립_결과의_항목은_null일_수_없다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiRouteGenerationResult(Status.ROUTE, null, null, null));
    }
}
