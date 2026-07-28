package com.example.ilgeobolkka.demo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;

@ExtendWith(MockitoExtension.class)
class DemoDataSeedRunnerTest {

    private static final String DEMO_PASSWORD = "Demo-password1!";

    @Mock private DemoDataSeeder demoDataSeeder;

    @Test
    void 비밀번호가_비어_있으면_시드를_실행하지_않는다() throws Exception {
        DemoDataSeedRunner runner = new DemoDataSeedRunner(demoDataSeeder, " ");

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(demoDataSeeder);
    }

    @Test
    void 비밀번호가_주입되면_시드를_실행한다() throws Exception {
        DemoDataSeedRunner runner = new DemoDataSeedRunner(demoDataSeeder, DEMO_PASSWORD);

        runner.run(new DefaultApplicationArguments());

        verify(demoDataSeeder).seed(DEMO_PASSWORD);
    }

    @Test
    void 자동_시드는_local_demo_프로필에서만_등록된다() {
        Profile runnerProfile = DemoDataSeedRunner.class.getAnnotation(Profile.class);
        Profile seederProfile = DemoDataSeeder.class.getAnnotation(Profile.class);

        assertAll(
                () -> assertNotNull(runnerProfile),
                () ->
                        assertArrayEquals(
                                new String[] {"local", "demo"}, runnerProfile.value()),
                () -> assertNotNull(seederProfile),
                () ->
                        assertArrayEquals(
                                new String[] {"local", "demo", "test"},
                                seederProfile.value()));
    }
}
