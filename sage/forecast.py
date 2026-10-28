"""Today's forecasts: every country, every question, the next three months.

Writes forecasts/latest.json for the app and website, and keeps a copy
of each month's forecasts in forecasts/history/ that is never changed
afterwards, so the live track record can't be quietly edited.
"""

from __future__ import annotations

import json
from datetime import date, datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd

from . import summary, tension, world
from .backtest import HORIZONS
from .features import FIRST_TRAINING_MONTH, NEVER, QUESTIONS, Question, feature_columns, month_index, month_name
from .gdelt import EARLY_DAYS
from .models import Forecaster
from .panel import SERIES

KILLED = {
    "state": "killed in fighting involving the government",
    "group_civ": "civilians killed by armed groups",
}
NEARBY_WORTH_MENTIONING = 100


def _month(m: int) -> str:
    return pd.Period(month_name(m), freq="M").strftime("%B %Y")


# News signals worth a sentence, per question: (feature, what it counts).
NEWS_REASONS = {
    "state": [("early_actor_mobilize_surge", "troop mobilisations and military alerts"),
              ("early_where_threaten_force_surge", "threats of military force"),
              ("early_where_heavy_weapons_surge", "shelling and air strikes"),
              ("early_where_fight_surge", "fighting")],
    "group_civ": [("early_where_bombing_surge", "bombings"),
                  ("early_where_assault_surge", "attacks"),
                  ("early_where_mass_violence_surge", "mass killings")],
}


def _news_reasons(q: Question, row: pd.Series) -> list[str]:
    """The strongest news surge this month so far, if one stands out."""
    best = None
    for feature, label in NEWS_REASONS[q.series]:
        value = row.get(feature)
        if value is None or not np.isfinite(value):
            continue
        times = float(np.exp(value))
        if times >= 2 and (best is None or times > best[0]):
            best = (times, label)
    if best is None:
        return []
    return [f"News reports of {best[1]} this month: about {best[0]:.0f}× the past year's usual"]


def _reasons(q: Question, row: pd.Series, history: pd.DataFrame, last: int) -> list[str]:
    """Two or three short facts behind a forecast, from the numbers."""
    deaths = KILLED[q.series]
    v = int(row[q.series])
    run, since = int(row[f"run_{q.key}"]), int(row[f"since_{q.key}"])
    why = []
    if v >= q.threshold:
        streak = f", {run} months in a row at this level" if run > 1 and run < NEVER else ""
        why.append(f"{v:,} {deaths} in {_month(last)}{streak}")
    elif v > 0:
        why.append(f"{v:,} {deaths} in {_month(last)}, below the {q.threshold:,} mark")
    elif since >= NEVER:
        why.append(f"No month with {q.threshold:,} or more {deaths} in the last 20 years")
    else:
        why.append(f"None in {_month(last)}; the last month at this level was {_month(last - since)}")

    recent = history[q.series].to_numpy()
    last3, before3 = recent[-3:].sum(), recent[-6:-3].sum()
    if max(last3, before3) >= q.threshold and before3 > 0:
        change = last3 / before3 - 1
        if change >= 1:
            why.append(f"The last three months: {last3 / before3:.0f}× the three before")
        elif abs(change) >= 0.25:
            word = "Up" if change > 0 else "Down"
            why.append(f"{word} {abs(change):.0%} on the three months before")
    elif last3 >= q.threshold and before3 == 0:
        why.append("Started in the last three months after a quiet spell")

    why += _news_reasons(q, row)

    near_col = f"near_{q.series}_last3"
    if near_col in row:
        nearby = int(round(float(np.expm1(row[near_col]))))
        if nearby >= NEARBY_WORTH_MENTIONING:
            why.append(f"{nearby:,} {deaths} in nearby countries over the last three months")
    return why


def _alert_reason(alert: dict) -> str:
    who = ("other countries' forces massing against it" if alert.get("kind") == "aimed"
           else "its forces mobilising, shows of force and threats of force")
    since = pd.Timestamp(alert["since"]).strftime("%-d %B %Y")
    return f"Build-up alert since {since}: news of {who} up to about {alert['times_usual']:.0f}× usual"


