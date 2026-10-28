# SAGE

Free, open forecasts of war and attacks on civilians, with every
forecast scored in public.

For each country, every month, SAGE gives the chance of:

| Question | Means |
|---|---|
| **Deadly fighting** | At least 25 people killed in fighting involving the government |
| **Major war** | At least 1,000 people killed in fighting involving the government |
| **Attacks on civilians** | Armed groups kill at least 10 civilians |

for the month in progress, next month and the month after, with a short
plain-English summary of where each country stands and which way it's
leaning, for example:

> Fighting is going on in Ethiopia: 563 people were killed in fighting
> involving the government in September. More deadly fighting in November
> is almost certain (96%), and escalation to full-scale war is unlikely
> (16%). It's leaning worse: deaths are up 37% on the three months before
> and the news shows fighting at about 7 times the usual level.

## How good is it?

See **[reports/backtest.md](reports/backtest.md)**: SAGE replays every
month since 2018 using only what was known at the time, and checks itself
against what happened. It's rerun automatically each time new data comes
out, so the numbers there are always the current ones. It also compares
SAGE with ViEWS, the early-warning system run by Uppsala University and
PRIO, on the same countries and months.

What it can and can't do, honestly:

- **Ongoing wars and attack campaigns:** reliable, and better than
  assuming nothing changes.
- **Wars that start with a visible build-up:** SAGE checks the news every
  day and raises a **build-up alert** when other countries' forces are
  mobilising against a country, showing force at it and threatening it,
  all far above usual at once. Ukraine's alert started on 25 November 2021,
  91 days before the invasion, and stayed on until it. The report lists
  every alert since 2014 with what followed, false alarms included. An
  alert says "this looks like past run-ups to war", not "war is coming".
- **Wars that start with little warning in the news**, like Sudan's in
  April 2023: missed, and the report shows it.
- **Surprise attacks planned in secret** (like 7 October): no forecaster
  working from public information can catch these reliably, SAGE included.

Every month's forecasts are kept unchanged in `forecasts/history/`, so the
live record can be checked against what happened.

## Between countries

SAGE also follows about 800 pairs of countries with real friction between
them (Russia and the UK, China and Taiwan, India and Pakistan...). Each
month it reads what each side does towards the other in the news, step by
step up the escalation ladder: criticism and demands, sanctions and
expulsions, threats, threats of force, shows of force, mobilisation,
attacks, fighting. It compares that with the pair's usual, takes into
account whether the two share a border and how many deadly disputes
they've had since 1816 (most fighting between countries is between
neighbours with a history), and forecasts two things for the next six
months: **deadly clashes** between their
forces (at least one death in fighting between the two governments) and
**war** (1,000 or more deaths in a month).

Replayed from 2008 to now, its chances for clashes and wars are clearly
better than assuming only pairs that fought recently will fight again. It
picks up build-ups: a month before Russia took Crimea in 2014, and a month
before the US strikes on Venezuela in 2026, those pairs had the highest
chance of any pair with no history of fighting each other, and a month
before the full invasion of Ukraine in 2022 Russia–Ukraine was 8th of the
784 pairs it follows, behind seven that had fought in recent years. But
the chances it gives stay small (1–3%), because most build-ups never turn
into fighting, and sudden clashes with no build-up in the news (China and
India in the Galwan valley, 2020) still get low numbers. The report has
the details.

Wars between countries are rare, and there's never been one between some
of the pairs people worry about most, so for those SAGE reports how tense
things are against their usual and keeps its chances honest rather than
dramatic: since 1990, about 8 in 100 military stand-offs between countries
led to deaths, and about 1 in 100 to war.

## The forecast file

`forecasts/latest.json` starts with `summary`, a few lines for the top of
an app (most likely wars, highest new risks, alerts on now), then holds,
for each country:

```
"name": "Ethiopia", "region": "Africa",
"summary": "Fighting is going on in Ethiopia: ...",
"last_month": {"state": 563, "nonstate": 30, "gov_civ": 1, "group_civ": 15},
"alert": {"on": false},
"fighting": {
  "chance": [0.978, 0.958, 0.959],   // this month, next month, the month after
  "usual":  [0.892, 0.872, 0.865],   // what's usual for a country in its situation
  "rank":   [5, 9, 7],               // 1 = highest chance in the world
  "why": ["563 killed in fighting involving the government in September 2026, ..."]
},
"major_war": {...}, "attacks": {...}
```

When an alert is on: `"alert": {"on": true, "since": "2026-10-12",
"last": "2026-10-25", "times_usual": 4.7}`. `months` names the three
months, `alert_record` is the alerts' track record and `record` the
backtest's headline scores. Each country also has `relations`: its most
important pairs, each with `with`, `tension` ("usual", "above usual",
"high", "very high", "fighting", "at war"), `clash_6` and `war_6` (chances
within six months) and a `summary`. The full list is under `pairs`.

