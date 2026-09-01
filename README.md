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
├── front/                 Flutter (MVVM)
│   ├── CLAUDE.md          프론트 작업 규칙
│   ├── README.md          앱 실행·구조·계약 매핑
│   └── lib/               app·core·shared·features(auth/dashboard/positions/orders/signals/strategy/risk/backtest)
└── ml/                    Python 상승예측 서비스 (FastAPI + 학습)
    ├── CLAUDE.md          ML 작업 규칙
    ├── README.md          서비스 실행·구조
    ├── docs/ML_API_CONTRACT.md   백엔드 ↔ ML /predict 계약
    ├── mlcore/            학습·추론 공유 피처·라벨 (skew 방지)
    ├── app/               추론 서비스 (/predict /health /model-info)
    └── training/          학습 파이프라인 (baseline 상승확률 모델)
```

---

## 실행 방법

세 모듈은 독립 실행된다. 최소로는 **백엔드만** 띄우면 되고, ML 예측(`ML` 전략)을 쓸 때만 `ml/`를,
모니터링 UI가 필요할 때만 `front/`를 함께 띄운다. 실행 순서 권장: (선택) ML → 백엔드 → 프론트.

**사전 준비**: JDK 21 · Python 3.11+ · Flutter 3.22+

### 1) 백엔드 (Spring Boot, 포트 8080)

```bash
cd back

# 최초 1회: 비밀값 환경변수 준비. application-example.yml 을 참고해 back/.env 작성
#   필요한 값: SUPABASE_DB_*, TOSS_CLIENT_ID/SECRET, APP_JWT_SECRET(32자↑), APP_AUTH_PASSWORD_HASH
#   (bootRun 이 back/.env 를 자동 로딩한다. .env 는 gitignore)

./gradlew bootRun            # http://localhost:8080 기동 (.env 자동 주입)
./gradlew test               # 테스트
```

**자동매매 루프 로컬 테스트** — 기본은 꺼져 있다. `back/.env` 에 아래를 추가하고 재기동:
```env
TRADING_ENABLED=true
TRADING_IGNORE_MARKET_HOURS=true    # 장 운영시간(평일 09:00~15:30 KST) 무시
TRADING_SEED_DEMO=true              # 데모 계좌·리스크설정·활성 전략(GOLDEN_CROSS/005930/1분봉) 시드
TRADING_POLL_INTERVAL_MS=10000      # (선택) 루프 주기 10초
```
> `TRADING_SEED_DEMO=true` 는 **실제 DB에 데모 행을 쓴다**(없을 때만). GOLDEN_CROSS 는 교차 시점에만
> 주문을 내므로 대부분 tick 은 `신호 산출 … HOLD` 로그만 보인다.

### 2) ML 예측 서비스 (FastAPI, 포트 8000) — `ML` 전략 쓸 때만

```bash
cd ml
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

python -m training.train                       # 모델 학습 → artifacts/model.joblib
uvicorn app.main:app --host 0.0.0.0 --port 8000 # 예측 서비스 기동
pytest                                          # 테스트
```
> 백엔드는 `ML_BASE_URL`(기본 `http://localhost:8000`)로 호출한다. ML 서비스가 꺼져 있어도 백엔드는
> 죽지 않고 해당 전략을 HOLD 로 안전 처리한다.

### 3) 프론트 (Flutter, 모니터링·제어 앱)

```bash
cd front
flutter pub get
dart run build_runner build --delete-conflicting-outputs   # freezed/json 코드 생성(최초·모델 변경 시)

# 실제 기기/시뮬레이터
flutter run --dart-define=API_BASE_URL=http://localhost:8080/api/v1
# 웹(Chrome)
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080/api/v1
#   안드로이드 에뮬레이터에서 호스트 localhost 는 10.0.2.2

flutter test               # 테스트
```

### 한눈에 보기

| 모듈 | 디렉토리 | 기동 명령 | 포트 | 테스트 |
|---|---|---|---|---|
| 백엔드 | `back/` | `./gradlew bootRun` | 8080 | `./gradlew test` |
| ML | `ml/` | `uvicorn app.main:app --port 8000` | 8000 | `pytest` |
| 프론트 | `front/` | `flutter run --dart-define=API_BASE_URL=…` | — | `flutter test` |

---

## 개발 현황

- [x] 프로젝트 설계 (아키텍처·도메인 모델)
- [x] Claude Code 작업 규칙 (`CLAUDE.md` × 3)
- [x] DB 스키마 명세 (`back/docs/DB_SCHEMA.md`)
- [x] JPA 엔티티 + enum 구현
- [x] DB 연결 검증 (Supabase)
- [x] Repository 계층 + 테이블 생성
- [x] 토스 연동 (인증 → 현재가 → 캔들 수집) — 라이브 검증 통과
- [x] 전략 엔진 (TradingStrategy + 골든크로스/RSI, 지표 SMA·RSI, RiskManager 경유)
- [x] 자동매매 루프 스케줄러 (`TradingScheduler` `@Scheduled` — 활성 전략 캔들 수집 → 평가 → 신호→리스크→주문) — 기본 꺼짐(`trading.enabled`), 장시간 바이패스 토글·데모 시더 제공, 6개 테스트
- [x] 리스크 관리 + 주문 실행기 (`PaperOrderExecutor`) — 가상 체결, RiskManager 경유 (단위 검증)
- [x] Flutter 모니터링·제어 앱 (MVVM·Riverpod·freezed) — 로그인·대시보드·포지션·주문/체결·신호·전략·리스크(kill switch)·백테스트, JWT 인터셉터, 계약(`API_CONTRACT.md`) 기준 연동 (`front/`)
- [x] 백테스트 엔진 (look-ahead 차단, 수수료·세금, MDD·승률·CAGR) — 회귀 테스트 포함
- [x] 조회·제어 REST API (JWT 인증, DTO 변환, kill switch confirm, 예외 일괄 처리) — `back/docs/API_CONTRACT.md`
- [x] ML 상승예측 서비스 + `MlStrategy` (Python FastAPI `/predict`, baseline P(up) 모델, look-ahead 차단·시간분할·재현성) — 백엔드가 같은 `TradingStrategy` 인터페이스로 끼우고 **RiskManager 그대로 경유**. 예측은 제안일 뿐, 실패는 HOLD 안전 처리 (`ml/`, ML 15개·MlStrategy 7개 테스트 통과)
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