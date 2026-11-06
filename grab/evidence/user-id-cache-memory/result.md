# 회원 ID 캐시 10만 항목의 객체 그래프 크기 측정

측정일: 2026-11-06 20:05~20:07 KST

**결과: 10만 항목 적재 후 캐시 객체 그래프 크기는 약 13.45MiB(14.10MB)였습니다.** 새 JVM에서 세 번 측정한 값은 모두 14,099,832바이트로 같았습니다. 힙 상한 550MiB의 약 2.44%에 해당합니다.

기존 기록의 “801항목 약 80KB”를 10만 항목으로 환산한 약 10MB보다 큽니다. 키·값 외에 Caffeine 노드, ConcurrentHashMap 노드·테이블, 접근 빈도 관리 배열 등이 필요하기 때문입니다. 이 결과는 캐시 메모리의 보장된 상한이나 운영 서버의 실측값이 아닙니다.

## 기준 코드와 환경

- 포트폴리오의 캐시 사례 기준 커밋: `7c2e51b84c45d4d31eea2e7549907d40416fc323`.
- 열람한 로컬 서비스 HEAD: `b5ef2c97497fb9dd5d5844b21014035f73276e6c`.
- 두 기준 사이 `UserIdCacheConfig` 변경이 없음을 확인했습니다. 기준 커밋의 Spring Boot 버전도 4.1.1입니다.
- Spring Boot 4.1.1 의존성 BOM이 지정한 Caffeine **3.2.4**를 사용했습니다.
- 구성: `Cache<UUID, Long>`, `Caffeine.newBuilder().maximumSize(100_000).recordStats().build()`. TTL·weight·weak key/value 설정은 없습니다.
- 기본 실행기는 프로젝트와 동일한 `ForkJoinPool.commonPool()`입니다.
- Windows 11 amd64, Oracle HotSpot JDK **17.0.12+8-LTS-286**.
- JVM 옵션: `-Xmx550m -XX:+UseG1GC -XX:ActiveProcessorCount=2`.
- 압축 객체 포인터·압축 클래스 포인터 활성화, 객체 정렬 8바이트를 실행 출력에서 확인했습니다.
- 측정 도구: OpenJDK **JOL 0.17**, `-javaagent`로 Instrumentation을 제공했습니다.
- 프로젝트의 부하 측정 설정과 힙·GC·JVM에 보이는 CPU 수를 맞췄습니다. 서비스 Dockerfile의 Linux/Temurin JVM을 그대로 실행한 것은 아닙니다. 측정 당시 Docker 데몬에 연결할 수 없어 로컬 JVM으로 독립 실행했습니다.

근거: [캐시 구성](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/7c2e51b84c45d4d31eea2e7549907d40416fc323/Backend/src/main/java/org/example/grab/domain/user/support/UserIdCacheConfig.java), [서비스의 키·값 처리](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/7c2e51b84c45d4d31eea2e7549907d40416fc323/Backend/src/main/java/org/example/grab/domain/user/service/UserIdLookupService.java), [빌드 설정](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/7c2e51b84c45d4d31eea2e7549907d40416fc323/Backend/build.gradle), [기존 측정 조건](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team3/blob/7c2e51b84c45d4d31eea2e7549907d40416fc323/Backend/k6/user-id-lookup/result.md).

## 측정 방법

1. 실제 캐시 설정과 같은 독립 캐시를 생성했습니다. Spring 애플리케이션·DB·Redis는 실행하지 않았습니다.
2. 고정 시드로 서로 다른 UUID 100,000개를 만들고, 각 UUID에 서로 다른 `Long`을 저장했습니다. Long 값은 JVM의 작은 정수 공유 영역을 피하도록 1,000부터 시작했습니다. 실제 사용자 정보는 사용하지 않았습니다.
3. `cleanUp()`과 공용 풀의 작업 종료 대기로 유지보수를 마친 뒤, `estimatedSize()`와 `asMap().size()`가 모두 100,000인지 확인했습니다.
4. JOL `GraphLayout.parseInstance(cache)`로 캐시에서 도달 가능한 객체를 중복 없이 합산했습니다.
5. 기본 실행기 참조를 따라가면 공용 풀·스레드·클래스 로더 등 공유 객체도 포함됩니다. `GraphLayout.parseInstance(ForkJoinPool.commonPool())`의 객체 집합을 `subtract`로 제외했습니다. 차감 후에도 UUID와 Long이 각각 정확히 100,000개인지 검사했습니다.
6. 별도 JVM 세 번에서 같은 과정을 반복했습니다.

이 값은 위 방식으로 구한 **객체 그래프 크기의 합계**입니다. 힙 덤프의 GC root/dominator 분석으로 구한 엄밀한 retained size, JVM 전체의 힙 사용량, RSS, 요청 중의 최대 할당량을 뜻하지 않습니다. 별도 Spring 빈인 Micrometer 바인더·레지스트리의 메모리도 포함하지 않습니다.

## 실행 결과

| 실행 | 항목 수 | 공용 풀 차감 전 | 공용 풀 차감 후 | 차감 후 MiB |
| --- | ---: | ---: | ---: | ---: |
| 기본 구성 1회 | 100,000 | 14,366,720B | **14,099,832B** | **13.446648** |
| 기본 구성 2회 | 100,000 | 14,366,720B | **14,099,832B** | **13.446648** |
| 기본 구성 3회 | 100,000 | 14,366,720B | **14,099,832B** | **13.446648** |

