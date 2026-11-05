# 한재훈 | Backend Developer

> 동작 원리와 선택의 근거를 설명할 수 있는 코드를 작성하는 백엔드 개발자입니다.
>
> 정상 흐름보다 동시 요청, 장애, 보안처럼 예외적인 상황에서 시스템이 어떻게 반응해야 하는지를 먼저 고민합니다. 판단의 근거는 주석과 문서로 남겨 팀원과 공유합니다.

**Contact**

- E-mail: [jhjh9907@gmail.com](mailto:jhjh9907@gmail.com)
- 연락처: 010-8482-0454
- GitHub: [github.com/ImJhoon](https://github.com/ImJhoon)
- Blog: [velog.io/@imjhoon](https://velog.io/@imjhoon/posts)

![증명사진](증명사진2.jpg)

---

## Skills

- **Backend**: Java, Spring Boot, Spring Security, Spring Data JPA
- **Database**: MySQL, PostgreSQL, Flyway, Redis
- **DevOps**: Docker, Docker Compose, GitHub Actions
- **Collaboration**: Git, GitHub, Swagger(SpringDoc)

---

## Project

### GRAB

<aside>
🛒

**한정 수량 상품을 정해진 시간에 판매하는 DROP 커머스 서비스**
**기간**  2026.09.22 ~ 2026.11.04
**인원**  4명
**역할**  회원 인증·인가 전반, 백엔드 초기 환경 구성

</aside>

**주요 담당 기능**

- HttpOnly 쿠키 기반 **JWT 인증·인가**, CSRF·CORS 및 Redis 인증 세션 관리
- **이메일 인증**(HMAC 저장·발송 제한·비동기 발송), 카카오·구글 **OIDC 로그인** 구현
- Docker Compose 개발 환경·GHCR 이미지 배포 워크플로우 구성, 인증 API 명세 작성·관리

**문제 해결 경험**

- **동시 토큰 재발급:** Lua 기반으로 토큰 교체를 원자화하고 재사용 탐지에 10초 유예 정책을 적용해, 동시 요청으로 인한 불필요한 세션 폐기를 방지했습니다.
- **인증 조회 성능 개선:** 반복되는 회원 ID 변환 조회에 Caffeine 캐시를 적용하고 조회 트랜잭션을 조정해, 로컬 부하 테스트(VU 1600)에서 내 정보 조회 API의 처리량을 약 1.95배 높였습니다.

🔗 [GitHub Repository](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3)

---

### 마주한끼

<aside>
🍽️

**지역·시간·식사 취향·대화 성향을 바탕으로 밥친구를 연결하는 실시간 1:1 식사 매칭 서비스**
**기간**  2026.08.21 ~ 2026.09.03
**인원**  2명
**역할**  실시간 매칭 전 과정, 인증·보안 기반, 성향 설문·후기, 초기 환경 구성

</aside>

**주요 담당 기능**

- **실시간 매칭** 요청·취소, 성향 점수 기반 후보 제안·수락·거절·확정 구현
- **Valkey 대기 상태 관리**와 PostgreSQL 기준 누락 키 복구·요청 만료 보정 구현
- **성향 설문·후기 작성**과 다시한끼 지수 계산 구현

**문제 예방 설계**

- **동시 수락·취소·만료:** 매칭 중복 확정을 예방하도록 비관적 락의 획득 순서를 제안 → 요청 ID 오름차순으로 통일하고, 매칭·참여자·채팅방 생성을 한 트랜잭션으로 묶었습니다.
- **DB–Valkey 상태 불일치:** PostgreSQL을 기준 상태로 두고, Valkey 대기 키가 누락된 `WAITING` 요청의 남은 대기 시간에 따라 키를 복구하거나 요청을 만료시키는 보정 작업을 구현했습니다.

🔗 [GitHub Repository](https://github.com/prgrms-aibe-devcourse/AIBE7-Project2-Team04)

---

## Education

### 프로그래머스 생성형 AI 활용 데브코스 7기

2026.05 ~ 2026.11 · 백엔드 개발자 교육

### 수원대학교 미디어SW학과 졸업

2020.04 ~ 2026.02 · GPA 4.02 / 4.5

---

## Certificate

| 자격증명 | 취득일 | 주관 |
| --- | --- | --- |
| [TOEIC Speaking IM3](https://app.notion.com/p/TOEIC-Speaking-IM3-d4dbd16e2f738276b984812220bcebea?pvs=21) | 2025/08/23 | 한국TOEIC위원회(YBM) |
| [SQLD](https://app.notion.com/p/SQLD-17cbd16e2f73828693c7817d981db2ea?pvs=21) | 2026/09/11 | 한국데이터산업진흥원(KDATA) |
