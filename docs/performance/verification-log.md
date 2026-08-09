# Account · Settlement 검증 작업 로그

## 2026-08-09 ~ 2026-08-10

### 환경

```text
Java: OpenJDK 17.0.19
MariaDB: 11.8.8 (Docker, localhost:3307)
Redis: 8.8.0 (Docker, localhost:6379)
```

### 완료: Account

```text
[PASS] Redis Lock Ownership
- Worker A가 획득했던 lock의 token으로
  TTL 만료 후 Worker B가 획득한 새 lock을 삭제할 수 없는지 검증

[PASS] Redis 최신 taskToken 보호
- Worker A가 claim한 뒤 새 Task B가 enqueue된 경우
  A의 complete()가 B의 task와 schedule을 삭제하지 못하는지 검증

[PASS] MariaDB Lost Update
- 초기 limit_amount: 30,000,000
- 동시 요청: 2
- 성공: 1
- 충돌: 1
- 최종 한도: 35,000,000 또는 40,000,000
- Lost Update: 0
- 근거: UPDATE ... WHERE limit_amount = expectedCurrentLimit
```

### 완료: Settlement

```text
[PASS] MariaDB 운영 DDL 및 Mapper
- settlement_fx_rate DECIMAL(15,4) 확인
- 원화 금액 컬럼 DECIMAL(15,0) 확인
- left_amount.exchange_id UNIQUE 확인
- settlement_batch.run_id UNIQUE 확인
- PROVISIONAL Snapshot 생성 및 settlement_fx_rate 매핑 확인

[PASS] 실패 Item Retry 이력 및 상태 전이
- FAILED Item 1건 -> Retry Item 1건 생성
- Retry 성공 후 최신 Item 기준 Batch COMPLETED 전환
- Retry runId가 Batch DB에 반영되는지 확인

[PASS] 실패 Batch 3건 재처리 이력
- 최초: SUCCESS 2 / FAILED 1
- Retry Item: 1
- 기존 SUCCESS 재처리: 0
- Retry 이후: SUCCESS 3 / FAILED 0
- Batch 상태: COMPLETED
- DB Batch runId == Retry runId: true

[PASS] CT-1 동일 업무일 Batch 동시 실행
- 동시 요청: 10
- 생성 Batch: 1
- 중복 Batch: 0

[PASS] CT-2 동일 Item 동시 Retry
- 동시 요청: 2
- 생성 Retry Item: 1

[PASS] CT-3 FAILED 상태 전이
- 동시 요청: 2
- 실제 RUNNING -> FAILED 전이: 1
- 후속 요청: 이미 FAILED 상태 확인

[PASS] CT-2-2 Retry 금액 정합성
- SettlementTransactionExecutor 실제 실행
- 대상 krw_exchange: FINALIZED
- account.amount: Before/After 변경 확인
- left_amount(exchange_id): 1건 생성 확인
```

### 보류 / 미검증

```text
[TODO] Settlement Retry 후 실제 SettlementTransactionExecutor까지 포함한 금액 정합성
- account.amount 중복 반영: 미검증
- left_amount 중복 생성: 미검증

[TODO] Account Before / After MyData 성능 비교
- OPENED 10,000건 / Sync 대상 100건: 미측정

[TODO] Settlement 100 / 1,000 / 5,000건 성능
- 평균 / P95 / 최소 / 최대 / items/s: 미측정

[TODO] 환율 Cache 실측
- 동일 USD·동일 기준일 100건의 Provider 호출 수: 미측정
```

### 주의 사항

```text
Settlement의 Retry 이력·상태 전이·동시 Retry Item 생성은 실제 MariaDB에서 검증했다.
그러나 CT-2와 3건 Retry 테스트는 실제 환율 조회 및 SettlementTransactionExecutor를 통한
account.amount / left_amount 반영까지 포함하지 않았다.
따라서 금액 중복 반영 0건은 현재 완료 결과로 사용하지 않는다.
```
