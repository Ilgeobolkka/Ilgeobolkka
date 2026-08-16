#!/usr/bin/env python3
"""book-084 이미지 페이지 6개의 SVG 생성. 색 토큰과 t()·base()는 figure_lib에서 가져온다.

여섯 도표가 모두 '무엇을 남기고 무엇을 버리는가'를 그린다. 쓰는 쪽·지킨 쪽은 진한 색, 버리는 쪽·
어긋난 쪽은 옅은 색으로 고정하고, 시간과 경로는 늘 왼쪽에서 오른쪽으로 둔다.

사용: python3 figures084.py <출력디렉터리>
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from figure_lib import W, INK, LINE, SOFT, MUTED, KEEP, DROP, MARK, caption, make_base, note_box, t

base = make_base()


def split_rock(cx, cy, s=1.0, color=INK, opacity=1.0, split=True):
    """갈림바위. 갈라져 보이는 모양과 한 덩어리로 보이는 모양을 같은 기호로 쓴다."""
    if split:
        return (f'<path d="M{cx - 46 * s},{cy + 30 * s} L{cx - 22 * s},{cy - 34 * s} '
                f'L{cx - 4 * s},{cy + 30 * s} z" fill="{color}" opacity="{opacity}"/>'
                f'<path d="M{cx + 4 * s},{cy + 30 * s} L{cx + 26 * s},{cy - 26 * s} '
                f'L{cx + 46 * s},{cy + 30 * s} z" fill="{color}" opacity="{opacity}"/>')
    return (f'<path d="M{cx - 46 * s},{cy + 30 * s} L{cx - 18 * s},{cy - 32 * s} '
            f'L{cx + 20 * s},{cy - 24 * s} L{cx + 46 * s},{cy + 30 * s} z" '
            f'fill="{color}" opacity="{opacity}"/>')


def walker(cx, cy, s=1.0, color=INK):
    return (f'<circle cx="{cx}" cy="{cy - 26 * s}" r="{7 * s}" fill="{color}"/>'
            f'<line x1="{cx}" y1="{cy - 19 * s}" x2="{cx}" y2="{cy + 2 * s}" stroke="{color}" '
            f'stroke-width="{3 * s}"/>'
            f'<path d="M{cx - 8 * s},{cy + 20 * s} L{cx},{cy + 2 * s} L{cx + 8 * s},{cy + 20 * s}" '
            f'fill="none" stroke="{color}" stroke-width="{3 * s}"/>')


# ── p7 걸어 둘 만한 것 ────────────────────────────────────────────────
def fig_landmarks():
    b = []
    b.append(f'<line x1="{W / 2}" y1="270" x2="{W / 2}" y2="880" stroke="{SOFT}" stroke-width="2"/>')
    b.append(t(280, 262, "걸어 둔다", 18, KEEP, "700"))
    b.append(t(520, 262, "버린다", 18, MUTED, "700"))
    keeps = [("두 갈래 바위", "셋 다"), ("마른 개천 바닥", "셋 다"),
             ("하늘이 열린 능선", "셋 다"), ("큰 구덩이", "셋 다")]
    drops = [("비슷한 나무", "헷갈린다"), ("쓰러진 나무", "이듬해에는 없다"),
             ("그루터기의 버섯", "계절이 바뀌면 없다"), ("발자국", "비가 오면 지워진다")]
    for i in range(4):
        y = 330 + i * 140
        b.append(f'<rect x="150" y="{y}" width="230" height="104" rx="10" fill="#f3f6f4" '
                 f'stroke="{KEEP}" stroke-width="1.6"/>')
        b.append(t(265, y + 40, keeps[i][0], 16, INK, "700"))
        b.append(t(265, y + 74, f"세 조건 {keeps[i][1]} 만족", 13, KEEP))
        b.append(f'<rect x="414" y="{y}" width="230" height="104" rx="10" fill="#f7f7f7" '
                 f'stroke="{DROP}" stroke-width="1.6"/>')
        b.append(t(529, y + 40, drops[i][0], 16, MUTED))
        b.append(t(529, y + 74, drops[i][1], 13, MUTED))
    b.append(t(W / 2, 232, "멀리서 보이는가 · 헷갈리지 않는가 · 계절이 바뀌어도 그대로인가", 14, MUTED))
    b.append(note_box(147, 920, 500, "고른 것은 순서대로 외운다"))
    b.append(caption(W / 2, 1042, [
        "세 조건을 모두 만족하는 것은 대개 지형이다.",
    ], 16))
    return base("걸어 둘 만한 것", "기억에 거는 지형지물과 버리는 것", "".join(b))


# ── p13 뒤를 보고 지나간다 ────────────────────────────────────────────
def fig_look_back():
    b = []
    cx, cy = W / 2, 420
    b.append(split_rock(cx, cy, 1.6, INK))
    b.append(t(cx, cy + 78, "같은 바위", 15, MUTED))
    for x, label, split, arrow in ((214, "갈 때", True, 1), (580, "올 때", False, -1)):
        b.append(walker(x, cy + 30, 1.2, KEEP if split else MARK))
        b.append(t(x, cy - 78, label, 18, INK, "700"))
        bx, by = x, 620
        b.append(f'<rect x="{bx - 92}" y="{by}" width="184" height="140" rx="10" fill="#ffffff" '
                 f'stroke="{KEEP if split else MARK}" stroke-width="2"/>')
        b.append(split_rock(bx, by + 74, 1.15, KEEP if split else MARK, split=split))
        b.append(t(bx, by + 168, "이렇게 보인다", 14, MUTED))
        b.append(f'<line x1="{x + arrow * 60}" y1="{cy + 20}" x2="{x + arrow * 96}" y2="{cy + 20}" '
                 f'stroke="{LINE}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(f'<line x1="306" y1="690" x2="488" y2="690" stroke="{LINE}" stroke-width="1.6" '
             f'stroke-dasharray="5 5"/>')
    b.append(t(W / 2, 682, "같은 자리, 다른 모양", 14, MUTED))
    b.append(f'<path d="M186,392 q-46,24 -8,58" fill="none" stroke="{MARK}" stroke-width="2.4" '
             f'marker-end="url(#mark)"/>')
    b.append(t(104, 384, "지날 때마다 삼 초", 13, MARK, "700", anchor="start"))
    b.append(t(W / 2, 262, "돌아올 때의 모양을 그때 봐 둔다", 15, MUTED))
    b.append(note_box(147, 880, 500, "되짚기는 뒤를 본 자리에서만 가능하다"))
    b.append(caption(W / 2, 1002, [
        "앞만 보고 걸으면 같은 길도 처음 보는 길이 된다.",
    ], 16))
    return base("뒤를 보고 지나간다", "같은 바위가 갈 때와 올 때 보이는 모양", "".join(b))


# ── p21 네 가지를 보고 정한다 ─────────────────────────────────────────
def fig_campsite():
    b = []
    b.append(f'<line x1="{W / 2}" y1="270" x2="{W / 2}" y2="900" stroke="{SOFT}" stroke-width="2"/>')
    for x0, label, good in ((110, "고른 자리", True), (420, "지나친 자리", False)):
        color = KEEP if good else DROP
        b.append(t(x0 + 132, 262, label, 18, KEEP if good else MUTED, "700"))
        gy = 560
        if good:
            b.append(f'<line x1="{x0}" y1="{gy}" x2="{x0 + 264}" y2="{gy}" stroke="{INK}" stroke-width="2"/>')
        else:
            b.append(f'<line x1="{x0}" y1="{gy - 24}" x2="{x0 + 264}" y2="{gy + 20}" stroke="{INK}" stroke-width="2"/>')
        tx = x0 + 150
        b.append(f'<path d="M{tx - 46},{gy - 4} L{tx},{gy - 74} L{tx + 46},{gy - 4} z" '
                 f'fill="#ffffff" stroke="{color}" stroke-width="2.4"/>')
        b.append(f'<line x1="{x0 + 8}" y1="{gy + 40}" x2="{x0 + 264}" y2="{gy + 40}" '
                 f'stroke="#9fc0cc" stroke-width="4"/>')
        b.append(t(x0 + 60, gy + 66, "물길", 13, MUTED))
        b.append(t(tx, gy + 26, "서른 걸음" if good else "바짝 붙음", 13,
                   KEEP if good else MARK, "700"))
        b.append(f'<line x1="{tx}" y1="{gy - 150}" x2="{tx}" y2="{gy - 96}" stroke="#6b7f63" stroke-width="6"/>')
        if good:
            b.append(f'<path d="M{tx - 40},{gy - 150} q40,-24 80,0" fill="none" stroke="#6b7f63" stroke-width="4"/>')
        else:
            b.append(f'<path d="M{tx - 40},{gy - 150} q40,-24 80,0" fill="none" stroke="#6b7f63" stroke-width="4"/>')
            b.append(f'<line x1="{tx + 6}" y1="{gy - 146}" x2="{tx + 44}" y2="{gy - 112}" '
                     f'stroke="{MARK}" stroke-width="4"/>')
            b.append(t(tx + 74, gy - 120, "부러진 가지", 12, MARK, "700", anchor="start"))
        wx = x0 + 250
        b.append(f'<line x1="{wx + 8}" y1="{gy - 60}" x2="{wx - 60}" y2="{gy - 60}" stroke="{LINE}" '
                 f'stroke-width="2" marker-end="url(#gray)"/>')
        if good:
            b.append(f'<rect x="{x0 + 176}" y="{gy - 78}" width="18" height="40" rx="4" fill="{LINE}"/>')
            b.append(t(x0 + 132, gy - 116, "바위가 막는다", 12, KEEP, "700"))
        else:
            b.append(t(x0 + 132, gy - 116, "그대로 들이친다", 12, MARK, "700"))
        checks = ["평평", "물과의 거리", "머리 위", "바람"] if good else ["기울어짐", "너무 가까움",
                                                                  "부러진 가지", "막힘없음"]
        for k, c in enumerate(checks):
            b.append(t(x0 + 20, 700 + k * 30, ("○ " if good else "✕ ") + c, 14,
                       KEEP if good else MARK, "700", anchor="start"))
    b.append(t(W / 2, 232, "평평한가 · 물과의 거리 · 머리 위 · 바람", 14, MUTED))
    b.append(note_box(147, 900, 500, "넷을 다 만족하는 자리는 드물다"))
    b.append(caption(W / 2, 1022, [
        "어두워지기 한 시간 전에 정한다.",
    ], 16))
    return base("네 가지를 보고 정한다", "잘 자리를 고르는 조건", "".join(b))


# ── p29 하루의 네 토막 ────────────────────────────────────────────────
def fig_day_blocks():
    b = []
    x0, x1 = 132, 672
    hours = 11.0  # 아침 일곱 시 ~ 저녁 여섯 시
    sunset = 10.0  # 오후 다섯 시
    days = [
        ("첫날", [(0, 1.2, "떠나기"), (1.2, 7.4, "걷기"), (7.4, 8.4, "쉬기"),
                 (8.4, 10.6, "자리 만들기")]),
        ("둘째 날", [(0, 1.0, "떠나기"), (1.0, 6.6, "걷기"), (6.6, 7.4, "쉬기"),
                   (7.4, 8.9, "자리 만들기")]),
        ("셋째 날", [(0, 1.0, "떠나기"), (1.0, 6.4, "걷기"), (6.4, 7.2, "쉬기"),
                   (7.2, 8.7, "자리 만들기")]),
    ]
    fills = {"떠나기": "#e8ece6", "걷기": KEEP, "쉬기": "#eef0ea", "자리 만들기": "#c9a97a"}
    for i, (label, blocks) in enumerate(days):
        y = 330 + i * 130
        b.append(t(x0 - 16, y + 34, label, 16, INK, "700", anchor="end"))
        for s, e, name in blocks:
            bx = x0 + (x1 - x0) * (s / hours)
            bw = (x1 - x0) * ((e - s) / hours)
            fill = fills[name]
            b.append(f'<rect x="{bx}" y="{y}" width="{bw}" height="56" fill="{fill}" '
                     f'stroke="#ffffff" stroke-width="1.5"/>')
            if bw > 70:
                b.append(t(bx + bw / 2, y + 34, name, 13,
                           "#ffffff" if name == "걷기" else INK))
        sx = x0 + (x1 - x0) * (sunset / hours)
        b.append(f'<line x1="{sx}" y1="{y - 12}" x2="{sx}" y2="{y + 68}" stroke="{MARK}" stroke-width="2.4"/>')
        if i == 0:
            b.append(t(sx + 8, y - 18, "해 짐", 13, MARK, "700", anchor="start"))
            b.append(t(x1 - 4, y + 92, "어두워진 뒤", 13, MARK, "700", anchor="end"))
    b.append(t(x0, 330 + 2 * 130 + 96, "아침 일곱 시", 13, MUTED, anchor="start"))
    b.append(t(x1, 330 + 2 * 130 + 96, "저녁 여섯 시", 13, MUTED, anchor="end"))
    b.append(t(W / 2, 262, "첫날만 자리 만들기가 해 진 뒤까지 갔다", 15, MUTED))
    b.append(note_box(147, 830, 500, "자리를 만드는 데 한 시간 반이 든다"))
    b.append(caption(W / 2, 952, [
        "둘째 날부터는 오후 세 시 반에 걷기를 멈췄다.",
    ], 16))
    return base("하루의 네 토막", "사흘 동안 하루가 나뉜 방식", "".join(b))


# ── p37 사흘의 자리 ───────────────────────────────────────────────────
def fig_three_days():
    b = []
    x0, y0, x1, y1 = 130, 720, 668, 380
    b.append(f'<line x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}" stroke="{KEEP}" stroke-width="3"/>')
    marks = [(0.0, "숲 입구", None), (0.26, "갈림바위", "rock"), (0.56, "마른개천", "creek"),
             (0.88, "능선길", "ridge")]
    pts = {}
    for at, name, kind in marks:
        x = x0 + (x1 - x0) * at
        y = y0 + (y1 - y0) * at
        pts[name] = (x, y)
        if kind == "rock":
            b.append(split_rock(x, y - 30, 0.8, INK))
        elif kind == "creek":
            b.append(f'<path d="M{x - 30},{y - 26} q15,-12 30,0 q15,12 30,0" fill="none" '
                     f'stroke="{LINE}" stroke-width="3"/>')
        elif kind == "ridge":
            b.append(f'<path d="M{x - 34},{y - 14} L{x - 6},{y - 46} L{x + 34},{y - 14}" '
                     f'fill="none" stroke="{LINE}" stroke-width="3"/>')
        b.append(f'<circle cx="{x}" cy="{y}" r="9" fill="{INK}"/>')
        b.append(t(x, y + 30, name, 15, INK, "700"))
    for at, label in ((0.18, "첫날 밤"), (0.5, "둘째 날 밤")):
        x = x0 + (x1 - x0) * at
        y = y0 + (y1 - y0) * at
        b.append(f'<path d="M{x - 20},{y + 58} L{x},{y + 22} L{x + 20},{y + 58} z" '
                 f'fill="#ffffff" stroke="{KEEP}" stroke-width="2"/>')
        b.append(t(x, y + 76, label, 13, KEEP, "700"))
    cx, cy = pts["마른개천"]
    b.append(f'<path d="M{cx + 16},{cy - 6} q60,-10 84,34 q-46,26 -84,-8" fill="none" '
             f'stroke="{DROP}" stroke-width="2.4" stroke-dasharray="6 5"/>')
    b.append(t(cx + 130, cy + 44, "헤맨 한 시간 반", 13, MUTED, anchor="start"))
    rx, ry = pts["능선길"]
    b.append(f'<line x1="{rx + 4}" y1="{ry + 18}" x2="{rx + 34}" y2="{ry + 52}" stroke="{MARK}" '
             f'stroke-width="2.4" marker-end="url(#mark)"/>')
    b.append(t(rx + 44, ry + 66, "셋째 날 여기서 되짚음", 13, MARK, "700", anchor="end"))
    b.append(t(W / 2, 262, "왼쪽 아래가 숲 입구, 오른쪽 위가 능선", 15, MUTED))
    b.append(note_box(147, 880, 500, "지형지물 셋과 잔 자리 둘"))
    b.append(caption(W / 2, 1002, [
        "옅은 점선이 계획에 없던 구간이다.",
    ], 16))
    return base("사흘의 자리", "지나온 지형지물과 잔 자리", "".join(b))


# ── p45 세 가지 조건 ──────────────────────────────────────────────────
def fig_conditions():
    b = []
    b.append(f'<line x1="{W / 2}" y1="270" x2="{W / 2}" y2="880" stroke="{SOFT}" stroke-width="2"/>')
    b.append(t(270, 262, "통하는 숲", 18, KEEP, "700"))
    b.append(t(524, 262, "통하지 않는 숲", 18, MUTED, "700"))
    y = 340
    # 1행 물
    for x, ok in ((270, True), (524, False)):
        c = KEEP if ok else DROP
        if ok:
            for dx in (-50, -14, 22):
                b.append(f'<path d="M{x + dx},{y} q18,40 36,70" fill="none" stroke="{c}" stroke-width="3"/>')
            b.append(f'<path d="M{x + 58},{y + 70} q-10,30 -14,50" fill="none" stroke="{c}" stroke-width="4"/>')
        else:
            for dx, dy in ((-50, 0), (-6, 6), (34, -4)):
                b.append(f'<path d="M{x + dx},{y + dy} q26,34 4,64" fill="none" stroke="{c}" stroke-width="3"/>')
            b.append(f'<ellipse cx="{x + 4}" cy="{y + 96}" rx="52" ry="16" fill="{c}" opacity="0.5"/>')
            b.append(t(x + 4, y + 100, "늪", 12, "#ffffff", "700"))
    b.append(t(270, y + 150, "물이 한 방향", 15, KEEP, "700"))
    b.append(t(524, y + 150, "마지막 답이 없다", 15, MUTED))
    # 2행 능선
    y2 = y + 210
    for x, ok in ((270, True), (524, False)):
        c = KEEP if ok else DROP
        if ok:
            b.append(f'<path d="M{x - 76},{y2 + 60} L{x},{y2} L{x + 76},{y2 + 60}" fill="none" '
                     f'stroke="{c}" stroke-width="3"/>')
        else:
            b.append(f'<path d="M{x - 76},{y2 + 44} L{x - 20},{y2 + 36} L{x + 26},{y2 + 46} '
                     f'L{x + 76},{y2 + 38}" fill="none" stroke="{c}" stroke-width="3"/>')
    b.append(t(270, y2 + 100, "능선이 뚜렷", 15, KEEP, "700"))
    b.append(t(524, y2 + 100, "경사가 기준이 못 된다", 15, MUTED))
    # 3행 기준점
    y3 = y2 + 160
    for x, ok in ((270, True), (524, False)):
        c = KEEP if ok else DROP
        if ok:
            b.append(split_rock(x - 54, y3 + 40, 0.6, c))
            b.append(f'<rect x="{x - 8}" y="{y3 + 20}" width="34" height="34" rx="6" fill="{c}"/>')
            b.append(f'<circle cx="{x + 62}" cy="{y3 + 36}" r="19" fill="{c}"/>')
        else:
            for dx in (-60, -20, 20, 60):
                b.append(f'<line x1="{x + dx}" y1="{y3 + 60}" x2="{x + dx}" y2="{y3 + 14}" '
                         f'stroke="{c}" stroke-width="5"/>')
    b.append(t(270, y3 + 100, "기억할 것이 있다", 15, KEEP, "700"))
    b.append(t(524, y3 + 100, "걸어 둘 것이 없다", 15, MUTED))
    b.append(note_box(147, 900, 500, "셋째 경우에는 들어가지 않는다"))
    b.append(caption(W / 2, 1022, [
        "이 셋은 들어가기 전에 마을에서 물어 알 수 있다.",
    ], 16))
    return base("세 가지 조건", "이 방법이 통하는 숲과 통하지 않는 숲", "".join(b))


FIGURES = {
    7: fig_landmarks,
    13: fig_look_back,
    21: fig_campsite,
    29: fig_day_blocks,
    37: fig_three_days,
    45: fig_conditions,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild084")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
