package com.ocr.automation.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocr.automation.backend.document.web.dto.ErrorResponse;
import com.ocr.automation.backend.security.apikey.ApiKeyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 인증·인가 배치.
 *
 * <p>세 갈래를 각각 다른 수단으로 막는다.
 *
 * <table>
 *   <tr><th>경로</th><th>수단</th><th>주체</th></tr>
 *   <tr><td>{@code /api/**}</td><td>API 키</td><td>외부 클라이언트</td></tr>
 *   <tr><td>{@code /internal/**}</td><td>공유 토큰</td><td>스케줄러</td></tr>
 *   <tr><td>actuator</td><td>별도 포트</td><td>운영</td></tr>
 * </table>
 *
 * <p>그 밖의 경로는 모두 거절한다. 새 컨트롤러를 추가했는데 여기 규칙을 넣지 않으면
 * 열리는 것이 아니라 막힌다 — 실수로 열리는 쪽보다 실수로 막히는 쪽이 낫다.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void warnOnDevToken() {
        if (securityProperties.usesDevToken()) {
            log.warn("""
                    
                    ================================================================
                     내부 토큰이 개발용 기본값입니다. /internal 이 사실상 열려 있습니다.
                     운영에서는 ocr.security.internal-token 을 반드시 바꾸세요.
                    ================================================================
                    """);
        }
    }

    /**
     * actuator 는 별도 포트(management.server.port)에서 열린다.
     * 공개 포트에 노출되지 않으므로 인증을 걸지 않는다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /** H2 콘솔은 local 프로파일에서만 켜진다. 켜져 있을 때만 이 체인이 등록된다. */
    @Bean
    @Order(2)
    @ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/h2-console/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                // 토큰 기반이라 세션이 필요 없다. 세션을 만들면 그 자체가 공격면이 된다.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .anonymous(Customizer.withDefaults())
                .addFilterBefore(new InternalTokenAuthenticationFilter(securityProperties.internalToken()),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyService),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/**")
                        .hasAuthority(InternalTokenAuthenticationFilter.ROLE)
                        .requestMatchers("/api/**")
                        .hasAuthority(ApiKeyAuthenticationFilter.ROLE)
                        // 규칙에 없는 경로는 열지 않는다
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .build();
    }

    /**
     * 인증 실패 응답.
     *
     * <p>보안 필터는 MVC 앞단이라 {@code @RestControllerAdvice} 가 잡지 못한다.
     * 그래서 오류 형식을 여기서 직접 맞춘다.
     */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) -> writeError(response, HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "유효한 인증 정보가 필요합니다");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> writeError(response, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "이 리소스에 접근할 권한이 없습니다");
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response,
                            HttpStatus status, String code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message));
    }
}
