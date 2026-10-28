"""UCDP's event data: where it lives, downloading it, and reading it.

UCDP publishes two kinds of event files, both free (CC BY 4.0):
  - GED, the checked yearly release (e.g. 26.1 covers 1989 to 2025);
  - Candidate, monthly releases for the months since, published with
    about a month's lag.
"""

from __future__ import annotations

import re
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path

import pandas as pd

DOWNLOADS_PAGE = "https://ucdp.uu.se/downloads/"
USER_AGENT = "sage-forecasts/0.1"
COLUMNS = ["type_of_violence", "side_a", "side_b", "gwnoa", "gwnob", "country", "country_id",
           "region", "latitude", "longitude", "date_start", "best"]

_GED = re.compile(r"https://ucdp\.uu\.se/downloads/ged/ged(\d{2})(\d)-csv\.zip")
# GEDEvent_v26_01_26_09.csv: January to September 2026 in one file.
_SPAN = re.compile(r"https://ucdp\.uu\.se/downloads/candidateged/"
                   r"GEDEvent_v(\d{2})_(\d{2})_(\d{2})_(\d{2})\.csv", re.I)
# GEDEvent_v26_0_9.csv: September 2026 alone.
_MONTH = re.compile(r"https://ucdp\.uu\.se/downloads/candidateged/"
                    r"GEDEvent_v(\d{2})_0_(\d{1,2})\.csv", re.I)


@dataclass(frozen=True)
class Source:
    url: str
    first: str  # first month covered, "YYYY-MM"
    last: str   # last month covered
    rank: int   # when two sources cover a month, the higher rank wins

    @property
    def filename(self) -> str:
        return self.url.rsplit("/", 1)[1]


def find_sources(page: str) -> list[Source]:
    """Every event file linked from UCDP's downloads page."""
    found: dict[str, Source] = {}
    for m in _GED.finditer(page):
        # Version 26.1 is released in 2026 and covers up to the end of 2025.
        last_year = 2000 + int(m.group(1)) - 1
        found[m.group(0)] = Source(m.group(0), "1989-01", f"{last_year}-12", 3)
    for m in _SPAN.finditer(page):
        first = f"20{m.group(1)}-{m.group(2)}"
        last = f"20{m.group(3)}-{m.group(4)}"
        found[m.group(0)] = Source(m.group(0), first, last, 2)
    for m in _MONTH.finditer(page):
        month = f"20{m.group(1)}-{int(m.group(2)):02d}"
        found[m.group(0)] = Source(m.group(0), month, month, 1)
    return sorted(found.values(), key=lambda s: (s.last, s.rank))


def months_between(first: str, last: str) -> list[str]:
    return [str(p) for p in pd.period_range(first, last, freq="M")]


def assign_months(sources: list[Source]) -> dict[str, Source]:
    """Which source each month's events are taken from.

    The checked GED release beats candidate data; among candidate files,
    the newer release wins, and a multi-month file beats a single month.
    """
    owner: dict[str, Source] = {}
    for s in sorted(sources, key=lambda s: (s.rank == 3, s.last, s.rank)):
        for month in months_between(s.first, s.last):
            owner[month] = s
    return owner


def fetch(url: str, dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    part = dest.with_suffix(dest.suffix + ".part")
    with urllib.request.urlopen(request, timeout=300) as response, open(part, "wb") as out:
        while chunk := response.read(1 << 20):
            out.write(chunk)
    part.replace(dest)
    return dest


def fetch_page() -> str:
    request = urllib.request.Request(DOWNLOADS_PAGE, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8", "replace")


def read_events(path: Path) -> pd.DataFrame:
    """One release's events, as the columns SAGE uses."""
    if path.suffix == ".zip":
        with zipfile.ZipFile(path) as z:
            name = next(n for n in z.namelist() if n.lower().endswith(".csv"))
            with z.open(name) as f:
                events = pd.read_csv(f, usecols=COLUMNS, low_memory=False)
    else:
        events = pd.read_csv(path, usecols=COLUMNS, low_memory=False)
    events["day"] = events["date_start"].astype(str).str[:10]
    events["month"] = events["day"].str[:7]
    events["best"] = pd.to_numeric(events["best"], errors="coerce").fillna(0)
    return events.drop(columns="date_start")


def download(raw_dir: Path, page: str | None = None) -> pd.DataFrame:
    """Every event since 1989, each month taken from its best source."""
    sources = find_sources(page if page is not None else fetch_page())
    if not any(s.rank == 3 for s in sources):
        raise RuntimeError("UCDP's downloads page no longer links a GED release")
    owner = assign_months(sources)
    parts = []
    for source in {s for s in owner.values()}:
        months = {m for m, s in owner.items() if s == source}
        events = read_events(fetch(source.url, raw_dir / source.filename))
        parts.append(events[events["month"].isin(months)])
    return pd.concat(parts, ignore_index=True)