def make(frame: pd.DataFrame, countries: pd.DataFrame, record: dict | None = None,
         alerts_now: dict[int, dict] | None = None, alert_record: dict | None = None,
         pair_forecasts: dict | None = None, iso3: dict[int, str] | None = None) -> dict:
    """Forecasts for every country. `alerts_now` and `alert_record` are the
    daily build-up alerts on today and their track record (daily.py);
    `pair_forecasts` the forecasts between countries (tension.py); `iso3`
    each country's ISO code, so an app can find the country it's in."""
    features = feature_columns(frame)
    last = int(frame["mi"].max())
    start = int(month_index(pd.Series([FIRST_TRAINING_MONTH])).iloc[0])
    now = frame[frame["mi"] == last].set_index("country_id")
    meta = countries.set_index("country_id")

    chances: dict[str, pd.DataFrame] = {}
    usual: dict[str, pd.DataFrame] = {}
    for q in QUESTIONS.values():
        cols, plain = {}, {}
        for h in HORIZONS:
            target = f"y_{q.key}_{h}"
            train = frame[(frame["mi"] >= start) & frame[target].notna()]
            predicted = Forecaster(q, features).fit(train, target).predict(now)
            cols[h], plain[h] = predicted["sage"], predicted["last_month"]
        chances[q.key] = pd.DataFrame(cols)
        usual[q.key] = pd.DataFrame(plain)
    # 1 = the highest chance of any country, for each question and month.
    ranks = {k: v.rank(ascending=False, method="min").astype(int) for k, v in chances.items()}

    out = []
    for country_id, row in now.iterrows():
        history = frame[(frame["country_id"] == country_id) & (frame["mi"] > last - 6)]
        entry = {
            "id": int(country_id),
            "name": str(meta.at[country_id, "name"]),
            "region": str(meta.at[country_id, "region"]),
            "last_month": {s: int(row[s]) for s in SERIES},
            "alert": (alerts_now or {}).get(int(country_id), {"on": False}),
        }
        for q in QUESTIONS.values():
            entry[q.key] = {
                "chance": [round(float(chances[q.key].at[country_id, h]), 4) for h in HORIZONS],
                # How often it happens next to countries in the same state this
                # month, so "10% where 1% is usual" can be told apart from
                # "10% where 10% is usual".
                "usual": [round(float(usual[q.key].at[country_id, h]), 4) for h in HORIZONS],
                "rank": [int(ranks[q.key].at[country_id, h]) for h in HORIZONS],
                "why": ([_alert_reason(entry["alert"])] if entry["alert"]["on"] and q.series == "state" else [])
                       + _reasons(q, row, history, last),
            }
        entry["relations"] = tension.for_country(pair_forecasts, int(country_id)) if pair_forecasts else []
        trend, news = summary.leaning_inputs(row, history["state"].to_numpy(), NEWS_REASONS["state"])
        entry["summary"] = summary.country(entry, month_name(last), trend, news, alert_record)
        out.append(entry)
    out.sort(key=lambda e: e["name"])

    next_month = month_name(last + HORIZONS[world.NEXT])
    threat = world.threat(frame, out, next_month, pd.Period(next_month, freq="M").strftime("%B"))
    return {
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "data_through": month_name(last),
        "months": [month_name(last + h) for h in HORIZONS],
        "questions": {q.key: {"title": q.title, "text": q.text} for q in QUESTIONS.values()},
        "world": threat,
        "summary": [threat["text"], *summary.world(out, month_name(last), pair_forecasts)],
        "pairs": pair_forecasts,
        "record": record,
        "alert_record": alert_record,
        "countries": out,
        "iso3": _codes(out, pair_forecasts, iso3 or {}),
    }


def _codes(entries: list[dict], pair_forecasts: dict | None, iso3: dict[int, str]) -> dict[str, str]:
    """ISO 3166 alpha-3 codes for every country in the file, by id."""
    ids = {e["id"] for e in entries}
    for p in (pair_forecasts or {}).get("pairs", []):
        ids |= {p["a"], p["b"]}
    return {str(i): iso3[i] for i in sorted(ids) if i in iso3}


def track_record(backtest_json: Path) -> dict | None:
    """The backtest's headline numbers, to show next to each forecast."""
    if not backtest_json.exists():
        return None
    data = json.loads(backtest_json.read_text())
    return {
        "source": "backtest",
        "from": data["forecasts_made_from"],
        "to": data["forecasts_made_to"],
        "scores": {
            s["question"] + f"_{s['ahead']}": {
                "brier": round(s["brier"]["sage"], 5),
                "better_than_last_month": round(s["skill_vs_last_month"], 3),
                "auc": None if s["auc"]["sage"] is None else round(s["auc"]["sage"], 3),
            }
            for s in data["results"]
        },
    }


def news_complete(month: str, today: date | None = None) -> bool:
    """Is the first fortnight of `month` (the news part of a forecast) over?"""
    today = today or date.today()
    year, mon = int(month[:4]), int(month[5:7])
    return today > date(year, mon, EARLY_DAYS)


def save(forecast: dict, out_dir: Path, keep: bool = True, force: bool = False) -> list[Path]:
    """Write latest.json, and with `keep` the month's history file if it
    isn't there yet (`force` replaces it)."""
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "history").mkdir(exist_ok=True)
    text = json.dumps(forecast, indent=1, ensure_ascii=False)
    written = [out_dir / "latest.json"]
    written[0].write_text(text)
    kept = out_dir / "history" / f"{forecast['data_through']}.json"
    if keep and (force or not kept.exists()):
        kept.write_text(text)
        written.append(kept)
    return written
