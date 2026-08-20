#!/usr/bin/env python3
"""book-091 이미지 페이지 6개의 SVG 생성. figures070.py의 t()/base() 패턴을 따른다.

여섯 도표는 서로 다른 형식을 쓴다. 하루의 시간을 다루는 책이라 띠 그림이 겹치기 쉬워서, 같은 것을
두 번 그리지 않도록 선그래프·상자 비교·띠·의존 그래프·서식·곡선으로 나눴다. 공통 약속은 하나다.
잃는 시간은 빗금, 고른 자리는 굵은 테두리로 표시한다.

사용: python3 figures091.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 남기는 것·실제로 일한 구간
DROP = "#c3ccd0"          # 사라지는 것·고르지 않은 자리
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
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <pattern id="hatch" width="8" height="8" patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
    <line x1="0" y1="0" x2="0" y2="8" stroke="{LINE}" stroke-width="2.4" opacity="0.55"/>
  </pattern>
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


def box(x, y, w, h, label, fill="#ffffff", stroke=INK, size=15, width=1.8):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="{fill}" stroke="{stroke}" '
            f'stroke-width="{width}"/>' + t(x + w / 2, y + h / 2 + 5, label, size, INK))


def axes(x0, y0, x1, y1, xlabel, ylabel):
    """왼쪽 아래를 원점으로 하는 축 두 개."""
    return (f'<line x1="{x0}" y1="{y0}" x2="{x0}" y2="{y1}" stroke="{INK}" stroke-width="1.8"/>'
            f'<line x1="{x0}" y1="{y1}" x2="{x1}" y2="{y1}" stroke="{INK}" stroke-width="1.8"/>'
            + t((x0 + x1) / 2, y1 + 42, xlabel, 16, MUTED)
            + f'<text x="{x0 - 26}" y="{(y0 + y1) / 2}" text-anchor="middle" font-size="16" '
              f'fill="{MUTED}" transform="rotate(-90 {x0 - 26} {(y0 + y1) / 2})">{ylabel}</text>')


# ── p8 다시 붙는 데 걸리는 시간 ──────────────────────────────────────
def fig_recovery():
    """전환마다 몰입이 떨어지고 다시 올라가는 선그래프. 오르는 구간이 사라진 시간이다."""
    b = []
    x0, x1, top, bot = 150, 660, 290, 560
    b.append(axes(x0, top, x1, bot, "시간", "일에 붙어 있는 정도"))
    # (오르기 시작 x, 평평해지는 x, 떨어지는 x) 네 구간. 모두 축 안에 들어가야 한다.
    spans = [(x0, x0 + 60, x0 + 150), (x0 + 150, x0 + 215, x0 + 290),
             (x0 + 290, x0 + 355, x0 + 430), (x0 + 430, x0 + 495, x1)]
    labels = ["전화", "질문", "다른 일"]
    points = []
    for rise_start, plateau, fall in spans:
        points += [(rise_start, bot), (plateau, top + 30), (fall, top + 30), (fall, bot)]
    b.append('<polyline points="' + " ".join(f"{x:.0f},{y:.0f}" for x, y in points[:-1])
             + f'" fill="none" stroke="{KEEP}" stroke-width="3"/>')
    for rise_start, plateau, _ in spans:
        b.append(f'<path d="M{rise_start},{bot} L{plateau},{top + 30} L{plateau},{bot} z" fill="url(#hatch)"/>')
    b.append(t(x0 + 30, bot + 30, "빗금이 사라진 시간", 14, MUTED))
    for (_, _, fall), label in zip(spans, labels):
        b.append(f'<line x1="{fall}" y1="{top - 42}" x2="{fall}" y2="{top + 16}" stroke="{MARK}" '
                 f'stroke-width="2.2" marker-end="url(#mark)"/>')
        b.append(t(fall, top - 52, label, 15, MARK, "700"))
    b.append(t(spans[0][1] + 10, top + 66, "실제로 일한 구간", 15, KEEP, "700", anchor="start"))
    b.append(note_box(147, 660, 500, "한 번 떨어지면 다시 올라가야 한다"))
    b.append(caption(W / 2, 782, [
        "전환할 때마다 어디까지 했는지 찾고 앞을 다시 읽는 시간이 든다.",
        "이 시간은 계획에 적히지 않지만 하루에서 빠져나간다.",
    ], 16))
    return base("다시 붙는 데 걸리는 시간", "일을 바꾼 뒤 몰입이 회복되는 과정", "".join(b))


# ── p16 같은 일, 세 가지 굵기 ─────────────────────────────────────────
def fig_grain():
    """하나의 일을 세 수준으로 쪼갠 결과. 가운데 줄에만 굵은 테두리를 둘렀다."""
    b = []
    rows = [
        (300, [("표지 작업", "")], "한 덩어리", False),
        (450, [("시안 세 개", "그리기"), ("한 개", "고르기"), ("고친 뒤", "넘기기")], "세 단위", True),
        (600, [], "열두 단위", False),
    ]
    for y, items, label, pick in rows:
        b.append(box(120, y, 118, 76, "가을호 표지", "#eef1f2", LINE, 15))
        if items:
            w = 132
            for i, (first, second) in enumerate(items):
                x = 268 + i * (w + 14)
                b.append(box(x, y, w, 76, "", "#ffffff", KEEP if pick else INK, 14,
                             2.6 if pick else 1.8))
                if second:
                    b.append(t(x + w / 2, y + 34, first, 14))
                    b.append(t(x + w / 2, y + 55, second, 14))
                else:
                    b.append(t(x + w / 2, y + 44, first, 15))
        else:
            for i in range(12):
                x = 268 + (i % 6) * 68
                yy = y + (i // 6) * 40
                b.append(f'<rect x="{x}" y="{yy}" width="60" height="32" rx="5" fill="#ffffff" '
                         f'stroke="{LINE}" stroke-width="1.4"/>')
            for i, small in enumerate(["자료 열기", "연필 고르기", "선 그리기"]):
                b.append(t(268 + i * 68 + 30, y + 20, small, 10, MUTED))
            b.append(f'<circle cx="700" cy="{y + 38}" r="15" fill="none" stroke="{MARK}" stroke-width="2"/>')
            b.append(f'<line x1="700" y1="{y + 38}" x2="700" y2="{y + 28}" stroke="{MARK}" stroke-width="2"/>')
            b.append(f'<line x1="700" y1="{y + 38}" x2="707" y2="{y + 42}" stroke="{MARK}" stroke-width="2"/>')
            b.append(t(700, y + 76, "적는 시간이", 13, MARK))
            b.append(t(700, y + 92, "더 길다", 13, MARK))
        b.append(t(179, y + 100, label, 15, MUTED))
    b.append(note_box(147, 760, 500, "한 번에 끝낼 수 있는 크기까지만"))
    b.append(caption(W / 2, 882, [
        "덜 쪼개면 시작할 자리가 없고 지나치게 쪼개면 목록이 일이 된다.",
    ], 16))
    return base("같은 일, 세 가지 굵기", "하나의 작업을 세 수준으로 쪼갠 결과", "".join(b))


# ── p24 같은 일을 모아 두면 ───────────────────────────────────────────
def fig_batching():
    """같은 구성의 하루를 순서만 바꿔 배열하고 종류가 바뀌는 경계를 세모로 센다."""
    b = []
    rows = [
        (320, "흩어진 하루", ["메일", "교정", "메일", "회의", "교정", "메일", "교정", "메일"]),
        (500, "묶은 하루", ["교정", "교정", "교정", "회의", "메일", "메일", "메일", "메일"]),
    ]
    fill = {"메일": "#e7eef0", "교정": "#dfeae6", "회의": "#f1ece1"}
    x0, w, h = 150, 64, 74
    for y, label, cells in rows:
        b.append(t(x0 - 12, y - 34, label, 16, INK, "700", anchor="start"))
        marks = 0
        for i, cell in enumerate(cells):
            x = x0 + i * w
            b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill[cell]}" '
                     f'stroke="{LINE}" stroke-width="1.2"/>')
            b.append(t(x + w / 2, y + h / 2 + 6, cell, 14, INK))
            if i and cells[i] != cells[i - 1]:
                marks += 1
                b.append(f'<path d="M{x - 9},{y - 8} L{x + 9},{y - 8} L{x},{y - 24} z" fill="{MARK}"/>')
        b.append(t(x0 + len(cells) * w + 18, y + h / 2 + 6,
                   f"세모 {'일곱' if marks == 7 else '둘'}", 16, MARK, "700", anchor="start"))
    b.append(t(W / 2, 660, "두 하루의 칸 수와 종류별 개수는 같다", 15, MUTED))
    b.append(note_box(147, 700, 500, "한 일의 양은 같다"))
    b.append(caption(W / 2, 822, [
        "같은 종류를 붙여 두면 전환이 일곱 번에서 두 번으로 줄어든다.",
        "줄어든 다섯 번이 되붙는 시간을 치르지 않은 만큼이다.",
    ], 16))
    return base("같은 일을 모아 두면", "같은 구성의 하루를 순서만 바꿔 배열한 결과", "".join(b))


# ── p31 무엇이 무엇을 기다리는가 ──────────────────────────────────────
def fig_dependency():
    """선후 제약을 방향 그래프로 그린다. 들어오는 화살표가 없는 자리만 오늘 고를 수 있다."""
    b = []
    nodes = {
        "원고 확정": (185, 300), "사진 고르기": (185, 410), "조판": (395, 355),
        "교정": (605, 355), "인쇄 넘기기": (605, 560),
        "표지 방향 정하기": (185, 560), "표지 시안": (395, 560),
    }
    edges = [("원고 확정", "조판"), ("사진 고르기", "조판"), ("조판", "교정"),
             ("교정", "인쇄 넘기기"), ("표지 방향 정하기", "표지 시안"), ("표지 시안", "인쇄 넘기기")]
    roots = {"원고 확정", "사진 고르기", "표지 방향 정하기"}
    w, h = 150, 62
    for a, c in edges:
        (ax, ay), (cx, cy) = nodes[a], nodes[c]
        sx, sy = ax + w / 2, ay + h / 2
        ex, ey = cx - w / 2 - 10, cy + h / 2
        if abs(ay - cy) > 90:                      # 세로로 꺾여 들어가는 간선
            sx, sy = ax, ay + h
            ex, ey = cx, cy - 10
        b.append(f'<line x1="{sx}" y1="{sy}" x2="{ex}" y2="{ey}" stroke="{LINE}" stroke-width="2" '
                 f'marker-end="url(#gray)"/>')
    for label, (x, y) in nodes.items():
        root = label in roots
        b.append(box(x - w / 2, y, w, h, label, "#ffffff",
                     KEEP if root else (DROP if label == "인쇄 넘기기" else INK), 15,
                     2.8 if root else 1.6))
    b.append(t(185, 660, "오늘 고를 수 있는 자리", 16, KEEP, "700"))
    b.append(f'<line x1="185" y1="640" x2="185" y2="628" stroke="{KEEP}" stroke-width="2"/>')
    b.append(note_box(147, 700, 500, "막고 있는 것부터"))
    b.append(caption(W / 2, 822, [
        "들어오는 화살표가 없는 셋만 오늘 시작할 수 있다.",
        "나머지는 앞의 결과를 기다리는 자리라 붙들어도 진도가 나가지 않는다.",
    ], 16))
    return base("무엇이 무엇을 기다리는가", "작업 사이의 선후 관계", "".join(b))


# ── p37 목록 한 장 ────────────────────────────────────────────────────
def fig_daysheet():
    """아침에 적는 종이 한 장의 서식. 위에서 아래로 갈수록 자리의 확정도가 낮아진다."""
    b = []
    px, py, pw, ph = 214, 250, 366, 560
    b.append(f'<rect x="{px}" y="{py}" width="{pw}" height="{ph}" rx="6" fill="#fdfdfb" '
             f'stroke="{INK}" stroke-width="2"/>')
    cuts = [py + 216, py + 372]
    for cy in cuts:
        b.append(f'<line x1="{px}" y1="{cy}" x2="{px + pw}" y2="{cy}" stroke="{LINE}"/>')

    b.append(t(px + 18, py + 34, "오늘의 블록", 16, INK, "700", anchor="start"))
    blocks = [("아홉 시—열 시 반", "교정 열두 쪽"), ("열한 시", "표지 방향 정하기"),
              ("네 시", "회신 묶어서")]
    for i, (when, what) in enumerate(blocks):
        y = py + 74 + i * 44
        if i == 0:
            b.append(t(px + 12, y + 5, "★", 14, MARK, anchor="start"))
        b.append(t(px + 30, y + 5, when, 13, MUTED, anchor="start"))
        b.append(t(px + pw - 18, y + 5, what, 14, INK, anchor="end"))
        b.append(f'<line x1="{px + 30}" y1="{y + 16}" x2="{px + pw - 18}" y2="{y + 16}" '
                 f'stroke="#dfe4e6"/>')

    b.append(t(px + 18, cuts[0] + 34, "오늘 하지 않음", 16, INK, "700", anchor="start"))
    for i, (what, why) in enumerate([("교정 나머지", "( 조판 뒤 )"), ("연간 계획 초안", "(          )")]):
        y = cuts[0] + 74 + i * 40
        b.append(t(px + 30, y + 5, what, 14, MUTED, anchor="start"))
        b.append(t(px + pw - 18, y + 5, why, 13, MUTED, anchor="end"))
        b.append(f'<line x1="{px + 30}" y1="{y + 16}" x2="{px + pw - 18}" y2="{y + 16}" '
                 f'stroke="#dfe4e6"/>')

    b.append(t(px + 18, cuts[1] + 34, "틈이 나면", 16, INK, "700", anchor="start"))
    for i in range(4):
        y = cuts[1] + 66 + i * 30
        b.append(f'<line x1="{px + 30}" y1="{y}" x2="{px + 30 + (pw - 48) / 2}" y2="{y}" '
                 f'stroke="#dfe4e6" stroke-width="1.4"/>')
    b.append(t(px + 30, cuts[1] + 66 - 8, "확인 전화 · 짧은 회신 · 자료 하나 찾아 두기", 12, MUTED,
               anchor="start"))

    ax = px + pw + 42
    b.append(f'<line x1="{ax}" y1="{py + 20}" x2="{ax}" y2="{py + ph - 20}" stroke="{MARK}" '
             f'stroke-width="2" marker-end="url(#mark)"/>')
    b.append(f'<text x="{ax + 22}" y="{py + ph / 2}" text-anchor="middle" font-size="15" fill="{MARK}" '
             f'transform="rotate(-90 {ax + 22} {py + ph / 2})">위에서부터 정해진다</text>')
    b.append(note_box(147, 850, 500, "자리를 받은 줄만 오늘 한다"))
    return base("목록 한 장", "아침 십 분 동안 적는 하루 계획", "".join(b))


# ── p45 어느 지점을 넘으면 늦어진다 ───────────────────────────────────
def fig_overhead():
    """착수 비용은 내려가고 관리 비용은 올라가 합이 골짜기를 이룬다."""
    b = []
    x0, x1, top, bot = 175, 655, 285, 610
    b.append(axes(x0, top, x1, bot, "쪼갠 개수", "하루에 걸린 시간"))
    b.append(t(x0 + 30, bot + 22, "적음", 14, MUTED))
    b.append(t(x1 - 30, bot + 22, "많음", 14, MUTED))
    n = 41

    def curve(fn, color, width, dash=""):
        pts = []
        for i in range(n):
            u = i / (n - 1)
            x = x0 + (x1 - x0) * u
            pts.append((x, bot - (bot - top) * fn(u)))
        return ('<polyline points="' + " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
                + f'" fill="none" stroke="{color}" stroke-width="{width}"{dash}/>'), pts

    start = lambda u: 0.60 * (1 - u) ** 1.6 + 0.05          # noqa: E731 착수에 드는 시간
    admin = lambda u: 0.05 + 0.62 * u ** 2.1                # noqa: E731 목록을 다루는 시간
    poly, _ = curve(start, DROP, 2.4)
    b.append(poly)
    poly, _ = curve(admin, DROP, 2.4)
    b.append(poly)
    poly, total = curve(lambda u: start(u) + admin(u), MARK, 3.4)
    b.append(poly)
    b.append(t(x1 - 6, bot - (bot - top) * start(1.0) - 14, "착수에 드는 시간", 14, MUTED, anchor="end"))
    b.append(t(x1 - 6, bot - (bot - top) * admin(1.0) - 16, "목록을 다루는 시간", 14, MUTED, anchor="end"))

    low = max(total, key=lambda pt: pt[1])                  # 합이 가장 작은 자리(화면에서 가장 아래)
    b.append(f'<circle cx="{low[0]:.1f}" cy="{low[1]:.1f}" r="6" fill="{MARK}"/>')
    b.append(f'<line x1="{low[0]:.1f}" y1="{low[1]:.1f}" x2="{low[0]:.1f}" y2="{bot}" '
             f'stroke="{MARK}" stroke-width="1.6" stroke-dasharray="5 5"/>')
    b.append(t(low[0], bot + 74, "알맞은 굵기", 16, MARK, "700"))
    b.append(t(x0 + 78, top - 18, "덩어리째 남는다", 14, MUTED))
    b.append(t(x1 - 78, top - 18, "목록이 일이 된다", 14, MUTED))
    b.append(note_box(147, 700, 500, "가운데가 가장 짧다"))
    b.append(caption(W / 2, 822, [
        "쪼갤수록 시작은 쉬워지지만 목록을 다루는 시간이 함께 늘어난다.",
        "둘을 더한 시간이 가장 짧은 자리가 그 일의 알맞은 굵기다.",
    ], 16))
    return base("어느 지점을 넘으면 늦어진다", "쪼갠 개수와 하루에 걸린 시간", "".join(b))


FIGURES = {8: fig_recovery, 16: fig_grain, 24: fig_batching,
           31: fig_dependency, 37: fig_daysheet, 45: fig_overhead}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild091")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
