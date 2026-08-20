#!/usr/bin/env python3
"""book-073 이미지 페이지 4개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 시간과 횟수를 눈금으로 넣지 않는다 — 조사에
걸리는 시간은 문제마다 달라 숫자를 넣으면 본문에 없는 기준이 생긴다.

사용: python3 figures073.py [출력디렉터리]
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
BOX = "#c3ccd0"
MARK = "#a8443a"
KEEP, GONE = "#4a5c73", "#b9c2c6"


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


def fig_symptom_to_cause():
    """1.6 — 증상에서 원인으로."""
    b = []
    steps = ["증상 적기", "재현 만들기", "사례 줄이기", "원인 좁히기", "고치고 확인하기"]
    outs = ["조건 목록", "되풀이되는 실행", "작아진 사례", "한 자리"]
    bx, bw, bh = 290, 230, 64
    for k, name in enumerate(steps):
        y = 250 + k * 100
        b.append(box(bx, y, bw, bh))
        b.append(t(bx + bw / 2, y + 40, name, 17, INK, "700"))
        if k < len(outs):
            b.append(arrow(bx + bw / 2, y + bh + 4, bx + bw / 2, y + 94, FADE, 1.6, "fade"))
            b.append(t(bx + bw + 16, y + bh + 26, outs[k], 13, "#5d6c74", "400", "start"))
    b.append(f'<path d="M{bx - 6},{382} C{200},{382} {200},{282} {bx - 6},{282}" fill="none" '
             f'stroke="{MARK}" stroke-width="1.8" marker-end="url(#mark)"/>')
    b.append(t(196, 316, "조건이 모자라면", 13, MARK, "700", "end"))
    b.append(t(196, 336, "되돌아간다", 13, MARK, "700", "end"))
    b.append(t(bx + bw + 16, 690, "작아진 사례로", 13, "#5d6c74", "400", "start"))
    b.append(t(bx + bw + 16, 710, "다시 확인한다", 13, "#5d6c74", "400", "start"))
    b.append(bottom(790, "재현이 서야 나머지가 관찰이 된다"))
    return base("증상에서 원인으로", "앞 단계의 결과가 다음 단계의 재료다", "\n".join(b))


def fig_shrinking():
    """2.5 — 줄이는 과정의 기록."""
    b = []
    rows = [(16, True), (8, True), (4, False), (6, True), (2, True)]
    cw, ch, gap, x0 = 24, 26, 5, 150
    for k, (count, has) in enumerate(rows):
        y = 270 + k * 66
        for i in range(count):
            x = x0 + i * (cw + gap)
            b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" rx="3" '
                     f'fill="{KEEP if has else GONE}" opacity="0.55" stroke="{BOX}"/>')
        label = "증상 있음" if has else "증상 없음"
        color = INK if has else MARK
        b.append(t(700, y + 19, label, 14, color, "700", "end"))
    y3 = 270 + 2 * 66
    b.append(f'<path d="M{712},{y3 + 13} C{760},{y3 + 13} {760},{y3 + 79} {712},{y3 + 79}" '
             f'fill="none" stroke="{MARK}" stroke-width="1.6" marker-end="url(#mark)"/>')
    b.append(t(700, y3 + 46, "이 자리는 되돌렸다", 13, MARK, "700", "end"))
    b.append(t(x0, 254, "덜어 낸 뒤 남은 부분", 14, FADE, "400", "start"))
    b.append(bottom(640, "남은 칸이 모두 증상에 필요한 부분이다"))
    return base("줄이는 과정의 기록", "증상이 사라지면 되돌리고 다른 곳을 덜어 낸다", "\n".join(b))


def fig_two_directions():
    """3.5 — 좁히는 두 방향."""
    b = []
    # 위: 시간으로 좁히기
    b.append(t(128, 250, "시간으로 좁히기", 19, INK, "700", "start"))
    x0, x1, y = 150, 650, 300
    b.append(f'<rect x="{x0}" y="{y}" width="{x1 - x0}" height="34" rx="4" fill="#f6f8f8" stroke="{BOX}"/>')
    for i in range(1, 10):
        tx = x0 + i * (x1 - x0) / 10
        b.append(f'<line x1="{tx}" y1="{y}" x2="{tx}" y2="{y + 34}" stroke="{BOX}"/>')
    b.append(t((x0 + x1) / 2, y - 12, "변경", 13, FADE))
    b.append(t(x0, y + 58, "되던 때", 14, "#5d6c74"))
    b.append(t(x1, y + 58, "안 되는 때", 14, "#5d6c74"))
    for k, (frac, label) in enumerate(((0.5, "첫 확인"), (0.25, "둘째 확인"), (0.37, "셋째 확인"))):
        tx = x0 + (x1 - x0) * frac
        ty = y + 86 + k * 44
        b.append(arrow(tx, ty + 22, tx, y + 40, FADE, 1.4, "fade"))
        b.append(t(tx, ty + 38, label, 13, "#5d6c74"))
    # 아래: 자리로 좁히기
    b.append(t(128, 570, "자리로 좁히기", 19, INK, "700", "start"))
    names = ["받기", "고르기", "세기", "묶기", "보내기"]
    bw, bh, gap, bx, by = 96, 58, 30, 122, 600
    for k, name in enumerate(names):
        x = bx + k * (bw + gap)
        b.append(box(x, by, bw, bh))
        b.append(t(x + bw / 2, by + 36, name, 15, INK, "700"))
        if k < 4:
            cx = x + bw + gap / 2
            ok = k < 2
            b.append(f'<circle cx="{cx}" cy="{by + bh / 2}" r="11" fill="{"#ffffff"}" '
                     f'stroke="{INK if ok else MARK}" stroke-width="2"/>')
            b.append(t(cx, by + bh + 26, "값이 맞음" if ok else "값이 틀림", 12,
                       INK if ok else MARK, "700"))
    b.append(bottom(730, "맞음과 틀림이 갈리는 자리가 남는다"))
    return base("좁히는 두 방향", "한 번 확인할 때마다 후보가 절반이 된다", "\n".join(b))


def fig_two_causes():
    """5.4 — 원인이 겹쳐 있을 때."""
    b = []
    rows = [("갑만 있음", (True, False), "증상 없음", False),
            ("을만 있음", (False, True), "증상 없음", False),
            ("갑과 을 모두", (True, True), "증상 있음", True),
            ("둘 다 없음", (False, False), "증상 없음", False)]
    for k, (label, marks, result, hit) in enumerate(rows):
        y = 290 + k * 96
        b.append(t(120, y + 28, label, 16, INK, "700", "start"))
        b.append(f'<line x1="112" y1="{y + 60}" x2="682" y2="{y + 60}" stroke="#e6eaec"/>')
        for i, on in enumerate(marks):
            cx = 360 + i * 84
            if on:
                b.append(f'<circle cx="{cx}" cy="{y + 22}" r="18" fill="{KEEP}" opacity="0.65"/>')
                b.append(t(cx, y + 28, "갑" if i == 0 else "을", 15, "#ffffff", "700"))
            else:
                b.append(f'<circle cx="{cx}" cy="{y + 22}" r="18" fill="none" stroke="{BOX}" '
                         f'stroke-dasharray="4 3"/>')
        b.append(t(682, y + 28, result, 16, MARK if hit else "#5d6c74", "700", "end"))
        if hit:
            b.append(t(682, y + 50, "이 짝일 때만 나온다", 12, MARK, "400", "end"))
    b.append(bottom(700, "하나씩 빼는 확인으로는 둘 다 무관해 보인다"))
    return base("원인이 겹쳐 있을 때", "하나만 빼면 증상이 그대로다", "\n".join(b))


FIGURES = {
    7: fig_symptom_to_cause,
    13: fig_shrinking,
    20: fig_two_directions,
    33: fig_two_causes,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild073")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
