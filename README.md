# tossOpenApiTest

기술적 지표를 기반으로 주식 매매 신호를 **자동으로 판단하고 자동으로 주문을 실행**하는
알고리즘 트레이딩 시스템. 사용자는 차트를 직접 보고 매매하지 않으며, 봇이 장중 판단·실행을
담당한다. 클라이언트 앱은 모니터링·제어용이다.

> ⚠️ 학습·포트폴리오 목적 프로젝트. 모든 매매는 **모의투자(paper trading)** 로 동작한다.
> 실거래 연동은 충분한 검증 이후 별도 플래그로만 활성화한다.

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
| 외부 API | 한국투자증권(KIS) OpenAPI — REST + WebSocket |

> 증권사 OpenAPI는 모의투자 계좌의 시세 조회·주문을 정식 지원하는 한국투자증권(KIS)을
> 사용한다. (레포명은 초기 토스 API 검토 흔적)

---

## 아키텍처

```
Flutter (모니터링·제어)
   │  REST (조회·설정)  /  WebSocket (실시간 손익·체결·신호)
   ▼
Spring Boot (트레이딩 엔진)
   ├─ MarketDataCollector   KIS WebSocket 구독, OHLCV 수집
   ├─ IndicatorCalculator   이평선·RSI·볼린저 등 지표 계산
   ├─ StrategyEngine        TradingStrategy 평가 → BUY / SELL / HOLD
   ├─ RiskManager           주문 직전 가드 (손절·한도·kill switch)
   ├─ OrderExecutor         KIS 주문 호출, 체결 폴링, 상태머신
   └─ Scheduler             장 운영시간 체크, KIS 토큰 갱신
   │
   ├─ PostgreSQL  계좌·주문·체결·포지션·전략설정
   └─ Redis       현재가 캐시, KIS 토큰, 멱등키
```

전략은 `TradingStrategy` 인터페이스로 추상화한다. 규칙 기반 전략(골든크로스·RSI)으로
시작하되, 추후 ML 모델 전략을 같은 인터페이스로 끼워넣을 수 있다.

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
- [ ] JPA 엔티티 + enum 구현
- [ ] Supabase 연결 + 테이블 생성 확인
- [ ] Repository 계층
- [ ] KIS 연동 (토큰 발급 → 현재가 조회)
- [ ] 전략 엔진 (TradingStrategy + 골든크로스/RSI)
- [ ] 리스크 관리 + 주문 실행기
- [ ] Flutter 모니터링 화면

---

## 안전 원칙

- 모든 매매는 기본적으로 모의투자로 동작한다.
- 모든 주문은 `RiskManager`(일일 손실 한도·종목당 비중·kill switch)를 경유한다.
- KIS 앱키/시크릿, DB 비밀번호 등 비밀값은 환경변수로만 주입한다 (소스·VCS 포함 금지).
- 금액 계산은 정수(원) 기반. 부동소수점을 사용하지 않는다.