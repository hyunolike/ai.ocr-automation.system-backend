-- 문서에 소유자를 도입한다.
--
-- 공개 API 의 모든 조회에 소유자 조건이 붙게 되므로, 인덱스도 소유자를 선두로 하는
-- 것으로 바꾼다. 다만 스케줄러가 집는 "시스템 전체의 대기 문서" 조회는 소유자를
-- 가리지 않으므로 idx_documents_status_uploaded_at 은 그대로 둔다.

ALTER TABLE documents ADD COLUMN owner_id VARCHAR(64) NOT NULL DEFAULT 'legacy';

-- 기본값은 기존 행을 채우기 위한 것일 뿐이다. 남겨두면 소유자 없이 들어온 행이
-- 조용히 'legacy' 가 되어 격리가 깨지므로 바로 떼어낸다.
ALTER TABLE documents ALTER COLUMN owner_id DROP DEFAULT;

CREATE INDEX idx_documents_owner_status_uploaded ON documents (owner_id, status, uploaded_at);
CREATE INDEX idx_documents_owner_checksum ON documents (owner_id, checksum);

-- 중복 판정을 소유자 범위로 좁혔으므로 전역 체크섬 인덱스는 더 이상 쓰이지 않는다.
DROP INDEX idx_documents_checksum;
