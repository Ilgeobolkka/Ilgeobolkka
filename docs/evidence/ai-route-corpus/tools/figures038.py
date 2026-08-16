#!/usr/bin/env python3
"""book-038 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 치수와 개수를 눈금으로 넣지 않는다 —
벽에서 잰 값은 남은 조각의 값이라 재어 견줄 수 없고 갈림만 견줄 수 있기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, DIM = "#33454b", "#8a9296"
CLAY, TEAL = "#a8623f", "#3d6f74"
RED = "#b4453c"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="dim" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{DIM}"/></marker>
  <marker id="clay" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{CLAY}"/></marker>
</defs>
{t(W/2, 122, title, 32, '#203238', '700')}
{t(W/2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def foot(y, text, size=16):
    return ('<rect x="105" y="%d" width="584" height="68" rx="14" fill="#f3efe7" stroke="#cdbfa6"/>' % y
            + t(W / 2, y + 42, text, size, "#5b4a35", "700"))


def fig_three_walls():
    """1.3 — 세 자리의 벽."""
    b = []
    b.append('<rect x="185" y="228" width="424" height="150" rx="6" fill="#faf8f3" stroke="#cfd6d8"/>')
    for x in (283, 381, 479):
        b.append(f'<line x1="{x}" y1="228" x2="{x}" y2="378" stroke="#e2e7e8"/>')
    for y in (278, 328):
        b.append(f'<line x1="185" y1="{y}" x2="609" y2="{y}" stroke="#e2e7e8"/>')
    b.append(t(397, 218, "채원궁 평면", 14, DIM))
    rooms = [(234, 253, 96, 48, "큰 방", 205),
             (430, 303, 60, 40, "작은 방", 400),
             (545, 303, 44, 132, "복도", 595)]
    for cx, cy, w, h, name, _ in rooms:
        b.append(f'<rect x="{cx - w/2}" y="{cy - h/2}" width="{w}" height="{h}" rx="4" '
                 f'fill="{TEAL}" opacity="0.75"/>')
        b.append(t(cx, cy + 6, name, 14, "#ffffff", "700"))

    cols = [(200, "큰 방", "손님", [0.85, 0.35, 0.85]),
            (325, "작은 방", "집안 사람", [0.3, 0.85, 0.4]),
            (450, "복도", "지나가는 사람", [0.25, 0.25, 0.2])]
    rows = ["사람 수", "그릇의 자세함", "쓰인 색"]
    top = 486
    for x, name, viewer, vals in cols:
        b.append(f'<path d="M{x + 62},418 L{x + 62},{top - 14}" stroke="{DIM}" '
                 f'stroke-width="1.4" marker-end="url(#dim)"/>')
        b.append(f'<rect x="{x}" y="{top}" width="124" height="322" rx="10" '
                 f'fill="#ffffff" stroke="#c8d0d2"/>')
        b.append(t(x + 62, top + 30, name, 16, INK, "700"))
        b.append(t(x + 62, top + 54, viewer, 13, DIM))
        for i, op in enumerate(vals):
            y = top + 86 + i * 76
            b.append(f'<rect x="{x + 16}" y="{y}" width="92" height="58" rx="7" '
                     f'fill="{CLAY}" opacity="{op}"/>')
    for i, label in enumerate(rows):
        b.append(t(188, top + 86 + i * 76 + 34, label, 14, "#46565b", "700", "end"))
    for x, name, _, _ in [(rooms[0][0], 0, 0, 0)]:
        pass
    b.append(f'<path d="M234,278 L262,414" stroke="{DIM}" stroke-width="1" stroke-dasharray="3 3"/>')
    b.append(f'<path d="M430,323 L387,414" stroke="{DIM}" stroke-width="1" stroke-dasharray="3 3"/>')
    b.append(f'<path d="M545,369 L512,414" stroke="{DIM}" stroke-width="1" stroke-dasharray="3 3"/>')
    b.append(foot(880, "한 궁의 벽도 한 덩어리가 아니다"))
    return base("세 자리의 벽", "누가 보는 벽이냐가 무엇을 그릴지 정한다", "\n".join(b))


def fig_color_split():
    """2.3 — 색이 나뉘어 있다."""
    b = []
    colors = ["붉은색", "푸른색", "흰색", "검은색", "흙빛"]
    targets = ["옷", "그릇", "음식", "바탕"]
    filled = {(0, 0), (1, 1), (2, 0), (2, 2), (3, 0), (3, 2), (4, 3)}
    x0, y0, cw, ch = 250, 300, 100, 84
    for j, name in enumerate(targets):
        b.append(t(x0 + j * cw + cw / 2, y0 - 18, name, 16, INK, "700"))
    for i, name in enumerate(colors):
        b.append(t(x0 - 20, y0 + i * ch + ch / 2 + 6, name, 16, INK, "700", "end"))
        for j in range(len(targets)):
            x, y = x0 + j * cw, y0 + i * ch
            b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" fill="#ffffff" '
                     f'stroke="#d5dbdd"/>')
            if (i, j) in filled:
                b.append(f'<rect x="{x + 10}" y="{y + 10}" width="{cw - 20}" height="{ch - 20}" '
                         f'rx="6" fill="{TEAL}" opacity="0.8"/>')
    b.append(f'<rect x="{x0 + 3}" y="{y0 + ch + 3}" width="{cw - 6}" height="{ch - 6}" rx="6" '
             f'fill="none" stroke="{RED}" stroke-width="2.5"/>')
    b.append(f'<path d="M{x0 - 8},{y0 + ch * 1.5} L{x0 - 60},{y0 + ch * 1.5}" stroke="{RED}" '
             f'stroke-width="1.4" opacity="0"/>')
    b.append(t(x0 + cw * 4 + 18, y0 + ch * 1.5 + 6, "두 자리에서만", 14, RED, "700", "start"))
    b.append(t(x0 + cw * 4 + 18, y0 + ch * 1.5 + 28, "어긋난다", 14, RED, "700", "start"))
    b.append(t(397, y0 + ch * 5 + 46, "채워진 칸 = 그 색이 그 대상에 쓰였다", 15, DIM))
    b.append(foot(830, "규칙이 있어야 예외가 보인다"))
    return base("색이 나뉘어 있다", "나뉘어 있으면 갈리는 자리가 뜻을 지닌다", "\n".join(b))


def fig_vessel_seat():
    """3.3 — 굽이 높은 그릇은 어디에 놓이는가."""
    b = []
    seat_cx, seat_cy = 566, 560
    b.append(f'<rect x="{seat_cx - 78}" y="{seat_cy - 58}" width="156" height="116" rx="10" '
             f'fill="#f4efe4" stroke="#cbbb9c"/>')
    b.append(t(seat_cx, seat_cy + 6, "상", 17, "#8a7551", "700"))
    seats = [(seat_cx, seat_cy - 92, True), (seat_cx - 62, seat_cy - 92, False),
             (seat_cx + 62, seat_cy - 92, False), (seat_cx - 62, seat_cy + 92, False),
             (seat_cx, seat_cy + 92, False), (seat_cx + 62, seat_cy + 92, False)]
    for sx, sy, main in seats:
        fill = CLAY if main else "#ffffff"
        b.append(f'<circle cx="{sx}" cy="{sy}" r="21" fill="{fill}" stroke="#b0a184" '
                 f'stroke-width="{2.5 if main else 1.2}"/>')
    b.append(t(seat_cx - 14, seat_cy - 116, "가운데 자리", 14, CLAY, "700"))

    x = 190
    tall_y, short_y = [], []
    for i in range(9):
        y = 262 + i * 66
        tall = i < 4
        stem = 26 if tall else 9
        b.append(f'<path d="M{x - 26},{y} h52 l-9,20 h-34 z" fill="#ffffff" stroke="{INK}"/>')
        b.append(f'<rect x="{x - 3}" y="{y + 20}" width="6" height="{stem}" fill="{INK}"/>')
        b.append(f'<rect x="{x - 16}" y="{y + 20 + stem}" width="32" height="6" rx="2" fill="{INK}"/>')
        (tall_y if tall else short_y).append(y + 14)
    b.append(f'<rect x="{x - 54}" y="248" width="14" height="{4 * 66 - 18}" rx="6" '
             f'fill="{CLAY}" opacity="0.85"/>')
    b.append(t(x - 62, 248 + (4 * 66 - 18) / 2, "굽이 높은 넷", 14, CLAY, "700", "end"))
    b.append(t(x - 62, 248 + 4 * 66 + (5 * 66 - 18) / 2 - 12, "굽이 짧은 다섯", 14, DIM, "700", "end"))

    for y in tall_y:
        b.append(f'<path d="M{x + 34},{y} C 360,{y} 400,{seat_cy - 92} {seat_cx - 26},{seat_cy - 92}" '
                 f'fill="none" stroke="{CLAY}" stroke-width="2" opacity="0.9"/>')
    for k, y in enumerate(short_y):
        tx, ty = seats[[1, 2, 3, 4, 5][k]][0], seats[[1, 2, 3, 4, 5][k]][1]
        if k == 1:
            # 오른쪽 위 자리는 표시된 자리를 지나지 않도록 위로 돌려 붙인다
            path = f"M{x + 34},{y} C 300,{y - 120} 480,{ty - 210} {tx},{ty - 26}"
        else:
            path = f"M{x + 34},{y} C 380,{y} 420,{ty} {tx - 26},{ty}"
        b.append(f'<path d="{path}" fill="none" stroke="{DIM}" stroke-width="1.2" '
                 f'stroke-dasharray="4 4"/>')
    b.append(foot(880, "세 자리 벽에서 모두 지켜진다"))
    return base("굽이 높은 그릇은 어디에 놓이는가",
                "그릇의 모양이 앉은 자리를 따라 갈린다", "\n".join(b))


def fig_four_cells():
    """4.3 — 네 칸 가운데 셋만 셀 수 있다."""
    b = []
    x0, y0, cw, ch = 227, 330, 170, 170
    b.append(t(x0 + cw / 2, y0 - 62, "살림 목록에 있음", 16, INK, "700"))
    b.append(t(x0 + cw * 1.5, y0 - 62, "없음", 16, INK, "700"))
    b.append(t(x0 - 24, y0 + ch / 2, "벽에", 15, INK, "700", "end"))
    b.append(t(x0 - 24, y0 + ch / 2 + 22, "있음", 15, INK, "700", "end"))
    b.append(t(x0 - 24, y0 + ch * 1.5 + 10, "없음", 15, INK, "700", "end"))
    cells = [(0, 0, "둘 다", 0.55), (1, 0, "벽에만", 0.3), (0, 1, "목록에만", 0.9)]
    for j, i, label, op in cells:
        x, y = x0 + j * cw, y0 + i * ch
        b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" fill="{TEAL}" '
                 f'opacity="{op}" stroke="#ffffff" stroke-width="2"/>')
        b.append(t(x + cw / 2, y + ch / 2 + 6, label, 18,
                   "#ffffff" if op > 0.5 else "#27353a", "700"))
    x, y = x0 + cw, y0 + ch
    b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" fill="#fbfbfa" '
             f'stroke="#c8d0d2" stroke-width="2"/>')
    b.append(f'<clipPath id="nocount"><rect x="{x}" y="{y}" width="{cw}" height="{ch}"/></clipPath>')
    b.append('<g clip-path="url(#nocount)">')
    for k in range(-4, 7):
        b.append(f'<line x1="{x + k * 30}" y1="{y + ch}" x2="{x + ch + k * 30}" y2="{y}" '
                 f'stroke="{DIM}" stroke-width="2" opacity="0.5"/>')
    b.append('</g>')
    b.append(f'<rect x="{x0}" y="{y0}" width="{cw * 2}" height="{ch * 2}" fill="none" '
             f'stroke="#9aa4a7" stroke-width="2"/>')
    b.append(t(x + cw + 20, y + ch / 2 + 6, "셀 수 없는 칸", 15, DIM, "700", "start"))
    b.append(foot(790, "없는 것을 세려면 다른 자료가 있어야 한다"))
    return base("네 칸 가운데 셋만 셀 수 있다",
                "어긋남은 오류이기 전에 목적의 차이다", "\n".join(b))


def fig_grid_count():
    """5.2 — 나누어 세면 다르게 보인다."""
    b = []
    x0, y0, cw, ch = 137, 400, 87, 106
    dens = [[0.25, 0.85, 0.5, 0.35, 0.2, 0.15],
            [0.3, 0.9, 0.45, 0.3, 0.2, 0.1],
            [0.2, 0.75, 0.4, 0.25, 0.15, 0.1]]
    for i in range(3):
        for j in range(6):
            x, y = x0 + j * cw, y0 + i * ch
            b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" fill="{TEAL}" '
                     f'opacity="{dens[i][j]}" stroke="#ffffff" stroke-width="1.5"/>')
    b.append(f'<rect x="{x0}" y="{y0}" width="{cw * 6}" height="{ch * 3}" fill="none" '
             f'stroke="#8f9a9d" stroke-width="2"/>')
    b.append(t(397, y0 - 24, "큰 방 동쪽 벽 · 가로 여섯 세로 셋", 15, DIM))

    ccx, ccy = x0 + cw * 3, y0 + ch * 1.5
    b.append(f'<circle cx="{ccx}" cy="{ccy}" r="62" fill="none" stroke="{RED}" stroke-width="3"/>')
    b.append(t(ccx, y0 + ch * 3 + 40, "눈에 먼저 들어온 자리", 15, RED, "700"))
    b.append(f'<path d="M{ccx},{y0 + ch * 3} L{ccx},{y0 + ch * 3 + 18}" stroke="{RED}" '
             f'stroke-width="1.6"/>')

    acx = x0 + cw * 1.5
    b.append(f'<path d="M{acx},{y0 - 62} L{acx},{y0 - 12}" stroke="{CLAY}" stroke-width="2" '
             f'marker-end="url(#clay)"/>')
    b.append(t(acx, y0 - 74, "사람이 가장 많은 자리", 15, CLAY, "700"))
    b.append(t(397, y0 + ch * 3 + 82, "칸이 짙을수록 그 칸에 든 사람이 많다", 14, DIM))
    b.append(foot(830, "눈이 고른 자리를 수가 고쳐 준다"))
    return base("나누어 세면 다르게 보인다",
                "크게 그린 자리와 많이 그린 자리가 다르다", "\n".join(b))


def fig_three_filters():
    """6.3 — 벽에 닿기까지 세 번 걸러진다."""
    b = []
    steps = [("실제로 먹은 것", 520), ("그릴 만하다고 여긴 것", 400),
             ("그릴 줄 아는 것", 290), ("벽에 남은 것", 190)]
    filters = ["값어치 매김", "익힌 모티프", "무너진 자리"]
    y = 250
    tops = []
    for k, (label, w) in enumerate(steps):
        x = 397 - w / 2
        op = 0.8 - k * 0.13
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="86" rx="10" fill="{TEAL}" '
                 f'opacity="{op}"/>')
        b.append(t(397, y + 52, label, 18, "#ffffff", "700"))
        tops.append((y, w))
        if k < 3:
            b.append(f'<path d="M397,{y + 86} L397,{y + 132}" stroke="{DIM}" stroke-width="2" '
                     f'marker-end="url(#dim)"/>')
            b.append(f'<path d="M{397 - w / 2 + 24},{y + 100} L{397 - w / 2 - 46},{y + 122}" '
                     f'stroke="{CLAY}" stroke-width="1.8" marker-end="url(#clay)"/>')
            b.append(t(397 - w / 2 - 54, y + 128, filters[k], 14, CLAY, "700", "end"))
        y += 132
    b.append(f'<path d="M{397 + steps[3][1] / 2 + 18},{tops[3][0] + 43} '
             f'C 700,{tops[3][0]} 700,{tops[0][0] + 43} {397 + steps[0][1] / 2 + 18},{tops[0][0] + 43}" '
             f'fill="none" stroke="{DIM}" stroke-width="1.6" stroke-dasharray="6 6"/>')
    b.append(t(716, (tops[0][0] + tops[3][0]) / 2 + 36, "되짚어", 14, DIM, "700", "start"))
    b.append(t(716, (tops[0][0] + tops[3][0]) / 2 + 58, "올라갈", 14, DIM, "700", "start"))
    b.append(t(716, (tops[0][0] + tops[3][0]) / 2 + 80, "길은 없다", 14, DIM, "700", "start"))
    b.append(foot(870, "좁아진 만큼만 말한다"))
    return base("벽에 닿기까지 세 번 걸러진다",
                "벽은 실제가 아니라 세 번 걸러진 나머지다", "\n".join(b))


FIGURES = {
    7: fig_three_walls,
    13: fig_color_split,
    21: fig_vessel_seat,
    29: fig_four_cells,
    35: fig_grid_count,
    43: fig_three_filters,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild038")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
