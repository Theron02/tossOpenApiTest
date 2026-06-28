# CLAUDE.md — 자동매매 시스템 (루트)

이 문서는 프로젝트 전반의 컨텍스트다. 백엔드/프론트 세부 규칙은 각 하위 디렉토리의 `CLAUDE.md`를 따른다.

- 백엔드: `backend/CLAUDE.md` (Spring Boot + Kotlin, MVC)
- 프론트: `frontend/CLAUDE.md` (Flutter, MVVM)

---

## 1. 프로젝트 목표

기술적 지표(이동평균선, RSI, 볼린저밴드 등)를 기반으로 주식 매매 신호를
**자동으로 판단하고, 자동으로 주문을 실행**하는 알고리즘 트레이딩 시스템.

- 사용자는 차트를 직접 보고 매매하지 않는다. 봇이 24시간(장중) 판단·실행한다.
- Flutter 앱은 **모니터링·제어용**이다. 전략 ON/OFF, 파라미터 설정, 손익·체결 확인.
  매매 의사결정에 사람이 개입하지 않는다.

### 핵심 원칙
- **반드시 모의투자(paper trading)로 먼저 동작한다.** 실계좌 연동은 충분한 검증 이후 별도 플래그로만.
- 모든 주문 경로에 리스크 가드(손절·익절·일일 손실 한도·kill switch)가 선행한다.
- 전략은 인터페이스로 추상화한다. 규칙 기반 전략으로 시작하되, 추후 ML 모델 전략을
  같은 인터페이스로 끼워넣을 수 있어야 한다.

---

## 2. 기술 스택

| 영역 | 스택 |
|------|------|
| 백엔드 | Kotlin, Spring Boot 3.x, Spring MVC, Gradle (Kotlin DSL), JDK 21 |
| DB | PostgreSQL (영속), Redis (시세 캐싱·토큰·세션) |
| 외부 API | 한국투자증권(KIS) OpenAPI — REST + WebSocket |
| 프론트 | Flutter 3.x, Dart, MVVM, Riverpod |
| 실시간 | KIS WebSocket → 백엔드 중계 → 앱(STOMP/WebSocket) |
| 인증 | JWT (앱 ↔ 백엔드) |

> KIS OpenAPI는 모의투자 계좌로 시세 조회·주문을 정식 지원한다. 실거래 전환은
> 도메인 토글로만 분리하며, 기본값은 항상 모의투자다.

---

## 3. 시스템 아키텍처

```
Flutter (모니터링·제어)
   │  REST (조회·설정)  /  WebSocket (실시간 손익·체결·신호)
   ▼
Spring Boot (트레이딩 엔진 = "차트 보고 판단하는 뇌")
   ├─ MarketDataCollector   KIS WebSocket 구독, OHLCV 수집
   ├─ IndicatorCalculator   이평선·RSI·볼린저 등 지표 계산
   ├─ StrategyEngine        TradingStrategy 평가 → BUY / SELL / HOLD 신호
   ├─ RiskManager           주문 직전 가드 (손절·한도·kill switch)
   ├─ OrderExecutor         KIS 주문 API 호출, 체결 폴링, 상태머신
   └─ Scheduler             장 운영시간 체크, KIS 토큰 갱신
   │
   ├─ PostgreSQL  계좌·주문·체결·포지션·전략설정
   └─ Redis       현재가 캐시, KIS 토큰, 멱등키
```

### 자동매매 판단 루프 (장중 반복)
```
시세 수신 → 지표 계산 → 전략 평가(조건 판단)
   → BUY  → RiskManager 통과 → 주문 실행
   → SELL → RiskManager 통과 → 주문 실행
   → HOLD → 대기
→ 다시 시세 수신
```

---

## 4. 디렉토리 구조

```
auto-trading/
├── CLAUDE.md              ← 이 파일 (전체 컨텍스트)
├── backend/
│   ├── CLAUDE.md          ← 백엔드 규칙
│   └── src/main/kotlin/...
└── frontend/
    ├── CLAUDE.md          ← 프론트 규칙
    └── lib/...
```

---

## 5. 도메인 용어 (백엔드·프론트 공통)

작업 시 아래 용어를 코드 식별자에 일관되게 사용한다.

| 용어 | 의미 |
|------|------|
| `Signal` | 전략이 산출한 매매 신호. `BUY` / `SELL` / `HOLD` |
| `Strategy` | 매매 규칙 단위. `TradingStrategy` 인터페이스 구현체 |
| `Indicator` | 시세에서 파생된 지표값 (MA, RSI, Bollinger 등) |
| `Candle` / `OHLCV` | 시가·고가·저가·종가·거래량 봉 데이터 |
| `Position` | 보유 종목 + 수량 + 평단가 |
| `Order` | 주문. 상태: `PENDING → FILLED / PARTIAL / CANCELLED / REJECTED` |
| `PaperAccount` | 모의투자 계좌 (가상 시드머니) |
| `RiskGuard` | 주문 차단 규칙 (손절·일일 손실 한도·종목당 비중) |
| `KillSwitch` | 전 전략 즉시 정지 + 선택적 전량 청산 |

---

## 6. 공통 컨벤션

- **언어**: 코드 식별자·주석은 영어, 설명·커밋 메시지는 한국어 허용.
- **시간**: 모든 시각은 `Asia/Seoul` 기준. 저장은 UTC, 표시는 KST.
- **통화·수량**: 금액은 정수(원) 또는 `BigDecimal`. 부동소수점으로 돈 계산 금지.
- **금지**: 어떤 코드도 KIS 앱키/시크릿, JWT 시크릿을 하드코딩하지 않는다.
  비밀값은 환경변수·`.env`(gitignore)로만 주입.
- **로그**: 모든 주문 시도·체결·리스크 차단은 구조화 로그로 남긴다 (감사 추적).

---

## 7. Claude 작업 지침

- 새 전략을 추가할 때는 `TradingStrategy` 인터페이스만 구현한다. 엔진 코드를 직접 고치지 않는다.
- 주문을 발생시키는 코드는 **반드시** `RiskManager`를 거치게 한다. 우회 경로를 만들지 않는다.
- 실계좌 주문 코드를 생성할 때는 명시적으로 사용자 확인을 받는다. 기본은 모의투자.
- 외부 API(KIS) 응답 스키마가 불확실하면 추측하지 말고 사용자에게 실제 응답 예시를 요청한다.
- 금융 손익 계산 로직은 반드시 단위 테스트를 동반한다.