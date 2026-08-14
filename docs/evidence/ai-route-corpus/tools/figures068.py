#!/usr/bin/env python3
"""book-068 이미지 페이지 7개의 SVG 생성. figures062.py의 t()/base() 패턴을 따른다.

매달린 구조를 다루는 책이라 일곱 도표가 모두 위에서 아래로 매달린 형태를 그린다. 지지점은 작은
검은 원, 무게중심은 십자 표시, 도는 방향은 곡선 화살표로 고정해 일곱 도표에서 같은 뜻으로 쓴다.

이 책은 이미지가 일곱 장이다. 6장에서 공기 조건과 실 꼬임을 각각 그림으로 보여야 해서 앞선 권의
여섯 장 구성을 그대로 따르지 않았다.

사용: python3 figures068.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
PIECE = "#5c7480"          # 조각
PIECE_SOFT = "#cfdae0"
MOVE = "#b4703a"           # 움직임·힘
AIR = "#5f97b5"            # 공기 흐름


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="move" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MOVE}"/></marker>
  <marker id="air" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{AIR}"/></marker>
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


def com(x, y, r=7):
    """무게중심 표시: 십자."""
    return (f'<line x1="{x - r}" y1="{y}" x2="{x + r}" y2="{y}" stroke="{INK}" stroke-width="2"/>'
            f'<line x1="{x}" y1="{y - r}" x2="{x}" y2="{y + r}" stroke="{INK}" stroke-width="2"/>')


def pivot(x, y, r=5):
    return f'<circle cx="{x}" cy="{y}" r="{r}" fill="{INK}"/>'


def blob(cx, cy, w=96, h=44):
    return (f'<ellipse cx="{cx}" cy="{cy}" rx="{w / 2}" ry="{h / 2}" fill="{PIECE}" opacity="0.9"/>')


# ── p8 매다는 자리가 정하는 것 ────────────────────────────────────────
def fig_pivot_height():
    b = []
    xs = [175, 397, 619]
    gaps = [70, 26, 0]
    labels = ["되돌아온다", "천천히 되돌아온다", "멈출 자리가 없다"]
    top = 300
    for x, gap, label in zip(xs, gaps, labels):
        cy = top + 150
        py = cy - gap
        b.append(f'<line x1="{x}" y1="{top - 20}" x2="{x}" y2="{py}" stroke="{LINE}" stroke-width="1.6"/>')
        b.append(blob(x, cy))
        b.append(com(x, cy))
        b.append(pivot(x, py))
        if gap >= 60:
            b.append(f'<path d="M{x - 78},{cy + 34} Q{x},{cy + 74} {x + 78},{cy + 34}" fill="none" '
                     f'stroke="{MOVE}" stroke-width="2.4" marker-end="url(#move)"/>')
        elif gap > 0:
            b.append(f'<path d="M{x - 44},{cy + 34} Q{x},{cy + 56} {x + 44},{cy + 34}" fill="none" '
                     f'stroke="{MOVE}" stroke-width="2" marker-end="url(#move)"/>')
        else:
            b.append(f'<path d="M{x + 62},{cy} A62,62 0 1,1 {x - 4},{cy - 62}" fill="none" '
                     f'stroke="{MOVE}" stroke-width="2.4" marker-end="url(#move)"/>')
        b.append(t(x, cy + 116, label, 16, INK, "600"))
    b.append(t(W / 2, 262, "세 조각은 모양도 무게중심도 같다 — 매다는 자리만 다르다", 15, MUTED))
    b.append(t(148, 606, "● 매다는 점    ✛ 무게중심", 14, MUTED, anchor="start"))
    b.append(note_box(147, 700, 500, "같은 조각, 다른 매다는 자리"))
    b.append(caption(W / 2, 822, [
        "매다는 점이 무게중심보다 높을수록 되돌아오는 힘이 크다.",
        "두 점이 겹치면 되돌아오지 않고 멈출 자리도 없어진다.",
    ], 16))
    return base("매다는 자리가 정하는 것", "지지점과 무게중심의 거리에 따라 갈리는 세 상태", "".join(b))


# ── p14 아래가 위의 한쪽이 된다 ───────────────────────────────────────
def fig_layers():
    b = []
    top_y = 280
    # 무거운 쪽(아래층 전체가 달린 오른쪽)이 짧은 팔에 걸려야 하므로 매다는 점을 오른쪽으로 둔다.
    top_cx = 480
    b.append(f'<line x1="{top_cx}" y1="{top_y - 40}" x2="{top_cx}" y2="{top_y}" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(pivot(top_cx, top_y))
    lx, rx = 190, 610
    b.append(f'<line x1="{lx}" y1="{top_y}" x2="{rx}" y2="{top_y}" stroke="{INK}" stroke-width="3"/>')
    b.append(f'<line x1="{lx}" y1="{top_y}" x2="{lx}" y2="{top_y + 70}" stroke="{LINE}"/>')
    b.append(blob(lx, top_y + 92, 84, 40))
    # 오른쪽: 아래층 전체
    sub_y = top_y + 110
    b.append(f'<line x1="{rx}" y1="{top_y}" x2="{rx}" y2="{sub_y}" stroke="{LINE}"/>')
    b.append(pivot(rx, sub_y))
    slx, srx = rx - 110, rx + 60
    b.append(f'<line x1="{slx}" y1="{sub_y}" x2="{srx}" y2="{sub_y}" stroke="{INK}" stroke-width="3"/>')
    for x in (slx, srx):
        b.append(f'<line x1="{x}" y1="{sub_y}" x2="{x}" y2="{sub_y + 56}" stroke="{LINE}"/>')
        b.append(blob(x, sub_y + 78, 72, 36))
    b.append(f'<rect x="{slx - 52}" y="{sub_y - 26}" width="{srx - slx + 104}" height="150" rx="14" '
             f'fill="none" stroke="{MOVE}" stroke-width="1.8" stroke-dasharray="7 5"/>')
    # 화살표를 길게 그으면 점선 상자를 가로질러 아래층 구조를 가린다. 상자 왼쪽에 짧은 지시선만 둔다.
    b.append(f'<line x1="{slx - 118}" y1="{sub_y + 62}" x2="{slx - 58}" y2="{sub_y + 62}" stroke="{MOVE}" '
             f'stroke-width="1.6" marker-end="url(#move)"/>')
    b.append(t(slx - 124, sub_y + 67, "이 전체가 한쪽 무게", 14, MOVE, "700", anchor="end"))
    b.append(f'<line x1="{lx}" y1="{top_y - 26}" x2="{top_cx}" y2="{top_y - 26}" stroke="{LINE}" stroke-dasharray="4 4"/>')
    b.append(f'<line x1="{top_cx}" y1="{top_y - 26}" x2="{rx}" y2="{top_y - 26}" stroke="{LINE}" stroke-dasharray="4 4"/>')
    b.append(t((lx + top_cx) / 2, top_y - 36, "긴 팔", 14, MUTED))
    b.append(t((top_cx + rx) / 2, top_y - 36, "짧은 팔", 14, MUTED))
    b.append(note_box(147, 660, 500, "아래부터 맞춰 올라간다"))
    b.append(caption(W / 2, 782, [
        "아래층 전체가 위층 가로대의 한쪽 무게가 된다.",
        "그래서 위를 먼저 맞추면 아래를 손볼 때마다 다시 어긋난다.",
    ], 16))
    return base("아래가 위의 한쪽이 된다", "두 층으로 쌓은 구조에서 하중이 전해지는 방식", "".join(b))


# ── p23 어디를 미느냐 ─────────────────────────────────────────────────
def fig_torque():
    b = []
    xs = [175, 397, 619]
    top = 330
    labels = ["밀리기만 한다", "돈다", "가장 크게 돈다"]
    for i, (x, label) in enumerate(zip(xs, labels)):
        cy = top + 60
        b.append(f'<circle cx="{x}" cy="{cy}" r="7" fill="none" stroke="{INK}" stroke-width="2"/>')
        b.append(f'<line x1="{x}" y1="{cy}" x2="{x + 62}" y2="{cy}" stroke="{INK}" stroke-width="2.4"/>')
        if i == 2:
            b.append(f'<line x1="{x + 50}" y1="{cy - 30}" x2="{x + 74}" y2="{cy + 22}" '
                     f'stroke="{PIECE}" stroke-width="10" stroke-linecap="round"/>')
        else:
            b.append(f'<line x1="{x + 62}" y1="{cy - 32}" x2="{x + 62}" y2="{cy + 32}" '
                     f'stroke="{PIECE}" stroke-width="10" stroke-linecap="round"/>')
        if i == 0:
            b.append(f'<line x1="{x - 92}" y1="{cy}" x2="{x - 22}" y2="{cy}" stroke="{AIR}" '
                     f'stroke-width="2.4" marker-end="url(#air)"/>')
        else:
            ay = cy + 46 if i == 1 else cy + 40
            b.append(f'<line x1="{x - 92}" y1="{ay}" x2="{x + 30}" y2="{ay}" stroke="{AIR}" '
                     f'stroke-width="2.4" marker-end="url(#air)"/>')
            r = 42 if i == 1 else 58
            b.append(f'<path d="M{x + r},{cy} A{r},{r} 0 1,1 {x - 4},{cy - r}" fill="none" '
                     f'stroke="{MOVE}" stroke-width="2.2" marker-end="url(#move)"/>')
        b.append(t(x, top + 190, label, 16, INK, "600"))
    b.append(t(W / 2, 292, "위에서 내려다본 그림 — 바람의 세기는 셋이 같다", 15, MUTED))
    b.append(t(148, 566, "○ 회전축    ━ 면    → 바람", 14, MUTED, anchor="start"))
    b.append(note_box(147, 660, 500, "축에서 벗어난 힘만 돌린다"))
    b.append(caption(W / 2, 782, [
        "힘이 축을 지나가면 조형은 밀려나기만 하고 돌지 않는다.",
        "면을 비스듬히 두면 같은 바람에서 도는 힘이 가장 커진다.",
    ], 16))
    return base("어디를 미느냐", "같은 바람이 세 자리에서 다르게 작용하는 그림", "".join(b))


# ── p30 도는 동안의 반짝임 ────────────────────────────────────────────
def fig_reflection():
    b = []
    xs = [130, 292, 454, 616]
    top = 320
    angles = [(0, 1.0), (28, 0.5), (86, 0.0), (152, 0.7)]
    labels = ["가장 밝다", "옆으로 비낀다", "사라진다", "다시 나타난다"]
    for x, (deg, bright), label in zip(xs, angles, labels):
        cy = top + 70
        b.append(f'<line x1="{x - 46}" y1="{top - 40}" x2="{x - 16}" y2="{top - 6}" stroke="{AIR}" '
                 f'stroke-width="2" marker-end="url(#air)"/>')
        b.append(f'<line x1="{x - 26}" y1="{top - 50}" x2="{x + 4}" y2="{top - 16}" stroke="{AIR}" '
                 f'stroke-width="2" marker-end="url(#air)"/>')
        b.append(f'<g transform="rotate({deg} {x} {cy})">'
                 f'<rect x="{x - 9}" y="{cy - 52}" width="18" height="104" rx="4" fill="{PIECE}"/>'
                 f'<rect x="{x - 9}" y="{cy - 52}" width="18" height="104" rx="4" fill="#ffe9a8" '
                 f'opacity="{bright}"/></g>')
        if bright > 0.2:
            b.append(f'<line x1="{x + 14}" y1="{cy + 6}" x2="{x + 52}" y2="{cy + 44 - 20 * bright}" '
                     f'stroke="{MOVE}" stroke-width="2" marker-end="url(#move)"/>')
        b.append(t(x, top + 168, label, 14, INK, "600"))
    b.append(t(W / 2, 282, "빛이 오는 방향은 네 그림이 같다", 15, MUTED))
    b.append(note_box(147, 640, 500, "반짝임은 한 바퀴에 두 번 온다"))
    b.append(caption(W / 2, 762, [
        "매끄러운 면은 빛을 한 방향으로만 되돌려 보낸다.",
        "그 방향에 보는 사람이 있을 때만 반짝임이 보인다.",
    ], 16))
    return base("도는 동안의 반짝임", "한 바퀴를 네 단계로 나눠 본 반사의 변화", "".join(b))


# ── p38 매달아 보고 옮기기 ────────────────────────────────────────────
def fig_test_hang():
    b = []
    rail_y = 268
    b.append(f'<line x1="120" y1="{rail_y}" x2="674" y2="{rail_y}" stroke="{INK}" stroke-width="3"/>')
    b.append(t(120, rail_y - 16, "작업실 가로줄", 15, MUTED, anchor="start"))
    xs = [200, 397, 594]
    tilts = [10, 4, 0]
    offs = [0, 12, 20]
    labels = ["기운다", "조금 옮긴다", "표시한다"]
    for x, tilt, off, label in zip(xs, tilts, offs, labels):
        py = rail_y + 80
        b.append(f'<line x1="{x + off}" y1="{rail_y}" x2="{x + off}" y2="{py}" stroke="{LINE}" stroke-width="1.6"/>')
        b.append(pivot(x + off, py))
        b.append(f'<g transform="rotate({tilt} {x + off} {py})">'
                 f'<line x1="{x - 84}" y1="{py}" x2="{x + 84}" y2="{py}" stroke="{INK}" stroke-width="3"/>'
                 f'<line x1="{x - 84}" y1="{py}" x2="{x - 84}" y2="{py + 40}" stroke="{LINE}"/>'
                 f'<line x1="{x + 84}" y1="{py}" x2="{x + 84}" y2="{py + 40}" stroke="{LINE}"/>'
                 f'<ellipse cx="{x - 84}" cy="{py + 58}" rx="26" ry="16" fill="{PIECE_SOFT}"/>'
                 f'<ellipse cx="{x + 84}" cy="{py + 58}" rx="32" ry="20" fill="{PIECE}"/></g>')
        if tilt:
            b.append(f'<line x1="{x + off + 12}" y1="{py - 26}" x2="{x + off + 44}" y2="{py - 26}" '
                     f'stroke="{MOVE}" stroke-width="2" marker-end="url(#move)"/>')
        else:
            b.append(f'<line x1="{x + off}" y1="{py - 30}" x2="{x + off}" y2="{py - 8}" stroke="{MOVE}" stroke-width="2.4"/>')
        b.append(t(x, py + 132, label, 16, INK, "600"))
    b.append(note_box(147, 660, 500, "계산은 자리를 좁히고 손이 마지막을 정한다"))
    b.append(caption(W / 2, 782, [
        "무거운 쪽으로 매다는 점을 아주 조금 옮기면 기울기가 줄어든다.",
        "맞춘 자리는 표시로 남겨야 다시 조립할 때 처음부터 하지 않는다.",
    ], 16))
    return base("매달아 보고 옮기기", "가로줄에서 균형을 찾는 세 단계", "".join(b))


# ── p44 같은 조형, 다른 자리 ──────────────────────────────────────────
def fig_room_air():
    b = []
    rooms = [(148, True, "창이 있는 자리"), (420, False, "막힌 자리")]
    w, h, y = 226, 260, 300
    for x, has_window, label in rooms:
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#f4f6f7" stroke="{INK}" stroke-width="2"/>')
        if has_window:
            b.append(f'<rect x="{x - 4}" y="{y + 50}" width="8" height="70" fill="#ffffff" stroke="{AIR}" stroke-width="2"/>')
            for i, dy in enumerate((60, 92, 124)):
                b.append(f'<path d="M{x + 8},{y + dy} Q{x + 70},{y + dy + 18} {x + 118},{y + dy - 6}" '
                         f'fill="none" stroke="{AIR}" stroke-width="2" marker-end="url(#air)"/>')
        else:
            b.append(f'<line x1="{x + 30}" y1="{y + h - 30}" x2="{x + 76}" y2="{y + h - 30}" '
                     f'stroke="{AIR}" stroke-width="1.4" opacity="0.5" marker-end="url(#air)"/>')
        cx = x + w / 2
        b.append(f'<line x1="{cx}" y1="{y}" x2="{cx}" y2="{y + 78}" stroke="{LINE}" stroke-width="1.6"/>')
        b.append(pivot(cx, y + 78))
        b.append(f'<line x1="{cx - 54}" y1="{y + 78}" x2="{cx + 54}" y2="{y + 78}" stroke="{INK}" stroke-width="2.6"/>')
        for ox in (-54, 54):
            b.append(f'<line x1="{cx + ox}" y1="{y + 78}" x2="{cx + ox}" y2="{y + 110}" stroke="{LINE}"/>')
            b.append(blob(cx + ox, y + 128, 60, 30))
        if has_window:
            b.append(f'<path d="M{cx + 76},{y + 96} A76,76 0 1,1 {cx - 6},{y + 20}" fill="none" '
                     f'stroke="{MOVE}" stroke-width="2.4" marker-end="url(#move)"/>')
        b.append(t(cx, y + h + 34, label, 17, INK, "600"))
    b.append(t(W / 2, 262, "두 방의 조형은 완전히 같다", 15, MUTED))
    b.append(note_box(147, 660, 500, "조형은 그 방의 공기까지가 재료다"))
    b.append(caption(W / 2, 782, [
        "흐름이 없는 자리에 걸린 조형은 하루 종일 같은 자세로 있는다.",
        "그래서 걸 자리를 먼저 정하고 조형을 만든다.",
    ], 16))
    return base("같은 조형, 다른 자리", "흐름이 있는 방과 없는 방에 같은 조형을 건 그림", "".join(b))


# ── p47 감기는 실 ────────────────────────────────────────────────────
def fig_twist():
    b = []
    xs = [175, 397, 619]
    top = 300
    labels = ["곧은 실", "몇 번 꼬임", "되돌아오려 한다"]
    for i, (x, label) in enumerate(zip(xs, labels)):
        b.append(pivot(x, top))
        length = 150
        if i == 0:
            b.append(f'<line x1="{x}" y1="{top}" x2="{x}" y2="{top + length}" stroke="{LINE}" stroke-width="2"/>')
        else:
            turns = 4 if i == 1 else 9
            amp = 7 if i == 1 else 5
            pts = []
            for k in range(0, 121):
                f = k / 120
                yy = top + length * f
                import math
                xx = x + amp * math.sin(f * turns * 3.14159 * 2)
                pts.append(f"{xx:.1f},{yy:.1f}")
            b.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{LINE}" stroke-width="2"/>')
        b.append(blob(x, top + length + 26, 92, 40))
        cy = top + length + 26
        if i == 0:
            b.append(f'<path d="M{x + 70},{cy} A70,70 0 1,1 {x - 4},{cy - 70}" fill="none" '
                     f'stroke="{MOVE}" stroke-width="2.4" marker-end="url(#move)"/>')
        elif i == 1:
            b.append(f'<path d="M{x + 44},{cy} A44,44 0 1,1 {x - 4},{cy - 44}" fill="none" '
                     f'stroke="{MOVE}" stroke-width="2" marker-end="url(#move)"/>')
        else:
            b.append(f'<path d="M{x + 30},{cy + 42} Q{x},{cy + 58} {x - 30},{cy + 42}" fill="none" '
                     f'stroke="{MOVE}" stroke-width="2.2" marker-end="url(#move)"/>')
        b.append(t(x, top + length + 108, label, 16, INK, "600"))
    b.append(t(W / 2, 262, "같은 조형이 한 방향으로 계속 돈 결과", 15, MUTED))
    b.append(note_box(147, 660, 500, "한 방향으로만 돌면 실이 대신 기억한다"))
    b.append(caption(W / 2, 782, [
        "감긴 실은 되돌아오려는 힘을 만들어 회전을 막는다.",
        "늘 같은 방향으로 바람이 오는 자리에서 특히 자주 생긴다.",
    ], 16))
    return base("감기는 실", "실이 감기며 회전을 막는 세 단계", "".join(b))


FIGURES = {8: fig_pivot_height, 14: fig_layers, 23: fig_torque, 30: fig_reflection,
           38: fig_test_hang, 44: fig_room_air, 47: fig_twist}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild068")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
