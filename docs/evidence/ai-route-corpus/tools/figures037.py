#!/usr/bin/env python3
"""book-037 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 액수와 날수를 넣지 않는다 — 남은 장부가
살아남은 상단의 것이라 값으로 견줄 수 없고 짜임만 견줄 수 있기 때문이다.
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
BLUE, ORANGE, GRAY = "#3d5f7a", "#9c6b3c", "#8a9296"
RED = "#b4453c"
SAND = "#d8c9a8"


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


def strip(y, text, size=16):
    return ('<rect x="105" y="%d" width="584" height="70" rx="14" fill="#f6f1e6" stroke="#cfc0a0"/>' % y
            + t(W / 2, y + 43, text, size, "#57492f", "700"))


def caravan(cx, cy, n=4, scale=1.0):
    out = []
    for i in range(n):
        x = cx + (i - (n - 1) / 2) * 30 * scale
        out.append(f'<rect x="{x - 9*scale}" y="{cy - 11*scale}" width="{18*scale}" height="{22*scale}" '
                   f'rx="3" fill="{SAND}" stroke="#a89272"/>')
    return "\n".join(out)


def fig_three_losses():
    """1.2 — 잃는 방식 셋."""
    b = [f'<rect x="130" y="262" width="530" height="42" rx="6" fill="#f2eee4" stroke="#d5cbb4"/>']
    b.append(t(130, 252, "소금성", 14, "#46565b", "700", "start"))
    b.append(t(660, 252, "향료항", 14, "#46565b", "700", "end"))
    b.append(caravan(300, 283, 5, 0.9))
    kinds = [(210, "빼앗김", "사람을 더 데려간다", False),
             (395, "상함", "싣는 방식을 바꾼다", False),
             (580, "늦어짐", "줄이기 어렵다", True)]
    for x, name, how, hard in kinds:
        b.append(f'<path d="M{x},310 L{x},366" stroke="{GRAY}" stroke-width="1.5" marker-end="url(#gray)"/>')
        b.append(f'<rect x="{x - 82}" y="{372}" width="164" height="48" rx="10" fill="#ffffff" stroke="{BLUE}"/>')
        b.append(t(x, 402, name, 16, "#33454b", "700"))
        b.append(t(x, 452, how, 14, GRAY if hard else "#5b686d"))
    b.append(t(105, 520, "시기별 무게", 15, "#46565b", "700", "start"))
    for i, label in enumerate(("앞 시기", "뒤 시기")):
        y = 545 + i * 70
        b.append(t(150, y + 26, label, 14, "#46565b", "700", "end"))
        vals = [(210, 0.85), (395, 0.4), (580, 0.25)] if i == 0 else [(210, 0.25), (395, 0.4), (580, 0.85)]
        for x, op in vals:
            b.append(f'<rect x="{x - 60}" y="{y}" width="120" height="42" rx="8" fill="{ORANGE}" opacity="{op}"/>')
    b.append(strip(760, "무엇을 잃었는지보다 어떻게 잃었는지가 더 많은 것을 말한다"))
    return base("잃는 방식 셋", "갈래가 다르면 막는 법도 다르다", "\n".join(b))


def fig_share_or_wage():
    """2.2 — 몫을 받는 자리와 삯을 받는 자리."""
    b = [f'<line x1="397" y1="250" x2="397" y2="580" stroke="#d9dfe1"/>']
    b.append(t(240, 278, "몫을 받는 자리", 16, BLUE, "700"))
    b.append(t(554, 278, "삯을 받는 자리", 16, ORANGE, "700"))
    for i, name in enumerate(("돈을 댄 사람", "길잡이", "상단을 이끄는 사람")):
        y = 310 + i * 62
        b.append(f'<rect x="130" y="{y}" width="220" height="46" rx="10" fill="{BLUE}" opacity="0.85"/>')
        b.append(t(240, y + 29, name, 15, "#ffffff", "700"))
    for i, name in enumerate(("짐승을 다루는 사람", "짐을 지키는 사람", "물을 맡은 사람", "셈을 하는 사람")):
        y = 310 + i * 62
        b.append(f'<rect x="444" y="{y}" width="220" height="46" rx="10" fill="none" stroke="{ORANGE}"/>')
        b.append(t(554, y + 29, name, 15, "#6b5330"))
    b.append(f'<line x1="105" y1="600" x2="689" y2="600" stroke="#d9dfe1"/>')
    b.append(f'<path d="M397,610 L240,650" stroke="{GRAY}" stroke-width="1.5" marker-end="url(#gray)"/>')
    b.append(f'<path d="M397,610 L554,650" stroke="{GRAY}" stroke-width="1.5" marker-end="url(#gray)"/>')
    b.append(t(240, 682, "잃으면 함께 잃는다", 15, BLUE, "700"))
    b.append(t(554, 682, "잃어도 삯은 받는다", 15, ORANGE, "700"))
    b.append(strip(770, "받는 방식이 곧 지는 위험이다"))
    return base("몫을 받는 자리와 삯을 받는 자리", "무엇으로 받느냐가 누가 위험을 지는지를 정한다",
                "\n".join(b))


def fig_shared_loss():
    """3.2 — 혼자 질 때와 나누어 질 때."""
    b = []
    for k, (label, y0) in enumerate((("나누어 지지 않을 때", 300), ("나누어 질 때", 540))):
        b.append(t(105, y0 - 22, label, 16, "#46565b", "700", "start"))
        base_y = y0 + 150
        for i in range(6):
            x = 150 + i * 78
            if k == 0:
                h = 10 if i == 2 else 140
            else:
                h = 108
            b.append(f'<rect x="{x}" y="{base_y - h}" width="46" height="{h}" rx="4" '
                     f'fill="{BLUE}" opacity="{0.35 if k == 0 and i == 2 else 0.8}"/>')
        b.append(f'<line x1="140" y1="{base_y}" x2="640" y2="{base_y}" stroke="#b9c2c6"/>')
        low = base_y - (10 if k == 0 else 108)
        b.append(f'<line x1="650" y1="{low}" x2="686" y2="{low}" stroke="{RED if k == 0 else BLUE}" stroke-width="2"/>')
        b.append(t(686, low - 8, "가장 낮은 기둥", 12, RED if k == 0 else BLUE, "700", "end"))
        if k == 0:
            b.append(t(150 + 2 * 78 + 23, base_y + 24, "이 사람이 다 잃었다", 13, RED, "700"))
    b.append(strip(760, "잃는 양이 아니라 잃는 모양을 바꾼다"))
    return base("혼자 질 때와 나누어 질 때", "각자의 최악이 얕아지는 대신 아무도 온전하지 않다",
                "\n".join(b))


def fig_what_travels():
    """4.1 — 무엇을 실어 나르는가."""
    b = []
    for k, (label, y0, big) in enumerate((("값을 실어 갈 때", 270, True), ("종이로 대신할 때", 520, False))):
        b.append(t(105, y0 - 12, label, 16, "#46565b", "700", "start"))
        b.append(f'<rect x="130" y="{y0}" width="530" height="36" rx="6" fill="#f2eee4" stroke="#d5cbb4"/>')
        n = 7 if big else 3
        for i in range(n):
            x = 200 + i * 46
            dark = big and i in (2, 3, 4)
            b.append(f'<rect x="{x}" y="{y0 + 6}" width="24" height="24" rx="3" '
                     f'fill="{"#8a7a5c" if dark else SAND}" stroke="#a89272"/>')
        if big:
            b.append(t(300, y0 + 54, "값으로 쓸 것", 13, "#6b5330", "700"))
        else:
            b.append(f'<rect x="200" y="{y0 + 6}" width="18" height="24" rx="2" fill="#ffffff" stroke="{BLUE}"/>')
            b.append(t(240, y0 + 54, "접은 종이 한 장", 13, BLUE, "700", "start"))
        for i, name in enumerate(("짐승 먹이", "사람 삯", "빼앗길 몫")):
            y = y0 + 82 + i * 34
            w = (160, 130, 150)[i] if big else (70, 60, 40)[i]
            b.append(t(196, y + 16, name, 13, "#46565b", "400", "end"))
            b.append(f'<rect x="206" y="{y}" width="{w}" height="20" rx="10" fill="{ORANGE}" opacity="0.75"/>')
    b.append(strip(790, "나르지 않는 것이 가장 싸게 나르는 법이다"))
    return base("무엇을 실어 나르는가", "값을 치르러 가는 데도 값이 든다", "\n".join(b))


def fig_one_round():
    """5.1 — 한 벌로 남은 왕복."""
    b = [f'<path d="M170,420 C300,300 520,300 640,400 C700,450 660,520 560,540 '
         f'C420,566 260,540 180,490 Z" fill="none" stroke="#cfc0a0" stroke-width="3"/>']
    b.append(t(158, 398, "소금성", 15, "#46565b", "700", "end"))
    b.append(t(406, 272, "비단마을", 14, "#46565b", "700"))
    b.append(t(672, 400, "향료항", 15, "#46565b", "700", "start"))
    spots = [(178, 452, "떠나기 전 문서", False, "end", -14, 34),
             (280, 342, "통행세 적바림", False, "middle", 0, -22),
             (406, 316, "비단마을 셈", False, "middle", 0, 34),
             (630, 380, "향료항 거래장", False, "start", 16, -18),
             (520, 552, "돌아오는 길 적바림", False, "middle", 0, 34),
             (240, 522, "돌아온 뒤의 셈", True, "middle", 0, -22)]
    for x, y, name, dark, anchor, dx, dy in spots:
        b.append(f'<rect x="{x - 9}" y="{y - 9}" width="18" height="18" rx="3" '
                 f'fill="{BLUE if dark else "#ffffff"}" stroke="{BLUE}" stroke-width="{2 if dark else 1}"/>')
        b.append(t(x + dx, y + dy, name, 13, "#46565b", "700" if dark else "400", anchor))
    b.append(f'<path d="M232,534 L168,590" stroke="{BLUE}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(t(150, 616, "여기서 시작한다", 14, BLUE, "700", "start"))
    b.append(strip(760, "계획은 하려던 일이고 셈은 한 일이다"))
    return base("한 벌로 남은 왕복", "끝에서 시작해 앞으로 거슬러 읽는다", "\n".join(b))


def fig_in_and_out():
    """6.2 — 장부 안과 밖."""
    b = []
    for k, (label, y0, ratio) in enumerate((("앞 시기", 300, 0.42), ("뒤 시기", 520, 0.88))):
        b.append(t(105, y0 - 14, label, 16, "#46565b", "700", "start"))
        b.append(f'<rect x="130" y="{y0}" width="530" height="90" rx="8" fill="#f4f1ea" stroke="#cfc0a0"/>')
        b.append(f'<rect x="130" y="{y0}" width="{530 * ratio}" height="90" rx="8" fill="{ORANGE}" opacity="0.7"/>')
        b.append(t(130 + 530 * ratio / 2, y0 + 52, "장부에 오른 것", 15, "#ffffff", "700"))
        if ratio < 0.8:
            b.append(t(130 + 530 * (ratio + 1) / 2, y0 + 52, "장부 밖", 15, GRAY, "700"))
        b.append(t(672, y0 - 14, "실제로 오간 거래", 13, GRAY, "400", "end"))
    b.append(f'<path d="M397,404 L397,506" stroke="{GRAY}" stroke-width="2" marker-end="url(#gray)"/>')
    b.append(t(412, 460, "차액 셈이 들어온 뒤", 14, GRAY, "400", "start"))
    b.append(strip(760, "늘어난 것은 거래가 아니라 적는 범위였을 수 있다"))
    return base("장부 안과 밖", "장부가 커진 것과 거래가 커진 것은 다르다", "\n".join(b))


FIGURES = {
    5: fig_three_losses,
    11: fig_share_or_wage,
    19: fig_shared_loss,
    25: fig_what_travels,
    33: fig_one_round,
    42: fig_in_and_out,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild037")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
