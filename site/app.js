// SAGE's website. Reads the day's forecast (forecasts/latest.json, written by
// the daily run) and shows it the way the app does. No libraries, no cookies,
// no tracking.
"use strict";

const DATA_URL = "forecasts/latest.json";
const APP_URL = "https://github.com/Hostu404/SAGE/releases/latest/download/app-release.apk";
const REPO_URL = "https://github.com/Hostu404/SAGE";
// The country lookup the app uses too: it answers with "loc=GB".
const LOOKUP_URL = "https://www.cloudflare.com/cdn-cgi/trace";
// Two-letter country code followed by three-letter code, for every country.
const ISO = "ADAND AEARE AFAFG AGATG AIAIA ALALB AMARM AOAGO AQATA ARARG ASASM ATAUT AUAUS AWABW AXALA AZAZE BABIH BBBRB BDBGD BEBEL BFBFA BGBGR BHBHR BIBDI BJBEN BLBLM BMBMU BNBRN BOBOL BQBES BRBRA BSBHS BTBTN BVBVT BWBWA BYBLR BZBLZ CACAN CCCCK CDCOD CFCAF CGCOG CHCHE CICIV CKCOK CLCHL CMCMR CNCHN COCOL CRCRI CUCUB CVCPV CWCUW CXCXR CYCYP CZCZE DEDEU DJDJI DKDNK DMDMA DODOM DZDZA ECECU EEEST EGEGY EHESH ERERI ESESP ETETH FIFIN FJFJI FKFLK FMFSM FOFRO FRFRA GAGAB GBGBR GDGRD GEGEO GFGUF GGGGY GHGHA GIGIB GLGRL GMGMB GNGIN GPGLP GQGNQ GRGRC GSSGS GTGTM GUGUM GWGNB GYGUY HKHKG HMHMD HNHND HRHRV HTHTI HUHUN IDIDN IEIRL ILISR IMIMN ININD IOIOT IQIRQ IRIRN ISISL ITITA JEJEY JMJAM JOJOR JPJPN KEKEN KGKGZ KHKHM KIKIR KMCOM KNKNA KPPRK KRKOR KWKWT KYCYM KZKAZ LALAO LBLBN LCLCA LILIE LKLKA LRLBR LSLSO LTLTU LULUX LVLVA LYLBY MAMAR MCMCO MDMDA MEMNE MFMAF MGMDG MHMHL MKMKD MLMLI MMMMR MNMNG MOMAC MPMNP MQMTQ MRMRT MSMSR MTMLT MUMUS MVMDV MWMWI MXMEX MYMYS MZMOZ NANAM NCNCL NENER NFNFK NGNGA NINIC NLNLD NONOR NPNPL NRNRU NUNIU NZNZL OMOMN PAPAN PEPER PFPYF PGPNG PHPHL PKPAK PLPOL PMSPM PNPCN PRPRI PSPSE PTPRT PWPLW PYPRY QAQAT REREU ROROU RSSRB RURUS RWRWA SASAU SBSLB SCSYC SDSDN SESWE SGSGP SHSHN SISVN SJSJM SKSVK SLSLE SMSMR SNSEN SOSOM SRSUR SSSSD STSTP SVSLV SXSXM SYSYR SZSWZ TCTCA TDTCD TFATF TGTGO THTHA TJTJK TKTKL TLTLS TMTKM TNTUN TOTON TRTUR TTTTO TVTUV TWTWN TZTZA UAUKR UGUGA UMUMI USUSA UYURY UZUZB VAVAT VCVCT VEVEN VGVGB VIVIR VNVNM VUVUT WFWLF WSWSM YEYEM YTMYT ZAZAF ZMZMB ZWZWE XKXKX";
const NEXT = 1; // "next month" among the three forecast months
const QUESTIONS = ["fighting", "major_war", "attacks"];
const TENSION = ["at war", "fighting", "very high", "high", "above usual", "usual"];
const THREAT_TEXT = ["#fff", "#fff", "#1b1c19", "#1b1c19", "#fff"];
const RELATIONS_SHOWN = 6;

const S = { data: null, places: [], byId: new Map(), iso3ToId: new Map(), here: undefined };

// ---------- Words: the same as SAGE's summaries ----------

