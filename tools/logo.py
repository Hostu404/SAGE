"""Draws SAGE's logo, an eye of providence, as SVG and Android vector drawables from one set of numbers.

Run: python tools/logo.py OUT_DIR, then copy logo.svg to the top of the
repository and the .xml files to android/app/src/main/res/drawable/.
"""
import math, os, sys
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
# The triangle is 4chan's worksafe blue (its reply boxes); the icon sits on the worksafe page blue.
# The iris is the worksafe boards' title red, softened; the eye's outline a soft navy to sit with the blue.
GREEN, WORKSAFE, GOLD, EYE, IRIS, PUPIL = "#3F5E45", "#D6DAF0", "#D9A23B", "#FFFFFF", "#B3242C", "#3A0B10"
OUTLINE = "#33345E"
ICON_BACKGROUND = "#EEF2FF"

def shapes(c, side, rays, ray_in, ray_out, ray_w, stroke):
    """Paths for a triangle of `side` centred (by its centroid) on c, the eye, and rays."""
    h = side * math.sqrt(3) / 2
    top = (c, c - 2 * h / 3); bl = (c - side / 2, c + h / 3); br = (c + side / 2, c + h / 3)
    tri = f"M{top[0]:.2f},{top[1]:.2f} L{br[0]:.2f},{br[1]:.2f} L{bl[0]:.2f},{bl[1]:.2f} Z"
    ey = c + h * 0.07                      # the eye sits a little below the centroid
    ew = side * 0.52; eh = side * 0.26     # almond width; control-point height
    eye = (f"M{c - ew / 2:.2f},{ey:.2f} Q{c:.2f},{ey - eh:.2f} {c + ew / 2:.2f},{ey:.2f} "
           f"Q{c:.2f},{ey + eh:.2f} {c - ew / 2:.2f},{ey:.2f} Z")
    iris_r, pupil_r = side * 0.104, side * 0.046
    ray_paths = []
    for i in range(rays):
        a = math.radians(-90 + i * 360 / rays + 180 / rays)
        x1, y1 = c + ray_in * math.cos(a), c + ray_in * math.sin(a)
        x2, y2 = c + ray_out * math.cos(a), c + ray_out * math.sin(a)
        ray_paths.append(f"M{x1:.2f},{y1:.2f} L{x2:.2f},{y2:.2f}")
    return dict(tri=tri, eye=eye, ey=ey, iris_r=iris_r, pupil_r=pupil_r, rays=" ".join(ray_paths),
                ray_w=ray_w, stroke=stroke, c=c, side=side)

def svg(size, s, badge=True):
    c = s["c"]
    hl = (c + s["iris_r"] * 0.35, s["ey"] - s["iris_r"] * 0.35)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" width="{size}" height="{size}" role="img" aria-label="SAGE">
  {'<circle cx="%g" cy="%g" r="%g" fill="%s"/>' % (c, c, size / 2, GREEN) if badge else ''}
  <path d="{s['rays']}" stroke="{GOLD}" stroke-opacity="0.85" stroke-width="{s['ray_w']}" stroke-linecap="round" fill="none"/>
  <path d="{s['tri']}" fill="{WORKSAFE}" stroke="{GOLD}" stroke-width="{s['stroke']}" stroke-linejoin="round"/>
  <path d="{s['eye']}" fill="{EYE}" stroke="{OUTLINE}" stroke-width="{s['stroke'] * 0.35:.2f}"/>
  <circle cx="{c}" cy="{s['ey']:.2f}" r="{s['iris_r']:.2f}" fill="{IRIS}"/>
  <circle cx="{c}" cy="{s['ey']:.2f}" r="{s['pupil_r']:.2f}" fill="{PUPIL}"/>
  <circle cx="{hl[0]:.2f}" cy="{hl[1]:.2f}" r="{s['pupil_r'] * 0.45:.2f}" fill="#FFFFFF"/>
</svg>
'''

def vector(s, size, mono=False, badge=False, dp=108):
    c = s["c"]
    hl = (c + s["iris_r"] * 0.35, s["ey"] - s["iris_r"] * 0.35)
    def circle(cx, cy, r):
        return f"M{cx - r:.2f},{cy:.2f} a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0 a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0 Z"
    W = "#FFFFFFFF"
    parts = []
    if badge:
        parts.append(f'<path android:fillColor="{GREEN}" android:pathData="{circle(c, c, size / 2)}" />')
    if mono:
        # One colour: Android takes only the shape. Outline triangle and eye, solid iris.
        parts += [
            f'<path android:strokeColor="{W}" android:strokeWidth="{s["stroke"]}" android:strokeLineJoin="round" android:pathData="{s["tri"]}" />',
            f'<path android:strokeColor="{W}" android:strokeWidth="{s["stroke"] * 0.8:.2f}" android:pathData="{s["eye"]}" />',
            f'<path android:fillColor="{W}" android:pathData="{circle(c, s["ey"], s["iris_r"])}" />',
        ]
    else:
        if s["rays"]:
            parts.append(f'<path android:strokeColor="{GOLD}" android:strokeAlpha="0.85" android:strokeWidth="{s["ray_w"]}" android:strokeLineCap="round" android:pathData="{s["rays"]}" />')
        parts += [
            f'<path android:fillColor="{WORKSAFE}" android:strokeColor="{GOLD}" android:strokeWidth="{s["stroke"]}" android:strokeLineJoin="round" android:pathData="{s["tri"]}" />',
            f'<path android:fillColor="{EYE}" android:strokeColor="{OUTLINE}" android:strokeWidth="{s["stroke"] * 0.35:.2f}" android:pathData="{s["eye"]}" />',
            f'<path android:fillColor="{IRIS}" android:pathData="{circle(c, s["ey"], s["iris_r"])}" />',
            f'<path android:fillColor="{PUPIL}" android:pathData="{circle(c, s["ey"], s["pupil_r"])}" />',
            f'<path android:fillColor="#FFFFFF" android:pathData="{circle(hl[0], hl[1], s["pupil_r"] * 0.45)}" />',
        ]
    body = "\n    ".join(parts)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- SAGE's logo: an eye of providence. Made by the logo script from the same numbers as logo.svg. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{dp}dp"
    android:height="{dp}dp"
    android:viewportWidth="{size}"
    android:viewportHeight="{size}">
    {body}
</vector>
'''

# Full logo for the README: 512, long rays.
full = shapes(256, side=250, rays=18, ray_in=150, ray_out=228, ray_w=7, stroke=12)
open(f"{out}/logo.svg", "w").write(svg(512, full, badge=False))
# App icon foreground: 108 viewport, everything inside the 66dp safe zone (radius 33).
icon = shapes(54, side=52, rays=12, ray_in=21, ray_out=33, ray_w=2.4, stroke=2.8)
open(f"{out}/ic_launcher_foreground.xml", "w").write(vector(icon, 108))
open(f"{out}/ic_launcher_monochrome.xml", "w").write(vector(icon, 108, mono=True))
# Small logo for inside the app (title bar): its own green disc.
small = shapes(54, side=80, rays=0, ray_in=0, ray_out=0, ray_w=0, stroke=5)
open(f"{out}/ic_logo.xml", "w").write(vector(small, 108, dp=32))
open(f"{out}/preview-small.svg", "w").write(svg(108, small, badge=False))
open(f"{out}/preview-icon.svg", "w").write(svg(108, icon, badge=False))
print("ok")
