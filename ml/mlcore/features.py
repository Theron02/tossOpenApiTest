"""피처 엔지니어링 — 학습(training)과 추론(app) 이 **동일하게 공유**한다.

원칙(ml/CLAUDE.md §5,§6):
- look-ahead 금지: 시점 T의 피처는 closes[..T] (T 포함, 그 이후 없음) 만으로 계산한다.
- 학습-서빙 skew 방지: 양쪽이 이 모듈의 같은 함수를 쓴다.
- 결정론적: 같은 입력 → 같은 출력.
"""
from __future__ import annotations

from typing import Dict, List, Sequence, Tuple

import numpy as np

# 피처 순서 고정(모델 입력 순서와 일치해야 함).
FEATURE_NAMES: List[str] = [
    "ret_1",
    "ret_5",
    "ret_10",
    "sma5_ratio",
    "sma20_ratio",
    "sma5_over_sma20",
    "rsi14",
    "vol_10",
    "mom_20",
]

# 모든 피처를 계산하려면 최소 이만큼의 종가가 필요하다(sma20·mom_20 기준).
MIN_HISTORY: int = 21


class InsufficientHistory(ValueError):
    """피처를 만들기에 종가 개수가 부족함."""


def _sma(values: np.ndarray, period: int) -> float:
    return float(values[-period:].mean())


def _rsi(values: np.ndarray, period: int = 14) -> float:
    """표준 RSI(0~100). gains/losses 의 단순평균 기반. 결정론적."""
    diffs = np.diff(values[-(period + 1):])
    gains = np.clip(diffs, 0.0, None)
    losses = np.clip(-diffs, 0.0, None)
    avg_gain = float(gains.mean())
    avg_loss = float(losses.mean())
    if avg_loss == 0.0:
        return 100.0 if avg_gain > 0.0 else 50.0
    rs = avg_gain / avg_loss
    return 100.0 - (100.0 / (1.0 + rs))


def compute_features(closes: Sequence[float]) -> Dict[str, float]:
    """closes(오래된→최신)의 **마지막 시점 T** 에 대한 피처 딕셔너리.

    closes[..T] 만 사용한다(look-ahead 없음). 부족하면 [InsufficientHistory].
    """
    arr = np.asarray(closes, dtype=float)
    if arr.size < MIN_HISTORY:
        raise InsufficientHistory(
            f"need >= {MIN_HISTORY} closes, got {arr.size}"
        )
    if np.any(arr <= 0):
        raise InsufficientHistory("closes must be positive")

    last = float(arr[-1])
    sma5 = _sma(arr, 5)
    sma20 = _sma(arr, 20)
    returns = np.diff(arr) / arr[:-1]

    feats: Dict[str, float] = {
        "ret_1": last / float(arr[-2]) - 1.0,
        "ret_5": last / float(arr[-6]) - 1.0,
        "ret_10": last / float(arr[-11]) - 1.0,
        "sma5_ratio": last / sma5 - 1.0,
        "sma20_ratio": last / sma20 - 1.0,
        "sma5_over_sma20": sma5 / sma20 - 1.0,
        "rsi14": _rsi(arr, 14) / 100.0,
        "vol_10": float(returns[-10:].std()),
        "mom_20": last / float(arr[-20:].mean()) - 1.0,
    }
    return feats


def feature_vector(closes: Sequence[float]) -> List[float]:
    """FEATURE_NAMES 순서의 피처 벡터."""
    feats = compute_features(closes)
    return [feats[name] for name in FEATURE_NAMES]


def build_matrix(series: Sequence[float]) -> Tuple[np.ndarray, List[int]]:
    """시계열 전체에서 각 시점 t의 피처 행렬을 만든다(학습용).

    각 t 의 피처는 series[:t+1] 로만 계산(look-ahead 없음).
    반환: (X[shape=(n, n_features)], times[유효 t 인덱스 목록]).
    """
    arr = np.asarray(series, dtype=float)
    rows: List[List[float]] = []
    times: List[int] = []
    for t in range(MIN_HISTORY - 1, arr.size):
        rows.append(feature_vector(arr[: t + 1]))
        times.append(t)
    if not rows:
        return np.empty((0, len(FEATURE_NAMES))), []
    return np.asarray(rows, dtype=float), times
