import numpy as np
import pandas as pd

from sage import pairs, tension


def test_fighting_between_governments_is_counted_per_pair_and_month():
    events = pd.DataFrame({
        "type_of_violence": [1, 1, 1, 2],
        "side_a": ["Government of Russia (Soviet Union)", "Government of Iraq",
                   "Government of Australia, Government of United Kingdom", "Government of X"],
        "side_b": ["Government of Ukraine", "Government of Kuwait", "Government of Iraq", "Government of Y"],
        "gwnoa": ["365", "645", "900;200", "1"],
        "gwnob": ["369.0", "690", "645", "2"],
        "month": ["2022-02", "1990-08", "2003-03", "2003-03"],
        "best": [500, 100, 50, 9],
    })
    table = pairs.fighting(events).set_index(["a", "b", "month"])["deaths"]
    assert table[(365, 369, "2022-02")] == 500
    assert table[(645, 690, "1990-08")] == 100
    # A coalition counts for each of its members.
    assert table[(200, 645, "2003-03")] == 50 and table[(645, 900, "2003-03")] == 50
    assert len(table) == 4


def test_directed_news_becomes_one_row_per_pair():
    directed = pd.DataFrame({
        "a1": ["RUS", "GBR", "RUS", "XXX"], "a2": ["GBR", "RUS", "UKR", "GBR"],
        "all": [10, 4, 50, 3], "threaten": [3, 1, 9, 1], "tone_sum": [-20.0, -5.0, -90.0, 0.0],
    })
    gw = {"RUS": 365, "GBR": 200, "UKR": 369}
    rows = pairs._month_of_pairs(directed, gw).set_index(["a", "b"])
    # Britain (200) is the lower number: ab is Britain towards Russia.
    assert rows.loc[(200, 365), ["ab_threaten", "ba_threaten"]].tolist() == [1, 3]
    assert rows.loc[(365, 369), ["ab_threaten", "ba_threaten"]].tolist() == [9, 0]
    assert len(rows) == 2   # unknown codes are dropped


def _news(months, threats):
    rows = []
    for m, k in zip(months, threats):
        row = {"a": 200, "b": 365, "month": m, "days": 30, "world_all": 1e6}
        for d in ("ab", "ba"):
            for c in pairs.LADDER + ["tone_sum"]:
                row[f"{d}_{c}"] = 0.0
        row["ba_threaten"] = k
        row["ba_all"] = 100.0
        row["ab_all"] = 100.0
        rows.append(row)
    return pd.DataFrame(rows)


def test_a_surge_in_threats_and_the_answers_line_up():
    months = [f"2020-{m:02d}" for m in range(1, 13)]
    news = _news(months, [10] * 11 + [80])
    fights = pd.DataFrame({"a": [200], "b": [365], "month": ["2020-05"], "deaths": [3]})
    frame = tension.build(news, fights, None).set_index("month")
    assert round(float(np.exp(frame.loc["2020-12", "ba_threaten_surge"])), 1) > 2
    assert frame.loc["2020-12", "climb"] >= 1
    assert frame.loc["2020-02", "y_clash_3"] == 1.0      # May is within 3 months of February
    assert frame.loc["2020-01", "y_clash_3"] == 0.0
    assert frame.loc["2020-07", "since_clash"] == 2


def test_a_pair_summary_reads_plainly():
    row = pd.Series({"a": 200, "b": 365, "climb": 3, "deaths_last12": 0.0,
                     "ba_threaten_surge": np.log(4), "ab_reduce_relations_surge": np.log(3)})
    text = tension.describe(row, {200: "United Kingdom", 365: "Russia"}, 0.003, 0.0004, None)
    assert text.startswith("Tension between United Kingdom and Russia is high.")
    assert "From Russia towards United Kingdom: threats (about 4× usual)" in text
    assert "Deadly clashes between their forces in the next six months: extremely unlikely (under 1%)" in text
    assert "about 8 in 100 military stand-offs" in text


