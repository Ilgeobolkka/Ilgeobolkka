#!/usr/bin/env python3
"""book-040 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 거리와 인원을 자로 잰 값으로 넣지 않는다 —
남은 일지가 해마다 이어지지 않아 값으로 견줄 수 없고 갈림과 몰림만 견줄 수 있기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
LINE, MUTE = "#2c3a42", "#939ea4"
NORTH, SOUTH = "#7c6a92", "#4f7a6a"
FLAG = "#b0503f"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, head, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="mute" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MUTE}"/></marker>
  <marker id="flag" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{FLAG}"/></marker>
</defs>
{t(W/2, 118, title, 31, '#1c2930', '700')}
{t(W/2, 158, head, 16, '#6f8088')}
<line x1="105" y1="188" x2="689" y2="188" stroke="#dfe4e6"/>
{body}
</svg>'''


def tailband(y, text, size=16):
    return (f'<rect x="105" y="{y}" width="584" height="60" rx="6" fill="#f2f0ea" '
            f'stroke="#cfc9ba"/>' + t(W / 2, y + 38, text, size, "#5a5340", "700"))


def fig_three_strands():
    """1.3 — 세 갈래로 남은 것."""
    b = []
    rows = ["조약문", "초소 일지", "도시 문서"]
    cols = ["선의 자리", "오간 수", "사람의 이름", "값과 셈"]
    filled = {(0, 0), (1, 0), (1, 1), (2, 2), (2, 3)}
    x0, y0, cw, ch = 246, 320, 86, 108
    for j, name in enumerate(cols):
        b.append(t(x0 + j * cw + cw / 2, y0 - 22, name, 15, LINE, "700"))
    for i, name in enumerate(rows):
        b.append(f'<rect x="{x0 - 142}" y="{y0 + i * ch + 22}" width="126" height="60" rx="8" '
                 f'fill="#ffffff" stroke="{LINE}" stroke-width="1.8"/>')
        b.append(t(x0 - 79, y0 + i * ch + 59, name, 17, LINE, "700"))
        for j in range(len(cols)):
            x, y = x0 + j * cw, y0 + i * ch
            b.append(f'<rect x="{x + 6}" y="{y + 22}" width="{cw - 12}" height="60" rx="6" '
                     f'fill="#ffffff" stroke="#d7dcde"/>')
            if (i, j) in filled:
                b.append(f'<rect x="{x + 6}" y="{y + 22}" width="{cw - 12}" height="60" rx="6" '
                         f'fill="{SOUTH}" opacity="0.75"/>')
    b.append(t(397, y0 + ch * 3 + 24, "채워진 네모 = 그 갈래가 그 물음에 답한다", 15, MUTE))
    nx = x0 + cw * 4 + 22
    b.append(t(nx, y0 + ch + 46, "한 갈래로는", 14, FLAG, "700", "start"))
    b.append(t(nx, y0 + ch + 68, "어느 물음에도", 14, FLAG, "700", "start"))
    b.append(t(nx, y0 + ch + 90, "닿지 않는다", 14, FLAG, "700", "start"))
    b.append(tailband(830, "겹쳐 놓아야 도시가 보인다"))
    return base("세 갈래로 남은 것",
                "무엇을 남기려 만든 문서인지가 무엇이 적혔는지를 정한다", "\n".join(b))


def fig_treaty_shape():
    """2.4 — 조약문의 겉모양."""
    b = []
    x, y, w, h = 300, 240, 250, 520
    b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="4" fill="#fcfbf7" '
             f'stroke="{LINE}" stroke-width="2"/>')
    b.append(f'<line x1="{x}" y1="{y + 44}" x2="{x + w}" y2="{y + 44}" stroke="{LINE}"/>')
    b.append(f'<line x1="{x}" y1="{y + 96}" x2="{x + w}" y2="{y + 96}" stroke="{LINE}"/>')
    b.append(t(x + w / 2, y + 76, "머리말 자리", 15, LINE, "700"))
    for k in range(14):
        ly = y + 122 + k * 20
        b.append(f'<line x1="{x + 24}" y1="{ly}" x2="{x + w - 24}" y2="{ly}" '
                 f'stroke="{MUTE}" stroke-width="2" opacity="0.55"/>')
    b.append(t(x + w + 22, y + 210, "본문", 16, LINE, "700", "start"))
    band_y = y + 412
    b.append(f'<rect x="{x}" y="{band_y}" width="{w}" height="42" fill="{FLAG}" opacity="0.14"/>')
    for k in range(2):
        b.append(f'<line x1="{x + 24}" y1="{band_y + 14 + k * 16}" x2="{x + w - 60}" '
                 f'y2="{band_y + 14 + k * 16}" stroke="{MUTE}" stroke-width="2" opacity="0.55"/>')
    b.append(f'<path d="M{x - 96},{y + 150} L{x - 96},{band_y + 14}" stroke="{FLAG}" '
             f'stroke-width="1.8" marker-end="url(#flag)"/>')
    b.append(t(x - 106, y + 140, "나중에", 14, FLAG, "700", "end"))
    b.append(t(x - 106, y + 162, "덧붙인 자리", 14, FLAG, "700", "end"))
    b.append(f'<path d="M{x - 90},{band_y + 20} L{x - 6},{band_y + 20}" stroke="{FLAG}" '
             f'stroke-width="1.4" opacity="0.6"/>')
    b.append(f'<circle cx="{x + 56}" cy="{y + h - 48}" r="26" fill="none" stroke="{NORTH}" '
             f'stroke-width="2.4"/>')
    b.append(f'<circle cx="{x + 84}" cy="{y + h - 48}" r="26" fill="none" stroke="{SOUTH}" '
             f'stroke-width="2.4"/>')
    b.append(t(x + 70, y + h + 24, "두 나라의 도장", 14, LINE, "700"))
    b.append(f'<line x1="{x + w - 96}" y1="{y + h - 44}" x2="{x + w - 20}" y2="{y + h - 44}" '
             f'stroke="{LINE}" stroke-width="2"/>')
    b.append(t(x + w - 58, y + h + 24, "날짜", 14, LINE, "700"))
    b.append(tailband(830, "덧붙인 자리는 늘 아래에 있다"))
    return base("조약문의 겉모양", "겉모양이 문서의 나이와 손을 말해 준다", "\n".join(b))


def fig_twelve_years():
    """3.3 — 열두 해의 오감."""
    b = []
    x0, y0, w, h = 200, 300, 460, 360
    b.append(f'<rect x="{x0}" y="{y0}" width="{w}" height="{h}" rx="6" fill="#fbfbfa" '
             f'stroke="#d7dcde"/>')
    north = [0.42, 0.44, 0.40, 0.45, 0.43, 0.41, 0.30, 0.22, 0.16, 0.10, 0.06, 0.03]
    south = [0.46, 0.44, 0.47, 0.43, 0.45, 0.46, 0.42, 0.40, 0.38, 0.36, 0.33, 0.31]
    bw = 26
    for k in range(12):
        cx = x0 + 26 + k * ((w - 52) / 11)
        sh = south[k] * h
        nh = north[k] * h
        b.append(f'<rect x="{cx - bw / 2}" y="{y0 + h - sh}" width="{bw}" height="{sh}" '
                 f'fill="{SOUTH}" opacity="0.8"/>')
        b.append(f'<rect x="{cx - bw / 2}" y="{y0 + h - sh - nh}" width="{bw}" height="{nh}" '
                 f'fill="{NORTH}" opacity="0.34"/>')
    b.append(f'<rect x="{x0 - 150}" y="{y0 + 24}" width="14" height="14" fill="{NORTH}" '
             f'opacity="0.34"/>')
    b.append(t(x0 - 130, y0 + 36, "북쪽에서 온 사람", 14, LINE, "700", "start"))
    b.append(f'<rect x="{x0 - 150}" y="{y0 + 54}" width="14" height="14" fill="{SOUTH}" '
             f'opacity="0.8"/>')
    b.append(t(x0 - 130, y0 + 66, "남쪽에서 온 사람", 14, LINE, "700", "start"))
    seventh = x0 + 26 + 6 * ((w - 52) / 11)
    b.append(f'<line x1="{seventh}" y1="{y0 - 34}" x2="{seventh}" y2="{y0 - 8}" '
             f'stroke="{FLAG}" stroke-width="2.6"/>')
    b.append(t(seventh, y0 - 44, "다시 앉은 해", 15, FLAG, "700"))
    b.append(t(x0, y0 + h + 28, "왼쪽이 첫해, 오른쪽이 마지막 해", 15, MUTE, "400", "start"))
    b.append(tailband(830, "한쪽이 먼저 끊긴다"))
    return base("열두 해의 오감", "수가 꺾인 해와 선이 움직인 해가 겹친다", "\n".join(b))


def fig_two_scales():
    """4.2 — 넓이로 세면, 사람으로 세면."""
    b = []

    def scale(cy, tilt, left, right, labels):
        out = [f'<line x1="397" y1="{cy + 18}" x2="397" y2="{cy + 96}" stroke="{LINE}" '
               f'stroke-width="4"/>',
               f'<path d="M357,{cy + 100} L437,{cy + 100}" stroke="{LINE}" stroke-width="4" '
               f'stroke-linecap="round"/>']
        ly, ry = cy + 18 + tilt, cy + 18 - tilt
        out.append(f'<line x1="247" y1="{ly}" x2="547" y2="{ry}" stroke="{LINE}" '
                   f'stroke-width="3" stroke-linecap="round"/>')
        for cx, yy, draw in ((247, ly, left), (547, ry, right)):
            out.append(f'<line x1="{cx}" y1="{yy}" x2="{cx}" y2="{yy - 34}" stroke="{LINE}" '
                       f'stroke-width="1.6"/>')
            out.append(f'<path d="M{cx - 48},{yy - 34} L{cx + 48},{yy - 34}" stroke="{LINE}" '
                       f'stroke-width="3" stroke-linecap="round"/>')
            out.extend(draw(cx, yy - 40))
        out.append(t(247, cy + 132, labels[0], 14, LINE, "700"))
        out.append(t(547, cy + 132, labels[1], 14, LINE, "700"))
        return out

    def land(size):
        def draw(cx, top):
            return [f'<rect x="{cx - size / 2}" y="{top - 34}" width="{size}" height="34" rx="3" '
                    f'fill="{SOUTH}" opacity="0.7"/>']
        return draw

    def folks(n):
        def draw(cx, top):
            out = []
            for i in range(n):
                px = cx + (i - (n - 1) / 2) * 18
                out.append(f'<circle cx="{px}" cy="{top - 26}" r="6.5" fill="{NORTH}" '
                           f'opacity="0.85"/>')
                out.append(f'<path d="M{px - 6},{top - 2} q6,-14 12,0 z" fill="{NORTH}" '
                           f'opacity="0.85"/>')
            return out
        return draw

    b += scale(310, 22, land(96), land(52), ("잔영국이 받은 땅", "잔영국이 넘긴 땅"))
    b.append(f'<line x1="180" y1="504" x2="614" y2="504" stroke="#d7dcde"/>')
    b.append(t(397, 496, "같은 교환, 다른 셈", 16, FLAG, "700"))
    b += scale(590, -22, folks(1), folks(5), ("받은 쪽 사람", "넘긴 쪽 사람"))
    b.append(tailband(830, "척도를 고르는 일이 이미 결론이다"))
    return base("넓이로 세면, 사람으로 세면", "무엇으로 세느냐가 이득과 손해를 정한다", "\n".join(b))


def fig_winter_days():
    """5.2 — 백열두 날의 떠남."""
    b = []
    x0, y0, w = 170, 440, 480
    days = [4, 11, 18, 26, 30, 38, 44, 47, 55, 61, 66, 70, 76, 79,
            84, 86, 88, 89, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100,
            101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112]
    box_x = x0 + (83 / 112) * w
    box_w = (30 / 112) * w
    b.append(f'<rect x="{box_x}" y="{y0 - 132}" width="{box_w}" height="152" rx="6" '
             f'fill="{NORTH}" opacity="0.07"/>')
    b.append(f'<rect x="{box_x}" y="{y0 - 132}" width="{box_w}" height="152" rx="6" '
             f'fill="none" stroke="{NORTH}" stroke-width="1.6" stroke-dasharray="6 5"/>')
    b.append(t(box_x + box_w / 2, y0 - 144, "몰린 자리", 15, NORTH, "700"))
    b.append(f'<rect x="{x0}" y="{y0}" width="{w}" height="9" rx="4" fill="{LINE}" '
             f'opacity="0.75"/>')
    for d in sorted(days):
        dx = x0 + (d / 112) * w
        b.append(f'<line x1="{dx}" y1="{y0 - 84}" x2="{dx}" y2="{y0 - 2}" stroke="{NORTH}" '
                 f'stroke-width="2.4" opacity="0.85"/>')
    b.append(t(x0 - 18, y0 - 52, "떠난 집이", 14, NORTH, "700", "end"))
    b.append(t(x0 - 18, y0 - 30, "적힌 날", 14, NORTH, "700", "end"))
    b.append(t(x0, y0 + 52, "겨울 초입", 14, MUTE, "700", "start"))
    b.append(t(x0 + w, y0 + 52, "겨울 끝", 14, MUTE, "700", "end"))
    marks = [(18, "곡식이 끊긴 날", 96), (76, "새 관리가 온 날", 142), (82, "장이 서지 않은 날", 96)]
    for d, name, dy in marks:
        mx = x0 + (d / 112) * w
        b.append(f'<line x1="{mx}" y1="{y0 + 9}" x2="{mx}" y2="{y0 + dy - 14}" stroke="{FLAG}" '
                 f'stroke-width="1.6"/>')
        b.append(f'<circle cx="{mx}" cy="{y0 + 4}" r="5" fill="{FLAG}"/>')
        b.append(t(mx, y0 + dy, name, 14, FLAG, "700"))
    b.append(t(x0, y0 + 200, "막대 하나가 떠난 집이 적힌 하루다", 15, MUTE, "400", "start"))
    b.append(tailband(830, "몰린 자리 앞을 본다"))
    return base("백열두 날의 떠남", "떠남은 고르게 일어나지 않았다", "\n".join(b))


def fig_three_layers():
    """6.4 — 선 위의 한 점을 볼 때."""
    b = []
    x0, w = 170, 460
    rows = [("두 나라가 정한 것", [(0.0, 0.58), (0.58, 0.42)]),
            ("초소가 세어 적은 것", [(0.06, 0.30), (0.36, 0.20), (0.62, 0.16)]),
            ("도시가 굴러간 방식", [(0.58, 0.42)])]
    y = 300
    cut = 0.58
    for k, (name, spans) in enumerate(rows):
        b.append(t(x0 - 16, y + 34, name, 15, LINE, "700", "end"))
        b.append(f'<rect x="{x0}" y="{y}" width="{w}" height="54" rx="6" fill="#f7f6f2" '
                 f'stroke="#e2e6e8"/>')
        for s, ln in spans:
            color = SOUTH if (s + ln / 2) < cut else NORTH
            b.append(f'<rect x="{x0 + s * w}" y="{y}" width="{ln * w}" height="54" rx="6" '
                     f'fill="{color}" opacity="0.75"/>')
        y += 118
    cx = x0 + cut * w
    b.append(f'<line x1="{cx}" y1="266" x2="{cx}" y2="{y - 40}" stroke="{FLAG}" '
             f'stroke-width="2" stroke-dasharray="7 5"/>')
    b.append(t(cx, 254, "다시 앉은 해", 15, FLAG, "700"))
    b.append(t(x0 - 16, y + 6, "왼쪽이 이른 시기", 14, MUTE, "400", "end"))
    b.append(t(x0 + w, y + 6, "오른쪽이 늦은 시기", 14, MUTE, "400", "end"))
    b.append(tailband(830, "한 사건이 세 자료에 다르게 남는다"))
    return base("선 위의 한 점을 볼 때",
                "위에서 정한 것과 아래에서 굴러간 것은 다른 자료에 남는다", "\n".join(b))


FIGURES = {
    7: fig_three_strands,
    14: fig_treaty_shape,
    20: fig_twelve_years,
    28: fig_two_scales,
    34: fig_winter_days,
    45: fig_three_layers,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild040")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
