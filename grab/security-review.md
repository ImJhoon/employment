# GRAB 보안 정적 검토 — 2026-11-05

현재 코드는 쿠키·CSRF·비밀번호 해시·토큰 교체 등 기본 방어를 구체적으로 구현했습니다. 주요 보완점은 **이미 발급되거나 탈취된 인증 권한의 회수, 반복 로그인 시도 제한, 계정 상태·권한의 일관된 재확인**입니다. Refresh Token을 해시로 저장한 것과 원문 토큰 탈취를 방어하는 것은 구분해야 합니다.

이 평가는 코드와 설정을 읽은 정적 검토입니다. 실제 침투나 운영 사고를 확인한 결과가 아니며, 안전 인증·취약점 전수 검증을 뜻하지 않습니다.

## 검토 기준과 범위

- 로컬 저장소: `C:\workspace\AIBE7_FinalProject_Team3`
- 기준 커밋: `b5ef2c97497fb9dd5d5844b21014035f73276e6c` — 현재 로컬 HEAD. 원격 기본 브랜치의 최신 상태나 실제 배포 커밋으로 단정하지 않습니다.
- 포트폴리오의 토큰 사례 기준 `223b697`과 구분합니다. 두 커밋 사이 인증·회원·보안·프론트 인증 경로의 변경 목록을 확인했습니다.
- 읽은 범위: SecurityFilterChain, JWT 발급·검증, 인증 쿠키, 로그인·재발급·로그아웃, 회원 ID 변환, 비밀번호 변경, 이메일 인증, OIDC 로그인, 프로필·판매자 서류 업로드, 주문 조회·생성 및 결제 소유권 검증의 일부 경로, Nginx·Compose·CI 설정과 관련 테스트의 일부 내용입니다.
- 비밀번호 재설정·계정 복구의 전체 제품 흐름은 확인하지 않았습니다. 현재 읽은 비밀번호 변경 API는 기존 비밀번호를 요구합니다.
- 실제 비밀값 파일, 배포 서버, 클라우드 방화벽·WAF·Storage 버킷 설정·로그 저장소에 접근하지 않았습니다. 서비스 코드와 배포 설정은 변경하지 않았습니다.
- 서비스 테스트·브라우저 재현·부하 실험·SCA/CVE 스캐너를 실행하지 않았습니다. 기존 테스트는 존재·내용 확인과 실행 성공을 구분했습니다.
- 아래 우선순위는 이 서비스의 위험에 대한 정성적 판단입니다. CVSS 점수나 검증된 공격 성공률이 아닙니다.

## 위협별 현재 보장 범위

| 위협 | 현재 방어와 한계 |
| --- | --- |
| Redis의 Refresh Token 해시만 읽기 유출 | 256비트 난수 원문 대신 SHA-256 해시로 키를 구성합니다. 해시만으로 원문을 현실적으로 복원하기 어렵고, 해시를 그대로 토큰처럼 제출할 수 없습니다. Redis 쓰기 권한이나 애플리케이션 비밀키 유출은 별개입니다. |
| 유효한 Refresh Token 원문 유출 | 서버가 원문을 해시해 조회하므로 저장 시 해시가 원문 유출을 막지는 못합니다. 요청 검증을 통과하면 재발급될 수 있습니다. sessionId는 서버의 추적용 식별자이며 추가 인증 비밀값이 아닙니다. |
| Access Token 원문 유출 | JWT 검증이 통과하면 남은 유효기간 동안 사용될 수 있습니다. 설정 TTL은 15분이고, 검증에는 30초 clock skew 허용이 있습니다. 탈취 시점부터 새로 15분이 부여되는 것은 아닙니다. |
| 교체된 Refresh Token의 재사용 | 사용 기록이 남아 있는 토큰은 교체 후 10초 안이면 요청만 거부하고, 이후 재사용은 해당 세션의 현재 Refresh Token까지 폐기합니다. 10초 경과 자체로 자동 폐기하지는 않습니다. |
| 악성 사이트의 브라우저 요청 | SameSite=Lax, 명시적 CORS, 상태 변경 요청의 CSRF 검증을 적용했습니다. CORS·CSRF는 원문을 이미 가진 비브라우저 요청자의 신원을 추가 증명하는 장치가 아닙니다. |
| 애플리케이션 출처에서의 XSS | HttpOnly가 인증 쿠키의 직접 읽기를 막아도, 실행 중인 스크립트의 인증된 요청 자체까지 막지는 않습니다. 검토한 프론트 소스의 명시적인 raw HTML/eval 검색에서 일치 항목은 없었지만, 전체 XSS 부재를 입증한 것은 아닙니다. |

