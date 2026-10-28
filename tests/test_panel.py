import pandas as pd

from sage import panel


def _events(rows):
    return pd.DataFrame(rows, columns=["type_of_violence", "side_a", "country", "country_id",
                                       "region", "latitude", "longitude", "month", "best"])


def test_deaths_are_split_by_kind_of_violence():
    events = _events([
        [1, "Government of Sudan", "Sudan", 625, "Africa", 15, 32, "2023-04", 900],
        [2, "RSF militia", "Sudan", 625, "Africa", 15, 32, "2023-04", 30],
        [3, "Government of Sudan", "Sudan", 625, "Africa", 15, 32, "2023-04", 12],
        [3, "RSF", "Sudan", 625, "Africa", 15, 32, "2023-04", 40],
        [3, "RSF", "Sudan", 625, "Africa", 15, 32, "2023-05", 5],
    ])
    rows = panel.by_country_month(events).set_index("month")
    assert rows.loc["2023-04", ["state", "nonstate", "gov_civ", "group_civ", "events"]].tolist() == [900, 30, 12, 40, 4]
    assert rows.loc["2023-05", "group_civ"] == 5


def test_country_names_lose_old_names_in_brackets():
    events = _events([[1, "Government of Russia (Soviet Union)", "Russia (Soviet Union)", 365, "Europe",
                       55, 37, "2022-03", 10]])
    assert panel.countries(events)["name"].tolist() == ["Russia"]


def test_update_keeps_months_the_new_download_no_longer_has(tmp_path):
    first = _events([[1, "Government of X", "X", 1, "Africa", 0, 0, "2026-01", 30],
                     [1, "Government of X", "X", 1, "Africa", 0, 0, "2026-02", 50]])
    panel.update(tmp_path, first)
    second = _events([[1, "Government of X", "X", 1, "Africa", 0, 0, "2026-02", 70],
                      [1, "Government of X", "X", 1, "Africa", 0, 0, "2026-03", 5]])
    table, _ = panel.update(tmp_path, second)
    assert table.set_index("month")["state"].to_dict() == {"2026-01": 30, "2026-02": 70, "2026-03": 5}


def test_months_without_violence_are_zero():
    events = _events([[1, "Government of X", "X", 1, "Africa", 0, 0, "2026-01", 30]])
    full = panel.complete(panel.by_country_month(events), [1, 2], "2026-01", "2026-02")
    assert len(full) == 4
    assert full["state"].sum() == 30
