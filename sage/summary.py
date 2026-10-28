"""Plain-English summaries: a few sentences per country saying where
things stand and which way they're leaning, and a short world overview.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from .features import month_index, month_name

NEXT = 1  # index of "next month" in the three forecast months


def word(p: float) -> str:
    """Words for a chance, roughly as intelligence analysts use them."""
    if p >= 0.95:
        return "almost certain"
    if p >= 0.8:
        return "very likely"
    if p >= 0.6:
        return "likely"
    if p >= 0.4:
        return "about as likely as not"
    if p >= 0.2:
        return "a real possibility"
    if p >= 0.05:
        return "unlikely"
    if p >= 0.01:
        return "very unlikely"
    return "extremely unlikely"


def pct(p: float) -> str:
    if p >= 0.995:
        return "over 99%"
    if p < 0.005:
        return "under 1%"
    return f"{p:.0%}"


def _month(m: str) -> str:
    return pd.Period(m, freq="M").strftime("%B")


def _versus_usual(p: float, usual: float) -> str:
    if usual <= 0 or p < 0.03:
        return ""
    ratio = p / usual
    if ratio >= 2:
        return f", about {ratio:.0f} times what's usual for a country in its situation"
    if ratio <= 0.5:
        return ", lower than usual for a country in its situation"
    return ""


def _leaning(trend: float | None, news: tuple[float, str] | None) -> str:
    """Which way things are moving, from the death toll and the news."""
    worse, calmer = [], []
    if trend is not None and abs(trend) >= 0.25:
        if trend > 0:
            worse.append(f"deaths are up {trend:.0%} on the three months before" if trend < 1
                         else f"deaths are {trend + 1:.0f} times the three months before")
        else:
            calmer.append(f"deaths are down {-trend:.0%} on the three months before")
    if news is not None:
        worse.append(f"the news shows {news[1]} at about {news[0]:.0f} times the usual level")
    if worse and not calmer:
        return "It's leaning worse: " + " and ".join(worse) + "."
    if calmer and not worse:
        return "It's leaning calmer: " + " and ".join(calmer) + "."
    if worse and calmer:
        return "The signs are mixed: " + calmer[0] + ", but " + worse[0] + "."
    return ""


def country(entry: dict, data_month: str, trend: float | None, news: tuple[float, str] | None,
            alert_record: dict | None) -> str:
    """Two to five sentences on one country."""
    name = entry["name"]
    last = entry["last_month"]
    month = _month(data_month)
    nxt = _month(month_name(month_index(pd.Series([data_month])).iloc[0] + 2))
    fight, war, attacks = entry["fighting"], entry["major_war"], entry["attacks"]
    s = []

    state, group = last["state"], last["group_civ"]
    if state >= 1000:
        s.append(f"{name} is at full-scale war: {state:,} people were killed in fighting involving "
                 f"the government in {month}.")
    elif state >= 25:
        s.append(f"Fighting is going on in {name}: {state:,} people were killed in fighting involving "
                 f"the government in {month}.")
    elif state > 0:
        s.append(f"{name} saw some armed clashes in {month} ({state} killed), below the level SAGE "
                 f"counts as fighting.")
    elif group >= 10:
        s.append(f"There was no fighting involving the government in {name} in {month}, but armed "
                 f"groups killed {group:,} civilians.")
    else:
        s.append(f"{name} was largely calm in {month}.")

    p_fight, p_war = fight["chance"][NEXT], war["chance"][NEXT]
    if state >= 1000:
        s.append(f"A war on this scale continuing into {nxt} is {word(p_war)} ({pct(p_war)}).")
    elif state >= 25:
        line = f"More deadly fighting in {nxt} is {word(p_fight)} ({pct(p_fight)})"
        if p_war >= 0.05:
            line += f", and escalation to full-scale war is {word(p_war)} ({pct(p_war)})"
        s.append(line + ".")
    else:
        s.append(f"Deadly fighting in {nxt} is {word(p_fight)} ({pct(p_fight)})"
                 f"{_versus_usual(p_fight, fight['usual'][NEXT])}.")

    lean = _leaning(trend, news)
    if lean:
        s.append(lean)

    p_att = attacks["chance"][NEXT]
    if group >= 10 or p_att >= 0.2:
        s.append(f"Armed groups killing 10 or more civilians in {nxt} is {word(p_att)} ({pct(p_att)}).")

    for rel in (entry.get("relations") or [])[:2]:
        if rel["tension"] in ("at war", "fighting"):
            continue  # already covered by the fighting above
        s.append(f"Tension with {rel['with']} is {rel['tension']}; deadly clashes between their forces in "
                 f"the next six months are {word(rel['clash_6'])} ({pct(rel['clash_6'])}).")

    alert = entry.get("alert") or {}
    if alert.get("on"):
        who = ("other countries' forces massing against it" if alert.get("kind") == "aimed"
               else "its forces mobilising and threats of force")
        since = pd.Timestamp(alert["since"]).strftime("%-d %B")
        line = (f"Warning: news of {who} has been far above usual since {since} "
                f"(up to about {alert['times_usual']:.0f} times).")
        # The record that fits: fighting starting, or fighting turning into full-scale war.
        key, what = ("fighting", "deadly fighting followed") if state < 25 else (
            ("major_war", "it escalated to full-scale war") if state < 1000 else (None, None))
        rec = (alert_record or {}).get("questions", {}).get(key) if key else None
        if rec and rec["alerts"]:
            line += (f" After past alerts like this, {what} within three months "
                     f"in {rec['followed']} of {rec['alerts']} cases.")
        s.append(line)
    return " ".join(s)


def world(entries: list[dict], data_month: str, pair_forecasts: dict | None = None) -> list[str]:
    """A few lines for the top of the app."""
    nxt = _month(month_name(month_index(pd.Series([data_month])).iloc[0] + 2))
    lines = []
    wars = sorted(entries, key=lambda e: -e["major_war"]["chance"][NEXT])
    top = [e for e in wars if e["major_war"]["chance"][NEXT] >= 0.2][:5]
    if top:
        lines.append(f"Most likely to see full-scale war in {nxt}: "
                     + ", ".join(f"{e['name']} ({pct(e['major_war']['chance'][NEXT])})" for e in top) + ".")
    calm = [e for e in entries if e["last_month"]["state"] < 25]
    rising = sorted(calm, key=lambda e: -e["fighting"]["chance"][NEXT])[:3]
    rising = [e for e in rising if e["fighting"]["chance"][NEXT] >= 0.05]
    if rising:
        lines.append(f"Among countries below the fighting mark last month, the highest chance of deadly "
                     f"fighting in {nxt}: "
                     + ", ".join(f"{e['name']} ({pct(e['fighting']['chance'][NEXT])})" for e in rising) + ".")
    if pair_forecasts:
        tense = [p for p in pair_forecasts["pairs"] if p["tension"] in ("very high", "high")][:4]
        if tense:
            lines.append("Most tense between countries not already fighting: "
                         + ", ".join(f"{p['names'][0]}–{p['names'][1]}" for p in tense) + ".")
        risky = sorted([p for p in pair_forecasts["pairs"] if p["tension"] not in ("at war", "fighting")],
                       key=lambda p: -p["clash_6"])[:3]
        risky = [p for p in risky if p["clash_6"] >= 0.02]
        if risky:
            lines.append("Highest chance of new clashes between countries in the next six months: "
                         + ", ".join(f"{p['names'][0]}–{p['names'][1]} ({pct(p['clash_6'])})" for p in risky) + ".")
    alerts = [e for e in entries if (e.get("alert") or {}).get("on")]
    if alerts:
        lines.append("Build-up alerts: " + ", ".join(e["name"] for e in alerts) + ".")
    else:
        lines.append("No build-up alerts right now: no country shows the pattern of news that came "
                     "before past wars.")
    return lines


def leaning_inputs(row: pd.Series, recent_state: np.ndarray, news_signs: list[tuple[str, str]]):
    """The trend and strongest news surge behind a summary."""
    last3, before3 = recent_state[-3:].sum(), recent_state[-6:-3].sum()
    trend = (last3 / before3 - 1) if before3 > 0 and max(last3, before3) >= 25 else None
    news = None
    for feature, label in news_signs:
        value = row.get(feature)
        if value is not None and np.isfinite(value) and np.exp(value) >= 2:
            if news is None or np.exp(value) > news[0]:
                news = (float(np.exp(value)), label)
    return trend, news
