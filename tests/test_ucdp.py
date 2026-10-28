from sage.ucdp import Source, assign_months, find_sources

PAGE = """
<a href="https://ucdp.uu.se/downloads/ged/ged261-csv.zip">CSV</a>
<a href="https://ucdp.uu.se/downloads/ged/ged261-xlsx.zip">Excel</a>
<a href="https://ucdp.uu.se/downloads/candidateged/GEDEvent_v26_0_9.csv">CSV</a>
<a href="https://ucdp.uu.se/downloads/candidateged/GEDevent_v26_0_9.xlsx">Excel</a>
<a href="https://ucdp.uu.se/downloads/candidateged/GEDEvent_v26_01_26_09.csv">CSV</a>
<a href="https://ucdp.uu.se/downloads/candidateged/GEDEvent_v26_0_10.csv">CSV</a>
"""


def test_finds_every_csv_release():
    found = {(s.filename, s.first, s.last, s.rank) for s in find_sources(PAGE)}
    assert found == {
        ("ged261-csv.zip", "1989-01", "2025-12", 3),
        ("GEDEvent_v26_01_26_09.csv", "2026-01", "2026-09", 2),
        ("GEDEvent_v26_0_9.csv", "2026-09", "2026-09", 1),
        ("GEDEvent_v26_0_10.csv", "2026-10", "2026-10", 1),
    }


def test_each_month_comes_from_the_best_release():
    owner = assign_months(find_sources(PAGE))
    assert owner["1989-01"].filename == "ged261-csv.zip"
    assert owner["2025-12"].filename == "ged261-csv.zip"
    assert owner["2026-01"].filename == "GEDEvent_v26_01_26_09.csv"
    assert owner["2026-09"].filename == "GEDEvent_v26_01_26_09.csv"
    assert owner["2026-10"].filename == "GEDEvent_v26_0_10.csv"


def test_checked_data_beats_candidate_data_for_the_same_month():
    ged = Source("https://x/ged271-csv.zip", "1989-01", "2026-12", 3)
    candidate = Source("https://x/GEDEvent_v26_01_26_12.csv", "2026-01", "2026-12", 2)
    assert assign_months([candidate, ged])["2026-06"] is ged
