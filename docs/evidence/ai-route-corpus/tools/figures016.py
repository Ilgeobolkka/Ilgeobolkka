#!/usr/bin/env python3
"""book-016 이미지 페이지 네 개의 SVG를 생성한다.

사용: python3 figures016.py <출력디렉터리>

네 도표에서 색의 뜻을 고정했다 — 말이 오간 것은 언제나 주황이고 말이 오가지 않은 것은 언제나 옅은
회색이다. 이 권의 세는 단위가 「말이 오간 서른두 번」이므로, 그림마다 색의 뜻이 달라지면 네 장을
이어서 읽을 수 없다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#2b3238"
MUTED = "#77828a"
LINE = "#c3cbd1"
PALE = "#dfe3e6"
SPOKE = "#c4703c"


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#fdfcf9"/>
<text x="397" y="108" text-anchor="middle" font-size="34" font-weight="700" fill="{INK}">{title}</text>
<text x="397" y="150" text-anchor="middle" font-size="18" fill="{MUTED}">{subtitle}</text>
{body}
<text x="397" y="1072" text-anchor="middle" font-size="14" fill="#98a1a7">우산 아래 나눈 짧은 문장</text>
</svg>'''


def text(x, y, value, size=17, fill=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}">{value}</text>')


def closing(y, value):
    return ('<rect x="97" y="%d" width="600" height="58" rx="16" fill="#f1efe8" stroke="%s"/>'
            % (y, LINE)) + text(397, y + 37, value, 20, INK, "700")


def person(x, y, fill, scale=1.0):
    """사람 모양 하나. 머리 원과 몸통 사다리꼴이다."""
    r = 3.4 * scale
    return (f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}" fill="{fill}"/>'
            f'<path d="M{x - 3.6 * scale:.1f} {y + 13 * scale:.1f} '
            f'L{x - 2.6 * scale:.1f} {y + 2.6 * scale:.1f} '
            f'L{x + 2.6 * scale:.1f} {y + 2.6 * scale:.1f} '
            f'L{x + 3.6 * scale:.1f} {y + 13 * scale:.1f} Z" fill="{fill}"/>')


# ── p8 백마흔 번 가운데 서른두 번 ────────────────────────────────────
SPOKEN_AT = {2, 5, 6, 17, 23, 24, 25, 38, 41, 44, 45, 52, 58, 61, 62, 63, 70, 77,
             81, 82, 88, 94, 95, 99, 106, 107, 112, 118, 121, 129, 130, 136}


def fig_one_forty():
    b = [text(397, 205, "한 해 동안 비 오는 날 처마 밑에 선 횟수", 19, INK, "700")]
    x0, y0, dx, dy = 190, 264, 21, 34
    for i in range(140):
        col, row = i % 20, i // 20
        fill = SPOKE if i in SPOKEN_AT else PALE
        b.append(person(x0 + col * dx, y0 + row * dy, fill))
    b.append(text(190, 264 + 7 * dy + 18, "회색은 아무 말 없이 헤어진 백여덟 번", 14, MUTED,
                  "400", "start"))
    b.append(text(700, 264 + 7 * dy + 18, "주황은 말이 오간 서른두 번", 14, SPOKE, "700", "end"))
    # 아래 띠: 비의 세기별
    ty = 610
    widths = [(0.24, "가는 비", 3), (0.42, "중간 세기", 26), (0.34, "센 비", 3)]
    bx = 150
    total = 500
    for frac, label, count in widths:
        w = total * frac
        b.append(f'<rect x="{bx:.0f}" y="{ty}" width="{w:.0f}" height="96" fill="#ffffff" '
                 f'stroke="{LINE}" stroke-width="1.5"/>')
        b.append(text(bx + w / 2, ty + 118, label, 15, INK, "700"))
        per_row = 9
        for k in range(count):
            col, row = k % per_row, k // per_row
            b.append(person(bx + 14 + col * 13, ty + 22 + row * 26, SPOKE, 0.8))
        bx += w
    b.append(text(397, ty + 148, "말이 오간 서른두 번을 비의 세기별로 나누면", 15, MUTED))
    b.append(closing(792, "자리가 있어야 말이 생기고 자리는 비의 세기가 정한다"))
    return base("백마흔 번 가운데 서른두 번", "1장 · 선 자리와 오간 말", "\n".join(b))


# ── p11 세 마디와 세 가지 ────────────────────────────────────────────
LENGTHS = [("두 마디", 7), ("세 마디", 12), ("네 마디", 6), ("다섯 마디", 4),
           ("여섯 마디 이상", 3)]
TOPICS = [("비 이야기", "스물한 번", 21), ("이 자리 이야기", "일곱 번", 7), ("그 밖", "네 번", 4)]


def fig_three_and_three():
    b = [text(397, 202, "서른두 번의 대화를 길이와 내용으로 나누면", 19, INK, "700")]
    x0, y0, rh = 300, 250, 40
    scale = 22
    for i, (name, n) in enumerate(LENGTHS):
        y = y0 + i * rh
        b.append(text(x0 - 14, y + 20, name, 14, INK, "400", "end"))
        b.append(f'<rect x="{x0}" y="{y + 4}" width="{n * scale}" height="24" rx="3" '
                 f'fill="{SPOKE}" fill-opacity="0.75"/>')
    b.append(text(120, y0 + 2 * rh + 20, "길이", 16, MUTED, "700", "start"))
    b.append(f'<line x1="120" y1="{y0 + 5 * rh + 20}" x2="674" y2="{y0 + 5 * rh + 20}" '
             f'stroke="{LINE}" stroke-width="1.5"/>')
    b.append(text(120, y0 + 5 * rh + 62, "내용", 16, MUTED, "700", "start"))
    cy = 560
    cx = 260
    for name, count_label, n in TOPICS:
        r = 14 + n * 3.4
        b.append(f'<circle cx="{cx:.0f}" cy="{cy}" r="{r:.0f}" fill="{SPOKE}" fill-opacity="0.16" '
                 f'stroke="{SPOKE}" stroke-width="2"/>')
        b.append(text(cx, cy - r - 12, name, 14, INK, "700"))
        b.append(text(cx, cy + 5, count_label, 13, SPOKE, "700"))
        cx += r + 106
    b.append(text(600, 660, "우산을 빌려준 일", 12, MUTED))
    b.append(text(600, 678, "짐을 들어 준 일", 12, MUTED))
    b.append(text(600, 696, "길을 알려 준 일", 12, MUTED))
    b.append(text(600, 714, "아이가 말을 건 일", 12, MUTED))
    b.append(closing(796, "서로 아는 것이 없을 때 함께 아는 것은 지금 여기뿐이다"))
    return base("세 마디와 세 가지", "2장 · 길이와 내용", "\n".join(b))


# ── p18 기댈 데가 없는 말 ────────────────────────────────────────────
def fig_dense():
    b = [text(397, 202, "같은 시간이 지난 뒤에 남아 있는 것", 19, INK, "700")]
    for row, (label, faded) in enumerate((("", False), ("한 달 뒤", True))):
        top = 262 + row * 300
        if row:
            b.append(f'<line x1="120" y1="{top - 46}" x2="674" y2="{top - 46}" stroke="{MUTED}" '
                     f'stroke-width="1.5"/>')
            b.append(f'<rect x="337" y="{top - 62}" width="120" height="30" rx="6" fill="#fdfcf9"/>')
            b.append(text(397, top - 40, label, 15, MUTED, "700"))
        for col, title in enumerate(("세 마디 대화", "한 시간 대화")):
            cx0 = 120 + col * 290
            b.append(f'<rect x="{cx0}" y="{top}" width="264" height="200" rx="14" fill="#ffffff" '
                     f'stroke="{LINE}" stroke-width="1.5"/>')
            if row == 0:
                b.append(text(cx0 + 132, top - 12, title, 16, INK, "700"))
            if col == 0:
                keep = 3
                for k in range(3):
                    if k >= keep:
                        continue
                    bx = cx0 + 52 + k * 62
                    b.append(f'<rect x="{bx}" y="{top + 78}" width="38" height="38" rx="5" '
                             f'fill="{SPOKE}" fill-opacity="0.85"/>')
                b.append(text(cx0 + 132, top + 168, "서로 기대지 않는다" if row == 0 else "그대로 셋",
                              14, MUTED))
            else:
                cells = []
                for k in range(20):
                    c, r = k % 5, k // 5
                    cells.append((cx0 + 46 + c * 36, top + 42 + r * 30))
                keep = 20 if row == 0 else 4
                for k, (bx, by) in enumerate(cells):
                    if k >= keep:
                        continue
                    op = 0.5 if row == 0 else 0.22
                    b.append(f'<rect x="{bx}" y="{by}" width="24" height="20" rx="4" '
                             f'fill="{MUTED}" fill-opacity="{op}" stroke="{MUTED}" '
                             f'stroke-opacity="{op + 0.2}"/>')
                    if row == 0 and k + 1 < 20 and (k + 1) % 5:
                        b.append(f'<line x1="{bx + 24}" y1="{by + 10}" x2="{bx + 36}" '
                                 f'y2="{by + 10}" stroke="{MUTED}" stroke-width="1" '
                                 f'stroke-opacity="0.5"/>')
                    if row == 0 and k + 5 < 20:
                        b.append(f'<line x1="{bx + 12}" y1="{by + 20}" x2="{bx + 12}" '
                                 f'y2="{by + 30}" stroke="{MUTED}" stroke-width="1" '
                                 f'stroke-opacity="0.5"/>')
                b.append(text(cx0 + 132, top + 168,
                              "앞뒤에 기대어 있다" if row == 0 else "서넛만 흐리게 남고 선은 없다",
                              14, MUTED))
    b.append(closing(842, "진하다는 것은 깊다는 뜻이 아니라 기댈 데가 없다는 뜻이다"))
    return base("기댈 데가 없는 말", "3장 · 한 달 뒤에 남는 것", "\n".join(b))


# ── p37 서른둘을 갈라 보면 ───────────────────────────────────────────
SPLITS = [("누가 먼저", 9, "내가 먼저", "상대가 먼저"),
          ("어떻게 끝났나", 19, "그냥 멈춤", "한쪽이 나감"),
          ("지금도 외우나", 11, "외움", "기록을 봐야")]


def fig_splits():
    b = [text(397, 205, "한 해 치 대화를 세 번 갈라 세면", 19, INK, "700")]
    x0, wtot = 240, 420
    cell = wtot / 32
    for i, (label, left, lname, rname) in enumerate(SPLITS):
        y = 300 + i * 150
        b.append(text(x0 - 16, y + 26, label, 16, INK, "700", "end"))
        for k in range(32):
            fill = SPOKE if k < left else PALE
            b.append(f'<rect x="{x0 + k * cell:.1f}" y="{y}" width="{cell - 1.4:.1f}" height="40" '
                     f'fill="{fill}" fill-opacity="0.85"/>')
        b.append(text(x0 + left * cell / 2, y + 62, f"{lname} {left}", 13, SPOKE, "700"))
        b.append(text(x0 + (left + 32) * cell / 2, y + 62, f"{rname} {32 - left}", 13, MUTED))
    mid = x0 + 16 * cell
    b.append(f'<line x1="{mid:.1f}" y1="272" x2="{mid:.1f}" y2="656" stroke="{MUTED}" '
             f'stroke-width="1.4" stroke-dasharray="5,6"/>')
    b.append(f'<rect x="{mid - 24:.1f}" y="252" width="48" height="24" rx="6" fill="#fdfcf9"/>')
    b.append(text(mid, 270, "반", 14, MUTED, "700"))
    b.append(closing(736, "어느 것도 반반이 아니었다"))
    return base("서른둘을 갈라 보면", "5장 · 세 기준으로 나눈 결과", "\n".join(b))


FIGURES = {8: fig_one_forty, 11: fig_three_and_three, 18: fig_dense, 37: fig_splits}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild016")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(fn(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
