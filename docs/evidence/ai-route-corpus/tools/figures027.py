#!/usr/bin/env python3
"""book-027 이미지 페이지 6개의 SVG 생성. figures021.py의 t()/base() 패턴을 따른다.

이 책의 도표는 힘과 움직임을 갈라 보이는 것이 목적이다. 그래서 여섯 도표에서 표기를 고정한다 —
당기는 힘은 주황색 실선 화살표, 그 밖의 힘은 회색 화살표, 물체가 실제로 옮겨 간 거리는 이중선이다.
힘 화살표의 길이는 언제나 힘의 세기를 뜻하고 움직임을 뜻하지 않는다.

사용: python3 figures027.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
BODY = "#3f6f8c"           # 물체
MARK = "#b4703a"           # 당기는 힘
FAR = "#8b95a1"            # 배경·보조


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
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


def arrow(x1, y1, x2, y2, color=MARK, width=3, marker="mark"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def move_bar(x, y, length):
    """실제로 옮겨 간 거리를 나타내는 이중선. 힘 화살표와 혼동되지 않게 화살촉이 없다."""
    return (f'<line x1="{x}" y1="{y}" x2="{x + length}" y2="{y}" stroke="{INK}" stroke-width="2"/>'
            f'<line x1="{x}" y1="{y - 5}" x2="{x}" y2="{y + 5}" stroke="{INK}" stroke-width="2"/>'
            f'<line x1="{x + length}" y1="{y - 5}" x2="{x + length}" y2="{y + 5}" stroke="{INK}" stroke-width="2"/>')


# ── p8 ────────────────────────────────────────────────────────────────
def fig_pair():
    b = []
    b.append(t(W / 2, 240, "지구와 쇠공처럼 크기가 크게 다른 두 물체", 16, MUTED))

    b.append(t(150, 300, "힘", 18, INK, "700", anchor="start"))
    big, small = 268, 560
    b.append(f'<circle cx="{big}" cy="380" r="76" fill="#eef2f4" stroke="{BODY}" stroke-width="2"/>')
    b.append(t(big, 386, "큰 쪽", 17, INK, "600"))
    b.append(f'<circle cx="{small}" cy="380" r="24" fill="#eef2f4" stroke="{BODY}" stroke-width="2"/>')
    b.append(t(small, 434, "작은 쪽", 15, INK, "600"))
    b.append(arrow(big + 88, 380, big + 152, 380))
    b.append(arrow(small - 36, 380, small - 100, 380))
    b.append(t(W / 2, 344, "같은 세기", 16, MARK, "700"))

    b.append(f'<line x1="105" y1="500" x2="689" y2="500" stroke="{SOFT}"/>')

    b.append(t(150, 560, "움직임", 18, INK, "700", anchor="start"))
    b.append(f'<circle cx="{big}" cy="650" r="76" fill="#f4f6f7" stroke="{FAR}" stroke-width="2"/>')
    b.append(f'<circle cx="{small}" cy="650" r="24" fill="#f4f6f7" stroke="{FAR}" stroke-width="2"/>')
    b.append(move_bar(big - 4, 762, 8))
    b.append(t(big, 800, "거의 그대로다", 15, INK, "600"))
    b.append(move_bar(small - 74, 762, 148))
    b.append(t(small, 800, "많이 움직인다", 15, INK, "600"))

    b.append(note_box(197, 856, 400, "힘이 같아도 결과는 같지 않다"))
    b.append(caption(W / 2, 962, ["힘 화살표의 길이는 세기이고 아래 이중선의 길이는 옮겨 간 거리다.",
                                  "움직임이 다른 것은 물질의 양이 다르기 때문이다."]))
    return base("힘은 같고 움직임은 다르다",
                "서로 당기는 두 물체가 받는 힘과 그 결과를 따로 그린 그림", "".join(b))


# ── p15 ───────────────────────────────────────────────────────────────
def fig_distance():
    b = []
    b.append(t(W / 2, 246, "왼쪽 큰 원에서 거리를 한 칸씩 늘려 갈 때", 16, MUTED))

    y = 330
    x0 = 160
    step = 168
    b.append(f'<line x1="{x0}" y1="{y}" x2="{x0 + 3 * step + 40}" y2="{y}" stroke="{SOFT}" stroke-width="2"/>')
    b.append(f'<circle cx="{x0}" cy="{y}" r="34" fill="#eef2f4" stroke="{BODY}" stroke-width="2"/>')

    labels = ["거리 한 칸", "두 칸", "세 칸"]
    # 힘의 세기는 화살표 길이로만 나타낸다. 굵기로 나타내면 화살촉이 함께 커져 세모 덩어리로 보인다.
    lengths = [144.0, 36.0, 16.0]
    values = ["1", "1/4", "1/9"]
    for i in range(3):
        cx = x0 + step * (i + 1)
        b.append(f'<circle cx="{cx}" cy="{y}" r="13" fill="#eef2f4" stroke="{BODY}" stroke-width="2"/>')
        b.append(t(cx, y - 46, labels[i], 15, INK, "600"))
        b.append(arrow(cx - 18, y + 46, cx - 18 - lengths[i], y + 46, width=3))
        b.append(t(cx - 18 - lengths[i] / 2, y + 84, values[i], 16, MARK, "700"))

    b.append(f'<line x1="105" y1="474" x2="689" y2="474" stroke="{SOFT}"/>')
    b.append(t(W / 2, 516, "같은 값을 막대 높이로 다시 그리면", 16, MUTED))

    ybase = 760
    heights = [200, 50, 22]
    for i in range(3):
        cx = x0 + step * (i + 1)
        b.append(f'<rect x="{cx - 34}" y="{ybase - heights[i]}" width="68" height="{heights[i]}" '
                 f'fill="#e8d9c4" stroke="{MARK}" stroke-width="2"/>')
        b.append(t(cx, ybase + 26, values[i], 16, MARK, "700"))
    b.append(f'<line x1="{x0 + step - 60}" y1="{ybase}" x2="{x0 + 3 * step + 60}" y2="{ybase}" stroke="{LINE}"/>')
    b.append(f'<line x1="{x0 + step - 60}" y1="{ybase - 100}" x2="{x0 + 3 * step + 60}" y2="{ybase - 100}" '
             f'stroke="{FAR}" stroke-dasharray="5 6"/>')
    b.append(t(x0 + 3 * step + 66, ybase - 95, "절반 자리", 14, FAR, "400", anchor="start"))

    b.append(note_box(197, 828, 400, "절반이 아니라 사분의 일이다"))
    b.append(caption(W / 2, 934, ["두 칸으로 벌리면 힘은 절반 자리를 한참 지나 사분의 일까지 내려간다.",
                                  "점선은 절반이었다면 있었을 높이다."]))
    return base("두 배 멀어지면 사분의 일",
                "거리를 늘릴 때 당김이 줄어드는 정도를 나타낸 그림", "".join(b))


# ── p24 ───────────────────────────────────────────────────────────────
def fig_air():
    b = []
    b.append(t(W / 2, 246, "같은 높이에서 함께 놓은 쇠구슬과 깃털", 16, MUTED))

    for side, (cx, label) in enumerate([(258, "공기 있음"), (536, "공기 뺌")]):
        top, bot = 300, 760
        b.append(f'<rect x="{cx - 96}" y="{top}" width="192" height="{bot - top}" rx="16" '
                 f'fill="#fbfcfc" stroke="{LINE}" stroke-width="2"/>')
        b.append(t(cx, top - 22, label, 18, INK, "700"))
        b.append(f'<line x1="{cx - 96}" y1="{top + 40}" x2="{cx + 96}" y2="{top + 40}" '
                 f'stroke="{SOFT}" stroke-dasharray="5 6"/>')
        b.append(t(cx, top + 28, "놓은 자리", 13, FAR))
        # 출발 자리에 흐린 윤곽을 남겨 두 통에서 같은 높이에서 놓았음을 보인다.
        b.append(f'<circle cx="{cx - 44}" cy="{top + 62}" r="16" fill="none" stroke="{SOFT}" '
                 f'stroke-width="2" stroke-dasharray="4 4"/>')
        b.append(f'<rect x="{cx + 20}" y="{top + 54}" width="52" height="15" rx="4" fill="none" '
                 f'stroke="{SOFT}" stroke-width="2" stroke-dasharray="4 4"/>')
        if side == 0:
            ball_y, feather_y = bot - 52, 520
        else:
            ball_y = feather_y = bot - 52
        b.append(f'<circle cx="{cx - 44}" cy="{ball_y}" r="16" fill="{BODY}"/>')
        b.append(f'<rect x="{cx + 20}" y="{feather_y - 8}" width="52" height="15" rx="4" '
                 f'fill="#eef2f4" stroke="{BODY}" stroke-width="2"/>')
        if side == 0:
            for k in range(3):
                x = cx + 28 + k * 18
                b.append(arrow(x, feather_y + 46, x, feather_y + 18, color=LINE, width=2, marker="gray"))
            b.append(t(cx + 46, feather_y + 74, "공기가 민다", 14, MUTED))
        b.append(t(cx, bot + 34, "따로 닿는다" if side == 0 else "함께 닿는다", 17, INK, "600"))

    b.append(note_box(197, 828, 400, "차이를 만든 것은 당김이 아니다"))
    b.append(caption(W / 2, 934, ["두 통에서 다른 것은 공기뿐이고 놓은 높이와 물체는 같다.",
                                  "공기를 빼면 무게가 크게 다른 둘이 나란히 내려온다."]))
    return base("같은 높이, 다른 통",
                "공기가 있을 때와 없을 때의 낙하를 나란히 비교한 그림", "".join(b))


# ── p32 ───────────────────────────────────────────────────────────────
def fig_orbit():
    import math
    b = []
    b.append(t(W / 2, 244, "높은 탑 위에서 옆으로 던지는 세기를 키워 갈 때", 16, MUTED))
    b.append(caption(W / 2, 300, ["회색 경로는 지면에 닿는다. 주황 경로는 닿지 않고 한 바퀴 돌아 제자리로 온다."]))

    cx, cy, r = 397, 700, 168
    tower_h = 72
    orb = r + tower_h
    tx, ty = cx, cy - orb

    b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#eef2f4" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(cx, cy + 8, "지구", 18, MUTED, "600"))
    b.append(f'<rect x="{tx - 8}" y="{ty}" width="16" height="{tower_h}" fill="#dfe4e6" stroke="{LINE}"/>')
    b.append(t(tx - 16, ty + 4, "탑", 15, MUTED, "600", anchor="end"))

    # 지면에 닿는 세 경로. 반지름을 orb에서 r까지 단조 감소시키며 각도를 벌려 그린다.
    # 베지에로 그리면 끝점만 지면에 맞춰도 도중에 지면 안으로 파고들 수 있어, 중심까지의 거리를
    # 직접 다뤄 언제나 r 이상이 되게 한다. f=0에서 반지름 변화가 0이라 탑에서 수평으로 나간다.
    steps = 48
    for theta, label in [(0.40, "조금 세게"), (0.88, "더 세게"), (1.52, "훨씬 세게")]:
        points = []
        for step in range(steps + 1):
            f = step / steps
            rad = orb - (orb - r) * f ** 2
            ang = theta * f
            points.append(f"{cx + rad * math.sin(ang):.1f},{cy - rad * math.cos(ang):.1f}")
        ex = cx + r * math.sin(theta)
        ey = cy - r * math.cos(theta)
        b.append(f'<polyline points="{" ".join(points)}" fill="none" stroke="{FAR}" stroke-width="2"/>')
        # 라벨은 주황 궤도 바깥에 두고 닿은 자리와 가는 점선으로 잇는다. 지면 위에 얹으면 읽히지 않는다.
        lx = cx + (orb + 18) * math.sin(theta)
        ly = cy - (orb + 18) * math.cos(theta)
        b.append(f'<line x1="{ex:.1f}" y1="{ey:.1f}" x2="{lx:.1f}" y2="{ly:.1f}" stroke="{SOFT}" '
                 f'stroke-dasharray="3 4"/>')
        b.append(t(lx + 8, ly + 5, label, 13, FAR, "600", anchor="start"))

    # 되돌아오는 경로. 지면과 같은 간격을 유지한다.
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{orb}" fill="none" stroke="{MARK}" stroke-width="3"/>')
    b.append(t(cx, ty - 22, "충분히 세게", 16, MARK, "700"))

    # 궤도 위 한 점에서 중심을 향하는 화살표. 회색 경로가 없는 왼쪽에 둔다.
    # 화살촉은 지면 바로 위에서 멈춘다. 반지름 r 안으로 들어가면 화살표가 지구를 뚫고 들어간다.
    a = math.radians(214)
    px, py = cx + orb * math.sin(a), cy - orb * math.cos(a)
    b.append(arrow(px, py, cx + (r + 10) * math.sin(a), cy - (r + 10) * math.cos(a), width=3))
    b.append(t(px - 12, py + 6, "이 순간에도 떨어지고 있다", 14, MARK, "700", anchor="end"))

    b.append(note_box(197, 986, 400, "닿지 않을 뿐 떨어지기는 마찬가지다", 16))
    return base("세게 던질수록 멀리, 더 세게 던지면 돌아온다",
                "던지는 세기를 키울 때 길이 달라지는 그림", "".join(b))


# ── p41 ───────────────────────────────────────────────────────────────
def fig_scale():
    import math
    b = []
    b.append(t(W / 2, 244, "같은 저울, 같은 공, 서로 다른 두 상황", 16, MUTED))

    def dial(cx, cy, deg):
        """눈금판. 왼쪽 끝이 0이고 오른쪽으로 갈수록 큰 값이다."""
        out = [f'<circle cx="{cx}" cy="{cy}" r="27" fill="#ffffff" stroke="{LINE}"/>']
        out.append(f'<path d="M{cx - 20},{cy} A20,20 0 0,1 {cx + 20},{cy}" fill="none" '
                   f'stroke="{SOFT}" stroke-width="3"/>')
        out.append(t(cx - 24, cy + 16, "0", 11, FAR))
        a = math.radians(deg)
        out.append(f'<line x1="{cx}" y1="{cy}" x2="{cx + 20 * math.cos(a):.1f}" '
                   f'y2="{cy - 20 * math.sin(a):.1f}" stroke="{MARK}" stroke-width="3"/>')
        out.append(f'<circle cx="{cx}" cy="{cy}" r="3" fill="{MARK}"/>')
        return "".join(out)

    def spring(cx, top, bottom):
        pts = []
        n = 8
        for i in range(n + 1):
            pts.append(f"{cx - 24 + (i % 2) * 48},{top + (bottom - top) / n * i:.1f}")
        return f'<polyline points="{" ".join(pts)}" fill="none" stroke="{LINE}" stroke-width="2"/>'

    # 왼쪽 — 바닥에 놓인 저울
    lx = 250
    b.append(f'<circle cx="{lx}" cy="350" r="26" fill="{BODY}"/>')
    b.append(spring(lx, 376, 448))
    b.append(f'<rect x="{lx - 84}" y="448" width="168" height="78" rx="10" fill="#eef2f4" '
             f'stroke="{LINE}" stroke-width="2"/>')
    b.append(dial(lx, 490, 62))
    b.append(f'<line x1="{lx - 122}" y1="534" x2="{lx + 122}" y2="534" stroke="{INK}" stroke-width="3"/>')
    b.append(arrow(lx - 104, 532, lx - 104, 480, color=LINE, width=3, marker="gray"))
    b.append(t(lx, 588, "눈금 있음", 19, INK, "700"))
    b.append(t(lx, 622, "받침이 밀어 올린다", 14, MUTED))

    # 오른쪽 — 저울과 공이 함께 내려가는 중
    rx = 544
    # 저울과 공은 질량이 달라 당겨지는 세기도 다르다. 같은 것은 떨어지는 빠르기이므로 그렇게 적는다.
    b.append(t(rx, 300, "같은 빠르기로 떨어진다", 15, MARK, "700"))
    b.append(f'<circle cx="{rx}" cy="350" r="26" fill="{BODY}"/>')
    b.append(spring(rx, 376, 460))
    b.append(f'<rect x="{rx - 84}" y="460" width="168" height="78" rx="10" fill="#eef2f4" '
             f'stroke="{LINE}" stroke-width="2"/>')
    b.append(dial(rx, 502, 180))
    for dx in (-112, 112):
        b.append(arrow(rx + dx, 372, rx + dx, 462))
    b.append(t(rx, 588, "눈금 0", 19, INK, "700"))
    b.append(t(rx, 622, "저울과 공이 함께 내려간다", 14, MUTED))

    b.append(f'<line x1="397" y1="286" x2="397" y2="646" stroke="{SOFT}" stroke-dasharray="6 7"/>')

    b.append(note_box(147, 704, 500, "저울이 재는 것은 받쳐 주는 힘이다"))
    b.append(caption(W / 2, 810, ["두 상황에서 공에 걸린 당김은 같고 달라진 것은 받침뿐이다.",
                                  "오른쪽에서 눈금이 0인 것은 밀어 올릴 필요가 없어졌기 때문이다."]))
    return base("눈금이 0이어도 당김은 그대로다",
                "같은 저울이 두 상황에서 다른 눈금을 내는 그림", "".join(b))


# ── p47 ───────────────────────────────────────────────────────────────
def fig_float():
    b = []
    b.append(t(W / 2, 244, "궤도를 도는 정거장과 그 안의 사람·물건", 16, MUTED))

    cx, cy, r, orb = 300, 646, 104, 240
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#eef2f4" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(cx, cy + 6, "지구", 18, MUTED, "600"))
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{orb}" fill="none" stroke="{FAR}" stroke-dasharray="7 8"/>')

    sy = cy - orb
    # 정거장·사람·공은 질량이 크게 달라 당겨지는 세기도 다르다. 화살표 길이가 같은 것은
    # 떨어지는 빠르기가 같다는 뜻이므로 그렇게 적는다.
    b.append(t(cx, sy - 62, "셋 다 같은 빠르기로 떨어진다", 16, MARK, "700"))
    b.append(f'<rect x="{cx - 88}" y="{sy - 38}" width="176" height="76" rx="12" '
             f'fill="#fbfcfc" stroke="{BODY}" stroke-width="2"/>')
    b.append(t(cx - 98, sy + 4, "정거장", 15, INK, "600", anchor="end"))
    b.append(f'<circle cx="{cx - 40}" cy="{sy - 12}" r="10" fill="{BODY}"/>')
    b.append(f'<rect x="{cx - 49}" y="{sy + 2}" width="18" height="22" rx="4" fill="{BODY}"/>')
    b.append(f'<circle cx="{cx + 42}" cy="{sy}" r="14" fill="#ffffff" stroke="{BODY}" stroke-width="2"/>')
    b.append(t(cx - 40, sy + 58, "사람", 12, MUTED))
    b.append(t(cx + 42, sy + 58, "공", 12, MUTED))

    for dx in (-62, 0, 62):
        b.append(arrow(cx + dx, sy + 74, cx + dx, sy + 134))

    bx, by, bw = 548, 306, 206
    b.append(f'<rect x="{bx}" y="{by}" width="{bw}" height="96" rx="12" fill="#fbfcfc" stroke="{LINE}"/>')
    b.append(t(bx + bw / 2, by + 34, "보이는 것", 15, MUTED, "700"))
    b.append(t(bx + bw / 2, by + 68, "물건이 뜬다", 18, INK, "700"))
    b.append(arrow(bx + 38, by + 112, bx + 38, by + 162, color=LINE, width=2, marker="gray"))
    b.append(t(bx + 58, by + 144, "같은 장면, 다른 해석", 13, MUTED, anchor="start"))
    b.append(f'<rect x="{bx}" y="{by + 178}" width="{bw}" height="96" rx="12" '
             f'fill="#f7f4ec" stroke="#c5a866"/>')
    b.append(t(bx + bw / 2, by + 212, "실제", 15, "#7a6636", "700"))
    b.append(t(bx + bw / 2, by + 246, "함께 떨어지고 있다", 18, "#51462c", "700"))

    b.append(note_box(197, 934, 400, "당김이 없으면 궤도도 없다"))
    b.append(caption(W / 2, 1038, ["세 화살표의 길이가 같다는 것이 이 그림의 요점이다. 셋이 얻는 속도의 변화가 같다.",
                                   "서로에 대해 움직이지 않으니 안에서는 떠 있는 것으로 보인다."]))
    return base("떠 있음은 없음이 아니다",
                "같은 자리에서 두 가지로 읽히는 상황을 정리한 그림", "".join(b))


FIGURES = {8: fig_pair, 15: fig_distance, 24: fig_air,
           32: fig_orbit, 41: fig_scale, 47: fig_float}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}")


if __name__ == "__main__":
    main()
