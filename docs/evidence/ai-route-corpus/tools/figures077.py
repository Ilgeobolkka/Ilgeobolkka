#!/usr/bin/env python3
"""book-077 이미지 페이지 4개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 확인의 개수와 시간을 눈금으로 넣지 않는다 —
알맞은 수는 코드마다 달라 숫자를 넣으면 본문에 없는 기준이 생긴다.

사용: python3 figures077.py [출력디렉터리]
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
BOX = "#c3ccd0"
MARK = "#a8443a"
REAL = "#4a5c73"
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
<defs>
  <marker id="fade" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{FADE}"/></marker>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="ink" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{INK}"/></marker>
  <pattern id="hatch" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
    <line x1="0" y1="0" x2="0" y2="8" stroke="{BOX}" stroke-width="3"/>
  </pattern>
</defs>
<rect width="{W}" height="{H}" fill="#ffffff"/>
{t(W / 2, 120, title, 31, '#1e2c33', '700')}
<line x1="105" y1="150" x2="689" y2="150" stroke="#dde2e4"/>
{t(W / 2, 182, lead, 16, '#71818a')}
{body}
</svg>'''


def bottom(y, text, size=16):
    return ('<line x1="105" y1="%d" x2="689" y2="%d" stroke="#dde2e4"/>' % (y, y)
            + t(W / 2, y + 38, text, size, "#4c5b64", "700"))


def fig_layers():
    """1.6 — 확인의 층과 범위."""
    b = []
    rings = [(150, 250, 380, 320, "바깥에서 부른 전체", "느리다 · 실제에 가깝다", True),
             (196, 296, 288, 228, "붙여 본 여러 부분", "중간이다 · 붙는 자리를 본다", False),
             (242, 342, 196, 136, "함수와 모듈", "빠르다 · 원인이 분명하다", False)]
    for x, y, w, h, name, note, dashed in rings:
        b.append(box(x, y, w, h, "#ffffff", MARK if dashed else BOX, 1.6,
                     "6 4" if dashed else None))
        b.append(t(x + w / 2, y + 26, name, 15, INK, "700"))
        b.append(t(560, y + 26, note, 12, "#5d6c74", "400", "start"))
    b.append(arrow(126, 280, 126, 560, FADE, 1.4, "fade"))
    b.append(t(120, 430, "개수는 안쪽이 많다", 12, FADE, "700", "end"))
    b.append(t(560, 250 - 14, "적게 두고 자주 돌리지 않는다", 12, MARK, "700", "start"))
    b.append(bottom(640, "층마다 맡을 물음을 다르게 준다"))
    return base("확인의 층과 범위", "넓을수록 실제에 가깝고 좁을수록 원인을 가리킨다", "\n".join(b))


def fig_shape():
    """2.5 — 확인 하나의 구조."""
    b = []
    cells = [("조건 갖추기", "확인할 코드에 닿기 전에 깨졌다"),
             ("실행하기", "실행 도중에 깨졌다"),
             ("결과 보기", "기대와 다르다")]
    x0, y0, bw, bh, gap = 170, 280, 230, 84, 40
    for k, (name, meaning) in enumerate(cells):
        y = y0 + k * (bh + gap)
        b.append(box(x0, y, bw, bh, "#f6f8f8"))
        b.append(t(x0 + bw / 2, y + 50, name, 17, INK, "700"))
        b.append(arrow(x0 + bw + 6, y + bh / 2, x0 + bw + 46, y + bh / 2, FADE, 1.4, "fade"))
        b.append(t(x0 + bw + 54, y + bh / 2 + 5, meaning, 13, "#5d6c74", "400", "start"))
        if k:
            b.append(arrow(x0 + bw / 2, y - gap + 4, x0 + bw / 2, y - 6, FADE, 1.6, "fade"))
    b.append(f'<rect x="{x0 - 26}" y="{y0}" width="10" height="{bh}" rx="3" fill="{MARK}" '
             f'opacity="0.55"/>')
    b.append(t(x0 - 34, y0 + 34, "이 칸이 길면", 12, MARK, "700", "end"))
    b.append(t(x0 - 34, y0 + 54, "의존이 많다는 뜻이다", 12, MARK, "700", "end"))
    last_y = y0 + 2 * (bh + gap) + bh
    b.append(arrow(x0 + 60, last_y + 6, x0 + 20, last_y + 50, SOFT, 1.6))
    b.append(t(x0 + 12, last_y + 68, "통과", 14, SOFT, "700"))
    b.append(arrow(x0 + bw - 60, last_y + 6, x0 + bw - 20, last_y + 50, MARK, 1.6, "mark"))
    b.append(t(x0 + bw - 12, last_y + 68, "실패", 14, MARK, "700"))
    b.append(bottom(700, "세 칸이 나뉘어 있어야 실패를 읽을 수 있다"))
    return base("확인 하나의 구조", "어디서 멈췄는지가 곧 무엇이 깨졌는지다", "\n".join(b))


def fig_doubles():
    """3.5 — 대체물이 놓이는 자리."""
    b = []
    parts = ["입구", "업무 규칙", "저장 접근", "바깥 연동"]
    rows = [("규칙만 보는 확인", (False, True, False, False)),
            ("저장까지 붙이는 확인", (False, True, True, False)),
            ("전체를 부르는 확인", (True, True, True, False))]
    bx, bw, bh, gap = 230, 110, 60, 12
    for r, (label, reals) in enumerate(rows):
        y = 270 + r * 110
        b.append(t(216, y + 36, label, 14, INK, "700", "end"))
        for k, name in enumerate(parts):
            x = bx + k * (bw + gap)
            fill = "#ffffff" if reals[k] else "url(#hatch)"
            b.append(box(x, y, bw, bh, fill, REAL if reals[k] else BOX,
                         1.8 if reals[k] else 1.2))
            b.append(t(x + bw / 2, y + 36, name, 13,
                       INK if reals[k] else "#7c878c", "700"))
    b.append(t(bx + 2 * (bw + gap), 600, "빗금은 대체물을 놓은 자리다", 13, FADE))
    b.append(bottom(650, "빗금 친 자리는 이 확인이 지켜 주지 않는다"))
    return base("대체물이 놓이는 자리", "진짜로 도는 부분이 그 확인이 지켜 주는 부분이다", "\n".join(b))


def fig_contract():
    """4.4 — 계약을 사이에 둔 두 확인."""
    b = []
    cx, cy, cw, ch = 340, 280, 116, 260
    b.append(box(cx, cy, cw, ch, "#f6f8f8", INK, 1.8))
    b.append(t(cx + cw / 2, cy + 120, "계약", 18, INK, "700"))
    b.append(t(cx + cw / 2, cy + 148, "한 벌", 18, INK, "700"))
    sides = [(90, "부르는 쪽", "요청이 계약에", "맞는가"),
             (566, "제공하는 쪽", "응답이 계약에", "맞는가")]
    for x0, name, l1, l2 in sides:
        b.append(box(x0, cy + 40, 140, 160))
        b.append(t(x0 + 70, cy + 74, name, 16, INK, "700"))
        b.append(box(x0 + 14, cy + 96, 112, 84, "#ffffff", SOFT, 1.4))
        b.append(t(x0 + 70, cy + 132, l1, 12, SOFT, "700"))
        b.append(t(x0 + 70, cy + 152, l2, 12, SOFT, "700"))
    b.append(arrow(232, cy + 120, cx - 6, cy + 120, FADE, 1.6, "fade"))
    b.append(arrow(560, cy + 120, cx + cw + 6, cy + 120, FADE, 1.6, "fade"))
    b.append(t(286, cy + 90, "같은 것을", 11, FADE))
    b.append(t(286, cy + 106, "본다", 11, FADE))
    b.append(t(512, cy + 90, "같은 것을", 11, FADE))
    b.append(t(512, cy + 106, "본다", 11, FADE))
    b.append(t(cx + cw / 2, cy + ch + 40, "계약이 바뀌면 양쪽 확인이 함께 깨진다", 13, MARK, "700"))
    b.append(bottom(620, "대체물이 낡는 자리를 여기서 막는다"))
    return base("계약을 사이에 둔 두 확인", "둘을 함께 띄우지 않고도 어긋남을 잡는다", "\n".join(b))


FIGURES = {
    7: fig_layers,
    13: fig_shape,
    20: fig_doubles,
    26: fig_contract,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild077")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
