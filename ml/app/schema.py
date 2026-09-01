"""요청·응답 스키마(pydantic v2). 필드명은 ML_API_CONTRACT.md 와 일치(camelCase)."""
from __future__ import annotations

from typing import Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field


class PredictRequest(BaseModel):
    # model_ 보호 네임스페이스 경고 방지(modelVersion 등과 무관하나 일관 적용).
    model_config = ConfigDict(protected_namespaces=())

    symbol: str
    closes: List[str] = Field(..., min_length=1, description="오래된→최신 종가(decimal 문자열)")
    holdingQuantity: Optional[int] = None


class PredictResponse(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    symbol: str
    signal: str  # BUY / SELL / HOLD (편의값)
    score: float  # P(up) 0..1 — 백엔드가 최종 판단에 쓰는 주값
    modelVersion: str
    featuresUsed: int
    degraded: bool = False


class HealthResponse(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    status: str
    modelLoaded: bool
    modelVersion: str


class ModelInfoResponse(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    modelVersion: str
    trainedAt: str
    featureNames: List[str]
    horizon: int
    labelThreshold: float
    metrics: Dict[str, float]
