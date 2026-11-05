# 마주한끼 작성 메모

공개 본문(`portfolio-short.md`)에 넣지 않는 근거, 실행 기록, 확인이 필요한 항목을 정리한다.

## 기준

- 원본 저장소: [AIBE7-Project2-Team04](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04)
- 기준 커밋: `310120e7ba6de85c372a028935fa2b07c1621c94` (main, 2026-09-03 마지막 커밋)
- 확인 방법: 2026-10-30 저장소를 로컬 임시 디렉토리에 클론해 코드, 커밋, `git blame`을 확인했다. README의 설명은 코드와 대조했다.

## 사용자 확인 사항 (2026-10-30)

| 항목 | 내용 |
| --- | --- |
| 기간 | 2026-08-21 ~ 2026-09-03. 커밋 기준과 일치한다. |
| 동시성 설계 경위 | 장애를 겪고 고친 것이 아니라 처음부터 대비해 설계했다. 본문은 "위험 예측 → 설계 → 검증" 흐름으로 쓴다. |
| 테스트 작성 | 동시성 테스트 코드는 AI에게 맡겨 작성했고, 당시에는 직접 실행하지 않았다. |
| 사용 기록 | 배포 서비스 DB에 남아 있을 수 있으나 확인하지 않았다. |
| 팀 인원 | 2명으로 쓴다. README 팀원 소개와 같다. 초기 커밋 3건(NZN-123/정의진, 2026-08-21)의 작성자가 따로 있지만 인원에 넣지 않기로 했다. |
| PR #50 `fix` 경위 | 커밋 `1f80bf7`(잠금 뒤 `EntityManager.refresh`, 재시도 스케줄러 추가)이 실제 버그를 보고 고친 것인지 사용자가 기억하지 못한다(2026-10-31). 실제로 겪은 문제로 쓰지 않는다. |
| 테스트 서술 | 공개 본문에서 테스트 관련 내용(검증 절, 시나리오 표, 실행 조건, 테스트 한계, 테스트 링크)을 일단 뺀다. 아래 실행 기록은 다시 넣을 때를 위해 남겨 둔다. |

## 개인 기여 근거

squash merge 커밋 작성자와 `git blame` 기준이다. 커밋 작성자만으로 설계 주도나 기여율을 단정하지 않는다.

| 영역 | 본인 (한재훈) | 팀원 (sang9831) |
| --- | --- | --- |
| 매칭 | #40, #42, #44, #46, #47, #50, #52, #55 | #74 (매칭 일부 수정), #78 |
| 인증 | #19 Security 기반, #24 OAuth·교환 코드, #26 카카오, #33 세션 수 제한 | 구글 OAuth, 401 응답 시 토큰 자동 갱신 (프론트) |
| 기타 | #30·#34 성향 설문, #58·#61 후기, #64·#66 배포 | 채팅(WebSocket), 신고·관리자, 마이페이지 |

매칭 핵심 파일의 blame 비율 (기준 커밋):

- `RedisRealtimeMatchWaitingStore` 256/256줄
- `MatchProposalLifecycleService` 337/337줄
- `RealtimeMatchWaitingReconciliationService` 102/102줄
- `RealtimeMatchRequestService` 348/387줄
- `MatchProposalSelectionService` 500/575줄

핵심 장치의 첫 등장 커밋:

| 장치 | 커밋 |
| --- | --- |
| `SET NX` 예약, Lua 활성화, 롤백 보상 | `0537105` #44 (2026-08-27). 최초 활성화는 DB 커밋 전이다. |
| 제안 → 요청 ID 오름차순 잠금, 보정 스케줄러 | `fd5ba2f` #46 (2026-08-28) |
| 잠금 뒤 `entityManager.refresh`, 동시성 통합 테스트 | `c96fc08` #50 (2026-08-28) |

## 동시성 통합 테스트 실행 기록

- 일시: 2026-10-30 20:43 (KST)
- 실행자: 사용자 PC에서 Claude가 대신 실행했다. 사용자가 직접 재실행하면 이 항목을 갱신한다.
- 환경: Windows 11, Java 17.0.12, Gradle 9.5.1(wrapper), Docker 임시 컨테이너 `pgvector/pgvector:pg16` + `postgresql-16-postgis-3` (PostgreSQL 16.15, PostGIS 3.7.0, pgvector 0.8.7), Redis 7 컨테이너
- 명령: `./gradlew test --tests "*MatchProposalConcurrencyIntegrationTest" --init-script exclude-broken-tests.gradle`
- 환경변수: `SPRING_PROFILES_ACTIVE=dev`, `DB_*`는 임시 컨테이너, `JWT_SECRET`은 임의의 Base64 48바이트, `GOOGLE_API_KEY`는 가짜 값, `FRONTEND_ORIGIN=http://localhost:3000`
- 결과: 6개 시나리오 모두 통과 (총 25.5초). 로그에서 `jdbc:postgresql://localhost:55432` 연결과 `for no key update` 잠금 쿼리 28회를 확인했다.

