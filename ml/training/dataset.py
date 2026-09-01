"""학습 데이터 소스. 실데이터(CSV) 또는 합성 시계열.

백엔드 DB의 캔들을 CSV(`close` 컬럼)로 내보내 학습에 쓸 수 있다. 실데이터가 없을 때를 위해
**모멘텀 성분이 있는 합성 시계열**을 제공한다(파이프라인 end-to-end 검증용).
"""
from __future__ import annotations

import csv
from pathlib import Path
from typing import List, Sequence, Tuple

import numpy as np


def synthesize_series(n: int = 2000, seed: int = 42, start: float = 10000.0) -> np.ndarray:
    """모멘텀(자기상관) 성분이 있는 가격 시계열. 결정론적(seed 고정).

    수익률에 지속성 성분을 넣어 momentum 계열 피처가 미래 방향을 어느 정도 예측하도록 한다.
    (합성이므로 '학습이 되는지'를 확인하는 용도. 실전 성능과 무관.)
    """
    rng = np.random.default_rng(seed)
    mom = 0.0
    rets: List[float] = []
    for _ in range(n):
        shock = float(rng.normal(0.0, 0.01))
        mom = 0.9 * mom + 0.1 * shock
        rets.append(0.6 * mom + 0.5 * shock)
    prices = start * np.exp(np.cumsum(rets))
    return prices.astype(float)


def load_series_from_csv(path: str, column: str = "close") -> np.ndarray:
    """CSV에서 종가 시계열 로드(오래된→최신 순서 가정)."""
    rows: List[float] = []
    with Path(path).open(newline="") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames is None or column not in reader.fieldnames:
            raise ValueError(f"CSV에 '{column}' 컬럼이 없습니다: {reader.fieldnames}")
        for row in reader:
            rows.append(float(row[column]))
    return np.asarray(rows, dtype=float)


def chronological_split(
    x: np.ndarray,
    y: np.ndarray,
    times: Sequence[int],
    test_ratio: float = 0.3,
) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    """**시간 순서**로 train/test 분할(무작위 분할 금지, look-ahead 방지).

    times 는 오름차순(build_labeled_dataset가 t 오름차순으로 생성)이라고 가정한다.
    """
    n = len(y)
    if n == 0:
        raise ValueError("빈 데이터셋")
    boundary = int(n * (1.0 - test_ratio))
    boundary = max(1, min(boundary, n - 1))
    return x[:boundary], x[boundary:], y[:boundary], y[boundary:]
