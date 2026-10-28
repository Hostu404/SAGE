"""SAGE's commands. Run from the repo folder:

  python -m sage update     everything the daily job does (below, in order)
  python -m sage fetch      new UCDP conflict data, if any
  python -m sage news       new GDELT news days, and today's build-up alerts
  python -m sage views      new ViEWS forecasts, if any
  python -m sage backtest   replay 2018 to now and write reports/
  python -m sage forecast   write forecasts/latest.json
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import warnings
from datetime import date
from pathlib import Path

import pandas as pd

from . import backtest, daily, features, forecast, gdelt, pairs, panel, report, tension, ucdp, views

# scikit-learn's worker threads repeat a harmless setup warning thousands
# of times per backtest.
warnings.filterwarnings("ignore", message=".*sklearn.utils.parallel.delayed.*")
os.environ.setdefault("PYTHONWARNINGS", "ignore::UserWarning")

DATA = Path("data")
RAW = DATA / "raw"
REPORTS = Path("reports")
FORECASTS = Path("forecasts")


def cmd_fetch(args) -> bool:
    """Returns True if the conflict data changed."""
    page = ucdp.fetch_page()
    names = sorted(s.filename for s in set(ucdp.assign_months(ucdp.find_sources(page)).values()))
    seen = DATA / "sources.json"
    if not args.force and seen.exists() and json.loads(seen.read_text()) == names:
        print("UCDP: nothing new")
        return False
    events = ucdp.download(RAW / "ucdp", page)
    table, _ = panel.update(DATA, events)
    pairs.save_fights(DATA, events)
    seen.write_text(json.dumps(names, indent=1))
    print(f"UCDP: {len(events):,} events, data up to {table['month'].max()}")
    return True


def cmd_news(args) -> None:
    cache = RAW / "gdelt"
    if args.history:
        gdelt.backfill_history(cache, workers=args.workers)
    if args.history or args.since:
        gdelt.backfill(cache, first=date.fromisoformat(args.since or "2013-04-01"), workers=args.workers)
        gdelt.rebuild(DATA, cache)
        if not (DATA / pairs.DISPUTES_FILE).exists():
            pairs.save_disputes(DATA)
        pairs.update_news(DATA, cache)
        print("News: rebuilt every month")
    else:
        months = gdelt.update(DATA, cache, workers=args.workers)
        pairs.update_news(DATA, cache, set(months))
        print(f"News: rebuilt {months[0]} to {months[-1]}")
    news, _ = gdelt.load(DATA)
    _, countries = panel.load(DATA)
    last = daily.update(DATA, cache, news, countries["country_id"])
    print(f"Build-up alerts checked to {last}")


def cmd_views(args) -> None:
    table = views.update(DATA)
    print(f"ViEWS: runs to {table['made'].max()}")


def _deaths():
    path = DATA / "deaths_daily.csv"
    return pd.read_csv(path, dtype={"day": str}) if path.exists() else None


def _frame():
    table, countries = panel.load(DATA)
    news, early = gdelt.load(DATA)
    frame = features.build(table, countries, news, early, views.load(DATA))
    return frame, countries, table["month"].max()


def _pairs_frame():
    news, fights, _ = pairs.load(DATA)
    if news is None or fights is None:
        return None, None
    table, _ = panel.load(DATA)
    return tension.build(pairs.followed(news, fights), fights, table, RAW), fights


def _names() -> dict[int, str]:
    names = dict(zip(pairs.states(DATA)["gw"], pairs.states(DATA)["name"]))
    _, countries = panel.load(DATA)
    names.update(dict(zip(countries["country_id"], countries["name"])))
    return {int(k): v for k, v in names.items()}


def _iso3() -> dict[int, str]:
    states = pairs.states(DATA)
    # UCDP numbers Serbia 345 (as Yugoslavia was); the states table, 340.
    return {**dict(zip(states["gw"].astype(int), states["iso3"])), 345: "SRB"}


def cmd_backtest(args) -> None:
    frame, countries, through = _frame()
    results = backtest.run(frame, first_year=args.from_year)
    summary = backtest.summarize(results)
    episodes, record, _ = daily.load(DATA, _deaths(), through)
    pair_frame, _ = _pairs_frame()
    pair_results = tension.backtest(pair_frame) if pair_frame is not None else None
    report.write(results, summary, countries, through, REPORTS, episodes, record, pair_results, _names())
    for s in summary:
        if s["ahead"] == report.HEADLINE_AHEAD:
            print(f"{s['title']}: {s['skill_vs_last_month']:+.0%} vs last month")


def cmd_forecast(args) -> None:
    frame, countries, through = _frame()
    record = forecast.track_record(REPORTS / "backtest.json")
    _, alert_record, alerts_now = daily.load(DATA, _deaths(), through)
    news, fights, _ = pairs.load(DATA)
    pair_forecasts = (tension.make(news, fights, panel.load(DATA)[0], _names(), RAW)
                      if news is not None and fights is not None else None)
    result = forecast.make(frame, countries, record, alerts_now, alert_record, pair_forecasts, _iso3())
    # The month's history copy waits until the first fortnight of news is in.
    keep = forecast.news_complete(result["months"][0]) or args.force
    for path in forecast.save(result, FORECASTS, keep=keep, force=args.force):
        print(f"wrote {path}")


def cmd_update(args) -> None:
    changed = cmd_fetch(args)
    cmd_news(args)
    cmd_views(args)
    if changed or not (REPORTS / "backtest.json").exists():
        cmd_backtest(args)
    cmd_forecast(args)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="sage", description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("update", "fetch", "news", "views", "backtest", "forecast"):
        p = sub.add_parser(name)
        p.add_argument("--force", action="store_true",
                       help="fetch: download even if nothing new; forecast: rewrite this month's history file")
        p.add_argument("--since", help="news: fetch daily news from this day (YYYY-MM-DD) and rebuild every month")
        p.add_argument("--history", action="store_true",
                       help="news: also fetch the 1988-2013 archive (a few GB, processed and deleted)")
        p.add_argument("--workers", type=int, default=4, help="news: downloads at once")
        p.add_argument("--from-year", type=int, default=2018, help="backtest: first year to replay")
    args = parser.parse_args(argv)
    {"update": cmd_update, "fetch": cmd_fetch, "news": cmd_news, "views": cmd_views,
     "backtest": cmd_backtest, "forecast": cmd_forecast}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
