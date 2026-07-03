# API_CONTRACT.md — 백엔드 REST 계약 (앱 연동 기준)

이 문서는 Flutter 앱이 소비할 백엔드 REST API 계약이다. TASK_06 산출물이며,
이후 프론트 작업은 이 계약을 화면에 붙인다. 구현이 바뀌면 이 문서를 먼저 갱신한다.

- Base URL: `/api/v1`
- 인증: 앱↔백엔드는 **JWT** (`Authorization: Bearer <token>`). 토스 OAuth와 무관.
- 시각: 저장·전송은 UTC(`Instant`, ISO-8601). 표시는 앱에서 KST 변환.
- 금액·수량: JS number 정밀도 손실 방지를 위해 **문자열(원/decimal)** 로 직렬화.
- 실거래 주문 실행 API는 노출하지 않는다. 제어는 전략/리스크 토글까지만.

---

## 1. 공통 응답 래퍼

모든 응답은 아래 래퍼로 감싼다. 성공이면 `data`, 실패면 `error`만 채워진다.

```jsonc
// 성공
{ "data": { /* ... */ }, "error": null, "timestamp": "2026-07-04T05:00:00Z" }

// 실패
{ "data": null, "error": { "code": "not-found", "message": "..." }, "timestamp": "..." }
```

### 에러 코드 → HTTP 상태

| code | HTTP | 발생 상황 |
|---|---|---|
| `unauthorized` | 401 | 토큰 없음/유효하지 않음 (보호 자원 접근) |
| `invalid-credentials` | 401 | 로그인 자격증명 불일치 |
| `forbidden` | 403 | 권한 부족 |
| `not-found` | 404 | 리소스 없음 |
| `conflict` | 409 | 상태 충돌 |
| `validation-failed` | 400 | 요청 본문 검증 실패 |
| `bad-request` | 400 | 파싱 불가·잘못된 파라미터(enum 등) |
| `risk-rejected` | 422 | 리스크 가드에 의해 거부 |
| `internal-error` | 500 | 처리되지 않은 서버 오류 |

---

## 2. 인증

### `POST /api/v1/auth/login` (공개)

단일 운영자 자격증명(env)과 대조 후 JWT 발급.

요청:
```json
{ "username": "operator", "password": "••••••" }
```
응답 `data`:
```json
{ "token": "<jwt>", "tokenType": "Bearer", "expiresInSeconds": 7200 }
```

이후 모든 보호 자원 호출에 `Authorization: Bearer <token>` 헤더 필요.
`/api/v1/auth/**` 외 모든 경로는 인증 필수(미인증 → 401 `unauthorized`).

---

## 3. 조회 API (GET)

### `GET /api/v1/portfolio`
포트폴리오·손익 요약. 현재가는 토스 시세 캐시(Redis) 사용, 실패 시 평단가 폴백.

`data`:
```json
{
  "accountId": "…", "name": "모의계좌",
  "cash": "9500000", "positionsValue": "520000", "totalEquity": "10020000",
  "evalPnl": "20000", "returnRate": "0.2", "initialSeed": "10000000",
  "pricedAtMarket": true
}
```
`pricedAtMarket=false`면 현재가 미연동/실패로 평단가 기준 평가된 값이다.

### `GET /api/v1/positions`
보유 종목 목록. 배열.

`data[]`:
```json
{
  "stockCode": "005930", "quantity": 10, "avgPrice": "52000",
  "currentPrice": "55000", "evalAmount": "550000",
  "evalPnl": "30000", "pnlRate": "5.77"
}
```
`currentPrice`는 시세 미연동 시 `null`.

### `GET /api/v1/orders?status=&page=&size=`
주문 내역(최신순, 페이지네이션).

| 쿼리 | 기본값 | 설명 |
|---|---|---|
| `status` | (없음) | `PENDING`/`FILLED`/`PARTIAL`/`CANCELLED`/`REJECTED`. 잘못된 값 → 400 |
| `page` | 0 | 0-base |
| `size` | 20 | 페이지 크기 |

`data[]`:
```json
{
  "id": "…", "stockCode": "005930", "side": "BUY", "orderType": "MARKET",
  "quantity": 10, "price": "52000", "status": "FILLED",
  "filledQuantity": 10, "createdAt": "2026-07-04T05:00:00Z"
}
```

