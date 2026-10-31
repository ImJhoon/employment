## 1\. 프로젝트 개요

**마주한끼**

지역·시간·식사 취향·대화 성향을 바탕으로 오늘 함께 식사할 한 사람을 연결하는 실시간 1:1 매칭 서비스입니다. 매칭 요청, 후보 제안, 양쪽 수락, 채팅, 후기까지 이어집니다. 매칭 전 과정을 담당하며, 동시에 들어오는 응답 속에서도 매칭이 한 번만 확정되도록 하고 PostgreSQL과 Valkey의 대기 상태를 맞추는 구조를 구현했습니다.

| 항목 | 내용 |
| :---- | :---- |
| 기간 · 인원 | 2026.08.21 \~ 2026.09.03 (2주) · 2명 |
| 담당 | 실시간 매칭 전 과정 (요청, 후보 탐색, 제안, 확정, DB–Valkey 정합성), 인증·보안 기반, 성향 설문, 후기, 초기 환경 구성 |
| 기술 | Java 17, Spring Boot 4, Spring Security, JPA, PostgreSQL (PostGIS, pgvector), Valkey, WebSocket(STOMP), Spring AI, Docker |
| 저장소 | [GitHub](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04) |

구현 설명은 기준 커밋 `310120e`를 따릅니다. 후보 선정은 본인 구현 시점(PR #55)과 후속 팀 변경을 구분했습니다.

**핵심 구현**

- **매칭 중복 확정 제어:** 수락·거절·취소·만료가 동시에 들어와도 매칭과 채팅방이 한 번만 만들어지도록, 모든 확정 경로의 잠금 순서를 **제안 → 요청 ID 오름차순**으로 통일했습니다. 실제 PostgreSQL 경쟁 테스트 6개 시나리오가 21회 반복 모두 통과했고, 잠금을 제거하면 실패하는 것을 확인했습니다.
- **DB–Valkey 상태 보정:** PostgreSQL을 기준 상태로 두고, 누락된 Valkey 대기 키는 보정 작업이 DB에 맞춰 복구하거나 만료시킵니다.

## 2\. 담당 업무

- **실시간 매칭:** 매칭 요청·취소, PostGIS 양방향 반경 후보 탐색, 후보 제안과 수락·거절, 매칭·채팅방 생성, Valkey 대기 상태와 보정 작업 ([PR \#44](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/44), [\#46](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/46), [\#50](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/50))  
- **인증·보안 기반:** Spring Security 설정, CSRF, Refresh Token 해시 저장·회전·재사용 탐지, 카카오 OAuth 로그인과 일회성 교환 코드 ([PR \#19](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/19), [\#24](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/24), [\#26](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/26), [\#33](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/33))  
- **성향·후기:** 식사·대화 성향 설문, 후기 작성과 다시한끼 지수 산식 ([PR \#34](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/34), [\#61](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/61))  
- **환경 구성:** 초기 프로젝트·도메인 구조, 환경변수 구성, Prometheus·Grafana 매칭 지표, 배포용 Dockerfile ([PR \#55](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/55), [\#64](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/64))

---

## 3\. 핵심 사례 ① 동시 수락·취소·만료에서 매칭 중복 확정을 제어

**위험:** 한 사용자가 매칭 제안을 수락하는 순간, 상대방이 거절하거나 대기를 취소할 수 있습니다. 응답 기한인 180초(3분)가 끝날 무렵에는 수락과 서버의 만료 처리가 겹칠 수도 있습니다. 각 작업이 서로의 처리 결과를 확인하지 않고 저장하면 취소한 제안이 매칭으로 확정되거나, 같은 제안에서 매칭이 두 번 만들어질 수 있습니다. 또한 같은 요청이 포함된 두 제안이 동시에 확정되면 한 사람이 두 매칭에 묶일 위험이 있습니다. 실제로 겪은 장애가 아니라, 설계 단계에서 예상하고 대비한 상황입니다.

**설계:**

- **잠금 순서 통일:** 모든 확정 경로가 제안 행을 먼저 잠그고, 두 요청 행을 ID 오름차순으로 `PESSIMISTIC_WRITE` 잠급니다. 요청에서 출발하는 대기 취소도 제안 경로로 넘겨 같은 순서를 따릅니다. 잠근 뒤에는 엔티티를 다시 읽어 최신 상태로 판정합니다.
- **한 트랜잭션과 DB 제약:** 양쪽이 수락하면 매칭, 참여자 2명, 채팅방을 한 트랜잭션에서 만듭니다. 매칭 테이블에는 요청 쌍과 각 요청에 UNIQUE 제약을 두었습니다.
- **충돌 응답과 화면 복구:** 같은 결정을 다시 보내면 기존 결과를 돌려줍니다. 이미 종료·만료된 제안이거나 잠금·제약 충돌이 나면 409로 응답하고, 화면은 현재 제안을 닫은 뒤 요청·제안·최근 결과를 다시 조회해 서버 상태에 맞춥니다.

**경로별 잠금 순서**

| 경로 | 시작 | 잠금 순서 |
| :---- | :---- | :---- |
| 후보 제안 생성 | 후보 탐색 | 두 요청 (ID 오름차순) |
| 수락·거절 | 사용자 응답 | 제안 → 두 요청 (ID 오름차순) |
| 만료 | 스케줄러 | 제안 → 두 요청 (ID 오름차순) |
| 대기 취소 (제안 있음) | 사용자 취소 | 제안 → 두 요청 (ID 오름차순) |
| 대기 취소 (제안 없음) | 사용자 취소 | 요청 하나, 잠근 뒤 제안 생성 여부 재확인 |

**선택 이유 (사후 비교 분석):** 당시 대안을 비교한 기록이 남아 있지 않아, 구현을 기준으로 다시 정리했습니다.

| 방식 | 이 흐름에서의 장점 | 비용·한계 |
| :---- | :---- | :---- |
| 비관적 락 (적용) | 경쟁 범위가 제안 1건과 요청 2건으로 좁습니다. 뒤에 온 요청은 앞선 트랜잭션이 끝날 때까지 기다린 뒤 확정된 상태를 다시 읽고, 기존 결과를 돌려줄지 409로 응답할지 바로 판단합니다. | 잠금 대기가 생깁니다. 경로마다 잠금 순서가 다르면 교착이 생길 수 있어 모든 경로의 순서를 맞춰야 합니다. |
| 낙관적 락 (`@Version`) | 대기 없이 진행하고 충돌을 커밋 시점에 감지합니다. | 충돌에서 진 쪽이 어떤 응답을 받아야 하는지 알려면 다시 읽고 재시도하는 경로가 필요합니다. 만료 스케줄러에도 같은 처리가 필요합니다. |
| Valkey 분산 락 | DB 밖에서 여러 서버의 진입을 막을 수 있습니다. | 상태와 제약은 PostgreSQL에 있어 락 해제와 DB 커밋 시점을 따로 맞춰야 하고, 락 TTL이 트랜잭션보다 먼저 끝날 수 있습니다. 매칭 확정이 Valkey 가용성에도 의존하게 됩니다. |

**검증과 결과:** 실제 PostgreSQL에서 두 트랜잭션을 동시에 출발시키는 통합 테스트로 6가지 경쟁 상황을 확인했습니다. 양쪽 동시 수락, 중복 수락 재전송, 한 요청을 공유하는 두 제안의 동시 수락, 수락과 거절·취소·만료의 경쟁입니다. 실행이 끝나면 매칭·참여자·채팅방 수와 제안·요청 상태를 검사합니다. 잠금이 결과를 실제로 바꾸는지 보기 위해 잠금을 제거한 버전과도 비교했습니다.

| 조건 | 실행 | 결과 |
| :---- | :---- | :---- |
| 기준 코드 | 21회 | 21회 모두 6개 시나리오 통과 |
| 제안 행 잠금만 제거 | 10회 | 10회 모두 실패. 동시 수락에서 한쪽 결정이 덮어써져 매칭이 만들어지지 않고 제안이 `PENDING`에 멈췄습니다. 일부 실행에서는 거절·취소도 유실됐습니다. |
| 제안·요청 잠금 모두 제거 | 10회 | 10회 모두 실패. 결정 유실에 더해 같은 제안의 매칭을 두 번 저장하려는 시도(4회)와 한 요청을 두 매칭에 쓰려는 시도(10회)가 생겼고, 매칭 테이블의 UNIQUE 제약이 이를 거부했습니다. |

검증 범위: 테스트는 서비스 계층을 직접 호출하며 Valkey와 WebSocket은 대역(mock)으로 대체했습니다. HTTP 409 변환과 화면 재조회는 테스트가 아니라 코드로 확인한 동작입니다. 2026년 10월 로컬 Docker 환경(PostgreSQL 16, 기준 커밋 `310120e`)에서 실행했으며, 테스트 코드 작성과 실행에는 AI 코딩 도구(Claude Code)를 활용했습니다.

**한계와 다음 단계:** 잠금 대기 시간 상한(lock timeout)을 설정하지 않아, 잠금을 쥔 트랜잭션이 길어지면 기다리는 요청도 함께 묶입니다. 대기 상한과 그때의 실패 응답을 정하는 것이 다음 과제입니다.

근거: [MatchProposalLifecycleService](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/proposal/MatchProposalLifecycleService.java), [요청 잠금·취소 경로](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/request/RealtimeMatchRequestService.java), [DB 제약](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/entity/Match.java), [충돌 응답 변환](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/proposal/MatchProposalInteractionService.java), [409 처리 화면](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/frontend/src/pages/matching-request.js#L592-L634), [동시성 통합 테스트](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/test/java/org/example/project2/domain/matching/service/proposal/MatchProposalConcurrencyIntegrationTest.java), [PR \#46](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/46), [PR \#50](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/50)

---

## 4\. 핵심 사례 ② DB를 기준으로 Valkey 대기 상태를 보정

**문제와 제약:** DB에 요청이 저장되어도 Valkey 등록이 누락되거나, DB에서 매칭이 확정되어도 대기 키 삭제가 실패할 수 있습니다. PostgreSQL 트랜잭션만으로 Valkey 쓰기까지 함께 커밋할 수 없으므로, 어느 상태를 기준으로 복구할지와 실패 시 처리 순서를 정해야 합니다.

**선택 이유 (사후 비교 분석):** 요청·제안·매칭은 채팅방과 후기가 참조하는 기록이며, 사례 ①의 행 잠금과 트랜잭션으로 상태 전이를 보호합니다. 반면 Valkey 대기 키는 TTL로 사라지는 것을 전제로 둔 값입니다. 그래서 두 저장소가 어긋나면 PostgreSQL을 기준으로 Valkey를 맞춥니다. 잠시 어긋날 수 있다는 비용은 남기고, 보정 작업으로 그 시간을 줄이는 구조입니다.

**설계와 구현:**

- **역할 분리:** PostgreSQL은 요청·제안·매칭을 보존하고, Valkey는 사용자별 중복 진입 방지와 TTL 5분의 대기 상태를 맡습니다.
- **최초 등록과 롤백 보상:** `SET NX`와 예약 토큰으로 사용자 키를 선점한 뒤 DB에 요청을 저장하고, DB 커밋 전에 Lua로 대기 키와 위치 등록을 함께 활성화합니다. DB가 롤백되면 요청 ID가 일치하는 키만 정리하고, 예약 해제도 토큰이 같을 때만 수행해 다른 요청의 키를 지우지 않습니다.
- **커밋 후 반영:** 제안 생성·확정·취소·대기 복귀에 따른 Valkey 갱신과 매칭 결과 WebSocket 알림은 DB 커밋 뒤에 수행합니다. 이 단계에서 Valkey 반영이 실패해도 이미 커밋된 DB 상태는 되돌리지 않습니다.
- **대기 요청 보정:** 5초 간격의 작업이 DB의 `WAITING` 요청을 100건씩 순회합니다. 요청마다 별도 트랜잭션에서 행을 잠근 뒤 아래 표에 따라 처리합니다.

| 상태 | 처리 |
| :---- | :---- |
| 최초 대기 등록 뒤 DB 롤백 | 요청 ID가 일치하는 Valkey 키 정리 시도 |
| DB는 `WAITING`, 대기 키 누락, 대기 시간 남음 | 남은 TTL로 대기 키와 위치 등록 복구 |
| DB는 `WAITING`, 대기 키 누락, 대기 시간 경과 | DB 요청 만료 후 Valkey 정리 |
| Valkey 조회 실패 | DB 상태를 유지하고 다음 순회에서 재시도 |

**한계와 다음 단계:** 두 저장소를 함께 커밋하지 않으므로 일시적인 불일치는 남고, 순회량과 장애 지속 시간에 따라 복구가 5초보다 늦어질 수 있습니다. 보정 대상은 `WAITING` 요청의 누락된 키입니다. 종료된 요청의 잔여 키는 TTL과 만료된 위치 등록 정리에 맡기며, WebSocket 알림 유실은 보정 범위에 포함되지 않습니다.

근거: [최초 등록·롤백 보상](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/request/RealtimeMatchRequestService.java), [Valkey 대기 저장소·Lua](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/repository/RedisRealtimeMatchWaitingStore.java), [커밋 후 반영](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/request/RealtimeMatchRedisLifecycleService.java), [보정 스케줄러](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/request/RealtimeMatchWaitingReconciliationService.java), [요청별 복구 판단](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/request/RealtimeMatchWaitingRepairService.java), [매칭 결과 알림](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/proposal/MatchResultCreatedEventHandler.java), [PR \#44](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/44), [PR \#46](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/46)

---

## 5\. 그 밖의 작업

- **양방향 조건 기반 후보 선정:** 두 사람 각각의 탐색 반경(PostGIS `ST_DWithin`)과 식사 시간 30분 이내 조건을 먼저 확인한 뒤 성향 점수를 계산했습니다. 태그와 자유 서술 임베딩 점수를 80:20으로 결합하고, 제안 시점의 양방향 점수·근거·산식 버전을 함께 저장했습니다. 본인 구현(PR #55)은 양방향 점수 중 낮은 값을 기준으로 정렬했고, 이후 팀원 PR #74에서 요청자→후보 점수 정렬과 다중 임베딩 계산으로 바뀌었습니다. ([양방향 조건 필터](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/candidate/BidirectionalCandidateSearchService.java), [본인 구현 시점의 후보 선정](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/bfa2ab3198ec0638df3785c6fb122299af561d28/src/main/java/org/example/project2/domain/matching/service/proposal/MatchProposalSelectionService.java), [PR \#55](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/55), [후속 팀 변경 \#74](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/74))
- **후기 작성 정책과 다시한끼 지수:** 완료된 매칭의 활성 참여자만 종료 후 7일 안에 상대에게 후기를 작성할 수 있게 했습니다. 사전 중복 조회와 DB UNIQUE 제약을 함께 사용하고, 저장 시 제약 충돌을 중복 후기 응답으로 변환합니다. 지수는 `(2.5 + 확실히 다시 + 0.5 × 아마도 다시) / (5 + 유효 후기 수) × 100`으로 계산하며 산식 버전을 관리합니다. 수식상 가상 후기 5건을 반영해 소수 후기의 점수 영향을 완화합니다. ([후기 작성](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/review/service/ReviewCommandService.java), [지수 산식](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/review/service/ReviewScorePolicy.java), [PR \#61](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/61))
- **OAuth 일회성 교환 코드:** OAuth 로그인 결과를 토큰으로 교환하는 코드의 해시를 Valkey 키로 저장하고, 유효 기간을 2분으로 제한했습니다. 교환 시 `GETDEL`로 조회와 삭제를 한 번에 수행하며, 코드를 소비한 뒤 활성 회원인지 확인하고 토큰을 발급합니다. ([교환 코드](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/auth/service/oauth/OAuthAuthorizationCodeService.java), [토큰 교환](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/auth/service/oauth/OAuthTokenExchangeService.java), [PR \#24](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/24))
