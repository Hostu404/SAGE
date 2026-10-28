from sage import summary


def _entry(state, group, fight, war, attacks, alert=None):
    return {
        "name": "Ruritania",
        "last_month": {"state": state, "nonstate": 0, "gov_civ": 0, "group_civ": group},
        "alert": alert or {"on": False},
        "fighting": {"chance": [fight] * 3, "usual": [0.01] * 3},
        "major_war": {"chance": [war] * 3, "usual": [0.001] * 3},
        "attacks": {"chance": [attacks] * 3, "usual": [0.01] * 3},
    }


def test_words_for_chances():
    assert summary.word(0.97) == "almost certain"
    assert summary.word(0.5) == "about as likely as not"
    assert summary.word(0.07) == "unlikely"
    assert summary.word(0.001) == "extremely unlikely"


def test_a_calm_country_with_raised_risk():
    text = summary.country(_entry(0, 0, 0.12, 0.001, 0.02), "2026-09", None, None, None)
    assert text.startswith("Ruritania was largely calm in September.")
    assert "Deadly fighting in November is unlikely (12%)" in text
    assert "12 times what's usual" in text


def test_a_country_at_war_leaning_worse_with_an_alert():
    alert = {"on": True, "since": "2026-10-03", "kind": "aimed", "times_usual": 4.2}
    record = {"questions": {"fighting": {"alerts": 11, "followed": 5},
                            "major_war": {"alerts": 60, "followed": 8}}}
    text = summary.country(_entry(300, 40, 0.97, 0.3, 0.8, alert), "2026-09", 0.6,
                           (5.0, "troop mobilisations and military alerts"), record)
    assert "Fighting is going on in Ruritania: 300 people were killed" in text
    assert "escalation to full-scale war is a real possibility (30%)" in text
    assert "It's leaning worse: deaths are up 60%" in text
    assert "about 5 times the usual level" in text
    # Already fighting, so the record quoted is about escalation.
    assert "other countries' forces massing against it" in text
    assert "it escalated to full-scale war within three months in 8 of 60 cases" in text
    assert "Armed groups killing 10 or more civilians in November is very likely (80%)" in text


def test_world_overview_mentions_alerts_or_their_absence():
    lines = summary.world([_entry(0, 0, 0.12, 0.001, 0.02)], "2026-09")
    assert any("No build-up alerts" in line for line in lines)
