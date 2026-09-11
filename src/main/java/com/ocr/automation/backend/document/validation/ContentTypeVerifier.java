package com.ocr.automation.backend.document.validation;

/**
 * 업로드된 내용이 <b>스스로 주장하는 형식이 맞는지</b> 확인한다.
 *
 * <p>{@code Content-Type} 헤더는 클라이언트가 보낸 문자열일 뿐이다. 확장자를
 * {@code .png} 로 바꾸고 헤더만 {@code image/png} 로 붙이면 무엇이든 통과한다.
 * 그래서 파일 내용 자체를 본다.
 */
public interface ContentTypeVerifier {

    /**
     * @throws ContentMismatchException 선언된 형식과 실제 내용이 맞지 않을 때
     */
    void verify(String declaredContentType, byte[] content);
}
