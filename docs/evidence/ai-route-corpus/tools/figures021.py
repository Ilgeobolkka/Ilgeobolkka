#!/usr/bin/env python3
"""book-021 이미지 페이지 6개의 SVG 생성. figures062.py의 t()/base() 패턴을 따른다.

분광을 다루는 책이라 여섯 도표 가운데 넷이 가로축을 파장으로 하는 그래프다. 파장은 언제나 왼쪽이
짧은 쪽이고, 흡수는 아래로 파인 홈, 방출은 위로 솟은 봉우리로 고정해 여섯 도표에서 같은 뜻으로 쓴다.

사용: python3 figures021.py <출력디렉터리>
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
CURVE = "#3f6f8c"          # 스펙트럼 곡선
MARK = "#b4703a"           # 선·강조
FAR = "#8b95a1"            # 먼 대상·배경


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


def hump(x0, x1, ybase, height, peak=0.42, n=90):
    """연속 스펙트럼의 완만한 언덕."""
    pts = []
    for i in range(n + 1):
        f = i / n
        v = math.exp(-((f - peak) ** 2) / 0.06)
        pts.append(f"{x0 + (x1 - x0) * f:.1f},{ybase - height * v:.1f}")
    return " ".join(pts)


# ── p8 언덕은 온도, 선은 성분 ─────────────────────────────────────────
def fig_spectrum_lines():
    b = []
    x0, x1 = 150, 650
    rows = [(320, "none", "연속 스펙트럼만"), (470, "abs", "흡수선"), (620, "emi", "방출선")]
    marks = [0.24, 0.36, 0.58, 0.71]
    for ybase, kind, label in rows:
        b.append(f'<line x1="{x0}" y1="{ybase}" x2="{x1 + 14}" y2="{ybase}" stroke="{LINE}" stroke-width="1.2"/>')
        b.append(f'<polyline points="{hump(x0, x1, ybase, 84)}" fill="none" stroke="{CURVE}" stroke-width="2.6"/>')
        for f in marks:
            mx = x0 + (x1 - x0) * f
            my = ybase - 84 * math.exp(-((f - 0.42) ** 2) / 0.06)
            if kind == "abs":
                depth = min(34, ybase - my - 4)
                b.append(f'<line x1="{mx}" y1="{my}" x2="{mx}" y2="{my + depth:.1f}" stroke="{MARK}" stroke-width="2.6"/>')
            elif kind == "emi":
                b.append(f'<line x1="{mx}" y1="{my}" x2="{mx}" y2="{my - 40}" stroke="{MARK}" stroke-width="2.6"/>')
        b.append(t(x1 + 24, ybase - 30, label, 15, INK, "600", anchor="start"))
    b.append(t(x0, 668, "짧은 파장", 14, MUTED, anchor="start"))
    b.append(t(x1, 668, "긴 파장", 14, MUTED, anchor="end"))
    b.append(t(W / 2, 232, "세 그래프의 언덕 모양은 완전히 같다", 15, MUTED))
    b.append(note_box(147, 720, 500, "같은 원소가 가리기도 하고 빛나기도 한다"))
    b.append(caption(W / 2, 842, [
        "언덕의 봉우리 자리는 그 대상이 얼마나 뜨거운지를 말한다.",
        "그 위의 홈과 봉우리는 무엇이 그 빛을 지났는지를 말한다.",
    ], 16))
    return base("언덕은 온도, 선은 성분", "같은 연속 스펙트럼 위에 선이 없는 경우와 흡수·방출선이 난 경우",
                "".join(b))


# ── p14 자리는 같고 모양이 다르다 ─────────────────────────────────────
def fig_line_shape():
    b = []
    x0, x1 = 150, 650
    cx = (x0 + x1) / 2
    rows = [(330, 34, 10, "얕다"), (470, 78, 10, "깊다"), (610, 78, 30, "넓다")]
    for ybase, depth, half, label in rows:
        pts = []
        for i in range(101):
            f = i / 100
            x = x0 + (x1 - x0) * f
            d = depth * math.exp(-(((x - cx) / half) ** 2))
            pts.append(f"{x:.1f},{ybase + d:.1f}")
        b.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{CURVE}" stroke-width="2.6"/>')
        b.append(t(x1 + 20, ybase + 6, label, 16, INK, "600", anchor="start"))
    b.append(f'<line x1="{cx}" y1="300" x2="{cx}" y2="700" stroke="{MARK}" stroke-width="1.4" stroke-dasharray="5 5"/>')
    b.append(t(cx, 288, "같은 자리", 14, MARK, "700"))
    b.append(note_box(147, 740, 500, "자리가 무엇인지를, 모양이 얼마나를 말한다"))
    b.append(caption(W / 2, 862, [
        "선의 자리는 어느 원소인지를 정하고, 깊이와 폭은 얼마나 있고 어떤 상태인지를 말한다.",
        "깊이는 어느 지점을 넘으면 더 깊어지지 않아 폭까지 함께 본다.",
    ], 16))
    return base("자리는 같고 모양이 다르다", "같은 파장의 흡수선이 깊이와 폭에서 갈리는 세 경우",
                "".join(b))


# ── p23 배치는 그대로, 자리만 옮긴다 ──────────────────────────────────
def fig_doppler():
    b = []
    x0, x1 = 150, 650
    rows = [(320, -46, "다가온다"), (450, 0, "실험실에서 잰 자리"), (580, 52, "멀어진다")]
    offsets = [0.22, 0.34, 0.52, 0.66]
    for ybase, shift, label in rows:
        b.append(f'<rect x="{x0}" y="{ybase - 26}" width="{x1 - x0}" height="52" fill="#f4f6f7" stroke="{SOFT}"/>')
        for f in offsets:
            mx = x0 + (x1 - x0) * f + shift
            b.append(f'<line x1="{mx}" y1="{ybase - 26}" x2="{mx}" y2="{ybase + 26}" stroke="{MARK}" stroke-width="2.6"/>')
        b.append(t(x1 + 20, ybase + 6, label, 15, INK, "600", anchor="start"))
    for i, (ybase, shift, _) in enumerate(rows[:-1]):
        nxt = rows[i + 1]
        b.append(f'<line x1="{x0 + (x1 - x0) * offsets[0] + shift}" y1="{ybase + 28}" '
                 f'x2="{x0 + (x1 - x0) * offsets[0] + nxt[1]}" y2="{nxt[0] - 28}" '
                 f'stroke="{LINE}" stroke-width="1.2" stroke-dasharray="4 4"/>')
    b.append(t(x0, 640, "짧은 파장", 14, MUTED, anchor="start"))
    b.append(t(x1, 640, "긴 파장", 14, MUTED, anchor="end"))
    b.append(t(W / 2, 282, "세 띠의 선 간격은 완전히 같다", 15, MUTED))
    b.append(note_box(147, 700, 500, "간격은 원소를, 자리는 속도를 말한다"))
    b.append(caption(W / 2, 822, [
        "선 전체가 같은 방향으로 밀리면 그 대상이 다가오거나 멀어지고 있다는 뜻이다.",
        "밀린 양을 재려면 그 선이 무슨 원소의 것인지 먼저 정해져 있어야 한다.",
    ], 16))
    return base("배치는 그대로, 자리만 옮긴다", "같은 원소의 선 배치가 세 경우에 놓이는 자리",
                "".join(b))


# ── p30 기선이 길수록 각이 커진다 ─────────────────────────────────────
def fig_baseline():
    b = []
    tx, ty = W / 2, 300
    b.append(f'<circle cx="{tx}" cy="{ty}" r="8" fill="{INK}"/>')
    b.append(t(tx, ty - 20, "대상", 15, MUTED))
    # 두 기선은 반드시 같은 높이에 둔다. 높이를 달리하면 대상까지의 거리가 서로 달라져,
    # 기선 길이만으로 각이 갈린다는 이 그림의 주장이 그림 안에서 무너진다.
    # 두 각의 호를 같은 반지름으로 그리면 겹쳐 하나로 보인다. 반지름을 벌려 각각 읽히게 한다.
    by = 700
    setups = [(70, "짧은 기선", "작은 각", 52, -18), (210, "긴 기선", "큰 각", 96, 30)]
    for half, blabel, alabel, r, loff in setups:
        b.append(f'<line x1="{tx - half}" y1="{by}" x2="{tx + half}" y2="{by}" stroke="{INK}" stroke-width="2.6"/>')
        for s in (-1, 1):
            b.append(f'<circle cx="{tx + s * half}" cy="{by}" r="5" fill="{INK}"/>')
            b.append(f'<line x1="{tx + s * half}" y1="{by}" x2="{tx}" y2="{ty + 8}" stroke="{LINE}" stroke-width="1.6"/>')
        ang = math.atan2(by - ty, half)
        b.append(f'<path d="M{tx - r * math.cos(ang):.1f},{ty + r * math.sin(ang):.1f} '
                 f'A{r},{r} 0 0,0 {tx + r * math.cos(ang):.1f},{ty + r * math.sin(ang):.1f}" '
                 f'fill="none" stroke="{MARK}" stroke-width="2.2"/>')
        b.append(t(tx, by + loff, blabel, 16, INK, "600"))
        b.append(t(tx + r * math.cos(ang) + 12, ty + r * math.sin(ang) + 6, alabel, 14, MARK, "700", anchor="start"))
    b.append(t(W / 2, 236, "대상까지의 거리는 두 경우가 같다", 15, MUTED))
    b.append(note_box(147, 780, 500, "같은 거리라도 기선이 정한다"))
    b.append(caption(W / 2, 902, [
        "거리는 우리가 정할 수 없으므로 키울 수 있는 것은 두 관측 지점 사이의 거리뿐이다.",
        "기선을 키우면 각은 커지지만 두 관측 사이의 시간도 함께 길어진다.",
    ], 16))
    return base("기선이 길수록 각이 커진다", "같은 대상을 짧은 기선과 긴 기선에서 본 그림", "".join(b))


# ── p38 대상만 찍어서는 읽을 수 없다 ──────────────────────────────────
def fig_calibration():
    b = []
    x0, x1 = 150, 560
    rows = [(300, "bias"), (400, "flat"), (500, "lamp"), (600, "target")]
    labels = ["검출기만", "고른 빛", "자리를 아는 광원", "관측 대상"]
    for (y, kind), label in zip(rows, labels):
        b.append(f'<rect x="{x0}" y="{y - 30}" width="{x1 - x0}" height="60" fill="#f4f6f7" stroke="{SOFT}"/>')
        if kind == "bias":
            pts = []
            for i in range(81):
                f = i / 80
                v = 4 * math.sin(f * 37) + 3 * math.sin(f * 11)
                pts.append(f"{x0 + (x1 - x0) * f:.1f},{y + v:.1f}")
            b.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{LINE}" stroke-width="1.6"/>')
        elif kind == "flat":
            b.append(f'<rect x="{x0 + 6}" y="{y - 20}" width="{x1 - x0 - 12}" height="40" fill="{CURVE}" opacity="0.28"/>')
        elif kind == "lamp":
            for f in (0.16, 0.3, 0.44, 0.58, 0.72, 0.86):
                mx = x0 + (x1 - x0) * f
                b.append(f'<line x1="{mx}" y1="{y - 24}" x2="{mx}" y2="{y + 24}" stroke="{MARK}" stroke-width="2.8"/>')
        else:
            b.append(f'<polyline points="{hump(x0 + 6, x1 - 6, y + 24, 44)}" fill="none" stroke="{CURVE}" stroke-width="2.4"/>')
            for f in (0.3, 0.46, 0.64):
                mx = x0 + 6 + (x1 - x0 - 12) * f
                b.append(f'<line x1="{mx}" y1="{y + 2}" x2="{mx}" y2="{y + 22}" stroke="{MARK}" stroke-width="2.2"/>')
        b.append(t(x1 + 18, y + 6, label, 15, INK, "600", anchor="start"))
    b.append(f'<line x1="{x1 + 130}" y1="330" x2="{x1 + 130}" y2="600" stroke="{LINE}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(note_box(147, 690, 500, "앞의 셋이 있어야 넷째를 읽는다"))
    b.append(caption(W / 2, 812, [
        "대상만 찍으면 별의 빛과 장비의 성질과 대기의 몫이 섞인 채로 남는다.",
        "이 절차를 건너뛴 밤의 자료는 나중에 되살릴 방법이 없다.",
    ], 16))
    return base("대상만 찍어서는 읽을 수 없다", "하룻밤에 함께 찍는 네 종류의 자료", "".join(b))


# ── p46 같은 방향, 다른 거리 ──────────────────────────────────────────
def fig_line_of_sight():
    b = []
    ox, oy = 150, 620
    b.append(f'<circle cx="{ox}" cy="{oy}" r="9" fill="{INK}"/>')
    b.append(t(ox, oy + 30, "관측자", 15, MUTED))
    ang = -0.5
    for spread in (-0.055, 0.055):
        ex = ox + 470 * math.cos(ang + spread)
        ey = oy + 470 * math.sin(ang + spread)
        b.append(f'<line x1="{ox}" y1="{oy}" x2="{ex:.1f}" y2="{ey:.1f}" stroke="{SOFT}" stroke-width="1.6"/>')
    near = (ox + 190 * math.cos(ang), oy + 190 * math.sin(ang))
    far = (ox + 430 * math.cos(ang), oy + 430 * math.sin(ang))
    for (px, py), label, color in ((near, "가까운 대상", INK), (far, "훨씬 먼 대상", FAR)):
        b.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="9" fill="{color}"/>')
        b.append(t(px + 14, py - 16, label, 14, color, "600", anchor="start"))
        b.append(f'<line x1="{ox}" y1="{oy}" x2="{px:.1f}" y2="{py:.1f}" stroke="{color}" stroke-width="1.4"/>')
    b.append(f'<rect x="470" y="700" width="180" height="120" fill="#f4f6f7" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<circle cx="560" cy="760" r="9" fill="{INK}"/>')
    b.append(t(560, 842, "기록에는 하나로 남는다", 15, INK, "600"))
    b.append(t(W / 2, 262, "망원경이 보는 좁은 방향 안에 두 대상이 놓여 있다", 15, MUTED))
    b.append(note_box(147, 890, 500, "같은 방향에 있다고 가까운 것은 아니다"))
    b.append(caption(W / 2, 1012, [
        "방향만으로 묶으면 무관한 두 대상이 한 무리가 된다.",
        "거리와 시선속도를 함께 보고 판정한다.",
    ], 16))
    return base("같은 방향, 다른 거리", "한 시선 위에 놓인 두 대상이 하나로 기록되는 그림", "".join(b))


FIGURES = {8: fig_spectrum_lines, 14: fig_line_shape, 23: fig_doppler,
           30: fig_baseline, 38: fig_calibration, 46: fig_line_of_sight}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild021")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
