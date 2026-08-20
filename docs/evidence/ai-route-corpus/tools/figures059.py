#!/usr/bin/env python3
"""book-059 이미지 페이지 4개의 SVG 생성. figures051.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '아는 자리와 모르는 자리의 경계'를 그린다. 확인된 자리는 진한 채움, 확인되지 않은
자리는 빈칸과 물음표, 남의 것에 기대는 자리는 점선으로 고정해 같은 뜻으로 쓴다.

사용: python3 figures059.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 확인된 자리
DROP = "#c3ccd0"          # 확인되지 않은 자리
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
{t(W / 2, 122, title, 30, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


# ── p4 아는 것과 모르는 것 사이는 넓다 ──────────────────────────────
def fig_layers():
    b = []
    x0, y0, cw, ch = 118, 300, 111, 96
    labels = ["모른다는 것도\n모른다", "모른다는 것은\n안다", "들어 본 적\n있다",
              "설명할 수\n있다", "해 볼 수\n있다"]
    for i, label in enumerate(labels):
        x = x0 + i * cw
        op = 0.12 + i * 0.19
        b.append(f'<rect x="{x}" y="{y0}" width="{cw}" height="{ch}" fill="{KEEP}" opacity="{op:.2f}" '
                 f'stroke="{LINE}" stroke-width="1.2"/>')
        for k, line in enumerate(label.split("\n")):
            b.append(t(x + cw / 2, y0 + 42 + k * 20, line, 13, INK, "600"))
    b.append(t(x0 - 8, y0 - 18, "모른다", 16, INK, "700", anchor="start"))
    b.append(t(x0 + 5 * cw + 8, y0 - 18, "안다", 16, INK, "700", anchor="end"))

    for i, note in ((0, "물음이 생기지 않는다"), (1, "배움이 시작된다")):
        cx = x0 + i * cw + cw / 2
        b.append(f'<line x1="{cx}" y1="{y0 + ch + 6}" x2="{cx}" y2="{y0 + ch + 34}" '
                 f'stroke="{MARK if i else LINE}" stroke-width="2" '
                 f'marker-end="url(#{"mark" if i else "line"})"/>')
        b.append(t(cx, y0 + ch + 58 + i * 22, note, 13, MARK if i else LINE, "700"))

    dx = x0 + 4 * cw
    b.append(f'<line x1="{dx}" y1="{y0 - 44}" x2="{dx}" y2="{y0 - 6}" stroke="{LINE}" '
             f'stroke-width="1.4" stroke-dasharray="5 4"/>')
    b.append(t(dx, y0 - 54, "여기서 자주 착각한다", 13, LINE))

    b.append(note_box(147, 560, 500, "안다와 모른다 사이에 여러 자리가 있다"))
    b.append(caption(W / 2, 682, [
        "아는 것과 모르는 것은 둘로 갈리지 않고 여러 층으로 놓인다.",
        "설명할 수 있는 것과 해 볼 수 있는 것 사이에서 자주 착각한다.",
    ], 16))
    return base("아는 것과 모르는 것 사이는 넓다", "앎과 모름 사이에 놓인 여러 층", "".join(b))


# ── p13 모른다는 것을 아는지가 갈림이다 ─────────────────────────────
def fig_quadrant():
    b = []
    x0, y0, cw, ch = 210, 268, 190, 152
    cells = [(0, 0, "모르는 것을 안다", True), (1, 0, "아는 것을 안다", False),
             (0, 1, "모르는 것을 모른다", None), (1, 1, "아는데 모르는 줄 안다", False)]
    for cx, cy, label, hi in cells:
        x, y = x0 + cx * cw, y0 + cy * ch
        fill = "#eaf0ee" if hi else ("#f1f3f4" if hi is None else "#ffffff")
        stroke = KEEP if hi else (DROP if hi is None else LINE)
        width = 3 if hi else 1.5
        b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" fill="{fill}" '
                 f'stroke="{stroke}" stroke-width="{width}"/>')
        b.append(t(x + cw / 2, y + 40, label, 14, INK, "700" if hi else "400"))
        if hi is None:
            b.append(t(x + cw / 2, y + 106, "?", 34, DROP, "700"))
    b.append(t(x0 + cw / 2, y0 + 2 * ch + 28, "모른다", 15, MUTED))
    b.append(t(x0 + cw * 1.5, y0 + 2 * ch + 28, "안다", 15, MUTED))
    b.append(f'<text x="{x0 - 20}" y="{y0 + ch / 2}" text-anchor="middle" font-size="15" '
             f'fill="{MUTED}" transform="rotate(-90 {x0 - 20} {y0 + ch / 2})">그것을 안다</text>')
    b.append(f'<text x="{x0 - 20}" y="{y0 + ch * 1.5}" text-anchor="middle" font-size="15" '
             f'fill="{MUTED}" transform="rotate(-90 {x0 - 20} {y0 + ch * 1.5})">그것을 모른다</text>')

    b.append(f'<line x1="{x0 + 46}" y1="{y0 + ch + 26}" x2="{x0 + 46}" y2="{y0 + ch - 12}" '
             f'stroke="{MARK}" stroke-width="2.6" marker-end="url(#mark)"/>')
    b.append(t(x0 + 108, y0 + ch + 46, "밖에서 온다", 13, MARK, "700"))
    b.append(f'<line x1="{x0 + cw / 2}" y1="{y0 - 8}" x2="{x0 + cw / 2}" y2="{y0 - 34}" '
             f'stroke="{KEEP}" stroke-width="2.4" marker-end="url(#keep)"/>')
    b.append(t(x0 + cw / 2, y0 - 44, "배움이 시작되는 칸", 14, KEEP, "700"))

    b.append(note_box(147, 640, 500, "가장 큰 칸이 가장 보이지 않는 칸이다"))
    b.append(caption(W / 2, 762, [
        "아는지 여부와 그것을 아는지 여부로 네 칸이 나뉜다.",
        "모르는 것을 모르는 칸은 스스로 열 수 없고 밖에서 온다.",
    ], 16))
    return base("모른다는 것을 아는지가 갈림이다", "앎과 자각으로 나눈 네 칸", "".join(b))


# ── p27 다리가 하나면 함께 무너진다 ─────────────────────────────────
def fig_legs():
    b = []
    for px, legs, tag in ((216, ["들었다"], "하나가 무너지면 함께 무너진다"),
                          (534, ["들었다", "봤다", "해 봤다"], "하나가 무너져도 선다")):
        tilt = -6 if len(legs) == 1 else 0
        b.append(f'<g transform="rotate({tilt} {px} 330)">'
                 f'<rect x="{px - 108}" y="316" width="216" height="20" rx="4" fill="{KEEP}" '
                 f'opacity="0.8"/></g>')
        b.append(t(px, 296, "안다고 여기는 것", 15, INK, "700"))
        n = len(legs)
        for i, leg in enumerate(legs):
            lx = px + (i - (n - 1) / 2) * 76
            b.append(f'<line x1="{lx}" y1="340" x2="{lx}" y2="446" stroke="{INK}" stroke-width="4"/>')
            b.append(t(lx, 472, leg, 13, MUTED))
        mid = px if n == 1 else px
        b.append(f'<line x1="{mid + 96}" y1="392" x2="{mid + 44}" y2="392" stroke="{MARK}" '
                 f'stroke-width="2.4" marker-end="url(#mark)"/>')
        b.append(f'<line x1="{px - 130}" y1="500" x2="{px + 130}" y2="500" stroke="{LINE}" stroke-width="1.4"/>')
        b.append(t(px, 526, tag, 14, INK, "700"))

    b.append(t(W / 2, 578, "근거를 세어 보면 다리 수가 나온다", 15, MUTED))
    b.append(note_box(147, 620, 500, "단단함은 확신이 아니라 다리 수에서 온다"))
    b.append(caption(W / 2, 742, [
        "같은 힘이 와도 다리가 하나면 기울고 셋이면 버틴다.",
        "다리 수는 겪음·들음·따짐·해 봄을 세어 보면 나온다.",
    ], 16))
    return base("다리가 하나면 함께 무너진다", "앎이 몇 개의 근거로 서 있는지", "".join(b))


# ── p36 셋은 시험해야 갈린다 ────────────────────────────────────────
def fig_look_alike():
    b = []
    rows = [("익숙하다", [1, 0, 0], None), ("알겠다고 느낀다", [1, 0.25, 0], None),
            ("물어볼 데가 있다", None, "남의 것"), ("안다", [1, 1, 1], None)]
    x0, bw = 300, 300
    for i, (label, fills, note) in enumerate(rows):
        y = 300 + i * 74
        b.append(t(x0 - 20, y + 26, label, 15, INK, "700", anchor="end"))
        for k in range(3):
            x = x0 + k * (bw / 3)
            if fills is None:
                b.append(f'<rect x="{x}" y="{y}" width="{bw / 3}" height="40" fill="none" '
                         f'stroke="{DROP}" stroke-width="1.6" stroke-dasharray="5 4"/>')
            else:
                op = fills[k]
                b.append(f'<rect x="{x}" y="{y}" width="{bw / 3}" height="40" '
                         f'fill="{KEEP}" opacity="{op * 0.8:.2f}" stroke="{LINE}" stroke-width="1"/>')
        if note:
            b.append(t(x0 + bw + 16, y + 26, note, 13, LINE, anchor="start"))
    for k, part in enumerate(("알아본다", "설명한다", "해 본다")):
        b.append(t(x0 + k * (bw / 3) + bw / 6, 286, part, 13, MUTED))

    ax = x0 + bw + 76
    b.append(f'<line x1="{ax}" y1="306" x2="{ax}" y2="562" stroke="{MARK}" stroke-width="2" '
             f'marker-end="url(#mark)"/>')
    b.append(f'<text x="{ax + 20}" y="434" text-anchor="middle" font-size="14" fill="{MARK}" '
             f'font-weight="700" transform="rotate(90 {ax + 20} 434)">시험하면 갈린다</text>')

    b.append(note_box(147, 626, 500, "겉에서는 넷이 같아 보인다"))
    b.append(caption(W / 2, 748, [
        "익숙함과 이해감과 빌린 앎은 쓰이는 동안 앎과 같아 보인다.",
        "설명해 보고 해 보는 자리에서만 넷이 갈린다.",
    ], 16))
    return base("셋은 시험해야 갈린다", "앎과 앎처럼 보이는 세 가지", "".join(b))


FIGURES = {4: fig_layers, 13: fig_quadrant, 27: fig_legs, 36: fig_look_alike}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild059")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