function chanceWord(p) {
  if (p >= 0.95) return "almost certain";
  if (p >= 0.8) return "very likely";
  if (p >= 0.6) return "likely";
  if (p >= 0.4) return "about as likely as not";
  if (p >= 0.2) return "a real possibility";
  if (p >= 0.05) return "unlikely";
  if (p >= 0.01) return "very unlikely";
  return "extremely unlikely";
}
function pct(p) {
  if (p >= 0.995) return "over 99%";
  if (p < 0.005) return "under 1%";
  return Math.round(p * 100) + "%";
}
function chanceColor(p) {
  const level = p >= 0.8 ? 3 : p >= 0.4 ? 2 : p >= 0.2 ? 1 : 0;
  return `var(--chance-${level})`;
}
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
function monthName(m, withYear) {
  const [y, mo] = m.split("-").map(Number);
  return new Date(Date.UTC(y, mo - 1, 1)).toLocaleDateString("en-GB",
    withYear ? { month: "long", year: "numeric", timeZone: "UTC" } : { month: "long", timeZone: "UTC" });
}
function dayName(s) {
  const d = new Date(s.length > 10 ? s : s + "T00:00:00Z");
  return d.toLocaleDateString("en-GB", { day: "numeric", month: "long", timeZone: "UTC" });
}

// ---------- Small helpers ----------

/** An element; text is always set as text, never as HTML. */
function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === "class") el.className = v;
    else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? "" : v);
  }
  for (const c of children.flat(Infinity)) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}
const chip = (text, color, cls) => h("span", { class: "chip" + (cls ? " " + cls : ""), style: `--dot:${color}` }, text);
const section = (title, ...body) => [h("h2", null, title), ...body];
const bullets = (lines) => h("ul", { class: "bullets" }, lines.map((l) => h("li", null, l)));

function status(c) {
  if (!c) return "Relations only";
  const m = c.last_month || {};
  if (m.state >= 1000) return "At war";
  if (m.state >= 25) return "Fighting";
  if (m.state > 0) return "Some clashes";
  if (m.group_civ >= 10) return "Attacks on civilians";
  return "Calm";
}
function statusColor(c) {
  return { "At war": "var(--chance-3)", "Fighting": "var(--chance-2)", "Some clashes": "var(--chance-1)",
    "Attacks on civilians": "var(--chance-1)", "Calm": "var(--chance-0)" }[status(c)] || "var(--calm)";
}
const tensionOrder = (t) => { const i = TENSION.indexOf(t); return i < 0 ? TENSION.length : i; };
function tensionColor(t) {
  if (t === "at war" || t === "fighting") return "var(--chance-3)";
  if (t === "very high") return "var(--chance-2)";
  if (t === "high") return "var(--chance-1)";
  if (t === "above usual") return "color-mix(in srgb, var(--chance-1) 65%, transparent)";
  return "var(--calm)";
}
function relationsOf(id) {
  return S.data.pairs.pairs.filter((p) => p.a === id || p.b === id)
    .sort((x, y) => tensionOrder(x.tension) - tensionOrder(y.tension) || y.clash_6 - x.clash_6);
}
const other = (p, id) => (id === p.a ? p.names[1] : p.names[0]);
const pairLink = (p) => `#/pair/${p.a}-${p.b}`;
const nextMonth = () => S.data.months[NEXT];

// ---------- Loading ----------

function prepare(d) {
  S.data = d;
  S.byId = new Map();
  for (const c of d.countries) S.byId.set(c.id, { id: c.id, name: c.name, country: c });
  for (const p of (d.pairs && d.pairs.pairs) || []) {
    if (!S.byId.has(p.a)) S.byId.set(p.a, { id: p.a, name: p.names[0], country: null });
    if (!S.byId.has(p.b)) S.byId.set(p.b, { id: p.b, name: p.names[1], country: null });
  }
  if (!d.pairs) d.pairs = { pairs: [] };
  S.places = [...S.byId.values()].sort((a, b) => a.name.localeCompare(b.name));
  S.iso3ToId = new Map(Object.entries(d.iso3 || {}).map(([id, code]) => [code, Number(id)]));
}

function placeForIso2(iso2) {
  const i = ISO.indexOf(" " + iso2);
  const code = ISO.startsWith(iso2) ? ISO.slice(2, 5) : i >= 0 ? ISO.slice(i + 3, i + 6) : null;
  const id = code && S.iso3ToId.get(code);
  return id != null ? S.byId.get(id) : null;
}
function regionName(iso2) {
  try { return new Intl.DisplayNames(["en-GB"], { type: "region" }).of(iso2); } catch (e) { return iso2; }
}

