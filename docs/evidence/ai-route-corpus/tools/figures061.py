#!/usr/bin/env python3
"""book-061 이미지 페이지 6개의 SVG 생성. figures041.py/071.py의 t()/base() 패턴을 따른다."""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

BLUE = "#2f5d82"
BLUE_SOFT = "#bcd2e4"
GRAY = "#9aa3a8"
GRAY_SOFT = "#dfe4e6"
INK = "#27353a"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="blue" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{BLUE}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{GRAY}"/></marker>
</defs>
{t(W/2, 122, title, 32, '#203238', '700')}
{t(W/2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def frame(x, y, w, h, stroke="#8b989d", width=2):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#ffffff" stroke="{stroke}" stroke-width="{width}"/>'


def caption(x, y, lines, size=15, color="#55666b"):
    return "".join(t(x, y + i * 24, line, size, color) for i, line in enumerate(lines))


def note_box(x, y, w, h, label, size=17):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def curve_path(x0, y0, x1, y1, cx1, cy1, cx2, cy2, color=BLUE):
    """점에서 뻗어 아래로 갈수록 굵어지는 곡선을 세 구간으로 나눠 그린다."""
    def pt(s):
        mt = 1 - s
        x = mt**3 * x0 + 3 * mt**2 * s * cx1 + 3 * mt * s**2 * cx2 + s**3 * x1
        y = mt**3 * y0 + 3 * mt**2 * s * cy1 + 3 * mt * s**2 * cy2 + s**3 * y1
        return x, y
    out = []
    steps = [(0.0, 0.34, 1.8), (0.33, 0.67, 3.2), (0.66, 1.0, 4.8)]
    for a, b, width in steps:
        pts = [pt(a + (b - a) * i / 12) for i in range(13)]
        d = "M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in pts)
        out.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round"/>')
    return "".join(out)


def fig_elements():
    """p5 — 점, 선, 면이 차례로 성질을 더하는 순서."""
    b = [note_box(147, 258, 500, 60, "형태요소는 앞선 요소를 포함하며 새로운 성질을 더한다")]
    cx = [197, 397, 597]
    top, size = 412, 212
    mid = top + size / 2
    for x in cx:
        b.append(f'<rect x="{x-size/2}" y="{top}" width="{size}" height="{size}" rx="14" fill="#fbfcfc" stroke="{GRAY_SOFT}"/>')
    # 점
    b.append(f'<circle cx="{cx[0]}" cy="{mid}" r="11" fill="{BLUE}"/>')
    # 선 (양 끝에 시작점·끝점)
    b.append(f'<line x1="{cx[1]-62}" y1="{mid+58}" x2="{cx[1]+62}" y2="{mid-58}" stroke="{BLUE}" stroke-width="3.5"/>')
    b.append(f'<circle cx="{cx[1]-62}" cy="{mid+58}" r="8" fill="{BLUE}"/>')
    b.append(f'<circle cx="{cx[1]+62}" cy="{mid-58}" r="8" fill="{BLUE}"/>')
    b.append(t(cx[1]-62, mid+86, "시작", 13, "#6b7a80"))
    b.append(t(cx[1]+62, mid-72, "끝", 13, "#6b7a80"))
    # 면 (닫힌 삼각형)
    b.append(f'<polygon points="{cx[2]-70},{mid+62} {cx[2]+70},{mid+62} {cx[2]},{mid-72}" fill="{BLUE_SOFT}" stroke="{BLUE}" stroke-width="3"/>')
    b.append(f'<polyline points="{cx[2]-70},{mid+62} {cx[2]+70},{mid+62} {cx[2]},{mid-72} {cx[2]-70},{mid+62}" fill="none" stroke="{BLUE}" stroke-width="1.4" stroke-dasharray="5,4"/>')
    # 단계 화살표
    for x in (cx[0] + size / 2 + 8, cx[1] + size / 2 + 8):
        b.append(f'<line x1="{x}" y1="{mid}" x2="{x+72}" y2="{mid}" stroke="{GRAY}" stroke-width="2.5" marker-end="url(#gray)"/>')
    labels = [("점", "위치만 있음"), ("선", "방향과 길이가 생김"), ("면", "넓이와 무게가 생김")]
    for x, (name, desc) in zip(cx, labels):
        b.append(t(x, top + size + 62, name, 26, INK, "700"))
        b.append(t(x, top + size + 100, desc, 16, "#55666b"))
    b.append(caption(W / 2, 862, [
        "점은 자리를 정하고, 선은 그 자리들을 이어 방향을 만들며,",
        "닫힌 선의 안쪽이 무게를 가진 면이 된다.",
    ], 16))
    return base("형태요소가 만들어지는 순서", "점에서 선으로, 선에서 면으로", "".join(b))


def fig_grid():
    """p11 — 삼등분 격자와 교차점 배치."""
    gx, gy, g = 232, 250, 330
    b = [frame(gx, gy, g, g)]
    for i in (1, 2):
        b.append(f'<line x1="{gx+g*i/3}" y1="{gy}" x2="{gx+g*i/3}" y2="{gy+g}" stroke="{GRAY_SOFT}" stroke-width="1.6"/>')
        b.append(f'<line x1="{gx}" y1="{gy+g*i/3}" x2="{gx+g}" y2="{gy+g*i/3}" stroke="{GRAY_SOFT}" stroke-width="1.6"/>')
    for i in (1, 2):
        for j in (1, 2):
            b.append(f'<circle cx="{gx+g*i/3}" cy="{gy+g*j/3}" r="8" fill="{BLUE}"/>')
    b.append(f'<circle cx="{gx+g/2}" cy="{gy+g/2}" r="7" fill="{GRAY}" opacity="0.38"/>')
    b.append(t(gx + g / 2, gy - 18, "네 교차점 — 중심 형태를 두기 좋은 자리", 15, "#55666b"))
    b.append(f'<line x1="{gx+g+34}" y1="{gy+g/2}" x2="{gx+g/2+14}" y2="{gy+g/2}" stroke="{GRAY}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(t(gx + g + 42, gy + g / 2 + 5, "정중앙보다 교차점이", 13, "#6b7a80", anchor="start"))
    b.append(t(gx + g + 42, gy + g / 2 + 25, "더 자연스럽다", 13, "#6b7a80", anchor="start"))

    ex, ey, ew, eh = 232, 660, 330, 250
    b.append(frame(ex, ey, ew, eh))
    for i in (1, 2):
        b.append(f'<line x1="{ex+ew*i/3}" y1="{ey}" x2="{ex+ew*i/3}" y2="{ey+eh}" stroke="#eef1f2" stroke-width="1.2"/>')
        b.append(f'<line x1="{ex}" y1="{ey+eh*i/3}" x2="{ex+ew}" y2="{ey+eh*i/3}" stroke="#eef1f2" stroke-width="1.2"/>')
    b.append(f'<circle cx="{ex+ew/3+6}" cy="{ey+eh/3-4}" r="11" fill="{BLUE}"/>')
    b.append(f'<path d="M{ex+ew/3+18} {ey+eh/3+8} C{ex+ew*0.62} {ey+eh*0.55} {ex+ew*0.66} {ey+eh*0.7} {ex+ew-30} {ey+eh-34}" fill="none" stroke="{BLUE}" stroke-width="2.6"/>')
    b.append(t(ex + ew / 2, ey + eh + 30, "배치 예시 — 교차점의 점 하나와 아래로 흐르는 선", 15, "#55666b"))
    b.append(caption(W / 2, 972, ["격자는 지켜야 할 규칙이 아니라 배치를 가늠하는 출발점이다."]))
    return base("화면 삼등분 격자", "형태를 어디에 둘지 가늠하는 기준선", "".join(b))


def fig_balance():
    """p19 — 대칭 균형과 비대칭 균형 비교."""
    b = []
    lx, rx, y, s = 118, 412, 292, 264
    b.append(frame(lx, y, s, s))
    b.append(f'<line x1="{lx+s/2}" y1="{y+12}" x2="{lx+s/2}" y2="{y+s-12}" stroke="{GRAY}" stroke-width="1.6" stroke-dasharray="6,5"/>')
    b.append(f'<circle cx="{lx+s*0.27}" cy="{y+s/2}" r="34" fill="{BLUE_SOFT}" stroke="{BLUE}" stroke-width="2"/>')
    b.append(f'<circle cx="{lx+s*0.73}" cy="{y+s/2}" r="34" fill="{BLUE_SOFT}" stroke="{BLUE}" stroke-width="2"/>')
    b.append(t(lx + s / 2, y - 16, "대칭 균형", 22, INK, "700"))
    b.append(caption(lx + s / 2, y + s + 34, ["좌우가 같음", "안정적이지만 단조로움"], 15))

    b.append(frame(rx, y, s, s))
    b.append(f'<rect x="{rx+26}" y="{y+s*0.3}" width="112" height="112" fill="{BLUE_SOFT}" opacity="0.75"/>')
    b.append(f'<circle cx="{rx+s-52}" cy="{y+s*0.42}" r="15" fill="#173d5c"/>')
    b.append(f'<line x1="{rx+82}" y1="{y+s*0.84}" x2="{rx+s-52}" y2="{y+s*0.84}" stroke="{GRAY}" stroke-width="1.6" stroke-dasharray="5,4"/>')
    b.append(f'<polygon points="{rx+s*0.52},{y+s*0.84} {rx+s*0.48},{y+s*0.91} {rx+s*0.56},{y+s*0.91}" fill="{GRAY}"/>')
    b.append(t(rx + s / 2, y - 16, "비대칭 균형", 22, INK, "700"))
    b.append(caption(rx + s / 2, y + s + 34, ["크고 옅은 것과 작고 진한 것", "무게감이 상쇄됨"], 15))

    b.append(note_box(147, 700, 500, 56, "둘 다 균형이지만 만드는 방식이 다르다"))
    b.append(caption(W / 2, 812, [
        "무게감은 크기만이 아니라 색의 진하기, 형태의 복잡성,",
        "화면 안에서의 위치가 함께 결정한다.",
    ]))
    return base("두 가지 균형", "좌우를 맞추는 방식과 무게를 맞추는 방식", "".join(b))


def fig_scale():
    """p28 — 같은 배치를 작은 화면과 큰 화면에 놓았을 때의 차이."""
    b = []
    sx, sy, ss = 130, 372, 150
    b.append(frame(sx, sy, ss, ss))
    b.append(f'<circle cx="{sx+ss*0.34}" cy="{sy+ss*0.3}" r="7" fill="{BLUE}"/>')
    b.append(f'<line x1="{sx+ss*0.42}" y1="{sy+ss*0.42}" x2="{sx+ss*0.68}" y2="{sy+ss*0.62}" stroke="{BLUE}" stroke-width="2.6"/>')
    b.append(caption(sx + ss / 2, sy + ss + 34, ["작은 화면", "형태 사이 거리가 짧아", "관계가 촘촘함"], 14))

    bx, by, bs = 380, 292, 300
    b.append(frame(bx, by, bs, bs))
    # 형태가 놓이지 않은 아래쪽 영역을 빈 공간으로 강조 — 형태와 겹치지 않게 둔다
    b.append(f'<rect x="{bx+bs*0.12}" y="{by+bs*0.7}" width="{bs*0.62}" height="{bs*0.18}" fill="#eef1f2"/>')
    b.append(t(bx + bs * 0.43, by + bs * 0.805, "빈 공간", 15, "#7d878b"))
    b.append(f'<circle cx="{bx+bs*0.34}" cy="{by+bs*0.3}" r="14" fill="{BLUE}"/>')
    b.append(f'<line x1="{bx+bs*0.42}" y1="{by+bs*0.42}" x2="{bx+bs*0.68}" y2="{by+bs*0.62}" stroke="{BLUE}" stroke-width="4"/>')
    b.append(caption(bx + bs / 2, by + bs + 34, ["큰 화면 — 같은 비율이어도", "빈 공간이 하나의 무게로 느껴짐"], 14))

    b.append(f'<line x1="295" y1="447" x2="368" y2="447" stroke="{GRAY}" stroke-width="2.5" marker-end="url(#gray)"/>')
    b.append(note_box(197, 752, 400, 56, "비율은 같지만 인상은 다르다"))
    b.append(caption(W / 2, 866, [
        "확대할 때는 형태의 크기만이 아니라",
        "함께 커진 빈 공간의 무게까지 다시 계산해야 한다.",
    ]))
    return base("화면 크기와 인상", "같은 배치를 두 크기에 놓았을 때", "".join(b))


def _practice_frame(x, y, w, h, with_area=False, faint=False):
    """실전 사례 화면 하나. with_area=True면 완성 단계의 면과 흐린 선을 함께 그린다."""
    b = [frame(x, y, w, h)]
    px, py = x + w * 0.64, y + h * 0.22
    ex, ey = x + w * 0.2, y + h * 0.78
    if with_area:
        b.append(f'<rect x="{x+w*0.1}" y="{y+h*0.63}" width="{w*0.4}" height="{h*0.22}" fill="#e3e7e9"/>')
        if faint:
            # 곡선과 겹치지 않도록 오른쪽 아래의 빈 공간에만 흐린 선을 겹친다
            for fy in (0.5, 0.58, 0.66):
                b.append(f'<line x1="{x+w*0.54}" y1="{y+h*fy}" x2="{x+w*0.88}" y2="{y+h*(fy-0.025)}" stroke="#d9dee0" stroke-width="1.4"/>')
    b.append(curve_path(px, py + 10, ex, ey, x + w * 0.6, y + h * 0.45, x + w * 0.32, y + h * 0.6))
    b.append(f'<circle cx="{px}" cy="{py}" r="{9 if not with_area else 11}" fill="#173d5c"/>')
    return b, (px, py), (ex, ey)


def fig_step1():
    """p36 — 점과 선만으로 동선이 생긴 1단계."""
    x, y, w, h = 250, 262, 294, 470
    b, (px, py), (ex, ey) = _practice_frame(x, y, w, h)
    # 곡선과 나란한 점선 대신, 곡선이 끝나는 자리에 진행 방향만 표시한다
    b.append(f'<polygon points="{ex-9},{ey+14} {ex+9},{ey+14} {ex},{ey+32}" fill="{GRAY}"/>')
    b.append(t(px + 26, py - 8, "첫 점", 15, "#6b7a80", anchor="start"))
    b.append(t(ex + 22, ey + 30, "시선이 내려오는 방향", 15, "#6b7a80", anchor="start"))
    b.append(note_box(147, 790, 500, 58, "점의 위치와 선의 방향만으로 동선이 생긴다"))
    b.append(caption(W / 2, 902, [
        "위쪽 교차점에서 시작한 시선이 곡선을 따라",
        "왼쪽 아래로 천천히 내려온다.",
    ], 16))
    return base("화면 연습 — 첫 단계", "점을 놓고 선으로 방향을 잡기", "".join(b))


def fig_final():
    """p40 — 1단계와 완성 단계를 나란히 비교."""
    b = []
    lx, rx, y, w, h = 118, 434, 300, 240, 336
    sb, _, _ = _practice_frame(lx, y, w, h)
    b += sb
    b.append(caption(lx + w / 2, y + h + 36, ["첫 단계", "점과 선"], 15))
    fb, _, _ = _practice_frame(rx, y, w, h, with_area=True, faint=True)
    b += fb
    b.append(caption(rx + w / 2, y + h + 36, ["완성 단계", "면을 더하고 균형을 맞춤"], 15))
    b.append(f'<line x1="{lx+w+16}" y1="{y+h/2}" x2="{rx-16}" y2="{y+h/2}" stroke="{GRAY}" stroke-width="2.5" marker-end="url(#gray)"/>')
    b.append(note_box(147, 748, 500, 56, "점 하나에서 균형 잡힌 전체 화면까지"))
    b.append(caption(W / 2, 862, [
        "면은 중심 쪽으로 옮겨 위아래 무게를 맞추고,",
        "빈 공간에는 흐린 선을 더해 커진 여백을 보충했다.",
    ]))
    return base("화면 연습 — 완성 단계", "첫 단계와 나란히 비교", "".join(b))


FIGURES = {5: fig_elements, 11: fig_grid, 19: fig_balance,
           28: fig_scale, 36: fig_step1, 40: fig_final}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tmp/pdfs/book-061")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
