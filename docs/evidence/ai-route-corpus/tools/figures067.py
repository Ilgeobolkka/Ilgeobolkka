#!/usr/bin/env python3
"""book-067 이미지 페이지 6개의 SVG 생성. figures064.py의 t()/base() 패턴을 따른다.

소리를 다루는 책이라 여섯 도표 가운데 넷이 가로로 흐르는 시간축을 쓴다.
시간축 도표는 왼쪽에서 오른쪽으로 흐르는 한 줄에 화면 사건과 소리를 나란히 놓아,
'언제 놓느냐'가 결과를 가른다는 본문 주장을 그림으로 겹쳐 보이게 했다.
"""
import math
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
ACC = "#2f6d7a"          # 소리를 나타내는 강조색
ACC_SOFT = "#cfe2e6"
WARM = "#a8763f"         # 대비되는 두 번째 계열
MUTED = "#55666b"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="acc" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{ACC}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <marker id="warm" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{WARM}"/></marker>
</defs>
{t(W/2, 122, title, 31, '#203238', '700')}
{t(W/2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, h, label, size=17):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def note_box2(x, y, w, line1, line2, size=17):
    """두 줄짜리 결론 상자. 상자 밖으로 둘째 줄이 새지 않도록 높이를 함께 잡는다."""
    h = 92
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + 38, line1, size, "#51462c", "700")
            + t(x + w / 2, y + 68, line2, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


# ── p7 · 장면 위에 쌓이는 소리의 층 ────────────────────────────────
BX, BW = 244, 452


def _fx(frac):
    return BX + BW * frac


def fig_layers():
    b = []
    # 위쪽: 장면이 흐르는 시간 띠
    sy = 268
    b.append(t(BX, sy - 42, "장면이 흐르는 시간", 15, MUTED, anchor="start"))
    b.append(f'<rect x="{BX}" y="{sy}" width="{BW}" height="30" rx="5" fill="#eef2f3" stroke="{LINE}"/>')
    events = [(0.16, "걸음"), (0.40, "말"), (0.63, "복도 문"), (0.87, "문")]
    for frac, label in events:
        x = _fx(frac)
        b.append(f'<line x1="{x:.1f}" y1="{sy}" x2="{x:.1f}" y2="{sy + 30}" stroke="{LINE}" stroke-width="1.4"/>')
        b.append(t(x, sy - 12, label, 13, MUTED))
    b.append(f'<line x1="{BX + BW + 8}" y1="{sy + 15}" x2="{BX + BW + 34}" y2="{sy + 15}" '
             f'stroke="{LINE}" stroke-width="2" marker-end="url(#gray)"/>')

    lanes = [
        ("말", 384, [(0.34, 0.52, 34, True), (0.72, 0.80, 20, True)]),
        ("효과음", 500, [(0.13, 0.20, 24, True), (0.58, 0.66, 22, False), (0.84, 0.91, 26, True)]),
        ("분위기음", 616, [(0.02, 0.98, 9, True)]),
    ]
    for name, yc, segs in lanes:
        b.append(f'<line x1="{BX}" y1="{yc}" x2="{BX + BW}" y2="{yc}" stroke="{SOFT}" stroke-dasharray="4,5"/>')
        b.append(t(BX - 22, yc + 6, name, 17, INK, "700", anchor="end"))
        for x0, x1, h, inside in segs:
            px, pw = _fx(x0), BW * (x1 - x0)
            fill = ACC if inside else "#ffffff"
            b.append(f'<rect x="{px:.1f}" y="{yc - h / 2:.1f}" width="{pw:.1f}" height="{h}" rx="4" '
                     f'fill="{fill}" stroke="{ACC}" stroke-width="2"/>')
    b.append(t(BX + BW / 2, 668, "띠가 굵을수록 그 순간 관객의 주의를 많이 가져간다", 14, "#7a878b"))

    # 범례
    ly = 716
    b.append(f'<rect x="{BX}" y="{ly}" width="30" height="18" rx="4" fill="{ACC}" stroke="{ACC}" stroke-width="2"/>')
    b.append(t(BX + 40, ly + 14, "화면 안에 원인이 보이는 소리", 15, MUTED, anchor="start"))
    b.append(f'<rect x="{BX}" y="{ly + 34}" width="30" height="18" rx="4" fill="#ffffff" stroke="{ACC}" stroke-width="2"/>')
    b.append(t(BX + 40, ly + 48, "화면 밖에서 오는 소리", 15, MUTED, anchor="start"))

    b.append(note_box(147, 826, 500, 58, "세 층이 함께 흐르지만 앞으로 나오는 것은 하나뿐이다"))
    b.append(caption(W / 2, 942, [
        "말·효과음·분위기음을 처음부터 따로 쌓아 두면",
        "나중에 어느 한 층만 줄이거나 걷어 낼 수 있다.",
    ], 16))
    return base("장면 위에 쌓이는 소리의 층", "한 장면 아래로 성격이 다른 소리가 나란히 흐른다", "".join(b))


# ── p14 · 장면과 소리의 시간축 ────────────────────────────────────
def _band(x0, xp, x1, yc, peak, thin=5):
    """앞뒤는 얇고 xp에서 가장 굵어지는 소리 띠."""
    up = [(x0, yc - thin / 2), (xp - 46, yc - thin), (xp - 8, yc - peak / 2),
          (xp + 12, yc - peak / 2 * 0.82), (x1, yc - thin / 2)]
    dn = [(x1, yc + thin / 2), (xp + 12, yc + peak / 2 * 0.82), (xp - 8, yc + peak / 2),
          (xp - 46, yc + thin), (x0, yc + thin / 2)]
    pts = " ".join(f"{x:.1f},{y:.1f}" for x, y in up + dn)
    return f'<polygon points="{pts}" fill="{ACC_SOFT}" stroke="{ACC}" stroke-width="2"/>'


def fig_timeline():
    b = []
    x0, x1 = 152, 640
    # 화면 사건 줄
    ey = 294
    b.append(t(x0, ey - 54, "화면에서 벌어지는 일", 16, INK, "700", anchor="start"))
    b.append(f'<line x1="{x0}" y1="{ey}" x2="{x1 + 30}" y2="{ey}" stroke="{LINE}" stroke-width="2" marker-end="url(#gray)"/>')
    marks = [(0.10, "걷기 시작"), (0.36, "발이 닿음"), (0.60, "문 앞에 섬"), (0.84, "문이 닫힘")]
    for frac, label in marks:
        x = x0 + (x1 - x0) * frac
        b.append(f'<circle cx="{x:.1f}" cy="{ey}" r="6" fill="{INK}"/>')
        b.append(t(x, ey - 18, label, 13, MUTED))

    door_x = x0 + (x1 - x0) * 0.84
    off = 48

    # 두 줄을 관통하는 기준선 — 화면 사건이 있는 자리
    b.append(f'<line x1="{door_x:.1f}" y1="{ey + 10}" x2="{door_x:.1f}" y2="620" '
             f'stroke="{ACC}" stroke-width="1.6" stroke-dasharray="5,4"/>')

    # 맞춘 경우
    yc = 400
    b.append(t(x0, yc - 58, "맞춘 소리", 16, INK, "700", anchor="start"))
    b.append(_band(x0 + 150, door_x, x1, yc, 62))
    b.append(t(x0 + 156, yc + 56, "소리가 시작되는 자리", 13, "#7a878b", anchor="start"))
    b.append(t(door_x + 6, yc + 60, "가장 굵은 자리", 13, ACC, "700", anchor="start"))

    # 늦게 놓은 경우
    yc2 = 552
    b.append(t(x0, yc2 - 58, "조금 늦게 놓은 소리", 16, INK, "700", anchor="start"))
    b.append(_band(x0 + 150 + off, door_x + off, x1 + off, yc2, 62))
    b.append(f'<line x1="{door_x + off:.1f}" y1="{yc2 - 46}" x2="{door_x + off:.1f}" y2="620" '
             f'stroke="{WARM}" stroke-width="1.6" stroke-dasharray="5,4"/>')
    b.append(f'<line x1="{door_x:.1f}" y1="626" x2="{door_x + off:.1f}" y2="626" '
             f'stroke="{WARM}" stroke-width="3"/>')
    b.append(t(door_x + off / 2, 650, "이만큼의 어긋남", 13, WARM, "700"))
    b.append(t(W / 2, 678, "반 걸음만 어긋나도 장면이 굼떠 보인다", 15, MUTED))

    b.append(note_box2(147, 726, 500,
                       "맞추는 것은 소리의 시작이 아니라",
                       "귀가 사건을 확인하는 순간이다"))
    b.append(caption(W / 2, 878, [
        "문소리는 문이 움직일 때부터 나지만 관객이 사건을 확인하는 것은",
        "문이 문틀에 부딪히는 순간이다. 맞출 좌표는 그 자리다.",
    ], 16))
    return base("장면과 소리의 시간축", "화면 사건과 소리를 나란히 놓고 맞출 자리를 찾는다", "".join(b))


# ── p22 · 같은 화면에 얹은 두 소리 ────────────────────────────────
def fig_two_sounds():
    b = []
    # 가운데 화면 한 장
    fx, fy, fw, fh = 317, 236, 160, 108
    b.append(f'<rect x="{fx}" y="{fy}" width="{fw}" height="{fh}" rx="6" fill="#eef2f3" stroke="{LINE}" stroke-width="2"/>')
    b.append(f'<rect x="{fx + 88}" y="{fy + 18}" width="{fw - 106}" height="{fh - 36}" fill="#ffffff" stroke="{LINE}"/>')
    b.append(f'<line x1="{fx + 88 + (fw - 106) / 2}" y1="{fy + 18}" x2="{fx + 88 + (fw - 106) / 2}" y2="{fy + fh - 18}" stroke="{LINE}"/>')
    b.append(f'<circle cx="{fx + 48}" cy="{fy + 44}" r="16" fill="{LINE}"/>')
    b.append(f'<path d="M{fx + 26} {fy + fh - 8} q22 -32 44 0 z" fill="{LINE}"/>')
    b.append(t(W / 2, fy + fh + 26, "창가에 앉아 밖을 보는 인물 — 화면은 이 한 장뿐이다", 15, MUTED))

    # 두 갈래
    cases = [
        (170, ACC, "얹은 소리", ["창밖 거리의 소음", "지나가는 차", "사람들의 말소리"],
         "관객이 읽는 것", "오후의 평범한 한때"),
        (424, WARM, "얹은 소리", ["아무도 없는 넓은 실내의 울림", "멀리서 들리는 아이들 웃음"],
         "관객이 읽는 것", "지금은 없는 시간"),
    ]
    for bx, color, head, items, foot_head, foot in cases:
        b.append(f'<line x1="{W / 2 + (1 if bx > 400 else -1) * 46}" y1="{fy + fh + 46}" '
                 f'x2="{bx + 100}" y2="{fy + fh + 92}" stroke="{color}" stroke-width="2.2" marker-end="url(#{"warm" if color == WARM else "acc"})"/>')
        by = 424
        b.append(f'<rect x="{bx}" y="{by}" width="200" height="118" rx="10" fill="#ffffff" stroke="{color}" stroke-width="2"/>')
        b.append(t(bx + 100, by + 26, head, 14, color, "700"))
        for i, item in enumerate(items):
            b.append(t(bx + 100, by + 54 + i * 24, item, 13, MUTED))
        b.append(f'<rect x="{bx}" y="{by + 132}" width="200" height="76" rx="10" fill="#f4f7f8" stroke="{SOFT}"/>')
        b.append(t(bx + 100, by + 158, foot_head, 13, "#7a878b"))
        b.append(t(bx + 100, by + 186, foot, 17, INK, "700"))
    b.append(t(W / 2, 486, "화면은", 14, "#7a878b"))
    b.append(t(W / 2, 508, "같다", 15, INK, "700"))

    # 어긋남의 정도 눈금
    gy = 726
    gx0, gx1 = 152, 642
    b.append(t(W / 2, gy - 26, "화면과 소리가 벌어진 정도", 16, INK, "700"))
    b.append(f'<rect x="{gx0}" y="{gy}" width="{(gx1 - gx0) * 0.66:.1f}" height="22" rx="11" fill="{ACC_SOFT}"/>')
    b.append(f'<rect x="{gx0 + (gx1 - gx0) * 0.66:.1f}" y="{gy}" width="{(gx1 - gx0) * 0.34:.1f}" height="22" rx="11" fill="#efdcd6"/>')
    b.append(f'<line x1="{gx0}" y1="{gy + 34}" x2="{gx1}" y2="{gy + 34}" stroke="{LINE}"/>')
    b.append(t(gx0, gy + 58, "완전히 맞물림", 13, MUTED, anchor="start"))
    b.append(t(gx1, gy + 58, "아무 관계 없음", 13, MUTED, anchor="end"))
    b.append(t(gx0 + (gx1 - gx0) * 0.33, gy + 16, "뜻이 선명해지는 구간", 13, "#245660"))
    b.append(t(gx0 + (gx1 - gx0) * 0.83, gy + 16, "실수로 읽히는 구간", 13, "#8b4a3f"))
    bx = gx0 + (gx1 - gx0) * 0.66
    b.append(f'<line x1="{bx:.1f}" y1="{gy - 10}" x2="{bx:.1f}" y2="{gy + 40}" stroke="{INK}" stroke-width="2"/>')
    b.append(t(bx, gy + 84, "여기를 넘으면 뜻이 아니라 잘못으로 읽힌다", 14, INK, "700"))

    b.append(note_box(147, 872, 500, 58, "뜻은 소리가 아니라 화면과의 거리에서 나온다"))
    return base("같은 화면에 얹은 두 소리", "한 장의 화면이 소리에 따라 다른 장면이 된다", "".join(b))


# ── p29 · 강조 앞뒤의 소리 흐름 ──────────────────────────────────
def fig_stinger():
    b = []
    ax0, ax1 = 150, 556
    ay0, ay1 = 268, 566        # 위(센 소리) ~ 아래(약한 소리)

    def px(f):
        return ax0 + (ax1 - ax0) * f

    def py(v):
        return ay1 - (ay1 - ay0) * v

    b.append(f'<line x1="{ax0}" y1="{ay1}" x2="{ax1 + 22}" y2="{ay1}" stroke="{LINE}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(f'<line x1="{ax0}" y1="{ay1}" x2="{ax0}" y2="{ay0 - 22}" stroke="{LINE}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(t(ax1 + 26, ay1 + 20, "시간", 14, MUTED, anchor="end"))
    b.append(t(ax0 - 10, ay0 - 28, "소리의 세기", 14, MUTED, anchor="start"))

    # 앞을 비운 경우
    main = [(0.00, 0.44), (0.26, 0.44), (0.31, 0.10), (0.46, 0.09), (0.50, 0.95),
            (0.545, 0.52), (0.62, 0.33), (0.78, 0.42), (1.00, 0.44)]
    pts = " ".join(f"{px(f):.1f},{py(v):.1f}" for f, v in main)
    # 앞을 비우지 않은 경우 (흐린 선)
    ghost = [(0.00, 0.44), (0.44, 0.44), (0.50, 0.95), (0.56, 0.50), (0.66, 0.44), (1.00, 0.44)]
    gpts = " ".join(f"{px(f):.1f},{py(v):.1f}" for f, v in ghost)
    b.append(f'<polyline points="{gpts}" fill="none" stroke="{LINE}" stroke-width="2.4" '
             f'stroke-dasharray="7,5" opacity="0.75"/>')
    b.append(f'<polyline points="{pts}" fill="none" stroke="{ACC}" stroke-width="3.6" stroke-linejoin="round"/>')

    # 비우는 구간 표시
    b.append(f'<rect x="{px(0.31):.1f}" y="{py(0.10):.1f}" width="{px(0.46) - px(0.31):.1f}" '
             f'height="{ay1 - py(0.10):.1f}" fill="{ACC_SOFT}" opacity="0.5"/>')
    b.append(t(px(0.385), py(0.10) + 26, "앞을 비우는 구간", 14, "#245660", "700"))
    b.append(t(px(0.50), py(0.95) - 16, "강조가 들어가는 자리", 14, ACC, "700"))
    b.append(t(px(0.72), py(0.33) + 28, "여운", 14, MUTED))

    # 차이를 재는 화살표 — 그래프 오른쪽 빈자리에 두어 선과 겹치지 않게 한다
    dx = 606
    for v in (0.95, 0.10):
        b.append(f'<line x1="{px(0.50 if v > 0.5 else 0.46):.1f}" y1="{py(v):.1f}" x2="{dx:.1f}" y2="{py(v):.1f}" '
                 f'stroke="{SOFT}" stroke-width="1.4" stroke-dasharray="4,4"/>')
    b.append(f'<line x1="{dx}" y1="{py(0.95):.1f}" x2="{dx}" y2="{py(0.10):.1f}" '
             f'stroke="{INK}" stroke-width="2" marker-start="url(#gray)" marker-end="url(#gray)"/>')
    mid = (py(0.95) + py(0.10)) / 2
    for i, line in enumerate(["관객이", "느끼는 크기는", "이 차이다"]):
        b.append(t(dx + 12, mid - 22 + i * 22, line, 13, INK, "700", anchor="start"))

    # 범례
    ly = 626
    b.append(f'<line x1="{ax0}" y1="{ly}" x2="{ax0 + 44}" y2="{ly}" stroke="{ACC}" stroke-width="3.6"/>')
    b.append(t(ax0 + 56, ly + 5, "앞을 비운 경우", 15, MUTED, anchor="start"))
    b.append(f'<line x1="{ax0}" y1="{ly + 30}" x2="{ax0 + 44}" y2="{ly + 30}" stroke="{LINE}" stroke-width="2.4" stroke-dasharray="7,5"/>')
    b.append(t(ax0 + 56, ly + 35, "비우지 않은 경우 — 꼭대기 높이는 같지만 차이가 작다", 15, MUTED, anchor="start"))

    b.append(note_box(147, 726, 500, 58, "같은 세기의 소리도 앞을 비우면 커진다"))
    b.append(caption(W / 2, 836, [
        "강조를 만드는 일은 소리를 키우는 일이 아니라",
        "그 앞을 얼마나 낮춰 두느냐를 정하는 일이다.",
    ], 16))
    return base("강조 앞뒤의 소리 흐름", "크기를 만드는 것은 세기가 아니라 앞과의 차이다", "".join(b))


# ── p36 · 거리와 잔향의 지도 ─────────────────────────────────────
def fig_space_map():
    b = []
    gx0, gy0 = 196, 262
    gw, gh = 400, 380
    b.append(f'<rect x="{gx0}" y="{gy0}" width="{gw}" height="{gh}" fill="#ffffff" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(f'<line x1="{gx0 + gw / 2}" y1="{gy0}" x2="{gx0 + gw / 2}" y2="{gy0 + gh}" stroke="{SOFT}"/>')
    b.append(f'<line x1="{gx0}" y1="{gy0 + gh / 2}" x2="{gx0 + gw}" y2="{gy0 + gh / 2}" stroke="{SOFT}"/>')
    b.append(t(gx0 + gw / 2, gy0 - 22, "소리까지의 거리 →", 15, MUTED))
    b.append(t(gx0 - 22, gy0 - 4, "잔향 많음", 14, MUTED, anchor="end"))
    b.append(t(gx0 - 22, gy0 + gh + 6, "잔향 적음", 14, MUTED, anchor="end"))
    b.append(t(gx0 + 40, gy0 + gh + 30, "가까움", 14, MUTED))
    b.append(t(gx0 + gw - 30, gy0 + gh + 30, "멂", 14, MUTED))

    cells = [
        (0, 0, 3, 1.00, "같은 넓은 공간 안", "바로 곁"),
        (1, 0, 4, 0.45, "넓은 공간의", "반대편 끝"),
        (0, 1, 0, 1.00, "귓가", "좁고 부드러운 방"),
        (1, 1, 1, 0.40, "문 하나 너머", "막힌 저편"),
    ]
    for col, row, ripples, solid, l1, l2 in cells:
        cx = gx0 + gw / 4 + gw / 2 * col
        cy = gy0 + gh / 4 + gh / 2 * row
        for i in range(ripples):
            r = 22 + i * 12
            b.append(f'<circle cx="{cx}" cy="{cy - 22}" r="{r}" fill="none" stroke="{ACC}" '
                     f'stroke-width="1.4" opacity="{0.42 - i * 0.07:.2f}"/>')
        b.append(f'<circle cx="{cx}" cy="{cy - 22}" r="13" fill="{ACC}" opacity="{solid:.2f}"/>')
        b.append(t(cx, cy + 42, l1, 15, INK, "700"))
        b.append(t(cx, cy + 63, l2, 15, INK, "700"))

    # 주의 문구는 격자 칸 안의 설명과 겹치지 않도록 격자 아래로 뺀다
    wy = gy0 + gh + 52
    b.append(f'<rect x="{gx0 + gw / 2 - 6}" y="{wy}" width="{gw / 2 + 6}" height="52" rx="9" '
             f'fill="#faf6f2" stroke="#d9b58e"/>')
    b.append(t(gx0 + gw * 0.75, wy + 21, "두 값이 서로 다른 말을 하면", 12.5, "#7a5a35"))
    b.append(t(gx0 + gw * 0.75, wy + 39, "관객은 장소를 짚지 못한다", 12.5, "#7a5a35"))
    b.append(t(gx0 + gw / 2 - 18, wy + 21, "동그라미의 진하기는 또렷함,", 13, MUTED, anchor="end"))
    b.append(t(gx0 + gw / 2 - 18, wy + 39, "물결의 수는 잔향의 양이다", 13, MUTED, anchor="end"))

    b.append(note_box(147, 780, 500, 58, "장소의 인상은 거리와 잔향의 조합에서 나온다"))
    b.append(caption(W / 2, 892, [
        "세기만 줄인 소리는 멀어지지 않는다. 또렷함이 함께 줄고",
        "지나온 공간의 울림이 따라붙어야 거리가 생긴다.",
    ], 16))
    return base("거리와 잔향의 지도", "두 값을 어떻게 짝짓느냐가 장소를 정한다", "".join(b))


# ── p44 · 거둔 구간이 놓인 자리 ──────────────────────────────────
def fig_silence():
    b = []
    tx0, tx1 = 176, 654
    span = tx1 - tx0

    def fx(f):
        return tx0 + span * f

    rows = [
        (300, "사건 직전에 거둔 경우", [(0.00, 0.40, True), (0.40, 0.60, False), (0.60, 1.00, True)],
         "다음을 기다리게 한다", 1),
        (452, "사건 직후에 거둔 경우", [(0.00, 0.60, True), (0.60, 0.80, False), (0.80, 1.00, True)],
         "방금 본 것을 받아들이게 한다", -1),
    ]
    event = 0.60
    for y, title, segs, note, direction in rows:
        b.append(t(tx0, y - 26, title, 17, INK, "700", anchor="start"))
        for f0, f1, filled in segs:
            x, w = fx(f0), span * (f1 - f0)
            if filled:
                b.append(f'<rect x="{x:.1f}" y="{y}" width="{w:.1f}" height="38" fill="{ACC_SOFT}" stroke="{ACC}" stroke-width="2"/>')
            else:
                b.append(f'<rect x="{x:.1f}" y="{y}" width="{w:.1f}" height="38" fill="#ffffff" stroke="{ACC}" stroke-width="2" stroke-dasharray="6,5"/>')
                b.append(t(x + w / 2, y + 24, "거둔 구간", 13, ACC, "700"))
        ex = fx(event)
        b.append(f'<line x1="{ex:.1f}" y1="{y - 16}" x2="{ex:.1f}" y2="{y + 54}" stroke="{INK}" stroke-width="2.2"/>')
        b.append(t(ex, y - 22, "사건", 13, INK, "700"))
        # 방향 화살표
        if direction > 0:
            a0, a1 = fx(0.42), fx(0.58)
        else:
            a0, a1 = fx(0.78), fx(0.62)
        b.append(f'<line x1="{a0:.1f}" y1="{y + 72}" x2="{a1:.1f}" y2="{y + 72}" stroke="{WARM}" stroke-width="2.2" marker-end="url(#warm)"/>')
        b.append(t((a0 + a1) / 2, y + 94, note, 14, WARM, "700"))

    # 화면에 남은 볼거리 띠
    vy = 640
    b.append(t(tx0, vy - 18, "화면에 남은 볼거리", 16, INK, "700", anchor="start"))
    b.append(f'<rect x="{tx0}" y="{vy}" width="{span * 0.78:.1f}" height="24" rx="5" fill="#eef2f3" stroke="{LINE}"/>')
    b.append(f'<rect x="{fx(0.78):.1f}" y="{vy}" width="{span * 0.22:.1f}" height="24" rx="5" fill="#f6ece8" stroke="#c08b7d"/>')
    for i in range(5):
        sx = fx(0.78) + 20 + i * 18
        b.append(f'<line x1="{sx:.1f}" y1="{vy + 3}" x2="{sx - 11:.1f}" y2="{vy + 21}" stroke="#c08b7d" stroke-width="1.4"/>')
    b.append(f'<line x1="{fx(0.78):.1f}" y1="{vy + 34}" x2="{fx(0.78):.1f}" y2="{vy + 56}" stroke="#a5675a" stroke-width="1.6"/>')
    b.append(t(fx(0.78), vy + 76, "여기부터 관객은 기다림을 의식한다", 14, "#8b4a3f", "700"))
    b.append(t(tx0 + span * 0.34, vy + 17, "표정이 바뀌거나 무언가 움직이는 동안", 13, MUTED))

    b.append(note_box2(147, 800, 500,
                       "같은 길이의 거둠도 앞에 두느냐 뒤에 두느냐에 따라",
                       "하는 일이 다르다"))
    b.append(caption(W / 2, 952, [
        "거두기 전 구간이 충분히 채워져 있어야 사라짐이 보인다.",
        "비움은 채움 위에서만 뜻을 가진다.",
    ], 16))
    return base("거둔 구간이 놓인 자리", "소리를 물린 자리가 사건의 앞이냐 뒤냐가 뜻을 가른다", "".join(b))


FIGURES = {7: fig_layers, 14: fig_timeline, 22: fig_two_sounds,
           29: fig_stinger, 36: fig_space_map, 44: fig_silence}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tmp/pdfs/book-067")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
