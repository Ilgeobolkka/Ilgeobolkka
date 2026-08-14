#!/usr/bin/env python3
"""book-070 이미지 페이지 6개의 SVG 생성. figures062.py의 t()/base() 패턴을 따른다.

과정을 다루는 책이라 여섯 도표가 모두 '무엇에서 무엇으로 가는가'를 그린다. 진행은 화살표, 선택된
갈래는 굵은 선, 버려진 갈래는 옅은 선으로 고정해 여섯 도표에서 같은 뜻으로 쓴다.

사용: python3 figures070.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 남는 갈래
DROP = "#c3ccd0"          # 버려지는 갈래
MARK = "#b4703a"          # 표시·강조


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


def box(x, y, w, h, label, fill="#ffffff", stroke=INK, size=15):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="{fill}" stroke="{stroke}" stroke-width="1.8"/>'
            + t(x + w / 2, y + h / 2 + 5, label, size, INK))


# ── p8 조건이 다르면 다른 것이 나온다 ────────────────────────────────
def fig_tools():
    b = []
    xs = [148, 320, 492]
    w, h, y = 154, 150, 330
    b.append(t(W / 2, 282, "세 화면은 같은 주제에서 출발했다", 15, MUTED))
    for i, x in enumerate(xs):
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#ffffff" stroke="{INK}" stroke-width="2"/>')
        cx, cy = x + w / 2, y + h / 2
        if i == 0:
            b.append(f'<line x1="{cx - 42}" y1="{cy + 26}" x2="{cx + 30}" y2="{cy - 30}" '
                     f'stroke="{KEEP}" stroke-width="16" stroke-linecap="round"/>')
            label = "큰 붓 하나"
        elif i == 1:
            for k in range(-4, 5):
                b.append(f'<line x1="{cx - 40 + k * 4}" y1="{cy + 34}" x2="{cx + 26 + k * 4}" y2="{cy - 34}" '
                         f'stroke="{KEEP}" stroke-width="1.4"/>')
            label = "가는 펜"
        else:
            for k, (dx, dy, sz) in enumerate(((-34, -18, 30), (2, -26, 26), (-16, 14, 34), (20, 8, 22))):
                b.append(f'<rect x="{cx + dx}" y="{cy + dy}" width="{sz}" height="{sz}" fill="{KEEP}" opacity="0.85"/>')
            label = "자른 종이"
        b.append(t(cx, y + h + 32, label, 16, INK, "600"))
    b.append(f'<line x1="{xs[0] + w / 2}" y1="{y + h + 56}" x2="{xs[2] + w / 2}" y2="{y + h + 56}" stroke="{LINE}"/>')
    b.append(t(W / 2, y + h + 82, "같은 주제", 15, MUTED))
    b.append(note_box(147, 660, 500, "도구가 먼저 답한다"))
    b.append(caption(W / 2, 782, [
        "무엇으로 시작하느냐가 만들 수 있는 것의 범위를 먼저 나눈다.",
        "그래서 도구를 고르는 일이 이미 첫 번째 결정이다.",
    ], 16))
    return base("조건이 다르면 다른 것이 나온다", "같은 주제를 세 가지 도구로 시작한 결과", "".join(b))


# ── p14 하나에서 갈라진 여섯 ──────────────────────────────────────────
def fig_branches():
    b = []
    ox, oy = W / 2, 290
    b.append(f'<circle cx="{ox}" cy="{oy}" r="16" fill="{INK}"/>')
    b.append(t(ox, oy - 28, "출발점", 15, MUTED))
    ends = [(140, 470, "drop"), (250, 500, "keep"), (360, 470, "keep"),
            (470, 500, "pick"), (580, 470, "keep"), (668, 500, "drop")]
    for ex, ey, kind in ends:
        color = DROP if kind == "drop" else (MARK if kind == "pick" else KEEP)
        width = 3.4 if kind == "pick" else (1.4 if kind == "drop" else 2.2)
        b.append(f'<line x1="{ox}" y1="{oy + 16}" x2="{ex}" y2="{ey - 24}" stroke="{color}" stroke-width="{width}"/>')
        op = 0.45 if kind == "drop" else 1
        b.append(f'<rect x="{ex - 26}" y="{ey - 22}" width="52" height="44" rx="8" fill="{color}" opacity="{op}"/>')
    px, py = 470, 500
    b.append(f'<line x1="{px}" y1="{py + 26}" x2="{px}" y2="{py + 78}" stroke="{MARK}" stroke-width="2.6" '
             f'marker-end="url(#mark)"/>')
    b.append(t(px, py + 104, "다음 단계", 16, MARK, "700"))
    b.append(t(W / 2, 252, "여섯은 같은 출발점에서 갈라졌다", 15, MUTED))
    b.append(note_box(147, 680, 500, "대부분은 버려지고 하나가 남는다"))
    b.append(caption(W / 2, 802, [
        "초안을 여럿 만드는 것은 고르기 위해서다.",
        "고를 것이 하나뿐이면 그것이 맞는지 알 방법이 없다.",
    ], 16))
    return base("하나에서 갈라진 여섯", "한 출발점에서 갈라진 초안과 그중 남은 하나", "".join(b))


# ── p23 좁혀야 보인다 ─────────────────────────────────────────────────
def fig_narrowing():
    b = []
    size, y = 226, 300
    lefts = [148, 420]
    import math
    pts = []
    for r in range(9):
        for c in range(9):
            pts.append((22 + c * 23, 22 + r * 23))
    for idx, (x, label) in enumerate(((lefts[0], "제약 없음"), (lefts[1], "제약 둘"))):
        b.append(f'<rect x="{x}" y="{y}" width="{size}" height="{size}" fill="#ffffff" stroke="{INK}" stroke-width="2"/>')
        for px, py in pts:
            inside = 68 <= px <= 160 and 45 <= py <= 175
            if idx == 0:
                col, op = LINE, 0.75
            else:
                col, op = (KEEP, 1) if inside else (LINE, 0.16)
            b.append(f'<circle cx="{x + px}" cy="{y + py}" r="3.4" fill="{col}" opacity="{op}"/>')
        if idx == 1:
            b.append(f'<rect x="{x + 62}" y="{y}" width="6" height="{size}" fill="{MARK}" opacity="0.55"/>')
            b.append(f'<rect x="{x + 160}" y="{y}" width="6" height="{size}" fill="{MARK}" opacity="0.55"/>')
            path = f"M{x + 76},{y + 160} L{x + 100},{y + 114} L{x + 122},{y + 68} L{x + 150},{y + 50}"
            b.append(f'<path d="{path}" fill="none" stroke="{MARK}" stroke-width="2.4" marker-end="url(#mark)"/>')
        b.append(t(x + size / 2, y + size + 34, label, 17, INK, "600"))
    b.append(t(W / 2, 262, "두 화면의 점은 같은 수만큼 있다", 15, MUTED))
    b.append(note_box(147, 640, 500, "고를 수 있는 것이 줄면 고를 수 있게 된다"))
    b.append(caption(W / 2, 762, [
        "선택지가 가득하면 어디서 시작할지 정해지지 않는다.",
        "두 개의 조건이 좁힌 구역 안에서는 경로가 보인다.",
    ], 16))
    return base("좁혀야 보인다", "제약을 걸기 전과 뒤의 선택 범위", "".join(b))


# ── p30 무엇이 달라지게 했는가 ────────────────────────────────────────
def fig_one_change():
    b = []

    def shape(x, y, marks):
        g = [f'<rect x="{x}" y="{y}" width="96" height="72" rx="8" fill="#ffffff" stroke="{INK}" stroke-width="1.8"/>']
        spots = [(22, 20), (56, 20), (38, 48)]
        for i, (dx, dy) in enumerate(spots):
            fill = KEEP if i in marks else DROP
            g.append(f'<circle cx="{x + dx}" cy="{y + dy}" r="10" fill="{fill}"/>')
            if i in marks:
                g.append(f'<circle cx="{x + dx}" cy="{y + dy}" r="16" fill="none" stroke="{MARK}" stroke-width="2"/>')
        return "".join(g)

    y1 = 300
    b.append(shape(130, y1, set()))
    b.append(f'<line x1="240" y1="{y1 + 36}" x2="300" y2="{y1 + 36}" stroke="{LINE}" stroke-width="1.8" marker-end="url(#gray)"/>')
    b.append(shape(312, y1, {0, 1, 2}))
    b.append(t(455, y1 + 42, "?", 34, MARK, "700"))
    b.append(t(660, y1 + 42, "한꺼번에", 16, INK, "600", anchor="end"))

    y2 = 460
    xs = [130, 312, 494]
    b.append(shape(xs[0] - 0, y2, set()))
    marks = [{0}, {0, 1}, {0, 1, 2}]
    for i, x in enumerate(xs):
        if i < len(xs) - 0 and i < 3:
            pass
    prev = xs[0]
    for i in range(3):
        nx = 130 + (i + 1) * 182
        if nx + 96 > 700:
            nx = 130 + (i + 1) * 178
        b.append(f'<line x1="{prev + 108}" y1="{y2 + 36}" x2="{prev + 168}" y2="{y2 + 36}" stroke="{LINE}" '
                 f'stroke-width="1.8" marker-end="url(#gray)"/>')
        b.append(f'<line x1="{prev + 116}" y1="{y2 + 54}" x2="{prev + 160}" y2="{y2 + 54}" stroke="{MARK}" stroke-width="1.2"/>')
        prev = prev + 178
        b.append(shape(prev, y2, marks[i]))
    b.append(t(660, y2 + 120, "하나씩", 16, INK, "600", anchor="end"))
    b.append(note_box(147, 660, 500, "하나씩 바꿔야 원인을 안다"))
    b.append(caption(W / 2, 782, [
        "여러 곳을 한꺼번에 고치면 나아져도 무엇 때문인지 알 수 없다.",
        "하나씩 바꾸면 느려 보이지만 다음 수정이 짧아진다.",
    ], 16))
    return base("무엇이 달라지게 했는가", "한꺼번에 고친 경우와 하나씩 고친 경우", "".join(b))


# ── p38 막혔을 때 여는 순서 ───────────────────────────────────────────
def fig_stuck():
    b = []
    cx = 300
    top = 270
    steps = ["더 작게 끊기", "제약 바꾸기", "다른 매체로 옮기기", "두고 나오기"]
    b.append(box(cx - 90, top, 180, 44, "막힘", "#f2f4f5"))
    b.append(f'<line x1="{cx}" y1="{top + 44}" x2="{cx}" y2="{top + 76}" stroke="{INK}" stroke-width="2.6"/>')
    y = top + 76
    for i, label in enumerate(steps):
        b.append(box(cx - 110, y, 220, 48, label))
        b.append(f'<line x1="{cx + 110}" y1="{y + 24}" x2="{cx + 176}" y2="{y + 24}" stroke="{KEEP}" '
                 f'stroke-width="1.8" marker-end="url(#keep)"/>')
        if i < len(steps) - 1:
            b.append(f'<line x1="{cx}" y1="{y + 48}" x2="{cx}" y2="{y + 84}" stroke="{INK}" stroke-width="2.6"/>')
        b.append(f'<path d="M{cx - 150},{y + 8} l-16,{16} l16,{16}" fill="none" stroke="{DROP}" stroke-width="2"/>')
        y += 84
    b.append(box(cx + 180, top + 76 + 126, 150, 52, "풀리면 멈춤", "#f7f4ec", "#c5a866"))
    b.append(t(cx - 190, top + 60, "작은 조치", 14, MUTED, anchor="end"))
    b.append(t(cx - 190, y - 40, "큰 조치", 14, MUTED, anchor="end"))
    b.append(note_box(147, 700, 500, "작은 것부터 시도한다"))
    b.append(caption(W / 2, 822, [
        "가장 잘 통하는 두고 나오기를 마지막에 두는 이유는",
        "먼저 시도할 것을 다 해 보지 않으면 미루는 일과 구분되지 않기 때문이다.",
    ], 16))
    return base("막혔을 때 여는 순서", "교착에서 시도할 조치를 비용이 작은 순으로 늘어놓은 그림", "".join(b))


# ── p46 의욕은 오르내리고 작업은 이어진다 ────────────────────────────
def fig_motivation():
    b = []
    x0, x1 = 140, 660
    ytop, ybot = 300, 520
    b.append(f'<line x1="{x0}" y1="{ybot}" x2="{x1 + 16}" y2="{ybot}" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(f'<line x1="{x0}" y1="{ybot}" x2="{x0}" y2="{ytop - 20}" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(t(x0 - 12, ytop - 4, "크기", 15, MUTED, anchor="end"))
    b.append(t(W / 2, ybot + 40, "날짜", 15, MUTED))
    vals = [0.9, 0.4, 0.75, 0.05, 0.6, 0.85, 0.3, 0.02, 0.55, 0.95, 0.45, 0.7]
    n = len(vals)
    pts = []
    for i, v in enumerate(vals):
        x = x0 + (x1 - x0) * i / (n - 1)
        pts.append((x, ybot - (ybot - ytop) * v, v))
    b.append('<polyline points="' + " ".join(f"{x:.1f},{y:.1f}" for x, y, _ in pts)
             + f'" fill="none" stroke="{MARK}" stroke-width="2.6"/>')
    b.append(t(x1 + 12, pts[-1][1] - 14, "의욕", 15, MARK, "700", anchor="end"))
    wy = ybot - (ybot - ytop) * 0.22
    work = " ".join(f"{x0 + (x1 - x0) * i / (n - 1):.1f},{wy + (3 if i % 2 else -3):.1f}" for i in range(n))
    b.append(f'<polyline points="{work}" fill="none" stroke="{KEEP}" stroke-width="3"/>')
    b.append(t(x1 + 12, wy + 30, "실제 작업량", 15, KEEP, "700", anchor="end"))
    for x, y, v in pts:
        if v < 0.1:
            b.append(f'<line x1="{x}" y1="{y + 34}" x2="{x}" y2="{y + 8}" stroke="{KEEP}" stroke-width="2" '
                     f'marker-end="url(#keep)"/>')
            b.append(t(x, y + 58, "절차대로 시작", 13, KEEP))
    b.append(note_box(147, 640, 500, "의욕은 조건이 아니다"))
    b.append(caption(W / 2, 762, [
        "의욕은 크게 오르내리지만 작업량은 낮은 자리에서 이어진다.",
        "바닥에 닿은 날에도 절차가 시작을 대신한다.",
    ], 16))
    return base("의욕은 오르내리고 작업은 이어진다", "같은 기간의 의욕과 실제 작업량", "".join(b))


FIGURES = {8: fig_tools, 14: fig_branches, 23: fig_narrowing,
           30: fig_one_change, 38: fig_stuck, 46: fig_motivation}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild070")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
