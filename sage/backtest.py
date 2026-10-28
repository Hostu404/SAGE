"""Replays the past: forecasts every month since 2018 using only what
was known at the time, then checks them against what happened.

Each year the models are retrained on everything up to the December
before, then forecast each month of that year from that month's data.
"""

from __future__ import annotations

import pandas as pd
from joblib import Parallel, delayed

from . import scoring
from .features import FIRST_TRAINING_MONTH, QUESTIONS, feature_columns, month_index
from .models import MODELS, Forecaster

HORIZONS = (1, 2, 3)
NEW_AFTER = 12  # a "new outbreak" follows 12 months or more without one


def _year(frame: pd.DataFrame, features: list[str], start: int, year: int) -> list[pd.DataFrame]:
    """Retrain on everything up to the December before `year`, then
    forecast each month of it."""
    cutoff = year * 12 - 1
    origins = frame[frame["mi"] // 12 == year]
    rows = []
    for q in QUESTIONS.values():
        for h in HORIZONS:
            target = f"y_{q.key}_{h}"
            test = origins[origins[target].notna()]
            if test.empty:
                continue
            train = frame[(frame["mi"] >= start) & (frame["mi"] + h <= cutoff)]
            forecasts = Forecaster(q, features).fit(train, target).predict(test)
            rows.append(pd.DataFrame({
                "question": q.key,
                "ahead": h,
                "country_id": test["country_id"],
                "made": test["mi"],
                "happened": test[target].astype(int),
                "now": test[q.series],
                "since": test[f"since_{q.key}"],
                **{m: forecasts[m] for m in MODELS},
                "views": forecasts.get("views", pd.Series(float("nan"), index=forecasts.index)),
            }))
    return rows


def run(frame: pd.DataFrame, first_year: int = 2018, jobs: int = -1) -> pd.DataFrame:
    """Every backtest forecast, one row per question, months ahead,
    country and month it was made in. Years are replayed side by side,
    one per processor."""
    features = feature_columns(frame)
    start = int(month_index(pd.Series([FIRST_TRAINING_MONTH])).iloc[0])
    years = range(first_year, int(frame["mi"].max()) // 12 + 1)
    parts = Parallel(n_jobs=jobs)(delayed(_year)(frame, features, start, y) for y in years)
    return pd.concat([r for part in parts for r in part], ignore_index=True)


def _versus_views(r: pd.DataFrame) -> dict | None:
    """SAGE against ViEWS on the same countries and months, where ViEWS
    forecast the same question."""
    both = r[r["views"].notna()]
    if both.empty:
        return None
    return {
        "forecasts": int(len(both)),
        "from": int(both["made"].min()), "to": int(both["made"].max()),
        "sage": scoring.brier(both["sage"], both["happened"]),
        "views": scoring.brier(both["views"], both["happened"]),
        "last_month": scoring.brier(both["last_month"], both["happened"]),
        "average": scoring.brier((both["sage"] + both["views"]) / 2, both["happened"]),
    }


def summarize(results: pd.DataFrame) -> list[dict]:
    """Scores for each question and number of months ahead."""
    out = []
    for (key, h), r in results.groupby(["question", "ahead"], sort=False):
        q = QUESTIONS[key]
        briers = {m: scoring.brier(r[m], r["happened"]) for m in MODELS}
        new = r[r["since"] >= NEW_AFTER]
        new_hits = new[new["happened"] == 1]
        flagged = new["sage"] >= 0.2
        by_year = []
        for year, ry in r.groupby(r["made"] // 12):
            b = scoring.brier(ry["sage"], ry["happened"])
            by_year.append({
                "year": int(year),
                "forecasts": int(len(ry)),
                "happened": int(ry["happened"].sum()),
                "sage_brier": b,
                "skill_vs_last_month": scoring.skill(b, scoring.brier(ry["last_month"], ry["happened"])),
            })
        out.append({
            "question": key,
            "title": q.title,
            "text": q.text,
            "ahead": int(h),
            "forecasts": int(len(r)),
            "happened": int(r["happened"].sum()),
            "first_month": int(r["made"].min()),
            "last_month": int(r["made"].max()),
            "brier": briers,
            "log_loss": {m: scoring.log_loss(r[m], r["happened"]) for m in MODELS},
            "auc": {m: scoring.auc(r[m], r["happened"]) for m in MODELS},
            "skill_vs_base_rate": scoring.skill(briers["sage"], briers["base_rate"]),
            "skill_vs_last_month": scoring.skill(briers["sage"], briers["last_month"]),
            "calibration": scoring.calibration(r["sage"], r["happened"]).to_dict("records"),
            "new": {
                "forecasts": int(len(new)),
                "happened": int(len(new_hits)),
                "auc": scoring.auc(new["sage"], new["happened"]),
                "flagged": int(flagged.sum()),
                "flagged_and_happened": int((flagged & (new["happened"] == 1)).sum()),
                "mean_when_happened": float(new_hits["sage"].mean()) if len(new_hits) else None,
                "mean_when_calm": float(new.loc[new["happened"] == 0, "sage"].mean()) if len(new) else None,
            },
            "by_year": by_year,
            "views": _versus_views(r),
        })
    return out
