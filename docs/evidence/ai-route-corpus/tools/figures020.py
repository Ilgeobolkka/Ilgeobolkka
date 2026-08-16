#!/usr/bin/env python3
"""book-020 이미지 페이지 네 개의 SVG를 생성한다.

사용: python3 figures020.py <출력디렉터리>

네 도표에서 표기를 고정했다 — 셀 수 있는 것은 언제나 테두리가 있는 도형이고, 셀 수 없는 것(알아차리지
못한 것)은 언제나 경계가 없는 옅은 영역이다. 이 권의 주장이 「백열아홉은 일어난 일의 수가 아니라
알아차리고 적은 것의 수」이므로, 이 구분이 흔들리면 네 장을 이어서 읽을 수 없다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#2b3238"
MUTED = "#77828a"
LINE = "#c3cbd1"
PALE = "#e2e5e7"
WARM = "#c4703c"
SOFT = "#8aa39b"


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#fdfcf9"/>
<defs>
  <marker id="m" markerWidth="9" markerHeight="9" refX="8" refY="3" orient="auto">
    <path d="M0,0 L0,6 L8,3 z" fill="{MUTED}"/>
  </marker>
  <linearGradient id="fade" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0%" stop-color="{MUTED}" stop-opacity="0.22"/>
    <stop offset="100%" stop-color="{MUTED}" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="fadeup" x1="0" y1="1" x2="0" y2="0">
    <stop offset="0%" stop-color="{MUTED}" stop-opacity="0.20"/>
    <stop offset="100%" stop-color="{MUTED}" stop-opacity="0"/>
  </linearGradient>
</defs>
<text x="397" y="108" text-anchor="middle" font-size="34" font-weight="700" fill="{INK}">{title}</text>
<text x="397" y="150" text-anchor="middle" font-size="18" fill="{MUTED}">{subtitle}</text>
{body}
<text x="397" y="1072" text-anchor="middle" font-size="14" fill="#98a1a7">말하지 않은 친절의 모양</text>
</svg>'''


def text(x, y, value, size=17, fill=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}">{value}</text>')


def closing(y, value):
    return ('<rect x="87" y="%d" width="620" height="58" rx="16" fill="#f1efe8" stroke="%s"/>'
            % (y, LINE)) + text(397, y + 37, value, 20, INK, "700")


# ── p8 네 조건을 통과한 것 ───────────────────────────────────────────
CONDITIONS = [("하지 않아도 되는 일인가", "일이라서 한 것"),
              ("그 사람에게 값이 들었나", "값이 안 든 것"),
              ("모르는 사람인가", "아는 사람이 해 준 것"),
              ("말이 오가지 않았나", "길을 알려 준 것")]


def fig_funnel():
    b = [text(397, 205, "무엇을 이 목록에 올리는가", 19, INK, "700")]
    top_y, bot_y = 262, 640
    top_half, bot_half = 190, 62
    cx = 300
    b.append(f'<path d="M{cx - top_half} {top_y} L{cx + top_half} {top_y} '
             f'L{cx + bot_half} {bot_y} L{cx - bot_half} {bot_y} Z" fill="#ffffff" '
             f'stroke="{LINE}" stroke-width="1.8"/>')
    b.append(text(cx, top_y - 12, "하루에 있었던 일 전부", 15, INK, "700"))
    for i, (cond, out) in enumerate(CONDITIONS):
        t = (i + 1) / (len(CONDITIONS) + 1)
        y = top_y + (bot_y - top_y) * t
        half = top_half + (bot_half - top_half) * t
        b.append(f'<line x1="{cx - half:.0f}" y1="{y:.0f}" x2="{cx + half:.0f}" y2="{y:.0f}" '
                 f'stroke="{MUTED}" stroke-width="1.4" stroke-dasharray="5,5"/>')
        b.append(text(cx - half - 10, y - 6, cond, 12, INK, "400", "end"))
        b.append(f'<line x1="{cx + half:.0f}" y1="{y:.0f}" x2="{cx + half + 44:.0f}" '
                 f'y2="{y - 14:.0f}" stroke="{MUTED}" stroke-width="1.4" marker-end="url(#m)"/>')
        b.append(text(cx + half + 50, y - 12, out, 11.5, MUTED, "400", "start"))
    b.append(f'<rect x="{cx - 96}" y="{bot_y + 20}" width="192" height="62" rx="10" fill="{SOFT}" '
             f'fill-opacity="0.18" stroke="{SOFT}" stroke-width="2"/>')
    b.append(text(cx, bot_y + 46, "이 목록에 오르는 것", 14, INK, "700"))
    b.append(text(cx, bot_y + 68, "한 해에 백열아홉", 13, SOFT, "700"))
    # 세 가지 작은 그림
    gx = cx + 150
    b.append(f'<path d="M{gx} {bot_y + 34} L{gx} {bot_y + 74}" stroke="{INK}" stroke-width="3"/>')
    b.append(f'<path d="M{gx - 16} {bot_y + 56} L{gx - 2} {bot_y + 56}" stroke="{INK}" '
             f'stroke-width="3" stroke-linecap="round"/>')
    b.append(text(gx, bot_y + 92, "문", 11, MUTED))
    b.append(f'<path d="M{gx + 48} {bot_y + 74} L{gx + 60} {bot_y + 56} L{gx + 74} {bot_y + 62}" '
             f'fill="none" stroke="{INK}" stroke-width="3" stroke-linecap="round"/>')
    b.append(text(gx + 60, bot_y + 92, "발", 11, MUTED))
    b.append(f'<path d="M{gx + 100} {bot_y + 66} A26 26 0 0 1 {gx + 148} {bot_y + 52}" '
             f'fill="none" stroke="{INK}" stroke-width="3"/>')
    b.append(text(gx + 124, bot_y + 92, "우산", 11, MUTED))
    b.append(closing(778, "네 조건을 다 통과해야 한 개다"))
    return base("네 조건을 통과한 것", "1장 · 무엇을 세는가", "\n".join(b))


# ── p11 아홉에서 열일곱으로 ──────────────────────────────────────────
# 2.1·5.3 본문이 센 값에 맞춘다 — 첫 달 아홉, 마지막 달 열일곱, 한 해 백열아홉.
MONTHLY = [9, 8, 9, 9, 10, 5, 5, 9, 11, 12, 15, 17]
assert (MONTHLY[0], MONTHLY[-1], sum(MONTHLY)) == (9, 17, 119)


def fig_monthly():
    b = [text(397, 205, "달마다 목록에 오른 수", 19, INK, "700")]
    x0, base_y = 150, 640
    gw, bw = 46, 30
    scale = 21
    # 배경: 셀 수 없는 영역
    b.append(f'<rect x="{x0 - 20}" y="238" width="{12 * gw + 40}" height="{base_y - 238}" '
             f'fill="url(#fadeup)"/>')
    b.append(text(x0 + 6 * gw, 286, "실제로 일어난 일", 15, MUTED, "700"))
    b.append(text(x0 + 6 * gw, 308, "셀 수 없음", 13, MUTED))
    for i, v in enumerate(MONTHLY):
        gx = x0 + i * gw
        h = v * scale
        b.append(f'<rect x="{gx}" y="{base_y - h}" width="{bw}" height="{h}" rx="3" '
                 f'fill="{WARM}" fill-opacity="0.68"/>')
        b.append(text(gx + bw / 2, base_y + 20, str(i + 1), 11, MUTED))
    b.append(text(x0 + bw / 2, base_y - MONTHLY[0] * scale - 10, "아홉", 12, WARM, "700"))
    b.append(text(x0 + 11 * gw + bw / 2, base_y - MONTHLY[-1] * scale - 10, "열일곱", 12, WARM, "700"))
    b.append(f'<line x1="{x0 - 12}" y1="{base_y}" x2="{x0 + 12 * gw}" y2="{base_y}" '
             f'stroke="{INK}" stroke-width="1.6"/>')
    b.append(text(x0 + 6 * gw, base_y + 42, "달", 12, MUTED))
    b.append(f'<line x1="{x0 + 30}" y1="{base_y + 74}" x2="{x0 + 12 * gw - 30}" y2="{base_y + 74}" '
             f'stroke="{MUTED}" stroke-width="2" marker-end="url(#m)"/>')
    b.append(f'<rect x="{x0 + 4 * gw}" y="{base_y + 60}" width="150" height="28" fill="#fdfcf9"/>')
    b.append(text(x0 + 4 * gw + 75, base_y + 80, "달라진 것은 내 쪽이다", 13, MUTED, "700"))
    b.append(text(x0 + 5.5 * gw, base_y - 8 * scale - 40, "바빴던 두 달", 12, MUTED))
    b.append(closing(800, "세상이 친절해진 것이 아니라 보게 된 것이다"))
    return base("아홉에서 열일곱으로", "2장 · 달마다 오른 수", "\n".join(b))


# ── p18 돌아가지 않는 화살표 ─────────────────────────────────────────
def fig_one_way():
    b = [text(397, 205, "아는 사이와 모르는 사이에서 흐름이 다르다", 19, INK, "700")]
    for col, title in enumerate(("아는 사이", "모르는 사이")):
        cx0 = 120 + col * 300
        b.append(f'<rect x="{cx0}" y="270" width="264" height="300" rx="14" fill="#ffffff" '
                 f'stroke="{LINE}" stroke-width="1.6"/>')
        b.append(text(cx0 + 132, 254, title, 17, INK, "700"))
        ax, bxx, cy = cx0 + 66, cx0 + 198, 396
        for x, label in ((ax, "나" if col == 0 else "모르는 사람"),
                         (bxx, "아는 사람" if col == 0 else "나")):
            b.append(f'<circle cx="{x}" cy="{cy}" r="34" fill="#ffffff" stroke="{INK}" '
                     f'stroke-width="2"/>')
            b.append(text(x, cy + 5, label, 11 if len(label) > 2 else 14, INK, "700"))
        b.append(f'<path d="M{ax + 36} {cy - 16} C{ax + 70} {cy - 42}, {bxx - 70} {cy - 42}, '
                 f'{bxx - 36} {cy - 16}" fill="none" stroke="{MUTED}" stroke-width="2" '
                 f'marker-end="url(#m)"/>')
        b.append(text((ax + bxx) / 2, cy - 42, "받는다", 12, MUTED, "700"))
        if col == 0:
            b.append(f'<path d="M{bxx - 36} {cy + 16} C{bxx - 70} {cy + 42}, {ax + 70} {cy + 42}, '
                     f'{ax + 36} {cy + 16}" fill="none" stroke="{MUTED}" stroke-width="2" '
                     f'marker-end="url(#m)"/>')
            b.append(text((ax + bxx) / 2, cy + 56, "갚는다", 12, MUTED, "700"))
        else:
            b.append(f'<path d="M{bxx - 36} {cy + 16} C{bxx - 60} {cy + 40}, {bxx - 88} {cy + 44}, '
                     f'{bxx - 104} {cy + 40}" fill="none" stroke="{MUTED}" stroke-width="1.6" '
                     f'stroke-dasharray="5,5" stroke-opacity="0.6"/>')
            b.append(text(bxx - 118, cy + 46, "?", 18, MUTED, "700"))
            b.append(f'<line x1="{bxx}" y1="{cy + 36}" x2="{bxx}" y2="{cy + 128}" '
                     f'stroke="{MUTED}" stroke-width="2" marker-end="url(#m)"/>')
            b.append(text(bxx, cy + 152, "어디로 가는지는 4장", 12, MUTED))
    b.append(closing(650, "갚으라는 사람이 없는 것을 무엇이라 부를까"))
    return base("돌아가지 않는 화살표", "3장 · 두 흐름", "\n".join(b))


# ── p37 백열아홉 개의 모양 ───────────────────────────────────────────
KINDS = [("문 잡아 줌", 31), ("자리 비켜 줌", 24), ("떨어진 것 주워 줌", 19),
         ("길 터 줌", 17), ("비 가려 줌", 14), ("나머지", 14)]
PLACES = [("지하철·버스", 44), ("건물 출입구", 38), ("길", 25), ("가게 안", 12)]
SPLITS = [("눈 마주침", 60, 119), ("그 자리에서 앎", 68, 119), ("받고 싶지 않았음", 9, 119)]


def fig_shapes():
    b = [text(397, 200, "무엇을 받았고 어디서 받았나", 19, INK, "700")]
    x0, y0, rh = 250, 244, 34
    scale = 9.4
    for i, (name, n) in enumerate(KINDS):
        y = y0 + i * rh
        b.append(text(x0 - 12, y + 18, name, 13, INK, "400", "end"))
        b.append(f'<rect x="{x0}" y="{y + 4}" width="{n * scale:.0f}" height="22" rx="3" '
                 f'fill="{WARM}" fill-opacity="0.62"/>')
        b.append(text(x0 + n * scale + 10, y + 20, str(n), 12, MUTED, "700", "start"))
    # 아래 왼쪽: 자리별 네모
    ty = y0 + 6 * rh + 30
    px = 96
    for name, n in PLACES:
        side = (n * 5.2) ** 0.5 * 5.4
        b.append(f'<rect x="{px}" y="{ty + 96 - side:.0f}" width="{side:.0f}" height="{side:.0f}" '
                 f'rx="4" fill="{SOFT}" fill-opacity="0.32" stroke="{SOFT}" stroke-width="1.4"/>')
        b.append(text(px + side / 2, ty + 116, name, 11, MUTED))
        b.append(text(px + side / 2, ty + 132, str(n), 11, MUTED, "700"))
        px += side + 22
    # 아래 오른쪽: 세 띠
    sx, sw = 470, 220
    for i, (label, part, total) in enumerate(SPLITS):
        y = ty + 16 + i * 44
        b.append(text(sx, y - 6, label, 12, MUTED, "400", "start"))
        b.append(f'<rect x="{sx}" y="{y}" width="{sw}" height="18" rx="3" fill="{PALE}"/>')
        b.append(f'<rect x="{sx}" y="{y}" width="{sw * part / total:.0f}" height="18" rx="3" '
                 f'fill="{WARM}" fill-opacity="0.62"/>')
    # 맨 아래: 셀 수 없는 띠
    fy = ty + 172
    b.append(f'<rect x="96" y="{fy}" width="600" height="34" fill="url(#fade)"/>')
    b.append(text(150, fy + 22, "알아차리지 못한 것 · 셀 수 없음", 13, MUTED, "700", "start"))
    b.append(closing(fy + 62, "백열아홉은 알아차리고 적은 것의 수다"))
    return base("백열아홉 개의 모양", "5장 · 한 해 치 목록", "\n".join(b))


FIGURES = {8: fig_funnel, 11: fig_monthly, 18: fig_one_way, 37: fig_shapes}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild020")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(fn(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
