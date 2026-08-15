#!/usr/bin/env python3
"""book-087 이미지 페이지 6개의 SVG 생성. figures081~086의 t()/base() 패턴을 그대로 쓴다.

여섯 도표가 모두 '소식이 며칠 걸리는가'를 그린다. 시간은 늘 왼쪽에서 오른쪽으로 흐르고, 기다리는
구간은 옅은 색, 실제로 움직이는 구간은 진한 색으로 고정한다.

사용: python3 figures087.py <출력디렉터리>
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


def letter(cx, cy, s=1.0, color=INK):
    w, h = 22 * s, 15 * s
    return (f'<rect x="{cx - w}" y="{cy - h}" width="{w * 2}" height="{h * 2}" rx="2" '
            f'fill="#ffffff" stroke="{color}" stroke-width="{2 * s}"/>'
            f'<path d="M{cx - w},{cy - h} L{cx},{cy + h * 0.2} L{cx + w},{cy - h}" fill="none" '
            f'stroke="{color}" stroke-width="{1.8 * s}"/>')


# ── p7 며칠 걸려 닿는가 ───────────────────────────────────────────────
def fig_days_to_arrive():
    b = []
    x0, y0, x1, y1 = 140, 720, 664, 350
    b.append(f'<line x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}" stroke="{LINE}" stroke-width="6"/>')
    spots = [(0.0, "아랫마을", "보낸 날"), (0.66, "구름마을", "사나흘 뒤"),
             (1.0, "돌담마을", "닷새에서 엿새 뒤")]
    for at, name, when in spots:
        x = x0 + (x1 - x0) * at
        y = y0 + (y1 - y0) * at
        b.append(f'<circle cx="{x}" cy="{y}" r="11" fill="{INK}"/>')
        b.append(t(x, y - 26, name, 16, INK, "700"))
        b.append(letter(x, y + 50, 0.9, KEEP))
        b.append(t(x, y + 92, when, 14, MARK, "700"))
    mid1x = x0 + (x1 - x0) * 0.33
    mid1y = y0 + (y1 - y0) * 0.33
    b.append(t(mid1x - 10, mid1y - 30, "차로 두 시간", 13, MUTED))
    b.append(t(mid1x - 10, mid1y - 10, "걸어서 다섯 시간", 13, MUTED))
    mid2x = x0 + (x1 - x0) * 0.83
    mid2y = y0 + (y1 - y0) * 0.83
    b.append(t(mid2x + 10, mid2y - 22, "걸어서 한 시간", 13, MUTED))
    b.append(t(W / 2, 262, "길은 하나이고 마을은 둘이다", 15, MUTED))
    b.append(note_box(147, 850, 500, "같은 편지가 마을마다 다른 날에 닿는다"))
    b.append(caption(W / 2, 972, [
        "안쪽 마을일수록 소식이 하루나 이틀 늦다.",
    ], 16))
    return base("며칠 걸려 닿는가", "아랫마을에서 두 고원 마을까지", "".join(b))


# ── p13 다섯 자리와 나흘 ──────────────────────────────────────────────
def fig_five_stops():
    b = []
    names = ["우체국", "가방", "어귀 우편함", "두 번째 걸음", "집"]
    gaps = ["모일 때까지\n하루에서 사흘", "두 시간", "한 시간", "보러 올 때까지\n하루"]
    x0, y = 108, 420
    bw, gap = 108, 36
    centers = []
    for i, name in enumerate(names):
        x = x0 + i * (bw + gap)
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="76" rx="10" fill="#ffffff" '
                 f'stroke="{INK}" stroke-width="1.8"/>')
        b.append(t(x + bw / 2, y + 46, name, 14, INK, "700"))
        centers.append(x + bw / 2)
    for i, label in enumerate(gaps):
        x1c = x0 + i * (bw + gap) + bw
        x2c = x1c + gap
        wide = i == 0
        b.append(f'<line x1="{x1c + 4}" y1="{y + 38}" x2="{x2c - 6}" y2="{y + 38}" '
                 f'stroke="{MARK if wide else LINE}" stroke-width="{6 if wide else 2.4}" '
                 f'marker-end="url(#{"mark" if wide else "gray"})"/>')
        lines = label.split("\n")
        for k, line in enumerate(lines):
            b.append(t((x1c + x2c) / 2, y - 22 + k * 18, line, 11,
                       MARK if wide else MUTED, "700" if wide else "400"))
    b.append(t(centers[0] + (centers[1] - centers[0]) / 2, y + 116, "늦는 이유의 절반", 13, MARK, "700"))
    b.append(f'<line x1="{x0}" y1="{y + 150}" x2="{x0 + 4 * (bw + gap) + bw}" y2="{y + 150}" '
             f'stroke="{SOFT}" stroke-width="2"/>')
    b.append(t(W / 2, y + 178, "모두 지나는 데 사나흘", 16, INK, "700"))
    b.append(t(W / 2, 262, "편지가 지나는 다섯 자리", 15, MUTED))
    b.append(note_box(147, 720, 500, "길보다 모으는 데 오래 걸린다"))
    b.append(caption(W / 2, 842, [
        "편지 한 통을 위해 차가 올라가지 않기 때문이다.",
    ], 16))
    return base("다섯 자리와 나흘", "우편이 지나며 머무는 곳", "".join(b))


# ── p21 몇 번 보러 가는가 ─────────────────────────────────────────────
def fig_waiting():
    b = []
    x0, x1 = 150, 664
    for i, (label, visits, note) in enumerate((("매일 보러 감", 4, "헛걸음 셋"),
                                               ("정해진 날에만", 1, "헛걸음 없음"))):
        y = 380 + i * 220
        b.append(t(x0 - 16, y + 30, label, 15, INK, "700", anchor="end"))
        b.append(f'<rect x="{x0}" y="{y}" width="{x1 - x0}" height="60" rx="8" fill="#f2f5f4" '
                 f'stroke="{SOFT}"/>')
        for k in range(visits):
            at = (k + 1) / 4 if visits == 4 else 1.0
            x = x0 + (x1 - x0) * at
            found = k == visits - 1
            b.append(f'<circle cx="{x - 26}" cy="{y + 30}" r="13" '
                     f'fill="{KEEP if found else "#ffffff"}" stroke="{KEEP if found else DROP}" '
                     f'stroke-width="2"/>')
            b.append(t(x - 26, y + 82, "있음" if found else "없음", 12,
                       KEEP if found else MUTED, "700" if found else "400"))
        b.append(t(x1, y - 14, note, 13, MARK if visits == 4 else KEEP, "700", anchor="end"))
    b.append(t(x0, 350, "편지 부친 날", 13, MUTED, anchor="start"))
    b.append(t(x1, 350, "답이 온 날", 13, MUTED, anchor="end"))
    b.append(f'<line x1="{(x0 + x1) / 2}" y1="470" x2="{(x0 + x1) / 2}" y2="580" stroke="{SOFT}" '
             f'stroke-width="2" stroke-dasharray="5 5"/>')
    b.append(t((x0 + x1) / 2 + 46, 530, "같은 나흘", 14, MUTED, anchor="start"))
    b.append(t(W / 2, 262, "기다린 길이는 같고 헛걸음만 다르다", 15, MUTED))
    b.append(note_box(147, 800, 500, "언제 오는지 알면 기다림이 짧아진다"))
    b.append(caption(W / 2, 922, [
        "주기가 정해져 있으면 그 사이에 다른 일을 할 수 있다.",
    ], 16))
    return base("몇 번 보러 가는가", "같은 나흘을 보내는 두 방법", "".join(b))


# ── p30 열린 계절과 닫힌 계절 ─────────────────────────────────────────
def fig_year():
    b = []
    cx, cy, r = W / 2, 560, 200
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#f4f6f5" stroke="{SOFT}" stroke-width="2"/>')
    def arc(a0, a1, color, width=44, dash=""):
        x0 = cx + (r - width / 2) * math.cos(math.radians(a0 - 90))
        y0 = cy + (r - width / 2) * math.sin(math.radians(a0 - 90))
        x1 = cx + (r - width / 2) * math.cos(math.radians(a1 - 90))
        y1 = cy + (r - width / 2) * math.sin(math.radians(a1 - 90))
        large = 1 if (a1 - a0) % 360 > 180 else 0
        d = f' stroke-dasharray="{dash}"' if dash else ""
        return (f'<path d="M{x0},{y0} A{r - width / 2},{r - width / 2} 0 {large} 1 {x1},{y1}" '
                f'fill="none" stroke="{color}" stroke-width="{width}"{d}/>')
    b.append(arc(0, 270, "#dfe7e3"))
    b.append(arc(270, 330, "#cbb08a"))
    b.append(arc(330, 360, "#5d6b74"))
    b.append(arc(0, 30, "#5d6b74"))
    b.append(t(cx + 150, cy - 130, "열린 계절", 16, KEEP, "700"))
    b.append(t(cx - 156, cy + 108, "준비기", 15, "#8a6a3a", "700"))
    b.append(t(cx - 30, cy - 214, "닫힌 계절", 16, "#48545c", "700"))
    b.append(f'<path d="M{cx - 60},{cy - 196} q60,-16 120,0" fill="none" stroke="{INK}" '
             f'stroke-width="1.6" stroke-dasharray="4 4"/>')
    b.append(t(cx + 130, cy - 196, "경계는 해마다 다르다", 12, MUTED, anchor="start"))
    for k, name in enumerate(("먹을 것", "연료", "약", "사료")):
        y = cy + 42 + k * 30
        b.append(t(cx - r - 40, y, name, 13, "#8a6a3a", "700", anchor="end"))
        b.append(f'<line x1="{cx - r - 34}" y1="{y - 4}" x2="{cx - r + 14}" y2="{cy + 70}" '
                 f'stroke="#cbb08a" stroke-width="1.4"/>')
    for k in range(3):
        b.append(letter(cx - 46 + k * 46, cy - 118, 0.55, "#48545c"))
    b.append(t(cx, cy - 84, "묵는 우편", 12, "#48545c", "700"))
    b.append(t(cx, cy + 8, "한 해", 18, MUTED))
    b.append(t(W / 2, 262, "한 해가 두 계절로 나뉜다", 15, MUTED))
    b.append(note_box(147, 830, 500, "열린 계절에 준비하고 닫힌 계절에 산다"))
    b.append(caption(W / 2, 952, [
        "닫히는 시점은 해마다 다르고 미리 정해지지 않는다.",
    ], 16))
    return base("열린 계절과 닫힌 계절", "고원의 한 해", "".join(b))


# ── p36 사흘의 동행 ───────────────────────────────────────────────────
def fig_three_days():
    b = []
    x0, y0, x1, y1 = 130, 740, 660, 360
    b.append(f'<line x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}" stroke="{SOFT}" stroke-width="8"/>')
    spots = [(0.0, "아랫마을 우체국", "이십 분"), (0.3, "오르는 길", "두 시간"),
             (0.6, "구름마을 어귀", "십 분"), (0.78, "구름마을 안", "한 시간"),
             (1.0, "돌담마을", "반나절")]
    pts = []
    for at, name, dur in spots:
        x = x0 + (x1 - x0) * at
        y = y0 + (y1 - y0) * at
        pts.append((x, y))
        b.append(f'<circle cx="{x}" cy="{y}" r="9" fill="{INK}"/>')
        b.append(t(x - 14, y - 18, name, 13, INK, "700", anchor="end"))
        b.append(t(x + 14, y + 22, dur, 13, MARK, "700", anchor="start"))
    days = [((0.0, 0.6), "첫날", 5), ((0.6, 1.0), "둘째 날", 8), ((1.0, 0.0), "셋째 날", 3)]
    for k, ((a, bb), label, width) in enumerate(days):
        ax = x0 + (x1 - x0) * a + (0 if k != 2 else 0)
        ay = y0 + (y1 - y0) * a
        bx = x0 + (x1 - x0) * bb
        by = y0 + (y1 - y0) * bb
        off = 26 + k * 14
        marker = "keep" if bb > a else "gray"
        b.append(f'<line x1="{ax - off}" y1="{ay - off}" x2="{bx - off}" y2="{by - off}" '
                 f'stroke="{KEEP if bb > a else LINE}" stroke-width="{width}" opacity="0.5" '
                 f'marker-end="url(#{marker})"/>')
        mx, my = (ax + bx) / 2 - off, (ay + by) / 2 - off
        b.append(t(mx - 24, my - 12, label, 13, KEEP, "700", anchor="end"))
    b.append(t(W / 2, 262, "굵기가 다른 세 선이 사흘의 걸음이다", 15, MUTED))
    b.append(note_box(147, 850, 500, "가장 오래 걸리는 자리는 길 위가 아니다"))
    b.append(caption(W / 2, 972, [
        "열여섯 시간 가운데 길 위에서 보낸 것은 여섯 시간이었다.",
    ], 16))
    return base("사흘의 동행", "우편을 따라다닌 자리와 시간", "".join(b))


# ── p44 무엇이 바뀌면 무엇이 바뀌는가 ─────────────────────────────────
def fig_what_changes():
    b = []
    x0 = 300
    cell = 48
    rows = [("지금", [1, 4], "전부", False),
            ("길이 포장되면", [0, 1, 2, 3, 4], "4장만", False),
            ("신호가 안정되면", [1, 4], "2장 일부", True)]
    for i, (label, days, remains, small) in enumerate(rows):
        y = 360 + i * 150
        b.append(t(x0 - 24, y + 32, label, 15, INK, "700", anchor="end"))
        for k in range(7):
            x = x0 + k * cell
            b.append(f'<rect x="{x}" y="{y}" width="{cell - 6}" height="56" rx="6" fill="#f4f6f5" '
                     f'stroke="{SOFT}"/>')
            if k in days:
                b.append(letter(x + (cell - 6) / 2, y + 28, 0.55 if small else 0.75, KEEP))
        b.append(t(x0 + 7 * cell + 10, y + 32, remains, 14, MARK, "700", anchor="start"))
        if small:
            b.append(t(x0 + 3.5 * cell, y + 84, "서류와 물건만", 12, MUTED))
    b.append(t(x0 + 3.5 * cell - 3, 336, "한 주", 13, MUTED))
    b.append(t(x0 + 7 * cell + 10, 336, "남는 부분", 13, MUTED, anchor="start"))
    b.append(t(W / 2, 262, "조건이 바뀌면 배달 주기가 바뀐다", 15, MUTED))
    b.append(note_box(147, 830, 500, "속도가 바뀌면 그 위에 세운 것도 바뀐다"))
    b.append(caption(W / 2, 952, [
        "셋 가운데 하나만 일어나도 이 책의 절반은 다시 써야 한다.",
    ], 16))
    return base("무엇이 바뀌면 무엇이 바뀌는가", "길과 신호가 달라질 때", "".join(b))


FIGURES = {
    7: fig_days_to_arrive,
    13: fig_five_stops,
    21: fig_waiting,
    30: fig_year,
    36: fig_three_days,
    44: fig_what_changes,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild087")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
