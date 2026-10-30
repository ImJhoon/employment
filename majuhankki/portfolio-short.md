## 1\. 프로젝트 개요

**마주한끼**

지역·시간·식사 취향·대화 성향을 바탕으로 오늘 함께 식사할 한 사람을 연결하는 실시간 1:1 매칭 서비스입니다. 매칭 요청, 후보 제안, 양쪽 수락, 채팅, 후기까지 이어지며, 하나의 후보 제안에 두 사용자의 응답과 만료 스케줄러가 동시에 닿을 수 있다는 점이 설계의 중심이었습니다.

| 항목 | 내용 |
| :---- | :---- |
| 기간 · 인원 | 2026.08.21 \~ 2026.09.03 (2주) · 2명 |
| 담당 | 실시간 매칭 전 과정 (요청, 후보 탐색, 제안, 확정, DB–Valkey 정합성), 인증·보안 기반, 성향 설문, 후기, 초기 환경 구성 |
| 기술 | Java 17, Spring Boot 4, Spring Security, JPA, PostgreSQL (PostGIS, pgvector), Valkey, WebSocket(STOMP), Spring AI, Docker |
| 저장소 | [GitHub](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04) |

**핵심 성과**

- 수락·거절·취소·만료가 같은 제안에 동시에 들어와도 매칭과 채팅방이 한 번만 만들어지도록, 모든 확정 경로의 잠금 순서를 **제안 → 요청 ID 오름차순**으로 통일했습니다.
- 보존할 상태는 PostgreSQL, TTL로 사라져도 되는 대기 상태는 Valkey에 두고, Valkey 반영은 DB 커밋 뒤에만 수행했습니다. 두 저장소가 어긋나면 5초 주기 보정 작업이 DB를 기준으로 복구합니다.

## 2\. 담당 업무

