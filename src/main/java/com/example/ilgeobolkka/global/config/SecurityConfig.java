package com.example.ilgeobolkka.global.config;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.example.ilgeobolkka.global.security.ApiSecurityErrorHandler;

@Configuration
public class SecurityConfig {

    private static final RequestMatcher SIGNUP = pathPattern(HttpMethod.POST, "/api/auth/signup");
    private static final RequestMatcher LOGIN = pathPattern(HttpMethod.POST, "/api/auth/login");
    private static final RequestMatcher PUBLIC_BOOK_LIST = pathPattern(HttpMethod.GET, "/api/books");
    private static final RequestMatcher PUBLIC_BOOK_DETAIL = pathPattern(HttpMethod.GET, "/api/books/{bookId}");
    private static final RequestMatcher PORTONE_WEBHOOK = pathPattern(HttpMethod.POST, "/api/webhooks/portone");
    private static final RequestMatcher API = pathPattern("/api/**");

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiSecurityErrorHandler securityErrorHandler) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(SIGNUP, LOGIN, PUBLIC_BOOK_LIST, PUBLIC_BOOK_DETAIL, PORTONE_WEBHOOK).permitAll()
                        .requestMatchers(API).authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers(PORTONE_WEBHOOK))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .requestCache(requestCache -> requestCache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
