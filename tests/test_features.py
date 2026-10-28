import numpy as np
import pandas as pd

from sage import features


def _news(table, seed):
    rng = np.random.default_rng(seed)
    cols = {}
    for kind, cats in features.NEWS.items():
        for cat in cats:
            cols[f"{kind}_{cat}"] = rng.integers(0, 50, len(table))
        for m in features.NEWS_MENTIONS:
            cols[f"{kind}_mentions_{m}"] = rng.integers(0, 500, len(table))
        cols[f"{kind}_all"] = rng.integers(50, 500, len(table))
        cols[f"{kind}_tone_sum"] = rng.normal(0, 50, len(table))
    news = pd.DataFrame({"country_id": table["country_id"], "month": table["month"], **cols})
    news["days"] = 30
    news["world_all"] = 100000
    return news


def test_answers_line_up_with_the_months_they_ask_about(table, countries):
    frame = features.build(table, countries).set_index(["country_id", "month"])
    now, later = frame.loc[(625, "1995-03")], frame.loc[(625, "1995-05")]
    assert now["y_fighting_2"] == float(later["state"] >= 25)
    assert now["y_attacks_2"] == float(later["group_civ"] >= 10)
    assert np.isnan(frame.loc[(625, "1999-12"), "y_fighting_1"])


def test_features_never_see_the_future(table, countries):
    cut = "1995-06"
    news, early = _news(table, 1), _news(table, 2)
    before = features.build(table, countries, news, early)

    changed = table.copy()
    after_cut = changed["month"] > cut
    changed.loc[after_cut, ["state", "group_civ", "events"]] = 99999
    news2, early2 = news.copy(), early.copy()
    news2.loc[news2["month"] > cut, "where_mobilize"] = 12345
    # The first fortnight of the month after the cut is known at the cut.
    early2.loc[early2["month"] > "1995-07", "where_mobilize"] = 12345
    after = features.build(changed, countries, news2, early2)

    cols = features.feature_columns(before)
    known = before["month"] <= cut
    pd.testing.assert_frame_equal(before.loc[known, cols], after.loc[known, cols])


def test_months_since_and_runs(countries):
    months = ["2000-01", "2000-02", "2000-03", "2000-04"]
    table = pd.DataFrame({
        "country_id": 625, "month": months, "state": [30, 30, 0, 0],
        "nonstate": 0, "gov_civ": 0, "group_civ": 0, "events": 1,
    })
    frame = features.build(table, countries[countries["country_id"] == 625])
    assert frame["run_fighting"].tolist() == [features.NEVER, features.NEVER, 0, 0]
    assert frame["since_fighting"].tolist() == [0, 0, 1, 2]


def test_views_forecasts_come_from_the_run_before(table, countries):
    views = pd.DataFrame({
        "made": ["1995-04", "1995-04", "1995-05"],
        "month": ["1995-06", "1995-07", "1995-07"],
        "country_id": [369, 369, 369],
        "views_fighting": [0.3, 0.4, 0.9],
        "views_deaths_ln": [1.0, 1.0, 1.0],
        "run": "x",
    })
    frame = features.build(table, countries, views=views).set_index(["country_id", "month"])
    # Forecasting at the end of May 1995: the run on data to April is the one out.
    row = frame.loc[(369, "1995-05")]
    assert row["views_fighting_1"] == 0.3 and row["views_fighting_2"] == 0.4
    assert "views_fighting_1" not in features.feature_columns(frame.reset_index())
