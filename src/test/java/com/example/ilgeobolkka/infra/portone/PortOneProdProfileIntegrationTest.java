package com.example.ilgeobolkka.infra.portone;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.ink.controller.InkPurchaseController;
import com.example.ilgeobolkka.ink.facade.InkPurchaseFacade;
import com.example.ilgeobolkka.ink.service.InkPurchaseService;
import com.example.ilgeobolkka.ownership.controller.OwnershipHistoryController;
import com.example.ilgeobolkka.ownership.controller.OwnershipPaymentController;
import com.example.ilgeobolkka.ownership.facade.OwnershipHistoryFacade;
import com.example.ilgeobolkka.ownership.facade.OwnershipPaymentFacade;
import com.example.ilgeobolkka.webhook.controller.PortOneWebhookController;
import com.example.ilgeobolkka.webhook.facade.PortOneWebhookFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import io.portone.sdk.server.payment.PaymentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
    "portone.payment.enabled=true",
    "portone.payment.store-id=store-test",
    "portone.payment.channel-key=channel-test",
    "portone.payment.api-secret=test-api-secret",
    "portone.payment.webhook-secret=test-webhook-secret"
})
@ActiveProfiles({"test", "prod"})
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class PortOneProdProfileIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 운영_프로필에서는_활성화_설정이_있어도_결제_API와_PortOne_클라이언트를_노출하지_않는다() {
        assertAll(
                () -> assertTrue(applicationContext
                        .getBeansOfType(InkPurchaseController.class)
                        .isEmpty()),
                () -> assertTrue(applicationContext
                        .getBeansOfType(InkPurchaseFacade.class)
                        .isEmpty()),
                () -> assertTrue(applicationContext
                        .getBeansOfType(InkPurchaseService.class)
                        .isEmpty()),
                () -> assertTrue(applicationContext
                        .getBeansOfType(OwnershipPaymentController.class)
                        .isEmpty()),
                () -> assertTrue(applicationContext
                        .getBeansOfType(OwnershipPaymentFacade.class)
                        .isEmpty()),
                () -> assertTrue(applicationContext
                        .getBeansOfType(PortOnePaymentGateway.class)
                        .isEmpty()),
                () -> assertTrue(applicationContext
                        .getBeansOfType(PortOneWebhookController.class)
                        .isEmpty()),
                () -> assertTrue(applicationContext
                        .getBeansOfType(PortOneWebhookFacade.class)
                        .isEmpty()),
                () -> assertTrue(applicationContext
                        .getBeansOfType(PortOneWebhookVerifier.class)
                        .isEmpty()),
                () -> assertTrue(applicationContext
                        .getBeansOfType(PaymentClient.class)
                        .isEmpty()));
    }

    @Test
    void 운영_프로필에서도_소장_결제_내역_조회_API는_노출된다() {
        assertAll(
                () -> assertEquals(
                        1,
                        applicationContext.getBeansOfType(OwnershipHistoryController.class).size()),
                () -> assertEquals(
                        1,
                        applicationContext.getBeansOfType(OwnershipHistoryFacade.class).size()));
    }
}