실행 시 우회한 점:

1. 기준 커밋에서 테스트 소스가 컴파일되지 않는다. 오류 26건이 아래 6개 파일에서 난다. 팀원 PR #74(2026-09-02)가 `UserPersonalityEmbedding` API를 바꾼 뒤 테스트가 갱신되지 않은 것으로 보인다. 클론 파일은 수정하지 않고 Gradle init 스크립트로 컴파일에서 제외했다.
   - `MatchProposalSelectionServiceTest`, `PersonalityEmbeddingEventHandlerTest`, `PersonalityEmbeddingMigrationServiceTest`, `PersonalityEmbeddingPipelineIntegrationTest`, `PersonalityServiceTest`, `UserPersonalityEmbeddingRepositoryTest`
2. Spring AI의 Google GenAI 자동 설정이 키 없이 컨텍스트 로딩을 막아 가짜 `GOOGLE_API_KEY`를 넣었다. 이 테스트는 AI를 호출하지 않는다.

본문 표현 원칙:

- 원본 저장소의 `gradlew test` 전체가 실패하므로 "전체 테스트 통과"나 테스트 개수를 성과로 쓰지 않는다.
- Valkey와 WebSocket은 mock이다. Valkey 경로(`RedisRealtimeMatchWaitingStoreTest` 등)는 Mockito 단위 테스트뿐이다.

### 반복 실행과 잠금 제거 비교 (2026-10-31)

사용자 요청으로 Claude가 사용자 PC에서 실행했다. 사용자가 직접 실행한 것은 아니다.

- 대상: 기준 커밋 `310120e`를 원본 클론에서 별도 worktree로 꺼내 실행. 원본 클론의 작업 트리는 바꾸지 않았다.
- 환경: Windows 11, Java 17.0.12, Gradle wrapper, Docker 컨테이너 `pgvector/pgvector:pg16` + `postgresql-16-postgis-3` (PostgreSQL 16.15, PostGIS 3.7.0, pgvector 0.8.7), `redis:7`. 환경변수와 컴파일 제외 init 스크립트는 위 실행 기록과 같다.
- 명령: `./gradlew test --tests "*MatchProposalConcurrencyIntegrationTest" --init-script exclude-broken-tests.gradle --rerun -q`
- 1회 실행(19:34): 6개 시나리오 모두 통과. 테스트 결과 XML에서 `localhost:55432` 연결과 `for no key update` 28회를 확인했다.
- 20회 반복(19:35~19:46): 20회 모두 6개 시나리오 통과(실패 0, 오류 0). 매회 `for no key update` 28회.
- 비교 실험 A: `MatchProposalRepository.findByIdForUpdate`의 `@Lock(PESSIMISTIC_WRITE)` 한 줄만 제거(요청 행 잠금은 유지)하고 10회 실행. 10회 모두 실패했다(회당 실패 2~4개).
  - 양쪽 동시 수락: 10/10 실패. 매칭이 0건 만들어지고 제안이 `PENDING`으로 남았다. 두 트랜잭션이 각자 자기 결정만 반영한 제안을 저장해 한쪽 결정이 덮어써진 것(갱신 손실)으로 해석한다.
  - 중복 수락 재전송: 10/10 실패. 앞 단계의 동시 수락부터 매칭이 확정되지 않았다.
  - 수락 vs 거절: 6/10 실패, 수락 vs 취소: 5/10 실패. 실패한 실행에서는 제안이 `REJECTED`·`CANCELLED`가 아니라 `PENDING`으로 남았다.
  - 경쟁 제안, 수락 vs 만료: 10/10 통과. 요청 행 잠금과 만료 판정이 남아 있어서로 해석한다.
