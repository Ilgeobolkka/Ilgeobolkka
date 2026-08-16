#!/usr/bin/env python3
"""book-090 이미지 페이지 6개의 SVG 생성. figures081~089의 t()/base() 패턴을 그대로 쓴다.

여섯 도표가 모두 '사막의 하루가 둘로 나뉜다'를 다른 각도에서 그린다. 걷는 시간과 더운 낮은 진한 색,
멈춰 있는 시간과 식은 밤은 옅은 색으로 고정한다.

사용: python3 figures090.py <출력디렉터리>
"""
import math
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
KEEP = "#3f6f66"
DROP = "#c3ccd0"
MARK = "#b4703a"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="keep" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{KEEP}"/></marker>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <pattern id="gravel" width="14" height="14" patternUnits="userSpaceOnUse">
    <rect width="14" height="14" fill="#eef1f1"/><circle cx="4" cy="4" r="2.4" fill="{LINE}"/><circle cx="11" cy="10" r="1.8" fill="{LINE}"/>
  </pattern>
  <pattern id="grain" width="10" height="10" patternUnits="userSpaceOnUse">
    <rect width="10" height="10" fill="#f4efe4"/><circle cx="3" cy="3" r="1" fill="{MARK}"/><circle cx="8" cy="7" r="1" fill="{MARK}"/>
  </pattern>
  <pattern id="dune" width="26" height="13" patternUnits="userSpaceOnUse">
    <rect width="26" height="13" fill="#f2e8d6"/><path d="M0,10 q6.5,-8 13,0 q6.5,8 13,0" fill="none" stroke="{MARK}" stroke-width="1.6"/>
  </pattern>
