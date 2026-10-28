"""SAGE's models and the two simple forecasts they have to beat.

  base rate:   how often this happens anywhere, in any month;
  last month:  how often it happens next, for countries in the same
               state this month (none, a little, a lot of violence);
  sage:        for countries already at that level of violence,
               gradient-boosted trees over everything in features.py;
               for the rest, the average of those trees and an outbreak
               model: a plain logistic regression on a few history and
               build-up signals. Outbreaks are rare, and the trees learn
               mostly from wars already under way; the simpler model
               can't overfit the handful of past outbreaks and keeps the
               build-up signals' effect in proportion;
               and while a build-up is on (several warning signs far above
               usual at once), never lower than how often it followed past
               build-ups like it.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from .features import ALERT_LEVEL, RISING, Question

MODELS = ["base_rate", "last_month", "sage"]
# Bins of this month's deaths for the "last month" forecast.
LEVELS = [0, 1, 10, 25, 100, 1000, np.inf]
# How many alerts' worth of weight the usual rate gets (see Forecaster.fit).
ALERT_PRIOR = 5
# What the outbreak model looks at, besides the question's own series.
OUTBREAK = ["near_state_last3", "since_any", "buildup", "news_actor_mobilize_surge",
            "news_actor_threaten_force_surge", "early_where_fight_surge",
            "early_where_heavy_weapons_surge", "early_where_assault_surge",
            "early_where_bombing_surge", "news_where_world_share"]


def _level(values: pd.Series) -> np.ndarray:
    return np.digitize(values.to_numpy(), LEVELS[1:-1])


class Forecaster:
    def __init__(self, question: Question, features: list[str]):
        self.question = question
        self.features = features
        self.model = HistGradientBoostingClassifier(
            learning_rate=0.05,
            max_iter=300,
            max_leaf_nodes=15,
            min_samples_leaf=50,
            l2_regularization=1.0,
            categorical_features=[features.index("region")],
            monotonic_cst=[1 if f in RISING else 0 for f in features],
            random_state=0,
        )
        s = question.series
        own = [f"{s}_now", f"{s}_last3", f"{s}_last12", f"since_{question.key}"]
        self.outbreak_features = own + [f for f in OUTBREAK if f in features and f not in own]
        self.outbreak = make_pipeline(StandardScaler(), LogisticRegression(C=0.1, max_iter=2000))

    def _outbreak_inputs(self, rows: pd.DataFrame) -> np.ndarray:
        x = rows[self.outbreak_features].astype(float)
        news = [f for f in self.outbreak_features if f.startswith(("news_", "early_", "buildup"))]
        has_news = x[news].notna().any(axis=1).astype(float) if news else pd.Series(0.0, index=x.index)
        # Before 2013 there's no news: "no signal", flagged as such.
        x = x.fillna(0).clip(-10, 10)
        return np.column_stack([x.to_numpy(), has_news.to_numpy()])

    def _at_level(self, rows: pd.DataFrame) -> np.ndarray:
        return (rows[self.question.series] >= self.question.threshold).to_numpy()

    def fit(self, train: pd.DataFrame, target: str) -> "Forecaster":
        self.ahead = int(target.rsplit("_", 1)[1])
        y = train[target].to_numpy()
        self.base_rate = (y.sum() + 1) / (len(y) + 2)
        level = _level(train[self.question.series])
        self.by_level = {
            lv: (y[level == lv].sum() + self.base_rate) / ((level == lv).sum() + 1)
            for lv in range(len(LEVELS) - 1)
        }
        self.alert_rate = None
        if "buildup" in train:
            # How often it followed past build-up alerts, where it wasn't
            # already happening, pulled towards the usual rate when there
            # are few alerts to go on.
            calm = ~self._at_level(train)
            usual = y[calm].mean() if calm.any() else self.base_rate
            alerted = calm & (train["buildup"] >= ALERT_LEVEL).to_numpy()
            self.alert_rate = (y[alerted].sum() + usual * ALERT_PRIOR) / (alerted.sum() + ALERT_PRIOR)
        self.fitted = 0 < y.sum() < len(y)
        if self.fitted:
            self.model.fit(train[self.features], y)
            calm = ~self._at_level(train)
            self.outbreak_fitted = 0 < y[calm].sum() < calm.sum()
            if self.outbreak_fitted:
                self.outbreak.fit(self._outbreak_inputs(train[calm]), y[calm])
        return self

    def predict(self, rows: pd.DataFrame) -> pd.DataFrame:
        level = _level(rows[self.question.series])
        out = pd.DataFrame(index=rows.index)
        out["base_rate"] = self.base_rate
        out["last_month"] = [self.by_level[lv] for lv in level]
        if not self.fitted:
            out["sage"] = out["base_rate"]
            return out
        trees = self.model.predict_proba(rows[self.features])[:, 1]
        out["trees"] = trees
        out["sage"] = trees
        if self.outbreak_fitted:
            calm = ~self._at_level(rows)
            if calm.any():
                simple = self.outbreak.predict_proba(self._outbreak_inputs(rows[calm]))[:, 1]
                out.loc[calm, "outbreak"] = simple
                out.loc[calm, "sage"] = (trees[calm] + simple) / 2
        # Another forecaster asking the same question, kept for comparison.
        # Averaging with it was tested and made SAGE worse (see the report).
        other = f"views_{self.question.key}_{self.ahead}"
        if other in rows:
            out["views"] = rows[other].to_numpy()
        if self.alert_rate is not None:
            # While an alert is on, never below what followed past alerts.
            alerted = ~self._at_level(rows) & (rows["buildup"] >= ALERT_LEVEL).to_numpy()
            out.loc[alerted, "sage"] = np.maximum(out.loc[alerted, "sage"], self.alert_rate)
        return out
