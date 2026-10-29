// SAGE's website. Reads the day's forecast (forecasts/latest.json, written by
// the daily run) and shows it as a dashboard. No libraries, no cookies, no
// tracking.
"use strict";

const DATA_URL = "forecasts/latest.json";
const MAP_URL = "site/world.json";
// The latest release's page, where the APK is (app-release.apk).
const APP_URL = "https://github.com/Hostu404/SAGE/releases/latest";
const REPO_URL = "https://github.com/Hostu404/SAGE";
// The country lookup the app uses too: it answers with "loc=GB".
const LOOKUP_URL = "https://www.cloudflare.com/cdn-cgi/trace";
// Two-letter country code followed by three-letter code, for every country.
const ISO = "ADAND AEARE AFAFG AGATG AIAIA ALALB AMARM AOAGO AQATA ARARG ASASM ATAUT AUAUS AWABW AXALA AZAZE BABIH BBBRB BDBGD BEBEL BFBFA BGBGR BHBHR BIBDI BJBEN BLBLM BMBMU BNBRN BOBOL BQBES BRBRA BSBHS BTBTN BVBVT BWBWA BYBLR BZBLZ CACAN CCCCK CDCOD CFCAF CGCOG CHCHE CICIV CKCOK CLCHL CMCMR CNCHN COCOL CRCRI CUCUB CVCPV CWCUW CXCXR CYCYP CZCZE DEDEU DJDJI DKDNK DMDMA DODOM DZDZA ECECU EEEST EGEGY EHESH ERERI ESESP ETETH FIFIN FJFJI FKFLK FMFSM FOFRO FRFRA GAGAB GBGBR GDGRD GEGEO GFGUF GGGGY GHGHA GIGIB GLGRL GMGMB GNGIN GPGLP GQGNQ GRGRC GSSGS GTGTM GUGUM GWGNB GYGUY HKHKG HMHMD HNHND HRHRV HTHTI HUHUN IDIDN IEIRL ILISR IMIMN ININD IOIOT IQIRQ IRIRN ISISL ITITA JEJEY JMJAM JOJOR JPJPN KEKEN KGKGZ KHKHM KIKIR KMCOM KNKNA KPPRK KRKOR KWKWT KYCYM KZKAZ LALAO LBLBN LCLCA LILIE LKLKA LRLBR LSLSO LTLTU LULUX LVLVA LYLBY MAMAR MCMCO MDMDA MEMNE MFMAF MGMDG MHMHL MKMKD MLMLI MMMMR MNMNG MOMAC MPMNP MQMTQ MRMRT MSMSR MTMLT MUMUS MVMDV MWMWI MXMEX MYMYS MZMOZ NANAM NCNCL NENER NFNFK NGNGA NINIC NLNLD NONOR NPNPL NRNRU NUNIU NZNZL OMOMN PAPAN PEPER PFPYF PGPNG PHPHL PKPAK PLPOL PMSPM PNPCN PRPRI PSPSE PTPRT PWPLW PYPRY QAQAT REREU ROROU RSSRB RURUS RWRWA SASAU SBSLB SCSYC SDSDN SESWE SGSGP SHSHN SISVN SJSJM SKSVK SLSLE SMSMR SNSEN SOSOM SRSUR SSSSD STSTP SVSLV SXSXM SYSYR SZSWZ TCTCA TDTCD TFATF TGTGO THTHA TJTJK TKTKL TLTLS TMTKM TNTUN TOTON TRTUR TTTTO TVTUV TWTWN TZTZA UAUKR UGUGA UMUMI USUSA UYURY UZUZB VAVAT VCVCT VEVEN VGVGB VIVIR VNVNM VUVUT WFWLF WSWSM YEYEM YTMYT ZAZAF ZMZMB ZWZWE XKXKX";
const NEXT = 1; // "next month" among the three forecast months
const QUESTIONS = ["fighting", "major_war", "attacks"];
const MEASURES = { fighting: "Deadly fighting", major_war: "Full-scale war" };
const TENSION = ["at war", "fighting", "very high", "high", "above usual", "usual"];
const THREAT_TEXT = ["#fff", "#fff", "#111", "#111", "#fff"];
const RISKS_SHOWN = 10;
// The map's shades of risk: under 1%, 1-5%, 5-20%, 20-40%, 40-80%, 80% or more.
const MAP_BINS = [0.01, 0.05, 0.2, 0.4, 0.8];
const MAP_LABELS = ["Under 1%", "1–5%", "5–20%", "20–40%", "40–80%", "80%+"];
// Other names people search by.
const ALIASES = {
  uk: 200, britain: 200, "great britain": 200, england: 200, scotland: 200, wales: 200, "northern ireland": 200,
  us: 2, usa: 2, america: 2, "united states": 2, uae: 696, emirates: 696, drc: 490, zaire: 490,
  "democratic republic of the congo": 490, "republic of the congo": 484, "cote d ivoire": 437, burma: 775,
  czechia: 316, bosnia: 346, turkiye: 640, "east timor": 860, dprk: 731, macedonia: 343, holland: 210, persia: 630, car: 482,
};
const STATUS_MEANS = {
  "At war": "Last month: 1,000 or more killed in fighting involving the government",
  "Fighting": "Last month: 25 or more killed in fighting involving the government",
  "Some clashes": "Last month: some killed in fighting involving the government",
  "Attacks on civilians": "Last month: armed groups killed 10 or more civilians",
  "Calm": "Last month: no deaths in fighting involving the government",
  "Relations only": "No organised violence here since 1989. SAGE follows its relations with other countries",
};
const TENSION_MEANS = "How tense, from the news, against what's usual for these two countries";
const ALERT_MEANS = "Build-up alert: news of troop build-ups, alerts and threats far above usual. A warning sign, not a forecast";

