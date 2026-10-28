"""Tension between pairs of countries, and what came of it.

Three records, linked by country (Gleditsch-Ward numbers, as UCDP uses):

  news:      GDELT events from one country's actors aimed at another's,
             by step of the escalation ladder (gdelt.LADDER), per month;
  fighting:  UCDP's deaths in fighting between the two governments, per
             month, since 1989;
  disputes:  the Correlates of War project's militarized interstate
             disputes, 1988-2014: every threat, show and use of force
             between states, and how far each went.
"""

from __future__ import annotations

import io
import urllib.request
import zipfile
from pathlib import Path

import numpy as np
import pandas as pd

from .features import month_index, month_name

MID_URL = "https://correlatesofwar.org/wp-content/uploads/dyadic_mid_4.03_update.zip"
USER_AGENT = "sage-forecasts/0.1"
# GDELT's actor codes are ISO 3-letter codes, with a few of its own.
CAMEO_TO_ISO = {"ROM": "ROU", "KSV": "XKX", "TMP": "TLS"}
# Correlates of War numbers that differ from Gleditsch-Ward's.
COW_TO_GW = {255: 260, 679: 678}
# The dispute record's hostility levels.
HOSTILITY = {2: "threat", 3: "show of force", 4: "use of force", 5: "war"}


def states(data_dir: Path) -> pd.DataFrame:
    return pd.read_csv(data_dir / "states.csv")


def iso_to_gw(table: pd.DataFrame) -> dict[str, int]:
    out = dict(zip(table["iso3"], table["gw"]))
    for cameo, iso in CAMEO_TO_ISO.items():
        if iso in out:
            out[cameo] = out[iso]
    return out


def _codes(value) -> list[int]:
    """'2;200;900' or '365.0' -> [2, 200, 900] or [365]."""
    if pd.isna(value):
        return []
    return [int(float(v)) for v in str(value).replace(",", ";").split(";") if v.strip()]


def fighting(events: pd.DataFrame) -> pd.DataFrame:
    """Deaths in fighting between two governments, per pair and month.
    Pairs are written with the lower number first."""
    between = events[(events["type_of_violence"] == 1)
                     & events["side_a"].astype(str).str.startswith("Government of")
                     & events["side_b"].astype(str).str.startswith("Government of")]
    rows = []
    for e in between.itertuples():
        for a in _codes(e.gwnoa):
            for b in _codes(e.gwnob):
                if a != b:
                    rows.append((min(a, b), max(a, b), e.month, e.best))
    table = pd.DataFrame(rows, columns=["a", "b", "month", "deaths"])
    return table.groupby(["a", "b", "month"], as_index=False)["deaths"].sum()


def mid_file(raw_dir: Path) -> Path:
    """The dyadic dispute record (1816-2014), downloaded once."""
    path = raw_dir / "dyadic_mid_4.03.csv"
    if not path.exists():
        request = urllib.request.Request(MID_URL, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=300) as response:
            data = response.read()
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            name = next(n for n in z.namelist() if n.endswith("dyadic_mid_4.03.csv") and "MACOSX" not in n)
            raw_dir.mkdir(parents=True, exist_ok=True)
            path.write_bytes(z.read(name))
    return path


def disputes(raw_dir: Path) -> pd.DataFrame:
    """The highest hostility between each pair in each month of a dispute,
    from the dyadic dispute record (1988 on)."""
    mid = pd.read_csv(mid_file(raw_dir), low_memory=False)
    mid = mid[mid["endyear"] >= 1988].drop_duplicates(["disno", "statea", "stateb"])
    rows = []
    for d in mid.itertuples():
        a, b = COW_TO_GW.get(d.statea, d.statea), COW_TO_GW.get(d.stateb, d.stateb)
        start = d.strtyr * 12 + max(d.strtmnth, 1) - 1
        end = d.endyear * 12 + max(d.endmnth, 1) - 1
        for m in range(max(start, 1988 * 12), end + 1):
            rows.append((min(a, b), max(a, b), month_name(m), int(d.hihost)))
    table = pd.DataFrame(rows, columns=["a", "b", "month", "level"])
    return table.groupby(["a", "b", "month"], as_index=False)["level"].max()


# Steps of the ladder kept per direction, and the hostile ones among them.
LADDER = ["all", "cooperate", "words", "reduce_relations", "threaten", "threaten_force",
          "force_posture", "mobilize", "coerce", "assault", "fight", "heavy_weapons", "mass_violence"]
HOSTILE = ["words", "reduce_relations", "threaten", "force_posture", "coerce", "assault", "fight"]
# Pairs worth following: this much hostile news over the whole record, or
# any fighting between them since 1989.
MIN_HOSTILE = 5000


def _month_of_pairs(table: pd.DataFrame, gw: dict[str, int]) -> pd.DataFrame:
    """Directed counts -> one row per unordered pair (lower number first),
    with ab_* for the lower-numbered country's actions towards the other,
    and ba_* the other way."""
    table = table.assign(ga=table["a1"].map(gw), gb=table["a2"].map(gw))
    table = table[table["ga"].notna() & table["gb"].notna() & (table["ga"] != table["gb"])]
    table = table.astype({"ga": int, "gb": int})
    forward = table["ga"] < table["gb"]
    values = [c for c in LADDER + ["tone_sum"] if c in table]
    out = []
    for direction, mask in (("ab", forward), ("ba", ~forward)):
        part = table[mask]
        a = np.where(direction == "ab", part["ga"], part["gb"])
        b = np.where(direction == "ab", part["gb"], part["ga"])
        sums = part[values].groupby([a, b]).sum()
        sums.columns = [f"{direction}_{c}" for c in sums.columns]
        out.append(sums)
    joined = out[0].join(out[1], how="outer").fillna(0)
    joined.index.names = ["a", "b"]
    return joined.reset_index()


