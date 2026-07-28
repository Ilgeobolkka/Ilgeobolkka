package com.example.ilgeobolkka.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
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
    void 예기치_못한_오류는_원문을_숨기고_공통_서버_오류로_응답한다() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
                .andExpect(content().string(not(containsString("PORTONE_API_SECRET=exposed"))));
    }

    @Test
    void Controller의_인가_실패는_공통_접근_오류로_응답한다() throws Exception {
        mockMvc.perform(get("/test/errors/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(content().string(not(containsString("viewerSessionId=exposed"))));
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
    }

    record TestRequest(@NotBlank String name) {
    }
}
