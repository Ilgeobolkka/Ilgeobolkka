package com.example.ilgeobolkka.demo;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class DemoDataSeedRunnerTest {

    private static final String DEMO_PASSWORD = "Demo-password1!";

    @Mock private DemoDataSeeder demoDataSeeder;
    @Mock private Environment environment;

    @Test
    void 비밀번호가_비어_있으면_시드를_실행하지_않는다() throws Exception {
        when(environment.getProperty("DEMO_VALIDATION_PASSWORD", "")).thenReturn(" ");
        DemoDataSeedRunner runner = new DemoDataSeedRunner(demoDataSeeder, environment);

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(demoDataSeeder);
    }

    @Test
    void 비밀번호가_주입되면_시드를_실행한다() throws Exception {
        when(environment.getProperty("DEMO_VALIDATION_PASSWORD", "")).thenReturn(DEMO_PASSWORD);
        DemoDataSeedRunner runner = new DemoDataSeedRunner(demoDataSeeder, environment);

        runner.run(new DefaultApplicationArguments());

        verify(demoDataSeeder).seed(DEMO_PASSWORD);
    }

    @Test
    void 자동_시드는_prod가_없는_local_demo_프로필에서만_등록된다() {
        assertAll(
                () -> assertTrue(자동_시드가_등록된다("local")),
                () -> assertTrue(자동_시드가_등록된다("demo")),
                () -> assertFalse(자동_시드가_등록된다("test")),
                () -> assertFalse(자동_시드가_등록된다("prod", "local")),
                () -> assertFalse(자동_시드가_등록된다("prod", "demo")));
    }

    @Test
    void prod가_포함되면_모든_시드_컴포넌트를_등록하지_않는다() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod", "demo", "test");
            context.register(DemoBookCatalog.class, DemoDataSeeder.class, DemoDataSeedRunner.class);
            context.refresh();

            assertAll(
                    () -> assertTrue(context.getBeansOfType(DemoBookCatalog.class).isEmpty()),
                    () -> assertTrue(context.getBeansOfType(DemoDataSeeder.class).isEmpty()),
                    () -> assertTrue(context.getBeansOfType(DemoDataSeedRunner.class).isEmpty()));
        }
    }

    @Test
    void 비밀번호_원문을_String_필드에_보관하지_않는다() {
        assertTrue(
                Arrays.stream(DemoDataSeedRunner.class.getDeclaredFields())
                        .noneMatch(field -> field.getType().equals(String.class)));
    }

    private boolean 자동_시드가_등록된다(String... profiles) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            context.registerBean(DemoDataSeeder.class, () -> demoDataSeeder);
            context.register(DemoDataSeedRunner.class);
            context.refresh();
            return !context.getBeansOfType(DemoDataSeedRunner.class).isEmpty();
        }
    }
}