const S = {
  data: null, places: [], byId: new Map(), iso3ToId: new Map(), idToIso3: new Map(),
  here: undefined, measure: "fighting", path: undefined, scroll: new Map(),
};

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
/** The same, short enough for a column of numbers. */
function pctShort(p) {
  if (p >= 0.995) return ">99%";
  if (p < 0.005) return "<1%";
  return Math.round(p * 100) + "%";
}
function chanceColor(p) {
  const level = p >= 0.8 ? 3 : p >= 0.4 ? 2 : p >= 0.2 ? 1 : 0;
  return `var(--chance-${level})`;
}
function mapColor(p) {
  let i = 0;
  while (i < MAP_BINS.length && p >= MAP_BINS[i]) i++;
  return `var(--map-${i})`;
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
function svgEl(tag, attrs) {
  const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
  for (const [k, v] of Object.entries(attrs || {})) if (v != null) el.setAttribute(k, v);
  return el;
}
/** Replace an element's contents, skipping the empty slots (null) that the browser would print as "null". */
const replaceKids = (el, ...kids) => el.replaceChildren(...kids.flat(Infinity).filter((k) => k != null && k !== false));
const tag = (text, color, title) => h("span", { class: "tag", title }, h("i", { style: `background:${color}` }), text);
const flag = () => h("span", { class: "flag", title: ALERT_MEANS }, "Alert");
const mbar = (p) => h("span", { class: "mbar", "aria-hidden": "true" },
  h("i", { style: `width:${Math.max(2, p * 100)}%;background:${chanceColor(p)}` }));
const bullets = (lines) => h("ul", { class: "bullets" }, lines.map((l) => h("li", null, l)));
/** A dashboard panel: a titled box, with an optional note on the right of the title. */
const panel = (cls, title, right, ...body) => h("section", { class: `panel ${cls || ""}` },
  h("header", null, h("h2", null, title), right ? h("span", { class: "right" }, right) : null), ...body);
const stat = (value, label, color, cls) => h("div", { class: "stat" + (cls ? " " + cls : "") },
  h("b", { style: color ? `color:${color}` : null }, value), h("span", null, label));

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
const statusTag = (c) => tag(status(c), statusColor(c), STATUS_MEANS[status(c)]);
const tensionTag = (t, text) => tag(text || t, tensionColor(t), TENSION_MEANS);
const tensionOrder = (t) => { const i = TENSION.indexOf(t); return i < 0 ? TENSION.length : i; };
function tensionColor(t) {
  if (t === "at war" || t === "fighting") return "var(--chance-3)";
  if (t === "very high") return "var(--chance-2)";
  if (t === "high") return "var(--chance-1)";
  if (t === "above usual") return "color-mix(in srgb, var(--chance-1) 60%, transparent)";
  return "var(--calm)";
}
function tensionFill(t) {
  if (t === "at war" || t === "fighting") return "var(--map-5)";
  if (t === "very high") return "var(--map-4)";
  if (t === "high") return "var(--map-3)";
  if (t === "above usual") return "var(--map-2)";
  return "var(--map-0)";
}
function relationsOf(id) {
  return S.data.pairs.pairs.filter((p) => p.a === id || p.b === id)
    .sort((x, y) => tensionOrder(x.tension) - tensionOrder(y.tension) || y.clash_6 - x.clash_6);
}
const other = (p, id) => (id === p.a ? p.names[1] : p.names[0]);
const otherId = (p, id) => (id === p.a ? p.b : p.a);
const pairLink = (p) => `#/pair/${p.a}-${p.b}`;
const nextMonth = () => S.data.months[NEXT];
const question = (k) => (S.data.questions || {})[k] || { title: MEASURES[k] || k, text: "" };
const chanceOf = (place, key) => (place && place.country && place.country[key] ? place.country[key].chance[NEXT] : null);
const alertOn = (c) => !!(c && c.alert && c.alert.on);

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
  S.idToIso3 = new Map(Object.entries(d.iso3 || {}).map(([id, code]) => [Number(id), code]));
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

let worldMap = null;
/** The map's country shapes (site/world.json), fetched once; null if they can't be. */
function loadWorld() {
  if (!worldMap) {
    worldMap = fetch(MAP_URL).then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); }).catch(() => null);
  }
  return worldMap;
}

// ---------- The map ----------

/**
 * A world map, drawn once the shapes arrive. opts.fill(place) colours each
 * country (place is null for countries SAGE has nothing on), opts.tip(place)
 * is the line under its name on hover, opts.picked outlines countries and
 * opts.zoom frames them.
 */
function mapBody(opts) {
  const wrap = h("div", { class: "map-wrap" }, h("p", { class: "empty" }, "Loading the map…"));
  loadWorld().then((world) => {
    if (!world) { wrap.replaceChildren(h("p", { class: "empty" }, "The map couldn't be loaded.")); return; }
    wrap.replaceChildren(...drawMap(world, opts, wrap));
  });
  return wrap;
}

