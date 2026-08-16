#!/usr/bin/env python3
"""book-078 이미지 페이지 5개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 시간과 건수를 눈금으로 넣지 않는다 — 저장소와
자료의 크기에 따라 자릿수가 달라 숫자를 넣으면 본문에 없는 기준이 생긴다.

사용: python3 figures078.py [출력디렉터리]
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
BOX = "#c3ccd0"
MARK = "#a8443a"
BAR = "#4a5c73"
SOFT = "#6f8a6a"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def box(x, y, w, h, fill="#ffffff", stroke=BOX, width=1.4, dash=None):
    extra = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="4" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="{width}"{extra}/>')


def arrow(x1, y1, x2, y2, color=INK, width=1.8, marker="ink"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def base(title, lead, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="fade" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{FADE}"/></marker>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="ink" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{INK}"/></marker>
</defs>
{t(W / 2, 120, title, 31, '#1e2c33', '700')}
<line x1="105" y1="150" x2="689" y2="150" stroke="#dde2e4"/>
{t(W / 2, 182, lead, 16, '#71818a')}
{body}
</svg>'''


def bottom(y, text, size=16):
    return ('<line x1="105" y1="%d" x2="689" y2="%d" stroke="#dde2e4"/>' % (y, y)
            + t(W / 2, y + 38, text, size, "#4c5b64", "700"))


def fig_time_split():
    """1.6 — 시간이 흩어지는 자리."""
    b = []
    cells = [("찾기", "색인", 0.42), ("묶고 정렬하기", "가져오는 양", 0.22),
             ("옮기기", "결과 크기", 0.2), ("부르는 쪽 처리", "조회 횟수", 0.16)]
    x0, y0, total, h = 110, 300, 570, 66
    b.append(t(x0, y0 - 18, "요청 한 번", 17, INK, "700", "start"))
    x = x0
    for name, check, ratio in cells:
        w = total * ratio
        b.append(box(x, y0, w, h, "#f6f8f8"))
        b.append(t(x + w / 2, y0 + 40, name, 14 if w > 110 else 12, INK, "700"))
        b.append(t(x + w / 2, y0 + h + 26, check, 12, MARK, "700"))
        x += w
    y1 = y0 + 170
    b.append(t(x0, y1 - 18, "조회 한 건 × 여러 번", 17, INK, "700", "start"))
    unit, gap = 34, 10
    count = int((total + gap) // (unit + gap))
    for i in range(count):
        b.append(box(x0 + i * (unit + gap), y1, unit, h, "#f6f8f8"))
    b.append(t(x0, y1 + h + 30, "한 건은 빠른데 합이 크다", 13, MARK, "700", "start"))
    b.append(bottom(600, "같은 시간을 두 가지로 나눠 보아야 한다"))
    return base("시간이 흩어지는 자리", "어느 칸이 넓은지에 따라 볼 자리가 다르다", "\n".join(b))


def fig_scan_vs_index():
    """2.6 — 훑기와 색인 찾기."""
    b = []
    for k, (name, x0) in enumerate((("전체 훑기", 132), ("색인 찾기", 432))):
        b.append(t(x0 + 110, 250, name, 18, INK, "700"))
        b.append(box(x0, 272, 220, 250))
        for i in range(10):
            y = 284 + i * 24
            b.append(f'<rect x="{x0 + 42}" y="{y}" width="150" height="14" rx="2" '
                     f'fill="{BAR}" opacity="0.25"/>')
            if k == 0 or i == 6:
                b.append(t(x0 + 28, y + 12, "✓", 13, MARK, "700"))
    b.append(f'<path d="M420,300 L400,340 L420,380 L400,420 L436,{284 + 6 * 24 + 8}" fill="none" '
             f'stroke="{SOFT}" stroke-width="2" marker-end="url(#ink)"/>')
    for i in range(6):
        b.append(f'<line x1="424" y1="{288 + i * 40}" x2="440" y2="{288 + i * 40}" stroke="{SOFT}"/>')
    b.append(t(300, 566, "모든 칸을 확인한다", 13, FADE))
    b.append(t(600, 566, "한 칸만 확인한다", 13, FADE))
    gx, gy, gw, gh = 190, 610, 400, 150
    b.append(f'<line x1="{gx}" y1="{gy + gh}" x2="{gx + gw}" y2="{gy + gh}" stroke="{BOX}"/>')
    b.append(f'<line x1="{gx}" y1="{gy}" x2="{gx}" y2="{gy + gh}" stroke="{BOX}"/>')
    b.append(t(gx + gw / 2, gy + gh + 28, "자료의 양", 13, FADE))
    b.append(f'<path d="M{gx},{gy + gh} L{gx + gw},{gy + 10}" stroke="{MARK}" stroke-width="2" fill="none"/>')
    b.append(t(gx + gw + 10, gy + 14, "훑기", 13, MARK, "700", "start"))
    b.append(f'<path d="M{gx},{gy + gh - 10} C{gx + 150},{gy + gh - 30} {gx + 250},{gy + gh - 36} '
             f'{gx + gw},{gy + gh - 40}" stroke="{SOFT}" stroke-width="2" fill="none"/>')
    b.append(t(gx + gw + 10, gy + gh - 36, "색인", 13, SOFT, "700", "start"))
    b.append(bottom(800, "지금 빠른가가 아니라 늘어도 견디는가를 본다"))
    return base("훑기와 색인 찾기", "자료가 늘 때 두 방식이 갈린다", "\n".join(b))


def fig_two_plans():
    """3.5 — 같은 조회의 두 계획."""
    b = []
    plans = [(150, "흔한 값으로 물었을 때", [("전체 훑기", 1.0), ("거르기", 0.45), ("정렬", 0.3)]),
             (460, "드문 값으로 물었을 때", [("색인 찾기", 0.16), ("자료 읽기", 0.14), ("정렬 없음", 0.0)])]
    bw, bh, step = 190, 60, 108
    for x0, title, steps in plans:
        b.append(t(x0 + bw / 2, 250, title, 16, INK, "700"))
        for i, (name, ratio) in enumerate(steps):
            y = 560 - i * step
            b.append(box(x0, y, bw, bh))
            b.append(t(x0 + bw / 2, y + 37, name, 15, INK, "700"))
            if ratio > 0:
                b.append(f'<rect x="{x0}" y="{y + bh + 8}" width="{bw * ratio}" height="12" rx="3" '
                         f'fill="{BAR}" opacity="0.55"/>')
            else:
                b.append(t(x0 + 6, y + bh + 20, "다루는 건수 없음", 11, FADE, "400", "start"))
    b.append(t(120, 300, "위로 갈수록", 12, FADE, "400", "start"))
    b.append(t(120, 318, "나중 단계", 12, FADE, "400", "start"))
    b.append(t(397, 660, "막대는 그 단계에서 다룬 건수다", 13, FADE))
    b.append(bottom(700, "중간에서 다룬 건수가 시간을 정한다"))
    return base("같은 조회의 두 계획", "같은 조회문이 늘 같은 절차로 처리되지 않는다", "\n".join(b))


def fig_n_plus_one():
    """4.4 — 반복 조회가 늘어나는 모양."""
    b = []
    for k, (label, note, y0) in enumerate((("줄마다 조회", "조회 횟수는 줄 수만큼", 250),
                                           ("모아서 한 번", "줄이 늘어도 한 번", 520))):
        b.append(t(120, y0, label, 17, INK, "700", "start"))
        for i in range(5):
            y = y0 + 24 + i * 34
            b.append(box(120, y, 120, 26, "#f6f8f8"))
        sx, sy = 470, y0 + 60
        b.append(box(sx, sy, 150, 74))
        b.append(t(sx + 75, sy + 44, "저장소", 16, INK, "700"))
        if k == 0:
            for i in range(5):
                y = y0 + 37 + i * 34
                b.append(arrow(246, y, sx - 6, sy + 37, FADE, 1.2, "fade"))
            b.append(t(356, y0 + 14, "한 줄에 한 번", 12, FADE))
        else:
            b.append(f'<path d="M246,{y0 + 37} C300,{y0 + 37} 320,{sy + 37} 380,{sy + 37}" '
                     f'fill="none" stroke="{FADE}" stroke-width="1"/>')
            for i in range(1, 5):
                y = y0 + 37 + i * 34
                b.append(f'<path d="M246,{y} C300,{y} 320,{sy + 37} 380,{sy + 37}" fill="none" '
                         f'stroke="{FADE}" stroke-width="1"/>')
            b.append(arrow(380, sy + 37, sx - 6, sy + 37, INK, 4))
        b.append(t(sx + 160, sy + 42, note, 13, MARK, "700", "start"))
    b.append(bottom(790, "줄 수가 늘 때 달라지는 것은 시간이 아니라 횟수다"))
    return base("반복 조회가 늘어나는 모양", "줄마다 묻는가 한 번에 묻는가", "\n".join(b))


def fig_two_clusters():
    """5.4 — 조회의 두 갈래 분포."""
    b = []
    gx, gy, gw, gh = 170, 260, 460, 380
    b.append(f'<line x1="{gx}" y1="{gy + gh}" x2="{gx + gw}" y2="{gy + gh}" stroke="{BOX}"/>')
    b.append(f'<line x1="{gx}" y1="{gy}" x2="{gx}" y2="{gy + gh}" stroke="{BOX}"/>')
    b.append(t(gx + gw / 2, gy + gh + 34, "한 건에 걸린 시간", 14, FADE))
    b.append(f'<text x="{gx - 26}" y="{gy + gh / 2}" text-anchor="middle" font-size="14" '
             f'fill="{FADE}" transform="rotate(-90 {gx - 26} {gy + gh / 2})">오는 횟수</text>')
    dense = [(0.08, 0.86), (0.11, 0.78), (0.06, 0.72), (0.14, 0.9), (0.1, 0.68),
             (0.16, 0.8), (0.05, 0.62), (0.13, 0.74), (0.09, 0.58), (0.18, 0.7)]
    for fx, fy in dense:
        b.append(f'<circle cx="{gx + gw * fx}" cy="{gy + gh * (1 - fy)}" r="7" fill="{SOFT}" '
                 f'opacity="0.7"/>')
    rare = [(0.72, 0.08), (0.84, 0.05), (0.62, 0.12), (0.92, 0.04)]
    for fx, fy in rare:
        b.append(f'<circle cx="{gx + gw * fx}" cy="{gy + gh * (1 - fy)}" r="17" fill="{MARK}" '
                 f'opacity="0.55"/>')
    b.append(t(gx + gw * 0.12, gy + gh * 0.02, "빠르지만 잦다", 14, SOFT, "700"))
    b.append(t(gx + gw * 0.78, gy + gh * 0.80, "느리지만 드물다", 14, MARK, "700"))
    b.append(f'<path d="M{gx + gw * 0.06},{gy + gh * 0.06} C{gx + gw * 0.3},{gy + gh * 0.55} '
             f'{gx + gw * 0.55},{gy + gh * 0.8} {gx + gw * 0.95},{gy + gh * 0.9}" fill="none" '
             f'stroke="{INK}" stroke-width="1.4" stroke-dasharray="5 4"/>')
    b.append(t(gx + gw * 0.62, gy + gh * 0.6, "시간과 횟수를 곱한", 12, INK))
    b.append(t(gx + gw * 0.62, gy + gh * 0.66, "값이 같은 자리", 12, INK))
    b.append(bottom(700, "곱한 값이 큰 쪽부터 손댄다"))
    return base("조회의 두 갈래 분포", "오래 걸리는 것과 많이 오는 것은 다르다", "\n".join(b))


FIGURES = {
    7: fig_time_split,
    14: fig_scan_vs_index,
    20: fig_two_plans,
    26: fig_n_plus_one,
    33: fig_two_clusters,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild078")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
