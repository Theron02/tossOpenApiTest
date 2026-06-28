# 데이터베이스 스키마 명세

이 문서는 Claude Code가 JPA 엔티티·마이그레이션을 구현할 때 참조하는 설계 명세다.
구현 규칙(패키지 구조, 코딩 컨벤션)은 `backend/CLAUDE.md`를 따른다.

---

## 0. 공통 설계 원칙

- **금액·수량**: 모든 금액은 `Long`(원 단위 정수). 부동소수점 금지.
- **PK**: UUID. 앱에서 생성(`UUID.randomUUID()`), DB auto-increment 사용 안 함.
- **시각**: `Instant`(UTC) 저장. `@EnableJpaAuditing` + `BaseEntity`로 `created_at`/`updated_at` 공통 처리.
- **연관관계**: `@ManyToOne(fetch = LAZY)` 기본. 양방향 매핑은 꼭 필요할 때만.
- **enum**: `@Enumerated(EnumType.STRING)`으로 저장 (ordinal 금지).
- **예약어 주의**: `order`는 SQL 예약어. 테이블명은 `trade_order`로 한다.
- **빈약한 도메인 모델 지양**: 핵심 불변식·계산(평단가 갱신, 상태 전이 등)은
  엔티티 메서드로 캡슐화한다. Service는 이를 호출만 한다.
- **jsonb**: 전략 파라미터·지표 스냅샷처럼 가변 구조는 `jsonb` 컬럼으로.
  (라이브러리: `hypersistence-utils` 또는 동등한 jsonb 매핑)

---

## 1. ERD 개요

```
PAPER_ACCOUNT ||--o{ POSITION        (계좌 1 : N 포지션)
PAPER_ACCOUNT ||--o{ TRADE_ORDER     (계좌 1 : N 주문)
PAPER_ACCOUNT ||--o{ STRATEGY_CONFIG (계좌 1 : N 전략설정)
PAPER_ACCOUNT ||--|| RISK_SETTING    (계좌 1 : 1 리스크설정)
STOCK         ||--o{ POSITION        (종목 1 : N 포지션)
STOCK         ||--o{ TRADE_ORDER     (종목 1 : N 주문)
STOCK         ||--o{ CANDLE          (종목 1 : N 캔들)
STRATEGY_CONFIG ||--o{ SIGNAL_LOG    (전략설정 1 : N 신호로그)
TRADE_ORDER   ||--o{ EXECUTION       (주문 1 : N 체결)
SIGNAL_LOG    ||--o| TRADE_ORDER     (신호 0..1 : 1 주문 — 주문이 신호를 참조)
```

---

## 2. 테이블 명세

### 2.1 `paper_account` — 모의투자 계좌

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | |
| name | varchar | NOT NULL | 계좌명 |
| cash_balance | bigint | NOT NULL | 현재 현금 잔고 (원) |
| initial_seed | bigint | NOT NULL, 불변 | 최초 시드머니 (원). 수익률 기준 |
| is_live | boolean | NOT NULL, default false | 실거래 여부. 기본 모의투자 |
| created_at / updated_at | timestamp | NOT NULL | BaseEntity |

**엔티티 메서드**
- `withdraw(amount: Long)`: 잔고 차감. `amount>0`, `cashBalance>=amount` 검증.
- `deposit(amount: Long)`: 잔고 증가. `amount>0` 검증.

---

### 2.2 `stock` — 종목 마스터

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| code | varchar(6) | PK | 종목코드. 자연키 (예: "005930") |
| name | varchar | NOT NULL | 종목명 |
| market | varchar(16) | NOT NULL, enum | `KOSPI` / `KOSDAQ` |
| is_active | boolean | NOT NULL, default true | 거래 가능 여부 |

> `code`는 KIS API 종목 식별자와 일치시킨다.

---

### 2.3 `position` — 보유 포지션

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | |
| account_id | UUID | FK → paper_account, NOT NULL | |
| stock_code | varchar(6) | FK → stock, NOT NULL | |
| quantity | int | NOT NULL | 보유 수량 |
| avg_price | bigint | NOT NULL | 평균 매입 단가 (원) |