function drawMap(world, opts, wrap) {
  const view = opts.zoom ? frame(world, opts.zoom) : world.viewBox;
  const svg = svgEl("svg", { viewBox: view.join(" "), role: "img", "aria-label": opts.label });
  const tip = h("div", { class: "tip", hidden: true });
  const href = opts.href || ((place) => `#/country/${place.id}`);
  const dots = [];
  const shade = (el, place) => {
    el.style.fill = opts.fill(place);
    if (!place) return;
    el.classList.add("live");
    el.dataset.id = place.id;
    if (opts.picked && opts.picked.has(place.id)) el.classList.add("picked");
  };
  for (const [code, c] of Object.entries(world.countries)) {
    const id = S.iso3ToId.get(code);
    const place = id != null ? S.byId.get(id) : null;
    const el = svgEl("path", { d: c.d });
    shade(el, place);
    svg.append(el);
    if (c.dot && place) dots.push([c.dot, place]);
  }
  // Countries too small to see get a dot to hover and click.
  for (const [[x, y], place] of dots) {
    const dot = svgEl("circle", { cx: x, cy: y, r: (view[2] / 380).toFixed(2) });
    shade(dot, place);
    svg.append(dot);
  }
  for (const el of svg.querySelectorAll(".picked")) svg.append(el); // outlines on top

  const placeAt = (e) => { const id = e.target.dataset && e.target.dataset.id; return id ? S.byId.get(Number(id)) : null; };
  const showTip = (e, place, pinned) => {
    const link = pinned ? href(place) : null;
    replaceKids(tip, h("b", null, place.name), h("span", null, opts.tip(place)), link ? h("a", { href: link }, "Open ›") : null);
    const r = wrap.getBoundingClientRect();
    const y = e.clientY - r.top;
    tip.classList.toggle("pinned", !!pinned);
    tip.classList.toggle("below", y < 90);
    tip.style.left = `${Math.min(Math.max(e.clientX - r.left, 90), r.width - 90)}px`;
    tip.style.top = `${y}px`;
    tip.hidden = false;
  };
  let pointer = "mouse";
  svg.addEventListener("pointerdown", (e) => { pointer = e.pointerType; });
  svg.addEventListener("click", (e) => {
    const place = placeAt(e);
    if (!place) { tip.hidden = true; return; }
    // A tap says what's there first; a click goes straight to it.
    if (pointer !== "mouse") { showTip(e, place, true); return; }
    const link = href(place);
    if (link) location.hash = link;
  });
  svg.addEventListener("pointermove", (e) => {
    if (e.pointerType !== "mouse") return;
    const place = placeAt(e);
    if (place) showTip(e, place, false); else tip.hidden = true;
  });
  svg.addEventListener("pointerleave", (e) => { if (e.pointerType === "mouse") tip.hidden = true; });
  if (opts.onDraw) opts.onDraw(svg);
  return [svg, tip];
}

/** A view of the map framing these countries, or the whole world if they're spread wide. */
function frame(world, ids) {
  const [vx, vy, vw, vh] = world.viewBox;
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const id of ids) {
    const c = world.countries[S.idToIso3.get(id)];
    if (!c) continue;
    x0 = Math.min(x0, c.box[0]); y0 = Math.min(y0, c.box[1]);
    x1 = Math.max(x1, c.box[2]); y1 = Math.max(y1, c.box[3]);
  }
  if (!isFinite(x0)) return world.viewBox;
  let w = Math.max(x1 - x0, 50) * 1.6, ht = Math.max(y1 - y0, 25) * 1.6;
  if (w / ht > vw / vh) ht = w * vh / vw; else w = ht * vw / vh;
  if (w > vw * 0.75) return world.viewBox;
  const x = Math.min(Math.max((x0 + x1) / 2 - w / 2, vx), vx + vw - w);
  const y = Math.min(Math.max((y0 + y1) / 2 - ht / 2, vy), vy + vh - ht);
  return [x, y, w, ht].map((v) => Math.round(v * 10) / 10);
}

const legend = (items) => h("div", { class: "legend" }, items.map(([label, color]) => tag(label, color)));
const riskLegend = () => legend([["Not forecast", "var(--map-none)"], ...MAP_LABELS.map((l, i) => [l, `var(--map-${i})`])]);

// ---------- Home ----------

function homePage() {
  const risks = risksPanel();
  return {
    title: "SAGE: war and conflict forecasts",
    body: [h("div", { class: "grid home" },
      S.data.world ? threatPanel(S.data.world) : null, herePanel(),
      riskMapPanel(risks.refresh), risks.el,
      alertsPanel(), tensionPanel(), briefPanel(), tablePanel(), appPanel())],
  };
}

function threatPanel(w) {
  const r = Math.max(0, Math.min(4, w.rank));
  const levels = w.levels || ["Low", "Guarded", "Elevated", "High", "Severe"];
  const month = monthName(w.month || nextMonth());
  const lm = w.last_month;
  const count = (n) => (n == null ? "–" : String(Math.round(n)));
  const alerts = S.data.countries.filter(alertOn).length;
  return panel("a-threat", "Global threat level", `Forecast for ${monthName(w.month || nextMonth(), true)}`,
    h("div", { class: "threat-row" },
      h("div", { class: "threat-level", title: "How many countries SAGE expects to see deadly fighting and war next month, against every month since 1989" },
        h("span", { class: "level", style: `background:var(--threat-${r});color:${THREAT_TEXT[r]}` }, w.level.toUpperCase()),
        h("div", { class: "meter" },
          levels.map((name, i) => h("span", { class: i === r ? "now" : null, title: name, style: `background:var(--threat-${i})` }))),
        h("div", { class: "meter-ends", "aria-hidden": "true" }, h("span", null, levels[0]), h("span", null, levels[levels.length - 1]))),
      h("div", { class: "stats" },
        stat(count(w.expected_fighting), `countries expected to see deadly fighting in ${month}` +
          (lm ? `, against ${lm.fighting} in ${monthName(lm.month)}` : "")),
        stat(count(w.expected_wars), `expected to be at full-scale war` + (lm ? `, against ${lm.wars} in ${monthName(lm.month)}` : "")),
        stat(w.score == null ? "–" : `${Math.round(w.score * 100)}%`, "of months since 1989 were calmer"),
        stat(String(alerts), alerts === 1 ? "build-up alert" : "build-up alerts", null, alerts ? "hot" : null))));
}

