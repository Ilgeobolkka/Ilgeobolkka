#!/usr/bin/env python3
"""book-052 이미지 페이지 4개의 SVG 생성. figures051.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '무엇이 내 몫이고 무엇이 아닌가'를 그린다. 몫에 드는 자리는 진한 선과 채움,
몫 밖의 자리는 옅은 선, 시간에 따라 굳는 것은 계단으로 고정해 같은 뜻으로 쓴다.

사용: python3 figures052.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 내 몫에 드는 자리
DROP = "#c3ccd0"          # 몫 밖의 자리
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
  <marker id="soft" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{DROP}"/></marker>
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


# ── p8 선택은 두 조건이 함께 설 때만 선다 ─────────────────────────────
def fig_quadrant():
    b = []
    x0, y0, cw, ch = 208, 268, 190, 150
    cells = [
        (0, 0, "강요", False), (1, 0, "선택", True),
        (0, 1, "그냥 일어난 일", False), (1, 1, "사고", False),
    ]
    for cx, cy, label, on in cells:
        x, y = x0 + cx * cw, y0 + cy * ch
        fill = "#eaf0ee" if on else "#ffffff"
        stroke = KEEP if on else DROP
        width = 3 if on else 1.4
        b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" fill="{fill}" '
                 f'stroke="{stroke}" stroke-width="{width}"/>')
        b.append(t(x + cw / 2, y + ch / 2 + 8, label,
                   24 if on else 16, INK if on else LINE, "700" if on else "400"))
    b.append(t(x0 + cw / 2, y0 + 2 * ch + 32, "열린 길 하나", 16, MUTED))
    b.append(t(x0 + cw * 1.5, y0 + 2 * ch + 32, "열린 길 여럿", 16, MUTED))
    b.append(f'<text x="{x0 - 22}" y="{y0 + ch / 2}" text-anchor="middle" font-size="16" '
             f'fill="{MUTED}" transform="rotate(-90 {x0 - 22} {y0 + ch / 2})">내 몫 있음</text>')
    b.append(f'<text x="{x0 - 22}" y="{y0 + ch * 1.5}" text-anchor="middle" font-size="16" '
             f'fill="{MUTED}" transform="rotate(-90 {x0 - 22} {y0 + ch * 1.5})">내 몫 없음</text>')
    ay = y0 + 2 * ch + 66
    b.append(f'<line x1="{x0 + 40}" y1="{ay}" x2="{x0 + 2 * cw - 40}" y2="{ay}" stroke="{LINE}" '
             f'stroke-width="1.8" marker-end="url(#line)"/>')
    b.append(t(x0 + cw, ay - 12, "가능 · 감당 · 인지 순으로 좁아진다", 15, LINE))
    b.append(note_box(147, 668, 500, "두 축 가운데 하나만 무너져도 선택이 아니다"))
    b.append(caption(W / 2, 790, [
        "다른 길이 열려 있어야 하고 그 방향에 내 몫이 있어야 한다.",
        "열려 있음을 어느 뜻으로 쓰는지가 책임의 크기를 정한다.",
    ], 16))
    return base("선택은 두 조건이 함께 설 때만 선다", "선택이라 부를 수 있는 자리와 그렇지 않은 자리",
                "".join(b))


# ── p14 값은 고르게 늘지 않는다 ───────────────────────────────────────
def fig_lock():
    b = []
    ox, oy, ow, oh = 150, 560, 500, 320
    b.append(f'<line x1="{ox}" y1="{oy}" x2="{ox + ow}" y2="{oy}" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<line x1="{ox}" y1="{oy}" x2="{ox}" y2="{oy - oh}" stroke="{INK}" stroke-width="2"/>')
    b.append(t(ox + ow / 2, oy + 44, "결정한 뒤 흐른 시간", 16, MUTED))
    b.append(f'<text x="{ox - 34}" y="{oy - oh / 2}" text-anchor="middle" font-size="16" '
             f'fill="{MUTED}" transform="rotate(-90 {ox - 34} {oy - oh / 2})">되돌리는 값</text>')

    x1, x2 = ox + 170, ox + 320
    lv = [oy - 60, oy - 150, oy - 262]
    path = (f"M{ox + 10},{lv[0]} L{x1},{lv[0]} L{x1},{lv[1]} L{x2},{lv[1]} "
            f"L{x2},{lv[2]} L{ox + ow - 12},{lv[2]}")
    b.append(f'<path d="{path}" fill="none" stroke="{MARK}" stroke-width="3.2" stroke-linejoin="round"/>')
    for x in (x1, x2):
        b.append(f'<line x1="{x}" y1="{oy}" x2="{x}" y2="{oy - oh + 20}" stroke="{LINE}" '
                 f'stroke-width="1.4" stroke-dasharray="5 5"/>')
    b.append(t(x1 - 10, oy - 26, "남들이 알게 된 때", 14, LINE, anchor="end"))
    b.append(t(x2 + 10, oy - 26, "다른 일이 얹힌 때", 14, LINE, anchor="start"))
    b.append(t((x1 + x2) / 2, lv[1] - 18, "아직 변덕", 15, MUTED))
    b.append(t((x2 + ox + ow) / 2, lv[2] - 18, "이제 배신", 15, MARK, "700"))
    b.append(f'<line x1="{ox + 24}" y1="{lv[0] - 32}" x2="{x1 - 16}" y2="{lv[0] - 32}" '
             f'stroke="{KEEP}" stroke-width="2.4" marker-end="url(#keep)"/>')
    b.append(t((ox + x1) / 2, lv[0] - 44, "확인은 여기서", 15, KEEP, "700"))

    b.append(note_box(147, 700, 500, "뛰기 전에 물어야 값이 있다"))
    b.append(caption(W / 2, 822, [
        "되돌리는 값은 시간에 비례해 늘지 않고 두 지점에서 뛴다.",
        "같은 철회라도 어느 구간에 있느냐에 따라 이름이 달라진다.",
    ], 16))
    return base("값은 고르게 늘지 않는다", "시간이 지나며 되돌리기 값이 뛰는 모양", "".join(b))


# ── p26 두 조건이 겹치는 자리만 내 몫이다 ─────────────────────────────
def fig_overlap():
    b = []
    cy, r = 430, 148
    lx, rx = 318, 476
    import random
    b.append(f'<circle cx="{lx}" cy="{cy}" r="{r}" fill="#eef2f1" stroke="{LINE}" stroke-width="2"/>')
    b.append(f'<circle cx="{rx}" cy="{cy}" r="{r}" fill="#eef2f1" stroke="{LINE}" stroke-width="2"/>')
    b.append(f'<clipPath id="cl"><circle cx="{lx}" cy="{cy}" r="{r}"/></clipPath>')
    b.append(f'<circle cx="{rx}" cy="{cy}" r="{r}" fill="{KEEP}" opacity="0.30" clip-path="url(#cl)"/>')
    b.append(t(lx - 46, cy - r - 22, "그릴 수 있었다", 17, INK, "700"))
    b.append(t(rx + 46, cy - r - 22, "다르게 할 수 있었다", 17, INK, "700"))
    b.append(t((lx + rx) / 2, cy + 4, "내 몫", 26, "#2c4f48", "700"))
    b.append(t(lx - 62, cy - 8, "알았지만", 14, LINE))
    b.append(t(lx - 62, cy + 14, "막을 수 없었다", 14, LINE))
    b.append(t(rx + 62, cy - 8, "막을 수 있었지만", 14, LINE))
    b.append(t(rx + 62, cy + 14, "몰랐다", 14, LINE))

    random.seed(52)
    for _ in range(46):
        px, py = random.uniform(120, 674), random.uniform(250, 660)
        if (px - lx) ** 2 + (py - cy) ** 2 < (r + 12) ** 2:
            continue
        if (px - rx) ** 2 + (py - cy) ** 2 < (r + 12) ** 2:
            continue
        b.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="2.6" fill="{DROP}"/>')
    b.append(t(160, 268, "운", 20, LINE, "700"))

    b.append(f'<line x1="{lx + 26}" y1="{cy + r - 26}" x2="{rx - 26}" y2="{cy + r - 26}" '
             f'stroke="{MARK}" stroke-width="2" marker-start="url(#mark)" marker-end="url(#mark)"/>')
    b.append(t((lx + rx) / 2, cy + r + 28, "자리와 경험에 따라 움직인다", 14, MARK, "700"))

    b.append(note_box(147, 700, 500, "겹치는 자리는 사람마다 다르게 그려진다"))
    b.append(caption(W / 2, 822, [
        "그릴 수 있었고 다르게 할 수 있었던 자리만 내 몫에 든다.",
        "두 조건 밖의 넓은 자리는 운이며 결과에 늘 섞여 있다.",
    ], 16))
    return base("두 조건이 겹치는 자리만 내 몫이다", "예견과 회피가 만드는 책임의 범위", "".join(b))


# ── p37 같은 자리에서 두 갈래로 갈린다 ────────────────────────────────
def fig_regret():
    b = []
    top = 268
    b.append(f'<rect x="322" y="{top}" width="150" height="58" rx="29" fill="#f4ece2" '
             f'stroke="{MARK}" stroke-width="2.4"/>')
    b.append(t(397, top + 37, "후회", 22, "#7d4b21", "700"))

    lx, rx = 250, 570
    b.append(f'<line x1="370" y1="{top + 58}" x2="{lx + 40}" y2="378" stroke="{KEEP}" '
             f'stroke-width="2.4" marker-end="url(#keep)"/>')
    b.append(f'<line x1="424" y1="{top + 58}" x2="{rx - 40}" y2="378" stroke="{DROP}" '
             f'stroke-width="2.4" marker-end="url(#soft)"/>')

    steps = ["그때 몰랐던 것을 적는다", "기준을 고친다", "다음 결정에 쓴다"]
    ys = [392, 470, 548]
    for yy, label in zip(ys, steps):
        b.append(f'<rect x="{lx - 118}" y="{yy}" width="236" height="52" rx="8" fill="#ffffff" '
                 f'stroke="{KEEP}" stroke-width="1.8"/>')
        b.append(t(lx, yy + 32, label, 15, INK))
    for a, bm in ((444, 470), (522, 548)):
        b.append(f'<line x1="{lx}" y1="{a}" x2="{lx}" y2="{bm}" stroke="{KEEP}" stroke-width="1.8" '
                 f'marker-end="url(#keep)"/>')
    b.append(f'<line x1="{lx}" y1="600" x2="{lx}" y2="632" stroke="{KEEP}" stroke-width="2.4" '
             f'marker-end="url(#keep)"/>')
    b.append(t(lx, 656, "다음 결정", 17, KEEP, "700"))

    b.append(f'<rect x="{rx - 108}" y="392" width="216" height="52" rx="8" fill="#ffffff" '
             f'stroke="{DROP}" stroke-width="1.8"/>')
    b.append(t(rx, 424, "나는 원래 그렇다", 15, LINE))
    b.append(f'<path d="M{rx + 108},412 C772,376 748,252 632,248 L516,248 Q478,248 474,286" '
             f'fill="none" stroke="{DROP}" stroke-width="2.2" marker-end="url(#soft)"/>')

    b.append(t(lx, 700, "일에 붙임", 17, KEEP, "700"))
    b.append(t(rx, 700, "사람에 붙임", 17, LINE, "700"))
    b.append(note_box(147, 748, 500, "끝 문장이 사람으로 끝나면 고리가 된다"))
    b.append(caption(W / 2, 870, [
        "같은 후회가 배움으로도 가고 되돌아오는 고리로도 간다.",
        "갈림을 정하는 것은 후회를 일에 붙이는지 사람에 붙이는지다.",
    ], 16))
    return base("같은 자리에서 두 갈래로 갈린다", "후회가 배움으로 가는 길과 자책으로 가는 길",
                "".join(b))


FIGURES = {8: fig_quadrant, 14: fig_lock, 26: fig_overlap, 37: fig_regret}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild052")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
