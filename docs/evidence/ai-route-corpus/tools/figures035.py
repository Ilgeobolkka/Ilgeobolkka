#!/usr/bin/env python3
"""book-035 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 눈금과 거리와 숫자를 넣지 않는다 — 남은 다리가
치우쳐 있어 값으로 견줄 수 없고 차례와 자리만 견줄 수 있기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
BLUE, ORANGE, GRAY = "#3d5f7a", "#9c6b3c", "#8a9296"
STONE = "#cfc7b8"


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


def bridge(x, y, span, arches, h=34, fill=STONE, stroke="#a89e8c"):
    """옆에서 본 다리. x부터 span 폭에 arches개의 아치."""
    w = span / arches
    out = [f'<rect x="{x}" y="{y}" width="{span}" height="10" fill="{fill}" stroke="{stroke}"/>']
    for i in range(arches):
        cx = x + w * (i + 0.5)
        out.append(f'<path d="M{cx - w*0.38},{y + h} A {w*0.38},{h*0.9} 0 0 1 {cx + w*0.38},{y + h} Z" '
                   f'fill="#ffffff" stroke="{stroke}"/>')
        if i:
            out.append(f'<rect x="{x + w*i - 3}" y="{y + 10}" width="6" height="{h - 10}" fill="{fill}" stroke="{stroke}"/>')
    out.append(f'<rect x="{x}" y="{y + h}" width="{span}" height="6" fill="{fill}" stroke="{stroke}"/>')
    return "\n".join(out)


def fig_what_stone_tells():
    """1.3 — 돌에서 읽히는 것과 읽히지 않는 것."""
    b = [f'<rect x="120" y="330" width="240" height="60" fill="#e6eef3" opacity="0.7"/>',
         bridge(120, 300, 240, 3, 40)]
    reads = ["놓인 자리", "아치의 폭", "돌을 다듬은 솜씨", "고친 자국", "길과 만나는 각도"]
    for i, name in enumerate(reads):
        y = 270 + i * 50
        b.append(f'<path d="M362,{330 + (i - 2) * 12} L440,{y + 16}" stroke="#ccd4d7" stroke-width="1"/>')
        b.append(f'<rect x="440" y="{y}" width="234" height="34" rx="8" fill="{BLUE}" opacity="0.85"/>')
        b.append(t(557, y + 23, name, 15, "#ffffff", "700"))
    lost = ["누가 건넜는가", "얼마나 건넜는가", "건너며 무엇을 냈는가", "건너기 싫었는가"]
    for i, name in enumerate(lost):
        y = 560 + i * 50
        b.append(f'<rect x="440" y="{y}" width="234" height="34" rx="8" fill="none" '
                 f'stroke="#c3ccd0" stroke-dasharray="5 4"/>')
        b.append(t(557, y + 23, name, 15, "#a3adb1"))
    b.append(f'<line x1="410" y1="255" x2="410" y2="760" stroke="#d9dfe1"/>')
    b.append(t(255, 500, "돌이 말해 주는 것", 15, BLUE, "700"))
    b.append(t(255, 620, "다른 자료가", 15, GRAY, "700"))
    b.append(t(255, 642, "있어야 하는 것", 15, GRAY, "700"))
    b.append(note(800, "읽히는 것의 목록이 곧 물을 수 있는 물음의 목록이다"))
    return base("돌에서 읽히는 것", "구조물은 만드는 동안 든 것을 감추지 못한다", "\n".join(b))


def fig_five_sizes():
    """2.3 — 다섯의 크기와 든 힘."""
    names = ["물푸레교", "자작교", "소나무교", "버들교", "은행교"]
    arches = [4, 3, 7, 2, 5]
    force = [190, 150, 300, 90, 220]
    used = [280, 250, 110, 150, 190]
    b = []
    for i in range(5):
        y = 250 + i * 118
        b.append(t(178, y + 26, names[i], 15, "#33454b", "700", "end"))
        b.append(bridge(190, y, 150, arches[i], 30))
        b.append(f'<rect x="360" y="{y + 4}" width="{force[i]}" height="16" rx="8" fill="{BLUE}" opacity="0.85"/>')
        b.append(f'<rect x="360" y="{y + 26}" width="{used[i]}" height="16" rx="8" fill="{STONE}" stroke="#a89e8c"/>')
        if abs(force[i] - used[i]) > 120:
            b.append(t(680, y + 40, "차이가 큼", 13, GRAY, "700", "end"))
    b.append(t(360, 240, "들인 힘", 14, BLUE, "700", "start"))
    b.append(t(430, 240, "건너는 사람", 14, "#8a7f6c", "700", "start"))
    b.append(note(880, "많이 건너는 다리가 큰 다리는 아니다"))
    return base("다섯의 크기", "크기는 필요가 아니라 들일 수 있었던 힘을 말한다", "\n".join(b))


def fig_skipped():
    """3.3 — 놓이지 않은 자리."""
    b = [f'<path d="M150,760 C240,640 200,520 300,430 C400,340 340,280 470,240 L520,250 '
         f'C400,300 450,360 350,450 C250,540 290,660 200,770 Z" fill="#e6eef3" stroke="#bcd0da"/>']
    b.append(f'<circle cx="168" cy="742" r="10" fill="#27353a"/>')
    b.append(t(150, 726, "도읍", 15, "#27353a", "700", "end"))
    built = [(196, 706, "첫째"), (262, 592, "둘째"), (322, 470, "셋째"), (404, 350, "넷째"), (492, 268, "다섯째")]
    for x, y, label in built:
        b.append(f'<rect x="{x - 26}" y="{y - 7}" width="52" height="14" rx="3" fill="{STONE}" stroke="#8d8271"/>')
        b.append(t(x + 42, y + 5, label, 14, "#46565b", "700", "start"))
    skipped = [(228, 652, "뒤에 놓임"), (356, 404, "뒤에 놓임"), (444, 306, "끝내 놓이지 않음")]
    for x, y, label in skipped:
        b.append(f'<rect x="{x - 20}" y="{y - 6}" width="40" height="12" rx="3" fill="none" '
                 f'stroke="#b0b8bb" stroke-dasharray="4 3"/>')
        b.append(t(x - 32, y + 4, label, 13, GRAY, "400", "end"))
    b.append(t(600, 420, "놓기 좋은 자리", 15, GRAY, "700"))
    b.append(f'<rect x="556" y="440" width="40" height="12" rx="3" fill="none" stroke="#b0b8bb" stroke-dasharray="4 3"/>')
    b.append(f'<rect x="616" y="440" width="52" height="14" rx="3" fill="{STONE}" stroke="#8d8271"/>')
    b.append(t(576, 476, "비운 자리", 13, GRAY))
    b.append(t(642, 476, "놓은 자리", 13, "#46565b"))
    b.append(note(830, "놓기 좋은 자리를 비워 둔 데에 뜻이 있다"))
    return base("놓이지 않은 자리", "놓지 않기로 한 것도 결정이다", "\n".join(b))


def fig_time_shrinks():
    """4.2 — 걸리는 시간이 줄면."""
    b = [f'<circle cx="150" cy="470" r="11" fill="#27353a"/>', t(150, 446, "도읍", 15, "#27353a", "700")]
    b.append(t(105, 300, "다리가 놓이기 전", 16, "#46565b", "700", "start"))
    b.append(f'<rect x="150" y="320" width="416" height="46" fill="#eef2f4" stroke="#c9d2d5"/>')
    for i, lab in enumerate(("하루", "이틀", "사흘", "나흘")):
        x = 150 + i * 104
        if i:
            b.append(f'<line x1="{x}" y1="320" x2="{x}" y2="366" stroke="#c9d2d5"/>')
        b.append(t(x + 52, 349, lab, 14, "#5b686d"))
    b.append(f'<line x1="566" y1="306" x2="566" y2="380" stroke="{GRAY}" stroke-width="2"/>')
    b.append(t(566, 298, "여기까지", 13, GRAY, "700"))
    b.append(t(105, 560, "다리가 놓인 뒤", 16, "#46565b", "700", "start"))
    b.append(f'<rect x="150" y="580" width="416" height="46" fill="#e9f0f4" stroke="{BLUE}"/>')
    for i in range(8):
        x = 150 + i * 52
        if i:
            b.append(f'<line x1="{x}" y1="580" x2="{x}" y2="626" stroke="#cfdbe2"/>')
    b.append(f'<rect x="566" y="580" width="108" height="46" fill="#dbe7ee" stroke="{BLUE}" stroke-dasharray="4 3"/>')
    b.append(t(620, 609, "늘어난 범위", 14, BLUE, "700"))
    b.append(f'<line x1="674" y1="566" x2="674" y2="640" stroke="{BLUE}" stroke-width="2"/>')
    b.append(t(674, 558, "여기까지", 13, BLUE, "700"))
    for i, name in enumerate(("보내는 사람", "오는 소식", "걷는 세")):
        x = 210 + i * 180
        b.append(f'<rect x="{x - 70}" y="700" width="140" height="42" rx="10" fill="#ffffff" stroke="#c9d2d5"/>')
        b.append(t(x, 727, name, 15, "#46565b", "700"))
        b.append(f'<path d="M{x},698 L{x},648" stroke="{GRAY}" stroke-width="1.5" marker-end="url(#gray)"/>')
    b.append(note(800, "시간이 줄면 힘이 늘어난다"))
    return base("걸리는 시간이 줄면", "같은 힘으로 더 넓은 곳을 쥔다", "\n".join(b))


def fig_layers():
    """5.2 — 아래에서 위로 어려지는 돌."""
    layers = [("처음 놓은 돌", 150, 5, "#b9ad98"), ("첫 번째 고침", 120, 6, "#c8bfae"),
              ("두 번째 고침", 96, 8, "#d6cfc2"), ("가장 나중의 바닥돌", 72, 10, "#e4dfd6")]
    b = []
    y = 700
    for name, h, cols, color in layers:
        y -= h
        b.append(f'<rect x="300" y="{y}" width="200" height="{h}" fill="{color}" stroke="#a89e8c"/>')
        for c in range(1, cols):
            x = 300 + 200 * c / cols
            b.append(f'<line x1="{x}" y1="{y}" x2="{x}" y2="{y + h}" stroke="#a89e8c" stroke-width="0.8"/>')
        dark = name in ("처음 놓은 돌", "첫 번째 고침")
        b.append(t(516, y + h / 2 + 5, name, 15, "#46565b" if dark else "#9aa3a7", "700" if dark else "400", "start"))
    b.append(f'<path d="M270,300 L270,690" stroke="{GRAY}" stroke-width="1.5" marker-end="url(#gray)"/>')
    b.append(t(258, 500, "아래일수록", 14, GRAY, "400", "end"))
    b.append(t(258, 520, "오래된 것", 14, GRAY, "400", "end"))
    b.append(f'<rect x="180" y="606" width="320" height="14" fill="{BLUE}" opacity="0.25"/>')
    b.append(t(180, 596, "이 아래는 물이 낮은 날에만 잰다", 14, BLUE, "400", "start"))
    b.append(note(790, "어느 층을 쟀는지 적지 않은 값은 다시 쓸 수 없다"))
    return base("아래에서 위로 어려지는 돌", "한 다리는 한 시점의 자료가 아니다", "\n".join(b))


def fig_reach_of_stone():
    """6.4 — 돌이 닿는 데까지."""
    cx, cy = 397, 760
    b = []
    for r, op, dashed in ((340, 0.07, True), (240, 0.13, False), (140, 0.22, False)):
        dash = ' stroke-dasharray="6 5"' if dashed else ''
        b.append(f'<path d="M{cx - r},{cy} A {r},{r} 0 0 1 {cx + r},{cy} Z" fill="{BLUE}" '
                 f'opacity="{op}" stroke="#cfd8dd"{dash}/>')
    b.append(bridge(cx - 40, cy - 24, 80, 2, 20))
    b.append(t(cx, 440, "돌로는 닿지 않는 것", 15, "#a3adb1", "700"))
    for x, name in ((197, "건넌 사람들의 사정"), (397, "피해 다닌 통행"), (597, "다리를 어떻게 여겼는가")):
        b.append(t(x, 472, name, 14, "#a3adb1"))
    b.append(t(cx, 540, "돌과 글을 함께 놓아야 답한 것", 15, "#5b686d", "700"))
    for x, name in ((237, "놓은 뜻"), (397, "부담을 진 쪽"), (557, "문이 하던 일")):
        b.append(t(x, 572, name, 14, "#5b686d"))
    b.append(t(cx, 640, "돌만으로 답한 것", 15, "#27353a", "700"))
    for x, y, name in ((327, 670, "놓인 차례"), (467, 670, "크기와 솜씨"),
                       (327, 700, "고친 횟수"), (467, 700, "쓰인 정도")):
        b.append(t(x, y, name, 14, "#27353a", "700"))
    b.append(note(880, "닿지 않는 데를 그려 두는 것도 그림의 한 부분이다"))
    return base("돌이 닿는 데까지", "자료의 종류가 물음의 종류를 정한다", "\n".join(b))


FIGURES = {
    6: fig_what_stone_tells,
    12: fig_five_sizes,
    20: fig_skipped,
    27: fig_time_shrinks,
    34: fig_layers,
    45: fig_reach_of_stone,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild035")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