### `GET /api/v1/executions?limit=`
체결 내역(최신순). `limit` 기본 50.

`data[]`:
```json
{
  "id": "…", "orderId": "…", "filledQty": 10, "filledPrice": "52000",
  "fee": "78", "executedAt": "2026-07-04T05:00:00Z"
}
```

### `GET /api/v1/signals?page=&size=`
신호 로그(추적성 핵심, 최신순). `page` 0, `size` 20 기본.

`data[]`:
```json
{
  "id": "…", "strategyName": "golden-cross", "stockCode": "005930",
  "signal": "BUY", "indicatorSnapshot": { "ma5": "…", "ma20": "…" },
  "createdAt": "2026-07-04T05:00:00Z"
}
```

### `GET /api/v1/strategies`
전략 설정 목록. 배열.

`data[]`:
```json
{
  "id": "…", "strategyName": "golden-cross", "stockCode": "005930",
  "params": { "shortPeriod": 5, "longPeriod": 20 }, "enabled": false
}
```

### `GET /api/v1/risk-setting`
리스크 설정 현황(단건).

`data`:
```json
{
  "id": "…", "accountId": "…", "dailyLossLimit": "500000",
  "maxPositionPct": 20, "killSwitch": false
}
```

---

## 4. 제어 API (POST/PATCH) — 신중

### `PATCH /api/v1/strategies/{id}`
전략 on/off·파라미터 변경. `null` 필드는 미변경.
**`enabled=true`는 봇이 이 전략으로 자동 주문을 시작한다는 의미.**

요청:
```json
{ "enabled": true, "params": { "shortPeriod": 5, "longPeriod": 20 } }
```
응답 `data`: 변경된 `StrategyResponse` (위 조회와 동일 스키마).
없는 `id` → 404 `not-found`.

### `PATCH /api/v1/risk-setting`
일일한도·종목당 비중 변경. `null`은 미변경. 범위 검증.

요청:
```json
{ "dailyLossLimit": 500000, "maxPositionPct": 20 }
```
- `dailyLossLimit` ≥ 0 (원, 정수)
- `maxPositionPct` 1–100
범위 위반 → 400 `validation-failed`.
응답 `data`: 변경된 `RiskSettingResponse`.

### `POST /api/v1/risk-setting/kill-switch`  ⚠ 위험 동작
kill switch on/off. **`confirm=true`가 아니면 거부**(오작동 방지). 감사 로그 기록.
`enabled=true`면 모든 신규 주문이 즉시 차단된다.

요청:
```json
{ "enabled": true, "confirm": true }
```
`confirm`이 없거나 `false` → 거부(400 계열). 응답 `data`: 변경된 `RiskSettingResponse`.

---

## 5. 백테스트

> 영속 `backtest_run` 테이블이 없어, TASK_06 명세의 `GET /backtests/{id}` 대신
> **즉시 실행(POST)** 으로 결과를 반환한다. 백테스트는 실주문과 완전히 격리된 경로다(토스 주문 없음).

### `POST /api/v1/backtests`
요청:
```json
{
  "symbol": "005930", "interval": "DAY", "strategy": "golden-cross",
  "params": { "shortPeriod": 5, "longPeriod": 20 },
  "initialCapital": "10000000",
  "commissionRate": "0.00015", "taxRate": "0.0023",
  "slippageRate": "0.0", "positionSizePct": 100
}
```
필수: `symbol`, `interval`, `strategy`, `initialCapital`. 나머지는 선택(미지정 시 엔진 기본값).
`interval` 잘못된 값 → 400.

응답 `data`:
```json
{
  "initialCapital": "10000000", "finalEquity": "10800000",
  "totalReturn": "0.08", "cagr": "0.12", "maxDrawdown": "0.15",
  "winRate": "0.6", "profitFactor": "1.8",
  "totalTrades": 20, "closedTrades": 18,
  "totalCommission": "3000", "totalTax": "24000",
  "equityCurve": [ { "time": "2026-01-02T00:00:00Z", "equity": "10000000" } ],
  "warnings": []
}
```

---

## 6. 노출하지 않는 것

- 실거래 주문 실행(`TossOrderExecutor`) — 별도 승인 절차 이후에만.
- 토스 client_id/client_secret, 토스 액세스 토큰 — 앱 응답에 절대 포함하지 않음.
- JPA 엔티티 원형 — 모든 응답은 전용 DTO로 변환.