/** The country the visitor is in: from their connection, else their browser's language. */
async function findHere() {
  const asked = new URLSearchParams(location.search).get("here");
  if (asked && /^[a-z]{2}$/i.test(asked)) return asked.toUpperCase();
  try {
    const kept = sessionStorage.getItem("sage-here");
    if (kept) return kept;
  } catch (e) { /* storage may be blocked */ }
  let found = null;
  try {
    const text = await (await fetch(LOOKUP_URL, { cache: "no-store" })).text();
    const m = text.match(/^loc=([A-Z]{2})$/m);
    if (m && m[1] !== "XX" && m[1] !== "T1") found = m[1];
  } catch (e) { /* offline or blocked: fall back */ }
  if (!found) {
    const region = (navigator.languages || [navigator.language || ""])
      .map((l) => (l.split("-")[1] || "")).find((r) => /^[A-Z]{2}$/i.test(r));
    found = region ? region.toUpperCase() : null;
  }
  try { if (found) sessionStorage.setItem("sage-here", found); } catch (e) { /* fine */ }
  return found;
}

// ---------- Pieces ----------

function placeCard(place) {
  const c = place.country;
  const month = monthName(nextMonth());
  let line = null;
  if (c) {
    const s = (c.last_month || {}).state || 0;
    const [what, key] = s >= 1000 ? ["Full-scale war continuing", "major_war"]
      : s >= 25 ? ["More deadly fighting", "fighting"] : ["Deadly fighting", "fighting"];
    const p = c[key].chance[NEXT];
    line = h("p", null, `${what} in ${month}: ${chanceWord(p)} (${pct(p)})`);
  }
  const tense = relationsOf(place.id).find((r) => r.tension !== "usual");
  return h("a", { class: "card", href: `#/country/${place.id}` },
    h("div", { class: "row" },
      h("span", { class: "name grow" }, place.name),
      c && c.alert && c.alert.on ? h("span", { class: "chip alert" }, "Alert") : null,
      chip(status(c), statusColor(c))),
    line ? h("div", { style: "margin-top:8px" }, line) : null,
    tense ? h("p", { class: "sub", style: "margin-top:4px" }, `Tension with ${other(tense, place.id)}: ${tense.tension}`) : null);
}

function relationRow(p, name) {
  return h("a", { href: pairLink(p) },
    h("span", { class: "grow" },
      h("span", { class: "name-sm" }, name),
      h("span", { class: "small", style: "display:block" }, `Deadly clashes in six months: ${pct(p.clash_6)}`)),
    chip(cap(p.tension), tensionColor(p.tension)));
}

function chanceBlock(label, p) {
  return h("div", null,
    h("div", { class: "chance-head" },
      h("span", null, label),
      h("span", { class: "word", style: `color:${chanceColor(p)}` }, cap(chanceWord(p))),
      h("span", { class: "pct", style: `color:${chanceColor(p)}` }, pct(p))),
    h("div", { class: "bar", "aria-hidden": "true" },
      h("i", { style: `width:${Math.max(1.5, p * 100)}%;background:${chanceColor(p)}` })));
}

// ---------- Pages ----------

