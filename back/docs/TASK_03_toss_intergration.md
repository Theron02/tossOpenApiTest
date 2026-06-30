# TASK 03: 토스증권 Open API 연동 (인증 → 현재가 → 주문)

이 문서는 Claude Code에게 넘기는 세 번째 작업 지시서다. 시작 전 아래를 읽는다.
- 루트 `CLAUDE.md`, `back/CLAUDE.md`
- 선행: TASK_01(엔티티), TASK_02(Repository) 완료

기준 스펙: **토스증권 Open API v1.1.5** (OpenAPI 3.1.0). BASE URL `https://openapi.tossinvest.com`.

---

## 0. 가장 중요한 사실 (설계 영향)

1. **모의투자(sandbox) API가 없다.** 토스 Open API는 실거래 단일 환경이다.
   → "모의투자 검증" 은 토스가 아니라 **우리 백엔드의 `PaperOrderExecutor`** 로 구현한다.
   `PaperOrderExecutor`는 토스 시세(`/prices` 등)는 실제로 받되, 주문은 토스로 보내지 않고
   현재가 기준으로 가상 체결해 DB에만 기록한다. 실제 `POST /orders` 는 `TossOrderExecutor`
   에서만 호출하며, 이 구현·활성화는 **사용자의 명시적 승인 전까지 작성하지 않는다.**
2. **모든 가격·수량이 문자열(decimal)** 이다. 예: `"72000"`, `"185.70"`, `"0.5"`.
    - 국내(KRW): 정수 → `Long`
    - 미국(USD): 소수점 → `BigDecimal`
    - JSON 파싱은 String 으로 받고 도메인 변환 시 `BigDecimal` 로 파싱한다. `Double` 금지.
3. **WebSocket 미지원** ("추후 지원 예정"). 실시간 시세는 **폴링**으로 구현한다.
   `GET /api/v1/prices` 가 최대 200종목 다건 조회를 지원하므로 폴링 단위로 활용한다.
4. **멱등성은 `clientOrderId`** 로 지원된다. 우리 `idempotencyKey` ↔ `clientOrderId` 매핑.
   **유효기간 10분.** 이후 동일 값은 새 주문으로 처리된다.
5. **인증은 OAuth 2.0 Client Credentials.** 사용자 로그인 없이 서버 간 토큰 발급.

---

## 1. 인증 (Auth)

### 토큰 발급
```
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
client_id={CLIENT_ID}
client_secret={CLIENT_SECRET}
```
- 응답(OAuth2 표준, 공통 envelope 아님):
  `{ "access_token": "...", "token_type": "Bearer", "expires_in": 86400 }`
- **client당 유효 토큰 1개.** 재발급 시 이전 토큰 즉시 무효화 → 동시 재발급 경쟁 주의.
- refresh token 없음. 만료 시 동일 엔드포인트로 재발급.
- `client_id`/`client_secret`은 **환경변수로만**. 코드·VCS 금지.

### 토큰 관리 규칙
- 발급한 토큰을 **Redis에 캐싱**하고 `expires_in` 기준으로 만료 전 갱신.
- 만료 임박(예: 잔여 60초) 시 사전 재발급. 매 요청마다 발급 금지.
- "유효 토큰 1개" 특성 때문에 재발급은 **락(분산락 등)으로 단일화**해 중복 발급 방지.
- 모든 인증 필요 호출에 `Authorization: Bearer {access_token}` 헤더.

### 인증 에러
- 토큰 발급 실패: `OAuth2ErrorResponse` (`error` 필드로 식별. `invalid_client` 등).
- API 호출 인증 실패: 401 + `WWW-Authenticate` 헤더, 본문은 공통 `ErrorResponse`
  (`expired-token`, `invalid-token`). expired-token이면 재발급 후 1회 재시도.

---

## 2. 공통 응답 구조

- **성공**: `{ "result": ... }` envelope (단, `/oauth2/token`만 예외 — OAuth2 표준).
- **실패**: `{ "error": { "requestId", "code", "message", "data"? } }` (4xx/5xx).
- `code`는 flat string (`invalid-request`, `order-not-found` 등).
  **클라이언트는 unknown code를 허용하도록 구현** (스펙 명시). enum 매핑 시 fallback 필수.
- 응답 헤더 `X-Request-Id` = 본문 `error.requestId`. 로그에 남겨 CS 추적에 사용.

