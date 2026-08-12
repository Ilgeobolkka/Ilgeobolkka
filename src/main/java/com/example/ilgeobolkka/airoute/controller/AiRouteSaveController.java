package com.example.ilgeobolkka.airoute.controller;

import com.example.ilgeobolkka.airoute.dto.FindAiRouteResponse;
import com.example.ilgeobolkka.airoute.dto.SaveAiRouteResult;
import com.example.ilgeobolkka.airoute.facade.AiRouteSaveFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-route-generations")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteSaveController {

    private final AiRouteSaveFacade aiRouteSaveFacade;

    /**
     * 임시 생성 결과를 저장 경로로 옮긴다. 바디를 받지 않는다. 페이지·순서·가이드 문구를 클라이언트에서
     * 받으면 조작한 경로가 저장될 수 있어서, 서버가 저장해 둔 값만 쓴다.
     *
     * <p>새로 만들면 201, 같은 생성의 재시도면 처음 만든 경로와 200 이다.
     */
    @PostMapping("/{generationId}/routes")
    ResponseEntity<FindAiRouteResponse> saveRoute(
            @PathVariable UUID generationId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        SaveAiRouteResult result =
                aiRouteSaveFacade.saveRoute(authenticatedReader.readerId(), generationId);

        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.route());
    }
}
