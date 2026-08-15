#!/usr/bin/env python3
"""book-083 이미지 페이지 6개의 SVG 생성. figures081·082의 t()/base() 패턴을 그대로 쓴다.

여섯 도표가 모두 '무엇이 어디서 어디로 가는가'와 '무엇을 보고 무엇을 못 보는가'를 그린다. 물가에서
안쪽으로, 새벽에서 낮으로 가는 방향은 늘 왼쪽에서 오른쪽 또는 아래에서 위로 두고, 본 것은 진한 색,
보지 못한 것은 옅은 색으로 고정한다.

사용: python3 figures083.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
KEEP = "#3f6f66"
DROP = "#c3ccd0"
MARK = "#b4703a"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="keep" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{KEEP}"/></marker>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
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


def person(cx, cy, scale=1.0, color=INK, opacity=1.0):
    """사람 기호. 시장의 사람 수를 나타낼 때 같은 뜻으로 쓴다."""
    r = 5 * scale
    return (f'<circle cx="{cx}" cy="{cy - 10 * scale}" r="{r}" fill="{color}" opacity="{opacity}"/>'
            f'<path d="M{cx - 6 * scale},{cy + 12 * scale} L{cx},{cy - 4 * scale} '
            f'L{cx + 6 * scale},{cy + 12 * scale}" fill="none" stroke="{color}" '
            f'stroke-width="{2.4 * scale}" opacity="{opacity}"/>')


# ── p7 물가에서 안쪽으로 ──────────────────────────────────────────────
def fig_three_markets():
    b = []
    x0, y = 118, 330
    bw, bh, gap = 168, 190, 26
    markets = [("새벽어시장", "반나절", "새벽", 4.0),
               ("향신료골목", "몇 달", "아침부터 낮", 2.4),
               ("헌옷마당", "몇 해", "오후", 1.2)]
    for i, (name, stay, opens, width) in enumerate(markets):
        x = x0 + i * (bw + gap)
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="10" fill="#ffffff" '
                 f'stroke="{INK}" stroke-width="{width}"/>')
        b.append(t(x + bw / 2, y + 44, name, 18, INK, "700"))
        b.append(t(x + bw / 2, y + 96, "머무는 시간", 13, MUTED))
        b.append(t(x + bw / 2, y + 124, stay, 20, MARK, "700"))
        b.append(t(x + bw / 2, y + bh + 34, opens, 15, MUTED))
    for k in range(3):
        b.append(f'<path d="M{60},{y + 40 + k * 46} q14,-12 28,0 q14,12 28,0" fill="none" '
                 f'stroke="{LINE}" stroke-width="2"/>')
    b.append(t(74, y - 16, "바다", 15, MUTED, anchor="start"))
    ay = y + bh + 76
    b.append(f'<line x1="{x0}" y1="{ay}" x2="{x0 + 3 * bw + 2 * gap}" y2="{ay}" stroke="{LINE}" '
             f'stroke-width="2" marker-end="url(#gray)"/>')
    b.append(t(W / 2, ay + 28, "머무는 시간이 길어질수록 안쪽", 15, MUTED))
    b.append(note_box(147, 780, 500, "테두리가 굵을수록 거래가 빠르다"))
    b.append(caption(W / 2, 902, [
        "값이 정해지는 속도도 이 순서를 따른다.",
    ], 16))
    return base("물가에서 안쪽으로", "세 시장이 놓인 순서와 물건이 머무는 시간", "".join(b))


# ── p13 한 바닥의 세 시간대 ───────────────────────────────────────────
def fig_one_floor():
    b = []
    x0, x1, y = 112, 682, 330
    segs = [("중매인", "짧은 외침", 8), ("도시 사람들", "말소리", 4), ("물 뿌리기", "물소리", 1)]
    width = (x1 - x0) / 3
    for i, (who, sound, count) in enumerate(segs):
        x = x0 + i * width
        b.append(f'<rect x="{x}" y="{y}" width="{width}" height="150" '
                 f'fill="{["#e6ecea", "#eef0ea", "#f2f2f0"][i]}" stroke="{SOFT}"/>')
        b.append(t(x + width / 2, y - 16, sound, 14, MARK, "700"))
        b.append(t(x + width / 2, y + 30, who, 17, INK, "700"))
        for k in range(count):
            px = x + 26 + (k % 4) * 40
            py = y + 76 + (k // 4) * 44
            b.append(person(px, py, 0.9, INK, 0.8))
    b.append(t(x0, y + 178, "새벽 세 시", 14, MUTED, anchor="start"))
    b.append(t(x1, y + 178, "아홉 시", 14, MUTED, anchor="end"))
    cy0, cy1 = y + 250, y + 380
    b.append(f'<line x1="{x0}" y1="{cy1}" x2="{x1}" y2="{cy1}" stroke="{SOFT}" stroke-width="2"/>')
    pts = [(x0, cy0), (x0 + width, cy0 + 40), (x0 + 2 * width, cy1 - 30), (x1, cy1)]
    b.append('<polyline points="' + " ".join(f"{px},{py}" for px, py in pts) +
             f'" fill="none" stroke="{MARK}" stroke-width="3"/>')
    b.append(t(x0 - 6, cy0 + 6, "많음", 14, MUTED, anchor="end"))
    b.append(t(x0 - 6, cy1 + 6, "없음", 14, MUTED, anchor="end"))
    b.append(t(W / 2, cy1 + 34, "바닥에 남은 물건의 양", 15, MUTED))
    b.append(t(W / 2, 262, "같은 바닥이 세 번 주인을 바꾼다", 15, MUTED))
    b.append(note_box(147, 830, 500, "같은 자리가 시각마다 다른 시장이다"))
    b.append(caption(W / 2, 952, [
        "몇 시에 여느냐가 아니라 몇 시에 무엇이 되느냐를 묻는다.",
    ], 16))
    return base("한 바닥의 세 시간대", "새벽 세 시부터 아홉 시까지", "".join(b))


# ── p21 이백 걸음의 네 층 ─────────────────────────────────────────────
def fig_layers():
    b = []
    lx, rx = 250, 500
    y0, y1 = 300, 800
    layers = [("기름", "#f3efe6"), ("볶은 씨앗", "#eee7da"), ("말린 껍질", "#e6dcc9"),
              ("마른 고추", "#dccdb2")]
    h = (y1 - y0) / 4
    for i, (name, fill) in enumerate(layers):
        y = y0 + i * h
        b.append(f'<rect x="{lx}" y="{y}" width="{rx - lx}" height="{h}" fill="{fill}" stroke="{SOFT}"/>')
        b.append(t((lx + rx) / 2, y + h / 2 + 6, name, 17, INK, "600"))
    b.append(t((lx + rx) / 2, y0 - 20, "안쪽", 15, MUTED))
    b.append(t((lx + rx) / 2, y1 + 30, "입구", 15, MUTED))
    b.append(f'<line x1="{rx + 40}" y1="{y1}" x2="{rx + 40}" y2="{y0}" stroke="{MARK}" '
             f'stroke-width="2" marker-end="url(#mark)"/>')
    b.append(t(rx + 52, y1 - 6, "싸다", 14, MARK, anchor="start"))
    b.append(t(rx + 52, y0 + 14, "비싸다", 14, MARK, anchor="start"))
    b.append(f'<line x1="{lx - 40}" y1="{y1}" x2="{lx - 40}" y2="{y0}" stroke="{LINE}" '
             f'stroke-width="2" marker-end="url(#gray)"/>')
    b.append(t(lx - 52, (y0 + y1) / 2 - 14, "입구에서 들어가면", 13, MUTED, anchor="end"))
    b.append(t(lx - 52, (y0 + y1) / 2 + 8, "뒤엣것이 묻힌다", 13, MUTED, anchor="end"))
    b.append(f'<line x1="{rx + 130}" y1="{y0}" x2="{rx + 130}" y2="{y1}" stroke="{KEEP}" '
             f'stroke-width="2.4" marker-end="url(#keep)"/>')
    b.append(t(rx + 142, (y0 + y1) / 2 - 14, "안쪽부터", 13, KEEP, "700", anchor="start"))
    b.append(t(rx + 142, (y0 + y1) / 2 + 8, "걸어 나오면", 13, KEEP, "700", anchor="start"))
    b.append(t(rx + 142, (y0 + y1) / 2 + 30, "다 구분된다", 13, KEEP, "700", anchor="start"))
    b.append(t(W / 2, 262, "이백 걸음 안에서 냄새가 네 번 바뀐다", 15, MUTED))
    b.append(note_box(147, 880, 500, "앞자리는 회전이 빠른 것의 자리"))
    b.append(caption(W / 2, 1002, [
        "바탕이 진할수록 냄새가 세다.",
    ], 16))
    return base("이백 걸음의 네 층", "향신료골목의 배열과 걷는 방향", "".join(b))


# ── p30 어디서 와서 어디로 가는가 ─────────────────────────────────────
def fig_flows():
    b = []
    cx0, cy0, cw, ch = 168, 380, 458, 300
    b.append(f'<rect x="{cx0}" y="{cy0}" width="{cw}" height="{ch}" rx="16" fill="#fbfbfa" '
             f'stroke="{INK}" stroke-width="2"/>')
    b.append(t(cx0 + cw / 2, cy0 + 30, "도시", 17, MUTED))
    names = ["새벽어시장", "향신료골목", "헌옷마당"]
    bw, gap = 126, 22
    inner_x = cx0 + (cw - (3 * bw + 2 * gap)) / 2
    centers = []
    for i, name in enumerate(names):
        x = inner_x + i * (bw + gap)
        y = cy0 + 110
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="96" rx="10" fill="#ffffff" '
                 f'stroke="{KEEP}" stroke-width="2"/>')
        b.append(t(x + bw / 2, y + 56, name, 15, INK, "700"))
        centers.append((x + bw / 2, y))
    b.append(t(96, cy0 + 150, "바다", 16, INK, "700"))
    b.append(f'<line x1="122" y1="{cy0 + 156}" x2="{centers[0][0] - 40}" y2="{centers[0][1] + 40}" '
             f'stroke="{KEEP}" stroke-width="2" marker-end="url(#keep)"/>')
    b.append(t(cx0 + cw / 2, 300, "내륙", 16, INK, "700"))
    b.append(f'<line x1="{cx0 + cw / 2}" y1="312" x2="{centers[1][0]}" y2="{centers[1][1] - 8}" '
             f'stroke="{KEEP}" stroke-width="2" marker-end="url(#keep)"/>')
    b.append(t(cx0 + cw / 2, 760, "다른 도시", 16, INK, "700"))
    b.append(f'<line x1="{cx0 + cw / 2 - 60}" y1="748" x2="{centers[1][0] - 20}" '
             f'y2="{centers[1][1] + 104}" stroke="{KEEP}" stroke-width="2" marker-end="url(#keep)"/>')
    b.append(f'<line x1="{centers[2][0] + 20}" y1="{centers[2][1] + 104}" '
             f'x2="{cx0 + cw / 2 + 70}" y2="748" stroke="{MARK}" stroke-width="2" '
             f'marker-end="url(#mark)"/>')
    b.append(t(cx0 + cw + 16, cy0 + 330, "다시 다른 도시로", 13, MARK, "700", anchor="start"))
    b.append(f'<line x1="{centers[2][0]}" y1="{cy0 + 70}" x2="{centers[2][0]}" y2="{centers[2][1] - 8}" '
             f'stroke="{LINE}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(t(centers[2][0], cy0 + 58, "도시 안에서 나온 것", 13, MUTED))
    b.append(t(W / 2, 262, "들어오는 방향이 그 시장의 성격을 정한다", 15, MUTED))
    b.append(note_box(147, 830, 500, "물건의 여정이 가장 긴 곳은 헌옷마당"))
    b.append(caption(W / 2, 952, [
        "어시장과 향신료골목은 바깥에서 들어오고,",
        "헌옷마당만 도시 안에서 나온다.",
    ], 16))
    return base("어디서 와서 어디로 가는가", "세 시장의 물건이 지나는 길", "".join(b))


# ── p37 사흘의 순서 ───────────────────────────────────────────────────
def fig_three_days():
    b = []
    x0, x1 = 150, 674
    rows = [
        ("첫날", [(5.0, "어시장", True), (6.0, "향신료골목", True), (10.5, "헌옷마당", True)]),
        ("둘째 날", [(3.0, "어시장", False), (8.0, "향신료골목", False), (14.0, "헌옷마당", False)]),
        ("셋째 날", [(3.0, "장이 서지 않음", None), (8.0, "향신료골목", False),
                   (13.0, "향신료골목", False)]),
    ]
    span = 15.0  # 새벽 세 시부터 저녁 여섯 시
    for i, (label, marks) in enumerate(rows):
        y = 340 + i * 150
        b.append(t(x0 - 16, y + 6, label, 16, INK, "700", anchor="end"))
        b.append(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="{SOFT}" stroke-width="3"/>')
        for k, (at, name, failed) in enumerate(marks):
            x = x0 + (x1 - x0) * (at / span)
            if failed is None:
                b.append(f'<circle cx="{x}" cy="{y}" r="9" fill="#ffffff" stroke="{LINE}" '
                         f'stroke-width="2" stroke-dasharray="3 3"/>')
            else:
                b.append(f'<circle cx="{x}" cy="{y}" r="9" fill="{MARK if failed else KEEP}"/>')
            b.append(t(x, y - 22 if k % 2 == 0 else y + 32, name, 13, MUTED))
            if failed:
                b.append(f'<line x1="{x - 9}" y1="{y - 9}" x2="{x + 9}" y2="{y + 9}" '
                         f'stroke="{MARK}" stroke-width="2.4"/>')
                b.append(f'<line x1="{x + 9}" y1="{y - 9}" x2="{x - 9}" y2="{y + 9}" '
                         f'stroke="{MARK}" stroke-width="2.4"/>')
        if i == 0:
            b.append(t(x0, y + 62, "새벽 세 시", 13, MUTED, anchor="start"))
            b.append(t(x1, y + 62, "저녁 여섯 시", 13, MUTED, anchor="end"))
    b.append(t(W / 2, 262, "가위표는 헛걸음한 자리다", 15, MUTED))
    b.append(note_box(147, 830, 500, "순서를 정하는 데 필요한 것은 세 시각뿐"))
    b.append(caption(W / 2, 952, [
        "셋째 날에는 어시장이 쉬어 향신료골목을 두 번 갔다.",
    ], 16))
    return base("사흘의 순서", "세 시장을 언제 갔는지", "".join(b))


# ── p45 본 것과 보지 않은 것 ──────────────────────────────────────────
def fig_scope():
    b = []
    cx, cy, r = 360, 560, 230
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#fafafa" stroke="{INK}" stroke-width="2"/>')
    b.append(t(cx, cy - r + 34, "도시", 18, INK, "700"))
    b.append(f'<ellipse cx="{cx - 40}" cy="{cy + 70}" rx="132" ry="86" fill="#eef2f0" '
             f'stroke="{KEEP}" stroke-width="2" stroke-dasharray="6 5"/>')
    for dx, dy, name in ((-96, 60, "새벽어시장"), (-16, 92, "향신료골목"), (-52, 128, "헌옷마당")):
        b.append(f'<circle cx="{cx + dx}" cy="{cy + dy - 30}" r="26" fill="#ffffff" stroke="{KEEP}"/>')
        b.append(t(cx + dx, cy + dy - 26, name, 11, INK, "700"))
    b.append(t(cx - 40, cy + 172, "사흘 동안 본 범위", 14, KEEP, "700"))
    for dx, dy, name in ((70, -110, "학교"), (128, -40, "관청"), (150, 14, "집 안"),
                         (40, -168, "팔지 않는 일")):
        b.append(t(cx + dx, cy + dy, name, 14, DROP))
    bx = 660
    b.append(f'<rect x="{bx}" y="{cy + 40}" width="34" height="60" fill="{KEEP}"/>')
    b.append(f'<rect x="{bx + 54}" y="{cy - 120}" width="34" height="220" fill="{DROP}"/>')
    b.append(t(bx + 17, cy + 122, "본 것", 13, KEEP, "700"))
    b.append(t(bx + 71, cy + 122, "도시 전체", 13, MUTED))
    b.append(t(W / 2, 262, "세 시장은 도시의 한 겹이다", 15, MUTED))
    b.append(note_box(147, 880, 500, "단면을 도시라고 적으면 틀린 기록이 된다"))
    b.append(caption(W / 2, 1002, [
        "무엇을 보지 못했는지를 함께 적는 것이 이 방법의 조건이다.",
    ], 16))
    return base("본 것과 보지 않은 것", "시장으로 읽은 범위와 도시의 크기", "".join(b))


FIGURES = {
    7: fig_three_markets,
    13: fig_one_floor,
    21: fig_layers,
    30: fig_flows,
    37: fig_three_days,
    45: fig_scope,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild083")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
