#!/usr/bin/env python3
"""book-034 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 숫자와 비율을 넣지 않는다 — 장부에서 얻은
수치가 아니라 자료의 구조를 보이는 그림이기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
BLUE, ORANGE, GRAY = "#3d5f7a", "#9c6b3c", "#8a9296"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{GRAY}"/></marker>
  <marker id="blue" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{BLUE}"/></marker>
</defs>
{t(W/2, 122, title, 32, '#203238', '700')}
{t(W/2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note(y, text, size=17):
    return ('<rect x="105" y="%d" width="584" height="72" rx="16" fill="#fff8e8" stroke="#c5a866"/>' % y
            + t(W / 2, y + 44, text, size, "#51462c", "700"))


def fig_filter_stages():
    """5페이지 — 기록이 걸러지는 단계."""
    b = []
    stages = [("일어난 일", 560), ("적힌 것", 430), ("보관된 것", 310), ("오늘 남은 것", 200)]
    reasons = ["적을 이유가 없었다", "보관할 곳이 없었다", "불과 물에 사라졌다"]
    y = 270
    height, gap = 86, 54
    for index, (label, width) in enumerate(stages):
        x = W / 2 - width / 2
        shade = ["#eef3f7", "#e6eef4", "#dde8f0", "#d3e1eb"][index]
        b.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="10" fill="{shade}" stroke="#7d97a8"/>')
        b.append(t(W / 2, y + height / 2 + 7, label, 19, "#25404f", "700"))
        if index < len(reasons):
            ny = y + height + gap
            next_width = stages[index + 1][1]
            nx = W / 2 - next_width / 2
            b.append(f'<path d="M{x},{y + height} L{nx},{ny}" fill="none" stroke="#b7c2c6" stroke-width="2"/>')
            b.append(f'<path d="M{x + width},{y + height} L{nx + next_width},{ny}" fill="none" '
                     f'stroke="#b7c2c6" stroke-width="2"/>')
            # 걸러진 이유는 띠 사이 빈 자리에 둔다. 양옆에 두면 넓은 띠에서 지면 밖으로 나간다.
            label_y = y + height + gap / 2 + 5
            width_px = 14 * len(reasons[index]) + 20
            b.append(f'<rect x="{W / 2 - width_px / 2}" y="{label_y - 17}" width="{width_px}" '
                     f'height="24" fill="#ffffff"/>')
            b.append(t(W / 2, label_y, reasons[index], 14, "#8a5a2b"))
        y += height + gap
    top_x = W / 2 - stages[0][1] / 2
    b.append(f'<line x1="{top_x + stages[0][1] + 12}" y1="270" x2="{top_x + stages[0][1] + 12}" y2="356" '
             f'stroke="{GRAY}" stroke-width="2"/>')
    b.append(t(top_x + stages[0][1] + 20, 318, "알고 싶은 폭", 15, "#5b6b70", "700", "start"))
    last_x = W / 2 - stages[-1][1] / 2
    b.append(f'<line x1="{last_x + stages[-1][1] + 12}" y1="{y - gap - height}" '
             f'x2="{last_x + stages[-1][1] + 12}" y2="{y - gap}" stroke="{BLUE}" stroke-width="2"/>')
    b.append(t(last_x + stages[-1][1] + 20, y - gap - height / 2 + 6, "이 폭으로 그린 그림", 15, BLUE, "700", "start"))
    b.append(note(880, "남은 자료의 폭과 알고 싶은 폭은 같지 않다"))
    return base("기록이 걸러지는 단계", "만들어진 기록에서 오늘 남은 기록까지", "\n".join(b))


def fig_ledger_page():
    """9페이지 — 장부 한 장의 짜임."""
    b = []
    x0, y0, w, h = 300, 250, 300, 520
    b.append(f'<rect x="{x0}" y="{y0}" width="{w}" height="{h}" fill="#fcfaf5" stroke="#9a8f7a" stroke-width="2"/>')
    b.append(f'<rect x="{x0}" y="{y0}" width="{w}" height="44" fill="#f0ebdf" stroke="#9a8f7a"/>')
    b.append(t(x0 + w / 2, y0 + 29, "해와 고을 이름", 15, "#6b5f48"))
    # 오른쪽에서 왼쪽으로 세로 칸
    cols = ["이름", "품목", "수량", "이행 여부"]
    meaning = ["집의 대표", "무엇을 내는가", "얼마를", "냈는가 못 냈는가"]
    cw = w / len(cols)
    # 뜻풀이는 칸 이름 바로 아래에 둔다. 지시선으로 빼면 선이 서로 엇갈린다.
    for index, name in enumerate(cols):
        cx = x0 + w - (index + 1) * cw
        b.append(f'<line x1="{cx}" y1="{y0 + 44}" x2="{cx}" y2="{y0 + h}" stroke="#b8ad96"/>')
        b.append(t(cx + cw / 2, y0 + 76, name, 15, "#5b5240", "700"))
        for line_index, part in enumerate(meaning[index].split(" ")):
            b.append(t(cx + cw / 2, y0 + 100 + line_index * 18, part, 12, "#9a9081"))
    # 굵게 표시한 한 줄
    row_y = y0 + 210
    b.append(f'<rect x="{x0}" y="{row_y}" width="{w}" height="46" fill="#eef3f7" stroke="{BLUE}" stroke-width="2"/>')
    for index in range(len(cols)):
        cx = x0 + w - (index + 1) * cw
        b.append(f'<rect x="{cx + 16}" y="{row_y + 18}" width="{cw - 32}" height="10" rx="5" fill="#b9c8d3"/>')
    b.append(t(x0 - 20, row_y + 30, "채워진 줄", 14, BLUE, "700", "end"))
    # 이행 여부 칸만 빈 줄
    empty_y = y0 + h - 96
    b.append(f'<rect x="{x0}" y="{empty_y}" width="{w}" height="46" fill="#ffffff" stroke="#c9c0ac"/>')
    for index in range(len(cols) - 1):
        cx = x0 + w - (index + 1) * cw
        b.append(f'<rect x="{cx + 16}" y="{empty_y + 18}" width="{cw - 32}" height="10" rx="5" fill="#d6cfc0"/>')
    b.append(t(x0 - 20, empty_y + 30, "이행 여부가", 14, "#8a5a2b", "700", "end"))
    b.append(t(x0 - 20, empty_y + 48, "빈 줄", 14, "#8a5a2b", "700", "end"))
    b.append(t(W / 2, y0 + h + 40, "빈칸도 한 줄이다", 17, "#8a5a2b", "700"))
    b.append(note(880, "한 줄은 사람이 아니라 집 하나를 뜻한다"))
    return base("장부 한 장의 짜임", "한 줄은 네 부분으로 되어 있다", "\n".join(b))


def fig_burden_distribution():
    """17페이지 — 부담을 큰 쪽부터 늘어세우면."""
    b = []
    left, bottom = 130, 620
    count, bar_w = 22, 24
    heights = [280, 236, 198, 150, 96, 74, 66, 60, 56, 52, 50, 48, 46, 44, 42, 41, 40, 38, 37, 36, 35, 34]
    b.append(f'<rect x="{left - 12}" y="{bottom - 300}" width="{4 * bar_w + 20}" height="300" rx="10" '
             f'fill="#eef3f7"/>')
    for index in range(count):
        x = left + index * bar_w
        h = heights[index]
        color = BLUE if index < 4 else "#9fb0b6"
        b.append(f'<rect x="{x}" y="{bottom - h}" width="{bar_w - 6}" height="{h}" fill="{color}"/>')
    b.append(f'<line x1="{left - 16}" y1="{bottom}" x2="{left + count * bar_w + 10}" y2="{bottom}" '
             f'stroke="#5b6b70" stroke-width="2"/>')
    b.append(t(left + 2 * bar_w, bottom - 320, "적은 수의 집", 15, BLUE, "700"))
    b.append(t(left + 14 * bar_w, bottom + 28, "많은 수의 집", 15, "#5b6b70"))
    b.append(t(left + 2 * bar_w, bottom - 296, "전체의 큰 몫", 13, "#4a6a82"))
    # 항목별 작은 그림 둘
    for index, (bx, label, steep) in enumerate(((150, "땅에 매긴 것", True), (430, "집에 매긴 것", False))):
        bw, by, bh = 220, 700, 150
        b.append(f'<rect x="{bx}" y="{by}" width="{bw}" height="{bh}" fill="#fbfdfd" stroke="#dde3e5"/>')
        for k in range(11):
            h = (120 * (0.92 ** (k * 3.2))) if steep else (52 - k * 1.4)
            x = bx + 14 + k * 18
            b.append(f'<rect x="{x}" y="{by + bh - 14 - h}" width="12" height="{h}" '
                     f'fill="{BLUE if steep else "#9fb0b6"}"/>')
        b.append(f'<line x1="{bx + 10}" y1="{by + bh - 14}" x2="{bx + bw - 10}" y2="{by + bh - 14}" '
                 f'stroke="#9fb0b6" stroke-width="1.5"/>')
        b.append(t(bx + bw / 2, by + bh + 26, label, 15, "#5b6b70", "700"))
    b.append(note(900, "총액이 고르게 보여도 항목마다 기울기가 다르다"))
    return base("부담을 큰 쪽부터 늘어세우면", "위쪽 몇 줄이 전체의 상당 부분을 차지한다", "\n".join(b))


def fig_year_schedule():
    """25페이지 — 한 해의 일정."""
    b = []
    left, right = 120, 674
    band_y, band_h = 430, 92
    seasons = ["봄", "여름", "가을", "겨울"]
    shades = ["#eef5ec", "#eef3f7", "#f7f1e6", "#eef0f3"]
    seg = (right - left) / 4
    for index, name in enumerate(seasons):
        x = left + index * seg
        b.append(f'<rect x="{x}" y="{band_y}" width="{seg}" height="{band_h}" fill="{shades[index]}" stroke="#dde3e5"/>')
        b.append(t(x + seg / 2, band_y + band_h / 2 + 7, name, 20, "#4a5a5f", "700"))
    works = ["옮기기", "기르기", "거두기", "짜기"]
    for index, name in enumerate(works):
        x = left + index * seg + seg / 2
        b.append(f'<rect x="{x - 52}" y="{band_y - 76}" width="104" height="46" rx="12" '
                 f'fill="#ffffff" stroke="#7d97a8" stroke-width="2"/>')
        b.append(t(x, band_y - 46, name, 16, "#25404f", "700"))
    b.append(t(left, band_y - 100, "사람들의 일", 15, "#5b6b70", "700", "start"))
    b.append(t(left, band_y + band_h + 52, "부담의 일정", 15, "#5b6b70", "700", "start"))
    duty = [("남은 몫 옮기기", 0), ("보관", 3)]
    for name, index in duty:
        x = left + index * seg + seg / 2
        b.append(f'<rect x="{x - 62}" y="{band_y + band_h + 70}" width="124" height="46" rx="12" '
                 f'fill="#ffffff" stroke="#9c8a6a" stroke-width="2"/>')
        b.append(t(x, band_y + band_h + 100, name, 15, "#6b5a34", "700"))
    deadline = left + 3 * seg
    b.append(f'<line x1="{deadline}" y1="{band_y - 130}" x2="{deadline}" y2="{band_y + band_h + 140}" '
             f'stroke="#a44f47" stroke-width="3"/>')
    b.append(t(deadline + 10, band_y + band_h + 160, "얼기 전에 옮겨야 하는 때", 15, "#8a3f38", "700", "middle"))
    b.append(f'<line x1="{deadline - 60}" y1="{band_y - 20}" x2="{deadline - 8}" y2="{band_y - 20}" '
             f'stroke="{GRAY}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(f'<line x1="{deadline - 60}" y1="{band_y + band_h + 20}" x2="{deadline - 8}" y2="{band_y + band_h + 20}" '
             f'stroke="{GRAY}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(note(780, "사람들의 일과 부담의 일정이 같은 때에 몰린다"))
    return base("한 해의 일정", "계절이 정해 둔 마감이 양쪽을 함께 누른다", "\n".join(b))


def fig_two_year_table():
    """36페이지 — 두 해를 나란히 놓는 표."""
    b = []
    x0, y0, rh = 130, 260, 52
    widths = [200, 130, 130, 110]
    heads = ["항목", "앞 해", "뒤 해", "같은 기준인가"]
    rows = ["줄 수", "이어진 이름", "땅에 매긴 것", "집에 매긴 것", "일로 갚는 것"]
    x = x0
    for index, name in enumerate(heads):
        w = widths[index]
        b.append(f'<rect x="{x}" y="{y0}" width="{w}" height="{rh}" fill="#eef2f4" stroke="#9fb0b6"/>')
        b.append(t(x + w / 2, y0 + 33, name, 16 if index < 3 else 14, "#33474d", "700"))
        x += w
    for r, label in enumerate(rows):
        y = y0 + rh + r * rh
        x = x0
        for index in range(len(heads)):
            w = widths[index]
            b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{rh}" fill="#ffffff" stroke="#c9d3d7"/>')
            if index == 0:
                b.append(t(x + w / 2, y + 33, label, 15, "#4a5a5f"))
            elif index < 3:
                b.append(f'<line x1="{x + 24}" y1="{y + 34}" x2="{x + w - 24}" y2="{y + 34}" stroke="#c9d3d7"/>')
            else:
                b.append(f'<rect x="{x + w / 2 - 11}" y="{y + 15}" width="22" height="22" '
                         f'fill="#ffffff" stroke="#8a9296" stroke-width="1.5"/>')
            x += w
    check_y = y0 + rh * (len(rows) + 1) + 40
    b.append(t(x0, check_y - 12, "견주기 전에 확인한다", 16, "#33474d", "700", "start"))
    for index, name in enumerate(("단위가 같은가", "세는 범위가 같은가", "서식이 같은가")):
        y = check_y + 16 + index * 42
        b.append(f'<rect x="{x0}" y="{y}" width="22" height="22" fill="#ffffff" stroke="#8a9296" stroke-width="1.5"/>')
        b.append(t(x0 + 34, y + 18, name, 16, "#4a5a5f", "400", "start"))
    b.append(t(W / 2, check_y + 172, "셋이 모두 참일 때만 견준다", 17, "#51462c", "700"))
    b.append(note(890, "견줄 수 없으면 견주지 않는다고 적는다"))
    return base("두 해를 나란히 놓는 표", "항목별 대조와 기준 확인을 한 장에 둔다", "\n".join(b))


def fig_distortion_flow():
    """42페이지 — 숫자가 밀리는 자리."""
    b = []
    labels = ["실제로 있었던 일", "고을에서 적은 것", "중앙에 보고한 것", "오늘 남은 장부"]
    reasons = ["적을 이유가 있는 것만", "거둔 양은 늘리고 남은 양은 줄여", "보관된 것만"]
    x, y, bw, bh, gap = 150, 270, 320, 78, 74
    for index, name in enumerate(labels):
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="12" fill="#eef3f7" stroke="#7d97a8" stroke-width="2"/>')
        b.append(t(x + bw / 2, y + bh / 2 + 7, name, 18, "#25404f", "700"))
        if index < len(reasons):
            b.append(f'<line x1="{x + bw / 2}" y1="{y + bh + 4}" x2="{x + bw / 2}" y2="{y + bh + gap - 8}" '
                     f'stroke="{ORANGE}" stroke-width="3" marker-end="url(#gray)"/>')
            b.append(t(x + bw + 16, y + bh + gap / 2 + 5, reasons[index], 14, "#8a5a2b", "400", "start"))
        y += bh + gap
    last_y = y - bh - gap
    b.append(f'<path d="M{x - 16},{last_y + bh / 2} C{x - 116},{last_y + bh / 2} {x - 116},290 {x - 16},290" '
             f'fill="none" stroke="{GRAY}" stroke-width="2" stroke-dasharray="7 6" marker-end="url(#gray)"/>')
    b.append(t(x - 62, (last_y + 290) / 2 + 20, "되짚을 수 있는", 14, "#5b6b70"))
    b.append(t(x - 62, (last_y + 290) / 2 + 40, "것은 방향뿐", 14, "#5b6b70"))
    b.append(note(890, "방향은 되짚을 수 있어도 크기는 알 수 없다"))
    return base("숫자가 밀리는 자리", "자료가 만들어지는 단계마다 기우는 방향", "\n".join(b))


FIGURES = {
    5: fig_filter_stages,
    9: fig_ledger_page,
    17: fig_burden_distribution,
    25: fig_year_schedule,
    36: fig_two_year_table,
    42: fig_distortion_flow,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild034")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
