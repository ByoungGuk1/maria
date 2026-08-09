# Account · MyData 동기화 검증 및 성능 리포트

## 요약

Account 도메인은 한도 변경의 Compare-And-Set(CAS)과 Redis 기반 MyData 재시도 큐를 사용한다. 이번 기록에서는 실제 Redis에서 Lock Ownership과 최신 taskToken 보호를 검증했다. 처리량 및 Before/After 호출량 비교는 아직 측정하지 않았으므로 수치를 기재하지 않는다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-08-10 |
| 기준 커밋 | `d2f1720` |
| Java | OpenJDK 17.0.19 |
| CPU / 메모리 | 12 vCPU / 15 GiB |
| Redis | 8.8.0 (`shinhan-redis`, localhost:6379) |
| MariaDB | 11.8.8 (`mariadb:11.8`, localhost:3307) |

## 실제 Redis 통합 테스트 결과

테스트 클래스: `com.app.maria.performance.domain.account.RedisMydataSyncTaskRepositoryIntegrationTest`

| 시나리오 | 결과 | 검증 내용 |
| --- | --- | --- |
| Lock Ownership | 성공 | TTL 만료 뒤 새 Worker가 획득한 lock을 이전 Worker의 token으로 해제할 수 없음 |
| 최신 taskToken 보호 | 성공 | 이전 Worker의 `complete()`가 새로 enqueue된 task와 schedule을 삭제할 수 없음 |

```text
tests=2, failures=0, errors=0, skipped=0
```

실행 명령:

```bash
./gradlew test \
  --tests com.app.maria.performance.domain.account.RedisMydataSyncTaskRepositoryIntegrationTest \
  -Daccount.redis.enabled=true \
  -Daccount.redis.host=127.0.0.1 \
  -Daccount.redis.port=6379
```

## MariaDB Lost Update 동시성 테스트

검증 대상은 `UPDATE account ... AND limit_amount = expectedCurrentLimit` 조건이다. Mockito 결과가 아니라, 서로 다른 커넥션을 사용하는 실제 MariaDB 테스트로 기록한다.

| 항목 | 결과 |
| --- | --- |
| 초기 `limit_amount` | 30,000,000 |
| 요청 A | `expected=30,000,000 -> new=35,000,000` |
| 요청 B | `expected=30,000,000 -> new=40,000,000` |
| 성공 요청 | 1건 |
| 충돌 요청 | 1건 |
| 최종 DB 값 | 35,000,000 또는 40,000,000 |
| Lost Update | 0건 |

실제 MariaDB 동시성 테스트에서 성공 1건·충돌 1건을 확인했고, stale 요청은 조건부 UPDATE에 의해 차단됐다.

## MyData 동기화 성능 측정 계획

| 비교 | 대상 | 기록 항목 | 상태 |
| --- | --- | --- | --- |
| Before | OPENED 계좌 10,000건 전체 Polling | DB 조회 대상 수, MyData API 호출 수, 전체 시간 | 미측정 |
| After | Redis due task 100건 | Redis 조회 수, MyData API 호출 수, 전체 시간 | 미측정 |

Warm-up 5회, 실측 20회로 평균·P95·최소·최대를 함께 기록한다. 외부 MyData API는 Stub으로 고정해 네트워크 변수를 제외한다.

## 설계 한계

```text
DB Commit 성공
-> Redis enqueue 실패
-> MyData 동기화 Task 유실 가능
```

현재는 Redis Retry Queue로 재시도를 관리한다. DB와 Redis 등록 사이의 원자성은 보장하지 않으므로, 규모 확장 시 Transactional Outbox와 Kafka를 검토 대상으로 남긴다.

## 포트폴리오 문장

> Redis Lua Script로 lock token과 task token을 비교한 뒤에만 삭제·완료 처리를 수행하도록 구현해, TTL 만료 후 이전 Worker가 새 작업을 제거하는 경쟁 상태를 방지했습니다. 실제 Redis 통합 테스트 2건과 MariaDB Lost Update 동시성 테스트를 통해 Lock Ownership, 최신 작업 보호, CAS 조건부 UPDATE의 stale 요청 차단을 검증했습니다.
