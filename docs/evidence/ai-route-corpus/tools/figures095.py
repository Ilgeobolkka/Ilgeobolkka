#!/usr/bin/env python3
"""book-095 이미지 페이지 4개의 SVG 생성. figures091.py의 t()/base() 패턴을 따른다.

네 도표의 형식을 모두 다르게 잡았다. 흐릿함과 또렷함의 대비, 격자 배분, 계단, 두 시간선이다.
공통 약속은 둘이다. 만들어진 것은 진한 색, 비어 있는 것은 빈 칸이다.

사용: python3 figures095.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"
DROP = "#c3ccd0"
MARK = "#b4703a"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body, defs=""):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="keep" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{KEEP}"/></marker>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <filter id="blur"><feGaussianBlur stdDeviation="6"/></filter>
{defs}
</defs>
{t(W / 2, 122, title, 31, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


# ── p7 꺼내 놓으면 보인다 ─────────────────────────────────────────────
def fig_externalize():
    """머릿속의 흐릿한 구상과 종이 위의 또렷한 초안을 좌우로 대비한다."""
    b = []
    bw, bh, y = 250, 250, 280
    xs = [82, 462]
    b.append(t(xs[0] + bw / 2, y - 18, "머릿속", 17, MUTED, "700"))
    b.append(t(xs[1] + bw / 2, y - 18, "종이 위", 17, KEEP, "700"))
    x = xs[0]
    b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="10" fill="#fbfbfc" '
             f'stroke="{DROP}" stroke-width="1.8"/>')
    blobs = [(70, 70, 60, 44), (130, 120, 74, 52), (60, 170, 66, 40), (150, 200, 58, 36)]
    inner = "".join(
        f'<ellipse cx="{x + cx}" cy="{y + cy}" rx="{rx}" ry="{ry}" fill="{LINE}" opacity="0.5"/>'
        for cx, cy, rx, ry in blobs)
    b.append(f'<g filter="url(#blur)">{inner}</g>')
    b.append(t(x + bw / 2, y + bh + 30, "어디가 비었는지 알 수 없다", 15, MUTED))
    x = xs[1]
    b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="10" fill="#ffffff" '
             f'stroke="{KEEP}" stroke-width="2.6"/>')
    cells = [(28, 34, True), (136, 34, False), (28, 118, True), (136, 118, False), (28, 196, True)]
    for cx, cy, filled in cells:
        b.append(f'<rect x="{x + cx}" y="{y + cy}" width="86" height="58" rx="6" '
                 f'fill="{"#e7eef0" if filled else "#ffffff"}" stroke="{INK}" stroke-width="1.6"/>')
        if not filled:
            b.append(t(x + cx + 43, y + cy + 38, "?", 24, MARK, "700"))
    b.append(f'<line x1="{x + 28}" y1="{y + 200 + 58}" x2="{x + 114}" y2="{y + 200 + 58}" '
             f'stroke="{MARK}" stroke-width="3.4"/>')
    b.append(t(x + bw / 2, y + bh + 30, "빈 자리가 보인다", 15, KEEP, "700"))
    ax = xs[0] + bw + 22
    b.append(f'<line x1="{ax}" y1="{y + bh / 2}" x2="{xs[1] - 22}" y2="{y + bh / 2}" stroke="{MARK}" '
             f'stroke-width="3" marker-end="url(#mark)"/>')
    b.append(t((ax + xs[1] - 22) / 2, y + bh / 2 - 16, "한 시간짜리", 13, MARK, "700"))
    b.append(t((ax + xs[1] - 22) / 2, y + bh / 2 + 30, "초안", 13, MARK, "700"))
    b.append(note_box(147, 640, 500, "초안의 값은 드러난 것으로 잰다"))
    b.append(caption(W / 2, 762, [
        "머릿속에서는 흐릿한 부분이 흐릿한 채로 있고,",
        "어디가 흐릿한지도 알 수 없다.",
    ], 16))
    return base("꺼내 놓으면 보인다", "머릿속의 계획과 밖으로 꺼낸 초안", "".join(b))


# ── p15 얇게 전체를 만든다 ────────────────────────────────────────────
def fig_thin_slice():
    """같은 제작량을 부분 심화와 전체 박화로 배분한 두 격자."""
    b = []
    cols, rows = 4, 3
    cw, ch = 62, 58
    y = 310
    xs = [92, 452]
    titles = ["앞부분만 완성", "전체를 얇게"]
    notes = ["끝까지 진행할 수 없다", "부족한 채로 끝까지 간다"]
    fills = [
        [(0, r) for r in range(rows)],
        [(c, rows - 1) for c in range(cols)],
    ]
    for x, title, note, filled in zip(xs, titles, notes, fills):
        b.append(t(x + cols * cw / 2, y - 22, title, 17, INK, "700"))
        for c in range(cols):
            for r in range(rows):
                on = (c, r) in filled
                b.append(f'<rect x="{x + c * cw}" y="{y + r * ch}" width="{cw}" height="{ch}" '
                         f'fill="{KEEP if on else "#ffffff"}" opacity="{0.85 if on else 1}" '
                         f'stroke="{LINE}" stroke-width="1.2"/>')
        b.append(t(x + cols * cw / 2, y + rows * ch + 32, note, 15, MUTED))
    b.append(t(xs[1] - 26, y + (rows - 0.5) * ch + 6, "최소", 13, MARK, "700", anchor="end"))
    b.append(t(xs[1] - 26, y + (rows - 0.5) * ch + 24, "버전", 13, MARK, "700", anchor="end"))
    b.append(f'<line x1="{xs[1] - 20}" y1="{y + (rows - 0.5) * ch}" x2="{xs[1] - 6}" '
             f'y2="{y + (rows - 0.5) * ch}" stroke="{MARK}" stroke-width="2" marker-end="url(#mark)"/>')
    b.append(t(W / 2, y + rows * ch + 76, "가로는 강의의 네 부분, 세로는 완성도의 세 층이다", 14, MUTED))
    b.append(note_box(147, 660, 500, "같은 양을 만들어도 어디에 쓰느냐가 다르다"))
    b.append(caption(W / 2, 782, [
        "부분만 두껍게 만들면 그 부분은 쓸 만하지만 전체로는 쓸 수 없다.",
        "얇아도 끝까지 있으면 한 번 돌려 보고 무엇이 부족한지 알 수 있다.",
    ], 16))
    return base("얇게 전체를 만든다", "같은 제작량의 두 가지 배분", "".join(b))


# ── p21 세 바퀴 ───────────────────────────────────────────────────────
def fig_cycles():
    """세 번의 주기를 계단으로 그리고 산출물과 남은 결손을 함께 보인다."""
    b = []
    x0, y_base = 120, 560
    sw, sh = 168, 62
    steps = [("만들기 두 시간", 0, 62, 3, "첫 회차"),
             ("고치기 한 시간", 1, 86, 2, "둘째 회차"),
             ("고치기 한 시간", 2, 112, 1, None)]
    for label, i, box_h, gaps, arrow in steps:
        x = x0 + i * (sw + 32)
        y = y_base - i * 40
        b.append(f'<rect x="{x}" y="{y}" width="{sw}" height="{sh}" rx="8" fill="#eef1f2" '
                 f'stroke="{INK}" stroke-width="1.6"/>')
        b.append(t(x + sw / 2, y + sh / 2 + 6, label, 15, INK))
        bx, by = x + sw / 2 - 52, y - box_h - 14
        b.append(f'<rect x="{bx}" y="{by}" width="104" height="{box_h}" fill="{KEEP}" opacity="0.85" '
                 f'stroke="{INK}" stroke-width="1.2"/>')
        for g in range(gaps):
            b.append(f'<rect x="{bx + 8 + g * 32}" y="{by + 8}" width="24" height="18" '
                     f'fill="#ffffff" stroke="{DROP}" stroke-width="1"/>')
        b.append(t(x + sw / 2, by - 12, "산출물", 13, MUTED))
        if arrow:
            ax0 = x + sw + 4
            b.append(f'<path d="M{ax0},{y + 34} q 14,-22 {28},-34" fill="none" '
                     f'stroke="{MARK}" stroke-width="2.2" marker-end="url(#mark)"/>')
            b.append(t(ax0 + 14, y + 66, "써 보기", 13, MARK, "700"))
            b.append(t(ax0 + 14, y + 84, arrow, 12, MUTED))
    b.append(t(W / 2, 296, "빈 칸은 아직 만들지 않은 부분이다", 15, MUTED))
    b.append(note_box(147, 700, 500, "써 보지 않으면 다음 바퀴가 돌지 않는다"))
    b.append(caption(W / 2, 822, [
        "한 바퀴는 만들고 써 보고 고치는 세 단계다.",
        "바퀴의 크기는 시간이 아니라 한 번 써 볼 수 있는 만큼으로 정한다.",
    ], 16))
    return base("세 바퀴", "반복할수록 자라는 산출물", "".join(b))


# ── p39 같은 넉 달 ────────────────────────────────────────────────────
def fig_timelines():
    """준비만 한 넉 달과 네 번 연 넉 달을 두 시간선으로 비교한다."""
    b = []
    x0, x1 = 190, 664
    for row, (y, label) in enumerate(((330, "준비만 한 넉 달"), (520, "초안으로 연 넉 달"))):
        b.append(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="{INK}" stroke-width="2"/>')
        b.append(t(x0 - 12, y + 5, label, 15, INK, "600", anchor="end"))
        if row == 0:
            for fx in (x0 + 150, x0 + 300):
                b.append(f'<line x1="{fx}" y1="{y - 16}" x2="{fx}" y2="{y}" stroke="{LINE}" '
                         f'stroke-width="2"/>')
                b.append(t(fx, y - 24, "뒤집기", 12, MUTED))
            b.append(f'<rect x="{x1 - 78}" y="{y - 34}" width="78" height="34" rx="6" fill="#ffffff" '
                     f'stroke="{DROP}" stroke-width="2"/>')
            b.append(t(x1 - 39, y - 12, "첫 강의", 13, MUTED))
            b.append(t(x1 - 39, y + 26, "?", 22, MARK, "700"))
        else:
            names = ["첫 회차", "둘째 회차", "셋째 회차", "넷째 회차"]
            for k, name in enumerate(names):
                fx = x0 + 40 + k * 138
                bh = 26 + k * 6
                b.append(f'<rect x="{fx - 38}" y="{y - bh - 6}" width="76" height="{bh}" rx="5" '
                         f'fill="{KEEP}" opacity="0.85" stroke="{INK}" stroke-width="1.2"/>')
                b.append(t(fx, y - bh / 2 + 2, name, 12, "#ffffff", "700"))
                if k < 3:
                    b.append(f'<line x1="{fx + 44}" y1="{y + 22}" x2="{fx + 126}" y2="{y + 22}" '
                             f'stroke="{MARK}" stroke-width="2" marker-end="url(#mark)"/>')
                    b.append(t(fx + 85, y + 42, "고치기", 12, MARK))
    b.append(t(x0, 620, "삼월", 14, MUTED))
    b.append(t(x1, 620, "유월", 14, MUTED))
    b.append(f'<line x1="{x0}" y1="600" x2="{x1}" y2="600" stroke="{DROP}" stroke-width="1" '
             f'stroke-dasharray="5 5"/>')
    b.append(note_box(147, 680, 500, "같은 시간에 확인한 횟수가 다르다"))
    b.append(caption(W / 2, 802, [
        "준비만 한 쪽은 넉 달 동안 한 번도 확인하지 못했다.",
        "초안으로 연 쪽은 회차마다 무엇을 고칠지 알고 다음으로 갔다.",
    ], 16))
    return base("같은 넉 달", "준비만 한 경우와 네 번 연 경우", "".join(b))


FIGURES = {7: fig_externalize, 15: fig_thin_slice, 21: fig_cycles, 39: fig_timelines}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild095")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