근거: [RefreshTokenGenerator](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/support/RefreshTokenGenerator.java), [RefreshTokenHasher](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/support/RefreshTokenHasher.java), [RefreshTokenRepository](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/repository/RefreshTokenRepository.java), [JwtProvider](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/security/jwt/JwtProvider.java), [JwtAuthenticationFilter](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/security/jwt/JwtAuthenticationFilter.java), [재사용 판정 Lua](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/resources/redis/refresh-token-reuse.lua), [애플리케이션 설정](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/resources/application.yml).

## 확인한 주요 방어

| 영역 | 코드에서 확인한 내용 | 범위·한계 |
| --- | --- | --- |
| 쿠키 | Access·Refresh 모두 HttpOnly·Secure·SameSite=Lax. Domain을 별도로 넓히지 않고 Path를 구분합니다. | 클라이언트의 쿠키 원문 자체가 유출된 상황을 해결하지는 않습니다. |
| CSRF·CORS | 상태 변경 요청에 CSRF 예외를 두지 않습니다. CORS는 명시한 Origin과 헤더만 허용하고 와일드카드 Origin을 시작 시 거부합니다. | 실제 프론트 배포 Origin·HTTPS 프록시 동작은 미확인입니다. |
| JWT | HS256만 허용하고 서명·issuer·audience·만료를 검증합니다. 서명키 최소 길이를 확인합니다. | 유효한 JWT의 실시간 세션·계정 상태 재확인은 필터에 없습니다. |
| 비밀번호 | Argon2id 19MiB·반복 2·병렬도 1, 계정별 salt를 사용하는 인코더, 없는 계정에도 더미 해시 검증을 적용합니다. | Argon2 동시 실행 제한은 계정별 추측 시도 제한을 대신하지 않습니다. |
| Refresh Token | 256비트 난수·해시 저장, Lua 교체, 사용 이력과 세션의 현재 토큰 추적, idle 14일·절대 30일 만료를 적용합니다. | 원문 탈취자의 최초 사용을 막지 못하고, 재사용이 오지 않으면 탐지할 수 없습니다. |
| 이메일 인증 | 6자리 코드의 HMAC-SHA256 저장과 상수 시간 비교, 확인 시도 제한, 이메일·IP 발송 한도가 있습니다. | 발송 제한은 로그인 시도 제한과 별개입니다. OTP의 모든 재발송·경합 순서는 이번 검토에서 전수 검증하지 않았습니다. |
| 소셜 로그인 | 표준 OIDC 클라이언트, state·nonce·PKCE 생성 경로, 검증된 이메일만 수용, 제공자 식별자로 회원 조회, 같은 이메일 자동 계정 연결 거부를 확인했습니다. | 실제 제공자 콘솔·콜백·JWKS 통신은 실행하지 않았습니다. |
| 주문·결제 소유권 | 읽은 주문 상세·결제·취소 경로에서 로그인 사용자 ID와 주문 UUID를 함께 조회합니다. 금액도 서버 주문 값과 비교합니다. | 모든 객체·모든 엔드포인트의 IDOR 부재를 보장하지 않습니다. |
| 파일·문서 | 프로필은 본인 UUID 경로를 검증하고, 판매자 서류는 AES-256-GCM 봉투 암호화·관리자 경로·열람 로그·no-store를 적용합니다. | 실제 버킷 공개 범위·RLS·업로드 제한과 원본 이미지 처리 자원 상한은 별도 점검이 필요합니다. |
| 배포·로그 | Compose의 백엔드 포트는 loopback에 바인딩하고 이미지 실행 사용자는 비루트입니다. DTO·외부 저장소 오류 등에서 비밀값 노출을 줄이는 처리가 있습니다. | 실제 EC2 노출, 환경변수 접근권한, 로그·백업·키 교체는 미확인입니다. |

