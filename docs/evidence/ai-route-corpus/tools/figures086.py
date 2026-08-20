#!/usr/bin/env python3
"""book-086 이미지 페이지 6개의 SVG 생성. 색 토큰과 t()·base()는 figure_lib에서 가져온다.

여섯 도표가 모두 '새벽의 순서'를 그린다. 시간과 물건은 늘 왼쪽에서 오른쪽으로 흐르고, 사람이
일하는 자리는 옅은 붉은색, 여행자가 설 수 있는 자리는 진한 초록으로 고정한다.

사용: python3 figures086.py <출력디렉터리>
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from figure_lib import W, INK, LINE, SOFT, MUTED, KEEP, DROP, MARK, caption, make_base, note_box, t

WORK = "#e5cfc2"

base = make_base()


def person(cx, cy, s=1.0, color=KEEP):
    return (f'<circle cx="{cx}" cy="{cy - 11 * s}" r="{5.5 * s}" fill="{color}"/>'
            f'<path d="M{cx - 6 * s},{cy + 12 * s} L{cx},{cy - 5 * s} L{cx + 6 * s},{cy + 12 * s}" '
            f'fill="none" stroke="{color}" stroke-width="{2.6 * s}"/>')


def boat(cx, cy, s=1.0, color=INK):
    return (f'<path d="M{cx - 30 * s},{cy} L{cx + 30 * s},{cy} L{cx + 18 * s},{cy + 14 * s} '
            f'L{cx - 18 * s},{cy + 14 * s} z" fill="{color}"/>'
            f'<line x1="{cx}" y1="{cy}" x2="{cx}" y2="{cy - 20 * s}" stroke="{color}" stroke-width="{2.6 * s}"/>')


# ── p9 바다에서 도로까지 ──────────────────────────────────────────────
def fig_market_flow():
    b = []
    x0, y0, w, h = 190, 340, 470, 300
    b.append(f'<rect x="{x0}" y="{y0}" width="{w}" height="{h}" rx="12" fill="#fbfbfa" '
             f'stroke="{INK}" stroke-width="2"/>')
    zones = [("부린 자리", 0, 0.3), ("경매 바닥", 0.3, 0.68), ("실어 내는 자리", 0.68, 1.0)]
    for name, s, e in zones:
        zx = x0 + w * s
        zw = w * (e - s)
        if name == "경매 바닥":
            b.append(f'<rect x="{zx}" y="{y0 + 40}" width="{zw}" height="{h - 100}" fill="#fdf6e6" '
                     f'stroke="#d8b45c" stroke-width="3"/>')
        b.append(t(zx + zw / 2, y0 + 30, name, 15, INK, "700"))
    for at in (0.3, 0.68):
        ax = x0 + w * at
        b.append(f'<line x1="{ax - 18}" y1="{y0 + h / 2}" x2="{ax + 18}" y2="{y0 + h / 2}" '
                 f'stroke="{KEEP}" stroke-width="3" marker-end="url(#keep)"/>')
    b.append(boat(120, 470, 1.0, INK))
    b.append(t(120, 520, "배", 15, MUTED))
    b.append(f'<line x1="152" y1="474" x2="{x0 - 8}" y2="474" stroke="{KEEP}" stroke-width="3" '
             f'marker-end="url(#keep)"/>')
    b.append(f'<rect x="676" y="452" width="52" height="32" rx="5" fill="{INK}"/>')
    b.append(t(702, 520, "도로", 15, MUTED))
    b.append(f'<line x1="{x0 + w + 8}" y1="470" x2="668" y2="470" stroke="{KEEP}" stroke-width="3" '
             f'marker-end="url(#keep)"/>')
    for px in (x0 + w * 0.32, x0 + w * 0.5, x0 + w * 0.64):
        b.append(person(px, y0 + h - 26, 0.9, KEEP))
    b.append(t(x0 + w * 0.49, y0 + h + 14, "여기서 본다", 13, KEEP, "700"))
    b.append(t(W / 2, 262, "물건은 한 방향으로만 지난다", 15, MUTED))
    b.append(note_box(147, 720, 500, "건물의 생김새가 순서를 담고 있다"))
    b.append(caption(W / 2, 842, [
        "노란 선 안쪽이 경매 바닥이고 여행자는 그 밖의 통로에 선다.",
    ], 16))
    return base("바다에서 도로까지", "위판장을 지나는 물건의 순서", "".join(b))


# ── p15 물러나 있을 자리 ──────────────────────────────────────────────
def fig_stand_back():
    b = []
    b.append(f'<rect x="110" y="300" width="574" height="440" rx="12" fill="#fcfcfb" stroke="{SOFT}"/>')
    b.append(boat(180, 400, 1.1, INK))
    b.append(t(180, 356, "배", 14, MUTED))
    b.append(f'<rect x="236" y="430" width="330" height="90" rx="10" fill="{WORK}"/>')
    b.append(t(400, 482, "짐이 지남", 15, MARK, "700"))
    b.append(f'<rect x="150" y="430" width="80" height="90" rx="10" fill="{WORK}" opacity="0.7"/>')
    b.append(t(190, 482, "밧줄", 13, MARK, "700"))
    b.append(f'<circle cx="600" cy="475" r="46" fill="{WORK}" opacity="0.7"/>')
    b.append(t(600, 480, "지게차", 13, MARK, "700"))
    b.append(f'<rect x="572" y="330" width="100" height="70" rx="8" fill="#ffffff" stroke="{INK}"/>')
    b.append(t(622, 372, "위판장", 14, INK, "700"))
    spots = [(200, 610, "방파제 안쪽", "배가 들어오는 것"),
             (400, 610, "위판장 통로", "값이 매겨지는 것"),
             (600, 610, "식당 앞", "사람들이 오가는 것")]
    for x, y, name, sees in spots:
        b.append(person(x, y, 1.2, KEEP))
        b.append(t(x, y + 34, name, 13, KEEP, "700"))
        b.append(t(x, y + 54, sees, 12, MUTED))
        b.append(f'<line x1="{x}" y1="{y - 24}" x2="{x}" y2="{y - 66}" stroke="{LINE}" '
                 f'stroke-width="1.4" stroke-dasharray="4 4"/>')
    b.append(t(W / 2, 262, "칠해진 자리에는 서지 않는다", 15, MUTED))
    b.append(note_box(147, 800, 500, "가까이서 보고 싶은 자리가 가장 위험하다"))
    b.append(caption(W / 2, 922, [
        "십 분 보고 자리를 옮기면 셋을 다 볼 수 있다.",
    ], 16))
    return base("물러나 있을 자리", "새벽 부두에서 설 수 있는 곳", "".join(b))


# ── p21 새벽 두 시부터 아홉 시까지 ────────────────────────────────────
def fig_shared_dawn():
    b = []
    x0, x1 = 168, 676
    span = 7.0  # 두 시 ~ 아홉 시
    rows = [("부두", [(1.0, 4.0, "배가 들어옴", KEEP)]),
            ("위판장", [(4.0, 5.5, "값이 매겨짐", MARK)]),
            ("아침식당", [(2.0, 4.0, "부두 쪽 집", KEEP), (4.0, 7.0, "골목 안쪽 집", "#8fb0a6")])]
    for i, (name, blocks) in enumerate(rows):
        y = 350 + i * 130
        b.append(t(x0 - 16, y + 34, name, 16, INK, "700", anchor="end"))
        b.append(f'<rect x="{x0}" y="{y}" width="{x1 - x0}" height="58" fill="#f4f6f5" stroke="{SOFT}"/>')
        for s, e, label, color in blocks:
            bx = x0 + (x1 - x0) * (s / span)
            bw = (x1 - x0) * ((e - s) / span)
            b.append(f'<rect x="{bx}" y="{y}" width="{bw}" height="58" fill="{color}" opacity="0.85"/>')
            b.append(t(bx + bw / 2, y + 35, label, 13, "#ffffff", "700"))
    lx = x0 + (x1 - x0) * (4.0 / span)
    b.append(f'<line x1="{lx}" y1="330" x2="{lx}" y2="{350 + 2 * 130 + 78}" stroke="{INK}" '
             f'stroke-width="2" stroke-dasharray="6 5"/>')
    b.append(t(lx, 318, "첫 배가 들어온 뒤", 13, INK, "700"))
    b.append(t(x0, 350 + 2 * 130 + 104, "새벽 두 시", 13, MUTED, anchor="start"))
    b.append(t(x1, 350 + 2 * 130 + 104, "아홉 시", 13, MUTED, anchor="end"))
    b.append(t(W / 2, 262, "세 곳이 같은 새벽을 나누어 쓴다", 15, MUTED))
    b.append(note_box(147, 830, 500, "어느 시각에 어디에 있어야 하는가"))
    b.append(caption(W / 2, 952, [
        "부두가 끝나는 자리에서 위판장이 시작된다.",
    ], 16))
    return base("새벽 두 시부터 아홉 시까지", "부두와 위판장과 식당의 시간", "".join(b))


# ── p29 서도 되는 자리 ────────────────────────────────────────────────
def fig_where_to_stand():
    b = []
    b.append(f'<rect x="120" y="310" width="554" height="420" rx="12" fill="#fcfcfb" stroke="{SOFT}"/>')
    b.append(boat(190, 390, 1.0, INK))
    b.append(f'<rect x="250" y="420" width="300" height="80" rx="10" fill="{WORK}"/>')
    b.append(t(400, 468, "짐이 지남", 14, MARK, "700"))
    b.append(f'<rect x="170" y="420" width="70" height="80" rx="10" fill="{WORK}" opacity="0.7"/>')
    b.append(t(205, 468, "밧줄", 12, MARK, "700"))
    b.append(f'<circle cx="590" cy="460" r="42" fill="{WORK}" opacity="0.7"/>')
    b.append(t(590, 466, "지게차", 12, MARK, "700"))
    rules = ["움직이는 것 앞에 서지 않는다", "한자리에 십 분을 넘기지 않는다", "묻고 들어간다"]
    for k, rule in enumerate(rules):
        y = 570 + k * 46
        b.append(person(170, y, 1.0, KEEP))
        b.append(t(196, y + 6, rule, 15, INK, anchor="start"))
    b.append(t(W / 2, 262, "일터에서 서 있는 세 가지 규칙", 15, MUTED))
    b.append(note_box(147, 790, 500, "십 분 보고 자리를 옮긴다"))
    b.append(caption(W / 2, 912, [
        "묻고 들어간 자리와 그냥 들어간 자리는 다르다.",
    ], 16))
    return base("서도 되는 자리", "새벽 부두에서 지키는 규칙", "".join(b))


# ── p37 사흘의 새벽 ───────────────────────────────────────────────────
def fig_three_dawns():
    b = []
    x0, x1 = 168, 676
    span = 7.0
    days = [("첫날", [(3.4, "숙소에서"), (4.2, "방파제"), (6.6, "아침식당")], 4.0),
            ("둘째 날", [(0.6, "숙소에서"), (1.2, "방파제"), (4.0, "위판장 통로"),
                       (6.0, "아침식당")], 1.4),
            ("셋째 날", [(0.6, "숙소에서"), (1.2, "방파제"), (3.6, "위판장 통로"),
                       (5.6, "아침식당")], 1.9)]
    for i, (label, marks, first) in enumerate(days):
        y = 350 + i * 150
        b.append(t(x0 - 16, y + 6, label, 16, INK, "700", anchor="end"))
        b.append(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="{SOFT}" stroke-width="3"/>')
        for k, (at, name) in enumerate(marks):
            x = x0 + (x1 - x0) * (at / span)
            b.append(f'<circle cx="{x}" cy="{y}" r="9" fill="{KEEP}"/>')
            b.append(t(x, y - 22 if k % 2 == 0 else y + 32, name, 12, MUTED))
        fx = x0 + (x1 - x0) * (first / span)
        b.append(f'<line x1="{fx}" y1="{y - 44}" x2="{fx}" y2="{y + 44}" stroke="{MARK}" '
                 f'stroke-width="2" stroke-dasharray="5 4"/>')
        if i == 0:
            b.append(t(fx, y - 54, "첫 배", 12, MARK, "700"))
            b.append(t(x0, y + 62, "새벽 두 시", 12, MUTED, anchor="start"))
            b.append(t(x1, y + 62, "아홉 시", 12, MUTED, anchor="end"))
    b.append(t(W / 2, 262, "첫날에는 위판장을 놓쳤다", 15, MUTED))
    b.append(note_box(147, 850, 500, "첫 배 시각이 그날의 나머지를 정한다"))
    b.append(caption(W / 2, 972, [
        "둘째 날부터 같은 순서를 같은 시각에 되풀이했다.",
    ], 16))
    return base("사흘의 새벽", "언제 어디에 있었는지", "".join(b))


# ── p45 두 철의 하루 ──────────────────────────────────────────────────
def fig_two_seasons():
    b = []
    x0, x1 = 150, 674
    span = 18.0  # 자정 ~ 저녁 여섯 시
    rows = [("조업하는 철", [(0, 3, "출항", KEEP), (3, 6, "귀항", KEEP),
                          (6, 7.5, "위판", MARK), (7.5, 9, "아침식사", KEEP),
                          (9, 18, "잠", DROP)]),
            ("나가지 않는 철", [(0, 9, "아무 일도 없음", DROP), (9, 14, "배 손질", KEEP),
                           (14, 18, "그물 짜기", KEEP)])]
    for i, (label, blocks) in enumerate(rows):
        y = 360 + i * 190
        b.append(t(x0 - 16, y + 34, label, 15, INK, "700", anchor="end"))
        for s, e, name, color in blocks:
            bx = x0 + (x1 - x0) * (s / span)
            bw = (x1 - x0) * ((e - s) / span)
            b.append(f'<rect x="{bx}" y="{y}" width="{bw}" height="58" fill="{color}" '
                     f'opacity="{0.35 if color == DROP else 0.85}" stroke="#ffffff"/>')
            if bw > 54:
                b.append(t(bx + bw / 2, y + 35, name, 12,
                           MUTED if color == DROP else "#ffffff", "700"))
            else:
                b.append(t(bx + bw / 2, y - 10, name, 12, INK, "700"))
    b.append(f'<line x1="240" y1="436" x2="240" y2="484" stroke="{MARK}" stroke-width="2.4"/>')
    b.append(f'<line x1="240" y1="484" x2="560" y2="484" stroke="{MARK}" stroke-width="2.4"/>')
    b.append(f'<line x1="560" y1="484" x2="560" y2="540" stroke="{MARK}" stroke-width="2.4" '
             f'marker-end="url(#mark)"/>')
    b.append(t(400, 474, "진한 자리가 낮으로 옮겨 간다", 13, MARK, "700"))
    b.append(t(x0, 360 + 190 + 84, "자정", 13, MUTED, anchor="start"))
    b.append(t(x1, 360 + 190 + 84, "저녁 여섯 시", 13, MUTED, anchor="end"))
    b.append(t(W / 2, 262, "같은 마을에 두 개의 하루가 있다", 15, MUTED))
    b.append(note_box(147, 800, 500, "언제 가느냐가 무엇을 보느냐를 정한다"))
    b.append(caption(W / 2, 922, [
        "나가지 않는 철에는 새벽에 일어날 필요가 없다.",
    ], 16))
    return base("두 철의 하루", "조업하는 철과 하지 않는 철", "".join(b))


FIGURES = {
    9: fig_market_flow,
    15: fig_stand_back,
    21: fig_shared_dawn,
    29: fig_where_to_stand,
    37: fig_three_dawns,
    45: fig_two_seasons,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild086")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
