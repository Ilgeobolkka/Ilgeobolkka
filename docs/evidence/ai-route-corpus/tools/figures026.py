#!/usr/bin/env python3
"""book-026 이미지 페이지 6개의 SVG 생성. figures021.py의 t()/base() 패턴을 따른다.

빛을 다루는 책이라 색이 뜻을 지닌다. 여섯 도표에서 색 쓰임을 고정한다 — 생물이 내는 빛은 청록 계열,
열이나 손실은 회갈색, 판정이 확정되지 않은 것은 점선 테두리다. 색을 장식으로 쓰지 않는다.

사용: python3 figures026.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
GLOW = "#2f7f96"           # 생물이 내는 빛
GREEN = "#3f8f6a"          # 색이 바뀐 빛
HEAT = "#a9764a"           # 열로 빠져나간 몫
FAR = "#8b95a1"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{GLOW}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
</defs>
{t(W / 2, 122, title, 30, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


def arrow(x1, y1, x2, y2, color=GLOW, width=3, marker="mark"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def box(x, y, w, h, label, fill="#fbfcfc", stroke=LINE, dashed=False, size=16, color=INK):
    dash = ' stroke-dasharray="6 5"' if dashed else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="2"{dash}/>' + t(x + w / 2, y + h / 2 + 6, label, size, color, "600"))


# ── p7 ────────────────────────────────────────────────────────────────
def fig_cold():
    b = []
    b.append(t(W / 2, 242, "빛 막대가 같은 길이가 되도록 맞춰 놓고 견주면", 16, MUTED))

    # 빛 막대는 셋 다 같은 길이다. 밝기를 같게 맞춘 뒤 그때 함께 나간 열을 견주는 그림이기 때문이다.
    LIGHT = 190
    rows = [("촛불", 452), ("백열전구", 316), ("생물이 내는 빛", 12)]
    barx = 300
    y = 330
    for name, heat in rows:
        b.append(t(272, y + 4, name, 16, INK, "600", anchor="end"))
        b.append(f'<rect x="{barx}" y="{y - 34}" width="{LIGHT}" height="24" fill="{GLOW}"/>')
        b.append(t(barx + LIGHT + 10, y - 16, "빛", 13, GLOW, "700", anchor="start"))
        b.append(f'<rect x="{barx}" y="{y - 4}" width="{heat}" height="24" fill="{HEAT}" opacity="0.8"/>')
        b.append(t(barx + heat + 10, y + 14, "열", 13, HEAT, "700", anchor="start"))
        y += 128

    b.append(f'<line x1="{barx}" y1="278" x2="{barx}" y2="600" stroke="{SOFT}"/>')
    b.append(f'<line x1="{barx + LIGHT}" y1="278" x2="{barx + LIGHT}" y2="600" stroke="{SOFT}" '
             f'stroke-dasharray="5 6"/>')
    b.append(t(barx + LIGHT, 268, "빛 막대의 끝은 셋이 같다", 13, FAR, "600"))

    b.append(note_box(197, 664, 400, "같은 밝기에 드는 열이 다르다"))
    b.append(caption(W / 2, 772, ["막대 길이는 실제 비율을 그대로 옮긴 것이 아니라 차이의 방향만 나타낸다.",
                                  "생물의 빛에서 열 막대가 거의 보이지 않는 것이 이 그림의 요점이다."]))
    return base("같은 밝기, 다른 값",
                "빛을 내는 세 가지 방식을 한자리에 놓고 견준 그림", "".join(b))


# ── p16 ───────────────────────────────────────────────────────────────
def fig_color():
    b = []
    b.append(t(W / 2, 242, "발광 물질이 효소를 만나 반응하는 경로", 16, MUTED))

    y = 330
    b.append(box(120, y, 160, 62, "발광 물질"))
    b.append(arrow(288, y + 31, 348, y + 31))
    b.append(f'<circle cx="{318}" cy="{y - 4}" r="15" fill="#eef2f4" stroke="{LINE}"/>')
    b.append(t(318, y + 1, "산소", 11, MUTED, "600"))
    b.append(box(356, y, 130, 62, "효소"))
    b.append(arrow(494, y + 31, 554, y + 31))
    b.append(f'<circle cx="{616}" cy="{y + 31}" r="46" fill="{GLOW}"/>')
    b.append(t(616, y + 37, "푸른빛", 16, "#ffffff", "700"))

    b.append(arrow(616, y + 88, 616, y + 148, color=LINE, width=2, marker="gray"))
    b.append(box(468, y + 156, 296, 58, "색을 바꾸는 물질", size=16))
    b.append(arrow(468 - 8, y + 185, 400, y + 185, color=LINE, width=2, marker="gray"))
    b.append(f'<circle cx="{344}" cy="{y + 185}" r="46" fill="{GREEN}"/>')
    b.append(t(344, y + 191, "초록빛", 16, "#ffffff", "700"))

    # 파장 이동의 방향. 되는 쪽과 안 되는 쪽을 각각 한 줄씩 그려 화살표 하나로 뭉치지 않는다.
    ay = y + 264
    b.append(f'<line x1="300" y1="{ay - 26}" x2="300" y2="{ay + 74}" stroke="{SOFT}"/>')
    b.append(f'<line x1="660" y1="{ay - 26}" x2="660" y2="{ay + 74}" stroke="{SOFT}"/>')
    b.append(t(300, ay - 38, "초록 쪽", 13, MUTED, "600"))
    b.append(t(660, ay - 38, "푸른 쪽", 13, MUTED, "600"))

    b.append(arrow(654, ay, 306, ay, color=GREEN, width=3, marker="gray"))
    b.append(t(676, ay + 5, "이 방향은 있다", 14, GREEN, "700", anchor="start"))

    ay2 = ay + 56
    b.append(f'<line x1="306" y1="{ay2}" x2="654" y2="{ay2}" stroke="{FAR}" stroke-width="3"/>')
    b.append(f'<path d="M654,{ay2} L640,{ay2 - 7} L640,{ay2 + 7} Z" fill="{FAR}"/>')
    for d in (-1, 1):
        b.append(f'<line x1="{480 - 15 * d}" y1="{ay2 - 15}" x2="{480 + 15 * d}" y2="{ay2 + 15}" '
                 f'stroke="#b4442f" stroke-width="5"/>')
    b.append(t(676, ay2 + 5, "이 방향은 없다", 14, "#b4442f", "700", anchor="start"))

    b.append(note_box(197, 776, 400, "같은 반응에서 두 색이 나올 수 있다"))
    b.append(caption(W / 2, 880, ["색을 바꾸는 물질은 받은 빛을 더 긴 파장 쪽으로만 내놓는다.",
                                  "그래서 초록에서 푸른 쪽으로 되돌리는 경로는 없다."]))
    return base("한 번 더 거치면 색이 바뀐다",
                "같은 반응에서 두 가지 색이 나오는 경로를 그린 그림", "".join(b))


# ── p22 ───────────────────────────────────────────────────────────────
def fig_view():
    b = []
    b.append(t(W / 2, 240, "같은 물고기를 두 자리에서 본 결과", 16, MUTED))

    # 위에서 내려오는 빛
    b.append(f'<rect x="105" y="268" width="584" height="44" fill="#eef4f6"/>')
    b.append(t(W / 2, 296, "위에서 내려오는 흐릿한 빛", 14, MUTED))
    for x in range(140, 690, 46):
        b.append(f'<line x1="{x}" y1="316" x2="{x}" y2="348" stroke="#cfdde2" stroke-width="2"/>')

    # 물고기
    fx, fy = 300, 430
    b.append(f'<path d="M{fx - 92},{fy} Q{fx - 40},{fy - 40} {fx + 52},{fy} '
             f'Q{fx - 40},{fy + 40} {fx - 92},{fy} Z" fill="#4a5c66"/>')
    b.append(f'<path d="M{fx + 52},{fy} L{fx + 88},{fy - 24} L{fx + 88},{fy + 24} Z" fill="#4a5c66"/>')
    for k in range(6):
        b.append(f'<circle cx="{fx - 76 + k * 24}" cy="{fy + 20}" r="6" fill="{GLOW}"/>')
    b.append(t(fx - 20, fy - 44, "배 아래쪽 발광 기관", 14, INK, "600"))

    # 아래에서 보는 눈
    b.append(f'<ellipse cx="{fx - 20}" cy="620" rx="20" ry="12" fill="#ffffff" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<circle cx="{fx - 20}" cy="620" r="6" fill="{INK}"/>')
    b.append(f'<line x1="{fx - 20}" y1="606" x2="{fx - 20}" y2="{fy + 34}" stroke="{FAR}" stroke-dasharray="4 5"/>')
    b.append(t(fx - 20, 656, "아래에서 보는 눈", 14, MUTED))

    # 옆에서 보는 눈
    b.append(f'<ellipse cx="600" cy="{fy}" rx="20" ry="12" fill="#ffffff" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<circle cx="600" cy="{fy}" r="6" fill="{INK}"/>')
    b.append(f'<line x1="582" y1="{fy}" x2="{fx + 96}" y2="{fy}" stroke="{FAR}" stroke-dasharray="4 5"/>')
    b.append(t(600, fy + 44, "옆에서 보는 눈", 14, MUTED))

    # 두 결과 화면
    b.append(box(150, 700, 216, 128, "", fill="#eef4f6"))
    b.append(t(258, 770, "아무것도 없다", 16, MUTED, "600"))
    b.append(t(258, 856, "아래에서 본 모습", 15, INK, "600"))

    b.append(f'<rect x="428" y="700" width="216" height="128" rx="12" fill="#1e2a31" stroke="{LINE}" stroke-width="2"/>')
    for k in range(6):
        b.append(f'<circle cx="{460 + k * 30}" cy="764" r="7" fill="{GLOW}"/>')
    b.append(t(536, 856, "옆에서 본 모습", 15, INK, "600"))

    b.append(note_box(197, 900, 400, "같은 빛인데 보는 자리가 다르다"))
    return base("아래에서 보면 사라지고 옆에서 보면 보인다",
                "같은 빛이 방향에 따라 반대로 쓰이는 상황을 그린 그림", "".join(b))


# ── p31 ───────────────────────────────────────────────────────────────
def fig_depth():
    b = []
    b.append(t(W / 2, 242, "수면에서 아래로 내려가며 각 색이 남는 깊이", 16, MUTED))

    top = 300
    bottom = 830
    b.append(f'<line x1="150" y1="{top}" x2="150" y2="{bottom}" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(142, top + 6, "수면", 14, MUTED, "600", anchor="end"))
    b.append(t(142, bottom, "깊은 곳", 14, MUTED, "600", anchor="end"))

    bands = [("붉은색", "#b4442f", 0.10), ("주황색", "#c2762f", 0.22),
             ("노란색", "#b9a33a", 0.40), ("초록색", GREEN, 0.68), ("푸른색", GLOW, 1.00)]
    x = 210
    span = bottom - top
    for name, color, frac in bands:
        h = span * frac
        b.append(f'<rect x="{x}" y="{top}" width="58" height="{h:.0f}" fill="{color}" opacity="0.85"/>')
        b.append(t(x + 29, top + h + 24, name, 13, MUTED, "600"))
        x += 86

    # 가장 깊은 자리의 발광 생물
    gx, gy = 620, bottom - 40
    b.append(f'<path d="M{gx - 44},{gy} Q{gx - 16},{gy - 22} {gx + 26},{gy} '
             f'Q{gx - 16},{gy + 22} {gx - 44},{gy} Z" fill="#4a5c66"/>')
    for k in range(4):
        b.append(f'<circle cx="{gx - 34 + k * 16}" cy="{gy + 11}" r="4.5" fill="{GLOW}"/>')
    b.append(t(gx + 40, gy - 6, "이 자리에서", 13, INK, "600", anchor="start"))
    b.append(t(gx + 40, gy + 14, "쓸모 있는 색", 13, INK, "600", anchor="start"))

    b.append(note_box(197, 902, 400, "멀리 가는 색이 곧 쓰이는 색이다"))
    b.append(caption(W / 2, 1006, ["띠의 길이는 그 색의 빛이 물속에서 남아 있는 깊이를 뜻한다."]))
    return base("붉은 쪽부터 사라진다",
                "색마다 물속에서 사라지는 깊이가 다른 것을 나타낸 그림", "".join(b))


# ── p39 ───────────────────────────────────────────────────────────────
def fig_darkroom():
    b = []
    b.append(t(W / 2, 242, "물마루호 갑판 아래 암실의 자리 배치", 16, MUTED))

    rx, ry, rw, rh = 130, 286, 456, 336
    b.append(f'<rect x="{rx}" y="{ry}" width="{rw}" height="{rh}" rx="10" fill="#1e2a31" '
             f'stroke="{LINE}" stroke-width="2"/>')
    b.append(t(rx + 16, ry + 32, "암실", 16, "#c3ced3", "700", anchor="start"))

    bench_y = 556
    # 붉은 등은 작업대 쪽만 비춘다. 방 전체를 밝히면 이 그림의 뜻이 사라진다.
    lx, ly = rx + rw - 96, ry + 66
    b.append(f'<path d="M{lx},{ly} L{lx - 96},{bench_y} L{lx + 74},{bench_y} Z" '
             f'fill="#b4442f" opacity="0.17"/>')
    b.append(f'<circle cx="{lx}" cy="{ly}" r="12" fill="#b4442f"/>')
    b.append(t(lx + 20, ly + 5, "붉은 등", 13, "#e0b4a8", "600", anchor="start"))

    b.append(f'<rect x="{rx + 44}" y="{bench_y}" width="{rw - 110}" height="11" fill="#3d4a52"/>')

    # 물이 든 통
    tx = rx + 168
    b.append(f'<rect x="{tx - 44}" y="{bench_y - 88}" width="88" height="88" rx="6" '
             f'fill="#25333b" stroke="#5d6d76" stroke-width="2"/>')
    b.append(f'<circle cx="{tx}" cy="{bench_y - 44}" r="12" fill="{GLOW}"/>')
    b.append(t(tx, bench_y + 34, "물이 든 통", 13, "#c3ced3"))

    # 장치 둘
    b.append(f'<rect x="{tx + 66}" y="{bench_y - 60}" width="76" height="42" rx="6" '
             f'fill="#33424b" stroke="#5d6d76"/>')
    b.append(t(tx + 104, bench_y - 72, "빛을 세는 장치", 12, "#c3ced3"))
    b.append(f'<rect x="{tx - 34}" y="{bench_y - 168}" width="70" height="38" rx="6" '
             f'fill="#33424b" stroke="#5d6d76"/>')
    b.append(t(tx + 1, bench_y - 180, "색을 재는 장치", 12, "#c3ced3"))
    b.append(f'<line x1="{tx + 1}" y1="{bench_y - 128}" x2="{tx + 1}" y2="{bench_y - 94}" '
             f'stroke="#5d6d76" stroke-dasharray="3 4"/>')
    b.append(f'<line x1="{tx + 62}" y1="{bench_y - 40}" x2="{tx + 50}" y2="{bench_y - 40}" '
             f'stroke="#5d6d76" stroke-dasharray="3 4"/>')

    # 관찰자
    ox = rx + 60
    b.append(f'<circle cx="{ox}" cy="{bench_y - 96}" r="13" fill="#5d6d76"/>')
    b.append(f'<rect x="{ox - 12}" y="{bench_y - 78}" width="24" height="46" rx="6" fill="#5d6d76"/>')
    b.append(f'<line x1="{ox + 16}" y1="{bench_y - 96}" x2="{tx - 50}" y2="{bench_y - 56}" '
             f'stroke="#7f8f98" stroke-dasharray="4 5"/>')
    b.append(t(ox, bench_y + 34, "어둠에 적응한 눈", 12, "#c3ced3"))

    # 기록지
    px = rx + rw + 30
    b.append(f'<rect x="{px}" y="{ry + 96}" width="132" height="152" rx="8" fill="#fbfcfc" '
             f'stroke="{LINE}" stroke-width="2"/>')
    for i, line in enumerate(["어디서", "얼마나 오래", "무슨 색"]):
        b.append(t(px + 66, ry + 142 + i * 40, line, 14, INK, "600"))
    b.append(t(px + 66, ry + 276, "기록지", 13, MUTED))

    b.append(note_box(197, 690, 400, "재는 일보다 어둡게 두는 일이 먼저다"))
    b.append(caption(W / 2, 794, ["붉은 등은 방 전체를 밝히지 않고 작업대 쪽만 비춘다.",
                                  "밝은 빛에 한 번 노출되면 그 개체는 한동안 빛을 내지 못한다."]))
    return base("붉은 등 하나만 켠 방",
                "배 위 암실에서 발광을 재는 자리 배치를 그린 그림", "".join(b))


# ── p45 ───────────────────────────────────────────────────────────────
def fig_claim():
    b = []
    b.append(t(W / 2, 244, "하나의 관찰에서 갈라지는 두 문장", 16, MUTED))

    b.append(box(217, 300, 360, 76, "관찰: 건드리자 밝게 빛났다", fill="#eef2f4", size=17))

    b.append(arrow(340, 386, 258, 452, color=LINE, width=2, marker="gray"))
    b.append(arrow(454, 386, 536, 452, color=LINE, width=2, marker="gray"))

    b.append(f'<rect x="118" y="460" width="280" height="120" rx="12" fill="#eef2f4" '
             f'stroke="{LINE}" stroke-width="2"/>')
    b.append(t(258, 500, "말할 수 있는 것", 14, MUTED, "700"))
    b.append(t(258, 534, "자극을 받으면", 17, INK, "700"))
    b.append(t(258, 560, "빛난다", 17, INK, "700"))

    b.append(f'<rect x="396" y="460" width="280" height="120" rx="12" fill="#ffffff" '
             f'stroke="{LINE}" stroke-width="2" stroke-dasharray="6 5"/>')
    b.append(t(536, 500, "말할 수 없는 것", 14, MUTED, "700"))
    b.append(t(536, 534, "이 빛은 방어에", 17, FAR, "700"))
    b.append(t(536, 560, "쓰인다", 17, FAR, "700"))
    b.append(t(664, 484, "?", 26, "#b4442f", "700"))

    b.append(caption(536, 616, ["빛난 뒤에 무슨 일이", "일어났는지 보지 않았다"], 14, MUTED))

    b.append(note_box(197, 712, 400, "관찰과 해석 사이에는 한 걸음이 있다"))
    b.append(caption(W / 2, 816, ["실선 상자는 이 관찰만으로 적을 수 있는 문장이고,",
                                  "점선 상자는 다른 관찰이 있어야 적을 수 있는 문장이다."]))
    return base("본 것과 말할 수 있는 것",
                "같은 관찰에서 서로 다른 결론이 나오는 갈림을 그린 그림", "".join(b))


FIGURES = {7: fig_cold, 16: fig_color, 22: fig_view,
           31: fig_depth, 39: fig_darkroom, 45: fig_claim}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}")


if __name__ == "__main__":
    main()
