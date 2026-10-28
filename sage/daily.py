"""Build-up alerts, checked every day.

Each day, for each country, SAGE compares the last 14 days of news with
the country's usual over the twelve months before, on three warning
signs at once:

  its own forces:       reports of them mobilising, shows of military
                        force in the country, and threats of force by it;
  forces aimed at it:   other countries mobilising against it, showing
                        force at it, and threatening it.

Both kinds are measured, but alerts go out only for forces aimed at a
country: tested since 2014, alerts on a country's own forces went off
mostly after terror attacks (police and soldiers on the streets) and said
much less about war to come (see the report). An alert goes out when all
three signs are at least about 2.7 times usual. Back-to-back alert days
are one episode, checked against what UCDP recorded from its start until
90 days after it ended.
"""

from __future__ import annotations

import json
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

from . import codes
from .features import ALERT_LEVEL, QUESTIONS, month_index

WINDOW = 14       # days of news compared with the usual
GAP = 7           # alert days closer than this are one episode
FOLLOW = 90       # days after an alert in which outcomes are checked
STRETCH = 30      # outcomes count deaths over any 30 days, like a month
FIRST = "2014-04-01"  # a year after the daily news begins
ALERTS = "aimed"      # the kind of build-up that raises a public alert
SIGNS = {
    "own": ["actor_mobilize", "where_force_posture", "actor_threaten_force"],
    "aimed": ["target_mobilize", "target_force_posture", "target_threaten_force"],
}


def news_days(cache: Path, since: str) -> pd.DataFrame:
    """Daily counts of the warning signs per country, from the cached days."""
    lookup = codes.lookup()
    wanted = {s for signs in SIGNS.values() for s in signs}
    rows = []
    for path in sorted((cache / "days").glob("*/*.csv.gz")):
        stamp = path.name[:8]
        day = f"{stamp[:4]}-{stamp[4:6]}-{stamp[6:8]}"
        if day < since:
            continue
        table = pd.read_csv(path, keep_default_na=False, dtype={"code": str})
        table["country_id"] = [lookup.get(k) for k in zip(table["kind"], table["code"])]
        table = table[table["country_id"].notna()]
        for sign in wanted:
            kind, cat = sign.split("_", 1)
            part = table[table["kind"] == kind].groupby("country_id")[cat].sum()
            rows.append(pd.DataFrame({"country_id": part.index.astype(int), "day": day,
                                      "sign": sign, "count": part.to_numpy()}))
    long = pd.concat(rows, ignore_index=True)
    return long.pivot_table(index=["country_id", "day"], columns="sign", values="count",
                            aggfunc="sum", fill_value=0).reset_index()


def strengths(days: pd.DataFrame, news_month: pd.DataFrame, country_ids) -> pd.DataFrame:
    """For every country and day: how far above usual each kind of build-up
    is (the smallest of its three signs, as log of times usual)."""
    first, last = days["day"].min(), days["day"].max()
    calendar = pd.date_range(first, last, freq="D").strftime("%Y-%m-%d")
    grid = pd.MultiIndex.from_product([sorted(country_ids), calendar], names=["country_id", "day"])
    signs = [s for group in SIGNS.values() for s in group]
    daily = days.set_index(["country_id", "day"]).reindex(grid, fill_value=0)[signs].reset_index()
    recent = (daily.groupby("country_id")[signs].rolling(WINDOW, min_periods=WINDOW).sum()
              .reset_index(level=0, drop=True)) / WINDOW

    # The usual: the twelve full months before the day's month.
    month = news_month[["country_id", "month", "days", *signs]].copy()
    month["mi"] = month_index(month["month"])
    month = month.sort_values(["country_id", "mi"])
    by = month.groupby("country_id")
    usual = pd.DataFrame({"country_id": month["country_id"], "mi": month["mi"]})
    days_12 = by["days"].transform(lambda s: s.shift(1).rolling(12, min_periods=12).sum())
    for s in signs:
        usual[s] = by[s].transform(lambda x: x.shift(1).rolling(12, min_periods=12).sum()) / days_12
    daily["mi"] = month_index(daily["day"].str[:7])
    base = daily[["country_id", "mi"]].merge(usual, on=["country_id", "mi"], how="left")

    out = daily[["country_id", "day"]].copy()
    for kind, group in SIGNS.items():
        surges = [np.log1p(recent[s].to_numpy()) - np.log1p(base[s].to_numpy()) for s in group]
        out[kind] = np.min(np.vstack(surges), axis=0)
    out["strength"] = out[["own", "aimed"]].max(axis=1)
    out["kind"] = np.where(out["aimed"] > out["own"], "aimed", "own")
    return out


