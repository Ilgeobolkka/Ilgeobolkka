#!/usr/bin/env python3
"""book-062 이미지 페이지 6개의 SVG 생성. figures067.py의 t()/base() 패턴을 따른다.

조명을 다루는 책이라 여섯 도표 가운데 넷이 '같은 조건에서 값 하나만 바꾼 비교'다. 무대를 위에서
내려다본 평면과 옆에서 본 단면을 섞어 쓰되, 한 도표 안에서는 시점을 섞지 않는다. 빛이 닿는 영역은
노란 계열, 어둠은 회색 계열로 고정해 여섯 도표에서 같은 뜻으로 쓴다.

사용: python3 figures062.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
LIT = "#e8b843"           # 빛이 닿는 영역
LIT_SOFT = "#f6e6b8"
DARK = "#5b666c"          # 어둠에 남는 영역
COOL = "#4a7fa5"          # 색온도 눈금의 푸른 쪽
MUTED = "#55666b"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="lit" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LIT}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <radialGradient id="pool"><stop offset="0%" stop-color="{LIT_SOFT}"/><stop offset="100%" stop-color="#ffffff" stop-opacity="0"/></radialGradient>
  <linearGradient id="kelvin" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0%" stop-color="#c8763a"/><stop offset="35%" stop-color="#e0a765"/>
    <stop offset="55%" stop-color="#f2efe6"/><stop offset="78%" stop-color="#9dc0d8"/>
    <stop offset="100%" stop-color="#5b8fb5"/>
  </linearGradient>
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


def actor(x, y, scale=1.0, color=INK):
    """위에서 내려다본 배우 표시가 아니라 정면에서 본 사람 모양."""
    head = 9 * scale
    return (f'<circle cx="{x}" cy="{y - 26 * scale}" r="{head}" fill="{color}"/>'
            f'<path d="M{x - 11 * scale},{y + 16 * scale} L{x - 8 * scale},{y - 14 * scale} '
            f'L{x + 8 * scale},{y - 14 * scale} L{x + 11 * scale},{y + 16 * scale} z" fill="{color}"/>')


# ── p8 세기만 바꾼 세 무대 ────────────────────────────────────────────
def fig_intensity():
    b = []
    xs = [175, 397, 619]
    widths = [1.8, 4.2, 7.5]
    radii = [46, 92, 150]
    labels = ["배우만", "배우와 자리", "무대 전체"]
    top, bottom = 300, 610
    for i, (x, wgt, r, label) in enumerate(zip(xs, widths, radii, labels)):
        b.append(f'<rect x="{x - 96}" y="{top}" width="192" height="{bottom - top}" '
                 f'rx="6" fill="#eceff0" stroke="{SOFT}"/>')
        ay = top + 168
        # 빛 웅덩이가 무대 밖으로 새면 세 무대의 크기가 달라 보인다. 무대 안으로 잘라 둔다.
        b.append(f'<clipPath id="stage{i}"><rect x="{x - 96}" y="{top}" width="192" '
                 f'height="{bottom - top}" rx="6"/></clipPath>')
        b.append(f'<circle cx="{x}" cy="{ay}" r="{r}" fill="url(#pool)" clip-path="url(#stage{i})"/>')
        b.append(f'<line x1="{x - 78}" y1="{top + 34}" x2="{x - 26}" y2="{ay - 44}" '
                 f'stroke="{LIT}" stroke-width="{wgt}" marker-end="url(#lit)"/>')
        b.append(actor(x, ay, 1.0, INK if r < 100 else "#3d4a51"))
        b.append(t(x, bottom + 34, label, 17, INK, "600"))
    b.append(t(W / 2, 268, "방향과 자리는 셋이 같다 — 화살표 굵기만 다르다", 15, MUTED))
    b.append(note_box(147, 690, 500, "세기는 밝기가 아니라 보이는 범위를 정한다"))
    b.append(caption(W / 2, 812, [
        "약한 빛에서는 배우만 남고, 세기를 올릴수록 바닥과 벽이 함께 보인다.",
        "무엇을 어둠에 남길지는 방향이 아니라 이 값이 정한다.",
    ], 16))
    return base("세기만 바꾼 세 무대", "방향·위치를 고정하고 세기 하나만 세 단계로 바꾼 무대 평면",
                "".join(b))


# ── p14 방향에 따라 갈리는 얼굴 ────────────────────────────────────────
def fig_direction():
    b = []
    cx, cy = W / 2, 392
    b.append(f'<circle cx="{cx}" cy="{cy}" r="52" fill="none" stroke="{LINE}" stroke-width="2"/>')
    b.append(t(cx, cy + 7, "배우", 16, MUTED))
    arrows = [(-124, 0, "옆"), (0, 124, "앞"), (0, -124, "뒤")]
    for dx, dy, label in arrows:
        x1, y1 = cx + dx, cy + dy
        x2 = cx + dx * 0.45
        y2 = cy + dy * 0.45
        b.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{LIT}" '
                 f'stroke-width="4" marker-end="url(#lit)"/>')
        b.append(t(x1 + (22 if dx else 0), y1 + (5 if dx else (26 if dy > 0 else -14)), label, 16, MUTED))
    b.append(t(cx, 232, "위에서 내려다본 자리 — 세 빛의 세기는 같다", 15, MUTED))

    faces = [(175, "앞", "full"), (397, "옆", "half"), (619, "뒤", "rim")]
    fy = 690
    for x, label, kind in faces:
        b.append(f'<rect x="{x - 86}" y="{fy - 118}" width="172" height="236" rx="8" '
                 f'fill="#f2f4f5" stroke="{SOFT}"/>')
        if kind == "full":
            b.append(f'<ellipse cx="{x}" cy="{fy}" rx="56" ry="74" fill="{LIT_SOFT}" stroke="{LINE}"/>')
        elif kind == "half":
            b.append(f'<ellipse cx="{x}" cy="{fy}" rx="56" ry="74" fill="{DARK}" stroke="{LINE}"/>')
            b.append(f'<path d="M{x},{fy - 74} A56,74 0 0,0 {x},{fy + 74} z" fill="{LIT_SOFT}"/>')
            b.append(f'<line x1="{x}" y1="{fy - 74}" x2="{x}" y2="{fy + 74}" stroke="{INK}" stroke-width="2"/>')
        else:
            b.append(f'<ellipse cx="{x}" cy="{fy}" rx="56" ry="74" fill="#2f3a40" stroke="{LIT}" stroke-width="3"/>')
        b.append(t(x, fy + 152, label, 18, INK, "700"))
    b.append(note_box(147, 878, 500, "같은 세기, 다른 자리"))
    b.append(caption(W / 2, 1000, [
        "앞은 표정을 읽게 하고, 옆은 굴곡을 만들고, 뒤는 윤곽만 남긴다.",
        "방향 하나가 얼굴에서 무엇이 보일지를 먼저 정한다.",
    ], 16))
    return base("방향에 따라 갈리는 얼굴", "같은 세기의 빛을 앞·옆·뒤 세 자리에 놓았을 때의 얼굴",
                "".join(b))


# ── p23 같은 장면, 다른 대비 ──────────────────────────────────────────
def fig_contrast():
    b = []
    stages = [(232, "관계가 보인다", False), (562, "결정이 보인다", True)]
    top, bottom = 300, 640
    for x, label, high in stages:
        b.append(f'<rect x="{x - 130}" y="{top}" width="260" height="{bottom - top}" rx="6" '
                 f'fill="{"#2b3439" if high else "#e4e8ea"}" stroke="{SOFT}"/>')
        ay = top + 200
        if high:
            b.append(f'<circle cx="{x}" cy="{ay}" r="76" fill="url(#pool)"/>')
        for i, ox in enumerate((-82, 0, 82)):
            if high:
                color = "#f0e2b4" if i == 1 else "#48555b"
            else:
                color = "#3d4a51"
            b.append(actor(x + ox, ay, 1.05, color))
        b.append(t(x, bottom + 36, label, 17, INK, "600"))
    b.append(f'<line x1="{W / 2}" y1="{top + 60}" x2="{W / 2}" y2="{bottom - 60}" '
             f'stroke="{LINE}" stroke-width="1.4" stroke-dasharray="5 5"/>')
    b.append(t(W / 2, top + 44, "낮은 대비", 14, MUTED))
    b.append(t(W / 2, bottom - 34, "높은 대비", 14, MUTED))
    b.append(t(W / 2, 268, "배우 세 명의 자리는 두 무대가 같다", 15, MUTED))
    b.append(note_box(147, 724, 500, "무엇을 동시에 보일 것인가"))
    b.append(caption(W / 2, 846, [
        "대비를 낮추면 세 사람의 관계가 보이고, 올리면 한 사람의 결정만 남는다.",
        "보기 좋은 비율이 따로 있는 것이 아니라 장면이 몇 가지를 보여야 하는지가 정한다.",
    ], 16))
    return base("같은 장면, 다른 대비", "배우 배치를 고정하고 밝은 쪽과 어두운 쪽의 비율만 바꾼 무대",
                "".join(b))


# ── p30 하나의 눈금 위에 놓인 빛들 ─────────────────────────────────────
def fig_kelvin():
    b = []
    x0, x1, y = 128, 666, 372
    b.append(f'<rect x="{x0}" y="{y - 22}" width="{x1 - x0}" height="44" rx="10" fill="url(#kelvin)"/>')
    b.append(f'<line x1="{x0}" y1="{y + 52}" x2="{x1}" y2="{y + 52}" stroke="{LINE}" '
             f'stroke-width="1.6" marker-end="url(#gray)"/>')
    b.append(t(x1 - 6, y + 78, "값이 커지는 쪽", 14, MUTED, anchor="end"))
    b.append(t(x0 + 6, y - 42, "붉은 기", 15, "#a9673a", anchor="start"))
    b.append(t(x1 - 6, y - 42, "푸른 기", 15, COOL, anchor="end"))

    lamps = [(0.06, "촛불", "#c8763a"), (0.27, "백열 계열", "#dfa464"),
             (0.5, "무대 표준 흰빛", "#e8e4d8"), (0.74, "주광 계열", "#9dc0d8"),
             (0.93, "흐린 하늘빛", "#5b8fb5")]
    for i, (frac, label, color) in enumerate(lamps):
        cx = x0 + (x1 - x0) * frac
        by = y + 132 + (i % 2) * 96
        b.append(f'<line x1="{cx}" y1="{y + 26}" x2="{cx}" y2="{by - 34}" stroke="{SOFT}" stroke-width="1.4"/>')
        b.append(f'<rect x="{cx - 34}" y="{by - 34}" width="68" height="68" rx="10" '
                 f'fill="{color}" stroke="{LINE}"/>')
        b.append(t(cx, by + 60, label, 15, INK))
    b.append(note_box(147, 726, 500, "같은 흰빛이 아니다"))
    b.append(caption(W / 2, 848, [
        "무대에서 흰빛이라 부르는 광원들도 눈금 위 자리가 서로 다르다.",
        "값을 모르고 섞으면 배우의 얼굴색이 자리마다 달라진다.",
    ], 16))
    return base("하나의 눈금 위에 놓인 빛들", "무대에서 쓰는 광원을 색온도 축 위에 늘어놓은 그림",
                "".join(b))


# ── p38 같은 곳에 닿는 세 가지 속도 ────────────────────────────────────
def fig_fade():
    b = []
    x0, x1 = 150, 650
    y0, y1 = 640, 300
    b.append(f'<line x1="{x0}" y1="{y0}" x2="{x1 + 20}" y2="{y0}" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(f'<line x1="{x0}" y1="{y0}" x2="{x0}" y2="{y1 - 30}" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(t(x0 - 14, y1 - 4, "밝기", 15, MUTED, anchor="end"))
    b.append(t(x0, y0 + 30, "큐 시작", 14, MUTED))
    b.append(t(x1, y0 + 30, "큐 끝", 14, MUTED))

    def curve(power, color, dash=""):
        pts = []
        for i in range(0, 41):
            f = i / 40
            v = f ** power
            pts.append(f"{x0 + (x1 - x0) * f:.1f},{y0 - (y0 - y1) * v:.1f}")
        d = f' stroke-dasharray="{dash}"' if dash else ""
        return f'<polyline points="{" ".join(pts)}" fill="none" stroke="{color}" stroke-width="3"{d}/>'

    b.append(curve(0.42, LIT))
    b.append(curve(1.0, INK, "7 5"))
    b.append(curve(2.6, COOL))
    b.append(f'<circle cx="{x0}" cy="{y0}" r="5" fill="{INK}"/>')
    b.append(f'<circle cx="{x1}" cy="{y1}" r="5" fill="{INK}"/>')
    b.append(t(x1 + 24, y1 - 46, "먼저 밝아지고 기다린다", 14, "#a8801f", anchor="end"))
    b.append(t(x1 + 6, y1 + 116, "고르게 올라간다", 14, INK, anchor="end"))
    b.append(t(x1 - 30, y0 - 58, "늦게 밝아진다", 14, COOL, anchor="end"))
    b.append(t(W / 2, 268, "세 선의 시작점과 끝점은 완전히 같다", 15, MUTED))
    b.append(note_box(147, 726, 500, "같은 시간, 다른 순간"))
    b.append(caption(W / 2, 848, [
        "전환에 쓴 시간이 같아도 관객이 변화를 느끼는 시점은 달라진다.",
        "알아채야 하는 전환인지 아닌지가 어느 선을 고를지를 정한다.",
    ], 16))
    return base("같은 곳에 닿는 세 가지 속도", "시작과 끝이 같은 세 전환 곡선을 겹쳐 그린 그림",
                "".join(b))


# ── p46 천장이 정하는 각도 ────────────────────────────────────────────
def fig_ceiling():
    b = []
    panels = [(232, 240, "높은 천장"), (562, 470, "낮은 천장")]
    floor = 630
    for cx, ceiling, label in panels:
        b.append(f'<rect x="{cx - 140}" y="{ceiling}" width="280" height="{floor - ceiling}" '
                 f'fill="#f2f4f5" stroke="{SOFT}"/>')
        b.append(f'<line x1="{cx - 140}" y1="{ceiling}" x2="{cx + 140}" y2="{ceiling}" '
                 f'stroke="{INK}" stroke-width="3"/>')
        b.append(f'<line x1="{cx - 140}" y1="{floor}" x2="{cx + 140}" y2="{floor}" '
                 f'stroke="{INK}" stroke-width="3"/>')
        # 등기구는 두 극장 모두 같은 가로 자리에 둔다
        lx = cx - 130
        b.append(f'<rect x="{lx - 13}" y="{ceiling}" width="26" height="20" fill="{INK}"/>')
        # 배우도 두 극장 모두 같은 자리·크기
        ax = cx + 10
        b.append(actor(ax, floor - 4, 2.0, "#3d4a51"))
        head_y = floor - 4 - 52
        b.append(f'<line x1="{lx}" y1="{ceiling + 22}" x2="{ax}" y2="{head_y}" '
                 f'stroke="{LIT}" stroke-width="3" marker-end="url(#lit)"/>')
        # 머리를 지나 바닥에 닿는 그림자 끝
        dx, dy = ax - lx, head_y - (ceiling + 22)
        shadow_x = ax + dx * ((floor - head_y) / dy)
        b.append(f'<line x1="{ax}" y1="{head_y}" x2="{min(shadow_x, cx + 138):.1f}" y2="{floor}" '
                 f'stroke="{LINE}" stroke-width="1.4" stroke-dasharray="5 4"/>')
        b.append(f'<line x1="{ax}" y1="{floor}" x2="{min(shadow_x, cx + 138):.1f}" y2="{floor}" '
                 f'stroke="{DARK}" stroke-width="7"/>')
        b.append(t(cx, floor + 40, label, 17, INK, "600"))
    b.append(t(W / 2, 226, "등기구 자리와 배우 자리는 두 극장이 같다", 15, MUTED))
    b.append(note_box(147, 724, 500, "같은 설계가 같은 결과를 주지 않는다"))
    b.append(caption(W / 2, 846, [
        "천장이 낮아지면 내려오는 각이 눕고 바닥 그림자가 객석 쪽으로 길어진다.",
        "옮길 때 이어지는 것은 값이 아니라 그 장면이 지켜야 할 의도다.",
    ], 16))
    return base("천장이 정하는 각도", "같은 설계를 천장 높이가 다른 두 극장에 놓은 단면",
                "".join(b))


FIGURES = {8: fig_intensity, 14: fig_direction, 23: fig_contrast,
           30: fig_kelvin, 38: fig_fade, 46: fig_ceiling}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild062")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