원본 출력: [1회](default-1.txt), [2회](default-2.txt), [3회](default-3.txt).

공유 풀을 처음부터 참조하지 않는 직접 실행기로 바꾼 비교 실행도 한 번 수행했습니다. 이 경우 **14,098,856B(13.445717MiB)**로, 기본 구성에서 공용 풀을 차감한 값과 약 1KB 차이였습니다. 실행기 교체는 이 비교 실행에만 적용했습니다. Caffeine 공식 메모리 벤치마크도 공용 풀을 함께 세지 않으려고 직접 실행기를 사용합니다. [비교 실행 출력](direct-comparison.txt), [Caffeine 공식 측정 코드](https://github.com/ben-manes/caffeine/blob/master/caffeine/src/jmh/java/com/github/benmanes/caffeine/cache/MemoryBenchmark.java).

### 기본 구성 1회차의 구성 요소

| 객체 | 개수 | 합계 |
| --- | ---: | ---: |
| UUID 키 | 100,000 | 3,200,000B |
| Long 값 | 100,000 | 2,400,000B |
| Caffeine 노드 `PSMS` | 100,001 | 3,200,032B |
| ConcurrentHashMap 노드 | 100,000 | 3,200,000B |
| ConcurrentHashMap 테이블 배열 | 1 | 1,048,592B |
| 접근 빈도 관리 `long[]` | 1 | 1,048,592B |
| 기타 캐시·큐·통계·잠금 객체 | — | 2,616B |
| **합계** | **400,030객체** | **14,099,832B** |

JOL 출력의 `PSMS`에는 보조 노드 한 개가 포함되어 있습니다. 실제 매핑 항목 수는 100,000개입니다.

## 재현

측정 코드는 [CacheMemoryProbe.java](CacheMemoryProbe.java)입니다. Java 17과 아래 두 JAR이 필요합니다.

| 의존성 | 다운로드 | SHA-256 |
| --- | --- | --- |
| Caffeine 3.2.4 | [Maven Central](https://repo.maven.apache.org/maven2/com/github/ben-manes/caffeine/caffeine/3.2.4/caffeine-3.2.4.jar) | `9d9d2cfd681fd9272ded3d27c9930db12f89f732345975aa113ebc223bbf1224` |
| JOL CLI 0.17 full | [Maven Central](https://repo.maven.apache.org/maven2/org/openjdk/jol/jol-cli/0.17/jol-cli-0.17-full.jar) | `ea8cf31b7dc6c18810ca7aeadcbe7a2b352fb250261b8060ce513e8a99ebcd12` |

실행 디렉토리는 이 문서가 있는 폴더입니다. 실제 측정에서는 두 JAR과 컴파일 결과를 `.work/`에 두었습니다. `.work/`는 Git에서 제외합니다.

```powershell
$probeDir = (Get-Location).Path
$workDir = Join-Path $probeDir '.work'
$jolJar = Join-Path $workDir 'jol.jar'
$caffeineJar = Join-Path $workDir 'caffeine.jar'

javac -encoding UTF-8 -cp "$jolJar;$caffeineJar" -d $workDir .\CacheMemoryProbe.java
java -Xmx550m -XX:+UseG1GC -XX:ActiveProcessorCount=2 "-javaagent:$jolJar" -cp "$workDir;$jolJar;$caffeineJar" CacheMemoryProbe

# 위 java 명령을 별도 JVM에서 총 세 번 실행합니다.
# 비교용: 직접 실행기로 바꾼 독립 캐시
java -Xmx550m -XX:+UseG1GC -XX:ActiveProcessorCount=2 "-javaagent:$jolJar" -cp "$workDir;$jolJar;$caffeineJar" CacheMemoryProbe direct
```

## 해석과 한계

- **단위:** 1MiB = 1,048,576B, 1MB = 1,000,000B입니다. 14,099,832B는 13.446648MiB, 14.099832MB입니다.
- **메모리 예산:** 550MiB에 대한 비율은 2.444845%입니다. 애플리케이션 전체의 여유 힙을 측정한 값이 아닙니다.
- **적용 범위:** Java 17·압축 포인터 활성화·8바이트 정렬·Caffeine 3.2.4·CPU 수 2 조건의 로컬 측정입니다. 다른 JVM·설정·라이브러리 버전에서는 재측정이 필요합니다.
- **부하 범위:** 단일 스레드로 10만 항목을 저장하고 유지보수가 끝난 상태입니다. 높은 동시성, 반복 교체, 미처리 유지보수에 따른 버퍼 확장과 일시적 할당량은 별도 조건입니다. `maximumSize`는 메모리의 바이트 상한이 아닙니다.
- **서비스:** 이 측정은 독립 실행으로 수행했으며, 원본 서비스의 코드·데이터·설정은 변경하지 않았습니다.

참고: [OpenJDK JOL](https://github.com/openjdk/jol), [Caffeine의 메모리 평가](https://github.com/ben-manes/caffeine/wiki/Memory-overhead).