def episodes(strength: pd.DataFrame, kind: str = ALERTS) -> pd.DataFrame:
    """Runs of alert days of one kind (allowing gaps under a week), one row each."""
    on = strength[strength[kind] >= ALERT_LEVEL].assign(strength=lambda d: d[kind], kind=kind)
    if on.empty:
        return pd.DataFrame(columns=["country_id", "start", "end", "peak", "kind", "days"])
    on["date"] = pd.to_datetime(on["day"])
    on = on.sort_values(["country_id", "date"])
    new = (on["country_id"] != on["country_id"].shift()) | (on["date"].diff().dt.days > GAP)
    on["episode"] = new.cumsum()
    grouped = on.groupby("episode")
    out = grouped.agg(country_id=("country_id", "first"), start=("day", "first"), end=("day", "last"),
                      peak=("strength", "max"), days=("day", "count"))
    out["kind"] = on.loc[grouped["strength"].idxmax(), "kind"].to_numpy()
    return out.reset_index(drop=True)


def _windows(deaths: pd.DataFrame, series: str, country_ids, first: str, last: str) -> pd.DataFrame:
    """Deaths in the 30 days starting on each day, per country."""
    calendar = pd.date_range(first, last, freq="D").strftime("%Y-%m-%d")
    grid = pd.MultiIndex.from_product([sorted(country_ids), calendar], names=["country_id", "day"])
    daily = deaths.set_index(["country_id", "day"])[series].groupby(level=[0, 1]).sum().reindex(grid, fill_value=0)
    table = daily.unstack("country_id")
    ahead = table[::-1].rolling(STRETCH, min_periods=1).sum()[::-1]
    return ahead


def outcomes(found: pd.DataFrame, deaths: pd.DataFrame, data_through: str) -> pd.DataFrame:
    """For each episode and question: 'yes' if it happened in some 30 days
    between the alert starting and 90 days after it ended, 'no' if not,
    'already' if it had in the 90 days before, 'wait' if that time isn't
    over yet."""
    found = found.copy()
    if found.empty:
        return found
    ids = sorted(set(found["country_id"]) | set(deaths["country_id"]))
    first = (pd.Timestamp(found["start"].min()) - timedelta(days=FOLLOW + 1)).strftime("%Y-%m-%d")
    end_of_data = (pd.Period(data_through, freq="M").end_time).strftime("%Y-%m-%d")
    for q in QUESTIONS.values():
        windows = _windows(deaths, q.series, ids, first, end_of_data)
        results = []
        for e in found.itertuples():
            start = pd.Timestamp(e.start)
            before = windows.loc[(start - timedelta(days=FOLLOW)).strftime("%Y-%m-%d"):
                                 (start - timedelta(days=STRETCH)).strftime("%Y-%m-%d"), e.country_id]
            if (before >= q.threshold).any():
                results.append("already")
                continue
            after_last = pd.Timestamp(e.end) + timedelta(days=FOLLOW - STRETCH + 1)
            if (after_last + timedelta(days=STRETCH - 1)).strftime("%Y-%m-%d") > end_of_data:
                happened = (windows.loc[(start + timedelta(days=1)).strftime("%Y-%m-%d"):, e.country_id]
                            >= q.threshold).any()
                results.append("yes" if happened else "wait")
                continue
            after = windows.loc[(start + timedelta(days=1)).strftime("%Y-%m-%d"):
                                after_last.strftime("%Y-%m-%d"), e.country_id]
            results.append("yes" if (after >= q.threshold).any() else "no")
        found[q.key] = results
    return found


