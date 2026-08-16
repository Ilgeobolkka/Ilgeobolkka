#!/usr/bin/env python3
"""book-081 이미지 페이지 6개의 SVG 생성. 색 토큰과 t()·base()는 figure_lib에서 가져온다.

이동을 다루는 책이라 여섯 도표가 모두 '시간 위에서 무엇이 어긋나는가'를 그린다. 시간 축은 늘 왼쪽에서
오른쪽으로, 계획대로 되는 쪽은 진한 색, 어긋나거나 사라지는 쪽은 옅은 색으로 고정해 여섯 도표에서 같은
뜻으로 쓴다.

사용: python3 figures081.py <출력디렉터리>
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from figure_lib import W, INK, LINE, SOFT, MUTED, KEEP, DROP, MARK, caption, make_base, note_box, t

base = make_base()


def boat(cx, cy, scale=1.0, color=KEEP, opacity=1.0):
    """작은 배 모양. 여섯 도표에서 '배 한 편'을 뜻하는 같은 기호로 쓴다."""
    w, h = 26 * scale, 11 * scale
    return (f'<path d="M{cx - w},{cy} L{cx + w},{cy} L{cx + w * 0.62},{cy + h} '
            f'L{cx - w * 0.62},{cy + h} z" fill="{color}" opacity="{opacity}"/>'
            f'<line x1="{cx}" y1="{cy}" x2="{cx}" y2="{cy - h * 1.5}" stroke="{color}" '
            f'stroke-width="{2 * scale}" opacity="{opacity}"/>')


# ── p7 몇 번째로 들르는가 ─────────────────────────────────────────────
def fig_call_order():
    b = []
    x0, x1 = 132, 662
    rows = [
        (330, ["첫물섬", "소금섬", "등대섬", "먹물섬"], 2, "이른 오후"),
        (520, ["첫물섬", "먹물섬", "소금섬", "등대섬"], 3, "저녁"),
    ]
    marks = []
    for y, names, target, when in rows:
        b.append(f'<rect x="{x0}" y="{y}" width="{x1 - x0}" height="46" rx="10" fill="#f2f5f5" stroke="{SOFT}"/>')
        b.append(t(x0 - 8, y + 30, "아침", 14, MUTED, anchor="end"))
        b.append(t(x1 + 8, y + 30, "저녁", 14, MUTED, anchor="start"))
        step = (x1 - x0) / 5
        for i, name in enumerate(names):
            cx = x0 + step * (i + 1)
            hit = i == target
            b.append(f'<circle cx="{cx}" cy="{y + 23}" r="{11 if hit else 7}" '
                     f'fill="{MARK if hit else LINE}"/>')
            b.append(t(cx, y - 12, name, 14, MARK if hit else MUTED, "700" if hit else "400"))
            if hit:
                marks.append((cx, y + 23))
                b.append(t(cx, y + 74, when, 15, MARK, "700"))
    # 아래 줄의 섬 이름과 다른 섬의 점을 지나지 않도록 두 줄 사이로 돌려 오른쪽에서 붙인다.
    (ax, ay), (bx, by) = marks
    mid = 455
    b.append(f'<path d="M{ax},{ay + 18} L{ax},{mid} L{bx + 52},{mid} L{bx + 52},{by} L{bx + 18},{by}" '
             f'fill="none" stroke="{MARK}" stroke-width="1.6" stroke-dasharray="5 5"/>')
    b.append(t((ax + bx) / 2, mid + 26, "같은 섬, 다른 시각", 15, MARK, "700"))
    b.append(t(W / 2, 262, "두 줄은 같은 하루이고 배가 도는 차례만 다르다", 15, MUTED))
    b.append(note_box(147, 700, 500, "몇 번째로 들르는지가 도착 시각이다"))
    b.append(caption(W / 2, 822, [
        "목적지 이름만으로는 언제 닿는지 알 수 없다.",
        "그 배가 그 섬을 몇 번째로 들르는지를 함께 본다.",
    ], 16))
    return base("몇 번째로 들르는가", "같은 섬이 항로의 앞과 뒤에 놓인 두 날", "".join(b))


# ── p14 여유가 버티는 데까지 ──────────────────────────────────────────
def fig_transfer_buffer():
    b = []
    x0 = 140
    arrive = 300           # 앞 배 예정 도착
    spread = 250           # 늦을 수 있는 폭

    def block(top, gate, label, note):
        s = []
        s.append(t(x0 - 12, top + 6, "앞 배", 14, MUTED, anchor="end"))
        s.append(t(x0 - 12, top + 96, "뒤 배", 14, MUTED, anchor="end"))
        s.append(f'<line x1="{x0}" y1="{top}" x2="{662}" y2="{top}" stroke="{SOFT}" stroke-width="2"/>')
        s.append(f'<rect x="{arrive}" y="{top - 13}" width="{spread}" height="26" rx="6" '
                 f'fill="{DROP}" opacity="0.75"/>')
        over = arrive + spread - gate
        if over > 0:
            s.append(f'<rect x="{gate}" y="{top - 13}" width="{over}" height="26" rx="6" fill="{MARK}"/>')
            s.append(t(arrive + spread + 14, top + 6, "여기부터는 놓친다", 14, MARK, "700", anchor="start"))
        s.append(f'<circle cx="{arrive}" cy="{top}" r="8" fill="#ffffff" stroke="{INK}" stroke-width="2"/>')
        s.append(t(arrive, top - 26, "예정 도착", 14, MUTED))
        s.append(f'<line x1="{gate}" y1="{top - 40}" x2="{gate}" y2="{top + 108}" stroke="{KEEP}" stroke-width="3"/>')
        s.append(t(gate, top + 128, "뒤 배 출항", 15, KEEP, "700"))
        s.append(f'<line x1="{gate}" y1="{top + 96}" x2="662" y2="{top + 96}" stroke="{KEEP}" stroke-width="2"/>')
        s.append(boat(gate + 46, top + 88, 0.62, KEEP))
        s.append(f'<line x1="{arrive}" y1="{top + 52}" x2="{gate}" y2="{top + 52}" stroke="{KEEP}" '
                 f'stroke-width="2" marker-end="url(#keep)"/>')
        s.append(t((arrive + gate) / 2, top + 44, label, 15, KEEP, "700"))
        s.append(t(x0, top + 172, note, 15, MUTED, anchor="start"))
        return "".join(s)

    b.append(t(W / 2, 250, "가로는 시간이고 옅은 띠는 앞 배가 늦을 수 있는 폭이다", 15, MUTED))
    b.append(block(320, 460, "환승 여유", "여유가 짧으면 늦어진 끝이 출항 시각을 넘는다"))
    b.append(block(640, 600, "여유를 두 배로", "여유를 늘리면 넘는 부분이 사라진다"))
    b.append(note_box(147, 880, 500, "여유는 걷는 시간이 아니라 늦어질 폭이다"))
    b.append(caption(W / 2, 1002, [
        "앞 구간이 길수록, 바다가 거칠수록 옅은 띠가 길어진다.",
    ], 16))
    return base("여유가 버티는 데까지", "앞 배의 지연과 뒤 배의 출항이 만나는 자리", "".join(b))


# ── p21 어느 배가 먼저 멈추는가 ───────────────────────────────────────
def fig_suspension():
    b = []
    ax, ay0, ay1 = 250, 860, 300
    b.append(f'<line x1="{ax}" y1="{ay0}" x2="{ax}" y2="{ay1}" stroke="{INK}" stroke-width="2.4" '
             f'marker-end="url(#gray)"/>')
    b.append(t(ax - 16, ay0 + 6, "잔잔함", 15, MUTED, anchor="end"))
    b.append(t(ax - 16, ay1 - 4, "거침", 15, MUTED, anchor="end"))
    right = 560
    bands = [(620, 760, "여객선은 뜨지만 옮겨 타기는 안 되는 날"),
             (450, 620, "여객선은 멈추고 짐배만 나가는 날")]
    for top, bottom, label in bands:
        b.append(f'<rect x="{ax}" y="{top}" width="{right - ax}" height="{bottom - top}" '
                 f'fill="{DROP}" opacity="0.35"/>')
        b.append(t((ax + right) / 2, (top + bottom) / 2 + 6, label, 15, MUTED))
    # 한계선의 이름은 오른쪽 끝에 배 기호와 함께 둔다. 선 위에 얹으면 띠 설명과 겹친다.
    lines = [(760, "작은 배로 옮겨 타는 섬"), (620, "여객선"), (450, "짐배")]
    for y, label in lines:
        b.append(f'<line x1="{ax}" y1="{y}" x2="{right}" y2="{y}" stroke="{INK}" stroke-width="2"/>')
        b.append(boat(right + 30, y - 6, 0.62, KEEP))
        b.append(t(right + 56, y + 2, label, 15, INK, "700", anchor="start"))
        b.append(f'<line x1="{ax + 26}" y1="{y + 10}" x2="{ax + 26}" y2="{y + 46}" stroke="{LINE}" '
                 f'stroke-width="1.6" marker-end="url(#gray)"/>')
        b.append(t(ax + 34, y + 34, "이 아래에서만 다닌다", 13, MUTED, anchor="start"))
    b.append(t(W / 2, 262, "선 아래에서만 그 배가 다닌다", 15, MUTED))
    b.append(note_box(147, 900, 500, "같은 바다에서도 멈추는 높이가 다르다"))
    b.append(caption(W / 2, 1022, [
        "결항은 하루가 통째로 막히는 일이 아니다.",
    ], 16))
    return base("어느 배가 먼저 멈추는가", "같은 바다에서 배마다 다른 운항 한계", "".join(b))


# ── p30 한 손이 남는가 ────────────────────────────────────────────────
def fig_baggage():
    b = []
    rail_y0, rail_y1 = 330, 640
    people = [
        (190, "두 손이 빈다", 0, True),
        (397, "한 손만 남는다", 1, False),
        (604, "손이 없다", 2, False),
    ]
    b.append(f'<line x1="120" y1="{rail_y0}" x2="674" y2="{rail_y0}" stroke="{SOFT}" stroke-width="2"/>')
    b.append(t(W / 2, rail_y0 - 18, "난간을 잡아야 하는 구간", 15, MUTED))
    for cx, label, load, ok in people:
        color = KEEP if ok else DROP
        # 사다리. 난간을 잡을 수 있는 쪽만 난간을 진하게 그린다.
        rail = KEEP if ok else SOFT
        b.append(f'<line x1="{cx - 26}" y1="{rail_y0}" x2="{cx - 26}" y2="{rail_y1}" stroke="{rail}" stroke-width="3"/>')
        b.append(f'<line x1="{cx + 26}" y1="{rail_y0}" x2="{cx + 26}" y2="{rail_y1}" stroke="{rail}" stroke-width="3"/>')
        for k in range(4):
            y = rail_y0 + 62 + k * 62
            b.append(f'<line x1="{cx - 26}" y1="{y}" x2="{cx + 26}" y2="{y}" stroke="{SOFT}" stroke-width="3"/>')
        # 사람
        b.append(f'<circle cx="{cx}" cy="{rail_y0 + 96}" r="17" fill="{color}"/>')
        b.append(f'<line x1="{cx}" y1="{rail_y0 + 113}" x2="{cx}" y2="{rail_y0 + 186}" stroke="{color}" stroke-width="7"/>')
        b.append(f'<line x1="{cx}" y1="{rail_y0 + 186}" x2="{cx - 20}" y2="{rail_y0 + 236}" stroke="{color}" stroke-width="6"/>')
        b.append(f'<line x1="{cx}" y1="{rail_y0 + 186}" x2="{cx + 20}" y2="{rail_y0 + 236}" stroke="{color}" stroke-width="6"/>')
        # 짐
        if load == 0:
            b.append(f'<rect x="{cx - 12}" y="{rail_y0 + 116}" width="24" height="30" rx="5" fill="{color}" opacity="0.65"/>')
            b.append(f'<line x1="{cx}" y1="{rail_y0 + 128}" x2="{cx - 34}" y2="{rail_y0 + 104}" stroke="{color}" stroke-width="5"/>')
            b.append(f'<line x1="{cx}" y1="{rail_y0 + 128}" x2="{cx + 34}" y2="{rail_y0 + 104}" stroke="{color}" stroke-width="5"/>')
        elif load == 1:
            b.append(f'<rect x="{cx - 16}" y="{rail_y0 + 112}" width="32" height="48" rx="6" fill="{color}" opacity="0.65"/>')
            b.append(f'<line x1="{cx}" y1="{rail_y0 + 128}" x2="{cx - 34}" y2="{rail_y0 + 104}" stroke="{color}" stroke-width="5"/>')
            b.append(f'<line x1="{cx}" y1="{rail_y0 + 130}" x2="{cx + 30}" y2="{rail_y0 + 168}" stroke="{color}" stroke-width="5"/>')
            b.append(f'<rect x="{cx + 20}" y="{rail_y0 + 168}" width="26" height="30" rx="4" fill="{color}" opacity="0.65"/>')
        else:
            b.append(f'<line x1="{cx}" y1="{rail_y0 + 130}" x2="{cx - 30}" y2="{rail_y0 + 160}" stroke="{color}" stroke-width="5"/>')
            b.append(f'<line x1="{cx}" y1="{rail_y0 + 130}" x2="{cx + 30}" y2="{rail_y0 + 160}" stroke="{color}" stroke-width="5"/>')
            b.append(f'<rect x="{cx - 34}" y="{rail_y0 + 158}" width="68" height="52" rx="6" fill="{color}" opacity="0.65"/>')
        b.append(t(cx, rail_y1 + 46, label, 16, INK if ok else MUTED, "700" if ok else "400"))
    b.append(t(W / 2, 262, "셋 모두 같은 사다리를 오른다", 15, MUTED))
    b.append(note_box(147, 740, 500, "짐은 무게가 아니라 남는 손으로 센다"))
    b.append(caption(W / 2, 862, [
        "난간을 잡을 손이 없으면 오르내리는 동안 붙잡을 것이 없다.",
        "환승이 잦은 여정에서 이 한 가지가 짐의 크기를 정한다.",
    ], 16))
    return base("한 손이 남는가", "좁은 사다리를 오르는 세 가지 짐", "".join(b))


# ── p37 열흘 동안 지난 자리 ───────────────────────────────────────────
def fig_route_log():
    b = []
    x0, x1, y = 118, 676, 380
    names = ["첫물섬", "등대섬", "소금섬", "먹물섬", "그물섬", "돌담섬", "끝물섬"]
    b.append(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="{LINE}" stroke-width="2"/>')
    step = (x1 - x0) / (len(names) - 1)
    for i, name in enumerate(names):
        cx = x0 + step * i
        stuck = name == "먹물섬"
        b.append(f'<circle cx="{cx}" cy="{y}" r="{20 if stuck else 12}" fill="{MARK if stuck else KEEP}"/>')
        b.append(t(cx, y - (34 if stuck else 26), name, 14, INK, "700" if stuck else "400"))
        if stuck:
            b.append(t(cx, y + 46, "묶인 이틀", 14, MARK, "700"))
        if i:
            b.append(boat(cx - step / 2, y - 8, 0.62, LINE))
    b.append(t(W / 2, 300, "왼쪽에서 오른쪽으로 지나온 차례", 15, MUTED))

    gy = 560
    b.append(t(x0, gy - 22, "잔 곳과 비워 둔 날", 15, MUTED, anchor="start"))
    slots = ["첫물섬", "등대섬", "등대섬", "예비", "소금섬", "먹물섬", "먹물섬", "예비", "돌담섬", "끝물섬"]
    bw = (x1 - x0 - 9 * 8) / 10
    for i, label in enumerate(slots):
        bx = x0 + i * (bw + 8)
        spare = label == "예비"
        fill = "#ffffff" if spare else KEEP
        b.append(f'<rect x="{bx}" y="{gy}" width="{bw}" height="52" rx="7" fill="{fill}" '
                 f'stroke="{MARK if spare else KEEP}" stroke-width="{3 if spare else 1.5}"/>')
        b.append(t(bx + bw / 2, gy + 78, label if spare else "", 13, MARK, "700"))
    b.append(t(W / 2, gy + 116, "섬은 일곱, 밤은 열", 16, MUTED))
    b.append(note_box(147, 720, 500, "계획한 자리와 바다가 바꾼 자리"))
    b.append(caption(W / 2, 842, [
        "예비일 둘 가운데 앞의 하나는 그대로 쓰였다.",
        "먹물섬의 이틀은 계획에 없던 이틀이다.",
    ], 16))
    return base("열흘 동안 지난 자리", "일곱 섬을 건넌 순서와 묵은 밤", "".join(b))


# ── p45 같은 항로의 두 계절 ───────────────────────────────────────────
def fig_seasons():
    b = []
    b.append(t(W / 2, 250, "첫물섬과 등대섬을 잇는 같은 항로", 15, MUTED))
    b.append(f'<line x1="{W / 2}" y1="280" x2="{W / 2}" y2="980" stroke="{SOFT}" stroke-width="2"/>')
    cols = [
        (222, "여름", 4, ["이른 아침", "저녁"], [3, 7, 12, 18, 24]),
        (572, "겨울", 2, ["이른 아침", "늦은 오후"], [1, 2, 3, 8, 9, 10, 11, 16, 17, 18, 22, 23, 24, 25]),
    ]
    for cx, label, boats, edges, closed in cols:
        b.append(t(cx, 312, label, 22, INK, "700"))
        for i in range(boats):
            b.append(boat(cx, 380 + i * 52, 1.0, KEEP))
        b.append(t(cx, 380 + 4 * 52 + 18, f"첫 편 {edges[0]}", 15, MUTED))
        b.append(t(cx, 380 + 4 * 52 + 44, f"마지막 편 {edges[1]}", 15, MUTED))
        gy = 690
        b.append(t(cx, gy - 16, "한 달 가운데 못 뜬 날", 14, MUTED))
        for k in range(30):
            row, col = divmod(k, 6)
            bx = cx - 111 + col * 38
            by = gy + row * 38
            on = k in closed
            b.append(f'<rect x="{bx}" y="{by}" width="30" height="30" rx="5" '
                     f'fill="{MARK if on else "#ffffff"}" stroke="{MARK if on else SOFT}" stroke-width="1.6"/>')
        b.append(t(cx, gy + 5 * 38 + 34, f"{len(closed)}일", 16, MARK, "700"))
    b.append(note_box(147, 940, 500, "표가 줄고 못 뜨는 날이 는다"))
    b.append(caption(W / 2, 1062, [
        "옮겨 쓸 것은 시각이 아니라 시각을 확인하는 순서다.",
    ], 16))
    return base("같은 항로의 두 계절", "계절에 따라 달라지는 편수와 결항", "".join(b))


FIGURES = {
    7: fig_call_order,
    14: fig_transfer_buffer,
    21: fig_suspension,
    30: fig_baggage,
    37: fig_route_log,
    45: fig_seasons,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild081")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
