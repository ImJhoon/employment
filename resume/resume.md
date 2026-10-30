>
>
>
> 동작 원리와 선택의 근거를 설명할 수 있는 코드를 작성하는 백엔드 개발자입니다.
>
> 정상 흐름보다 동시 요청, 장애, 보안처럼 예외적인 상황에서 시스템이 어떻게 반응해야 하는지를 먼저 고민합니다. 판단의 근거는 주석과 문서로 남겨 팀원과 공유합니다.
>

**Contact**

- E-mail: jhjh9907@gmail.com
- GitHub: github.com/ImJhoon
- Blog: velog.io/@imjhoon

!증명사진2.jpg

---

## Skills

- **Backend**: Java, Spring Boot, Spring Security, Spring Data JPA
- **Database**: PostgreSQL, Flyway, Redis, MySQL
- **DevOps**: Docker, Docker Compose, GitHub Actions
- **Collaboration**: Git, GitHub, Swagger(SpringDoc)

---

## Project

### GRAB

<aside>
🛒

**한정 수량 상품을 정해진 시간에 판매하는 DROP 커머스 서비스**
**기간**  2026.09 ~ 진행 중
**인원**  4명
**역할**  회원 인증·인가 전반, 백엔드 초기 환경 구성

</aside>

**주요 담당 기능**

- HttpOnly 쿠키 기반 **JWT 인증·인가**, CSRF·CORS 및 Redis 인증 세션 관리
- **이메일 인증**(HMAC 저장·발송 제한·비동기 발송), 카카오·구글 **OIDC 로그인** 구현
- Docker Compose 개발 환경·GHCR 이미지 배포 워크플로우 구성, 인증 API 명세 작성·관리

**문제 해결 경험**

- **동시 토큰 재발급:** Lua 기반으로 토큰 교체를 원자화하고 재사용 탐지에 10초 유예 정책을 적용해, 동시 요청으로 인한 불필요한 세션 폐기를 방지했습니다.
- **CSRF 토큰 반복 발급에 따른 403:** JWT 인증 시 CSRF 토큰이 반복 발급되는 원인을 분석하고 세션 인증 전략을 변경해 해결했습니다.

🔗 GitHub Repository

---

## Education

### 프로그래머스 생성형 AI 활용 데브코스 7기

2026.05 ~ 2026.11 · 백엔드 개발자 교육

### 수원대학교 미디어SW학과 졸업

2020.04 ~ 2026.02 · GPA 4.02 / 4.5

---

## Certificate

제목 없음