- 비교 실험 B: A에 더해 `MatchRequestRepository`의 `@Lock(PESSIMISTIC_WRITE)` 3개를 제거하고 `refreshLockedRequests`의 `entityManager.refresh(request, PESSIMISTIC_WRITE)`를 `refresh(request)`로 바꿔 10회 실행. 매칭 도메인에 남은 비관적 잠금은 없고, 로그의 `for no key update`는 0회였다. 10회 모두 실패했다(회당 3~4개).
  - 양쪽 동시 수락: 10/10 실패. A와 같이 매칭 0건, 제안 `PENDING`.
  - 경쟁 제안: 10/10 실패. 두 번째 매칭 저장이 `UNIQUE (request_1_id)` 위반(`DataIntegrityViolationException`)으로 거부됐다.
  - 중복 수락 재전송: 10/10 실패. 6회는 결정 유실, 4회는 같은 요청 쌍의 두 번째 매칭 저장이 `uk_matches_request_pair` 위반으로 거부됐다.
  - 수락 vs 거절: 3/10 실패(결정 유실). 수락 vs 취소, 수락 vs 만료: 10/10 통과.
  - 매칭 테이블 UNIQUE 제약(실행 DB에서 확인): `uk_matches_request_pair (request_1_id, request_2_id)`, `request_1_id` 단독, `request_2_id` 단독.
- 해석: 제안 행 잠금은 결정 유실을, 요청 행 잠금은 중복 매칭 시도를 막는다. 잠금이 모두 없으면 중복 매칭 시도가 실제로 생기며 DB UNIQUE 제약이 마지막에 거부한다. 운영 코드에서는 이 제약 위반이 `MatchProposalInteractionService`에서 409로 변환되지만, 테스트는 서비스 계층을 직접 호출하므로 409 변환은 확인하지 않았다. Valkey·WebSocket은 mock이다.
- 본문 반영(2026-10-31, 사용자 요청): 사례 ① "검증과 결과"에 기준 21회, A·B 각 10회 결과와 검증 범위, AI 활용(테스트 작성과 실행에 Claude Code 활용)을 적었다. 사용자가 직접 실행했다고 쓰지 않는다.

## 409 응답 이후 클라이언트 동작

사용자도 기억하지 못해 코드로 확인했다. 기준 커밋의 `frontend/src/pages/matching-request.js`이며, L592-634와 L250-300 모두 git blame상 한재훈이 작성했다.

- `handleDecision`: 수락·거절 응답이 409면 현재 제안을 닫는다. 이어 "응답 시간이 끝났거나 다른 매칭이 먼저 확정되었습니다. 현재 상태를 다시 확인합니다."를 보여 주고 `refreshFlow({ checkResult: true })`를 호출한다.
- `refreshFlow`: 현재 요청과 현재 제안을 다시 조회한다. 요청이 없고 제안이 닫혔으면 최근 매칭 결과를 조회한다. `MATCHED`면 `applyMatchResult`로 결과를 반영하고, 아니면 반경 확장을 제안하거나 "주변에 후보를 찾지 못했어요" 안내를 보여 준다.
- 실제 브라우저에서 이 동작을 확인하지는 않았다. 코드에서 읽은 흐름이다.

## 코드와 문서가 어긋나는 부분

- 랭킹: 현재 기준 커밋은 `pairScore = min(양방향 점수)`를 계산하고 공식 이름도 `..._BIDIRECTIONAL_MIN_V1`이지만, 정렬은 `sourceToTargetScore` 기준이다. PR #55 시점의 본인 구현은 `pairScore`로 정렬했고, 팀원 PR #74가 이를 바꿨다. 현재 동작과 본인 구현 시점을 구분한다.
- Redis GEO: `match:waiting:geo`에 기록하고 정리만 하며, 후보 탐색은 PostGIS `ST_DWithin`이 한다. "Valkey로 위치 검색"이라고 쓰지 않는다.
- 제안 응답 제한: 기준 커밋은 `PROPOSAL_TTL = 180초`다. #50 커밋 메시지의 "15초"는 이후 바뀌었다.
- 트러블슈팅 문서 `docs/troubleshooting/실시간_매칭_동시성_이슈.md`는 팀원이 2026-09-03에 썼다. 이 문서의 "증상"은 실제 관찰이 아니므로 인용하지 않는다.
- Valkey 반영 시점: 기존 본문의 "Valkey 반영은 DB 커밋 뒤에만 수행"은 범위가 넓어 부정확했다. `RealtimeMatchRequestService.create`는 `saveAndFlush` 뒤, DB 커밋 전에 대기 키를 활성화하고 `afterCompletion`으로 롤백 보상을 등록한다. 제안 생성·확정·복귀의 반영은 `RealtimeMatchRedisLifecycleService`의 `afterCommit` 경로다. 누락 대기 키 복구도 별도 DB 트랜잭션 안에서 Valkey를 직접 호출한다.

