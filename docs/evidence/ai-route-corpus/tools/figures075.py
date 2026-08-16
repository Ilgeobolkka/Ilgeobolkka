#!/usr/bin/env python3
"""book-075 이미지 페이지 4개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 건수와 용량을 눈금으로 넣지 않는다 — 서비스마다
자릿수가 달라 숫자를 넣으면 본문에 없는 기준이 생긴다.

사용: python3 figures075.py [출력디렉터리]
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
BOX = "#c3ccd0"
MARK = "#a8443a"
LV = ("#a8443a", "#8a6a44", "#6f8a6a")  # 오류·경고·정보
TRACE = "#4a5c73"


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


def fig_one_line():
    """1.6 — 한 줄의 로그가 담는 것."""
    b = []
    cells = [("언제", "시각"), ("어디서", "남긴 자리"), ("어느 수준", "레벨"),
             ("무슨 일", "사건 이름"), ("어떤 값", "이어 붙인 항목들")]
    x0, y0, cw, ch = 92, 340, 122, 84
    for k, (name, sub) in enumerate(cells):
        x = x0 + k * cw
        dash = "5 4" if k == 4 else None
        b.append(box(x, y0, cw, ch, "#ffffff", MARK if k == 4 else BOX, 1.4, dash))
        b.append(t(x + cw / 2, y0 + 50, name, 17, INK, "700"))
        b.append(t(x + cw / 2, y0 + ch + 28, sub, 13, "#5d6c74"))
    bx0, bx1 = x0, x0 + 4 * cw
    b.append(f'<path d="M{bx0},{y0 - 34} L{bx0},{y0 - 20} L{bx1},{y0 - 20} L{bx1},{y0 - 34}" '
             f'fill="none" stroke="{FADE}"/>')
    b.append(t((bx0 + bx1) / 2, y0 - 44, "모든 줄이 같은 자리에 갖는다", 14, FADE))
    b.append(t(x0 + 4 * cw + cw / 2, y0 + ch + 54, "항목의 수는", 13, MARK, "700"))
    b.append(t(x0 + 4 * cw + cw / 2, y0 + ch + 74, "사건마다 다르다", 13, MARK, "700"))
    b.append(bottom(560, "한 줄에 물음과 답이 함께 있어야 한다"))
    return base("한 줄의 로그가 담는 것", "한 줄만 떼어 놓아도 뜻이 통해야 한다", "\n".join(b))


def fig_level_collapse():
    """2.6 — 레벨이 무너지는 방식."""
    b = []
    points = [((0.1, 0.25, 0.65), "기준대로 붙임"),
              ((0.3, 0.3, 0.4), "급해서 한 자리 올림"),
              ((0.6, 0.25, 0.15), "놓칠까 봐 올림"),
              ((0.9, 0.07, 0.03), "모두 오류")]
    x0, bw, gap, top, height = 118, 118, 40, 290, 300
    for k, (ratio, caption) in enumerate(points):
        x = x0 + k * (bw + gap)
        y = top
        for i, r in enumerate(ratio):
            h = height * r
            b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{h}" fill="{LV[i]}" '
                     f'opacity="0.62" stroke="#ffffff"/>')
            if h > 26:
                b.append(t(x + bw / 2, y + h / 2 + 6, ("오류", "경고", "정보")[i], 13, "#ffffff", "700"))
            y += h
        b.append(t(x + bw / 2, top + height + 34, caption, 13, "#5d6c74"))
    lastx = x0 + 3 * (bw + gap) + bw / 2
    b.append(t(lastx, top - 22, "이제 아무도", 13, MARK, "700"))
    b.append(t(lastx, top - 4, "오류를 보지 않는다", 13, MARK, "700"))
    b.append(bottom(700, "레벨을 올리는 일에는 되돌리는 규칙이 필요하다"))
    return base("레벨이 무너지는 방식", "한 자리씩 올라가면 결국 모두 같은 자리에 선다", "\n".join(b))


def fig_pipeline():
    """4.4 — 수집 파이프라인의 단계."""
    b = []
    steps = [("남기기", "남긴 수"), ("내보내기", "내보낸 수"),
             ("모으기", "받은 수"), ("저장하기", "저장한 수")]
    x0, y0, bw, bh, gap = 84, 300, 126, 74, 40
    for k, (name, count) in enumerate(steps):
        x = x0 + k * (bw + gap)
        b.append(box(x, y0, bw, bh))
        b.append(t(x + bw / 2, y0 + 44, name, 16, INK, "700"))
        b.append(t(x + bw / 2, y0 + bh + 26, count, 13, "#5d6c74"))
        if k:
            b.append(arrow(x - gap + 4, y0 + bh / 2, x - 6, y0 + bh / 2, FADE, 1.6, "fade"))
        if k in (1, 2):
            cx = x + bw / 2
            b.append(arrow(cx, y0 + bh + 44, cx, y0 + bh + 84, MARK, 1.4, "mark"))
            b.append(box(x + 14, y0 + bh + 90, bw - 28, 44, "#f6f8f8", MARK, 1.0, "5 4"))
            b.append(t(cx, y0 + bh + 118, "버려짐", 14, MARK, "700"))
    right = x0 + 4 * bw + 3 * gap
    for k, label in enumerate(("찾아보기", "세어 보기")):
        ty = y0 + 12 + k * 46
        b.append(arrow(right - gap + 4, y0 + bh / 2, right + 6, ty + 6, FADE, 1.6, "fade"))
        b.append(t(right + 12, ty + 12, label, 15, INK, "700", "start"))
    b.append(bottom(620, "단계마다 수를 세면 새는 자리가 드러난다"))
    return base("수집 파이프라인의 단계", "어디서 막혔는지 말할 수 있어야 한다", "\n".join(b))


def fig_stitch():
    """5.4 — 흩어진 기록을 잇는 방법."""
    b = []
    bands = [("앞단", [(0.05, "가"), (0.30, "나"), (0.62, "가")]),
             ("업무 처리", [(0.18, "가"), (0.44, "나"), (0.75, ""), (0.88, "가")]),
             ("저장 접근", [(0.26, "가"), (0.55, "나"), (0.70, "가")])]
    x0, x1 = 190, 690
    marks = []
    for k, (name, items) in enumerate(bands):
        y = 260 + k * 86
        b.append(f'<line x1="{x0}" y1="{y + 18}" x2="{x1}" y2="{y + 18}" stroke="#e6eaec" stroke-width="2"/>')
        b.append(t(x0 - 14, y + 24, name, 15, "#5d6c74", "700", "end"))
        for frac, tag in items:
            x = x0 + (x1 - x0) * frac
            color = TRACE if tag == "가" else BOX
            b.append(f'<rect x="{x - 13}" y="{y + 4}" width="26" height="28" rx="4" '
                     f'fill="{"#ffffff" if tag != "가" else color}" stroke="{color if tag else MARK}" '
                     f'stroke-width="{2 if tag == "가" else 1.4}"/>')
            if tag:
                b.append(t(x, y + 24, tag, 13, "#ffffff" if tag == "가" else "#5d6c74", "700"))
            if tag == "가":
                marks.append((x, y + 32))
            if not tag:
                b.append(t(x + 18, y + 52, "식별자가 끊긴 자리", 12, MARK, "700", "start"))
    fy = 590
    b.append(f'<line x1="{x0}" y1="{fy + 18}" x2="{x1}" y2="{fy + 18}" stroke="#e6eaec" stroke-width="2"/>')
    b.append(t(x0 - 14, fy + 24, "한 처리의 흐름", 15, TRACE, "700", "end"))
    ordered = sorted(marks)
    for i, (mx, my) in enumerate(ordered):
        fx = x0 + 70 + i * 74
        b.append(f'<rect x="{fx - 13}" y="{fy + 4}" width="26" height="28" rx="4" fill="{TRACE}"/>')
        b.append(t(fx, fy + 24, "가", 13, "#ffffff", "700"))
        b.append(f'<path d="M{mx},{my} C{mx},{my + 40} {fx},{fy - 40} {fx},{fy}" fill="none" '
                 f'stroke="{TRACE}" stroke-width="1" stroke-dasharray="3 3" opacity="0.7"/>')
    b.append(bottom(690, "끊긴 자리부터 흐름이 사라진다"))
    return base("흩어진 기록을 잇는 방법", "같은 값이 붙어 있어야 한 흐름이 된다", "\n".join(b))


FIGURES = {
    7: fig_one_line,
    14: fig_level_collapse,
    26: fig_pipeline,
    33: fig_stitch,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild075")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