function homePage() {
  const d = S.data;
  const out = [];
  out.push(h("p", { class: "fresh" },
    `Conflict data to ${monthName(d.data_through, true)}` +
    (d.alert_record && d.alert_record.checked_through ? ` · news to ${dayName(d.alert_record.checked_through)}` : "")));

  if (d.world) {
    const w = d.world;
    const r = Math.max(0, Math.min(4, w.rank));
    out.push(...section("Global threat level",
      h("div", { class: "card threat" },
        h("span", { class: "level", style: `background:var(--threat-${r});color:${THREAT_TEXT[r]}` }, w.level.toUpperCase()),
        h("p", null, w.text.replace(`Global threat level: ${w.level}. `, "")))));
  }

  const hereSlot = h("div");
  out.push(hereSlot);
  const fillHere = (iso2) => {
    hereSlot.replaceChildren();
    if (!iso2) return;
    const place = placeForIso2(iso2);
    hereSlot.append(...section("Where you are", place ? placeCard(place) :
      h("div", { class: "card" }, h("div", { class: "name" }, regionName(iso2)),
        h("p", { style: "margin-top:8px" }, "No organised violence has been recorded here since 1989, and SAGE sees no tension with another country worth following."))));
  };
  if (S.here !== undefined) fillHere(S.here);
  else findHere().then((iso2) => { S.here = iso2; fillHere(iso2); });

  const world = (d.summary || []).filter((l) => !l.startsWith("Global threat level"));
  if (world.length) out.push(...section("Where the world is leaning", h("div", { class: "card" }, bullets(world))));

  const search = h("input", { class: "search", type: "search", placeholder: "Type a country, like France", "aria-label": "Find a country", autocomplete: "off" });
  const results = h("div", { class: "results" });
  search.addEventListener("input", () => {
    const q = search.value.trim().toLowerCase();
    results.replaceChildren();
    if (!q) return;
    const found = S.places.filter((p) => p.name.toLowerCase().includes(q))
      .sort((a, b) => (a.name.toLowerCase().startsWith(q) ? 0 : 1) - (b.name.toLowerCase().startsWith(q) ? 0 : 1));
    if (!found.length) {
      results.append(h("p", { class: "small" }, `No country called "${search.value.trim()}". SAGE covers every country with organised violence since 1989, and those in tense relations with another.`));
      return;
    }
    const list = h("div", { class: "card list" });
    for (const p of found.slice(0, 12)) {
      list.append(h("a", { href: `#/country/${p.id}` },
        h("span", { class: "grow name-sm" }, p.name),
        p.country && p.country.alert && p.country.alert.on ? h("span", { class: "chip alert" }, "Alert") : null,
        chip(status(p.country), statusColor(p.country))));
    }
    results.append(list);
  });
  out.push(...section("Find a country", search, results));

  const alerts = d.countries.filter((c) => c.alert && c.alert.on);
  if (alerts.length) out.push(...section("Build-up alerts", alerts.map((c) => placeCard(S.byId.get(c.id)))));

  const tense = d.pairs.pairs.filter((p) => p.tension === "very high" || p.tension === "high")
    .sort((x, y) => tensionOrder(x.tension) - tensionOrder(y.tension) || y.clash_6 - x.clash_6).slice(0, 6);
  if (tense.length) out.push(...section("Tensest between countries not fighting",
    h("div", { class: "card list" }, tense.map((p) => relationRow(p, `${p.names[0]} and ${p.names[1]}`)))));

  out.push(...section("Get the app",
    h("div", { class: "card" },
      h("p", null, "The same forecasts on your Android phone, with the country you're in and the ones you follow on the home screen. Free, no ads, no accounts."),
      h("div", { class: "buttons" }, h("a", { class: "button", href: APP_URL }, "Download for Android"),
        h("a", { class: "button ghost", href: "#/about" }, "How SAGE works")))));
  return { title: "SAGE: war and conflict forecasts", body: out };
}

function alertText(c) {
  const a = c.alert;
  const who = a.kind === "aimed" ? `other countries' forces massing against ${c.name}` : `${c.name}'s forces mobilising and threats of force`;
  const since = a.since ? ` since ${dayName(a.since)}` : "";
  const times = a.times_usual ? `, up to about ${Math.round(a.times_usual)} times usual` : "";
  const calm = ((c.last_month || {}).state || 0) < 25;
  const rec = S.data.alert_record && S.data.alert_record.questions && S.data.alert_record.questions[calm ? "fighting" : "major_war"];
  const past = rec && rec.alerts ? ` After past alerts like this, ${calm ? "deadly fighting" : "full-scale war"} followed within three months in ${rec.followed} of ${rec.alerts} cases.` : "";
  return `News of ${who} has been far above usual${since}${times}.${past} An alert is a warning sign, not a forecast.`;
}

