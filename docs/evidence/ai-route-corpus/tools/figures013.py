#!/usr/bin/env python3
"""book-013 이미지 페이지 네 개의 SVG를 생성한다.

사용: python3 figures013.py <출력디렉터리>

네 도표에서 표기를 고정했다 — 내가 실제로 걸은 자리는 언제나 주황 실선이고, 걷지 않은 길은 옅은
회색이다. 규칙이 데려간 곳과 데려가지 않은 곳을 가르는 것이 이 책의 방법이라, 그림마다 이 두 색이
뜻하는 바가 달라지면 네 장을 이어서 읽을 수 없다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#2b3238"
MUTED = "#77828a"
LINE = "#c3cbd1"
PALE = "#dfe3e6"
WALK = "#c4703c"
WALL = "#8d949a"


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#fdfcf9"/>
<defs>
  <marker id="walk-end" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto">
    <path d="M0,0 L0,6 L9,3 z" fill="{WALK}"/>
  </marker>
</defs>
<text x="397" y="108" text-anchor="middle" font-size="34" font-weight="700" fill="{INK}">{title}</text>
<text x="397" y="150" text-anchor="middle" font-size="18" fill="{MUTED}">{subtitle}</text>
{body}
<text x="397" y="1072" text-anchor="middle" font-size="14" fill="#98a1a7">낯선 골목과 친해지는 시간</text>
</svg>'''


def text(x, y, value, size=17, fill=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}">{value}</text>')


def closing(y, value):
    return ('<rect x="127" y="%d" width="540" height="58" rx="16" fill="#f1efe8" stroke="%s"/>'
            % (y, LINE)) + text(397, y + 37, value, 20, INK, "700")


# ── p8 규칙이 고른 길 ────────────────────────────────────────────────
# (x, y) 좌표는 골목 그림 안의 자리다. 굵기는 길의 폭을 뜻한다.
JUNCTIONS = [(150, 780), (300, 690), (400, 545), (545, 470)]
CHOSEN = [(150, 780), (300, 690), (400, 545), (545, 470), (600, 350)]
WIDE_STUBS = [((150, 780), (95, 640)), ((300, 690), (215, 560)),
              ((400, 545), (315, 430)), ((545, 470), (655, 545))]


def fig_rule_route():
    b = [text(397, 205, "갈림길에서 덜 넓은 쪽으로", 19, INK, "700")]
    b.append('<rect x="60" y="240" width="500" height="640" rx="18" fill="#ffffff" '
             f'stroke="{LINE}" stroke-width="2"/>')
    # 넓은 길(굵은 회색 띠) — 갈림길에서 고르지 않은 쪽
    for (jx, jy), (ex, ey) in WIDE_STUBS:
        b.append(f'<line x1="{jx}" y1="{jy}" x2="{ex}" y2="{ey}" stroke="{PALE}" '
                 f'stroke-width="20" stroke-linecap="round"/>')
        b.append(f'<line x1="{jx}" y1="{jy}" x2="{ex}" y2="{ey}" stroke="{MUTED}" '
                 f'stroke-width="2" stroke-dasharray="6,6" opacity="0.7"/>')
    # 좁은 길(가는 회색 띠) 위에 실제로 걸은 주황 실선
    pts = " ".join(f"{x},{y}" for x, y in CHOSEN)
    b.append(f'<polyline points="{pts}" fill="none" stroke="{PALE}" stroke-width="11" '
             f'stroke-linecap="round" stroke-linejoin="round"/>')
    b.append(f'<polyline points="{pts}" fill="none" stroke="{WALK}" stroke-width="4.5" '
             f'stroke-linecap="round" stroke-linejoin="round"/>')
    for jx, jy in JUNCTIONS:
        b.append(f'<circle cx="{jx}" cy="{jy}" r="7" fill="#ffffff" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<circle cx="150" cy="780" r="10" fill="{INK}"/>')
    b.append(text(150, 822, "출발", 16, INK, "700"))
    # 막다른 길 벽
    b.append(f'<line x1="565" y1="332" x2="640" y2="312" stroke="{WALL}" stroke-width="9" '
             f'stroke-linecap="round"/>')
    b.append(text(600, 292, "막다른 길, 오늘은 여기까지", 15, WALL, "700"))
    b.append(text(190, 640, "고른 쪽 (좁음)", 15, WALK, "700", "start"))
    b.append(text(88, 620, "고르지 않은 쪽 (넓음)", 14, MUTED, "400", "start"))
    b.append(text(300, 855, "마흔 분 가운데 스물두 분", 16, MUTED))
    # 오른쪽 위 지도 상자
    b.append(f'<rect x="586" y="600" width="150" height="150" rx="12" fill="#ffffff" '
             f'stroke="{LINE}" stroke-width="2"/>')
    thin = [((600, 730), (660, 700)), ((660, 700), (700, 660)), ((700, 660), (722, 620)),
            ((660, 700), (612, 662)), ((700, 660), (730, 700)), ((612, 662), (600, 630))]
    for (x1, y1), (x2, y2) in thin:
        b.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{MUTED}" stroke-width="1.5"/>')
    b.append(text(661, 772, "지도에서 본 같은 자리", 14, MUTED))
    b.append(closing(908, "고르는 일을 규칙에 맡기면 내 취향이 빠진다"))
    return base("규칙이 고른 길", "1장 · 갈림길마다 좁은 쪽", "\n".join(b))


# ── p11 지도의 길이와 걸음의 길이 ────────────────────────────────────
def ground(x0, x1, base_y, rise, samples=26):
    """옆에서 본 바닥선의 표본 좌표. rise가 0이면 평평하다."""
    import math
    pts = []
    for k in range(samples + 1):
        t = k / samples
        pts.append((x0 + (x1 - x0) * t, base_y - rise * math.sin(math.pi * t)))
    return pts


def fig_two_lengths():
    b = [text(397, 205, "지도에서 같은 길이인 두 골목", 19, INK, "700")]
    for i, (name, steps, count, rise, floor) in enumerate(
            (("가 골목", "아흔 걸음", 90, 0, "평평하고 곧다"),
             ("나 골목", "백서른 걸음", 130, 78, "오래된 돌바닥"))):
        y = 250 + i * 310
        b.append(f'<rect x="56" y="{y}" width="684" height="282" rx="16" fill="#ffffff" '
                 f'stroke="{LINE}" stroke-width="2"/>')
        b.append(text(100, y + 40, name, 20, INK, "700", "start"))
        # 왼쪽: 지도 선 (두 칸 모두 같은 길이)
        b.append(f'<line x1="100" y1="{y + 92}" x2="270" y2="{y + 92}" stroke="{MUTED}" '
                 f'stroke-width="2"/>')
        for ex in (100, 270):
            b.append(f'<line x1="{ex}" y1="{y + 84}" x2="{ex}" y2="{y + 100}" stroke="{MUTED}" '
                     f'stroke-width="2"/>')
        b.append(text(185, y + 124, "지도에서는 같다", 14, MUTED))
        # 가운데: 옆에서 본 바닥선과 그 위의 바닥 무늬
        pts = ground(316, 616, y + 232, rise)
        b.append('<polyline points="' + " ".join(f"{px:.1f},{py:.1f}" for px, py in pts)
                 + f'" fill="none" stroke="{INK}" stroke-width="3"/>')
        for px, py in pts[1:-1:2]:
            b.append(f'<rect x="{px - 7:.1f}" y="{py + 5:.1f}" width="14" height="7" rx="2" '
                     f'fill="{PALE}" stroke="{LINE}"/>')
        if rise:
            b.append(text(466, y + 132, "오르막", 15, INK, "700"))
        b.append(text(466, y + 264, floor, 14, MUTED))
        # 맨 오른쪽: 걸음 수 막대 (길이가 걸음 수에 비례한다)
        b.append(f'<rect x="646" y="{y + 74}" width="{count * 0.7:.0f}" height="26" rx="6" '
                 f'fill="{WALK}" fill-opacity="0.85"/>')
        b.append(text(646, y + 124, steps, 15, WALK, "700", "start"))
    b.append(closing(890, "걸음 수에는 굽이와 오르막이 함께 들어 있다"))
    return base("지도의 길이와 걸음의 길이", "2장 · 같은 길이, 다른 걸음", "\n".join(b))


# ── p18 한 달 동안 걸은 자리 ─────────────────────────────────────────
def fig_month():
    """큰길을 맨 위에 두고 골목이 아래로 뻗게 그린다. 아래로 갈수록 큰길에서 먼 안쪽이다."""
    b = [text(397, 205, "규칙이 데려간 곳과 데려가지 않은 곳", 19, INK, "700")]
    left, right, top, bottom = 70, 724, 244, 856
    b.append(f'<rect x="{left}" y="{top}" width="{right - left}" height="{bottom - top}" rx="16" '
             f'fill="#ffffff" stroke="{LINE}" stroke-width="2"/>')
    road_y = 284
    b.append(f'<rect x="{left}" y="{road_y - 15}" width="{right - left}" height="30" fill="{PALE}"/>')
    b.append(text(left + 16, road_y + 6, "큰길", 16, MUTED, "700", "start"))
    lane_x = [left + 40 + k * 58 for k in range(12)]
    depths = [770, 700, 640, 800, 740, 690, 810, 760, 700, 380, 380, 380]
    for x, d in zip(lane_x, depths):
        b.append(f'<line x1="{x}" y1="{road_y + 15}" x2="{x}" y2="{d}" stroke="{PALE}" '
                 f'stroke-width="9" stroke-linecap="round"/>')
    cross = [360, 450, 550, 660, 750]
    for y in cross:
        b.append(f'<line x1="{lane_x[0]}" y1="{y}" x2="{lane_x[8]}" y2="{y}" stroke="{PALE}" '
                 f'stroke-width="9" stroke-linecap="round"/>')
    # 걸은 자리 — 아래로 갈수록 촘촘해진다
    walked = [
        [(lane_x[1], road_y + 15), (lane_x[1], 550), (lane_x[4], 550), (lane_x[4], 690)],
        [(lane_x[3], road_y + 15), (lane_x[3], 660), (lane_x[6], 660), (lane_x[6], 810)],
        [(lane_x[6], road_y + 15), (lane_x[6], 450), (lane_x[4], 450), (lane_x[4], 550)],
        [(lane_x[0], 660), (lane_x[4], 660)],
        [(lane_x[2], 550), (lane_x[2], 750), (lane_x[7], 750)],
        [(lane_x[5], 550), (lane_x[5], 690), (lane_x[8], 690)],
        [(lane_x[4], 660), (lane_x[4], 750)],
        [(lane_x[7], road_y + 15), (lane_x[7], 550), (lane_x[5], 550)],
    ]
    for path in walked:
        pts = " ".join(f"{x},{y}" for x, y in path)
        b.append(f'<polyline points="{pts}" fill="none" stroke="{WALK}" stroke-width="4" '
                 f'stroke-opacity="0.7" stroke-linecap="round" stroke-linejoin="round"/>')
    b.append(f'<circle cx="{lane_x[4]}" cy="660" r="30" fill="none" stroke="{WALK}" '
             f'stroke-width="2" stroke-dasharray="5,5"/>')
    b.append(f'<rect x="{lane_x[4] + 32}" y="626" width="130" height="24" rx="6" fill="#ffffff"/>')
    b.append(text(lane_x[4] + 97, 644, "여기서만 다섯 번", 15, WALK, "700"))
    # 걷지 않은 무리 — 큰길에 붙은 넓은 골목 셋
    b.append(f'<rect x="{lane_x[9] - 22}" y="{road_y + 26}" width="{lane_x[11] - lane_x[9] + 44}" '
             f'height="128" rx="10" fill="none" stroke="{MUTED}" stroke-width="2" '
             f'stroke-dasharray="6,5"/>')
    b.append(text((lane_x[9] + lane_x[11]) / 2, 452, "넓어서 규칙이", 14, MUTED))
    b.append(text((lane_x[9] + lane_x[11]) / 2, 472, "고르지 않은 자리", 14, MUTED))
    # 막다른 길 표시 — 안쪽 끝에 몰려 있다
    for x, y in ((lane_x[4], 690), (lane_x[6], 810), (lane_x[7], 750), (lane_x[8], 690),
                 (lane_x[2], 750), (lane_x[0], 660), (lane_x[4], 750), (lane_x[5], 550)):
        b.append(f'<line x1="{x - 12}" y1="{y}" x2="{x + 12}" y2="{y}" stroke="{WALL}" '
                 f'stroke-width="5" stroke-linecap="round"/>')
    b.append(text(left + 12, 884, "가로 막대는 막다른 길", 15, MUTED, "400", "start"))
    b.append(text(right - 12, 884, "옅은 회색은 걷지 않은 골목", 15, MUTED, "400", "end"))
    b.append(closing(912, "걷지 않은 자리가 어디인지도 규칙이 정했다"))
    return base("한 달 동안 걸은 자리", "3장 · 한 장에 겹쳐 놓은 경로", "\n".join(b))


# ── p37 종이 여든두 장을 붙이면 ──────────────────────────────────────
CELL = 25


def grid(x0, y0, cols, rows, gap=13):
    return [(x0 + c * (CELL + gap), y0 + r * (CELL + gap))
            for r in range(rows) for c in range(cols)]


def draw_grid(b, cells, cols, rows):
    """격자 덩어리를 그리고 이웃끼리 잇는다. 바깥 가장자리 칸에 막다른 표시를 단다."""
    for i, (x, y) in enumerate(cells):
        b.append(f'<rect x="{x:.0f}" y="{y:.0f}" width="{CELL}" height="{CELL}" rx="3" '
                 f'fill="#ffffff" stroke="{LINE}" stroke-width="1.5"/>')
        c, r = i % cols, i // cols
        if c + 1 < cols:
            nx, ny = cells[i + 1]
            b.append(f'<line x1="{x + CELL:.0f}" y1="{y + CELL / 2:.0f}" x2="{nx:.0f}" '
                     f'y2="{ny + CELL / 2:.0f}" stroke="{MUTED}" stroke-width="1.5"/>')
        if r + 1 < rows:
            nx, ny = cells[i + cols]
            b.append(f'<line x1="{x + CELL / 2:.0f}" y1="{y + CELL:.0f}" x2="{nx + CELL / 2:.0f}" '
                     f'y2="{ny:.0f}" stroke="{MUTED}" stroke-width="1.5"/>')
        if r == rows - 1:
            b.append(f'<line x1="{x:.0f}" y1="{y + CELL:.0f}" x2="{x + CELL:.0f}" '
                     f'y2="{y + CELL:.0f}" stroke="{WALL}" stroke-width="5"/>')


def dashed_border(b, cells, label, label_below=False):
    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    b.append(f'<rect x="{min(xs) - 16:.0f}" y="{min(ys) - 16:.0f}" '
             f'width="{max(xs) - min(xs) + CELL + 32:.0f}" '
             f'height="{max(ys) - min(ys) + CELL + 32:.0f}" rx="14" fill="none" '
             f'stroke="{MUTED}" stroke-width="1.5" stroke-dasharray="7,6"/>')
    y = max(ys) + CELL + 46 if label_below else min(ys) - 30
    b.append(text((min(xs) + max(xs) + CELL) / 2, y, label, 16, INK, "700"))


def fig_glued():
    b = [text(397, 200, "골목 한 장씩 적은 종이를 이어짐 칸대로 놓으면", 19, INK, "700")]
    first = grid(96, 280, 6, 4)
    second = grid(432, 280, 6, 5)
    draw_grid(b, first, 6, 4)
    draw_grid(b, second, 6, 5)
    dashed_border(b, first, "첫째 덩어리")
    dashed_border(b, second, "둘째 덩어리")
    road_y = 566
    b.append(f'<rect x="60" y="{road_y - 15}" width="676" height="30" fill="{PALE}"/>')
    b.append(text(76, road_y + 6, "큰길", 15, MUTED, "700", "start"))
    # 셋째 덩어리: 큰길에 매달린 빗살 열넷 + 그 아래 이어진 격자 열넷
    comb = [(96 + k * 44, 632) for k in range(14)]
    lower = grid(184, 730, 7, 2)
    for x, y in comb:
        b.append(f'<line x1="{x + CELL / 2:.0f}" y1="{road_y + 15}" x2="{x + CELL / 2:.0f}" '
                 f'y2="{y:.0f}" stroke="{MUTED}" stroke-width="1.5"/>')
        b.append(f'<rect x="{x:.0f}" y="{y:.0f}" width="{CELL}" height="{CELL}" rx="3" '
                 f'fill="#ffffff" stroke="{LINE}" stroke-width="1.5"/>')
        b.append(f'<line x1="{x:.0f}" y1="{y + CELL:.0f}" x2="{x + CELL:.0f}" '
                 f'y2="{y + CELL:.0f}" stroke="{WALL}" stroke-width="5"/>')
    draw_grid(b, lower, 7, 2)
    dashed_border(b, comb + lower, "셋째 덩어리", label_below=True)
    b.append(f'<rect x="{comb[0][0] - 8:.0f}" y="{comb[0][1] - 8:.0f}" '
             f'width="{comb[-1][0] - comb[0][0] + CELL + 16:.0f}" height="{CELL + 16}" rx="9" '
             f'fill="none" stroke="{WALK}" stroke-width="2"/>')
    b.append(f'<rect x="290" y="{comb[0][1] - 38}" width="214" height="26" rx="6" fill="#fdfcf9"/>')
    b.append(text(397, comb[0][1] - 20, "이 구역만 그물이 아니라 빗살", 15, WALK, "700"))
    b.append(text(96, 856, "네모 하나가 골목 한 장", 14, MUTED, "400", "start"))
    b.append(f'<line x1="596" y1="851" x2="622" y2="851" stroke="{WALL}" stroke-width="5"/>')
    b.append(text(630, 856, "막다른 길", 14, MUTED, "400", "start"))
    b.append(closing(886, "걷는 동안에는 보이지 않던 모양이 종이 위에 있다"))
    return base("종이 여든두 장을 붙이면", "5장 · 이어짐 칸대로 놓은 결과", "\n".join(b))


FIGURES = {8: fig_rule_route, 11: fig_two_lengths, 18: fig_month, 37: fig_glued}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild013")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(fn(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
