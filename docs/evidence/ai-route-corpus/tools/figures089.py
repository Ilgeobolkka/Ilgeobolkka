#!/usr/bin/env python3
"""book-089 이미지 페이지 6개의 SVG 생성. figures081~088의 t()/base() 패턴을 그대로 쓴다.

여섯 도표가 모두 '하루의 시간이 어디로 가는가'를 그린다. 머무는 시간은 진한 색, 타고 기다리는 시간은
옅은 색으로 고정하고, 순환선은 늘 원으로 그린다.

사용: python3 figures089.py <출력디렉터리>
"""
import math
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


def ring(cx, cy, r, stops=12):
    """순환선의 원과 정거장 자리. 정거장 좌표를 함께 돌려준다."""
    out = [f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{SOFT}" stroke-width="10"/>']
    pts = []
    for i in range(stops):
        a = math.radians(-90 + 360 * i / stops)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a), a))
    return out, pts


# ── p7 열둘을 셋으로 나누다 ───────────────────────────────────────────
def fig_split_stops():
    b = []
    cx, cy, r = W / 2, 560, 190
    out, pts = ring(cx, cy, r)
    b += out
    grade = {0: "big", 3: "big", 6: "big", 8: "big",
             1: "mid", 2: "mid", 5: "mid", 9: "mid", 11: "mid"}
    for i, (x, y, a) in enumerate(pts):
        kind = grade.get(i, "small")
        if kind == "big":
            b.append(f'<circle cx="{x}" cy="{y}" r="15" fill="{KEEP}"/>')
        elif kind == "mid":
            b.append(f'<circle cx="{x}" cy="{y}" r="9" fill="{LINE}"/>')
        else:
            b.append(f'<circle cx="{x}" cy="{y}" r="5" fill="{DROP}"/>')
        lx = cx + (r + 34) * math.cos(a)
        ly = cy + (r + 34) * math.sin(a) + 5
        b.append(t(lx, ly, f"{i + 1}", 13, MUTED))
        if kind == "big":
            b.append(t(cx + (r + 66) * math.cos(a), cy + (r + 66) * math.sin(a) + 5,
                       "한 시간 반", 11, KEEP, "700"))
    b.append(t(cx, cy - 14, "한 바퀴 오십 분", 17, INK, "700"))
    b.append(t(cx, cy + 16, "배정 여덟 시간 반", 14, MUTED))
    b.append(t(cx, cy + 42, "예비 두 시간", 14, MUTED))
    legend = [("큰 점", "반드시 내릴 넷 · 한 시간 반", KEEP, 15),
              ("중간 점", "지나가며 볼 다섯 · 이십 분", LINE, 9),
              ("작은 점", "잠깐 설 셋 · 오 분", DROP, 5)]
    for k, (name, desc, color, rr) in enumerate(legend):
        y = 812 + k * 30
        b.append(f'<circle cx="200" cy="{y - 5}" r="{rr}" fill="{color}"/>')
        b.append(t(228, y, desc, 14, MUTED, anchor="start"))
    b.append(t(W / 2, 262, "정거장 열둘을 세 부류로 나눈다", 15, MUTED))
    b.append(note_box(147, 900, 500, "나누고 나면 하루가 보인다"))
    b.append(caption(W / 2, 1022, [
        "다 내리면 한 곳에 삼십 분씩밖에 못 있는다.",
    ], 16))
    return base("열둘을 셋으로 나누다", "정거장의 등급과 배정 시간", "".join(b))


