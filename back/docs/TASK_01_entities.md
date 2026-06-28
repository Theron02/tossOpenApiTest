# TASK: JPA 엔티티 + enum 구현

이 문서는 Claude Code에게 넘기는 작업 지시서다. 작업 시작 전 아래를 반드시 먼저 읽는다.
- 루트 `CLAUDE.md` — 전체 컨텍스트·도메인 용어·공통 컨벤션
- `back/CLAUDE.md` — 백엔드 패키지 구조·코딩 규칙·테스트 규칙
- `back/docs/DB_SCHEMA.md` — **이 작업의 핵심 명세.** 테이블·컬럼·제약·도메인 메서드 정의

---

## 목표

`DB_SCHEMA.md`에 정의된 9개 테이블과 6종 enum을 JPA 엔티티로 구현한다.
도메인 메서드(평단가 가중평균, 주문 상태 전이 등)와 핵심 단위 테스트를 포함한다.

---

## 범위 (이번 작업에서 할 것)

1. **enum 6종** (`back/.../domain/type/`)
    - `Signal`, `OrderSide`, `OrderType`, `OrderStatus`, `CandleInterval`, `Market`
    - `OrderStatus`에는 `canTransitionTo(next)`와 `isTerminal` 포함 (상태머신 규칙은 DB_SCHEMA 2.4)

2. **BaseEntity** (`back/.../entity/`)
    - `created_at` / `updated_at` 공통 처리 (`Instant`, UTC)
    - `@EnableJpaAuditing` 설정을 메인 클래스 또는 별도 config에 추가

3. **엔티티 9종** (`back/.../entity/`)
    - 독립: `Stock`, `PaperAccount`, `Candle`
    - 의존: `Position`, `TradeOrder`, `Execution`, `StrategyConfig`, `SignalLog`, `RiskSetting`
    - 각 컬럼·타입·제약·유니크·인덱스는 DB_SCHEMA.md 그대로 따른다
    - 도메인 메서드를 엔티티 안에 구현 (DB_SCHEMA의 "엔티티 메서드" 항목)

4. **단위 테스트** (`back/.../test/`)
    - `Position.addBuy()` 가중평균 평단가 계산 (분할 매수 시나리오)
    - `Position.reduceSell()` 수량 차감·부족 시 예외
    - `Order.applyFill()` 부분체결 → PARTIAL → 전량체결 → FILLED 전이
    - `OrderStatus.canTransitionTo()` 불법 전이 차단 (예: FILLED → PENDING)
    - `PaperAccount.withdraw()` 잔고 부족 예외

---

## 반드시 지킬 것 (체크리스트)

- [ ] 테이블명은 `trade_order` (order는 SQL 예약어)
- [ ] 모든 금액·수량 필드는 `Long` (또는 int 수량). `Double`/`Float` 금지
- [ ] PK는 `UUID`, 앱에서 생성 (`UUID.randomUUID()`)
- [ ] enum은 `@Enumerated(EnumType.STRING)`
- [ ] 연관관계는 `@ManyToOne(fetch = LAZY)` 기본
- [ ] 엔티티를 컨트롤러 응답에 직접 노출하지 않는다 (이번엔 엔티티만, DTO는 다음 작업)
- [ ] jsonb 컬럼(`params`, `indicator_snapshot`)은 jsonb 매핑 라이브러리 사용
  (`hypersistence-utils-hibernate-63` 등). 의존성 추가 필요 시 build.gradle.kts에 명시
- [ ] 도메인 메서드의 불변식은 `require`/`check`로 검증
- [ ] 금전 계산·상태 전이 로직은 단위 테스트 동반

---

## 산출물

```
back/src/main/kotlin/com/.../domain/type/Enums.kt   (또는 enum별 파일)
back/src/main/kotlin/com/.../entity/BaseEntity.kt
back/src/main/kotlin/com/.../entity/*.kt            (엔티티 9개)
back/src/main/kotlin/com/.../config/JpaConfig.kt    (auditing 활성화)
back/src/test/kotlin/com/.../entity/*Test.kt        (단위 테스트)
build.gradle.kts                                    (jsonb 의존성 추가)
```

---

## 작업 후 검증

1. `./gradlew compileKotlin` — 컴파일 통과
2. `./gradlew test` — 단위 테스트 통과
3. (DB 연결 후) `spring.jpa.hibernate.ddl-auto=validate` 또는 `create`로
   테이블이 명세대로 생성되는지 확인 — 단, **이 검증은 다음 작업(Supabase 연결)에서** 수행.
   이번 작업은 엔티티 정의 + 테스트까지.

---

## 범위 밖 (다음 작업)

- Repository 인터페이스
- Supabase 데이터소스 설정 + 테이블 생성 검증
- DTO·Service·Controller
- KIS 연동

> 위 항목은 이번 PR에 넣지 않는다. 엔티티 레이어에 집중한다.