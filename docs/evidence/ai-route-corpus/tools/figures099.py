#!/usr/bin/env python3
"""book-099 이미지 페이지 4개의 SVG 생성. figures091.py의 t()/base() 패턴을 따른다.

네 도표의 형식을 모두 다르게 잡았다. 눈금자 셋, 수직선 구간, 확인 간격 세 줄, 주별 꺾은선이다.
공통 약속은 둘이다. 고른 자·닿은 값은 진한 색, 고르지 않은 것은 옅은 색이다.

사용: python3 figures099.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
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


# ── p7 같은 목표, 세 가지 자 ──────────────────────────────────────────
def fig_rulers():
    """하나의 목표에 세 가지 자를 대고 각 자가 부르는 행동을 적는다."""
    b = []
    b.append(f'<rect x="{W / 2 - 130}" y="250" width="260" height="56" rx="26" fill="#eef1f2" '
             f'stroke="{INK}" stroke-width="1.8"/>')
    b.append(t(W / 2, 285, "책을 더 많이 읽자", 18, INK, "700"))
    rows = [("권수", 5, "짧은 책을 고른다", False),
            ("쪽수", 13, "두꺼운 책을 고른다", False),
            ("읽은 날 수", 8, "매일 조금씩 읽는다", True)]
    x0, rw, y0, gap = 232, 330, 400, 108
    spine = x0 - 84
    b.append(f'<path d="M{W / 2},306 L{W / 2},350 L{spine},350 L{spine},{y0 + 2 * gap + 17}" '
             f'fill="none" stroke="{LINE}" stroke-width="1.4"/>')
    for i, (name, ticks, effect, pick) in enumerate(rows):
        y = y0 + i * gap
        b.append(f'<line x1="{spine}" y1="{y + 17}" x2="{x0 - 6}" y2="{y + 17}" stroke="{LINE}" '
                 f'stroke-width="1.4" marker-end="url(#gray)"/>')
        b.append(f'<rect x="{x0}" y="{y}" width="{rw}" height="34" rx="4" fill="#ffffff" '
                 f'stroke="{KEEP if pick else LINE}" stroke-width="{2.6 if pick else 1.4}"/>')
        for k in range(1, ticks):
            tx = x0 + rw * k / ticks
            b.append(f'<line x1="{tx:.1f}" y1="{y}" x2="{tx:.1f}" y2="{y + (14 if k % 2 else 20)}" '
                     f'stroke="{LINE}" stroke-width="1"/>')
        b.append(t(x0 - 20, y - 4, name, 15, INK, "600", anchor="end"))
        b.append(t(x0 + rw + 14, y + 23, effect, 14, KEEP if pick else MUTED, anchor="start"))
        if pick:
            b.append(t(x0 + rw + 14, y + 44, "이든이 바란 것", 13, MARK, "700", anchor="start"))
    b.append(note_box(147, 740, 500, "자를 고르는 일이 무엇을 늘릴지 정하는 일이다"))
    b.append(caption(W / 2, 862, [
        "같은 목표라도 어느 자를 대느냐에 따라 하게 되는 행동이 달라진다.",
    ], 16))
    return base("같은 목표, 세 가지 자", "하나의 목표에 댈 수 있는 자들", "".join(b))


# ── p17 선을 미리 긋는다 ──────────────────────────────────────────────
def fig_bands():
    """성공 판정 구간을 수직선 위에 셋으로 나눈다."""
    b = []
    x0, x1, y = 140, 660, 420
    span = x1 - x0

    def pos(v):
        return x0 + span * v / 30

    bands = [(0, 20, DROP, 0.5, "다시 봐야 하는 것", "스무 권 아래"),
             (20, 25, KEEP, 0.35, "절반", "스물에서 스물넷"),
             (25, 30, KEEP, 0.85, "된 것", "스물다섯 이상")]
    for lo, hi, color, op, name, rng in bands:
        b.append(f'<rect x="{pos(lo)}" y="{y - 26}" width="{pos(hi) - pos(lo)}" height="52" '
                 f'fill="{color}" opacity="{op}"/>')
        mid = (pos(lo) + pos(hi)) / 2
        b.append(t(mid, y + 56, name, 15, INK, "600"))
        b.append(t(mid, y - 40, rng, 13, MUTED))
    b.append(f'<line x1="{x0}" y1="{y + 26}" x2="{x1}" y2="{y + 26}" stroke="{INK}" '
             f'stroke-width="2"/>')
    for v in range(0, 31, 5):
        b.append(f'<line x1="{pos(v)}" y1="{y + 26}" x2="{pos(v)}" y2="{y + 36}" stroke="{INK}" '
                 f'stroke-width="1.4"/>')
        b.append(t(pos(v), y + 54 if False else y + 34 + 0, "", 12, MUTED))
        b.append(t(pos(v), y + 100, str(v), 13, MUTED))
    for v in (20, 25):
        b.append(f'<line x1="{pos(v)}" y1="{y - 34}" x2="{pos(v)}" y2="{y + 34}" stroke="{INK}" '
                 f'stroke-width="2"/>')
    b.append(f'<path d="M{pos(28)},{y - 30} L{pos(28) - 10},{y - 48} L{pos(28) + 10},{y - 48} z" '
             f'fill="{MARK}"/>')
    b.append(f'<line x1="{pos(28)}" y1="{y - 48}" x2="{pos(28)}" y2="{y - 76}" stroke="{MARK}" '
             f'stroke-width="1.6"/>')
    b.append(t(pos(28), y - 86, "이번 달 결과", 14, MARK, "700"))
    b.append(t(x1 + 16, y + 6, "목표 서른", 14, MUTED, anchor="start"))
    b.append(note_box(147, 640, 500, "선을 끝난 뒤에 그으면 그을 이유가 없다"))
    b.append(caption(W / 2, 762, [
        "두 칸으로 나누면 하나만 모자라도 실패가 된다.",
        "세 칸으로 나누면 아슬아슬한 결과를 다룰 수 있다.",
    ], 16))
    return base("선을 미리 긋는다", "성공을 세 칸으로 나눈 기준", "".join(b))


# ── p29 얼마나 자주 볼 것인가 ─────────────────────────────────────────
def fig_intervals():
    """같은 기간을 세 가지 확인 간격으로 본 결과."""
    b = []
    x0, x1 = 220, 640
    rows = [
        (310, "매일 보기", "오르내림만 보인다", 30, False),
        (470, "주에 한 번", "흐름이 보인다", 4, True),
        (630, "끝나고 한 번", "고칠 시간이 없다", 1, False),
    ]
    daily = [2, 3, 1, 3, 2, 4, 1, 2, 3, 3, 1, 4, 2, 2, 3, 4, 1, 3, 2, 4, 3, 2, 4, 3, 1, 4, 3, 4, 2, 4]
    for y, name, effect, n, pick in rows:
        b.append(f'<line x1="{x0 - 20}" y1="{y + 40}" x2="{x1 + 20}" y2="{y + 40}" stroke="{LINE}" '
                 f'stroke-width="1.2"/>')
        b.append(t(x0 - 34, y + 20, name, 15, INK, "600", anchor="end"))
        b.append(t(x1 + 34, y + 20, effect, 14, KEEP if pick else MUTED, anchor="start"))
        if n == 30:
            for k, v in enumerate(daily):
                px = x0 + (x1 - x0) * k / 29
                py = y + 40 - v * 11
                b.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="3.2" fill="{DROP}"/>')
        elif n == 4:
            vals = [2.2, 2.6, 2.9, 3.4]
            pts = []
            for k, v in enumerate(vals):
                px = x0 + (x1 - x0) * (k + 0.5) / 4
                py = y + 40 - v * 11
                pts.append((px, py))
                b.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="5" fill="{KEEP}"/>')
            b.append('<polyline points="' + " ".join(f"{a:.1f},{c:.1f}" for a, c in pts)
                     + f'" fill="none" stroke="{KEEP}" stroke-width="2.2"/>')
        else:
            b.append(f'<circle cx="{x1}" cy="{y + 40 - 3.2 * 11:.1f}" r="5" fill="{DROP}"/>')
        if pick:
            b.append(f'<rect x="{x0 - 26}" y="{y - 16}" width="{x1 - x0 + 52}" height="76" rx="8" '
                     f'fill="none" stroke="{MARK}" stroke-width="2.2"/>')
    b.append(t(x0 - 20, 268, "한 달", 14, MUTED, anchor="start"))
    b.append(note_box(147, 760, 500, "너무 자주 보면 흔들림에 끌려간다"))
    b.append(caption(W / 2, 882, [
        "같은 값도 보는 간격에 따라 다르게 읽힌다.",
    ], 16))
    return base("얼마나 자주 볼 것인가", "확인 간격에 따라 달라 보이는 숫자", "".join(b))


# ── p39 여덟 주 ───────────────────────────────────────────────────────
def fig_weeks_line():
    """팔 주간 주별 값과 목표선을 함께 그린다."""
    b = []
    x0, x1, base_y, top_y = 190, 650, 590, 300
    vals = [2, 3, 1, 3, 4, 2, 4, 4]
    target = 3

    def px(i):
        return x0 + (x1 - x0) * i / (len(vals) - 1)

    def py(v):
        return base_y - (base_y - top_y) * v / 5

    b.append(f'<line x1="{x0 - 30}" y1="{base_y}" x2="{x1 + 30}" y2="{base_y}" stroke="{INK}" '
             f'stroke-width="1.8"/>')
    b.append(f'<line x1="{x0 - 30}" y1="{base_y}" x2="{x0 - 30}" y2="{top_y - 10}" stroke="{INK}" '
             f'stroke-width="1.8"/>')
    for v in range(0, 6):
        b.append(t(x0 - 42, py(v) + 5, str(v), 12, MUTED, anchor="end"))
        b.append(f'<line x1="{x0 - 34}" y1="{py(v)}" x2="{x0 - 30}" y2="{py(v)}" stroke="{INK}"/>')
    b.append(f'<text x="{x0 - 72}" y="{(base_y + top_y) / 2}" text-anchor="middle" font-size="14" '
             f'fill="{MUTED}" transform="rotate(-90 {x0 - 72} {(base_y + top_y) / 2})">'
             f'한 주에 읽은 날 수</text>')
    b.append(f'<line x1="{x0 - 20}" y1="{py(target)}" x2="{x1 + 20}" y2="{py(target)}" '
             f'stroke="{DROP}" stroke-width="1.6" stroke-dasharray="6 5"/>')
    b.append(t(x1 + 26, py(target) + 5, "주 세 날", 13, MUTED, anchor="start"))
    pts = [(px(i), py(v)) for i, v in enumerate(vals)]
    b.append('<polyline points="' + " ".join(f"{a:.1f},{c:.1f}" for a, c in pts)
             + f'" fill="none" stroke="{KEEP}" stroke-width="2.4"/>')
    for i, (a, c) in enumerate(pts):
        strong = vals[i] >= target
        b.append(f'<circle cx="{a:.1f}" cy="{c:.1f}" r="6" fill="{KEEP if strong else "#ffffff"}" '
                 f'stroke="{KEEP if strong else DROP}" stroke-width="2"/>')
        b.append(t(a, base_y + 24, str(i + 1), 13, MUTED))
    b.append(t(px(2), py(1) + 30, "책방 행사", 12, MUTED))
    mx = (px(4) + px(3)) / 2
    b.append(f'<line x1="{mx:.1f}" y1="{top_y - 6}" x2="{mx:.1f}" y2="{base_y}" stroke="{MARK}" '
             f'stroke-width="1.4" stroke-dasharray="5 5"/>')
    b.append(t(mx, top_y - 16, "읽는 시각을 옮김", 13, MARK, "700"))
    b.append(t(W / 2, base_y + 48, "주", 13, MUTED))
    b.append(note_box(147, 700, 500, "한 주가 낮다고 계획을 바꾸지 않는다"))
    b.append(caption(W / 2, 822, [
        "셋째 주가 낮은 것은 그 주의 사정이고, 세 번 이어졌을 때만 흐름으로 본다.",
    ], 16))
    return base("여덟 주", "주별 값과 목표선", "".join(b))


FIGURES = {7: fig_rulers, 17: fig_bands, 29: fig_intervals, 39: fig_weeks_line}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild099")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
