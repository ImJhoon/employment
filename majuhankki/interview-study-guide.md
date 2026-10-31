# 마주한끼 포트폴리오 면접 준비 학습 가이드

## 학습 목표와 시작점

매칭 요청 하나가 제안·확정·채팅방 생성까지 이어지는 흐름을 먼저 이해하고, 그 위에 포트폴리오의 두 핵심 사례를 연결한다. 마주한끼의 사례는 실제 장애를 겪고 고친 경험이 아니라 설계 단계에서 예상한 위험에 대비한 것이므로, "어떤 위험을 예상했고 어떻게 막았으며 무엇으로 확인했는가"를 설명할 수 있어야 한다.

- 대상 포트폴리오: [마주한끼](portfolio-short.md)
- 근거·실행 기록: [작성 메모](notes.md)
- 기준 코드: [`310120e`](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/tree/310120e7ba6de85c372a028935fa2b07c1621c94)
- 첫 학습 주제: 매칭 요청 한 건의 전체 흐름과 요청·제안 상태 변화
- 첫 번째 목표: 그림 없이 매칭 흐름을 2분 안에 설명하기

GRAB 학습 가이드의 트랜잭션·Redis·동시성 개념을 먼저 익혔다면 그 내용을 그대로 이어서 사용한다. 설명할 수 없는 용어나 수치는 따로 표시하고, 코드에서 근거를 확인한 뒤 자신의 말로 정리한다.

## 1. 포트폴리오 학습 순서

### 1단계: 매칭 전체 흐름과 상태 이해하기

매칭 요청 한 건을 기준으로 다음 과정을 코드에서 따라간다.

`매칭 요청 저장 → Valkey 대기 등록 → (커밋 후) 후보 탐색 → 제안 생성 → 수락·거절·취소·만료 → 매칭·채팅방 생성 → (커밋 후) WebSocket 알림`

| 대상 | 상태 |
| --- | --- |
| 매칭 요청 `MatchRequestStatus` | `WAITING`, `CONFIRMING`, `MATCHED`, `CANCELLED`, `EXPIRED` |
| 후보 제안 `MatchProposalStatus` | `PENDING`, `MATCHED`, `REJECTED`, `EXPIRED`, `CANCELLED` |

**먼저 볼 코드**

- `RealtimeMatchRequestService`: 요청 생성·취소, Valkey 예약과 롤백 보상
- `RealtimeMatchProposalSelectionEventHandler`: 요청 커밋 뒤(`AFTER_COMMIT`) 후보 선정 시작
- `BidirectionalCandidateSearchService`, `MatchProposalSelectionService`: 양방향 조건 필터와 후보 선택
- `MatchProposalLifecycleService`: 수락·거절·취소·만료와 매칭·채팅방 생성
- `MatchProposalExpirationScheduler`(기본 1초), `MatchProposalRetryScheduler`(기본 5초): 만료 처리와 후보 재탐색

**설명할 수 있어야 할 질문**

- 요청과 제안은 각각 어떤 사건에서 어떤 상태로 바뀌는가?
- 요청이 `CONFIRMING`이 되는 시점과 다시 `WAITING`으로 돌아가는 경우는 언제인가?
- 사용자 요청, 이벤트 핸들러, 스케줄러 중 어느 것이 각 상태 전이를 일으키는가?
- PostgreSQL, Valkey, WebSocket에는 각각 무엇이 저장되거나 전달되는가?
- 후보 선정과 매칭 결과 알림을 왜 DB 커밋 뒤에 실행하는가?

**학습 방법과 완료 기준**

- 요청과 제안의 상태 다이어그램을 직접 그리고, 화살표마다 일으키는 코드(메서드나 스케줄러)를 적는다.
- 각 단계 옆에 트랜잭션 경계와 저장소 접근(PostgreSQL, Valkey)을 표시한다.
- 그림을 보지 않고 전체 흐름을 2분 안에 설명한다.

### 2단계: 사례 ① 동시성 제어 공부하기

마주한끼에서 가장 깊이 질문받을 사례다. 개념 → 코드 → 테스트 순서로 공부한다.

**먼저 익힐 개념**

- 경쟁 상태, 갱신 손실(lost update)
- PostgreSQL 기본 격리 수준(Read Committed)과 행 잠금(`SELECT ... FOR UPDATE`, `FOR NO KEY UPDATE`)
- 교착 상태(deadlock)의 발생 조건과 잠금 순서 통일
- JPA 영속성 컨텍스트(1차 캐시)와 `EntityManager.refresh`
- 비관적 락, 낙관적 락(`@Version`), 분산 락의 차이
- UNIQUE 제약과 `DataIntegrityViolationException`
- HTTP 409 Conflict와 멱등한 재요청

