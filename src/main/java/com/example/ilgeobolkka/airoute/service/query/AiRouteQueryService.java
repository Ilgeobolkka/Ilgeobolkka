package com.example.ilgeobolkka.airoute.service.query;

import com.example.ilgeobolkka.airoute.dto.AiRouteItemResponse;
import com.example.ilgeobolkka.airoute.dto.FindAiRouteResponse;
import com.example.ilgeobolkka.airoute.dto.FindAiRoutesResponse;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotFoundException;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteSummaryProjection;
import com.example.ilgeobolkka.airoute.service.AiRouteAdditionalCostCalculator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유자의 저장 경로 목록과 상세를 읽는다.
 *
 * <p>조회만 하고 아무 상태도 바꾸지 않는다. 항목 열람 시각·경로 완료·피드백·잉크·대여는 이 경로로 변하지
 * 않으며, 다시 계산하는 값은 항목별 추가 비용 상태 하나뿐이다.
 *
 * <p>응답 record 를 이 트랜잭션 안에서 끝까지 만든다. 호출자가 lazy 연관을 들고 나가면 Controller 단계의
 * 초기화가 필요해지고, 그때부터는 OIV 설정이 계약의 일부가 된다.
 */
@Service
@RequiredArgsConstructor
public class AiRouteQueryService {

    /** 한 페이지 건수. 정본이 고정한 값이라 클라이언트가 지정하지 않는다. */
    static final int PAGE_SIZE = 10;

    private final AiReadingRouteRepository aiReadingRouteRepository;
    private final AiReadingRouteItemRepository aiReadingRouteItemRepository;
    private final AiRouteAdditionalCostCalculator additionalCostCalculator;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public FindAiRoutesResponse getRoutes(long readerId, int page) {
        PageRequest pageable = PageRequest.of(page - 1, PAGE_SIZE);

        return FindAiRoutesResponse.from(findSummaries(readerId, pageable), page);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public FindAiRouteResponse getRoute(long readerId, long routeId) {
        AiRouteSummaryProjection route =
                aiReadingRouteRepository
                        .findSummaryByReaderIdAndId(readerId, routeId)
                        .orElseThrow(() -> new AiRouteNotFoundException(routeId));

        return FindAiRouteResponse.of(route, findItems(readerId, route));
    }

    /**
     * 요청한 페이지의 경로를 읽되, 오프셋이 int 범위를 넘으면 질의하지 않고 빈 페이지를 만든다.
     *
     * <p>정본은 전체 범위를 넘은 양수를 오류가 아니라 빈 목록으로 정했다. 그런데 {@code page}가 클수록
     * 오프셋이 커져 드라이버가 다루지 못하는 값이 되므로, 전체 건수만 세어 같은 형태의 응답을 만든다.
     */
    private Page<AiRouteSummaryProjection> findSummaries(long readerId, PageRequest pageable) {
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(
                    List.of(), pageable, aiReadingRouteRepository.countByReaderId(readerId));
        }

        return aiReadingRouteRepository.findSummariesByReaderId(readerId, pageable);
    }

    /**
     * 항목을 저장 순서대로 읽고 추가 비용 상태만 지금 권한으로 다시 계산한다.
     *
     * <p>판정은 {@link AiRouteAdditionalCostCalculator} 가 한다. 저장이 예산과 비교할 때 쓰는 규칙과 같은
     * 규칙이어야 하고, 그 둘은 저장 요청 하나에서 잇달아 돈다.
     *
     * <p>소장 여부는 도서 단위라 경로마다 한 번만 확인한다. 대여는 페이지 단위여서 항목마다 확인하므로 상세 한
     * 건에 항목 수만큼 대여 조회가 나간다. 깊이 상한 15는 소장 도서 경로에만 걸리고 잉크 예산 경로는 페이지 수
     * 상한이 없으므로, 실질 상한은 schema 가 정한 item 72개다. 후보 상한 40으로 좁혀 볼 수는 없는데 허용
     * 페이지가 후보와 그 선수 개념 closure 의 합집합이기 때문이다. 읽기 전용이고 유계라 이번 범위에서는 두었고,
     * 일괄 조회는 RentalService 를 고쳐야 해서 별도 과제로 넘긴다.
     *
     * <p>가이드 문구와 예상 시간도 여기서 조립해 넘긴다. 응답 record 가 문구 규칙을 직접 부르면 dto 와
     * {@code service.query}가 서로를 임포트한다.
     */
    private List<AiRouteItemResponse> findItems(long readerId, AiRouteSummaryProjection route) {
        boolean owned = additionalCostCalculator.isOwned(readerId, route.getBookId());
        Instant now = clock.instant();

        return aiReadingRouteItemRepository.findItemsByRouteId(route.getRouteId()).stream()
                .map(item ->
                        AiRouteItemResponse.of(
                                item,
                                AiRouteItemGuideAssembler.estimatedMinutes(
                                        item.getEstimatedReadingSeconds()),
                                AiRouteItemGuideAssembler.guide(
                                        item.getRole(), item.getGuideTopic()),
                                additionalCostCalculator.status(
                                        readerId, item.getBookPageId(), owned, now)))
                .toList();
    }
}
