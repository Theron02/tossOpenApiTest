# TASK 02: Supabase 연결 + 테이블 생성 검증 + Repository 계층

이 문서는 Claude Code에게 넘기는 두 번째 작업 지시서다. 시작 전 아래를 읽는다.
- 루트 `CLAUDE.md`, `back/CLAUDE.md`
- `back/docs/DB_SCHEMA.md` — 테이블·인덱스·유니크 정의
- 선행 작업: `TASK_01_entities.md` (엔티티·enum 구현 완료 상태여야 함)

---

## 목표

1. Spring Boot를 Supabase(PostgreSQL)에 연결한다.
2. 엔티티 → 실제 테이블 생성을 검증한다.
3. 각 엔티티에 대한 Repository 인터페이스를 구현한다.

> 자동매매 로직은 모두 Spring Boot 안에서 돈다. Supabase는 **관리형 PostgreSQL로만**
> 사용한다. Supabase Auth/RLS/Edge Function/Realtime은 이 프로젝트에서 쓰지 않는다.
> 앱은 Supabase에 직접 붙지 않고 항상 Spring Boot를 경유한다.

---

## 범위 (이번 작업에서 할 것)

### 1. 데이터소스 설정

- `application.yml`(또는 profile별 분리)에 Supabase 연결 설정 추가
- **비밀값은 환경변수로만** 주입. DB 비밀번호·URL을 소스/VCS에 넣지 않는다
- 의존성: `org.postgresql:postgresql` (런타임)

### 2. Supabase 커넥션 풀 — ⚠️ 가장 주의할 부분

Supabase는 두 가지 접속 경로를 제공한다. 잘못 고르면 prepared statement 충돌이 난다.

| 경로 | 포트 | 용도 |
|------|------|------|
| Direct connection | 5432 | 직접 연결. 마이그레이션·DDL 검증에 적합 |
| Transaction pooler (PgBouncer) | 6543 | 서버리스/단기 연결용. transaction 모드 |
| Session pooler | 5432(pooler) | session 모드 |

**규칙:**
- 애플리케이션 런타임 접속은 pooler를 쓸 경우, PgBouncer **transaction 모드에서는
  서버사이드 prepared statement가 깨진다.** 다음 중 하나로 대응한다.
    - JDBC URL에 `prepareThreshold=0` 추가 (prepared statement 비활성화), 또는
    - `?pgbouncer=true` 관련 설정 + HikariCP `data-source-properties`로 `cachePrepStmts=false`
- **DDL 검증·초기 개발 단계에서는 Direct connection(5432)** 사용을 권장. 단순하고 충돌 없음
- HikariCP 풀 크기는 Supabase 무료 티어 커넥션 한도를 넘지 않게 보수적으로
  (`maximum-pool-size`를 5 이하로 시작)

### 3. 테이블 생성 검증

- 1차: `spring.jpa.hibernate.ddl-auto=create` 로 띄워 테이블이 명세대로 생성되는지 확인
  (콘솔 `show-sql=true`로 DDL 로그 확인)
- 생성된 스키마가 `DB_SCHEMA.md`와 일치하는지 점검:
    - `trade_order` 테이블명, 유니크 제약(position·strategy_config·candle), `candle` 인덱스
    - jsonb 컬럼(`params`, `indicator_snapshot`)이 jsonb 타입으로 생성됐는지
- 2차: 확인 후 `ddl-auto`를 `validate`로 전환 (운영 시 스키마 드리프트 방지)
- **권장**: 장기적으로는 Flyway 마이그레이션으로 전환 (이번엔 선택. 도입 시 V1 스크립트 생성)

### 4. Repository 인터페이스

각 엔티티에 대해 Spring Data JPA `JpaRepository`를 구현한다. 필요한 조회 메서드:

- `PaperAccountRepository`
- `StockRepository` — `findByIsActiveTrue()`
- `PositionRepository` — `findByAccountId(id)`, `findByAccountIdAndStockCode(id, code)`
- `TradeOrderRepository` — `findByAccountIdAndStatus(id, status)`,
  `existsByIdempotencyKey(key)` (멱등성 확인용), `findByStockCodeAndStatusIn(...)`
- `ExecutionRepository` — `findByOrderId(orderId)`
- `StrategyConfigRepository` — `findByEnabledTrue()`, `findByAccountIdAndEnabledTrue(id)`
- `SignalLogRepository` — `findByStrategyConfigIdOrderByCreatedAtDesc(id)`
- `RiskSettingRepository` — `findByAccountId(id)`
- `CandleRepository` —
  `findByStockCodeAndIntervalOrderByCandleTimeDesc(code, interval, pageable)`
  (지표 계산 시 최근 N개 봉 조회. Pageable로 limit)

---

## 반드시 지킬 것 (체크리스트)

- [ ] DB 비밀번호·연결정보는 환경변수(`SUPABASE_DB_URL`, `SUPABASE_DB_PASSWORD` 등)로만
- [ ] `application.yml`에 평문 비밀값 없음. `.env`/예시 파일은 `application-example.yml`로 제공
- [ ] `.gitignore`에 실제 `.env`/로컬 설정 포함 확인
- [ ] PgBouncer 사용 시 prepared statement 충돌 대응(`prepareThreshold=0` 등) 적용
- [ ] HikariCP 풀 크기 보수적(≤5 시작)
- [ ] 멱등성 조회(`existsByIdempotencyKey`)는 반드시 포함 (주문 중복 방지의 핵심)
- [ ] Candle 조회는 limit 가능한 형태(Pageable)로 — 전체 스캔 금지
- [ ] 테이블 검증 후 `ddl-auto`를 `validate`로 전환

---

## 산출물

```
back/src/main/resources/application.yml          (datasource·jpa 설정)
back/src/main/resources/application-example.yml  (비밀값 자리표시자 예시)
back/src/main/kotlin/com/.../repository/*.kt      (Repository 9개)
back/build.gradle.kts                             (postgresql 드라이버)
back/src/test/kotlin/com/.../repository/*Test.kt  (선택: Testcontainers 통합 테스트)
```

---

## 작업 후 검증

1. 앱 기동 → Supabase에 연결되고 테이블 9개가 생성되는지 (`ddl-auto=create`, `show-sql`)
2. Supabase 대시보드 Table Editor에서 테이블·컬럼·제약 육안 확인
3. `ddl-auto=validate`로 재기동 시 스키마 불일치 에러가 없는지
4. (선택) Testcontainers로 Repository 쿼리 메서드 동작 확인

---

## 트러블슈팅 메모 (예상 이슈)

- **`prepared statement "S_1" already exists`**: PgBouncer transaction 모드 + prepared
  statement 충돌. → `prepareThreshold=0` 또는 direct connection(5432)로.
- **`too many connections`**: 무료 티어 커넥션 한도 초과. → HikariCP `maximum-pool-size` 축소.
- **jsonb 매핑 오류**: `hypersistence-utils` 의존성/`@Type(JsonType::class)` 누락 확인
  (TASK_01에서 추가됐어야 함).
- **연결 타임아웃**: Supabase 프로젝트 일시정지(무료 티어 비활성) 여부 확인.

---

## 범위 밖 (다음 작업)

- KIS 연동 (토큰 발급 → 현재가 조회)
- DTO·Service·Controller
- 전략 엔진

> 이번 작업은 "DB에 안정적으로 붙고, 스키마가 정확히 서고, 데이터 접근 계층이 준비된 상태"
> 까지가 목표다. 비즈니스 로직은 다음 작업에서.
