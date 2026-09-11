package com.ocr.automation.backend.security;

import com.ocr.automation.backend.security.apikey.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API 키로 호출자를 인증한다.
 *
 * <p>인증에 성공하면 {@code SecurityContext} 의 principal 이 <b>소유자 식별자</b>가 된다.
 * {@link com.ocr.automation.backend.security.apikey.ApiKeyDocumentOwnerResolver} 가
 * 그 값을 꺼내 쓰므로, 컨트롤러는 소유자를 요청에서 읽을 일이 없다.
 *
 * <p>키가 없거나 틀려도 여기서 막지 않는다. 인증 정보를 세우지 않고 통과시키면
 * 뒤의 인가 단계가 거절한다. 어떤 경로가 인증을 요구하는지는 한곳
 * ({@link SecurityConfig})에서만 정하기 위해서다.
 */
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String ROLE = "ROLE_API_CLIENT";

    private static final String BEARER = "Bearer ";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = extractKey(request);
        if (presented != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            apiKeyService.resolveOwner(presented).ifPresent(ownerId -> {
                var authentication = new UsernamePasswordAuthenticationToken(
                        ownerId, null, List.of(new SimpleGrantedAuthority(ROLE)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }

    /** {@code Authorization: Bearer <key>} 와 {@code X-API-Key} 둘 다 받는다. */
    private String extractKey(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER)) {
            return authorization.substring(BEARER.length()).trim();
        }
        String headerKey = request.getHeader(API_KEY_HEADER);
        return StringUtils.hasText(headerKey) ? headerKey.trim() : null;
    }
}
