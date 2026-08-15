#!/usr/bin/env python3
"""book-098 이미지 페이지 4개의 SVG 생성. figures091.py의 t()/base() 패턴을 따른다.

네 도표의 형식을 모두 다르게 잡았다. 세로 막대 둘, 절차 띠, 겹친 두 원, 단계별 표시 기록이다.
공통 약속은 둘이다. 실제로 나온 것은 진한 색, 나오지 않은 것은 빈 칸이나 옅은 색이다.

사용: python3 figures098.py <출력디렉터리>
"""
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
  <pattern id="hatch2" width="8" height="8" patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
    <line x1="0" y1="0" x2="0" y2="8" stroke="{DROP}" stroke-width="3"/>
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


# ── p5 느낌과 실제 ────────────────────────────────────────────────────
def fig_gap():
    """안다고 느낀 정도와 실제로 떠올린 정도를 세로 막대로 견준다."""
    b = []
    base_y, top_y = 600, 280
    bw = 116
    xs = [252, 452]
    heights = [base_y - top_y, int((base_y - top_y) * 0.44)]
    b.append(f'<line x1="180" y1="{base_y}" x2="620" y2="{base_y}" stroke="{INK}" stroke-width="1.8"/>')
    labels = [("읽고 나서", "안다고 느낀 정도", "거의 다", DROP, 1),
              ("덮고", "실제로 떠올린 정도", "절반 아래", KEEP, 0.85)]
    for x, h, (l1, l2, cap, color, op) in zip(xs, heights, labels):
        y = base_y - h
        if color is DROP:
            b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{h}" fill="{DROP}" opacity="0.8" '
                     f'stroke="{LINE}" stroke-width="1.4"/>')
        else:
            b.append(f'<rect x="{x}" y="{base_y - heights[0]}" width="{bw}" height="{heights[0]}" '
                     f'fill="url(#hatch2)" opacity="0.5" stroke="{DROP}" stroke-width="1.2"/>')
            b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{h}" fill="{KEEP}" opacity="0.9" '
                     f'stroke="{INK}" stroke-width="1.4"/>')
        b.append(t(x + bw / 2, y - 14, cap, 16, MARK, "700"))
        b.append(t(x + bw / 2, base_y + 28, l1, 15, INK))
        b.append(t(x + bw / 2, base_y + 50, l2, 15, INK))
    gx = xs[1] + bw + 34
    b.append(f'<line x1="{gx}" y1="{base_y - heights[0]}" x2="{gx}" y2="{base_y - heights[1]}" '
             f'stroke="{MARK}" stroke-width="2"/>')
    b.append(f'<line x1="{gx - 8}" y1="{base_y - heights[0]}" x2="{gx + 8}" y2="{base_y - heights[0]}" '
             f'stroke="{MARK}" stroke-width="2"/>')
    b.append(f'<line x1="{gx - 8}" y1="{base_y - heights[1]}" x2="{gx + 8}" y2="{base_y - heights[1]}" '
             f'stroke="{MARK}" stroke-width="2"/>')
    b.append(t(gx + 16, (2 * base_y - heights[0] - heights[1]) / 2, "이만큼이", 14, MARK, "700",
               anchor="start"))
    b.append(t(gx + 16, (2 * base_y - heights[0] - heights[1]) / 2 + 20, "착각", 14, MARK, "700",
               anchor="start"))
    b.append(note_box(147, 700, 500, "덮기 전에는 이 차이가 보이지 않는다"))
    b.append(caption(W / 2, 822, [
        "빗금 친 자리는 안다고 느꼈지만 나오지 않은 부분이다.",
        "읽는 동안에는 이 자리가 채워져 있는 것처럼 느껴진다.",
    ], 16))
    return base("느낌과 실제", "안다고 느낀 정도와 떠올린 정도", "".join(b))


