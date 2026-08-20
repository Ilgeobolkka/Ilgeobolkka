#!/usr/bin/env python3
"""book-012 이미지 페이지 네 개의 SVG를 생성한다.

사용: python3 figures012.py <출력디렉터리>

네 도표에서 표기를 고정했다 — 창밖의 것(창틀·담장·가지·화분)은 회색 계열이고, 내가 알아본 것을
표시하는 색만 노랑과 초록이다. 창은 어느 그림에서나 그대로이고 달라지는 것은 그 위에 얹힌 표시라는
것이 이 책의 주장이라, 그림에서도 창 쪽 색을 바꾸지 않는다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#2b3238"
MUTED = "#77828a"
LINE = "#c3cbd1"
WALL = "#d7d2c9"
LEAF = "#4b7f5a"
MARK = "#e0a83c"
WARN = "#b8564b"


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#fdfcf9"/>
<defs>
  <marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
    <path d="M0,0 L0,6 L9,3 z" fill="{MUTED}"/>
  </marker>
  <marker id="arrow-warn" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
    <path d="M0,0 L0,6 L9,3 z" fill="{WARN}"/>
  </marker>
  <pattern id="miss" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
    <rect width="7" height="7" fill="#ffffff"/>
    <line x1="0" y1="0" x2="0" y2="7" stroke="{LINE}" stroke-width="2"/>
  </pattern>
</defs>
<text x="397" y="108" text-anchor="middle" font-size="34" font-weight="700" fill="{INK}">{title}</text>
<text x="397" y="150" text-anchor="middle" font-size="18" fill="{MUTED}">{subtitle}</text>
{body}
<text x="397" y="1072" text-anchor="middle" font-size="14" fill="#98a1a7">창가의 작은 계절들</text>
</svg>'''


def text(x, y, value, size=17, fill=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}">{value}</text>')


def closing(y, value):
    return ('<rect x="147" y="%d" width="500" height="58" rx="16" fill="#f1efe8" stroke="%s"/>'
            % (y, LINE)) + text(397, y + 37, value, 20, INK, "700")


# ── p8 같은 창, 세 번 보기 ────────────────────────────────────────────
def window(x, y, w, h):
    """세 칸에 똑같이 들어가는 창 그림. 좌표는 창틀 왼쪽 위 모서리."""
    b = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#ffffff" '
         f'stroke="{INK}" stroke-width="3"/>']
    for i in (1, 2):
        b.append(f'<line x1="{x + w * i / 3:.1f}" y1="{y}" x2="{x + w * i / 3:.1f}" '
                 f'y2="{y + h}" stroke="{LINE}" stroke-width="1.5"/>')
        b.append(f'<line x1="{x}" y1="{y + h * i / 3:.1f}" x2="{x + w}" '
                 f'y2="{y + h * i / 3:.1f}" stroke="{LINE}" stroke-width="1.5"/>')
    # 담장: 아래쪽을 가로지르고 벽돌 줄눈을 몇 개 넣는다
    wall_y = y + h * 0.72
    b.append(f'<rect x="{x}" y="{wall_y:.1f}" width="{w}" height="{h * 0.28:.1f}" fill="{WALL}"/>')
    for k in range(1, 6):
        b.append(f'<line x1="{x + w * k / 6:.1f}" y1="{wall_y:.1f}" x2="{x + w * k / 6:.1f}" '
                 f'y2="{wall_y + 16:.1f}" stroke="#c0b9ac" stroke-width="1.5"/>')
    b.append(f'<line x1="{x}" y1="{wall_y + 16:.1f}" x2="{x + w}" y2="{wall_y + 16:.1f}" '
             f'stroke="#c0b9ac" stroke-width="1.5"/>')
    # 가지: 왼쪽 위 모서리에서 들어와 가운데 칸에서 끊긴다
    b.append(f'<path d="M{x} {y + 6} L{x + w * 0.22:.1f} {y + h * 0.20:.1f} '
             f'L{x + w * 0.46:.1f} {y + h * 0.36:.1f}" fill="none" stroke="#8a7f70" '
             f'stroke-width="5" stroke-linecap="round"/>')
    b.append(f'<path d="M{x + w * 0.22:.1f} {y + h * 0.20:.1f} L{x + w * 0.34:.1f} '
             f'{y + h * 0.09:.1f}" fill="none" stroke="#8a7f70" stroke-width="3.5" '
             f'stroke-linecap="round"/>')
    # 담장 위 화분 셋 (오른쪽), 하나만 붉다
    for k, cx in enumerate((0.58, 0.72, 0.86)):
        fill = WARN if k == 1 else "#b3aa9d"
        px = x + w * cx
        b.append(f'<rect x="{px - 11:.1f}" y="{wall_y - 22:.1f}" width="22" height="22" rx="3" '
                 f'fill="{fill}"/>')
    # 담장 가운데의 얼룩
    b.append(f'<ellipse cx="{x + w * 0.30:.1f}" cy="{wall_y + 40:.1f}" rx="17" ry="11" '
             f'fill="#c6bdb0"/>')
    return "\n".join(b)


def mark(x, y, r, opacity=0.85):
    return (f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r}" fill="{MARK}" fill-opacity="{opacity * 0.35}" '
            f'stroke="{MARK}" stroke-width="2.5" stroke-opacity="{opacity}"/>')


def fig_three_looks():
    b = []
    b.append(text(397, 205, "같은 창을 세 번 보면 눈이 머무는 자리가 옮겨 간다", 19, INK, "700"))
    xs = (48, 287, 526)
    y, w, h = 250, 220, 300
    labels = ("첫 번째", "두 번째", "세 번째")
    for i, x in enumerate(xs):
        b.append(window(x, y, w, h))
        b.append(text(x + w / 2, y + h + 34, labels[i], 19, INK, "700"))
        wall_y = y + h * 0.66
        pot_x = x + w * 0.72
        if i == 0:
            b.append(mark(pot_x, wall_y - 11, 30))
        elif i == 1:
            b.append(mark(pot_x, wall_y - 11, 22, 0.35))
            b.append(mark(x + w * 0.30, wall_y + 34, 20))
            b.append(mark(x + w * 0.46, y + h * 0.36, 18))
        else:
            b.append(mark(pot_x, wall_y - 11, 16, 0.15))
            b.append(mark(x + w * 0.14, wall_y + 8, 14))
            b.append(mark(x + w * 0.90, y + h - 20, 14))
            b.append(mark(x + w * 0.22, y + h * 0.20, 14))
    # 세 칸을 가로지르는 화살표
    ay = y + h + 82
    b.append(f'<line x1="{xs[0] + 20}" y1="{ay}" x2="{xs[2] + w - 20}" y2="{ay}" '
             f'stroke="{MUTED}" stroke-width="3" marker-end="url(#arrow)"/>')
    b.append(f'<rect x="322" y="{ay - 19}" width="150" height="38" rx="10" fill="#fdfcf9"/>')
    b.append(text(397, ay + 7, "창은 그대로다", 19, MUTED, "700"))
    b.append(text(397, ay + 74, "노란 자리는 그때 눈이 머문 곳이다", 17, MUTED))
    b.append(closing(ay + 110, "달라지는 것은 창이 아니라 내가 보는 자리다"))
    return base("같은 창, 세 번 보기", "1장 · 되풀이해 볼 때 눈이 옮겨 가는 자리", "\n".join(b))


# ── p11 사람들이 기억하는 하루 ───────────────────────────────────────
DAILY = [3, 5, 4, 7, 6, 9, 8, 12, 11, 15, 14, 18, 17, 20, 19, 22, 21, 19, 17, 16,
         14, 13, 11, 10, 8, 7, 6, 5, 4, 2]


def fig_last_day():
    b = []
    b.append(text(397, 210, "잎이 다 떨어진 날에 달라진 것은 가장 적다", 19, INK, "700"))
    x0, y0, bw, gap = 92, 640, 17, 4.5
    top = 300
    scale = (y0 - top) / max(DAILY)
    b.append(f'<line x1="{x0 - 16}" y1="{y0}" x2="{x0 + len(DAILY) * (bw + gap)}" y2="{y0}" '
             f'stroke="{INK}" stroke-width="2"/>')
    b.append(f'<line x1="{x0 - 16}" y1="{top - 10}" x2="{x0 - 16}" y2="{y0}" '
             f'stroke="{INK}" stroke-width="2"/>')
    b.append(text(x0 - 16, top - 24, "그날 달라진 몫", 15, MUTED, "400", "start"))
    for i, v in enumerate(DAILY):
        x = x0 + i * (bw + gap)
        hgt = v * scale
        fill = WARN if i == len(DAILY) - 1 else "#b9c2c7"
        b.append(f'<rect x="{x:.1f}" y="{y0 - hgt:.1f}" width="{bw}" height="{hgt:.1f}" '
                 f'rx="2" fill="{fill}"/>')
    b.append(text(x0 + 6, y0 + 28, "이른 가을", 15, MUTED, "400", "start"))
    b.append(text(x0 + len(DAILY) * (bw + gap) - 6, y0 + 28, "늦은 가을", 15, MUTED, "400", "end"))
    b.append(text(397, y0 + 56, "가을의 여러 주", 16, MUTED))
    # 누적 곡선
    total = sum(DAILY)
    acc, pts = 0, []
    for i, v in enumerate(DAILY):
        acc += v
        pts.append((x0 + i * (bw + gap) + bw / 2, y0 - acc / total * (y0 - top - 20)))
    path = "M" + " L".join(f"{px:.1f} {py:.1f}" for px, py in pts)
    b.append(f'<path d="{path}" fill="none" stroke="#3f6f8f" stroke-width="4"/>')
    b.append(text(pts[-1][0] - 8, pts[-1][1] - 18, "쌓인 몫", 17, "#3f6f8f", "700", "end"))
    # 마지막 막대 이름표
    lx = x0 + (len(DAILY) - 1) * (bw + gap) + bw / 2
    b.append(f'<line x1="{lx:.1f}" y1="{y0 - DAILY[-1] * scale - 8:.1f}" x2="{lx:.1f}" '
             f'y2="{y0 - 150}" stroke="{WARN}" stroke-width="2" stroke-dasharray="5,4"/>')
    b.append(f'<rect x="{lx - 118:.1f}" y="{y0 - 214}" width="150" height="62" rx="12" '
             f'fill="#fbeeec" stroke="{WARN}" stroke-width="2"/>')
    b.append(text(lx - 43, y0 - 190, "잎이 다 떨어진 날", 16, WARN, "700"))
    b.append(text(lx - 43, y0 - 167, "사람들이 기억하는 하루", 15, WARN))
    b.append(text(397, y0 + 108, "회색 막대는 하루치이고 파란 선은 그때까지 쌓인 몫이다", 17, MUTED))
    b.append(closing(y0 + 140, "눈에 띄는 날은 변화가 끝난 날이다"))
    return base("사람들이 기억하는 하루", "2장 · 하루치 변화와 쌓인 변화", "\n".join(b))


# ── p18 계절은 여러 줄로 온다 ────────────────────────────────────────
ROWS = [("저녁이 밝아짐", 0.02, 0.98),
        ("가지 끝이 붉어짐", 0.18, 0.72),
        ("담장 오른쪽의 풀", 0.34, 0.95),
        ("담장 왼쪽의 풀", 0.55, 0.95),
        ("잎이 창틀을 넘음", 0.74, 1.0)]


def fig_many_lines():
    b = []
    b.append(text(397, 202, "한 창에서 본 봄의 지표들", 19, INK, "700"))
    b.append(text(397, 228, "같은 창, 같은 해", 16, MUTED))
    lx, x0, x1 = 218, 236, 730
    y0, rh = 312, 74
    span = x1 - x0
    b.append(f'<line x1="{x0}" y1="{y0}" x2="{x1}" y2="{y0}" stroke="{INK}" stroke-width="2"/>')
    for k in range(1, 6):
        gx = x0 + span * k / 6
        b.append(f'<line x1="{gx:.1f}" y1="{y0}" x2="{gx:.1f}" y2="{y0 + rh * len(ROWS) + 10}" '
                 f'stroke="{LINE}" stroke-width="1" stroke-dasharray="3,5"/>')
    b.append(text(x0, y0 - 14, "이른 봄", 16, MUTED, "400", "start"))
    b.append(text(x1, y0 - 14, "늦은 봄", 16, MUTED, "400", "end"))
    for i, (label, s, e) in enumerate(ROWS):
        cy = y0 + rh * i + rh / 2 + 8
        b.append(text(lx, cy + 6, label, 17, INK, "400", "end"))
        b.append(f'<line x1="{x0}" y1="{cy + 30}" x2="{x1}" y2="{cy + 30}" stroke="{LINE}" '
                 f'stroke-width="1"/>')
        bx, bw = x0 + span * s, span * (e - s)
        b.append(f'<rect x="{bx:.1f}" y="{cy - 16:.1f}" width="{bw:.1f}" height="32" rx="9" '
                 f'fill="{LEAF}" fill-opacity="0.75"/>')
        b.append(f'<line x1="{bx:.1f}" y1="{cy - 22:.1f}" x2="{bx:.1f}" y2="{cy + 22:.1f}" '
                 f'stroke="{LEAF}" stroke-width="3"/>')
    # 셋째 줄과 넷째 줄 사이의 벌어짐
    y3 = y0 + rh * 2 + rh / 2 + 8
    y4 = y0 + rh * 3 + rh / 2 + 8
    gx3, gx4 = x0 + span * ROWS[2][1], x0 + span * ROWS[3][1]
    b.append(f'<line x1="{gx3:.1f}" y1="{y3 + 22:.1f}" x2="{gx3:.1f}" y2="{y4 - 22:.1f}" '
             f'stroke="{WARN}" stroke-width="1.5" stroke-dasharray="4,4"/>')
    my = (y3 + y4) / 2
    b.append(f'<line x1="{gx3:.1f}" y1="{my:.1f}" x2="{gx4 - 4:.1f}" y2="{my:.1f}" '
             f'stroke="{WARN}" stroke-width="2.5" marker-end="url(#arrow-warn)"/>')
    b.append(f'<rect x="{(gx3 + gx4) / 2 - 26:.1f}" y="{my - 34:.1f}" width="52" height="24" '
             f'rx="6" fill="#fdfcf9"/>')
    b.append(text((gx3 + gx4) / 2, my - 16, "두 주", 16, WARN, "700"))
    # 이 무렵을 세로로 자르는 띠
    cut = x0 + span * 0.44
    b.append(f'<rect x="{cut - 13:.1f}" y="{y0 - 4}" width="26" height="{rh * len(ROWS) + 14}" '
             f'fill="#8d99a2" fill-opacity="0.20"/>')
    b.append(text(cut, y0 - 46, "이 무렵", 17, "#5d6a72", "700"))
    b.append(text(cut, y0 + rh * len(ROWS) + 42, "겨울과 봄이 함께 있는 몇 주", 17, "#5d6a72", "700"))
    b.append(closing(y0 + rh * len(ROWS) + 82, "시작한 날을 하나로 찍을 수 없다"))
    return base("계절은 여러 줄로 온다", "3장 · 지표마다 다른 시작", "\n".join(b))


# ── p37 한 해를 늘어놓으면 ───────────────────────────────────────────
MONTHS = ["첫째 달", "둘째 달", "셋째 달", "넷째 달", "다섯째 달", "여섯째 달",
          "일곱째 달", "여덟째 달", "아홉째 달", "열째 달", "열한째 달", "열두째 달"]
DAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]


def year_state(month, day):
    """달·날에서 칸의 상태를 정한다. 무작위를 쓰지 않아 다시 실행해도 같은 그림이 나온다.

    나오는 날수는 5.3 본문이 센 값(못 봄 마흔 날, 달라짐 팔십 날에 조금 못 미침, 나머지 이백사십
    날쯤)에 맞춰 골랐다. 여섯째~여덟째 달에 초록을 두지 않은 것은 한여름에 같아 보이는 구간이
    길었다는 본문과 맞추기 위해서다.
    """
    if month == 7 and 12 <= day <= 20:
        return "miss"
    if (month * 3 + day * 5) % 11 == 0:
        return "miss"
    if month in (4, 5, 9, 10) and day % 2 == 1:
        return "green"
    if month in (3, 11) and day % 5 == 2:
        return "green"
    if month in (1, 2, 12) and day % 11 == 3:
        return "green"
    return "gray"


def fig_year():
    counts = {"gray": 0, "green": 0, "miss": 0}
    for m, n in enumerate(DAYS, start=1):
        for d in range(1, n + 1):
            counts[year_state(m, d)] += 1
    b = [text(397, 208, "한 해치 수첩을 날짜 순서로 늘어놓으면", 19, INK, "700")]
    x0, y0, cell, rh = 158, 300, 14.6, 42
    fills = {"gray": "#e2e5e6", "green": LEAF, "miss": "url(#miss)"}
    for m, n in enumerate(DAYS, start=1):
        cy = y0 + (m - 1) * rh
        b.append(text(x0 - 12, cy + 20, MONTHS[m - 1], 14, MUTED, "400", "end"))
        for d in range(1, n + 1):
            state = year_state(m, d)
            b.append(f'<rect x="{x0 + (d - 1) * cell:.1f}" y="{cy + 6}" width="{cell - 2.2:.1f}" '
                     f'height="26" rx="2" fill="{fills[state]}" stroke="#ffffff" stroke-width="1"/>')
    table_bottom = y0 + len(DAYS) * rh
    # 범례
    ly = 236
    for k, (state, label) in enumerate((("gray", "적지 못함"), ("green", "달라짐"), ("miss", "못 봄"))):
        lx = 476 + k * 108
        b.append(f'<rect x="{lx}" y="{ly}" width="18" height="18" rx="2" fill="{fills[state]}" '
                 f'stroke="{LINE}"/>')
        b.append(text(lx + 24, ly + 14, label, 14, MUTED, "400", "start"))
    # 오른쪽 막대 셋
    bx0, bw, btop = 646, 34, 330
    bmax = max(counts.values())
    for k, (state, label) in enumerate((("gray", "적지 못함"), ("green", "달라짐"), ("miss", "못 봄"))):
        hgt = counts[state] / bmax * (table_bottom - btop - 20)
        bx = bx0 + k * (bw + 20)
        b.append(f'<rect x="{bx}" y="{table_bottom - 20 - hgt:.1f}" width="{bw}" '
                 f'height="{hgt:.1f}" rx="4" fill="{fills[state]}" stroke="{LINE}"/>')
        b.append(text(bx + bw / 2, table_bottom - 26 - hgt, str(counts[state]), 14, MUTED, "700"))
        b.append(text(bx + bw / 2, table_bottom + 2, label, 13, MUTED))
    b.append(text(397, table_bottom + 44, "한 줄이 한 달이고 한 칸이 하루다", 17, MUTED))
    b.append(closing(table_bottom + 70, "빈칸도 회색 칸도 그해의 일부다"))
    return base("한 해를 늘어놓으면", "5장 · 한 해치 기록의 모양", "\n".join(b))


FIGURES = {8: fig_three_looks, 11: fig_last_day, 18: fig_many_lines, 37: fig_year}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild012")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(fn(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
