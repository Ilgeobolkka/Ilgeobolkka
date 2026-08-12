package com.example.ilgeobolkka.airoute.service.assembly;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult.Item;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult.Status;
import com.example.ilgeobolkka.airoute.service.validation.ValidatedRouteProposal;
import com.example.ilgeobolkka.airoute.service.validation.ValidatedRouteProposal.ValidatedRouteItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class AiRouteAssemblerTest {

    private static final long BOOK_ID = 42L;
    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final int INK_BALANCE = 15;

    private final AiRouteAssembler assembler = new AiRouteAssembler(new AiRouteGuideFactory());

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 15})
    void 비소장_경로는_새_페이지_비용을_선택_예산까지_포함한다(int budget) {
        ValidatedRouteProposal proposal = independentCandidates(15);

        AiRouteGenerationResult result = assembler.assemble(
                proposal,
                inkCommand(budget, INK_BALANCE),
                AiRouteEntitlementSnapshot.forNonOwned(INK_BALANCE, Set.of()),
                pages(15));

        assertAll(
                () -> assertEquals(Status.ROUTE, result.status()),
                () -> assertEquals(budget, result.items().size()),
                () -> assertTrue(result.items().stream()
                        .allMatch(item -> item.additionalCostStatus()
                                == AiRouteAdditionalCostStatus.ONE_INK)),
                () -> assertEquals(
                        IntStream.rangeClosed(1, budget).boxed().toList(),
                        pageNumbers(result)));
    }

    @Test
    void 예산이_0이어도_활성_대여_페이지로_경로를_만들_수_있다() {
        AiRouteGenerationResult result = assembler.assemble(
                independentCandidates(3),
                inkCommand(0, INK_BALANCE),
                AiRouteEntitlementSnapshot.forNonOwned(INK_BALANCE, Set.of(1, 3)),
                pages(3));

        assertAll(
                () -> assertEquals(List.of(1, 3), pageNumbers(result)),
                () -> assertTrue(result.items().stream()
                        .allMatch(item -> item.additionalCostStatus()
                                == AiRouteAdditionalCostStatus.ACTIVE_RENTAL)));
    }

    @Test
    void 생성_시점_잔액보다_큰_예산은_조립_전에_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> assembler.assemble(
                        independentCandidates(1),
                        inkCommand(10, 10),
                        AiRouteEntitlementSnapshot.forNonOwned(9, Set.of()),
                        pages(1)));
    }

    @ParameterizedTest
    @EnumSource(AiRouteDepth.class)
    void 소장_경로는_깊이별_페이지_상한과_소장_비용_상태를_적용한다(AiRouteDepth depth) {
        int expectedPageCount = switch (depth) {
            case QUICK -> 5;
            case BALANCED -> 10;
            case DEEP -> 15;
        };

        AiRouteGenerationResult result = assembler.assemble(
                independentCandidates(20),
                ownedCommand(depth),
                AiRouteEntitlementSnapshot.forOwned(),
                pages(20));

        assertAll(
                () -> assertEquals(expectedPageCount, result.items().size()),
                () -> assertTrue(result.items().stream()
                        .allMatch(item -> item.additionalCostStatus()
                                == AiRouteAdditionalCostStatus.OWNED)));
    }

    @Test
    void 소장_경로는_관련_페이지가_깊이_상한보다_적으면_있는_페이지만_포함한다() {
        AiRouteGenerationResult result = assembler.assemble(
                independentCandidates(3),
                ownedCommand(AiRouteDepth.DEEP),
                AiRouteEntitlementSnapshot.forOwned(),
                pages(3));

        assertEquals(List.of(1, 2, 3), pageNumbers(result));
    }

    @Test
    void 소장_후보의_선수_묶음이_깊이_상한보다_크면_INSUFFICIENT_DEPTH를_반환한다() {
        ValidatedRouteProposal proposal = proposal(
                List.of(
                        item(1, true),
                        item(2, true),
                        item(3, true),
                        item(4, true),
                        item(5, true),
                        item(6, false)),
                Map.of(6, Set.of(1, 2, 3, 4, 5)));

        AiRouteGenerationResult result = assembler.assemble(
                proposal,
                ownedCommand(AiRouteDepth.QUICK),
                AiRouteEntitlementSnapshot.forOwned(),
                pages(6));

        assertAll(
                () -> assertEquals(Status.NO_ROUTE, result.status()),
                () -> assertEquals(
                        AiRouteNoRouteReason.INSUFFICIENT_DEPTH,
                        result.noRouteReason()),
                () -> assertNull(result.minimumRequiredInk()),
                () -> assertTrue(result.items().isEmpty()));
    }

    @Test
    void 다단계_선수_묶음이_예산을_넘으면_선수와_의존_페이지를_함께_제외한다() {
        ValidatedRouteProposal proposal = proposal(
                List.of(item(1, true), item(2, true), item(3, false), item(4, false)),
                Map.of(3, Set.of(1, 2), 4, Set.of()));

        AiRouteGenerationResult result = assembler.assemble(
                proposal,
                inkCommand(1, INK_BALANCE),
                AiRouteEntitlementSnapshot.forNonOwned(INK_BALANCE, Set.of()),
                pages(4));

        assertEquals(List.of(4), pageNumbers(result));
    }

    @Test
    void 같은_중복_그룹에서는_먼저_선택한_페이지_하나만_포함한다() {
        List<AiRouteAssemblyPage> pages = List.of(
                page(1, 60, List.of("same-concept")),
                page(2, 60, List.of("same-concept")),
                page(3, 60, List.of()));

        AiRouteGenerationResult result = assembler.assemble(
                independentCandidates(3),
                inkCommand(3, INK_BALANCE),
                AiRouteEntitlementSnapshot.forNonOwned(INK_BALANCE, Set.of()),
                pages);

        assertAll(
                () -> assertEquals(List.of(1, 3), pageNumbers(result)),
                () -> assertEquals(List.of(1, 2), result.items().stream()
                        .map(Item::position)
                        .toList()));
    }

    @Test
    void 내부_중복_충돌_후보는_건너뛰고_완전한_다른_후보를_선택한다() {
        ValidatedRouteProposal proposal = proposal(
                List.of(item(1, true), item(2, false), item(3, false)),
                Map.of(2, Set.of(1), 3, Set.of()));
        List<AiRouteAssemblyPage> pages = List.of(
                page(1, 60, List.of("same-concept")),
                page(2, 60, List.of("same-concept")),
                page(3, 60, List.of()));

        AiRouteGenerationResult result = assembler.assemble(
                proposal,
                inkCommand(1, INK_BALANCE),
                AiRouteEntitlementSnapshot.forNonOwned(INK_BALANCE, Set.of()),
                pages);

        assertEquals(List.of(3), pageNumbers(result));
    }

    @Test
    void 모든_후보_묶음에_내부_중복_충돌이_있으면_조립_입력을_거부한다() {
        ValidatedRouteProposal proposal = proposal(
                List.of(item(1, true), item(2, false)),
                Map.of(2, Set.of(1)));
        List<AiRouteAssemblyPage> pages = List.of(
                page(1, 60, List.of("same-concept")),
                page(2, 60, List.of("same-concept")));

        assertThrows(
                IllegalStateException.class,
                () -> assembler.assemble(
                        proposal,
                        inkCommand(0, INK_BALANCE),
                        AiRouteEntitlementSnapshot.forNonOwned(INK_BALANCE, Set.of()),
                        pages));
    }

    @Test
    void 관련_후보가_없으면_최소_필요_잉크_없이_NO_RELEVANT_PAGES를_반환한다() {
        AiRouteGenerationResult result = assembler.noRelevantPages();

        assertAll(
                () -> assertEquals(Status.NO_ROUTE, result.status()),
                () -> assertEquals(AiRouteNoRouteReason.NO_RELEVANT_PAGES, result.noRouteReason()),
                () -> assertNull(result.minimumRequiredInk()),
                () -> assertTrue(result.items().isEmpty()));
    }

    @Test
    void 선수_묶음의_최소_비용이_예산보다_크면_INSUFFICIENT_BUDGET을_반환한다() {
        ValidatedRouteProposal proposal = proposal(
                List.of(item(1, true), item(2, true), item(3, false)),
                Map.of(3, Set.of(1, 2)));
        AiRouteEntitlementSnapshot entitlement =
                AiRouteEntitlementSnapshot.forNonOwned(INK_BALANCE, Set.of(1));

        AiRouteGenerationResult insufficient = assembler.assemble(
                proposal, inkCommand(1, INK_BALANCE), entitlement, pages(3));
        AiRouteGenerationResult boundary = assembler.assemble(
                proposal, inkCommand(2, INK_BALANCE), entitlement, pages(3));

        assertAll(
                () -> assertEquals(Status.NO_ROUTE, insufficient.status()),
                () -> assertEquals(
                        AiRouteNoRouteReason.INSUFFICIENT_BUDGET,
                        insufficient.noRouteReason()),
                () -> assertEquals(2, insufficient.minimumRequiredInk()),
                () -> assertEquals(List.of(1, 2, 3), pageNumbers(boundary)));
    }

    @Test
    void 예상_시간은_올림해_최소_1분이며_비용_상태는_생성_사본에서_정한다() {
        ValidatedRouteProposal proposal = independentCandidates(3);
        List<AiRouteAssemblyPage> pages = List.of(
                page(1, 1, List.of()),
                page(2, 60, List.of()),
                page(3, 61, List.of()));

        AiRouteGenerationResult result = assembler.assemble(
                proposal,
                inkCommand(2, INK_BALANCE),
                AiRouteEntitlementSnapshot.forNonOwned(INK_BALANCE, Set.of(1)),
                pages);

        assertAll(
                () -> assertEquals(List.of(1, 1, 2), result.items().stream()
                        .map(Item::estimatedMinutes)
                        .toList()),
                () -> assertEquals(
                        List.of(
                                AiRouteAdditionalCostStatus.ACTIVE_RENTAL,
                                AiRouteAdditionalCostStatus.ONE_INK,
                                AiRouteAdditionalCostStatus.ONE_INK),
                        result.items().stream().map(Item::additionalCostStatus).toList()),
                () -> assertTrue(result.items().stream()
                        .allMatch(item -> item.guide().contains("공개 주제"))));
    }

    @Test
    void 입력_목록과_권한_집합을_변경하지_않는다() {
        List<AiRouteAssemblyPage> pages = new ArrayList<>(pages(2));
        Set<Integer> activeRentals = new LinkedHashSet<>(Set.of(1));
        AiRouteEntitlementSnapshot entitlement =
                AiRouteEntitlementSnapshot.forNonOwned(INK_BALANCE, activeRentals);

        assembler.assemble(independentCandidates(2), inkCommand(1, INK_BALANCE), entitlement, pages);

        assertAll(
                () -> assertEquals(List.of(1, 2), pages.stream()
                        .map(AiRouteAssemblyPage::pageNumber)
                        .toList()),
                () -> assertEquals(Set.of(1), activeRentals),
                () -> assertEquals(Set.of(1), entitlement.activeRentalPageNumbers()));
    }

    @Test
    void 한_페이지에_같은_중복_그룹_키가_반복되면_입력에서_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> page(1, 60, List.of("same-concept", "same-concept")));
    }

    @Test
    void 경로_조립_결과의_상태는_필수다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiRouteGenerationResult(null, List.of(), null, null));
    }

    @Test
    void 깊이_부족_결과에는_최소_필요_잉크를_넣을_수_없다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiRouteGenerationResult(
                        Status.NO_ROUTE,
                        List.of(),
                        AiRouteNoRouteReason.INSUFFICIENT_DEPTH,
                        1));
    }

    private AiRouteGenerationCommand inkCommand(int budget, int balance) {
        return AiRouteGenerationCommand.forInkBudget(
                BOOK_ID, CONTENT_VERSION, "투자 판단 기준", budget, balance);
    }

    private AiRouteGenerationCommand ownedCommand(AiRouteDepth depth) {
        return AiRouteGenerationCommand.forOwnedDepth(
                BOOK_ID, CONTENT_VERSION, "투자 판단 기준", depth);
    }

    private ValidatedRouteProposal independentCandidates(int pageCount) {
        List<ValidatedRouteItem> items = IntStream.rangeClosed(1, pageCount)
                .mapToObj(pageNumber -> item(pageNumber, false))
                .toList();
        Map<Integer, Set<Integer>> closures = new LinkedHashMap<>();
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            closures.put(pageNumber, Set.of());
        }
        return proposal(items, closures);
    }

    private ValidatedRouteProposal proposal(
            List<ValidatedRouteItem> items, Map<Integer, Set<Integer>> closures) {
        Set<Integer> allowedPageNumbers = new LinkedHashSet<>();
        closures.forEach((candidate, prerequisites) -> {
            allowedPageNumbers.add(candidate);
            allowedPageNumbers.addAll(prerequisites);
        });
        return new ValidatedRouteProposal(items, closures, allowedPageNumbers);
    }

    private ValidatedRouteItem item(int pageNumber, boolean prerequisite) {
        return new ValidatedRouteItem(
                1000L + pageNumber,
                pageNumber,
                pageNumber,
                AiRouteItemRelevance.HIGH,
                prerequisite,
                prerequisite ? AiRouteItemRole.PREREQUISITE : AiRouteItemRole.CORE);
    }

    private List<AiRouteAssemblyPage> pages(int pageCount) {
        return IntStream.rangeClosed(1, pageCount)
                .mapToObj(pageNumber -> page(pageNumber, 60, List.of()))
                .toList();
    }

    private AiRouteAssemblyPage page(
            int pageNumber, int estimatedReadingSeconds, List<String> duplicateGroups) {
        return new AiRouteAssemblyPage(
                1000L + pageNumber,
                pageNumber,
                "공개 주제 " + pageNumber,
                estimatedReadingSeconds,
                duplicateGroups);
    }

    private List<Integer> pageNumbers(AiRouteGenerationResult result) {
        return result.items().stream().map(Item::pageNumber).toList();
    }
}
