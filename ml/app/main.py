"""FastAPI 추론 서비스 진입점. 계약: ml/docs/ML_API_CONTRACT.md."""
from __future__ import annotations

import logging
import time
from typing import List

from fastapi import FastAPI, HTTPException

from .inference import Predictor
from .schema import (
    HealthResponse,
    ModelInfoResponse,
    PredictRequest,
    PredictResponse,
)

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("ml.app")

app = FastAPI(title="AutoTrading ML 예측 서비스", version="0.1.0")
predictor = Predictor()


def _to_floats(closes: List[str]) -> List[float]:
    """decimal 문자열 종가 → float(피처 계산용. 회계 계산 아님)."""
    return [float(c) for c in closes]


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest) -> PredictResponse:
    started = time.perf_counter()
    try:
        closes = _to_floats(req.closes)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=f"invalid closes: {exc}")

    result = predictor.predict(closes)
    elapsed_ms = (time.perf_counter() - started) * 1000
    log.info(
        "predict symbol=%s n=%d signal=%s score=%.4f degraded=%s ver=%s %.1fms",
        req.symbol, len(closes), result["signal"], result["score"],
        result["degraded"], result["modelVersion"], elapsed_ms,
    )
    return PredictResponse(symbol=req.symbol, **result)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        modelLoaded=predictor.model_loaded,
        modelVersion=predictor.model_version,
    )


@app.get("/model-info", response_model=ModelInfoResponse)
def model_info() -> ModelInfoResponse:
    meta = predictor.metadata()
    if meta is None:
        raise HTTPException(status_code=404, detail="model not loaded")
    return ModelInfoResponse(
        modelVersion=str(meta.get("model_version", "")),
        trainedAt=str(meta.get("trained_at", "")),
        featureNames=list(meta.get("feature_names", [])),
        horizon=int(meta.get("horizon", 0)),
        labelThreshold=float(meta.get("label_threshold", 0.0)),
        metrics={k: float(v) for k, v in meta.get("metrics", {}).items()},
    )
