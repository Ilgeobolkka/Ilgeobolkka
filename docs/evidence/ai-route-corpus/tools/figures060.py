#!/usr/bin/env python3
"""book-060 이미지 페이지 4개의 SVG 생성. figures051.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '어디서 갈리고 어디까지 닿는가'를 그린다. 이해에 닿은 자리는 진한 채움, 닿지 못한
자리는 옅은 색, 서로 지나쳐 버리는 길은 어긋난 화살표로 고정해 같은 뜻으로 쓴다.

사용: python3 figures060.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 이해에 닿은 자리
DROP = "#c3ccd0"          # 닿지 못한 자리
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


def eye(x, y, color):
    return (f'<path d="M{x - 20},{y} q20,-16 40,0 q-20,16 -40,0 z" fill="#ffffff" '
            f'stroke="{color}" stroke-width="1.8"/><circle cx="{x}" cy="{y}" r="5.5" fill="{color}"/>')


# ── p4 같은 것을 보아도 보이는 면이 다르다 ──────────────────────────
def fig_two_views():
    b = []
    cx, cy = 400, 400
    top = f"M{cx - 70},{cy - 40} L{cx},{cy - 76} L{cx + 70},{cy - 40} L{cx},{cy - 4} Z"
    left = f"M{cx - 70},{cy - 40} L{cx},{cy - 4} L{cx},{cy + 76} L{cx - 70},{cy + 40} Z"
    right = f"M{cx + 70},{cy - 40} L{cx},{cy - 4} L{cx},{cy + 76} L{cx + 70},{cy + 40} Z"
    b.append(f'<path d="{top}" fill="{KEEP}" opacity="0.30" stroke="{INK}" stroke-width="1.8"/>')
    b.append(f'<path d="{left}" fill="{KEEP}" opacity="0.62" stroke="{INK}" stroke-width="1.8"/>')
    b.append(f'<path d="{right}" fill="{KEEP}" opacity="0.16" stroke="{INK}" stroke-width="1.8"/>')
    for k in range(3):
        b.append(f'<line x1="{cx - 56 + k * 18}" y1="{cy + 8 + k * 6}" x2="{cx - 20 + k * 18}" '
                 f'y2="{cy + 28 + k * 6}" stroke="{INK}" stroke-width="1.2" opacity="0.5"/>')
    for k in range(3):
        b.append(f'<circle cx="{cx - 34 + k * 34}" cy="{cy - 40 + (k % 2) * 12}" r="4" fill="{INK}" opacity="0.45"/>')

    b.append(eye(178, 520, KEEP))
    b.append(t(178, 556, "이 자리에서", 15, INK, "700"))
    b.append(f'<line x1="200" y1="508" x2="{cx - 60}" y2="{cy + 40}" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(f'<rect x="140" y="392" width="76" height="76" rx="6" fill="{KEEP}" opacity="0.62" '
             f'stroke="{INK}" stroke-width="1.6"/>')
    for k in range(3):
        b.append(f'<line x1="{152 + k * 16}" y1="440" x2="{172 + k * 16}" y2="416" stroke="{INK}" '
                 f'stroke-width="1.2" opacity="0.5"/>')

    b.append(eye(618, 288, LINE))
    b.append(t(618, 262, "저 자리에서", 15, INK, "700"))
    b.append(f'<line x1="596" y1="298" x2="{cx + 50}" y2="{cy - 48}" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(f'<rect x="580" y="322" width="76" height="76" rx="6" fill="{KEEP}" opacity="0.30" '
             f'stroke="{INK}" stroke-width="1.6"/>')
    for k in range(3):
        b.append(f'<circle cx="{596 + k * 22}" cy="{350 + (k % 2) * 18}" r="4" fill="{INK}" opacity="0.45"/>')

    b.append(f'<line x1="228" y1="430" x2="572" y2="380" stroke="{MARK}" stroke-width="1.6" '
             f'marker-start="url(#mark)" marker-end="url(#mark)"/>')
    b.append(t(400, 246, "다르게 보인다", 14, MARK, "700"))
    b.append(t(400, 552, "둘 다 그 도형이다", 15, INK, "700"))

    b.append(f'<rect x="316" y="592" width="168" height="40" rx="6" fill="none" stroke="{DROP}" '
             f'stroke-width="1.6" stroke-dasharray="5 4"/>')
    b.append(t(400, 618, "어디서도 보지 않는 자리", 13, LINE))
    b.append(f'<line x1="498" y1="600" x2="518" y2="624" stroke="{DROP}" stroke-width="2.4"/>')
    b.append(f'<line x1="518" y1="600" x2="498" y2="624" stroke="{DROP}" stroke-width="2.4"/>')

    b.append(note_box(147, 668, 500, "관점이 없는 자리에서는 아무것도 보이지 않는다", 16))
    b.append(caption(W / 2, 790, [
        "같은 것을 보아도 어디서 보느냐에 따라 보이는 면이 다르다.",
        "두 상은 서로 다르지만 둘 다 그 대상에 대한 것이다.",
    ], 16))
    return base("같은 것을 보아도 보이는 면이 다르다", "두 자리에서 본 하나의 대상", "".join(b))


# ── p13 갈림은 세 자리에서 생긴다 ───────────────────────────────────
def fig_three_forks():
    b = []
    b.append(f'<rect x="120" y="392" width="104" height="48" rx="8" fill="#eef2f1" '
             f'stroke="{INK}" stroke-width="1.8"/>')
    b.append(t(172, 422, "같은 사실", 15, INK, "700"))
    nodes = [(292, "무엇이 원인인가", 26), (416, "무엇을 해야 하는가", 46), (540, "얼마나 급한가", 74)]
    prev_x, prev_ys = 224, [416]
    for i, (nx, label, spread) in enumerate(nodes):
        b.append(f'<circle cx="{nx}" cy="416" r="9" fill="{MARK}"/>')
        b.append(t(nx, 344 + (i % 2) * 22, label, 13, INK, "700"))
        b.append(f'<line x1="{nx}" y1="{352 + (i % 2) * 22}" x2="{nx}" y2="404" '
                 f'stroke="{LINE}" stroke-width="1"/>')
        for y in prev_ys:
            b.append(f'<line x1="{prev_x}" y1="{y}" x2="{nx - 9}" y2="416" stroke="{LINE}" stroke-width="1.4"/>')
        prev_ys = [416 - spread, 416 + spread]
        for y in prev_ys:
            b.append(f'<line x1="{nx + 9}" y1="416" x2="{nx + 62}" y2="{y}" stroke="{LINE}" stroke-width="1.4"/>')
        prev_x = nx + 62
        if i < 2:
            prev_ys = [416]
            b.append(f'<circle cx="{nx + 62}" cy="416" r="3" fill="{LINE}"/>')
    for y, label in ((416 - 74, "이 결론"), (416 + 74, "저 결론")):
        b.append(f'<rect x="614" y="{y - 22}" width="92" height="44" rx="8" fill="#ffffff" '
                 f'stroke="{INK}" stroke-width="1.6"/>')
        b.append(t(660, y + 6, label, 14, INK, "700"))

    b.append(t(354, 520, "따져서 좁혀지기도 한다", 13, KEEP, "700"))
    b.append(t(556, 552, "대개 좁혀지지 않는다", 13, MARK, "700"))
    b.append(t(556, 574, "무엇을 크게 세는지에 달렸다", 12, LINE))

    b.append(note_box(147, 628, 500, "어느 마디에서 갈렸는지 알면 무엇을 할지도 정해진다", 15))
    b.append(caption(W / 2, 750, [
        "같은 사실에서 결론까지 가는 길에는 세 개의 마디가 있다.",
        "앞의 두 마디는 좁혀지기도 하지만 마지막 마디는 대개 남는다.",
    ], 16))
    return base("갈림은 세 자리에서 생긴다", "같은 사실에서 결론이 갈라져 나가는 지점", "".join(b))


# ── p25 합의만 도착지가 아니다 ──────────────────────────────────────
def fig_four_ends():
    b = []
    x0, y0, cw, ch = 206, 268, 196, 152
    cells = [(1, 0, "알고 동의한다", True), (1, 1, "알고 다르다", True),
             (0, 0, "모르고 동의한다", False), (0, 1, "모르고 다르다", False)]
    for cx, cy, label, hi in cells:
        x, y = x0 + cx * cw, y0 + cy * ch
        b.append(f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" '
                 f'fill="{"#eef2f1" if hi else "#ffffff"}" stroke="{LINE}" stroke-width="1.4"/>')
        b.append(t(x + cw / 2, y + 42, label, 15, INK, "700" if hi else "400"))
    b.append(f'<rect x="{x0 + cw}" y="{y0}" width="{cw}" height="{2 * ch}" fill="none" '
             f'stroke="{KEEP}" stroke-width="3"/>')
    b.append(t(x0 + 2 * cw + 16, y0 + ch, "대화가 한 일", 15, KEEP, "700", anchor="start"))

    b.append(t(x0 + cw / 2, y0 + 96, "?", 26, DROP, "700"))
    b.append(t(x0 + cw / 2, y0 + 128, "언제든 뒤집힌다", 12, LINE))

    ax = x0 + cw * 1.5
    b.append(f'<line x1="{ax}" y1="{y0 + ch + 74}" x2="{ax}" y2="{y0 + ch + 118}" stroke="{MARK}" '
             f'stroke-width="2.4" marker-end="url(#mark)"/>')
    b.append(t(ax, y0 + ch + 142, "남는 차이를 적어 둔다", 14, MARK, "700"))

    b.append(t(x0 + cw / 2, y0 + 2 * ch + 28, "이해하지 못했다", 14, MUTED))
    b.append(t(x0 + cw * 1.5, y0 + 2 * ch + 28, "이해했다", 14, MUTED))
    b.append(f'<text x="{x0 - 20}" y="{y0 + ch / 2}" text-anchor="middle" font-size="14" '
             f'fill="{MUTED}" transform="rotate(-90 {x0 - 20} {y0 + ch / 2})">동의한다</text>')
    b.append(f'<text x="{x0 - 20}" y="{y0 + ch * 1.5}" text-anchor="middle" font-size="14" '
             f'fill="{MUTED}" transform="rotate(-90 {x0 - 20} {y0 + ch * 1.5})">동의하지 않는다</text>')

    b.append(note_box(147, 690, 500, "알고 다른 자리도 대화가 도착한 자리다"))
    b.append(caption(W / 2, 812, [
        "대화의 결과는 이해 여부와 동의 여부로 네 자리에 놓인다.",
        "이해에 이른 두 자리가 대화가 실제로 한 일이다.",
    ], 16))
    return base("합의만 도착지가 아니다", "대화가 끝날 때 놓이는 네 자리", "".join(b))


# ── p36 부딪혀도 닫히고 비켜도 닫힌다 ───────────────────────────────
def fig_two_closings():
    b = []
    for y, tag, mode in ((320, "이기려는 대화", "clash"), (490, "각자의 관점이라는 말", "pass")):
        for x in (206, 588):
            b.append(f'<circle cx="{x}" cy="{y}" r="30" fill="#eef2f1" stroke="{INK}" stroke-width="1.8"/>')
        b.append(f'<line x1="236" y1="{y}" x2="558" y2="{y}" stroke="{DROP}" stroke-width="1.2"/>')
        if mode == "clash":
            b.append(f'<line x1="248" y1="{y}" x2="376" y2="{y}" stroke="{MARK}" stroke-width="2.6" '
                     f'marker-end="url(#mark)"/>')
            b.append(f'<line x1="546" y1="{y}" x2="418" y2="{y}" stroke="{MARK}" stroke-width="2.6" '
                     f'marker-end="url(#mark)"/>')
            for k in range(4):
                b.append(f'<line x1="{386 + k * 6}" y1="{y - 18}" x2="{386 + k * 6}" y2="{y + 18}" '
                         f'stroke="{LINE}" stroke-width="2"/>')
        else:
            b.append(f'<line x1="248" y1="{y - 12}" x2="552" y2="{y - 12}" stroke="{DROP}" '
                     f'stroke-width="2.4" marker-end="url(#soft)"/>')
            b.append(f'<line x1="546" y1="{y + 12}" x2="242" y2="{y + 12}" stroke="{DROP}" '
                     f'stroke-width="2.4" marker-end="url(#soft)"/>')
        b.append(t(397, y + 54, tag, 15, INK, "700"))
        b.append(f'<line x1="618" y1="{y}" x2="640" y2="{y}" stroke="{DROP}" stroke-width="2.2"/>')
    b.append(f'<line x1="640" y1="320" x2="640" y2="596" stroke="{DROP}" stroke-width="2.2"/>')
    b.append(f'<line x1="640" y1="596" x2="470" y2="596" stroke="{DROP}" stroke-width="2.2" '
             f'marker-end="url(#soft)"/>')
    b.append(f'<rect x="240" y="572" width="226" height="48" rx="10" fill="#ffffff" '
             f'stroke="{DROP}" stroke-width="1.8"/>')
    b.append(t(353, 602, "남는 것이 없다", 16, LINE, "700"))

    b.append(note_box(147, 668, 500, "닫히는 방향은 둘이고 결과는 하나다"))
    b.append(caption(W / 2, 790, [
        "부딪혀 막히는 대화와 서로 비켜 가는 대화가 있다.",
        "방향은 반대인데 대화가 끝난 뒤에 남는 것이 없다는 점은 같다.",
    ], 16))
    return base("부딪혀도 닫히고 비켜도 닫힌다", "대화가 두 방향으로 닫히는 모습", "".join(b))


FIGURES = {4: fig_two_views, 13: fig_three_forks, 25: fig_four_ends, 36: fig_two_closings}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild060")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