근거: [AuthCookies](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/security/AuthCookies.java), [SecurityConfig](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/config/SecurityConfig.java), [CorsProperties](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/config/CorsProperties.java), [UserCredentialService](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/service/UserCredentialService.java), [이메일 HMAC](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/support/EmailVerificationHasher.java), [이메일 인증 제한](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/service/EmailVerificationService.java), [OIDC 구성](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/support/oauth2/OAuth2LoginConfig.java), [인가 요청 Resolver](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/support/oauth2/SocialAuthorizationRequestResolver.java), [검증된 이메일 확인](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/support/oauth2/SocialProfile.java), [계정 연결 정책](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/service/SocialAccountService.java), [주문 소유권](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/order/service/OrderQueryService.java), [결제·취소 소유권](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/order/service/OrderPaymentService.java), [결제 금액 확인](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/payment/service/PaymentTransactionService.java), [서류 암호화](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/seller/crypto/DocumentEncryptionService.java), [Compose](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/deploy/compose.prod.yaml), [Dockerfile](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/Dockerfile).

비밀번호 해시 파라미터는 확인한 [OWASP Password Storage 지침](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#argon2id)의 최소 권장 구성에 해당합니다. 이것만으로 계정 탈취나 반복 로그인 시도에 대한 보호가 완성되는 것은 아닙니다.

## 우선순위별 보완점

| ID | 우선순위 | 확인 상태 | 내용 |
| --- | --- | --- | --- |
| S01 | 높음 | 코드에 명시된 동작 | 비밀번호 변경이 기존 로그인 세션을 회수하지 않습니다. |
| S02 | 높음 | 읽은 코드·설정의 보호 공백 | 로그인 계정·IP별 실패 횟수·시간당 시도 제한이 확인되지 않았습니다. |
| S03 | 중간, 관리자·민감 API는 높음 | 코드 구조로 확인 | 로그아웃·Refresh 폐기 후 기존 Access Token이 즉시 무효화되지 않습니다. |
| S04 | 중간~높음 | 읽은 경로의 정책 불일치 | 정지·탈퇴·관리자 권한 회수의 반영이 API별로 다릅니다. |
| S05 | 중간 | 코드 흐름으로 확인 | 재발급 오류·응답 순서에 따라 서버 세션이 남아도 화면이 비로그인으로 바뀔 수 있습니다. |
| S06 | 중간 | 코드·프록시 설정 확인, 실노출 미확인 | Prometheus와 API 문서가 인증 없이 허용됩니다. |
| S07 | 중간 | 자원 상한 누락 확인, 영향은 추론 | 판매자 서류 이미지의 디코딩 크기·픽셀 상한이 없습니다. |
| S08 | 중간 | 저장·발급 구조 확인, 자원 사용은 추론 | 로그인 세션·Refresh 교체 이력의 생성량 상한이 확인되지 않았습니다. |

### S01. 비밀번호를 바꿔도 탈취된 Refresh Token을 회수하지 못합니다

**확인:** PasswordChangeService는 현재 비밀번호를 검증하고 해시를 조건부 UPDATE하지만, 기존 세션을 종료하지 않는다고 명시합니다. Refresh 세션에는 사용자 ID·sessionId·절대 만료 시각만 있고, 비밀번호 변경 세대나 인증 버전은 없습니다. 재발급의 ActiveUserLookupService도 회원 상태만 확인합니다.

**발생 조건과 영향:** 공격자가 이미 유효한 Refresh Token을 갖고 있고 재사용 탐지나 별도 로그아웃으로 폐기되지 않았다면, 정상 사용자가 비밀번호를 바꾼 뒤에도 토큰을 교체하며 접근을 이어갈 수 있습니다. 비밀번호 변경은 기존 세션의 재발급 자격에 영향을 주지 않습니다.

**개선 방향:** 사용자별 보안 버전 또는 tokensValidAfter, 사용자별 세션 인덱스를 이용해 비밀번호 변경 시 전체 세션 또는 현재 세션을 제외한 세션을 회수하는 정책을 정합니다. 단순히 세션 목록을 순회 삭제하면 새 발급·교체와 경합할 수 있어, 발급·재발급에서도 버전을 검증해야 합니다. 비밀번호 DB 변경과 Redis 반영의 실패 시 동작도 정의해야 합니다.

근거: [PasswordChangeService](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/service/PasswordChangeService.java#L31), [세션 필드](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/repository/RefreshTokenSession.java#L16), [재발급 시 회원 확인](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/service/ActiveUserLookupService.java). 민감한 변경 후 세션 회수·재인증 방향은 [OWASP 인증 지침](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#reauthentication-after-risk-events)도 참고했습니다.

### S02. 해시 동시 실행 제한과 로그인 공격 제한은 다릅니다

**확인:** LoginService → UserCredentialService → PasswordEncoder 경로에는 실패 횟수·계정/IP별 시간 구간 제한이 없습니다. 검색한 보안 필터·서비스·Nginx 설정에서도 로그인용 제한을 찾지 못했습니다. 이메일 인증과 일부 AI·SSE 경로의 제한은 존재하지만 로그인에는 적용되지 않습니다.

**영향:** 낮은 동시성으로 오래 이어지는 비밀번호 추측, 유출된 비밀번호 대입을 계정 단위로 제한하지 못합니다. 해시 4건 제한·3초 대기·503은 힙과 처리 자원을 보호하지만, 공격자가 차례로 제출하는 시도 자체를 금지하지 않습니다. 유효 계정을 가진 공격자의 민감 API 비밀번호 추측도 함께 고려해야 합니다.

**개선 방향:** 계정 중심 실패 카운터에 IP·장치·전체 유입량 제한을 보완합니다. 계정 존재 여부를 드러내지 않는 응답과 점진적 지연을 설계하고, 강제 잠금이 타인의 계정에 대한 서비스 거부 수단이 되지 않게 합니다. 관리자는 MFA·재인증을 우선 검토합니다. 외부 WAF가 실제로 같은 정책을 제공하는지는 미확인입니다.

근거: [LoginService](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/service/LoginService.java), [UserCredentialService](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/service/UserCredentialService.java#L42), [해시 동시 실행 제한](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/security/password/ConcurrencyLimitedPasswordEncoder.java), [Nginx 설정](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/deploy/nginx/grab.conf). 계정 중심 제한과 잠금의 서비스 거부 위험은 [OWASP Login Throttling](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#login-throttling)을 참고했습니다.

### S03. 서버의 Refresh 세션 폐기는 이미 발급된 JWT를 회수하지 않습니다

**확인:** JWT 필터는 서명·Claim을 검증해 인증을 구성하며 Redis 세션, jti 차단 목록, 사용자 보안 버전을 조회하지 않습니다. JWT에는 jti가 있지만 차단 목록에 연결되지 않습니다. 로그아웃은 해당 Refresh 세션을 폐기하고 브라우저 쿠키를 만료시킵니다.

**영향:** 공격자가 별도로 보관한 Access Token은 피해자의 로그아웃이나 Refresh 재사용 탐지 뒤에도 JWT 유효기간 동안 인증에 사용될 수 있습니다. 일부 서비스의 추가 회원 상태 검사는 별개입니다. 현재 TTL 15분과 검증 허용 오차 30초를 감안해야 합니다. 쿠키 삭제가 공격자의 복사본 삭제를 뜻하지는 않습니다.

**판단:** 짧은 수명의 JWT를 사용하는 설계의 비용이며, 모든 경우의 인증 우회 버그로 분류하지는 않습니다. 다만 관리자 서류 열람·권한 회수 같은 경로에서는 즉시 회수 요구와 충돌할 수 있습니다.

**개선 방향:** 즉시 회수가 필요한 API의 범위를 먼저 정합니다. 세션 ID·보안 버전을 JWT와 서버 상태에 연결하거나 jti 차단 목록·제한된 캐시를 적용할 수 있습니다. 서버 조회 비용과 Redis 장애 시 거부·허용 정책까지 함께 결정해야 합니다. TTL을 줄이는 것만으로 비밀번호 변경 후 Refresh 세션 잔존 문제는 해결되지 않습니다.

근거: [JWT 필터](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/security/jwt/JwtAuthenticationFilter.java#L55), [JWT 발급·검증](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/security/jwt/JwtProvider.java#L39), [로그아웃 처리](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/service/LogoutService.java), [TTL 설정](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/resources/application.yml).

### S04. 회원 정지·탈퇴와 관리자 권한 회수가 모든 경로에 일관되게 반영되지 않습니다

**확인:** 내 정보·프로필·비밀번호 변경과 Refresh 재발급은 ACTIVE를 확인합니다. 반면 CurrentUserIdProvider의 ID 변환은 존재·ID 대응만 확인하고 상태를 판정하지 않습니다. 이 매핑은 Caffeine에서 적중하면 DB 조회도 생략합니다.

읽은 주문 생성·조회, WISH 서비스 경로에는 별도의 사용자 상태 검사가 없습니다. SellerIdLookupService는 판매자 APPROVED 상태는 재조회하지만 조인한 사용자의 ACTIVE 여부는 조회 조건에 없습니다. 관리자 URL은 JWT의 ADMIN 권한으로 보호되고, 읽은 심사 서비스는 현재 관리자의 DB 역할·상태를 재검증하지 않습니다. 관리자 목록 조회는 현재 사용자 ID 조회도 하지 않습니다.

**영향:** 유효한 JWT를 가진 회원을 DB에서 정지·탈퇴 처리하거나 관리자 역할을 회수했을 때, 내 정보에서는 거부되지만 다른 경로는 남은 JWT 유효기간 동안 처리가 가능할 여지가 있습니다. 즉시 정지 정책을 요구한다면 불일치입니다. 실제 정지·권한 변경 후 HTTP 요청을 실행해 확인한 것은 아닙니다.

**개선 방향:** 인증 실패·권한 회수·계정 상태의 공통 규칙을 정하고, 공통 인증 계층 또는 민감 API의 guard에서 검사합니다. ID 매핑 캐시는 그대로 둘 수 있지만, 이를 활성 회원 인증 근거로 쓰면 안 됩니다. 관리자는 현행 권한을 재확인하는 정책을 우선 적용합니다.

근거: [CurrentUserIdProvider](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/security/identity/SecurityContextCurrentUserIdProvider.java), [ID 매핑 캐시](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/service/UserIdLookupService.java#L29), [ID 조회](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/repository/UserRepository.java), [주문 생성 경로](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/order/service/OrderCreateService.java), [WISH 경로](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/wish/service/WishTransactionService.java#L33), [판매자 조회](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/seller/repository/SellerRepository.java), [관리자 URL 권한](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/config/SecurityConfig.java#L108), [관리자 심사 경로](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/seller/service/AdminSellerApplicationService.java#L39).

### S05. 재발급 오류가 화면의 로그아웃처럼 나타날 수 있습니다

**경로 A — 재발급 500·네트워크 오류:**

1. /users/me가 Access Token 만료로 401을 반환합니다.
2. 인터셉터가 /auth/refresh를 호출하지만 서버 오류 또는 네트워크 오류가 납니다.
3. refreshSession은 retryable=false를 반환합니다.
4. resolveUnauthorized는 재발급 오류 대신 원래 /users/me의 401 오류를 던집니다.
5. fetchMe는 그 401을 null로 바꾸고, useAuth는 null을 비로그인으로 해석합니다.

이때 서버 쿠키를 지우거나 Refresh 세션을 폐기한 것은 아닙니다. 화면의 인증 상태와 서버의 복구 가능한 상태가 달라집니다.

**경로 B — 여러 탭의 재발급 경쟁:** 재발급 401 후 원래 API를 한 번 재호출하지만, 다른 탭의 성공 응답이 쿠키에 반영될 때까지 기다리는 처리가 없습니다. 재호출도 401이면 onSessionExpired로 사용자 캐시를 비웁니다. 성공 응답의 새 쿠키가 끝내 반영되지 않는 경우도 자동 복구를 보장하지 않습니다.

**테스트의 한계:** session.test.ts의 “재발급 500은 로그아웃하지 않는다” 테스트는 sessionExpired 콜백 호출이 0회인지 확인하면서, API가 원래 401을 내보내는 것까지 기대합니다. /users/me → fetchMe → useAuth 전체 흐름의 인증 상태 유지는 이 테스트가 확인하지 않습니다. 테스트를 실행한 결과가 아닌 코드 내용 확인입니다.

**개선 방향:** 인증 만료, 일시적 장애, 경합으로 인한 거부를 구분해 전파합니다. 일시적 실패를 인증 정보 없음(null)으로 바꾸지 말고 재확인 가능한 오류 상태로 둡니다. 다중 탭 동기화·한정된 대기/재조회 정책을 설계하되 상태 변경 요청을 무조건 재전송하지 않습니다. 네트워크 응답 유실 시 “항상 복구”가 필요하다면 서버 프로토콜도 별도 검토해야 합니다.

근거: [재발급 오류·재시도](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Frontend/src/api/auth/session.ts#L44), [fetchMe의 401 처리](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Frontend/src/api/auth/auth.ts#L44), [화면 인증 상태](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Frontend/src/features/auth/logic/useAuth.ts), [세션 만료 시 캐시 변경](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Frontend/src/main.tsx#L17), [500 오류 테스트](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Frontend/src/api/auth/__tests__/session.test.ts#L129).

### S06. Prometheus와 Swagger가 인증 없이 허용됩니다

**확인:** SecurityConfig는 /actuator/health·/actuator/prometheus와 Swagger·OpenAPI 경로를 permitAll로 허용합니다. application.yml은 health·prometheus를 노출합니다. 검토한 Nginx는 일반 경로를 백엔드로 프록시하고 해당 경로의 별도 차단을 두지 않습니다.

**영향:** 이 설정이 그대로 적용되고 외부 게이트웨이 차단이 없다면, 메트릭과 API 구조·스키마를 인증 없이 볼 수 있습니다. 비밀번호나 토큰 원문이 노출된다고 확인한 것은 아닙니다. health 공개와 관리용 메트릭 공개는 구분해야 합니다.

**개선 방향:** 메트릭은 내부 수집망·전용 포트·인증 또는 프록시 ACL로 제한하고, Swagger는 운영 노출 정책을 분리합니다. 외부에서 실제 접근 가능한지는 별도로 확인해야 합니다.

근거: [공개 경로 설정](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/global/config/SecurityConfig.java#L65), [메트릭 노출 설정](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/resources/application.yml), [프록시 설정](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/deploy/nginx/grab.conf). [Spring Boot Actuator 문서](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.security)는 엔드포인트 노출과 접근 보안을 별도로 설명합니다.

### S07. 이미지 바이트 크기 제한이 디코딩 메모리까지 제한하지는 않습니다

**확인:** 판매자 서류 업로드는 multipart 10MB 제한을 두지만 ImageIO.read로 이미지를 전체 디코딩합니다. 가로·세로·총 픽셀 수를 먼저 제한하는 코드는 없습니다. 관리자 워터마크 생성 시에도 다시 전체 디코딩합니다.

**영향:** 압축된 파일은 작아도 디코딩 후 이미지가 커질 수 있어, 인증된 사용자의 업로드가 메모리·CPU를 소모할 수 있습니다. 실제 고해상도 파일이나 동시 요청으로 OOM을 재현하지 않았으므로 발생 임계값·공격 성공 여부는 미확인입니다.

**개선 방향:** 전체 디코딩 전에 ImageReader로 크기를 읽어 가로·세로·총 픽셀을 제한하고, 디코딩 동시 실행·사용자별 업로드량·시간 제한을 함께 둡니다. 저장 Content-Type도 클라이언트 문자열만 믿지 않고 실제 디코더 형식으로 정규화하는 것이 좋습니다.

프로필·DROP의 직접 업로드는 Storage 버킷 제한을 신뢰하는 구조입니다. 현재 버킷의 MIME·크기·RLS 설정은 별도 확인해야 합니다. 프로필 URL 검사 자체는 본인 경로와 정규형 파일명 확인이며 실제 발급 이력·객체 내용 검증은 아닙니다.

근거: [업로드 디코딩](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/seller/service/SellerDocumentUploadService.java#L69), [워터마크 처리](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/seller/service/SellerDocumentViewService.java), [서명 업로드 URL](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/service/ProfileImageUploadService.java), [본인 URL 경로 검사](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/user/service/ProfileImageUrlVerifier.java#L27). 처리 후 크기와 요청량 제한은 [OWASP File Upload 지침](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html#upload-and-download-limits)을 참고했습니다.

### S08. 세션·교체 이력의 수명 제한은 생성량 제한과 다릅니다

**확인:** 로그인마다 새 sessionId를 만들고, Refresh 교체마다 옛 토큰의 사용 표식을 새 토큰과 같은 TTL로 보관합니다. 읽은 경로에는 사용자별 동시 세션 수, 재발급 빈도, 사용 이력 수의 제한이 없습니다.

**영향:** 유효 계정을 가진 사용자의 반복 발급·교체가 Redis 키와 DB 조회·JWT 서명 연산을 늘릴 수 있습니다. TTL이 있어도 TTL 안에 만들 수 있는 총량은 제한하지 않습니다. 현재 설정에서는 사용 이력이 비활동 TTL 범위로 오래 남을 수 있습니다. 실제 Redis 메모리 사용량이나 서비스 거부를 측정한 것은 아닙니다.

**개선 방향:** 세션별 재발급 요청 빈도와 계정별 활성 세션 수를 제한하고, 폐기와 필요한 탐지 기간을 유지하면서 사용 표식의 보존·정리 정책을 검토합니다. 표식 TTL만 짧게 줄이면 오래된 토큰 재사용 탐지와 로그아웃 회수 범위가 줄어드는 비용이 있습니다.

근거: [로그인별 세션 생성](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/service/AuthSessionIssuer.java), [교체 TTL](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/java/org/example/grab/domain/auth/repository/RefreshTokenRepository.java), [사용 표식 생성·만료](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/b5ef2c97497fb9dd5d5844b21014035f73276e6c/Backend/src/main/resources/redis/refresh-token-rotate.lua).

## 10초 유예 정책의 별도 평가

10초 유예 자체를 무조건 취약점이나 무조건 안전한 정책으로 판단하지 않습니다.

- 유예 중에도 사용된 옛 토큰은 거부되므로 새 토큰을 다시 발급하지 않습니다.
- 반면 먼저 교체한 주체가 공격자여도 서버는 구분할 수 없습니다. 정상 사용자가 옛 토큰을 유예 안에 사용하면 요청만 거부되고 공격자가 보유한 현재 Refresh Token의 세션은 유지됩니다.
- 그 뒤 정상 사용자가 옛 토큰을 다시 사용하지 않으면, 10초가 지나도 자동으로 재사용 탐지·폐기가 실행되지 않습니다. 현재 화면은 인증 실패 후 비로그인으로 바뀔 수 있어, 뒤늦은 재사용이 꼭 발생한다고 전제할 수 없습니다.
- 유예 이후 사용 기록이 남은 옛 토큰이 다시 들어오면 최신 Refresh Token까지 폐기합니다. 이미 발급된 Access Token의 즉시 회수는 S03의 별도 문제입니다.
- 공격자가 새 Refresh Token을 다시 교체해도 원래 옛 토큰 표식의 교체 시각이 매번 초기화되는 구조는 아닙니다.
- 따라서 설명 범위는 “정상 중복 요청의 오탐을 줄이는 대신 일부 탐지·폐기를 유예한다”입니다. “토큰 탈취 방지” 또는 “네트워크 오류에서도 무조건 로그인 유지”로 확대하면 안 됩니다.
- 10초의 적절성은 클라이언트의 실제 요청·응답 지연과 보안 요구를 함께 봐야 합니다. 이 검토에서는 적정 시간을 측정하지 않았습니다.

Rotation은 양쪽 중 누가 공격자인지 구분할 수 없어 현재 토큰을 폐기하는 방식이라는 점을 [RFC 9700 §4.14.2](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2)와 대조했습니다. RFC가 GRAB의 10초 값을 권고하는 것은 아닙니다.

## 배포·운영에서 추가 확인할 항목

아래는 확인된 취약점 목록이 아니라, 코드만으로 확정하지 못한 항목입니다.

- 실제 배포 커밋, 프록시·CDN·WAF의 로그인 제한과 관리 경로 접근 정책.
- HTTPS 응답의 HSTS·CSP·클릭재킹·캐시 정책. 검토한 애플리케이션·Nginx에 명시적 CSP 설정은 찾지 못했지만 프론트 호스팅/CDN의 추가 정책은 미확인입니다. Spring 기본 보안 헤더까지 없다고 판단하지 않습니다.
- Redis 인증·TLS·ACL·외부 노출·메모리 상한·eviction·영속성·백업, PostgreSQL의 접근권한·TLS·백업 정책.
- Supabase의 서류 비공개 버킷·RLS, 프로필/이미지 MIME·크기 제한, 서비스 Secret Key의 최소 권한과 관리 상태.
- JWT 서명키·이메일 HMAC 키·서류 마스터키의 분리·접근권한·교체 절차. 서류는 환경변수 마스터키 기반이고 KMS 연동은 코드에 없습니다.
- 관리자 MFA, 민감 서류 열람·심사의 재인증, 이상 로그인·재사용 탐지 알림과 감사 로그 보존·접근권한.
- 의존성·컨테이너 CVE와 실제 해석된 버전. 읽은 CI는 테스트·빌드 중심이며 SCA·시크릿 스캔 작업은 찾지 못했습니다. 플랫폼 측 자동 보안 기능 활성 여부는 미확인입니다.
- Git 전체 이력·CI 아티팩트·운영 로그의 비밀값 유출 여부. 현재 추적 파일 이름에서는 .env 예시 파일을 확인했지만, 이를 전체 이력의 유출 부재로 일반화하지 않습니다.

## 개선 순서와 이후 검증 제안

1. **인증 회수 정책:** 비밀번호 변경·계정 정지·관리자 권한 회수·로그아웃에서 회수할 범위를 정하고, 사용자/세션 버전과 전체 세션 회수 체계를 설계합니다. 기존 Access Token 허용 시간을 민감 API별로 결정합니다.
2. **반복 시도·자원 제한:** 로그인 실패와 재발급을 계정·세션·IP 단위로 제한합니다. Argon2 상한은 유지하면서 요청 유입과 실패 누적을 별도로 제어합니다.
3. **프론트 인증 오류 구분:** 401·일시적 서버 장애·다중 탭 경합을 구분하고 사용자 정보 조회까지 연결해 검증합니다.
4. **운영 노출·파일 처리:** 메트릭·문서 접근을 제한하고 서류 이미지의 픽셀·동시 디코딩 상한을 둡니다.

이후 변경 시 의미 있는 회귀 검증은 다음과 같습니다. 이번 작업에서 실행하거나 새 테스트를 작성하지 않았습니다.

| 검증 | 기대하는 확인 내용 |
| --- | --- |
| 세션 A·B 발급 후 비밀번호 변경 | 정책에 따라 A 또는 전체 세션의 재발급이 거부되는지 |
| 계정 상태·관리자 권한 변경 후 기존 JWT로 요청 | 내 정보뿐 아니라 주문·WISH·관리자 서류 경로에도 같은 정책이 적용되는지 |
| 복사한 Access Token으로 로그아웃 이후 요청 | 즉시 회수 대상과 TTL까지 허용하는 대상의 경계 |
| 로그인 반복 실패·다중 출처 요청 | 계정/IP 제한, 존재 여부 비노출, 정상 사용자 복구와 잠금 DoS 방지 |
| /users/me 401 → refresh 500 또는 네트워크 오류 | 사용자 정보가 null로 확정되지 않고 복구 가능한 장애 상태로 남는지 |
| 탭 간 재발급 응답 순서 교차·응답 미반영 | 일시적 경쟁과 실제 세션 만료가 구분되는지, 복구 한계가 명시되는지 |
| 관리자 메트릭·문서 경로의 외부 요청 | 운영 접근 정책과 실제 응답의 일치 |
| 압축 크기는 작지만 픽셀 수가 큰 서류 | 전체 디코딩 전 거부와 제한된 자원 사용 |

## 포트폴리오에 사용할 설명의 경계

현재 구현을 “JWT·Redis이므로 안전하다” 또는 “탈취를 완전히 막았다”고 요약하지 않습니다. 확인한 구현은 **쿠키 보호·CSRF·저장소 원문 노출 감소·원자적 토큰 교체·재사용 탐지**이며, **이미 발급된 토큰의 회수·사고 후 세션 정리·공격성 반복 요청 차단**은 별도의 보완 과제입니다.

아직 구현하지 않은 개선을 개인 성과로 적지 않습니다. 이 보고서는 작성·면접 대비용 분석 자료이며 공개 포트폴리오 본문을 자동으로 변경하지 않았습니다.