### 공통 헤더
- 인증 필요 모든 API: `Authorization: Bearer {token}`
- **계좌 컨텍스트 API** (Asset, Order, Order History, Order Info): 추가로
  `X-Tossinvest-Account: {accountSeq}` (int64). `GET /api/v1/accounts`에서 획득.

---

## 3. 이번 작업 범위 (3단계로 검증)

### 단계 1 — 인증 + 현재가 조회 (외부 연동 첫 관문)
- `external/toss/` 패키지 생성. `TossAuthClient`, `TossMarketDataClient`.
- `POST /oauth2/token`으로 토큰 발급 → Redis 캐싱 → `TossTokenProvider` 추상화.
- `GET /api/v1/prices?symbols=005930` 호출해 현재가 파싱 성공 확인.
    - 응답: `result[].{symbol, timestamp(nullable), lastPrice(string), currency}`
    - 검증 테스트: 삼성전자(005930) 현재가를 받아 BigDecimal로 파싱.

### 단계 2 — 계좌 조회 + 시세 보강
- `GET /api/v1/accounts` → `accountSeq` 획득 (`TossAccountClient`).
    - 응답: `result[].{accountNo, accountSeq(int64), accountType}` (현재 `BROKERAGE`만).
    - `accountSeq`를 이후 계좌 컨텍스트 호출의 `X-Tossinvest-Account`로 사용.
- `GET /api/v1/candles?symbol=005930&interval=1d&count=100` → 캔들 수집.
    - 응답: `result.candles[].{timestamp, openPrice, highPrice, lowPrice, closePrice, volume, currency}` + `nextBefore`(페이지네이션).
    - `interval`은 `1m` / `1d`만. 우리 `CandleInterval` enum을 이 둘에 매핑(또는 확장).
    - 수집한 캔들을 `candle` 테이블에 저장 (지표 계산용).

### 단계 3 — 주문 (PaperOrderExecutor 우선)
- `OrderExecutor` 인터페이스 구현:
    - **`PaperOrderExecutor` (기본, 이번 작업 대상)**: 토스로 주문 전송하지 않음.
      `GET /api/v1/prices` 현재가로 가상 체결 → `trade_order`/`execution`/`position` 기록.
      매수가능금액·수량 검증은 자체 잔고(`PaperAccount`)로.
    - **`TossOrderExecutor` (실거래, 작성 보류)**: `POST /api/v1/orders` 실제 호출.
      **사용자 명시적 승인 전까지 구현하지 않는다.** 인터페이스 시그니처와 TODO 주석만 남긴다.
- 주문 생성 스펙(참고, TossOrderExecutor용):
  ```
  POST /api/v1/orders
  Header: X-Tossinvest-Account: {accountSeq}
  Body(quantity 기반): { clientOrderId, symbol, side(BUY/SELL),
                        orderType(LIMIT/MARKET), quantity(string), price(string, LIMIT만),
                        timeInForce(DAY/CLS, 기본 DAY), confirmHighValueOrder }
  응답: { result: { orderId, clientOrderId } }
  ```
    - `clientOrderId` = 멱등성 키 (≤36자, `[a-zA-Z0-9\-_]`, 10분 유효).
    - 가격/수량 string. KR 정수, US 소수점. 호가단위(tickSize) 위반 시 400.

---

## 4. enum 매핑 (토스 스펙 반영 — 기존 enum 확장 필요)

기존 `OrderStatus`는 토스보다 단순하다. 토스 스펙에 맞춰 **확장**한다 (우리 내부 상태머신과
토스 상태를 매핑하는 변환 계층을 둔다).

토스 `OrderStatus`:
`PENDING, PENDING_CANCEL, PENDING_REPLACE, PARTIAL_FILLED, FILLED, CANCELED, REJECTED, CANCEL_REJECTED, REPLACE_REJECTED, REPLACED`

- 주문 목록 조회의 `status` 파라미터는 그룹 라벨 `OPEN` / `CLOSED` (개별 status와 체계 다름).
- `OrderSide`: `BUY` / `SELL` (동일).
- `OrderType`: `LIMIT` / `MARKET` (동일).
- 신규: `TimeInForce` (`DAY` / `CLS`), `Currency`(`KRW`/`USD`), `MarketCountry`(`KR`/`US`).
- `Market` enum 확장: 토스는 `KOSPI, KOSDAQ, NYSE, NASDAQ, AMEX, KR_ETC, US_ETC`.
- **unknown enum 허용**: 모든 enum 역직렬화에 fallback(UNKNOWN) 처리. 스펙이 요구.