**코드에서 따라갈 경로**

`MatchProposalInteractionService.decide → MatchProposalLifecycleService.decide → findForUpdate(제안 잠금) → lockProposalRequests(요청 ID 오름차순 잠금) → refreshLockedRequests → 매칭·참여자·채팅방 생성`

**설명할 수 있어야 할 질문**

- 잠금이 없다면 두 사용자가 동시에 수락할 때 어떤 결과가 생길 수 있는가?
- 모든 경로가 "제안 → 요청 ID 오름차순"으로 잠그면 왜 교착 상태를 피할 수 있는가?
- 요청에서 출발하는 대기 취소를 왜 제안 경로로 넘겨 같은 순서로 잠그는가?
- 잠근 뒤에 엔티티를 다시 읽지 않으면 어떤 문제가 생길 수 있는가?
- 행 잠금이 있는데 UNIQUE 제약은 왜 추가로 필요한가?
- 같은 결정을 다시 보내면 왜 오류 대신 기존 결과를 돌려주는가?
- 409를 받은 화면은 무엇을 다시 조회하는가?
- 낙관적 락이나 Valkey 분산 락 대신 비관적 락을 쓴 구조의 장점과 비용은 무엇인가?
- 서버가 여러 대로 늘어나도 이 방식이 동작하는 이유는 무엇인가?
- 잠금 대기 시간 상한(lock timeout)이 없으면 어떤 문제가 생길 수 있는가?

**테스트로 확인할 내용**

`MatchProposalConcurrencyIntegrationTest`의 6개 시나리오를 각각 "동시에 실행하는 두 작업"과 "기대하는 최종 상태"로 설명한다.

| 시나리오 | 동시에 실행하는 작업 | 기대 결과 |
| --- | --- | --- |
| 양쪽 동시 수락 | A 수락 + B 수락 | 매칭 1건, 참여자 2명, 채팅방 1개 |
| 중복 수락 재전송 | 매칭 확정 후 같은 수락 2번 | 매칭·채팅방 수 변화 없음 |
| 경쟁 제안 | 한 요청을 공유하는 두 제안을 동시에 수락 | 하나만 `MATCHED`, 다른 하나는 `CANCELLED` |
| 수락 vs 거절 | 거절 + 수락 | 매칭 없음, 제안 `REJECTED` |
| 수락 vs 취소 | 취소 + 수락 | 매칭 없음, 취소한 쪽만 `CANCELLED` |
| 수락 vs 만료 | 만료 + 수락 | 매칭 없음, 제안 `EXPIRED` |

- `CountDownLatch`로 두 스레드를 동시에 출발시키는 방법을 이해한다.
- 테스트는 실제 PostgreSQL을 사용하지만 HTTP API를 거치지 않고 `MatchProposalLifecycleService`를 직접 호출한다. Valkey와 WebSocket은 mock이다. 따라서 409 변환과 화면 재조회, Valkey 동작은 이 테스트의 검증 범위 밖이다.
- 실행 로그에서 `PESSIMISTIC_WRITE`가 PostgreSQL에서 `FOR NO KEY UPDATE`로 실행된다. `FOR UPDATE`와 무엇이 다른지(다른 트랜잭션이 이 행을 FK로 참조하는 행을 넣을 때 막는지 여부) 설명할 수 있게 한다.
- 실행 일시·환경·결과와 우회한 점(컴파일이 안 되는 테스트 제외, 가짜 `GOOGLE_API_KEY`)은 [작성 메모](notes.md)에서 확인한다.

**학습 방법과 완료 기준**

- 요청 A와 B의 실행 순서를 시간축으로 적고, 어느 쪽이 잠금을 기다리는지 표시한다.
- 잠금이 없을 때와 있을 때의 결과를 나란히 비교해 설명한다.
- 테스트 코드는 AI가 작성했으므로, 6개 시나리오의 준비 데이터·실행·검증을 직접 읽고 설명할 수 있어야 한다.
- 대안 비교는 당시 실제로 검토한 과정이 아니라 사후 비교 분석임을 구분해 답한다.

