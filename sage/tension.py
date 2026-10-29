"""Forecasts for pairs of countries: will their forces clash, or go to war?

For every pair SAGE follows (pairs.followed), each month:

  features:  how far up the escalation ladder each side's actions towards
             the other are, compared with the pair's usual over the year
             before; how hostile and how friendly their dealings are; how
             much attention the world pays them; whether and how recently
             they've fought; and how much fighting each has at home;
  questions: deadly clashes between their forces (at least one death in
             fighting between the two governments, UCDP) and war (1,000 or
             more deaths in a month), within the next 3 and 6 months.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd
from joblib import Parallel, delayed
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from . import scoring, ties
from .features import month_index, month_name

HOSTILE_STEPS = ["words", "reduce_relations", "threaten", "force_posture", "coerce", "assault", "fight"]
STEPS = ["words", "reduce_relations", "threaten", "threaten_force", "force_posture", "mobilize",
         "coerce", "assault", "fight", "heavy_weapons"]
QUESTIONS = {
    "clash": (1, "Deadly clashes", "at least one person killed in fighting between their forces"),
    "war": (1000, "War", "at least 1,000 killed in a month in fighting between them"),
}
WINDOWS = (3, 6)
NEVER = 240
FIRST_MONTH = "1989-01"          # UCDP's record starts here
KEEP_CALM = 0.25                 # share of calm pair-months the models learn from
SIMPLE = ["max_threaten_surge", "max_threaten_force_surge", "max_force_posture_surge",
          "max_mobilize_surge", "max_reduce_relations_surge", "max_fight_surge",
          "max_heavy_weapons_surge", "climb", "hostile_share", "attention",
          "since_clash", "deaths_last12", "home_a", "home_b"]


def _past_year(x: np.ndarray) -> np.ndarray:
    """For a pairs x months grid: the mean of the 12 months before each
    month (at least 3 needed)."""
    pairs_, months = x.shape
    total = np.concatenate([np.zeros((pairs_, 1)), np.cumsum(x, axis=1)], axis=1)
    t = np.arange(months)
    lo = np.maximum(t - 12, 0)
    n = t - lo
    with np.errstate(invalid="ignore", divide="ignore"):
        mean = (total[:, t] - total[:, lo]) / n
    mean[:, n < 3] = np.nan
    return mean


def _last(x: np.ndarray, months: int) -> np.ndarray:
    """Sum over the last `months` months, this one included."""
    total = np.concatenate([np.zeros((x.shape[0], 1)), np.cumsum(x, axis=1)], axis=1)
    t = np.arange(x.shape[1])
    return total[:, t + 1] - total[:, np.maximum(t + 1 - months, 0)]


def build(news: pd.DataFrame, fights: pd.DataFrame, panel: pd.DataFrame | None,
          raw_dir: Path | None = None) -> pd.DataFrame:
    """One row per pair and month: features and answers. Every pair gets
    every month, so the work is done on a pairs x months grid. With
    raw_dir, also borders and past disputes (ties.py)."""
    pairs_ = news[["a", "b"]].drop_duplicates().sort_values(["a", "b"]).reset_index(drop=True)
    first = int(month_index(pd.Series([news["month"].min()])).iloc[0])
    last = int(month_index(pd.Series([news["month"].max()])).iloc[0])
    months = [month_name(m) for m in range(first, last + 1)]
    P, M = len(pairs_), len(months)
    grid = pairs_.merge(pd.DataFrame({"month": months}), how="cross")
    frame = grid.merge(news, on=["a", "b", "month"], how="left")
    counts = [c for c in news.columns if c[:3] in ("ab_", "ba_")]
    frame[counts] = frame[counts].fillna(0)
    per_month = news.groupby("month")[["days", "world_all"]].first()
    frame["days"] = frame["month"].map(per_month["days"])
    frame["world_all"] = frame["month"].map(per_month["world_all"])
    frame["mi"] = month_index(frame["month"])

    def g(column: str | pd.Series) -> np.ndarray:
        values = frame[column] if isinstance(column, str) else column
        return values.to_numpy(dtype=float).reshape(P, M)

    days = g("days")
    out: dict[str, np.ndarray] = {}
    for step in STEPS:
        surges = []
        for d in ("ab", "ba"):
            rate = np.log1p(g(f"{d}_{step}") / days)
            out[f"{d}_{step}_rate"] = rate
            with np.errstate(invalid="ignore"):
                surge = rate - np.log1p(_past_year(g(f"{d}_{step}") / days))
            out[f"{d}_{step}_surge"] = surge
            surges.append(surge)
        out[f"max_{step}_surge"] = np.fmax(surges[0], surges[1])
    # How many steps of the ladder are at least twice their usual.
    out["climb"] = sum(np.nan_to_num(out[f"max_{s}_surge"]) >= np.log(2) for s in STEPS).astype(float)

    total = g("ab_all") + g("ba_all")
    hostile = sum(g(f"{d}_{s}") for d in ("ab", "ba") for s in HOSTILE_STEPS)
    with np.errstate(invalid="ignore", divide="ignore"):
        share = np.where(total > 0, hostile / total, 0.0)
        out["hostile_share"] = share
        out["hostile_share_change"] = share - _past_year(share)
        out["cooperate_share"] = np.where(total > 0, (g("ab_cooperate") + g("ba_cooperate")) / total, 0.0)
        out["tone"] = np.where(total > 0, (g("ab_tone_sum") + g("ba_tone_sum")) / total, 0.0)
        attention = np.log1p(1e5 * total / g("world_all"))
    out["attention"] = attention
    out["attention_surge"] = attention - _past_year(attention)

    # Fighting between them so far.
    deaths_s = frame[["a", "b", "month"]].merge(fights, on=["a", "b", "month"], how="left")["deaths"].fillna(0)
    deaths = deaths_s.to_numpy(dtype=float).reshape(P, M)
    since = np.full((P, M), float(NEVER))
    last_seen = np.full(P, np.nan)
    for m in range(M):
        last_seen = np.where(deaths[:, m] >= 1, m, last_seen)
        since[:, m] = np.where(np.isnan(last_seen), NEVER, np.minimum(m - last_seen, NEVER))
    out["since_clash"] = since
    out["deaths_last12"] = np.log1p(_last(deaths, 12))
    out["clash_months_last12"] = _last((deaths >= 1).astype(float), 12)

    # Fighting inside each country (from the country records).
    for side in ("a", "b"):
        if panel is not None:
            home = panel[["country_id", "month", "state"]].rename(columns={"country_id": side})
            v = frame[[side, "month"]].merge(home, on=[side, "month"], how="left")["state"].fillna(0)
            out[f"home_{side}"] = np.log1p(v.to_numpy(dtype=float)).reshape(P, M)
        else:
            out[f"home_{side}"] = np.zeros((P, M))

    flat = {k: v.reshape(-1) for k, v in out.items()}
    frame = pd.concat([frame, pd.DataFrame(flat, index=frame.index)], axis=1)
    frame["deaths"] = deaths.reshape(-1)
    known_to = int(month_index(pd.Series([fights["month"].max()])).iloc[0]) if len(fights) else first
    mi = np.arange(first, last + 1)
    for name, (threshold, _, _) in QUESTIONS.items():
        hit = (deaths >= threshold).astype(float)
        for w in WINDOWS:
            ahead = np.full((P, M), np.nan)
            for m in range(M - w):
                ahead[:, m] = hit[:, m + 1:m + 1 + w].max(axis=1)
            known = (mi + w <= known_to) & (mi >= month_index(pd.Series([FIRST_MONTH])).iloc[0])
            ahead[:, ~known] = np.nan
            frame[f"y_{name}_{w}"] = ahead.reshape(-1)
    if raw_dir is not None:
        frame = pd.concat([frame, ties.build(raw_dir, frame)], axis=1)
    return frame


OTHER = ["climb", "hostile_share", "hostile_share_change", "cooperate_share", "tone", "attention",
         "attention_surge", "since_clash", "deaths_last12", "clash_months_last12", "home_a", "home_b",
         *ties.FEATURES]


# The line model reads a surge of more than about 7 times usual as 7 times.
SURGE_CAP = 2.0


def feature_columns(frame: pd.DataFrame) -> list[str]:
    return [c for c in frame.columns if c.endswith(("_rate", "_surge")) or c in OTHER]


class PairForecaster:
    """The average of gradient-boosted trees over every feature and a plain
    logistic regression over a few, like the country outbreak model."""

    def __init__(self, features: list[str]):
        self.features = features
        self.simple = [f for f in SIMPLE if f in features]
        self.trees = HistGradientBoostingClassifier(
            learning_rate=0.05, max_iter=200, max_leaf_nodes=15, min_samples_leaf=40,
            l2_regularization=1.0, random_state=0)
        # Surges stay on their own scale: nearly every pair-month is close
        # to 0, so standardising made a rare spike look like 40 deviations
        # and the line forecast war between allies on one busy news month.
        surges = [i for i, f in enumerate(self.simple) if f.endswith("_surge")]
        rest = [i for i in range(len(self.simple)) if i not in surges]
        self.line = make_pipeline(
            ColumnTransformer([("surges", "passthrough", surges), ("rest", StandardScaler(), rest)]),
            LogisticRegression(C=0.1, max_iter=3000))

    def _x(self, rows: pd.DataFrame) -> np.ndarray:
        x = rows[self.simple].astype(float).fillna(0)
        surges = [f for f in self.simple if f.endswith("_surge")]
        x[surges] = x[surges].clip(-SURGE_CAP, SURGE_CAP)
        if "since_clash" in x:
            x["since_clash"] = np.log1p(x["since_clash"])
        return x.to_numpy()

    def fit(self, train: pd.DataFrame, target: str) -> "PairForecaster":
        y = train[target].to_numpy()
        self.base_rate = (y.sum() + 1) / (len(y) + 2)
        recent = (train["since_clash"] < 6).to_numpy()
        self.by_recent = {r: (y[recent == r].sum() + self.base_rate) / ((recent == r).sum() + 1) for r in (True, False)}
        self.fitted = 0 < y.sum() < len(y)
        if self.fitted:
            # Nearly every pair-month is calm: learn from every clash but a
            # random share of the calm months, weighted back up to scale.
            rng = np.random.default_rng(0)
            keep = (y == 1) | (rng.random(len(y)) < KEEP_CALM)
            weight = np.where(y[keep] == 1, 1.0, 1.0 / KEEP_CALM)
            rows = train[keep]
            self.trees.fit(rows[self.features], y[keep], sample_weight=weight)
            self.line.fit(self._x(rows), y[keep], logisticregression__sample_weight=weight)
        return self

    def predict(self, rows: pd.DataFrame) -> pd.DataFrame:
        out = pd.DataFrame(index=rows.index)
        out["base_rate"] = self.base_rate
        out["recent"] = [self.by_recent[r] for r in (rows["since_clash"] < 6)]
        if not self.fitted:
            out["trees"] = out["line"] = out["sage"] = out["base_rate"]
            return out
        out["trees"] = self.trees.predict_proba(rows[self.features])[:, 1]
        out["line"] = self.line.predict_proba(self._x(rows))[:, 1]
        out["sage"] = (out["trees"] + out["line"]) / 2
        return out


MODELS = ["base_rate", "recent", "trees", "line", "sage"]


def _year(frame: pd.DataFrame, features: list[str], start: int, year: int,
          windows: tuple[int, ...]) -> list[pd.DataFrame]:
    cutoff = year * 12 - 1
    origins = frame[frame["mi"] // 12 == year]
    rows = []
    for name in QUESTIONS:
        for w in windows:
            target = f"y_{name}_{w}"
            test = origins[origins[target].notna()]
            if test.empty:
                continue
            train = frame[(frame["mi"] >= start) & (frame["mi"] + w <= cutoff) & frame[target].notna()]
            p = PairForecaster(features).fit(train, target).predict(test)
            rows.append(pd.DataFrame({
                "question": name, "within": w, "a": test["a"], "b": test["b"], "made": test["mi"],
                "happened": test[target].astype(int), "since_clash": test["since_clash"],
                **{m: p[m] for m in MODELS}}))
    return rows


def backtest(frame: pd.DataFrame, first_year: int = 2008, jobs: int = -1,
             features: list[str] | None = None, windows: tuple[int, ...] = WINDOWS) -> pd.DataFrame:
    """Replay every month from `first_year`, retraining each January;
    years run side by side, one per processor."""
    features = features or feature_columns(frame)
    start = int(month_index(pd.Series([FIRST_MONTH])).iloc[0]) + 12
    years = range(first_year, int(frame["mi"].max()) // 12 + 1)
    parts = Parallel(n_jobs=jobs)(delayed(_year)(frame, features, start, y, windows) for y in years)
    return consistent(pd.concat([r for part in parts for r in part], ignore_index=True))


def consistent(results: pd.DataFrame) -> pd.DataFrame:
    """A war is also a clash: the chance of clashes is never below the
    chance of war for the same pair, month and window."""
    keys = ["a", "b", "made", "within"]
    war = results[results["question"] == "war"].set_index(keys)
    clash = results["question"] == "clash"
    idx = pd.MultiIndex.from_frame(results.loc[clash, keys])
    for m in MODELS:
        floor = war[m].reindex(idx).to_numpy()
        results.loc[clash, m] = np.fmax(results.loc[clash, m].to_numpy(), floor)
    return results


def summarize(results: pd.DataFrame) -> list[dict]:
    out = []
    for (name, w), r in results.groupby(["question", "within"], sort=False):
        fresh = r[r["since_clash"] >= 12]
        out.append({
            "question": name, "within": int(w), "forecasts": int(len(r)), "happened": int(r["happened"].sum()),
            "brier": {m: scoring.brier(r[m], r["happened"]) for m in MODELS},
            "auc": {m: scoring.auc(r[m], r["happened"]) for m in MODELS},
            "skill_vs_recent": scoring.skill(scoring.brier(r["sage"], r["happened"]),
                                             scoring.brier(r["recent"], r["happened"])),
            "calibration": scoring.calibration(r["sage"], r["happened"]).to_dict("records"),
            "fresh": {
                "forecasts": int(len(fresh)), "happened": int(fresh["happened"].sum()),
                "auc": scoring.auc(fresh["sage"], fresh["happened"]),
                "mean_when_happened": float(fresh.loc[fresh["happened"] == 1, "sage"].mean()) if fresh["happened"].any() else None,
                "mean_when_calm": float(fresh.loc[fresh["happened"] == 0, "sage"].mean()) if len(fresh) else None,
            },
        })
    return out


# What each step of the ladder means, for summaries.
STEP_WORDS = {
    "words": "criticism and demands",
    "reduce_relations": "sanctions and expulsions",
    "threaten": "threats",
    "threaten_force": "threats of military force",
    "force_posture": "shows of military force",
    "mobilize": "troop mobilisations and military alerts",
    "coerce": "seizures and arrests",
    "assault": "attacks",
    "fight": "fighting",
    "heavy_weapons": "shelling and air strikes",
}
# Since 1990, of pairs of countries in a military dispute (Correlates of
# War, dyadic MIDs 1990-2014): share with any deaths, and share at war.
DISPUTES_DEADLY, DISPUTES_WAR = 0.077, 0.013


# The major powers today, as Correlates of War lists them (Gleditsch and
# Ward numbers): the United States, Britain, France, Germany, Russia, China
# and Japan.
MAJOR_POWERS = {2, 200, 220, 255, 260, 365, 710, 740}


def relevant(row: pd.Series) -> bool:
    """Whether two countries could plausibly come to blows: neighbours by
    land or within 400 miles by sea, a major power between them, or a past
    military dispute or clash (what political scientists call politically
    relevant pairs). Between any other two, news of fighting is nearly
    always someone else's attack with one side's citizens among the victims,
    and their threats are often one promising to defend the other, so
    their tension stays at usual. Without the ties data, every pair counts."""
    if "border" not in row.index:
        return True
    if int(row["a"]) in MAJOR_POWERS or int(row["b"]) in MAJOR_POWERS:
        return True
    return bool(row["border"] > 0 or row["sea_near"] > 0 or row.get("disputes_before", 0) > 0
                or row["deaths_last12"] > 0 or row.get("since_clash", NEVER) < NEVER)


# Attacks and fighting: news of them is only taken as between the two
# countries themselves when they're neighbours or have fought (counted_steps).
PHYSICAL = ("assault", "fight", "heavy_weapons")


def counted_steps(row: pd.Series) -> list[str]:
    """The steps of the ladder that count towards how tense two countries
    are: none unless they could come to blows (relevant), and attacks and
    fighting only between neighbours (by land, or within 400 miles by sea)
    or countries that have fought since 1989. Between others, news of
    fighting is nearly always someone else's attack with one side's
    citizens among the victims: Americans in the Paris attacks, British
    tourists in Tunisia. Without the ties data, every step counts."""
    if not relevant(row):
        return []
    if "border" not in row.index:
        return list(STEPS)
    near_or_fought = bool(row["border"] > 0 or row["sea_near"] > 0 or row["deaths_last12"] > 0
                          or row.get("since_clash", NEVER) < NEVER)
    return [s for s in STEPS if near_or_fought or s not in PHYSICAL]


def tension_of(row: pd.Series) -> str:
    """How tense two countries are, from the news (tension_word), counting only counted_steps."""
    steps = counted_steps(row)
    if len(steps) == len(STEPS):
        climb = row["climb"]
    else:
        climb = sum(1 for s in steps if raised_steps(row, "ab", [s]) or raised_steps(row, "ba", [s]))
    return tension_word(climb, row["deaths_last12"])


def tension_word(climb: float, deaths_last12: float) -> str:
    if deaths_last12 >= np.log1p(1000):
        return "at war"
    if deaths_last12 > 0:
        return "fighting"
    if climb >= 5:
        return "very high"
    if climb >= 3:
        return "high"
    if climb >= 1:
        return "above usual"
    return "usual"


def raised_steps(row: pd.Series, direction: str, steps: list[str] | None = None) -> list[tuple[str, float]]:
    """Steps of the ladder (or just `steps`) at least twice their usual from one side, biggest first."""
    out = []
    for step in STEPS if steps is None else steps:
        surge = row.get(f"{direction}_{step}_surge")
        if surge is not None and np.isfinite(surge) and surge >= np.log(2):
            out.append((step, float(np.exp(surge))))
    return sorted(out, key=lambda x: -x[1])


def describe(row: pd.Series, names: dict[int, str], clash: float, war: float, last_fought: str | None,
             past: ties.Past | None = None) -> str:
    """A few sentences on one pair, from the side of country a. `last_fought`
    is the month of their last deadly clash in UCDP's data (1989 on), and
    `past` their history in the dispute record (ties.history)."""
    from .summary import pct, word
    a, b = names.get(int(row["a"]), str(row["a"])), names.get(int(row["b"]), str(row["b"]))
    level = tension_of(row)
    counted = counted_steps(row)
    s = []
    if level == "at war":
        s.append(f"{a} and {b} are at war.")
    elif level == "fighting":
        s.append(f"{a} and {b} have clashed in the past year.")
    elif level == "usual":
        s.append(f"Tension between {a} and {b} is at its usual level.")
    else:
        s.append(f"Tension between {a} and {b} is {level}.")
    for direction, who, whom in (("ab", a, b), ("ba", b, a)):
        steps = raised_steps(row, direction, counted)[:2]
        if steps:
            parts = [f"{STEP_WORDS[k]} (about {v:.0f}× usual)" for k, v in steps]
            s.append(f"From {who} towards {whom}: " + " and ".join(parts) + ".")
    s.append(f"Deadly clashes between their forces in the next six months: {word(clash)} ({pct(clash)}); "
             f"war: {word(war)} ({pct(war)}).")
    if last_fought:
        s.append(f"They last fought in {last_fought}.")
    elif level in ("high", "very high", "above usual"):
        # UCDP only counts clashes between two governments' forces, so it has
        # nothing for Australia and Afghanistan in 2001; the dispute record
        # does. In a dispute with several countries its deaths can be someone
        # else's (Britain and Israel's in 1956 were Israel fighting Jordan),
        # so short of a war it says "deadly military stand-off", not "fought".
        if past and past.deadly:
            if past.war:
                text = f"They were last at war in {past.deadly}"
                later = f"; their last military stand-off was in {past.last}"
            else:
                text = f"Their last deadly military stand-off was in {past.deadly}"
                later = f"; their last of any kind was in {past.last}"
            s.append(text + (later if past.last > past.deadly else "") + ".")
        elif past:
            s.append(f"There's no record of their forces fighting each other; their last military stand-off was in {past.last}.")
        else:
            s.append("There's no record of their forces fighting each other.")
        s.append(f"Since 1990, about {DISPUTES_DEADLY * 100:.0f} in 100 military stand-offs between countries led to deaths, "
                 f"and about {DISPUTES_WAR * 100:.0f} in 100 to war.")
    return " ".join(s)


def make(news: pd.DataFrame, fights: pd.DataFrame, panel: pd.DataFrame | None,
         names: dict[int, str], raw_dir: Path | None = None) -> dict:
    """Forecasts for every pair SAGE follows, as of the last month of
    conflict data, with a summary each."""
    from .pairs import followed
    frame = build(followed(news, fights), fights, panel, raw_dir)
    features = feature_columns(frame)
    origin = month_index(pd.Series([fights["month"].max() if len(fights) else news["month"].max()])).iloc[0]
    origin = min(int(origin), int(frame["mi"].max()))
    now = frame[frame["mi"] == origin].set_index(["a", "b"])
    start = int(month_index(pd.Series([FIRST_MONTH])).iloc[0]) + 12
    chances = {}
    for name in QUESTIONS:
        target = f"y_{name}_6"
        train = frame[(frame["mi"] >= start) & frame[target].notna()]
        p = PairForecaster(features).fit(train, target).predict(now.reset_index())
        chances[name] = pd.Series(p["sage"].to_numpy(), index=now.index)
        chances[name + "_usual"] = pd.Series(p["recent"].to_numpy(), index=now.index)
    fought = fights[fights["deaths"] >= 1].groupby(["a", "b"])["month"].max()
    past = ties.history(raw_dir) if raw_dir is not None else {}
    out = []
    for (a, b), row in now.iterrows():
        last = fought.get((a, b))
        last_fought = pd.Period(last, freq="M").strftime("%B %Y") if isinstance(last, str) and last <= month_name(origin) else None
        war = float(chances["war"][(a, b)])
        clash = max(float(chances["clash"][(a, b)]), war)  # a war is also a clash
        row = row.copy()
        row["a"], row["b"] = a, b
        counted = counted_steps(row)
        out.append({
            "a": int(a), "b": int(b), "names": [names.get(int(a), str(a)), names.get(int(b), str(b))],
            "tension": tension_of(row),
            "clash_6": round(clash, 4), "war_6": round(war, 4),
            "usual_6": round(float(chances["clash_usual"][(a, b)]), 4),
            "raised": {d: [k for k, _ in raised_steps(row, d, counted)] for d in ("ab", "ba")},
            "last_fought": last_fought,
            "summary": describe(row, names, clash, war, last_fought, past.get((int(a), int(b)))),
        })
    order = {"at war": 0, "fighting": 1, "very high": 2, "high": 3, "above usual": 4, "usual": 5}
    out.sort(key=lambda e: (order[e["tension"]], -e["clash_6"]))
    return {"as_of": month_name(origin), "pairs": out}


def for_country(pair_forecasts: dict, country_id: int, limit: int = 3) -> list[dict]:
    """The pairs that matter most for one country, from its side."""
    mine = [p for p in pair_forecasts["pairs"] if country_id in (p["a"], p["b"])]
    keep = [p for p in mine if p["tension"] != "usual" or p["clash_6"] >= 0.01][:limit]
    return [{"with": p["names"][1] if p["a"] == country_id else p["names"][0], **{
        k: p[k] for k in ("tension", "clash_6", "war_6", "usual_6", "last_fought", "summary")}} for p in keep]
