-- API 키. 공개 API 의 호출자가 누구인지 증명하는 수단이다.
--
-- 평문은 저장하지 않는다. 발급 시 한 번만 보여주고, 이후에는 해시로만 대조한다.
-- 저장소가 통째로 유출돼도 키 자체는 복원되지 않는다.
CREATE TABLE api_keys
(
    id           BIGSERIAL PRIMARY KEY,

    -- 식별용 앞부분(평문). 운영자가 "어느 키인지" 구분하는 용도이며 인증에는 쓰지 않는다.
    key_prefix   VARCHAR(16)              NOT NULL,

    -- SHA-256 hex. 인증은 이 값의 일치로만 판단한다.
    key_hash     VARCHAR(64)              NOT NULL,

    owner_id     VARCHAR(64)              NOT NULL,
    label        VARCHAR(100),

    enabled      BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_api_keys_key_hash UNIQUE (key_hash)
);

-- 소유자별 키 목록 조회
CREATE INDEX idx_api_keys_owner_id ON api_keys (owner_id);
