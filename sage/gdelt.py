"""News signals from GDELT, which machine-reads world news and logs each
reported event (who did what to whom, where): daily files since April
2013, and a historical archive back to 1979.

Each file is reduced to counts per country of the kinds of event that
come before wars: troop mobilisations, raised military alerts, threats of
force, as well as fighting and attacks themselves. Daily files are filed
under the day GDELT published them, so a count for a day only holds what
the news had reported by then; the archive, which GDELT built afterwards
from old news, is filed by the date of each event.
"""

from __future__ import annotations

import csv
import http.client
import io
import os
import sys
import threading
import time
import urllib.error
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

from . import codes

BASE = "https://data.gdeltproject.org/events/"
# GDELT 2.0: the same events, published every 15 minutes since February
# 2015. SAGE reads GDELT 1.0's daily files while they keep coming, and
# builds any day they don't cover from GDELT 2.0's files instead.
BASE2 = "https://data.gdeltproject.org/gdeltv2/"
V2_FIRST_DAY = date(2015, 2, 19)
FIRST_DAY = date(2013, 4, 1)
EARLY_DAYS = 14
USER_AGENT = "sage-forecasts/0.1"

# GDELT 1.0 event columns SAGE reads.
_COLS = {1: "date", 7: "actor1", 17: "actor2", 27: "base", 28: "root", 29: "quad", 31: "mentions",
         34: "tone", 51: "where"}
# GDELT 2.0 adds district codes, which moves the place column along.
_COLS2 = {**{k: v for k, v in _COLS.items() if v != "where"}, 53: "where"}
# The archive before the daily files: yearly to 2005, then monthly.
HISTORY_FROM, HISTORY_TO = 1988, "2013-03"

# Kinds of event, by CAMEO code (GDELT's event codebook).
CATEGORIES = {
    "all": None,
    "verbal_conflict": ("quad", {"3"}),
    "material_conflict": ("quad", {"4"}),
    "protest": ("root", {"14"}),
    "threaten": ("root", {"13"}),
    "threaten_force": ("base", {"138"}),        # threaten with military force
    "force_posture": ("root", {"15"}),          # show of military or police force
    "mobilize": ("base", {"152", "154"}),       # raise military alert, mobilise armed forces
    "reduce_relations": ("root", {"16"}),       # cut ties, expel diplomats, sanctions
    "coerce": ("root", {"17"}),
    "assault": ("root", {"18"}),
    "bombing": ("base", {"183"}),               # suicide, car or other bombings
    "fight": ("root", {"19"}),
    "heavy_weapons": ("base", {"194", "195"}),  # artillery, tanks, air strikes
    "mass_violence": ("root", {"20"}),
}
MENTIONED = ["all", "threaten_force", "force_posture", "mobilize", "fight", "assault"]


def _flags(events: pd.DataFrame) -> pd.DataFrame:
    flags = {}
    for name, rule in CATEGORIES.items():
        flags[name] = (np.ones(len(events), bool) if rule is None
                       else events[rule[0]].isin(rule[1]).to_numpy())
    return pd.DataFrame(flags, index=events.index)


def _count(events: pd.DataFrame, flags: pd.DataFrame, key: pd.Series) -> pd.DataFrame:
    keep = key.notna() & (key != "")
    k = key[keep]
    counts = flags[keep].groupby(k.to_numpy()).sum().astype("int64")
    mentions = events.loc[keep, "mentions"]
    for name in MENTIONED:
        counts[f"mentions_{name}"] = (mentions * flags.loc[keep, name]).groupby(k.to_numpy()).sum()
    counts["tone_sum"] = events.loc[keep, "tone"].groupby(k.to_numpy()).sum().round(2)
    return counts


