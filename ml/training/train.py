"""baseline 모델 학습 진입점.

    python -m training.train                 # 합성 데이터로 학습
    python -m training.train --csv data.csv  # 실데이터(close 컬럼)로 학습

파이프라인: 라벨 생성(look-ahead 차단) → 시간분할 → StandardScaler+LogisticRegression →
백테스트 아님(분류지표만). 최종 판단은 백엔드 백테스트로(ml/CLAUDE.md §5).
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict

import joblib
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, roc_auc_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from mlcore.features import FEATURE_NAMES
from mlcore.labeling import build_labeled_dataset

from training.dataset import (
    chronological_split,
    load_series_from_csv,
    synthesize_series,
)

SEED = 42


def train(
    series: np.ndarray,
    horizon: int,
    threshold: float,
    test_ratio: float,
    out_path: Path,
) -> Dict[str, Any]:
    x, y, times = build_labeled_dataset(series, horizon=horizon, threshold=threshold)
    if len(y) < 50:
        raise SystemExit(f"학습 표본이 너무 적습니다(n={len(y)}). 데이터를 늘리세요.")
    if len(np.unique(y)) < 2:
        raise SystemExit("라벨이 한 클래스뿐입니다. threshold/horizon 을 조정하세요.")

    x_tr, x_te, y_tr, y_te = chronological_split(x, y, times, test_ratio)

    model = Pipeline(
        [
            ("scaler", StandardScaler()),
            ("clf", LogisticRegression(max_iter=1000, random_state=SEED)),
        ]
    )
    model.fit(x_tr, y_tr)

    proba_te = model.predict_proba(x_te)[:, 1]
    pred_te = (proba_te >= 0.5).astype(int)
    metrics = {
        "testAccuracy": round(float(accuracy_score(y_te, pred_te)), 4),
        "testAuc": round(float(roc_auc_score(y_te, proba_te)), 4)
        if len(np.unique(y_te)) > 1 else 0.5,
        "nTrain": float(len(y_tr)),
        "nTest": float(len(y_te)),
        "posRate": round(float(y.mean()), 4),
    }

    trained_at = datetime.now(timezone.utc).replace(microsecond=0)
    version = "baseline-logreg-" + trained_at.strftime("%Y%m%dT%H%M%SZ")
    metadata: Dict[str, Any] = {
        "model_version": version,
        "trained_at": trained_at.isoformat().replace("+00:00", "Z"),
        "feature_names": FEATURE_NAMES,
        "horizon": horizon,
        "label_threshold": threshold,
        "metrics": metrics,
    }

    out_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {"model": model, "feature_names": FEATURE_NAMES, "metadata": metadata},
        out_path,
    )
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description="baseline 상승예측 모델 학습")
    parser.add_argument("--csv", help="종가 CSV 경로(close 컬럼). 없으면 합성 데이터")
    parser.add_argument("--column", default="close")
    parser.add_argument("--horizon", type=int, default=5, help="라벨 기간(봉)")
    parser.add_argument("--threshold", type=float, default=0.0, help="상승 판정 수익률 임계")
    parser.add_argument("--test-ratio", type=float, default=0.3)
    parser.add_argument("--n", type=int, default=2000, help="합성 데이터 길이")
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument(
        "--out",
        default=str(Path(__file__).resolve().parent.parent / "artifacts" / "model.joblib"),
    )
    args = parser.parse_args()

    if args.csv:
        series = load_series_from_csv(args.csv, args.column)
        source = f"csv:{args.csv}"
    else:
        series = synthesize_series(n=args.n, seed=args.seed)
        source = f"synthetic(n={args.n}, seed={args.seed})"

    meta = train(series, args.horizon, args.threshold, args.test_ratio, Path(args.out))
    print(f"[train] source={source}")
    print(f"[train] version={meta['model_version']}")
    print(f"[train] metrics={meta['metrics']}")
    print(f"[train] saved -> {args.out}")


if __name__ == "__main__":
    main()
