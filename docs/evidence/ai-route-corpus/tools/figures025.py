#!/usr/bin/env python3
"""book-025 이미지 페이지 6개의 SVG 생성. figures021.py의 t()/base() 패턴을 따른다.

여섯 도표에서 표기를 고정한다 — 보이지 않는 물은 옅은 파랑, 보이는 물방울은 짙은 파랑, 얼음은 회청색,
올라가는 흐름은 주황 화살표다. 세로축이 있을 때는 언제나 위가 높은 하늘이다.

사용: python3 figures025.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
VAPOR = "#a9c9dd"          # 보이지 않는 물
DROP = "#3f6f8c"           # 보이는 물방울
ICE = "#8fa6b4"            # 얼음
RISE = "#c07f3e"           # 올라가는 흐름
FAR = "#8b95a1"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="rise" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{RISE}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <marker id="drop" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{DROP}"/></marker>
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


def arrow(x1, y1, x2, y2, color=RISE, width=3, marker="rise"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def cloud(cx, cy, w, h, fill="#e9eef1", stroke=LINE, puffy=True):
    """납작한 층 모양과 볼록한 덩이 모양을 한 함수로 그린다."""
    if puffy:
        return (f'<path d="M{cx - w / 2},{cy + h / 2} '
                f'Q{cx - w / 2},{cy - h * 0.1} {cx - w * 0.28},{cy - h * 0.2} '
                f'Q{cx - w * 0.2},{cy - h / 2} {cx},{cy - h * 0.46} '
                f'Q{cx + w * 0.22},{cy - h / 2} {cx + w * 0.3},{cy - h * 0.18} '
                f'Q{cx + w / 2},{cy - h * 0.08} {cx + w / 2},{cy + h / 2} Z" '
                f'fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
    return (f'<rect x="{cx - w / 2}" y="{cy - h / 2}" width="{w}" height="{h}" rx="{h / 2}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="2"/>')


# ── p7 ────────────────────────────────────────────────────────────────
def fig_capacity():
    b = []
    b.append(t(W / 2, 242, "같은 공기를 그대로 두고 온도만 낮추면", 16, MUTED))

    water_top = 540
    water_h = 96
    cols = [(196, 300, "따뜻함"), (396, 210, "식는 중"), (596, 96, "이슬점")]
    for cx, cup_h, label in cols:
        top = water_top - cup_h + water_h - water_h
        cup_top = water_top + water_h - cup_h
        b.append(f'<path d="M{cx - 62},{cup_top} L{cx - 62},{water_top + water_h} '
                 f'L{cx + 62},{water_top + water_h} L{cx + 62},{cup_top}" '
                 f'fill="none" stroke="{LINE}" stroke-width="3"/>')
        b.append(f'<rect x="{cx - 59}" y="{water_top}" width="118" height="{water_h}" fill="{VAPOR}"/>')
        if cup_h > water_h + 6:
            b.append(f'<line x1="{cx}" y1="{cup_top + 8}" x2="{cx}" y2="{water_top - 6}" '
                     f'stroke="{INK}" stroke-width="2"/>')
            b.append(f'<path d="M{cx},{cup_top + 4} L{cx - 6},{cup_top + 16} L{cx + 6},{cup_top + 16} Z" fill="{INK}"/>')
            b.append(f'<path d="M{cx},{water_top - 2} L{cx - 6},{water_top - 14} L{cx + 6},{water_top - 14} Z" fill="{INK}"/>')
            b.append(t(cx + 70, (cup_top + water_top) / 2 + 5, "아직 남은", 13, MUTED, anchor="start"))
            b.append(t(cx + 70, (cup_top + water_top) / 2 + 24, "여유", 13, MUTED, anchor="start"))
        else:
            b.append(t(cx, cup_top - 16, "여유가 없다", 14, "#a24f3d", "700"))
        b.append(t(cx, water_top + water_h + 34, label, 16, INK, "700"))

    b.append(f'<line x1="120" y1="{water_top}" x2="674" y2="{water_top}" stroke="{DROP}" '
             f'stroke-dasharray="6 6"/>')
    b.append(t(126, water_top - 10, "물의 양은 그대로", 13, DROP, "700", anchor="start"))

    b.append(f'<line x1="150" y1="{water_top + water_h + 62}" x2="644" y2="{water_top + water_h + 62}" '
             f'stroke="{LINE}" stroke-width="2"/>')
    b.append(f'<path d="M644,{water_top + water_h + 62} L632,{water_top + water_h + 56} '
             f'L632,{water_top + water_h + 68} Z" fill="{LINE}"/>')
    b.append(t(397, water_top + water_h + 88, "온도가 내려가는 방향", 14, MUTED))

    b.append(note_box(197, 812, 400, "넘치는 것은 물이 늘어서가 아니다", 16))
    return base("물은 그대로인데 그릇이 작아진다",
                "온도가 내려갈 때 여유가 줄어드는 모습을 그린 그림", "".join(b))


# ── p15 ───────────────────────────────────────────────────────────────
def fig_lift():
    b = []
    b.append(t(W / 2, 242, "공기가 위로 가게 되는 네 가지 자리", 16, MUTED))

    cells = [(196, 400, "아래가 데워짐"), (566, 400, "산을 만남"),
             (196, 680, "두 공기가 만남"), (566, 680, "한자리로 모임")]
    hw, hh = 158, 118
    cloud_y = 300
    for i, (cx, cy, label) in enumerate(cells):
        b.append(f'<rect x="{cx - hw}" y="{cy - hh}" width="{2 * hw}" height="{2 * hh}" rx="12" '
                 f'fill="#fbfcfc" stroke="{SOFT}" stroke-width="2"/>')
        ground = cy + hh - 34
        cloud_top = cy - hh + 44
        b.append(cloud(cx, cloud_top, 120, 40))
        if i == 0:
            for k in range(4):
                x = cx - 54 + k * 36
                b.append(f'<line x1="{x}" y1="{ground - 6}" x2="{x + 8}" y2="{ground - 22}" '
                         f'stroke="#d0a24e" stroke-width="2"/>')
            b.append(arrow(cx, ground - 30, cx, cloud_top + 28, width=3))
        elif i == 1:
            b.append(f'<path d="M{cx - hw + 16},{ground} L{cx + 6},{ground - 74} '
                     f'L{cx + hw - 16},{ground}" fill="#e6e0d4" stroke="{INK}" stroke-width="2"/>')
            b.append(arrow(cx - 96, ground - 14, cx - 4, ground - 78, width=3))
        elif i == 2:
            b.append(f'<path d="M{cx - hw + 16},{ground} L{cx + 40},{ground} '
                     f'L{cx - 20},{ground - 62} L{cx - hw + 16},{ground - 62} Z" fill="#dfe8ec"/>')
            b.append(f'<path d="M{cx + 40},{ground} L{cx + hw - 16},{ground} '
                     f'L{cx + hw - 16},{ground - 62} L{cx - 20},{ground - 62} Z" fill="#c3ced3"/>')
            b.append(arrow(cx - 74, ground - 16, cx + 10, ground - 66, width=3))
        else:
            b.append(arrow(cx - hw + 26, ground - 18, cx - 24, ground - 18, width=3))
            b.append(arrow(cx + hw - 26, ground - 18, cx + 24, ground - 18, width=3))
            b.append(arrow(cx, ground - 30, cx, cloud_top + 28, width=3))
        # 지면선은 지형·기단을 다 그린 뒤에 얹어야 가려지지 않는다.
        b.append(f'<line x1="{cx - hw + 16}" y1="{ground}" x2="{cx + hw - 16}" y2="{ground}" '
                 f'stroke="{INK}" stroke-width="2"/>')
        b.append(t(cx, cy + hh - 12, label, 15, INK, "700"))

    b.append(note_box(197, 852, 400, "올라가는 이유는 달라도 식는 방식은 같다", 15))
    b.append(caption(W / 2, 956, ["네 칸에서 구름 밑면의 높이를 같게 그렸다.",
                                  "올라가게 만드는 힘은 달라도 한계에 닿는 조건은 같기 때문이다."]))
    return base("올라가는 길은 넷",
                "공기를 올려 보내는 네 가지 자리를 나란히 그린 그림", "".join(b))


# ── p23 ───────────────────────────────────────────────────────────────
def fig_grid():
    b = []
    b.append(t(W / 2, 242, "왼쪽은 넓게 퍼진 것, 오른쪽은 덩이로 솟은 것", 16, MUTED))

    x0, y0 = 214, 300
    cw, ch = 190, 150
    rows = [("높은 층", ICE, "얼음 알갱이"), ("중간 층", "#b6c4cd", "섞여 있음"), ("낮은 층", DROP, "물방울")]
    for r, (rlabel, color, phase) in enumerate(rows):
        y = y0 + r * ch
        b.append(t(x0 - 16, y + ch / 2 + 5, rlabel, 15, INK, "700", anchor="end"))
        for c in range(2):
            cx = x0 + c * cw + cw / 2
            b.append(f'<rect x="{x0 + c * cw}" y="{y}" width="{cw}" height="{ch}" fill="none" '
                     f'stroke="{SOFT}"/>')
            fill = "#eef3f5" if r < 2 else "#e2eaee"
            shape = (cloud(cx, y + ch / 2, 148, 34 if r == 0 else 44, fill=fill,
                           stroke=color, puffy=False) if c == 0
                     else cloud(cx, y + ch / 2, 128, 84 if r == 2 else 66, fill=fill, stroke=color))
            # 높은 층은 성긴 얼음 알갱이로 이루어져 윤곽이 또렷하지 않다. 점선으로 그 차이를 남긴다.
            if r == 0:
                shape = shape.replace('stroke-width="2"', 'stroke-width="2" stroke-dasharray="5 4"')
            b.append(shape)
        b.append(f'<rect x="{x0 + 2 * cw + 18}" y="{y + 24}" width="112" height="{ch - 48}" rx="8" '
                 f'fill="{color}" opacity="0.28" stroke="{color}"/>')
        b.append(t(x0 + 2 * cw + 74, y + ch / 2 + 5, phase, 13, INK, "600"))

    b.append(t(x0 + cw / 2, y0 - 16, "넓게 퍼짐", 15, INK, "700"))
    b.append(t(x0 + cw + cw / 2, y0 - 16, "덩이로 솟음", 15, INK, "700"))
    b.append(f'<line x1="{x0}" y1="{y0}" x2="{x0}" y2="{y0 + 3 * ch}" stroke="{LINE}" stroke-width="2"/>')
    b.append(f'<line x1="{x0}" y1="{y0 + 3 * ch}" x2="{x0 + 2 * cw}" y2="{y0 + 3 * ch}" stroke="{LINE}" stroke-width="2"/>')

    # 세 층에 걸치는 구름을 나타내는 화살표. 이름표는 맨 아래 칸 안에 두어 격자 밖으로 나가지 않게 한다.
    ax = x0 + cw + cw / 2 + 74
    b.append(f'<line x1="{ax}" y1="{y0 + 3 * ch - 44}" x2="{ax}" y2="{y0 + 26}" '
             f'stroke="{RISE}" stroke-width="3" marker-end="url(#rise)"/>')
    b.append(t(x0 + cw + cw / 2, y0 + 3 * ch - 16, "여러 층에 걸친다", 13, RISE, "700"))

    b.append(note_box(197, 826, 400, "이름은 높이와 모양을 함께 말한다", 16))
    return base("두 축으로 자리를 정한다",
                "높이와 모양 두 축으로 구름을 늘어놓은 그림", "".join(b))


# ── p30 ───────────────────────────────────────────────────────────────
def fig_collide():
    b = []
    b.append(t(W / 2, 242, "구름 속 방울은 크기가 고르지 않다", 16, MUTED))

    b.append(f'<rect x="150" y="272" width="494" height="66" rx="20" fill="#e9eef1" stroke="{LINE}"/>')
    for i, (fx, r) in enumerate([(0.08, 5), (0.18, 7), (0.28, 4), (0.38, 8), (0.48, 5),
                                 (0.58, 6), (0.68, 9), (0.78, 5), (0.88, 7)]):
        b.append(f'<circle cx="{150 + 494 * fx:.0f}" cy="{305 + (i % 3 - 1) * 12}" r="{r}" fill="{DROP}"/>')

    # 세 단계를 나란히 놓는다. 위아래로 늘어놓으면 화살표가 방울에서 멀어져 뜻이 흐려진다.
    stages = [(206, 9, 4, 30, "처음"), (396, 13, 2, 52, "작은 것을 삼킨 뒤"),
              (586, 19, 0, 78, "더 커진 뒤")]
    top, mid = 440, 570
    for cx, br, n, alen, label in stages:
        b.append(f'<rect x="{cx - 84}" y="410" width="168" height="250" rx="12" fill="#fbfcfc" '
                 f'stroke="{SOFT}"/>')
        for k in range(n):
            b.append(f'<circle cx="{cx - 42 + k * 28}" cy="{top}" r="5" fill="{DROP}" opacity="0.75"/>')
        if n:
            b.append(t(cx, top - 22, "작은 방울", 12, MUTED))
        b.append(f'<circle cx="{cx - 18}" cy="{mid}" r="{br}" fill="{DROP}"/>')
        b.append(arrow(cx + 40, mid - alen / 2, cx + 40, mid + alen / 2, color=DROP, width=3, marker="drop"))
        b.append(t(cx, 624, f"지름 {br * 2}", 13, MUTED, "600"))
        b.append(t(cx, 686, label, 14, INK, "700"))

    for x, y, r in [(636, 726, 11), (672, 712, 8), (652, 748, 6)]:
        b.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="none" stroke="{DROP}" stroke-width="2"/>')
    b.append(t(586, 776, "너무 커지면 부서진다", 12, MUTED, anchor="end"))

    b.append(note_box(197, 812, 400, "크기가 고르면 시작되지 않는다", 16))
    b.append(caption(W / 2, 916, ["화살표 길이는 내려오는 속도이고 커질수록 빨라진다.",
                                  "빨라지면 더 많이 따라잡으므로 커지는 속도도 함께 빨라진다."]))
    return base("따라잡으면 합쳐진다",
                "작은 방울이 큰 방울로 자라는 과정을 그린 그림", "".join(b))


# ── p37 ───────────────────────────────────────────────────────────────
def fig_tools():
    b = []
    b.append(t(W / 2, 242, "구름 하나를 두고 네 도구를 함께 쓰면", 16, MUTED))

    ground = 830
    b.append(f'<line x1="120" y1="{ground}" x2="674" y2="{ground}" stroke="{INK}" stroke-width="3"/>')

    ctop, cbot = 470, 600
    b.append(cloud(400, (ctop + cbot) / 2, 300, cbot - ctop, fill="#e2eaee"))
    b.append(f'<line x1="130" y1="{ctop}" x2="664" y2="{ctop}" stroke="{FAR}" stroke-dasharray="4 5"/>')
    b.append(f'<line x1="130" y1="{cbot}" x2="664" y2="{cbot}" stroke="{FAR}" stroke-dasharray="4 5"/>')
    b.append(t(130, ctop - 8, "꼭대기", 12, FAR, "600", anchor="start"))
    b.append(t(130, cbot + 18, "밑면", 12, FAR, "600", anchor="start"))

    b.append(f'<rect x="352" y="292" width="96" height="42" rx="8" fill="#eef2f4" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(400, 282, "위에서 내려다보는 장치", 13, INK, "700"))
    b.append(f'<path d="M366,334 L300,{ctop - 6} L500,{ctop - 6} L434,334 Z" fill="{RISE}" opacity="0.13"/>')
    b.append(t(516, 400, "넓이와 꼭대기", 13, MUTED, anchor="start"))

    b.append(f'<path d="M158,{ground} L142,{ground - 34} L190,{ground - 34} Z" fill="#eef2f4" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(166, ground + 24, "되돌아오는 신호를 쓰는 장치", 13, INK, "700", anchor="start"))
    b.append(arrow(178, ground - 40, 318, cbot + 4, color=DROP, width=2, marker="drop"))
    b.append(f'<line x1="330" y1="{cbot + 14}" x2="190" y2="{ground - 34}" stroke="{DROP}" stroke-width="2" stroke-dasharray="5 5"/>')
    b.append(t(300, 690, "구름 속의 물", 13, DROP, "700", anchor="start"))

    b.append(f'<rect x="374" y="{ground - 38}" width="52" height="38" rx="6" fill="#eef2f4" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(400, ground + 24, "지상 장치", 13, INK, "700"))
    b.append(t(400, ground + 44, "기온과 이슬점", 12, MUTED))

    b.append(f'<line x1="600" y1="{ground - 10}" x2="600" y2="280" stroke="{LINE}" stroke-width="2" stroke-dasharray="6 6"/>')
    b.append(f'<circle cx="600" cy="272" r="9" fill="#eef2f4" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(614, 320, "올려 보내는 장치", 13, INK, "700", anchor="start"))
    b.append(t(614, 340, "높이에 따른 온도", 12, MUTED, anchor="start"))

    b.append(note_box(197, 906, 400, "넷이 보는 것이 서로 다르다", 16))
    return base("네 도구가 보는 자리",
                "네 도구가 각각 어디를 보는지 나타낸 그림", "".join(b))


# ── p44 ───────────────────────────────────────────────────────────────
def fig_intensity():
    b = []
    b.append(t(W / 2, 242, "같은 양이 시간에 따라 다르게 내리면", 16, MUTED))

    x0, x1 = 300, 660
    axis_h = 190
    # 두 색칠 넓이를 같게 맞춘다. 평평한 쪽은 낮고 넓게, 쏟아지는 쪽은 좁고 높게 그린다.
    flat_h, spike_w = 26, 52
    spike_h = flat_h * (x1 - x0 - 4) / spike_w

    for ybase, label, kind, puffy, ch in [(520, "오래 나누어 내림", "flat", False, 40),
                                          (856, "짧게 쏟아짐", "spike", True, 92)]:
        b.append(t(x0 - 24, ybase - axis_h - 26, label, 16, INK, "700", anchor="start"))
        b.append(f'<line x1="{x0}" y1="{ybase}" x2="{x1}" y2="{ybase}" stroke="{LINE}" stroke-width="2"/>')
        b.append(f'<line x1="{x0}" y1="{ybase}" x2="{x0}" y2="{ybase - axis_h}" stroke="{LINE}" stroke-width="2"/>')
        b.append(t(x0 - 10, ybase - axis_h + 4, "많음", 12, MUTED, anchor="end"))
        b.append(t(x0 - 10, ybase - 4, "없음", 12, MUTED, anchor="end"))
        b.append(t((x0 + x1) / 2, ybase + 26, "시간", 12, MUTED))
        if kind == "flat":
            b.append(f'<rect x="{x0 + 2}" y="{ybase - flat_h}" width="{x1 - x0 - 4}" '
                     f'height="{flat_h}" fill="{DROP}" opacity="0.7"/>')
        else:
            b.append(f'<rect x="{(x0 + x1) / 2 - spike_w / 2}" y="{ybase - spike_h:.0f}" '
                     f'width="{spike_w}" height="{spike_h:.0f}" fill="{DROP}" opacity="0.7"/>')
        b.append(t(x1 + 12, ybase - axis_h / 2, "내린 양은", 13, MUTED, "700", anchor="start"))
        b.append(t(x1 + 12, ybase - axis_h / 2 + 20, "두 칸이 같다", 13, MUTED, "700", anchor="start"))
        b.append(cloud(190, ybase - 96, 150 if not puffy else 130, ch, fill="#e2eaee", puffy=puffy))
        b.append(t(190, ybase - 30, "넓게 퍼진 구름" if not puffy else "덩이로 솟은 구름", 13, MUTED))

    b.append(note_box(197, 908, 400, "양이 같아도 겪는 일은 다르다", 16))
    b.append(caption(W / 2, 1012, ["색칠한 두 넓이가 같도록 그렸다.",
                                   "확률·양·세기는 서로 다른 값이라 하나로 뭉치면 읽을 수 없다."]))
    return base("같은 양, 다른 일",
                "같은 강수량이 전혀 다른 모습이 되는 것을 나타낸 그림", "".join(b))


FIGURES = {7: fig_capacity, 15: fig_lift, 23: fig_grid,
           30: fig_collide, 37: fig_tools, 44: fig_intensity}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}")


if __name__ == "__main__":
    main()
