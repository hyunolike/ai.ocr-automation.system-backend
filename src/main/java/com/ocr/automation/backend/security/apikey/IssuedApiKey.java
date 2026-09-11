package com.ocr.automation.backend.security.apikey;

/**
 * 방금 발급한 키. <b>평문은 이 순간에만 존재한다.</b>
 *
 * @param plaintext 호출자에게 한 번만 보여줄 평문 키. 저장되지 않는다
 * @param prefix    이후 이 키를 가리킬 때 쓸 식별용 앞부분
 * @param ownerId   이 키가 대변하는 소유자
 */
public record IssuedApiKey(String plaintext, String prefix, String ownerId) {
}
