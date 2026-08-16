#!/usr/bin/env python3
"""book-054 이미지 페이지 4개의 SVG 생성. figures051.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '말이 닿는 자리와 닿지 않는 자리'를 그린다. 말이 덮은 자리는 진한 채움, 덮지 못한
자리는 빈 자리와 물음표, 말이 아닌 것으로 채워진 자리는 옅은 색으로 고정해 같은 뜻으로 쓴다.

사용: python3 figures054.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 말이 닿은 자리
DROP = "#c3ccd0"          # 말이 닿지 않은 자리
MARK = "#b4703a"          # 표시·강조


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
  <marker id="line" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <marker id="soft" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{DROP}"/></marker>
</defs>
{t(W / 2, 122, title, 29, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


# ── p8 낱말은 겹치지 않고 남는 자리를 만든다 ─────────────────────────
def fig_blobs():
    b = []
    x, y, w, h = 280, 258, 236, 380
    b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="#f2f5f4" '
             f'stroke="{LINE}" stroke-width="1.6"/>')
    b.append(t(x + w + 18, y + 20, "겪는 것 전체", 16, INK, "700", anchor="start"))

    blobs = [(72, 62, 40, "기쁨"), (162, 118, 32, "두려움"), (86, 210, 44, "지침"),
             (176, 250, 20, None), (56, 314, 24, None), (168, 336, 15, None), (128, 40, 16, None)]
    for bx, by, r, label in blobs:
        b.append(f'<ellipse cx="{x + bx}" cy="{y + by}" rx="{r}" ry="{r * 0.78:.0f}" '
                 f'fill="{KEEP}" opacity="0.82"/>')
        if label:
            b.append(t(x + bx, y + by + 5, label, 14, "#ffffff", "700"))
    for qx, qy in ((124, 150), (120, 288)):
        b.append(t(x + qx, y + qy, "?", 22, MARK, "700"))
    b.append(t(x + 150, y + 300, "이름이 없는 자리", 13, MARK, "700"))

    ax = x - 34
    b.append(f'<line x1="{ax}" y1="{y + 30}" x2="{ax}" y2="{y + h - 30}" stroke="{LINE}" '
             f'stroke-width="1.6" marker-end="url(#line)"/>')
    b.append(t(ax - 10, y + 34, "촘촘한 쪽", 14, MUTED, anchor="end"))
    b.append(t(ax - 10, y + h - 24, "성긴 쪽", 14, MUTED, anchor="end"))

    b.append(note_box(147, 690, 500, "말이 닿지 않는 자리는 없는 자리가 아니다"))
    b.append(caption(W / 2, 812, [
        "낱말이 덮는 자리는 서로 닿지 않고 사이에 빈자리를 남긴다.",
        "자주 쓰이는 영역은 촘촘하고 드문 영역은 성기다.",
    ], 16))
    return base("낱말은 겹치지 않고 남는 자리를 만든다", "말이 덮는 영역과 그 사이의 빈틈", "".join(b))


# ── p13 말할 수 없다는 말은 네 가지다 ────────────────────────────────
def fig_kinds():
    b = []
    x0, y0, cw, ch = 200, 250, 196, 152
    cells = [(0, 0, "이름이 없다"), (1, 0, "겪어야 안다"),
             (1, 1, "말하면 달라진다"), (0, 1, "말하지 않기로 했다")]
    for cx, cy, label in cells:
        x, y = x0 + cx * cw, y0 + cy * ch
        b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" fill="#ffffff" '
                 f'stroke="{LINE}" stroke-width="1.6"/>')
        b.append(t(x + cw / 2, y + 30, label, 16, INK, "700"))
        mx, my = x + cw / 2, y + 96
        if label == "이름이 없다":
            b.append(f'<rect x="{mx - 30}" y="{my - 34}" width="60" height="34" fill="none" '
                     f'stroke="{LINE}" stroke-width="1.6"/>')
            b.append(f'<line x1="{mx - 30}" y1="{my + 8}" x2="{mx + 30}" y2="{my + 8}" '
                     f'stroke="{DROP}" stroke-width="2" stroke-dasharray="5 4"/>')
        elif label == "겪어야 안다":
            for dx in (-42, 42):
                b.append(f'<circle cx="{mx + dx}" cy="{my - 14}" r="12" fill="none" stroke="{LINE}" stroke-width="1.6"/>')
                b.append(f'<path d="M{mx + dx - 14},{my + 12} q14,-16 28,0" fill="none" stroke="{LINE}" stroke-width="1.6"/>')
            b.append(f'<line x1="{mx - 24}" y1="{my - 10}" x2="{mx - 4}" y2="{my - 10}" stroke="{DROP}" stroke-width="2.4"/>')
            b.append(f'<line x1="{mx + 6}" y1="{my - 10}" x2="{mx + 22}" y2="{my - 10}" stroke="{DROP}" '
                     f'stroke-width="2.4" marker-end="url(#soft)"/>')
        elif label == "말하면 달라진다":
            b.append(f'<circle cx="{mx - 44}" cy="{my - 10}" r="16" fill="{KEEP}" opacity="0.7"/>')
            b.append(f'<line x1="{mx - 22}" y1="{my - 10}" x2="{mx + 14}" y2="{my - 10}" stroke="{MARK}" '
                     f'stroke-width="2.2" marker-end="url(#mark)"/>')
            b.append(f'<rect x="{mx + 24}" y="{my - 28}" width="34" height="34" rx="4" fill="{KEEP}" opacity="0.45"/>')
        else:
            b.append(f'<path d="M{mx - 34},{my - 20} h68 v28 h-44 l-14,16 v-16 h-10 z" fill="none" '
                     f'stroke="{LINE}" stroke-width="1.6"/>')
            b.append(f'<line x1="{mx - 40}" y1="{my - 6}" x2="{mx + 40}" y2="{my - 6}" stroke="{MARK}" stroke-width="3"/>')

    ay = y0 + 2 * ch + 56
    b.append(f'<rect x="{x0}" y="{ay}" width="{2 * cw}" height="30" rx="15" fill="#eef1f0" stroke="{LINE}"/>')
    b.append(t(x0 + 6, ay + 52, "고칠 수 있다", 14, MUTED, anchor="start"))
    b.append(t(x0 + 2 * cw - 6, ay + 52, "고칠 수 없다", 14, MUTED, anchor="end"))
    for px, label in ((x0 + 40, "이름"), (x0 + cw, "말하기·선택"), (x0 + 2 * cw - 40, "겪음")):
        b.append(f'<circle cx="{px}" cy="{ay + 15}" r="6" fill="{MARK}"/>')
        b.append(t(px, ay - 10, label, 13, MARK, "700"))

    b.append(note_box(147, 700, 500, "어느 종류인지 먼저 가려야 방법이 정해진다"))
    b.append(caption(W / 2, 822, [
        "말할 수 없다는 말은 네 가지 사태를 함께 가리킨다.",
        "종류마다 고칠 수 있는 정도와 쓸 방법이 다르다.",
    ], 16))
    return base("말할 수 없다는 말은 네 가지다", "표현 불가가 가리키는 네 가지 사태", "".join(b))


# ── p25 조용함은 앞뒤가 있어야 읽힌다 ────────────────────────────────
def fig_silence():
    b = []

    def bubble(x, y, filled):
        out = [f'<path d="M{x},{y} h96 a10,10 0 0 1 10,10 v42 a10,10 0 0 1 -10,10 h-58 '
               f'l-16,16 v-16 h-22 a10,10 0 0 1 -10,-10 v-42 a10,10 0 0 1 10,-10 z" '
               f'fill="#ffffff" stroke="{LINE if filled else DROP}" stroke-width="1.8"/>']
        if filled:
            for k in range(2):
                out.append(f'<line x1="{x + 16}" y1="{y + 22 + k * 14}" x2="{x + 84 - k * 20}" '
                           f'y2="{y + 22 + k * 14}" stroke="{KEEP}" stroke-width="3" stroke-linecap="round"/>')
        return "".join(out)

    for row, (y, pattern, tag) in enumerate(((286, [1, 1, 0, 1], "뜻이 있다"),
                                             (486, [0, 0, 0, 0], "뜻이 없다"))):
        b.append(f'<line x1="150" y1="{y + 96}" x2="640" y2="{y + 96}" stroke="{LINE}" stroke-width="1.6"/>')
        for i, filled in enumerate(pattern):
            b.append(bubble(158 + i * 124, y, filled))
        b.append(t(676, y + 46, tag, 16, INK, "700", anchor="middle"))
        if row == 0:
            ex = 158 + 2 * 124 + 53
            b.append(f'<line x1="{ex}" y1="{y + 96}" x2="{ex}" y2="{y + 128}" stroke="{MARK}" '
                     f'stroke-width="2.2" marker-end="url(#mark)"/>')
            b.append(t(ex, y + 150, "무엇을 말할 자리였는지 서로 안다", 14, MARK, "700"))
        else:
            for k in range(3):
                b.append(t(300 + k * 96, y + 130, "?", 22, DROP, "700"))
            b.append(t(W / 2, y + 158, "무엇이 보통인지 정해져 있지 않다", 14, LINE))

    b.append(note_box(147, 700, 500, "침묵은 말 사이에서만 말한다"))
    b.append(caption(W / 2, 822, [
        "앞뒤에 말이 있어야 빈자리가 무엇을 가리키는지 정해진다.",
        "모두가 조용한 자리에서는 조용함이 아무것도 말하지 않는다.",
    ], 16))
    return base("조용함은 앞뒤가 있어야 읽힌다", "같은 침묵이 다르게 읽히는 조건", "".join(b))


# ── p36 양쪽 끝에서 같은 결과가 나온다 ───────────────────────────────
def fig_two_failures():
    b = []
    ax, ay, aw = 140, 300, 514
    cx = ax + aw / 2
    b.append(f'<line x1="{ax}" y1="{ay}" x2="{ax + aw}" y2="{ay}" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<rect x="{cx - 89}" y="{ay - 8}" width="178" height="16" rx="8" '
             f'fill="{KEEP}" opacity="0.35" stroke="{KEEP}" stroke-width="1.4"/>')
    b.append(t(cx, ay - 24, "말이 할 수 있는 만큼", 15, "#2c4f48", "700"))
    b.append(t(ax, ay - 24, "말로는 안 된다", 16, INK, "700", anchor="start"))
    b.append(t(ax + aw, ay - 24, "말하면 다 됐다", 16, INK, "700", anchor="end"))

    b.append(f'<line x1="{cx}" y1="{ay + 12}" x2="{cx}" y2="392" stroke="{KEEP}" '
             f'stroke-width="2.4" marker-end="url(#keep)"/>')
    b.append(f'<rect x="{cx - 110}" y="396" width="220" height="56" rx="10" fill="#eef2f1" '
             f'stroke="{KEEP}" stroke-width="1.8"/>')
    b.append(t(cx, 430, "말한 뒤에 무엇이 달라졌는지 본다", 14, "#2c4f48", "700"))

    b.append(f'<line x1="{ax}" y1="{ay + 12}" x2="330" y2="514" stroke="{DROP}" '
             f'stroke-width="2.4" marker-end="url(#soft)"/>')
    b.append(f'<line x1="{ax + aw}" y1="{ay + 12}" x2="464" y2="514" stroke="{DROP}" '
             f'stroke-width="2.4" marker-end="url(#soft)"/>')
    b.append(f'<rect x="{cx - 118}" y="520" width="236" height="56" rx="10" fill="#ffffff" '
             f'stroke="{DROP}" stroke-width="1.8"/>')
    b.append(t(cx, 554, "확인하지 않는다", 17, LINE, "700"))

    b.append(note_box(147, 640, 500, "양쪽 끝은 서로 반대인데 하는 일이 같다"))
    b.append(caption(W / 2, 762, [
        "말을 너무 믿지 않는 쪽과 너무 믿는 쪽은 반대편에 있다.",
        "그러나 둘 다 말한 뒤에 무엇이 달라졌는지 보지 않는다.",
    ], 16))
    return base("양쪽 끝에서 같은 결과가 나온다", "말을 과소평가하는 실패와 과대평가하는 실패",
                "".join(b))


FIGURES = {8: fig_blobs, 13: fig_kinds, 25: fig_silence, 36: fig_two_failures}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild054")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