function herePanel() {
  const body = h("div", { class: "body" }, h("p", { class: "note" }, "Finding where you are…"));
  const fill = (iso2) => {
    if (!iso2) {
      body.replaceChildren(h("p", { class: "note" }, "SAGE couldn't tell where you are."));
      return;
    }
    const place = placeForIso2(iso2);
    if (!place) {
      body.replaceChildren(h("p", { class: "place-name" }, regionName(iso2)),
        h("p", { class: "note" }, "No organised violence has been recorded here since 1989, and SAGE sees no tension with another country worth following."));
      return;
    }
    replaceKids(body, placeSummary(place));
  };
  if (S.here !== undefined) fill(S.here);
  else findHere().then((iso2) => { S.here = iso2; fill(iso2); });
  return panel("a-here", "Where you are", null, body);
}

function placeSummary(place) {
  const c = place.country;
  const month = monthName(nextMonth());
  const tense = relationsOf(place.id).find((r) => r.tension !== "usual");
  return [
    h("p", { class: "place-name" }, h("a", { href: `#/country/${place.id}` }, place.name)),
    h("p", { class: "tags" }, statusTag(c), alertOn(c) ? flag() : null),
    c ? h("div", { class: "meters" }, Object.keys(MEASURES).filter((k) => c[k]).map((k) => {
      const p = c[k].chance[NEXT];
      return h("div", null,
        h("div", { class: "top" }, h("span", null, `${MEASURES[k]} in ${month}`),
          h("span", { class: "num", style: `color:${chanceColor(p)}` }, pct(p))),
        mbar(p));
    })) : null,
    tense ? h("p", { class: "note" }, "Tension with ", h("a", { href: pairLink(tense) }, other(tense, place.id)), `: ${tense.tension}.`) : null,
  ];
}

function riskMapPanel(onChange) {
  const title = h("h2");
  const fill = (place) => { const p = chanceOf(place, S.measure); return p == null ? "var(--map-none)" : mapColor(p); };
  let svg = null;
  const buttons = Object.keys(MEASURES).map((key) => h("button", {
    type: "button", "data-key": key, onclick: () => { S.measure = key; update(); onChange(); },
    title: `${question(key).title}: ${question(key).text || MEASURES[key]}`,
  }, key === "fighting" ? "Fighting" : "War"));
  const update = () => {
    title.textContent = `${MEASURES[S.measure]} in ${monthName(nextMonth())}`;
    for (const b of buttons) b.setAttribute("aria-pressed", String(b.dataset.key === S.measure));
    if (svg) for (const el of svg.querySelectorAll("[data-id]")) el.style.fill = fill(S.byId.get(Number(el.dataset.id)));
  };
  const map = mapBody({
    label: "World map of each country's chance",
    fill,
    tip: (place) => {
      const p = chanceOf(place, S.measure);
      return p == null ? "No forecast of its own" : `${MEASURES[S.measure]}: ${pct(p)}`;
    },
    onDraw: (el) => { svg = el; },
  });
  update();
  return h("section", { class: "panel a-map" },
    h("header", null, title, h("span", { class: "right toggle", role: "group", "aria-label": "Show on the map" }, buttons)),
    map, riskLegend());
}

function risksPanel() {
  const title = h("h2");
  const list = h("ol", { class: "rows ranked" });
  const refresh = () => {
    const key = S.measure;
    title.textContent = `Most likely: ${MEASURES[key].toLowerCase()}`;
    const ranked = S.data.countries.filter((c) => c[key])
      .sort((a, b) => b[key].chance[NEXT] - a[key].chance[NEXT]).slice(0, RISKS_SHOWN);
    list.replaceChildren(...ranked.map((c, i) => {
      const p = c[key].chance[NEXT];
      return h("li", null, h("a", { href: `#/country/${c.id}` },
        h("span", { class: "rank" }, String(i + 1)),
        h("span", { class: "name" }, c.name, alertOn(c) ? flag() : null),
        mbar(p), h("span", { class: "pct", style: `color:${chanceColor(p)}` }, pctShort(p))));
    }));
  };
  refresh();
  const el = h("section", { class: "panel a-risks" },
    h("header", null, title, h("span", { class: "right" }, monthName(nextMonth()))), list);
  return { el, refresh };
}

function alertShort(c) {
  const a = c.alert;
  const who = a.kind === "aimed" ? "other countries' forces massing against it" : "its forces mobilising and threats of force";
  return `News of ${who} far above usual` + (a.since ? ` since ${dayName(a.since)}` : "") +
    (a.times_usual ? `, up to about ${Math.round(a.times_usual)}×.` : ".");
}

function alertsPanel() {
  const alerts = S.data.countries.filter(alertOn);
  return panel("a-alerts", "Build-up alerts", alerts.length ? String(alerts.length) : null,
    alerts.length ? h("div", null, alerts.map((c) => h("div", { class: "alert-item" },
      h("p", { class: "place-name" }, h("a", { href: `#/country/${c.id}` }, c.name), statusTag(c)),
      h("p", null, alertShort(c)))))
      : h("p", { class: "empty" }, "No build-up alerts right now."));
}

