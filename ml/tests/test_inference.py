import numpy as np

from mlcore.features import MIN_HISTORY


def _series(n=80, seed=7):
    rng = np.random.default_rng(seed)
    return (10000 * np.exp(np.cumsum(rng.normal(0, 0.01, n)))).tolist()


def test_predict_degraded_without_model(monkeypatch, tmp_path):
    # 모델 경로를 빈 곳으로 돌려 미로드 상태를 만든다.
    from app import config, inference

    monkeypatch.setattr(config, "MODEL_PATH", tmp_path / "none.joblib")
    predictor = inference.Predictor()
    assert predictor.model_loaded is False

    result = predictor.predict(_series())
    assert result["degraded"] is True
    assert result["signal"] == "HOLD"
    assert result["score"] == 0.5
    assert result["modelVersion"] == "uninitialized"


def test_predict_with_trained_model(monkeypatch, tmp_path):
    from app import config, inference
    from training.dataset import synthesize_series
    from training.train import train

    out = tmp_path / "model.joblib"
    train(synthesize_series(n=800, seed=42), horizon=5, threshold=0.0,
          test_ratio=0.3, out_path=out)

    monkeypatch.setattr(config, "MODEL_PATH", out)
    predictor = inference.Predictor()
    assert predictor.model_loaded is True

    result = predictor.predict(_series())
    assert result["degraded"] is False
    assert 0.0 <= result["score"] <= 1.0
    assert result["signal"] in {"BUY", "SELL", "HOLD"}
    assert result["featuresUsed"] > 0


def test_predict_short_history_is_degraded(monkeypatch, tmp_path):
    from app import config, inference
    from training.dataset import synthesize_series
    from training.train import train

    out = tmp_path / "model.joblib"
    train(synthesize_series(n=800, seed=42), horizon=5, threshold=0.0,
          test_ratio=0.3, out_path=out)
    monkeypatch.setattr(config, "MODEL_PATH", out)
    predictor = inference.Predictor()

    short = [10000.0] * (MIN_HISTORY - 1)
    result = predictor.predict(short)
    assert result["degraded"] is True
    assert result["signal"] == "HOLD"
