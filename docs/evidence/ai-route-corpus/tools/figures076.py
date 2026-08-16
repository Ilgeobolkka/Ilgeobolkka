#!/usr/bin/env python3
"""book-076 이미지 페이지 4개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 초와 건수를 눈금으로 넣지 않는다 — 알맞은 값은
시스템마다 달라 숫자를 넣으면 본문에 없는 기준이 생긴다.

사용: python3 figures076.py [출력디렉터리]
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
BOX = "#c3ccd0"
MARK = "#a8443a"
PICK = "#4a5c73"
SOFT = "#6f8a6a"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def box(x, y, w, h, fill="#ffffff", stroke=BOX, width=1.4, dash=None):
    extra = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="4" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="{width}"{extra}/>')


def arrow(x1, y1, x2, y2, color=INK, width=1.8, marker="ink", dash=None):
    extra = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"{extra}/>')


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


def fig_layers():
    """1.6 — 값이 정해지는 자리."""
    b = []
    layers = [("실행할 때 준 값", False), ("환경에 둔 값", False),
              ("설정 파일의 값", True), ("기본값", True)]
    x0, y0, bw, bh, gap = 170, 270, 300, 66, 18
    b.append(t(150, y0 + 20, "먼저", 13, FADE, "700", "end"))
    b.append(t(150, y0 + 3 * (bh + gap) + 46, "나중", 13, FADE, "700", "end"))
    for k, (name, has) in enumerate(layers):
        y = y0 + k * (bh + gap)
        b.append(box(x0, y, bw, bh))
        b.append(t(x0 + bw / 2 - 24, y + 41, name, 16, INK, "700"))
        if has:
            b.append(f'<rect x="{x0 + bw - 62}" y="{y + 22}" width="44" height="22" rx="4" '
                     f'fill="{PICK}" opacity="0.7"/>')
        else:
            b.append(f'<rect x="{x0 + bw - 62}" y="{y + 22}" width="44" height="22" rx="4" '
                     f'fill="none" stroke="{BOX}" stroke-dasharray="4 3"/>')
    y_pick = y0 + 2 * (bh + gap)
    y_hid = y0 + 3 * (bh + gap)
    b.append(box(560, y_pick - 6, 150, 78, "#ffffff", PICK, 1.8))
    b.append(t(635, y_pick + 28, "실제로", 15, PICK, "700"))
    b.append(t(635, y_pick + 50, "쓰이는 값", 15, PICK, "700"))
    b.append(arrow(x0 + bw + 6, y_pick + 33, 554, y_pick + 33, PICK, 3))
    b.append(arrow(x0 + bw + 6, y_hid + 33, 540, y_hid + 33, FADE, 1.4, "fade", "5 4"))
    b.append(t(548, y_hid + 38, "가려진 값", 13, FADE, "400", "start"))
    b.append(bottom(660, "어느 층에서 왔는지 말할 수 있어야 한다"))
    return base("값이 정해지는 자리", "아무도 정하지 않으면 맨 아래가 쓰인다", "\n".join(b))


def fig_merge():
    """2.5 — 설정이 겹치는 모양."""
    b = []
    for side, (x0, title, whole) in enumerate(((110, "값 단위로 겹치기", False),
                                               (430, "묶음 단위로 덮기", True))):
        b.append(t(x0 + 125, 246, title, 16, INK, "700"))
        b.append(t(x0 + 125, 288, "환경에 둔 값", 13, FADE))
        b.append(box(x0, 300, 250, 96))
        for i in range(3):
            y = 312 + i * 28
            if i == 0:
                b.append(f'<rect x="{x0 + 16}" y="{y}" width="218" height="18" rx="3" '
                         f'fill="{PICK}" opacity="0.7"/>')
            else:
                b.append(f'<rect x="{x0 + 16}" y="{y}" width="218" height="18" rx="3" '
                         f'fill="none" stroke="{BOX}" stroke-dasharray="4 3"/>')
        b.append(t(x0 + 125, 434, "설정 파일의 값", 13, FADE))
        b.append(box(x0, 446, 250, 96))
        for i in range(3):
            y = 458 + i * 28
            b.append(f'<rect x="{x0 + 16}" y="{y}" width="218" height="18" rx="3" '
                     f'fill="{SOFT}" opacity="0.65"/>')
        b.append(arrow(x0 + 125, 556, x0 + 125, 592, FADE, 1.6, "fade"))
        b.append(t(x0 + 125, 618, "합친 결과", 13, FADE))
        b.append(box(x0, 630, 250, 96))
        for i in range(3):
            y = 642 + i * 28
            if i == 0:
                fill, op, label = PICK, 0.7, None
            elif whole:
                fill, op, label = "#d8dde0", 0.55, "기본값"
            else:
                fill, op, label = SOFT, 0.65, None
            b.append(f'<rect x="{x0 + 16}" y="{y}" width="218" height="18" rx="3" '
                     f'fill="{fill}" opacity="{op}"/>')
            if label:
                b.append(t(x0 + 125, y + 14, label, 11, "#5d6c74", "700"))
        if whole:
            b.append(t(x0 + 125, 752, "묶음을 통째로 덮었다", 13, MARK, "700"))
    b.append(bottom(790, "조용히 기본값으로 돌아가는 자리가 여기다"))
    return base("설정이 겹치는 모양", "값 단위로 겹치는가 묶음 단위로 덮는가", "\n".join(b))


def fig_spread():
    """3.5 — 실패가 번지는 길."""
    b = []
    names = ["들어온 요청", "업무 처리", "바깥 연동", "응답 없는 상대"]
    for side, x0 in enumerate((110, 440)):
        for k, name in enumerate(names):
            y = 260 + k * 96
            b.append(box(x0, y, 220, 62))
            b.append(t(x0 + 110, y + 38, name, 15, INK, "700"))
        if side == 0:
            b.append(f'<path d="M{x0 - 24},{260 + 3 * 96 - 6} L{x0 - 24},{260 + 62 + 6}" '
                     f'stroke="{MARK}" stroke-width="4" marker-end="url(#mark)"/>')
            b.append(t(x0 - 34, 470, "기다림이", 12, MARK, "700", "end"))
            b.append(t(x0 - 34, 488, "쌓인다", 12, MARK, "700", "end"))
            b.append(t(x0 + 110, 244, "자원이 모자라 다른 요청도 멈춘다", 12, MARK, "700"))
        else:
            gate = 260 + 3 * 96 - 18
            b.append(f'<line x1="{x0 - 20}" y1="{gate}" x2="{x0 + 240}" y2="{gate}" '
                     f'stroke="{INK}" stroke-width="5"/>')
            b.append(t(x0 + 110, gate - 10, "상한과 경계", 12, INK, "700"))
            b.append(arrow(x0 + 220, 260 + 2 * 96 + 31, x0 + 300, 260 + 2 * 96 + 31, SOFT, 2))
            b.append(t(x0 + 258, 260 + 2 * 96 + 16, "줄여서", 11, SOFT, "700"))
            b.append(t(x0 + 258, 260 + 2 * 96 + 58, "준 응답", 11, SOFT, "700"))
    b.append(bottom(700, "막는 자리를 정하면 위층은 계속 답한다"))
    return base("실패가 번지는 길", "기다림이 길면 실패가 위로 번진다", "\n".join(b))


def fig_steps():
    """4.4 — 부하에 따른 응답의 단계."""
    b = []
    steps = [("모두 준다", "전체 화면"), ("보조 부분을 뺀다", "핵심 화면"),
             ("오래 걸리는 부분을 뺀다", "느린 부분 없는 화면"), ("일부 요청을 거절한다", "다시 시도 안내")]
    x0, y0, sw, sh, drop = 88, 300, 124, 60, 46
    for k, (name, gets) in enumerate(steps):
        x = x0 + k * sw
        y = y0 + k * drop
        b.append(box(x, y, sw, sh, "#f6f8f8"))
        size = 13 if len(name) > 8 else 15
        b.append(t(x + sw / 2, y + 36, name, size, INK, "700"))
        b.append(t(x + sw / 2, y + sh + 26, gets, 12, "#5d6c74"))
    x = x0 + 4 * sw
    y = y0 + 4 * drop
    b.append(box(x, y, sw, sh, "#ffffff", MARK, 1.2, "5 4"))
    b.append(t(x + sw / 2, y + 36, "아무 답도", 12, MARK, "700"))
    b.append(t(x + sw / 2, y + 52, "못 하는 상태", 12, MARK, "700"))
    b.append(arrow(x0, 560, x0 + 5 * sw, 560, FADE, 1.4, "fade"))
    b.append(t(x0 + 2.5 * sw, 584, "부하가 커지는 방향", 13, FADE))
    b.append(bottom(650, "점선 단에 닿지 않게 앞의 단을 만든다"))
    return base("부하에 따른 응답의 단계", "한 번에 무너지는 대신 단을 밟는다", "\n".join(b))


FIGURES = {
    7: fig_layers,
    13: fig_merge,
    20: fig_spread,
    26: fig_steps,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild076")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
