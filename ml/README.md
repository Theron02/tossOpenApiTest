# ml — 상승예측 ML 서비스 (Python)

시세(종가 시계열)를 받아 **"오를 것 같은지"(P(up))** 를 예측해 반환한다.
백엔드 `MlStrategy` 가 `POST /predict` 로 호출하며, 예측은 **제안일 뿐** — 최종 주문 판단·리스크·실행은
Kotlin 백엔드가 하고 `RiskManager` 를 그대로 통과한다(ML이라고 우회 없음).

계약: [docs/ML_API_CONTRACT.md](docs/ML_API_CONTRACT.md)

## 구조
```
ml/
├── mlcore/        학습·추론 공유 (train-serving skew 방지)
│   ├── features.py   지표 피처 (look-ahead 없음)
│   └── labeling.py   라벨 생성 (미래 수익 — 학습 전용)
├── app/           추론 서비스 (FastAPI)
│   ├── main.py       /predict /health /model-info
│   ├── inference.py  모델 로드 + 예측 (실패는 degraded HOLD 로 안전 반환)
│   ├── schema.py     pydantic 요청·응답
│   └── config.py     경로·임계값 (환경변수)
├── training/      학습 파이프라인 (오프라인)
│   ├── dataset.py    실데이터(CSV)/합성 시계열 + 시간분할
│   └── train.py      baseline 학습 진입점
├── artifacts/     학습된 모델 (gitignore, 버전 태깅)
└── tests/
```

## 설치·실행
```bash
cd ml
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# 1) 모델 학습 (실데이터 없으면 합성 데이터로 파이프라인 검증)
python -m training.train                    # -> artifacts/model.joblib
python -m training.train --csv closes.csv   # close 컬럼 CSV 로 학습

# 2) 추론 서비스 기동 (기본 8000 포트)
uvicorn app.main:app --host 0.0.0.0 --port 8000

# 3) 테스트
pytest
```

호출 예:
```bash
curl -s localhost:8000/predict -H 'content-type: application/json' \
  -d '{"symbol":"005930","closes":["52000","52500","53000", "..."]}'
# {"symbol":"005930","signal":"HOLD","score":0.57,"modelVersion":"baseline-logreg-...","featuresUsed":9,"degraded":false}
```

## 모델
- baseline: `StandardScaler + LogisticRegression`. 피처: 수익률·SMA 비율·RSI·변동성·모멘텀(9개).
- 라벨: `horizon`(기본 5봉) 뒤 수익률 > `threshold`(기본 0) 이면 상승(1).
- **look-ahead 차단**: 피처는 시점 T까지만. **무작위 분할 금지** — 시간 순 train/test 분할.
- 재현성: 시드 고정. `model-info` 에 버전·학습시각·피처·지표 노출.

> ⚠️ 분류 정확도가 높다고 돈을 버는 게 아니다. 최종 판단은 백엔드 **백테스트 엔진에
> `MlStrategy` 로 넣어 수익률·MDD·수수료 반영** 결과로 한다(ml/CLAUDE.md §5). 합성 데이터 성능은
> 파이프라인 동작 확인용일 뿐 실전과 무관하다.

## 안전
- 예측 실패(모델 미로드·히스토리 부족)는 예외가 아니라 `degraded`(HOLD/0.5)로 반환 → 백엔드가 안전하게 HOLD.
- 이 서비스는 토스 API·DB·주문에 직접 접근하지 않는다. 데이터는 백엔드가 요청 본문으로 전달.
- 실거래 전환은 백엔드 승인 절차 이후. ML 서비스가 직접 주문하지 않는다.