function countryPage(id) {
  const place = S.byId.get(id);
  if (!place) return notFound();
  const c = place.country;
  const out = [h("a", { class: "back", href: "#/" }, "← Home"), h("h1", null, place.name),
    h("div", { class: "row" }, chip(status(c), statusColor(c)), c ? h("span", { class: "sub" }, c.region) : null)];
  if (!c) {
    out.push(h("p", { style: "margin-top:16px" }, `There's been no organised violence recorded in ${place.name} since 1989, so SAGE doesn't forecast fighting inside it. It does follow ${place.name}'s relations with other countries, below.`));
  } else {
    if (c.alert && c.alert.on) out.push(h("div", { class: "alert-card" }, h("b", null, "⚠ Build-up alert"), alertText(c)));
    const summary = c.alert && c.alert.on ? c.summary.split(" Warning: ")[0] : c.summary;
    out.push(...section("Where things stand", h("div", { class: "card" }, h("p", null, summary))));
    out.push(h("h2", null, "The chances"));
    for (const key of QUESTIONS) {
      const o = c[key];
      if (!o) continue;
      const q = (S.data.questions || {})[key] || { title: key, text: "" };
      const p = o.chance[NEXT];
      out.push(h("div", { class: "card" },
        h("div", { class: "name" }, q.title), h("div", { class: "small" }, q.text),
        chanceBlock(monthName(nextMonth()), p),
        h("p", { class: "small", style: "margin-top:8px" },
          S.data.months.map((m, i) => `${monthName(m).slice(0, 3)} ${pct(o.chance[i])}`).join(" · ")),
        o.usual ? h("p", { class: "small", style: "margin-top:0" }, `Usual for a country in its situation: ${pct(o.usual[NEXT])}`) : null,
        o.why && o.why.length ? h("div", { class: "why" }, h("b", null, "Why"), bullets(o.why)) : null));
    }
  }
  const rel = relationsOf(id);
  if (rel.length) {
    const list = h("div", { class: "card list" });
    const fill = (all) => {
      list.replaceChildren(...(all ? rel : rel.slice(0, RELATIONS_SHOWN)).map((p) => relationRow(p, other(p, id))));
      if (rel.length > RELATIONS_SHOWN) {
        list.append(h("button", { class: "more", type: "button", onclick: () => fill(!all) }, all ? "Show fewer" : `Show all ${rel.length}`));
      }
    };
    fill(false);
    out.push(...section("Relations with other countries", list));
  }
  return { title: `SAGE: ${place.name}`, body: out };
}

function pairPage(a, b) {
  const p = S.data.pairs.pairs.find((x) => x.a === a && x.b === b);
  if (!p) return notFound();
  const title = `${p.names[0]} and ${p.names[1]}`;
  return {
    title: `SAGE: ${title}`,
    body: [h("a", { class: "back", href: `#/country/${a}` }, `← ${p.names[0]}`),
      h("h1", null, title), chip(`Tension: ${p.tension}`, tensionColor(p.tension)),
      ...section("Where things stand", h("div", { class: "card" }, h("p", null, p.summary))),
      ...section("In the next six months", h("div", { class: "card" },
        chanceBlock("Deadly clashes", p.clash_6),
        h("div", { style: "margin-top:14px" }, chanceBlock("War", p.war_6)),
        h("p", { class: "small", style: "margin-top:14px" },
          "Deadly clashes: at least one death in fighting between the two governments' forces. War: 1,000 or more deaths in a month." +
          (p.last_fought ? ` They last fought in ${p.last_fought}.` : "")))),
      h("div", { class: "buttons" },
        h("a", { class: "button ghost", href: `#/country/${p.a}` }, p.names[0]),
        h("a", { class: "button ghost", href: `#/country/${p.b}` }, p.names[1]))],
  };
}

