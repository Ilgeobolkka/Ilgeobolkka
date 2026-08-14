#!/usr/bin/env python3
"""book-023 이미지 페이지 6개의 SVG 생성. figures021.py의 t()/base() 패턴을 따른다.

이 책의 도표는 대부분 「무엇이 걸러지고 무엇이 남는가」를 보인다. 여섯 도표에서 표기를 고정한다 —
산소가 닿는 자리는 밝은 색, 닿지 않는 자리는 어두운 색, 계에서 빠져나간 몫은 회색 화살표,
확인되지 않았거나 죽은 것은 점선 윤곽이다.

사용: python3 figures023.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
LIVE = "#4a7c59"           # 생물·살아 있는 몫
AIR = "#3f6f8c"            # 공기 쪽
SOIL = "#8a6a44"           # 흙 쪽
LOSS = "#a24f3d"           # 빠져나간 몫
FAR = "#8b95a1"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LIVE}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <marker id="loss" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LOSS}"/></marker>
  <marker id="soil" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{SOIL}"/></marker>
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


def arrow(x1, y1, x2, y2, color=LIVE, width=3, marker="mark"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def shape(kind, cx, cy, color, dashed=False):
    """네 가지 모양으로 서로 다른 미생물 무리를 나타낸다. 점선은 죽은 것이다."""
    dash = ' stroke-dasharray="3 3"' if dashed else ""
    fill = "none" if dashed else color
    stroke = color
    if kind == "circle":
        return f'<circle cx="{cx}" cy="{cy}" r="7" fill="{fill}" stroke="{stroke}" stroke-width="2"{dash}/>'
    if kind == "rod":
        return (f'<rect x="{cx - 9}" y="{cy - 4}" width="18" height="8" rx="4" fill="{fill}" '
                f'stroke="{stroke}" stroke-width="2"{dash}/>')
    if kind == "tri":
        return (f'<path d="M{cx},{cy - 8} L{cx + 8},{cy + 6} L{cx - 8},{cy + 6} Z" fill="{fill}" '
                f'stroke="{stroke}" stroke-width="2"{dash}/>')
    return (f'<path d="M{cx},{cy - 9} L{cx + 3},{cy - 2} L{cx + 9},{cy - 2} L{cx + 4},{cy + 3} '
            f'L{cx + 6},{cy + 9} L{cx},{cy + 5} L{cx - 6},{cy + 9} L{cx - 4},{cy + 3} '
            f'L{cx - 9},{cy - 2} L{cx - 3},{cy - 2} Z" fill="{fill}" stroke="{stroke}" '
            f'stroke-width="1.5"{dash}/>')


KINDS = ["circle", "rod", "tri", "star"]


# ── p7 ────────────────────────────────────────────────────────────────
def fig_methods():
    b = []
    b.append(t(W / 2, 240, "같은 시료에 세 가지 방법을 각각 써 보면", 16, MUTED))

    # 원래 있던 것들
    b.append(f'<ellipse cx="196" cy="400" rx="94" ry="82" fill="#f2f5f6" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(196, 300, "실제로 있던 것들", 16, INK, "700"))
    spots = [(158, 358), (216, 350), (188, 388), (240, 392), (150, 412),
             (204, 424), (168, 444), (232, 436), (126, 386), (196, 462)]
    for i, (x, y) in enumerate(spots):
        b.append(shape(KINDS[i % 4], x, y, LIVE))

    results = [
        ("길러 보기", [("circle", 0), ("circle", 0), ("circle", 0)], "자라는 것만 남는다", False),
        ("그대로 세기", [(k, 0) for k in KINDS] + [("circle", 0), ("rod", 0)], "수는 알아도 종류는 흐리다", False),
        ("유전물질 읽기", [(k, 0) for k in KINDS] + [("tri", 1), ("star", 1)], "죽은 것도 함께 잡힌다", True),
    ]
    bx = 402
    by = 276
    for name, items, note, has_dead in results:
        b.append(arrow(298, 400, bx - 12, by + 44, color=LINE, width=2, marker="gray"))
        b.append(f'<rect x="{bx}" y="{by}" width="266" height="88" rx="12" fill="#fbfcfc" '
                 f'stroke="{LINE}" stroke-width="2"/>')
        b.append(t(bx + 14, by + 26, name, 16, INK, "700", anchor="start"))
        color = FAR if name == "그대로 세기" else LIVE
        for i, (kind, dead) in enumerate(items):
            b.append(shape(kind, bx + 30 + i * 34, by + 60, color, dashed=bool(dead)))
        b.append(t(bx + 4, by + 108, note, 14, MUTED, anchor="start"))
        by += 142

    b.append(note_box(197, 762, 400, "세 방법을 겹쳐야 원래 모습에 가까워진다", 16))
    b.append(caption(W / 2, 866, ["모양은 서로 다른 무리를 뜻하고 점선은 이미 죽은 것을 뜻한다.",
                                  "회색으로 칠한 칸은 종류를 가리지 못한 경우다."]))
    return base("방법마다 걸러지는 것이 다르다",
                "세 가지 조사 방법이 각각 걸러 내는 것을 나타낸 그림", "".join(b))


# ── p15 ───────────────────────────────────────────────────────────────
def fig_oxygen():
    b = []
    b.append(t(W / 2, 240, "흙 알갱이 하나를 크게 확대하면", 16, MUTED))

    cx, cy, r = 392, 458, 150
    ring = 34

    b.append(f'<rect x="222" y="288" width="340" height="340" rx="16" fill="#f6f2ec"/>')
    for gx, gy, gr in [(240, 308, 20), (546, 316, 24), (234, 606, 22), (544, 600, 18)]:
        b.append(f'<circle cx="{gx}" cy="{gy}" r="{gr}" fill="#e3d9c9" stroke="{SOIL}" stroke-width="1.5"/>')
    b.append(t(392, 654, "알갱이 사이 틈", 14, MUTED))

    # 바깥 밝은 띠와 안쪽 어두운 영역. 띠 위에 글씨를 얹으면 읽히지 않아 이름은 바깥으로 뺀다.
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#f3efe4" stroke="{SOIL}" stroke-width="2"/>')
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{r - ring}" fill="#4b4238"/>')
    b.append(t(cx, cy + 6, "산소가 닿지 않는 자리", 15, "#e6ded1", "700"))

    b.append(f'<line x1="200" y1="358" x2="{cx - (r - ring / 2) * 0.72:.0f}" '
             f'y2="{cy - (r - ring / 2) * 0.69:.0f}" stroke="{SOIL}" stroke-dasharray="3 4"/>')
    b.append(t(194, 362, "산소가 닿는 자리", 14, "#5c4f3c", "700", anchor="end"))

    # 두 산물 상자를 아래에 나란히 두어 화살표가 알갱이를 가로지르지 않게 한다.
    b.append(arrow(cx - 76, cy + r - 26, 268, 700, color=LINE, width=2, marker="gray"))
    b.append(arrow(cx + 76, cy + r - 26, 522, 700, color=LINE, width=2, marker="gray"))

    b.append(f'<rect x="150" y="712" width="236" height="62" rx="12" fill="#fbfcfc" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(268, 750, "이산화탄소와 물", 16, INK, "600"))
    b.append(f'<rect x="404" y="712" width="236" height="62" rx="12" fill="#3d3730" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(522, 750, "여러 중간 산물", 16, "#e6ded1", "600"))
    b.append(t(395, 700, "같은 재료, 다른 산물", 14, MUTED, "700"))

    b.append(f'<line x1="180" y1="828" x2="280" y2="828" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<line x1="180" y1="822" x2="180" y2="834" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<line x1="280" y1="822" x2="280" y2="834" stroke="{INK}" stroke-width="2"/>')
    b.append(t(292, 833, "실제로는 손가락 한 마디보다 작다", 14, MUTED, anchor="start"))

    b.append(note_box(197, 880, 400, "같은 흙 한 줌 안에서 함께 돈다", 16))
    return base("손가락 한 마디 안에서 갈린다",
                "흙 알갱이 둘레에서 조건이 갈리는 모습을 그린 그림", "".join(b))


# ── p21 ───────────────────────────────────────────────────────────────
def fig_nitrogen():
    b = []
    b.append(t(W / 2, 238, "질소가 공기와 흙 사이를 오가는 고리", 16, MUTED))

    b.append(f'<rect x="120" y="272" width="554" height="52" rx="10" fill="#e6eef3" stroke="{AIR}"/>')
    b.append(t(397, 305, "공기 중의 질소", 17, AIR, "700"))

    # 두 문은 공기와 흙 사이의 빈 띠에만 그린다. 흙 안까지 끌고 들어가면 고리 상자를 가로지른다.
    b.append(arrow(544, 336, 544, 420, color=LIVE, width=4))
    b.append(shape("rod", 508, 368, LIVE))
    b.append(t(566, 372, "들어오는 문", 15, LIVE, "700", anchor="start"))
    b.append(t(566, 396, "식물이 쓸 수 있게 된다", 13, MUTED, anchor="start"))

    b.append(arrow(250, 420, 250, 336, color=LIVE, width=4))
    b.append(shape("rod", 286, 368, LIVE))
    b.append(t(228, 372, "나가는 문", 15, LIVE, "700", anchor="end"))
    b.append(t(228, 396, "공기 중 형태로 돌아간다", 13, MUTED, anchor="end"))

    # 흙 영역과 그 안의 고리
    b.append(f'<rect x="120" y="430" width="554" height="392" rx="12" fill="#efe6d8" stroke="{SOIL}"/>')
    b.append(t(136, 458, "흙", 16, SOIL, "700", anchor="start"))

    boxes = {"풀려난 형태": (250, 512), "식물이 받는 형태": (544, 512),
             "식물 몸 안": (544, 744), "죽은 몸과 배설물": (250, 744)}
    for label, (x, y) in boxes.items():
        b.append(f'<rect x="{x - 100}" y="{y - 27}" width="200" height="54" rx="12" fill="#fbfcfc" '
                 f'stroke="{LINE}" stroke-width="2"/>')
        b.append(t(x, y + 6, label, 15, INK, "600"))

    b.append(arrow(544, 545, 544, 711, color=LINE, width=2, marker="gray"))
    b.append(arrow(440, 744, 356, 744, color=LINE, width=2, marker="gray"))
    b.append(arrow(250, 711, 250, 545, color=LINE, width=2, marker="gray"))
    b.append(arrow(356, 512, 440, 512, color=LINE, width=2, marker="gray"))

    b.append(shape("circle", 250, 628, LIVE))
    b.append(t(268, 633, "분해자", 14, LIVE, "700", anchor="start"))

    b.append(note_box(197, 878, 400, "문을 여닫는 것도 미생물이다", 16))
    b.append(caption(W / 2, 982, ["흙 안의 네 상자는 시계 방향으로 돌고,",
                                  "공기와 흙 사이는 두 문에서만 오간다."]))
    return base("들어오는 문과 나가는 문",
                "질소가 공기와 흙 사이를 오가는 고리를 그린 그림", "".join(b))


# ── p28 ───────────────────────────────────────────────────────────────
def fig_input():
    b = []
    b.append(t(W / 2, 240, "흙에 넣은 것이 갈라지는 네 갈래", 16, MUTED))

    b.append(f'<rect x="286" y="272" width="222" height="60" rx="12" fill="#efe6d8" stroke="{SOIL}" stroke-width="2"/>')
    b.append(t(397, 309, "흙에 넣은 양", 17, INK, "700"))

    targets = [(168, "식물이 받아들인 몫", LIVE, "mark"), (330, "흙에 남아 다음에 쓰이는 몫", SOIL, "soil"),
               (500, "물로 씻겨 나간 몫", LOSS, "loss"), (652, "공기로 빠져나간 몫", LOSS, "loss")]
    for tx, label, color, marker in targets:
        b.append(arrow(397, 340, tx, 430, color=color, width=3, marker=marker))
    b.append(t(168, 466, "식물이", 14, INK, "600"))
    b.append(t(168, 486, "받아들인 몫", 14, INK, "600"))
    b.append(t(330, 466, "흙에 남아", 14, INK, "600"))
    b.append(t(330, 486, "다음에 쓰이는 몫", 14, INK, "600"))
    b.append(t(500, 466, "물로", 14, LOSS, "600"))
    b.append(t(500, 486, "씻겨 나간 몫", 14, LOSS, "600"))
    b.append(t(652, 466, "공기로", 14, LOSS, "600"))
    b.append(t(652, 486, "빠져나간 몫", 14, LOSS, "600"))

    b.append(f'<line x1="105" y1="524" x2="689" y2="524" stroke="{SOFT}"/>')
    b.append(t(W / 2, 566, "같은 양을 넣되 넣는 속도만 달리하면", 16, MUTED))

    # 두 막대
    segs_slow = [(0.44, LIVE), (0.30, SOIL), (0.14, LOSS), (0.12, LOSS)]
    segs_fast = [(0.26, LIVE), (0.16, SOIL), (0.34, LOSS), (0.24, LOSS)]
    top, height = 610, 250
    for i, (label, segs) in enumerate([("천천히 넣었을 때", segs_slow), ("한꺼번에 넣었을 때", segs_fast)]):
        x = 250 + i * 190
        y = top
        for j, (frac, color) in enumerate(segs):
            h = height * frac
            b.append(f'<rect x="{x}" y="{y:.0f}" width="106" height="{h:.0f}" fill="{color}" '
                     f'opacity="{0.85 if j < 2 else 0.6}" stroke="#ffffff"/>')
            y += h
        b.append(t(x + 53, top + height + 28, label, 14, INK, "600"))

    # 막대 옆에 세로로 이름을 붙이면 두 막대의 경계와 어긋나 보인다. 아래에 범례로 따로 둔다.
    legend = [("식물이 받아들임", LIVE, 0.85), ("흙에 남음", SOIL, 0.85), ("빠져나감", LOSS, 0.6)]
    lx = 214
    for name, color, op in legend:
        b.append(f'<rect x="{lx}" y="{top + height + 44}" width="18" height="14" fill="{color}" opacity="{op}"/>')
        b.append(t(lx + 26, top + height + 57, name, 14, MUTED, anchor="start"))
        lx += 132

    b.append(note_box(197, 952, 400, "속도가 맞지 않으면 새어 나간다", 16))
    return base("넣은 것이 다 남지는 않는다",
                "넣은 것이 어디로 가는지를 갈래로 나눈 그림", "".join(b))


# ── p37 ───────────────────────────────────────────────────────────────
def fig_sampling():
    b = []
    b.append(t(W / 2, 240, "같은 흙을 두 가지 방식으로 떠 오면", 16, MUTED))

    def section(x0, y0, w, h, mixed):
        out = [f'<rect x="{x0}" y="{y0}" width="{w}" height="{h}" rx="8" '
               f'fill="{"#d9cdb8" if mixed else "#f0e9dc"}" stroke="{SOIL}" stroke-width="2"/>']
        grains = [(0.22, 0.24, 20), (0.58, 0.20, 26), (0.80, 0.42, 18), (0.34, 0.52, 24),
                  (0.66, 0.66, 22), (0.18, 0.74, 19), (0.50, 0.84, 17)]
        for fx, fy, rr in grains:
            gx, gy = x0 + w * fx, y0 + h * fy
            if mixed:
                out.append(f'<circle cx="{gx:.0f}" cy="{gy:.0f}" r="{rr}" fill="#c9bda6" '
                           f'stroke="{SOIL}" stroke-width="1.5"/>')
            else:
                out.append(f'<circle cx="{gx:.0f}" cy="{gy:.0f}" r="{rr}" fill="#f3efe4" '
                           f'stroke="{SOIL}" stroke-width="1.5"/>')
                out.append(f'<circle cx="{gx:.0f}" cy="{gy:.0f}" r="{max(4, rr - 8)}" fill="#4b4238"/>')
        return "".join(out)

    # 위 두 칸은 뜨기 전의 같은 흙이므로 반드시 똑같이 그린다. 오른쪽 칸을 미리 섞인 모습으로
    # 그리면 「같은 흙을 두 가지 방식으로」라는 이 그림의 전제가 그림 안에서 무너진다.
    # 방식에 따라 갈리는 결과는 아래 결과 칸에서만 보인다.
    b.append(section(126, 296, 240, 250, False))
    b.append(t(246, 260, "관을 박아 뽑는다", 17, INK, "700"))
    b.append(f'<rect x="196" y="288" width="66" height="270" fill="none" stroke="{AIR}" '
             f'stroke-width="3"/>')

    b.append(section(428, 296, 240, 250, False))
    b.append(t(548, 260, "삽으로 퍼서 섞는다", 17, INK, "700"))
    b.append(f'<path d="M528,318 L568,318 L568,402 L548,424 L528,402 Z" fill="none" '
             f'stroke="{AIR}" stroke-width="3"/>')
    b.append(f'<line x1="548" y1="318" x2="548" y2="288" stroke="{AIR}" stroke-width="3"/>')

    b.append(arrow(246, 566, 246, 616, color=LINE, width=2, marker="gray"))
    b.append(arrow(548, 566, 548, 616, color=LINE, width=2, marker="gray"))

    def result(x0, mixed, label, color):
        out = [f'<rect x="{x0}" y="624" width="240" height="116" rx="12" fill="#fbfcfc" '
               f'stroke="{LINE}" stroke-width="2"/>']
        for k in range(3):
            gx = x0 + 76 + k * 44
            if mixed:
                out.append(f'<circle cx="{gx}" cy="660" r="16" fill="#c9bda6" stroke="{SOIL}" stroke-width="1.5"/>')
            else:
                out.append(f'<circle cx="{gx}" cy="660" r="16" fill="#f3efe4" stroke="{SOIL}" stroke-width="1.5"/>')
                out.append(f'<circle cx="{gx}" cy="660" r="8" fill="#4b4238"/>')
        out.append(t(x0 + 120, 712, label, 15, color, "600"))
        return "".join(out)

    b.append(result(126, False, "어두운 자리가 그대로 있다", INK))
    b.append(result(428, True, "어두운 자리가 사라졌다", LOSS))

    b.append(note_box(197, 776, 400, "섞는 순간 그 시료는 다른 자리의 시료가 된다", 15))
    b.append(caption(W / 2, 880, ["알갱이 안쪽의 어두운 색이 산소가 닿지 않던 자리다."]))
    return base("뜨는 순간 사라지는 것",
                "흙을 뜨는 두 방식이 남기는 것과 지우는 것을 그린 그림", "".join(b))


# ── p42 ───────────────────────────────────────────────────────────────
def fig_share():
    b = []
    b.append(t(W / 2, 240, "알려진 미생물을 백 칸으로 나누면", 16, MUTED))

    x0, y0, cell = 132, 288, 32
    for r in range(10):
        for c in range(10):
            idx = r * 10 + c
            dark = idx in (23, 57)
            b.append(f'<rect x="{x0 + c * cell}" y="{y0 + r * cell}" width="{cell - 3}" '
                     f'height="{cell - 3}" fill="{"#a24f3d" if dark else "#e6ecee"}" '
                     f'stroke="#ffffff"/>')
    b.append(t(x0 + 5 * cell - 2, y0 - 16, "알려진 미생물", 16, INK, "700"))
    # 화살표는 실제로 칠해진 칸(다섯째 줄 여덟째)을 가리켜야 한다.
    b.append(arrow(x0 + 10 * cell + 58, y0 + 5 * cell + 4, x0 + 8 * cell + 4, y0 + 5 * cell + 14,
                   color=LOSS, width=2, marker="loss"))
    b.append(t(x0 + 10 * cell + 64, y0 + 5 * cell + 9, "병을 일으키는 쪽", 14, LOSS, "700", anchor="start"))
    b.append(t(x0 + 5 * cell - 2, y0 + 10 * cell + 26, "나머지", 14, MUTED))

    b.append(f'<line x1="105" y1="676" x2="689" y2="676" stroke="{SOFT}"/>')

    for i, (label, frac) in enumerate([("우리가 겪는 빈도", 0.72), ("실제 비율", 0.02)]):
        y = 730 + i * 108
        b.append(t(132, y - 14, label, 15, INK, "600", anchor="start"))
        b.append(f'<rect x="132" y="{y}" width="500" height="40" fill="#e6ecee"/>')
        b.append(f'<rect x="132" y="{y}" width="{500 * frac:.0f}" height="40" fill="#a24f3d"/>')
    b.append(t(397, 912, "겪는 만큼 많은 것은 아니다", 15, MUTED, "700"))

    b.append(note_box(197, 954, 400, "없애는 일과 가려내는 일은 다르다", 16))
    return base("눈에 띄는 쪽과 큰 쪽",
                "알려진 미생물 가운데 병을 일으키는 몫을 나타낸 그림", "".join(b))


FIGURES = {7: fig_methods, 15: fig_oxygen, 21: fig_nitrogen,
           28: fig_input, 37: fig_sampling, 42: fig_share}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}")


if __name__ == "__main__":
    main()