- **실시간 매칭:** 매칭 요청·취소, PostGIS 양방향 반경 후보 탐색, 후보 제안과 수락·거절, 매칭·채팅방 생성, Valkey 대기 상태와 보정 작업 ([PR \#44](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/44), [\#46](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/46), [\#50](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/50))  
- **인증·보안 기반:** Spring Security 설정, CSRF, Refresh Token 해시 저장·회전·재사용 탐지, 카카오 OAuth 로그인과 일회성 교환 코드 ([PR \#19](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/19), [\#24](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/24), [\#26](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/26), [\#33](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/33))  
- **성향·후기:** 식사·대화 성향 설문, 후기 작성과 다시한끼 지수 산식 ([PR \#34](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/34), [\#61](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/61))  
- **환경 구성:** 초기 프로젝트·도메인 구조, 환경변수 구성, Prometheus·Grafana 매칭 지표, 배포용 Dockerfile ([PR \#55](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/55), [\#64](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/64))

---

## 3\. 핵심 사례 ① 동시 수락·취소·만료에도 한 사람이 두 매칭에 묶이지 않는 실시간 매칭

**위험:** 후보 제안 하나에 두 사용자의 수락, 한쪽의 거절이나 대기 취소, 180초 응답 제한 만료가 서로 다른 요청과 스케줄러에서 동시에 들어올 수 있습니다. 순서를 통제하지 않으면 같은 제안에서 매칭이 두 번 생기거나, 한 요청이 서로 다른 두 제안에서 동시에 확정될 수 있습니다. 또 대기 상태는 TTL이 필요해 Valkey에, 매칭 결과는 PostgreSQL에 두므로 두 저장소가 어긋나면 이미 매칭된 사용자가 다시 대기열에 남을 수 있습니다. 실제 장애를 겪은 것이 아니라 설계 단계에서 예상하고 대비한 위험입니다.

**설계:**

- **역할 분리:** 요청·제안·매칭처럼 보존할 상태는 PostgreSQL을 기준으로 삼고, Valkey에는 중복 진입 방지 키와 대기 등록처럼 TTL(5분)로 사라져도 되는 상태만 둡니다.  
- **진입 차단:** 매칭 요청 시 사용자 키를 `SET NX`와 예약 토큰으로 선점합니다. DB에 요청을 저장한 뒤에는 Lua 스크립트가 토큰이 일치할 때만 대기 키와 위치 등록을 한 번에 씁니다. 해제도 토큰이 같을 때만 지워, TTL 만료 뒤 다른 요청이 잡은 키를 지우지 않습니다.  
- **잠금 순서 통일:** 수락·거절·취소·만료와 매칭 확정 경로가 모두 제안 행을 먼저 잠근 뒤, 두 요청 행을 ID 오름차순으로 `PESSIMISTIC_WRITE` 잠급니다. 대기 취소처럼 요청에서 출발하는 경로도 요청을 먼저 잠그지 않고 제안 경로로 넘겨 순서를 맞췄습니다. 잠근 뒤에는 엔티티를 다시 읽어, 영속성 컨텍스트에 남은 이전 상태로 판정하지 않게 했습니다.  
- **한 트랜잭션과 DB 제약:** 양쪽이 수락하면 매칭, 참여자 2명, 채팅방을 한 트랜잭션에서 만듭니다. 매칭 테이블의 요청 쌍 UNIQUE와 요청별 UNIQUE를 마지막 방어선으로 두었고, 경쟁에서 진 요청은 409(`MATCHING_012`)로 응답합니다. 같은 결정을 다시 보내면 새로 만들지 않고 기존 결과를 돌려줍니다.  
- **클라이언트 후속 동작:** 409를 받은 화면은 현재 제안을 닫고 "응답 시간이 끝났거나 다른 매칭이 먼저 확정되었습니다"라고 안내한 뒤, 매칭 요청·제안·최근 결과를 다시 조회해 서버 상태에 화면을 맞춥니다.  
- **외부 반영은 커밋 뒤:** Valkey 정리·복귀와 WebSocket 알림은 DB 커밋 이후에 실행해, 롤백된 매칭이 사용자에게 보이지 않게 했습니다. 커밋 뒤 Valkey 반영이 실패하면 5초 주기 보정 작업이 DB 상태를 기준으로 대기 키를 복구하거나 만료 처리합니다.

**경로별 잠금 순서**

| 경로 | 시작 | 잠금 순서 |
| :---- | :---- | :---- |
| 후보 제안 생성 | 후보 탐색 | 두 요청 (ID 오름차순) |
| 수락·거절 | 사용자 응답 | 제안 → 두 요청 (ID 오름차순) |
| 만료 | 스케줄러 | 제안 → 두 요청 (ID 오름차순) |
| 대기 취소 (제안 있음) | 사용자 취소 | 제안 → 두 요청 (ID 오름차순) |
| 대기 취소 (제안 없음) | 사용자 취소 | 요청 하나, 잠근 뒤 제안 생성 여부 재확인 |

**한계와 다음 단계:**

- 매칭 요청 생성 중 AI 키워드 추출과 임베딩 호출이 DB 트랜잭션 안에서 동기로 실행되어, AI 응답이 느리면 커넥션 점유가 길어질 수 있습니다. 커밋 뒤 비동기 처리로 옮기는 것이 다음 과제입니다.

근거: [MatchProposalLifecycleService](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/proposal/MatchProposalLifecycleService.java), [RealtimeMatchRequestService](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/request/RealtimeMatchRequestService.java), [Valkey 대기 저장소·Lua](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/repository/RedisRealtimeMatchWaitingStore.java), [보정 작업](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/src/main/java/org/example/project2/domain/matching/service/request/RealtimeMatchWaitingReconciliationService.java), [409 처리 화면](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/310120e7ba6de85c372a028935fa2b07c1621c94/frontend/src/pages/matching-request.js#L592-L634), [PR \#46](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/46), [PR \#50](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/pull/50)
