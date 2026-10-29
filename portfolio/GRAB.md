# GRAB 포트폴리오

출처: [Linear HAN-6 — GRAB 포트폴리오 초안 — 인증 세션·이메일 인증·프로필 이미지 정합성](https://linear.app/hanjaehoon/issue/HAN-6/grab-포트폴리오-초안-인증-세션이메일-인증프로필-이미지-정합성)

## 초안 안내

이 문서는 Notion·웹 포트폴리오로 옮길 수 있는 **본문 초안**이다. ImJhoon의 개인 기여를 중심으로 **기능·요구사항 → 문제 → 설계 이유 → 처리 흐름 → 핵심 코드 → 검증·한계** 순서로 작성했다.

* 작성 기준일: 2026-10-25
* 코드 기준: [main의 223b69778 커밋](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/commit/223b697783bbd712c68d359999f42ad141353802>)
* 코드는 해당 커밋에서 실제로 발췌했으며, 일부 주석만 생략했다.
* 검증 표는 저장소의 테스트 코드와 검증 조건을 확인해 작성했다. 이번 작업에서 테스트를 새로 실행하지 않았으며 최신 실행 결과·캡처는 후속 보완 항목이다.
* 대안 표는 현재 구현을 설명하는 비교 분석이다. 당시 실제로 검토했던 대안과 결정 배경은 본인의 경험으로 보강한다.

---

## 1. 프로젝트 소개

**GRAB — 기다림은 짧게, 기회는 단 한 번.**

정해진 시간에 한정 수량 상품을 판매하고 WISH부터 주문·결제·배송까지 연결하는 DROP 커머스 서비스입니다.

| 항목 | 내용 |
| -- | -- |
| 개발 기간 | 2026.09 \~ 진행 중 |
| 팀 규모 | 4명 |
| 담당 역할 | 회원 인증·인가, 가입·소셜 로그인, 회원·프로필 관리, 백엔드 초기 환경 구성 |
| 주요 기술 | Java 17, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, Redis, Docker, GitHub Actions |
| 저장소 | [GRAB GitHub](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3>) |

저는 로그인 이후의 세션 갱신과 폐기, 여러 탭의 동시 요청, SMTP 장애, DB와 외부 저장소의 처리 순서처럼 **예외 상황에서의 인증·회원 기능 동작**을 고려해 구현했습니다.

### 담당 범위

* HttpOnly 쿠키 기반 JWT 인증, CSRF·CORS, Redis 기반 Refresh Token 관리
* 이메일 인증 회원가입, 카카오·구글 OIDC 로그인
* 회원 조회, 닉네임·비밀번호 변경, 프로필 이미지 등록·교체·삭제
* Docker Compose 개발 환경, GHCR 이미지 배포 워크플로우, 인증 API 명세 작성·관리

서비스 전체의 주문·결제·재고 정합성은 팀 프로젝트의 기능이며, 개인 기술 사례는 인증·회원 영역을 중심으로 구성했습니다.

### 담당 영역의 구성

| 구성 요소 | 책임 |
| -- | -- |
| Spring Security·인증 API | 인증 필터, 권한 검사, CSRF·CORS, 세션 발급·갱신·폐기 |
| PostgreSQL | 회원 정보·계정 상태·프로필 이미지 URL |
| Redis | 인증 세션, 토큰 교체 이력, 이메일 인증 상태·요청 제한·가입 컨텍스트 |
| SMTP·메일 실행기 | 외부 메일 발송과 지연·실패 처리 |
| Supabase Storage | 이미지 객체 업로드·삭제 |

---

## 2. 핵심 사례 — 동시 재발급과 재사용을 고려한 인증 세션 관리

### 2.1 기능과 요구사항

Access Token이 만료되면 Refresh Token으로 인증 상태를 갱신합니다. 재발급 시 Refresh Token도 교체하고, 로그인 이후 변경된 회원 상태와 권한을 반영하도록 구성했습니다.

이 기능은 다음 조건을 지켜야 했습니다.

* 같은 Refresh Token의 동시 교체는 한 요청만 성공해야 합니다.
* 정상적인 동시 요청이 즉시 세션 전체 폐기로 이어지지 않아야 합니다.
* 교체된 토큰이 유예 시간(교체 후 10초)이 지난 뒤 재사용되면 해당 세션의 현재 Refresh Token을 폐기해야 합니다.
* 갱신을 반복해도 절대 만료 시점은 연장되지 않아야 합니다.
* Redis 장애를 잘못된 인증 정보와 구분해야 합니다.

### 2.2 기술적 문제

GRAB은 정해진 오픈 시각에 요청이 몰리는 서비스라, 15분인 Access Token 만료 주기와 겹치면 같은 세션의 재발급 요청이 동시에 들어오는 상황이 흔할 것이라고 판단했습니다. Refresh Token Rotation을 도입하면서, 이 동시 요청을 탈취와 구분하지 못하면 정상 사용자가 로그아웃된다는 점을 먼저 문제로 정의했습니다.

예를 들어 같은 로그인 세션을 사용하는 두 탭이 동시에 재발급을 요청할 수 있습니다. 요청 A가 먼저 토큰을 교체하면 요청 B는 이미 교체된 토큰을 제출하게 됩니다. 이를 곧바로 탈취로 판단해 세션을 폐기하면 정상 사용자의 로그인 상태도 끊길 수 있습니다.

조회·삭제·새 토큰 저장을 개별 명령으로 나누면 동시 요청이 같은 토큰을 유효하다고 판단하거나, 교체 중간 상태가 다른 요청에 노출될 수 있습니다.

### 2.3 설계와 선택 이유

**원자적 교체와 재사용 정책을 분리했습니다.** 교체 연산은 Redis Lua로 처리하고, 사용된 토큰의 기록과 세션의 현재 토큰 해시를 관리해 이후 재사용에 대응했습니다.

| 접근 | 고려할 점 | 현재 구현 |
| -- | -- | -- |
| 조회·삭제·저장을 개별 명령으로 실행 | 명령 사이에 다른 요청이 끼어들 수 있음 | Lua로 교체 연산을 묶음 |
| 교체된 토큰의 재사용마다 세션 폐기 | 정상적인 다중 탭 요청도 세션 폐기로 이어질 수 있음 | 10초 안에는 요청을 거부하되 세션 유지 |
| 교체된 토큰을 단순 만료 처리 | 이후 재사용 시 현재 세션과 연결하기 어려움 | 사용 기록과 세션 → 현재 토큰 해시 관리 |

**유예 시간**은 토큰 교체 후 10초 동안, 이전 토큰이 다시 들어와도 탈취로 판정하지 않는 기간입니다. 요청은 거부하되 세션은 폐기하지 않습니다. **유예는 이전 토큰의 재발급을 허용하는 정책이 아닙니다.**

같은 브라우저에서 동일한 쿠키 범위를 사용하는 탭은 인증 쿠키를 공유합니다. 따라서 탭 A의 재발급 응답으로 새 쿠키가 브라우저에 저장된 뒤에는, 탭 B가 원래 API 요청을 다시 보낼 때도 새 Access Token 쿠키가 자동으로 전송됩니다. 이때 B가 이전 Refresh Token으로 보낸 재발급 요청이 거부되었다는 이유만으로 세션을 폐기하면, A가 발급받은 현재 Refresh Token까지 사용할 수 없게 됩니다. 이를 방지하기 위해 유예 시간 안에는 해당 요청만 거부하고 현재 세션은 유지했습니다.

경쟁에서 진 B의 재발급 요청에는 **401(INVALID_TOKEN)**을 반환합니다. 클라이언트 연동에서는 이 응답만으로 즉시 로그아웃하지 않고, 처음 Access Token 만료로 실패했던 **원래 API 요청을 1회 재시도**하도록 처리되어 있습니다. 재발급 요청 자체를 반복하는 방식은 아니며, A의 새 쿠키가 이미 저장돼 있다면 재시도에는 새 Access Token 쿠키가 자동으로 실립니다. 재시도한 API 요청도 401이면 세션 만료로 처리합니다. 근거: [클라이언트 세션 연동 코드](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Frontend/src/api/auth/session.ts>)

| 상황 | 요청 결과 | 현재 세션 |
| -- | -- | -- |
| 유효한 토큰의 첫 교체 | 새 토큰 발급 | 유지 |
| 다른 요청이 먼저 교체한 경쟁 요청 | 401(INVALID_TOKEN), 재발급 거부 | 경쟁 실패 자체로는 폐기하지 않음 |
| 교체 직후 10초 안의 이전 토큰 재사용 | 401(INVALID_TOKEN), 재발급 거부 | 유지 |
| 유예 이후 이전 토큰 재사용 | 401(INVALID_TOKEN), 재발급 거부 | 해당 세션의 현재 Refresh Token 폐기 |
| Redis 접속 실패 | 서버 오류 | 인증 쿠키 삭제 응답을 보내지 않음 |

교체 시각과 유예 판정은 Redis 시계를 사용합니다. 비활동 만료와 로그인 시점의 절대 만료를 구분하고, 재발급 시 원래 세션의 절대 만료 시점을 이어받습니다.

### 2.4 처리 흐름

1. 쿠키의 Refresh Token으로 세션을 조회합니다.
2. 조회되지 않으면 사용 기록을 확인하고 유예·재사용 정책을 적용합니다.
3. 회원의 활성 상태와 최신 권한을 확인합니다.
4. 새 Access Token과 Refresh Token을 준비합니다.
5. Lua로 기존 Refresh Token을 원자적으로 교체합니다.
6. 교체에 성공한 요청만 새 토큰과 쿠키 만료 시간을 응답합니다.

### 2.5 핵심 메서드와 코드

**Redis 키 구성**

| 키 | 값 | 역할 |
| -- | -- | -- |
| 토큰 키 | 세션 정보(JSON) | 현재 유효한 Refresh Token. 토큰 원문 대신 해시로 키를 만듦 |
| 세션 키 | 현재 토큰 해시 | 세션의 현재 토큰을 찾아 폐기할 때 사용 |
| 사용 기록 키 | sessionId, rotatedAt | 교체된 이전 토큰이 속한 세션과 교체 시각. 재사용 판정에 사용 |

**TokenRefreshService.refresh — 확인 작업과 상태 변경의 순서 제어**

```java
public TokenRefreshResult refresh(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
        log.debug("토큰 재발급 거부: reason=missing_cookie, sessionId=null");
        throw new BusinessException(CommonErrorCode.INVALID_TOKEN);
    }

    RefreshTokenSession session = refreshTokenRepository.find(rawToken)
            .orElseThrow(() -> rejectMissingToken(rawToken));

    Optional<LoginUser> activeUser = activeUserLookupService.findActive(session.userId());
    if (activeUser.isEmpty()) {
        refreshTokenRepository.revokeSession(session.sessionId());
        throw rejected("user_inactive", session.sessionId());
    }
    LoginUser user = activeUser.get();

    Set<AuthRole> roles = authRoleCalculator.calculate(user.publicId(), user.role());
    IssuedAccessToken accessToken = jwtProvider.issue(user.publicId(), roles);
    String newRefreshToken = refreshTokenGenerator.generate();

    Duration refreshTokenMaxAge = rotate(rawToken, newRefreshToken, session)
            .orElseThrow(() -> rejected("race_lost", session.sessionId()));

    log.info("토큰 재발급 성공: userId={}, sessionId={}", user.publicId(), session.sessionId());
    return new TokenRefreshResult(accessToken, newRefreshToken, refreshTokenMaxAge);
}
```

회원 상태·권한 확인과 새 토큰 준비를 마친 뒤 기존 토큰을 교체합니다. 교체 이전에 DB 오류 등이 발생하면 기존 토큰이 남아 재시도할 수 있게 하기 위한 순서입니다. 실제 쿠키 응답은 교체 성공 이후에 생성됩니다.

Redis 오류를 인증 실패로 일괄 변환하지 않은 것도 같은 맥락입니다. 인프라 장애가 자격 증명 오류로 전달되면 불필요한 세션 정리를 유발할 수 있어 서버 오류로 구분했습니다.

**TokenRefreshService.rejectMissingToken — 없는 토큰의 사유 분류**

```java
private BusinessException rejectMissingToken(String rawToken) {
    RefreshTokenReuseCheck check = refreshTokenRepository.checkReuse(rawToken);
    return switch (check.status()) {
        case NOT_FOUND -> rejected("not_found", null);
        case WITHIN_GRACE -> rejected("reuse_within_grace", check.sessionId());
        case REUSE_DETECTED -> {
            log.warn("토큰 재발급 거부: reason=reuse_detected, sessionId={}. 세션을 폐기했습니다", check.sessionId());
            yield new BusinessException(CommonErrorCode.INVALID_TOKEN);
        }
    };
}
```

토큰 키가 없으면 곧바로 거부하지 않고, 교체된 적 있는 토큰인지 재사용 Lua로 확인합니다. 세 경우 모두 같은 `INVALID_TOKEN`으로 응답해 외부에는 사유를 드러내지 않습니다. 사유는 로그로만 구분하며, 탈취가 의심되는 `reuse_detected`만 WARN으로 남깁니다.

**refresh-token-reuse.lua — 유예 판정과 세션 폐기를 한 번에 처리**

```lua
local marker = redis.call('HMGET', KEYS[1], 'sessionId', 'rotatedAt')
local sessionId, rotatedAt = marker[1], marker[2]
if not sessionId or not rotatedAt then
    return {'NOT_FOUND'}
end

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
if now - tonumber(rotatedAt) < tonumber(ARGV[1]) then
    return {'WITHIN_GRACE', sessionId}
end

local sessionKey = ARGV[3] .. sessionId
local currentHash = redis.call('GET', sessionKey)
if currentHash then
    redis.call('DEL', ARGV[2] .. currentHash)
end
redis.call('DEL', sessionKey)
return {'REUSE_DETECTED', sessionId}
```

* `KEYS[1]`은 제출된 토큰의 사용 기록 키, `ARGV[1]`은 유예 시간(10초, 밀리초 단위)입니다.
* 사용 기록이 없으면 교체된 적 없는 토큰이므로 `NOT_FOUND`입니다.
* 교체 시각과 현재 시각을 모두 Redis 시계로 계산해, 애플리케이션 서버 간 시계 차이가 유예 판정에 영향을 주지 않게 했습니다.
* 유예가 지났으면 세션 키에서 **폐기 시점의 현재 토큰 해시**를 읽어 그 토큰과 세션 키를 함께 삭제합니다.
* 확인과 폐기를 한 스크립트로 묶었기 때문에, 판정 직후 같은 세션의 교체가 끼어들어도 새로 발급된 토큰까지 폐기 대상이 됩니다.
* 현재 토큰 키는 세션 키의 값으로 만들어지므로 `KEYS`에 미리 선언할 수 없습니다. 이 때문에 단일 Redis를 전제로 하며, 2.6의 Redis Cluster 한계로 이어집니다.

**refresh-token-rotate.lua — 교체 작업의 원자성** (사용 기록 저장 부분 일부 생략)

```lua
-- KEYS[1]: 이전 토큰 키, KEYS[2]: 새 토큰 키, KEYS[3]: 이전 토큰의 사용 기록 키, KEYS[4]: 세션 키
if redis.call('DEL', KEYS[1]) == 0 then
    return 0
end

redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3])
redis.call('SET', KEYS[4], ARGV[2], 'EX', ARGV[3])
-- Redis TIME으로 rotatedAt을 계산한 뒤
redis.call('HSET', KEYS[3], 'sessionId', ARGV[4], 'rotatedAt', rotatedAt)
```

* 이전 토큰 키의 DEL 결과가 1인 요청만 새 토큰을 저장하므로, 같은 토큰의 동시 교체는 한 요청만 성공합니다.
* 이전 키 삭제와 새 키 저장이 한 스크립트에서 일어나 두 토큰이 동시에 유효한 순간이 없습니다.
* 여기서 남긴 사용 기록이 위 재사용 Lua의 판정 근거가 됩니다.

근거: [재발급 서비스](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/main/java/org/example/grab/domain/auth/service/TokenRefreshService.java>), [교체 Lua](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/main/resources/redis/refresh-token-rotate.lua>), [재사용·폐기 Lua](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/main/resources/redis/refresh-token-reuse.lua>), 본인 [PR #101](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/pull/101>)

### 2.6 검증과 한계

회전 저장소 통합 테스트는 실제 Redis를 사용합니다. API의 Redis 장애 시나리오는 의존성을 대체해 오류를 유발하는 테스트이므로, 실제 운영 Redis 중단 실험과 구분합니다.

| 확인한 테스트 | 검증 조건 |
| -- | -- |
| concurrentRotationSucceedsOnlyOnce | 같은 토큰의 동시 교체 중 한 요청만 성공하고 새 키도 하나만 남음 |
| reuseWithinGraceKeepsSession | 유예 안의 재사용에서는 현재 토큰 유지 |
| reuseAfterGraceRevokesSession | 유예 이후 재사용에서는 현재 토큰·세션 키 삭제 |
| revokeAfterInterleavedRotationRemovesNewToken | 조회 후 교체가 끼어들어도 세션 폐기가 새 토큰까지 대상으로 함 |
| respondsWithInternalServerErrorOnRedisFailure | Redis 장애 시 서버 오류를 반환하고 인증 쿠키를 삭제하지 않음 |

근거: [실제 Redis 회전 통합 테스트](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/test/java/org/example/grab/domain/auth/repository/RefreshTokenRotationIntegrationTest.java>), [Redis 장애 API 테스트](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/test/java/org/example/grab/domain/auth/controller/TokenRefreshRedisFailureTests.java>)

**남은 한계와 개선 방향**

* 10초는 정책값입니다. 실제 탭 간 요청 간격과 네트워크 지연을 측정해 적정성을 검토할 수 있습니다.
* 유예 안의 재사용도 거부하므로, 응답 유실 후 이전 토큰으로 새 토큰을 다시 받아오는 기능은 제공하지 않습니다.
* 세션 폐기는 Refresh Token 기준입니다. 이미 발급된 Access Token은 즉시 무효화되지 않지만, 만료 시간이 15분이라 폐기 이후의 노출은 최대 15분으로 제한됩니다.
* 현재 Lua의 키 배치·동적 키 접근은 Redis Cluster에 그대로 적용할 수 없어 클러스터 도입 시 재설계가 필요합니다.

---

## 3. 보조 사례 — 이메일 인증 요청 제한과 SMTP 장애 대응

### 3.1 기능과 기술적 문제

회원가입 전에 이메일 소유를 확인하도록 인증 코드를 발송하고, 확인 성공 후 가입 컨텍스트를 발급했습니다.

외부 SMTP는 응답이 느리거나 연결에 실패할 수 있습니다. 동시에 반복 발송과 인증 코드 무차별 대입을 제한하고, 응답·저장·로그에서 민감정보 노출을 줄여야 했습니다.

### 3.2 설계와 선택 이유

* 인증 코드를 HMAC으로 저장하고 이메일·IP별 발송 제한과 이메일별 재발송 간격을 적용했습니다.
* 가입 여부와 관계없이 코드 요청 흐름을 같게 두고, 코드 확인으로 이메일 소유가 확인된 뒤 가입 가능 여부를 검사했습니다.
* 코드를 먼저 저장하고 발송 작업을 제출해, Redis 저장 실패 시 사용할 수 없는 코드가 발송되지 않게 했습니다.
* 메일 전용 실행기로 SMTP 작업을 넘겨 요청 스레드가 SMTP 응답을 기다리지 않도록 했습니다.
* 제한된 대기열과 타임아웃으로 지연 작업의 누적을 제한하고 작업 제출 거부와 발송 실패를 처리했습니다.

동기 발송은 결과 전달이 단순하지만 요청 시간이 SMTP 응답에 영향을 받습니다. 현재 구현은 비동기 발송을 선택하고 API 응답과 실제 메일 도착을 구분하는 정책을 사용했습니다.

### 3.3 처리 흐름

**발송:** IP 한도 → 재발송 간격 → 이메일 한도 → 코드 생성·저장 → 전용 실행기에 발송 제출.

**확인:** 코드 조회 → 비교 전 시도 횟수 증가 → 해시 비교 → 코드 소비 → 가입 가능 여부 확인 → 가입 컨텍스트 발급.

코드 확인 시도를 5회로 제한하고, 코드를 먼저 소비한 요청만 다음 단계로 진행하도록 구성했습니다.

### 3.4 핵심 메서드와 코드

**EmailVerificationService.requestCode — 저장 성공 후 발송 제출**

```java
public void requestCode(String email, String ipAddress) {
    checkSendLimit(email, ipAddress);

    String code = codeGenerator.generate();
    codeRepository.save(email, code);
    emailDispatcher.dispatch(mailFactory.create(email, code));
}
```

한도 검사에 실패하면 코드 저장과 발송을 하지 않습니다. 저장에 실패하면 발송 작업도 제출하지 않습니다. dispatch는 실제 메일 수신을 기다리는 호출이 아닙니다.

**AsyncEmailDispatcher.dispatch — 작업 제출과 거부 처리**

```java
public void dispatch(EmailMessage message) {
    try {
        authMailTaskExecutor.execute(() -> send(message));
    } catch (TaskRejectedException e) {
        log.warn("[AsyncEmailDispatcher.dispatch]메일 발송 대기열이 가득 차 발송하지 않음: {}", message);
    }
}
```

실행기에 직접 작업을 제출해 대기열 초과 시 TaskRejectedException을 이 진입점에서 처리했습니다. 실제 발송 예외는 실행기 안의 send 메서드에서 처리합니다. SMTP 오류 메시지에 주소가 포함될 수 있어 일반 발송 실패 로그는 원인 예외 이름 중심으로 남겼습니다.

근거: [이메일 인증 서비스](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/main/java/org/example/grab/domain/user/service/EmailVerificationService.java>), [비동기 발송 진입점](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/main/java/org/example/grab/domain/mail/service/AsyncEmailDispatcher.java>), 본인 [PR #63](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/pull/63>), [PR #71](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/pull/71>)

### 3.5 검증과 한계

메일 통합 테스트는 GreenMail 테스트 SMTP 서버를 사용합니다. 실제 Gmail·수신함 도착률이나 운영 발송 지연을 측정한 결과는 아닙니다.

| 확인한 테스트 | 검증 조건 |
| -- | -- |
| savesCodeThenDispatchesMail | 한도 확인·코드 저장·발송 제출 순서 |
| doesNotDispatchWhenSaveFails | Redis 저장 실패 시 메일 미발송 |
| rejectsWhenIpLimitExceeded / rejectsWithinResendInterval | 한도·간격 위반 시 후속 작업 차단 |
| deletesCodeOnFifthMismatch | 5번째 불일치 시 코드 폐기 |
| rejectsWhenCodeAlreadyConsumed | 먼저 소비된 코드로 가입 컨텍스트 미발급 |
| deliversThroughMailTaskExecutor | 메일 전용 풀에서 테스트 SMTP 서버로 발송 |
| logsFailureOnMailThreadWithoutThrowing | SMTP 실패가 호출자에게 발송 예외로 전파되지 않음 |

근거: [인증 단위 테스트](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/test/java/org/example/grab/domain/user/service/EmailVerificationServiceTest.java>), [GreenMail 발송 통합 테스트](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/test/java/org/example/grab/domain/mail/service/AsyncEmailDispatcherGreenMailTest.java>)

**남은 한계와 개선 방향**

* API 성공은 메일 수신 보장이 아닙니다. 대기열 초과·발송 실패 시 사용자가 재발송 간격 이후 다시 요청하는 정책입니다.
* 영속 큐와 자동 재시도를 통한 전달 보장은 현재 사례의 구현 범위에 포함하지 않습니다.
* 실행기 크기·대기열 용량은 실제 SMTP 지연과 요청량을 측정해 조정할 수 있습니다.
* 코드 확인의 여러 Redis 명령과 가입 컨텍스트 발급 전체를 하나의 원자적 작업으로 묶은 것은 아닙니다. 명령 사이 장애·재발송 경합은 추가 검증 대상입니다.
* 현재 저장소의 인증·알림 메일 실행기 분리는 팀의 후속 변경입니다. 개인 기여는 PR #63의 발송 기반과 PR #71의 인증 흐름을 기준으로 서술합니다.

---

## 4. 보조 사례 — 프로필 이미지 변경의 DB·외부 저장소 정합성

### 4.1 기능과 기술적 문제

회원은 프로필 이미지를 등록·교체·삭제할 수 있습니다. PostgreSQL은 URL을 저장하고 실제 객체는 Supabase Storage에서 관리합니다.

이전 객체를 먼저 삭제한 뒤 DB 변경이 롤백되면 회원 정보가 사라진 객체를 가리킬 수 있습니다. 같은 회원의 동시 교체에서는 각 요청이 읽은 이전 URL과 최종 URL의 관계도 달라질 수 있습니다.

### 4.2 설계와 선택 이유

DB의 URL 변경을 트랜잭션으로 처리하고, 이전 객체 삭제는 DB 커밋 이후의 이벤트 리스너로 분리했습니다. 같은 회원의 변경은 회원 행 잠금으로 직렬화하고 인증된 사용자 경로에 해당하는 URL인지 검사했습니다.

| 접근 | 고려할 점 | 현재 구현 |
| -- | -- | -- |
| 이전 객체 삭제 후 DB 변경 | 롤백 시 객체 복구가 필요함 | DB 커밋 성공 후 이전 객체 삭제 |
| 잠금 없이 이전 URL 조회 | 동시 요청의 이전 값과 삭제 대상이 엇갈릴 수 있음 | 회원 행 잠금 후 조회·변경 |
| 임의 URL 등록·삭제 | 타인 또는 다른 버킷의 경로를 다룰 수 있음 | 본인 경로·UUID·확장자·정규 URL 검사 |

### 4.3 처리 흐름

서명 업로드 URL 발급 → 클라이언트 업로드 → 등록 요청 → 활성 회원 잠금 조회 → URL 소유 경로 검증 → DB 변경·이벤트 발행 → 커밋 성공 → 이전 객체 삭제.

같은 URL의 재등록에서는 삭제 이벤트를 만들지 않습니다. 이미지가 없는 상태의 삭제도 성공하도록 멱등적으로 처리했습니다.

### 4.4 핵심 메서드와 코드

**ProfileImageCommandService.register — 잠금 안에서 이전 값 확정**

```java
public ProfileImageResponse register(Long userId, String imageUrl) {
    User user = lockActiveUser(userId, "프로필 이미지 등록");
    if (profileImageUrlVerifier.ownedObjectKey(user.getUuid(), imageUrl).isEmpty()) {
        throw new BusinessException(CommonErrorCode.VALIDATION_FAILED, List.of(
                new ErrorResponse.FieldError("imageUrl", "본인이 발급받은 프로필 이미지 URL이 아닙니다.")));
    }

    String previous = user.getProfileImageUrl();
    user.changeProfileImage(imageUrl);
    if (!Objects.equals(previous, imageUrl)) {
        discard(user, previous);
    }
    return ProfileImageResponse.from(user);
}
```

이 메서드는 @Transactional 범위에서 실행됩니다. lockActiveUser가 회원 행을 잠근 뒤 이전 URL을 읽고 새 URL로 변경합니다. 값이 바뀐 경우에만 정리 이벤트를 발행하며, discard는 이전 URL도 본인 경로인지 확인합니다.

**ProfileImageCleanupListener — 커밋 이후 외부 객체 삭제**

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onProfileImageDiscarded(ProfileImageDiscardedEvent event) {
    try {
        profileImageStorage.delete(event.objectKey());
    } catch (RuntimeException e) {
        log.warn("이전 프로필 이미지 삭제 실패: userId={}, cause={}", event.userId(), e.getClass().getSimpleName());
    }
}
```

DB 커밋 이후에만 리스너가 실행되므로 롤백된 변경 때문에 이전 객체를 삭제하지 않습니다. 외부 삭제 실패가 발생하면 이미 커밋된 DB 변경을 되돌리려고 하지 않고 경고를 남깁니다.

근거: [이미지 변경 서비스](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/main/java/org/example/grab/domain/user/service/ProfileImageCommandService.java>), [커밋 후 삭제 리스너](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/main/java/org/example/grab/domain/user/service/ProfileImageCleanupListener.java>), [URL 경로 검증](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/main/java/org/example/grab/domain/user/service/ProfileImageUrlVerifier.java>), 본인 [PR #163](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/pull/163>)

### 4.5 검증과 한계

API 통합 테스트는 DB·Redis를 사용하지만 ProfileImageStorage는 MockitoBean으로 대체합니다. 객체 삭제 호출의 시점과 대상을 검증하며, 실제 Supabase HTTP 통신·객체 삭제 성공은 별도 확인이 필요합니다.

| 확인한 테스트 | 검증 조건 |
| -- | -- |
| replacingDeletesPreviousAfterCommit | DB 커밋 후 이전 객체 삭제 |
| rollbackDoesNotDelete | 롤백 시 기존 URL·객체 유지 |
| concurrentPutsKeepFinalObject | 같은 회원의 동시 교체에서 최종 객체 유지 |
| reRegisteringSameUrlKeepsObject | 같은 URL 재등록 시 객체 미삭제 |
| rejectsNotOwnedUrl | 본인 경로에 해당하지 않는 URL 등록 거부 |
| swallowsDeleteFailure | 저장소 삭제 실패가 요청 결과에 예외로 전파되지 않음 |

근거: [프로필 이미지 API 통합 테스트](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/test/java/org/example/grab/domain/user/controller/ProfileImageApiIntegrationTest.java>), [삭제 리스너 단위 테스트](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/223b697783bbd712c68d359999f42ad141353802/Backend/src/test/java/org/example/grab/domain/user/service/ProfileImageCleanupListenerTest.java>)

**남은 한계와 개선 방향**

* 리스너는 현재 요청 스레드에서 동기 실행되므로 저장소 지연만큼 API 응답이 늦어질 수 있습니다.
* 삭제 실패는 로그에 남고 영속적인 재시도·고아 객체 자동 정리는 후속 개선 사항입니다.
* URL 검증은 본인 경로·형식 검사입니다. 실제 객체 존재나 모든 URL의 서버 발급 이력을 확인하는 기능은 아닙니다.
* 삭제 예정 객체의 재등록 경합 등 추가 시나리오를 고려해야 하므로 외부 저장소까지 포함한 완전한 원자성 보장으로 표현하지 않습니다.
* 프로필 이미지 화면 연동의 완료·병합·배포 상태는 최종 공개 전에 확인합니다.

---

## 5. 추가 담당 기능과 배운 점

### 소셜 로그인과 계정 정책

카카오·구글 OIDC 로그인에서 제공자 식별자로 기존 회원을 판정하고, 동일 이메일을 가진 다른 가입 방식의 계정을 자동 연결하지 않도록 처리했습니다. 신규 회원의 추가 입력은 가입 컨텍스트로 관리하고 로그인 토큰 발급을 공통화했습니다. 본인 [PR #126](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/pull/126>)

### 쿠키 인증과 CSRF·CORS

쿠키 인증 구조에 맞춰 CSRF 토큰 발급·검증과 허용 Origin의 credential 요청 정책을 적용했습니다. JWT 인증 시 CSRF 토큰이 반복 발급되는 문제는 인증 단계의 세션 전략과 연결해 분석했습니다. [PR #82](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/pull/82>), [PR #126](<https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/pull/126>)

### 프로젝트를 통해 정리한 판단 기준

* 데이터가 여러 저장소에 나뉘면 상태를 바꾸는 경계와 실패 이후의 동작을 먼저 정의해야 했습니다.
* 재사용·중복 요청을 처리하면서 보안 정책과 사용자 경험을 함께 고려했습니다.
* 비동기 실행과 커밋 후 처리는 서로 다른 선택입니다. 메일은 전용 실행기에 제출하고, 이미지 정리는 DB 커밋 후 동기로 실행한다는 차이를 구분했습니다.
* 구현한 보장과 테스트로 검증한 범위를 구분하고 남은 실패 시나리오를 개선 과제로 남겼습니다.

---

## 6. 최종 공개 전 보완할 항목

- [ ] 실제 개발 당시 문제·검토 대안·선택 이유와 본문이 일치하는지 확인한다.
- [ ] 10초 유예값의 선정 배경과 실제 관찰한 증상을 추가한다.
- [ ] 토큰 동시 요청·재사용, 메일 저장·발송, 프로필 변경·커밋 후 삭제의 흐름을 그림으로 보완한다.
- [ ] 사례별 테스트 실행 커밋·환경·실행일·결과를 기록하고 필요한 캡처를 첨부한다.
- [ ] 수정 전 재현 기록이 있으면 재현 조건과 수정 후 차이를 추가한다.
- [ ] API 응답시간·SMTP 지연·실행기 거부 건수 등 실측값이 생기면 조건과 함께 추가한다.
- [ ] 테스트 개수·성능 개선율·장애 감소율은 해당 사례의 근거가 있을 때만 사용한다.
- [ ] 프로필 이미지 프론트 연동의 최신 병합·배포 상태를 확인한다.
- [ ] 개인 발송 기반 구현과 팀의 후속 메일 실행기 분리 변경을 구분한다.
- [ ] 공개 분량에 맞춰 토큰 사례를 중심에 두고 보조 사례 길이를 조정한다.
- [ ] Notion·웹 전환 시 작성 메모와 체크리스트는 공개 본문에서 분리한다.

