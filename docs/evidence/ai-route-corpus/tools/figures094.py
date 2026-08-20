#!/usr/bin/env python3
"""book-094 이미지 페이지 4개의 SVG 생성. figures091.py의 t()/base() 패턴을 따른다.

네 도표의 형식을 모두 다르게 잡았다. 순환 고리, 노트 지면 비교, 조건 대조표, 달력 격자다.
공통 약속은 둘이다. 고른 것·이어진 것은 굵은 테두리와 진한 색, 버려진 것·빈 것은 옅은 색이다.

사용: python3 figures094.py <출력디렉터리>
"""
import math
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"
DROP = "#c3ccd0"
MARK = "#b4703a"


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
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
</defs>
{t(W / 2, 122, title, 31, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


def box(x, y, w, h, label="", fill="#ffffff", stroke=INK, size=15, width=1.8):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{fill}" stroke="{stroke}" '
            f'stroke-width="{width}"/>' + (t(x + w / 2, y + h / 2 + 5, label, size, INK) if label else ""))


# ── p5 세 부분이 이어진 고리 ──────────────────────────────────────────
def fig_loop():
    """신호·행동·보상이 원을 이루고 행동 쪽에 마찰이 걸리는 도식."""
    b = []
    cx, cy, r = W / 2, 470, 165
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{DROP}" stroke-width="2.4"/>')
    nodes = [(-90, "신호", "진료를 마친 순간"), (30, "행동", "한 줄 적기"),
             (150, "보상", "지난 기록을 오늘 쓴다")]
    pos = {}
    for angle, label, example in nodes:
        rad = math.radians(angle)
        x, y = cx + r * math.cos(rad), cy + r * math.sin(rad)
        pos[label] = (x, y)
        b.append(box(x - 68, y - 27, 136, 54, label, "#ffffff", KEEP, 18, 2.6))
        # 위쪽 노드는 제목 아래 여백이 좁아 예시를 원 가까이 붙인다.
        out = 72 if angle == -90 else 108
        ex, ey = cx + (r + out) * math.cos(rad), cy + (r + out) * math.sin(rad)
        b.append(t(ex, ey + 5, example, 13, MUTED))
    for angle in (-30, 90, 210):
        rad = math.radians(angle)
        ax, ay = cx + r * math.cos(rad), cy + r * math.sin(rad)
        tang = math.radians(angle + 90)
        b.append(f'<line x1="{ax - 14 * math.cos(tang):.1f}" y1="{ay - 14 * math.sin(tang):.1f}" '
                 f'x2="{ax + 14 * math.cos(tang):.1f}" y2="{ay + 14 * math.sin(tang):.1f}" '
                 f'stroke="{LINE}" stroke-width="2.4" marker-end="url(#gray)"/>')
    fx, fy = pos["행동"]
    b.append(f'<rect x="{fx - 40}" y="{fy + 96}" width="112" height="44" rx="8" fill="#f2f4f5" '
             f'stroke="{DROP}" stroke-width="1.8"/>')
    b.append(t(fx + 16, fy + 124, "마찰", 15, MUTED))
    b.append(f'<line x1="{fx + 16}" y1="{fy + 92}" x2="{fx + 16}" y2="{fy + 40}" stroke="{DROP}" '
             f'stroke-width="2.2" marker-end="url(#gray)"/>')
    b.append(t(cx, cy - 8, "한 바퀴가 돌면", 15, MUTED))
    b.append(t(cx, cy + 16, "다음 바퀴는 쉬워진다", 15, MUTED))
    b.append(note_box(147, 740, 500, "셋 중 하나만 빠져도 고리가 끊긴다"))
    b.append(caption(W / 2, 862, [
        "기록이 습관이 되지 않는 것은 대개 셋 가운데 하나가 없어서다.",
    ], 16))
    return base("세 부분이 이어진 고리", "신호와 행동과 보상", "".join(b))


# ── p15 같은 하루, 세 가지 양식 ───────────────────────────────────────
def fig_forms():
    """양식의 분량과 작성 시간·작성 일수를 세 지면으로 비교한다."""
    b = []
    pw, ph, y = 190, 250, 280
    xs = [70, 302, 534]
    specs = [("열 줄 양식", 10, 3, "십이 분", "엿새", False),
             ("세 줄 양식", 3, 3, "사 분", "열이레", False),
             ("한 줄 양식", 1, 1, "사십 초", "스무여드레", True)]
    for x, (title, lines, filled, mins, days, pick) in zip(xs, specs):
        b.append(f'<rect x="{x}" y="{y}" width="{pw}" height="{ph}" rx="6" fill="#fdfdfb" '
                 f'stroke="{KEEP if pick else LINE}" stroke-width="{3 if pick else 1.6}"/>')
        b.append(t(x + pw / 2, y - 16, title, 16, KEEP if pick else MUTED, "700"))
        for k in range(lines):
            ly = y + 30 + k * 21
            b.append(f'<line x1="{x + 16}" y1="{ly}" x2="{x + pw - 16}" y2="{ly}" stroke="#dfe4e6" '
                     f'stroke-width="1.4"/>')
            if k < filled:
                b.append(f'<line x1="{x + 20}" y1="{ly - 6}" x2="{x + 20 + (pw - 60) * 0.8:.0f}" '
                         f'y2="{ly - 6}" stroke="{INK}" stroke-width="3"/>')
        b.append(t(x + pw / 2, y + ph + 30, mins, 17, MARK, "700"))
        b.append(t(x + pw / 2, y + ph + 56, f"한 달에 {days}", 14, MUTED))
    b.append(note_box(147, 660, 500, "가장 지친 날에 맞춘 양식만 매일 채워진다"))
    b.append(caption(W / 2, 782, [
        "열 줄 양식은 채울 수 있는 날에만 채워지고 나머지 날에는 비어 있다.",
        "한 줄은 짧아 보이지만 백 일이면 백 줄이 된다.",
    ], 16))
    return base("같은 하루, 세 가지 양식", "양식의 분량과 채워진 날", "".join(b))


# ── p23 어디에 붙일 것인가 ────────────────────────────────────────────
def fig_trigger_table():
    """신호 후보를 세 조건으로 견준 대조표."""
    b = []
    x0, y0 = 120, 300
    cw, colw, rh = 190, 148, 62
    heads = ["거의 매일 있는가", "끝이 분명한가", "끝나고 손이 비는가"]
    rows = [("아침 첫 진료", [1, 1, 0], False), ("점심 식사", [1, 1, 1], False),
            ("마지막 진료", [1, 1, 1], True), ("퇴근길 지하철", [0, 0, 1], False)]
    b.append(f'<rect x="{x0}" y="{y0 - rh}" width="{cw + colw * 3}" height="{rh}" fill="#eef1f2" '
             f'stroke="{LINE}" stroke-width="1.2"/>')
    for i, head in enumerate(heads):
        words = head.split(" ")
        hx = x0 + cw + colw * i + colw / 2
        b.append(t(hx, y0 - rh / 2 - 2, " ".join(words[:2]), 13, INK, "600"))
        if len(words) > 2:
            b.append(t(hx, y0 - rh / 2 + 16, " ".join(words[2:]), 13, INK, "600"))
    for r, (label, marks, pick) in enumerate(rows):
        y = y0 + r * rh
        fill = "#f7faf9" if sum(marks) == 3 else "#ffffff"
        b.append(f'<rect x="{x0}" y="{y}" width="{cw}" height="{rh}" fill="{fill}" stroke="{LINE}" '
                 f'stroke-width="1.2"/>')
        b.append(t(x0 + cw / 2, y + rh / 2 + 6, label, 15, INK))
        for c, mark in enumerate(marks):
            cx = x0 + cw + colw * c
            b.append(f'<rect x="{cx}" y="{y}" width="{colw}" height="{rh}" fill="{fill}" '
                     f'stroke="{LINE}" stroke-width="1.2"/>')
            mid = cx + colw / 2
            if mark:
                b.append(f'<circle cx="{mid}" cy="{y + rh / 2}" r="13" fill="none" stroke="{KEEP}" '
                         f'stroke-width="2.6"/>')
            else:
                b.append(f'<line x1="{mid - 10}" y1="{y + rh / 2 - 10}" x2="{mid + 10}" '
                         f'y2="{y + rh / 2 + 10}" stroke="{DROP}" stroke-width="2.6"/>')
                b.append(f'<line x1="{mid + 10}" y1="{y + rh / 2 - 10}" x2="{mid - 10}" '
                         f'y2="{y + rh / 2 + 10}" stroke="{DROP}" stroke-width="2.6"/>')
        if pick:
            b.append(f'<rect x="{x0}" y="{y}" width="{cw + colw * 3}" height="{rh}" fill="none" '
                     f'stroke="{MARK}" stroke-width="3"/>')
            b.append(f'<line x1="{x0 + cw + colw * 3 + 46}" y1="{y + rh / 2}" '
                     f'x2="{x0 + cw + colw * 3 + 8}" y2="{y + rh / 2}" stroke="{MARK}" '
                     f'stroke-width="2" marker-end="url(#mark)"/>')
            b.append(t(x0 + cw + colw * 3 + 52, y + rh / 2 - 8, "여기에", 13, MARK, anchor="start"))
            b.append(t(x0 + cw + colw * 3 + 52, y + rh / 2 + 10, "붙였다", 13, MARK, anchor="start"))
    b.append(note_box(147, 640, 500, "셋을 모두 만족하는 자리만 신호가 된다"))
    b.append(caption(W / 2, 762, [
        "점심 식사도 셋을 만족하지만 기록 대상이 생기는 자리가 아니다.",
        "조건을 통과한 자리 가운데 대상과 가까운 쪽을 고른다.",
    ], 16))
    return base("어디에 붙일 것인가", "신호 후보와 세 조건", "".join(b))


# ── p40 네 번의 시도 ──────────────────────────────────────────────────
def fig_calendar():
    """네 번의 시도를 열두 주 격자로 비교한다."""
    b = []
    x0, y0 = 150, 300
    cell, gap, rows_gap = 5.4, 1.0, 74
    labels = ["첫 번째", "두 번째", "세 번째", "네 번째"]
    # 84칸(열두 주) 가운데 적힌 날을 True로 둔다.
    filled = [
        [k < 10 for k in range(84)],
        [k < 21 and not (8 <= k < 13) for k in range(84)],
        [k < 14 and k % 3 != 2 and k != 13 for k in range(84)],
        [k % 7 in (0, 1, 2, 4, 5) for k in range(84)],
    ]
    for r, (label, marks) in enumerate(zip(labels, filled)):
        y = y0 + r * rows_gap
        b.append(t(x0 - 16, y + 16, label, 15, INK, "600", anchor="end"))
        for k, on in enumerate(marks):
            x = x0 + k * (cell + gap)
            color = KEEP if on else "#ffffff"
            b.append(f'<rect x="{x:.1f}" y="{y}" width="{cell}" height="{22}" fill="{color}" '
                     f'stroke="{DROP}" stroke-width="0.7"/>')
        if r == 3:
            ex = x0 + 84 * (cell + gap) + 6
            b.append(f'<line x1="{ex}" y1="{y + 11}" x2="{ex + 30}" y2="{y + 11}" stroke="{MARK}" '
                     f'stroke-width="2" marker-end="url(#mark)"/>')
            b.append(t(ex + 6, y + 36, "이어지는 중", 13, MARK, anchor="start"))
    b.append(t(W / 2, y0 + 4 * rows_gap + 18, "가로 한 줄은 열두 주다", 14, MUTED))
    b.append(caption(W / 2, y0 + 4 * rows_gap + 56, [
        "앞의 셋은 빈틈이 없다가 끊겼고 넷째는 빈틈이 있는 채로 이어졌다.",
    ], 15))
    b.append(note_box(147, 700, 500, "빈 칸이 있어도 줄은 끊기지 않는다"))
    b.append(caption(W / 2, 822, [
        "매일 채우는 것을 목표로 삼은 시도는 한 번 빠진 뒤에 멈췄다.",
        "빠진 날을 비워 두기로 한 시도만 넉 달을 넘겼다.",
    ], 16))
    return base("네 번의 시도", "열두 주 동안 적힌 날", "".join(b))


FIGURES = {5: fig_loop, 15: fig_forms, 23: fig_trigger_table, 40: fig_calendar}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild094")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
