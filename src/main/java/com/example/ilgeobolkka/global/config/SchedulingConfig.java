package com.example.ilgeobolkka.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 전역 스케줄링 스위치.
 *
 * <p>도메인 클래스가 들고 있으면 그 클래스를 지우거나 패키지를 들어낼 때 다른 도메인의
 * {@code @Scheduled} 가 조용히 멈춘다. 전역 동작은 전역 설정이 들고 있어야 한다.
 *
 * <p>{@code content-import} 배치에서는 켜지 않는다. {@code @EnableScheduling} 이 만드는 스케줄러
 * 스레드는 비데몬이라, 켜 두면 적재를 마친 배치 JVM 이 할 일 없이 살아 있어 종료되지 않는다.
 * 배치는 적재하고 끝나야 하고, 도는 동안 유지보수 배치가 같은 DB 를 함께 건드릴 이유도 없다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!content-import")
@EnableScheduling
public class SchedulingConfig {}