## 사례 확장 시 확인한 근거 (2026-10-31)

GitHub 도구로 필요한 원격 파일과 PR #74 변경분을 열람했다. 기존 기준 커밋은 유지했고, 본인 구현 비교에만 PR #55 병합 커밋 `bfa2ab3198ec0638df3785c6fb122299af561d28`을 추가했다. 이번 작업에서는 서비스·브라우저·테스트를 실행하지 않았다. 아래 효과 해석은 코드 분석이며 실사용 성과가 아니다.

사례 ①도 `MatchProposalInteractionService`, `Match`를 대조했다. 409는 종료·만료된 제안의 결정 변경과 잠금·무결성 충돌 등의 처리이며, 모든 경쟁 패배 경로가 409라고 일반화하지 않는다. `Match`의 UNIQUE는 요청 쌍과 각 요청 FK 컬럼에 걸린 제약이므로, 컬럼을 가로지르는 요청 중복 방지는 행 잠금 및 상태·기존 매칭 조회와 함께 설명한다.

### 사례 ②: DB–Valkey 상태 보정

- `RealtimeMatchRequestService`, `RedisRealtimeMatchWaitingStore`: 최초 예약·활성화·롤백 보상과 토큰/요청 ID 비교를 확인했다. Lua의 원자성은 Valkey 내부 작업에 한정되며 DB와 함께 커밋되는 것은 아니다.
- `RealtimeMatchRedisLifecycleService`, `MatchResultCreatedEventHandler`: 상태 전이에 따른 Valkey 쓰기와 매칭 결과 알림의 커밋 후 실행을 확인했다. 이 설계를 영속 재시도나 알림 전달 보장으로 확대하지 않는다.
- `RealtimeMatchWaitingReconciliationService`: `fixedDelay` 기본 5초, 초기 지연 30초, 100건씩 ID 커서 순회를 확인했다. 한 요청의 실패는 다음 요청의 보정을 중단하지 않는다. 모든 요청이 5초 안에 복구되는 구조는 아니다.
- `RealtimeMatchWaitingRepairService`: `REQUIRES_NEW`로 요청 행을 잠근 뒤 `WAITING` 여부와 대기 키 TTL을 확인한다. 키가 이미 있으면 그대로 반환하며, 키가 없을 때만 DB의 `updatedAt`(없으면 `createdAt`)과 애플리케이션 `Clock`으로 잔여 시간을 계산한다. 잔여 시간이 양수면 키 복구, 경과했으면 DB 만료, Valkey 조회 실패면 DB 변경 보류다.
- 보정 대상은 `WAITING` 요청이다. 종료된 요청의 키 삭제 실패나 WebSocket 알림 유실까지 복구한다고 쓰지 않는다. 잔여 키의 TTL과 만료된 GEO 등록 정리는 별도로 구분했다.
- `MatchingProperties`, `application.yml`: 대기 TTL 5분, 보정 간격 5초 설정을 대조했다.

### 사례 ③: 후보 선정과 개인 기여 경계

- PR #55 병합 시점의 `BidirectionalCandidateSearchService`, `MatchProposalSelectionService`, `PersonalityCompatibilityCalculator`를 현재 기준 커밋 및 PR #74 패치와 대조했다. PR #55 작성자는 ImJhoon, PR #74 작성자는 sang9831이다. 기존 기여 조사와 함께 본인 구현/후속 변경을 구분하는 근거로 사용했다.
- 양방향 거리·시간 조건 필터, 프로필·태그·임베딩 일괄 조회, 태그/임베딩 80:20 결합, 임베딩이 없을 때 태그 점수 반영, 양방향 점수·일치 태그·이유·산식 버전 스냅샷은 PR #55 시점에도 존재한다.
- PR #55는 성향 점수 계산 가능 여부 → `pairScore` 내림차순 → 거리 → 대기 시작 시각 → 요청 ID로 정렬한다. 팀원 PR #74가 점수 정렬을 `sourceToTargetScore`로 바꾸고, 임베딩 목록 처리와 유사도 점수 변환을 추가했다. 후속 구현을 개인 성과로 편입하지 않았다.
- 현재 기준 커밋은 랭킹 전에 후보 프로필의 `styleTags`와 `aiKeywords`가 모두 비어 있지 않은지 필터링한다. 따라서 계산기의 태그 대체 경로만 보고 "AI 장애에도 항상 매칭 가능"이라고 쓰지 않는다. 이 선행 필터는 향후 개선 과제로 본문에 명시했다.
- 80:20 가중치의 당시 선택 이유, 다른 가중치와의 실험, 추천 품질 개선은 확인하지 않았다. 본문에는 구현된 정책값만 쓰고 수락률·후기 기반 조정은 다음 단계로 표현했다.

