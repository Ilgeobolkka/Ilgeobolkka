package com.example.ilgeobolkka.airoute.service.assembly;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiRouteGenerationResultTest {

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
