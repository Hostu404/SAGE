"""Turns UCDP events into one row per country per month.

The table is kept in the repo (data/country_month.csv), so months that
UCDP stops linking from its downloads page are never lost.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

from .ucdp import months_between

# Deaths in each month, by kind of violence (UCDP's best estimate).
SERIES = {
    "state": "fighting involving a government (UCDP state-based)",
    "nonstate": "fighting between armed groups (UCDP non-state)",
    "gov_civ": "killings of civilians by governments (UCDP one-sided)",
    "group_civ": "killings of civilians by armed groups (UCDP one-sided)",
}
COUNTS = ["events"]
PANEL_COLUMNS = ["country_id", "month", *SERIES, *COUNTS]
COUNTRY_COLUMNS = ["country_id", "name", "region", "lat", "lon"]


def by_country_month(events: pd.DataFrame) -> pd.DataFrame:
    """Deaths and event counts for each country and month that had any."""
    kind = np.select(
        [events["type_of_violence"] == 1,
         events["type_of_violence"] == 2,
         events["side_a"].astype(str).str.startswith("Government of")],
        ["state", "nonstate", "gov_civ"],
        "group_civ",
    )
    deaths = (events.assign(kind=kind)
              .pivot_table(index=["country_id", "month"], columns="kind",
                           values="best", aggfunc="sum", fill_value=0))
    deaths = deaths.reindex(columns=list(SERIES), fill_value=0)
    counts = events.groupby(["country_id", "month"]).size().rename("events")
    return deaths.join(counts).reset_index()


DAILY_FROM = "2013-01-01"


def by_country_day(events: pd.DataFrame) -> pd.DataFrame:
    """Deaths per country per day since 2013 (days with any), to judge the
    daily build-up alerts against."""
    if "day" not in events:
        return pd.DataFrame(columns=["country_id", "day", *SERIES])
    recent = events[events["day"] >= DAILY_FROM]
    table = by_country_month(recent.assign(month=recent["day"])).rename(columns={"month": "day"})
    table = table[(table[list(SERIES)] > 0).any(axis=1)]
    return table[["country_id", "day", *SERIES]]


def countries(events: pd.DataFrame) -> pd.DataFrame:
    """Each country's name, region and the middle of where its violence happened."""
    latest = events.sort_values("month").groupby("country_id").tail(1).set_index("country_id")
    centre = events.groupby("country_id")[["latitude", "longitude"]].median()
    out = pd.DataFrame({
        # "Russia (Soviet Union)" -> "Russia"
        "name": latest["country"].str.replace(r"\s*\(.*\)$", "", regex=True),
        "region": latest["region"].astype(str).str.split(",").str[0].str.strip(),
        "lat": centre["latitude"].round(3),
        "lon": centre["longitude"].round(3),
    })
    return out.reset_index()[COUNTRY_COLUMNS]


def complete(rows: pd.DataFrame, country_ids, first: str, last: str) -> pd.DataFrame:
    """Every country for every month, with zeros where nothing happened."""
    index = pd.MultiIndex.from_product(
        [sorted(country_ids), months_between(first, last)], names=["country_id", "month"])
    full = rows.set_index(["country_id", "month"]).reindex(index, fill_value=0)
    return full.reset_index()[PANEL_COLUMNS].astype({c: "int64" for c in [*SERIES, *COUNTS]})


def update(data_dir: Path, events: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Merge newly downloaded events into the stored tables and save them.

    Months present in the new download replace the stored ones; stored
    months the download no longer covers are kept as they were.
    """
    panel_path = data_dir / "country_month.csv"
    countries_path = data_dir / "countries.csv"
    new_rows = by_country_month(events)
    new_months = set(events["month"].unique())
    new_countries = countries(events)

    if panel_path.exists():
        old = pd.read_csv(panel_path, dtype={"month": str})
        kept = old[~old["month"].isin(new_months)]
        rows = pd.concat([kept, new_rows], ignore_index=True)
        old_countries = pd.read_csv(countries_path)
        missing = old_countries[~old_countries["country_id"].isin(new_countries["country_id"])]
        new_countries = pd.concat([new_countries, missing], ignore_index=True)
        months = sorted(new_months | set(old["month"]))
    else:
        rows = new_rows
        months = sorted(new_months)

    panel = complete(rows, new_countries["country_id"], months[0], months[-1])
    new_countries = new_countries.sort_values("country_id").reset_index(drop=True)
    data_dir.mkdir(parents=True, exist_ok=True)
    days_path = data_dir / "deaths_daily.csv"
    daily = by_country_day(events)
    if days_path.exists():
        old_days = pd.read_csv(days_path, dtype={"day": str})
        daily = pd.concat([old_days[~old_days["day"].str[:7].isin(new_months)], daily], ignore_index=True)
    daily.sort_values(["country_id", "day"]).to_csv(days_path, index=False)
    panel.to_csv(panel_path, index=False)
    new_countries.to_csv(countries_path, index=False)
    return panel, new_countries


def load(data_dir: Path) -> tuple[pd.DataFrame, pd.DataFrame]:
    panel = pd.read_csv(data_dir / "country_month.csv", dtype={"month": str})
    return panel, pd.read_csv(data_dir / "countries.csv")
