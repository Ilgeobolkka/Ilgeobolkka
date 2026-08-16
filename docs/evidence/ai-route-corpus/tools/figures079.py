#!/usr/bin/env python3
"""book-079 이미지 페이지 4개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 대비 값과 크기를 눈금으로 넣지 않는다 —
기준 값은 글자 크기와 상황에 따라 달라 숫자를 넣으면 본문에 없는 기준이 생긴다.

사용: python3 figures079.py [출력디렉터리]
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
BOX = "#c3ccd0"
MARK = "#a8443a"
PICK = "#4a5c73"
SOFT = "#6f8a6a"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def box(x, y, w, h, fill="#ffffff", stroke=BOX, width=1.4, dash=None):
    extra = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="4" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="{width}"{extra}/>')


def arrow(x1, y1, x2, y2, color=INK, width=1.8, marker="ink"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def base(title, lead, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="fade" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{FADE}"/></marker>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="ink" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{INK}"/></marker>
</defs>
{t(W / 2, 120, title, 31, '#1e2c33', '700')}
<line x1="105" y1="150" x2="689" y2="150" stroke="#dde2e4"/>
{t(W / 2, 182, lead, 16, '#71818a')}
{body}
</svg>'''


def bottom(y, text, size=16):
    return ('<line x1="105" y1="%d" x2="689" y2="%d" stroke="#dde2e4"/>' % (y, y)
            + t(W / 2, y + 38, text, size, "#4c5b64", "700"))


def fig_two_readings():
    """1.6 — 같은 화면의 두 가지 읽기."""
    b = []
    b.append(box(96, 250, 280, 300))
    b.append(t(236, 232, "화면", 14, FADE))
    b.append(box(116, 274, 240, 44, "#f6f8f8"))
    b.append(t(236, 302, "제목", 15, INK, "700"))
    b.append(box(116, 342, 130, 48))
    b.append(t(181, 372, "입력칸", 14, INK, "700"))
    b.append(box(256, 350, 100, 32, "#ffffff", BOX, 1.0))
    b.append(t(306, 371, "안내 문구", 11, "#5d6c74"))
    b.append(box(116, 456, 110, 44, "#f6f8f8"))
    b.append(t(171, 484, "보내기", 14, INK, "700"))
    rows = ["제목 수준 하나", "입력칸 이름 없음", "안내 문구", "단추 보내기"]
    b.append(t(560, 232, "도구가 읽는 차례", 14, FADE))
    for k, name in enumerate(rows):
        y = 262 + k * 62
        color = MARK if k == 1 else INK
        b.append(box(456, y, 210, 44, "#ffffff", MARK if k == 1 else BOX,
                     1.6 if k == 1 else 1.2))
        b.append(t(561, y + 28, name, 14, color, "700"))
    links = [(360, 296, 456, 284), (250, 366, 456, 346), (356, 366, 456, 408),
             (230, 478, 456, 470)]
    for k, (x1, y1, x2, y2) in enumerate(links):
        color = MARK if k == 1 else FADE
        b.append(f'<path d="M{x1},{y1} C{x1 + 40},{y1} {x2 - 40},{y2} {x2 - 4},{y2}" '
                 f'fill="none" stroke="{color}" stroke-width="{1.6 if k == 1 else 1}"/>')
    b.append(t(396, 596, "이름이 전달되지 않는다", 13, MARK, "700"))
    b.append(bottom(650, "두 벌이 같은 것을 말하는지 본다"))
    return base("같은 화면의 두 가지 읽기", "눈으로 읽는 벌과 도구가 읽는 벌이 있다", "\n".join(b))


def fig_roles():
    """2.5 — 화면 요소의 뜻과 이름."""
    b = []
    cols = [(140, "화면에 보이는 것"), (360, "전달되는 종류"), (560, "전달되는 이름")]
    for x, name in cols:
        b.append(t(x, 250, name, 14, FADE, "700"))
    rows = [("돋보기", "단추", "이름 없음", True, "무엇을 하는지 알 수 없다"),
            ("돋보기", "단추", "검색", False, None),
            ("밑줄 친 글자", "링크", "자세히 보기", False, None),
            ("네모 상자", "이름 없음", "이름 없음", True, "요소인지조차 알 수 없다")]
    for k, (shown, kind, name, bad, note) in enumerate(rows):
        y = 286 + k * 78
        b.append(f'<line x1="105" y1="{y + 52}" x2="689" y2="{y + 52}" stroke="#e6eaec"/>')
        if shown == "돋보기":
            b.append(f'<circle cx="140" cy="{y + 22}" r="12" fill="none" stroke="{INK}" '
                     f'stroke-width="2"/>')
            b.append(f'<line x1="149" y1="{y + 31}" x2="158" y2="{y + 40}" stroke="{INK}" '
                     f'stroke-width="2"/>')
        elif shown == "밑줄 친 글자":
            b.append(t(140, y + 28, "자세히 보기", 14, PICK, "400"))
            b.append(f'<line x1="102" y1="{y + 34}" x2="178" y2="{y + 34}" stroke="{PICK}"/>')
        else:
            b.append(box(112, y + 6, 56, 34, "#f6f8f8"))
        b.append(t(360, y + 28, kind, 14, MARK if kind == "이름 없음" else INK, "700"))
        b.append(t(560, y + 28, name, 14, MARK if name == "이름 없음" else INK, "700"))
        if bad:
            b.append(t(676, y + 28, "●", 12, MARK, "700", "end"))
            b.append(t(676, y + 46, note, 11, MARK, "400", "end"))
    b.append(bottom(640, "보이는 모양이 같아도 전달되는 것은 다르다"))
    return base("화면 요소의 뜻과 이름", "도구는 종류와 이름과 상태를 함께 읽는다", "\n".join(b))


def fig_focus_path():
    """3.5 — 초점이 지나는 길."""
    b = []
    order_pairs = [(0, 1, 2, 3, 4, 5), (0, 3, 1, 4, 2, 5)]
    titles = ["적힌 차례가 보이는 차례와 같을 때", "배치만 옮겼을 때"]
    for side, x0 in enumerate((110, 430)):
        b.append(t(x0 + 130, 244, titles[side], 13, INK, "700"))
        b.append(box(x0, 296, 260, 340))
        b.append(box(x0 + 40, 258, 180, 30, "#f6f8f8", SOFT, 1.2))
        b.append(t(x0 + 130, 278, "건너뛰기", 12, SOFT, "700"))
        pos = []
        for i in range(6):
            bx = x0 + 30 + (i % 2) * 120
            by = 320 + (i // 2) * 100
            b.append(box(bx, by, 100, 56, "#f6f8f8"))
            pos.append((bx + 50, by + 28))
        seq = order_pairs[side]
        for a, c in zip(seq, seq[1:]):
            (x1, y1), (x2, y2) = pos[a], pos[c]
            b.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{PICK}" '
                     f'stroke-width="1.6" opacity="0.8"/>')
        b.append(f'<path d="M{x0 + 130},{290} L{pos[seq[0]][0]},{pos[seq[0]][1] - 20}" '
                 f'fill="none" stroke="{SOFT}" stroke-width="1.2" stroke-dasharray="4 3"/>')
    b.append(t(560, 672, "눈으로는 보이지 않는 어긋남", 13, MARK, "700"))
    b.append(bottom(720, "초점의 길은 구조에서 나온다"))
    return base("초점이 지나는 길", "보이는 차례와 옮겨 다니는 차례가 같아야 한다", "\n".join(b))


def fig_alt_branch():
    """4.4 — 그림에 붙는 설명의 갈래."""
    b = []
    b.append(box(287, 240, 220, 56, "#f6f8f8", INK, 1.6))
    b.append(t(397, 274, "지웠을 때 사라지는가", 15, INK, "700"))
    b.append(arrow(340, 300, 250, 340, FADE, 1.4, "fade"))
    b.append(arrow(454, 300, 570, 340, FADE, 1.4, "fade"))
    b.append(t(210, 362, "사라지는 것이 있다", 13, INK, "700"))
    b.append(t(590, 362, "사라지는 것이 없다", 13, INK, "700"))
    leaves = [(96, "단추 안의 그림", "하는 일을 적는다"),
              (250, "정보를 담은 그림", "담긴 정보를 적는다"),
              (404, "본문과 같은 말", "짧게 적거나 비운다")]
    for x, name, howto in leaves:
        b.append(box(x, 396, 140, 60, "#ffffff"))
        b.append(t(x + 70, 432, name, 12, INK, "700"))
        b.append(arrow(x + 70, 460, x + 70, 492, FADE, 1.2, "fade"))
        b.append(t(x + 70, 514, howto, 12, "#5d6c74"))
        b.append(f'<line x1="{210}" y1="378" x2="{x + 70}" y2="392" stroke="{FADE}" '
                 f'stroke-width="1"/>')
    b.append(box(560, 396, 140, 60, "#ffffff"))
    b.append(t(630, 432, "장식", 13, INK, "700"))
    b.append(arrow(630, 460, 630, 492, FADE, 1.2, "fade"))
    b.append(t(630, 514, "비워 둔다", 12, "#5d6c74"))
    b.append(t(630, 540, "적지 않는 것과 다르다", 11, MARK, "700"))
    b.append(bottom(600, "역할이 정해지면 적을 말도 정해진다"))
    return base("그림에 붙는 설명의 갈래", "그림을 지웠을 때 사라지는 것이 있는가", "\n".join(b))


FIGURES = {
    7: fig_two_readings,
    13: fig_roles,
    20: fig_focus_path,
    26: fig_alt_branch,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild079")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
