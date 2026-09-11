package com.ocr.automation.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 보안 설정.
 *
 * @param internalToken 스케줄러가 {@code /internal} 을 부를 때 제시하는 공유 토큰
 */
@ConfigurationProperties(prefix = "ocr.security")
public record SecurityProperties(@DefaultValue(SecurityProperties.DEV_TOKEN) String internalToken) {

    /**
     * 개발 편의용 기본 토큰.
     *
     * <p>이름 자체를 경고로 삼았다. 로그나 설정에서 이 값이 보이면 보호가 없는 상태다.
     * 기동 시 이 값이 쓰이면 경고를 남긴다.
     */
    public static final String DEV_TOKEN = "local-dev-only-token";

    public boolean usesDevToken() {
        return DEV_TOKEN.equals(internalToken);
    }
}