**유니크 제약**: `(account_id, stock_code)` — 계좌+종목 당 하나.

**엔티티 메서드**
- `addBuy(filledQty, filledPrice)`: 매수 체결 반영. 평단가를 **가중평균**으로 갱신.
  `newAvg = (avgPrice*qty + filledPrice*filledQty) / (qty + filledQty)` (정수 나눗셈).
- `reduceSell(filledQty)`: 매도 체결 반영. 수량 차감, 평단가 유지. `quantity>=filledQty` 검증.
- `isEmpty`: 수량 0 여부.

---

### 2.4 `trade_order` — 주문

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | |
| account_id | UUID | FK → paper_account, NOT NULL | |
| stock_code | varchar(6) | FK → stock, NOT NULL | |
| side | varchar(8) | NOT NULL, enum | `BUY` / `SELL` |
| order_type | varchar(8) | NOT NULL, enum | `MARKET` / `LIMIT` |
| quantity | int | NOT NULL | 주문 수량 |
| price | bigint | NULL | 지정가(LIMIT)일 때만. 시장가면 null |
| status | varchar(16) | NOT NULL | 주문 상태머신 (아래) |
| filled_quantity | int | NOT NULL, default 0 | 누적 체결 수량 |
| idempotency_key | varchar | NOT NULL, **UNIQUE** | 중복 주문 방지 멱등키 |
| signal_id | UUID | NULL | 유발 신호(signal_log) 참조. 수동이면 null |

**불변식 (init 검증)**: `quantity>0`. `order_type=LIMIT`이면 `price` 필수.

**상태머신** (`OrderStatus`):
```
PENDING → PARTIAL / FILLED / CANCELLED / REJECTED
PARTIAL → FILLED / CANCELLED
FILLED, CANCELLED, REJECTED → (종료, 전이 불가)
```
- `transitionTo(next)`: 불법 전이 시 예외.
- `applyFill(filledQty)`: 체결 누적 + 상태 자동 전이.
  전량 체결 → `FILLED`, 일부 체결 → `PARTIAL`. `filled<=quantity` 검증.
- `remainingQuantity`: `quantity - filledQuantity`.

> **모든 주문은 RiskManager 통과 후에만 생성**한다. 엔티티는 그 사실을 강제하지 않으므로
> Service 계층에서 보장한다 (`backend/CLAUDE.md` 5번).

---

### 2.5 `execution` — 체결 내역

주문 1건이 여러 번 나눠 체결될 수 있어 `trade_order : execution = 1 : N`.
(부분체결 대응 + 정확한 평단가/수수료 계산)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | |
| order_id | UUID | FK → trade_order, NOT NULL | |
| filled_qty | int | NOT NULL | 이번 체결 수량 |
| filled_price | bigint | NOT NULL | 이번 체결 단가 (원) |
| fee | bigint | NOT NULL | 이번 체결 수수료 (원) |
| executed_at | timestamp | NOT NULL | 체결 시각 |

**불변식**: `filled_qty>0`, `filled_price>0`, `fee>=0`.
**파생값**: `grossAmount = filled_price * filled_qty` (수수료 제외 대금).

---

### 2.6 `strategy_config` — 전략 설정

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | |
| account_id | UUID | FK → paper_account, NOT NULL | |
| strategy_name | varchar | NOT NULL | TradingStrategy 구현체 name (예: "GOLDEN_CROSS") |
| stock_code | varchar(6) | NOT NULL | 대상 종목 |
| params | jsonb | NOT NULL | 전략 파라미터 (예: `{"shortPeriod":5,"longPeriod":20}`) |
| enabled | boolean | NOT NULL, default false | 활성 여부. 엔진은 enabled=true만 평가 |

**유니크 제약**: `(account_id, strategy_name, stock_code)`.

> `params`를 jsonb로 둔 이유: 전략마다 파라미터 구조가 달라 컬럼 고정이 불가.
> 새 전략(ML 포함) 추가 시 스키마 변경 없이 확장 가능.

