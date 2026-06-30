# TASK 02-A: Supabase DB 연결 테스트

`TASK_02`의 일부. 본격적인 Repository 구현 전에 **DB에 실제로 붙는지 먼저 검증**한다.
가장 흔한 실패 지점(비밀값 주입, PgBouncer 충돌, 커넥션 한도)을 빠르게 걸러내는 게 목적이다.

선행: 엔티티(TASK_01) 완료. Spring Boot 프로젝트가 기동 가능한 상태.

---

## 0. 비밀값 준비 (사람이 직접)

> ⚠️ DB 비밀번호·연결 URL은 **환경변수로만** 주입한다. 코드·`application.yml`·VCS에
> 평문으로 넣지 않는다. 이 단계는 운영자(사람)가 직접 수행한다 — Claude Code는
> 값을 알 필요도, 가질 필요도 없다.

Supabase 대시보드 → `Project Settings → Database`에서 다음을 확인한다.
- Host, Port (Direct: 5432 / Transaction pooler: 6543)
- Database name (보통 `postgres`)
- User (보통 `postgres` 또는 pooler용 `postgres.<project-ref>`)
- Password (프로젝트 생성 시 설정한 값)

환경변수로 설정 (예: 로컬 `.env` 또는 IDE Run Configuration):
```
SUPABASE_DB_HOST=db.xxxxxxxx.supabase.co
SUPABASE_DB_PORT=5432
SUPABASE_DB_NAME=postgres
SUPABASE_DB_USER=postgres
SUPABASE_DB_PASSWORD=********
```

> 연결 테스트 단계는 **Direct connection(5432)** 을 권장한다. pooler보다 단순하고
> prepared statement 충돌이 없다. pooler 전환은 연결이 확인된 뒤에 한다.

---

## 1. 설정 파일 (Claude Code 작업)

### `application.yml` — 환경변수 참조만, 평문 금지
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${SUPABASE_DB_HOST}:${SUPABASE_DB_PORT}/${SUPABASE_DB_NAME}
    username: ${SUPABASE_DB_USER}
    password: ${SUPABASE_DB_PASSWORD}
    hikari:
      maximum-pool-size: 3        # 무료 티어 커넥션 한도 보호. 보수적으로 시작
      connection-timeout: 10000
  jpa:
    hibernate:
      ddl-auto: validate          # 연결 테스트 단계에선 스키마 안 건드림. 생성은 TASK_02 본작업
    properties:
      hibernate:
        show_sql: true
        format_sql: true
```

### `application-example.yml` — 저장소에 커밋하는 자리표시자 예시
```yaml
spring:
  datasource:
    url: jdbc:postgresql://YOUR_HOST:5432/postgres
    username: YOUR_USER
    password: YOUR_PASSWORD
```

### `.gitignore` 확인
- 실제 `.env`, 로컬 시크릿 설정 파일이 무시되는지 확인.
- `application-example.yml`만 커밋, 실제 값이 든 파일은 절대 커밋 금지.

---

## 2. 연결 테스트 코드 (Claude Code 작업)

DDL·엔티티와 무관하게 **순수 커넥션만** 확인하는 가벼운 테스트를 만든다.

### 방법 A — 통합 테스트 (권장)
`back/src/test/kotlin/.../DbConnectionTest.kt`:
- `@SpringBootTest` 컨텍스트에서 `DataSource`를 주입받아
  `dataSource.connection.use { it.isValid(5) }` 가 `true`인지 검증.
- `SELECT 1` 단순 쿼리를 실행해 응답이 오는지 확인.
- 환경변수가 없으면 테스트를 스킵하도록 `@EnabledIfEnvironmentVariable`로 가드
  (CI에서 비밀값 없이 빌드 깨지지 않게).

### 방법 B — 기동 시 헬스 로그 (선택)
`ApplicationRunner` 빈으로 기동 직후 커넥션을 한 번 열어
"DB 연결 성공: <db version>" 을 로그로 남긴다. (운영 중 수동 확인용)

### 방법 C — Actuator (선택)
`spring-boot-starter-actuator` 추가 후 `/actuator/health/db` 로 상태 확인.

---

## 3. 검증 절차 (사람이 실행)

1. 환경변수 설정 후 테스트 실행:
   `./gradlew test --tests "*DbConnectionTest"`
2. 통과하면 연결 OK. 실패하면 아래 트러블슈팅.
3. 연결 확인 후 → `TASK_02` 본작업(테이블 생성, Repository)으로 진행.

---

## 4. 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| `password authentication failed` | 비밀번호·유저 불일치 | Supabase Database 설정에서 재확인. pooler는 user가 `postgres.<ref>` |
| `prepared statement "S_1" already exists` | PgBouncer transaction 모드 충돌 | Direct(5432) 사용, 또는 URL에 `?prepareThreshold=0` |
| `too many connections` | 무료 티어 커넥션 한도 초과 | `hikari.maximum-pool-size` 축소 (2~3) |
| `connection timed out` | 프로젝트 일시정지 / 방화벽 | Supabase 프로젝트 활성 상태 확인, 네트워크 확인 |
| `UnknownHostException` | host 오타 | `db.<project-ref>.supabase.co` 정확히 |
| 환경변수 `null` | 주입 누락 | IDE Run Config / 셸 export 확인 |

---

## 체크리스트

- [ ] 비밀값이 환경변수로만 주입됨 (코드·yml·VCS에 평문 없음)
- [ ] `application-example.yml`만 커밋, 실제 값 파일은 `.gitignore`
- [ ] `DbConnectionTest`가 환경변수 있을 때 통과, 없을 때 스킵
- [ ] Direct connection(5432)으로 먼저 검증
- [ ] 연결 성공 확인 후 TASK_02 본작업으로 진행

> 이 단계의 목표는 딱 하나: **"키 채우면 DB에 붙는다"를 확인.**
> 테이블 생성·Repository는 연결이 확인된 다음이다.