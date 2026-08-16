#!/usr/bin/env python3
"""도표 SVG 공용 부품. figures081~090이 함께 쓴다.

색 토큰과 t()·note_box()·caption()은 열 권이 같은 값을 쓴다. 화면 크기와 제목 자리도 같다.
다른 것은 `<defs>`뿐이라 base()를 make_base(defs)로 만들어 권마다 필요한 마커·무늬만 넣는다.

권마다 다른 색(바다·물 같은 것)과 그림 함수는 각 figuresNNN.py에 둔다.
"""

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 계획대로 되는 쪽
DROP = "#c3ccd0"          # 어긋나거나 사라지는 쪽
MARK = "#b4703a"          # 표시·강조

MARKER_KEEP = (f'  <marker id="keep" markerWidth="10" markerHeight="10" refX="9" refY="3" '
               f'orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{KEEP}"/></marker>\n')
MARKER_MARK = (f'  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" '
               f'orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>\n')
MARKER_GRAY = (f'  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" '
               f'orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>\n')
DEFAULT_DEFS = MARKER_KEEP + MARKER_MARK + MARKER_GRAY


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def make_base(defs=DEFAULT_DEFS):
    """제목·부제·가로선이 같은 자리에 오는 base()를 만든다. defs는 `<defs>` 안에 그대로 들어간다."""
    def base(title, subtitle, body):
        return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
{defs}</defs>
{t(W / 2, 122, title, 31, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''
    return base


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))
