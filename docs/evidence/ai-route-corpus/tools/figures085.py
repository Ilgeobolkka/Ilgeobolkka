#!/usr/bin/env python3
"""book-085 이미지 페이지 6개의 SVG 생성. figures081~084의 t()/base() 패턴을 그대로 쓴다.

여섯 도표가 모두 '바람이 어디서 어떻게 오는가'를 그린다. 바람은 늘 굵은 화살표로, 갈 수 있는 쪽은
진한 색, 못 가거나 피해야 하는 쪽은 옅은 색으로 고정한다.

사용: python3 figures085.py <출력디렉터리>
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
SEA = "#9fc0cc"


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
  <marker id="wind" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{SEA}"/></marker>
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


def bike(cx, cy, s=1.0, color=INK):
    r = 9 * s
    return (f'<circle cx="{cx - 16 * s}" cy="{cy}" r="{r}" fill="none" stroke="{color}" stroke-width="{2.4 * s}"/>'
            f'<circle cx="{cx + 16 * s}" cy="{cy}" r="{r}" fill="none" stroke="{color}" stroke-width="{2.4 * s}"/>'
            f'<path d="M{cx - 16 * s},{cy} L{cx - 2 * s},{cy - 16 * s} L{cx + 16 * s},{cy} '
            f'M{cx - 2 * s},{cy - 16 * s} L{cx + 8 * s},{cy - 20 * s}" fill="none" stroke="{color}" '
            f'stroke-width="{2.4 * s}"/>')


def wind_arrows(x0, y0, count, dx, dy, gap, color=SEA, width=3.0):
    out = []
    for i in range(count):
        sx = x0 + (0 if dy else 0) + (i * gap if dy else 0)
        sy = y0 + (i * gap if dx else 0)
        out.append(f'<line x1="{sx}" y1="{sy}" x2="{sx + dx}" y2="{sy + dy}" stroke="{color}" '
                   f'stroke-width="{width}" marker-end="url(#wind)"/>')
    return "".join(out)


# ── p9 어디로 달릴 것인가 ─────────────────────────────────────────────
def fig_surface():
    b = []
    b.append(f'<path d="M150,760 Q150,470 400,430 Q640,392 660,300" fill="none" stroke="{INK}" '
             f'stroke-width="64" stroke-linecap="round" opacity="0.10"/>')
    b.append(f'<path d="M150,760 Q150,470 400,430 Q640,392 660,300" fill="none" stroke="{DROP}" '
             f'stroke-width="52" stroke-linecap="round" stroke-dasharray="3 6" opacity="0.55"/>')
    b.append(f'<path d="M150,760 Q150,470 400,430 Q640,392 660,300" fill="none" stroke="{KEEP}" '
             f'stroke-width="16" stroke-linecap="round"/>')
    b.append(t(404, 386, "달리는 자리", 15, KEEP, "700"))
    b.append(t(250, 560, "바람이 올린 모래", 14, MUTED, anchor="start"))
    b.append(f'<line x1="246" y1="554" x2="212" y2="524" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(t(560, 470, "파인 자리", 14, MUTED, anchor="start"))
    b.append(f'<line x1="556" y1="464" x2="516" y2="446" stroke="{LINE}" stroke-width="1.6"/>')
    for k in range(4):
        b.append(f'<path d="M{600 + k * 24},{700 - k * 10} q16,-10 32,0 q16,10 32,0" fill="none" '
                 f'stroke="{SEA}" stroke-width="3"/>')
    b.append(t(690, 740, "바다", 15, MUTED, anchor="end"))
    b.append(bike(400, 424, 1.1, INK))
    b.append(f'<path d="M300,470 q60,-30 120,-24" fill="none" stroke="{DROP}" stroke-width="3" '
             f'stroke-dasharray="6 5"/>')
    b.append(f'<line x1="352" y1="440" x2="380" y2="468" stroke="{MARK}" stroke-width="3"/>')
    b.append(f'<line x1="380" y1="440" x2="352" y2="468" stroke="{MARK}" stroke-width="3"/>')
    b.append(t(300, 500, "안쪽으로 붙지 않는다", 13, MARK, "700", anchor="start"))
    b.append(t(W / 2, 262, "굽이 안쪽에는 모래, 바깥쪽 갓길에는 파임", 15, MUTED))
    b.append(note_box(147, 830, 500, "갓길 안쪽 반 뼘을 비워 둔다"))
    b.append(caption(W / 2, 952, [
        "모래가 쌓인 자리는 색이 밝아 멀리서도 보인다.",
    ], 16))
    return base("어디로 달릴 것인가", "굽이에서 피할 자리와 달릴 자리", "".join(b))


# ── p14 굽이를 돌면 바람이 바뀐다 ─────────────────────────────────────
def fig_crosswind():
    b = []
    b.append(f'<path d="M170,760 Q170,520 400,470 Q630,420 640,300" fill="none" stroke="{LINE}" '
             f'stroke-width="34" stroke-linecap="round" opacity="0.35"/>')
    b.append(wind_arrows(96, 330, 6, 66, 0, 82))
    b.append(t(96, 300, "바다에서 오는 바람", 14, SEA, "700", anchor="start"))
    spots = [(196, 690, "맞바람", 0, -1), (330, 494, "옆바람", 1, 0), (566, 424, "뒷바람", 0, 1)]
    for x, y, label, dxs, dys in spots:
        b.append(bike(x, y, 1.0, INK if label != "옆바람" else MARK))
        b.append(t(x, y - 46, label, 15, INK if label != "옆바람" else MARK, "700"))
    b.append(f'<path d="M330,516 q-52,26 -86,10" fill="none" stroke="{DROP}" stroke-width="3" '
             f'stroke-dasharray="6 5" marker-end="url(#gray)"/>')
    b.append(t(238, 566, "옆바람이 가장 위험하다", 13, MARK, "700", anchor="start"))
    b.append(t(W / 2, 262, "바람은 그대로인데 방향이 바뀐다", 15, MUTED))
    b.append(note_box(147, 830, 500, "굽이 앞에서 다음 바람을 예상한다"))
    b.append(caption(W / 2, 952, [
        "해안선의 모양이 굽이 뒤의 바람을 미리 알려 준다.",
    ], 16))
    return base("굽이를 돌면 바람이 바뀐다", "같은 바람 속에서 달라지는 세 순간", "".join(b))


# ── p20 굽이가 알려 주는 것 ───────────────────────────────────────────
def fig_read_curves():
    b = []
    x0, x1, y = 110, 684, 470
    b.append(f'<path d="M{x0},{y} C190,360 250,360 300,470 C350,580 410,580 460,470 '
             f'C510,360 570,360 620,470 C650,530 668,540 {x1},520" fill="none" stroke="{INK}" '
             f'stroke-width="3"/>')
    b.append(f'<path d="M{x0},{y + 120} C190,480 250,480 300,590 C350,700 410,700 460,590 '
             f'C510,480 570,480 620,590 C650,650 668,660 {x1},640" fill="none" stroke="{SEA}" '
             f'stroke-width="4" opacity="0.6"/>')
    b.append(t(x1 - 10, 700, "바다", 14, SEA, "700", anchor="end"))
    for cx, kind in ((240, "cape"), (380, "bay"), (560, "cape")):
        if kind == "cape":
            b.append(f'<circle cx="{cx}" cy="380" r="7" fill="{MARK}"/>')
            b.append(t(cx, 330, "오르막", 13, INK, "700"))
            b.append(t(cx, 308, "바람 셈", 13, INK, "700"))
            b.append(t(cx, 286, "전망대", 13, MARK, "700"))
            b.append(f'<path d="M{cx - 8},{358} L{cx},{344} L{cx + 8},{358} z" fill="{MARK}"/>')
        else:
            b.append(f'<circle cx="{cx}" cy="560" r="7" fill="{KEEP}"/>')
            b.append(t(cx, 630, "내리막", 13, INK, "700"))
            b.append(t(cx, 652, "바람 약함", 13, INK, "700"))
            b.append(t(cx, 674, "마을", 13, KEEP, "700"))
            for k in range(3):
                hx = cx - 26 + k * 26
                b.append(f'<path d="M{hx - 8},{600} L{hx},{588} L{hx + 8},{600} z" fill="{KEEP}"/>')
    b.append(bike(150, 452, 0.9, INK))
    b.append(f'<line x1="176" y1="452" x2="208" y2="430" stroke="{LINE}" stroke-width="2" '
             f'marker-end="url(#gray)"/>')
    b.append(t(150, 412, "진행 방향", 13, MUTED))
    b.append(t(W / 2, 262, "튀어나온 자리와 들어간 자리가 번갈아 온다", 15, MUTED))
    b.append(note_box(147, 830, 500, "휘는 방향만 보고도 다음이 보인다"))
    b.append(caption(W / 2, 952, [
        "곶에는 바람과 전망이, 만에는 마을과 쉼이 있다.",
    ], 16))
    return base("굽이가 알려 주는 것", "해안선의 모양과 그다음에 오는 것", "".join(b))


# ── p31 곶은 아침에, 만은 오후에 ──────────────────────────────────────
def fig_two_days():
    b = []
    x0, x1 = 132, 672
    for i, (label, capes, bays) in enumerate((
            ("첫날", [0.08, 0.2, 0.34], [0.5, 0.68, 0.86]),
            ("둘째 날", [0.12], [0.34, 0.52, 0.7, 0.88]))):
        y = 340 + i * 250
        b.append(t(x0 - 16, y + 6, label, 16, INK, "700", anchor="end"))
        b.append(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="{SOFT}" stroke-width="3"/>')
        for at in capes:
            x = x0 + (x1 - x0) * at
            b.append(f'<path d="M{x - 24},{y} L{x - 10},{y - 34} L{x + 10},{y - 34} L{x + 24},{y} z" '
                     f'fill="{MARK}" opacity="0.85"/>')
        for at in bays:
            x = x0 + (x1 - x0) * at
            b.append(f'<path d="M{x - 26},{y} q26,34 52,0" fill="none" stroke="{KEEP}" stroke-width="3"/>')
        wy = y + 110
        pts = [(x0, wy), (x0 + (x1 - x0) * 0.45, wy - 62), (x0 + (x1 - x0) * 0.62, wy - 70),
               (x1, wy - 12)]
        b.append('<polyline points="' + " ".join(f"{px},{py}" for px, py in pts) +
                 f'" fill="none" stroke="{SEA}" stroke-width="3"/>')
        b.append(t(x0 - 16, wy + 6, "바람", 13, SEA, "700", anchor="end"))
        if i == 0:
            b.append(t(x0, y - 52, "곶", 13, MARK, "700", anchor="start"))
            b.append(t(x0 + (x1 - x0) * 0.5, y - 52, "만", 13, KEEP, "700"))
            b.append(t(x1, wy + 34, "오후 두세 시가 가장 세다", 13, MUTED, anchor="end"))
    b.append(t(x0, 340 + 250 + 150, "아침 여섯 시", 13, MUTED, anchor="start"))
    b.append(t(x1, 340 + 250 + 150, "저녁 여섯 시", 13, MUTED, anchor="end"))
    b.append(t(W / 2, 262, "곶 표시가 모두 바람이 약한 자리에 있다", 15, MUTED))
    b.append(note_box(147, 880, 500, "어려운 지형을 약한 바람에 넣는다"))
    b.append(caption(W / 2, 1002, [
        "첫날에 곶 셋, 둘째 날에 곶 하나를 넣었다.",
    ], 16))
    return base("곶은 아침에, 만은 오후에", "이틀 동안의 지형과 바람", "".join(b))


# ── p38 좁아지면 빨라진다 ─────────────────────────────────────────────
def fig_gap():
    b = []
    b.append(f'<path d="M120,330 Q300,340 340,430 Q380,520 560,520 L680,520 L680,300 L120,300 z" '
             f'fill="#eef0ea" stroke="{SOFT}"/>')
    b.append(f'<path d="M120,790 Q300,780 340,690 Q380,600 560,600 L680,600 L680,800 L120,800 z" '
             f'fill="#eef0ea" stroke="{SOFT}"/>')
    b.append(t(220, 360, "언덕", 15, MUTED))
    b.append(t(220, 760, "언덕", 15, MUTED))
    b.append(wind_arrows(96, 380, 3, 76, 0, 90, SEA, 3))
    b.append(wind_arrows(96, 700, 3, 76, 0, 60, SEA, 3))
    for y in (486, 510, 534, 596, 620):
        b.append(f'<line x1="356" y1="{y}" x2="524" y2="{y}" stroke="{SEA}" stroke-width="4" '
                 f'marker-end="url(#wind)"/>')
    b.append(f'<line x1="120" y1="560" x2="680" y2="560" stroke="{INK}" stroke-width="8" opacity="0.25"/>')
    b.append(t(444, 462, "이백 걸음", 15, INK, "700"))
    b.append(f'<circle cx="330" cy="560" r="10" fill="{KEEP}"/>')
    b.append(t(322, 676, "들어가기 전 오 분", 13, KEEP, "700", anchor="middle"))
    b.append(f'<circle cx="574" cy="560" r="10" fill="{KEEP}"/>')
    b.append(t(586, 676, "나온 뒤 삼십 분", 13, KEEP, "700", anchor="middle"))
    b.append(bike(444, 556, 0.9, MARK))
    b.append(t(444, 676, "내려서 끈다", 13, MARK, "700"))
    b.append(t(W / 2, 262, "언덕 사이에서 화살표가 촘촘해진다", 15, MUTED))
    b.append(note_box(147, 880, 500, "센 구간은 두 번의 쉼 사이에 둔다"))
    b.append(caption(W / 2, 1002, [
        "같은 바람도 좁은 자리를 지나며 빨라진다.",
    ], 16))
    return base("좁아지면 빨라진다", "바람언덕의 이백 걸음", "".join(b))


# ── p44 어느 세기까지 갈 수 있는가 ────────────────────────────────────
def fig_limits():
    b = []
    ax, ay0, ay1 = 220, 840, 320
    b.append(f'<line x1="{ax}" y1="{ay0}" x2="{ax}" y2="{ay1}" stroke="{INK}" stroke-width="2.4" '
             f'marker-end="url(#gray)"/>')
    b.append(t(ax - 16, ay0 + 6, "잔잔함", 15, MUTED, anchor="end"))
    b.append(t(ax - 16, ay1 - 4, "거침", 15, MUTED, anchor="end"))
    right = 560
    lines = [(740, "자전거로 곶을 넘는다"), (610, "자전거로 만을 달린다"), (450, "차로 지난다")]
    prev = ay0
    for y, label in lines:
        b.append(f'<rect x="{ax}" y="{y}" width="{right - ax}" height="{prev - y}" fill="{KEEP}" '
                 f'opacity="0.10"/>')
        b.append(f'<line x1="{ax}" y1="{y}" x2="{right}" y2="{y}" stroke="{INK}" stroke-width="2"/>')
        b.append(t(right + 16, y + 5, label, 15, INK, "700", anchor="start"))
        prev = y
    b.append(f'<rect x="{ax}" y="{ay1}" width="{right - ax}" height="{450 - ay1}" fill="{DROP}" '
             f'opacity="0.35"/>')
    b.append(t((ax + right) / 2, 400, "아무것도 하지 않는다", 16, MUTED, "700"))
    b.append(f'<path d="M{(ax + right) / 2 - 14},{432} L{(ax + right) / 2},{416} '
             f'L{(ax + right) / 2 + 14},{432} z" fill="{MUTED}"/>')
    b.append(t(W / 2, 262, "선 아래에서만 그 방법으로 갈 수 있다", 15, MUTED))
    b.append(note_box(147, 900, 500, "기준은 바람이 아니라 오늘 무엇을 타느냐다"))
    b.append(caption(W / 2, 1022, [
        "가지 않기로 하는 것도 하나의 선택지다.",
    ], 16))
    return base("어느 세기까지 갈 수 있는가", "바람의 세기와 이동 수단", "".join(b))


FIGURES = {
    9: fig_surface,
    14: fig_crosswind,
    20: fig_read_curves,
    31: fig_two_days,
    38: fig_gap,
    44: fig_limits,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild085")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