def count_events(events: pd.DataFrame) -> pd.DataFrame:
    """Events -> counts per country, keyed three ways:

      where:  where events happened (FIPS country codes);
      actor:  the countries of the actors involved (CAMEO codes), since a
              mobilisation is often reported at home;
      target: the country on the receiving end of another country's
              actions, so Russia's build-up against Ukraine counts for
              Ukraine too.
    """
    events = events.copy()
    events["mentions"] = pd.to_numeric(events["mentions"], errors="coerce").fillna(1)
    events["tone"] = pd.to_numeric(events["tone"], errors="coerce").fillna(0)
    flags = _flags(events)

    where = _count(events, flags, events["where"]).assign(kind="where")
    # Each event counts once per actor country (twice only if two countries).
    a1 = events["actor1"]
    a2 = events["actor2"].where(events["actor2"] != events["actor1"])
    both = pd.concat([events.assign(country=a1), events.assign(country=a2)], ignore_index=True)
    both_flags = pd.concat([flags, flags], ignore_index=True)
    actor = _count(both, both_flags, both["country"]).assign(kind="actor")
    foreign = a1.notna() & (a1 != "") & a2.notna() & (a2 != "")
    target = _count(events[foreign], flags[foreign], a2[foreign]).assign(kind="target")
    out = pd.concat([where, actor, target]).rename_axis("code").reset_index()
    return out[["kind", "code", *[c for c in out.columns if c not in ("kind", "code")]]]


# The escalation ladder between two countries, by CAMEO code: from words,
# through cutting ties and threats, to shows of force, mobilisation and
# fighting. "cooperate" counts the friendly side for comparison.
LADDER = {
    "cooperate": ("quad", {"1", "2"}),
    "words": ("root", {"10", "11", "12"}),       # demands, criticism, rejection
    "reduce_relations": ("root", {"16"}),        # sanctions, expulsions, cutting ties
    "threaten": ("root", {"13"}),
    "threaten_force": ("base", {"138"}),
    "force_posture": ("root", {"15"}),
    "mobilize": ("base", {"152", "154"}),
    "coerce": ("root", {"17"}),
    "assault": ("root", {"18"}),
    "fight": ("root", {"19"}),
    "heavy_weapons": ("base", {"194", "195"}),
    "mass_violence": ("root", {"20"}),
}


def count_pairs(events: pd.DataFrame) -> pd.DataFrame:
    """Events between two countries' actors, by step of the ladder: one row
    per (from, to) pair, e.g. RUS -> GBR for Russia threatening Britain."""
    a1, a2 = events["actor1"].fillna(""), events["actor2"].fillna("")
    between = (a1.str.len() == 3) & (a2.str.len() == 3) & (a1 != a2)
    e = events[between]
    if e.empty:
        return pd.DataFrame(columns=["a1", "a2", "all", *LADDER, "tone_sum"])
    flags = {"all": np.ones(len(e), dtype="int64")}
    for name, (column, codes_) in LADDER.items():
        flags[name] = e[column].isin(codes_).to_numpy().astype("int64")
    table = pd.DataFrame(flags, index=e.index)
    table["tone_sum"] = pd.to_numeric(e["tone"], errors="coerce").fillna(0).round(2)
    table["a1"], table["a2"] = a1[between], a2[between]
    return table.groupby(["a1", "a2"], as_index=False).sum()


def _read(f, chunksize: int | None = None, columns: dict = _COLS):
    return pd.read_csv(f, sep="\t", header=None, usecols=list(columns), dtype=str,
                       quoting=csv.QUOTE_NONE, on_bad_lines="skip", encoding_errors="replace",
                       chunksize=chunksize)


def _v1_events(raw: bytes) -> pd.DataFrame:
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        with z.open(z.namelist()[0]) as f:
            return _read(f).rename(columns=_COLS)


def _v2_events(day: date) -> pd.DataFrame | None:
    """One day from GDELT 2.0's 96 quarter-hour files (None if it has none)."""
    frames = []
    for hour in range(24):
        for minute in (0, 15, 30, 45):
            raw = _get(f"{BASE2}{day:%Y%m%d}{hour:02d}{minute:02d}00.export.CSV.zip")
            if raw is None:
                continue
            with zipfile.ZipFile(io.BytesIO(raw)) as z, z.open(z.namelist()[0]) as f:
                frames.append(_read(f, columns=_COLS2))
    if not frames:
        return None
    return pd.concat(frames, ignore_index=True).rename(columns=_COLS2)


