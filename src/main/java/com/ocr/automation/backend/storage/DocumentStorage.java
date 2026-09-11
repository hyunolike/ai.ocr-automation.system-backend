package com.ocr.automation.backend.storage;

/**
 * 원본 문서 보관소 포트.
 *
 * <p>현재 구현은 로컬 파일시스템이다. S3 로 옮길 때는 이 인터페이스를 구현한
 * 어댑터만 추가하면 되고, 도메인과 서비스 계층은 손대지 않는다.
 * 그래서 반환값을 "경로"가 아니라 <b>스토리지 키</b>로 정의했다.
 */
public interface DocumentStorage {

    /**
     * 문서를 저장하고 스토리지 키를 돌려준다.
     *
     * @return 이후 조회/삭제에 사용할 키 (구현체 내부 형식)
     */
    String store(String originalFilename, byte[] content);

    /** 키로 문서 내용을 읽는다. */
    byte[] read(String storageKey);

    /** 키에 해당하는 문서를 지운다. 없으면 조용히 넘어간다. */
    void delete(String storageKey);

    boolean exists(String storageKey);
}
