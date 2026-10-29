"""What sets some pairs of countries apart from the start: whether they
share a border or sit close across the sea, and how many deadly military
disputes they've had since 1816 (Correlates of War, free). Most fighting
between countries is between neighbours with a history.

Tested and left out: alliances and the balance of power. The alliance
record stops in 2012 (it would still call Russia and Ukraine allies),
and neither improved the replay.

Contiguity is taken as of the year before the forecast, or 2016 after
that; disputes count only those begun before the forecast month (the
record ends in 2014). So a replay never sees later years.
"""

from __future__ import annotations

import io
import urllib.request
import zipfile
from pathlib import Path

import numpy as np
import pandas as pd

from .pairs import COW_TO_GW, USER_AGENT, mid_file

CONTIGUITY_URL = "https://correlatesofwar.org/wp-content/uploads/DirectContiguity320.zip"
FEATURES = ["border", "sea_near", "deadly_disputes_before"]


def contiguity_file(raw_dir: Path) -> Path:
    path = raw_dir / "contiguity.csv"
    if not path.exists():
        request = urllib.request.Request(CONTIGUITY_URL, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=300) as response:
            data = response.read()
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            name = next(n for n in z.namelist() if n.endswith("contdird.csv") and "MACOSX" not in n)
            raw_dir.mkdir(parents=True, exist_ok=True)
            path.write_bytes(z.read(name))
    return path


def _ordered(table: pd.DataFrame, x: str, y: str) -> pd.DataFrame:
    a = table[x].astype(int).map(lambda c: COW_TO_GW.get(c, c))
    b = table[y].astype(int).map(lambda c: COW_TO_GW.get(c, c))
    return table.assign(a=np.minimum(a, b), b=np.maximum(a, b))


def build(raw_dir: Path, frame: pd.DataFrame) -> pd.DataFrame:
    """The tie features for every row of a pairs x months frame
    (columns a, b, mi). raw_dir holds cow/ and mid/."""
    out = pd.DataFrame(index=frame.index)

    # 1 = land or river border; 2-5 = up to 400 miles of water apart.
    cont = _ordered(pd.read_csv(contiguity_file(raw_dir / "cow")), "state1no", "state2no")
    cont = cont.groupby(["a", "b", "year"])["conttype"].min()
    year = np.minimum(frame["mi"].to_numpy() // 12 - 1, int(cont.index.get_level_values("year").max()))
    kind = cont.reindex(pd.MultiIndex.from_arrays([frame["a"], frame["b"], year])).to_numpy()
    out["border"] = (kind == 1).astype(float)
    out["sea_near"] = ((kind >= 2) & (kind <= 5)).astype(float)

    mid = pd.read_csv(mid_file(raw_dir / "mid"), low_memory=False).drop_duplicates(["disno", "statea", "stateb"])
    mid = _ordered(mid, "statea", "stateb")
    mid = mid.assign(start=mid["strtyr"] * 12 + mid["strtmnth"].clip(lower=1) - 1).sort_values("start")
    rows_of = frame.groupby(["a", "b"]).indices

    def before(disputes: pd.DataFrame) -> np.ndarray:
        """How many of these disputes each row's pair had begun before its month."""
        n = np.zeros(len(frame))
        for pair, d in disputes.groupby(["a", "b"]):
            rows = rows_of.get(pair)
            if rows is not None:
                n[rows] = np.searchsorted(d["start"].to_numpy(), frame["mi"].to_numpy()[rows], side="left")
        return n

    out["deadly_disputes_before"] = np.log1p(before(mid[mid["fatlev"] >= 1]))
    # Any dispute at all, deadly or not: not a model feature, but one of the
    # signs that two countries could come to blows (tension.relevant).
    out["disputes_before"] = np.log1p(before(mid))
    return out
