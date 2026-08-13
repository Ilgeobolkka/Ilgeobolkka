package com.example.ilgeobolkka.airoute.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiRouteAdditionalCostPolicyTest {

    @Test
    void 소장과_활성_대여와_미권한을_같은_우선순위로_판정한다() {
        assertAll(
                () -> assertEquals(
                        AiRouteAdditionalCostStatus.OWNED,
                        AiRouteAdditionalCostPolicy.status(true, true)),
                () -> assertEquals(
                        AiRouteAdditionalCostStatus.ACTIVE_RENTAL,
                        AiRouteAdditionalCostPolicy.status(false, true)),
                () -> assertEquals(
                        AiRouteAdditionalCostStatus.ONE_INK,
                        AiRouteAdditionalCostPolicy.status(false, false)));
    }

    @Test
    void 페이지_묶음에서는_ONE_INK만_추가_잉크로_센다() {
        int additionalInk = AiRouteAdditionalCostPolicy.additionalInk(List.of(
                AiRouteAdditionalCostStatus.OWNED,
                AiRouteAdditionalCostStatus.ACTIVE_RENTAL,
                AiRouteAdditionalCostStatus.ONE_INK,
                AiRouteAdditionalCostStatus.ONE_INK));

        assertEquals(2, additionalInk);
    }

    @Test
    void 상태_목록이_null이면_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AiRouteAdditionalCostPolicy.additionalInk(null));
    }
}
