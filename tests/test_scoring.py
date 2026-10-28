import pytest

from sage import scoring


def test_brier_and_skill():
    assert scoring.brier([1, 0], [1, 0]) == 0
    assert scoring.brier([0.5, 0.5], [1, 0]) == pytest.approx(0.25)
    assert scoring.skill(0.05, 0.1) == pytest.approx(0.5)


def test_calibration_groups_forecasts_into_bands():
    table = scoring.calibration([0.01, 0.01, 0.7, 0.75], [0, 0, 1, 0])
    assert table["forecasts"].tolist() == [2, 2]
    assert table["happened"].tolist() == [0.0, 0.5]


def test_auc_needs_both_outcomes():
    assert scoring.auc([0.2, 0.3], [0, 0]) is None
    assert scoring.auc([0.2, 0.8], [0, 1]) == 1.0