def daily_counts(raw: bytes) -> pd.DataFrame:
    """One day's GDELT file -> counts per country (see count_events)."""
    return count_events(_v1_events(raw))


def v2_day_counts(day: date) -> pd.DataFrame | None:
    events = _v2_events(day)
    return None if events is None else count_events(events)


def history_counts(zip_path: Path) -> tuple[pd.DataFrame, pd.DataFrame]:
    """One archive file -> counts per country, month and half-month
    ("early": days 1-14, "late": the rest), by the date of each event; and
    counts per pair of countries and month."""
    totals, pairs = [], []
    with zipfile.ZipFile(zip_path) as z:
        with z.open(z.namelist()[0]) as f:
            for chunk in _read(f, chunksize=1_000_000):
                chunk = chunk.rename(columns=_COLS)
                stamp = chunk["date"].astype(str)
                chunk["month"] = stamp.str[:4] + "-" + stamp.str[4:6]
                chunk["part"] = np.where(pd.to_numeric(stamp.str[6:8], errors="coerce") <= EARLY_DAYS,
                                         "early", "late")
                chunk = chunk[(chunk["month"] >= f"{HISTORY_FROM}-01") & (chunk["month"] <= HISTORY_TO)]
                for (month, part), group in chunk.groupby(["month", "part"]):
                    totals.append(count_events(group).assign(month=month, part=part))
                for month, group in chunk.groupby("month"):
                    pairs.append(count_pairs(group).assign(month=month))
    if not totals:
        return pd.DataFrame(), pd.DataFrame()
    rows = pd.concat(totals, ignore_index=True)
    rows = rows.groupby(["month", "part", "kind", "code"], as_index=False).sum(numeric_only=True)
    pair_rows = pd.concat(pairs, ignore_index=True).groupby(["month", "a1", "a2"], as_index=False).sum(numeric_only=True)
    return rows, pair_rows


def available_days(index_html: str) -> list[date]:
    import re
    days = sorted({m for m in re.findall(r"(\d{8})\.export\.CSV\.zip", index_html)})
    return [date(int(d[:4]), int(d[4:6]), int(d[6:])) for d in days]


def _get(url: str, tries: int = 4) -> bytes | None:
    for attempt in range(tries):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=120) as response:
                return response.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            time.sleep(2 ** (attempt + 1))
        except (urllib.error.URLError, http.client.HTTPException, TimeoutError, ConnectionError):
            time.sleep(2 ** (attempt + 1))
    raise RuntimeError(f"couldn't download {url}")


def day_path(cache: Path, day: date) -> Path:
    return cache / "days" / f"{day:%Y}" / f"{day:%Y%m%d}.csv.gz"


def pairs_day_path(cache: Path, day: date) -> Path:
    return cache / "pairs" / "days" / f"{day:%Y}" / f"{day:%Y%m%d}.csv.gz"


def _history_paths(cache: Path, name: str) -> tuple[Path, Path]:
    return cache / "history" / f"{name}.csv.gz", cache / "pairs" / "history" / f"{name}.csv.gz"


def _write(table: pd.DataFrame, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    part = path.with_suffix(f".{os.getpid()}.{threading.get_ident()}.part")
    table.to_csv(part, index=False, compression="gzip")
    part.replace(path)


def history_files(index_html: str) -> list[str]:
    import re
    names = sorted(set(re.findall(r"\b((?:19|20)\d{2}(?:\d{2})?)\.zip", index_html)))
    keep = []
    for n in names:
        if len(n) == 4 and HISTORY_FROM <= int(n) <= 2005:
            keep.append(n)
        elif len(n) == 6 and f"{n[:4]}-{n[4:]}" <= HISTORY_TO:
            keep.append(n)
    return keep


def fetch_history(name: str, cache: Path) -> str:
    path, pairs_path = _history_paths(cache, name)
    if path.exists() and pairs_path.exists():
        return "cached"
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(f".{os.getpid()}.{threading.get_ident()}.zip")
    request = urllib.request.Request(f"{BASE}{name}.zip", headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=300) as response, open(tmp, "wb") as out:
                while chunk := response.read(1 << 20):
                    out.write(chunk)
            break
        except (urllib.error.URLError, http.client.HTTPException, TimeoutError, ConnectionError):
            time.sleep(2 ** (attempt + 2))
    else:
        raise RuntimeError(f"couldn't download {name}.zip")
    try:
        counts, pair_counts = history_counts(tmp)
    finally:
        tmp.unlink(missing_ok=True)
    if not path.exists():
        _write(counts, path)
    _write(pair_counts, pairs_path)
    return "done"


def backfill_history(cache: Path, workers: int = 2) -> None:
    """Download and reduce the archive before April 2013. Safe to stop and rerun."""
    index = _get(BASE + "index.html").decode("utf-8", "replace")
    names = [n for n in history_files(index) if not all(p.exists() for p in _history_paths(cache, n))]
    print(f"{len(names)} archive files to fetch", flush=True)
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(fetch_history, n, cache): n for n in names}
        for future in as_completed(futures):
            try:
                future.result()
                print(f"{futures[future]} done", flush=True)
            except Exception as e:
                print(f"{futures[future]} failed: {e}", file=sys.stderr, flush=True)