# ── p15 타는 시간과 머무는 시간 ───────────────────────────────────────
def fig_time_split():
    b = []
    x0, x1, y = 108, 686, 380
    span = 16.0  # 일곱 시 ~ 밤 열한 시
    blocks = [(0.0, 0.4, "ride"), (0.4, 1.7, "stay_mid"), (1.7, 2.1, "ride"),
              (2.1, 3.6, "stay_big"), (3.6, 4.0, "ride"), (4.0, 4.4, "stay_small"),
              (4.4, 5.0, "ride"), (5.0, 6.5, "stay_big"), (6.5, 7.2, "ride"),
              (7.2, 8.2, "stay_mid"), (8.2, 8.8, "ride"), (8.8, 10.3, "stay_big"),
              (10.3, 11.0, "ride"), (11.0, 12.0, "stay_mid"), (12.0, 12.6, "ride"),
              (12.6, 14.1, "stay_big"), (14.1, 15.0, "ride"), (15.0, 15.6, "stay_small"),
              (15.6, 16.0, "ride")]
    colors = {"ride": "#e3e8e7", "stay_big": KEEP, "stay_mid": "#6f9b90", "stay_small": "#a9c4bc"}
    for s, e, kind in blocks:
        bx = x0 + (x1 - x0) * (s / span)
        bw = (x1 - x0) * ((e - s) / span)
        b.append(f'<rect x="{bx}" y="{y}" width="{bw}" height="90" fill="{colors[kind]}" '
                 f'stroke="#ffffff" stroke-width="1.5"/>')
    b.append(t(x0, y - 16, "아침 일곱 시", 13, MUTED, anchor="start"))
    b.append(t(x1, y - 16, "밤 열한 시", 13, MUTED, anchor="end"))
    lx = x0 + (x1 - x0) * (2.0 / span)
    rx = x0 + (x1 - x0) * (10.0 / span)
    b.append(f'<line x1="{lx}" y1="{y + 104}" x2="{rx}" y2="{y + 104}" stroke="{MARK}" stroke-width="2"/>')
    b.append(t((lx + rx) / 2, y + 126, "낮에는 십이 분 간격", 13, MARK, "700"))
    b.append(f'<rect x="{x0}" y="{y + 170}" width="{(x1 - x0) * 0.594}" height="46" fill="{KEEP}"/>')
    b.append(t(x0 + (x1 - x0) * 0.297, y + 200, "머문 시간 아홉 시간 반", 14, "#ffffff", "700"))
    b.append(f'<rect x="{x0 + (x1 - x0) * 0.594}" y="{y + 170}" width="{(x1 - x0) * 0.406}" '
             f'height="46" fill="#e3e8e7"/>')
    b.append(t(x0 + (x1 - x0) * 0.797, y + 200, "타고 기다린 시간 여섯 시간 반", 13, MUTED))
    b.append(t(W / 2, 262, "진한 칸이 내려서 머문 시간이다", 15, MUTED))
    b.append(note_box(147, 720, 500, "하루권으로 사는 것은 자리가 아니라 시간이다"))
    b.append(caption(W / 2, 842, [
        "여섯 시간 반 가운데 두 시간 반이 기다린 시간이었다.",
    ], 16))
    return base("타는 시간과 머무는 시간", "하루가 나뉜 방식", "".join(b))


# ── p21 같은 구간, 두 시각 ────────────────────────────────────────────
def fig_two_times():
    b = []
    x0, x1 = 130, 664
    rows = [("아침", [1, 1, 2, 3, 3, 2, 1, 1, 1, 2, 1, 1]),
            ("저녁", [1, 1, 1, 1, 2, 2, 2, 3, 3, 2, 1, 1])]
    shades = {1: "#eef1f0", 2: "#a9c4bc", 3: KEEP}
    for i, (label, cells) in enumerate(rows):
        y = 360 + i * 190
        b.append(t(x0 - 16, y + 34, label, 16, INK, "700", anchor="end"))
        cw = (x1 - x0) / 12
        for k, v in enumerate(cells):
            b.append(f'<rect x="{x0 + k * cw}" y="{y}" width="{cw - 3}" height="58" '
                     f'fill="{shades[v]}" stroke="#ffffff"/>')
            if i == 1:
                b.append(t(x0 + k * cw + cw / 2, y + 82, f"{k + 1}", 11, MUTED))
        b.append(t(x1 + 16, y + 34, "육 분 간격", 12, MUTED, anchor="start"))
    b.append(f'<path d="M{x0 + (x1 - x0) * 0.33},{452} q60,60 {(x1 - x0) * 0.33},98" fill="none" '
             f'stroke="{MARK}" stroke-width="2.4" marker-end="url(#mark)"/>')
    b.append(t(W / 2, 500, "붐비는 자리가 옮겨 간다", 13, MARK, "700"))
    b.append(t(x0, 336, "첫째 정거장", 12, MUTED, anchor="start"))
    b.append(t(x1, 336, "열둘째 정거장", 12, MUTED, anchor="end"))
    b.append(t(W / 2, 262, "진할수록 사람이 많았다", 15, MUTED))
    b.append(note_box(147, 780, 500, "같은 구간도 시각에 따라 다른 구간이다"))
    b.append(caption(W / 2, 902, [
        "한 바퀴를 두 번 돌면 열두 구간을 두 번 적게 된다.",
    ], 16))
    return base("같은 구간, 두 시각", "아침과 저녁의 한 바퀴", "".join(b))


