import numpy as np
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def _closes(n=80, seed=11):
    rng = np.random.default_rng(seed)
    prices = 10000 * np.exp(np.cumsum(rng.normal(0, 0.01, n)))
    return [f"{p:.2f}" for p in prices]


def test_health_ok():
    res = client.get("/health")
    assert res.status_code == 200
    body = res.json()
    assert body["status"] == "ok"
    assert "modelLoaded" in body


def test_predict_contract_shape():
    res = client.post("/predict", json={"symbol": "005930", "closes": _closes()})
    assert res.status_code == 200
    body = res.json()
    assert body["symbol"] == "005930"
    assert body["signal"] in {"BUY", "SELL", "HOLD"}
    assert 0.0 <= body["score"] <= 1.0
    assert "modelVersion" in body
    assert "degraded" in body


def test_predict_rejects_bad_closes():
    res = client.post("/predict", json={"symbol": "005930", "closes": ["oops"]})
    assert res.status_code == 422


def test_predict_missing_closes_is_422():
    res = client.post("/predict", json={"symbol": "005930"})
    assert res.status_code == 422
