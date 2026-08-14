#!/usr/bin/env python3
"""book-024 이미지 페이지 6개의 SVG 생성. figures021.py의 t()/base() 패턴을 따른다.

이 책의 도표는 모두 시간 축을 쓴다. 여섯 도표에서 축 방향을 고정한다 — 가로 시간 축은 왼쪽이 이르고
오른쪽이 늦다. 세로 시간 축은 아래가 이르고 위가 늦다. 빛이 지나간 길은 굵은 실선, 소식이 닿지 못하는
자리는 점선이다.

사용: python3 figures024.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
LIGHT = "#c08a3e"          # 빛이 지나간 길
SOUND = "#4a6d8c"          # 소리·느린 신호
NONE = "#a24f3d"           # 정할 근거가 없는 자리
FAR = "#8b95a1"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="light" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LIGHT}"/></marker>
  <marker id="sound" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{SOUND}"/></marker>
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


def arrow(x1, y1, x2, y2, color=LIGHT, width=3, marker="light"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def star(cx, cy, r, color):
    pts = []
    import math
    for i in range(10):
        rad = r if i % 2 == 0 else r * 0.45
        a = math.radians(-90 + i * 36)
        pts.append(f"{cx + rad * math.cos(a):.1f},{cy + rad * math.sin(a):.1f}")
    return f'<polygon points="{" ".join(pts)}" fill="{color}"/>'


def person(cx, cy, color=INK, scale=1.0):
    r = 11 * scale
    return (f'<circle cx="{cx}" cy="{cy - r * 1.6}" r="{r}" fill="{color}"/>'
            f'<rect x="{cx - r}" y="{cy - r * 0.3}" width="{2 * r}" height="{r * 2.6}" '
            f'rx="{r * 0.5}" fill="{color}"/>')


def tick_axis(x0, x1, y, label_left, label_right):
    return (f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="{LINE}" stroke-width="2"/>'
            f'<line x1="{x0}" y1="{y - 7}" x2="{x0}" y2="{y + 7}" stroke="{LINE}" stroke-width="2"/>'
            f'<line x1="{x1}" y1="{y - 7}" x2="{x1}" y2="{y + 7}" stroke="{LINE}" stroke-width="2"/>'
            + t(x0, y + 28, label_left, 13, MUTED)
            + t(x1, y + 28, label_right, 13, MUTED))


# ── p7 ────────────────────────────────────────────────────────────────
def fig_arrival():
    b = []
    b.append(t(W / 2, 240, "한 사건에서 나온 두 소식이 닿기까지", 16, MUTED))

    b.append(star(168, 400, 30, LIGHT))
    b.append(t(168, 460, "사건", 16, INK, "700"))
    b.append(person(640, 400))
    b.append(t(640, 460, "보는 사람", 16, INK, "700"))

    y = 560
    b.append(tick_axis(180, 628, y, "사건이 일어난 때", "알게 된 때"))

    xl, xs = 258, 560
    b.append(f'<path d="M188,412 Q220,472 {xl},{y - 12}" fill="none" stroke="{LIGHT}" stroke-width="3"/>')
    b.append(f'<circle cx="{xl}" cy="{y}" r="7" fill="{LIGHT}"/>')
    b.append(t(xl, y - 26, "빛이 닿음", 14, LIGHT, "700"))

    b.append(f'<path d="M188,424 Q340,560 {xs},{y + 12}" fill="none" stroke="{SOUND}" stroke-width="5"/>')
    b.append(f'<circle cx="{xs}" cy="{y}" r="7" fill="{SOUND}"/>')
    b.append(t(xs, y + 62, "소리가 닿음", 14, SOUND, "700"))

    b.append(f'<line x1="{xl}" y1="{y - 48}" x2="{xs}" y2="{y - 48}" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<path d="M{xl},{y - 48} L{xl + 12},{y - 54} L{xl + 12},{y - 42} Z" fill="{INK}"/>')
    b.append(f'<path d="M{xs},{y - 48} L{xs - 12},{y - 54} L{xs - 12},{y - 42} Z" fill="{INK}"/>')
    b.append(t((xl + xs) / 2, y - 60, "이 사이가 지연의 차이", 14, INK, "700"))

    b.append(note_box(197, 700, 400, "지연은 언제나 있다", 16))
    b.append(caption(W / 2, 806, ["견줄 것이 곁에 있을 때만 지연이 눈에 띈다.",
                                  "소리의 지연이 보이는 것은 훨씬 빠른 빛이 함께 오기 때문이다."]))
    return base("같은 사건, 다른 도착",
                "같은 사건의 두 소식이 서로 다른 때에 닿는 모습을 그린 그림", "".join(b))


# ── p13 ───────────────────────────────────────────────────────────────
def fig_layers():
    b = []
    b.append(t(W / 2, 240, "한 자리에서 올려다본 여러 거리의 대상", 16, MUTED))

    px, py = 380, 830
    b.append(f'<path d="M{px},{py - 44} L{px - 214},300 L{px + 214},300 Z" fill="#f2f5f6"/>')
    person(px, py)
    b.append(person(px, py))
    b.append(t(px, py + 46, "보는 사람", 15, INK, "700"))

    # 가까운 것은 크고 아래, 먼 것은 작고 위. 화살표는 모두 보는 사람 쪽으로 모인다.
    layers = [(300, 686, 38, "몇 초 전의 모습", "end", -52),
              (452, 522, 26, "몇 해 전의 모습", "start", 40),
              (338, 376, 16, "아주 오래전의 모습", "end", -30)]
    for cx, cy, r, label, anchor, dx in layers:
        b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#dfe8ec" stroke="{LINE}" stroke-width="2"/>')
        b.append(arrow(cx, cy + r + 6, px - (px - cx) * 0.18, py - 82, color=LIGHT, width=2))
        b.append(t(cx + dx, cy + 5, label, 14, INK, "600", anchor=anchor))

    lx = 648
    b.append(f'<line x1="{lx}" y1="330" x2="{lx}" y2="760" stroke="{LINE}" stroke-width="2"/>')
    b.append(f'<path d="M{lx},322 L{lx - 7},338 L{lx + 7},338 Z" fill="{LINE}"/>')
    b.append(f'<path d="M{lx},768 L{lx - 7},752 L{lx + 7},752 Z" fill="{LINE}"/>')
    b.append(t(lx + 12, 336, "먼 곳", 13, MUTED, "600", anchor="start"))
    b.append(t(lx + 12, 356, "오래된 모습", 13, MUTED, "600", anchor="start"))
    b.append(t(lx + 12, 744, "가까운 곳", 13, MUTED, "600", anchor="start"))
    b.append(t(lx + 12, 764, "최근 모습", 13, MUTED, "600", anchor="start"))

    b.append(note_box(197, 906, 400, "깊이가 곧 나이다", 16))
    b.append(caption(W / 2, 1010, ["원의 크기는 가까울수록 크게 그린 것이고 실제 크기와는 무관하다."]))
    return base("한 화면, 여러 때",
                "한 화면에 여러 때의 모습이 겹쳐 있는 것을 나타낸 그림", "".join(b))


# ── p19 ───────────────────────────────────────────────────────────────
def fig_order():
    b = []
    b.append(t(W / 2, 240, "같은 때에 일어난 두 사건을 두 자리에서 보면", 16, MUTED))

    ay, by_ = 300, 300
    b.append(f'<line x1="150" y1="{ay}" x2="644" y2="{ay}" stroke="{SOFT}" stroke-width="2"/>')
    b.append(star(150, ay, 22, LIGHT))
    b.append(t(150, ay - 38, "사건 가", 15, INK, "700"))
    b.append(star(644, by_, 22, LIGHT))
    b.append(t(644, by_ - 38, "사건 나", 15, INK, "700"))
    b.append(t(397, ay - 38, "둘은 같은 때에 일어났다", 14, MUTED))

    obs = [(250, "왼쪽에서 본 사람", ["가", "나"]), (546, "오른쪽에서 본 사람", ["나", "가"])]
    for ox, label, order in obs:
        oy = 470
        b.append(person(ox, oy))
        b.append(t(ox, oy + 46, label, 14, INK, "600"))
        b.append(f'<line x1="150" y1="{ay + 16}" x2="{ox - 14}" y2="{oy - 42}" stroke="{FAR}" stroke-dasharray="4 5"/>')
        b.append(f'<line x1="644" y1="{by_ + 16}" x2="{ox + 14}" y2="{oy - 42}" stroke="{FAR}" stroke-dasharray="4 5"/>')

        axy = 620
        b.append(tick_axis(ox - 92, ox + 92, axy, "이름", "늦음"))
        first, second = order
        b.append(f'<circle cx="{ox - 52}" cy="{axy}" r="8" fill="{LIGHT}"/>')
        b.append(t(ox - 52, axy - 18, first, 14, INK, "700"))
        b.append(f'<circle cx="{ox + 52}" cy="{axy}" r="8" fill="{LIGHT}"/>')
        b.append(t(ox + 52, axy - 18, second, 14, INK, "700"))

    b.append(t(397, 704, "도착 순서가 다르다", 16, INK, "700"))
    b.append(t(397, 736, "거리를 빼면 같은 때로 돌아온다", 15, MUTED))

    b.append(note_box(197, 790, 400, "보이는 순서가 곧 일어난 순서는 아니다", 15))
    return base("어디서 보느냐에 따라 갈린다",
                "보는 자리에 따라 도착 순서가 달라지는 모습을 그린 그림", "".join(b))


# ── p26 ───────────────────────────────────────────────────────────────
def fig_cone():
    b = []
    b.append(t(W / 2, 240, "시간을 세로로, 거리를 가로로 놓으면", 16, MUTED))

    cx, cy = 397, 570
    half = 250
    b.append(f'<path d="M{cx},{cy} L{cx - half},{cy - half} L{cx + half},{cy - half} Z" '
             f'fill="#f3ecdf"/>')
    b.append(f'<path d="M{cx},{cy} L{cx - half},{cy + half} L{cx + half},{cy + half} Z" '
             f'fill="#eef2f4"/>')
    b.append(f'<line x1="{cx - half - 20}" y1="{cy}" x2="{cx + half + 20}" y2="{cy}" stroke="{LINE}"/>')
    b.append(f'<line x1="{cx}" y1="{cy - half - 34}" x2="{cx}" y2="{cy + half + 34}" stroke="{LINE}"/>')
    b.append(t(cx + 12, cy - half - 40, "나중", 13, MUTED, "600", anchor="start"))
    b.append(t(cx + 12, cy + half + 52, "먼저", 13, MUTED, "600", anchor="start"))
    b.append(t(cx + half + 26, cy + 5, "멀어짐", 13, MUTED, "600", anchor="start"))
    b.append(t(cx - half - 26, cy + 5, "멀어짐", 13, MUTED, "600", anchor="end"))

    for sx in (-1, 1):
        for sy in (-1, 1):
            b.append(f'<line x1="{cx}" y1="{cy}" x2="{cx + sx * half}" y2="{cy + sy * half}" '
                     f'stroke="{LIGHT}" stroke-width="3"/>')
    b.append(t(cx + half - 8, cy - half - 12, "빛이 지나간 길", 13, LIGHT, "700", anchor="end"))

    b.append(f'<circle cx="{cx}" cy="{cy}" r="7" fill="{INK}"/>')
    b.append(t(cx - 14, cy + 22, "지금 여기", 14, INK, "700", anchor="end"))

    b.append(t(cx, cy - half + 56, "영향을 줄 수 있는 자리", 15, "#7a6636", "700"))
    b.append(t(cx, cy + half - 40, "영향을 줄 수 있었던 자리", 15, "#4a5c66", "700"))

    b.append(star(cx + 190, cy - 24, 15, NONE))
    b.append(t(cx + 186, cy - 52, "이 사건과는", 13, NONE, "700"))
    b.append(t(cx + 186, cy + 8, "먼저와 나중이 없다", 13, NONE, "700"))
    b.append(t(cx - 176, cy - 24, "아무 관계도", 13, MUTED, "600"))
    b.append(t(cx - 176, cy - 4, "없는 자리", 13, MUTED, "600"))

    b.append(note_box(197, 880, 400, "기울기를 정하는 것이 빛의 빠르기다", 16))
    return base("닿는 자리와 닿지 않는 자리",
                "한 사건에서 영향이 닿는 범위를 나타낸 그림", "".join(b))


# ── p33 ───────────────────────────────────────────────────────────────
def fig_subtract():
    b = []
    b.append(t(W / 2, 240, "거울을 두 자리에 두고 각각 재면", 16, MUTED))

    rows = [(340, 190, "가까운 거울", 0.24), (510, 396, "먼 거울", 0.62)]
    for y, mirror_x, label, path_frac in rows:
        b.append(f'<rect x="130" y="{y - 30}" width="86" height="60" rx="8" fill="#eef2f4" '
                 f'stroke="{LINE}" stroke-width="2"/>')
        b.append(t(173, y + 5, "장치", 14, INK, "600"))
        mx = 216 + mirror_x
        b.append(f'<rect x="{mx}" y="{y - 40}" width="12" height="80" fill="{SOUND}"/>')
        b.append(t(mx + 6, y - 52, label, 14, INK, "600"))
        b.append(arrow(220, y - 14, mx - 6, y - 14, color=LIGHT, width=2))
        b.append(f'<line x1="{mx - 6}" y1="{y + 14}" x2="224" y2="{y + 14}" stroke="{LIGHT}" stroke-width="2"/>')
        b.append(f'<path d="M220,{y + 14} L232,{y + 8} L232,{y + 20} Z" fill="{LIGHT}"/>')

    b.append(f'<line x1="105" y1="632" x2="689" y2="632" stroke="{SOFT}"/>')
    b.append(t(W / 2, 674, "걸린 시간을 막대로 늘어놓으면", 16, MUTED))

    bars = [("가까운 거울", 120), ("먼 거울", 310)]
    y = 706
    fixed = 96
    for label, path_len in bars:
        b.append(t(240, y + 22, label, 14, INK, "600", anchor="end"))
        b.append(f'<rect x="252" y="{y}" width="{fixed}" height="30" fill="{SOUND}"/>')
        b.append(f'<rect x="{252 + fixed}" y="{y}" width="{path_len}" height="30" fill="{LIGHT}" opacity="0.55"/>')
        y += 54
    b.append(t(300, 696, "장치와 거울 안에서 드는 몫", 12, SOUND, "700"))
    b.append(t(500, 802, "길에서 드는 몫", 12, "#8d6a2f", "700"))

    y = 820
    b.append(t(240, y + 22, "차이", 14, INK, "700", anchor="end"))
    b.append(f'<rect x="252" y="{y}" width="190" height="30" fill="{LIGHT}" opacity="0.55"/>')
    b.append(t(456, y + 21, "빼면 길에서 드는 몫만 남는다", 14, INK, "600", anchor="start"))

    b.append(note_box(197, 900, 400, "같은 몫은 빼면 사라진다", 16))
    return base("두 번 재서 빼면 남는 것",
                "두 거리에서 재서 차이만 쓰는 방식을 그린 그림", "".join(b))


# ── p41 ───────────────────────────────────────────────────────────────
def fig_two_cases():
    b = []
    b.append(t(W / 2, 240, "어긋남에는 두 종류가 있다", 16, MUTED))

    # 위 칸 — 고칠 수 있는 어긋남
    b.append(f'<rect x="120" y="272" width="554" height="248" rx="12" fill="#f6f8f9" stroke="{SOFT}"/>')
    b.append(t(138, 302, "고칠 수 있는 어긋남", 16, INK, "700", anchor="start"))

    b.append(tick_axis(168, 336, 400, "이름", "늦음"))
    b.append(f'<circle cx="196" cy="400" r="8" fill="{LIGHT}"/>')
    b.append(t(196, 382, "나", 13, INK, "700"))
    b.append(f'<circle cx="308" cy="400" r="8" fill="{LIGHT}"/>')
    b.append(t(308, 382, "가", 13, INK, "700"))
    b.append(t(252, 452, "본 순서", 13, MUTED))

    b.append(arrow(370, 400, 442, 400, color=LINE, width=2, marker="gray"))
    b.append(t(406, 380, "거리를 빼면", 12, MUTED, "600"))

    b.append(tick_axis(474, 642, 400, "이름", "늦음"))
    b.append(f'<circle cx="502" cy="400" r="8" fill="{LIGHT}"/>')
    b.append(t(502, 382, "가", 13, INK, "700"))
    b.append(f'<circle cx="614" cy="400" r="8" fill="{LIGHT}"/>')
    b.append(t(614, 382, "나", 13, INK, "700"))
    b.append(t(558, 452, "일어난 순서", 13, MUTED))

    # 아래 칸 — 없는 답
    b.append(f'<rect x="120" y="540" width="554" height="270" rx="12" fill="#fbf6f4" stroke="#e5cec6"/>')
    b.append(t(138, 570, "없는 답", 16, NONE, "700", anchor="start"))

    b.append(star(214, 646, 20, LIGHT))
    b.append(t(214, 690, "사건 가", 13, INK, "600"))
    b.append(star(430, 646, 20, LIGHT))
    b.append(t(430, 690, "사건 나", 13, INK, "600"))
    b.append(f'<line x1="240" y1="646" x2="296" y2="646" stroke="{NONE}" stroke-dasharray="5 5" stroke-width="2"/>')
    b.append(f'<line x1="348" y1="646" x2="404" y2="646" stroke="{NONE}" stroke-dasharray="5 5" stroke-width="2"/>')
    for d in (-1, 1):
        b.append(f'<line x1="{322 - 12 * d}" y1="{646 - 12}" x2="{322 + 12 * d}" y2="{646 + 12}" '
                 f'stroke="{NONE}" stroke-width="4"/>')
    b.append(t(322, 604, "소식이 닿지 못한다", 13, NONE, "700"))

    b.append(tick_axis(506, 640, 646, "이름", "늦음"))
    b.append(t(534, 640, "?", 22, NONE, "700"))
    b.append(t(612, 640, "?", 22, NONE, "700"))
    b.append(t(573, 700, "정할 근거가 없다", 13, NONE, "700"))

    b.append(t(397, 762, "더 정밀하게 재도 아래쪽은 달라지지 않는다", 15, MUTED, "700"))

    b.append(note_box(197, 858, 400, "고칠 수 있는 것과 없는 것을 가른다", 15))
    return base("고칠 수 있는 어긋남과 없는 답",
                "두 종류의 사건 짝을 갈라 놓은 그림", "".join(b))


FIGURES = {7: fig_arrival, 13: fig_layers, 19: fig_order,
           26: fig_cone, 33: fig_subtract, 41: fig_two_cases}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}")


if __name__ == "__main__":
    main()
