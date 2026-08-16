#!/usr/bin/env python3
"""book-057 이미지 페이지 4개의 SVG 생성. figures051.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '무엇이 이어지고 어디서 갈라지는가'를 그린다. 정해진 쪽은 굵은 실선, 열린 쪽은 가는
갈래, 묶어서 생긴 것은 겹쳐 그린 곡선으로 고정해 같은 뜻으로 쓴다.

사용: python3 figures057.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 정해졌거나 이어진 쪽
DROP = "#c3ccd0"          # 아직 아니거나 지나간 쪽
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


# ── p6 같은 시간을 두 가지로 그린다 ──────────────────────────────────
def fig_two_pictures():
    b = []
    x0, xs_n = 176, 7
    step = 62
    for row, (y, mode, tag) in enumerate(((320, "flat", "모두 똑같이 있다"),
                                          (500, "now", "지금만 있다"))):
        b.append(f'<line x1="{x0 - 24}" y1="{y}" x2="{x0 + step * (xs_n - 1) + 48}" y2="{y}" '
                 f'stroke="{INK}" stroke-width="2.4"/>')
        for i in range(xs_n):
            cx = x0 + i * step
            if mode == "flat":
                b.append(f'<rect x="{cx - 16}" y="{y - 40}" width="32" height="32" rx="4" '
                         f'fill="{KEEP}" opacity="0.7"/>')
            else:
                if i == 3:
                    b.append(f'<rect x="{cx - 24}" y="{y - 52}" width="48" height="44" rx="5" '
                             f'fill="{KEEP}" stroke="{INK}" stroke-width="2.6"/>')
                elif i < 3:
                    b.append(f'<rect x="{cx - 16}" y="{y - 40}" width="32" height="32" rx="4" '
                             f'fill="{DROP}" opacity="0.85"/>')
                else:
                    b.append(f'<rect x="{cx - 16}" y="{y - 40}" width="32" height="32" rx="4" '
                             f'fill="none" stroke="{DROP}" stroke-width="1.6" stroke-dasharray="4 4"/>')
        if mode == "flat":
            nx = x0 + 3 * step
            b.append(f'<line x1="{nx}" y1="{y + 8}" x2="{nx}" y2="{y + 34}" stroke="{MARK}" '
                     f'stroke-width="2.2" marker-end="url(#mark)"/>')
            b.append(t(nx, y + 56, "여기가 지금", 14, MARK, "700"))
        b.append(t(x0 + step * (xs_n - 1) + 56, y - 20, tag, 15, INK, "700", anchor="start"))
        b.append(t(x0 - 30, y + 26, "지나간 쪽", 13, MUTED, anchor="middle"))
        b.append(t(x0 + step * (xs_n - 1) + 22, y + 26, "올 쪽", 13, MUTED))

    b.append(f'<line x1="{x0 + 3 * step}" y1="392" x2="{x0 + 3 * step}" y2="440" stroke="{LINE}" '
             f'stroke-width="1.8" marker-start="url(#line)" marker-end="url(#line)"/>')
    b.append(t(x0 + 3 * step + 12, 420, "겪는 쪽에서는 구별되지 않는다", 14, LINE, anchor="start"))

    b.append(note_box(147, 620, 500, "어느 그림을 고르든 지금 안에서 지내는 일은 같다", 16))
    b.append(caption(W / 2, 742, [
        "모든 시점이 똑같이 있다는 그림과 지금만 있다는 그림이 있다.",
        "두 그림은 크게 다르지만 겪는 자리에서는 갈라지지 않는다.",
    ], 16))
    return base("같은 시간을 두 가지로 그린다", "과거·현재·미래가 놓이는 두 가지 방식", "".join(b))


# ── p15 한쪽은 하나이고 한쪽은 여럿이다 ──────────────────────────────
def fig_open_closed():
    b = []
    nx, ny = 400, 420
    b.append(f'<line x1="{nx}" y1="270" x2="{nx}" y2="580" stroke="{LINE}" stroke-width="1.6" '
             f'stroke-dasharray="6 5"/>')
    b.append(t(nx, 258, "지금", 17, INK, "700"))

    b.append(f'<line x1="150" y1="{ny}" x2="{nx}" y2="{ny}" stroke="{KEEP}" stroke-width="4"/>')
    for i in range(5):
        b.append(f'<circle cx="{170 + i * 46}" cy="{ny}" r="5" fill="{KEEP}"/>')
    b.append(t(262, ny + 34, "하나로 정해져 있다", 15, KEEP, "700"))
    for dy in (-26, 24):
        b.append(f'<path d="M152,{ny + dy} q26,{-dy * 0.5} 52,{-dy * 0.2}" fill="none" '
                 f'stroke="{DROP}" stroke-width="1.6" stroke-dasharray="4 4"/>')
    b.append(t(196, ny - 46, "있었을 뿐 지금은 하나", 12, LINE))

    ends = [(-104, 1.0), (-52, 0.4), (0, 0.0), (52, -0.4), (104, -1.0)]
    for k, (dy, _) in enumerate(ends):
        col = MARK if k == 2 else LINE
        wid = 3 if k == 2 else 1.6
        b.append(f'<path d="M{nx},{ny} C{nx + 70},{ny} {nx + 130},{ny + dy} {nx + 220},{ny + dy}" '
                 f'fill="none" stroke="{col}" stroke-width="{wid}"/>')
    b.append(t(nx + 232, ny + 4, "지금 향하는 쪽", 13, MARK, "700", anchor="start"))
    b.append(t(nx + 110, ny + 148, "여러 갈래가 있다", 15, INK, "700"))
    b.append(f'<path d="M{nx},{ny} L{nx + 96},{ny - 58} L{nx + 96},{ny + 58} Z" fill="{KEEP}" '
             f'opacity="0.12"/>')
    b.append(t(nx + 54, ny - 84, "지금까지의 것이 폭을 좁혀 둔다", 12, MUTED))

    b.append(note_box(147, 640, 500, "열려 있다는 것은 아무 갈래나 있다는 뜻이 아니다", 16))
    b.append(caption(W / 2, 762, [
        "지나간 쪽은 하나로 정해져 있고 올 쪽은 여러 갈래로 벌어진다.",
        "다만 그 갈래의 폭은 지금까지의 것이 이미 좁혀 두었다.",
    ], 16))
    return base("한쪽은 하나이고 한쪽은 여럿이다", "지나간 쪽과 올 쪽의 다른 모양", "".join(b))


# ── p25 달라지려면 그대로인 것이 있어야 한다 ─────────────────────────
def fig_change_and_stay():
    b = []
    x0, step, y = 152, 82, 400
    for i in range(6):
        cx = x0 + i * step
        op = 0.85 - i * 0.11
        rad = 2 + i * 5
        b.append(f'<rect x="{cx}" y="{y - 34}" width="68" height="68" rx="{rad}" '
                 f'fill="{KEEP}" opacity="{op:.2f}"/>')
        if i:
            bar = 12 + i * 5
            b.append(f'<rect x="{cx + 26}" y="{y - 52 - bar}" width="16" height="{bar}" rx="3" fill="{MARK}"/>')
    b.append(t(x0 + 5 * step + 34 + 26, y - 96, "달라진 것", 14, MARK, "700", anchor="end"))
    b.append(f'<line x1="{x0 - 22}" y1="{y}" x2="{x0 + 5 * step + 90}" y2="{y}" stroke="{INK}" stroke-width="3"/>')
    b.append(t(x0 + 2.6 * step, y - 6, "그대로인 것", 15, "#1f3b36", "700"))

    b.append(f'<path d="M{x0 + 34},{y + 54} C{x0 + 120},{y + 130} {x0 + 5 * step},{y + 130} '
             f'{x0 + 5 * step + 34},{y + 54}" fill="none" stroke="{LINE}" stroke-width="1.8" '
             f'stroke-dasharray="6 5" marker-end="url(#line)"/>')
    b.append(t(W / 2, y + 148, "양끝만 보면 다른 것처럼 보인다", 15, LINE))

    b.append(note_box(147, 620, 500, "변화와 지속은 서로 반대가 아니라 짝이다"))
    b.append(caption(W / 2, 742, [
        "같은 것이 이어지면서 어떤 면이 달라질 때 변화라고 부른다.",
        "그대로인 것을 정하지 않으면 달라진 것도 말할 수 없다.",
    ], 16))
    return base("달라지려면 그대로인 것이 있어야 한다", "같은 것이 이어지면서 달라지는 모습",
                "".join(b))


# ── p34 모아 놓는 것과 묶는 것은 다르다 ──────────────────────────────
def fig_sum_of_moments():
    b = []
    x0, bw, gap = 152, 36, 4
    n = 12
    for row, (y, tag) in enumerate(((300, "차례로 있었던 것"), (470, "묶은 것"))):
        for i in range(n):
            x = x0 + i * (bw + gap)
            b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="52" rx="3" fill="{KEEP}" opacity="0.55"/>')
        b.append(t(x0 - 14, y + 32, tag, 14, INK, "700", anchor="end"))
    cy = 470
    pts = []
    for i in range(n):
        x = x0 + i * (bw + gap) + bw / 2
        h = 34 - abs(i - (n - 1) / 2) * 9
        pts.append(f"{x:.0f},{cy - h:.0f}")
    b.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{MARK}" stroke-width="3"/>')
    b.append(f'<line x1="{x0 + n * (bw + gap) + 16}" y1="356" x2="{x0 + n * (bw + gap) + 16}" y2="446" '
             f'stroke="{LINE}" stroke-width="1.8" marker-end="url(#line)"/>')
    b.append(t(x0 + n * (bw + gap) + 28, 404, "곡선은 어느", 13, LINE, anchor="start"))
    b.append(t(x0 + n * (bw + gap) + 28, 422, "네모에도 없다", 13, LINE, anchor="start"))

    ex = x0 + (n - 1) * (bw + gap) + bw
    b.append(f'<line x1="{ex}" y1="{cy + 66}" x2="{ex}" y2="{cy + 34}" stroke="{MARK}" '
             f'stroke-width="2" marker-end="url(#mark)"/>')
    b.append(t(ex - 10, cy + 86, "돌아보는 자리", 14, MARK, "700", anchor="end"))
    for k in range(2):
        x = x0 + (n + k) * (bw + gap)
        b.append(f'<rect x="{x}" y="{cy}" width="{bw}" height="52" rx="3" fill="none" '
                 f'stroke="{DROP}" stroke-width="1.6" stroke-dasharray="4 4"/>')
    b.append(t(x0 + (n + 1) * (bw + gap) + 12, cy + 76, "뒤가 오면", 12, LINE))
    b.append(t(x0 + (n + 1) * (bw + gap) + 12, cy + 94, "곡선이 달라진다", 12, LINE))

    b.append(note_box(147, 640, 500, "전체는 돌아보는 자리에서 생긴다"))
    b.append(caption(W / 2, 762, [
        "차례로 있었던 것을 모아 놓는 일과 하나로 묶는 일은 다르다.",
        "묶어서 생긴 곡선은 어느 순간에도 통째로 있지 않다.",
    ], 16))
    return base("모아 놓는 것과 묶는 것은 다르다", "순간을 모아도 전체가 되지 않는 이유", "".join(b))


FIGURES = {6: fig_two_pictures, 15: fig_open_closed, 25: fig_change_and_stay,
           34: fig_sum_of_moments}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild057")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
