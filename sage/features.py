"""The questions SAGE answers, and what it knows about each country-month.

Everything computed for month t uses only months up to t, so a forecast
made at the end of t never sees what came after.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

from .panel import COUNTS, SERIES

FIRST_TRAINING_MONTH = "1992-01"  # the 36-month windows are full from here
NEARBY_KM = 1000
NEVER = 240  # "months since" for something not seen in the last 20 years
REGIONS = ["Africa", "Americas", "Asia", "Europe", "Middle East"]


@dataclass(frozen=True)
class Question:
    key: str
    series: str
    threshold: int
    title: str
    text: str


QUESTIONS = {
    q.key: q for q in [
        Question("fighting", "state", 25, "Deadly fighting",
                 "At least 25 people killed in fighting involving the government"),
        Question("major_war", "state", 1000, "Major war",
                 "At least 1,000 people killed in fighting involving the government"),
        Question("attacks", "group_civ", 10, "Attacks on civilians",
                 "Armed groups kill at least 10 civilians"),
    ]
}


def month_index(months: pd.Series) -> pd.Series:
    """'2026-09' -> a running month number, so months can be added and compared."""
    return months.str[:4].astype(int) * 12 + months.str[5:7].astype(int) - 1


def month_name(index: int) -> str:
    return f"{index // 12:04d}-{index % 12 + 1:02d}"


def _months_since(frame: pd.DataFrame, hit: pd.Series) -> pd.Series:
    last = frame["mi"].where(hit).groupby(frame["country_id"]).ffill()
    return (frame["mi"] - last).fillna(NEVER).clip(upper=NEVER).astype(float)


def _nearby(frame: pd.DataFrame, countries: pd.DataFrame, values: pd.Series) -> pd.Series:
    """For each country-month, the sum of `values` in other countries
    whose violence centres lie within NEARBY_KM."""
    c = countries.set_index("country_id").loc[sorted(frame["country_id"].unique())]
    lat, lon = np.radians(c["lat"].to_numpy()), np.radians(c["lon"].to_numpy())
    dlat = lat[:, None] - lat[None, :]
    dlon = lon[:, None] - lon[None, :]
    a = np.sin(dlat / 2) ** 2 + np.cos(lat[:, None]) * np.cos(lat[None, :]) * np.sin(dlon / 2) ** 2
    km = 2 * 6371 * np.arcsin(np.sqrt(np.clip(a, 0, 1)))
    near = ((km < NEARBY_KM) & (km > 0)).astype(float)
    np.fill_diagonal(near, 0)
    grid = values.groupby([frame["mi"], frame["country_id"]]).sum().unstack(fill_value=0)
    grid = grid.reindex(columns=c.index, fill_value=0)
    summed = pd.DataFrame(grid.to_numpy() @ near.T, index=grid.index, columns=grid.columns)
    stacked = summed.stack()
    return pd.Series(stacked.reindex(pd.MultiIndex.from_arrays([frame["mi"], frame["country_id"]])).to_numpy(),
                     index=frame.index)


# News signals (see gdelt.py): kinds of report, where they happened and
# which countries' forces were involved.
NEWS = {
    "where": ["all", "material_conflict", "verbal_conflict", "protest", "threaten",
              "threaten_force", "force_posture", "mobilize", "reduce_relations", "coerce",
              "assault", "bombing", "fight", "heavy_weapons", "mass_violence"],
    "actor": ["all", "threaten", "threaten_force", "force_posture", "mobilize",
              "reduce_relations", "fight"],
    # Other countries' actions aimed at this one.
    "target": ["all", "threaten", "threaten_force", "force_posture", "mobilize",
               "reduce_relations", "assault", "fight", "heavy_weapons"],
}
NEWS_MENTIONS = ["all", "threaten_force", "force_posture", "mobilize", "fight", "assault"]
BUILDUP = ["news_actor_mobilize_surge", "news_where_force_posture_surge",
           "news_actor_threaten_force_surge", "early_actor_mobilize_surge"]
# The same, but by other countries against this one.
BUILDUP_AIMED = ["news_target_mobilize_surge", "news_target_force_posture_surge",
                 "news_target_threaten_force_surge", "early_target_mobilize_surge"]
# A build-up alert: each of the four signs at least e^1 ≈ 2.7 times the
# country's usual (see alerts.py).
ALERT_LEVEL = 1.0
# Features where more can only mean more risk (see models.py).
RISING = ["buildup"]
# The first fortnight of the month in progress, known when a forecast is made.
EARLY = ["all", "material_conflict", "threaten_force", "force_posture", "mobilize",
         "reduce_relations", "assault", "bombing", "fight", "heavy_weapons", "mass_violence"]


def _news_features(frame: pd.DataFrame, news: pd.DataFrame, early: pd.DataFrame | None) -> dict[str, pd.Series]:
    """How much of each kind of report, compared with the country's own
    past year and with the rest of its news. Empty before GDELT (2013)."""
    keys = ["country_id", "month"]

    def attach(table: pd.DataFrame, months: pd.Series) -> pd.DataFrame:
        # A country with no reports in a month GDELT covers had zero, not "unknown".
        rows = pd.DataFrame({"country_id": frame["country_id"], "month": months}).merge(table, on=keys, how="left")
        covered = table.groupby("month")[["days", "world_all"]].first()
        known = rows["month"].isin(covered.index)
        counts = [c for c in table.columns if c not in (*keys, "days", "world_all")]
        rows.loc[known, counts] = rows.loc[known, counts].fillna(0)
        for c in ("days", "world_all"):
            rows.loc[known, c] = rows.loc[known, "month"].map(covered[c])
        return rows

    month = attach(news, frame["month"])
    out: dict[str, pd.Series] = {}
    by_country = frame["country_id"]

    def per_day(table: pd.DataFrame, column: str) -> pd.Series:
        return table[column] / table["days"]

    def past_year(rate: pd.Series) -> pd.Series:
        return rate.groupby(by_country).transform(lambda s: s.shift(1).rolling(12, min_periods=3).mean())

    columns = [(kind, cat, f"{kind}_{cat}") for kind, cats in NEWS.items() for cat in cats]
    columns += [(kind, f"mentions_{m}", f"{kind}_mentions_{m}") for kind in NEWS for m in NEWS_MENTIONS]
    for kind, cat, col in columns:
        rate = per_day(month, col)
        out[f"news_{col}_surge"] = np.log1p(rate) - np.log1p(past_year(rate))
        if cat != "all":
            out[f"news_{col}_share"] = month[col] / month[f"{kind}_all"].replace(0, np.nan)
    for kind in NEWS:
        out[f"news_{kind}_world_share"] = month[f"{kind}_all"] / month["world_all"]
        out[f"news_{kind}_tone"] = month[f"{kind}_tone_sum"] / month[f"{kind}_all"].replace(0, np.nan)

    if early is not None:
        soon = attach(early, (frame["mi"] + 1).map(month_name))
        for kind in NEWS:
            for cat in EARLY:
                if cat not in NEWS[kind]:
                    continue
                col = f"{kind}_{cat}"
                rate = per_day(month, col)
                year = rate.groupby(by_country).transform(lambda s: s.rolling(12, min_periods=3).mean())
                out[f"early_{col}_surge"] = np.log1p(per_day(soon, col)) - np.log1p(year)
                if cat != "all":
                    out[f"early_{col}_share"] = soon[col] / soon[f"{kind}_all"].replace(0, np.nan)
    return {k: pd.Series(v.to_numpy(), index=frame.index) for k, v in out.items()}


def _views(frame: pd.DataFrame, views: pd.DataFrame) -> dict[str, pd.Series]:
    """ViEWS's chance of deadly fighting for each of the next three months,
    from its run on data to the month before (the one out by the time SAGE
    forecasts), or the run before that if one's missing."""
    table = views.assign(made_mi=month_index(views["made"]), month_mi=month_index(views["month"]))
    table = table.set_index(["country_id", "made_mi", "month_mi"])["views_fighting"]
    out = {}
    for h in (1, 2, 3):
        found = pd.Series(np.nan, index=frame.index)
        for back in (1, 2):
            keys = pd.MultiIndex.from_arrays([frame["country_id"], frame["mi"] - back, frame["mi"] + h])
            found = found.fillna(pd.Series(table.reindex(keys).to_numpy(), index=frame.index))
        out[f"views_fighting_{h}"] = found
    return out