# ── p13 네 단계 ───────────────────────────────────────────────────────
def fig_steps():
    """덮기·떠올리기·표시·대조 네 단계와 반복 경로."""
    b = []
    y, bw, bh = 320, 140, 96
    gap = 34
    x0 = (W - (4 * bw + 3 * gap)) / 2
    steps = [("덮는다", "한 절을 읽은 직후", "일 초", False),
             ("떠올린다", "소리 내어 설명한다", "오 분", False),
             ("표시한다", "막힌 자리에 표시만", "표시하는 동안", True),
             ("맞춰 본다", "표시한 자리만 다시 읽는다", "삼 분", False)]
    for i, (name, sub, dur, pick) in enumerate(steps):
        x = x0 + i * (bw + gap)
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="10" fill="#ffffff" '
                 f'stroke="{MARK if pick else INK}" stroke-width="{3 if pick else 1.8}"/>')
        b.append(t(x + bw / 2, y + 40, name, 18, INK, "700"))
        words = sub.split(" ")
        half = (len(words) + 1) // 2
        b.append(t(x + bw / 2, y + 64, " ".join(words[:half]), 12, MUTED))
        b.append(t(x + bw / 2, y + 80, " ".join(words[half:]), 12, MUTED))
        b.append(t(x + bw / 2, y - 14, dur, 14, MARK, "700"))
        if i < 3:
            b.append(f'<line x1="{x + bw + 6}" y1="{y + bh / 2}" x2="{x + bw + gap - 6}" '
                     f'y2="{y + bh / 2}" stroke="{LINE}" stroke-width="2" marker-end="url(#gray)"/>')
    lx0, lx1 = x0 + 3 * bw + 3 * gap - bw / 2, x0 + bw / 2
    b.append(f'<path d="M{lx0},{y + bh + 6} L{lx0},{y + bh + 60} L{lx1},{y + bh + 60} '
             f'L{lx1},{y + bh + 12}" fill="none" stroke="{KEEP}" stroke-width="2" '
             f'marker-end="url(#keep)"/>')
    b.append(t(W / 2, y + bh + 82, "다음 절", 15, KEEP, "700"))
    b.append(note_box(147, 640, 500, "막혔을 때 곧바로 펴지 않는 것이 전부다"))
    b.append(caption(W / 2, 762, [
        "셋째 단계에서 자료를 펴면 그 자리는 다시 익숙해질 뿐이다.",
        "표시만 하고 끝까지 가야 막힌 자리를 모두 찾는다.",
    ], 16))
    return base("네 단계", "덮고 떠올리고 표시하고 맞춰 보기", "".join(b))


# ── p25 겹치는 만큼만 ─────────────────────────────────────────────────
def fig_overlap():
    """비유가 겹치는 부분과 겹치지 않는 부분을 두 원으로 보인다."""
    b = []
    cy, r = 430, 175
    cx1, cx2 = W / 2 - 96, W / 2 + 96
    b.append(f'<circle cx="{cx1}" cy="{cy}" r="{r}" fill="{DROP}" opacity="0.28" stroke="{LINE}" '
             f'stroke-width="1.6"/>')
    b.append(f'<circle cx="{cx2}" cy="{cy}" r="{r}" fill="{DROP}" opacity="0.28" stroke="{LINE}" '
             f'stroke-width="1.6"/>')
    b.append(f'<path d="M{W / 2},{cy - 146} A {r},{r} 0 0 1 {W / 2},{cy + 146} '
             f'A {r},{r} 0 0 1 {W / 2},{cy - 146}" fill="{KEEP}" opacity="0.3" stroke="none"/>')
    b.append(t(cx1 - 30, cy - r - 18, "주사기", 18, INK, "700"))
    b.append(t(cx2 + 30, cy - r - 18, "유압 브레이크", 18, INK, "700"))
    for i, line in enumerate(["누르는 힘이", "액체로 전해진다", "", "한쪽을 누르면",
                              "다른 쪽이 움직인다", "", "공기가 들어가면", "힘이 덜 전해진다"]):
        if line:
            b.append(t(W / 2, cy - 96 + i * 26, line, 13, "#1f3c36", "600"))
    b.append(t(cx1 - 74, cy - 10, "한 번 쓰고", 13, MUTED))
    b.append(t(cx1 - 74, cy + 10, "버린다", 13, MUTED))
    b.append(t(cx2 + 74, cy - 10, "열이 생기고", 13, MUTED))
    b.append(t(cx2 + 74, cy + 10, "부품이 닳는다", 13, MUTED))
    b.append(f'<line x1="{cx2 + 150}" y1="{cy + 40}" x2="{cx2 + 96}" y2="{cy + 22}" stroke="{MARK}" '
             f'stroke-width="1.8" marker-end="url(#mark)"/>')
    b.append(t(cx2 + 116, cy + 62, "비유가 닿지", 13, MARK, "700"))
    b.append(t(cx2 + 116, cy + 80, "않는 자리", 13, MARK, "700"))
    b.append(note_box(147, 700, 500, "겹치는 부분이 셋이면 쓸 만하다"))
    b.append(caption(W / 2, 822, [
        "겹치는 부분이 하나뿐인 비유는 그 하나를 넘어가면 곧바로 어긋난다.",
        "어디까지 같은지 함께 말해야 비유가 잘못 가르치지 않는다.",
    ], 16))
    return base("겹치는 만큼만", "비유가 닿는 자리와 닿지 않는 자리", "".join(b))


