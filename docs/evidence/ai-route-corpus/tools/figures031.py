#!/usr/bin/env python3
"""book-031 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 눈금과 숫자를 넣지 않는다 — 연대기에서 얻은
수치가 아니라 기록의 구조를 보이는 그림이기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
BLUE, ORANGE, GRAY = "#3d5f7a", "#9c6b3c", "#8a9296"


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


def note(y, text, size=17):
    return ('<rect x="105" y="%d" width="584" height="72" rx="16" fill="#fff8e8" stroke="#c5a866"/>' % y
            + t(W / 2, y + 44, text, size, "#51462c", "700"))


def fig_year_to_line():
    """1.2 — 한 해에 일어난 일 가운데 연대기에 오르는 몫."""
    gates = [(240, "성주가 바뀌었는가"), (420, "물에 관한 일인가"), (600, "성 밖과 오간 일인가")]
    b = ['<rect x="105" y="250" width="584" height="150" rx="14" fill="#eef1f2" stroke="#c9d2d5"/>',
         t(W / 2, 285, "그 해에 일어난 일", 18, "#46565b", "700")]
    spots = [(150, 350, 15), (196, 332, 10), (320, 362, 20), (350, 330, 12), (490, 358, 9),
             (525, 340, 17), (545, 368, 11), (660, 336, 14), (680, 362, 9), (172, 372, 8)]
    for cx, cy, r in spots:
        b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#b9c6cc" opacity="0.75"/>')
    for x, _ in gates:
        b.append(f'<circle cx="{x}" cy="348" r="19" fill="{BLUE}" opacity="0.85"/>')
    for x, label in gates:
        b.append(t(x, 428, label, 14, BLUE, "700"))
        b.append(f'<rect x="{x - 46}" y="440" width="92" height="120" rx="10" fill="#ffffff" stroke="{BLUE}"/>')
        b.append(f'<path d="M{x},367 L{x},440" stroke="{BLUE}" stroke-width="2"/>')
        b.append(f'<circle cx="{x}" cy="500" r="19" fill="{BLUE}" opacity="0.85"/>')
        b.append(f'<path d="M{x},560 L{x},612" stroke="{GRAY}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append('<rect x="105" y="620" width="584" height="86" rx="14" fill="#ffffff" stroke="#c9d2d5"/>')
    b.append(t(W / 2, 650, "연대기에 적힌 일", 18, "#46565b", "700"))
    for x, _ in gates:
        b.append(f'<circle cx="{x}" cy="678" r="14" fill="{BLUE}" opacity="0.85"/>')
    b.append(f'<path d="M172,384 L150,576" stroke="{GRAY}" stroke-width="1.5"/>')
    b.append(t(150, 600, "기준에 들지 않았다", 15, GRAY, "400", "middle"))
    b.append(note(790, "자주 적힌 것은 자주 일어난 것이 아니라 자주 살핀 것이다"))
    return base("한 해에서 한 줄로", "일어난 일과 적힌 일의 폭은 다르다", "\n".join(b))


def fig_same_event_twice():
    """2.3 — 같은 사건이 다른 해에 두 번 오른 자리."""
    b = []
    for x, label, mark in ((175, "앞쪽 칸", 3), (509, "뒤쪽 칸", 6)):
        b.append(f'<rect x="{x}" y="250" width="110" height="330" rx="10" fill="#fbfaf7" stroke="#c9d2d5"/>')
        b.append(t(x + 55, 235, label, 17, "#46565b", "700"))
        for i in range(9):
            y = 275 + i * 34
            if i == mark:
                b.append(f'<rect x="{x + 8}" y="{y - 14}" width="94" height="26" rx="6" fill="#eaf1f6" stroke="{BLUE}" stroke-width="2"/>')
            b.append(f'<line x1="{x + 14}" y1="{y - 5}" x2="{x + 14}" y2="{y + 5}" stroke="{GRAY}" stroke-width="2"/>')
            b.append(f'<line x1="{x + 22}" y1="{y}" x2="{x + 96}" y2="{y}" stroke="#c2cbcf"/>')
    b.append(f'<path d="M285,377 L509,479" stroke="{GRAY}" stroke-width="1.5" stroke-dasharray="6 5"/>')
    labels = [("물길 이름", True), ("무너진 자리", True), ("고친 사람", True), ("걸린 햇수", False)]
    for i, (name, solid) in enumerate(labels):
        y = 640 + i * 46
        fill = "#dfe8ee" if solid else "#f2f3f3"
        stroke = BLUE if solid else "#c9d2d5"
        b.append(f'<rect x="270" y="{y}" width="254" height="34" rx="17" fill="{fill}" stroke="{stroke}"/>')
        b.append(t(W / 2, y + 23, name, 16, "#33454b" if solid else GRAY, "700" if solid else "400"))
    b.append(t(W / 2, 840, "셋이 겹치면 같은 일로 본다", 17, ORANGE, "700"))
    b.append(note(880, "닮은 것을 묶는 일과 다른 것을 가르는 일은 같은 무게로 위험하다"))
    return base("두 줄이 한 사건일 때", "겹치는 세부를 세어 판단한다", "\n".join(b))


def fig_two_rulers():
    """3.2 — 같은 백 년을 두 가지 자로 나눈 그림."""
    b = []
    b.append(t(105, 268, "성주로 나눈 백 년", 17, "#46565b", "700", "start"))
    b.append(f'<rect x="105" y="285" width="584" height="90" rx="10" fill="#eef2f4" stroke="#c9d2d5"/>')
    cuts = [105, 214, 300, 424, 566, 689]
    names = ["첫째", "둘째", "셋째", "넷째", "다섯째"]
    for i in range(5):
        if i:
            b.append(f'<line x1="{cuts[i]}" y1="285" x2="{cuts[i]}" y2="375" stroke="{BLUE}" stroke-width="2"/>')
        b.append(t((cuts[i] + cuts[i + 1]) / 2, 337, names[i], 16, "#33454b", "700"))
    b.append(t(105, 470, "물 사정으로 나눈 백 년", 17, "#46565b", "700", "start"))
    b.append(f'<rect x="105" y="487" width="584" height="90" rx="10" fill="#f4f1ea" stroke="#cfc7b4"/>')
    wcuts = [105, 342, 540, 689]
    wnames = ["넉넉하던 때", "줄어들던 때", "끊긴 뒤"]
    for i in range(3):
        if i:
            b.append(f'<line x1="{wcuts[i]}" y1="487" x2="{wcuts[i]}" y2="577" stroke="{ORANGE}" stroke-width="2"/>')
        b.append(t((wcuts[i] + wcuts[i + 1]) / 2, 539, wnames[i], 16, "#4a3f2c", "700"))
    b.append(f'<path d="M342,600 L342,616 L540,616 L540,600" fill="none" stroke="{GRAY}" stroke-width="1.5"/>')
    b.append(t(441, 645, "위 띠에서는 두 대에 걸친 자리", 15, GRAY))
    b.append(note(760, "자는 대상이 아니라 물음에 붙는다"))
    return base("같은 백 년, 두 가지 자", "자를 바꾸면 어디가 한 시기인지가 달라진다", "\n".join(b))


def fig_four_relations():
    """4.2 — 붙어 있는 두 줄이 가질 수 있는 네 관계."""
    b = ['<rect x="277" y="250" width="240" height="96" rx="10" fill="#fbfaf7" stroke="#c9d2d5"/>']
    b.append(f'<rect x="289" y="262" width="216" height="32" rx="6" fill="#eaf1f6" stroke="{BLUE}"/>')
    b.append(t(W / 2, 284, "앞의 일", 16, "#33454b", "700"))
    b.append(f'<rect x="289" y="302" width="216" height="32" rx="6" fill="#eaf1f6" stroke="{BLUE}"/>')
    b.append(t(W / 2, 324, "뒤의 일", 16, "#33454b", "700"))
    b.append(f'<path d="M397,346 L397,392" stroke="{GRAY}" stroke-width="2"/>')
    xs = [178, 324, 470, 616]
    for x in xs:
        b.append(f'<path d="M397,392 L{x},420" stroke="{GRAY}" stroke-width="1.5" marker-end="url(#gray)"/>')
    texts = [["앞이 뒤의", "까닭이다"], ["둘 다 다른", "하나에서 나왔다"],
             ["순서가", "사실은 반대다"], ["아무 관계도", "없다"]]
    for i, x in enumerate(xs):
        stroke = BLUE if i == 0 else "#c9d2d5"
        b.append(f'<rect x="{x - 68}" y="440" width="136" height="104" rx="12" fill="#ffffff" stroke="{stroke}" stroke-width="{2 if i == 0 else 1}"/>')
        for j, line in enumerate(texts[i]):
            b.append(t(x, 484 + j * 26, line, 15, "#33454b" if i == 0 else "#5b686d", "700" if i == 0 else "400"))
    b.append(f'<line x1="105" y1="584" x2="689" y2="584" stroke="#d9dfe1"/>')
    b.append(t(W / 2, 616, "연대기만으로는 넷을 가를 수 없다", 17, ORANGE, "700"))
    b.append(note(760, "먼저라는 것은 까닭의 필요조건일 뿐이다"))
    return base("붙어 있다는 것만으로는", "같은 배치가 네 가지 관계를 담는다", "\n".join(b))


def fig_first_twenty():
    """5.1 — 첫 스무 해의 칸 길이와 빈 해."""
    raw = [46, 62, 38, 0, 88, 54, 30, 96, 42, 0, 58, 34, 92, 48, 26, 0, 84, 40, 90, 50]
    heights = [round(h * 2.4) for h in raw]
    base_y, x0, w, gap = 500, 118, 22, 8
    tall = 192
    b = [f'<line x1="105" y1="{base_y}" x2="689" y2="{base_y}" stroke="#b9c2c6"/>']
    b.append(f'<line x1="105" y1="{base_y - tall}" x2="689" y2="{base_y - tall}" stroke="#d3d9db" stroke-dasharray="5 5"/>')
    b.append(t(105, base_y - tall - 12, "보통 해의 높이", 14, GRAY, "400", "start"))
    for i, h in enumerate(heights):
        x = x0 + i * (w + gap)
        if h == 0:
            b.append(f'<rect x="{x}" y="{base_y - 12}" width="{w}" height="12" fill="#dfe3e5"/>')
            b.append(t(x + w / 2, base_y + 28, "빈 해", 13, GRAY))
            continue
        fill = BLUE if h > tall else "#a9bcc7"
        b.append(f'<rect x="{x}" y="{base_y - h}" width="{w}" height="{h}" rx="3" fill="{fill}"/>')
    b.append(t(105, base_y + 84, "적힌 해와 일어난 해가 어긋난 줄", 15, "#46565b", "700", "start"))
    b.append(f'<rect x="105" y="{base_y + 100}" width="584" height="34" rx="8" fill="#faf7f1" stroke="#d8cfbc"/>')
    for i in (1, 4, 5, 9, 12, 16, 18):
        x = x0 + i * (w + gap)
        b.append(f'<rect x="{x}" y="{base_y + 108}" width="{w}" height="18" rx="4" fill="{ORANGE}" opacity="0.8"/>')
    b.append(note(760, "모양을 먼저 보아야 이야기에 끌려가지 않는다"))
    return base("첫 스무 해의 모양", "문장을 읽기 전에 칸의 모양부터 본다", "\n".join(b))


def fig_who_wrote():
    """6.2 — 누가 적었는가에 따라 달라지는 백 년."""
    b = [f'<line x1="{W/2}" y1="250" x2="{W/2}" y2="700" stroke="#d9dfe1" stroke-dasharray="6 6"/>']
    b.append(t(250, 268, "성 안에서 적은 백 년", 17, "#46565b", "700"))
    b.append(f'<rect x="120" y="290" width="260" height="300" rx="12" fill="#eef2f4" stroke="#c9d2d5"/>')
    for i in range(8):
        y = 320 + i * 34
        b.append(f'<rect x="140" y="{y}" width="220" height="20" rx="5" fill="{BLUE}" opacity="0.8"/>')
    for i, name in enumerate(("성주 교체", "수로 공사", "사절 왕래")):
        b.append(t(250, 630 + i * 26, name, 15, "#46565b"))
    b.append(t(544, 268, "성 밖에서 적었다면", 17, "#46565b", "700"))
    b.append(f'<rect x="414" y="290" width="260" height="300" rx="12" fill="#ffffff" stroke="#c9d2d5"/>')
    for i in (1, 4, 6):
        y = 320 + i * 34
        b.append(f'<rect x="434" y="{y}" width="220" height="20" rx="5" fill="none" stroke="{GRAY}" stroke-dasharray="4 4"/>')
    for i, name in enumerate(("물 배분 다툼", "천막의 이동", "관문의 통행료")):
        b.append(t(544, 630 + i * 26, name, 15, GRAY))
    b.append(t(544, 726, "이런 기록은 남지 않았다", 16, GRAY, "700"))
    b.append(note(800, "남은 것으로 그린 그림은 남긴 쪽의 그림이다"))
    return base("누가 적었는가에 따라", "남은 기록은 남긴 쪽의 자리에서 만들어진다", "\n".join(b))


FIGURES = {
    5: fig_year_to_line,
    13: fig_same_event_twice,
    19: fig_two_rulers,
    27: fig_four_relations,
    33: fig_first_twenty,
    41: fig_who_wrote,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild031")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
