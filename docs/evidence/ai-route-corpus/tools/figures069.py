#!/usr/bin/env python3
"""book-069 이미지 페이지 6개의 SVG 생성. figures062.py의 t()/base() 패턴을 따른다.

프레이밍을 다루는 책이라 여섯 도표가 모두 '화면의 테두리'를 실선 직사각형으로 그린다. 자를 후보는
점선 직사각형, 시선과 눈이 지나가는 길은 점선 화살표로 고정해 여섯 도표에서 같은 뜻으로 쓴다.

사용: python3 figures069.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
SUBJ = "#4a5b64"          # 주인공
FILL = "#e7eef1"          # 비어 있는 자리
GAZE = "#b4703a"          # 시선·경로


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="gaze" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{GAZE}"/></marker>
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


def frame(x, y, w, h, dashed=False, color=INK):
    d = ' stroke-dasharray="7 5"' if dashed else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="none" stroke="{color}" '
            f'stroke-width="{1.8 if dashed else 2.4}"{d}/>')


def person(x, y, s=1.0, facing=1, color=SUBJ):
    """옆모습 실루엣. facing=1이면 오른쪽, -1이면 왼쪽을 본다."""
    head = 11 * s
    nose = f'<path d="M{x + facing * head},{y - 30 * s} l{facing * 7 * s},{4 * s} l{-facing * 7 * s},{4 * s} z" fill="{color}"/>'
    return (f'<circle cx="{x}" cy="{y - 32 * s}" r="{head}" fill="{color}"/>' + nose
            + f'<path d="M{x - 13 * s},{y + 26 * s} L{x - 9 * s},{y - 18 * s} '
              f'L{x + 9 * s},{y - 18 * s} L{x + 13 * s},{y + 26 * s} z" fill="{color}"/>')


# ── p8 보는 쪽을 비워 두기 ────────────────────────────────────────────
def fig_gaze_space():
    b = []
    w, h, y = 240, 180, 320
    lefts = [148, 406]
    for x, px, label in ((lefts[0], 0.28, "보는 쪽이 열려 있다"), (lefts[1], 0.76, "보는 쪽이 막혀 있다")):
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#ffffff"/>')
        if px < 0.5:
            b.append(f'<rect x="{x + w * px + 24}" y="{y}" width="{w * (1 - px) - 24}" height="{h}" fill="{FILL}"/>')
        b.append(frame(x, y, w, h))
        cx = x + w * px
        b.append(person(cx, y + h * 0.72))
        b.append(f'<line x1="{cx + 22}" y1="{y + h * 0.42}" x2="{x + w - 10}" y2="{y + h * 0.42}" '
                 f'stroke="{GAZE}" stroke-width="2" stroke-dasharray="6 4" marker-end="url(#gaze)"/>')
        b.append(t(x + w / 2, y + h + 34, label, 16, INK, "600"))
    b.append(t(W / 2, 282, "두 화면의 인물 크기와 보는 방향은 같다", 15, MUTED))
    b.append(note_box(147, 620, 500, "시선은 공간을 요구한다"))
    b.append(caption(W / 2, 742, [
        "인물이 보는 쪽에 자리가 없으면 시선이 가장자리에 부딪혀 답답해 보인다.",
        "같은 크기의 빈 공간도 앞에 있느냐 뒤에 있느냐에 따라 다르게 읽힌다.",
    ], 16))
    return base("보는 쪽을 비워 두기", "인물의 시선 앞 여백이 있는 경우와 없는 경우", "".join(b))


# ── p14 절반만 걸린 자리 ──────────────────────────────────────────────
def fig_edge_cut():
    b = []
    w, h, y = 170, 150, 330
    lefts = [110, 312, 514]
    modes = ["half", "out", "in"]
    labels = ["절반만 걸림", "완전히 뺌", "완전히 넣음"]
    for x, mode, label in zip(lefts, modes, labels):
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#ffffff"/>')
        b.append(f'<circle cx="{x + w / 2}" cy="{y + h / 2}" r="26" fill="{SUBJ}"/>')
        if mode == "half":
            b.append(f'<rect x="{x - 22}" y="{y + 34}" width="44" height="52" fill="{LINE}"/>')
            b.append(f'<line x1="{x + 26}" y1="{y + 60}" x2="{x + w / 2 - 32}" y2="{y + h / 2 - 4}" '
                     f'stroke="{GAZE}" stroke-width="2" stroke-dasharray="6 4" marker-end="url(#gaze)"/>')
        elif mode == "in":
            b.append(f'<rect x="{x + 16}" y="{y + 34}" width="44" height="52" fill="{LINE}"/>')
        b.append(frame(x, y, w, h))
        b.append(t(x + w / 2, y + h + 34, label, 15, INK, "600"))
    b.append(t(W / 2, 292, "가운데 원은 세 화면이 같은 주인공이다", 15, MUTED))
    b.append(note_box(147, 600, 500, "절반이 가장 나쁘다"))
    b.append(caption(W / 2, 722, [
        "절반만 걸린 것은 눈이 완성하려 들어 주인공에게서 시선을 빼앗는다.",
        "완전히 빼거나 완전히 넣는 두 선택지 가운데 하나를 고른다.",
    ], 16))
    return base("절반만 걸린 자리", "같은 대상을 가장자리에서 세 가지로 처리한 그림", "".join(b))


# ── p23 눈이 지나가는 길 ──────────────────────────────────────────────
def fig_leading_line():
    b = []
    w, h, y = 240, 200, 310
    lefts = [148, 406]
    for i, (x, label) in enumerate(((lefts[0], "길이 있다"), (lefts[1], "길이 없다"))):
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#ffffff"/>')
        sx, sy = x + w * 0.76, y + h * 0.26
        if i == 0:
            b.append(f'<line x1="{x + 14}" y1="{y + h - 16}" x2="{sx - 10}" y2="{sy + 14}" '
                     f'stroke="{LINE}" stroke-width="6"/>')
            b.append(f'<line x1="{x + 26}" y1="{y + h - 26}" x2="{sx - 18}" y2="{sy + 12}" '
                     f'stroke="{GAZE}" stroke-width="2" stroke-dasharray="6 4" marker-end="url(#gaze)"/>')
        else:
            spots = [(0.22, 0.72), (0.44, 0.34), (0.3, 0.5), (0.6, 0.78), (0.5, 0.2)]
            for fx, fy in spots:
                b.append(f'<rect x="{x + w * fx}" y="{y + h * fy}" width="16" height="16" fill="{LINE}" opacity="0.7"/>')
            path = f"M{x + 20},{y + h - 20} L{x + w * 0.24},{y + h * 0.74} L{x + w * 0.46},{y + h * 0.38} " \
                   f"L{x + w * 0.32},{y + h * 0.54} L{x + w * 0.62},{y + h * 0.8} L{sx - 12},{sy + 12}"
            b.append(f'<path d="{path}" fill="none" stroke="{GAZE}" stroke-width="2" '
                     f'stroke-dasharray="6 4" marker-end="url(#gaze)"/>')
        b.append(f'<circle cx="{sx}" cy="{sy}" r="18" fill="{SUBJ}"/>')
        b.append(frame(x, y, w, h))
        b.append(t(x + w / 2, y + h + 34, label, 16, INK, "600"))
    b.append(t(W / 2, 272, "주인공의 자리는 두 화면이 같다", 15, MUTED))
    b.append(note_box(147, 620, 500, "눈은 선을 따라 걷는다"))
    b.append(caption(W / 2, 742, [
        "화면 안의 선이 주인공에게 닿으면 시선이 곧장 도착한다.",
        "선이 없으면 눈이 여러 번 꺾이며 헤매다 늦게 닿는다.",
    ], 16))
    return base("눈이 지나가는 길", "유도선이 있는 화면과 없는 화면", "".join(b))


# ── p30 같은 장면, 다른 비율 ──────────────────────────────────────────
def fig_ratio():
    b = []
    h, y = 200, 310
    widths = [250, 180, 120]
    labels = ["앞이 넓다", "균형", "앞이 좁다"]
    x = 120
    for wdt, label in zip(widths, labels):
        b.append(f'<rect x="{x}" y="{y}" width="{wdt}" height="{h}" fill="#ffffff"/>')
        cx = x + 44
        b.append(f'<rect x="{cx + 20}" y="{y}" width="{max(0, wdt - 64)}" height="{h}" fill="{FILL}"/>')
        b.append(person(cx, y + h * 0.74))
        b.append(f'<line x1="{cx + 22}" y1="{y + h * 0.4}" x2="{x + wdt - 8}" y2="{y + h * 0.4}" '
                 f'stroke="{GAZE}" stroke-width="2" stroke-dasharray="6 4" marker-end="url(#gaze)"/>')
        b.append(frame(x, y, wdt, h))
        b.append(t(x + wdt / 2, y + h + 34, label, 15, INK, "600"))
        x += wdt + 24
    b.append(t(W / 2, 272, "인물의 크기와 자리는 세 화면이 같다", 15, MUTED))
    b.append(note_box(147, 620, 500, "비율이 여백을 다시 나눈다"))
    b.append(caption(W / 2, 742, [
        "가로를 줄이면 시선 앞의 자리가 함께 줄어든다.",
        "그래서 비율은 자를 때가 아니라 찍기 전에 정하는 편이 낫다.",
    ], 16))
    return base("같은 장면, 다른 비율", "같은 배치를 세 가지 비율로 자른 그림", "".join(b))


# ── p38 순서가 만드는 이야기 ──────────────────────────────────────────
def fig_sequence():
    b = []
    w, h = 150, 110

    def wide(x, y):
        return (f'<rect x="{x + 12}" y="{y + h - 42}" width="{w - 24}" height="6" fill="{LINE}"/>'
                f'<path d="M{x + 20},{y + h - 42} l24,-30 l24,30 z" fill="{LINE}" opacity="0.7"/>'
                f'<circle cx="{x + w - 34}" cy="{y + 34}" r="10" fill="{LINE}" opacity="0.5"/>')

    def face(x, y):
        return person(x + w / 2, y + h * 0.8, 1.1)

    def obj(x, y):
        return (f'<rect x="{x + w / 2 - 18}" y="{y + h / 2 - 14}" width="36" height="28" rx="4" fill="{SUBJ}"/>'
                f'<path d="M{x + w / 2 - 34},{y + h / 2 + 22} q16,-12 34,-10" fill="none" stroke="{LINE}" stroke-width="4"/>')

    rows = [(300, [wide, face, obj], "멀리서 가까이"), (500, [obj, face, wide], "가까이서 멀리")]
    for y, drawers, label in rows:
        x = 120
        for i, draw in enumerate(drawers):
            b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#ffffff"/>')
            b.append(draw(x, y))
            b.append(frame(x, y, w, h))
            if i < 2:
                b.append(f'<line x1="{x + w + 8}" y1="{y + h / 2}" x2="{x + w + 34}" y2="{y + h / 2}" '
                         f'stroke="{LINE}" stroke-width="1.6" marker-end="url(#gray)"/>')
            x += w + 42
        b.append(t(120 + (w * 3 + 84) / 2, y + h + 32, label, 16, INK, "600"))
    b.append(t(W / 2, 272, "두 줄은 완전히 같은 세 장이다", 15, MUTED))
    b.append(note_box(147, 680, 500, "같은 세 장, 다른 이야기"))
    b.append(caption(W / 2, 802, [
        "순서를 바꾸면 같은 사진들이 다른 흐름을 만든다.",
        "무엇을 먼저 보여 줄지가 곧 무엇을 말할지를 정한다.",
    ], 16))
    return base("순서가 만드는 이야기", "같은 세 장을 두 가지 순서로 놓은 그림", "".join(b))


# ── p46 두 걸음 옆의 사실 ─────────────────────────────────────────────
def fig_context():
    b = []
    x0, y0, w0, h0 = 110, 270, 574, 170
    b.append(f'<rect x="{x0}" y="{y0}" width="{w0}" height="{h0}" fill="#ffffff"/>')
    b.append(person(x0 + 96, y0 + h0 * 0.76, 1.0))
    for i, ox in enumerate((330, 380, 430, 470)):
        b.append(person(x0 + ox, y0 + h0 * 0.76, 0.95, -1, "#6d7c84"))
    b.append(frame(x0, y0, w0, h0))
    b.append(t(x0 + w0 / 2, y0 - 16, "실제 장면", 15, MUTED))

    # 두 프레임 모두 실제 장면 안에 들어와야 한다. 넓은 쪽은 혼자 있는 사람과 무리를 함께 담는다.
    cw, ch = 140, 130
    cy = y0 + 22
    b.append(frame(x0 + 34, cy, cw, ch, dashed=True, color=GAZE))
    b.append(frame(x0 + 46, cy - 10, 476, ch + 20, dashed=True, color=GAZE))

    ry, rh = 500, 150
    outs = [(148, cw + 60, "혼자 있는 사람", "single"), (420, cw + 110, "무리 곁의 사람", "group")]
    for x, wdt, label, kind in outs:
        b.append(f'<rect x="{x}" y="{ry}" width="{wdt}" height="{rh}" fill="#ffffff"/>')
        b.append(person(x + 52, ry + rh * 0.74, 1.0))
        if kind == "group":
            for ox in (120, 160, 200):
                b.append(person(x + ox, ry + rh * 0.74, 0.95, -1, "#6d7c84"))
        b.append(frame(x, ry, wdt, rh))
        b.append(t(x + wdt / 2, ry + rh + 34, label, 16, INK, "600"))
    b.append(note_box(147, 730, 500, "같은 순간, 다른 사실"))
    b.append(caption(W / 2, 852, [
        "프레임을 좁히면 화면은 정돈되지만 이해에 필요한 정보가 함께 빠진다.",
        "사진은 거짓말을 하지 않았고 사실의 일부만 남겼다.",
    ], 16))
    return base("두 걸음 옆의 사실", "같은 장면에서 두 프레임을 고른 결과", "".join(b))


FIGURES = {8: fig_gaze_space, 14: fig_edge_cut, 23: fig_leading_line,
           30: fig_ratio, 38: fig_sequence, 46: fig_context}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild069")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
