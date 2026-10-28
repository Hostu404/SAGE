import json
from datetime import date

from sage import features, forecast


def test_every_country_gets_three_months_and_reasons(table, countries):
    frame = features.build(table, countries)
    result = forecast.make(frame, countries)
    assert result["data_through"] == "1999-12"
    assert result["months"] == ["2000-01", "2000-02", "2000-03"]
    assert len(result["countries"]) == 4
    for c in result["countries"]:
        for key in features.QUESTIONS:
            assert len(c[key]["chance"]) == len(c[key]["usual"]) == len(c[key]["rank"]) == 3
            assert all(1 <= r <= 4 for r in c[key]["rank"])
            assert all(0 <= p <= 1 for p in c[key]["chance"])
            assert c[key]["why"]
        assert c["summary"].endswith(".") and c["name"] in c["summary"]
    assert result["summary"]


def test_history_is_written_once_and_kept(tmp_path):
    first = {"data_through": "2026-09", "v": 1}
    forecast.save(first, tmp_path, keep=True)
    forecast.save({"data_through": "2026-09", "v": 2}, tmp_path, keep=True)
    assert json.loads((tmp_path / "history" / "2026-09.json").read_text())["v"] == 1
    assert json.loads((tmp_path / "latest.json").read_text())["v"] == 2


def test_history_waits_for_the_first_fortnight_of_news(tmp_path):
    assert not forecast.news_complete("2026-10", today=date(2026, 10, 9))
    assert forecast.news_complete("2026-10", today=date(2026, 10, 15))
    forecast.save({"data_through": "2026-09"}, tmp_path, keep=False)
    assert not (tmp_path / "history" / "2026-09.json").exists()



def test_every_country_in_the_file_gets_its_iso_code():
    entries = [{"id": 200}, {"id": 345}]
    pairs_ = {"pairs": [{"a": 220, "b": 365}]}
    codes = forecast._codes(entries, pairs_, {200: "GBR", 220: "FRA", 345: "SRB", 365: "RUS", 2: "USA"})
    assert codes == {"200": "GBR", "220": "FRA", "345": "SRB", "365": "RUS"}