### 보조 사례

- `ReviewCommandService`, `ReviewScorePolicy`: 완료된 매칭 참여자·활성 회원·7일 작성 기한, 중복 조회 및 특정 UNIQUE 충돌 변환, 지수 산식과 버전 상수를 확인했다. 가상 후기 5건의 완충 효과는 수식에서 도출한 해석이다.
- `OAuthAuthorizationCodeService`, `OAuthTokenExchangeService`: 해시를 사용한 Valkey 키, 2분 TTL, `getAndDelete` 일회성 소비, 활성 회원 확인 후 토큰 발급을 확인했다. GRAB의 Refresh Token 사례 반복을 줄이기 위해 마주한끼 보조 사례는 교환 코드에 초점을 맞췄다.
- 상세 코드 링크는 공개 본문의 각 사례에 고정 SHA로 연결했다. 공개 본문의 기존 테스트 서술 제외 결정은 유지했다.

## HTML 표시 문서 (2026-10-31)

- `portfolio-short.html`, `portfolio.css`는 GRAB 포트폴리오의 레이아웃을 참고해 작성했다. 상단에는 정량 성과 대신 세 가지 핵심 구현 주제를 배치했다.
- 색상은 마주한끼의 Warm Table 테마로 맞췄다. 로컬 원본 저장소의 커밋 `c7a79f2e4fb983ddd42445dfbccb60525478c202`에서 수정되지 않은 [`frontend/src/style.css`](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/c7a79f2e4fb983ddd42445dfbccb60525478c202/frontend/src/style.css)와 [`frontend/Design.md`](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04/blob/c7a79f2e4fb983ddd42445dfbccb60525478c202/frontend/Design.md)를 확인했다. 이는 표시용 테마 출처이며 포트폴리오 구현 설명의 기준 커밋을 변경한 것은 아니다.
- 사용자 요청에 따라 페이지 배경은 흰색 `#FFFFFF`으로 두고, 원본의 아이보리 `#FAF7F2`는 카드와 표 배경에 적용했다. 강조선은 코랄 오렌지 `#FF6B4A`, 제목은 네이비 `#1E2C4A`, 링크와 강조 글자는 짙은 주황 `#AE3115`, 보조 글자는 `#505E7F`를 사용한다. 표 머리글과 코드 배경은 원본 보조 버튼에도 쓰인 `#FFF2ED`로 구성했다.
- 본문을 수정하면 `portfolio-short.md`와 HTML의 설명·수치·링크를 함께 맞춘다. 프로젝트별 스타일 파일은 각 디렉토리에서 관리한다.
- Markdown 본문과 근거 링크 40개의 HTML 반영, 태그 구조, ID 중복, 내부 이동·ARIA 참조, 스타일 파일 경로를 정적으로 확인했다. 모바일 표 스크롤·반응형 카드·인쇄 스타일은 GRAB 기반 CSS에 포함했다.
- 로컬 Edge의 헤드리스 렌더링을 시도했으나 GPU 프로세스 접근 오류와 렌더링 지연으로 화면 캡처를 완료하지 못했다. 실제 브라우저의 모바일·데스크톱 표시와 인쇄 결과는 시각 검증하지 못했다.

## 선택 이유 추가와 구성 정리 (2026-10-31)

- 사례 ①·②에 "선택 이유 (사후 비교 분석)"를 추가했다. 당시 대안 검토 기록이 없으므로 구현을 근거로 한 비교 분석임을 본문에 밝혔다. 실제로 검토·실험한 과정처럼 쓰지 않는다.
- 로컬 클론(`C:\workspace\AIBE7-Project2-Team04`, `git fetch`로 기준 커밋 확보)에서 기준 커밋 `310120e`를 확인했다.
  - `@Version` 사용 없음, Redisson·`tryLock` 등 분산 락 없음 → 낙관적 락·분산 락은 대안 비교로만 쓴다.
  - `@Lock(PESSIMISTIC_WRITE)`: `MatchProposalRepository`, `MatchRequestRepository`. `MatchProposalLifecycleService`는 `entityManager.refresh(request, PESSIMISTIC_WRITE)`를 사용한다.
  - `lock_timeout`, `jakarta.persistence.lock.timeout` 설정 없음 → 사례 ① 한계로 적었다.
  - `MatchProposalInteractionService.decide`: 제안 잠금 → 같은 결정이면 기존 응답 → `PENDING`이 아니거나 만료면 409 → `DataIntegrityViolationException`·`PessimisticLockingFailureException`도 409로 변환.
  - `ChatRoom.match`, `UserReview.match`가 `Match`를 참조 → 사례 ②의 "채팅방과 후기가 참조하는 기록" 근거.
