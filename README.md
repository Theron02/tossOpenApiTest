# tossOpenApiTest

기술적 지표를 기반으로 주식 매매 신호를 **자동으로 판단하고 자동으로 주문을 실행**하는
알고리즘 트레이딩 시스템. 사용자는 차트를 직접 보고 매매하지 않으며, 봇이 장중 판단·실행을
담당한다. 클라이언트 앱은 모니터링·제어용이다.

**최종 목표는 실거래(live trading)** 다. 다만 자동매매는 버그·전략 결함이 곧바로 실제 손실로
이어지므로, **모의 거래(paper trading)로 충분히 검증한 뒤** 실거래를 명시적으로 활성화하는
단계적 접근을 취한다. 토스증권 Open API는 모의투자(sandbox) 환경을 제공하지 않으므로,
모의 거래는 **백엔드가 자체적으로** 구현한다 — 토스 시세는 실제로 받되, 주문은 토스로 보내지
않고 현재가 기준으로 가상 체결해 DB에만 기록한다.

> ⚠️ 실거래는 본인 책임이다. 자동매매로 발생하는 손익·손실에 대한 책임은 전적으로
> 운영자에게 있다. 실거래 활성화 전 반드시 모의 거래로 전략·리스크 가드를 검증한다.

---

## 핵심 개념

"차트를 보고 판단한다"는 것은 사람처럼 차트를 눈으로 보는 것이 아니라,
차트를 구성하는 시세 데이터(OHLCV)를 코드가 분석해 지표를 계산하고, 규칙에 따라
매수/매도/관망을 자동 결정하는 것을 의미한다.

```
시세 수신 → 지표 계산(이평선·RSI 등) → 전략 평가 → 리스크 검사 → 주문 실행
   → (반복)
```

---

## 기술 스택

| 영역 | 스택 |
|------|------|
| 백엔드 (`back/`) | Kotlin, Spring Boot 3.x, Spring MVC, JPA, Gradle |
| 프론트 (`front/`) | Flutter, Dart, MVVM, Riverpod |
| DB | PostgreSQL (Supabase) |
| 캐시 | Redis (시세 캐싱·토큰·멱등키) |
| 외부 API | 토스증권 Open API v1.1.5 — REST (OAuth 2.0), 국내·미국 주식 |

> 토스증권 Open API를 사용한다. 표준 REST + OAuth 2.0 Client Credentials 인증.
> WebSocket은 토스에서 아직 미지원(추후 예정)이라 실시간 시세는 **폴링**으로 받는다.
> 모의투자(sandbox) 환경이 없어 모의 거래는 백엔드가 자체 구현한다.

---

## 아키텍처

```
Flutter (모니터링·제어)
   │  REST (조회·설정)  /  WebSocket (실시간 손익·체결·신호)
   ▼
Spring Boot (트레이딩 엔진)
   ├─ MarketDataCollector   토스 /prices·/candles 폴링, OHLCV 수집
   ├─ IndicatorCalculator   이평선·RSI·볼린저 등 지표 계산
   ├─ StrategyEngine        TradingStrategy 평가 → BUY / SELL / HOLD
   ├─ RiskManager           주문 직전 가드 (손절·한도·kill switch)
   ├─ OrderExecutor         Paper(가상 체결) / Toss(실거래) 주문, 상태머신
   └─ Scheduler             장 운영시간 체크, 토스 토큰 갱신
   │
   ├─ PostgreSQL  계좌·주문·체결·포지션·전략설정
   └─ Redis       현재가 캐시, 토스 토큰, 멱등키
```

전략은 `TradingStrategy` 인터페이스로 추상화한다. 규칙 기반 전략(골든크로스·RSI)으로
시작하되, 추후 ML 모델 전략을 같은 인터페이스로 끼워넣을 수 있다.

### 모의 거래 → 실거래 전환

같은 코드로 두 모드를 모두 지원한다. 검증은 모의 거래로 하고, 준비되면 모드만 전환한다.

- `PaperAccount.isLive` 플래그 (기본 `false` = 모의 거래)
- `OrderExecutor`를 인터페이스로 두고 구현 분리:
  - `PaperOrderExecutor` (기본) — 토스 시세로 가상 체결, 토스에 주문 전송 안 함
  - `TossOrderExecutor` — 토스 `POST /orders`로 실제 주문 실행
- 주입은 설정 플래그로 제어. 실거래 모드는 명시적으로만 활성화된다.

---

## 프로젝트 구조

```
tossOpenApiTest/
├── CLAUDE.md              전체 프로젝트 컨텍스트 (Claude Code용)
├── back/                  Spring Boot + Kotlin (MVC)
│   ├── CLAUDE.md          백엔드 작업 규칙
│   └── docs/
│       └── DB_SCHEMA.md   DB 스키마 설계 명세
└── front/                 Flutter (MVVM)
    └── CLAUDE.md          프론트 작업 규칙
```

---

## 개발 현황

- [x] 프로젝트 설계 (아키텍처·도메인 모델)
- [x] Claude Code 작업 규칙 (`CLAUDE.md` × 3)
- [x] DB 스키마 명세 (`back/docs/DB_SCHEMA.md`)
- [x] JPA 엔티티 + enum 구현
- [x] DB 연결 검증 (Supabase)
- [x] Repository 계층 + 테이블 생성
- [ ] 토스 연동 (인증 → 현재가 → 캔들 수집)
- [ ] 전략 엔진 (TradingStrategy + 골든크로스/RSI)
- [ ] 리스크 관리 + 주문 실행기 (`PaperOrderExecutor`)
- [ ] Flutter 모니터링 화면
- [ ] 모의 거래 전체 흐름 검증 (전략→리스크→체결→손익)
- [ ] 실거래 전환 (`TossOrderExecutor`, 토스 POST /orders) — 충분한 검증 이후

---

## 안전 원칙

- **모의 거래가 기본값**이다. 실거래는 모의 거래로 전략·리스크 가드를 충분히 검증한 뒤
  명시적으로만 활성화한다.
- 모든 주문은 `RiskManager`(일일 손실 한도·종목당 비중·kill switch)를 경유한다.
- 토스 `client_id`/`client_secret`, DB 비밀번호 등 비밀값은 환경변수로만 주입한다
  (소스·VCS 포함 금지).
- 금액 계산은 정수(원) 기반. 부동소수점을 사용하지 않는다.
- 실거래 주문을 실행하는 코드(`LiveOrderExecutor`)는 실제 금전 거래를 발생시키므로,
  도입·활성화 시 각별한 검토와 테스트를 거친다.