def test_far_apart_strangers_get_no_tension_from_someone_elses_attack():
    # News of Saudi Arabia "fighting" the Philippines in October 2026 was
    # Filipino workers caught in Iranian and Houthi attacks on Saudi Arabia.
    row = pd.Series({"a": 670, "b": 840, "climb": 1, "deaths_last12": 0.0, "since_clash": tension.NEVER,
                     "ab_fight_surge": np.log(3), "border": 0.0, "sea_near": 0.0, "disputes_before": 0.0})
    names = {670: "Saudi Arabia", 840: "Philippines"}
    text = tension.describe(row, names, 0.0007, 0.0, None)
    assert tension.tension_of(row) == "usual"
    assert text.startswith("Tension between Saudi Arabia and Philippines is at its usual level.")
    assert "fighting" not in text
    # Fighting counts between neighbours, or countries that have fought.
    for change in ({"border": 1.0}, {"sea_near": 1.0}, {"since_clash": 30.0}):
        assert tension.tension_of(pd.Series({**row.to_dict(), **change})) == "above usual"
    # With a major power or after a past dispute, threats count but news of
    # fighting doesn't: Americans killed in the Paris attacks aren't the US
    # fighting France.
    for change in ({"a": 2, "b": 220}, {"disputes_before": np.log1p(1)}):
        far = pd.Series({**row.to_dict(), **change})
        assert tension.tension_of(far) == "usual"
        assert "fighting" not in tension.describe(far, {2: "United States", 220: "France", 670: "Saudi Arabia", 840: "Philippines"}, 0.001, 0.0, None)
        threats = pd.Series({**far.to_dict(), "ba_threaten_surge": np.log(3), "climb": 2})
        assert tension.tension_of(threats) == "above usual"
        assert "threats (about 3× usual)" in tension.describe(threats, {2: "United States", 220: "France", 670: "Saudi Arabia", 840: "Philippines"}, 0.001, 0.0, None)


def test_the_line_model_reads_news_spikes_on_a_fixed_scale():
    rows = pd.DataFrame({f: [0.0, 0.0] for f in tension.SIMPLE})
    rows.loc[0, "max_mobilize_surge"] = 9.0  # thousands of times usual
    rows.loc[1, "since_clash"] = tension.NEVER
    x = tension.PairForecaster(tension.SIMPLE)._x(rows)
    assert x[0, tension.SIMPLE.index("max_mobilize_surge")] == tension.SURGE_CAP
    assert np.isclose(x[1, tension.SIMPLE.index("since_clash")], np.log1p(tension.NEVER))


def test_borders_and_past_deadly_disputes_count_only_what_came_before(tmp_path):
    from sage import ties
    (tmp_path / "cow").mkdir()
    (tmp_path / "mid").mkdir()
    border = pd.DataFrame([(a, b, y, kind) for y in range(1990, 2017)
                           for a, b, kind in [(750, 770, 1), (770, 750, 1), (2, 365, 4), (365, 2, 4)]],
                          columns=["state1no", "state2no", "year", "conttype"])
    border.to_csv(tmp_path / "cow" / "contiguity.csv", index=False)
    pd.DataFrame({"disno": [1, 2, 3], "statea": [750, 770, 750], "stateb": [770, 750, 770],
                  "strtyr": [1999, 2001, 2010], "strtmnth": [5, 12, 1], "fatlev": [3, 1, 0]}
                 ).to_csv(tmp_path / "mid" / "dyadic_mid_4.03.csv", index=False)
    frame = pd.DataFrame({"a": [750, 750, 750, 2, 20], "b": [770, 770, 770, 365, 900],
                          "month": ["1999-05", "2000-01", "2020-01", "2020-01", "2020-01"]})
    frame["mi"] = tension.month_index(frame["month"])
    t = ties.build(tmp_path, frame)
    assert t["border"].tolist() == [1, 1, 1, 0, 0]
    assert t["sea_near"].tolist() == [0, 0, 0, 1, 0]
    # The Kargil dispute (May 1999) only counts from the month after it began;
    # the 2010 one wasn't deadly.
    assert np.expm1(t["deadly_disputes_before"]).round().tolist() == [0, 1, 2, 0, 0]
    assert np.expm1(t["disputes_before"]).round().tolist() == [0, 1, 3, 0, 0]
    assert "disputes_before" not in tension.feature_columns(t)