# ── p39 삼 분 동안 막힌 세 곳 ─────────────────────────────────────────
def fig_stuck():
    """절차 여덟 단계에 설명량과 막힌 자리를 표시한 기록."""
    b = []
    x, y0, rh = 250, 280, 52
    said = [1, 1, 0, 1, 0, 1, 1, 0]
    notes = {3: "무엇을 하는 단계인지", 5: "왜 시동을 켜는지", 8: "무엇을 확인하는지"}
    b.append(f'<line x1="{x}" y1="{y0 - 10}" x2="{x}" y2="{y0 + 8 * rh - 20}" stroke="{LINE}" '
             f'stroke-width="1.6"/>')
    for i in range(8):
        n = i + 1
        y = y0 + i * rh
        stuck = n in notes
        b.append(f'<rect x="{x - 19}" y="{y - 2}" width="38" height="34" rx="8" fill="#ffffff" '
                 f'stroke="{MARK if stuck else INK}" stroke-width="{2.4 if stuck else 1.6}"/>')
        b.append(t(x, y + 22, str(n), 15, INK))
        length = 190 if said[i] else 52
        b.append(f'<rect x="{x + 34}" y="{y + 8}" width="{length}" height="10" rx="5" '
                 f'fill="{KEEP if said[i] else DROP}" opacity="0.85"/>')
        if stuck:
            mx = x + 34 + length + 22
            b.append(f'<line x1="{mx - 9}" y1="{y + 4}" x2="{mx + 9}" y2="{y + 22}" stroke="{MARK}" '
                     f'stroke-width="2.6"/>')
            b.append(f'<line x1="{mx + 9}" y1="{y + 4}" x2="{mx - 9}" y2="{y + 22}" stroke="{MARK}" '
                     f'stroke-width="2.6"/>')
            b.append(t(mx + 20, y + 20, notes[n], 13, MARK, anchor="start"))
    b.append(t(x, y0 + 8 * rh + 4, "삼 분", 15, MUTED))
    b.append(t(x - 34, y0 - 30, "단계", 13, MUTED, anchor="end"))
    b.append(t(x + 130, y0 - 30, "나온 설명", 13, MUTED))
    b.append(note_box(147, 760, 500, "표시만 하고 끝까지 갔다"))
    b.append(caption(W / 2, 882, [
        "막힌 자리에서 교본을 펴지 않았기 때문에 세 곳을 다 찾았다.",
    ], 16))
    return base("삼 분 동안 막힌 세 곳", "설명 중 표시한 기록", "".join(b))


FIGURES = {5: fig_gap, 13: fig_steps, 25: fig_overlap, 39: fig_stuck}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild098")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