def fetch_day(day: date, cache: Path, source: str = "v1") -> str:
    path, pairs_path = day_path(cache, day), pairs_day_path(cache, day)
    if path.exists() and pairs_path.exists():
        return "cached"
    events = None
    if source == "v1":
        raw = _get(f"{BASE}{day:%Y%m%d}.export.CSV.zip")
        events = _v1_events(raw) if raw is not None else None
    # A few days are listed by GDELT 1.0 but never published.
    if events is None and day >= V2_FIRST_DAY:
        events = _v2_events(day)
    if events is None:
        return "missing"
    if not path.exists():
        _write(count_events(events), path)
    _write(count_pairs(events), pairs_path)
    return "done"


def plan(v1_days: set[date], first: date, last: date, today: date, cached) -> dict[date, str]:
    """Where to get each missing day: GDELT 1.0's daily file if there is
    one; otherwise GDELT 2.0, once the day is safely over (or straight away
    if GDELT 1.0 has stopped publishing)."""
    v1_alive = bool(v1_days) and max(v1_days) >= today - timedelta(days=3)
    settled = today - timedelta(days=2 if v1_alive else 1)
    out = {}
    day = first
    while day <= last:
        if not cached(day):
            if day in v1_days:
                out[day] = "v1"
            elif V2_FIRST_DAY <= day <= settled:
                out[day] = "v2"
        day += timedelta(days=1)
    return out


def backfill(cache: Path, first: date = FIRST_DAY, last: date | None = None, workers: int = 4) -> None:
    """Download and reduce every day not cached yet. Safe to stop and rerun."""
    try:
        index = _get(BASE + "index.html")
        v1_days = set(available_days(index.decode("utf-8", "replace"))) if index else set()
    except RuntimeError:  # GDELT 1.0 unreachable: GDELT 2.0 covers it
        v1_days = set()
    today = date.today()
    last = min(last or today, today - timedelta(days=1))
    sources = plan(v1_days, first, last, today,
                   lambda d: day_path(cache, d).exists() and pairs_day_path(cache, d).exists())
    days = sorted(sources)
    print(f"{len(days)} days to fetch ({sum(s == 'v2' for s in sources.values())} from GDELT 2.0)", flush=True)
    started, done = time.time(), 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(fetch_day, d, cache, sources[d]): d for d in days}
        for future in as_completed(futures):
            day = futures[future]
            try:
                result = future.result()
            except Exception as e:  # keep going; a rerun picks the day up again
                print(f"{day} failed: {e}", file=sys.stderr, flush=True)
                continue
            done += 1
            if result == "missing":
                print(f"{day} missing on GDELT", flush=True)
            if done % 100 == 0 or done == len(days):
                rate = done / (time.time() - started)
                print(f"{done}/{len(days)} days, {rate:.1f}/s, "
                      f"~{(len(days) - done) / max(rate, 1e-9) / 60:.0f} min left", flush=True)


def _days_in(month: str) -> int:
    return pd.Period(month, freq="M").days_in_month


