# Settlement 검증 및 성능 리포트

## 책임과 Freeze 범위

```text
EXECUTED sell_order
  -> [매도 처리 도메인] PROVISIONAL krw_exchange 생성
  -> [Settlement 도메인] PROVISIONAL -> FINALIZED
```

Settlement Batch는 이미 생성된 `PROVISIONAL krw_exchange`를 확정산한다. 현재 생산 코드에는 `INSERT krw_exchange`가 없으므로 가환전 생성 책임은 매도 처리 도메인 또는 병합 대상 브랜치에서 확정한다. 이후에는 검증에서 발견된 오류만 수정하며, 새 기능은 추가하지 않는다.

## 요약

Settlement는 `PROVISIONAL` 환전을 대상으로 Batch Snapshot을 만들고, 확정산 처리 및 재처리 이력을 관리한다. 실제 MariaDB 운영 DDL에서 컬럼·제약조건과 Snapshot 조회를 검증했고, Batch 재처리 이력, runId 정합성, 동일 업무일 Batch 생성 및 동일 Item Retry의 동시성 제어를 검증했다. 실제 `SettlementTransactionExecutor`까지 포함한 금액 중복 반영 검증과 처리량 측정은 아직 진행하지 않았다.

## 실행 환경

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-08-09 |
| 기준 커밋 | `d2f1720` |
| Java | OpenJDK 17.0.19 |
| CPU / 메모리 | 12 vCPU / 15 GiB |
| MariaDB | 11.8.8 (`mariadb:11.8`, localhost:3307) |
| 외부 환율 API | 미사용 — Mapper 통합 테스트 |

## 실제 MariaDB 검증 결과

| 항목 | 결과 |
| --- | --- |
| `sell_order.settlement_fx_rate` | `DECIMAL(15,4)` 확인 |
| 원화 금액 컬럼 | `account.amount`, `krw_exchange.final_amount`, `left_amount.cur_amount` 모두 `DECIMAL(15,0)` 확인 |
| 제약조건 | `uk_left_amount_exchange_id`, `uk_settlement_batch_run_id` 적용 확인 |
| Mapper 통합 테스트 | `SettlementMariaDbMapperIntegrationTest` 성공 1건 |

테스트는 실제 운영 DDL에 `PROVISIONAL` 환전 대상을 준비하고, Batch Snapshot 생성 및 `settlement_fx_rate` 매핑을 검증한다.

```text
tests=1, failures=0, errors=0, skipped=0
```

## 실패 Batch Retry 통합 테스트

| 항목 | 결과 |
| --- | --- |
| 최초 대상 | 3건 |
| 1차 SUCCESS / FAILED | 2건 / 1건 |
| 새 Retry Item | 1건 |
| 기존 SUCCESS 재처리 | 0건 |
| Retry 이후 SUCCESS / FAILED | 3건 / 0건 |
| `account.amount` 중복 반영 | 미측정 |
| `left_amount` 중복 생성 | 미측정 |
| 최종 Batch 상태 | `COMPLETED` |
| `DB Batch runId == Retry Job runId` | true |

시나리오는 `SUCCESS / FAILED / SUCCESS -> Batch Retry -> COMPLETED`다. 성공한 두 건은 새 Retry Item을 만들지 않아야 하고, 실패한 한 건만 새 이력을 만들어야 한다.

## 동시성 검증

| 테스트 | 검증 범위 | 실제 결과 |
| --- | --- | --- |
| CT-1 동일 업무일 Batch | 동시 요청 10건의 Batch 중복 생성 방지 | Batch 생성 1, 중복 0 |
| CT-2 동일 Item Retry | 동시 요청 2건의 Retry Item 중복 생성 방지 | Retry Item 생성 1 |
| IT-4 Retry Executor 정상 반영 | Retry Item의 Executor 금융 반영 | `FINALIZED`, amount 변경 1회, `left_amount` 1건 |
| CT-2-2 동일 Item Retry End-to-End | 동시 Retry 이후 금액·잔여금 중복 반영 방지 | 미검증 |
| CT-3 FAILED 전이 | 동시 실패 상태 전이의 멱등 처리 | 실제 전이 1, 후속 요청 1 |

## 처리량 측정 계획

외부 환율 Provider는 동일 응답 Stub으로 고정한다. Batch가 `RUNNING`으로 전환된 시점부터 최종 상태가 될 때까지를 측정 범위로 한다.

| 대상 건수 | Warm-up | 실측 | 평균(ms) | P95(ms) | 최소(ms) | 최대(ms) | 처리량(items/s) | 상태 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 100 | 5 | 20 | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 | 예정 |
| 1,000 | 5 | 20 | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 | 예정 |
| 5,000 | 5 | 20 | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 | 예정 |

## 환율 Cache 검증 계획

현재 Cache 범위는 Batch 전체가 아니라 Tasklet의 100건 페이지다. 따라서 포트폴리오에는 **“100건 처리 페이지 내 동일 통화·기준일 환율 캐싱”**으로 표현한다.

| 조건 | 기대 Provider 호출 수 | 상태 |
| --- | ---: | --- |
| 100개 Item, 동일 USD·동일 기준일 | 1 | 단위 테스트로 검증됨, 실측 예정 |
| 100개 Item, 통화·기준일 혼합 | 고유 `(통화, 기준일)` 수 | 실측 예정 |

## 포트폴리오 문장

> `runId`를 Batch 실행 세대 식별자로 사용하고 업무 DB와 Spring Batch Job 파라미터의 일치 여부를 검증했습니다. 또한 Batch 상태 전이를 단일 Updater로 통합하고, 중복된 비동기 실패 경로에서도 `RUNNING -> FAILED` 전이를 멱등적으로 처리하도록 설계했습니다.

> 실제 MariaDB 환경에서 동일 업무일 Batch 생성 요청 10건을 동시에 실행해 Batch가 1건만 생성됨을 검증하고, 동일 실패 Item에 대한 동시 Retry 요청 2건에서도 Retry Item이 1건만 생성됨을 확인했습니다. 실패 Batch 재처리 시 실패한 Item만 새 이력으로 생성하고, runId를 새로운 실행 세대로 갱신한 뒤 최종 `COMPLETED` 전환까지 검증했습니다.

> 실제 금액 중복 반영 검증은 `SettlementTransactionExecutor`를 포함한 Retry 통합 테스트 후 확정합니다.