# ── p29 네 번을 넘기면 ────────────────────────────────────────────────
def fig_break_even():
    b = []
    x0, y0 = 180, 740
    x1, y1 = 660, 350
    b.append(f'<line x1="{x0}" y1="{y0}" x2="{x1 + 10}" y2="{y0}" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<line x1="{x0}" y1="{y0}" x2="{x0}" y2="{y1 - 20}" stroke="{INK}" stroke-width="2"/>')
    b.append(t(x1 + 10, y0 + 28, "탄 횟수", 13, MUTED, anchor="end"))
    b.append(t(x0 - 10, y1 - 24, "치른 값", 13, MUTED, anchor="end"))
    step_x = (x1 - x0) / 10
    step_y = (y0 - y1) / 10
    pts = []
    for k in range(11):
        pts.append((x0 + step_x * k, y0 - step_y * k))
        b.append(t(x0 + step_x * k, y0 + 24, f"{k}", 11, MUTED))
    path = f"M{pts[0][0]},{pts[0][1]}"
    for k in range(1, 11):
        path += f" L{pts[k][0]},{pts[k - 1][1]} L{pts[k][0]},{pts[k][1]}"
    b.append(f'<path d="{path}" fill="none" stroke="{LINE}" stroke-width="2.6"/>')
    b.append(t(pts[9][0] + 6, pts[9][1] - 16, "낱장", 13, MUTED, anchor="start"))
    flat_y = y0 - step_y * 4
    b.append(f'<line x1="{x0}" y1="{flat_y}" x2="{x1 + 10}" y2="{flat_y}" stroke="{KEEP}" stroke-width="3"/>')
    b.append(t(x1 + 14, flat_y - 10, "하루권", 13, KEEP, "700", anchor="end"))
    b.append(f'<rect x="{pts[4][0]}" y="{y1 - 10}" width="{x1 + 10 - pts[4][0]}" '
             f'height="{flat_y - y1 + 10}" fill="{KEEP}" opacity="0.10"/>')
    b.append(t((pts[4][0] + x1) / 2, y1 + 24, "하루권이 싸다", 14, KEEP, "700"))
    b.append(f'<circle cx="{pts[4][0]}" cy="{flat_y}" r="7" fill="{MARK}"/>')
    b.append(t(pts[4][0], flat_y - 18, "네 번", 13, MARK, "700"))
    b.append(f'<line x1="{pts[9][0]}" y1="{y0}" x2="{pts[9][0]}" y2="{y0 - 18}" stroke="{MARK}" stroke-width="3"/>')
    b.append(t(pts[9][0], y0 + 46, "그날 실제로 탄 횟수", 12, MARK, "700"))
    b.append(t(W / 2, 262, "네 장 값이 하루권 값이다", 15, MUTED))
    b.append(note_box(147, 830, 500, "값보다 크게 달라지는 것은 고민의 수다"))
    b.append(caption(W / 2, 952, [
        "다섯 번째부터는 타는 일에 값이 붙지 않는다.",
    ], 16))
    return base("네 번을 넘기면", "낱장과 하루권의 값", "".join(b))


# ── p37 하루의 한 바퀴 반 ─────────────────────────────────────────────
def fig_day_loop():
    b = []
    cx, cy, r = W / 2, 560, 190
    out, pts = ring(cx, cy, r)
    b += out
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{KEEP}" stroke-width="7" '
             f'stroke-dasharray="{2 * math.pi * r * 0.92} {2 * math.pi * r}" '
             f'transform="rotate(-90 {cx} {cy})"/>')
    got = {2: ("한 시간 반", "big", "07:20"), 5: ("한 시간 반", "big", "09:10"),
           8: ("한 시간 반", "big", "13:40"), 10: ("한 시간 반", "big", "18:00")}
    mids = {0: "07:00", 3: "08:10", 6: "11:20", 7: "12:40", 11: "20:30"}
    for i, (x, y, a) in enumerate(pts):
        if i in got:
            b.append(f'<circle cx="{x}" cy="{y}" r="14" fill="{KEEP}"/>')
        elif i in mids:
            b.append(f'<circle cx="{x}" cy="{y}" r="9" fill="{LINE}"/>')
        else:
            b.append(f'<circle cx="{x}" cy="{y}" r="5" fill="{DROP}"/>')
        lx = cx + (r + 32) * math.cos(a)
        ly = cy + (r + 32) * math.sin(a) + 4
        b.append(t(lx, ly, f"{i + 1}", 12, MUTED))
        label = got.get(i, (None,))[0] if i in got else None
        when = got[i][2] if i in got else mids.get(i)
        if when:
            b.append(t(cx + (r + 60) * math.cos(a), cy + (r + 60) * math.sin(a) + 4, when, 11,
                       KEEP if i in got else MUTED, "700" if i in got else "400"))
        if label:
            b.append(t(cx + (r + 84) * math.cos(a), cy + (r + 84) * math.sin(a) + 4, label, 10, KEEP))
    for i in (7, 9):
        x0v, y0v, _ = pts[i]
        x1v, y1v, _ = pts[i - 1]
        b.append(f'<path d="M{x0v},{y0v} A{r},{r} 0 0 0 {x1v},{y1v}" fill="none" stroke="{MARK}" '
                 f'stroke-width="3" stroke-dasharray="6 5"/>')
    b.append(t(cx, cy - 8, "아홉 번 타고", 16, INK, "700"))
    b.append(t(cx, cy + 20, "네 번 내렸다", 16, INK, "700"))
    b.append(t(cx, cy + 50, "옅은 점선은 반대 방향", 12, MARK))
    b.append(t(W / 2, 262, "굵은 선이 그날 지난 경로다", 15, MUTED))
    b.append(note_box(147, 900, 500, "열둘 가운데 일곱을 봤다"))
    b.append(caption(W / 2, 1022, [
        "한 바퀴 반을 돌며 방향을 두 번 바꿨다.",
    ], 16))
    return base("하루의 한 바퀴 반", "그날의 경로와 시각", "".join(b))


