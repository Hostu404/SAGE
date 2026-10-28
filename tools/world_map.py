"""Draw the website's world map from Natural Earth's country shapes.

    python tools/world_map.py ne_50m_admin_0_countries.geojson site/world.json

The shapes are Natural Earth's 1:50m countries (public domain,
naturalearthdata.com), on the Equal Earth projection, simplified to what
shows on a screen. Each country is keyed by its three-letter code, as in the
forecast file's "iso3". Countries too small to see also get a dot to click.
"""

import json
import math
import sys
from pathlib import Path

WIDTH = 1000  # the map's width in its own units
TOLERANCE = 0.3  # how far a simplified line may stray, in those units
SMALL = 4.0  # a country narrower and shorter than this gets a dot
SOUTH = -58  # nothing further south (Antarctica and the far south islands)

# Natural Earth codes that differ from the ones SAGE uses.
CODES = {"SAH": "ESH", "PSX": "PSE", "KOS": "XKX", "SDS": "SSD", "SOL": "SOM"}
SKIP = {"ATA", "CYN", "KAS"}  # Antarctica, Northern Cyprus, Siachen Glacier

A1, A2, A3, A4 = 1.340264, -0.081106, 0.000893, 0.003796
M = math.sqrt(3) / 2
SCALE = WIDTH / (2 * 2 * math.sqrt(3) * math.pi / (3 * A1))  # 180° east is x = WIDTH / 2


def project(lon, lat):
    """Equal Earth: x to the right, y downwards."""
    lam, phi = math.radians(lon), math.radians(lat)
    t = math.asin(M * math.sin(phi))
    t2, t6 = t * t, t ** 6
    x = 2 * math.sqrt(3) * lam * math.cos(t) / (3 * (9 * A4 * t6 * t2 + 7 * A3 * t6 + 3 * A2 * t2 + A1))
    y = t * (A4 * t6 * t2 + A3 * t6 + A2 * t2 + A1)
    return x * SCALE + WIDTH / 2, -y * SCALE


def simplify(points, tol):
    """Douglas-Peucker on a closed ring."""
    if len(points) < 4:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (ax, ay), (bx, by) = points[a], points[b]
        dx, dy = bx - ax, by - ay
        length = math.hypot(dx, dy)
        far, far_i = 0.0, None
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / length if length else math.hypot(px - ax, py - ay)
            if d > far:
                far, far_i = d, i
        if far_i is not None and far > tol:
            keep[far_i] = True
            stack += [(a, far_i), (far_i, b)]
    return [p for p, k in zip(points, keep) if k]


def area(ring):
    return abs(sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(ring, ring[1:]))) / 2


def box(rings):
    xs = [x for r in rings for x, _ in r]
    ys = [y for r in rings for _, y in r]
    return [min(xs), min(ys), max(xs), max(ys)]


def path(rings):
    """SVG path data, each ring as a start point then relative steps, to one decimal."""
    out = []
    for ring in rings:
        pts = [(round(x * 10), round(y * 10)) for x, y in ring]
        steps = [f"M{pts[0][0] / 10:g} {pts[0][1] / 10:g}"]
        lx, ly = pts[0]
        rel = []
        for x, y in pts[1:-1]:
            if (x, y) != (lx, ly):
                rel.append(f"{(x - lx) / 10:g} {(y - ly) / 10:g}")
                lx, ly = x, y
        out.append(steps[0] + "l" + " ".join(rel) + "z")
    return "".join(out)


def main(src, dst):
    shapes = {}
    for feature in json.loads(Path(src).read_text())["features"]:
        p = feature["properties"]
        adm = p["ADM0_A3"]
        if adm in SKIP:
            continue
        code = p["ISO_A3_EH"] if p["ISO_A3_EH"] != "-99" else CODES.get(adm, adm)
        code = CODES.get(code, code)
        g = feature["geometry"]
        polygons = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
        for polygon in polygons:
            outer = polygon[0]  # holes are lakes and enclaves: leave them filled
            if max(lat for _, lat in outer) < SOUTH:
                continue
            shapes.setdefault(code, []).append([project(lon, max(lat, SOUTH)) for lon, lat in outer])

    countries = {}
    top = min(project(0, 84)[1], 0)
    bottom = project(0, SOUTH)[1]
    for code, rings in sorted(shapes.items()):
        biggest = max(rings, key=area)
        drawn = []
        for ring in rings:
            r = simplify(ring, TOLERANCE)
            if len(r) >= 4 and (area(r) >= 0.6 or ring is biggest):
                drawn.append(r)
        if not drawn:
            drawn = [biggest]
        entry = {"d": path(drawn), "box": [round(v, 1) for v in box([biggest])]}
        x0, y0, x1, y1 = box(drawn)
        if x1 - x0 < SMALL and y1 - y0 < SMALL:
            entry["dot"] = [round((x0 + x1) / 2, 1), round((y0 + y1) / 2, 1)]
        countries[code] = entry
    out = {
        "source": "Natural Earth 1:50m countries (public domain), Equal Earth projection",
        "viewBox": [0, round(top, 1), WIDTH, round(bottom - top, 1)],
        "countries": countries,
    }
    Path(dst).parent.mkdir(parents=True, exist_ok=True)
    Path(dst).write_text(json.dumps(out, separators=(",", ":")))
    print(f"{len(countries)} countries, {Path(dst).stat().st_size / 1024:.0f} KB")


if __name__ == "__main__":
    main(*sys.argv[1:3])