/** A row of column names over a list. */
const rowsHead = (...names) => h("li", { class: "rows-head", "aria-hidden": "true" }, names.map((n) => h("span", null, n)));

function pairRow(p, name) {
  return h("li", null, h("a", { href: pairLink(p) },
    h("span", { class: "name" }, name),
    tensionTag(p.tension),
    h("span", { class: "pct", style: `color:${chanceColor(p.clash_6)}` }, pctShort(p.clash_6))));
}

function tensionPanel() {
  const tense = S.data.pairs.pairs.filter((p) => p.tension === "very high" || p.tension === "high")
    .sort((x, y) => tensionOrder(x.tension) - tensionOrder(y.tension) || y.clash_6 - x.clash_6).slice(0, 8);
  return panel("a-tension", "Tensest relations", "Next six months",
    tense.length ? h("ul", { class: "rows" }, rowsHead("Countries", "Tension", "Clashes"),
      tense.map((p) => pairRow(p, `${p.names[0]} – ${p.names[1]}`)))
      : h("p", { class: "empty" }, "No relations between countries are unusually tense right now."));
}

function briefPanel() {
  const lines = (S.data.summary || []).filter((l) => !l.startsWith("Global threat level"));
  return panel("a-brief", "Summary", null, h("ul", { class: "lines" }, lines.map((l) => h("li", null, l))));
}

/** Every country, sortable by name, how things stood last month, or either chance. */
function tablePanel() {
  const cols = [["name", "Country"], ["status", "Last month"], ["fighting", "Fighting"], ["major_war", "War"]];
  const severity = ["Relations only", "Calm", "Attacks on civilians", "Some clashes", "Fighting", "At war"];
  let by = "name", desc = false;
  const tbody = h("tbody");
  const heads = cols.map(([key, label]) => {
    const th = h("th", { scope: "col", class: key === "status" ? "status-col" : key === "name" ? null : "num" },
      h("button", { type: "button", onclick: () => { desc = by === key ? !desc : key !== "name"; by = key; fill(); } }, label));
    return [key, th];
  });
  const cell = (p) => h("td", { class: "num" },
    p == null ? h("span", { class: "faint" }, "–") : [h("span", { style: `color:${chanceColor(p)}` }, pctShort(p)), mbar(p)]);
  const fill = () => {
    const value = (pl) => (by === "name" ? 0 : by === "status" ? severity.indexOf(status(pl.country))
      : chanceOf(pl, by) == null ? -1 : chanceOf(pl, by));
    const rows = [...S.places].sort((a, b) => (desc ? -1 : 1) * (value(a) - value(b)) || (by === "name" && desc ? -1 : 1) * a.name.localeCompare(b.name));
    for (const [key, th] of heads) th.setAttribute("aria-sort", key === by ? (desc ? "descending" : "ascending") : "none");
    tbody.replaceChildren(...rows.map((pl) => h("tr", { onclick: (e) => { if (e.target.tagName !== "A") location.hash = `#/country/${pl.id}`; } },
      h("td", null, h("a", { href: `#/country/${pl.id}` }, pl.name), alertOn(pl.country) ? flag() : null,
        h("div", { class: "sub" }, statusTag(pl.country))),
      h("td", { class: "status-col" }, statusTag(pl.country)),
      cell(chanceOf(pl, "fighting")), cell(chanceOf(pl, "major_war")))));
  };
  fill();
  return panel("a-all", "Every country", `Chance in ${monthName(nextMonth())}`,
    h("div", { class: "table-scroll" }, h("table", { class: "list" },
      h("thead", null, h("tr", null, heads.map(([, th]) => th))), tbody)));
}

function appPanel() {
  return panel("a-app", "Android app", null, h("div", { class: "app-strip" },
    h("p", null, h("b", null, "SAGE on your phone. "),
      "The same forecasts, plus the countries you follow with what's changed since you last looked, a home-screen widget, and forecasts to share as pictures. Free, no ads, no accounts."),
    h("div", { class: "buttons" }, h("a", { class: "button", href: APP_URL }, "Get the app"),
      h("a", { class: "button ghost", href: REPO_URL }, "View source"))));
}

// ---------- A country ----------

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

const tensionLegend = (self) => legend([[self, "var(--text)"], ["At war or fighting", "var(--map-5)"],
  ["Very high", "var(--map-4)"], ["High", "var(--map-3)"], ["Above usual", "var(--map-2)"], ["Usual", "var(--map-0)"]]);

