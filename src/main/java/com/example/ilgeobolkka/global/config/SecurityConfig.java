package com.example.ilgeobolkka.global.config;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.example.ilgeobolkka.global.security.ApiSecurityErrorHandler;

@Configuration
public class SecurityConfig {

    private static final RequestMatcher SIGNUP = pathPattern(HttpMethod.POST, "/api/auth/signup");
    private static final RequestMatcher LOGIN = pathPattern(HttpMethod.POST, "/api/auth/login");
    private static final RequestMatcher PUBLIC_BOOK_LIST = pathPattern(HttpMethod.GET, "/api/books");
    private static final RequestMatcher PUBLIC_BOOK_DETAIL = pathPattern(HttpMethod.GET, "/api/books/{bookId}");
    private static final RequestMatcher SMOKE = pathPattern(HttpMethod.GET, "/api/smoke");
    private static final RequestMatcher PORTONE_WEBHOOK = pathPattern(HttpMethod.POST, "/api/webhooks/portone");
    private static final RequestMatcher API = pathPattern("/api/**");

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new SafeRequestCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    LogoutHandler logoutHandler(SecurityContextRepository securityContextRepository) {
        SecurityContextLogoutHandler securityContextLogoutHandler =
                new SecurityContextLogoutHandler();
        securityContextLogoutHandler.setSecurityContextRepository(securityContextRepository);
        return new CompositeLogoutHandler(
                securityContextLogoutHandler,
                new CookieClearingLogoutHandler("JSESSIONID"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiSecurityErrorHandler securityErrorHandler,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(SIGNUP, LOGIN, PUBLIC_BOOK_LIST, PUBLIC_BOOK_DETAIL, SMOKE, PORTONE_WEBHOOK)
                        .permitAll()
                        .requestMatchers(API).authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(PORTONE_WEBHOOK))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .requestCache(requestCache -> requestCache.requestCache(new NullRequestCache()))
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
