#!/usr/bin/env python3
"""book-028 이미지 페이지 6개의 SVG 생성. figures021.py의 t()/base() 패턴을 따른다.

여섯 도표에서 표기를 고정한다 — 식물은 초록, 균류의 실은 흙빛 가는 선, 확인된 것은 실선 테두리,
아직 확인되지 않은 것은 점선 테두리와 물음표다. 화살표 굵기는 세기를 뜻하지 않는다.

사용: python3 figures028.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
PLANT = "#4a7c59"          # 식물
FUNGI = "#9a7b4f"          # 균류의 실
MARK = "#b4703a"           # 신호·강조
NONE = "#a24f3d"           # 확인되지 않은 것
FAR = "#8b95a1"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <marker id="plant" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{PLANT}"/></marker>
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


def arrow(x1, y1, x2, y2, color=MARK, width=3, marker="mark"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def leaf(cx, cy, w=64, h=34, color=PLANT, bitten=False, opacity=1.0):
    out = [f'<path d="M{cx - w / 2},{cy} Q{cx},{cy - h} {cx + w / 2},{cy} '
           f'Q{cx},{cy + h} {cx - w / 2},{cy} Z" fill="{color}" opacity="{opacity}"/>']
    if bitten:
        out.append(f'<circle cx="{cx + w * 0.22}" cy="{cy - h * 0.16}" r="{h * 0.30}" fill="#ffffff"/>')
        out.append(f'<circle cx="{cx + w * 0.05}" cy="{cy + h * 0.22}" r="{h * 0.22}" fill="#ffffff"/>')
    return "".join(out)


def tree(cx, base_y, height, spread, color=PLANT):
    return (f'<line x1="{cx}" y1="{base_y}" x2="{cx}" y2="{base_y - height}" '
            f'stroke="#6b5a45" stroke-width="{max(3, spread / 14)}"/>'
            f'<ellipse cx="{cx}" cy="{base_y - height}" rx="{spread}" ry="{spread * 0.72}" '
            f'fill="{color}" opacity="0.55"/>')


def box(x, y, w, h, label, dashed=False, fill="#fbfcfc", size=15, color=INK):
    dash = ' stroke-dasharray="6 5"' if dashed else ""
    stroke = NONE if dashed else LINE
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="2"{dash}/>'
            + t(x + w / 2, y + h / 2 + 5, label, size, color, "600"))


# ── p7 ────────────────────────────────────────────────────────────────
def fig_three():
    b = []
    b.append(t(W / 2, 240, "무엇을 신호라 부를 수 있는가", 16, MUTED))

    def row(y, ok, tail_note=None):
        out = []
        alpha = 1.0 if ok else 0.35
        xs = [190, 397, 604]
        out.append(leaf(xs[0], y, 72, 38, bitten=True, opacity=1.0))
        for k in range(4):
            out.append(f'<circle cx="{xs[0] + 44 + k * 12}" cy="{y - 16 + k * 9}" r="4" '
                       f'fill="{MARK}"/>')
        out.append(t(xs[0], y + 62, "조건에 따라", 13, INK, "600"))
        out.append(t(xs[0], y + 82, "양이 달라진다", 13, INK, "600"))

        out.append(leaf(xs[1], y, 72, 38, opacity=alpha))
        for k in range(3):
            out.append(f'<circle cx="{xs[1] - 26 + k * 14}" cy="{y - 26}" r="3.5" fill="{MARK}" '
                       f'opacity="{alpha}"/>')
        out.append(f'<circle cx="{xs[1]}" cy="{y}" r="9" fill="none" stroke="{INK}" '
                   f'stroke-width="2" opacity="{alpha}"/>')
        out.append(t(xs[1], y + 62, "받는 쪽이", 13, INK, "600", ))
        out.append(t(xs[1], y + 82, "알아챈다", 13, INK, "600"))

        out.append(leaf(xs[2], y, 72, 44, color="#2f5c3f", opacity=alpha))
        out.append(t(xs[2], y + 62, "알아챈 뒤에", 13, INK, "600"))
        out.append(t(xs[2], y + 82, "달라진다", 13, INK, "600"))

        for i in range(2):
            out.append(arrow(xs[i] + 46, y, xs[i + 1] - 46, y, color=LINE if ok else FAR,
                             width=2, marker="gray"))
        for i, x in enumerate(xs):
            good = ok or i == 0
            if good:
                out.append(f'<path d="M{x - 6},{y - 54} L{x - 1},{y - 48} L{x + 8},{y - 62}" '
                           f'fill="none" stroke="{PLANT}" stroke-width="3"/>')
            else:
                for d in (-1, 1):
                    out.append(f'<line x1="{x - 6 * d}" y1="{y - 62}" x2="{x + 6 * d}" y2="{y - 50}" '
                               f'stroke="{NONE}" stroke-width="3"/>')
        if tail_note:
            out.append(t(xs[2], y + 110, tail_note, 13, NONE, "700"))
        return "".join(out)

    b.append(t(120, 320, "셋이 다 있을 때", 15, INK, "700", anchor="start"))
    b.append(row(390, True))
    b.append(f'<line x1="120" y1="530" x2="674" y2="530" stroke="{SOFT}"/>')
    b.append(t(120, 578, "하나만 빠져도", 15, NONE, "700", anchor="start"))
    b.append(row(648, False, "여기까지 가지 못한다"))

    b.append(note_box(197, 838, 400, "내보냈다는 것만으로는 신호가 아니다", 15))
    return base("셋이 다 있어야 신호다",
                "신호로 인정하는 데 필요한 세 조건을 나타낸 그림", "".join(b))


# ── p13 ───────────────────────────────────────────────────────────────
def fig_gravity():
    b = []
    b.append(t(W / 2, 240, "옆으로 눕힌 식물에서 일어나는 일", 16, MUTED))

    def organ(cy, label, up, note):
        out = []
        x0, x1 = 220, 560
        if up:
            out.append(f'<path d="M{x0},{cy - 22} Q{x1 - 60},{cy - 22} {x1},{cy - 96} '
                       f'L{x1 + 26},{cy - 78} Q{x1 - 40},{cy + 22} {x0},{cy + 22} Z" '
                       f'fill="{PLANT}" opacity="0.55" stroke="{PLANT}" stroke-width="2"/>')
        else:
            out.append(f'<path d="M{x0},{cy - 22} Q{x1 - 60},{cy - 22} {x1},{cy + 52} '
                       f'L{x1 + 26},{cy + 34} Q{x1 - 40},{cy + 22} {x0},{cy + 22} Z" '
                       f'fill="#c2a878" opacity="0.7" stroke="{FUNGI}" stroke-width="2"/>')
        for k in range(11):
            out.append(f'<circle cx="{x0 + 24 + k * 28}" cy="{cy + 14}" r="3.4" fill="{MARK}"/>')
        out.append(t(x0 - 12, cy + 5, label, 16, INK, "700", anchor="end"))
        out.append(t(x0 + 168, cy + 46, "아래쪽에 몰린다", 13, MARK, "700"))
        out.append(t(x1 + 40, cy - 60 if up else cy + 66, note, 14, INK, "600", anchor="start"))
        return "".join(out)

    b.append(organ(400, "줄기", True, "위로 굽는다"))
    b.append(t(600, 356, "아래쪽이 더 자란다", 13, MUTED, anchor="start"))
    b.append(f'<line x1="120" y1="560" x2="674" y2="560" stroke="{SOFT}"/>')
    b.append(organ(700, "뿌리", False, "아래로 굽는다"))
    b.append(t(600, 782, "아래쪽이 덜 자란다", 13, MUTED, anchor="start"))

    b.append(f'<line x1="150" y1="300" x2="150" y2="820" stroke="{LINE}" stroke-dasharray="5 6"/>')
    b.append(t(150, 286, "중력", 13, MUTED, "600"))
    b.append(f'<path d="M150,828 L143,812 L157,812 Z" fill="{LINE}"/>')

    b.append(note_box(197, 872, 400, "같은 물질, 같은 자리, 다른 결과", 16))
    return base("같은 쪽에 몰리고 반대로 굽는다",
                "같은 물질이 줄기와 뿌리에서 반대로 작용하는 것을 나타낸 그림", "".join(b))


# ── p21 ───────────────────────────────────────────────────────────────
def fig_broadcast():
    b = []
    b.append(t(W / 2, 240, "상한 잎에서 나간 물질이 닿는 곳들", 16, MUTED))

    b.append(tree(300, 780, 210, 118))
    b.append(t(300, 806, "상한 잎이 있는 그루", 14, INK, "600"))
    b.append(leaf(206, 596, 76, 40, bitten=True))
    b.append(t(206, 640, "갉아 먹힌 잎", 13, INK, "600"))

    import math
    for i in range(26):
        a = math.radians(-160 + i * 11)
        r = 70 + (i % 4) * 26
        b.append(f'<circle cx="{206 + r * math.cos(a):.0f}" cy="{596 + r * math.sin(a):.0f}" '
                 f'r="3" fill="{MARK}" opacity="0.5"/>')

    b.append(arrow(268, 556, 344, 512))
    b.append(t(360, 506, "같은 그루의 다른 가지", 13, INK, "600", anchor="start"))

    b.append(tree(620, 780, 150, 84))
    b.append(t(620, 806, "이웃한 다른 그루", 14, INK, "600"))
    b.append(arrow(280, 636, 548, 662))
    b.append(t(414, 700, "바람을 타고 옆 그루로", 13, INK, "600"))

    b.append(f'<ellipse cx="470" cy="330" rx="14" ry="8" fill="#5c5340"/>')
    b.append(f'<path d="M462,324 Q450,306 470,312" fill="none" stroke="#5c5340" stroke-width="2"/>')
    b.append(f'<path d="M478,324 Q490,306 470,312" fill="none" stroke="#5c5340" stroke-width="2"/>')
    b.append(arrow(240, 540, 442, 350))
    b.append(t(492, 330, "냄새를 따라오는 다른 생물", 13, INK, "600", anchor="start"))

    b.append(f'<path d="M120,420 Q160,398 200,420 Q240,442 280,420" fill="none" stroke="{FAR}" stroke-width="2"/>')
    b.append(t(130, 398, "바람이 정한다", 13, FAR, "600", anchor="start"))
    b.append(t(300, 862, "세 화살표는 모두 같은 굵기다 — 어느 쪽으로 갈지 정해지지 않는다", 13, MUTED))

    b.append(note_box(197, 900, 400, "공기로 보낸 것은 주소가 없다", 16))
    return base("내보낸 쪽은 받는 쪽을 고르지 못한다",
                "한 잎에서 나온 물질이 닿는 세 방향을 그린 그림", "".join(b))


# ── p29 ───────────────────────────────────────────────────────────────
def fig_network():
    import math
    b = []
    b.append(t(W / 2, 240, "땅 위와 흙 속을 함께 보면", 16, MUTED))

    ground = 520
    b.append(f'<rect x="120" y="{ground}" width="554" height="300" fill="#f2ece1"/>')
    b.append(f'<line x1="120" y1="{ground}" x2="674" y2="{ground}" stroke="{INK}" stroke-width="3"/>')
    b.append(t(130, ground - 12, "땅 위", 13, MUTED, "600", anchor="start"))
    b.append(t(130, ground + 24, "흙 속", 13, MUTED, "600", anchor="start"))

    trees = [(226, 150, 62, "큰 나무"), (400, 104, 44, "중간 나무"), (574, 52, 24, "어린 나무")]
    for cx, hgt, spread, label in trees:
        b.append(tree(cx, ground, hgt, spread))
        b.append(t(cx, ground - hgt - spread * 0.72 - 14, label, 13, INK, "600"))
        for dx in (-22, 0, 22):
            b.append(f'<path d="M{cx},{ground + 2} Q{cx + dx},{ground + 46} {cx + dx * 2.4},{ground + 92}" '
                     f'fill="none" stroke="#6b5a45" stroke-width="3.5"/>')

    # 균사는 흩어진 조각이 아니라 이어진 그물로 보여야 한다. 긴 물결선을 겹쳐 그린다.
    for row in range(6):
        y = ground + 158 + row * 24
        pts = []
        for k in range(29):
            x = 138 + k * 19
            pts.append(f"{x},{y + math.sin(k * 0.9 + row) * 9:.1f}")
        b.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{FUNGI}" '
                 f'stroke-width="1.1" opacity="0.75"/>')
    for cx, hgt, spread, label in trees:
        for dx in (-53, 0, 53):
            b.append(f'<line x1="{cx + dx * 0.9}" y1="{ground + 92}" x2="{cx + dx}" y2="{ground + 300}" '
                     f'stroke="{FUNGI}" stroke-width="1.1" opacity="0.75"/>')
    for x, y in [(310, ground + 182), (490, ground + 230), (392, ground + 258), (240, ground + 206)]:
        b.append(f'<circle cx="{x}" cy="{y}" r="5" fill="{FUNGI}"/>')
    b.append(t(150, ground + 294, "가는 실이 뿌리보다 넓게 퍼져 서로 만난다", 13, "#6b5a45", anchor="start"))

    # 두 줄은 뿌리 끝과 균사 그물 사이의 빈 띠에 둔다. 위쪽에 두면 나무와 겹친다.
    b.append(arrow(268, ground + 58, 540, ground + 70, color=MARK, width=3))
    b.append(t(404, ground + 110, "물질이 오간다", 14, MARK, "700"))
    b.append(t(404, ground + 134, "얼마나인지는 따로 물어야 한다  ?", 13, NONE, "700"))

    b.append(note_box(197, 872, 400, "이어져 있다는 것과 나눈다는 것은 다르다", 15))
    return base("흙 밑에서 이어져 있다",
                "지하에서 여러 그루가 균류로 이어진 모습을 그린 그림", "".join(b))


# ── p37 ───────────────────────────────────────────────────────────────
def fig_barrier():
    b = []
    b.append(t(W / 2, 240, "두 나무 사이에 막을 묻고 표시한 물질을 넣으면", 16, MUTED))

    import math
    for side, (ox, title, blocked) in enumerate([(226, "촘촘한 막", True), (566, "성긴 막", False)]):
        ground = 520
        b.append(f'<rect x="{ox - 156}" y="290" width="312" height="380" rx="12" fill="#fbfcfc" '
                 f'stroke="{SOFT}" stroke-width="2"/>')
        b.append(t(ox, 320, title, 17, INK, "700"))
        b.append(f'<rect x="{ox - 140}" y="{ground}" width="280" height="130" fill="#f2ece1"/>')
        b.append(f'<line x1="{ox - 140}" y1="{ground}" x2="{ox + 140}" y2="{ground}" stroke="{INK}" stroke-width="2"/>')

        for dx in (-76, 76):
            b.append(tree(ox + dx, ground, 96, 46))
            b.append(f'<path d="M{ox + dx},{ground} Q{ox + dx * 1.3},{ground + 50} {ox + dx * 1.5},{ground + 96}" '
                     f'fill="none" stroke="#6b5a45" stroke-width="3"/>')
        b.append(f'<circle cx="{ox - 76}" cy="{ground - 118}" r="7" fill="{MARK}"/>')
        b.append(t(ox - 76, ground - 134, "표시한 물질", 11, MARK, "700"))

        # 파헤친 자국 — 두 칸에서 같게
        b.append(f'<path d="M{ox - 34},{ground} L{ox - 22},{ground + 124} L{ox + 22},{ground + 124} '
                 f'L{ox + 34},{ground} Z" fill="#e7dcc9"/>')
        b.append(f'<rect x="{ox - 7}" y="{ground + 6}" width="14" height="112" fill="#cbbea6" '
                 f'stroke="{LINE}"/>')
        if blocked:
            for k in range(14):
                b.append(f'<circle cx="{ox}" cy="{ground + 14 + k * 8}" r="1.4" fill="{LINE}"/>')
            b.append(t(ox, ground + 154, "균사가 지나가지 못한다", 14, INK, "700"))
        else:
            for k in range(4):
                b.append(f'<circle cx="{ox}" cy="{ground + 24 + k * 28}" r="5" fill="#f2ece1" stroke="{LINE}"/>')
            b.append(t(ox, ground + 154, "균사가 지나간다", 14, INK, "700"))
        b.append(t(ox, ground + 182, "흙을 건드린 정도는 같다", 12, MUTED))

        ay = ground + 62
        if blocked:
            b.append(arrow(ox - 110, ay, ox - 22, ay, color=MARK, width=2))
            for d in (-1, 1):
                b.append(f'<line x1="{ox - 12 * d}" y1="{ay - 12}" x2="{ox + 12 * d}" y2="{ay + 12}" '
                         f'stroke="{NONE}" stroke-width="4"/>')
        else:
            b.append(arrow(ox - 110, ay, ox + 104, ay, color=MARK, width=2))

    b.append(note_box(197, 762, 400, "두 칸에서 다른 것은 구멍 크기뿐이다", 15))
    b.append(caption(W / 2, 866, ["흙을 파고 묻는 일은 두 칸에 똑같이 했다.",
                                  "그래야 남는 차이를 구멍 크기 탓으로 돌릴 수 있다."]))
    return base("흙은 똑같이 건드리고 구멍만 다르게",
                "구멍 크기만 다른 두 가림막을 견주는 배치를 그린 그림", "".join(b))


# ── p43 ───────────────────────────────────────────────────────────────
def fig_claims():
    b = []
    b.append(t(W / 2, 240, "하나의 관찰에서 갈라지는 세 문장", 16, MUTED))

    b.append(box(160, 296, 474, 78, "관찰: 표시한 물질이 이웃 그루에서 나왔다", size=17,
                 fill="#eef2f4"))

    # 상자를 겹치지 않게 벌리고 문장은 줄을 직접 나눠 둔다. 글자 수로 자르면 어색한 자리에서 끊긴다.
    targets = [(132, ["건너갔다"], False, "이 관찰만으로 적을 수 있다"),
               (320, ["받은 쪽에", "쓸모가 있었다"], True, "양을 재야 안다"),
               (508, ["보낸 쪽이", "보냈다"], True, "균류가 옮겼을 수 있다")]
    for x, lines, dashed, note in targets:
        b.append(arrow(397, 384, x + 84, 448, color=LINE, width=2, marker="gray"))
        b.append(box(x, 456, 168, 96, "", dashed=dashed))
        color = FAR if dashed else INK
        if len(lines) == 1:
            b.append(t(x + 84, 512, lines[0], 16, color, "700"))
        else:
            b.append(t(x + 84, 500, lines[0], 15, color, "700"))
            b.append(t(x + 84, 524, lines[1], 15, color, "700"))
        if dashed:
            b.append(t(x + 150, 480, "?", 20, NONE, "700"))
        else:
            b.append(f'<path d="M{x + 74},{478} L{x + 80},{485} L{x + 92},{470}" fill="none" '
                     f'stroke="{PLANT}" stroke-width="3"/>')
        b.append(t(x + 84, 582, note, 12, MUTED))

    b.append(note_box(197, 680, 400, "실선만이 이 관찰에서 나오는 문장이다", 15))
    b.append(caption(W / 2, 784, ["점선 상자는 다른 관찰이 있어야 적을 수 있는 문장이다.",
                                  "물음표 아래에 무엇이 더 필요한지를 적어 두었다."]))
    return base("건너갔다는 관찰에서 갈라지는 길",
                "같은 관찰에서 갈라지는 세 가지 읽기를 그린 그림", "".join(b))


FIGURES = {7: fig_three, 13: fig_gravity, 21: fig_broadcast,
           29: fig_network, 37: fig_barrier, 43: fig_claims}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}")


if __name__ == "__main__":
    main()
