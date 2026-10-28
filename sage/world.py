"""The global threat level: how dangerous next month looks for the world
as a whole, against every month since 1989.

Adding up SAGE's chances across countries gives how many it expects to
see deadly fighting next month, and how many to be at full-scale war.
Each is compared with how many actually did in every month on record;
the level is the average of the two shares of months that were calmer.
"""

from __future__ import annotations

import pandas as pd

from .features import QUESTIONS

# (level, below this share of calmer months)
LEVELS = [("Low", 0.20), ("Guarded", 0.50), ("Elevated", 0.80), ("High", 0.95), ("Severe", float("inf"))]
NEXT = 1  # index of "next month" in the three forecast months


def _calmer(history: pd.Series, value: float) -> float:
    """Share of months below `value`, counting ties as half."""
    return float(((history < value).sum() + 0.5 * (history == value).sum()) / len(history))


def level_for(score: float) -> tuple[int, str]:
    for rank, (name, below) in enumerate(LEVELS):
        if score < below:
            return rank, name
    return len(LEVELS) - 1, LEVELS[-1][0]


def threat(frame: pd.DataFrame, entries: list[dict], next_month: str, month_word: str) -> dict:
    """The level, the numbers behind it and a sentence saying what it means.
    `frame` holds every country-month on record (its "state" deaths);
    `entries` the forecast for each country."""
    fighting_q, war_q = QUESTIONS["fighting"], QUESTIONS["major_war"]
    by_month = frame.groupby("month")["state"]
    history = pd.DataFrame({
        "fighting": by_month.apply(lambda s: int((s >= fighting_q.threshold).sum())),
        "wars": by_month.apply(lambda s: int((s >= war_q.threshold).sum())),
    })
    expected_fighting = sum(e["fighting"]["chance"][NEXT] for e in entries)
    expected_wars = sum(e["major_war"]["chance"][NEXT] for e in entries)
    score = (_calmer(history["fighting"], expected_fighting) + _calmer(history["wars"], expected_wars)) / 2
    rank, name = level_for(score)
    last = history.iloc[-1]
    alerts = [e["name"] for e in entries if (e.get("alert") or {}).get("on")]

    wars = round(expected_wars)
    war_words = "none to be at full-scale war" if wars == 0 else (
        f"about {wars} to be at full-scale war" if wars > 1 else "about one to be at full-scale war")
    text = (f"Global threat level: {name}. SAGE expects about {round(expected_fighting)} countries to see "
            f"deadly fighting in {month_word} and {war_words}, ")
    if score >= 0.5:
        text += f"more than in {score:.0%} of months since {history.index[0][:4]}."
    else:
        text += f"fewer than in {1 - score:.0%} of months since {history.index[0][:4]}."
    return {
        "level": name,
        "rank": rank,
        "levels": [n for n, _ in LEVELS],
        "score": round(score, 3),
        "month": next_month,
        "expected_fighting": round(expected_fighting, 1),
        "expected_wars": round(expected_wars, 1),
        "last_month": {"month": str(history.index[-1]), "fighting": int(last["fighting"]), "wars": int(last["wars"])},
        "since": str(history.index[0]),
        "alerts": len(alerts),
        "text": text,
    }
