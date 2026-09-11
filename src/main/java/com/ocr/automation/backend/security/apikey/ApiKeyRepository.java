package com.ocr.automation.backend.security.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /** 인증 경로. 평문이 아니라 해시로 찾는다. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByOwnerId(String ownerId);
}
