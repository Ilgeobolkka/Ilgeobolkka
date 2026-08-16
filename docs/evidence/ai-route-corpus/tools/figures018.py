#!/usr/bin/env python3
"""book-018 이미지 페이지 네 개의 SVG를 생성한다.

사용: python3 figures018.py <출력디렉터리>

네 도표에서 색의 뜻을 고정했다 — 안 된 것은 언제나 갈색이고 무언가로 이어진 것은 언제나 초록이다.
이 권이 세는 단위가 「안 된 백열두 번」과 「이어진 열여덟 번」이므로, 그림마다 색의 뜻이 달라지면 네
장이 서로 다른 것을 세는 그림이 된다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#2b3238"
MUTED = "#77828a"
LINE = "#c3cbd1"
PALE = "#dfe3e6"
SOIL = "#9a7250"
LEAF = "#4b7f5a"


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#fdfcf9"/>
<defs>
  <marker id="m" markerWidth="9" markerHeight="9" refX="8" refY="3" orient="auto">
    <path d="M0,0 L0,6 L8,3 z" fill="{MUTED}"/>
  </marker>
</defs>
<text x="397" y="108" text-anchor="middle" font-size="34" font-weight="700" fill="{INK}">{title}</text>
<text x="397" y="150" text-anchor="middle" font-size="18" fill="{MUTED}">{subtitle}</text>
{body}
<text x="397" y="1072" text-anchor="middle" font-size="14" fill="#98a1a7">작은 실패를 기르는 정원</text>
</svg>'''


def text(x, y, value, size=17, fill=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}">{value}</text>')


def closing(y, value):
    return ('<rect x="97" y="%d" width="600" height="58" rx="16" fill="#f1efe8" stroke="%s"/>'
            % (y, LINE)) + text(397, y + 37, value, 20, INK, "700")


# ── p8 세 물음으로 가른다 ────────────────────────────────────────────
QUESTIONS = [("되돌릴 수 있나", "아니다"), ("남에게 값이 가나", "그렇다"),
             ("다시 해 볼 수 있나", "아니다")]


def fig_three_questions():
    b = [text(397, 205, "이 방법의 대상인지 먼저 가린다", 19, INK, "700")]
    y = 300
    b.append(f'<rect x="42" y="{y - 22}" width="136" height="44" rx="20" fill="#ffffff" '
             f'stroke="{INK}" stroke-width="2"/>')
    b.append(text(110, y + 5, "뜻대로 안 된 일 하나", 11, INK, "700"))
    dx, half = 150, 52
    mxs = [230 + dx * i for i in range(3)]
    prev = 178
    for (q, no_label), mx in zip(QUESTIONS, mxs):
        b.append(f'<line x1="{prev}" y1="{y}" x2="{mx - half}" y2="{y}" stroke="{MUTED}" '
                 f'stroke-width="1.6"/>')
        b.append(f'<path d="M{mx} {y - 38} L{mx + half} {y} L{mx} {y + 38} L{mx - half} {y} Z" '
                 f'fill="#ffffff" stroke="{INK}" stroke-width="1.8"/>')
        b.append(text(mx, y + 4, q, 10.5, INK))
        b.append(f'<line x1="{mx}" y1="{y + 38}" x2="{mx}" y2="{y + 150}" stroke="{MUTED}" '
                 f'stroke-width="1.4" stroke-dasharray="5,5"/>')
        b.append(text(mx + 24, y + 64, no_label, 11, MUTED))
        prev = mx + half
    b.append(f'<line x1="{prev}" y1="{y}" x2="{prev + 38}" y2="{y}" stroke="{MUTED}" '
             f'stroke-width="1.6" marker-end="url(#m)"/>')
    gx = prev + 44
    b.append(f'<rect x="{gx}" y="{y - 34}" width="124" height="68" rx="10" fill="{LEAF}" '
             f'fill-opacity="0.16" stroke="{LEAF}" stroke-width="2"/>')
    b.append(text(gx + 62, y - 8, "작은 실패", 15, LEAF, "700"))
    b.append(text(gx + 62, y + 16, "이 정원에 심는다", 12, INK))
    # 아래로 모이는 회색 상자
    gy = y + 150
    b.append(f'<line x1="{mxs[0]}" y1="{gy}" x2="{mxs[2]}" y2="{gy}" stroke="{MUTED}" '
             f'stroke-width="1.4" stroke-dasharray="5,5"/>')
    mid = (mxs[0] + mxs[2]) / 2
    b.append(f'<line x1="{mid}" y1="{gy}" x2="{mid}" y2="{gy + 40}" stroke="{MUTED}" '
             f'stroke-width="1.4" stroke-dasharray="5,5" marker-end="url(#m)"/>')
    b.append(f'<rect x="{mid - 100}" y="{gy + 46}" width="200" height="62" rx="10" fill="{PALE}" '
             f'stroke="{MUTED}" stroke-width="1.6"/>')
    b.append(text(mid, gy + 74, "이 방법의 대상이 아님", 14, INK, "700"))
    b.append(text(mid, gy + 96, "따로 다룬다", 12, MUTED))
    # 화분 둘 — 초록 상자 아래와 회색 상자 아래
    px = gx + 62
    b.append(f'<path d="M{px - 26} 640 L{px - 18} 682 L{px + 18} 682 L{px + 26} 640 Z" '
             f'fill="#ffffff" stroke="{SOIL}" stroke-width="2"/>')
    b.append(f'<path d="M{px} 640 C{px} 618, {px + 16} 616, {px + 18} 606" fill="none" '
             f'stroke="{LEAF}" stroke-width="3"/>')
    b.append(text(px, 704, "태운 빵", 12, MUTED))
    b.append(f'<path d="M{mid - 26} 640 L{mid - 18} 682 L{mid + 18} 682 L{mid + 26} 640 Z" '
             f'fill="#ffffff" stroke="{MUTED}" stroke-width="2"/>')
    b.append(text(mid, 704, "남의 일정을 어그러뜨린 일", 12, MUTED))
    b.append(closing(790, "셋 다 예일 때만 작은 실패다"))
    return base("세 물음으로 가른다", "1장 · 크기를 가리는 절차", "\n".join(b))


# ── p11 백서른 가운데 ────────────────────────────────────────────────
def fig_one_thirty():
    b = [text(397, 205, "한 해 동안 심은 것과 그 뒤", 19, INK, "700")]
    # 왼쪽 씨앗 백서른
    x0, y0 = 96, 270
    for i in range(130):
        col, row = i % 10, i // 10
        cx, cy = x0 + col * 15, y0 + row * 20
        b.append(f'<ellipse cx="{cx}" cy="{cy}" rx="4.6" ry="6" fill="{MUTED}" fill-opacity="0.5"/>')
    b.append(text(x0 + 66, y0 + 13 * 20 + 12, "심은 것 백서른", 14, INK, "700"))
    # 굵은 화살표
    b.append(f'<line x1="{x0 + 156}" y1="{y0 + 120}" x2="{x0 + 230}" y2="{y0 + 120}" '
             f'stroke="{MUTED}" stroke-width="5" marker-end="url(#m)"/>')
    # 위: 초록 잎 열여덟
    lx, ly = 420, 292
    for i in range(18):
        col, row = i % 6, i // 6
        cx, cy = lx + col * 34, ly + row * 34
        b.append(f'<path d="M{cx} {cy + 8} C{cx - 12} {cy}, {cx - 10} {cy - 12}, {cx} {cy - 14} '
                 f'C{cx + 10} {cy - 12}, {cx + 12} {cy}, {cx} {cy + 8} Z" fill="{LEAF}" '
                 f'fill-opacity="0.75"/>')
    b.append(text(lx + 86, ly + 96, "무언가로 이어진 것 열여덟", 14, LEAF, "700"))
    # 아래: 갈색 점 백열둘
    bx, by = 400, 470
    for i in range(112):
        col, row = i % 16, i // 16
        b.append(f'<circle cx="{bx + col * 18}" cy="{by + row * 22}" r="5.6" fill="{SOIL}" '
                 f'fill-opacity="0.6"/>')
    b.append(text(bx + 134, by + 7 * 22 + 6, "뜻대로 안 된 것 백열두", 14, SOIL, "700"))
    # 갈색에서 초록으로 올라가는 가는 화살표
    b.append(f'<path d="M{bx - 24} {by + 40} C{bx - 70} {by - 40}, {lx - 70} {ly + 90}, '
             f'{lx - 18} {ly + 40}" fill="none" stroke="{MUTED}" stroke-width="1.6" '
             f'marker-end="url(#m)"/>')
    b.append(text(bx - 96, by - 24, "실패가 먼저 있어야", 11, MUTED))
    b.append(text(bx - 96, by - 8, "나오는 자리", 11, MUTED))
    b.append(closing(806, "심는 기준을 낮게 잡았으니 안 되는 쪽이 많은 것이 당연하다"))
    return base("백서른 가운데", "2장 · 심은 것과 그 결과", "\n".join(b))


# ── p18 실패는 빠르고 그 뒤는 늦다 ───────────────────────────────────
SPANS = [0.03, 0.05, 0.06, 0.08, 0.09, 0.11, 0.14, 0.18, 0.22, 0.28,
         0.34, 0.41, 0.48, 0.56, 0.68, 0.78, 0.88, 1.00]


def fig_gap():
    b = [text(397, 205, "심은 날부터 무언가로 이어진 날까지", 19, INK, "700")]
    x0, x1 = 150, 700
    y0, rh = 268, 25
    for i, span in enumerate(SPANS):
        y = y0 + i * rh
        ex = x0 + (x1 - x0) * span
        long_one = span >= 0.56
        if long_one:
            mid0, mid1 = x0 + 22, ex - 22
            b.append(f'<line x1="{x0}" y1="{y}" x2="{mid0}" y2="{y}" stroke="{MUTED}" '
                     f'stroke-width="2.4"/>')
            b.append(f'<line x1="{mid0}" y1="{y}" x2="{mid1}" y2="{y}" stroke="{MUTED}" '
                     f'stroke-width="1.6" stroke-dasharray="5,5"/>')
            b.append(f'<line x1="{mid1}" y1="{y}" x2="{ex}" y2="{y}" stroke="{MUTED}" '
                     f'stroke-width="2.4"/>')
        else:
            b.append(f'<line x1="{x0}" y1="{y}" x2="{ex}" y2="{y}" stroke="{MUTED}" '
                     f'stroke-width="2.4"/>')
        b.append(f'<circle cx="{x0}" cy="{y}" r="5" fill="{SOIL}"/>')
        b.append(f'<circle cx="{ex:.1f}" cy="{y}" r="5" fill="{LEAF}"/>')
    b.append(f'<line x1="{x0}" y1="{y0 + 18 * rh}" x2="{x1}" y2="{y0 + 18 * rh}" stroke="{INK}" '
             f'stroke-width="1.6"/>')
    b.append(text(x0, y0 + 18 * rh + 26, "심은 날", 14, MUTED, "400", "start"))
    b.append(text(x1, y0 + 18 * rh + 26, "아홉 달 뒤", 14, MUTED, "400", "end"))
    b.append(f'<circle cx="{x0 + 40}" cy="{y0 + 18 * rh + 60}" r="5" fill="{SOIL}"/>')
    b.append(text(x0 + 52, y0 + 18 * rh + 65, "실패한 날", 13, MUTED, "400", "start"))
    b.append(f'<circle cx="{x0 + 190}" cy="{y0 + 18 * rh + 60}" r="5" fill="{LEAF}"/>')
    b.append(text(x0 + 202, y0 + 18 * rh + 65, "무언가로 이어진 날", 13, MUTED, "400", "start"))
    b.append(f'<line x1="{x0 + 400}" y1="{y0 + 18 * rh + 60}" x2="{x0 + 432}" '
             f'y2="{y0 + 18 * rh + 60}" stroke="{MUTED}" stroke-width="1.6" '
             f'stroke-dasharray="5,5"/>')
    b.append(text(x0 + 440, y0 + 18 * rh + 65, "잊고 있던 동안", 13, MUTED, "400", "start"))
    b.append(closing(846, "실패는 그날 오고 그 뒤는 언제 올지 모른다"))
    return base("실패는 빠르고 그 뒤는 늦다", "3장 · 심은 날과 이어진 날", "\n".join(b))


# ── p37 백열두 줄 ────────────────────────────────────────────────────
MONTH_LABELS = ["첫", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한", "열두"]
# 2.1·5.3 본문이 센 값에 맞춘다 — 심음 130, 안 됨 112, 이어짐 18.
# 안 된 것은 심은 것에서 이어진 것을 뺀 값이므로 따로 적지 않고 계산한다.
PLANTED = [9, 10, 12, 13, 16, 12, 11, 10, 12, 11, 8, 6]
LINKED = [1, 1, 2, 2, 3, 1, 0, 2, 3, 3, 0, 0]
FAILED = [p - k for p, k in zip(PLANTED, LINKED)]
assert (sum(PLANTED), sum(FAILED), sum(LINKED)) == (130, 112, 18)


def fig_year_rows():
    b = [text(397, 205, "달마다 심은 것과 안 된 것", 19, INK, "700")]
    x0, y0 = 130, 660
    gw, bw = 44, 17
    scale = 22
    for i in range(12):
        gx = x0 + i * gw
        b.append(f'<rect x="{gx}" y="{y0 - PLANTED[i] * scale}" width="{bw}" '
                 f'height="{PLANTED[i] * scale}" rx="2" fill="{PALE}" stroke="{LINE}"/>')
        b.append(f'<rect x="{gx + bw + 3}" y="{y0 - FAILED[i] * scale}" width="{bw}" '
                 f'height="{FAILED[i] * scale}" rx="2" fill="{SOIL}" fill-opacity="0.62"/>')
        for k in range(LINKED[i]):
            b.append(f'<circle cx="{gx + bw + 3 + bw / 2}" '
                     f'cy="{y0 - FAILED[i] * scale - 12 - k * 13}" r="4.4" fill="{LEAF}"/>')
        b.append(text(gx + bw, y0 + 20, MONTH_LABELS[i], 12, MUTED))
    b.append(f'<line x1="{x0 - 10}" y1="{y0}" x2="{x0 + 12 * gw}" y2="{y0}" stroke="{INK}" '
             f'stroke-width="1.6"/>')
    # 범례
    ly = 262
    b.append(f'<rect x="470" y="{ly}" width="15" height="15" rx="2" fill="{PALE}" stroke="{LINE}"/>')
    b.append(text(492, ly + 12, "심은 것", 13, MUTED, "400", "start"))
    b.append(f'<rect x="556" y="{ly}" width="15" height="15" rx="2" fill="{SOIL}" '
             f'fill-opacity="0.62"/>')
    b.append(text(578, ly + 12, "안 된 것", 13, MUTED, "400", "start"))
    b.append(f'<circle cx="652" cy="{ly + 8}" r="4.4" fill="{LEAF}"/>')
    b.append(text(664, ly + 12, "이어진 것", 13, MUTED, "400", "start"))
    b.append(f'<rect x="{x0 - 10}" y="{y0 + 40}" width="{12 * gw + 10}" height="26" rx="6" '
             f'fill="{PALE}" fill-opacity="0.6"/>')
    b.append(text(397, y0 + 58, "못 적은 스물하나는 이 그림에 없다", 13, MUTED))
    b.append(closing(766, "실패의 수는 의욕이 아니라 착수의 지표였다"))
    return base("백열두 줄", "5장 · 한 해 치 기록", "\n".join(b))


FIGURES = {8: fig_three_questions, 11: fig_one_thirty, 18: fig_gap, 37: fig_year_rows}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild018")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(fn(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
