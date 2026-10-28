import pandas as pd

from sage import daily


def _strength(days, values, country=369):
    return pd.DataFrame({"country_id": country, "day": days, "aimed": values, "own": 0.0})


def test_alert_days_close_together_are_one_episode():
    days = ["2022-01-01", "2022-01-02", "2022-01-06", "2022-02-01"]
    found = daily.episodes(_strength(days, [1.2, 1.5, 1.1, 1.3]))
    assert found[["start", "end", "days"]].values.tolist() == [
        ["2022-01-01", "2022-01-06", 3], ["2022-02-01", "2022-02-01", 1]]
    assert round(found["peak"].iloc[0], 1) == 1.5


def test_only_the_chosen_kind_raises_alerts():
    days = pd.DataFrame({"country_id": 369, "day": ["2022-01-01"], "aimed": [0.2], "own": [1.5]})
    assert daily.episodes(days).empty
    assert len(daily.episodes(days, "own")) == 1


def test_outcomes_follow_what_happened_in_the_90_days_after():
    found = daily.episodes(_strength(["2021-12-14"], [1.2]))
    deaths = pd.DataFrame({"country_id": 369, "day": ["2021-12-01", "2022-02-24", "2022-03-01"],
                           "state": [3, 900, 900], "nonstate": 0, "gov_civ": 0, "group_civ": 0})
    judged = daily.outcomes(found, deaths, "2022-12")
    assert judged[["fighting", "major_war", "attacks"]].values.tolist() == [["yes", "yes", "no"]]


def test_outcomes_say_already_when_it_was_happening_before():
    found = daily.episodes(_strength(["2022-03-10"], [1.2]))
    deaths = pd.DataFrame({"country_id": 369, "day": ["2022-01-20", "2022-04-01"],
                           "state": [40, 40], "nonstate": 0, "gov_civ": 0, "group_civ": 0})
    judged = daily.outcomes(found, deaths, "2022-12")
    assert judged["fighting"].iloc[0] == "already"


def test_too_recent_to_tell():
    found = daily.episodes(_strength(["2022-12-20"], [1.2]))
    deaths = pd.DataFrame({"country_id": 369, "day": ["2020-01-01"], "state": [1],
                           "nonstate": 0, "gov_civ": 0, "group_civ": 0})
    assert daily.outcomes(found, deaths, "2022-12")["fighting"].iloc[0] == "wait"
