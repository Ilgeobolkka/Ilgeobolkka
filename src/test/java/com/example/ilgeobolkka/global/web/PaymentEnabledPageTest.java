package com.example.ilgeobolkka.global.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.config.SecurityConfig;
import com.example.ilgeobolkka.global.security.ApiSecurityErrorHandler;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = CommonPageController.class,
        properties = "portone.payment.enabled=true")
@Import({SecurityConfig.class, ApiSecurityErrorHandler.class, CommonWebModelAdvice.class})
class PaymentEnabledPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 결제_활성화_잉크_화면은_구매_제목과_버튼만_렌더링한다() throws Exception {
        String html = mockMvc.perform(get("/ink").with(authentication(
                        new TestingAuthenticationToken(
                                new AuthenticatedReader(42L), null, "ROLE_USER"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(html.contains("100잉크 구매"));
        assertTrue(html.contains(">1,000원 결제</button>"));
        assertFalse(html.contains("PortOne V2 테스트 채널에서 1,000원 카드 결제를 진행합니다."));
        assertFalse(html.contains("현재 환경에서는 잉크 구매를 사용할 수 없습니다."));
    }
}
