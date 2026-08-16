#!/usr/bin/env python3
"""book-033 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 눈금과 거리와 숫자를 넣지 않는다 — 남은 종이
치우쳐 있어 값으로 견줄 수 없고 자리와 순서만 견줄 수 있기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
BLUE, ORANGE, GRAY = "#3d5f7a", "#9c6b3c", "#8a9296"
RED = "#b4453c"


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


def bell(cx, cy, w=44, h=56, fill="#dde5ea", stroke="#93a7b2"):
    return (f'<path d="M{cx - w/2},{cy + h/2} L{cx - w/2 + 4},{cy - h/2 + 12} '
            f'Q{cx},{cy - h/2 - 8} {cx + w/2 - 4},{cy - h/2 + 12} L{cx + w/2},{cy + h/2} Z" '
            f'fill="{fill}" stroke="{stroke}"/>')


def fig_two_groupings():
    """1.3 — 명문이 앞세운 사람과 물건에 남은 손자국."""
    xs = [160 + i * 95 for i in range(6)]
    names = ["첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째"]
    b = []
    for x, n in zip(xs, names):
        b.append(bell(x, 280))
        b.append(t(x, 330, n, 14, "#46565b"))
    xm = (xs[2] + xs[3]) / 2
    b.append(f'<line x1="{xm}" y1="350" x2="{xm}" y2="600" stroke="{RED}" stroke-width="2"/>')
    b.append(t(105, 390, "명문이 앞세운 사람", 16, BLUE, "700", "start"))
    for (lo, hi, label) in ((0, 1, "왕"), (2, 3, "왕과 신하"), (4, 5, "신하")):
        x0, x1 = xs[lo] - 38, xs[hi] + 38
        b.append(f'<rect x="{x0}" y="405" width="{x1 - x0}" height="52" rx="12" fill="#ffffff" stroke="{BLUE}"/>')
        b.append(t((x0 + x1) / 2, 437, label, 15, BLUE, "700"))
    b.append(t(105, 510, "물건에 남은 손자국", 16, ORANGE, "700", "start"))
    for (lo, hi, label) in ((0, 2, "앞 공방"), (3, 5, "뒤 공방")):
        x0, x1 = xs[lo] - 38, xs[hi] + 38
        b.append(f'<rect x="{x0}" y="525" width="{x1 - x0}" height="52" rx="12" fill="#ffffff" stroke="{ORANGE}"/>')
        b.append(t((x0 + x1) / 2, 557, label, 15, ORANGE, "700"))
    b.append(t(xm, 626, "같은 이름, 다른 손", 15, RED, "700"))
    b.append(note(760, "두 묶음이 어긋나는 자리가 가장 많이 말해 준다"))
    return base("두 가지 묶음", "적힌 이름과 남은 손자국은 다른 것을 말한다", "\n".join(b))


def fig_read_and_guessed():
    """2.3 — 판독한 글자와 짐작한 글자."""
    import math
    b = ['<rect x="120" y="250" width="554" height="230" rx="10" fill="#faf9f6" stroke="#cfc7b4"/>']
    kinds = []
    for r in range(5):
        for c in range(18):
            x, y = 140 + c * 30, 270 + r * 42
            far = c >= 8 or (r >= 3 and c >= 3)
            k = 2 if far and (c + r) % 5 != 0 else (1 if (c * 3 + r) % 5 == 0 else 0)
            kinds.append(k)
            if k == 0:
                b.append(f'<rect x="{x}" y="{y}" width="22" height="28" rx="3" fill="{BLUE}" opacity="0.85"/>')
            elif k == 1:
                b.append(f'<rect x="{x}" y="{y}" width="22" height="28" rx="3" fill="{BLUE}" opacity="0.28"/>')
            else:
                b.append(f'<rect x="{x}" y="{y}" width="22" height="28" rx="3" fill="none" stroke="#c3ccd0"/>')
    b.append(t(660, 500, "손이 닿는 자리", 14, GRAY, "400", "end"))
    b.append(f'<path d="M600,492 L560,470" stroke="{GRAY}" stroke-width="1.2"/>')
    legend = [("읽은 글자", 0), ("짐작한 글자", 1), ("아무것도 짐작할 수 없는 자리", 2)]
    for i, (label, k) in enumerate(legend):
        y = 560 + i * 52
        if k == 0:
            b.append(f'<rect x="140" y="{y}" width="22" height="28" rx="3" fill="{BLUE}" opacity="0.85"/>')
        elif k == 1:
            b.append(f'<rect x="140" y="{y}" width="22" height="28" rx="3" fill="{BLUE}" opacity="0.28"/>')
        else:
            b.append(f'<rect x="140" y="{y}" width="22" height="28" rx="3" fill="none" stroke="#c3ccd0"/>')
        b.append(t(176, y + 20, label, 15, "#46565b", "400", "start"))
        n = sum(1 for v in kinds if v == k)
        b.append(f'<rect x="470" y="{y + 6}" width="{n * 3.4}" height="16" rx="8" fill="{BLUE}" opacity="{0.85 - k * 0.28}"/>')
    b.append(note(760, "짐작을 표시하지 않은 판독문은 다시 쓸 수 없다"))
    return base("읽은 자리와 짐작한 자리", "채운 글자는 두 세대 뒤에 원문이 된다", "\n".join(b))


def fig_sound_reach():
    """3.4 — 소리가 닿는 범위와 마을."""
    cx, cy = 397, 470
    b = []
    for r, label in ((230, "들리지 않는 데"), (160, "겨우 들리는 데"), (92, "또렷이 들리는 데")):
        op = 0.10 if r == 230 else (0.18 if r == 160 else 0.30)
        b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{BLUE}" opacity="{op}" stroke="#cfd8dd"/>')
    b.append(t(cx, cy - 236, "들리지 않는 데", 14, GRAY))
    b.append(t(cx, cy - 166, "겨우 들리는 데", 14, "#5b7a8a"))
    b.append(t(cx, cy - 98, "또렷이 들리는 데", 14, BLUE, "700"))
    b.append(f'<rect x="{cx - 14}" y="{cy - 34}" width="28" height="46" fill="#e7ecef" stroke="#93a7b2"/>')
    b.append(bell(cx, cy - 44, 26, 32, "#c9d5dc", "#7f95a2"))
    towns = [(0, 60), (55, -30), (-62, 20), (120, 70), (-130, -60), (140, -40),
             (200, 30), (-210, 60), (215, -80), (-190, -110), (250, -20), (-255, 40),
             (60, 210), (-90, 200), (180, 170), (-170, 180)]
    for dx, dy in towns:
        d = (dx * dx + dy * dy) ** 0.5
        color = BLUE if d <= 92 else ("#8fb0c1" if d <= 160 else "#b8bfc2")
        b.append(f'<circle cx="{cx + dx}" cy="{cy + dy}" r="6" fill="{color}"/>')
    b.append(f'<path d="M{cx + 236},{cy - 4} L{cx + 300},{cy - 4}" stroke="{GRAY}" stroke-width="4" marker-end="url(#gray)"/>')
    b.append(t(689, cy + 30, "성문 밖으로는", 14, GRAY, "400", "end"))
    b.append(t(689, cy + 50, "사람을 보내 알린다", 14, GRAY, "400", "end"))
    b.append(note(790, "들리는 범위가 곧 의례의 범위는 아니다"))
    return base("소리가 닿는 데까지", "누가 들을 수 있었는가가 누가 참여했는가를 정한다", "\n".join(b))


def fig_repetition():
    """4.4 — 되풀이가 만드는 것."""
    b = []
    marked = [2, 1, 1, 0]
    for k in range(4):
        cx = 175 + k * 148
        cy = 380
        b.append(t(cx, 258, ["첫 시기", "둘째 시기", "셋째 시기", "넷째 시기"][k], 15, "#46565b", "700"))
        for r, op in ((70, 0.10), (48, 0.18), (24, 0.32)):
            b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{BLUE}" opacity="{op}" stroke="#dde3e6"/>')
        b.append(f'<rect x="{cx - 7}" y="{cy - 11}" width="14" height="22" fill="#e7ecef" stroke="#93a7b2"/>')
        rings = [(24, 4, BLUE), (48, 6, "#8fb0c1"), (70, 7, "#c2c9cc")]
        import math
        for ri, (rad, cnt, col) in enumerate(rings):
            for i in range(cnt):
                a = (i / cnt) * 2 * math.pi + ri * 0.4
                px, py = cx + rad * math.cos(a), cy + rad * math.sin(a)
                if ri == marked[k] and i == 0:
                    b.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="6" fill="{ORANGE}"/>')
                else:
                    b.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="4" fill="{col}"/>')
    b.append(f'<path d="M120,500 L674,500" stroke="{GRAY}" stroke-width="1.5" marker-end="url(#gray)"/>')
    b.append(t(397, 528, "여러 해에 걸쳐 한 자리씩", 15, GRAY))
    b.append(t(619, 596, "이 자리는 처음부터", 14, GRAY))
    b.append(t(619, 616, "그랬던 것처럼 보인다", 14, GRAY))
    b.append(note(760, "되풀이는 정해진 것을 보이는 일이면서 정해 가는 일이다"))
    return base("되풀이가 만드는 것", "해마다 같은 자리에 서는 일이 그 자리를 당연하게 만든다",
                "\n".join(b))


def fig_words_vs_goods():
    """5.2 — 명문이 말한 크기와 실제로 든 물자."""
    pairs = [(300, 290), (250, 262), (340, 130), (270, 258), (200, 320), (330, 150)]
    b = [t(105, 258, "명문이 말한 크기", 15, BLUE, "700", "start"),
         t(105, 278, "의례에 든 물자", 15, ORANGE, "700", "start")]
    for i, (a, c) in enumerate(pairs):
        y = 310 + i * 92
        b.append(f'<rect x="230" y="{y}" width="{a}" height="24" rx="6" fill="{BLUE}" opacity="0.85"/>')
        b.append(f'<rect x="230" y="{y + 32}" width="{c}" height="24" rx="6" fill="{ORANGE}" opacity="0.8"/>')
        if a - c > 120:
            b.append(f'<path d="M{230 + c},{y + 62} L{230 + a},{y + 62}" stroke="{RED}" stroke-width="2"/>')
            b.append(f'<path d="M{230 + c},{y + 57} L{230 + c},{y + 67}" stroke="{RED}" stroke-width="2"/>')
            b.append(f'<path d="M{230 + a},{y + 57} L{230 + a},{y + 67}" stroke="{RED}" stroke-width="2"/>')
            b.append(t(220, y + 34, "말만 큰 해", 14, RED, "700", "end"))
    b.append(note(900, "어긋나는 쌍이 이 대조에서 얻는 것이다"))
    return base("말과 물자를 맞대면", "꾸밀 이유가 없는 자리의 숫자가 꾸민 자리의 말을 확인해 준다",
                "\n".join(b))


def fig_left_and_lost():
    """6.3 — 남은 것과 남지 않은 것."""
    b = [f'<line x1="397" y1="250" x2="397" y2="700" stroke="#d9dfe1"/>']
    b.append(t(240, 268, "남은 것", 18, BLUE, "700"))
    for i, name in enumerate(("종", "새긴 글", "터에 남은 자리 표시")):
        y = 300 + i * 64
        b.append(f'<rect x="140" y="{y}" width="200" height="46" rx="10" fill="{BLUE}" opacity="0.82"/>')
        b.append(t(240, y + 29, name, 15, "#ffffff", "700"))
    b.append(t(554, 268, "남지 않은 것", 18, GRAY, "700"))
    lost = ("소리", "순서와 몸짓", "그날의 말", "모인 사람의 표정", "끝난 뒤의 자리")
    for i, name in enumerate(lost):
        y = 300 + i * 64
        b.append(f'<rect x="454" y="{y}" width="200" height="46" rx="10" fill="none" '
                 f'stroke="#c3ccd0" stroke-dasharray="5 4"/>')
        b.append(t(554, y + 29, name, 15, "#a3adb1"))
    b.append(f'<line x1="105" y1="660" x2="689" y2="660" stroke="#d9dfe1"/>')
    b.append(t(397, 694, "왼쪽으로 오른쪽을 짐작한다", 16, "#46565b", "700"))
    b.append(note(790, "남은 것이 적다는 사실도 함께 적어야 한다"))
    return base("남은 것과 남지 않은 것", "이 의례에서 사람들이 실제로 겪은 것은 대부분 남지 않았다",
                "\n".join(b))


FIGURES = {
    7: fig_two_groupings,
    13: fig_read_and_guessed,
    22: fig_sound_reach,
    30: fig_repetition,
    35: fig_words_vs_goods,
    43: fig_left_and_lost,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild033")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
