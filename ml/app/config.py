"""추론 서비스 설정. 경로·임계값은 환경변수로 주입(기본값 제공)."""
from __future__ import annotations

import os
from pathlib import Path

_ROOT = Path(__file__).resolve().parent.parent  # ml/

ARTIFACT_DIR = Path(os.environ.get("ML_ARTIFACT_DIR", str(_ROOT / "artifacts")))
MODEL_PATH = Path(os.environ.get("ML_MODEL_PATH", str(ARTIFACT_DIR / "model.joblib")))

# 서비스 편의 신호(응답 signal)용 기본 임계값. 최종 결정권은 백엔드 MlStrategy.
DEFAULT_BUY_THRESHOLD = float(os.environ.get("ML_BUY_THRESHOLD", "0.6"))
DEFAULT_SELL_THRESHOLD = float(os.environ.get("ML_SELL_THRESHOLD", "0.4"))