function countryPage(id) {
  const place = S.byId.get(id);
  if (!place) return notFound();
  const c = place.country;
  const month = monthName(nextMonth());
  const keys = c ? QUESTIONS.filter((k) => c[k]) : [];
  const head = h("section", { class: "panel a-head" }, h("div", { class: "head-row" + (keys.length ? "" : " solo") },
    h("div", { class: "who" }, h("p", { class: "kicker" }, c ? c.region : "Relations only"), h("h1", null, place.name),
      h("p", { class: "tags" }, statusTag(c), c ? h("span", { class: "when" }, `in ${monthName(S.data.data_through)}`) : null,
        alertOn(c) ? flag() : null)),
    keys.map((k) => {
      const p = c[k].chance[NEXT];
      return stat(pctShort(p), `${question(k).title} in ${month}: ${chanceWord(p)}`, chanceColor(p));
    })));

  const panels = [head];
  if (c) {
    const summary = alertOn(c) ? c.summary.split(" Warning: ")[0] : c.summary;
    panels.push(panel("a-situation", "Situation", null, h("div", { class: "body" }, h("p", null, summary),
      alertOn(c) ? h("div", { class: "alert-box" }, h("b", null, "Build-up alert"), alertText(c)) : null)));
    panels.push(chancesPanel(c));
  } else {
    panels.push(panel("a-situation", "Situation", null, h("div", { class: "body" }, h("p", null,
      `There's been no organised violence recorded in ${place.name} since 1989, so SAGE doesn't forecast fighting inside it. It does follow ${place.name}'s relations with other countries.`))));
  }

  const rel = relationsOf(id);
  const tension = new Map(rel.map((p) => [otherId(p, id), p.tension]));
  const pairWith = new Map(rel.map((p) => [otherId(p, id), p]));
  panels.push(h("section", { class: "panel a-map" }, h("header", null, h("h2", null, "Relations map")),
    mapBody({
      label: `Map of ${place.name}'s relations with other countries`,
      fill: (pl) => (!pl ? "var(--map-none)" : pl.id === id ? "var(--text)" : tension.has(pl.id) ? tensionFill(tension.get(pl.id)) : "var(--map-none)"),
      tip: (pl) => (pl.id === id ? "This country" : tension.has(pl.id) ? `Tension: ${tension.get(pl.id)}` : "No relation SAGE follows"),
      href: (pl) => (pl.id === id ? null : pairWith.has(pl.id) ? pairLink(pairWith.get(pl.id)) : `#/country/${pl.id}`),
      picked: new Set([id]),
      zoom: [id, ...rel.filter((p) => p.tension !== "usual").map((p) => otherId(p, id))],
    }), tensionLegend(place.name)));
  panels.push(relationsPanel(rel, id));

  const whys = keys.filter((k) => c[k].why && c[k].why.length);
  if (whys.length) {
    panels.push(panel("a-why", "Why", null, h("div", { class: "why-cols" },
      whys.map((k) => h("div", null, h("h3", null, question(k).title), bullets(c[k].why))))));
  }
  return {
    title: `SAGE: ${place.name}`,
    body: [h("a", { class: "back", href: "#/" }, "← Overview"), h("div", { class: "grid country" + (c ? "" : " bare") }, panels)],
  };
}

function chancesPanel(c) {
  const months = S.data.months;
  const rows = QUESTIONS.filter((k) => c[k]).map((k) => {
    const o = c[k];
    const q = question(k);
    return h("tr", null,
      h("th", { scope: "row" }, q.title, q.text ? h("span", null, q.text) : null),
      months.map((m, i) => h("td", { class: i === NEXT ? "next" : null },
        h("span", { style: `color:${chanceColor(o.chance[i])}` }, pctShort(o.chance[i])), mbar(o.chance[i]))),
      h("td", { class: "usual" }, o.usual ? pctShort(o.usual[NEXT]) : "–"));
  });
  return panel("a-chances", "The chances", `${monthName(months[0])} to ${monthName(months[months.length - 1], true)}`,
    h("table", { class: "chances" },
      h("thead", null, h("tr", null, h("td"),
        months.map((m, i) => h("th", { scope: "col", class: i === NEXT ? "next" : null }, monthName(m).slice(0, 3))),
        h("th", { scope: "col", title: `How often it happens in ${monthName(nextMonth())} to countries in the same situation` }, "Usual"))),
      h("tbody", null, rows)),
    h("div", { class: "note body" },
      h("p", { class: "phone-only" }, QUESTIONS.filter((k) => c[k] && question(k).text).map((k) => `${question(k).title}: ${question(k).text}. `)),
      h("p", null, `Usual: how often it happens in ${monthName(nextMonth())} to countries in the same situation.`)));
}

function relationsPanel(rel, id) {
  return panel("a-relations", "Relations", "Next six months",
    rel.length ? h("ul", { class: "rows scroll" }, rowsHead("Country", "Tension", "Clashes"), rel.map((p) => pairRow(p, other(p, id))))
      : h("p", { class: "empty" }, "None that SAGE follows."));
}

// ---------- Two countries ----------

function pairPage(a, b) {
  const p = S.data.pairs.pairs.find((x) => x.a === a && x.b === b);
  if (!p) return notFound();
  const title = `${p.names[0]} and ${p.names[1]}`;
  const head = h("section", { class: "panel a-head" }, h("div", { class: "head-row" },
    h("div", { class: "who" }, h("p", { class: "kicker" }, "Between countries"), h("h1", null, title),
      h("p", { class: "tags" }, tensionTag(p.tension, `Tension: ${p.tension}`),
        h("a", { href: `#/country/${p.a}` }, p.names[0]), h("a", { href: `#/country/${p.b}` }, p.names[1]))),
    stat(pctShort(p.clash_6), `Deadly clashes in the next six months: ${chanceWord(p.clash_6)}`, chanceColor(p.clash_6)),
    stat(pctShort(p.war_6), `War in the next six months: ${chanceWord(p.war_6)}`, chanceColor(p.war_6)),
    p.usual_6 != null ? stat(pctShort(p.usual_6), "Usual chance of clashes for two countries like these") : null));
  const situation = panel("a-situation", "Situation", null, h("div", { class: "body" }, h("p", null, p.summary),
    h("p", { class: "note" }, "Deadly clashes: at least one death in fighting between the two governments' forces. War: 1,000 or more deaths in a month.")));
  const map = h("section", { class: "panel a-map" }, h("header", null, h("h2", null, "Map")),
    mapBody({
      label: `Map of ${title}`,
      fill: (pl) => (!pl ? "var(--map-none)" : pl.id === a ? "var(--text)" : pl.id === b ? "var(--pick-2)" : "var(--map-none)"),
      tip: (pl) => (pl.id === a || pl.id === b ? `Tension: ${p.tension}` : "Not in this pair"),
      picked: new Set([a, b]),
      zoom: [a, b],
    }), legend([[p.names[0], "var(--text)"], [p.names[1], "var(--pick-2)"]]));
  return {
    title: `SAGE: ${title}`,
    body: [h("a", { class: "back", href: `#/country/${a}` }, `← ${p.names[0]}`), h("div", { class: "grid pair" }, head, map, situation)],
  };
}

