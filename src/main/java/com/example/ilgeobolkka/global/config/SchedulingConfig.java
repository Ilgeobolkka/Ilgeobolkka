package com.example.ilgeobolkka.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 전역 스케줄링 스위치.
 *
 * <p>도메인 클래스가 들고 있으면 그 클래스를 지우거나 패키지를 들어낼 때 다른 도메인의
 * {@code @Scheduled} 가 조용히 멈춘다. 전역 동작은 전역 설정이 들고 있어야 한다.
 * 일회성 비웹 프로필은 작업 뒤 scheduler thread가 남지 않도록 전역 스케줄링에서 제외한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!performance-seed & !content-import")
public class SchedulingConfig {}
