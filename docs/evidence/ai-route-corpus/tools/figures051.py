#!/usr/bin/env python3
"""book-051 이미지 페이지 4개의 SVG 생성. figures070.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '무엇이 검토되고 무엇이 검토되지 않는가'를 그린다. 검토로 이어지는 갈래는 진한 선,
검토를 지우는 갈래는 옅은 선, 말해지지 않은 것은 점선으로 고정해 네 도표에서 같은 뜻으로 쓴다.

사용: python3 figures051.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 물음이 살아 있는 갈래
DROP = "#c3ccd0"          # 물음이 지워지는 갈래
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
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{DROP}"/></marker>
  <marker id="line" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
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


def box(x, y, w, h, label, stroke=INK, dash=None, size=14):
    style = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="#ffffff" '
            f'stroke="{stroke}" stroke-width="1.8"{style}/>'
            + t(x + w / 2, y + h / 2 + 5, label, size, INK))


# ── p8 어긋난 곳에서 물음이 생긴다 ────────────────────────────────────
def fig_gap():
    b = []
    y0, apex = 430, 330
    xs = [148, 240, 332, 424, 516, 608]
    b.append(f'<path d="M110,{y0} L{xs[2] + 30},{y0} L{xs[3]},{apex} L{xs[3] + 76},{y0} L684,{y0}" '
             f'fill="none" stroke="{INK}" stroke-width="3.4" stroke-linejoin="round"/>')
    for i, x in enumerate(xs):
        if i == 3:
            continue
        b.append(f'<circle cx="{x}" cy="{y0}" r="6" fill="{INK}"/>')
    b.append(t(200, y0 - 22, "기대한 대로", 16, MUTED))
    b.append(f'<circle cx="{xs[3]}" cy="{apex}" r="15" fill="#ffffff" stroke="{MARK}" stroke-width="3"/>')
    b.append(t(xs[3] + 30, apex + 6, "어긋남", 17, MARK, "700", anchor="start"))
    b.append(f'<line x1="{xs[3]}" y1="{apex - 22}" x2="{xs[3]}" y2="{apex - 72}" stroke="{MARK}" '
             f'stroke-width="2.8" marker-end="url(#mark)"/>')
    b.append(t(xs[3], apex - 86, "물음", 19, MARK, "700"))

    for ex, label in ((196, "예외였다"), (424, "사정이 있었다"), (648, "원래 그렇다")):
        b.append(f'<line x1="{xs[3]}" y1="{y0 + 16}" x2="{ex}" y2="537" stroke="{DROP}" '
                 f'stroke-width="2.4" marker-end="url(#gray)"/>')
        b.append(t(ex, 568, label, 16, LINE))
    b.append(f'<line x1="150" y1="592" x2="694" y2="592" stroke="{DROP}" stroke-width="2"/>')
    b.append(t(W / 2, 620, "어긋남이 지워진다", 16, LINE))

    b.append(note_box(147, 668, 500, "메우기 전에 적어 둔다"))
    b.append(caption(W / 2, 790, [
        "물음은 마음먹어서 생기지 않고 기대가 어긋난 자리에서 생긴다.",
        "설명을 서둘러 붙이면 어긋남과 함께 물음도 사라진다.",
    ], 16))
    return base("어긋난 곳에서 물음이 생긴다", "기대가 이어지는 구간과 어긋난 한 지점", "".join(b))


# ── p13 말해진 것과 말해지지 않은 것 ──────────────────────────────────
def fig_layers():
    b = []
    x, w = 200, 380
    b.append(box(x, 268, w, 62, "이 길로 가면 빠르다", INK, size=18))
    b.append(t(x + w + 20, 305, "말해진 것", 16, INK, "700", anchor="start"))

    hidden = ["길이 막히지 않는다", "걸어서 간다", "빠르다는 것은 시간을 뜻한다"]
    ys = [382, 462, 542]
    for i, (yy, label) in enumerate(zip(ys, hidden)):
        b.append(box(x, yy, w, 58, label, LINE, dash="7 5", size=16))
    for a, bm in ((330, 382), (440, 462), (520, 542)):
        b.append(f'<line x1="{x + w / 2}" y1="{a}" x2="{x + w / 2}" y2="{bm}" stroke="{LINE}" stroke-width="1.6"/>')

    bx = x + w + 14
    b.append(f'<path d="M{bx},382 q10,0 10,12 v40 q0,12 10,12 q-10,0 -10,12 v40 q0,12 -10,12" '
             f'fill="none" stroke="{LINE}" stroke-width="1.8"/>')
    b.append(t(bx + 26, 476, "말해지지 않은 것", 16, LINE, "700", anchor="start"))

    b.append(f'<line x1="150" y1="486" x2="{x - 10}" y2="486" stroke="{MARK}" stroke-width="2.6" '
             f'marker-end="url(#mark)"/>')
    b.append(t(52, 470, "여기를 부정하면", 15, MARK, "700", anchor="start"))
    b.append(t(52, 492, "뜻을 잃는다", 15, MARK, "700", anchor="start"))

    b.append(note_box(147, 664, 500, "다툼은 대개 점선에서 일어난다"))
    b.append(caption(W / 2, 786, [
        "한 문장이 참이 되려면 그 문장에 적히지 않은 것들이 함께 참이어야 한다.",
        "부정했을 때 뜻을 잃는 자리가 근거가 아니라 전제다.",
    ], 16))
    return base("말해진 것과 말해지지 않은 것", "하나의 주장을 떠받치는 층", "".join(b))


# ── p25 물음은 세 가지로만 끝난다 ─────────────────────────────────────
def fig_regress():
    b = []
    cols = (172, 397, 622)
    for cx in cols:
        b.append(box(cx - 62, 258, 124, 42, "주장", INK, size=16))

    # 왼쪽: 끝없이 물러남
    cx = cols[0]
    ys = [326, 388, 450, 512, 574]
    b.append(f'<line x1="{cx}" y1="300" x2="{cx}" y2="322" stroke="{LINE}" stroke-width="1.8" marker-end="url(#line)"/>')
    for i, yy in enumerate(ys):
        b.append(box(cx - 56, yy, 112, 40, "근거", LINE, size=14))
        if i < len(ys) - 1:
            b.append(f'<line x1="{cx}" y1="{yy + 40}" x2="{cx}" y2="{yy + 58}" stroke="{LINE}" '
                     f'stroke-width="1.8" marker-end="url(#line)"/>')
    for k in range(3):
        b.append(f'<circle cx="{cx}" cy="{632 + k * 16}" r="3.4" fill="{LINE}"/>')
    b.append(t(cx, 712, "끝없이 물러남", 17, INK, "700"))

    # 가운데: 돌아옴
    cx = cols[1]
    b.append(box(cx - 100, 402, 92, 40, "근거", LINE, size=14))
    b.append(box(cx + 8, 402, 92, 40, "근거", LINE, size=14))
    b.append(f'<line x1="{cx + 34}" y1="300" x2="{cx + 54}" y2="396" stroke="{LINE}" stroke-width="1.8" marker-end="url(#line)"/>')
    b.append(f'<line x1="{cx + 8}" y1="422" x2="{cx - 2}" y2="422" stroke="{LINE}" stroke-width="1.8" marker-end="url(#line)"/>')
    b.append(f'<line x1="{cx - 54}" y1="402" x2="{cx - 34}" y2="306" stroke="{LINE}" stroke-width="1.8" marker-end="url(#line)"/>')
    b.append(t(cx, 366, "서로 떠받침", 15, MUTED))
    b.append(t(cx, 712, "돌아옴", 17, INK, "700"))

    # 오른쪽: 더 묻지 않기로 함
    cx = cols[2]
    b.append(f'<line x1="{cx}" y1="300" x2="{cx}" y2="322" stroke="{LINE}" stroke-width="1.8" marker-end="url(#line)"/>')
    for yy in (326, 388):
        b.append(box(cx - 56, yy, 112, 40, "근거", LINE, size=14))
    b.append(f'<line x1="{cx}" y1="366" x2="{cx}" y2="384" stroke="{LINE}" stroke-width="1.8" marker-end="url(#line)"/>')
    b.append(f'<line x1="{cx}" y1="428" x2="{cx}" y2="452" stroke="{LINE}" stroke-width="1.8" marker-end="url(#line)"/>')
    b.append(f'<line x1="{cx - 76}" y1="460" x2="{cx + 76}" y2="460" stroke="{MARK}" stroke-width="6" stroke-linecap="round"/>')
    b.append(t(cx, 494, "여기서 멈춤", 16, MARK, "700"))
    b.append(t(cx, 712, "더 묻지 않기로 함", 17, INK, "700"))

    b.append(note_box(147, 762, 500, "멈춘 자리를 아는 것이 남는 일이다"))
    b.append(caption(W / 2, 884, [
        "근거를 대는 일은 세 가지 모양으로만 끝난다.",
        "실제로 끝나는 것은 세 번째뿐이고 그것은 근거가 아니라 결정이다.",
    ], 16))
    return base("물음은 세 가지로만 끝난다", "근거를 대는 일이 끝나는 세 가지 모양", "".join(b))


# ── p36 어느 쪽으로 기울어 있는가 ─────────────────────────────────────
def fig_bias():
    b = []
    mid = W / 2

    def row(top, baseline, left_h, right_h, left_q, right_q, tag):
        out = [f'<line x1="{mid}" y1="{top}" x2="{mid}" y2="{baseline + 14}" stroke="{LINE}" '
               f'stroke-width="1.6" stroke-dasharray="6 5"/>',
               f'<line x1="132" y1="{baseline}" x2="662" y2="{baseline}" stroke="{INK}" stroke-width="2"/>']
        for x, h, q, color in ((246, left_h, left_q, KEEP), (458, right_h, right_q, MARK)):
            out.append(f'<rect x="{x}" y="{baseline - h}" width="90" height="{h}" rx="6" '
                       f'fill="{color}" opacity="0.16" stroke="{color}" stroke-width="1.8"/>')
            for k in range(q):
                out.append(t(x + 45, baseline - h + 30 + k * 30, "?", 21, color, "700"))
        out.append(t(690, baseline - 4, tag, 17, INK, "700", anchor="end"))
        return out

    b.append(t(266, 258, "내 생각과 같은 주장", 15, MUTED))
    b.append(t(536, 258, "내 생각과 다른 주장", 15, MUTED))
    b += row(272, 400, 108, 108, 3, 3, "고르게")

    b.append(t(266, 470, "내 생각과 같은 주장", 15, MUTED))
    b.append(t(536, 470, "내 생각과 다른 주장", 15, MUTED))
    b += row(484, 700, 46, 166, 1, 5, "기울어짐")
    b.append(f'<line x1="230" y1="640" x2="352" y2="640" stroke="{KEEP}" stroke-width="2"/>')
    b.append(f'<line x1="442" y1="520" x2="564" y2="520" stroke="{MARK}" stroke-width="2"/>')
    b.append(f'<line x1="397" y1="632" x2="397" y2="528" stroke="{LINE}" stroke-width="1.8" '
             f'marker-end="url(#line)"/>')
    b.append(t(408, 588, "문턱의 차이", 14, LINE, "700", anchor="start"))

    b.append(note_box(147, 762, 500, "세어 보기 전에는 보이지 않는다"))
    b.append(caption(W / 2, 884, [
        "기울기는 물음의 총량이 아니라 어디에 물었는지에서 드러난다.",
        "한쪽만 따지는 사람은 자기가 따진다고 믿는다.",
    ], 16))
    return base("어느 쪽으로 기울어 있는가", "같은 주장과 다른 주장에 쓴 물음의 양", "".join(b))


FIGURES = {8: fig_gap, 13: fig_layers, 25: fig_regress, 36: fig_bias}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild051")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