// ---------- About ----------

function aboutPage() {
  const d = S.data;
  const panels = [panel("wide", "About SAGE", null, h("div", { class: "body" },
    h("p", null, "SAGE forecasts war and deadly violence, country by country, a few months ahead. It looks at how much fighting there's been, what the news says each side is doing, and what happened in similar situations since 1989. It's free, with no ads and no accounts."),
    h("div", { class: "buttons" }, h("a", { class: "button", href: REPO_URL }, "View source"),
      h("a", { class: "button ghost", href: APP_URL }, "Get the app"))))];
  panels.push(panel("", "How it works", null, h("div", { class: "body" }, bullets([
    "Each month, SAGE reads the latest count of people killed in organised violence (UCDP), which runs about a month behind, and forecasts the three months after it for every country where there's been any since 1989. The first of those months is usually already under way.",
    "Each day, it reads the world's news (GDELT): who is threatening, mobilising or attacking whom, compared with what's usual for that country.",
    "When several warning signs are far above usual at once, it raises a build-up alert.",
    "Between countries, it also takes into account whether they share a border and how often they've clashed in the past (Correlates of War).",
    "The global threat level compares how many countries SAGE expects to see deadly fighting and full-scale war next month with every month since 1989."]))));
  const rec = d.record && d.record.scores && (d.record.scores.fighting_2 || d.record.scores.fighting_1);
  if (rec) {
    const lines = [
      `SAGE was replayed month by month from ${monthName(d.record.from, true)} to ${monthName(d.record.to, true)}, using only what was known at the time, and checked against what happened.`,
      `Shown two countries, one where deadly fighting followed the next month and one where it didn't, SAGE gave the higher chance to the right one ${(rec.auc * 100).toFixed(1).replace(/\.0$/, "")} times in 100.`,
      `Its errors were ${Math.round(rec.better_than_last_month * 100)}% smaller than assuming next month will be like this one, which is the forecast to beat.`,
    ];
    const al = d.alert_record && d.alert_record.questions && d.alert_record.questions.fighting;
    if (al && al.alerts) lines.push(`After past build-up alerts, deadly fighting followed within three months in ${al.followed} of ${al.alerts} cases (${Math.round(100 * al.followed / al.alerts)}%), against ${pct(al.usual)} for an ordinary country.`);
    panels.push(panel("", "How accurate it is", null, h("div", { class: "body" }, bullets(lines),
      h("p", { class: "note", style: "margin-top:10px" }, h("a", { href: `${REPO_URL}/blob/main/reports/backtest.md` }, "The full results")))));
  }
  panels.push(panel("", "What it can't do", null, h("div", { class: "body" }, bullets([
    "It can't see surprises that don't show up in the news first.",
    "Most build-ups never turn into war, so even serious warning signs often come with small percentages. A small chance isn't no chance.",
    "Three months out, a war that only just started gets too low a chance. Each month is forecast on its own, so the third month can drop sharply: since 2018, when SAGE gave a war under three months old less than 30% for three months on (11% on average), it was still going about a third of the time (8 of 25 forecasts). Such flare-ups really do split: some stopped within weeks (Azerbaijan in 2020, Lebanon in 2024), others kept going (Ethiopia's Tigray war in 2020, Russia's Kursk region in 2024).",
    "Between countries, chances under about a third run low: since 2018, when SAGE said about 4% for deadly clashes within six months, they followed about 6% of the time, and at about 21%, about 34%. Higher chances have been close.",
    "It forecasts countries, not towns or people, and it isn't a warning service: for travel and safety, follow official advice."]))));
  panels.push(panel("", "Privacy", null, h("div", { class: "body" }, h("p", null, "No cookies, no tracking, no accounts. The forecasts are one public file, updated every day."))));
  panels.push(panel("", "Where the numbers come from", null, h("div", { class: "body" }, bullets([
    "Uppsala Conflict Data Program (UCDP): Georeferenced Event Dataset and Candidate Events, CC BY 4.0.",
    "The GDELT Project.",
    "Correlates of War Project: Dyadic Militarized Interstate Disputes and Direct Contiguity.",
    "ViEWS (Uppsala University and PRIO), used to check SAGE against.",
    "Map: Natural Earth (public domain)."]))));
  panels.push(panel("", "Licence", null, h("div", { class: "body" }, h("p", null, "SAGE is free to use and share with credit. Its code is open source under the Apache License 2.0, and its forecasts are licensed under Creative Commons Attribution 4.0: if you copy or share them, credit \"SAGE (github.com/Hostu404/SAGE)\"."))));
  return { title: "About SAGE", body: [h("div", { class: "grid about" }, panels)] };
}

