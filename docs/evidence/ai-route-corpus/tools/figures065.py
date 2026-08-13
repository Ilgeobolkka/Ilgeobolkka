#!/usr/bin/env python3
"""book-065 이미지 페이지 6개의 SVG 생성. figures062.py의 t()/base() 패턴을 따른다.

색 자체가 주제인 책이라 여섯 도표 모두 "같은 색을 다른 이웃 옆에 놓는" 형태다. 같은 색임을 눈으로
확인할 수 있도록 비교 대상 사이에 같은 색의 가는 띠를 이어 둔다. 이 띠가 없으면 도표가 주장하는
'같은 색'을 독자가 믿을 근거가 없다.

사용: python3 figures065.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"

MID_GRAY = "#8e9498"      # 도표에서 같은 색임을 보이는 기준 회색
LIGHT_BG = "#d9dde0"
DARK_BG = "#3b464c"
WARM = "#b2593f"
COOL = "#3f6f96"
GREEN = "#4f8368"
NEUTRAL = "#c9ced1"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
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


def same_color_link(x1, x2, y, color, label="이 둘은 같은 색이다"):
    """두 비교 대상이 물리적으로 같은 색임을 보이는 연결 띠."""
    return (f'<rect x="{x1}" y="{y}" width="{x2 - x1}" height="14" fill="{color}"/>'
            + t((x1 + x2) / 2, y + 38, label, 14, MUTED))


# ── p8 같은 회색, 다른 바탕 ───────────────────────────────────────────
def fig_value_contrast():
    b = []
    size, inner = 226, 92
    y = 290
    lefts = [148, 420]
    for x, bg, label in ((lefts[0], LIGHT_BG, "밝은 바탕"), (lefts[1], DARK_BG, "어두운 바탕")):
        b.append(f'<rect x="{x}" y="{y}" width="{size}" height="{size}" fill="{bg}" stroke="{SOFT}"/>')
        cx = x + (size - inner) / 2
        cy = y + (size - inner) / 2
        b.append(f'<rect x="{cx}" y="{cy}" width="{inner}" height="{inner}" fill="{MID_GRAY}"/>')
        b.append(t(x + size / 2, y + size + 34, label, 17, INK, "600"))
    link_y = y + size + 76
    b.append(same_color_link(lefts[0] + size / 2, lefts[1] + size / 2, link_y, MID_GRAY))
    b.append(t(W / 2, 258, "가운데 두 정사각형은 완전히 같은 회색이다", 15, MUTED))
    b.append(note_box(147, 726, 500, "눈은 곁의 색과 견주어 본다"))
    b.append(caption(W / 2, 848, [
        "밝은 바탕 위에서는 어둡게, 어두운 바탕 위에서는 밝게 보인다.",
        "달라진 것은 색이 아니라 그 색이 놓인 자리다.",
    ], 16))
    return base("같은 회색, 다른 바탕", "같은 회색을 밝기가 다른 두 바탕 위에 올린 그림", "".join(b))


# ── p14 맞닿은 자리 ───────────────────────────────────────────────────
def fig_edge():
    b = []
    x0, w, h = 148, 498, 150
    for i, (y, gap, label) in enumerate(((290, 0, "직접 닿음"), (520, 16, "사이에 선을 둠"))):
        half = (w - gap) / 2
        b.append(f'<rect x="{x0}" y="{y}" width="{half}" height="{h}" fill="{WARM}"/>')
        b.append(f'<rect x="{x0 + half + gap}" y="{y}" width="{half}" height="{h}" fill="{GREEN}"/>')
        if gap:
            b.append(f'<rect x="{x0 + half}" y="{y}" width="{gap}" height="{h}" fill="{NEUTRAL}"/>')
        else:
            # 경계 양옆에서 서로를 밀어내는 옅은 띠
            b.append(f'<rect x="{x0 + half - 14}" y="{y}" width="14" height="{h}" fill="#ffffff" opacity="0.22"/>')
            b.append(f'<rect x="{x0 + half}" y="{y}" width="14" height="{h}" fill="#000000" opacity="0.14"/>')
        b.append(t(x0 + w + 16, y + h / 2 + 6, label, 16, MUTED, anchor="start"))
    b.append(t(W / 2, 258, "두 색의 밝기는 위아래가 같다", 15, MUTED))
    b.append(note_box(147, 726, 500, "흔들림은 경계에서 생긴다"))
    b.append(caption(W / 2, 848, [
        "밝기가 비슷한 두 색이 직접 닿으면 경계가 떨리는 것처럼 보인다.",
        "사이에 밝기가 다른 선을 넣으면 두 색을 그대로 두고 떨림만 없앨 수 있다.",
    ], 16))
    return base("맞닿은 자리", "두 색이 직접 닿을 때와 사이에 선을 둘 때", "".join(b))


# ── p23 어느 쪽으로도 갈 수 있는 색 ───────────────────────────────────
def fig_warm_cool():
    b = []
    w, h, y = 240, 200, 300
    lefts = [148, 406]
    for x, neighbor, nlabel, read in ((lefts[0], WARM, "붉은 이웃", "차갑게 읽힌다"),
                                      (lefts[1], COOL, "푸른 이웃", "따뜻하게 읽힌다")):
        b.append(f'<rect x="{x}" y="{y}" width="{w / 2}" height="{h}" fill="{neighbor}"/>')
        b.append(f'<rect x="{x + w / 2}" y="{y}" width="{w / 2}" height="{h}" fill="{GREEN}"/>')
        b.append(t(x + w * 0.75, y - 16, read, 14, MUTED))
        b.append(t(x + w / 2, y + h + 34, nlabel, 17, INK, "600"))
    link_y = y + h + 76
    b.append(same_color_link(lefts[0] + w * 0.75, lefts[1] + w * 0.75, link_y, GREEN,
                             "두 초록은 같은 색이다"))
    b.append(t(W / 2, 262, "오른쪽 절반의 초록은 두 그림이 완전히 같다", 15, MUTED))
    b.append(note_box(147, 700, 500, "온감도 관계 안에서 정해진다"))
    b.append(caption(W / 2, 822, [
        "초록처럼 어느 쪽으로도 읽히는 색은 이웃이 온감을 정한다.",
        "그래서 이 계열의 색은 이름으로 기억하면 반드시 틀린다.",
    ], 16))
    return base("어느 쪽으로도 갈 수 있는 색", "같은 초록을 붉은 이웃과 푸른 이웃 옆에 놓은 그림",
                "".join(b))


# ── p30 같은 색, 다른 이웃 ────────────────────────────────────────────
def fig_association():
    b = []
    size, y = 168, 300
    lefts = [110, 313, 516]
    backs = [("#2f5140", "짙은 초록"), ("#d5d8da", "밝은 회색"), ("#2b3a55", "짙은 남색")]
    accent = "#b8402f"
    for x, (bg, label) in zip(lefts, backs):
        b.append(f'<rect x="{x}" y="{y}" width="{size}" height="{size}" fill="{bg}"/>')
        b.append(f'<rect x="{x + 18}" y="{y + size - 62}" width="44" height="44" fill="{accent}"/>')
        b.append(t(x + size / 2, y + size + 34, label, 16, INK, "600"))
    link_y = y + size + 76
    b.append(same_color_link(lefts[0] + 40, lefts[2] + 40, link_y, accent,
                             "세 붉은 사각형은 같은 색이다"))
    b.append(t(W / 2, 262, "왼쪽 아래의 붉은 사각형은 세 그림이 완전히 같다", 15, MUTED))
    b.append(note_box(147, 672, 500, "같은 붉음이 다른 것을 부른다"))
    b.append(caption(W / 2, 794, [
        "색 하나는 여러 가지를 부르지만 조합은 범위를 좁힌다.",
        "그래서 연상은 색이 아니라 조합으로 만든다.",
    ], 16))
    return base("같은 색, 다른 이웃", "같은 붉은색을 세 가지 이웃과 함께 놓은 그림", "".join(b))


# ── p38 넓게, 받치고, 한 번만 ─────────────────────────────────────────
def fig_three_colors():
    b = []
    w, h, y = 240, 220, 300
    a, c1, c2 = "#cfd6d2", "#4f6f66", "#c2643c"
    x = 148
    for i, part in enumerate((a, c1, c2)):
        b.append(f'<rect x="{x + i * (w / 3)}" y="{y}" width="{w / 3}" height="{h}" fill="{part}"/>')
    b.append(t(x + w / 2, y + h + 34, "같은 넓이", 17, INK, "600"))

    x2 = 406
    b.append(f'<rect x="{x2}" y="{y}" width="{w}" height="{h}" fill="{a}"/>')
    b.append(f'<rect x="{x2}" y="{y + h - 52}" width="{w}" height="52" fill="{c1}"/>')
    b.append(f'<rect x="{x2 + w - 58}" y="{y + 24}" width="30" height="30" fill="{c2}"/>')
    b.append(f'<line x1="{x2 + w - 28}" y1="{y + 39}" x2="{x2 + w + 34}" y2="{y + 39}" stroke="{LINE}"/>')
    b.append(t(x2 + w + 40, y + 44, "한 번만 쓰는 색", 14, MUTED, anchor="start"))
    b.append(t(x2 + w / 2, y + h + 34, "넓게 · 받치고 · 한 번만", 17, INK, "600"))

    b.append(t(W / 2, 262, "두 그림은 완전히 같은 세 색만 쓴다", 15, MUTED))
    b.append(note_box(147, 640, 500, "같은 세 색, 다른 구조"))
    b.append(caption(W / 2, 762, [
        "셋을 비슷한 넓이로 쓰면 어느 색도 주인이 되지 못한다.",
        "역할을 나누면 넓은 색이 분위기를, 좁은 색이 시선을 맡는다.",
    ], 16))
    return base("넓게, 받치고, 한 번만", "같은 세 색을 균등하게 쓴 배치와 역할을 나눈 배치",
                "".join(b))


# ── p46 빛이 바뀌면 ───────────────────────────────────────────────────
def fig_light():
    b = []
    w, h, y = 240, 220, 300
    lefts = [148, 406]
    films = [("#e8b74a", 0.26, "따뜻한 빛 아래"), ("#5f9ad0", 0.26, "차가운 빛 아래")]
    for x, (film, opacity, label) in zip(lefts, films):
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h / 2}" fill="{WARM}"/>')
        b.append(f'<rect x="{x}" y="{y + h / 2}" width="{w}" height="{h / 2}" fill="{COOL}"/>')
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{film}" opacity="{opacity}"/>')
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="none" stroke="{SOFT}"/>')
        b.append(t(x + w / 2, y + h + 34, label, 17, INK, "600"))
    b.append(t(W / 2, 262, "두 그림의 배색과 비율은 완전히 같다", 15, MUTED))
    b.append(t(lefts[0] + w / 2, y + h / 4 + 6, "붉은 계열이 산다", 14, "#ffffff"))
    b.append(t(lefts[1] + w / 2, y + h * 0.75 + 6, "푸른 계열이 산다", 14, "#ffffff"))
    b.append(note_box(147, 640, 500, "배색은 빛과 함께 완성된다"))
    b.append(caption(W / 2, 762, [
        "같은 배색이 조명에 따라 어느 계열을 살릴지 달라진다.",
        "그래서 배색은 그 물건이 놓일 자리의 빛 아래에서 정한다.",
    ], 16))
    return base("빛이 바뀌면", "같은 배색을 따뜻한 빛과 차가운 빛 아래 놓은 그림", "".join(b))


FIGURES = {8: fig_value_contrast, 14: fig_edge, 23: fig_warm_cool,
           30: fig_association, 38: fig_three_colors, 46: fig_light}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild065")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