PostgreSQL 잠금 모드는 [PostgreSQL 명시적 잠금 공식 문서](https://www.postgresql.org/docs/current/explicit-locking.html)에서 확인한다.

### 3단계: 사례 ② DB–Valkey 상태 보정 공부하기

두 저장소를 함께 쓸 때 생기는 불일치와, 기준 상태를 정해 맞추는 방법을 공부한다.

**먼저 익힐 개념**

- Redis(Valkey)의 `SET NX`, TTL, Lua 스크립트의 원자성 범위, GEO
- 이중 쓰기(dual write) 문제: DB와 캐시를 하나의 트랜잭션으로 커밋할 수 없다는 것
- 스프링 트랜잭션 동기화: `afterCommit`, `afterCompletion`, `@TransactionalEventListener(AFTER_COMMIT)`
- 트랜잭션 전파 속성 `REQUIRES_NEW`
- 보상 처리(compensation)와 주기적 보정(reconciliation)
- (다음 단계 대비) Outbox 패턴의 개념

**먼저 볼 코드**

- `RealtimeMatchRequestService`: `SET NX` 예약, 커밋 전 활성화, 롤백 보상
- `RedisRealtimeMatchWaitingStore`: Lua 스크립트, 예약 토큰과 요청 ID 비교
- `RealtimeMatchRedisLifecycleService`: 상태 전이 뒤 커밋 후 Valkey 반영
- `RealtimeMatchWaitingReconciliationService`, `RealtimeMatchWaitingRepairService`: `WAITING` 요청 순회와 요청별 복구 판단
- `MatchingProperties`, `application.yml`: 대기 TTL 5분, 보정 간격 5초

**설명할 수 있어야 할 질문**

- DB와 Valkey 중 어느 한쪽 쓰기만 성공하면 각각 어떤 상태가 남는가?
- 왜 PostgreSQL을 기준 상태로 삼는가? 채팅방과 후기가 매칭을 참조한다는 점과 연결해 설명한다.
- 최초 등록은 왜 커밋 전에 Valkey를 활성화하고, 롤백 때 보상하는가?
- 예약 해제를 토큰이 같을 때만 하는 이유는 무엇인가?
- 확정·취소·대기 복귀의 Valkey 반영을 왜 커밋 뒤에 하며, 그 반영이 실패하면 어떻게 되는가?
- 보정 작업은 대기 키가 없을 때 남은 시간을 어떻게 계산하는가?
- Valkey 조회 자체가 실패하면 왜 DB 요청을 만료시키지 않는가?
- 요청마다 별도 트랜잭션(`REQUIRES_NEW`)으로 처리하는 이유는 무엇인가?
- 이 구조가 보장하지 않는 것은 무엇인가? (5초 내 복구, 종료된 요청의 잔여 키, WebSocket 알림 유실)

**학습 방법과 완료 기준**

- 포트폴리오의 상태별 처리 표 4행을 각각 실제 코드 분기와 연결한다.
- "DB 커밋 성공 + Valkey 실패", "DB 롤백 + Valkey 성공"처럼 실패 조합을 나누어 남는 상태를 적는다.
- Lua의 원자성은 Valkey 내부 작업에만 해당하고 DB와 함께 커밋되지 않는다는 점을 설명한다.
- 이 사례는 장애 주입 실험으로 확인한 적이 없으므로, 코드로 확인한 동작과 검증하지 않은 범위를 구분해 답한다.

### 4단계: 나머지 담당 범위로 확장하기

핵심 두 사례를 설명할 수 있게 된 뒤 다음 순서로 범위를 넓힌다.

1. **양방향 조건 기반 후보 선정:** PostGIS `ST_DWithin`과 geography 거리 계산, 양방향 반경과 식사 시간 30분 조건, 태그·임베딩 80:20 결합, pgvector 코사인 유사도, 제안 시점 점수 스냅샷. 본인 구현(PR #55)과 팀원 변경(PR #74)의 정렬 기준 차이를 구분한다.
2. **후기 정책과 다시한끼 지수:** 작성 자격(완료된 매칭의 활성 참여자, 7일 기한), 사전 조회와 UNIQUE 제약의 이중 방어, 가상 후기 5건을 더하는 산식이 소수 후기의 영향을 줄이는 원리(베이지안 평균과 비슷한 방식)
3. **OAuth 일회성 교환 코드:** 코드 해시를 Valkey 키로 쓰는 이유, 2분 유효 기간, `GETDEL`로 조회와 삭제를 한 번에 해 재사용을 막는 구조. GRAB의 Refresh Token 사례와 무엇이 다른지 비교한다.
4. **인증·보안 기반:** Spring Security 설정, CSRF, Refresh Token 해시 저장·회전·재사용 탐지. GRAB과 비슷한 부분이 많으므로 두 프로젝트의 구현 차이를 따로 정리한다.

각 기능에 대해 정상 흐름뿐 아니라 실패하거나 동시에 실행될 때 어떤 상태가 남는지 설명한다.

### 5단계: 한계와 다음 단계 답변 준비하기

다음 항목을 각각 30초 안에 "현재 동작 → 문제가 되는 조건 → 개선 방향" 순서로 설명한다.

- 잠금 대기 시간 상한(lock timeout)이 없다는 점
- 매칭 요청 생성 중 AI 키워드 추출과 임베딩 호출이 DB 트랜잭션 안에서 동기로 실행된다는 점 (팀원이 PR #74 시기에 추가한 코드다)
- 현재 후보 선정이 성향 태그와 AI 키워드가 모두 있는 프로필만 점수 계산으로 넘긴다는 점 (요청 생성 단계의 같은 검사는 팀원이 추가했다)
- 80:20 가중치의 추천 품질을 측정하지 않았다는 점

## 2. CS 학습 순서

GRAB 가이드의 CS 학습 순서를 기본으로 하고, 마주한끼에서는 다음 주제를 더 깊게 공부한다.

| 순서 | 학습 분야 | 먼저 이해할 내용 | 프로젝트 연결 |
| --- | --- | --- | --- |
| 1 | 데이터베이스 | 트랜잭션, 격리 수준, 행 잠금 모드, 교착 상태, UNIQUE·FK 제약 | 사례 ① 잠금 순서와 DB 제약 |
| 2 | Spring·JPA | `@Transactional`, 전파 속성, 영속성 컨텍스트, 트랜잭션 이벤트 | 잠금 뒤 `refresh`, 커밋 후 반영, `REQUIRES_NEW` |
| 3 | 동시성 | 경쟁 상태, 갱신 손실, 비관적·낙관적 동시성 제어, 멱등성 | 동시 수락·취소·만료 처리 |
| 4 | 캐시·분산 시스템 | TTL, 원자적 명령, Lua, 이중 쓰기, 보상·보정, Outbox | 사례 ② DB–Valkey 상태 보정 |
| 5 | 공간·벡터 검색 | 좌표계, 거리 계산, 공간 인덱스, 임베딩과 코사인 유사도 | PostGIS 후보 탐색, pgvector 성향 점수 |
| 6 | 실시간 통신 | WebSocket, STOMP, 사용자별 구독 | 제안·매칭 결과 알림 |

격리 수준은 이름을 외우는 데 그치지 않고, 두 트랜잭션이 같은 행을 동시에 읽고 수정할 때 어떤 결과가 가능한지 직접 순서를 적어 확인한다. [PostgreSQL 트랜잭션 격리 공식 문서](https://www.postgresql.org/docs/current/transaction-iso.html)를 참고한다.

## 3. 공부한 내용을 면접 답변으로 정리하는 방법

각 사례를 공부한 뒤 다음 다섯 질문에 자신의 말로 답한다.

1. 어떤 위험을 예상했는가? (마주한끼 사례는 실제 장애가 아니라 설계 단계에서 예상한 위험이다.)
2. 그 위험은 어떤 조건에서 생기는가?
3. 왜 이 방법을 선택했는가? 대안 대비 무엇을 얻고 포기했는가?
4. 무엇으로 확인했는가? 테스트 존재, 직접 검토, 실제 실행 결과를 구분한다.
5. 어떤 조건에서는 보장하지 못하는가?

실제로 겪은 일, 테스트로 확인한 동작, 코드로만 확인한 동작, 사후에 정리한 대안 비교를 구분한다. AI로 작성한 테스트는 그 사실과 본인이 검토·실행한 범위를 함께 설명한다. 확인하지 못한 부분은 추가 확인이 필요하다고 답한다.

## 4. 가장 먼저 진행할 학습 체크리스트

- [ ] 매칭 요청 한 건의 흐름을 관련 클래스와 메서드까지 따라간다.
- [ ] 요청·제안 상태 다이어그램을 그리고 전이마다 일으키는 코드를 적는다.
- [ ] PostgreSQL, Valkey, WebSocket의 역할을 구분한다.
- [ ] 트랜잭션, 행 잠금, 교착 상태의 기본 개념을 학습한다.
- [ ] 동시성 테스트 6개 시나리오를 직접 읽고 각각 설명한다.
- [ ] 전체 매칭 흐름을 그림 없이 2분 안에 설명한다.

이 체크리스트를 마친 뒤 사례 ① 동시성 제어, 사례 ② DB–Valkey 상태 보정, 나머지 담당 범위 순서로 깊이를 늘린다.
