#!/usr/bin/env python3
"""book-036 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 치수와 날짜를 넣지 않는다 — 남은 편지가
걸러진 묶음이라 값으로 견줄 수 없고 차례와 결만 견줄 수 있기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
BLUE, ORANGE, GRAY = "#3d5f7a", "#9c6b3c", "#8a9296"
RED = "#b4453c"
PAPER = "#efe9dd"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{GRAY}"/></marker>
</defs>
{t(W/2, 122, title, 32, '#203238', '700')}
{t(W/2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note(y, text, size=16):
    return ('<rect x="105" y="%d" width="584" height="76" rx="16" fill="#fff8e8" stroke="#c5a866"/>' % y
            + t(W / 2, y + 46, text, size, "#51462c", "700"))


def envelope(x, y, w=34, h=22, solid=True):
    fill = PAPER if solid else "none"
    stroke = "#a99e88" if solid else "#c3ccd0"
    dash = '' if solid else ' stroke-dasharray="3 3"'
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="2" fill="{fill}" stroke="{stroke}"{dash}/>'
            f'<path d="M{x},{y} L{x + w/2},{y + h*0.6} L{x + w},{y}" fill="none" stroke="{stroke}"{dash}/>')


def house(cx, y, label):
    return (f'<path d="M{cx - 46},{y + 34} L{cx - 46},{y} L{cx},{y - 30} L{cx + 46},{y} '
            f'L{cx + 46},{y + 34} Z" fill="#eef2f4" stroke="#b6c2c7"/>'
            + t(cx, y + 62, label, 15, "#46565b", "700"))


def fig_attic():
    """1.3 — 한 집 다락에 남은 것."""
    b = [house(190, 300, "다형의 집"), house(604, 300, "은설의 집")]
    for i in range(7):
        b.append(envelope(258 + i * 40, 300 + (i % 3) * 16, solid=True))
    for i in range(3):
        b.append(envelope(276 + i * 100, 400 + (i % 2) * 14, solid=False))
    b.append(f'<path d="M250,318 L556,318" stroke="{GRAY}" stroke-width="1.5" marker-end="url(#gray)"/>')
    b.append(f'<path d="M556,428 L250,428" stroke="#c3ccd0" stroke-width="1.5" stroke-dasharray="4 4"/>')
    b.append(f'<rect x="520" y="470" width="168" height="120" rx="10" fill="#faf7f0" stroke="#c9bfa8"/>')
    b.append(t(604, 496, "다락에서 나온 묶음", 14, "#6b5f45", "700"))
    for i in range(6):
        b.append(envelope(536 + (i % 3) * 50, 512 + (i // 3) * 34, 30, 20, True))
    for i in range(3):
        b.append(f'<path d="M{604},{404 + i * 6} L{604},{466}" stroke="#d3d9db" stroke-width="1"/>')
    b.append(t(190, 470, "흩어져 사라짐", 14, GRAY))
    b.append(t(604, 620, "보낸 편지는 상대에게 가 있다", 14, GRAY))
    b.append(note(760, "한 집에서 나온 묶음은 그 집이 받은 것들이다"))
    return base("한 집 다락에 남은 것", "남은 수가 아니라 남은 방식이 그 수를 만든다", "\n".join(b))


def fig_repeated():
    """2.4 — 되풀이된 네 물음."""
    rows = [("밭을 언제 나누나", [0, 2, 5], 7), ("누가 다녀갔나", [1, 3, 6], 8),
            ("값을 받았나", [0, 3, 4], 6), ("그 일은 어찌 되었나", [2, 4, 6, 8], None)]
    b = []
    for r, (name, asks, ans) in enumerate(rows):
        y = 300 + r * 120
        b.append(f'<line x1="270" y1="{y}" x2="670" y2="{y}" stroke="#c9d2d5"/>')
        b.append(t(258, y + 5, name, 15, "#46565b", "700", "end"))
        for a in asks:
            x = 285 + a * 42
            b.append(f'<line x1="{x}" y1="{y}" x2="{x}" y2="{y - 30}" stroke="{BLUE}" stroke-width="3"/>')
        if ans is not None:
            x = 285 + ans * 42
            b.append(f'<line x1="{x}" y1="{y}" x2="{x}" y2="{y + 30}" stroke="{ORANGE}" stroke-width="3"/>')
        else:
            b.append(t(670, y + 26, "끝내 오지 않음", 14, RED, "700", "end"))
    b.append(t(270, 268, "물음", 14, BLUE, "700", "start"))
    b.append(t(330, 268, "답", 14, ORANGE, "700", "start"))
    b.append(note(800, "묻기를 멈춘 자리가 답이 온 자리는 아니다"))
    return base("되풀이된 네 물음", "답이 없어 다시 묻는 일에도 자국이 남는다", "\n".join(b))


def fig_two_sentences():
    """3.3 — 같은 날, 두 문장."""
    pairs = [("누가 왔는지", "무엇을 가져왔는지"), ("몇이 모였는지", "무슨 말이 오갔는지"),
             ("그날 날씨", "그날 마음")]
    b = [f'<line x1="397" y1="250" x2="397" y2="560" stroke="#d9dfe1"/>']
    b.append(t(250, 278, "다형이 적은 것", 16, BLUE, "700"))
    b.append(t(544, 278, "은설이 적은 것", 16, ORANGE, "700"))
    for i, (left, right) in enumerate(pairs):
        y = 340 + i * 70
        b.append(f'<rect x="130" y="{y - 22}" width="200" height="40" rx="10" fill="#eef2f4" stroke="#c9d2d5"/>')
        b.append(t(230, y + 4, left, 15, "#33454b"))
        b.append(f'<rect x="464" y="{y - 22}" width="200" height="40" rx="10" fill="#faf4ea" stroke="#d8cbb4"/>')
        b.append(t(564, y + 4, right, 15, "#4a3f2c"))
        b.append(f'<rect x="352" y="{y - 6}" width="90" height="8" rx="4" fill="#d3d9db"/>')
        b.append(t(397, y - 14, "같은 날", 12, GRAY))
    b.append(f'<line x1="105" y1="600" x2="689" y2="600" stroke="#d9dfe1"/>')
    b.append(t(397, 632, "두 줄을 합치면 둘 다 잃는다", 16, "#46565b", "700"))
    b.append(note(760, "어긋난 자리는 두 줄로 남긴다"))
    return base("같은 날, 두 문장", "어긋남은 틀림이 아니라 자리의 차이다", "\n".join(b))


def fig_two_layers():
    """4.4 — 두 층에 적힌 같은 봄."""
    b = [t(105, 290, "관에서 적은 것", 16, "#46565b", "700", "start")]
    b.append(f'<rect x="105" y="310" width="584" height="60" rx="8" fill="#eef2f4" stroke="#c9d2d5"/>')
    top = [(190, "일이 난 날"), (400, "거둔 수"), (610, "끝난 날")]
    for x, name in top:
        b.append(f'<rect x="{x - 5}" y="326" width="10" height="28" rx="2" fill="{BLUE}"/>')
        b.append(t(x, 392, name, 13, "#5b686d"))
    b.append(t(105, 500, "편지에 적힌 것", 16, "#46565b", "700", "start"))
    b.append(f'<rect x="105" y="520" width="584" height="60" rx="8" fill="#faf4ea" stroke="#d8cbb4"/>')
    xs = [130 + i * 21 for i in range(27)]
    for i, x in enumerate(xs):
        h = 14 + (i * 7 % 18)
        b.append(f'<rect x="{x}" y="{550 - h/2}" width="6" height="{h}" rx="2" fill="{ORANGE}" opacity="0.75"/>')
    marks = [(200, "안부가 길어진 날"), (360, "밖에 나가지 말라는 말"),
             (520, "밥 이야기가 사라진 날"), (650, "다시 밭 이야기가 나온 날")]
    for i, (x, name) in enumerate(marks):
        y = 606 + (i % 2) * 26
        b.append(t(x, y, name, 13, "#6b5f45"))
    for x in (190, 400, 610):
        b.append(f'<line x1="{x}" y1="372" x2="{x}" y2="518" stroke="#d3d9db" stroke-dasharray="4 4"/>')
        b.append(t(x, 448, "같은 무렵", 12, GRAY))
    b.append(note(760, "큰 기록은 자리를 주고 편지는 결을 준다"))
    return base("두 층에 적힌 같은 봄", "어느 쪽도 다른 쪽을 대신하지 못한다", "\n".join(b))


def fig_grain_change():
    """5.3 — 결이 바뀐 자리."""
    b = []
    bands = ["문장의 길이", "안부의 길이", "밖에 관한 말", "밥에 관한 말"]
    for i, name in enumerate(bands):
        y = 300 + i * 54
        b.append(t(258, y + 24, name, 14, "#46565b", "700", "end"))
        for c in range(24):
            x = 272 + c * 17
            near = 9 <= c <= 15
            op = 0.75 if near else 0.22
            b.append(f'<rect x="{x}" y="{y}" width="15" height="34" fill="{ORANGE}" opacity="{op}"/>')
    b.append(f'<rect x="424" y="288" width="123" height="234" rx="8" fill="none" stroke="{RED}" stroke-width="2.5"/>')
    b.append(t(486, 278, "이 무렵에 무슨 일이 있었다", 14, RED, "700"))
    b.append(f'<line x1="272" y1="600" x2="680" y2="600" stroke="#b9c2c6"/>')
    dots = [(0, 3), (1, 1), (2, 4), (3, 2), (5, 3), (6, 1), (8, 2), (9, 4), (10, 1), (11, 3),
            (13, 2), (14, 4), (16, 1), (17, 3), (18, 2), (20, 4), (21, 1), (22, 3), (23, 2)]
    for c, k in dots:
        for j in range(k):
            b.append(f'<circle cx="{278 + c * 17}" cy="{590 - j * 12}" r="3.5" fill="{BLUE}" opacity="0.8"/>')
    for lo, hi in ((3, 5), (11, 13), (18, 20)):
        b.append(f'<rect x="{272 + lo * 17}" y="604" width="{(hi - lo) * 17}" height="12" fill="#d3d9db"/>')
    b.append(t(272, 646, "사라진 편지가 있는 자리", 13, GRAY, "400", "start"))
    b.append(note(760, "가운데를 비워 둔 채로도 둘레는 그릴 수 있다"))
    return base("결이 바뀐 자리", "적히지 않은 일이 적는 방식에 남는다", "\n".join(b))


def fig_three_filters():
    """6.3 — 세 번 걸러진 묶음."""
    bands = [("오간 편지", 560), ("한쪽 집에 모인 편지", 420), ("버리지 않은 편지", 300),
             ("오늘 남은 편지", 210)]
    reasons = ["보낸 것은 상대에게", "다투거나 곤란한 것은 버림", "불과 물과 이사"]
    b = []
    y = 260
    prev = None
    for i, (name, w) in enumerate(bands):
        x = 397 - w / 2
        shade = 0.10 + i * 0.08
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="62" rx="8" fill="{ORANGE}" opacity="{shade}" stroke="#d8cbb4"/>')
        b.append(t(397, y + 38, name, 16, "#4a3f2c", "700"))
        if prev:
            px, pw, py = prev
            b.append(f'<line x1="{px}" y1="{py + 62}" x2="{x}" y2="{y}" stroke="#cfc7b4"/>')
            b.append(f'<line x1="{px + pw}" y1="{py + 62}" x2="{x + w}" y2="{y}" stroke="#cfc7b4"/>')
            b.append(t(397, py + 92, reasons[i - 1], 14, ORANGE))
        prev = (x, w, y)
        y += 122
    b.append(f'<line x1="{397 + 280 + 12}" y1="260" x2="{397 + 280 + 12}" y2="322" stroke="#8a9296" stroke-width="2"/>')
    b.append(t(689, 296, "실제로 오간 봄", 14, GRAY, "400", "end"))
    b.append(f'<line x1="{397 + 105 + 12}" y1="626" x2="{397 + 105 + 12}" y2="688" stroke="{BLUE}" stroke-width="2"/>')
    b.append(t(689, 662, "이 묶음으로 그린 봄", 14, BLUE, "400", "end"))
    b.append(note(780, "걸러진 방향을 알면 깎아 읽을 방향도 안다"))
    return base("세 번 걸러진 묶음", "남은 것은 남기기로 한 것이다", "\n".join(b))


FIGURES = {
    7: fig_attic,
    15: fig_repeated,
    21: fig_two_sentences,
    30: fig_two_layers,
    36: fig_grain_change,
    44: fig_three_filters,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild036")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
