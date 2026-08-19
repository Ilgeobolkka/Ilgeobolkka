package com.example.ilgeobolkka.airoute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.ilgeobolkka.airoute.controller.AiRouteDetailPageController;
import com.example.ilgeobolkka.airoute.dto.AiRouteItemResponse;
import com.example.ilgeobolkka.airoute.dto.FindAiRouteResponse;
import com.example.ilgeobolkka.airoute.entity.AiReadingRouteFeedback;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotFoundException;
import com.example.ilgeobolkka.airoute.facade.AiRouteQueryFacade;
import com.example.ilgeobolkka.global.config.SecurityConfig;
import com.example.ilgeobolkka.global.security.ApiSecurityErrorHandler;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.global.web.CommonWebModelAdvice;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(
        controllers = AiRouteDetailPageController.class,
        properties = "ai-route.enabled=true")
@Import({SecurityConfig.class, ApiSecurityErrorHandler.class, CommonWebModelAdvice.class})
class AiRouteDetailPageTest {

    private static final long READER_ID = 47L;
    private static final long OTHER_READER_ID = 48L;
    private static final long ROUTE_ID = 476L;
    private static final long BOOK_ID = 15L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiRouteQueryFacade aiRouteQueryFacade;

    @Test
    void 소유자_경로를_추천_순서와_비용_상태로_렌더링한다() throws Exception {
        FindAiRouteResponse route = 진행중_경로();
        when(aiRouteQueryFacade.findRoute(READER_ID, ROUTE_ID)).thenReturn(route);

        MvcResult result = mockMvc.perform(get("/ai-routes/{routeId}", ROUTE_ID)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/ai-route-detail"))
                .andExpect(model().attribute("route", route))
                .andReturn();

        String html = result.getResponse().getContentAsString();

        assertTrue(html.contains("data-ai-route-detail-root"));
        assertTrue(html.contains("name=\"_csrf\""));
        assertTrue(html.contains("name=\"_csrf_header\""));
        assertTrue(html.contains("/js/ai-route/route-detail.js"));
        assertFalse(html.contains("/js/ai-route/route-detail-page.js"));
        assertTrue(html.indexOf("원본 <span>42</span>페이지")
                < html.indexOf("원본 <span>3</span>페이지"));
        assertTrue(html.contains("1잉크로 열기"));
        assertTrue(html.contains("소장 도서 · 열기"));
        assertTrue(html.contains("관련도 높음"));
        assertTrue(html.contains("관련도 보통"));
        assertTrue(html.contains(" · 선수 페이지</span>"));
        assertFalse(html.contains(" · 선수 개념</span>"));
        assertTrue(html.contains("data-route-position=\"1\" data-page-number=\"42\""));
        assertTrue(html.contains("data-route-position=\"2\" data-page-number=\"3\""));
        for (String requiredHook : List.of(
                "data-route-progress",
                "data-reader-status",
                "data-route-content",
                "data-route-ink-notice",
                "data-route-ink-notice-message",
                "data-route-ink-link",
                "data-route-previous",
                "data-route-next",
                "data-original-viewer-link",
                "data-make-current",
                "data-delete-route",
                "data-current-badge",
                "data-completed-badge",
                "data-completed-time",
                "data-feedback-guide",
                "data-feedback-rating",
                "data-item-opened-badge",
                "data-item-opened-time",
                "data-open-route-item")) {
            assertTrue(html.contains(requiredHook), requiredHook + " 훅이 필요합니다.");
        }
        assertTrue(html.contains("data-cost-status=\"ONE_INK\""));
        assertTrue(html.contains("data-cost-status=\"OWNED\""));
        assertTrue(html.contains("data-opened=\"false\""));
        assertTrue(html.contains("data-opened=\"true\""));
        assertTrue(html.contains("data-prerequisite=\"true\""));
        assertTrue(html.contains("data-prerequisite=\"false\""));
        assertFalse(html.contains("data-route-items"));
        assertFalse(html.contains("data-route-placeholder"));

        int openedTimeHookIndex = html.indexOf("data-item-opened-time");
        String unreadOpenedTimeTag = html.substring(
                html.lastIndexOf("<time", openedTimeHookIndex),
                html.indexOf(">", openedTimeHookIndex) + 1);
        assertTrue(unreadOpenedTimeTag.contains("hidden"));
        // Bootstrap의 d-block은 hidden보다 우선하므로 미열람 시각이 노출되지 않게 막는다.
        assertFalse(unreadOpenedTimeTag.contains("d-block"));
        assertTrue(html.contains("열람 2026. 8. 14. 10:05"));

        assertTrue(html.contains("href=\"/ink\""));
        assertTrue(html.matches("(?s).*data-feedback-rating=\"HELPFUL\"[^>]*disabled.*"));
        assertFalse(html.contains("<script>alert('purpose')</script>"));
        assertFalse(html.contains("<img src=x onerror=alert('guide')>"));
        assertTrue(html.contains("&lt;script&gt;alert(&#39;purpose&#39;)&lt;/script&gt;"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(&#39;guide&#39;)&gt;"));
    }

    @Test
    void 완료_경로는_피드백_세_개를_활성화하고_현재_평가를_표시한다() throws Exception {
        FindAiRouteResponse route = 완료_경로();
        when(aiRouteQueryFacade.findRoute(READER_ID, ROUTE_ID)).thenReturn(route);

        String html = mockMvc.perform(get("/ai-routes/{routeId}", ROUTE_ID)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(html.contains("data-route-completed=\"true\""));
        assertTrue(html.contains("data-evaluation-available=\"true\""));
        assertTrue(html.contains("data-route-rating=\"NEUTRAL\""));
        assertTrue(html.contains("data-completed-time"));
        assertTrue(html.contains("완료 2026. 8. 14. 10:10"));
        assertTrue(html.matches(
                "(?s).*data-feedback-rating=\"NEUTRAL\"[^>]*aria-pressed=\"true\".*"));
        assertFalse(html.matches(
                "(?s).*data-feedback-rating=\"HELPFUL\"[^>]*disabled.*"));
        assertFalse(html.matches(
                "(?s).*data-feedback-rating=\"NEUTRAL\"[^>]*disabled.*"));
        assertFalse(html.matches(
                "(?s).*data-feedback-rating=\"NOT_HELPFUL\"[^>]*disabled.*"));
    }

    @Test
    void 비로그인은_로그인으로_이동하고_다른_독자_경로는_404다() throws Exception {
        mockMvc.perform(get("/ai-routes/{routeId}", ROUTE_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        AiRouteNotFoundException notFoundException = new AiRouteNotFoundException(ROUTE_ID);
        when(aiRouteQueryFacade.findRoute(OTHER_READER_ID, ROUTE_ID))
                .thenThrow(notFoundException);

        MvcResult result = mockMvc.perform(get("/ai-routes/{routeId}", ROUTE_ID)
                        .with(authentication(인증된_독자(OTHER_READER_ID))))
                .andExpect(status().isNotFound())
                .andReturn();

        ResponseStatusException responseStatusException = assertInstanceOf(
                ResponseStatusException.class,
                result.getResolvedException());
        assertSame(notFoundException, responseStatusException.getCause());
    }

    @Test
    void 잘못된_경로_ID도_비로그인이면_컨트롤러_검증보다_로그인을_먼저_요구한다() throws Exception {
        mockMvc.perform(get("/ai-routes/not-a-number"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    private FindAiRouteResponse 진행중_경로() {
        return new FindAiRouteResponse(
                ROUTE_ID,
                BOOK_ID,
                "테스트 도서",
                "<script>alert('purpose')</script>",
                false,
                Instant.parse("2026-08-14T01:00:00Z"),
                null,
                false,
                null,
                List.of(
                        new AiRouteItemResponse(
                                1,
                                42,
                                AiRouteItemRelevance.HIGH,
                                true,
                                AiRouteItemRole.PREREQUISITE,
                                3,
                                "<img src=x onerror=alert('guide')>",
                                AiRouteAdditionalCostStatus.ONE_INK,
                                null),
                        new AiRouteItemResponse(
                                2,
                                3,
                                AiRouteItemRelevance.MEDIUM,
                                false,
                                AiRouteItemRole.CORE,
                                2,
                                "핵심을 확인합니다.",
                                AiRouteAdditionalCostStatus.OWNED,
                                Instant.parse("2026-08-14T01:05:00Z"))));
    }

    private FindAiRouteResponse 완료_경로() {
        FindAiRouteResponse route = 진행중_경로();
        List<AiRouteItemResponse> openedItems = route.items().stream()
                .map(item -> new AiRouteItemResponse(
                        item.position(),
                        item.pageNumber(),
                        item.relevance(),
                        item.prerequisite(),
                        item.role(),
                        item.estimatedMinutes(),
                        item.guide(),
                        item.additionalCostStatus(),
                        Instant.parse("2026-08-14T01:10:00Z")))
                .toList();
        return new FindAiRouteResponse(
                route.routeId(),
                route.bookId(),
                route.bookTitle(),
                route.purpose(),
                true,
                route.createdAt(),
                Instant.parse("2026-08-14T01:10:00Z"),
                true,
                AiReadingRouteFeedback.NEUTRAL,
                openedItems);
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(readerId), null, "ROLE_USER");
    }
}