## Where the numbers come from

- **Conflict deaths:** the [Uppsala Conflict Data Program](https://ucdp.uu.se)
  (UCDP): its checked yearly data (GED) and its monthly candidate data for
  the months since. Free, CC BY 4.0.
- **News:** the [GDELT Project](https://www.gdeltproject.org), which reads
  world news and logs each reported event: daily since April 2013, plus its
  archive back to 1988. SAGE reads GDELT's original daily files and, for
  any day they're missing (or if they ever stop), builds the day from
  GDELT 2.0's quarter-hour files instead, which hold the same events. SAGE counts, per country, reports of troop
  mobilisations, military alerts, threats of force, fighting, bombings and
  attacks, both by a country's own forces and by other countries aimed at
  it, compared with that country's usual.
- **Model:** gradient-boosted trees (scikit-learn), plus a simpler model
  for outbreaks in quiet countries, retrained each run. It has to beat two
  simple forecasts to be worth anything: the base rate and "same as last
  month".
- **Benchmark:** [ViEWS](https://viewsforecasting.org)'s monthly forecasts,
  as published at the time. Averaging them in was tested and made SAGE
  worse, so they're only used for comparison.

No paid services, no API keys.

## Files

| Path | What |
|---|---|
| `forecasts/latest.json` | Today's forecasts, for the app and website (also served at hostu404.github.io/SAGE/forecasts/latest.json) |
| `forecasts/history/` | Each month's forecasts, kept as first published |
| `reports/backtest.md` | How SAGE would have done since 2018, and every build-up alert |
| `data/country_month.csv` | Deaths per country per month since 1989 (from UCDP) |
| `data/deaths_daily.csv` | Deaths per country per day since 2013, to judge the alerts (from UCDP) |
| `data/news_month.csv`, `data/news_early.csv` | News counts per country per month, and per month's first fortnight (from GDELT) |
| `data/alert_days.csv`, `data/alerts.json` | Every day a build-up was far above usual, and the last day checked |
| `data/views.csv` | ViEWS's forecasts as published, for comparison |
| `data/pair_news.csv.gz` | Each pair of countries' actions towards each other per month since 1988, by step of the escalation ladder (from GDELT) |
| `data/pair_fights.csv` | Deaths in fighting between two governments per month since 1989 (from UCDP) |
| `data/pair_disputes.csv` | Military disputes between countries per month, 1988–2014 (Correlates of War) |
| `android/` | The SAGE app for Android phones (see its README) |

## Running it

Needs Python 3.11 or newer.

```
pip install -e ".[test]"
python -m sage update      # fetch new data, rerun the backtest if needed, forecast
python -m pytest -q
```

The first run downloads UCDP's data (about 50 MB). To rebuild the news
history from scratch: `python -m sage news --history` (GDELT's archive and
every day since 2013, about 15 GB downloaded over a couple of hours; each
file is reduced to a few kilobytes and deleted).

The **Daily forecast** GitHub Action runs `python -m sage update` every
morning and commits the results, free on a public repo.

## Licence

SAGE is free to use, copy and build on, as long as you credit it.

- **Code:** Apache License 2.0 ([LICENSE](LICENSE)). Copies must keep the
  copyright notice and the [NOTICE](NOTICE) file, which credits SAGE.
- **Forecasts, reports and data:** Creative Commons Attribution 4.0
  ([LICENSE-CONTENT](LICENSE-CONTENT)). Share them with the credit
  "SAGE (https://github.com/Hostu404/SAGE)".

The data SAGE is built on stays under its sources' own terms (below).

## Credits

- Davies, Pettersson & Öberg (2026), *Organized violence 1989–2025, and
  violent political protests*, Journal of Peace Research; Sundberg &
  Melander (2013), *Introducing the UCDP Georeferenced Event Dataset*,
  Journal of Peace Research 50(4); Hegre, Croicu, Eck & Högbladh (2020),
  *Introducing the UCDP Candidate Events Dataset*, Research & Politics.
- The GDELT Project, www.gdeltproject.org.
- Hegre et al. (2019), *ViEWS: A political violence early-warning system*,
  Journal of Peace Research 56(2); viewsforecasting.org.
- Maoz, Johnson, Kaplan, Ogunkoya & Shreve (2019), *The Dyadic Militarized
  Interstate Disputes (MIDs) Dataset Version 3.0*, Journal of Conflict
  Resolution 63(3); Correlates of War Project, dyadic MID 4.03.
- Stinnett, Tir, Schafer, Diehl & Gochman (2002), *The Correlates of War
  Project Direct Contiguity Data, Version 3*, Conflict Management and Peace
  Science 19(2); Correlates of War Project, Direct Contiguity 3.2.

SAGE is a statistical forecast, not a warning service. For travel and
safety decisions, follow official advice.
