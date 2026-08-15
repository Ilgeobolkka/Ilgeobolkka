#!/usr/bin/env python3
"""book-014 이미지 페이지 네 개의 SVG를 생성한다.

사용: python3 figures014.py <출력디렉터리>

네 도표에서 표기를 고정했다 — 세로축은 언제나 위가 뜨겁고 아래가 미지근하며, 가로축은 언제나 왼쪽이
부은 직후이고 오른쪽이 사십 분 뒤다. 이 권의 주장이 「앞이 빠르고 뒤가 느리다」이므로, 그림마다 축의
방향이 달라지면 네 장을 이어서 읽을 수 없다.
"""
import math
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#2b3238"
MUTED = "#77828a"
LINE = "#c3cbd1"
PALE = "#e4e7e9"
HOT = "#c4703c"
COOL = "#4a7286"
BAND = "#e8d9c4"


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#fdfcf9"/>
<text x="397" y="108" text-anchor="middle" font-size="34" font-weight="700" fill="{INK}">{title}</text>
<text x="397" y="150" text-anchor="middle" font-size="18" fill="{MUTED}">{subtitle}</text>
{body}
<text x="397" y="1072" text-anchor="middle" font-size="14" fill="#98a1a7">한 잔의 온도가 남긴 것</text>
</svg>'''


def text(x, y, value, size=17, fill=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}">{value}</text>')


def closing(y, value):
    return ('<rect x="117" y="%d" width="560" height="58" rx="16" fill="#f1efe8" stroke="%s"/>'
            % (y, LINE)) + text(397, y + 37, value, 20, INK, "700")


def cool_curve(x0, x1, y_top, y_bot, tau, samples=60):
    """식어 가는 곡선. tau가 작을수록 빨리 식는다."""
    pts = []
    for k in range(samples + 1):
        t = k / samples
        pts.append((x0 + (x1 - x0) * t, y_top + (y_bot - y_top) * (1 - math.exp(-t / tau))))
    return pts


def poly(pts, stroke, width=3.5, dash=""):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return ('<polyline points="' + " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
            + f'" fill="none" stroke="{stroke}" stroke-width="{width}"{d}/>')


# ── p8 잔이 정하는 길이 ──────────────────────────────────────────────
CUPS = [("얇고 입 넓은 잔", 0.20, HOT), ("보통 잔", 0.33, MUTED), ("두껍고 입 좁은 머그", 0.55, COOL)]


def fig_cups():
    b = [text(397, 205, "같은 물, 같은 양, 다른 잔", 19, INK, "700")]
    x0, x1, y_top, y_bot = 230, 720, 300, 640
    b.append(f'<line x1="{x0}" y1="{y_top - 20}" x2="{x0}" y2="{y_bot + 30}" stroke="{INK}" '
             f'stroke-width="2"/>')
    b.append(f'<line x1="{x0}" y1="{y_bot + 30}" x2="{x1}" y2="{y_bot + 30}" stroke="{INK}" '
             f'stroke-width="2"/>')
    b.append(text(x0 - 12, y_top - 4, "뜨겁다", 15, MUTED, "400", "end"))
    b.append(text(x0 - 12, y_bot + 6, "미지근하다", 15, MUTED, "400", "end"))
    b.append(text(x0 + 4, y_bot + 54, "부은 직후", 15, MUTED, "400", "start"))
    b.append(text(x1, y_bot + 54, "사십 분 뒤", 15, MUTED, "400", "end"))
    # 마시기 좋은 구간 띠
    band_top, band_bot = y_top + 96, y_top + 150
    b.append(f'<rect x="{x0}" y="{band_top}" width="{x1 - x0}" height="{band_bot - band_top}" '
             f'fill="{BAND}" fill-opacity="0.55"/>')
    b.append(text(x0 - 12, (band_top + band_bot) / 2 + 5, "마시기 좋은 구간", 14, "#8a6a44",
                  "700", "end"))
    for i, (name, tau, color) in enumerate(CUPS):
        pts = cool_curve(x0, x1, y_top, y_bot, tau)
        b.append(poly(pts, color))
        # 띠 안에 머무는 구간을 아래쪽 막대로 표시한다
        inside = [px for px, py in pts if band_top <= py <= band_bot]
        if inside:
            by = y_bot + 74 + i * 26
            b.append(f'<line x1="{min(inside):.1f}" y1="{by}" x2="{max(inside):.1f}" y2="{by}" '
                     f'stroke="{color}" stroke-width="7" stroke-linecap="round"/>')
            b.append(text(max(inside) + 12, by + 5, name, 14, color, "700", "start"))
    # 왼쪽 바깥 가운데의 잔 세 개
    for i, (wall, mouth) in enumerate(((3, 46), (6, 38), (10, 30))):
        cx = 84 + i * 46
        b.append(f'<path d="M{cx - mouth / 2} {498} L{cx - mouth / 2 + 6} {550} '
                 f'L{cx + mouth / 2 - 6} {550} L{cx + mouth / 2} {498} Z" fill="#ffffff" '
                 f'stroke="{INK}" stroke-width="{wall}"/>')
    b.append(text(84 + 46, 574, "얇은 잔 · 보통 · 머그", 13, MUTED))
    b.append(closing(870, "그릇을 고르는 일이 곧 시간을 고르는 일이다"))
    return base("잔이 정하는 길이", "1장 · 잔에 따라 달라지는 구간", "\n".join(b))


# ── p11 손이 나눈 여섯 칸 ────────────────────────────────────────────
STEPS = [("못 잡겠다", 0.06), ("잡을 수는 있다", 0.09), ("들고 있을 만하다", 0.11),
         ("따뜻하다", 0.16), ("미지근하다", 0.40), ("찼다", 0.18)]


def fig_six_steps():
    b = [text(397, 205, "손으로 읽은 눈금과 실제로 떨어진 온도", 19, INK, "700")]
    x0, x1 = 150, 720
    # 위 칸: 실제 곡선
    y_top, y_bot = 270, 500
    b.append(f'<line x1="{x0}" y1="{y_top - 16}" x2="{x0}" y2="{y_bot + 12}" stroke="{INK}" '
             f'stroke-width="2"/>')
    pts = cool_curve(x0, x1, y_top, y_bot, 0.30)
    b.append(poly(pts, COOL))
    b.append(text(x1 - 6, y_top + 6, "실제로 떨어진 온도", 16, COOL, "700", "end"))
    b.append(text(x0 + 4, y_bot + 40, "부은 직후", 15, MUTED, "400", "start"))
    b.append(text(x1, y_bot + 40, "사십 분 뒤", 15, MUTED, "400", "end"))
    # 아래 칸: 여섯 조각 띠
    band_y = 620
    cx = x0
    edges = []
    for name, frac in STEPS:
        w = (x1 - x0) * frac
        b.append(f'<rect x="{cx:.1f}" y="{band_y}" width="{w:.1f}" height="52" fill="{PALE}" '
                 f'stroke="{LINE}" stroke-width="1.5"/>')
        # 앞쪽 칸은 폭이 좁아 가로 이름표가 서로 겹친다. 기울여 단다.
        lx, ly = cx + w / 2, band_y + 68
        b.append(f'<text x="{lx:.1f}" y="{ly}" text-anchor="end" font-size="13" fill="{INK}" '
                 f'transform="rotate(-38 {lx:.1f} {ly})">{name}</text>')
        cx += w
        edges.append(cx)
    b.append(text(x0 - 12, band_y + 32, "손이 읽은 칸", 15, MUTED, "400", "end"))
    # 두 칸을 잇는 세로 점선 — 곡선과 만나는 자리가 위쪽에 몰린다
    for ex in edges[:-1]:
        t = (ex - x0) / (x1 - x0)
        cy = y_top + (y_bot - y_top) * (1 - math.exp(-t / 0.30))
        b.append(f'<line x1="{ex:.1f}" y1="{cy:.1f}" x2="{ex:.1f}" y2="{band_y}" stroke="{MUTED}" '
                 f'stroke-width="1.2" stroke-dasharray="4,5"/>')
        b.append(f'<circle cx="{ex:.1f}" cy="{cy:.1f}" r="4" fill="{COOL}"/>')
    b.append(closing(768, "손은 자처럼 고르게 되어 있지 않다"))
    return base("손이 나눈 여섯 칸", "2장 · 눈금과 실제", "\n".join(b))


# ── p18 앞의 오 분과 뒤의 오 분 ──────────────────────────────────────
def fig_five_minutes():
    b = [text(397, 205, "같은 오 분인데 떨어지는 폭이 다르다", 19, INK, "700")]
    x0, y0, top = 160, 660, 300
    n = 8
    bw = 52
    gap = 16
    drops = []
    prev = 0.0
    for k in range(n):
        t = (k + 1) / n
        cur = 1 - math.exp(-t / 0.30)
        drops.append(cur - prev)
        prev = cur
    scale = (y0 - top) / max(drops)
    b.append(f'<line x1="{x0 - 18}" y1="{y0}" x2="{x0 + n * (bw + gap)}" y2="{y0}" stroke="{INK}" '
             f'stroke-width="2"/>')
    b.append(f'<line x1="{x0 - 18}" y1="{top - 30}" x2="{x0 - 18}" y2="{y0}" stroke="{INK}" '
             f'stroke-width="2"/>')
    b.append(text(x0 - 24, top - 40, "그 오 분 동안 떨어진 폭", 15, MUTED, "400", "start"))
    for k, d in enumerate(drops):
        x = x0 + k * (bw + gap)
        h = d * scale
        b.append(f'<rect x="{x:.0f}" y="{y0 - h:.0f}" width="{bw}" height="{h:.0f}" rx="3" '
                 f'fill="{HOT}" fill-opacity="0.75"/>')
    b.append(text(x0 + 4, y0 + 30, "첫 오 분", 14, MUTED, "400", "start"))
    b.append(text(x0 + n * (bw + gap) - 20, y0 + 30, "마지막 오 분", 14, MUTED, "400", "end"))
    b.append(text(x0 + 4, y0 + 54, "부은 직후", 14, MUTED, "400", "start"))
    b.append(text(x0 + n * (bw + gap) - 20, y0 + 54, "사십 분 뒤", 14, MUTED, "400", "end"))
    # 멈춘 것처럼 보이는 구간
    lx = x0 + (n - 2) * (bw + gap)
    rx = x0 + n * (bw + gap) - gap
    b.append(f'<path d="M{lx} {y0 + 74} L{lx} {y0 + 86} L{rx} {y0 + 86} L{rx} {y0 + 74}" '
             f'fill="none" stroke="{MUTED}" stroke-width="2"/>')
    b.append(text((lx + rx) / 2, y0 + 112, "멈춘 것처럼 보이는 구간", 15, MUTED, "700"))
    b.append(closing(830, "잔이 시간을 알려 주기를 그만두는 자리가 있다"))
    return base("앞의 오 분과 뒤의 오 분", "3장 · 오 분씩 끊어 본 폭", "\n".join(b))


# ── p37 한 해의 잔들 ─────────────────────────────────────────────────
MONTHS = ["첫째 달", "둘째 달", "셋째 달", "넷째 달", "다섯째 달", "여섯째 달",
          "일곱째 달", "여덟째 달", "아홉째 달", "열째 달", "열한째 달", "열두째 달"]
DAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]


def cup_state(month, day, slot):
    """잔 하나의 상태. 무작위를 쓰지 않아 다시 실행해도 같은 그림이 나온다."""
    key = month * 7 + day * 3 + slot
    if month in (3, 4, 9, 10) and key % 9 == 0:
        return "poured"
    if month in (3, 4, 9, 10) and key % 11 == 0:
        return "reheated"
    if month in (6, 7):
        return "plain"
    if key % 23 == 0:
        return "poured"
    if key % 31 == 0:
        return "reheated"
    return "plain"


def fig_year_cups():
    b = [text(397, 200, "한 해 동안 부은 잔을 날짜 순서로 늘어놓으면", 19, INK, "700")]
    x0, y0, rh = 130, 268, 46
    slot_w = 7.4
    for m, n in enumerate(DAYS, start=1):
        cy = y0 + (m - 1) * rh
        b.append(text(x0 - 12, cy + 22, MONTHS[m - 1], 13, MUTED, "400", "end"))
        for d in range(1, n + 1):
            # 하루에 두 잔이지만 절반쯤은 한 잔이고 일부는 없다
            cups = 2 if (m * 5 + d * 3) % 4 else (0 if (m + d) % 11 == 0 else 1)
            for slot in range(cups):
                bx = x0 + (d - 1) * (slot_w * 2 + 2.2) + slot * slot_w
                state = cup_state(m, d, slot)
                if state == "plain":
                    b.append(f'<rect x="{bx:.1f}" y="{cy + 6}" width="{slot_w - 2:.1f}" '
                             f'height="26" rx="1.5" fill="{PALE}" stroke="{LINE}" '
                             f'stroke-width="0.8"/>')
                elif state == "poured":
                    b.append(f'<rect x="{bx:.1f}" y="{cy + 6}" width="{slot_w - 2:.1f}" '
                             f'height="26" rx="1.5" fill="none" stroke="{HOT}" '
                             f'stroke-width="1.6"/>')
                else:
                    b.append(f'<rect x="{bx:.1f}" y="{cy + 6}" width="{slot_w - 2:.1f}" '
                             f'height="26" rx="1.5" fill="{PALE}" stroke="{LINE}" '
                             f'stroke-width="0.8"/>')
                    b.append(f'<circle cx="{bx + (slot_w - 2) / 2:.1f}" cy="{cy + 2}" r="2.6" '
                             f'fill="{COOL}"/>')
    # 범례
    ly = 232
    b.append(f'<rect x="470" y="{ly}" width="14" height="16" rx="1.5" fill="{PALE}" stroke="{LINE}"/>')
    b.append(text(490, ly + 13, "마신 잔", 13, MUTED, "400", "start"))
    b.append(f'<rect x="556" y="{ly}" width="14" height="16" rx="1.5" fill="none" stroke="{HOT}" '
             f'stroke-width="1.6"/>')
    b.append(text(576, ly + 13, "버린 잔", 13, MUTED, "400", "start"))
    b.append(f'<rect x="642" y="{ly}" width="14" height="16" rx="1.5" fill="{PALE}" stroke="{LINE}"/>')
    b.append(f'<circle cx="649" cy="{ly - 4}" r="2.6" fill="{COOL}"/>')
    b.append(text(662, ly + 13, "되데운 잔", 13, MUTED, "400", "start"))
    # 여섯째·일곱째 줄 대괄호 — 표 오른쪽에 단다
    ty = y0 + 5 * rh + 4
    by = y0 + 7 * rh - 4
    rx = x0 + 31 * (slot_w * 2 + 2.2) + 12
    b.append(f'<path d="M{rx} {ty} L{rx + 10} {ty} L{rx + 10} {by} L{rx} {by}" '
             f'fill="none" stroke="{MUTED}" stroke-width="2"/>')
    b.append(text(rx + 18, (ty + by) / 2 - 10, "되데움도 버림도", 13, MUTED, "400", "start"))
    b.append(text(rx + 18, (ty + by) / 2 + 8, "거의 없던 두 달", 13, MUTED, "400", "start"))
    b.append(closing(866, "잔을 세는 일은 앉아 있을 수 있었던 날을 세는 일이었다"))
    return base("한 해의 잔들", "5장 · 한 해치 기록의 모양", "\n".join(b))


FIGURES = {8: fig_cups, 11: fig_six_steps, 18: fig_five_minutes, 37: fig_year_cups}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild014")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(fn(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
