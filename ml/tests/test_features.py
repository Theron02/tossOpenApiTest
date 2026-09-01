import numpy as np
import pytest

from mlcore.features import (
    FEATURE_NAMES,
    MIN_HISTORY,
    InsufficientHistory,
    build_matrix,
    compute_features,
    feature_vector,
)


def _series(n=60, seed=1):
    rng = np.random.default_rng(seed)
    return (10000 * np.exp(np.cumsum(rng.normal(0, 0.01, n)))).tolist()


def test_insufficient_history_raises():
    with pytest.raises(InsufficientHistory):
        compute_features([1.0] * (MIN_HISTORY - 1))


def test_feature_vector_length_and_order():
    vec = feature_vector(_series())
    assert len(vec) == len(FEATURE_NAMES)
    feats = compute_features(_series())
    assert [feats[n] for n in FEATURE_NAMES] == vec


def test_no_look_ahead():
    """시점 T의 피처는 T 이후 값에 영향받지 않는다(look-ahead 없음)."""
    series = _series(n=60)
    t = 40
    before = feature_vector(series[: t + 1])
    tampered = list(series)
    for i in range(t + 1, len(tampered)):
        tampered[i] *= 2.0  # 미래를 바꿔치기
    after = feature_vector(tampered[: t + 1])
    assert before == after


def test_deterministic():
    series = _series()
    assert feature_vector(series) == feature_vector(series)


def test_build_matrix_shapes():
    series = _series(n=60)
    x, times = build_matrix(series)
    assert x.shape[1] == len(FEATURE_NAMES)
    assert x.shape[0] == len(times)
    assert times[0] == MIN_HISTORY - 1
    assert times == sorted(times)