---

## 5. Rate Limit 대응

- 429 응답에 `X-RateLimit-Limit/Remaining/Reset`, `Retry-After` 헤더.
- Rate Limit Group 별로 관리됨 (`AUTH`, `MARKET_DATA`, `MARKET_DATA_CHART`, `ORDER` 등).
- 폴링 주기는 보수적으로. 429 시 `Retry-After` 따라 백오프 후 재시도.
- 다건 조회(`/prices` symbols 최대 200, `/candles` count 최대 200)로 호출 수를 줄인다.

---

## 6. 통화·금액 처리 (필수 규칙)

- JSON의 가격/수량 string → **`BigDecimal`로 파싱**. `Double`/`Float` 절대 금지.
- 국내(KRW) 금액은 정수 → 내부 저장 `Long` 유지 가능.
- 미국(USD)은 소수점 발생 → `BigDecimal` 필요. 종목 통화(`currency`)로 분기.
- 소수점 수량은 US 시장가 매도에만 허용(최대 6자리) — Paper 단계에선 KR 정수 위주로 시작.
- 손익률(`rate`)은 소수비율(0.1077 = 10.77%) — 그대로 보관, 표시 단계에서 %.

---

## 7. 반드시 지킬 것 (체크리스트)

- [ ] `client_id`/`client_secret` 환경변수로만. 코드·VCS에 없음
- [ ] 토큰 Redis 캐싱 + 만료 전 갱신 + 재발급 단일화(락). "유효 토큰 1개" 특성 반영
- [ ] 모든 가격/수량 String→BigDecimal 파싱. Double 미사용
- [ ] 계좌 컨텍스트 API에 `X-Tossinvest-Account` 헤더 누락 없음
- [ ] enum 역직렬화에 unknown fallback 처리
- [ ] 멱등성 키(clientOrderId) ≤36자/패턴/10분 유효 반영
- [ ] **TossOrderExecutor(실거래) 미구현** — 인터페이스+TODO만. 승인 전 호출 코드 작성 금지
- [ ] PaperOrderExecutor가 RiskManager를 경유 (우회 금지)
- [ ] 429 백오프(Retry-After) 처리
- [ ] 외부 호출 타임아웃·재시도 적용, requestId 로깅

---

## 8. 산출물

```
back/src/main/kotlin/com/.../external/toss/
   ├── TossAuthClient.kt           POST /oauth2/token
   ├── TossTokenProvider.kt        토큰 캐싱·갱신(Redis)·재발급 단일화
   ├── TossMarketDataClient.kt     /prices, /candles, /orderbook
   ├── TossAccountClient.kt        /accounts
   ├── dto/                        요청·응답 DTO (스펙 기준, String 가격)
   └── TossApiException.kt         error code 매핑
back/src/main/kotlin/com/.../domain/order/
   ├── OrderExecutor.kt            인터페이스
   ├── PaperOrderExecutor.kt       기본 구현 (가상 체결)
   └── TossOrderExecutor.kt        실거래 — 인터페이스+TODO만 (미구현)
back/src/main/resources/application.yml   (toss.base-url, 환경변수 참조)
back/src/test/...                 인증·현재가·캔들 파싱 테스트
```

---

## 9. 작업 후 검증

1. 토큰 발급 → `/prices?symbols=005930` 현재가 파싱 성공 (단계 1 통과)
2. `/accounts`로 accountSeq 획득, `/candles`로 일봉 100개 수집·저장 (단계 2)
3. PaperOrderExecutor로 가상 매수→포지션/체결 기록, RiskManager 경유 확인 (단계 3)
4. 토큰 만료·재발급, 429 백오프 동작 확인
5. README 개발 현황 갱신 (WORKFLOW_readme_update 규칙)

---

## 10. 범위 밖 (다음 작업)

- 실거래 주문 실행(`TossOrderExecutor` 본체) — 별도 승인·검증 후
- 전략 엔진(TradingStrategy + 골든크로스/RSI)
- 폴링 스케줄러 고도화, 호가/체결 실시간 표시
- Flutter 모니터링 화면

> 이번 작업의 목표: **토스 인증이 서고, 시세·캔들이 들어오고, 모의(Paper) 주문으로
> 전체 매매 흐름이 도는 상태.** 실거래는 다음.