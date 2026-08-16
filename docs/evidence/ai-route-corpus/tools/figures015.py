#!/usr/bin/env python3
"""book-015 이미지 페이지 네 개의 SVG를 생성한다.

사용: python3 figures015.py <출력디렉터리>

네 도표에서 색의 뜻을 고정했다 — 판정을 받은 소리는 언제나 주황이고 판정을 받지 않고 지나간 소리는
언제나 옅은 회색이다. 이 권의 주장이 「소음은 소리의 성질이 아니라 판정」이므로, 그림마다 색의 뜻이
달라지면 네 장을 이어서 읽을 수 없다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#2b3238"
MUTED = "#77828a"
LINE = "#c3cbd1"
PALE = "#dfe3e6"
JUDGED = "#c4703c"
QUIET = "#eef0f1"


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#fdfcf9"/>
<defs>
  <marker id="a" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
    <path d="M0,0 L0,6 L9,3 z" fill="{MUTED}"/>
  </marker>
</defs>
<text x="397" y="108" text-anchor="middle" font-size="34" font-weight="700" fill="{INK}">{title}</text>
<text x="397" y="150" text-anchor="middle" font-size="18" fill="{MUTED}">{subtitle}</text>
{body}
<text x="397" y="1072" text-anchor="middle" font-size="14" fill="#98a1a7">오늘의 소음을 접어두기</text>
</svg>'''


def text(x, y, value, size=17, fill=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}">{value}</text>')


def closing(y, value):
    return ('<rect x="107" y="%d" width="580" height="58" rx="16" fill="#f1efe8" stroke="%s"/>'
            % (y, LINE)) + text(397, y + 37, value, 20, INK, "700")


# ── p8 스물셋 가운데 둘 ──────────────────────────────────────────────
QUIET_SOUNDS = [
    ("냉장고", 150, 320), ("시계", 268, 296), ("창밖 차", 392, 312), ("새", 512, 292),
    ("문", 618, 330), ("물", 150, 400), ("엘리베이터", 286, 396), ("계단", 430, 382),
    ("바람", 548, 404), ("보일러", 646, 400), ("환풍기", 168, 472), ("빗소리", 300, 468),
    ("의자", 424, 476), ("설거지", 548, 470), ("자판", 654, 466), ("현관", 176, 540),
    ("주전자", 306, 546), ("복도", 432, 538), ("배수관", 556, 542), ("가방", 660, 536),
    ("옆집 문", 250, 604),
]
JUDGED_SOUNDS = [("위층 의자", 430, 598), ("저녁 공사", 590, 594)]


def fig_twenty_three():
    b = [text(397, 205, "하루 동안 들린 소리를 적어 보면", 19, INK, "700")]
    b.append(f'<rect x="96" y="252" width="604" height="420" rx="18" fill="none" '
             f'stroke="{MUTED}" stroke-width="1.5" stroke-dasharray="7,6"/>')
    for name, x, y in QUIET_SOUNDS:
        b.append(f'<circle cx="{x}" cy="{y}" r="13" fill="{QUIET}" stroke="{LINE}" stroke-width="1.2"/>')
        b.append(text(x, y + 30, name, 12, MUTED))
    for name, x, y in JUDGED_SOUNDS:
        b.append(f'<circle cx="{x}" cy="{y}" r="24" fill="{JUDGED}" fill-opacity="0.85" '
                 f'stroke="{JUDGED}" stroke-width="2"/>')
        b.append(text(x, y + 44, name, 13, JUDGED, "700"))
    b.append(text(300, 698, "아무 판정도 받지 않고 지나간 소리", 15, MUTED))
    b.append(text(570, 698, "소음이라고 부른 소리", 15, JUDGED, "700"))
    # 아래쪽 막대 둘
    bx, by = 300, 726
    b.append(f'<rect x="{bx}" y="{by}" width="34" height="96" rx="4" fill="{PALE}" stroke="{LINE}"/>')
    b.append(text(bx + 17, by + 118, "스물하나", 14, MUTED))
    b.append(f'<rect x="{bx + 96}" y="{by + 87}" width="34" height="9" rx="3" fill="{JUDGED}"/>')
    b.append(text(bx + 113, by + 118, "둘", 14, JUDGED, "700"))
    b.append(closing(866, "소음은 들리는 것 가운데 아주 작은 부분이다"))
    return base("스물셋 가운데 둘", "1장 · 하루에 들린 소리", "\n".join(b))


# ── p11 크기가 아니라 규칙성 ─────────────────────────────────────────
PLOT = [("냉장고", 0.10, 0.30), ("환풍기", 0.16, 0.55), ("시계", 0.08, 0.14),
        ("창밖 차", 0.28, 0.78), ("위층 발소리", 0.72, 0.52), ("문 여닫힘", 0.80, 0.70),
        ("엘리베이터", 0.66, 0.34), ("저녁 공사", 0.88, 0.88)]


def fig_regularity():
    b = [text(397, 205, "배경이 되는 소리와 끝까지 남는 소리", 19, INK, "700")]
    x0, x1, y0, y1 = 168, 700, 260, 660
    mid = (x0 + x1) / 2
    b.append(f'<rect x="{x0}" y="{y0}" width="{mid - x0}" height="{y1 - y0}" fill="{QUIET}"/>')
    b.append(f'<rect x="{mid}" y="{y0}" width="{x1 - mid}" height="{y1 - y0}" fill="{JUDGED}" '
             f'fill-opacity="0.12"/>')
    b.append(f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="none" '
             f'stroke="{LINE}" stroke-width="1.5"/>')
    b.append(f'<line x1="{mid}" y1="{y0}" x2="{mid}" y2="{y1}" stroke="{MUTED}" stroke-width="2"/>')
    b.append(text((x0 + mid) / 2, y0 + 28, "배경이 된다", 17, MUTED, "700"))
    b.append(text((mid + x1) / 2, y0 + 28, "끝까지 남는다", 17, JUDGED, "700"))
    for name, gx, gy in PLOT:
        px = x0 + (x1 - x0) * gx
        py = y1 - (y1 - y0) * gy
        color = JUDGED if gx > 0.5 else MUTED
        b.append(f'<circle cx="{px:.0f}" cy="{py:.0f}" r="6" fill="{color}"/>')
        anchor = "start" if gx < 0.5 else "end"
        dx = 12 if gx < 0.5 else -12
        b.append(text(px + dx, py + 5, name, 13, INK, "400", anchor))
    b.append(text(x0, y1 + 28, "규칙적", 15, MUTED, "400", "start"))
    b.append(text(x1, y1 + 28, "들쭉날쭉", 15, MUTED, "400", "end"))
    b.append(text(x0 - 12, y1, "작다", 14, MUTED, "400", "end"))
    b.append(text(x0 - 12, y0 + 12, "크다", 14, MUTED, "400", "end"))
    b.append(f'<line x1="{x0}" y1="{y1 + 56}" x2="{x1}" y2="{y1 + 56}" stroke="{MUTED}" '
             f'stroke-width="2" marker-end="url(#a)"/>')
    b.append(text(397, y1 + 82, "이 방향으로 갈수록 배경이 되지 않는다", 15, MUTED))
    b.append(closing(796, "큰 소리도 규칙적이면 사라지고 작은 소리도 불규칙하면 남는다"))
    return base("크기가 아니라 규칙성", "2장 · 두 축에 놓아 본 소리들", "\n".join(b))


# ── p18 하나를 앞에 놓으면 ───────────────────────────────────────────
FIVE = ["시계", "창밖 차", "냉장고", "위층", "물소리"]
PANELS = [("시계를 고를 때", 0), ("창밖을 고를 때", 1), ("아무것도 고르지 않을 때", None)]
SPOTS = [(-150, 34), (-62, -26), (44, 40), (196, -34), (250, 30)]


def fig_foreground():
    b = [text(397, 200, "주의를 옮기면 소리들이 자리를 바꾼다", 19, INK, "700")]
    front_x = 470
    for i, (label, chosen) in enumerate(PANELS):
        cy = 300 + i * 196
        b.append(f'<rect x="196" y="{cy - 76}" width="520" height="164" rx="14" fill="#ffffff" '
                 f'stroke="{LINE}" stroke-width="1.5"/>')
        b.append(text(186, cy + 6, label, 14, INK, "700", "end"))
        for k, name in enumerate(FIVE):
            if chosen is not None and k == chosen:
                px, py = front_x, cy
                r, fill, op = 30, JUDGED, 0.85
                size, color, weight = 14, "#ffffff", "700"
            else:
                dx, dy = SPOTS[k]
                px, py = 340 + dx, cy + dy
                if chosen is None:
                    r, fill, op = 17, MUTED, 0.28
                else:
                    r, fill, op = 13, MUTED, 0.18
                size, color, weight = 12, MUTED, "400"
            b.append(f'<circle cx="{px}" cy="{py}" r="{r}" fill="{fill}" fill-opacity="{op}" '
                     f'stroke="{fill}" stroke-width="1.5" stroke-opacity="0.6"/>')
            b.append(text(px, py + 4, name, size, color, weight))
    b.append(f'<line x1="{front_x}" y1="200" x2="{front_x}" y2="700" stroke="{MUTED}" '
             f'stroke-width="1.2" stroke-dasharray="5,6"/>')
    b.append(f'<rect x="{front_x - 32}" y="212" width="64" height="24" rx="6" fill="#fdfcf9"/>')
    b.append(text(front_x, 230, "앞자리", 14, MUTED, "700"))
    b.append(closing(892, "앞에 놓을 수 있는 것은 한 번에 하나뿐이다"))
    return base("하나를 앞에 놓으면", "3장 · 주의가 옮겨 갈 때", "\n".join(b))


# ── p37 여든셋 가운데 열 ─────────────────────────────────────────────
TOP_TEN = [("위층", 1.00, True), ("창밖 차", 0.88, True), ("냉장고", 0.76, True),
           ("문", 0.62, True), ("계단", 0.54, True), ("엘리베이터", 0.47, True),
           ("물", 0.41, True), ("바람", 0.35, True), ("새", 0.30, True),
           ("이름 없는 웅", 0.26, False)]


def fig_eighty_three():
    b = [text(397, 200, "한 해 공책에 적힌 소리를 잦은 순서로 늘어놓으면", 19, INK, "700")]
    x0, y0, rh = 250, 254, 40
    full = 340
    for i, (name, frac, mapped) in enumerate(TOP_TEN):
        y = y0 + i * rh
        b.append(text(x0 - 14, y + 20, name, 14, INK, "400", "end"))
        b.append(f'<rect x="{x0}" y="{y + 4}" width="{full * frac:.0f}" height="24" rx="3" '
                 f'fill="{JUDGED}" fill-opacity="0.72"/>')
        mx = x0 + full * frac + 16
        if mapped:
            b.append(f'<circle cx="{mx}" cy="{y + 16}" r="4.5" fill="{INK}"/>')
        else:
            b.append(text(mx + 2, y + 22, "?", 17, JUDGED, "700"))
    # 열 개를 감싸는 괄호
    bx = x0 + full + 92
    b.append(f'<path d="M{bx} {y0 + 4} L{bx + 10} {y0 + 4} L{bx + 10} {y0 + 9 * rh + 28} '
             f'L{bx} {y0 + 9 * rh + 28}" fill="none" stroke="{MUTED}" stroke-width="2"/>')
    b.append(text(bx + 18, y0 + 4.5 * rh - 4, "전체 기록의", 13, MUTED, "400", "start"))
    b.append(text(bx + 18, y0 + 4.5 * rh + 14, "절반 넘음", 13, MUTED, "400", "start"))
    # 나머지 일흔셋
    ty = y0 + 10 * rh + 16
    for k in range(73):
        b.append(f'<rect x="{x0 + (k % 25) * 3.4:.1f}" y="{ty + (k // 25) * 12}" width="2.2" '
                 f'height="9" fill="{PALE}"/>')
    b.append(text(x0 + 110, ty + 62, "한두 번만 나온 일흔세 가지", 14, MUTED, "400", "start"))
    # 범례
    b.append(f'<circle cx="{x0}" cy="{ty + 96}" r="4.5" fill="{INK}"/>')
    b.append(text(x0 + 12, ty + 101, "지도에 자리가 있음", 13, MUTED, "400", "start"))
    b.append(closing(852, "잘 접힌 소리는 여기 오르지 않는다"))
    return base("여든셋 가운데 열", "5장 · 한 해 치 소리 목록", "\n".join(b))


FIGURES = {8: fig_twenty_three, 11: fig_regularity, 18: fig_foreground, 37: fig_eighty_three}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild015")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(fn(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