function notFound() {
  return { title: "SAGE", body: [h("div", { class: "grid about" }, panel("wide", "Not found", null, h("div", { class: "body" },
    h("p", null, "SAGE has nothing at this address."), h("a", { href: "#/" }, "Back to the overview"))))] };
}

// ---------- Search ----------

const norm = (t) => t.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();

/** Countries matching what's been typed, best first: whole names, then starts of names and words, then anywhere. */
function findPlaces(text) {
  const q = norm(text);
  if (!q) return [];
  const found = [];
  for (const p of S.places) {
    const name = norm(p.name);
    let score = name === q ? 0 : name.startsWith(q) ? 1 : name.split(" ").some((w) => w.startsWith(q)) ? 2 : name.includes(q) ? 3 : 9;
    for (const [alias, id] of Object.entries(ALIASES)) {
      if (id === p.id && (alias === q || (q.length >= 2 && alias.startsWith(q)))) score = Math.min(score, alias === q ? 0 : 2);
    }
    if (score < 9) found.push([score, p]);
  }
  return found.sort((a, b) => a[0] - b[0] || a[1].name.localeCompare(b[1].name)).map(([, p]) => p);
}

/** The search box in the top bar: type, pick with the arrows or the mouse, press / to jump to it. */
function setUpSearch() {
  const input = document.getElementById("q");
  const list = document.getElementById("q-list");
  const toggle = document.getElementById("find-toggle");
  if (!input || !list) return;
  let found = [], active = -1;
  const close = () => {
    list.hidden = true;
    input.setAttribute("aria-expanded", "false");
    input.removeAttribute("aria-activedescendant");
  };
  const done = () => { input.value = ""; close(); input.blur(); document.body.classList.remove("finding"); };
  const go = (place) => { done(); location.hash = `#/country/${place.id}`; };
  const mark = () => {
    [...list.children].forEach((li, i) => li.classList.toggle("active", i === active));
    if (active >= 0) { input.setAttribute("aria-activedescendant", `q-${active}`); list.children[active].scrollIntoView({ block: "nearest" }); }
  };
  const show = () => {
    found = findPlaces(input.value).slice(0, 8);
    list.replaceChildren(...found.map((p, i) => {
      const v = chanceOf(p, "fighting");
      return h("li", { id: `q-${i}`, role: "option", onclick: () => go(p) },
        h("span", { class: "name" }, p.name), statusTag(p.country),
        h("span", { class: "pct", style: v == null ? null : `color:${chanceColor(v)}` }, v == null ? "–" : pctShort(v)));
    }));
    if (!found.length) list.append(h("li", { class: "none" }, "No country by that name"));
    const open = !!input.value.trim();
    list.hidden = !open;
    input.setAttribute("aria-expanded", String(open));
    active = found.length ? 0 : -1;
    mark();
  };
  input.addEventListener("input", show);
  input.addEventListener("focus", () => { if (input.value.trim()) show(); });
  input.addEventListener("blur", () => setTimeout(close, 200));
  input.addEventListener("keydown", (e) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (!found.length) return;
      active = (active + (e.key === "ArrowDown" ? 1 : found.length - 1)) % found.length;
      mark();
      e.preventDefault();
    } else if (e.key === "Enter") {
      if (found.length) go(found[Math.max(active, 0)]);
      e.preventDefault();
    } else if (e.key === "Escape") {
      done();
    }
  });
  document.addEventListener("keydown", (e) => {
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName);
    if (e.key === "/" && !typing && !e.ctrlKey && !e.metaKey && !e.altKey) {
      e.preventDefault();
      document.body.classList.add("finding");
      input.focus();
    }
  });
  if (toggle) {
    toggle.addEventListener("click", () => {
      if (document.body.classList.toggle("finding")) input.focus(); else done();
    });
  }
}

// ---------- Routing ----------

function route() {
  const main = document.getElementById("main");
  const path = location.hash.replace(/^#\/?/, "");
  let m, page;
  if (!S.data) return;
  if (S.path !== undefined) S.scroll.set(S.path, window.scrollY);
  S.path = path;
  if (!path) page = homePage();
  else if ((m = path.match(/^country\/(\d+)$/))) page = countryPage(Number(m[1]));
  else if ((m = path.match(/^pair\/(\d+)-(\d+)$/))) page = pairPage(Number(m[1]), Number(m[2]));
  else if (path === "about") page = aboutPage();
  else page = notFound();
  document.title = page.title;
  replaceKids(main, page.body);
  for (const a of document.querySelectorAll("nav a[data-route]")) {
    const here = a.dataset.route === (path === "about" ? "about" : path ? "" : "home");
    if (here) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current");
  }
  document.body.classList.remove("finding");
  // Back on the overview, carry on where you left it; every other page starts at the top.
  const y = path ? 0 : S.scroll.get("") || 0;
  window.scrollTo(0, y);
  if (y) loadWorld().then(() => requestAnimationFrame(() => window.scrollTo(0, y)));
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
  const d = S.data;
  const news = d.alert_record && d.alert_record.checked_through ? `, news to ${dayName(d.alert_record.checked_through)}` : "";
  const stamp = document.getElementById("stamp");
  if (stamp) stamp.textContent = `Updated ${dayName(d.generated)} · data to ${monthName(d.data_through, true)}${news}`;
  const made = document.getElementById("made");
  if (made) made.textContent = `Forecast made ${dayName(d.generated)}, from conflict data to ${monthName(d.data_through, true)}${news}.`;
  setUpSearch();
  window.addEventListener("hashchange", route);
  route();
}

start();
