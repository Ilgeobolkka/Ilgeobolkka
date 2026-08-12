package com.example.ilgeobolkka.airoute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장 경로 목록·상세 조회의 페이지·소유권·정렬·DTO 계약.
 *
 * <p>경로 저장 API 가 아직 없어 픽스처를 SQL 로 직접 넣는다. 저장이 들어오면 그 API 로 바꾸는 편이 낫지만,
 * 지금 그것을 기다리면 조회 계약이 검증되지 않은 채로 남는다.
 *
 * <p>{@code ai-route.enabled}를 켠다. 기본값이 꺼짐이라 켜지 않으면 경로 자체가 등록되지 않는다. 켜면
 * {@code OpenAiConfiguration}이 OpenAI 세 설정을 기동 시점에 검증하므로(활성 서버가 키 없이 뜨는 것을 막는
 * 계약이다) 자리만 채운 값을 함께 넣는다. 조회는 외부를 호출하지 않아 값의 내용은 쓰이지 않는다.
 */
@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=proj-scrum465-test",
            "openai.api-key=not-a-real-key-scrum465-test",
            "openai.data-policy-version=policy-test"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class AiRouteQueryApiMySqlIntegrationTest {

    private static final long READER_ID = 465_001L;
    private static final long OTHER_READER_ID = 465_002L;
    private static final long BOOK_ID = 465_001L;
    private static final long OTHER_BOOK_ID = 465_002L;
    private static final long PAGE_ID_BASE = 465_100L;
    private static final long ROUTE_ID_BASE = 465_100L;
    private static final long ITEM_ID_BASE = 465_100L;
    private static final long RENTAL_ID_BASE = 465_100L;
    private static final long OWNERSHIP_ID_BASE = 465_100L;

    private static final int BOOK_PRICE_WON = 10_000;

    /** 세 항목이 서로 다른 역할·관련도·선수 개념·예상 시간을 갖도록 만든 페이지들. */
    private static final long PAGE_ID_CORE = PAGE_ID_BASE + 1;
    private static final long PAGE_ID_EXAMPLE = PAGE_ID_BASE + 2;
    private static final long PAGE_ID_NO_TOPIC = PAGE_ID_BASE + 3;

    /** 조회 시각과 무관하게 활성·만료가 갈리도록 현재에서 충분히 떨어뜨린 대여 기간. */
    private static final String ACTIVE_RENTAL_START = "2020-01-01 00:00:00.000000";
    private static final String ACTIVE_RENTAL_END = "2099-01-01 00:00:00.000000";
    private static final String EXPIRED_RENTAL_START = "2020-01-01 00:00:00.000000";
    private static final String EXPIRED_RENTAL_END = "2020-02-01 00:00:00.000000";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AiRouteQueryApiMySqlIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        독자와_잉크_계좌를_생성한다(READER_ID);
        독자와_잉크_계좌를_생성한다(OTHER_READER_ID);
        도서를_생성한다(BOOK_ID, "샘플 도서");
        도서를_생성한다(OTHER_BOOK_ID, "다른 도서");
        페이지를_생성한다(PAGE_ID_CORE, BOOK_ID, 12, "투자 판단 기준", 200);
        페이지를_생성한다(PAGE_ID_EXAMPLE, BOOK_ID, 7, "부채비율 해석", 59);
        페이지를_생성한다(PAGE_ID_NO_TOPIC, BOOK_ID, 30, null, null);
    }

    @Test
    void 저장_경로가_없는_계정은_빈_목록과_0_페이지를_반환한다() throws Exception {
        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void 목록은_소유자의_경로만_최신순으로_반환한다() throws Exception {
        경로를_생성한다(READER_ID, ROUTE_ID_BASE + 1, BOOK_ID, "재무제표 읽기", "2026-08-01 09:00:00.123456");
        경로를_생성한다(READER_ID, ROUTE_ID_BASE + 2, OTHER_BOOK_ID, "손익 구조", "2026-08-02 09:00:00.123456");
        경로를_생성한다(
                OTHER_READER_ID, ROUTE_ID_BASE + 3, BOOK_ID, "남의 목적", "2026-08-03 09:00:00.123456");
        현재_경로로_지정한다(READER_ID, BOOK_ID, ROUTE_ID_BASE + 1);

        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(2))
                .andExpect(jsonPath("$.routes[0].routeId").value(ROUTE_ID_BASE + 2))
                .andExpect(jsonPath("$.routes[0].bookId").value(OTHER_BOOK_ID))
                .andExpect(jsonPath("$.routes[0].bookTitle").value("다른 도서"))
                .andExpect(jsonPath("$.routes[0].purpose").value("손익 구조"))
                .andExpect(jsonPath("$.routes[0].current").value(false))
                .andExpect(jsonPath("$.routes[0].createdAt").value("2026-08-02T09:00:00.123456Z"))
                .andExpect(jsonPath("$.routes[0].completedAt").value(nullValue()))
                .andExpect(jsonPath("$.routes[0].rating").value(nullValue()))
                .andExpect(jsonPath("$.routes[1].routeId").value(ROUTE_ID_BASE + 1))
                .andExpect(jsonPath("$.routes[1].current").value(true))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void 열_건은_한_페이지에_모두_담긴다() throws Exception {
        경로를_여러_건_생성한다(10, "2026-08-01 09:00:00.123456");

        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(10))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(10));

        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "2")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(0))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(10));
    }

    @Test
    void T_AIR_016_같은_시각의_열한_건은_ID_내림차순으로_겹침_없이_나뉜다() throws Exception {
        경로를_여러_건_생성한다(11, "2026-08-01 09:00:00.123456");

        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(10))
                .andExpect(
                        jsonPath("$.routes[*].routeId")
                                .value(contains(
                                        (int) ROUTE_ID_BASE + 11,
                                        (int) ROUTE_ID_BASE + 10,
                                        (int) ROUTE_ID_BASE + 9,
                                        (int) ROUTE_ID_BASE + 8,
                                        (int) ROUTE_ID_BASE + 7,
                                        (int) ROUTE_ID_BASE + 6,
                                        (int) ROUTE_ID_BASE + 5,
                                        (int) ROUTE_ID_BASE + 4,
                                        (int) ROUTE_ID_BASE + 3,
                                        (int) ROUTE_ID_BASE + 2)))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalCount").value(11));

        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "2")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(1))
                .andExpect(jsonPath("$.routes[0].routeId").value(ROUTE_ID_BASE + 1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalCount").value(11));
    }

    @Test
    void 잘못된_페이지는_400이고_범위_초과_양수는_빈_목록이다() throws Exception {
        경로를_여러_건_생성한다(1, "2026-08-01 09:00:00.123456");

        mockMvc.perform(get("/api/ai-routes")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "not-a-number")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "0")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "-1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "214748366")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(0))
                .andExpect(jsonPath("$.page").value(214748366))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void 상세는_저장한_항목을_순서대로_전부_반환한다() throws Exception {
        long routeId = ROUTE_ID_BASE + 1;
        완료한_경로를_생성한다(READER_ID, routeId, "재무제표 읽기");
        현재_경로로_지정한다(READER_ID, BOOK_ID, routeId);
        경로_항목을_생성한다(
                routeId, ITEM_ID_BASE + 1, PAGE_ID_CORE, 1, "HIGH", false, "CORE",
                "2026-08-03 10:00:00.123456");
        경로_항목을_생성한다(
                routeId, ITEM_ID_BASE + 2, PAGE_ID_EXAMPLE, 2, "MEDIUM", true, "EXAMPLE", null);
        경로_항목을_생성한다(
                routeId, ITEM_ID_BASE + 3, PAGE_ID_NO_TOPIC, 3, "HIGH", false, "CONCLUSION", null);

        mockMvc.perform(get("/api/ai-routes/" + routeId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value(routeId))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.bookTitle").value("샘플 도서"))
                .andExpect(jsonPath("$.purpose").value("재무제표 읽기"))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-08-01T09:00:00.123456Z"))
                .andExpect(jsonPath("$.completedAt").value("2026-08-03T11:00:00.123456Z"))
                .andExpect(jsonPath("$.rating").value("HELPFUL"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[*].position").value(contains(1, 2, 3)))
                .andExpect(jsonPath("$.items[*].pageNumber").value(contains(12, 7, 30)))
                .andExpect(jsonPath("$.items[0].relevance").value("HIGH"))
                .andExpect(jsonPath("$.items[0].prerequisite").value(false))
                .andExpect(jsonPath("$.items[0].role").value("CORE"))
                .andExpect(jsonPath("$.items[0].estimatedMinutes").value(4))
                .andExpect(
                        jsonPath("$.items[0].guide")
                                .value("투자 판단 기준에 관한 핵심 개념을 다루는 페이지입니다."))
                .andExpect(jsonPath("$.items[0].openedAt").value("2026-08-03T10:00:00.123456Z"))
                .andExpect(jsonPath("$.items[1].relevance").value("MEDIUM"))
                .andExpect(jsonPath("$.items[1].prerequisite").value(true))
                .andExpect(jsonPath("$.items[1].role").value("EXAMPLE"))
                .andExpect(jsonPath("$.items[1].estimatedMinutes").value(1))
                .andExpect(
                        jsonPath("$.items[1].guide")
                                .value("부채비율 해석에 관한 사례를 다루는 페이지입니다."))
                .andExpect(jsonPath("$.items[1].openedAt").value(nullValue()))
                .andExpect(jsonPath("$.items[2].estimatedMinutes").value(1))
                .andExpect(jsonPath("$.items[2].guide").value("결론을 다루는 페이지입니다."));
    }

    @Test
    void 상세는_분석_텍스트와_내부_식별자를_노출하지_않는다() throws Exception {
        long routeId = ROUTE_ID_BASE + 1;
        경로를_생성한다(READER_ID, routeId, BOOK_ID, "재무제표 읽기", "2026-08-01 09:00:00.123456");
        경로_항목을_생성한다(
                routeId, ITEM_ID_BASE + 1, PAGE_ID_CORE, 1, "HIGH", false, "CORE", null);

        mockMvc.perform(get("/api/ai-routes/" + routeId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").doesNotExist())
                .andExpect(jsonPath("$.contentVersion").doesNotExist())
                .andExpect(jsonPath("$.items[0].bookPageId").doesNotExist())
                .andExpect(jsonPath("$.items[0].aiAnalysisText").doesNotExist())
                .andExpect(jsonPath("$.items[0].embedding").doesNotExist())
                .andExpect(jsonPath("$.items[0].textContent").doesNotExist());
    }

    @Test
    void T_AIR_016_다른_독자의_경로와_없는_경로는_같은_404다() throws Exception {
        long ownedRouteId = ROUTE_ID_BASE + 1;
        경로를_생성한다(
                OTHER_READER_ID, ownedRouteId, BOOK_ID, "남의 목적", "2026-08-01 09:00:00.123456");
        경로_항목을_생성한다(
                ownedRouteId, ITEM_ID_BASE + 1, PAGE_ID_CORE, 1, "HIGH", false, "CORE", null);

        mockMvc.perform(get("/api/ai-routes/" + ownedRouteId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.purpose").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());

        mockMvc.perform(get("/api/ai-routes/" + (ROUTE_ID_BASE + 999))
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void 목록과_상세_조회는_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/ai-routes").param("page", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/ai-routes/" + (ROUTE_ID_BASE + 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void 추가_비용_상태는_소장과_활성_대여로_다시_계산한다() throws Exception {
        long routeId = ROUTE_ID_BASE + 1;
        경로를_생성한다(READER_ID, routeId, BOOK_ID, "재무제표 읽기", "2026-08-01 09:00:00.123456");
        경로_항목을_생성한다(
                routeId, ITEM_ID_BASE + 1, PAGE_ID_CORE, 1, "HIGH", false, "CORE", null);
        경로_항목을_생성한다(
                routeId, ITEM_ID_BASE + 2, PAGE_ID_EXAMPLE, 2, "MEDIUM", false, "EXAMPLE", null);
        경로_항목을_생성한다(
                routeId, ITEM_ID_BASE + 3, PAGE_ID_NO_TOPIC, 3, "HIGH", false, "CONCLUSION", null);

        // 대여도 소장도 없으면 세 항목 모두 1잉크다.
        mockMvc.perform(get("/api/ai-routes/" + routeId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[*].additionalCostStatus")
                                .value(contains("ONE_INK", "ONE_INK", "ONE_INK")));

        // 첫 페이지만 활성 대여, 둘째 페이지는 만료 대여라 만료는 비용을 낮추지 않는다.
        대여한다(READER_ID, RENTAL_ID_BASE + 1, PAGE_ID_CORE, ACTIVE_RENTAL_START, ACTIVE_RENTAL_END);
        대여한다(
                READER_ID,
                RENTAL_ID_BASE + 2,
                PAGE_ID_EXAMPLE,
                EXPIRED_RENTAL_START,
                EXPIRED_RENTAL_END);

        mockMvc.perform(get("/api/ai-routes/" + routeId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[*].additionalCostStatus")
                                .value(contains("ACTIVE_RENTAL", "ONE_INK", "ONE_INK")));

        // 소장은 도서 단위라 대여 여부와 무관하게 모든 항목을 덮는다.
        소장한다(READER_ID, OWNERSHIP_ID_BASE + 1, BOOK_ID);

        mockMvc.perform(get("/api/ai-routes/" + routeId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[*].additionalCostStatus")
                                .value(contains("OWNED", "OWNED", "OWNED")))
                // 비용 상태만 바뀌고 저장한 값은 그대로다.
                .andExpect(jsonPath("$.items[*].position").value(contains(1, 2, 3)))
                .andExpect(jsonPath("$.items[*].pageNumber").value(contains(12, 7, 30)))
                .andExpect(
                        jsonPath("$.items[*].role")
                                .value(contains("CORE", "EXAMPLE", "CONCLUSION")))
                .andExpect(jsonPath("$.purpose").value("재무제표 읽기"));
    }

    @Test
    void 조회는_진행_피드백_잉크_대여를_바꾸지_않는다() throws Exception {
        long routeId = ROUTE_ID_BASE + 1;
        완료한_경로를_생성한다(READER_ID, routeId, "재무제표 읽기");
        경로_항목을_생성한다(
                routeId, ITEM_ID_BASE + 1, PAGE_ID_CORE, 1, "HIGH", false, "CORE",
                "2026-08-03 10:00:00.123456");
        경로_항목을_생성한다(
                routeId, ITEM_ID_BASE + 2, PAGE_ID_EXAMPLE, 2, "MEDIUM", false, "EXAMPLE", null);
        대여한다(READER_ID, RENTAL_ID_BASE + 1, PAGE_ID_CORE, ACTIVE_RENTAL_START, ACTIVE_RENTAL_END);
        잔액을_변경한다(READER_ID, 42);

        Map<String, Object> before = 상태_스냅샷(routeId);

        mockMvc.perform(get("/api/ai-routes")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ai-routes/" + routeId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk());

        assertThat(상태_스냅샷(routeId)).isEqualTo(before);
    }

    private Map<String, Object> 상태_스냅샷(long routeId) {
        Map<String, Object> route =
                jdbcTemplate.queryForMap(
                        """
                        SELECT completed_at, feedback, feedback_at
                        FROM ai_reading_route
                        WHERE id = ?
                        """,
                        routeId);
        List<Map<String, Object>> items =
                jdbcTemplate.queryForList(
                        """
                        SELECT id, opened_at
                        FROM ai_reading_route_item
                        WHERE route_id = ?
                        ORDER BY position
                        """,
                        routeId);
        Map<String, Object> ink =
                jdbcTemplate.queryForMap(
                        "SELECT balance FROM ink_account WHERE reader_id = ?", READER_ID);
        List<Map<String, Object>> rentals =
                jdbcTemplate.queryForList(
                        """
                        SELECT id, book_page_id, rented_at, expires_at
                        FROM page_rental
                        WHERE reader_id = ?
                        ORDER BY id
                        """,
                        READER_ID);

        return Map.of("route", route, "items", items, "ink", ink, "rentals", rentals);
    }

    private void 독자와_잉크_계좌를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-30 00:00:00.000000')
                """,
                readerId,
                "scrum465-" + readerId + "@example.com");
        jdbcTemplate.update("INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)", readerId);
    }

    private void 도서를_생성한다(long bookId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '경제', ?, '샘플 저자', 100, ?)
                """,
                bookId,
                title,
                BOOK_PRICE_WON);
    }

    /**
     * 조회에 필요한 컬럼만 채운 페이지.
     *
     * <p>{@code ai_route_candidate}는 0으로 둔다. 1로 두려면 임베딩과 분석 텍스트까지 채워야 하는데, 조회는
     * 후보 여부를 보지 않으므로 계약과 무관한 픽스처만 늘어난다.
     */
    private void 페이지를_생성한다(
            long pageId,
            long bookId,
            int pageNumber,
            String guideTopic,
            Integer estimatedReadingSeconds) {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path,
                     ai_public_guide_topic, estimated_reading_seconds)
                VALUES (?, ?, ?, 'TEXT', '샘플 본문', NULL, ?, ?)
                """,
                pageId,
                bookId,
                pageNumber,
                guideTopic,
                estimatedReadingSeconds);
    }

    private void 경로를_생성한다(
            long readerId, long routeId, long bookId, String purpose, String createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route
                    (id, generation_id, reader_id, book_id, content_version, normalized_purpose,
                     request_type, max_additional_ink, depth, completed_at, feedback, feedback_at,
                     created_at)
                VALUES (?, ?, ?, ?, 'ai-route-v2', ?, 'INK_BUDGET', 3, NULL, NULL, NULL, NULL, ?)
                """,
                routeId,
                new UUID(0L, routeId).toString(),
                readerId,
                bookId,
                purpose,
                createdAt);
    }

    /** 완료·피드백까지 채운 경로. 상세가 두 시각과 rating 을 그대로 돌려주는지 보려고 나눠 둔다. */
    private void 완료한_경로를_생성한다(long readerId, long routeId, String purpose) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route
                    (id, generation_id, reader_id, book_id, content_version, normalized_purpose,
                     request_type, max_additional_ink, depth, completed_at, feedback, feedback_at,
                     created_at)
                VALUES (?, ?, ?, ?, 'ai-route-v2', ?, 'INK_BUDGET', 3, NULL,
                        '2026-08-03 11:00:00.123456', 'HELPFUL', '2026-08-03 12:00:00.123456',
                        '2026-08-01 09:00:00.123456')
                """,
                routeId,
                new UUID(0L, routeId).toString(),
                readerId,
                BOOK_ID,
                purpose);
    }

    private void 경로를_여러_건_생성한다(int count, String createdAt) {
        IntStream.rangeClosed(1, count)
                .forEach(index -> 경로를_생성한다(
                        READER_ID,
                        ROUTE_ID_BASE + index,
                        BOOK_ID,
                        "재무제표 읽기 " + index,
                        createdAt));
    }

    private void 경로_항목을_생성한다(
            long routeId,
            long itemId,
            long bookPageId,
            int position,
            String relevance,
            boolean prerequisite,
            String role,
            String openedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route_item
                    (id, route_id, book_id, book_page_id, position, relevance, prerequisite,
                     role, opened_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                itemId,
                routeId,
                BOOK_ID,
                bookPageId,
                position,
                relevance,
                prerequisite,
                role,
                openedAt);
    }

    private void 현재_경로로_지정한다(long readerId, long bookId, long routeId) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_current (reader_id, book_id, route_id, updated_at)
                VALUES (?, ?, ?, '2026-08-03 09:00:00.123456')
                """,
                readerId,
                bookId,
                routeId);
    }

    private void 대여한다(
            long readerId, long rentalId, long bookPageId, String rentedAt, String expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                rentalId,
                readerId,
                bookPageId,
                rentedAt,
                expiresAt);
    }

    private void 소장한다(long readerId, long ownershipId, long bookId) {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', ?, '2026-08-01 08:00:00.000000',
                        '2026-08-01 08:00:00.000000')
                """,
                ownershipId,
                readerId,
                bookId,
                new UUID(1L, ownershipId).toString(),
                BOOK_PRICE_WON);
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (id, reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?, '2026-08-01 08:00:00.000000')
                """,
                ownershipId,
                readerId,
                bookId,
                ownershipId);
    }

    private void 잔액을_변경한다(long readerId, int balance) {
        jdbcTemplate.update(
                "UPDATE ink_account SET balance = ? WHERE reader_id = ?", balance, readerId);
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(new AuthenticatedReader(readerId), null, "ROLE_USER");
    }
}