# ── p44 되돌아가는 두 가지 길 ─────────────────────────────────────────
def fig_two_ways():
    b = []
    cx, cy, r = W / 2, 420, 130
    out, pts = ring(cx, cy, r, stops=8)
    b += out
    for i, (x, y, a) in enumerate(pts):
        b.append(f'<circle cx="{x}" cy="{y}" r="7" fill="{INK}"/>')
        b.append(t(cx + (r + 26) * math.cos(a), cy + (r + 26) * math.sin(a) + 4, f"{i + 1}", 12, MUTED))
    sx, sy, _ = pts[2]
    tx, ty, _ = pts[0]
    b.append(f'<circle cx="{sx}" cy="{sy}" r="12" fill="{MARK}"/>')
    b.append(f'<path d="M{sx},{sy} A{r},{r} 0 1 1 {tx},{ty}" fill="none" stroke="{DROP}" '
             f'stroke-width="6"/>')
    b.append(f'<path d="M{sx},{sy} A{r},{r} 0 0 0 {tx},{ty}" fill="none" stroke="{KEEP}" '
             f'stroke-width="6"/>')
    b.append(t(cx, cy + 4, "두 가지 길", 15, INK, "700"))
    b.append(t(cx + r + 24, cy - 70, "반대 방향 · 두 칸", 12, KEEP, "700", anchor="start"))
    b.append(t(cx - r - 24, cy + 30, "계속 돌기 · 여섯 칸", 12, MUTED, anchor="end"))
    ly, lx0, lx1 = 760, 200, 600
    b.append(f'<line x1="{lx0}" y1="{ly}" x2="{lx1}" y2="{ly}" stroke="{SOFT}" stroke-width="10"/>')
    for k in range(8):
        x = lx0 + (lx1 - lx0) * k / 7
        b.append(f'<circle cx="{x}" cy="{ly}" r="7" fill="{INK}"/>')
        b.append(t(x, ly + 28, f"{k + 1}", 12, MUTED))
    sx2 = lx0 + (lx1 - lx0) * 2 / 7
    tx2 = lx0
    b.append(f'<circle cx="{sx2}" cy="{ly}" r="12" fill="{MARK}"/>')
    b.append(f'<path d="M{sx2},{ly - 22} L{tx2},{ly - 22}" fill="none" stroke="{KEEP}" '
             f'stroke-width="6" marker-end="url(#keep)"/>')
    b.append(t((sx2 + tx2) / 2, ly - 44, "길 건너 갈아타기", 12, KEEP, "700"))
    b.append(t(cx, 620, "순환선 · 두 가지 길", 15, INK, "700"))
    b.append(t(cx, 830, "직선 노선 · 한 가지 길", 15, INK, "700"))
    b.append(t(W / 2, 262, "셋째 정거장에서 첫째로 돌아가려면", 15, MUTED))
    b.append(note_box(147, 880, 500, "순환선은 고를 것이 하나 더 있다"))
    b.append(caption(W / 2, 1002, [
        "여섯 칸 이내로 뒤에 있으면 반대 방향이 빠르다.",
    ], 16))
    return base("되돌아가는 두 가지 길", "순환선과 직선 노선", "".join(b))


FIGURES = {
    7: fig_split_stops,
    15: fig_time_split,
    21: fig_two_times,
    29: fig_break_even,
    37: fig_day_loop,
    44: fig_two_ways,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild089")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