function aboutPage() {
  const d = S.data;
  const out = [h("h1", null, "About SAGE"),
    h("p", null, "SAGE forecasts war and deadly violence, country by country, a few months ahead. It looks at how much fighting there's been, what the news says each side is doing, and what happened in similar situations since 1989. It's free, with no ads and no accounts."),
    h("div", { class: "buttons" }, h("a", { class: "button", href: REPO_URL }, "View source"),
      h("a", { class: "button ghost", href: APP_URL }, "Download for Android"))];
  out.push(...section("How it works", bullets([
    "Each month, SAGE reads the latest count of people killed in organised violence (UCDP) and forecasts the next three months for every country where there's been any since 1989.",
    "Each day, it reads the world's news (GDELT): who is threatening, mobilising or attacking whom, compared with what's usual for that country.",
    "When several warning signs are far above usual at once, it raises a build-up alert.",
    "Between countries, it also takes into account whether they share a border and how often they've clashed in the past (Correlates of War).",
    "The global threat level compares how many countries SAGE expects to see deadly fighting and full-scale war next month with every month since 1989."])));
  const rec = d.record && d.record.scores && (d.record.scores.fighting_2 || d.record.scores.fighting_1);
  if (rec) {
    const lines = [
      `SAGE was replayed month by month from ${monthName(d.record.from, true)} to ${monthName(d.record.to, true)}, using only what was known at the time, and checked against what happened.`,
      `Shown two countries, one where deadly fighting followed the next month and one where it didn't, SAGE gave the higher chance to the right one ${(rec.auc * 100).toFixed(1).replace(/\.0$/, "")} times in 100.`,
      `Its errors were ${Math.round(rec.better_than_last_month * 100)}% smaller than assuming next month will be like this one, which is the forecast to beat.`,
    ];
    const al = d.alert_record && d.alert_record.questions && d.alert_record.questions.fighting;
    if (al && al.alerts) lines.push(`After past build-up alerts, deadly fighting followed within three months in ${al.followed} of ${al.alerts} cases (${Math.round(100 * al.followed / al.alerts)}%), against ${pct(al.usual)} for an ordinary country.`);
    out.push(...section("How accurate it is", bullets(lines),
      h("p", { class: "small", style: "margin-top:10px" }, h("a", { href: `${REPO_URL}/blob/main/reports/backtest.md` }, "The full results"))));
  }
  out.push(...section("What it can't do", bullets([
    "It can't see surprises that don't show up in the news first.",
    "Most build-ups never turn into war, so even serious warning signs often come with small percentages. A small chance isn't no chance.",
    "It forecasts countries, not towns or people, and it isn't a warning service: for travel and safety, follow official advice."])));
  out.push(...section("Privacy", h("p", null, "No cookies, no tracking, no accounts. The forecasts are one public file, updated every day.")));
  out.push(...section("Where the numbers come from", bullets([
    "Uppsala Conflict Data Program (UCDP): Georeferenced Event Dataset and Candidate Events, CC BY 4.0.",
    "The GDELT Project.",
    "Correlates of War Project: Dyadic Militarized Interstate Disputes and Direct Contiguity.",
    "ViEWS (Uppsala University and PRIO), used to check SAGE against."])));
  out.push(...section("Licence", h("p", null, "SAGE is free to use and share with credit. Its code is open source under the Apache License 2.0, and its forecasts are licensed under Creative Commons Attribution 4.0: if you copy or share them, credit \"SAGE (github.com/Hostu404/SAGE)\".")));
  return { title: "About SAGE", body: out };
}

function notFound() {
  return { title: "SAGE", body: [h("h1", null, "Not found"), h("p", null, "SAGE has nothing at this address. "), h("a", { href: "#/" }, "Back to the forecast")] };
}

// ---------- Routing ----------

function route() {
  const main = document.getElementById("main");
  const path = location.hash.replace(/^#\/?/, "");
  let m, page;
  if (!S.data) return;
  if (!path) page = homePage();
  else if ((m = path.match(/^country\/(\d+)$/))) page = countryPage(Number(m[1]));
  else if ((m = path.match(/^pair\/(\d+)-(\d+)$/))) page = pairPage(Number(m[1]), Number(m[2]));
  else if (path === "about") page = aboutPage();
  else page = notFound();
  document.title = page.title;
  main.replaceChildren(...page.body.flat(Infinity));
  for (const a of document.querySelectorAll("nav a[data-route]")) {
    a.toggleAttribute("aria-current", a.dataset.route === (path === "about" ? "about" : path ? "" : "home"));
    if (a.hasAttribute("aria-current")) a.setAttribute("aria-current", "page");
  }
  window.scrollTo(0, 0);
}

async function start() {
  const main = document.getElementById("main");
  try {
    const res = await fetch(DATA_URL, { cache: "no-cache" });
    if (!res.ok) throw new Error(res.status);
    prepare(await res.json());
  } catch (e) {
    main.replaceChildren(h("p", { class: "loading" }, "The forecast couldn't be loaded. Check your connection and try again."));
    return;
  }
  const made = document.getElementById("made");
  if (made) made.textContent = `Forecast made ${dayName(S.data.generated)}, from conflict data to ${monthName(S.data.data_through, true)}.`;
  window.addEventListener("hashchange", route);
  route();
}

start();