</defs>
{t(W / 2, 122, title, 31, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


def tent(x, y, w=17, h=30, color=KEEP):
    """잔 자리 기호. (x, y)가 바닥 가운데다."""
    return f'<path d="M{x - w},{y} L{x},{y - h} L{x + w},{y} z" fill="none" stroke="{color}" stroke-width="2.4"/>'


def walker(x, y, color=KEEP):
    """걷는 기호. (x, y)가 발밑이다."""
    return (f'<circle cx="{x}" cy="{y - 30}" r="5" fill="{color}"/>'
            f'<path d="M{x},{y - 25} L{x},{y - 12} M{x},{y - 12} L{x - 7},{y} M{x},{y - 12} L{x + 7},{y} '
            f'M{x - 8},{y - 22} L{x + 8},{y - 19}" fill="none" stroke="{color}" stroke-width="2.2"/>')


def canopy(x, y, color=MARK):
    """그늘 천 기호. (x, y)가 바닥 가운데다."""
    return (f'<path d="M{x - 26},{y - 26} q26,-10 52,0" fill="none" stroke="{color}" stroke-width="2.4"/>'
            f'<path d="M{x - 26},{y - 26} L{x - 26},{y} M{x + 26},{y - 26} L{x + 26},{y}" '
            f'fill="none" stroke="{color}" stroke-width="2"/>')


# ── p7 같은 거리, 다른 시간 ────────────────────────────────────────────
def fig_surface_time():
    b = []
    rows = [("굳은 바닥", "gravel", "두 시간"),
            ("얕은 모래", "grain", "세 시간"),
            ("모래언덕", "dune", "네 시간 반")]
    for i, (name, pat, hours) in enumerate(rows):
        y = 250 + i * 78
        b.append(f'<rect x="212" y="{y}" width="386" height="48" rx="6" fill="url(#{pat})" stroke="{LINE}"/>')
        b.append(t(198, y + 31, name, 16, INK, "600", "end"))
        b.append(t(614, y + 31, hours, 17, MARK, "700", "start"))
    b.append(t(405, 236, "세 막대의 길이는 모두 같은 거리다", 14, MUTED))

    b.append(f'<line x1="150" y1="505" x2="644" y2="505" stroke="#e2e6e7"/>')
    b.append(t(397, 545, "위에서 본 두 경로", 17, INK, "700"))

    b.append(f'<ellipse cx="420" cy="735" rx="152" ry="58" fill="url(#dune)" stroke="{MARK}" stroke-opacity="0.5"/>')
    b.append(t(420, 700, "모래언덕", 16, "#7a4f24", "700"))
    b.append(f'<circle cx="212" cy="735" r="9" fill="{INK}"/>')
    b.append(f'<circle cx="628" cy="735" r="9" fill="{INK}"/>')
    b.append(t(212, 776, "떠난 자리", 14, MUTED))
    b.append(t(628, 776, "닿을 자리", 14, MUTED))

    b.append(f'<path d="M221,735 L604,735" fill="none" stroke="{LINE}" stroke-width="3" '
             f'stroke-dasharray="8 6" marker-end="url(#gray)"/>')
    b.append(t(420, 820, "넘어가는 길 — 거리는 짧고 시간은 길다", 15, MUTED))

    b.append(f'<path d="M213,724 q207,-122 404,-4" fill="none" stroke="{KEEP}" stroke-width="3.4" '
             f'marker-end="url(#keep)"/>')
    b.append(t(420, 636, "돌아가는 길 — 거리는 길고 시간은 짧다", 15, KEEP, "600"))

    b.append(note_box(197, 880, 400, "거리보다 바닥을 본다"))
    return base("같은 거리, 다른 시간", "바닥의 종류가 정하는 하루의 폭", "".join(b))


# ── p15 하루의 네 토막 ─────────────────────────────────────────────────
def fig_day_four():
    b = []
    x0, x1 = 130, 664
    base_y, top_y = 820, 400
    span = x1 - x0

    def px(h):
        return x0 + span * h / 24.0

    def value(h):
        if h < 1:
            h += 24
        if h <= 10:
            return 0.5 - 0.5 * math.cos(math.pi * (h - 1) / 9)
        return 0.5 + 0.5 * math.cos(math.pi * (h - 10) / 15)

    def py(h):
        return base_y - value(h) * (base_y - top_y)

    bands = [(1, 7, "걷는다", KEEP, 0.30), (7, 13, "그늘에 있는다", MARK, 0.20),
             (13, 17, "걷는다", KEEP, 0.30), (17, 25, "잔다", LINE, 0.16)]
    for lo, hi, label, color, op in bands:
        pts = [f"{px(min(h, 24)):.1f},{py(h):.1f}" for h in
               [lo + (hi - lo) * i / 40.0 for i in range(41)]]
        b.append(f'<polygon points="{px(lo):.1f},{base_y} ' + " ".join(pts) +
                 f' {px(min(hi, 24)):.1f},{base_y}" fill="{color}" fill-opacity="{op}"/>')
        b.append(f'<line x1="{px(min(hi, 24)):.1f}" y1="{base_y}" x2="{px(min(hi, 24)):.1f}" '
                 f'y2="{py(hi):.1f}" stroke="#ffffff" stroke-width="2"/>')

    curve = " ".join(f"{px(h * 24 / 96.0):.1f},{py(h * 24 / 96.0):.1f}" for h in range(97))
    b.append(f'<polyline points="{curve}" fill="none" stroke="{INK}" stroke-width="3"/>')
    b.append(f'<line x1="{x0}" y1="{base_y}" x2="{x1}" y2="{base_y}" stroke="{LINE}" stroke-width="2"/>')

    for h, label in [(0, "새벽 4시"), (8, "정오"), (14, "저녁 6시"), (20, "자정"), (24, "다음 새벽")]:
        b.append(f'<line x1="{px(h):.1f}" y1="{base_y}" x2="{px(h):.1f}" y2="{base_y + 7}" stroke="{LINE}"/>')
        b.append(t(px(h), base_y + 26, label, 13, MUTED))

    for lo, hi, label, color, _ in bands:
        cx = px((lo + min(hi, 24)) / 2.0)
        b.append(t(cx, 884, label, 15, color if color != LINE else MUTED, "700"))
    b.append(walker(px(4), 940))
    b.append(canopy(px(10), 940))
    b.append(walker(px(15), 940))
    b.append(f'<path d="M{px(21) - 20},{940} q20,-26 40,0" fill="none" stroke="{LINE}" stroke-width="2.4"/>')
    b.append(t(px(21), 918, "쉼", 13, MUTED))

    hi_y, lo_y = py(10), py(1)
    b.append(f'<line x1="{px(10):.1f}" y1="{hi_y:.1f}" x2="694" y2="{hi_y:.1f}" stroke="{MARK}" stroke-dasharray="5 5"/>')
    b.append(f'<line x1="{px(1):.1f}" y1="{lo_y:.1f}" x2="694" y2="{lo_y:.1f}" stroke="{MARK}" stroke-dasharray="5 5"/>')
    b.append(f'<line x1="694" y1="{hi_y:.1f}" x2="694" y2="{lo_y:.1f}" stroke="{MARK}" stroke-width="2.4" '
             f'marker-start="url(#mark)" marker-end="url(#mark)"/>')
    b.append(f'<rect x="466" y="272" width="228" height="66" rx="12" fill="#ffffff" stroke="{MARK}" stroke-opacity="0.6"/>')
    b.append(t(580, 300, "가장 높은 때와 가장 낮은 때", 14, "#7a4f24"))
    b.append(t(580, 324, "서른 도 넘게 차이", 16, "#7a4f24", "700"))

    b.append(note_box(197, 980, 400, "더운 것보다 두 번 바뀌는 것이 문제다"))
    return base("하루의 네 토막", "기온 곡선이 정하는 걷는 시각", "".join(b))


# ── p21 맑을수록 춥다 ──────────────────────────────────────────────────
def fig_clear_cold():
    b = []
    b.append(f'<line x1="397" y1="232" x2="397" y2="880" stroke="#e2e6e7"/>')
    panels = [(110, "맑은 밤", True), (409, "흐린 밤", False)]
    for x, name, clear in panels:
        cx = x + 137
        b.append(t(cx, 258, name, 20, INK, "700"))
        b.append(f'<rect x="{x}" y="756" width="275" height="46" fill="url(#grain)" stroke="{LINE}" stroke-opacity="0.5"/>')

        if clear:
            b.append(t(cx - 22, 306, "열이 그대로 빠져나간다", 15, KEEP, "600"))
            for sx, sy, r in [(150, 350, 3), (196, 386, 2.2), (238, 342, 2.6), (286, 378, 3),
                              (330, 348, 2.2), (172, 424, 2.4), (262, 420, 2.8), (330, 424, 2.2)]:
                b.append(f'<circle cx="{sx}" cy="{sy}" r="{r}" fill="{INK}"/>')
            for ax in (152, 205, 258, 311):
                b.append(f'<path d="M{ax},745 L{ax},466" fill="none" stroke="{KEEP}" stroke-width="2.6" '
                         f'marker-end="url(#keep)"/>')
            fill_top = 700
            reading = "가장 추운 밤"
        else:
            b.append(t(cx - 22, 306, "일부가 되돌아온다", 15, MARK, "600"))
            for i in range(4):
                cxx = 452 + i * 52
                b.append(f'<ellipse cx="{cxx}" cy="470" rx="38" ry="17" fill="{SOFT}" stroke="{LINE}" stroke-opacity="0.6"/>')
            b.append(t(cx - 22, 350, "구름층", 15, MUTED))
            b.append(f'<line x1="420" y1="365" x2="560" y2="450" stroke="{LINE}" stroke-dasharray="4 4"/>')
            for ax in (451, 504, 557):
                b.append(f'<path d="M{ax},745 L{ax},500" fill="none" stroke="{KEEP}" stroke-width="2.6" '
                         f'marker-end="url(#keep)"/>')
            for ax in (477, 530, 583):
                b.append(f'<path d="M{ax},500 L{ax},640" fill="none" stroke="{MARK}" stroke-width="2.6" '
                         f'marker-end="url(#mark)"/>')
            b.append(t(cx - 22, 878, "되돌아온 열이 지면을 덜 식힌다", 13, MUTED))
            fill_top = 560
            reading = "덜 춥다"

        tx = x + 232
        b.append(f'<rect x="{tx}" y="540" width="20" height="200" rx="10" fill="#ffffff" stroke="{INK}" stroke-width="2"/>')
        b.append(f'<rect x="{tx + 4}" y="{fill_top}" width="12" height="{744 - fill_top}" fill="{MARK}"/>')
        b.append(f'<circle cx="{tx + 10}" cy="752" r="15" fill="{MARK}"/>')
        b.append(t(tx + 10, 524, "기온", 13, MUTED))
        b.append(t(cx, 846, reading, 17, INK, "700"))
        if clear:
            b.append(t(cx - 22, 878, "막는 것이 없어 열이 다 빠져나간다", 13, MUTED))

    b.append(note_box(197, 930, 400, "별이 잘 보이는 밤이 가장 춥다"))
    return base("맑을수록 춥다", "밤하늘의 상태와 지면이 식는 정도", "".join(b))


# ── p29 물에서 멀어지는 순서 ──────────────────────────────────────────
def fig_oasis_rings():
    b = []
    cx, cy = 397, 600
    rings = [(300, "여행자가 묵는 곳", "이백 걸음", "#f3f5f5"),
             (224, "가축 자리", "백 걸음", "#e9eeee"),
             (148, "집", "쉰 걸음", "#dfe7e6"),
             (84, "밭", "스무 걸음", "#d3e0dc")]
    for r, _, _, fill in rings:
        b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" stroke="{LINE}" stroke-opacity="0.7"/>')
    b.append(f'<circle cx="{cx}" cy="{cy}" r="32" fill="{KEEP}"/>')
    b.append(t(cx, cy + 7, "물", 18, "#ffffff", "700"))

    bands = [(84, 32, "밭", "스무 걸음"), (148, 84, "집", "쉰 걸음"),
             (224, 148, "가축 자리", "백 걸음"), (300, 224, "여행자가 묵는 곳", "이백 걸음")]
    for outer, inner, name, dist in bands:
        mid = (outer + inner) / 2.0
        b.append(t(cx, cy - mid + 6, name, 16, INK, "700"))
        b.append(t(cx, cy + mid + 6, dist, 14, MUTED))
    b.append(t(cx, 278, "사막", 18, MARK, "700"))

    b.append(caption(110, 250, ["물은 마을의 살림이다.", "쓰는 차례가 거리로 나타난다."], 15))
    b.append(note_box(197, 950, 400, "거리가 곧 차례다"))
    return base("물에서 멀어지는 순서", "오아시스 마을의 자리 배치", "".join(b))


# ── p37 나흘의 자리 ────────────────────────────────────────────────────
def fig_four_days():
    b = []
    b.append(tent(150, 236, 12, 20, LINE))
    b.append(t(170, 236, "잔 자리", 13, MUTED, "400", "start"))
    b.append(f'<circle cx="300" cy="228" r="8" fill="{KEEP}"/>')
    b.append(t(316, 236, "물이 있는 자리", 13, MUTED, "400", "start"))

    line_y = 320
    b.append(f'<line x1="120" y1="{line_y}" x2="674" y2="{line_y}" stroke="{LINE}" stroke-width="2"/>')
    waters = [(150, "어귀의 우물", True), (300, "물빛마을", True), (470, "우물", True), (640, "마르는 자리", False)]
    for x, name, sure in waters:
        b.append(f'<circle cx="{x}" cy="{line_y}" r="9" fill="{KEEP if sure else "#ffffff"}" '
                 f'stroke="{KEEP}" stroke-width="2" stroke-dasharray="{"" if sure else "3 3"}"/>')
        b.append(t(x, 278, name, 14, INK if sure else MUTED, "600" if sure else "400"))
    b.append(t(640, 258, "믿지 않는다", 12, MARK))

    for x in (225, 300, 470):
        b.append(tent(x, line_y, 16, 28))

    b.append(f'<path d="M666,362 L586,362" fill="none" stroke="{MARK}" stroke-width="2.4" marker-end="url(#mark)"/>')
    b.append(t(672, 390, "돌아 나온 방향", 13, MARK, "400", "end"))

    days = [("첫날", 96, 74), ("둘째 날", 84, 92), ("셋째 날", 0, 0), ("넷째 날", 90, 80)]
    for i, (name, w1, w2) in enumerate(days):
        x = 122 + i * 138
        b.append(f'<rect x="{x}" y="404" width="126" height="248" rx="12" fill="#fbfbfa" stroke="{SOFT}"/>')
        b.append(t(x + 63, 436, name, 16, INK, "700"))
        if w1 == 0:
            b.append(f'<rect x="{x + 18}" y="468" width="90" height="152" rx="10" fill="{SOFT}"/>')
            b.append(t(x + 63, 552, "쉬는 날", 16, MUTED, "700"))
            continue
        b.append(f'<rect x="{x + 63 - w1 / 2}" y="472" width="{w1}" height="22" rx="6" fill="{KEEP}"/>')
        b.append(t(x + 63, 512, "걷는다", 12, MUTED))
        b.append(canopy(x + 63, 578))
        b.append(t(x + 63, 596, "그늘", 12, MARK))
        b.append(f'<rect x="{x + 63 - w2 / 2}" y="612" width="{w2}" height="22" rx="6" fill="{KEEP}"/>')
    b.append(t(397, 686, "막대의 길이가 그날 걸은 두 구간이다", 14, MUTED))

    b.append(note_box(197, 730, 400, "걷지 않는 하루가 걷는 사흘을 만든다"))
    b.append(caption(397, 850, ["셋째 날을 비운 것은 물빛마을에서 쉬었기 때문이다.",
                                "이 하루가 남은 이틀의 걷는 속도를 정했다."], 15))
    return base("나흘의 자리", "물과 잠자리와 걷는 구간", "".join(b))


# ── p43 무엇으로 방향을 잡는가 ────────────────────────────────────────
def fig_direction_table():
    b = []
    cols = ["맑은 낮", "맑은 밤", "흐린 밤", "모래바람 뒤"]
    rows = [("별", [False, True, False, False]),
            ("멀리 보이는 바위산", [True, True, False, True]),
            ("발자국과 작은 언덕", [True, False, False, False])]
    x0, cw = 270, 105
    for j, name in enumerate(cols):
        b.append(t(x0 + cw * j + cw / 2, 300, name, 14, MUTED, "600"))
    for i, (name, cells) in enumerate(rows):
        y = 330 + i * 120
        b.append(t(256, y + 58, name, 15, INK, "600", "end"))
        for j, ok in enumerate(cells):
            x = x0 + cw * j
            b.append(f'<rect x="{x + 4}" y="{y}" width="{cw - 8}" height="100" rx="10" '
                     f'fill="{KEEP if ok else "#eef1f1"}" stroke="{LINE}" stroke-opacity="0.5"/>')
            b.append(t(x + cw / 2, y + 57, "쓸 수 있다" if ok else "어렵다", 14,
                       "#ffffff" if ok else "#93a0a4", "700" if ok else "400"))

    b.append(f'<rect x="272" y="712" width="26" height="20" rx="5" fill="{KEEP}"/>')
    b.append(t(308, 728, "쓸 수 있다", 14, MUTED, "400", "start"))
    b.append(f'<rect x="430" y="712" width="26" height="20" rx="5" fill="#eef1f1" stroke="{LINE}" stroke-opacity="0.5"/>')
    b.append(t(466, 728, "그 상황에서는 어렵다", 14, MUTED, "400", "start"))
    b.append(t(397, 790, "세 줄이 한꺼번에 어두워지는 칸은 흐린 밤 하나뿐이다.", 15, MUTED))

    b.append(note_box(197, 830, 400, "어느 하나만 믿지 않는다"))
    return base("무엇으로 방향을 잡는가", "수단 셋과 상황 넷", "".join(b))


FIGURES = {
    7: fig_surface_time,
    15: fig_day_four,
    21: fig_clear_cold,
    29: fig_oasis_rings,
    37: fig_four_days,
    43: fig_direction_table,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild090")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
