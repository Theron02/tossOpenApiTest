"""라벨 생성 — **학습에서만** 사용한다(미래 수익을 본다).

look-ahead 규칙(ml/CLAUDE.md §5):
- 피처는 시점 T까지만 본다(features.py).
- 라벨은 미래(T+horizon)를 본다 — 이는 학습 라벨 생성에서만 허용된다.
  추론 시에는 절대 미래를 쓰지 않는다.
"""
from __future__ import annotations

from typing import List, Optional, Sequence, Tuple

import numpy as np

from .features import MIN_HISTORY, feature_vector


def make_label(
    series: Sequence[float],
    t: int,
    horizon: int,
    threshold: float,
) -> Optional[int]:
    """t 시점 라벨: horizon 봉 뒤 수익률 > threshold 이면 1(상승) 아니면 0.

    t+horizon 이 범위를 벗어나면 None(라벨 불가).
    """
    if t + horizon >= len(series):
        return None
    future_ret = series[t + horizon] / series[t] - 1.0
    return 1 if future_ret > threshold else 0


def build_labeled_dataset(
    series: Sequence[float],
    horizon: int = 5,
    threshold: float = 0.0,
) -> Tuple[np.ndarray, np.ndarray, List[int]]:
    """(X, y, times) 학습 데이터.

    - X[i]: series[:t+1] 로 계산한 피처(look-ahead 없음)
    - y[i]: make_label(series, t, ...) (미래 사용 — 학습 전용)
    - times[i]: t (시간 분할에 사용)
    라벨이 정의되는 t(=t+horizon < len) 만 포함한다.
    """
    arr = np.asarray(series, dtype=float)
    xs: List[List[float]] = []
    ys: List[int] = []
    times: List[int] = []
    for t in range(MIN_HISTORY - 1, arr.size):
        label = make_label(arr, t, horizon, threshold)
        if label is None:
            continue
        xs.append(feature_vector(arr[: t + 1]))
        ys.append(label)
        times.append(t)
    if not xs:
        return np.empty((0,)), np.empty((0,)), []
    return np.asarray(xs, dtype=float), np.asarray(ys, dtype=int), times
