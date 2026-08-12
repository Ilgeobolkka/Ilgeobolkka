package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.repository.AiRouteSummaryProjection;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 저장 경로 목록 응답.
 *
 * <p>{@code page}는 조회한 페이지를 그대로 돌려준다. 전체 범위를 넘은 양수도 오류가 아니라 빈 목록과 요청한
 * 페이지 번호를 반환하는 계약이라, 실제 데이터가 있는 마지막 페이지로 바꿔 주지 않는다.
 */
public record FindAiRoutesResponse(
        List<AiRouteSummaryResponse> routes,
        int page,
        int totalPages,
        long totalCount) {

    public static FindAiRoutesResponse from(
            Page<AiRouteSummaryProjection> routes,
            int requestedPage) {
        List<AiRouteSummaryResponse> items =
                routes.getContent().stream().map(AiRouteSummaryResponse::from).toList();
        return new FindAiRoutesResponse(
                items,
                requestedPage,
                routes.getTotalPages(),
                routes.getTotalElements());
    }
}