def news_months(cache: Path, gw: dict[str, int], only: set[str] | None = None) -> pd.DataFrame:
    """Every pair's news, per month: the archive's months to March 2013,
    then the daily files summed into months (just the months in `only`,
    if given)."""
    months: dict[str, list[pd.DataFrame]] = {}
    days_in: dict[str, int] = {}
    if only is None or min(only) <= "2013-03":
        for path in sorted((cache / "pairs" / "history").glob("*.csv.gz")):
            table = pd.read_csv(path, keep_default_na=False)
            for month, rows in table.groupby("month"):
                if only is None or month in only:
                    months.setdefault(month, []).append(rows.drop(columns="month"))
                    days_in[month] = pd.Period(month, freq="M").days_in_month
    for path in sorted((cache / "pairs" / "days").glob("*/*.csv.gz")):
        stamp = path.name[:8]
        month = f"{stamp[:4]}-{stamp[4:6]}"
        if only is not None and month not in only:
            continue
        months.setdefault(month, []).append(pd.read_csv(path, keep_default_na=False))
        days_in[month] = days_in.get(month, 0) + 1
    rows = []
    for month in sorted(months):
        table = pd.concat(months[month], ignore_index=True)
        table = table.groupby(["a1", "a2"], as_index=False).sum(numeric_only=True)
        pairs_ = _month_of_pairs(table, gw)
        pairs_["month"], pairs_["days"] = month, days_in[month]
        pairs_["world_all"] = float(table["all"].sum())
        rows.append(pairs_)
    return pd.concat(rows, ignore_index=True)


def followed(news: pd.DataFrame, fights: pd.DataFrame) -> pd.DataFrame:
    """Keep the pairs with enough friction to follow."""
    hostile = sum(news[f"{d}_{c}"] for d in ("ab", "ba") for c in HOSTILE)
    totals = hostile.groupby([news["a"], news["b"]]).sum()
    keep = set(totals[totals >= MIN_HOSTILE].index) | set(zip(fights["a"], fights["b"]))
    keys = list(zip(news["a"], news["b"]))
    return news[[k in keep for k in keys]].reset_index(drop=True)


# Stored: pairs with at least this much hostile news over the record (the
# rest are noise), plus any that fought; and in the daily updates, any pair
# that suddenly has this much in the months being rebuilt.
MIN_STORED = 2000
MIN_NEW = 200
NEWS_FILE, FIGHTS_FILE, DISPUTES_FILE = "pair_news.csv.gz", "pair_fights.csv", "pair_disputes.csv"


def _hostile_totals(news: pd.DataFrame) -> pd.Series:
    hostile = sum(news[f"{d}_{c}"] for d in ("ab", "ba") for c in HOSTILE)
    return hostile.groupby([news["a"], news["b"]]).sum()


def update_news(data_dir: Path, cache: Path, only: set[str] | None = None) -> pd.DataFrame:
    """Refresh data/pair_news.csv.gz from the cache: every month, or just
    the months in `only`, keeping the stored ones."""
    gw = iso_to_gw(states(data_dir))
    fresh = news_months(cache, gw, only)
    path = data_dir / NEWS_FILE
    fought = set()
    if (data_dir / FIGHTS_FILE).exists():
        table = pd.read_csv(data_dir / FIGHTS_FILE)
        fought = set(zip(table["a"], table["b"]))
    totals = _hostile_totals(fresh)
    if only is not None and path.exists():
        stored = pd.read_csv(path, dtype={"month": str})
        keep = set(zip(stored["a"], stored["b"])) | set(totals[totals >= MIN_NEW].index) | fought
        fresh = fresh[[k in keep for k in zip(fresh["a"], fresh["b"])]]
        fresh = pd.concat([stored[~stored["month"].isin(set(fresh["month"]))], fresh], ignore_index=True)
    else:
        keep = set(totals[totals >= MIN_STORED].index) | fought
        fresh = fresh[[k in keep for k in zip(fresh["a"], fresh["b"])]]
    fresh = fresh.sort_values(["a", "b", "month"])
    fresh.to_csv(path, index=False, float_format="%.6g", compression="gzip")
    return fresh


def save_fights(data_dir: Path, events: pd.DataFrame) -> pd.DataFrame:
    table = fighting(events)
    table.to_csv(data_dir / FIGHTS_FILE, index=False)
    return table


def save_disputes(data_dir: Path) -> pd.DataFrame:
    table = disputes(data_dir / "raw" / "mid")
    table.to_csv(data_dir / DISPUTES_FILE, index=False)
    return table


def load(data_dir: Path):
    """The stored news, fighting and disputes between pairs (None if missing)."""
    out = []
    for name in (NEWS_FILE, FIGHTS_FILE, DISPUTES_FILE):
        path = data_dir / name
        out.append(pd.read_csv(path, dtype={"month": str}) if path.exists() else None)
    return tuple(out)
