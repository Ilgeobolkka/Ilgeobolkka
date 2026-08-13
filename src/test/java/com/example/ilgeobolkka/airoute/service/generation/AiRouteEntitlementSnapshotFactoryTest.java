package com.example.ilgeobolkka.airoute.service.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiRouteEntitlementSnapshotFactoryTest {

    private final AiRouteEntitlementSnapshotFactory factory =
            new AiRouteEntitlementSnapshotFactory();

    @Test
    void 소장_도서는_깊이_명령과_소장_snapshot을_만든다() {
        var snapshot = factory.create(
                AiRouteGenerationCommand.forOwnedDepth(
                        1L, "v1", "핵심 이해", AiRouteDepth.QUICK),
                true,
                0,
                Set.of());

        assertEquals(true, snapshot.owned());
    }

    @Test
    void 비소장_도서는_현재_잔액과_활성_대여를_보존한다() {
        var snapshot = factory.create(
                AiRouteGenerationCommand.forInkBudget(1L, "v1", "핵심 이해", 2, 5),
                false,
                5,
                Set.of(11L));

        assertEquals(5, snapshot.inkBalance());
        assertEquals(Set.of(11L), snapshot.activeRentalPageIds());
    }

    @Test
    void 현재_권한과_명령_종류가_다르면_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> factory.create(
                        AiRouteGenerationCommand.forInkBudget(
                                1L, "v1", "핵심 이해", 2, 5),
                        true,
                        0,
                        Set.of()));
    }

    @Test
    void 입력_뒤_잔액이_줄어_예산보다_작아졌으면_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> factory.create(
                        AiRouteGenerationCommand.forInkBudget(
                                1L, "v1", "핵심 이해", 5, 5),
                        false,
                        4,
                        Set.of()));
    }
}