- 가독성 정리: 상단 핵심 구현을 사례 ①·② 두 개로 줄이고 결과가 먼저 보이게 고쳤다. 사례 ①의 오류 코드(`MATCHING_012`)와 표 뒤 반복 문단, 사례 ②의 PostGIS 위치 검색 문장을 뺐다.
- 후보 선정(기존 사례 ③)은 "그 밖의 작업"의 한 항목으로 줄였다. 본문에서 뺀 한계 두 가지는 후속 개선 후보로 여기에 남긴다.
  - 현재 후보 선정은 `styleTags`와 `aiKeywords`가 모두 있는 프로필만 점수 계산으로 넘긴다. 이 필터의 작성자(본인/팀원 PR #74)는 확인하지 않았다.
  - 매칭 요청 생성 중 AI 키워드 추출과 임베딩 호출이 DB 트랜잭션 안에서 동기로 실행된다. 부하테스트 개선 후보다.
  - 작성자 확인(2026-10-31, `git blame`, 기준 커밋): `RealtimeMatchRequestService.create`의 AI 동기 호출(L127-146, 커밋 `d7e21ddd`)과 요청자 프로필의 태그·AI 키워드 필수 검사(L81-90, 커밋 `327afbcb`)는 팀원 sang9831이 2026-09-02에 작성했다. 이 부분을 개선 사례로 쓰면 "팀원 코드의 병목을 프로젝트 종료 후 개인 포크에서 개선"으로 표현해야 하며, 본인 설계의 결함처럼 쓰지 않는다.
  - 요청 생성 단계에서 이미 요청자 본인에게 AI 키워드를 요구하므로, 후보 선정 단계의 같은 필터가 실제로 제외하는 경우는 생성 뒤 프로필이 바뀐 경우 등으로 좁을 수 있다. 영향 범위는 확인하지 않았다.
- HTML은 같은 내용으로 맞췄고, 핵심 구현 카드가 두 개가 되어 `portfolio.css`에 `.metric-grid.two-up`을 추가했다. 브라우저 시각 확인은 하지 않았다.

## 로고와 시스템 아키텍처 (2026-11-02)

- 로고: 기준 커밋의 `frontend/public/assets/maju-hankki-wordmark-v2.png`(2020×778, 투명 배경)를 사용했다. 서비스 헤더(`frontend/index.html` L185)가 쓰는 워드마크다. GRAB 워드마크와 여백을 맞추려고 글자 영역(249,188)–(1771,568)에서 약 40px 여백을 남기고 1603×461로 잘라 `assets/majuhankki-wordmark.png`에 저장했다. 색과 형태는 바꾸지 않았다. 공개 본문 Markdown은 GRAB과 같이 텍스트 제목을 유지하고, HTML 머리글에만 로고를 넣었다.
- 구성도: `assets/majuhankki-architecture.svg`(GRAB 구성도와 같은 1280×720 형식, Warm Table 색상)를 새로 그렸고, `architecture.html`에 같은 SVG를 인라인으로 넣었다. 그림을 고치면 두 파일을 함께 맞춘다. 설명과 근거 링크는 `architecture.md`에 있다.
- 기준 커밋 `310120e`를 임시 디렉토리에 클론해 다음을 확인했다.
  - 호스팅: Render·Cloudflare Pages·Supabase·Aiven은 README 배포 구성 표가 근거다. 저장소에 플랫폼 설정 파일과 `.github` 워크플로가 없어 배포 경로는 그리지 않았다. `application-prod.yml` 주석과 `Dockerfile`은 Render를 전제로 한다.
  - 연결: `WebSocketConfig`(`/ws-chat`, SockJS, `enableSimpleBroker`), `OAuthAuthorizationCodeService`(교환 코드를 Valkey에 저장), `S3FileStore.putObject`(서버 경유 업로드), `KakaoRegionPinValidator`(`coord2regioncode`, 매칭 요청 생성 시 호출), `application-ai.yml`(제공자 기본값 `none`), `application-prod.yml`(Prometheus 노출 끔), `docker-compose.yml`(로컬 Redis·Prometheus·Grafana), `frontend/index.html`(Kakao Maps SDK, Tailwind·SockJS·STOMP CDN).
  - `@Scheduled`: `MatchProposalExpirationScheduler`, `MatchProposalRetryScheduler`, `RealtimeMatchWaitingReconciliationService`, `RefreshTokenCleanupScheduler`.
- 팀이 만든 구성도 `docs/assets/image/시스템아키텍쳐.jpg`가 저장소에 있다. 참고만 했고, 코드 기준으로 다시 그렸다. 팀 그림에는 저장소가 "Redis"로 표시되어 있으나 배포 구성은 Aiven for Valkey이고 Redis는 로컬 대체용이다.
- 구성도는 팀 전체 서비스의 맥락이다. 구성 요소별 `git blame` 작성자를 일부 확인했지만(예: `S3FileStore`는 팀원), 구성도와 `architecture.md`에는 개인 기여를 표시하지 않았다.
- 검증: 로컬 Edge 헤드리스로 SVG 단독(1280×720)과 `portfolio-short.html` 데스크톱 화면을 캡처해 로고·구성도 표시, 라벨 겹침이 없음을 확인했다. 390px 폭 캡처는 헤드리스 최소 창 폭 때문에 GRAB 페이지도 오른쪽이 잘려 판단에 쓰지 않았다. 실제 모바일 기기와 인쇄 결과는 확인하지 않았다.

## 이력서 Notion 동기화 (2026-11-05)

- 사용자 요청에 따라 [Notion 이력서](https://app.notion.com/p/23fbd16e2f7383a4af8481e291d67fe4)의 내용을 `resume/resume.md`와 `resume/resume.html`에 반영했다. 원문 최종 편집 시각은 2026-11-05 15:41:51 KST이며, 자격증은 원문에 연결된 데이터베이스의 두 항목을 조회했다.
- 마주한끼의 매칭 경합 6종 × 100회(총 600회), Valkey 재시작 후 요청 205건의 복구 102건·만료 103건, 추가 보정 9회는 Notion 원문에 기재된 결과를 옮긴 것이다. 이번 동기화에서는 해당 실행의 기준 커밋·명령·환경·실행일·로그를 별도로 확인하거나 테스트를 재실행하지 않았다.
- 기존 포트폴리오의 경쟁 테스트 6종 × 21회 기록과 새 이력서의 600회 기록은 실행 근거가 대조되지 않았다. 기존 검증 기록을 덮어쓰거나 포트폴리오 수치를 확대하지 않았으며, 포트폴리오에도 새 결과를 반영하려면 각 실행 자료를 확인해야 한다.
- 동기화 검증은 이력서 Markdown·HTML 본문 64개 항목, 링크, 로컬 사진 경로, HTML 태그·ID·ARIA 참조와 기존 스타일 유지 여부를 정적으로 확인한 범위다.

## 로컬 신뢰성 검증 보고서 대조 (2026-11-05)

- 사용자 안내로 `C:\workspace\majuhankki`의 [매칭 신뢰성 검증 결과](/C:/workspace/majuhankki/performance/매칭_신뢰성_검증_결과.md), `performance/README.md`, 실행 스크립트와 동시성·복구 테스트 코드를 읽었다. 해당 작업 트리는 깨끗하며 HEAD와 보고서의 마지막 변경 커밋은 `ee7d2ba113e892bb92dd693223dcfc58d3acb95b`다. 위 Notion 동기화 절의 미대조 상태는 이 조사 이전의 기록이다.
- 기존 포트폴리오의 21회는 2026-10-31 기준 커밋 `310120e`에서 6개 시나리오 묶음을 21회 실행한 기록(총 126개 시나리오 실행)이다. 새 보고서의 600회는 2026-11-05에 6종을 각각 100회 실행한 별도 기록이다. 서로 다른 실행 시점·코드 기준·반복 횟수이므로 어느 한쪽의 단순 오기로 보지 않는다.
- 새 보고서는 600회 모두 성공하고 중복 매칭·상태 불일치·데드락·타임아웃이 0건이었다고 기록한다. 현재 테스트 코드의 `@RepeatedTest(100)` 6개, 작업 스레드 2개, 결과 검증과 집계 코드를 대조했다. 600회는 동시 접속자 수가 아니며, 이 동시성 클래스의 캐시와 WebSocket은 mock이다.
- Valkey 재시작 후 요청 205건의 복구 102건·만료 103건·미처리 0건은 보고서와 복구 테스트의 fixture 및 검증 조건에 일치한다. 각 시나리오의 전체 205건 순회는 배치 100 → 100 → 5로 구성된다. 추가 보정 9회는 배치 메서드 9회 호출, 즉 전체 205건을 3차례 더 순회한 별도 멱등성 검증이며 재시작을 9회 반복했다는 뜻이 아니다.
- 새 실험에는 Redis 명령 타임아웃 적용 수정이 포함된다. 실험 설정은 명령 타임아웃 200ms, 스케줄러 초기 지연 1시간, 보정 서비스 직접 호출이다. 기존 포트폴리오의 기준 커밋을 유지한 채 새 수치만 치환하지 않는다. 새 검증을 소개할 때는 별도 코드 기준과 환경을 연결한다.
- 확인 수준: 보고서의 결과 기록과 현재 테스트 코드·실행 스크립트의 일치를 확인했다. 보고서가 원본 보존 경로로 안내한 `performance/results/reliability-measure/`와 `reliability-regression/`의 XML·CSV·실행 로그는 현재 이 로컬 저장소에 없다. 이번 조사에서 실행 성공을 원본 로그로 독립 확인하거나 테스트를 재실행한 것은 아니다.

## 최신 검증 결과의 포트폴리오 반영 (2026-11-05)

- 사용자 요청으로 프로젝트별 `portfolio-short.md/html`과 통합 `portfolio/portfolio.md`·`portfolio/index.html`에 600회 검증을 대표 결과로 반영했다. 기존 21회 및 잠금 제거 A·B 각 10회는 2026-10-31의 이전 비교 실험으로 구분해 보존했다.
- 실제 Valkey 재시작 후 205건의 복구 102건·만료 103건·미처리 0건과 별도 반복 보정 검증을 사례 ②에 추가했다. 운영 복구 시간·동시 접속 처리량으로 확대하지 않았다.
- 구현 기준은 팀 저장소의 `310120e`로 유지하고, 프로젝트 종료 후 검증 기준은 개인 공개 저장소 `ImJhoon/majuhankki`의 `ee7d2ba113e892bb92dd693223dcfc58d3acb95b`로 분리했다. 이 검증 버전에 Redis 명령 타임아웃 적용 수정이 포함됨을 명시했다.
- GitHub 연결 도구로 개인 저장소의 공개 상태, 고정 SHA의 [측정 보고서](https://github.com/ImJhoon/majuhankki/blob/ee7d2ba113e892bb92dd693223dcfc58d3acb95b/performance/%EB%A7%A4%EC%B9%AD_%EC%8B%A0%EB%A2%B0%EC%84%B1_%EA%B2%80%EC%A6%9D_%EA%B2%B0%EA%B3%BC.md), 동시성 테스트·복구 테스트·실행 스크립트의 접근과 내용을 확인했다. 공개 본문의 최신 근거는 해당 고정 SHA 링크로 연결했다.
- 이번 작업은 문서 반영과 보고서·코드 대조이며 서비스 테스트를 재실행하지 않았다. 원본 XML·CSV·로그의 부재와 직접 재실행하지 않았다는 확인 범위는 위 대조 기록을 유지한다.

## 확인이 필요한 항목

- [x] 테스트 내용을 본문에 다시 넣을지 결정 → 2026-10-31 사례 ①에 다시 넣었다(실행은 Claude). 이전 메모: 다시 넣으려면 직접 재실행하고 시나리오를 검토한 뒤 실행자 항목과 AI 활용 서술을 정한다. (예: "테스트 코드는 AI로 작성하고, 시나리오 검토와 실제 DB 실행 검증은 직접 했습니다.") 그 전까지 사례 ①에는 검증 절이 없다.
- [ ] 배포 DB의 사용 기록이 실사용자 것인지, 팀 시연·테스트 데이터인지. 확인 전에는 운영 성과로 쓰지 않는다.
- [x] DB–Valkey 상태 보정을 사례 ②로 분리했다. 후보 선정은 사례 ③으로 작성했다가 2026-10-31 "그 밖의 작업"으로 줄였다. 후기와 OAuth 교환 코드는 보조 사례에 반영했다. 이 체크는 문서 반영 완료를 뜻하며 실행 검증 완료를 뜻하지 않는다.
- [ ] 후보 선정·보정·후기 정책의 당시 선택 이유와 실제 검토한 대안은 추가 확인이 필요하다. 확인 전에는 비교 실험이나 의사결정 과정을 경험으로 쓰지 않는다.
- [ ] 보정의 장애 복구 시간, 추천 품질, 사용자 이용 결과는 측정 근거가 없다. 별도 실행·측정 없이 정량 성과로 확대하지 않는다.
