import io
import zipfile
from datetime import date

import pandas as pd

from sage import gdelt


def _row(actor1, actor2, base, root, quad, where, mentions=3, tone=-2.0, date="20220115"):
    cols = [""] * 58
    cols[1] = date
    cols[7], cols[17], cols[26], cols[27], cols[28] = actor1, actor2, base + "0", base, root
    cols[29], cols[31], cols[34], cols[51] = quad, str(mentions), str(tone), where
    return "\t".join(cols)


def _zip(rows):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("20220115.export.CSV", "\n".join(rows) + "\n")
    return buf.getvalue()


def test_a_day_is_counted_by_place_and_by_actor_country():
    raw = _zip([
        _row("RUS", "UKR", "154", "15", "4", "UP"),   # Russia mobilises, reported in Ukraine
        _row("RUS", "RUS", "138", "13", "3", "RS"),   # threat, Russian actors only
        _row("UKR", "", "190", "19", "4", "UP"),      # fighting in Ukraine
    ])
    day = gdelt.daily_counts(raw).set_index(["kind", "code"])
    assert day.loc[("where", "UP"), ["all", "mobilize", "force_posture", "fight"]].tolist() == [2, 1, 1, 1]
    assert day.loc[("actor", "RUS"), ["all", "mobilize", "threaten_force"]].tolist() == [2, 1, 1]
    assert day.loc[("actor", "UKR"), ["all", "mobilize", "fight"]].tolist() == [2, 1, 1]
    assert day.loc[("where", "UP"), "mentions_mobilize"] == 3


def test_actions_aimed_at_a_country_count_for_it():
    raw = _zip([_row("RUS", "UKR", "154", "15", "4", "RS"), _row("RUS", "RUS", "138", "13", "3", "RS")])
    day = gdelt.daily_counts(raw).set_index(["kind", "code"])
    assert day.loc[("target", "UKR"), ["all", "mobilize"]].tolist() == [1, 1]
    assert ("target", "RUS") not in day.index


def test_the_archive_is_counted_by_month_and_half_month(tmp_path):
    rows = [_row("RUS", "UKR", "154", "15", "4", "UP", date="20080803"),
            _row("RUS", "GEO", "190", "19", "4", "GG", date="20080808"),
            _row("RUS", "GEO", "190", "19", "4", "GG", date="20080820")]
    path = tmp_path / "2008.zip"
    path.write_bytes(_zip(rows))
    counts, pairs = gdelt.history_counts(path)
    counts = counts.set_index(["month", "part", "kind", "code"])
    assert counts.loc[("2008-08", "early", "where", "GG"), "fight"] == 1
    assert counts.loc[("2008-08", "late", "target", "GEO"), "fight"] == 1
    pairs = pairs.set_index(["month", "a1", "a2"])
    assert pairs.loc[("2008-08", "RUS", "GEO"), "fight"] == 2
    assert pairs.loc[("2008-08", "RUS", "UKR"), "mobilize"] == 1


def test_events_between_countries_go_up_the_ladder():
    events = pd.DataFrame({
        "actor1": ["RUS", "RUS", "RUS", "GBR", "RUS", ""],
        "actor2": ["GBR", "GBR", "GBR", "RUS", "RUS", "GBR"],
        "base": ["138", "163", "042", "112", "190", "190"],
        "root": ["13", "16", "04", "11", "19", "19"],
        "quad": ["3", "4", "1", "3", "4", "4"],
        "tone": ["-5", "-4", "2", "-3", "-8", "-8"],
    })
    pairs = gdelt.count_pairs(events).set_index(["a1", "a2"])
    assert pairs.loc[("RUS", "GBR"), ["all", "threaten", "threaten_force", "reduce_relations", "cooperate"]].tolist() == [3, 1, 1, 1, 1]
    assert pairs.loc[("GBR", "RUS"), "words"] == 1
    assert len(pairs) == 2   # no pair for a country with itself, or a missing side


def test_days_add_up_into_country_months(tmp_path):
    for d in (date(2022, 1, 3), date(2022, 1, 20)):
        path = gdelt.day_path(tmp_path, d)
        path.parent.mkdir(parents=True, exist_ok=True)
        gdelt.daily_counts(_zip([_row("RUS", "UKR", "154", "15", "4", "UP")])).to_csv(
            path, index=False, compression="gzip")
    whole = gdelt.country_months(tmp_path).set_index("country_id")
    assert whole.loc[369, "where_mobilize"] == 2
    assert whole.loc[369, "days"] == 2
    assert whole.loc[365, "actor_mobilize"] == 2   # Russia, by actor
    early = gdelt.country_months(tmp_path, last_day=gdelt.EARLY_DAYS).set_index("country_id")
    assert early.loc[369, "where_mobilize"] == 1
    assert early.loc[369, "days"] == 1


def test_gaza_counts_for_israel_as_in_ucdp():
    from sage.codes import lookup
    assert lookup()[("where", "GZ")] == 666
    assert lookup()[("actor", "PSE")] == 666


def test_days_come_from_gdelt_1_while_it_lasts_then_gdelt_2():
    from datetime import date, timedelta
    today = date(2026, 10, 27)
    days = lambda a, b: {a + timedelta(n) for n in range((b - a).days + 1)}
    v1 = days(date(2026, 10, 1), date(2026, 10, 26)) - {date(2026, 10, 10)}
    sources = gdelt.plan(v1, date(2026, 10, 1), date(2026, 10, 26), today, lambda d: False)
    assert sources[date(2026, 10, 9)] == "v1"
    assert sources[date(2026, 10, 10)] == "v2"   # a gap in GDELT 1.0
    # GDELT 1.0 stopped a week ago: everything since comes from GDELT 2.0.
    v1 = days(date(2026, 10, 1), date(2026, 10, 19))
    sources = gdelt.plan(v1, date(2026, 10, 1), date(2026, 10, 26), today, lambda d: False)
    assert sources[date(2026, 10, 26)] == "v2"
    # Before GDELT 2.0 began, a missing day stays missing.
    assert gdelt.plan(set(), date(2015, 1, 1), date(2015, 1, 2), today, lambda d: False) == {}