def usual_rates(deaths: pd.DataFrame, country_ids, since: str, data_through: str) -> dict[str, float]:
    """How often each thing happens within 90 days of a random day, where
    it hadn't in the 90 days before: what an alert is compared with."""
    end_of_data = (pd.Period(data_through, freq="M").end_time).strftime("%Y-%m-%d")
    first = (pd.Timestamp(since) - timedelta(days=FOLLOW)).strftime("%Y-%m-%d")
    rates = {}
    for q in QUESTIONS.values():
        w = _windows(deaths, q.series, country_ids, first, end_of_data) >= q.threshold
        hit = w.astype(int)
        # Any qualifying 30 days starting within the next 60 days (so inside 90).
        after = hit[::-1].rolling(FOLLOW - STRETCH + 1, min_periods=1).max()[::-1].shift(-1)
        before = hit.rolling(FOLLOW - STRETCH + 1, min_periods=1).max().shift(STRETCH)
        days = (w.index >= since) & (w.index <= (pd.Timestamp(end_of_data) - timedelta(days=FOLLOW)).strftime("%Y-%m-%d"))
        fresh = (before == 0) & after.notna()
        sample = after[fresh][days]
        rates[q.key] = float(np.nanmean(sample.to_numpy()))
    return rates


def record(found: pd.DataFrame, rates: dict[str, float]) -> dict:
    out = {"level": ALERT_LEVEL, "within_days": FOLLOW, "episodes": int(len(found)), "questions": {}}
    for q in QUESTIONS.values():
        if found.empty:
            continue
        judged = found[found[q.key].isin(["yes", "no"])]
        followed = int((judged[q.key] == "yes").sum())
        out["questions"][q.key] = {
            "alerts": int(len(judged)), "followed": followed,
            "rate": followed / len(judged) if len(judged) else None, "usual": rates.get(q.key),
        }
    return out


def update(data_dir: Path, cache: Path, news_month: pd.DataFrame, country_ids) -> str:
    """Check every day the cache has a full 14 days for, and store the
    alert days in data/alert_days.csv (days already stored are rechecked).
    Returns the last day checked."""
    days = news_days(cache, since="2013-04-01")
    found = strengths(days, news_month, country_ids)
    found = found[found["strength"].notna()]
    checked = set(found["day"])
    path = data_dir / "alert_days.csv"
    stored = pd.read_csv(path, dtype={"day": str}) if path.exists() else pd.DataFrame(
        columns=["country_id", "day", "own", "aimed"])
    kept = stored[~stored["day"].isin(checked)]
    either = (found["own"] >= ALERT_LEVEL) | (found["aimed"] >= ALERT_LEVEL)
    new = found.loc[either, ["country_id", "day", "own", "aimed"]]
    table = pd.concat([kept, new], ignore_index=True).sort_values(["country_id", "day"])
    table[["own", "aimed"]] = table[["own", "aimed"]].astype(float)
    table.to_csv(path, index=False, float_format="%.4f")
    last = max(checked | {(_checked_through(data_dir) or "")})
    (data_dir / "alerts.json").write_text(json.dumps({"checked_through": last}))
    return last


def _checked_through(data_dir: Path) -> str | None:
    path = data_dir / "alerts.json"
    return json.loads(path.read_text())["checked_through"] if path.exists() else None


def load(data_dir: Path, deaths: pd.DataFrame | None, data_through: str):
    """Alert episodes with outcomes, their record (with the own-forces
    version alongside for comparison), and the alerts on now."""
    path = data_dir / "alert_days.csv"
    if not path.exists() or deaths is None:
        return pd.DataFrame(), None, {}
    alert_days = pd.read_csv(path, dtype={"day": str})
    last = _checked_through(data_dir)
    ids = sorted(set(deaths["country_id"]))
    rates = usual_rates(deaths, ids, FIRST, data_through)
    found = outcomes(episodes(alert_days, ALERTS), deaths, data_through)
    rec = record(found, rates)
    rec["own_forces"] = record(outcomes(episodes(alert_days, "own"), deaths, data_through), rates)
    # Still on if it fired in the last week (closer days are one alert).
    recent = (pd.Timestamp(last) - timedelta(days=GAP)).strftime("%Y-%m-%d") if last else ""
    now = {}
    for e in found[found["end"] >= recent].itertuples() if len(found) and last else []:
        now[int(e.country_id)] = {"on": True, "since": e.start, "last": e.end, "kind": e.kind,
                                  "times_usual": round(float(np.exp(e.peak)), 1)}
    rec["checked_through"] = last
    return found, rec, now