**엔티티 메서드**: `enable()`, `disable()`.

---

### 2.7 `signal_log` — 신호 발생 기록 (추적성)

"왜 이 시점에 이 신호가 나왔는가"를 추적. 백테스트 검증·디버깅용.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | |
| strategy_id | UUID | FK → strategy_config, NOT NULL | |
| stock_code | varchar(6) | NOT NULL | |
| signal | varchar(8) | NOT NULL, enum | `BUY` / `SELL` / `HOLD` |
| indicator_snapshot | jsonb | NOT NULL | 산출 시점 지표값 (예: `{"ma5":71000,"ma20":70500,"rsi":68.2}`) |
| created_at | timestamp | NOT NULL | |

> 주문으로 이어지면 `trade_order.signal_id`가 이 로그를 참조한다.

---

### 2.8 `risk_setting` — 리스크 설정 (계좌 1:1)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | |
| account_id | UUID | FK → paper_account, NOT NULL, **UNIQUE** | 1:1 |
| daily_loss_limit | bigint | NOT NULL | 일일 누적 손실 한도 (원). 도달 시 신규 매수 차단 |
| max_position_pct | int | NOT NULL | 종목당 최대 비중 (1~100%) |
| kill_switch | boolean | NOT NULL, default false | true면 모든 신규 주문 즉시 차단 |

**불변식**: `max_position_pct in 1..100`, `daily_loss_limit>=0`.
**엔티티 메서드**: `activateKillSwitch()`, `deactivateKillSwitch()`.

> RiskManager가 주문 직전 이 설정을 검사한다.
> 리스크 파라미터를 계좌 본체에서 분리해, 런타임 변경이 계좌에 영향 주지 않게 한다.

---

### 2.9 `candle` — OHLCV 봉 데이터

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | UUID | PK | |
| stock_code | varchar(6) | NOT NULL | |
| candle_interval | varchar(8) | NOT NULL, enum | `MIN_1` / `MIN_5` / `MIN_15` / `MIN_30` / `HOUR_1` / `DAY_1` |
| open / high / low / close | bigint | NOT NULL | OHLC (원) |
| volume | bigint | NOT NULL | 거래량 |
| candle_time | timestamp | NOT NULL | 봉 시작 시각 (UTC) |

**유니크 제약**: `(stock_code, candle_interval, candle_time)`.
**인덱스**: `(stock_code, candle_interval, candle_time)` — 지표 계산 시 기간 조회 패턴.

> 지표 계산기(IndicatorCalculator)는 이 데이터를 기간 조회해 MA/RSI/Bollinger를 산출한다.

---

## 3. enum 정의 (`domain/type`)

| enum | 값 |
|------|-----|
| `Signal` | BUY, SELL, HOLD |
| `OrderSide` | BUY, SELL |
| `OrderType` | MARKET, LIMIT |
| `OrderStatus` | PENDING, PARTIAL, FILLED, CANCELLED, REJECTED (+ `canTransitionTo`, `isTerminal`) |
| `CandleInterval` | MIN_1, MIN_5, MIN_15, MIN_30, HOUR_1, DAY_1 |
| `Market` | KOSPI, KOSDAQ |

---

## 4. 구현 순서 (Claude Code 작업 가이드)

1. `domain/type/Enums.kt` — enum 6종 + OrderStatus 전이 로직
2. `entity/BaseEntity.kt` — 감사 필드, `@EnableJpaAuditing` 설정 추가
3. 독립 엔티티: `Stock`, `PaperAccount`, `Candle`
4. 의존 엔티티: `Position`, `TradeOrder`(Order), `Execution`, `StrategyConfig`, `SignalLog`, `RiskSetting`
5. 각 엔티티의 도메인 메서드 + 단위 테스트 (평단가 가중평균, 주문 상태 전이는 필수)
6. Repository 인터페이스 (다음 단계)

> 금전 계산(평단가, 손익, 수수료)과 상태 전이는 반드시 단위 테스트를 동반한다.