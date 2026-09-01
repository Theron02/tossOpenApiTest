# ML_API_CONTRACT.md — 백엔드 ↔ ML 예측 서비스 계약

백엔드 `MlStrategy`(Kotlin)가 이 계약대로 Python ML 서비스를 호출한다.
스키마를 바꾸면 이 문서 · Python `app/schema.py` · 백엔드 `external/ml/*` 를 함께 갱신한다.

- Base URL: 백엔드 설정 `ml.base-url` (기본 `http://localhost:8000`)
- 인증: 없음(내부 서비스). 외부 노출 금지 — 백엔드만 호출한다.
- 가격은 **문자열(decimal)** 로 전달(정밀도 보존). ML은 피처 계산 시 float 로 변환(회계 계산 아님).
- **경계**: 이 서비스는 예측(제안)만 한다. 주문 판단·리스크·실행은 백엔드가. ML은 RiskManager 를 우회하지 않는다.

---

## `POST /predict`

한 종목·한 시점의 "오를 것 같은지"를 예측한다. 결정론적(같은 입력 → 같은 출력).

요청:
```json
{
  "symbol": "005930",
  "closes": ["52000", "52500", "53000", "..."],
  "holdingQuantity": 10
}
```
| 필드 | 필수 | 설명 |
|---|---|---|
| `symbol` | ✔ | 종목코드 |
| `closes` | ✔ | 종가 시계열, **오래된→최신** 순, decimal 문자열. 최신값이 현재가 근사 |
| `holdingQuantity` | | 현재 보유 수량(참고용, 예측엔 미사용 가능) |

응답 `200`:
```json
{
  "symbol": "005930",
  "signal": "BUY",
  "score": 0.73,
  "modelVersion": "baseline-logreg-20260901T0000Z",
  "featuresUsed": 9,
  "degraded": false
}
```
| 필드 | 설명 |
|---|---|
| `signal` | `BUY`/`SELL`/`HOLD`. 서비스 기본 임계값으로 산출한 편의값 |
| `score` | **상승 확률 P(up) 0.0~1.0**. 백엔드가 최종 신호 판단에 쓰는 주값 |
| `modelVersion` | 모델 버전. 미로드/열화 시 `"uninitialized"` |
| `featuresUsed` | 사용한 피처 개수 |
| `degraded` | 모델 미로드·히스토리 부족으로 안전 기본값(HOLD/0.5)을 반환했는지 |

### 열화(degraded) 처리 — 안전
- 모델 미로드 또는 `closes` 길이 부족 → `200`, `signal:"HOLD"`, `score:0.5`, `degraded:true`.
- 즉, **예측 실패를 예외가 아니라 HOLD 로 안전 반환**한다. 백엔드는 호출 실패(네트워크 등)도 HOLD 로 처리한다.

### 신호 결정 권한
- 서비스는 `score`(P(up))를 계산하는 게 본질이다.
- **최종 BUY/SELL/HOLD 는 백엔드 `MlStrategy` 가 `score` + 임계값 파라미터로 결정**한다
  (`buyThreshold` 기본 0.6, `sellThreshold` 기본 0.4). 리스크 판단이 있는 백엔드에 결정권을 둔다.
- 응답의 `signal` 은 서비스 기본 임계값(0.6/0.4) 기준 편의값이며 참고용이다.

---

## `GET /health`
```json
{ "status": "ok", "modelLoaded": true, "modelVersion": "baseline-logreg-..." }
```
`modelLoaded=false` 여도 `status:"ok"`(서비스는 살아있음). 예측은 degraded 로 응답.

## `GET /model-info`
```json
{
  "modelVersion": "baseline-logreg-20260901T0000Z",
  "trainedAt": "2026-09-01T00:00:00Z",
  "featureNames": ["ret_1", "ret_5", "..."],
  "horizon": 5,
  "labelThreshold": 0.0,
  "metrics": { "testAccuracy": 0.55, "testAuc": 0.58, "nTrain": 700, "nTest": 300 }
}
```
모델 미로드 시 `404`.

---

## 에러

| HTTP | 상황 |
|---|---|
| `422` | 요청 스키마 검증 실패(pydantic) — `closes` 누락/형식 오류 등 |
| `404` | `/model-info` 인데 모델 미로드 |
| `500` | 예기치 못한 오류 |

> 예측 자체의 "실패"(히스토리 부족·모델 미로드)는 에러가 아니라 `degraded` 응답으로 처리한다.
