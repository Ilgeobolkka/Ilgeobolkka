#!/usr/bin/env python3
"""book-082 이미지 페이지 6개의 SVG 생성. 색 토큰과 t()·base()는 figure_lib에서 가져온다.

여섯 도표가 모두 '시간과 거리 위에서 무엇이 쌓이거나 사라지는가'를 그린다. 시간 축은 왼쪽에서
오른쪽 또는 위에서 아래로 두고, 계획대로 되는 쪽은 진한 색, 잃거나 줄어드는 쪽은 옅은 색으로
고정해 여섯 도표에서 같은 뜻으로 쓴다.

사용: python3 figures082.py <출력디렉터리>
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from figure_lib import W, INK, LINE, SOFT, MUTED, KEEP, DROP, MARK, caption, make_base, note_box, t

base = make_base()


def train(cx, cy, scale=1.0, color=KEEP, opacity=1.0):
    """작은 기차 기호. 여섯 도표에서 '한 편'을 뜻하는 같은 기호로 쓴다."""
    w, h = 30 * scale, 14 * scale
    return (f'<rect x="{cx - w}" y="{cy - h / 2}" width="{w * 2}" height="{h}" rx="{4 * scale}" '
            f'fill="{color}" opacity="{opacity}"/>'
            f'<rect x="{cx + w * 0.55}" y="{cy - h}" width="{w * 0.45}" height="{h / 2}" '
            f'fill="{color}" opacity="{opacity}"/>')


# ── p7 서는 곳과 지나치는 곳 ──────────────────────────────────────────
def fig_stops():
    b = []
    y0, y1 = 300, 800
    names = ["자작나무역", "", "밀밭역", "", "안개고개역", "돌다리역", "", "경계역"]
    express = {0, 4, 7}
    step = (y1 - y0) / (len(names) - 1)
    lx, rx = 300, 500
    for label, x, stops, title in ((None, lx, express, "급행"), (None, rx, set(range(8)), "완행")):
        b.append(f'<line x1="{x}" y1="{y0 - 34}" x2="{x}" y2="{y1 + 34}" stroke="{LINE}" stroke-width="2"/>')
        b.append(t(x, y0 - 52, title, 18, INK, "700"))
        for i in range(len(names)):
            y = y0 + step * i
            if i in stops:
                b.append(f'<line x1="{x - 16}" y1="{y}" x2="{x + 16}" y2="{y}" stroke="{KEEP}" stroke-width="5"/>')
            else:
                b.append(f'<circle cx="{x}" cy="{y}" r="3" fill="{DROP}"/>')
    for i, label in enumerate(names):
        y = y0 + step * i
        if label:
            b.append(t(lx - 40, y + 5, label, 15, INK, anchor="end"))
        else:
            b.append(f'<circle cx="{lx - 52}" cy="{y}" r="3.5" fill="{LINE}"/>')
    b.append(t(lx, y1 + 62, "다섯 시간", 18, MARK, "700"))
    b.append(t(rx, y1 + 62, "아홉 시간", 18, MARK, "700"))
    b.append(f'<line x1="{lx + 62}" y1="{y1 + 56}" x2="{rx - 66}" y2="{y1 + 56}" stroke="{MARK}" '
             f'stroke-width="1.8" marker-end="url(#mark)"/>')
    b.append(t(W / 2, 232, "같은 여덟 역을 두 기차가 지난다", 15, MUTED))
    b.append(note_box(147, 900, 500, "차이는 속도가 아니라 서는 횟수"))
    b.append(caption(W / 2, 1022, [
        "굵은 가로선은 그 역에 선다는 뜻이고 점은 지나친다는 뜻이다.",
    ], 16))
    return base("서는 곳과 지나치는 곳", "같은 구간을 지나는 급행과 완행", "".join(b))


# ── p13 아홉 시간의 띠 ────────────────────────────────────────────────
def fig_nine_hours():
    b = []
    x0, x1 = 108, 686
    y = 330
    segs = [("밭", 0, 2.2, "#e8ece6"), ("호수", 2.2, 4.6, "#dde7ec"),
            ("숲과 고개", 4.6, 6.8, "#e4e8dd"), ("마을", 6.8, 9, "#efe9e0")]
    unit = (x1 - x0) / 9
    for label, s, e, fill in segs:
        b.append(f'<rect x="{x0 + s * unit}" y="{y}" width="{(e - s) * unit}" height="96" '
                 f'fill="{fill}" stroke="{SOFT}"/>')
        b.append(t(x0 + (s + e) / 2 * unit, y + 56, label, 17, INK, "600"))
    for hour in range(10):
        x = x0 + hour * unit
        b.append(f'<line x1="{x}" y1="{y + 96}" x2="{x}" y2="{y + 108}" stroke="{LINE}"/>')
    b.append(t(x0, y - 18, "출발", 15, MUTED, anchor="start"))
    b.append(t(x1, y - 18, "도착", 15, MUTED, anchor="end"))
    b.append(t(W / 2, y + 132, "한 칸이 한 시간", 14, MUTED))
    for at, kind in ((2.2, "bridge"), (4.6, "hill"), (6.8, "bridge")):
        x = x0 + at * unit
        b.append(f'<line x1="{x}" y1="{y - 8}" x2="{x}" y2="{y + 104}" stroke="{MARK}" stroke-width="3"/>')
        if kind == "bridge":
            b.append(f'<path d="M{x - 16},{y - 22} Q{x},{y - 42} {x + 16},{y - 22}" fill="none" '
                     f'stroke="{MARK}" stroke-width="2.4"/>')
        else:
            b.append(f'<path d="M{x - 18},{y - 20} L{x},{y - 44} L{x + 18},{y - 20} z" fill="{MARK}"/>')
    ty = y + 190
    b.append(t(x0, ty - 18, "보이지 않는 시간", 15, MUTED, anchor="start"))
    for s, e in ((1.4, 1.7), (5.0, 5.4), (5.8, 6.0), (7.4, 7.7)):
        b.append(f'<rect x="{x0 + s * unit}" y="{ty}" width="{(e - s) * unit}" height="48" fill="{INK}"/>')
    b.append(f'<line x1="{x0}" y1="{ty + 48}" x2="{x1}" y2="{ty + 48}" stroke="{SOFT}" stroke-width="2"/>')
    b.append(t(W / 2, ty + 84, "터널 넷", 15, MUTED))
    b.append(t(W / 2, 262, "다리와 고개에서 창밖이 한 번에 바뀐다", 15, MUTED))
    b.append(note_box(147, 720, 500, "바뀌는 자리는 대개 다리와 고개다"))
    b.append(caption(W / 2, 842, [
        "네 구간의 경계 셋 가운데 둘이 다리, 하나가 고개였다.",
        "터널 구간은 관찰이 아니라 정리에 배정한다.",
    ], 16))
    return base("아홉 시간의 띠", "한 편이 지나는 하루의 구간", "".join(b))


# ── p21 오 분이 이십 분이 되는 동안 ───────────────────────────────────
def fig_delay():
    b = []
    x0, x1 = 150, 660
    ybase, ytop = 720, 340
    steps = [(0, 5, ""), (1, 6, ""), (2, 11, "교행 대기"), (3, 12, ""),
             (4, 13, ""), (5, 18, "교행 대기"), (6, 19, ""), (7, 20, "화물 취급")]
    xs = [x0 + (x1 - x0) / 7 * i for i in range(8)]
    ys = [ybase - (ybase - ytop) * (m / 20) for _, m, _ in steps]
    b.append(f'<line x1="{x0 - 30}" y1="{ybase}" x2="{x1 + 20}" y2="{ybase}" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<line x1="{x0 - 30}" y1="{ybase}" x2="{x0 - 30}" y2="{ytop - 30}" stroke="{INK}" stroke-width="2"/>')
    for minute in (5, 10, 15, 20):
        y = ybase - (ybase - ytop) * (minute / 20)
        b.append(f'<line x1="{x0 - 36}" y1="{y}" x2="{x1 + 20}" y2="{y}" stroke="{SOFT}"/>')
        b.append(t(x0 - 44, y + 5, f"{minute}분", 14, MUTED, anchor="end"))
    points = " ".join(f"{x},{y}" for x, y in zip(xs, ys))
    b.append(f'<polyline points="{points}" fill="none" stroke="{MARK}" stroke-width="3"/>')
    b.append(f'<polygon points="{points} {xs[-1]},{ybase} {xs[0]},{ybase}" fill="{MARK}" opacity="0.12"/>')
    for i, ((_, minute, label), x, y) in enumerate(zip(steps, xs, ys)):
        b.append(f'<circle cx="{x}" cy="{y}" r="5" fill="{MARK}"/>')
        b.append(t(x, ybase + 26, f"역 {i + 1}", 13, MUTED))
        if label:
            b.append(t(x, y - 18, label, 13, INK, "700"))
    b.append(t(xs[-1] - 10, ys[-1] + 30, "이십 분", 16, MARK, "700", anchor="end"))
    b.append(t(xs[0] + 10, ys[0] - 16, "오 분", 14, MUTED, anchor="start"))
    b.append(t(W / 2, 262, "출발할 때 오 분 늦은 기차가 종점에서", 15, MUTED))
    b.append(note_box(147, 830, 500, "지연은 역마다 조금씩 붙는다"))
    b.append(caption(W / 2, 952, [
        "크게 꺾이는 세 자리가 교행 대기와 화물 취급이다.",
    ], 16))
    return base("오 분이 이십 분이 되는 동안", "역을 지나며 쌓이는 지연", "".join(b))


# ── p30 십 분은 몇 분인가 ─────────────────────────────────────────────
def fig_transfer():
    b = []
    x0, x1 = 130, 664
    w = x1 - x0
    b.append(t(x0, 300, "표에 적힌 십 분", 17, INK, "700", anchor="start"))
    b.append(f'<rect x="{x0}" y="{320}" width="{w}" height="64" rx="8" fill="{KEEP}" opacity="0.85"/>')
    b.append(t(x0, 460, "실제로 남는 시간", 17, INK, "700", anchor="start"))
    parts = [("내리는 데 일 분", 1), ("승강장 건너기 오 분", 5), ("표와 자리 확인 이 분", 2),
             ("남는 시간 이 분", 2)]
    x = x0
    for i, (label, minutes) in enumerate(parts):
        seg = w * minutes / 10
        last = i == len(parts) - 1
        b.append(f'<rect x="{x}" y="480" width="{seg}" height="64" rx="8" '
                 f'fill="{KEEP if last else DROP}" opacity="{1 if last else 0.75}"/>')
        b.append(t(x + seg / 2, 566 + (26 if i % 2 else 0), label, 13,
                   INK if last else MUTED, "700" if last else "400"))
        x += seg
    green_center = x0 + w * 0.9
    b.append(t(x1, 446, "앞 기차가 늦으면 여기가 먼저 줄어든다", 13, MARK, "700", anchor="end"))
    b.append(f'<line x1="{green_center}" y1="{456}" x2="{green_center}" y2="{474}" stroke="{MARK}" '
             f'stroke-width="2.4" marker-end="url(#mark)"/>')
    b.append(t(W / 2, 262, "같은 길이를 무엇이 채우는가", 15, MUTED))
    b.append(note_box(147, 680, 500, "남는 시간은 표에 적히지 않는다"))
    b.append(caption(W / 2, 802, [
        "승강장이 여럿인 큰 역에서는 건너는 시간이 절반을 가져간다.",
        "그래서 큰 역의 환승 여유는 이십 분으로 잡는다.",
    ], 16))
    return base("십 분은 몇 분인가", "표에 적힌 환승 시간과 실제로 남는 시간", "".join(b))


# ── p37 한 바퀴를 넷으로 끊다 ─────────────────────────────────────────
def fig_loop():
    import math
    b = []
    cx, cy, r = W / 2, 540, 186
    names = ["자작나무역", "밀밭역", "안개고개역", "돌다리역", "경계역"]
    days = ["첫날 두 시간", "이틀째 세 시간", "사흘째 두 시간", "나흘째 두 시간"]
    pts = []
    for i in range(5):
        a = -math.pi / 2 + 2 * math.pi * i / 5
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a), a))
    for i in range(5):
        x1c, y1c, _ = pts[i]
        x2c, y2c, _ = pts[(i + 1) % 5]
        ridden = i < 4
        dash = "" if ridden else ' stroke-dasharray="7 7"'
        b.append(f'<path d="M{x1c},{y1c} A{r},{r} 0 0 1 {x2c},{y2c}" fill="none" '
                 f'stroke="{KEEP if ridden else DROP}" stroke-width="{5 if ridden else 3}"{dash}/>')
        mid = -math.pi / 2 + 2 * math.pi * (i + 0.5) / 5
        label = days[i] if ridden else "타지 않은 구간"
        b.append(t(cx + (r + 84) * math.cos(mid), cy + (r + 84) * math.sin(mid) + 5,
                   label, 14, INK if ridden else MUTED, "700" if ridden else "400"))
    for (x, y, a), name in zip(pts, names):
        walked = name in ("안개고개역", "돌다리역")
        b.append(f'<circle cx="{x}" cy="{y}" r="{12 if walked else 9}" fill="{MARK if walked else INK}"/>')
        lx = cx + (r + 32) * math.cos(a)
        ly = cy + (r + 32) * math.sin(a) + 5
        b.append(t(lx, ly, name, 15, INK, "700" if walked else "400"))
        if walked:
            b.append(t(lx, ly + 20, "걸어 나감", 12, MARK, "700"))
    b.append(t(cx, cy - 8, "아홉 시간을", 17, MUTED))
    b.append(t(cx, cy + 20, "나흘로", 17, MUTED))
    b.append(t(W / 2, 240, "진한 호가 그날 탄 구간이다", 15, MUTED))
    b.append(note_box(147, 900, 500, "이동은 오전, 오후는 걷기"))
    b.append(caption(W / 2, 1022, [
        "점선 구간은 나흘 안에 타지 않은 구간이다.",
    ], 16))
    return base("한 바퀴를 넷으로 끊다", "순환선 일주를 나흘로 나눈 자리", "".join(b))


# ── p45 다섯 편과 세 편 ───────────────────────────────────────────────
def fig_seasons():
    b = []
    b.append(t(W / 2, 250, "편수만 다른 북부 순환선", 15, MUTED))
    b.append(f'<line x1="{W / 2}" y1="280" x2="{W / 2}" y2="900" stroke="{SOFT}" stroke-width="2"/>')
    for cx, label, count, gap in ((222, "여름", 5, "두 시간"), (572, "겨울", 3, "네 시간")):
        b.append(t(cx, 312, label, 22, INK, "700"))
        top, bottom = 360, 720
        b.append(f'<line x1="{cx}" y1="{top}" x2="{cx}" y2="{bottom}" stroke="{SOFT}" stroke-width="3"/>')
        step = (bottom - top) / (count - 1)
        for i in range(count):
            y = top + step * i
            b.append(train(cx, y, 0.72, KEEP))
            if i:
                b.append(t(cx + 62, y - step / 2 + 5, gap, 13, MUTED, anchor="start"))
        b.append(t(cx, bottom + 46, f"하루 {'다섯' if count == 5 else '세'} 편", 16, INK, "700"))
        wait = "세 시간 걷고 다음 편" if count == 5 else "세 시간 걷고 두 시간 더 기다림"
        b.append(f'<circle cx="{cx - 96}" cy="{bottom + 96}" r="12" fill="{MARK if count == 3 else KEEP}"/>')
        b.append(t(cx + 8, bottom + 102, wait, 14, INK if count == 5 else MARK,
                   "400" if count == 5 else "700"))
    b.append(note_box(147, 930, 500, "편수가 줄면 내리는 일이 먼저 어려워진다"))
    b.append(caption(W / 2, 1052, [
        "옮겨 쓸 것은 편수가 아니라 확인하는 순서다.",
    ], 16))
    return base("다섯 편과 세 편", "계절에 따라 달라지는 편수와 간격", "".join(b))


FIGURES = {
    7: fig_stops,
    13: fig_nine_hours,
    21: fig_delay,
    30: fig_transfer,
    37: fig_loop,
    45: fig_seasons,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild082")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
