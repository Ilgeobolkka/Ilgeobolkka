package com.example.ilgeobolkka.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import com.example.ilgeobolkka.reading.exception.ReadingSessionNotFoundException;
import com.example.ilgeobolkka.reading.exception.ViewerSessionReplacedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 유효한_JSON_요청은_그대로_처리한다() throws Exception {
        mockMvc.perform(post("/test/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"reader"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void DTO_검증_실패는_공통_입력_오류로_응답한다() throws Exception {
        mockMvc.perform(post("/test/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    void 읽을_수_없는_JSON은_공통_입력_오류로_응답한다() throws Exception {
        mockMvc.perform(post("/test/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    void 지원하지_않는_Content_Type은_공통_입력_오류로_응답한다() throws Exception {
        mockMvc.perform(post("/test/errors")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("reader"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    void 쿼리_파라미터_검증_실패는_공통_입력_오류로_응답한다() throws Exception {
        mockMvc.perform(get("/test/errors/page").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    void 쿼리_파라미터_형식_오류는_공통_입력_오류로_응답한다() throws Exception {
        mockMvc.perform(get("/test/errors/page").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    void 예기치_못한_오류는_원문을_숨기고_공통_서버_오류로_응답한다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
                .andExpect(content().string(not(containsString("PORTONE_API_SECRET=exposed"))));

        assertTrue(output.getOut().contains("errorCode=INTERNAL_SERVER_ERROR"));
        assertTrue(output.getOut().contains("exceptionType=IllegalStateException"));
        assertFalse(output.getOut().contains("PORTONE_API_SECRET=exposed"));
    }

    @Test
    void Controller의_인가_실패는_공통_접근_오류로_응답한다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/errors/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(content().string(not(containsString("viewerSessionId=exposed"))));

        assertTrue(output.getOut().contains("errorCode=ACCESS_DENIED"));
        assertTrue(output.getOut().contains("exceptionType=AccessDeniedException"));
        assertFalse(output.getOut().contains("viewerSessionId=exposed"));
    }

    @Test
    void 잉크_부족은_구매_안내_오류로_응답한다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/errors/insufficient-ink"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INK"))
                .andExpect(jsonPath("$.message").value("잉크가 부족합니다. 잉크를 충전해 주세요."));

        assertTrue(output.getOut().contains("errorCode=INSUFFICIENT_INK"));
    }

    @Test
    void 교체된_뷰어_세션은_충돌_오류로_응답하고_내부_메시지를_숨긴다(CapturedOutput output)
            throws Exception {
        mockMvc.perform(get("/test/errors/replaced-viewer"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIEWER_SESSION_REPLACED"))
                .andExpect(jsonPath("$.message").value("새 뷰어로 교체된 열람 세션입니다."))
                .andExpect(content().string(not(containsString("교체되었습니다"))));

        assertTrue(output.getOut().contains("errorCode=VIEWER_SESSION_REPLACED"));
        assertFalse(output.getOut().contains("교체되었습니다"));
    }

    @Test
    void 현재_열람_세션_없음은_공통_리소스_없음_오류로_응답한다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/errors/missing-reading-session"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));

        assertTrue(output.getOut().contains("errorCode=RESOURCE_NOT_FOUND"));
    }

    @RestController
    @RequestMapping("/test/errors")
    public static class TestController {

        @PostMapping
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/page")
        void page(@RequestParam @Min(1) int page) {
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("PORTONE_API_SECRET=exposed");
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("viewerSessionId=exposed");
        }

        @GetMapping("/insufficient-ink")
        void insufficientInk() {
            throw new InsufficientInkException();
        }

        @GetMapping("/replaced-viewer")
        void replacedViewer() {
            throw new ViewerSessionReplacedException(7L);
        }

        @GetMapping("/missing-reading-session")
        void missingReadingSession() {
            throw new ReadingSessionNotFoundException(7L);
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
