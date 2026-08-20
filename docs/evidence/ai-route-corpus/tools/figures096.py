#!/usr/bin/env python3
"""book-096 이미지 페이지 4개의 SVG 생성. figures091.py의 t()/base() 패턴을 따른다.

네 도표의 형식을 모두 다르게 잡았다. 부챗살 대비, 깔때기, 좌석 배치도, 가로 막대다.
공통 약속은 둘이다. 열린 쪽·말한 몫은 진한 색, 부담이 큰 쪽·말하지 않은 자리는 옅은 색이다.

사용: python3 figures096.py <출력디렉터리>
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


# ── p5 물음이 답의 폭을 정한다 ────────────────────────────────────────
def fig_fan():
    """닫힌 질문과 열린 질문에서 갈라지는 답의 갈래를 부챗살로 비교한다."""
    b = []
    blocks = [
        (300, "이대로 진행해도 괜찮을까요", [("예", True), ("아니오", False)], "갈래 둘"),
        (540, "이 방식으로 하면 무엇이 달라질까요",
         [("빨라진다", True), ("비용이 는다", True), ("누가 맡나", True),
          ("지난번과 다르다", True), ("해 본 적 없다", True), ("조합원이 묻는다", True)], "갈래 여섯"),
    ]
    for y, question, answers, tag in blocks:
        b.append(f'<rect x="80" y="{y - 30}" width="250" height="60" rx="14" fill="#eef1f2" '
                 f'stroke="{INK}" stroke-width="1.8"/>')
        words = question.split(" ")
        half = (len(words) + 1) // 2
        b.append(t(205, y - 6, " ".join(words[:half]), 14, INK))
        b.append(t(205, y + 16, " ".join(words[half:]), 14, INK))
        n = len(answers)
        for i, (label, strong) in enumerate(answers):
            ey = y - 70 + (140 / max(1, n - 1)) * i if n > 1 else y
            color = KEEP if strong else DROP
            width = 2.6 if strong else 1.2
            b.append(f'<line x1="336" y1="{y}" x2="470" y2="{ey:.0f}" stroke="{color}" '
                     f'stroke-width="{width}"/>')
            b.append(t(480, ey + 5, label, 14, INK if strong else MUTED, anchor="start"))
            if not strong:
                b.append(t(480, ey + 24, "이유까지 말해야 한다", 11, MUTED, anchor="start"))
        b.append(t(205, y + 52, tag, 15, MARK, "700"))
    b.append(note_box(147, 700, 500, "답의 폭은 태도가 아니라 물음이 정한다"))
    b.append(caption(W / 2, 822, [
        "닫힌 질문에서는 아니오 쪽에만 이유를 대야 하는 부담이 붙는다.",
        "열린 질문에서는 어느 갈래로 답해도 부담이 같다.",
    ], 16))
    return base("물음이 답의 폭을 정한다", "닫힌 질문과 열린 질문", "".join(b))


# ── p15 넓게 열고 좁혀 간다 ───────────────────────────────────────────
def fig_funnel():
    """회의 질문을 여는·벌리는·좁히는 세 자리로 배치한 깔때기."""
    b = []
    top_w, bot_w = 470, 190
    y0, band = 270, 108
    cx = W / 2
    rows = [
        ("여는 질문", "지난 한 달 동안 무엇이 가장 손이 많이 갔나요", "모두가 답할 수 있다"),
        ("벌리는 질문", "그 문제를 다르게 볼 수 있는 방법이 있을까요", "갈래를 늘린다"),
        ("좁히는 질문", "이 가운데 다음 달에 시작할 수 있는 것은", "고를 수 있게 만든다"),
    ]
    for i, (name, question, effect) in enumerate(rows):
        wt = top_w - (top_w - bot_w) * (i / 3)
        wb = top_w - (top_w - bot_w) * ((i + 1) / 3)
        yt, yb = y0 + i * band, y0 + (i + 1) * band
        b.append(f'<polygon points="{cx - wt / 2},{yt} {cx + wt / 2},{yt} {cx + wb / 2},{yb} '
                 f'{cx - wb / 2},{yb}" fill="{"#f4f7f6" if i % 2 == 0 else "#ffffff"}" '
                 f'stroke="{INK}" stroke-width="1.6"/>')
        words = question.split(" ")
        half = (len(words) + 1) // 2
        b.append(t(cx, yt + band / 2 - 6, " ".join(words[:half]), 13, INK))
        b.append(t(cx, yt + band / 2 + 16, " ".join(words[half:]), 13, INK))
        b.append(t(cx - wt / 2 - 12, yt + band / 2 + 5, name, 15, KEEP, "700", anchor="end"))
        b.append(t(cx + wt / 2 + 12, yt + band / 2 + 5, effect, 13, MUTED, anchor="start"))
    yb = y0 + 3 * band
    b.append(f'<rect x="{cx - 60}" y="{yb + 20}" width="120" height="44" rx="8" fill="#f2f4f5" '
             f'stroke="{DROP}" stroke-width="1.6"/>')
    b.append(t(cx, yb + 48, "결정", 15, MUTED))
    b.append(t(cx + 76, yb + 48, "다음 회의", 13, MUTED, anchor="start"))
    b.append(f'<line x1="{cx}" y1="{yb}" x2="{cx}" y2="{yb + 16}" stroke="{DROP}" stroke-width="1.6" '
             f'marker-end="url(#gray)"/>')
    b.append(note_box(147, 700, 500, "좁히는 질문을 먼저 던지면 갈래가 생기지 않는다"))
    b.append(caption(W / 2, 822, [
        "세 자리의 순서가 회의의 모양을 정한다.",
    ], 16))
    return base("넓게 열고 좁혀 간다", "회의의 세 자리에 놓이는 질문", "".join(b))


# ── p29 누가 얼마나 말했나 ────────────────────────────────────────────
def fig_seats():
    """한 바퀴 도입 전후의 발언 분포를 좌석 배치도 위에 점으로 표시한다."""
    b = []
    names = "가나다라마바사아"
    tables = [
        (368, "한 바퀴 없이", [9, 8, 4, 2, 1, 0, 0, 0], "말한 사람 다섯"),
        (688, "한 바퀴 뒤", [6, 5, 4, 3, 3, 2, 2, 1], "말한 사람 여덟"),
    ]
    for cy, label, counts, tag in tables:
        cx, r = 318, 78
        b.append(f'<circle cx="{cx}" cy="{cy}" r="{r - 30}" fill="#f7f6f2" stroke="{LINE}" '
                 f'stroke-width="1.6"/>')
        b.append(t(cx, cy + 5, label, 14, MUTED, "700"))
        for i, (name, count) in enumerate(zip(names, counts)):
            ang = math.radians(-90 + i * 45)
            ux, uy = math.cos(ang), math.sin(ang)
            sx, sy = cx + r * ux, cy + r * uy
            b.append(f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="15" fill="#ffffff" '
                     f'stroke="{INK if count else DROP}" stroke-width="1.8"/>')
            b.append(t(sx, sy + 5, name, 13, INK if count else DROP))
            # 점은 바깥 방향으로 최대 다섯 개씩 두 줄로 찍는다.
            for k in range(count):
                dist = r + 22 + (k % 5) * 10
                off = 11 if k >= 5 else 0
                dx = dist * ux - off * uy
                dy = dist * uy + off * ux
                b.append(f'<circle cx="{cx + dx:.1f}" cy="{cy + dy:.1f}" r="3.6" fill="{KEEP}"/>')
        b.append(t(540, cy + 5, tag, 16, MARK, "700", anchor="start"))
    b.append(note_box(147, 872, 500, "전체 발언량이 아니라 말한 사람 수가 달라진다"))
    b.append(caption(W / 2, 986, [
        "두 회의의 점 개수 합은 스물넷과 스물여섯으로 비슷하다.",
    ], 15))
    return base("누가 얼마나 말했나", "한 바퀴 도입 전후의 발언 분포", "".join(b))


# ── p37 세어 본 세 번 ─────────────────────────────────────────────────
def fig_counts():
    """세 번의 회의에서 사람별 발언 횟수를 가로 막대로 나타낸다."""
    b = []
    names = "가나다라마바사아"
    totals = [18, 16, 10, 8, 6, 4, 0, 0]
    parts = [(7, 6, 5), (6, 5, 5), (4, 3, 3), (3, 3, 2), (2, 2, 2), (2, 1, 1), (0, 0, 0), (0, 0, 0)]
    x0, unit, bh, gap = 200, 21, 34, 18
    shades = [0.9, 0.68, 0.46]
    for i, (name, total, part) in enumerate(zip(names, totals, parts)):
        y = 290 + i * (bh + gap)
        b.append(t(x0 - 16, y + bh / 2 + 6, name, 16, INK, "600", anchor="end"))
        if total == 0:
            b.append(f'<line x1="{x0}" y1="{y + 4}" x2="{x0}" y2="{y + bh - 4}" stroke="{DROP}" '
                     f'stroke-width="3"/>')
            b.append(t(x0 + 14, y + bh / 2 + 6, "한 번도 말하지 않음", 13, MUTED, anchor="start"))
            continue
        cx = x0
        for k, seg in enumerate(part):
            wseg = seg * unit
            b.append(f'<rect x="{cx}" y="{y}" width="{wseg}" height="{bh}" fill="{KEEP}" '
                     f'opacity="{shades[k]}" stroke="#ffffff" stroke-width="1"/>')
            cx += wseg
        b.append(t(cx + 12, y + bh / 2 + 6, str(total), 15, MARK, "700", anchor="start"))
        if name == "나":
            b.append(t(x0 - 40, y + bh / 2 + 6, "진행자", 12, MARK, "700", anchor="end"))
    ly = 290 + 8 * (bh + gap) + 6
    for k, label in enumerate(["첫 회의", "둘째 회의", "셋째 회의"]):
        b.append(f'<rect x="{x0 + k * 116}" y="{ly}" width="18" height="14" fill="{KEEP}" '
                 f'opacity="{shades[k]}"/>')
        b.append(t(x0 + k * 116 + 26, ly + 13, label, 13, MUTED, anchor="start"))
    b.append(note_box(147, 790, 500, "세기 전에는 조금 더 말한다고만 느꼈다"))
    b.append(caption(W / 2, 912, [
        "여덟 명 가운데 둘이 예순두 번 가운데 서른네 번을 말했다.",
    ], 15))
    return base("세어 본 세 번", "사람별 발언 횟수", "".join(b))


FIGURES = {5: fig_fan, 15: fig_funnel, 29: fig_seats, 37: fig_counts}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild096")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
