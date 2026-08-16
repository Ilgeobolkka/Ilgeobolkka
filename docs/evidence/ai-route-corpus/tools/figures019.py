#!/usr/bin/env python3
"""book-019 이미지 페이지 네 개의 SVG를 생성한다.

사용: python3 figures019.py <출력디렉터리>

네 도표에서 표기를 고정했다 — 고정한 것(시각·길·방향·여섯 지점)은 언제나 굵은 실선이고 날마다
달라지는 것은 언제나 색이다. 이 권의 방법이 「셋을 못 박고 하나만 남긴다」이므로, 그림마다 이 구분이
흔들리면 네 장을 이어서 읽을 수 없다.
"""
import math
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#2b3238"
MUTED = "#77828a"
LINE = "#c3cbd1"
PALE = "#e2e5e7"
DUSK = "#4a5b7a"
LIGHT = "#d8b45c"
MARK = "#c4703c"

POINTS = ["담장", "건널목", "계단", "다리", "벤치", "가로등"]


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#fdfcf9"/>
<defs>
  <marker id="m" markerWidth="9" markerHeight="9" refX="8" refY="3" orient="auto">
    <path d="M0,0 L0,6 L8,3 z" fill="{MUTED}"/>
  </marker>
</defs>
<text x="397" y="108" text-anchor="middle" font-size="34" font-weight="700" fill="{INK}">{title}</text>
<text x="397" y="150" text-anchor="middle" font-size="18" fill="{MUTED}">{subtitle}</text>
{body}
<text x="397" y="1072" text-anchor="middle" font-size="14" fill="#98a1a7">저녁 여섯 시의 산책자</text>
</svg>'''


def text(x, y, value, size=17, fill=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}">{value}</text>')


def closing(y, value):
    return ('<rect x="97" y="%d" width="600" height="58" rx="16" fill="#f1efe8" stroke="%s"/>'
            % (y, LINE)) + text(397, y + 37, value, 20, INK, "700")


# ── p8 고정한 셋과 남는 하나 ─────────────────────────────────────────
def fig_fixed():
    b = [text(397, 205, "시각과 길과 방향을 못 박으면 남는 것은 날짜뿐이다", 18, INK, "700")]
    bx, by, bw, bh = 80, 268, 200, 74
    for i, (label, locked) in enumerate((("시각 여섯 시", True), ("같은 길", True),
                                         ("시계 반대 방향", True), ("날짜", False))):
        y = by + i * (bh + 16)
        dash = "" if locked else ' stroke-dasharray="7,6"'
        wdt = 2.6 if locked else 1.6
        b.append(f'<rect x="{bx}" y="{y}" width="{bw}" height="{bh}" rx="10" fill="#ffffff" '
                 f'stroke="{INK if locked else MUTED}" stroke-width="{wdt}"{dash}/>')
        b.append(text(bx + bw / 2, y + bh / 2 + 6, label, 16, INK if locked else MUTED,
                      "700" if locked else "400"))
        if locked:
            lx, ly = bx + bw - 26, y + 16
            b.append(f'<rect x="{lx - 7}" y="{ly}" width="14" height="11" rx="2" fill="{INK}"/>')
            b.append(f'<path d="M{lx - 4} {ly} L{lx - 4} {ly - 5} A4 4 0 0 1 {lx + 4} {ly - 5} '
                     f'L{lx + 4} {ly}" fill="none" stroke="{INK}" stroke-width="1.6"/>')
    # 오른쪽 고리
    cx, cy, rx, ry = 520, 470, 160, 150
    b.append(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="none" stroke="{INK}" '
             f'stroke-width="3"/>')
    angles = [200, 250, 300, 350, 60, 130]
    for i, (name, ang) in enumerate(zip(POINTS, angles)):
        a = math.radians(ang)
        px, py = cx + rx * math.cos(a), cy + ry * math.sin(a)
        b.append(f'<circle cx="{px:.0f}" cy="{py:.0f}" r="9" fill="#ffffff" stroke="{INK}" '
                 f'stroke-width="2.4"/>')
        lx = px + 34 * math.cos(a)
        ly = py + 30 * math.sin(a)
        b.append(text(lx, ly + 4, name, 13, INK, "700"))
        b.append(f'<line x1="{px + 12 * math.cos(a):.0f}" y1="{py + 12 * math.sin(a):.0f}" '
                 f'x2="{px + 20 * math.cos(a):.0f}" y2="{py + 20 * math.sin(a):.0f}" '
                 f'stroke="{MUTED}" stroke-width="2.4"/>')
    a = math.radians(170)
    hx, hy = cx + rx * math.cos(a), cy + ry * math.sin(a)
    b.append(f'<circle cx="{hx:.0f}" cy="{hy:.0f}" r="7" fill="{INK}"/>')
    b.append(text(hx - 34, hy + 5, "집", 14, INK, "700"))
    for ang in (215, 285, 15, 105):
        a = math.radians(ang)
        px, py = cx + rx * math.cos(a), cy + ry * math.sin(a)
        tang = math.radians(ang - 90)
        b.append(f'<line x1="{px:.0f}" y1="{py:.0f}" '
                 f'x2="{px + 16 * math.cos(tang):.0f}" y2="{py + 16 * math.sin(tang):.0f}" '
                 f'stroke="{MUTED}" stroke-width="2.4" marker-end="url(#m)"/>')
    b.append(text(cx, cy - 6, "삼십 분", 16, MUTED, "700"))
    b.append(text(cx, cy + 18, "스무 걸음쯤 멈춤", 13, MUTED))
    b.append(closing(700, "남는 것이 하나일 때 달라진 까닭을 말할 수 있다"))
    return base("고정한 셋과 남는 하나", "1장 · 못 박은 것들", "\n".join(b))


# ── p11 네 개의 다른 시계 ────────────────────────────────────────────
def fig_four_clocks():
    b = [text(397, 205, "같은 삼십 분 안에서 달라지는 속도가 서로 다르다", 18, INK, "700")]
    x0, x1 = 200, 720
    rows = [("빛", "하루마다"), ("사람", "요일마다"), ("자라는 것", "몇 주마다"),
            ("놓인 것", "아무 때나")]
    y0, rh = 280, 108
    for i, (name, pace) in enumerate(rows):
        cy = y0 + i * rh
        b.append(text(x0 - 16, cy + 5, name, 16, INK, "700", "end"))
        b.append(f'<line x1="{x0}" y1="{cy + 42}" x2="{x1}" y2="{cy + 42}" stroke="{PALE}" '
                 f'stroke-width="1.4"/>')
        b.append(text(x1 + 8, cy + 5, pace, 13, MUTED, "400", "start"))
        if i == 0:
            pts = [(x0 + (x1 - x0) * t / 60,
                    cy + 26 - 42 * math.sin(math.pi * t / 60)) for t in range(61)]
            b.append('<polyline points="' + " ".join(f"{px:.1f},{py:.1f}" for px, py in pts)
                     + f'" fill="none" stroke="{LIGHT}" stroke-width="3.4"/>')
        elif i == 1:
            for k in range(36):
                px = x0 + (x1 - x0) * k / 36 + 4
                h = 34 if k % 7 == 0 else 16
                b.append(f'<line x1="{px:.1f}" y1="{cy + 40}" x2="{px:.1f}" y2="{cy + 40 - h}" '
                         f'stroke="{MARK}" stroke-width="3.2" stroke-opacity="0.8"/>')
        elif i == 2:
            pts = [(x0 + (x1 - x0) * t / 60,
                    cy + 26 - 24 * math.sin(math.pi * t / 60) ** 1.4) for t in range(61)]
            b.append('<polyline points="' + " ".join(f"{px:.1f},{py:.1f}" for px, py in pts)
                     + f'" fill="none" stroke="#6f8f7a" stroke-width="15" stroke-opacity="0.35" '
                     f'stroke-linecap="round"/>')
        else:
            spots = [0.02, 0.05, 0.06, 0.13, 0.27, 0.29, 0.30, 0.44, 0.58, 0.60,
                     0.72, 0.86, 0.87, 0.92]
            for s in spots:
                px = x0 + (x1 - x0) * s
                b.append(f'<circle cx="{px:.1f}" cy="{cy + 24}" r="5" fill="{DUSK}" '
                         f'fill-opacity="0.7"/>')
    b.append(text(x0, y0 + 4 * rh - 40, "한 해의 시작", 14, MUTED, "400", "start"))
    b.append(text(x1, y0 + 4 * rh - 40, "한 해의 끝", 14, MUTED, "400", "end"))
    b.append(closing(760, "한 화면에 네 개의 시계가 함께 돌고 있다"))
    return base("네 개의 다른 시계", "2장 · 달라지는 속도", "\n".join(b))


# ── p18 서른과 백 사이 ───────────────────────────────────────────────
def fig_thirty_hundred():
    b = [text(397, 205, "몇 번째 산책이냐에 따라 적는 양과 종류가 달라진다", 18, INK, "700")]
    x0, x1, top, base_y = 170, 710, 280, 620
    marks = [(0.0, "첫 번째"), (0.10, "서른 번째"), (0.33, "백 번째"), (1.0, "삼백 번째")]

    def curve_y(t):
        return base_y - (250 * math.exp(-t / 0.05) + 60 + 150 * (t ** 1.4))

    pts = [(x0 + (x1 - x0) * (i / 120), curve_y(i / 120)) for i in range(121)]
    # 구간 배경
    zones = [(0.0, 0.10, "무엇이 있는지 적는다", 0.16), (0.10, 0.33, "적을 것이 없다", 0.07),
             (0.33, 1.0, "무엇이 달라졌는지 적는다", 0.16)]
    for k, (a, c, label, op) in enumerate(zones):
        ax, cxx = x0 + (x1 - x0) * a, x0 + (x1 - x0) * c
        b.append(f'<rect x="{ax:.1f}" y="{top}" width="{cxx - ax:.1f}" height="{base_y - top}" '
                 f'fill="{MUTED}" fill-opacity="{op}"/>')
        # 구간 이름은 왼쪽 구간이 좁아 아래에 나란히 적으면 겹친다. 위쪽에 범례로 모은다.
        lx = 380 + k * 118
        b.append(f'<rect x="{lx}" y="234" width="14" height="14" fill="{MUTED}" '
                 f'fill-opacity="{op}" stroke="{LINE}"/>')
        b.append(text(lx + 20, 246, label, 11.5, MUTED, "400", "start"))
    b.append('<polyline points="' + " ".join(f"{px:.1f},{py:.1f}" for px, py in pts)
             + f'" fill="none" stroke="{INK}" stroke-width="3"/>')
    b.append(f'<line x1="{x0}" y1="{base_y}" x2="{x1}" y2="{base_y}" stroke="{INK}" '
             f'stroke-width="1.8"/>')
    b.append(f'<line x1="{x0}" y1="{top}" x2="{x0}" y2="{base_y}" stroke="{INK}" '
             f'stroke-width="1.8"/>')
    b.append(text(x0 - 12, top + 6, "한 번에 적은 양", 14, MUTED, "400", "end"))
    for t, label in marks:
        px = x0 + (x1 - x0) * t
        b.append(f'<line x1="{px:.1f}" y1="{base_y}" x2="{px:.1f}" y2="{base_y + 7}" '
                 f'stroke="{INK}" stroke-width="1.6"/>')
        b.append(text(px, base_y + 48, label, 12, INK))
    # 그만두기 쉬운 자리
    ax, cxx = x0 + (x1 - x0) * 0.10, x0 + (x1 - x0) * 0.33
    b.append(f'<path d="M{ax:.1f} {top - 14} L{ax:.1f} {top - 24} L{cxx:.1f} {top - 24} '
             f'L{cxx:.1f} {top - 14}" fill="none" stroke="{MARK}" stroke-width="2"/>')
    b.append(text((ax + cxx) / 2, top - 32, "그만두기 쉬운 자리", 13, MARK, "700"))
    b.append(f'<rect x="{ax + 26:.1f}" y="{base_y - 40}" width="26" height="40" '
             f'fill="{MARK}" fill-opacity="0.22"/>')
    b.append(text(ax + 118, base_y - 48, "두 주쯤 거의 안 나감", 12, MARK, "700"))
    b.append(closing(720, "양은 되돌아왔지만 적는 종류가 바뀌었다"))
    return base("서른과 백 사이", "3장 · 회차에 따른 기록", "\n".join(b))


# ── p37 삼백여섯 번 ──────────────────────────────────────────────────
def light_stage(day):
    """날짜에서 밝기 세 단계를 정한다. 무작위를 쓰지 않아 다시 실행해도 같다."""
    v = math.sin(math.pi * ((day - 15) % 365) / 365) ** 1.2
    if v > 0.72:
        return "bright"
    if v > 0.42:
        return "dusk"
    return "dark"


SKIP_RUNS = [(48, 62, "서른에서 백 사이"), (188, 202, "더운 두 주"), (342, 358, "한 해 끝")]
# 5.2·5.3 본문이 센 값. 도표는 이 값에서 만들어야 본문과 어긋나지 않는다.
MISSED_DAYS = 59


def _skipped_set():
    """못 나간 날을 정확히 MISSED_DAYS개 고른다.

    세 구간을 먼저 채우고 남은 자리를 고르게 나눠 합계를 맞춘다. 나머지 연산만 쓰면 개수가
    본문과 어긋나므로 이렇게 한다. 무작위를 쓰지 않아 다시 실행해도 같은 그림이 나온다.
    """
    picked = {d for a, c, _ in SKIP_RUNS for d in range(a, c + 1)}
    rest = [d for d in range(365) if d not in picked]
    need = MISSED_DAYS - len(picked)
    step = len(rest) / need
    return picked | {rest[int(i * step)] for i in range(need)}


SKIPPED = _skipped_set()


def skipped(day):
    return day in SKIPPED


def fig_year_walks():
    b = [text(397, 200, "여섯 시의 밝기와 나간 날을 한 줄로 늘어놓으면", 18, INK, "700")]
    x0, wtot = 100, 616
    cw = wtot / 365
    colors = {"bright": LIGHT, "dusk": "#9aa3ad", "dark": DUSK}
    # 범례를 띠 위에 둔다
    ly = 238
    for k, (key, label) in enumerate((("bright", "밝음"), ("dusk", "어스름"), ("dark", "어두움"))):
        lx = 400 + k * 110
        b.append(f'<rect x="{lx}" y="{ly}" width="15" height="15" fill="{colors[key]}"/>')
        b.append(text(lx + 22, ly + 13, label, 13, MUTED, "400", "start"))
    y1 = 292
    for d in range(365):
        b.append(f'<rect x="{x0 + d * cw:.2f}" y="{y1}" width="{cw + 0.4:.2f}" height="54" '
                 f'fill="{colors[light_stage(d)]}"/>')
    b.append(text(x0, y1 - 10, "한 해의 시작", 13, MUTED, "400", "start"))
    b.append(text(x0 + wtot, y1 - 10, "한 해의 끝", 13, MUTED, "400", "end"))
    # 어스름 구간 대괄호 — 띠 바로 아래
    for a, c in ((0.14, 0.24), (0.70, 0.80)):
        ax, cxx = x0 + wtot * a, x0 + wtot * c
        b.append(f'<path d="M{ax:.1f} {y1 + 62} L{ax:.1f} {y1 + 70} L{cxx:.1f} {y1 + 70} '
                 f'L{cxx:.1f} {y1 + 62}" fill="none" stroke="{MARK}" stroke-width="1.8"/>')
        b.append(text((ax + cxx) / 2, y1 + 88, "몇 주밖에 안 걸린다", 11, MARK, "700"))
    # 두 번째 띠: 나간 날
    y2 = y1 + 170
    for d in range(365):
        if skipped(d):
            continue
        b.append(f'<rect x="{x0 + d * cw:.2f}" y="{y2}" width="{max(cw - 0.4, 0.9):.2f}" '
                 f'height="46" fill="{MUTED}" fill-opacity="0.62"/>')
    b.append(text(x0 - 10, y2 + 28, "나간 날", 13, MUTED, "400", "end"))
    # 빈칸이 몰린 세 자리 — 띠 아래에 지시선과 함께
    for a, c, label in SKIP_RUNS:
        mx = x0 + wtot * ((a + c) / 2 / 365)
        b.append(f'<line x1="{mx:.1f}" y1="{y2 + 50}" x2="{mx:.1f}" y2="{y2 + 66}" '
                 f'stroke="{MARK}" stroke-width="1.6"/>')
        b.append(text(mx, y2 + 84, label, 11, MARK, "700"))
    # 합계 막대 — 훨씬 아래에
    by = y2 + 220
    for k, (label, val) in enumerate((("나감", 365 - MISSED_DAYS), ("못 나감", MISSED_DAYS),
                                      ("규칙 어긋남", 50))):
        bx = 220 + k * 150
        h = val * 0.3
        b.append(f'<rect x="{bx}" y="{by - h:.0f}" width="46" height="{h:.0f}" rx="3" '
                 f'fill="{MUTED}" fill-opacity="0.5"/>')
        b.append(text(bx + 23, by + 20, label, 13, MUTED))
        b.append(text(bx + 23, by - h - 8, str(val), 12, MUTED, "700"))
    b.append(closing(880, "걷는 동안에는 천천히 바뀌는 것 같았다"))
    return base("삼백여섯 번", "5장 · 한 해 치 산책", "\n".join(b))


FIGURES = {8: fig_fixed, 11: fig_four_clocks, 18: fig_thirty_hundred, 37: fig_year_walks}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild019")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(fn(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
