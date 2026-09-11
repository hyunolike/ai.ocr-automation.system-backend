package com.ocr.automation.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 서비스 간 호출({@code /internal})을 공유 토큰으로 인증한다.
 *
 * <p>이것은 <b>2차 방어</b>다. 1차는 네트워크다 — {@code /internal} 은 방화벽이나
 * 인그레스 규칙으로 외부에서 닿지 않게 막아야 하며, 이 토큰은 그 차단이 잘못됐거나
 * 내부망 안에서 잘못된 호출이 올 때를 위한 것이다.
 *
 * <p>비교는 {@link MessageDigest#isEqual} 로 한다. {@code String.equals} 는 첫 불일치에서
 * 멈추므로 응답 시간 차이로 토큰을 한 글자씩 알아낼 수 있다.
 */
@RequiredArgsConstructor
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String ROLE = "ROLE_INTERNAL";

    private final String expectedToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (StringUtils.hasText(presented) && matches(presented)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "internal", null, List.of(new SimpleGrantedAuthority(ROLE)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String presented) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }
}
