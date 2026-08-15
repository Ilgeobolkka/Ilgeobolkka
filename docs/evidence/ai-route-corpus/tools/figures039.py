#!/usr/bin/env python3
"""book-039 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 찍은 수와 값을 눈금으로 넣지 않는다 —
남은 셈 문서가 인쇄소마다 다른 항목을 적어 값으로 견줄 수 없고 짜임만 견줄 수 있기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
IRON, PULP = "#4a5c73", "#8a6a44"
MARK = "#a8443a"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, lead, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="fade" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{FADE}"/></marker>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="ink" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{INK}"/></marker>
</defs>
{t(W/2, 120, title, 31, '#1e2c33', '700')}
<line x1="105" y1="150" x2="689" y2="150" stroke="#dde2e4"/>
{t(W/2, 182, lead, 16, '#71818a')}
{body}
</svg>'''


def bottom(y, text, size=16):
    return ('<line x1="105" y1="%d" x2="689" y2="%d" stroke="#dde2e4"/>' % (y, y)
            + t(W / 2, y + 38, text, size, "#4c5b64", "700"))


def fig_three_copies():
    """1.3 — 같은 책 세 벌."""
    b = []
    names = ["첫째 벌", "둘째 벌", "셋째 벌"]
    blanks = [set(), {6, 7}, {6, 7, 15}]
    x0, y0, cw, rh, rows = 150, 250, 122, 24, 22
    gap = 168
    for k, name in enumerate(names):
        x = x0 + k * gap
        b.append(t(x + cw / 2, y0 - 18, name, 17, INK, "700"))
        b.append(f'<rect x="{x}" y="{y0}" width="{cw}" height="{rows * rh}" '
                 f'fill="#ffffff" stroke="#c3ccd0"/>')
        for r in range(rows):
            if r in blanks[k]:
                continue
            b.append(f'<rect x="{x + 12}" y="{y0 + r * rh + 7}" width="{cw - 24}" height="10" '
                     f'rx="2" fill="{IRON}" opacity="0.35"/>')
    for r in (6, 7):
        y = y0 + r * rh + 12
        b.append(f'<line x1="{x0 + gap + cw}" y1="{y}" x2="{x0 + gap * 2}" y2="{y}" '
                 f'stroke="{MARK}" stroke-width="2"/>')
    rx = x0 + gap * 2 + cw + 18
    b.append(t(rx, y0 + 6 * rh + 20, "같은 자리에서", 14, MARK, "700", "start"))
    b.append(t(rx, y0 + 6 * rh + 42, "둘 다 빠졌다", 14, MARK, "700", "start"))
    y15 = y0 + 15 * rh + 12
    b.append(f'<path d="M{rx + 6},{y15} L{x0 + gap * 2 + cw + 6},{y15}" stroke="{FADE}" '
             f'stroke-width="1.6" marker-end="url(#fade)"/>')
    b.append(t(rx + 14, y15 - 6, "이 벌에서만", 14, FADE, "700", "start"))
    b.append(t(rx + 14, y15 + 16, "빠졌다", 14, FADE, "700", "start"))
    b.append(bottom(830, "빠진 자리가 계보를 그린다"))
    return base("같은 책 세 벌", "같은 자리에서 빠진 둘은 한 줄기다", "\n".join(b))


def fig_metal_and_wood():
    """2.4 — 쇠와 나무가 견디는 만큼."""
    b = []
    x0, xe = 250, 660
    for k, (name, color, cy) in enumerate((("쇠", IRON, 380), ("나무", PULP, 620))):
        b.append(f'<rect x="150" y="{cy - 34}" width="62" height="68" rx="4" '
                 f'fill="#ffffff" stroke="{color}" stroke-width="2"/>')
        b.append(f'<rect x="158" y="{cy - 26}" width="46" height="20" rx="2" fill="{color}"/>')
        b.append(t(181, cy + 24, name, 17, color, "700"))
    b.append(f'<line x1="{x0}" y1="380" x2="{xe}" y2="380" stroke="{IRON}" stroke-width="9" '
             f'stroke-linecap="round"/>')
    b.append(f'<line x1="212" y1="380" x2="{x0}" y2="380" stroke="{IRON}" stroke-width="2"/>')
    b.append(f'<line x1="212" y1="620" x2="{x0}" y2="620" stroke="{PULP}" stroke-width="2"/>')
    mid = 452
    for i in range(28):
        xa = x0 + i * (mid - x0) / 28
        xb = x0 + (i + 1) * (mid - x0) / 28
        wdt = 9 - 6 * i / 27
        b.append(f'<line x1="{xa}" y1="620" x2="{xb + 1}" y2="620" stroke="{PULP}" '
                 f'stroke-width="{wdt:.2f}"/>')
    b.append(f'<line x1="{mid}" y1="594" x2="{mid}" y2="646" stroke="{PULP}" stroke-width="2.5"/>')
    b.append(t(mid + 12, 666, "가장자리가 뭉개진다", 14, PULP, "700", "start"))
    b.append(f'<line x1="{mid}" y1="316" x2="{mid}" y2="560" stroke="{FADE}" '
             f'stroke-width="1.4" stroke-dasharray="5 5"/>')
    b.append(t((x0 + mid) / 2, 302, "두 재료가 같이 쓰이는 구간", 14, FADE, "700"))
    b.append(t((mid + xe) / 2, 302, "쇠만 남는 구간", 14, FADE, "700"))
    b.append(bottom(830, "견디는 만큼이 쓰임을 정한다"))
    return base("쇠와 나무가 견디는 만큼", "오래 쓸 것과 한 번 쓸 것을 갈라 만들었다", "\n".join(b))


def fig_where_they_stood():
    """3.3 — 인쇄소가 자리 잡은 곳."""
    b = []
    wx, wy, ww, wh = 288, 280, 372, 420
    b.append('<path d="M262,240 C 218,360 206,480 176,660" fill="none" '
             f'stroke="#9fb7c4" stroke-width="15" stroke-linecap="round" opacity="0.5"/>')
    villages = [(118, 322), (146, 380), (118, 438)]
    for vx, vy in villages:
        b.append(f'<rect x="{vx}" y="{vy}" width="34" height="26" rx="3" '
                 f'fill="#ffffff" stroke="{PULP}" stroke-width="1.8"/>')
    b.append(t(118, 306, "종이 마을", 15, PULP, "700", "start"))
    b.append(f'<rect x="{wx}" y="{wy}" width="{ww}" height="{wh}" rx="8" fill="#fbfaf7" '
             f'stroke="{INK}" stroke-width="4"/>')
    for gy in (370, 460, 550, 640):
        b.append(f'<line x1="{wx}" y1="{gy}" x2="{wx + ww}" y2="{gy}" stroke="#e6eaec"/>')
    for gx in (382, 476, 570):
        b.append(f'<line x1="{gx}" y1="{wy}" x2="{gx}" y2="{wy + wh}" stroke="#e6eaec"/>')
    b.append(f'<rect x="{wx - 9}" y="474" width="18" height="56" fill="#ffffff" '
             f'stroke="{INK}" stroke-width="3"/>')
    b.append(t(wx + 16, 466, "서쪽 문", 15, INK, "700", "start"))
    b.append(f'<path d="M{wx - 14},502 L192,414" stroke="{PULP}" stroke-width="7" '
             f'stroke-linecap="round" opacity="0.85"/>')
    b.append(t(140, 600, "종이가 들어오는 길", 15, PULP, "700", "start"))
    b.append(f'<path d="M212,586 L232,458" stroke="{PULP}" stroke-width="1.2" opacity="0.7"/>')
    shops = [(306, 330), (310, 400), (314, 596), (348, 366), (352, 546),
             (510, 402), (536, 512)]
    for sx, sy in shops:
        b.append(f'<rect x="{sx}" y="{sy}" width="28" height="24" rx="3" fill="{IRON}" '
                 f'opacity="0.8"/>')
    b.append(t(576, 396, "관청 가까이", 15, FADE, "700", "start"))
    b.append(f'<path d="M572,392 L546,406" stroke="{FADE}" stroke-width="1.2"/>')
    b.append(f'<path d="M576,404 L568,510" stroke="{FADE}" stroke-width="1.2"/>')
    b.append(t(wx + ww / 2, wy + wh + 40, "작은 네모 하나가 인쇄소 하나다", 15, FADE))
    b.append(bottom(830, "자리는 원료와 손님이 갈라 정한다"))
    return base("인쇄소가 자리 잡은 곳", "무거운 것 가까이에 선다", "\n".join(b))


def fig_gate_before():
    """4.2 — 찍기 전에 거치는 문."""
    b = []
    steps = ["글을 받는다", "허락을 받는다", "판을 짠다", "찍는다", "판다"]
    bw, bh = 210, 62
    xs = [150, 222, 294, 366, 438]
    ys = [240, 352, 464, 576, 688]
    for k, name in enumerate(steps):
        x, y = xs[k], ys[k]
        gate = k == 1
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="9" fill="#ffffff" '
                 f'stroke="{MARK if gate else INK}" stroke-width="{3.5 if gate else 1.4}"/>')
        b.append(t(x + bw / 2, y + 41, name, 17, INK, "700"))
        if k < 4:
            b.append(f'<path d="M{x + bw / 2},{y + bh} L{xs[k + 1] + bw / 2},{ys[k + 1] - 10}" '
                     f'stroke="{FADE}" stroke-width="1.8" marker-end="url(#fade)"/>')
    b.append(f'<line x1="{xs[1] + bw - 40}" y1="{ys[1] - 22}" x2="{xs[1] + bw - 40}" '
             f'y2="{ys[1] - 6}" stroke="{MARK}" stroke-width="2.5"/>')
    b.append(t(xs[1] + bw - 40, ys[1] - 30, "문", 18, MARK, "700"))
    b.append(f'<path d="M{xs[1] + bw + 8},{ys[1] + 33} L{xs[1] + bw + 92},{ys[1] + 33}" '
             f'stroke="{MARK}" stroke-width="1.8" marker-end="url(#mark)"/>')
    b.append(t(xs[1] + bw + 100, ys[1] + 38, "되돌려 보낸다", 15, MARK, "700", "start"))
    b.append(f'<path d="M{xs[4] - 8},{ys[4] + 31} C {xs[4] - 90},{ys[4] + 31} '
             f'{xs[4] - 120},{ys[4] + 74} 250,{ys[4] + 74}" fill="none" stroke="{FADE}" '
             f'stroke-width="1.4" stroke-dasharray="6 6" marker-end="url(#fade)"/>')
    b.append(t(244, ys[4] + 79, "거두려 해도 닿지 않는다", 14, FADE, "700", "end"))
    b.append(bottom(830, "문은 앞쪽에만 세울 수 있다"))
    return base("찍기 전에 거치는 문", "나온 뒤에 못 거두면 나오기 전에 본다", "\n".join(b))


def fig_ledger_gaps():
    """5.4 — 한 판이 남긴 셈."""
    b = []
    cols = ["판 이름", "종이 들인 날", "찍은 날", "삯을 준 사람", "받는 이"]
    fill = [1.0, 0.9, 0.78, 0.5, 0.25]
    x0, y0, cw, rh, rows = 130, 300, 96, 50, 8
    for j, name in enumerate(cols):
        b.append(t(x0 + j * cw + cw / 2, y0 - 16, name, 14, INK, "700"))
    for j in range(len(cols)):
        for r in range(rows):
            x, y = x0 + j * cw, y0 + r * rh
            b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{rh}" fill="#ffffff" '
                     f'stroke="#dbe1e3"/>')
            if (r + 1) / rows <= fill[j] + 1e-9:
                b.append(f'<rect x="{x + 10}" y="{y + 17}" width="{cw - 20}" height="16" '
                         f'rx="3" fill="{IRON}" opacity="0.6"/>')
    b.append(f'<rect x="{x0}" y="{y0}" width="{cw * 5}" height="{rh * rows}" fill="none" '
             f'stroke="{INK}" stroke-width="1.8"/>')
    asks = [("언제 만들었나", 1.0), ("누가 만들었나", 1.0), ("어디로 갔나", 0.3)]
    for k, (label, op) in enumerate(asks):
        y = y0 + 70 + k * 112
        b.append(f'<path d="M{x0 + cw * 5 + 10},{y} L{x0 + cw * 5 + 58},{y}" stroke="{INK}" '
                 f'stroke-width="1.6" opacity="{op}" marker-end="url(#ink)"/>')
        b.append(t(x0 + cw * 5 + 66, y + 5, label, 14, INK, "700", "start")
                 .replace('fill="#27353a"', f'fill="{INK}" opacity="{op}"'))
    b.append(t(397, y0 + rh * rows + 44, "채워진 칸이 많을수록 그 항목이 자주 적혔다", 15, FADE))
    b.append(bottom(830, "빈 칸이 물음의 한계를 긋는다"))
    return base("한 판이 남긴 셈", "무엇을 적었는지가 무엇을 알 수 있는지를 정한다", "\n".join(b))


def fig_printed_and_read():
    """6.3 — 찍힌 수와 읽힌 수."""
    b = []
    bars = [("찍은 수", 380, True), ("팔린 수", 300, False),
            ("펼쳐진 수", 230, False), ("읽힌 수", 160, False)]
    leaks = ["되가져온 것", "사서 쌓아 둔 것", "펼치고 만 것"]
    x = 170
    y = 280
    for k, (label, w, solid) in enumerate(bars):
        dash = "" if solid else ' stroke-dasharray="8 5"'
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="78" rx="8" fill="{IRON}" '
                 f'opacity="{0.75 - k * 0.17}"/>')
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="78" rx="8" fill="none" '
                 f'stroke="{INK}" stroke-width="{2.2 if solid else 1.8}"{dash}/>')
        b.append(t(x + w / 2, y + 48, label, 18,
                   "#ffffff" if k < 2 else INK, "700"))
        if k < 3:
            b.append(f'<path d="M{x + w + 12},{y + 40} L{x + w + 62},{y + 70}" stroke="{FADE}" '
                     f'stroke-width="1.6" marker-end="url(#fade)"/>')
            b.append(t(x + w + 70, y + 78, leaks[k], 14, FADE, "700", "start"))
        y += 124
    b.append(f'<line x1="{x}" y1="262" x2="{x}" y2="306" stroke="{MARK}" stroke-width="3"/>')
    b.append(t(x + 12, 252, "여기까지만 문서에 남는다", 14, MARK, "700", "start"))
    b.append(t(x, 786, "테두리가 점선인 띠는 문서로 셀 수 없는 수다", 15, FADE, "400", "start"))
    b.append(bottom(830, "아래로 갈수록 자료가 없다"))
    return base("찍힌 수와 읽힌 수", "셀 수 있는 것은 맨 위 하나뿐이다", "\n".join(b))


FIGURES = {
    7: fig_three_copies,
    15: fig_metal_and_wood,
    20: fig_where_they_stood,
    26: fig_gate_before,
    37: fig_ledger_gaps,
    44: fig_printed_and_read,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild039")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
