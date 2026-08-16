#!/usr/bin/env python3
"""book-056 이미지 페이지 4개의 SVG 생성. figures051.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '재료가 어떻게 놓이고 어떻게 다투는가'를 그린다. 재료가 채워진 자리는 진한 채움,
줄거나 비는 자리는 옅은 색, 서로 밀어내는 관계는 반대 방향 화살표로 고정해 같은 뜻으로 쓴다.

사용: python3 figures056.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 채워진 자리
DROP = "#c3ccd0"          # 비거나 줄어드는 자리
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
  <marker id="line" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
</defs>
{t(W / 2, 122, title, 29, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


def person(x, y, s, color):
    return (f'<circle cx="{x}" cy="{y - s}" r="{s * 0.62:.1f}" fill="none" stroke="{color}" stroke-width="1.8"/>'
            f'<path d="M{x - s},{y + s * 0.7:.1f} q{s},-{s * 1.1:.1f} {s * 2},0" fill="none" '
            f'stroke="{color}" stroke-width="1.8"/>')


# ── p6 한 물음처럼 보이지만 셋이다 ───────────────────────────────────
def fig_three_layers():
    b = []
    bx, bw, bh = 246, 250, 78
    rows = [(270, "무엇이 재료인가"), (376, "얼마씩 둘 것인가"), (482, "누가 정하는가")]
    for y, label in rows:
        b.append(f'<rect x="{bx}" y="{y}" width="{bw}" height="{bh}" rx="10" fill="#eef2f1" '
                 f'stroke="{KEEP}" stroke-width="1.8"/>')
        b.append(t(bx + bw / 2, y + bh / 2 + 6, label, 17, INK, "700"))
    b.append(f'<line x1="{bx - 26}" y1="270" x2="{bx - 26}" y2="{482 + bh}" stroke="{LINE}" stroke-width="2"/>')
    b.append(f'<text x="{bx - 44}" y="400" text-anchor="middle" font-size="15" fill="{MUTED}" '
             f'transform="rotate(-90 {bx - 44} 400)">좋은 삶은 무엇인가</text>')

    rx = bx + bw + 26
    for i, label in enumerate(("즐거움", "성취", "관계")):
        b.append(f'<rect x="{rx + i * 62}" y="{270 + 20}" width="56" height="38" rx="6" '
                 f'fill="#ffffff" stroke="{LINE}" stroke-width="1.6"/>')
        b.append(t(rx + i * 62 + 28, 270 + 45, label, 13, INK))

    cx, cy, r = rx + 68, 376 + 34, 32
    import math
    start = -90
    for frac, col in ((0.5, KEEP), (0.3, LINE), (0.2, DROP)):
        end = start + frac * 360
        x1 = cx + r * math.cos(math.radians(start))
        y1 = cy + r * math.sin(math.radians(start))
        x2 = cx + r * math.cos(math.radians(end))
        y2 = cy + r * math.sin(math.radians(end))
        large = 1 if frac > 0.5 else 0
        b.append(f'<path d="M{cx},{cy} L{x1:.1f},{y1:.1f} A{r},{r} 0 {large},1 {x2:.1f},{y2:.1f} Z" '
                 f'fill="{col}" opacity="0.8"/>')
        start = end
    b.append(t(cx + 76, cy + 5, "사람마다 다르다", 13, MUTED))

    py = 482 + 40
    b.append(person(rx + 22, py, 15, INK))
    for k in range(3):
        b.append(person(rx + 128 + k * 26, py, 12, LINE))
    b.append(f'<line x1="{rx + 48}" y1="{py - 8}" x2="{rx + 106}" y2="{py - 8}" stroke="{MARK}" '
             f'stroke-width="1.8" marker-start="url(#mark)" marker-end="url(#mark)"/>')
    b.append(t(rx + 76, py + 34, "안과 밖이 함께 정한다", 13, MARK, "700"))

    b.append(note_box(147, 640, 500, "어느 층을 묻는지 정하지 않으면 답이 엇갈린다", 16))
    b.append(caption(W / 2, 762, [
        "하나의 물음처럼 보이지만 세 층이 겹쳐 있다.",
        "층마다 답의 모양이 다르고 답할 수 있는 사람도 다르다.",
    ], 16))
    return base("한 물음처럼 보이지만 셋이다", "좋은 삶에 대한 물음이 갈라지는 세 층", "".join(b))


# ── p18 이룬 뒤에 남는 것이 다르다 ───────────────────────────────────
def fig_after_goal():
    b = []
    ox, oy, ow, oh = 150, 560, 500, 300
    b.append(f'<line x1="{ox}" y1="{oy}" x2="{ox + ow}" y2="{oy}" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<line x1="{ox}" y1="{oy}" x2="{ox}" y2="{oy - oh}" stroke="{INK}" stroke-width="2"/>')
    b.append(t(ox + ow, oy + 26, "시간", 15, MUTED, anchor="end"))
    b.append(f'<text x="{ox - 34}" y="{oy - oh / 2}" text-anchor="middle" font-size="15" '
             f'fill="{MUTED}" transform="rotate(-90 {ox - 34} {oy - oh / 2})">느끼는 값</text>')
    gx = ox + 250
    b.append(f'<line x1="{gx}" y1="{oy}" x2="{gx}" y2="{oy - oh + 16}" stroke="{LINE}" '
             f'stroke-width="1.4" stroke-dasharray="5 5"/>')
    b.append(t(gx, oy + 24, "이룬 날", 14, LINE))

    base_y = oy - 40
    goal = (f"M{ox + 12},{base_y} C{ox + 120},{base_y - 12} {gx - 60},{base_y - 24} {gx},{oy - 250} "
            f"C{gx + 40},{base_y - 40} {gx + 90},{base_y - 4} {ox + ow - 12},{base_y}")
    b.append(f'<path d="{goal}" fill="none" stroke="{DROP}" stroke-width="3.2"/>')
    b.append(t(ox + ow - 30, base_y + 26, "목표에 걸었을 때", 14, LINE, anchor="end"))

    pts = []
    for i in range(0, 26):
        x = ox + 12 + i * (ow - 24) / 25
        lvl = base_y - 20 - i * 3.4 + (6 if i % 2 else -6)
        if x > gx:
            lvl -= 26
        pts.append(f"{x:.0f},{lvl:.0f}")
    b.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{KEEP}" stroke-width="3.2"/>')
    b.append(t(ox + ow - 30, oy - 250 + 8, "하는 동안에 걸었을 때", 14, KEEP, "700", anchor="end"))

    ex = ox + ow - 12
    b.append(f'<line x1="{ex + 14}" y1="{base_y}" x2="{ex + 14}" y2="{base_y - 128}" stroke="{MARK}" '
             f'stroke-width="1.8" marker-start="url(#mark)" marker-end="url(#mark)"/>')
    b.append(t(ex + 24, base_y - 60, "남는 것", 13, MARK, "700", anchor="start"))

    b.append(note_box(147, 690, 500, "끝나는 것에 걸면 비고 이어지는 것에 걸면 남는다", 16))
    b.append(caption(W / 2, 812, [
        "목표에 건 값은 이룬 날에 솟았다가 곧 제자리로 돌아온다.",
        "하는 동안에 건 값은 천천히 오르고 이룬 뒤에도 높은 자리에서 이어진다.",
    ], 16))
    return base("이룬 뒤에 남는 것이 다르다", "목표를 이룬 뒤 값이 움직이는 두 모양", "".join(b))


# ── p27 겹치는 자리와 밀어내는 자리 ──────────────────────────────────
def fig_overlap3():
    b = []
    r = 122
    cx, cy = 400, 430
    centres = [(cx, cy - 62, "즐거움"), (cx - 92, cy + 66, "성취"), (cx + 92, cy + 66, "관계")]
    b.append('<defs>'
             + "".join(f'<clipPath id="c{i}"><circle cx="{x}" cy="{y}" r="{r}"/></clipPath>'
                       for i, (x, y, _) in enumerate(centres))
             + '</defs>')
    for x, y, _ in centres:
        b.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#eef2f1" stroke="{LINE}" stroke-width="1.8"/>')
    pairs = [(0, 1, "익히는 즐거움", cx - 62, cy + 10), (0, 2, "함께 즐김", cx + 62, cy + 10),
             (1, 2, "함께 이룸", cx, cy + 96)]
    for i, j, label, lx, ly in pairs:
        b.append(f'<g clip-path="url(#c{i})"><circle cx="{centres[j][0]}" cy="{centres[j][1]}" '
                 f'r="{r}" fill="{KEEP}" opacity="0.22"/></g>')
        b.append(t(lx, ly, label, 13, "#2c4f48", "700"))
    b.append(f'<g clip-path="url(#c0)"><g clip-path="url(#c1)"><circle cx="{centres[2][0]}" '
             f'cy="{centres[2][1]}" r="{r}" fill="{KEEP}" opacity="0.42"/></g></g>')
    b.append(t(cx, cy + 44, "함께 익히고", 13, "#1f3b36", "700"))
    b.append(t(cx, cy + 62, "나누는 일", 13, "#1f3b36", "700"))
    for x, y, label in centres:
        oy = y - r - 14 if label == "즐거움" else y + r + 26
        b.append(t(x, oy, label, 17, INK, "700"))

    for (ax, ay, bx2, by2, label) in ((150, 330, 210, 300, "시간"), (150, 560, 210, 590, "주의"),
                                      (644, 330, 584, 300, "시점")):
        b.append(f'<line x1="{ax}" y1="{ay}" x2="{bx2}" y2="{by2}" stroke="{MARK}" stroke-width="1.8" '
                 f'marker-start="url(#mark)" marker-end="url(#mark)"/>')
        b.append(t(ax, ay + 24, label, 13, MARK, "700"))

    b.append(note_box(147, 690, 500, "겹치는 자리는 그렇게 되도록 잡아야 생긴다"))
    b.append(caption(W / 2, 812, [
        "세 재료는 겹치는 자리를 가지면서 같은 자원을 두고 다툰다.",
        "겹치는 자리는 두 재료를 다 얻는 자리가 아니라 덜 잃는 자리다.",
    ], 16))
    return base("겹치는 자리와 밀어내는 자리", "세 재료가 겹치고 밀어내는 모습", "".join(b))


# ── p34 자를 바꾸면 순서가 바뀐다 ────────────────────────────────────
def fig_scales():
    b = []
    panels = [(150, "만족한다고 답한 정도", (70, 150, 110)),
              (452, "할 수 있는 일의 수", (150, 62, 110))]
    names = ["가", "나", "다"]
    colors = [MARK, KEEP, LINE]
    for px, title, heights in panels:
        base_y = 540
        b.append(f'<line x1="{px}" y1="{base_y}" x2="{px + 196}" y2="{base_y}" stroke="{INK}" stroke-width="2"/>')
        b.append(t(px + 98, 268, title, 16, INK, "700"))
        for i, h in enumerate(heights):
            x = px + 22 + i * 60
            b.append(f'<rect x="{x}" y="{base_y - h}" width="42" height="{h}" rx="4" '
                     f'fill="{colors[i]}" opacity="0.55" stroke="{colors[i]}" stroke-width="1.6"/>')
            b.append(t(x + 21, base_y + 24, names[i], 15, INK, "700"))
    b.append(f'<line x1="400" y1="262" x2="400" y2="566" stroke="{LINE}" stroke-width="1.4" '
             f'stroke-dasharray="5 5"/>')
    b.append(f'<rect x="336" y="574" width="128" height="30" rx="15" fill="#ffffff" stroke="{LINE}"/>')
    b.append(t(400, 594, "같은 세 사람", 14, MUTED))
    b.append(f'<line x1="200" y1="626" x2="600" y2="626" stroke="{MARK}" stroke-width="1.8" '
             f'marker-start="url(#mark)" marker-end="url(#mark)"/>')
    b.append(t(400, 650, "둘 다 정확하다", 15, MARK, "700"))

    b.append(note_box(147, 686, 500, "무엇을 재려는지를 먼저 정한다"))
    b.append(caption(W / 2, 808, [
        "같은 세 사람을 다른 자로 재면 순위가 뒤바뀐다.",
        "어느 자가 옳은지가 아니라 무엇을 재려는지가 먼저다.",
    ], 16))
    return base("자를 바꾸면 순서가 바뀐다", "어떤 자로 재느냐에 따라 달라지는 순위", "".join(b))


FIGURES = {6: fig_three_layers, 18: fig_after_goal, 27: fig_overlap3, 34: fig_scales}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild056")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