def build(panel: pd.DataFrame, countries: pd.DataFrame, news: pd.DataFrame | None = None,
          early: pd.DataFrame | None = None, views: pd.DataFrame | None = None) -> pd.DataFrame:
    """One row per country-month: the raw counts, the features and the answers.

    Answer columns are named y_<question>_<months ahead> and are empty
    where that month hasn't happened yet.
    """
    frame = panel.sort_values(["country_id", "month"]).reset_index(drop=True)
    frame["mi"] = month_index(frame["month"])
    by_country = frame.groupby("country_id", sort=False)
    out: dict[str, pd.Series] = {}
    if news is not None:
        out.update(_news_features(frame, news, early))
        # A build-up: several warning signs at once, each measured against
        # the country's own past year. Taking the smallest means one noisy
        # signal on its own can't raise it.
        kinds = []
        for group in (BUILDUP, BUILDUP_AIMED):
            signs = [out[s] for s in group if s in out]
            if signs:
                kinds.append(pd.concat(signs, axis=1).min(axis=1, skipna=False))
        out["buildup"] = pd.concat(kinds, axis=1).max(axis=1)

    raw_sums: dict[tuple[str, int], pd.Series] = {}
    for s in [*SERIES, *COUNTS]:
        x = frame[s].astype(float)
        out[f"{s}_now"] = np.log1p(x)
        for lag in (1, 2):
            out[f"{s}_lag{lag}"] = np.log1p(by_country[s].shift(lag).fillna(0))
        for w in (3, 6, 12, 36):
            total = by_country[s].rolling(w, min_periods=1).sum().reset_index(level=0, drop=True)
            raw_sums[(s, w)] = total
            out[f"{s}_last{w}"] = np.log1p(total)

    # Is it getting worse? The last three months against the three before.
    for s in SERIES:
        before = raw_sums[(s, 6)] - raw_sums[(s, 3)]
        out[f"{s}_trend"] = np.log1p(raw_sums[(s, 3)]) - np.log1p(before)

    for q in QUESTIONS.values():
        hit = frame[q.series] >= q.threshold
        out[f"since_{q.key}"] = _months_since(frame, hit)
        # How many months in a row it has been going on (0 if not now).
        out[f"run_{q.key}"] = _months_since(frame, ~hit)
    out["since_any"] = _months_since(frame, frame["events"] > 0)

    for s in ("state", "nonstate", "group_civ"):
        out[f"near_{s}_last3"] = np.log1p(_nearby(frame, countries, raw_sums[(s, 3)]))

    if views is not None:
        out.update(_views(frame, views))

    region = frame["country_id"].map(countries.set_index("country_id")["region"])
    out["region"] = region.map({r: i for i, r in enumerate(REGIONS)}).fillna(len(REGIONS))

    for q in QUESTIONS.values():
        for h in (1, 2, 3):
            future = by_country[q.series].shift(-h)
            out[f"y_{q.key}_{h}"] = (future >= q.threshold).astype(float).where(future.notna())

    return pd.concat([frame, pd.DataFrame(out, index=frame.index)], axis=1)


def feature_columns(frame: pd.DataFrame) -> list[str]:
    skip = {"country_id", "month", "mi", *SERIES, *COUNTS}
    # Other forecasters' numbers are averaged in afterwards (models.py), not learned from.
    return [c for c in frame.columns if c not in skip and not c.startswith(("y_", "views_"))]
