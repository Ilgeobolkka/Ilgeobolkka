#!/usr/bin/env python3
"""book-032 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에도 눈금과 숫자를 넣지 않는다 — 세 항구의 자료는
값을 맞출 수 없고 방향만 견줄 수 있기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
BLUE, ORANGE, GRAY = "#3d5f7a", "#9c6b3c", "#8a9296"
SAND = "#c9b48d"


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


def fig_three_seats():
    """1.2 — 세 항구가 만 안에 놓인 자리와 두 가지 묶음."""
    ports = [(230, "여울항"), (400, "조개항"), (580, "달빛항")]
    b = ['<path d="M120,300 Q300,262 690,250 L690,392 Q300,380 120,342 Z" fill="#e6eef3" stroke="#bcd0da"/>']
    b.append(t(126, 285, "강", 16, "#46565b", "700", "start"))
    b.append(f'<path d="M105,300 L120,321 L105,342 Z" fill="{BLUE}" opacity="0.5"/>')
    b.append(t(676, 236, "열린 바다", 15, GRAY, "400", "end"))
    for x, name in ports:
        b.append(f'<circle cx="{x}" cy="336" r="9" fill="{BLUE}"/>')
        b.append(t(x, 322, name, 16, "#25353b", "700"))
    b.append(t(105, 440, "강어귀에서 떨어진 거리", 15, "#46565b", "700", "start"))
    for i, (x, _) in enumerate(ports):
        b.append(f'<rect x="{x - 45}" y="455" width="{40 + i * 42}" height="14" rx="7" fill="{SAND}"/>')
    b.append(t(105, 545, "셋으로 보기", 16, "#46565b", "700", "start"))
    for x, _ in ports:
        b.append(f'<circle cx="{x}" cy="580" r="9" fill="{BLUE}"/>')
        b.append(f'<circle cx="{x}" cy="580" r="26" fill="none" stroke="{BLUE}" stroke-dasharray="4 4"/>')
    b.append(t(105, 665, "안쪽과 바깥쪽으로 보기", 16, "#46565b", "700", "start"))
    for x, _ in ports:
        b.append(f'<circle cx="{x}" cy="700" r="9" fill="{BLUE}"/>')
    b.append(f'<rect x="188" y="672" width="256" height="56" rx="28" fill="none" stroke="{ORANGE}" stroke-dasharray="4 4"/>')
    b.append(f'<circle cx="580" cy="700" r="30" fill="none" stroke="{ORANGE}" stroke-dasharray="4 4"/>')
    b.append(note(800, "묶음을 하나만 만들면 그 묶음이 못 보는 것은 영영 안 보인다"))
    return base("같은 만, 세 자리", "어떻게 묶느냐가 무엇이 원인으로 보이는지를 정한다", "\n".join(b))


def fig_ten_maps():
    """2.2 — 남은 지도 열 장이 네 묶음."""
    sizes = [4, 3, 2, 1]
    b = [t(105, 250, "남은 지도 열 장", 16, "#46565b", "700", "start")]
    xs, i = [], 0
    for n in sizes:
        xs.append([140 + (i + k) * 54 for k in range(n)])
        i += n
    for row in xs:
        for x in row:
            b.append(f'<rect x="{x}" y="{270}" width="44" height="52" rx="5" fill="#f1f3f4" stroke="#c9d2d5"/>')
    b.append(t(105, 452, "닮은 것끼리 묶는다", 16, GRAY, "700", "start"))
    for row in xs:
        cx = sum(row) / len(row) + 22
        b.append(f'<rect x="{cx - 40}" y="{470}" width="80" height="104" rx="12" fill="#ffffff" stroke="{BLUE}"/>')
        b.append(f'<rect x="{cx - 22}" y="{490}" width="44" height="52" rx="5" fill="{BLUE}" opacity="0.85"/>')
        b.append(t(cx, 562, "한 번의 관찰", 13, "#46565b", "700"))
        for x in row:
            b.append(f'<path d="M{x + 22},322 L{cx},470" stroke="#ccd4d7" stroke-width="1"/>')
    b.append(f'<line x1="105" y1="620" x2="689" y2="620" stroke="#d9dfe1"/>')
    b.append(t(280, 660, "남은 장수 열", 18, GRAY, "700"))
    b.append(t(514, 660, "독립된 관찰 넷", 18, BLUE, "700"))
    b.append(note(790, "한 사람의 실수도 열 번 베껴지면 열 장이 된다"))
    return base("열 장이 네 묶음", "자료의 수와 근거의 수는 다르다", "\n".join(b))


def fig_depth_ships():
    """3.2 — 얕아지면 큰 배부터 못 들어온다."""
    labels = ["이른 때", "중간", "늦은 때"]
    silts = [26, 50, 70]
    drafts = [80, 58, 38]
    cargo = ["먼 곳 물건", "이웃 고을 물건", "만 안 물건"]
    b = []
    for k in range(3):
        y0 = 268 + k * 176
        b.append(f'<rect x="180" y="{y0}" width="380" height="140" fill="#e8f0f4" stroke="#bcd0da"/>')
        b.append(f'<rect x="180" y="{y0 + 140 - silts[k]}" width="380" height="{silts[k]}" fill="{SAND}"/>')
        b.append(t(170, y0 + 74, labels[k], 15, "#46565b", "700", "end"))
        for i, draft in enumerate(drafts):
            cx = 250 + i * 110
            top = y0 + 18
            hit = top + draft >= y0 + 140 - silts[k]
            stroke = "#b4453c" if hit else BLUE
            b.append(f'<rect x="{cx - 28}" y="{top}" width="56" height="{draft}" rx="6" '
                     f'fill="#ffffff" stroke="{stroke}" stroke-width="{3 if hit else 2}"/>')
            color = "#c3ccd0" if hit else "#46565b"
            b.append(t(cx, y0 - 10, cargo[i], 14, color, "700"))
    b.append(note(830, "무엇이 끊겼는지가 물의 깊이를 말해 준다"))
    return base("얕아지면 큰 배부터", "항구는 한꺼번에 닫히지 않는다", "\n".join(b))


def fig_three_curves():
    """4.1 — 세 항구의 드나든 배가 엇갈린다."""
    b = [f'<line x1="140" y1="620" x2="680" y2="620" stroke="#b9c2c6"/>']
    b.append(f'<path d="M140,300 C300,320 420,470 680,600" fill="none" stroke="{BLUE}" stroke-width="3"/>')
    b.append(t(150, 288, "여울항", 16, BLUE, "700", "start"))
    b.append(f'<path d="M140,430 C260,450 360,520 430,600" fill="none" stroke="{ORANGE}" stroke-width="3"/>')
    b.append(t(150, 418, "조개항", 16, ORANGE, "700", "start"))
    b.append(f'<path d="M140,584 C320,570 470,430 680,340" fill="none" stroke="#4b7a5b" stroke-width="3"/>')
    b.append(t(674, 328, "달빛항", 16, "#4b7a5b", "700", "end"))
    b.append(f'<line x1="452" y1="270" x2="452" y2="620" stroke="{GRAY}" stroke-dasharray="6 5"/>')
    b.append(t(452, 258, "같은 해", 15, GRAY, "700"))
    rows = [("여울항", "옮겨 가며 버팀", BLUE), ("조개항", "옮길 자리가 없음", ORANGE),
            ("달빛항", "받은 쪽", "#4b7a5b")]
    for i, (name, tail, color) in enumerate(rows):
        y = 690 + i * 34
        b.append(t(240, y, name, 16, color, "700", "end"))
        b.append(t(268, y, tail, 16, "#46565b", "400", "start"))
    b.append(note(820, "이웃이 잃은 것을 받은 것과 스스로 벌어들인 것은 다르다"))
    return base("하나가 줄면 하나가 는다", "같은 만에 있었다는 것이 같은 조건을 뜻하지는 않는다",
                "\n".join(b))


def fig_mismatch():
    """5.3 — 어긋난 칸을 다루는 법."""
    cols = ["여울항", "조개항", "달빛항"]
    rows = ["드나든 배", "통행세", "다룬 물건", "사람 수", "지도의 이름"]
    b = ['<rect x="230" y="250" width="330" height="44" fill="#eef2f4" stroke="#c9d2d5"/>']
    for i, c in enumerate(cols):
        b.append(t(285 + i * 110, 278, c, 16, "#33454b", "700"))
        if i:
            b.append(f'<line x1="{230 + i * 110}" y1="250" x2="{230 + i * 110}" y2="514" stroke="#dde3e5"/>')
    for r, name in enumerate(rows):
        y = 294 + r * 44
        b.append(f'<rect x="230" y="{y}" width="330" height="44" fill="none" stroke="#dde3e5"/>')
        b.append(t(220, y + 28, name, 15, "#46565b", "400", "end"))
        for i in range(3):
            cx = 285 + i * 110
            if r == 2 and i == 1:
                b.append(f'<line x1="{cx - 30}" y1="{y + 18}" x2="{cx + 30}" y2="{y + 18}" stroke="#7d8a8f" stroke-width="2"/>')
                b.append(f'<line x1="{cx - 30}" y1="{y + 32}" x2="{cx + 30}" y2="{y + 32}" stroke="#7d8a8f" stroke-width="2"/>')
                b.append(f'<line x1="{cx}" y1="{y + 18}" x2="{cx}" y2="{y + 32}" stroke="#b4453c" stroke-width="2"/>')
                continue
            b.append(f'<line x1="{cx - 30}" y1="{y + 25}" x2="{cx + 30}" y2="{y + 25}" stroke="#c2cbcf" stroke-width="2"/>')
    b.append(t(578, 400, "두 자료가 다른 값을 적었다", 15, "#b4453c", "700", "start"))
    outs = [("한쪽을 고른다", False), ("둘 다 적는다", True), ("견줄 수 없다고 적는다", False)]
    for i, (name, mark) in enumerate(outs):
        y = 570 + i * 62
        b.append(f'<path d="M395,530 L395,{y + 22} L455,{y + 22}" fill="none" stroke="{GRAY}" stroke-width="1.5" marker-end="url(#gray)"/>')
        stroke = BLUE if mark else "#c9d2d5"
        b.append(f'<rect x="465" y="{y}" width="224" height="44" rx="10" fill="#ffffff" stroke="{stroke}" stroke-width="{2 if mark else 1}"/>')
        b.append(t(577, y + 28, name, 15, "#33454b" if mark else "#5b686d", "700" if mark else "400"))
    b.append(note(790, "어긋남은 흠이 아니라 그 자리에 무언가 있었다는 표시다"))
    return base("어긋난 칸을 다루는 법", "어긋남을 지우면 어긋남이 알려 주던 것도 지워진다", "\n".join(b))


def fig_surviving_maps():
    """6.2 — 만들어진 지도와 남은 지도의 치우침."""
    def bay(y0, label, inner_solid):
        out = [f'<path d="M150,{y0 + 42} Q340,{y0 + 12} 660,{y0} L660,{y0 + 140} Q340,{y0 + 128} 150,{y0 + 98} Z" '
               f'fill="#e6eef3" stroke="#bcd0da"/>', t(150, y0 - 12, label, 17, "#46565b", "700", "start")]
        inner = [(200, 40), (250, 74), (300, 34), (350, 86), (400, 52)]
        outer = [(470, 40), (520, 80), (570, 36), (620, 74)]
        for i, (x, dy) in enumerate(inner):
            if i < inner_solid:
                out.append(f'<rect x="{x}" y="{y0 + dy}" width="20" height="20" rx="3" fill="{BLUE}" opacity="0.8"/>')
            else:
                out.append(f'<rect x="{x}" y="{y0 + dy}" width="20" height="20" rx="3" fill="none" '
                           f'stroke="#c3ccd0" stroke-dasharray="3 3"/>')
        for x, dy in outer:
            out.append(f'<rect x="{x}" y="{y0 + dy}" width="20" height="20" rx="3" fill="{BLUE}" opacity="0.8"/>')
        return out
    b = bay(280, "만들어졌던 지도", 5) + bay(560, "오늘 남은 지도", 2)
    b.append(f'<path d="M690,440 L690,540" stroke="{GRAY}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(t(686, 496, "쓸모가 끝나면", 14, GRAY, "400", "end"))
    b.append(t(686, 516, "옮겨 그리지 않는다", 14, GRAY, "400", "end"))
    b.append(note(790, "남은 자료의 치우침은 대상의 치우침이 아니다"))
    return base("남은 쪽만 자세하다", "흐릿한 것은 그때 흐릿했기 때문이 아닐 수 있다", "\n".join(b))


FIGURES = {
    5: fig_three_seats,
    11: fig_ten_maps,
    19: fig_depth_ships,
    25: fig_three_curves,
    36: fig_mismatch,
    42: fig_surviving_maps,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild032")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
