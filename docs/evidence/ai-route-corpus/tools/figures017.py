#!/usr/bin/env python3
"""book-017 이미지 페이지 네 개의 SVG를 생성한다.

사용: python3 figures017.py <출력디렉터리>

네 도표에서 표기를 고정했다 — 다섯 서랍은 언제나 같은 순서(오늘 지나간 것·아직 남은 것·내가 할 수
있는 것·남이 정하는 것·모르겠는 것)로 놓이고, 미결을 뜻하는 것은 언제나 점선이다. 이 권의 방법이
같은 다섯 칸을 되풀이해 쓰는 것이므로, 그림마다 순서가 달라지면 네 장을 이어서 읽을 수 없다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#2b3238"
MUTED = "#77828a"
LINE = "#c3cbd1"
PALE = "#e2e5e7"
MARK = "#7a6a9c"
WARM = "#c4703c"

DRAWERS = ["오늘 지나간 것", "아직 남은 것", "내가 할 수 있는 것", "남이 정하는 것", "모르겠는 것"]


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
<text x="397" y="1072" text-anchor="middle" font-size="14" fill="#98a1a7">마음의 서랍을 정리하는 밤</text>
</svg>'''


def text(x, y, value, size=17, fill=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'fill="{fill}" font-weight="{weight}">{value}</text>')


def closing(y, value):
    return ('<rect x="87" y="%d" width="620" height="58" rx="16" fill="#f1efe8" stroke="%s"/>'
            % (y, LINE)) + text(397, y + 37, value, 20, INK, "700")


# ── p8 서랍 다섯과 하루치 ────────────────────────────────────────────
def fig_five_drawers():
    b = [text(397, 205, "하루가 끝나면 그날 것만 꺼낸다", 19, INK, "700")]
    bx, by, bw, rh = 120, 262, 250, 66
    b.append(f'<rect x="{bx}" y="{by}" width="{bw}" height="{rh * 5}" rx="8" fill="#ffffff" '
             f'stroke="{INK}" stroke-width="2.5"/>')
    for i, name in enumerate(DRAWERS):
        y = by + i * rh
        dashed = ' stroke-dasharray="6,5"' if i == 4 else ""
        b.append(f'<rect x="{bx + 10}" y="{y + 8}" width="{bw - 20}" height="{rh - 16}" rx="5" '
                 f'fill="{PALE}" stroke="{MUTED}" stroke-width="1.6"{dashed}/>')
        b.append(text(bx + bw / 2, y + rh / 2 + 5, name, 15, INK))
    # 오른쪽: 오늘 꺼낸 것 다섯
    spots = [(560, 300), (640, 344), (546, 396), (648, 452), (582, 512)]
    b.append(text(600, 268, "오늘 꺼낸 것", 16, INK, "700"))
    colors = [WARM, MUTED, "#6f8f7a", "#9c7d5a", MARK]
    for (sx, sy), color in zip(spots, colors):
        b.append(f'<circle cx="{sx}" cy="{sy}" r="13" fill="{color}" fill-opacity="0.7"/>')
    targets = [0, 1, 2, 3, 4]
    for (sx, sy), t in zip(spots, targets):
        ty = by + t * rh + rh / 2
        b.append(f'<path d="M{sx - 14} {sy} C{sx - 90} {sy}, {bx + bw + 80} {ty}, '
                 f'{bx + bw + 8} {ty}" fill="none" stroke="{MUTED}" stroke-width="1.6" '
                 f'marker-end="url(#m)"/>')
    # 왼쪽 대괄호
    b.append(f'<path d="M{bx - 14} {by} L{bx - 24} {by} L{bx - 24} {by + rh * 5} '
             f'L{bx - 14} {by + rh * 5}" fill="none" stroke="{MUTED}" stroke-width="2"/>')
    b.append(f'<text x="{bx - 34}" y="{by + rh * 2.5 - 8}" text-anchor="end" font-size="13" '
             f'fill="{MUTED}">한 밤에</text>')
    b.append(f'<text x="{bx - 34}" y="{by + rh * 2.5 + 10}" text-anchor="end" font-size="13" '
             f'fill="{MUTED}">서넛에서 예닐곱</text>')
    # 범위 밖 상자
    b.append(f'<rect x="{bx}" y="{by + rh * 5 + 40}" width="{bw}" height="66" rx="8" fill="none" '
             f'stroke="{PALE}" stroke-width="2.5"/>')
    b.append(text(bx + bw / 2, by + rh * 5 + 80, "지난주 · 몇 년 전", 15, "#b6bcc0"))
    b.append(closing(786, "모르겠는 것을 넣을 자리가 없으면 아는 것처럼 넣게 된다"))
    return base("서랍 다섯과 하루치", "1장 · 범주와 분량", "\n".join(b))


# ── p11 왼쪽과 오른쪽 ────────────────────────────────────────────────
ROWS = [("회의에서 말이 막혔다", 1), ("점심을 혼자 먹었다", 3), ("답장이 오지 않았다", 1),
        ("저녁에 전화를 못 받았다", 2)]


def fig_two_columns():
    b = [text(397, 205, "있었던 일과 느낀 것을 갈라 적으면", 19, INK, "700")]
    x0, x1, x2 = 170, 420, 690
    y0 = 262
    heights = [70 + n * 26 for _, n in ROWS]
    b.append(f'<rect x="{x0}" y="{y0}" width="{x2 - x0}" height="{sum(heights) + 44}" rx="10" '
             f'fill="#ffffff" stroke="{LINE}" stroke-width="1.8"/>')
    b.append(f'<line x1="{x1}" y1="{y0}" x2="{x1}" y2="{y0 + sum(heights) + 44}" stroke="{LINE}" '
             f'stroke-width="1.8"/>')
    b.append(f'<line x1="{x0}" y1="{y0 + 44}" x2="{x2}" y2="{y0 + 44}" stroke="{LINE}" '
             f'stroke-width="1.8"/>')
    b.append(text((x0 + x1) / 2, y0 + 29, "있었던 일", 16, INK, "700"))
    b.append(text((x1 + x2) / 2, y0 + 29, "느낀 것", 16, INK, "700"))
    y = y0 + 44
    longest_y = None
    for (label, n), h in zip(ROWS, heights):
        b.append(text((x0 + x1) / 2, y + 30, label, 13, INK))
        for k in range(n):
            b.append(f'<rect x="{x1 + 26}" y="{y + 18 + k * 26}" width="{x2 - x1 - 52}" '
                     f'height="14" rx="4" fill="{MARK}" fill-opacity="0.28"/>')
        if n == max(m for _, m in ROWS):
            longest_y = y + 18 + 26
        y += h
        b.append(f'<line x1="{x0}" y1="{y}" x2="{x2}" y2="{y}" stroke="{PALE}" stroke-width="1.2"/>')
    b.append(f'<path d="M{x2 + 10} {longest_y} L{x2 + 40} {longest_y}" stroke="{WARM}" '
             f'stroke-width="2"/>')
    b.append(text(x2 + 46, longest_y + 5, "그날 가장", 13, WARM, "700", "start"))
    b.append(text(x2 + 46, longest_y + 23, "컸던 것", 13, WARM, "700", "start"))
    ay = y0 + sum(heights) + 96
    for cx, label in (((x0 + x1) / 2, "먼저"), ((x1 + x2) / 2, "나중에")):
        b.append(f'<line x1="{cx}" y1="{ay}" x2="{cx}" y2="{ay + 44}" stroke="{MUTED}" '
                 f'stroke-width="2" marker-end="url(#m)"/>')
        b.append(text(cx + 34, ay + 28, label, 14, MUTED, "700"))
    b.append(closing(ay + 88, "느낀 것을 먼저 적으면 있었던 일이 거기 맞춰진다"))
    return base("왼쪽과 오른쪽", "2장 · 두 칸으로 적기", "\n".join(b))


# ── p18 세 번 묻고 넣는다 ────────────────────────────────────────────
QUESTIONS = ["오늘로 끝났나", "내가 할 수 있는 것이 있나", "남이 정하는 것인가"]
YES_TARGET = [0, 2, 3]
AMOUNTS = [("남이 정하는 것", 380), ("오늘 지나간 것", 310), ("아직 남은 것", 240),
           ("모르겠는 것", 120), ("내가 할 수 있는 것", 50)]


def fig_flow():
    b = [text(397, 202, "이름을 붙인 다음에 묻는 순서", 19, INK, "700")]
    cx, top = 300, 250
    b.append(f'<rect x="{cx - 92}" y="{top}" width="184" height="42" rx="21" fill="#ffffff" '
             f'stroke="{INK}" stroke-width="2"/>')
    b.append(text(cx, top + 27, "이름 붙인 것 하나", 15, INK, "700"))
    box_x, box_w = 520, 176
    slot_y = {}
    for i, name in enumerate(DRAWERS):
        y = 300 + i * 74
        dashed = ' stroke-dasharray="6,5"' if i == 4 else ""
        b.append(f'<rect x="{box_x}" y="{y}" width="{box_w}" height="42" rx="6" fill="{PALE}" '
                 f'stroke="{MUTED}" stroke-width="1.6"{dashed}/>')
        b.append(text(box_x + box_w / 2, y + 27, name, 13, INK))
        slot_y[i] = y + 21
    y = top + 42
    for i, q in enumerate(QUESTIONS):
        my = y + 54
        b.append(f'<line x1="{cx}" y1="{y}" x2="{cx}" y2="{my - 30}" stroke="{MUTED}" '
                 f'stroke-width="1.6"/>')
        b.append(f'<path d="M{cx} {my - 30} L{cx + 108} {my} L{cx} {my + 30} L{cx - 108} {my} Z" '
                 f'fill="#ffffff" stroke="{INK}" stroke-width="1.8"/>')
        b.append(text(cx, my + 5, q, 12, INK))
        b.append(f'<line x1="{cx + 108}" y1="{my}" x2="{box_x - 6}" y2="{slot_y[YES_TARGET[i]]}" '
                 f'stroke="{MUTED}" stroke-width="1.6" marker-end="url(#m)"/>')
        b.append(text((cx + 108 + box_x) / 2, my - 8, "그렇다", 11, MUTED))
        b.append(f'<line x1="{cx - 108}" y1="{my}" x2="{cx - 150}" y2="{my}" stroke="{MUTED}" '
                 f'stroke-width="1.2" stroke-dasharray="4,4"/>')
        y = my + 30
        if i < 2:
            b.append(text(cx + 16, y + 22, "아니다", 11, MUTED, "400", "start"))
    b.append(f'<line x1="{cx}" y1="{y}" x2="{cx}" y2="{slot_y[1]}" stroke="{MUTED}" '
             f'stroke-width="1.6"/>')
    b.append(f'<line x1="{cx}" y1="{slot_y[1]}" x2="{box_x - 6}" y2="{slot_y[1]}" stroke="{MUTED}" '
             f'stroke-width="1.6" marker-end="url(#m)"/>')
    b.append(f'<line x1="{cx - 150}" y1="{top + 96}" x2="{cx - 150}" y2="{slot_y[4]}" '
             f'stroke="{MUTED}" stroke-width="1.2" stroke-dasharray="4,4"/>')
    b.append(f'<line x1="{cx - 150}" y1="{slot_y[4]}" x2="{box_x - 6}" y2="{slot_y[4]}" '
             f'stroke="{MUTED}" stroke-width="1.2" stroke-dasharray="4,4" marker-end="url(#m)"/>')
    b.append(text(cx - 156, slot_y[4] - 14, "어느 물음에도 답이 안 나오면", 11, MUTED, "400", "end"))
    # 오른쪽 막대
    for i, (name, amount) in enumerate(AMOUNTS):
        idx = DRAWERS.index(name)
        y = slot_y[idx]
        b.append(f'<rect x="{box_x + box_w + 8}" y="{y - 9}" width="{amount * 0.19:.0f}" '
                 f'height="18" rx="3" fill="{MARK}" fill-opacity="0.55"/>')
    b.append(text(box_x + box_w + 8, 288, "한 해 치 양", 12, MUTED, "400", "start"))
    b.append(closing(722, "오래 든다고 더 잘 넣게 되지는 않았다"))
    return base("세 번 묻고 넣는다", "3장 · 분류의 순서", "\n".join(b))


# ── p37 이백일흔다섯 밤 ──────────────────────────────────────────────
MONTHS = ["첫째 달", "둘째 달", "셋째 달", "넷째 달", "다섯째 달", "여섯째 달",
          "일곱째 달", "여덟째 달", "아홉째 달", "열째 달", "열한째 달", "열두째 달"]
DAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]


# 5.2·5.3 본문이 센 값. 도표는 이 값에서 만들어야 본문과 어긋나지 않는다.
MISSED_NIGHTS = 90


def _missed_set():
    """못 앉은 밤을 정확히 MISSED_NIGHTS개 고른다.

    나머지 연산으로 고르면 개수가 목표와 어긋난다. 본문은 못 앉은 밤이 특정 달에 몰렸다고
    말하지 않으므로 한 해에 고르게 흩어 놓는다. 무작위를 쓰지 않아 다시 실행해도 같은 그림이다.
    """
    days = [(m, d) for m, n in enumerate(DAYS, start=1) for d in range(1, n + 1)]
    step = len(days) / MISSED_NIGHTS
    return {days[int(i * step)] for i in range(MISSED_NIGHTS)}


MISSED = _missed_set()


def missed(month, day):
    return (month, day) in MISSED


def fig_year_nights():
    b = [text(397, 200, "한 해 동안 앉은 밤과 서랍에 들어간 것", 19, INK, "700")]
    x0, y0, rh, cw = 150, 250, 34, 17.6
    for m, n in enumerate(DAYS, start=1):
        cy = y0 + (m - 1) * rh
        b.append(text(x0 - 12, cy + 18, MONTHS[m - 1], 13, MUTED, "400", "end"))
        for d in range(1, n + 1):
            bx = x0 + (d - 1) * cw
            if missed(m, d):
                b.append(f'<rect x="{bx:.1f}" y="{cy + 4}" width="{cw - 2.6:.1f}" height="20" '
                         f'rx="2" fill="#ffffff" stroke="{LINE}" stroke-width="1"/>')
                b.append(f'<line x1="{bx:.1f}" y1="{cy + 24}" x2="{bx + cw - 2.6:.1f}" '
                         f'y2="{cy + 4}" stroke="{LINE}" stroke-width="1.2"/>')
            else:
                b.append(f'<rect x="{bx:.1f}" y="{cy + 4}" width="{cw - 2.6:.1f}" height="20" '
                         f'rx="2" fill="{MARK}" fill-opacity="0.42"/>')
    ty = y0 + 12 * rh + 30
    for i, (name, amount) in enumerate(AMOUNTS):
        y = ty + i * 40
        b.append(text(x0 + 60, y + 18, name, 14, INK, "400", "end"))
        b.append(f'<rect x="{x0 + 70}" y="{y + 2}" width="{amount * 0.95:.0f}" height="22" rx="3" '
                 f'fill="{MARK}" fill-opacity="0.55"/>')
        b.append(text(x0 + 80 + amount * 0.95, y + 19, str(amount), 13, MUTED, "700", "start"))
    ly = ty + 4 * 40
    b.append(f'<rect x="{x0 + 190}" y="{ly - 6}" width="228" height="42" rx="8" fill="none" '
             f'stroke="{WARM}" stroke-width="1.6"/>')
    for k in range(2):
        b.append(f'<line x1="{x0 + 208 + k * 18}" y1="{ly + 30}" x2="{x0 + 208 + k * 18}" '
                 f'y2="{ly + 2}" stroke="{WARM}" stroke-width="4"/>')
    b.append(text(x0 + 254, ly + 22, "이사한 두 달만 세 배", 12, WARM, "700", "start"))
    b.append(closing(902, "분포는 그해에 일어난 일의 모양이다"))
    return base("이백일흔다섯 밤", "5장 · 한 해 치 기록", "\n".join(b))


FIGURES = {8: fig_five_drawers, 11: fig_two_columns, 18: fig_flow, 37: fig_year_nights}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild017")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(fn(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
