"""ViEWS (Uppsala/PRIO's Violence & Impacts Early-Warning System): its
monthly forecasts, kept exactly as published, since December 2021.

SAGE uses two of its numbers per country and month: the chance of at
least 25 deaths in fighting involving the government (the same question
as SAGE's "deadly fighting"), and its predicted number of deaths.
"""

from __future__ import annotations

import json
import re
import urllib.request
from pathlib import Path

import pandas as pd

from .features import month_name

API = "https://api.viewsforecasting.org/"
USER_AGENT = "sage-forecasts/0.1"
STEPS = 4  # months ahead kept from each run
_RUN = re.compile(r"^fatalities(\d{3})_(\d{4})_(\d{2})_t(\d{2})$")


def _get(url: str) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read())


def _month(views_month_id: int) -> str:
    # ViEWS counts months from January 1980 = 1.
    return month_name(1980 * 12 + views_month_id - 1)


def runs() -> list[str]:
    """The published monthly runs, one per data month: where a month has
    several (a newer model, a re-run), the latest."""
    best: dict[tuple[str, str], tuple] = {}
    for run in _get(API)["runs"]:
        m = _RUN.match(run)
        if m:
            best[(m.group(2), m.group(3))] = max(best.get((m.group(2), m.group(3)), ()), (m.group(1), m.group(4), run))
    return sorted(v[2] for v in best.values())


def fetch_run(run: str) -> pd.DataFrame:
    """A run's first STEPS forecast months, per country."""
    first = _get(f"{API}{run}/cm/sb?page=1")["start_date"]
    start, end = _month(first), _month(first + STEPS - 1)
    end_day = pd.Period(end, freq="M").days_in_month
    rows, url = [], f"{API}{run}/cm/sb?date_start={start}-01&date_end={end}-{end_day}"
    while url:
        page = _get(url)
        rows += page["data"]
        url = page.get("next_page") or ""
    table = pd.DataFrame(rows)
    return pd.DataFrame({
        "made": _month(first - 1),  # the last month of data the run used
        "month": table["month_id"].map(_month),
        "country_id": table["gwcode"].astype(int),
        # Runs before April 2023 used the older names.
        "views_fighting": _column(table, "main_dich", "sc_cm_sb_dich_main"),
        "views_deaths_ln": _column(table, "main_mean_ln", "sc_cm_sb_main"),
    })


def _column(table: pd.DataFrame, *names: str) -> pd.Series:
    for name in names:
        if name in table:
            return table[name]
    return pd.Series(float("nan"), index=table.index)


def update(data_dir: Path) -> pd.DataFrame:
    """Add any runs not stored yet to data/views.csv."""
    path = data_dir / "views.csv"
    stored = pd.read_csv(path, dtype={"made": str, "month": str}) if path.exists() else pd.DataFrame()
    have = set(stored["run"]) if len(stored) else set()
    new = [fetch_run(r).assign(run=r) for r in runs() if r not in have]
    table = pd.concat([stored, *new], ignore_index=True) if new else stored
    if new:
        # A newer run for the same data month replaces the older one.
        table = (table.sort_values("run").drop_duplicates(["made", "month", "country_id"], keep="last")
                 .sort_values(["made", "country_id", "month"]))
        table.to_csv(path, index=False, float_format="%.5g")
    return table


def load(data_dir: Path) -> pd.DataFrame | None:
    path = data_dir / "views.csv"
    return pd.read_csv(path, dtype={"made": str, "month": str}) if path.exists() else None
