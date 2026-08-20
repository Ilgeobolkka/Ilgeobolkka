#!/usr/bin/env python3
"""book-063 이미지 페이지 6개의 SVG 생성. figures062.py의 t()/base() 패턴을 따른다.

시간 예술을 다루는 책이라 여섯 도표가 모두 왼쪽에서 오른쪽으로 흐르는 가로축을 쓴다. 소리가 있는
구간은 채운 도형, 없는 구간은 빈 도형으로 고정해 여섯 도표에서 같은 뜻으로 쓴다.

사용: python3 figures063.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
SOUND = "#3f6f8c"         # 소리가 있는 구간
SOUND_SOFT = "#cfe0ea"
REST = "#ffffff"          # 소리가 없는 구간(빈 도형)
ACC = "#b4703a"           # 숨·경계 표시
MUTED = "#55666b"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="acc" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{ACC}"/></marker>
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


def breath(x, y):
    """숨의 자리를 나타내는 작은 반원."""
    return f'<path d="M{x - 11},{y} A11,11 0 0,1 {x + 11},{y}" fill="none" stroke="{ACC}" stroke-width="2.4"/>'


# ── p8 같은 줄, 다른 나눔 ─────────────────────────────────────────────
def fig_grouping():
    b = []
    # 끊는 자리를 나타내는 세로선이 네모에 닿지 않도록 네모 사이 간격을 넉넉히 둔다.
    x0, cell, gap = 150, 30, 14
    rows = [(300, [4, 4, 4], "고르게"), (450, [6, 6], "길게"), (600, [3, 5, 4], "고르지 않게")]
    for y, groups, label in rows:
        n = 0
        for g in groups:
            for _ in range(g):
                x = x0 + n * (cell + gap)
                b.append(f'<rect x="{x}" y="{y}" width="{cell}" height="{cell}" rx="4" '
                         f'fill="{SOUND}" opacity="0.85"/>')
                n += 1
            if n < sum(groups):
                bx = x0 + n * (cell + gap) - gap / 2
                b.append(f'<line x1="{bx}" y1="{y - 16}" x2="{bx}" y2="{y + cell + 16}" '
                         f'stroke="{ACC}" stroke-width="2"/>')
                b.append(breath(bx, y - 22))
        b.append(t(x0 + 12 * (cell + gap) + 16, y + cell / 2 + 6, label, 16, MUTED, anchor="start"))
    b.append(t(W / 2, 262, "세 줄의 음은 완전히 같다 — 끊는 자리만 다르다", 15, MUTED))
    b.append(note_box(147, 728, 500, "음은 같고 나눔이 다르다"))
    b.append(caption(W / 2, 850, [
        "같은 음렬이라도 어디서 끊느냐에 따라 듣는 사람이 기억하는 단위가 달라진다.",
        "나눔은 악보에 그어져 있지 않고 연주자가 정한다.",
    ], 16))
    return base("같은 줄, 다른 나눔", "같은 음의 줄을 세 가지로 끊어 놓은 그림", "".join(b))


# ── p14 악구 하나의 모양 ──────────────────────────────────────────────
def fig_phrase():
    b = []
    x0, x1 = 150, 650
    ybase, ytop = 520, 300
    pts = []
    for i in range(0, 51):
        f = i / 50
        # 정점이 가운데보다 뒤(0.58)에 오는 비대칭 산 모양
        v = (f ** 0.9) * ((1 - f) ** 0.62)
        pts.append((x0 + (x1 - x0) * f, v))
    peak = max(p[1] for p in pts)
    poly = " ".join(f"{x:.1f},{ybase - (ytop - ybase) * -1 * (v / peak):.1f}" for x, v in pts)
    b.append(f'<polyline points="{poly}" fill="none" stroke="{SOUND}" stroke-width="3.4"/>')
    b.append(f'<line x1="{x0}" y1="{ybase}" x2="{x1 + 20}" y2="{ybase}" stroke="{LINE}" stroke-width="1.4"/>')
    px = x0 + (x1 - x0) * 0.58
    b.append(f'<line x1="{px}" y1="{ytop - 16}" x2="{px}" y2="{ybase}" stroke="{ACC}" '
             f'stroke-width="1.6" stroke-dasharray="5 4"/>')
    b.append(t(px, ytop - 26, "무게 중심", 15, ACC, "700"))
    for x, label in ((x0, "시작"), (x1, "끝")):
        b.append(f'<line x1="{x}" y1="{ybase}" x2="{x}" y2="{ybase + 20}" stroke="{LINE}" stroke-dasharray="4 4"/>')
        b.append(t(x, ybase + 42, label, 15, MUTED))
    b.append(breath(x1 + 30, ybase - 8))
    b.append(t(x1 + 30, ybase + 42, "숨", 14, ACC))

    # 박 띠는 곡선과 같은 가로 범위에 두어 중심이 어느 박에 오는지 눈으로 맞출 수 있게 한다.
    gap = 12
    cell = ((x1 - x0) - gap * 7) / 8
    by = 600
    for i in range(8):
        x = x0 + i * (cell + gap)
        fill = SOUND if i == 4 else SOUND_SOFT
        b.append(f'<rect x="{x:.1f}" y="{by}" width="{cell:.1f}" height="30" rx="4" fill="{fill}"/>')
        b.append(t(x + cell / 2, by + 21, str(i + 1), 14, "#ffffff" if i == 4 else MUTED))
    b.append(f'<line x1="{px}" y1="{ybase}" x2="{px}" y2="{by}" stroke="{ACC}" stroke-width="1.4" '
             f'stroke-dasharray="4 4"/>')
    b.append(t(W / 2, by + 62, "여덟 박 가운데 다섯 번째 박에 중심이 온다", 15, MUTED))
    b.append(note_box(147, 726, 500, "한 덩어리에는 오르막과 내리막이 있다"))
    b.append(caption(W / 2, 848, [
        "악구는 평평하게 흐르지 않고 한 지점을 향해 올라갔다 내려온다.",
        "중심을 어디에 두느냐가 같은 음을 다른 이야기로 만든다.",
    ], 16))
    return base("악구 하나의 모양", "한 악구 안에서 크기가 오르내리는 모양과 그 무게 중심", "".join(b))


# ── p23 굴곡인 쉼과 경계인 쉼 ─────────────────────────────────────────
def fig_rest_kinds():
    b = []
    x0, cell, gap = 140, 38, 8
    n = 12

    def row(y, empties, arcs, label):
        for i in range(n):
            x = x0 + i * (cell + gap)
            empty = i in empties
            b.append(f'<rect x="{x}" y="{y}" width="{cell}" height="{cell}" rx="4" '
                     f'fill="{"#ffffff" if empty else SOUND}" stroke="{LINE if empty else SOUND}" '
                     f'stroke-width="1.6" opacity="{1 if empty else 0.85}"/>')
        for i in arcs:
            x = x0 + i * (cell + gap) + cell / 2
            b.append(f'<path d="M{x - cell},{y - 10} Q{x},{y - 44} {x + cell},{y - 10}" '
                     f'fill="none" stroke="{SOUND}" stroke-width="2.2"/>')
        b.append(t(x0 + n * (cell + gap) + 14, y + cell / 2 + 6, label, 16, MUTED, anchor="start"))

    row(330, {2, 6, 9}, [2, 6, 9], "흐름 안의 굴곡")
    seg = {5, 6, 7, 8}
    row(540, seg, [], "흐름의 경계")
    for i in (5, 9):
        bx = x0 + i * (cell + gap) - gap / 2
        b.append(f'<line x1="{bx}" y1="{540 - 20}" x2="{bx}" y2="{540 + cell + 20}" '
                 f'stroke="{ACC}" stroke-width="2"/>')
        b.append(f'<line x1="{bx + 5}" y1="{540 - 20}" x2="{bx + 5}" y2="{540 + cell + 20}" '
                 f'stroke="{ACC}" stroke-width="2"/>')
    b.append(t(W / 2, 292, "채운 네모는 소리, 빈 네모는 쉼", 15, MUTED))
    b.append(note_box(147, 700, 500, "같은 기호가 길이에 따라 다른 일을 한다"))
    b.append(caption(W / 2, 822, [
        "흩어진 짧은 쉼은 앞뒤 소리를 잇는 굴곡으로 들리고,",
        "한자리에 모인 긴 쉼은 두 덩어리를 가르는 경계가 된다.",
    ], 16))
    return base("굴곡인 쉼과 경계인 쉼", "짧은 쉼과 긴 쉼이 놓인 자리를 나란히 본 그림", "".join(b))


# ── p30 같은 폭, 다른 줄임 ────────────────────────────────────────────
def fig_diminuendo():
    b = []
    x0, x1 = 150, 650
    ytop, ybot = 300, 600
    b.append(f'<line x1="{x0}" y1="{ybot}" x2="{x1 + 20}" y2="{ybot}" stroke="{LINE}" stroke-width="1.4"/>')
    b.append(f'<line x1="{x0}" y1="{ybot}" x2="{x0}" y2="{ytop - 30}" stroke="{LINE}" stroke-width="1.4"/>')
    b.append(t(x0 - 14, ytop - 4, "크기", 15, MUTED, anchor="end"))

    def curve(power, color, dash=""):
        pts = []
        for i in range(0, 41):
            f = i / 40
            v = 1 - (f ** power)
            pts.append(f"{x0 + (x1 - x0) * f:.1f},{ybot - (ybot - ytop) * v:.1f}")
        d = f' stroke-dasharray="{dash}"' if dash else ""
        return f'<polyline points="{" ".join(pts)}" fill="none" stroke="{color}" stroke-width="3"{d}/>'

    b.append(curve(0.42, ACC))
    b.append(curve(1.0, INK, "7 5"))
    b.append(curve(2.6, SOUND))
    b.append(f'<circle cx="{x0}" cy="{ytop}" r="5" fill="{INK}"/>')
    b.append(f'<circle cx="{x1}" cy="{ybot}" r="5" fill="{INK}"/>')
    b.append(t(x0 + 150, ytop + 92, "먼저 줄이고 유지한다", 14, ACC, anchor="start"))
    b.append(t(x1 - 8, ytop + 146, "고르게 줄인다", 14, INK, anchor="end"))
    b.append(t(x1 - 8, ybot - 66, "끝에서 줄인다", 14, SOUND, anchor="end"))
    b.append(t(x0, ybot + 30, "구간 시작", 14, MUTED))
    b.append(t(x1, ybot + 30, "구간 끝", 14, MUTED))
    b.append(t(W / 2, 262, "세 선의 시작점과 끝점은 완전히 같다", 15, MUTED))
    b.append(note_box(147, 700, 500, "같은 지시, 다른 도착 방식"))
    b.append(caption(W / 2, 822, [
        "점점 여리게라는 지시는 시작과 끝만 정하고 그 사이를 연주자에게 맡긴다.",
        "줄임을 어디에 몰아 두느냐가 구간의 인상을 가른다.",
    ], 16))
    return base("같은 폭, 다른 줄임", "같은 구간을 세 가지 방식으로 줄여 간 그림", "".join(b))


# ── p38 어긋남은 쌓인다 ───────────────────────────────────────────────
def fig_drift():
    b = []
    x0 = 150
    ref_y, off_y = 340, 520
    step = 42
    drift = 3.4
    n = 12
    b.append(f'<line x1="{x0 - 20}" y1="{ref_y}" x2="{x0 + n * step + 40}" y2="{ref_y}" stroke="{LINE}"/>')
    b.append(f'<line x1="{x0 - 20}" y1="{off_y}" x2="{x0 + n * step + 40}" y2="{off_y}" stroke="{LINE}"/>')
    b.append(t(x0 - 30, ref_y + 5, "기준", 15, MUTED, anchor="end"))
    b.append(t(x0 - 30, off_y + 5, "연주", 15, MUTED, anchor="end"))
    rest_at = 6
    for i in range(n):
        rx = x0 + i * step
        extra = drift * i + (drift * 2.4 * max(0, i - rest_at))
        ox = rx + extra
        b.append(f'<line x1="{rx}" y1="{ref_y - 12}" x2="{rx}" y2="{ref_y + 12}" stroke="{INK}" stroke-width="2"/>')
        b.append(f'<line x1="{ox}" y1="{off_y - 12}" x2="{ox}" y2="{off_y + 12}" stroke="{SOUND}" stroke-width="2"/>')
        b.append(f'<line x1="{rx}" y1="{ref_y + 14}" x2="{ox}" y2="{off_y - 14}" stroke="{LINE}" '
                 f'stroke-width="1.1" stroke-dasharray="4 4"/>')
        if extra > 6:
            b.append(f'<rect x="{rx}" y="{off_y - 5}" width="{extra:.1f}" height="10" fill="{SOUND_SOFT}"/>')
    bx = x0 + rest_at * step + step / 2
    b.append(f'<line x1="{bx}" y1="{off_y - 46}" x2="{bx}" y2="{off_y + 46}" stroke="{ACC}" stroke-width="2"/>')
    b.append(f'<line x1="{bx + 5}" y1="{off_y - 46}" x2="{bx + 5}" y2="{off_y + 46}" stroke="{ACC}" stroke-width="2"/>')
    b.append(t(bx + 60, off_y + 72, "쉼 구간", 15, ACC, "700"))
    b.append(t(W / 2, 282, "한 박씩의 차이는 눈에 띄지 않는다", 15, MUTED))
    b.append(note_box(147, 700, 500, "한 박의 차이는 처음에 보이지 않는다"))
    b.append(caption(W / 2, 822, [
        "미세한 차이도 박이 지날수록 쌓여 결국 한 박 이상으로 벌어진다.",
        "소리가 없는 쉼 구간에서는 서로를 확인할 수 없어 더 빠르게 벌어진다.",
    ], 16))
    return base("어긋남은 쌓인다", "기준 박과 조금 느린 박을 나란히 놓고 이은 그림", "".join(b))


# ── p46 비어 있지 않은 정적 ───────────────────────────────────────────
def fig_silence():
    b = []
    x0, x1 = 140, 660
    mid = (x0 + x1) / 2
    axis = 420
    pts = []
    for i in range(0, 261):
        f = i / 260
        x = x0 + (mid - x0) * f
        import math
        v = math.sin(f * 22) * (26 + 14 * math.sin(f * 5))
        pts.append(f"{x:.1f},{axis - v:.1f}")
    b.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{SOUND}" stroke-width="2.4"/>')
    b.append(f'<line x1="{x0}" y1="{axis}" x2="{x1}" y2="{axis}" stroke="{LINE}" stroke-width="1.2"/>')
    b.append(f'<line x1="{mid}" y1="{axis - 90}" x2="{mid}" y2="{axis + 130}" stroke="{ACC}" '
             f'stroke-width="1.8" stroke-dasharray="6 5"/>')
    b.append(t(x0 + (mid - x0) / 2, axis - 108, "소리 구간", 16, SOUND, "700"))
    b.append(t(mid + (x1 - mid) / 2, axis - 108, "쉼 구간", 16, ACC, "700"))
    b.append(f'<rect x="{mid}" y="{axis + 34}" width="{x1 - mid}" height="16" fill="{SOUND_SOFT}"/>')
    b.append(t(mid + (x1 - mid) / 2, axis + 76, "앞 소리의 여운", 14, MUTED))
    noises = [(0.24, -46, "기침"), (0.52, -18, "옷 스치는 소리"), (0.78, -62, "공기 소리")]
    for f, dy, label in noises:
        x = mid + (x1 - mid) * f
        b.append(f'<line x1="{x}" y1="{axis + dy}" x2="{x}" y2="{axis}" stroke="{LINE}" stroke-width="1.6"/>')
        b.append(f'<circle cx="{x}" cy="{axis + dy}" r="4" fill="{LINE}"/>')
        b.append(t(x, axis + dy - 14, label, 13, MUTED))
    b.append(note_box(147, 700, 500, "연주자가 만든 정적에 다른 것이 들어온다"))
    b.append(caption(W / 2, 822, [
        "쉼 구간은 소리가 없는 구간이 아니라 다른 소리가 들어오는 구간이다.",
        "정적이 길수록 그 소리는 더 잘 들린다.",
    ], 16))
    return base("비어 있지 않은 정적", "정적 구간에 여운과 객석의 소리가 들어오는 모양", "".join(b))


FIGURES = {8: fig_grouping, 14: fig_phrase, 23: fig_rest_kinds,
           30: fig_diminuendo, 38: fig_drift, 46: fig_silence}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild063")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
