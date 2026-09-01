import numpy as np

from mlcore.features import MIN_HISTORY
from mlcore.labeling import build_labeled_dataset, make_label


def test_make_label_direction():
    series = [100.0, 101.0, 99.0, 105.0]
    # t=0, horizon=3 -> series[3]/series[0]-1 = 0.05 > 0 -> 1
    assert make_label(series, 0, horizon=3, threshold=0.0) == 1
    # t=1, horizon=1 -> series[2]/series[1]-1 < 0 -> 0
    assert make_label(series, 1, horizon=1, threshold=0.0) == 0


def test_make_label_none_when_no_future():
    series = [100.0, 101.0]
    assert make_label(series, 1, horizon=3, threshold=0.0) is None


def test_dataset_alignment_and_no_leakage():
    rng = np.random.default_rng(3)
    series = (10000 * np.exp(np.cumsum(rng.normal(0, 0.01, 100)))).tolist()
    horizon = 5
    x, y, times = build_labeled_dataset(series, horizon=horizon, threshold=0.0)
    assert len(x) == len(y) == len(times)
    assert times[0] == MIN_HISTORY - 1
    # 라벨이 정의되는 마지막 t 는 len-horizon-1 을 넘지 않는다(미래가 있어야 함).
    assert max(times) <= len(series) - horizon - 1
    assert set(np.unique(y)).issubset({0, 1})
