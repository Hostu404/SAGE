"""How good a set of probability forecasts was."""

from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score

# Forecasts are grouped into these bands to check "when it said 70%,
# did it happen about 70% of the time?"
BANDS = [0, 0.02, 0.05, 0.1, 0.2, 0.35, 0.5, 0.65, 0.8, 0.9, 0.95, 1.0]


def brier(p, y) -> float:
    """Mean squared gap between forecast and outcome: 0 is perfect."""
    p, y = np.asarray(p, float), np.asarray(y, float)
    return float(np.mean((p - y) ** 2))


def log_loss(p, y) -> float:
    p = np.clip(np.asarray(p, float), 1e-4, 1 - 1e-4)
    y = np.asarray(y, float)
    return float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))


def skill(score: float, reference: float) -> float:
    """How much smaller the error is than the reference's: 0.3 = 30% better."""
    return float(1 - score / reference) if reference > 0 else 0.0


def auc(p, y) -> float | None:
    """Chance a random case that happened was rated above one that didn't."""
    y = np.asarray(y)
    if y.min() == y.max():
        return None
    return float(roc_auc_score(y, p))


def calibration(p, y) -> pd.DataFrame:
    p, y = np.asarray(p, float), np.asarray(y, float)
    band = np.clip(np.digitize(p, BANDS[1:-1], right=False), 0, len(BANDS) - 2)
    rows = []
    for b in range(len(BANDS) - 1):
        mask = band == b
        if mask.sum() == 0:
            continue
        rows.append({
            "from": BANDS[b], "to": BANDS[b + 1], "forecasts": int(mask.sum()),
            "said": float(p[mask].mean()), "happened": float(y[mask].mean()),
        })
    return pd.DataFrame(rows)