def country_months(cache: Path, last_day: int = 31, months: set[str] | None = None) -> pd.DataFrame:
    """The cache summed into one row per UCDP country and month.

    With last_day=14, only each month's first fortnight is counted: the
    part of the month in progress that's known when a forecast is made.
    `days` is how many days of news went in (GDELT has the odd gap).
    """
    lookup = codes.lookup()
    parts, days_seen, world = [], {}, {}

    def add(table: pd.DataFrame, month: str, days: int) -> None:
        days_seen[month] = days_seen.get(month, 0) + days
        world[month] = world.get(month, 0) + int(table.loc[table["kind"] == "where", "all"].sum())
        table = table.assign(country_id=[lookup.get(k) for k in zip(table["kind"], table["code"])])
        table = table[table["country_id"].notna()].drop(columns=["code", "month", "part"], errors="ignore")
        parts.append(table.assign(month=month))

    for path in sorted((cache / "history").glob("*.csv.gz")):
        table = pd.read_csv(path, keep_default_na=False, dtype={"code": str, "month": str})
        if last_day <= EARLY_DAYS:
            table = table[table["part"] == "early"]
        for month, rows in table.groupby("month"):
            if months is None or month in months:
                add(rows, month, 0)
    for month in list(days_seen):
        days_seen[month] = min(last_day, _days_in(month))

    for path in sorted((cache / "days").glob("*/*.csv.gz")):
        stamp = path.name[:8]
        month = f"{stamp[:4]}-{stamp[4:6]}"
        if int(stamp[6:]) > last_day or (months is not None and month not in months):
            continue
        add(pd.read_csv(path, keep_default_na=False, dtype={"code": str}), month, 1)

    rows = pd.concat(parts, ignore_index=True)
    values = [c for c in rows.columns if c not in ("kind", "country_id", "month")]
    wide = rows.pivot_table(index=["country_id", "month"], columns="kind", values=values,
                            aggfunc="sum", fill_value=0)
    wide.columns = [f"{kind}_{name}" for name, kind in wide.columns]
    wide = wide.reset_index()
    wide["country_id"] = wide["country_id"].astype("int64")
    wide["days"] = wide["month"].map(days_seen)
    wide["world_all"] = wide["month"].map(world)
    return wide


def update(data_dir: Path, cache: Path, first: date | None = None, workers: int = 4) -> list[str]:
    """Fetch new days and refresh the stored month tables.

    Without `first`, starts from the 1st of last month, so the months it
    rebuilds always have every day in the cache. Months outside the
    rebuilt ones are kept as stored. Returns the months rebuilt.
    """
    today = date.today()
    if first is None:
        first = date(today.year - (today.month == 1), (today.month - 2) % 12 + 1, 1)
    backfill(cache, first=first, workers=workers)
    months = {str(p) for p in pd.period_range(f"{first:%Y-%m}", f"{today:%Y-%m}", freq="M")}
    for name, last_day in (("news_month.csv", 31), ("news_early.csv", EARLY_DAYS)):
        fresh = country_months(cache, last_day, months)
        path = data_dir / name
        if path.exists():
            stored = pd.read_csv(path, dtype={"month": str})
            fresh = pd.concat([stored[~stored["month"].isin(set(fresh["month"]))], fresh],
                              ignore_index=True)
        fresh = fresh.sort_values(["country_id", "month"])
        fresh.to_csv(path, index=False, float_format="%.6g")
    return sorted(months)


def rebuild(data_dir: Path, cache: Path) -> None:
    """Rebuild the month tables from everything in the cache."""
    for name, last_day in (("news_month.csv", 31), ("news_early.csv", EARLY_DAYS)):
        table = country_months(cache, last_day).sort_values(["country_id", "month"])
        table.to_csv(data_dir / name, index=False, float_format="%.6g")


def load(data_dir: Path) -> tuple[pd.DataFrame | None, pd.DataFrame | None]:
    tables = []
    for name in ("news_month.csv", "news_early.csv"):
        path = data_dir / name
        tables.append(pd.read_csv(path, dtype={"month": str}) if path.exists() else None)
    return tables[0], tables[1]
