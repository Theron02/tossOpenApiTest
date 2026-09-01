"""모델 로드 + 예측. 예측 실패(모델 미로드·히스토리 부족)는 예외가 아니라 degraded 로 안전 반환."""
from __future__ import annotations

from typing import Any, Dict, List, Optional, Sequence

import joblib

from mlcore.features import InsufficientHistory, feature_vector

from . import config

_UNINITIALIZED = "uninitialized"


class Predictor:
    """학습 아티팩트(model.joblib)를 로드해 P(up) 을 예측한다.

    아티팩트 번들: {"model", "feature_names", "metadata"}.
    """

    def __init__(self) -> None:
        self._bundle: Optional[Dict[str, Any]] = None
        self.load()

    def load(self) -> bool:
        """아티팩트 로드 시도. 성공 True. 없으면 False(서비스는 계속 동작)."""
        path = config.MODEL_PATH
        if not path.exists():
            self._bundle = None
            return False
        self._bundle = joblib.load(path)
        return True

    @property
    def model_loaded(self) -> bool:
        return self._bundle is not None

    @property
    def model_version(self) -> str:
        if not self._bundle:
            return _UNINITIALIZED
        return str(self._bundle["metadata"].get("model_version", _UNINITIALIZED))

    def metadata(self) -> Optional[Dict[str, Any]]:
        return None if not self._bundle else self._bundle["metadata"]

    def predict(self, closes: Sequence[float]) -> Dict[str, Any]:
        """P(up) 예측. 결정론적. 실패 시 degraded(HOLD/0.5)."""
        if not self._bundle:
            return self._degraded()
        try:
            vec: List[float] = feature_vector(closes)
        except InsufficientHistory:
            return self._degraded()

        model = self._bundle["model"]
        score = float(model.predict_proba([vec])[0][1])  # P(class=1=up)
        signal = self._signal_for(score)
        return {
            "signal": signal,
            "score": score,
            "modelVersion": self.model_version,
            "featuresUsed": len(vec),
            "degraded": False,
        }

    @staticmethod
    def _signal_for(score: float) -> str:
        if score >= config.DEFAULT_BUY_THRESHOLD:
            return "BUY"
        if score <= config.DEFAULT_SELL_THRESHOLD:
            return "SELL"
        return "HOLD"

    def _degraded(self) -> Dict[str, Any]:
        return {
            "signal": "HOLD",
            "score": 0.5,
            "modelVersion": self.model_version,
            "featuresUsed": 0,
            "degraded": True,
        }
