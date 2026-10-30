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
| `SET NX` 예약, Lua 활성화, 커밋 후 Valkey 반영 | `0537105` #44 (2026-08-27) |
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

## 409 응답 이후 클라이언트 동작

사용자도 기억하지 못해 코드로 확인했다. 기준 커밋의 `frontend/src/pages/matching-request.js`이며, L592-634와 L250-300 모두 git blame상 한재훈이 작성했다.

- `handleDecision`: 수락·거절 응답이 409면 현재 제안을 닫는다. 이어 "응답 시간이 끝났거나 다른 매칭이 먼저 확정되었습니다. 현재 상태를 다시 확인합니다."를 보여 주고 `refreshFlow({ checkResult: true })`를 호출한다.
- `refreshFlow`: 현재 요청과 현재 제안을 다시 조회한다. 요청이 없고 제안이 닫혔으면 최근 매칭 결과를 조회한다. `MATCHED`면 `applyMatchResult`로 결과를 반영하고, 아니면 반경 확장을 제안하거나 "주변에 후보를 찾지 못했어요" 안내를 보여 준다.
- 실제 브라우저에서 이 동작을 확인하지는 않았다. 코드에서 읽은 흐름이다.

## 코드와 문서가 어긋나는 부분

- 랭킹: `pairScore = min(양방향 점수)`를 계산하고 공식 이름도 `..._BIDIRECTIONAL_MIN_V1`이지만, 정렬은 `sourceToTargetScore` 기준이다. "양방향 최소 점수로 정렬"이라고 쓰지 않는다.
- Redis GEO: `match:waiting:geo`에 기록하고 정리만 하며, 후보 탐색은 PostGIS `ST_DWithin`이 한다. "Valkey로 위치 검색"이라고 쓰지 않는다.
- 제안 응답 제한: 기준 커밋은 `PROPOSAL_TTL = 180초`다. #50 커밋 메시지의 "15초"는 이후 바뀌었다.
- 트러블슈팅 문서 `docs/troubleshooting/실시간_매칭_동시성_이슈.md`는 팀원이 2026-09-03에 썼다. 이 문서의 "증상"은 실제 관찰이 아니므로 인용하지 않는다.

## 확인이 필요한 항목

- [ ] 테스트 내용을 본문에 다시 넣을지 결정. 다시 넣으려면 직접 재실행하고 시나리오를 검토한 뒤 실행자 항목과 AI 활용 서술을 정한다. (예: "테스트 코드는 AI로 작성하고, 시나리오 검토와 실제 DB 실행 검증은 직접 했습니다.") 그 전까지 사례 ①에는 검증 절이 없다.
- [ ] 배포 DB의 사용 기록이 실사용자 것인지, 팀 시연·테스트 데이터인지. 확인 전에는 운영 성과로 쓰지 않는다.
- [ ] 다른 사례 후보 (아직 미작성):
  - 후보 탐색: 양방향 반경 필터, 결정론적 정렬, AI 장애 시 태그 점수로 대체
  - 인증: Refresh Token 해시·회전·재사용 탐지, 2분 일회성 OAuth 교환 코드. GRAB 사례와 겹치므로 차이를 정리한 뒤 쓴다.
  - 후기: 다시한끼 지수 `(2.5 + 확실히 + 0.5×아마도) / (5 + n) × 100`, 산식 버전 관리, 감사 이벤트 가명 처리
