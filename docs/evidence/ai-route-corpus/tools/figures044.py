#!/usr/bin/env python3
"""book-044 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 축에 숫자를 넣지 않는 것이 이 책의 방침이다 — 공개 가이드
주제에 수치를 넣지 않는 계약과 같은 이유이고, 도표가 특정 사례의 값이 아니라 관계를 보여야 한다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
BLUE, ORANGE, GRAY = "#315f83", "#a4703c", "#8a9296"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="blue" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{BLUE}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{GRAY}"/></marker>
  <pattern id="hatch" width="8" height="8" patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
    <line x1="0" y1="0" x2="0" y2="8" stroke="#9aa3a6" stroke-width="2"/>
  </pattern>
</defs>
{t(W/2, 122, title, 32, '#203238', '700')}
{t(W/2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note(y, text, size=17):
    return ('<rect x="105" y="%d" width="584" height="72" rx="16" fill="#fff8e8" stroke="#c5a866"/>' % y
            + t(W / 2, y + 44, text, size, "#51462c", "700"))


def fig_one_price_many_values():
    """5페이지 — 값 하나, 여러 값어치."""
    b = []
    axis_x, top, bottom = 175, 280, 800
    b.append(f'<line x1="{axis_x}" y1="{top}" x2="{axis_x}" y2="{bottom}" stroke="#5b6b70" stroke-width="3"/>')
    b.append(t(axis_x - 14, top - 14, "금액", 16, "#5b6b70", "400", "end"))
    price_y = 620
    b.append(f'<line x1="{axis_x}" y1="{price_y}" x2="660" y2="{price_y}" stroke="#2c3d43" stroke-width="4"/>')
    b.append(t(676, price_y + 6, "값", 20, "#2c3d43", "700", "start"))
    # 손님 네 명. 값 위 둘, 값과 같은 높이 하나, 값 아래 하나.
    for cx, cy, name, above, caption in ((265, 360, "가", True, "내려던 금액"), (385, 460, "나", True, ""),
                                         (505, 620, "다", None, "남는 몫 없음"),
                                         (615, 730, "라", False, "사지 않음")):
        color = BLUE if above else (GRAY if above is False else "#4a5a5f")
        if above:
            b.append(f'<line x1="{cx}" y1="{cy + 18}" x2="{cx}" y2="{price_y - 4}" '
                     f'stroke="{BLUE}" stroke-width="14" opacity="0.24"/>')
        b.append(f'<circle cx="{cx}" cy="{cy}" r="16" fill="{color}"/>')
        b.append(t(cx, cy + 5, name, 13, "#ffffff", "700"))
        if caption:
            b.append(t(cx, cy - 30, caption, 14, color))
        b.append(t(cx, 838, f"손님 {name}", 16, color, "700"))
    b.append(t(325, 545, "남는 몫", 18, BLUE, "700", "start"))
    b.append(note(900, "같은 값, 다른 값어치"))
    return base("하나의 값, 여러 값어치", "값은 하나지만 각자가 매기는 금액은 다르다", "\n".join(b))


def fig_one_person_surplus():
    """9페이지 — 한 사람의 잉여."""
    b = []
    for x, price_y, caption in ((180, 620, "지금 값"), (474, 470, "값이 오른 경우")):
        top = 320
        b.append(f'<rect x="{x}" y="{top}" width="140" height="{760 - top}" rx="10" '
                 f'fill="#f4f7f9" stroke="#8fa3ac" stroke-width="2"/>')
        b.append(f'<line x1="{x - 18}" y1="{top}" x2="{x + 158}" y2="{top}" stroke="#2c3d43" stroke-width="4"/>')
        b.append(t(x + 70, top - 18, "내려던 금액", 16, "#2c3d43", "700"))
        b.append(f'<line x1="{x - 18}" y1="{price_y}" x2="{x + 158}" y2="{price_y}" stroke="#2c3d43" stroke-width="4"/>')
        b.append(t(x + 70, price_y + 30, "치른 값", 16, "#2c3d43"))
        b.append(f'<rect x="{x}" y="{top}" width="140" height="{price_y - top}" fill="url(#hatch)" opacity="0.75"/>')
        b.append(t(x + 70, (top + price_y) / 2 + 6, "남은 몫", 18, "#27353a", "700"))
        b.append(t(x + 70, 800, caption, 17, "#5b6b70"))
    b.append(note(880, "값이 올라도 내려던 금액은 그대로다"))
    return base("한 사람의 잉여", "두 선 사이의 간격만 줄어든다", "\n".join(b))


def _stair_path(x0, y0, dy, count, step_w):
    """계단 선. dy만큼 한 칸씩 이동하며 (수평 → 수직)을 반복한다."""
    parts, y = [f"M{x0},{y0}"], y0
    for index in range(count):
        x = x0 + (index + 1) * step_w
        parts.append(f"L{x},{y}")
        y += dy
        parts.append(f"L{x},{y}")
    return " ".join(parts)


def _stair_bars(x0, y0, dy, count, step_w, price_y, color, above):
    """계단과 값 선 사이를 칸마다 채운다. above면 계단이 값 위에 있는 칸만."""
    out = []
    for index in range(count):
        y = y0 + dy * index
        if (y < price_y) if above else (y > price_y):
            top, height = (y, price_y - y) if above else (price_y, y - price_y)
            out.append(f'<rect x="{x0 + index * step_w}" y="{top}" width="{step_w}" '
                       f'height="{height}" fill="{color}" opacity="0.22"/>')
    return out


def fig_two_ladders():
    """18페이지 — 두 계단이 만나는 자리."""
    b = []
    left, right, top, bottom = 170, 660, 300, 790
    steps, step_w, dy = 10, 40, 34
    demand_y0, supply_y0, price_y = 350, 740, 545
    b.append(f'<line x1="{left}" y1="{bottom}" x2="{right}" y2="{bottom}" stroke="#5b6b70" stroke-width="3"/>')
    b.append(f'<line x1="{left}" y1="{top}" x2="{left}" y2="{bottom}" stroke="#5b6b70" stroke-width="3"/>')
    b.append(t(right, bottom + 30, "수량", 16, "#5b6b70", "400", "end"))
    b.append(t(left - 12, top - 12, "금액", 16, "#5b6b70", "400", "end"))
    b.extend(_stair_bars(left, demand_y0, dy, steps, step_w, price_y, BLUE, above=True))
    b.extend(_stair_bars(left, supply_y0, -dy, steps, step_w, price_y, ORANGE, above=False))
    b.append(f'<path d="{_stair_path(left, demand_y0, dy, steps, step_w)}" fill="none" '
             f'stroke="{BLUE}" stroke-width="4"/>')
    b.append(f'<path d="{_stair_path(left, supply_y0, -dy, steps, step_w)}" fill="none" '
             f'stroke="{ORANGE}" stroke-width="4"/>')
    b.append(f'<line x1="{left}" y1="{price_y}" x2="{right}" y2="{price_y}" stroke="#2c3d43" '
             f'stroke-width="3" stroke-dasharray="9 7"/>')
    b.append(t(right + 8, price_y + 6, "값", 18, "#2c3d43", "700", "start"))
    cross_x = left + 6 * step_w
    b.append(f'<circle cx="{cross_x}" cy="{price_y}" r="8" fill="#2c3d43"/>')
    b.append(f'<line x1="{cross_x}" y1="{price_y + 12}" x2="{cross_x}" y2="{bottom}" '
             f'stroke="#2c3d43" stroke-width="2" stroke-dasharray="5 5"/>')
    b.append(t(cross_x, bottom + 30, "거래가 멈추는 수량", 15, "#2c3d43", "700"))
    b.append(t(290, 475, "사는 쪽의 몫", 19, BLUE, "700"))
    b.append(t(258, 632, "파는 쪽의 몫", 19, "#8a5a2b", "700"))
    b.append(t(590, 505, "거래가 없는 구간", 14, GRAY))
    b.append(note(880, "두 계단이 만나는 자리에서 값이 정해진다"))
    return base("두 계단이 만나는 자리", "사는 쪽과 파는 쪽의 몫이 나뉘는 모습", "\n".join(b))


def fig_transfer_and_loss():
    """24페이지 — 옮겨 가는 몫과 사라지는 몫."""
    b = []
    width, top, bottom = 244, 330, 700
    demand_y0, supply_y0, slope = 360, 660, 280 / 244  # 두 직선의 기울기는 크기가 같고 부호만 다르다
    cross_dx = (supply_y0 - demand_y0) / (2 * slope)
    cross_y = demand_y0 + cross_dx * slope
    for left, price_y, label, dead in ((120, cross_y, "값이 교차점에 있을 때", False),
                                       (430, cross_y - 80, "값이 교차점보다 높을 때", True)):
        limit_dx = (price_y - demand_y0) / slope  # 값에서 살 뜻이 끊기는 수량
        limit = left + limit_dx
        b.append(f'<rect x="{left}" y="{top}" width="{width}" height="{bottom - top}" fill="#fbfcfc" stroke="#dde3e5"/>')
        b.append(f'<path d="M{left},{demand_y0} L{left + width},{demand_y0 + width * slope}" '
                 f'fill="none" stroke="{BLUE}" stroke-width="3"/>')
        b.append(f'<path d="M{left},{supply_y0} L{left + width},{supply_y0 - width * slope}" '
                 f'fill="none" stroke="{ORANGE}" stroke-width="3"/>')
        b.append(f'<line x1="{left}" y1="{price_y}" x2="{left + width}" y2="{price_y}" '
                 f'stroke="#2c3d43" stroke-width="3" stroke-dasharray="8 6"/>')
        b.append(f'<path d="M{left},{price_y} L{limit:.1f},{price_y} L{left},{demand_y0} z" '
                 f'fill="{BLUE}" opacity="0.22"/>')
        b.append(f'<path d="M{left},{price_y} L{limit:.1f},{price_y} '
                 f'L{limit:.1f},{supply_y0 - limit_dx * slope:.1f} L{left},{supply_y0} z" '
                 f'fill="{ORANGE}" opacity="0.22"/>')
        if dead:
            b.append(f'<path d="M{limit:.1f},{price_y} L{left + cross_dx:.1f},{cross_y} '
                     f'L{limit:.1f},{supply_y0 - limit_dx * slope:.1f} z" fill="url(#hatch)" opacity="0.85"/>')
            b.append(f'<line x1="{limit + 50:.1f}" y1="{bottom + 22}" x2="{limit + 24:.1f}" y2="{cross_y + 36}" '
                     f'stroke="{GRAY}" stroke-width="2" marker-end="url(#gray)"/>')
            b.append(t(left + width / 2, bottom + 44, "아무에게도 가지 않은 몫", 14, "#6b7478"))
        else:
            b.append(t(left + 70, price_y - 14, "사는 쪽", 15, BLUE, "700"))
            b.append(t(left + 70, price_y + 52, "파는 쪽", 15, "#8a5a2b", "700"))
        b.append(t(left + width / 2, top - 16, label, 16, "#4a5a5f", "700"))
        bar = 200 if not dead else 150
        b.append(f'<rect x="{left + 22}" y="800" width="{bar}" height="26" rx="8" fill="#7d8f95"/>')
        b.append(t(left + width / 2, 862, "전체 합", 15, "#5b6b70"))
    b.append(note(910, "옮겨 간 몫과 사라진 몫은 다르다"))
    return base("옮겨 가는 몫과 사라지는 몫", "값이 어긋나면 합 자체가 줄어든다", "\n".join(b))


def fig_calculation_table():
    """37페이지 — 하루치 계산표."""
    b = []
    cols = ["손님", "상한선", "치른 값", "남은 몫", "비고"]
    x0, y0, w, rh = 120, 270, 112, 42
    for index, name in enumerate(cols):
        b.append(f'<rect x="{x0 + index * w}" y="{y0}" width="{w}" height="{rh}" fill="#eef2f4" stroke="#9fb0b6"/>')
        b.append(t(x0 + index * w + w / 2, y0 + 28, name, 16, "#33474d", "700"))
    rows = [("가", "채움", "채움", "채움", ""), ("나", "채움", "채움", "채움", ""),
            ("다", "", "채움", "", "답하지 않음"), ("라", "채움", "채움", "채움", ""),
            ("마", "", "채움", "", "답하지 않음"), ("바", "채움", "채움", "채움", ""),
            ("사", "채움", "채움", "채움", ""), ("아", "채움", "채움", "채움", ""),
            ("자", "채움", "채움", "채움", "")]
    for r, row in enumerate(rows):
        y = y0 + rh + r * rh
        for c, value in enumerate(row):
            x = x0 + c * w
            b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{rh}" fill="#ffffff" stroke="#c9d3d7"/>')
            if value == "채움":
                b.append(f'<rect x="{x + 24}" y="{y + 16}" width="{w - 48}" height="10" rx="5" fill="#c3ced3"/>')
            elif value:
                b.append(t(x + w / 2, y + 27, value, 13, "#8a5a2b"))
            elif c in (1, 3):
                b.append(f'<line x1="{x + 26}" y1="{y + 21}" x2="{x + w - 26}" y2="{y + 21}" '
                         f'stroke="{GRAY}" stroke-width="3"/>')
    table_bottom = y0 + rh * (len(rows) + 1)
    mid = x0 + w * len(cols) / 2
    y_sub = table_bottom + 34
    sub_w = 300
    b.append(f'<rect x="{mid - sub_w / 2}" y="{y_sub}" width="{sub_w}" height="{rh}" rx="10" '
             f'fill="#f7f3ec" stroke="#c5a866"/>')
    b.append(t(mid, y_sub + 27, "같은 방식의 표 하나 더 — 파는 쪽", 15, "#6b5a34", "700"))
    b.append(f'<line x1="{mid}" y1="{y_sub + rh + 4}" x2="{mid}" y2="{y_sub + rh + 44}" '
             f'stroke="{GRAY}" stroke-width="3" marker-end="url(#gray)"/>')
    y_total = y_sub + rh + 54
    b.append(f'<rect x="{mid - 150}" y="{y_total}" width="300" height="62" rx="16" '
             f'fill="#eef4f0" stroke="#6d9077" stroke-width="2"/>')
    b.append(t(mid, y_total + 39, "두 합을 더한 값", 19, "#3d6448", "700"))
    b.append(note(y_total + 92, "빠진 줄이 있으므로 실제보다 아랫값이다", 15))
    return base("하루치 계산표", "답하지 않은 손님은 계산에서 뺀다", "\n".join(b))


def fig_asymmetric_response():
    """42페이지 — 대칭이 아닌 반응."""
    b = []
    cx, cy = 397, 560
    b.append(f'<line x1="150" y1="{cy}" x2="644" y2="{cy}" stroke="#5b6b70" stroke-width="3"/>')
    b.append(f'<line x1="{cx}" y1="300" x2="{cx}" y2="820" stroke="#5b6b70" stroke-width="3"/>')
    b.append(t(cx - 14, cy - 16, "기준점", 16, "#2c3d43", "700", "end"))
    b.append(f'<path d="M{cx},{cy} C480,{cy - 60} 560,{cy - 85} 630,{cy - 95}" fill="none" '
             f'stroke="{BLUE}" stroke-width="4"/>')
    b.append(f'<path d="M{cx},{cy} C314,{cy + 110} 234,{cy + 175} 164,{cy + 200}" fill="none" '
             f'stroke="{ORANGE}" stroke-width="4"/>')
    gain_x, loss_x = cx + 170, cx - 170
    gain_y, loss_y = cy - 78, cy + 168
    for x, y, color, bracket_y, side in ((gain_x, gain_y, BLUE, cy + 34, 1), (loss_x, loss_y, ORANGE, cy - 34, -1)):
        b.append(f'<line x1="{x}" y1="{cy}" x2="{x}" y2="{y}" stroke="{color}" stroke-width="2" stroke-dasharray="6 5"/>')
        b.append(f'<line x1="{x - 26}" y1="{y}" x2="{x + 26}" y2="{y}" stroke="{color}" stroke-width="6"/>')
        b.append(t(x + side * 46, (cy + y) / 2 + 5, "반응 크기", 14, color, "400", "start" if side > 0 else "end"))
        # 두 방향의 밑변이 같다는 것을 축 위 괄호로 보인다.
        b.append(f'<path d="M{cx},{bracket_y - side * 8} L{cx},{bracket_y} L{x},{bracket_y} '
                 f'L{x},{bracket_y - side * 8}" fill="none" stroke="#8a9296" stroke-width="2"/>')
        b.append(t((cx + x) / 2, bracket_y + (22 if side > 0 else -12), "같은 크기", 14, "#5b6b70"))
    b.append(t(620, cy - 130, "얻음", 18, BLUE, "700"))
    b.append(t(180, cy + 240, "잃음", 18, "#8a5a2b", "700"))
    b.append(note(900, "같은 크기라도 잃는 쪽이 더 크게 느껴진다"))
    return base("대칭이 아닌 반응", "기준점에서 멀어지는 두 방향의 기울기가 다르다", "\n".join(b))


FIGURES = {
    5: fig_one_price_many_values,
    9: fig_one_person_surplus,
    18: fig_two_ladders,
    24: fig_transfer_and_loss,
    37: fig_calculation_table,
    42: fig_asymmetric_response,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tmp/pdfs/book-044")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
