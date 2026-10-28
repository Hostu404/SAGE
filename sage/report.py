"""Writes the backtest up in plain English, with a chart."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

from .backtest import NEW_AFTER  # noqa: E402
from .features import QUESTIONS, month_index, month_name  # noqa: E402

# Forecasts are made mid-month, when the previous month's conflict data is
# out: 1 = the month in progress, 2 = next month, 3 = the month after.
HEADLINE_AHEAD = 2
# Wars that started suddenly, as a reality check.
FAMOUS = [
    (369, "2022-02-24", "Russia's full invasion of Ukraine"),
    (666, "2023-10-07", "Hamas's 7 October attack and the Gaza war"),
    (625, "2023-04-15", "Sudan's civil war"),
]


def _pct(x: float | None, digits: int = 0) -> str:
    return "–" if x is None else f"{x * 100:.{digits}f}%"


def _when(m: int) -> str:
    return pd.Period(month_name(m), freq="M").strftime("%b %Y")


def _misses(results: pd.DataFrame, countries: pd.DataFrame, key: str, limit: int) -> pd.DataFrame:
    """Outbreaks after a calm year that SAGE rated least likely."""
    r = results[(results["question"] == key) & (results["ahead"] == HEADLINE_AHEAD)
                & (results["happened"] == 1) & (results["since"] >= NEW_AFTER)]
    r = r.sort_values("sage").drop_duplicates("country_id").head(limit)
    return r.assign(name=r["country_id"].map(countries.set_index("country_id")["name"]))


def chart(summary: list[dict], path: Path) -> None:
    rows = [s for s in summary if s["ahead"] == HEADLINE_AHEAD]
    fig, axes = plt.subplots(1, len(rows), figsize=(4.2 * len(rows), 4.4), dpi=150)
    for ax, s in zip(axes, rows):
        cal = pd.DataFrame(s["calibration"])
        ax.plot([0, 1], [0, 1], color="#bbbbbb", lw=1, zorder=1)
        ax.scatter(cal["said"], cal["happened"], s=12 + cal["forecasts"] ** 0.5 * 4,
                   color="#1f4e79", zorder=2)
        ax.plot(cal["said"], cal["happened"], color="#1f4e79", lw=1, zorder=2)
        ax.set_title(s["title"], fontsize=11)
        ax.set_xlim(0, 1)
        ax.set_ylim(0, 1)
        ax.set_xlabel("SAGE said")
        ax.grid(color="#eeeeee", lw=0.6)
        ax.set_axisbelow(True)
        for side in ("top", "right"):
            ax.spines[side].set_visible(False)
    axes[0].set_ylabel("How often it happened")
    fig.suptitle("Forecasts for next month: on the grey line = honest percentages",
                 fontsize=10, color="#444444")
    fig.tight_layout()
    fig.savefig(path)
    plt.close(fig)


def _day(d: str) -> str:
    return pd.Timestamp(d).strftime("%-d %b %Y")


# Clashes and wars between countries, as a reality check for the pair
# forecasts: (lower GW number, higher, month fighting began, label).
PAIR_CASES = [
    (365, 369, "2014-03", "Russia–Ukraine, Crimea and Donbas"),
    (710, 750, "2020-06", "China–India, Galwan valley"),
    (750, 770, "2019-02", "India–Pakistan, Pulwama and Balakot"),
    (365, 369, "2022-02", "Russia–Ukraine, full invasion"),
    (630, 666, "2024-04", "Iran–Israel, first direct strikes"),
    (750, 770, "2025-05", "India–Pakistan, Operation Sindoor"),
    (800, 811, "2025-07", "Thailand–Cambodia border war"),
    (2, 101, "2026-01", "United States–Venezuela"),
]


def _pairs_section(pair_results: pd.DataFrame, names: dict[int, str]) -> tuple[list[str], list[dict]]:
    from . import tension
    summary = tension.summarize(pair_results)
    by = {(s["question"], s["within"]): s for s in summary}
    lines = [
        "## Between countries",
        "",
        "For every pair of countries with real friction between them, SAGE reads each side's actions "
        "towards the other in the news (criticism, sanctions, threats, shows of force, mobilisation, "
        "fighting), compares them with the pair's usual, adds whether they share a border and their record "
        "of deadly disputes since 1816 (Correlates of War), and forecasts whether their forces will clash "
        "(at least one death in fighting between the two governments, UCDP) or go to war (1,000 or more "
        "deaths in a month) within the next 3 or 6 months. Replayed every month from 2008, using only what "
        "was known at the time.",
        "",
        "| | Pair-months | It happened | Error: SAGE | Error: \"clashed recently?\" | Ranking (AUC) |",
        "|---|---|---|---|---|---|",
    ]
    for (q, w), s in by.items():
        title = tension.QUESTIONS[q][1]
        lines.append(f"| {title}, within {w} months | {s['forecasts']:,} | {s['happened']:,} | "
                     f"{s['brier']['sage']:.5f} | {s['brier']['recent']:.5f} | {_pct(s['auc']['sage'], 1)} |")
    s = by.get(("clash", 6))
    if s:
        fresh = s["fresh"]
        lines += [
            "",
            f"\"Clashed recently?\" says pairs that fought in the last six months will fight again, and others "
            f"won't: the forecast to beat. For **new clashes** (pairs that hadn't fought for a year or more), "
            f"{fresh['happened']} of {fresh['forecasts']:,} pair-months saw fighting within six months; SAGE "
            f"rated those at {_pct(fresh['mean_when_happened'], 1)} on average, against "
            f"{_pct(fresh['mean_when_calm'], 2)} for pairs that stayed calm (ranking {_pct(fresh['auc'], 0)}).",
            "",
            "When SAGE said… (deadly clashes within 6 months)",
            "",
            "| SAGE said | Pair-months | It happened |",
            "|---|---|---|",
        ]
        for c in s["calibration"]:
            lines.append(f"| {_pct(c['from'])}–{_pct(c['to'])} (avg {_pct(c['said'], 1)}) | {c['forecasts']:,} | "
                         f"{_pct(c['happened'], 1)} |")
    lines += ["", "Before well-known clashes: the chance SAGE gave of deadly clashes within six months, the "
              "month before and three months before, and where that ranked among all the pairs it follows "
              "(1 = the highest in the world that month):", "",
              "| Clash | Began | A month before | Three months before |", "|---|---|---|---|"]
    r = pair_results[(pair_results["question"] == "clash") & (pair_results["within"] == 6)]
    for a, b, month, label in PAIR_CASES:
        target = int(month_index(pd.Series([month])).iloc[0])
        cells = []
        for back in (1, 3):
            made = r[r["made"] == target - back]
            hit = made[(made["a"] == a) & (made["b"] == b)]
            if len(hit):
                p = float(hit["sage"].iloc[0])
                rank = int((made["sage"] > p).sum()) + 1
                cells.append(f"{_pct(p, 1)} (#{rank} of {len(made)})")
            else:
                cells.append("–")
        lines.append(f"| {label} | {pd.Period(month, freq='M').strftime('%b %Y')} | {cells[0]} | {cells[1]} |")
    lines.append("")
    return lines, summary


def write(results: pd.DataFrame, summary: list[dict], countries: pd.DataFrame,
          data_through: str, out_dir: Path, episodes: pd.DataFrame | None = None,
          alert_record: dict | None = None, pair_results: pd.DataFrame | None = None,
          names: dict[int, str] | None = None) -> None:
    """Write reports/backtest.md, backtest.json and the chart. `episodes`
    and `alert_record` are the daily build-up alerts (daily.py);
    `pair_results` the backtest between countries (tension.py)."""
    out_dir.mkdir(parents=True, exist_ok=True)
    episodes = episodes if episodes is not None else pd.DataFrame()
    country_names = countries.set_index("country_id")["name"]
    first, last = int(results["made"].min()), int(results["made"].max())
    meta = {
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "data_through": data_through,
        "forecasts_made_from": month_name(first),
        "forecasts_made_to": month_name(last),
    }
    alert_list = [{"country": str(country_names.get(e.country_id, e.country_id)), "start": e.start, "end": e.end,
                   "times_usual": round(float(np.exp(e.peak)), 1), "kind": e.kind,
                   **{q.key: getattr(e, q.key) for q in QUESTIONS.values()}}
                  for e in episodes.itertuples()]
    pair_lines, pair_summary = (_pairs_section(pair_results, names or {}) if pair_results is not None
                                else ([], None))
    (out_dir / "backtest.json").write_text(json.dumps(
        {**meta, "results": summary, "alerts": {"record": alert_record, "list": alert_list},
         "pairs": pair_summary}, indent=1))
    chart(summary, out_dir / "calibration.png")

    by = {(s["question"], s["ahead"]): s for s in summary}
    lines = [
        "# How SAGE would have done, 2018 to now",
        "",
        f"SAGE replayed every month from {_when(first)} to {_when(last)}: each forecast was made "
        "using only the data that existed at the time, then checked against what actually happened. "
        "The model was retrained each January on everything up to the December before.",
        "",
        f"Data: UCDP conflict deaths (checked data to 2025, monthly candidate data after), "
        f"up to {pd.Period(data_through, freq='M').strftime('%B %Y')}. "
        f"{len(countries)} countries: every country with organised violence recorded since 1989.",
        "",
        "## The short version (forecasts for next month)",
        "",
        "| Question | SAGE vs \"same as last month\" | SAGE vs the base rate | Percentages honest? |",
        "|---|---|---|---|",
    ]
    for q in QUESTIONS.values():
        s = by[(q.key, HEADLINE_AHEAD)]
        cal = pd.DataFrame(s["calibration"])
        weighted_gap = (abs(cal["said"] - cal["happened"]) * cal["forecasts"]).sum() / cal["forecasts"].sum()
        lines.append(
            f"| **{q.title}**: {q.text.lower()} in a month | "
            f"{_pct(s['skill_vs_last_month'])} smaller error | {_pct(s['skill_vs_base_rate'])} smaller error | "
            f"off by {_pct(weighted_gap, 1)} on average |")
    lines += [
        "",
        "\"Same as last month\" is the forecast to beat: it says a war that's on now will still be on, "
        "and a quiet country will stay quiet. It's right most of the time, which is why beating it at all is hard. "
        "The error measure is the Brier score: the average squared gap between the forecast and what happened.",
        "",
        "![How honest SAGE's percentages were](calibration.png)",
        "",
    ]
    compared = [by[("fighting", h)].get("views") for h in (1, 2, 3)]
    if all(compared):
        lines += [
            "## Compared with ViEWS",
            "",
            "ViEWS (Uppsala University and PRIO) publishes monthly forecasts of the same \"deadly fighting\" "
            "question. On the same countries and months, using the ViEWS forecast that was out when SAGE made "
            f"its own ({_when(compared[0]['from'])} to {_when(compared[0]['to'])}), the error of each "
            "(lower is better):",
            "",
            "| | This month | Next month | The month after |",
            "|---|---|---|---|",
            "| SAGE | " + " | ".join(f"{c['sage']:.4f}" for c in compared) + " |",
            "| ViEWS | " + " | ".join(f"{c['views']:.4f}" for c in compared) + " |",
            "| Same as last month | " + " | ".join(f"{c['last_month']:.4f}" for c in compared) + " |",
            "| Average of SAGE and ViEWS | " + " | ".join(f"{c['average']:.4f}" for c in compared) + " |",
            "",
            "Averaging the two was worse than SAGE alone, so SAGE doesn't use ViEWS's numbers. This only covers "
            "the 130 countries SAGE follows, not every country ViEWS forecasts.",
            "",
        ]
    if alert_record:
        lines += [
            "## Build-up alerts",
            "",
            "Checked every day. An alert goes out when three warning signs in the last 14 days of news are "
            "all at least 2.7 times their usual level over the year before: other countries' forces "
            "mobilising against the country, showing force at it, and threatening it. Alert days less than "
            "a week apart count as one alert. It's a flag, not a forecast: it marks the rare stretches that "
            "look like the run-up to past wars.",
            "",
            f"{alert_record['episodes']} alerts since April 2014. What followed between each alert starting "
            f"and {alert_record['within_days']} days after it ended, in that country, where it hadn't happened "
            "in the 90 days before (\"in a month\" here means any 30 days):",
            "",
            "| | After an alert | After a random day |",
            "|---|---|---|",
        ]
        for q in QUESTIONS.values():
            r = alert_record["questions"].get(q.key)
            if r and r["alerts"]:
                lines.append(f"| {q.title} | {r['followed']} of {r['alerts']} ({_pct(r['rate'])}) | "
                             f"{_pct(r['usual'], 1)} |")
        own = alert_record.get("own_forces")
        if own and own["questions"].get("fighting"):
            f_own, f_aim = own["questions"]["fighting"], alert_record["questions"]["fighting"]
            lines += ["", f"The same test on a country's own forces (its forces mobilising, shows of force in "
                      f"it, threats by it) went off {own['episodes']} times, mostly after terror attacks, when "
                      f"police and soldiers fill the streets; deadly fighting followed {f_own['followed']} of "
                      f"{f_own['alerts']} times ({_pct(f_own['rate'])}), against {_pct(f_aim['rate'])} for "
                      "alerts about forces aimed at a country. So only the latter raise alerts; the own-forces "
                      "signs still feed into the forecasts."]
        lines += ["", "Every alert so far (✔ = followed, ✘ = didn't, "
                  "· = had already happened in the 90 days before, … = too recent to tell):", "",
                  "| Country | From | To | Peak | " + " | ".join(q.title for q in QUESTIONS.values()) + " |",
                  "|---|---|---|---|" + "---|" * len(QUESTIONS)]
        mark = {"yes": "✔", "no": "✘", "already": "·", "wait": "…"}
        for a in alert_list:
            lines.append(f"| {a['country']} | {_day(a['start'])} | {_day(a['end'])} | {a['times_usual']:.1f}× | "
                         + " | ".join(mark[a[q.key]] for q in QUESTIONS.values()) + " |")
        lines.append("")

    for q in QUESTIONS.values():
        lines += [f"## {q.title}", "", f"{q.text}, in a given month.", "",
                  "| | This month (made mid-month) | Next month | The month after |", "|---|---|---|---|"]
        rows = [by[(q.key, h)] for h in (1, 2, 3)]
        lines.append("| Forecasts checked | " + " | ".join(f"{s['forecasts']:,}" for s in rows) + " |")
        lines.append("| Times it happened | " + " | ".join(f"{s['happened']:,}" for s in rows) + " |")
        lines.append("| Error: SAGE | " + " | ".join(f"{s['brier']['sage']:.4f}" for s in rows) + " |")
        lines.append("| Error: same as last month | " + " | ".join(f"{s['brier']['last_month']:.4f}" for s in rows) + " |")
        lines.append("| Error: base rate | " + " | ".join(f"{s['brier']['base_rate']:.4f}" for s in rows) + " |")
        lines.append("| SAGE better than last month by | " + " | ".join(_pct(s["skill_vs_last_month"]) for s in rows) + " |")
        lines.append("| Ranking (AUC: 50% = guessing, 100% = perfect) | "
                     + " | ".join(_pct(s["auc"]["sage"], 1) for s in rows) + " |")

        s = by[(q.key, HEADLINE_AHEAD)]
        lines += ["", "When SAGE said… (next month)", "",
                  "| SAGE said | Forecasts | It happened |", "|---|---|---|"]
        for c in s["calibration"]:
            lines.append(f"| {_pct(c['from'])}–{_pct(c['to'])} (avg {_pct(c['said'], 1)}) | "
                         f"{c['forecasts']:,} | {_pct(c['happened'], 1)} |")

        new = s["new"]
        lines += [
            "",
            f"**New outbreaks** (after {NEW_AFTER}+ calm months): {new['happened']} happened in "
            f"{new['forecasts']:,} calm country-months. SAGE rated those that happened at "
            f"{_pct(new['mean_when_happened'], 1)} on average, against {_pct(new['mean_when_calm'], 1)} "
            f"for places that stayed calm, so it does point at the right places "
            f"(ranking {_pct(new['auc'], 0)}), but almost never with confidence: it went above 20% "
            f"{new['flagged']} times, {new['flagged_and_happened']} of which happened.",
            "",
        ]
        misses = _misses(results, countries, q.key, 6)
        if len(misses):
            lines.append("Biggest surprises: " + "; ".join(
                f"{m.name}, {_when(int(m.made) + HEADLINE_AHEAD)} (SAGE said {_pct(m.sage, 1)})"
                for m in misses.itertuples()) + ".")
            lines.append("")

        lines += ["Year by year (next month):", "", "| Year | Forecasts | Happened | Better than last month by |",
                  "|---|---|---|---|"]
        for y in s["by_year"]:
            lines.append(f"| {y['year']} | {y['forecasts']:,} | {y['happened']} | {_pct(y['skill_vs_last_month'])} |")
        lines.append("")

    lines += pair_lines
    lines += ["## Before the big surprises", "",
              "What SAGE said before wars that started suddenly. A forecast for a month is made in the middle "
              "of the month before (\"next month\"), or two months before (\"the month after\"), using "
              "conflict data to the end of the previous month and the news so far.", "",
              "| War | Began | Question | Forecast a month before | Two months before | Build-up alert before? |",
              "|---|---|---|---|---|---|"]
    for country_id, began, label in FAMOUS:
        target = int(month_index(pd.Series([began[:7]])).iloc[0])
        warned = "No"
        if len(episodes):
            mine = episodes[(episodes["country_id"] == country_id) & (episodes["start"] < began)
                            & (episodes["end"] >= (pd.Timestamp(began) - pd.Timedelta(days=90)).strftime("%Y-%m-%d"))]
            if len(mine):
                first_day = mine["start"].min()
                lead = (pd.Timestamp(began) - pd.Timestamp(first_day)).days
                warned = f"Yes, from {_day(first_day)} ({lead} days before)"
        for key in ("fighting", "major_war"):
            cells = []
            for h in (2, 3):
                hit = results[(results["question"] == key) & (results["ahead"] == h)
                              & (results["country_id"] == country_id) & (results["made"] + h == target)]
                cells.append(_pct(float(hit["sage"].iloc[0]), 1) if len(hit) else "–")
            lines.append(f"| {label} | {_day(began)} | {QUESTIONS[key].title} | {cells[0]} | {cells[1]} | {warned} |")
    lines += [
        "",
        "## Caveats",
        "",
        "- The backtest uses UCDP's checked data for 2018–2025; live forecasts use their monthly candidate data, "
        "which is rougher, so live results may be a little worse. The live record will show it.",
        "- Surprise attacks planned in secret (like 7 October) leave little or no trace in public news, so no "
        "forecaster working from public information can be expected to catch them reliably.",
        "",
    ]
    (out_dir / "backtest.md").write_text("\n".join(lines))
