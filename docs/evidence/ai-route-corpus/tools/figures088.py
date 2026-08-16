#!/usr/bin/env python3
"""book-088 이미지 페이지 6개의 SVG 생성. figures081~087의 t()/base() 패턴을 그대로 쓴다.

여섯 도표가 모두 '골목에서 무엇을 보고 무엇을 그리는가'를 그린다. 지날 수 있는 쪽은 진한 색, 막히거나
확인하지 못한 쪽은 옅은 색으로 고정하고, 물은 늘 작은 화살표로 나타낸다.

사용: python3 figures088.py <출력디렉터리>
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
WATER = "#8fb2c4"


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
  <marker id="water" markerWidth="9" markerHeight="9" refX="8" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z" fill="{WATER}"/></marker>
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


def flow(x, y, dx, dy, n=3, gap=26):
    """물이 흐르는 방향의 짧은 화살표 n개. 세로 화살표는 아래로, 가로 화살표는 옆으로 늘어놓는다."""
    out = []
    for i in range(n):
        sx = x + (i * gap if dx else 0)
        sy = y + (i * gap if dy else 0)
        out.append(f'<line x1="{sx}" y1="{sy}" x2="{sx + dx}" y2="{sy + dy}" stroke="{WATER}" '
                   f'stroke-width="2.4" marker-end="url(#water)"/>')
    return "".join(out)


# ── p7 물이 가리키는 쪽 ───────────────────────────────────────────────
def fig_water():
    b = []
    lanes = [((150, 340), (150, 700), 6), ((150, 420), (620, 420), 4),
             ((330, 340), (330, 700), 3), ((150, 560), (620, 560), 6),
             ((500, 420), (500, 700), 4)]
    for (x1v, y1v), (x2v, y2v), width in lanes:
        b.append(f'<line x1="{x1v}" y1="{y1v}" x2="{x2v}" y2="{y2v}" stroke="{INK}" '
                 f'stroke-width="{width}" opacity="0.85"/>')
    b.append(flow(168, 360, 0, 26, 4, 74))
    b.append(flow(348, 360, 0, 26, 4, 74))
    b.append(flow(518, 450, 0, 26, 3, 74))
    b.append(flow(184, 434, 26, 0, 4, 104))
    b.append(flow(184, 574, 26, 0, 4, 104))
    b.append(f'<line x1="620" y1="380" x2="620" y2="760" stroke="{WATER}" stroke-width="12"/>')
    b.append(t(660, 560, "큰 배수로", 14, WATER, "700", anchor="start"))
    b.append(f'<line x1="120" y1="760" x2="680" y2="760" stroke="{INK}" stroke-width="10"/>')
    b.append(t(400, 792, "큰길", 15, INK, "700"))
    b.append(f'<circle cx="176" cy="366" r="10" fill="{MARK}"/>')
    b.append(t(176, 340, "여기", 13, MARK, "700"))
    b.append(f'<path d="M176,376 L176,560 L620,560 L620,748" fill="none" stroke="{MARK}" '
             f'stroke-width="3.4" marker-end="url(#mark)"/>')
    b.append(t(W / 2, 262, "화살표는 물이 흐르는 쪽이다", 15, MUTED))
    b.append(note_box(147, 850, 500, "물을 따라가면 큰길이 나온다"))
    b.append(caption(W / 2, 972, [
        "골목에서 길을 잃었을 때의 답이 발밑에 있다.",
    ], 16))
    return base("물이 가리키는 쪽", "비 오는 날 골목의 기울기", "".join(b))


# ── p16 젖으면 달라지는 바닥 ──────────────────────────────────────────
def fig_surface():
    b = []
    x0 = 108
    cw = 142
    kinds = [
        ("판 돌", "#d8d5cd", ["아주 미끄러움", "물이 고임", "오래된 골목"], "가장자리로"),
        ("작은 돌", "#ccc7bd", ["덜 미끄러움", "물이 스밈", "중간"], "가운데로"),
        ("흙", "#c4b7a4", ["진창", "물이 고임", "안쪽 골목"], "피해서"),
        ("콘크리트", "#d5d8d9", ["보통", "빨리 마름", "새 골목"], "아무 데나"),
    ]
    for i, (name, fill, props, how) in enumerate(kinds):
        x = x0 + i * (cw + 12)
        b.append(t(x + cw / 2, 300, how, 13, KEEP, "700"))
        b.append(f'<rect x="{x}" y="{320}" width="{cw}" height="120" fill="{fill}" stroke="{SOFT}"/>')
        if i == 0:
            for k in range(3):
                b.append(f'<line x1="{x}" y1="{350 + k * 30}" x2="{x + cw}" y2="{350 + k * 30}" '
                         f'stroke="#ffffff" stroke-width="2"/>')
        elif i == 1:
            for r in range(4):
                for c in range(6):
                    b.append(f'<circle cx="{x + 14 + c * 23}" cy="{334 + r * 28}" r="7" '
                             f'fill="#ffffff" opacity="0.5"/>')
        elif i == 2:
            b.append(f'<path d="M{x},{400} q30,-14 60,0 q30,14 {cw - 60},0" fill="none" '
                     f'stroke="#ffffff" stroke-width="3" opacity="0.7"/>')
        b.append(t(x + cw / 2, 470, name, 17, INK, "700"))
        for k, prop in enumerate(props):
            b.append(t(x + cw / 2, 506 + k * 26, prop, 13, MUTED))
    b.append(t(W / 2, 262, "위는 걷는 방법, 아래는 그 바닥의 성질", 15, MUTED))
    b.append(note_box(147, 660, 500, "바닥을 보면 그 골목의 나이도 보인다"))
    b.append(caption(W / 2, 782, [
        "판 돌 골목의 가장자리에는 이끼가 끼어 있다.",
    ], 16))
    return base("젖으면 달라지는 바닥", "골목 바닥 네 가지", "".join(b))


# ── p24 여덟 개의 기호 ────────────────────────────────────────────────
def fig_symbols():
    b = []
    x0, y0 = 130, 320
    cw, ch = 140, 150
    items = [
        ("wide", "수레가 지나는 폭"), ("mid", "두 사람 폭"), ("narrow", "한 사람 폭"),
        ("flow", "물이 흐르는 쪽"), ("eaves", "처마"), ("blocked", "막힌 자리"),
        ("sound", "소리로만 확인"), ("rainonly", "비 올 때만"),
    ]
    for i, (kind, label) in enumerate(items):
        col, row = i % 4, i // 4
        x = x0 + col * (cw + 8)
        y = y0 + row * (ch + 16)
        b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" rx="10" fill="#fbfbfa" '
                 f'stroke="{SOFT}"/>')
        cx, cy = x + cw / 2, y + 58
        if kind == "wide":
            b.append(f'<line x1="{cx - 44}" y1="{cy}" x2="{cx + 44}" y2="{cy}" stroke="{INK}" stroke-width="9"/>')
        elif kind == "mid":
            b.append(f'<line x1="{cx - 44}" y1="{cy}" x2="{cx + 44}" y2="{cy}" stroke="{INK}" stroke-width="5"/>')
        elif kind == "narrow":
            b.append(f'<line x1="{cx - 44}" y1="{cy}" x2="{cx + 44}" y2="{cy}" stroke="{INK}" stroke-width="2"/>')
        elif kind == "flow":
            b.append(f'<line x1="{cx - 30}" y1="{cy}" x2="{cx + 26}" y2="{cy}" stroke="{WATER}" '
                     f'stroke-width="3" marker-end="url(#water)"/>')
        elif kind == "eaves":
            b.append(f'<line x1="{cx - 44}" y1="{cy}" x2="{cx + 44}" y2="{cy}" stroke="{INK}" stroke-width="5"/>')
            for k in range(7):
                b.append(f'<line x1="{cx - 42 + k * 14}" y1="{cy - 14}" x2="{cx - 42 + k * 14}" '
                         f'y2="{cy - 4}" stroke="{INK}" stroke-width="2"/>')
        elif kind == "blocked":
            b.append(f'<line x1="{cx - 44}" y1="{cy}" x2="{cx + 20}" y2="{cy}" stroke="{INK}" stroke-width="5"/>')
            b.append(f'<line x1="{cx + 20}" y1="{cy - 18}" x2="{cx + 20}" y2="{cy + 18}" stroke="{INK}" stroke-width="5"/>')
        elif kind == "sound":
            b.append(f'<line x1="{cx - 44}" y1="{cy}" x2="{cx + 10}" y2="{cy}" stroke="{INK}" stroke-width="5"/>')
            b.append(f'<line x1="{cx + 10}" y1="{cy - 18}" x2="{cx + 10}" y2="{cy + 18}" stroke="{INK}" stroke-width="5"/>')
            b.append(f'<path d="M{cx + 22},{cy - 10} q10,10 0,20 q-10,10 0,20" fill="none" '
                     f'stroke="{MARK}" stroke-width="2.4"/>')
        else:
            b.append(f'<line x1="{cx - 44}" y1="{cy}" x2="{cx + 44}" y2="{cy}" stroke="{KEEP}" '
                     f'stroke-width="5" stroke-dasharray="8 7"/>')
        b.append(t(cx, y + ch - 26, label, 13, INK, "700"))
    b.append(t(W / 2, 262, "이 지도에서 쓴 기호 전부", 15, MUTED))
    b.append(note_box(147, 700, 500, "기호는 여덟을 넘기지 않는다"))
    b.append(caption(W / 2, 822, [
        "골목에서 그리는 시간이 짧아 고를 수 있는 수가 정해져 있다.",
    ], 16))
    return base("여덟 개의 기호", "폭과 물과 통행을 적는 방법", "".join(b))


# ── p30 뚫렸는가 막혔는가 ─────────────────────────────────────────────
def fig_dead_end():
    b = []
    b.append(f'<line x1="{W / 2}" y1="280" x2="{W / 2}" y2="820" stroke="{SOFT}" stroke-width="2"/>')
    for x0, title, through in ((150, "뚫린 골목", True), (450, "막힌 골목", False)):
        cx = x0 + 100
        b.append(t(cx, 300, title, 18, KEEP if through else MUTED, "700"))
        top, bottom = 340, 700
        b.append(f'<line x1="{cx - 46}" y1="{top}" x2="{cx - 46}" y2="{bottom}" stroke="{INK}" stroke-width="3"/>')
        b.append(f'<line x1="{cx + 46}" y1="{top}" x2="{cx + 46}" y2="{bottom}" stroke="{INK}" stroke-width="3"/>')
        if not through:
            b.append(f'<line x1="{cx - 46}" y1="{top}" x2="{cx + 46}" y2="{top}" stroke="{INK}" stroke-width="5"/>')
            b.append(f'<path d="M{cx - 16},{top + 26} q12,12 0,24 q-12,12 0,24" fill="none" '
                     f'stroke="{MARK}" stroke-width="2.4"/>')
            b.append(t(cx + 30, top + 60, "소리가 되돌아옴", 11, MARK, "700", anchor="start"))
        wear_top = top if through else top + 120
        b.append(f'<rect x="{cx - 16}" y="{wear_top}" width="32" height="{bottom - wear_top}" '
                 f'fill="{DROP}" opacity="0.7"/>')
        b.append(t(cx, bottom + 26, "끝까지 닳음" if through else "안쪽에서 흐려짐", 13, MUTED))
        rx = cx + 34
        b.append(f'<line x1="{rx}" y1="{top if through else top + 90}" x2="{rx}" y2="{bottom}" '
                 f'stroke="{WATER}" stroke-width="5"/>')
        b.append(t(rx + 12, (top + bottom) / 2, "이어짐" if through else "끝남", 12, WATER, "700",
                   anchor="start"))
    b.append(t(W / 2, 262, "들어가기 전에 셋을 본다", 15, MUTED))
    b.append(note_box(147, 860, 500, "바닥과 배수로와 소리"))
    b.append(caption(W / 2, 982, [
        "사흘 동안 여덟 번 짐작해 여섯 번 맞았다.",
    ], 16))
    return base("뚫렸는가 막혔는가", "막다른 골목을 알아보는 단서", "".join(b))


# ── p36 처마길 한 장 ──────────────────────────────────────────────────
def fig_first_sheet():
    b = []
    b.append(f'<line x1="120" y1="800" x2="680" y2="800" stroke="{INK}" stroke-width="10"/>')
    b.append(t(180, 832, "큰길", 14, INK, "700"))
    b.append(f'<line x1="300" y1="800" x2="300" y2="700" stroke="{INK}" stroke-width="5"/>')
    b.append(t(268, 760, "이십삼", 13, MARK, "700", anchor="end"))
    b.append(f'<line x1="300" y1="700" x2="300" y2="480" stroke="{INK}" stroke-width="5"/>')
    for k in range(9):
        b.append(f'<line x1="{288}" y1="{690 - k * 24}" x2="{288}" y2="{680 - k * 24}" '
                 f'stroke="{INK}" stroke-width="2"/>')
        b.append(f'<line x1="{312}" y1="{690 - k * 24}" x2="{312}" y2="{680 - k * 24}" '
                 f'stroke="{INK}" stroke-width="2"/>')
    b.append(t(258, 590, "백십", 13, MARK, "700", anchor="end"))
    b.append(t(344, 590, "처마", 13, INK, "700", anchor="start"))
    b.append(f'<circle cx="300" cy="480" r="7" fill="{INK}"/>')
    b.append(f'<line x1="300" y1="480" x2="470" y2="380" stroke="{INK}" stroke-width="5"/>')
    b.append(t(400, 404, "팔십", 13, MARK, "700"))
    b.append(f'<line x1="470" y1="380" x2="600" y2="330" stroke="{INK}" stroke-width="10"/>')
    b.append(t(620, 320, "큰길", 14, INK, "700", anchor="start"))
    for ex, ey in ((210, 430), (270, 400)):
        b.append(f'<line x1="300" y1="480" x2="{ex}" y2="{ey}" stroke="{INK}" stroke-width="2"/>')
        b.append(f'<line x1="{ex - 10}" y1="{ey - 8}" x2="{ex + 6}" y2="{ey + 12}" stroke="{INK}" stroke-width="3"/>')
        b.append(f'<path d="M{ex - 18},{ey - 18} q10,8 0,16" fill="none" stroke="{MARK}" stroke-width="2"/>')
    b.append(t(184, 452, "소리로만 확인", 12, MARK, "700", anchor="end"))
    for k in range(5):
        b.append(f'<line x1="{318}" y1="{520 + k * 52}" x2="{318}" y2="{548 + k * 52}" '
                 f'stroke="{WATER}" stroke-width="2.4" marker-end="url(#water)"/>')
    b.append(t(360, 700, "물이 흐르는 쪽", 12, WATER, "700", anchor="start"))
    b.append(t(660, 470, "이백십삼 걸음", 14, INK, "700", anchor="end"))
    b.append(t(660, 494, "갈림 하나", 14, INK, "700", anchor="end"))
    b.append(t(W / 2, 262, "첫날 오후에 그린 한 장", 15, MUTED))
    b.append(note_box(147, 880, 500, "입구에서 출구까지 한 시간"))
    b.append(caption(W / 2, 1002, [
        "그중 절반은 처마 아래에서 그리는 데 썼다.",
    ], 16))
    return base("처마길 한 장", "첫날에 그린 골목 지도", "".join(b))


# ── p43 두 날의 같은 골목 ─────────────────────────────────────────────
def fig_two_days():
    b = []
    b.append(f'<line x1="{W / 2}" y1="280" x2="{W / 2}" y2="800" stroke="{SOFT}" stroke-width="2"/>')
    for x0, title, rainy in ((150, "맑은 날", False), (450, "비 오는 날", True)):
        cx = x0 + 100
        b.append(t(cx, 300, title, 18, INK if rainy else MUTED, "700"))
        top, bottom = 350, 660
        b.append(f'<line x1="{cx - 40}" y1="{top}" x2="{cx - 40}" y2="{bottom}" stroke="{INK}" stroke-width="3"/>')
        b.append(f'<line x1="{cx + 40}" y1="{top}" x2="{cx + 40}" y2="{bottom}" stroke="{INK}" stroke-width="3"/>')
        if rainy:
            for k in range(5):
                b.append(f'<line x1="{cx}" y1="{top + 20 + k * 60}" x2="{cx}" y2="{top + 48 + k * 60}" '
                         f'stroke="{WATER}" stroke-width="2.4" marker-end="url(#water)"/>')
            b.append(f'<ellipse cx="{cx - 18}" cy="{top + 200}" rx="18" ry="10" fill="{WATER}" opacity="0.5"/>')
            for k in range(6):
                b.append(f'<line x1="{cx - 38 + k * 16}" y1="{top - 8}" x2="{cx - 38 + k * 16}" '
                         f'y2="{top + 2}" stroke="{INK}" stroke-width="2"/>')
            b.append(t(cx, top - 22, "처마", 12, INK, "700"))
            b.append(t(cx, bottom + 30, "지날 수 있음", 14, KEEP, "700"))
            b.append(t(cx, bottom + 58, "알 수 있는 것 일곱", 13, MUTED))
        else:
            for k in range(3):
                b.append(f'<rect x="{cx - 34}" y="{top + 40 + k * 70}" width="60" height="44" rx="6" '
                         f'fill="{DROP}"/>')
            b.append(t(cx + 46, top + 130, "짐", 12, MUTED, anchor="start"))
            b.append(t(cx, bottom + 30, "지날 수 없음", 14, MARK, "700"))
            b.append(t(cx, bottom + 58, "알 수 있는 것 넷", 13, MUTED))
    b.append(t(W / 2, 262, "같은 골목을 두 날에 본 것", 15, MUTED))
    b.append(note_box(147, 840, 500, "비가 골목의 절반을 보여 준다"))
    b.append(caption(W / 2, 962, [
        "기울기와 처마와 미끄러운 자리는 비 오는 날에만 보인다.",
    ], 16))
    return base("두 날의 같은 골목", "맑은 날과 비 오는 날", "".join(b))


FIGURES = {
    7: fig_water,
    16: fig_surface,
    24: fig_symbols,
    30: fig_dead_end,
    36: fig_first_sheet,
    43: fig_two_days,
}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfbuild088")
    out.mkdir(exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
