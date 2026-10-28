import numpy as np
import pandas as pd
import pytest

from sage.ucdp import months_between


@pytest.fixture
def countries():
    return pd.DataFrame({
        "country_id": [365, 369, 625, 666],
        "name": ["Russia", "Ukraine", "Sudan", "Israel"],
        "region": ["Europe", "Europe", "Africa", "Middle East"],
        "lat": [55.7, 48.5, 15.5, 31.5],
        "lon": [37.6, 35.0, 32.5, 34.8],
    })


@pytest.fixture
def table(countries):
    """Ten years of made-up monthly deaths: some quiet, some at war."""
    rng = np.random.default_rng(1)
    months = months_between("1990-01", "1999-12")
    rows = []
    for i, cid in enumerate(countries["country_id"]):
        level = [0, 40, 400, 5][i]
        for m in months:
            war = rng.random() < 0.6
            rows.append({
                "country_id": cid, "month": m,
                "state": int(rng.poisson(level) if war else 0),
                "nonstate": int(rng.poisson(2)),
                "gov_civ": int(rng.poisson(1)),
                "group_civ": int(rng.poisson(level / 8) if war else 0),
                "events": int(rng.poisson(level / 4 + 1)),
            })
    return pd.DataFrame(rows)
