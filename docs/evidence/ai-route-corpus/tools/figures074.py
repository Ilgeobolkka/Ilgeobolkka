#!/usr/bin/env python3
"""book-074 이미지 페이지 4개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 수치 눈금을 넣지 않는다 — 구조 설계의
판단 기준은 양이 아니라 무엇이 어디에 놓였는지에 있고, 숫자를 넣으면 본문에 없는 기준이 생긴다.

사용: python3 figures074.py [출력디렉터리]
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
BOX = "#c3ccd0"
MARK = "#a8443a"
# 함께 바뀌는 코드를 나타내는 세 가지 색. 도표 안에서만 뜻을 가진다.
TONE = ("#4a5c73", "#6f8a6a", "#8a6a44")


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


def fig_decompose():
    """1.6 — 함수 분해 전후 구조."""
    b = []
    # 위 칸: 한 덩어리
    b.append(t(397, 250, "나누기 전", 19, INK, "700"))
    bx, by, bw, bh = 232, 272, 330, 236
    b.append(box(bx, by, bw, bh))
    order = [0, 1, 0, 2, 1, 0, 2, 1, 2, 0, 1, 2]
    for i, tone in enumerate(order):
        b.append(f'<rect x="{bx + 26}" y="{by + 18 + i * 18}" width="{bw - 52}" height="9" '
                 f'rx="2" fill="{TONE[tone]}" opacity="0.55"/>')
    b.append(arrow(140, by + bh / 2, bx - 8, by + bh / 2))
    b.append(t(136, by + bh / 2 - 14, "들어오는 값", 14, FADE, "400", "end"))
    b.append(arrow(bx + bw + 8, by + bh / 2, 660, by + bh / 2))
    b.append(t(664, by + bh / 2 - 14, "결과", 14, FADE, "400", "start"))

    # 아래 칸: 세 단계
    b.append(t(397, 590, "나눈 뒤", 19, INK, "700"))
    names = ["값 준비", "계산", "결과 정리"]
    sx, sy, sw, sh = 104, 616, 158, 132
    gap = 44
    for k, name in enumerate(names):
        x = sx + k * (sw + gap)
        b.append(box(x, sy, sw, sh))
        b.append(f'<rect x="{x + 22}" y="{sy + 34}" width="{sw - 44}" height="9" rx="2" '
                 f'fill="{TONE[k]}" opacity="0.55"/>')
        b.append(f'<rect x="{x + 22}" y="{sy + 52}" width="{sw - 44}" height="9" rx="2" '
                 f'fill="{TONE[k]}" opacity="0.55"/>')
        b.append(f'<rect x="{x + 22}" y="{sy + 70}" width="{sw - 44}" height="9" rx="2" '
                 f'fill="{TONE[k]}" opacity="0.55"/>')
        b.append(t(x + sw / 2, sy + 112, name, 17, INK, "700"))
    for k, label in enumerate(("넘기는 값 둘", "넘기는 값 하나")):
        x = sx + k * (sw + gap) + sw
        b.append(arrow(x + 6, sy + sh / 2, x + gap - 6, sy + sh / 2))
        b.append(t(x + gap / 2, sy + sh / 2 - 16, label, 13, FADE))
    right = sx + 3 * sw + 2 * gap
    b.append(arrow(right + 6, sy + sh / 2, right + 54, sy + sh / 2))
    b.append(t(right + 58, sy + sh / 2 - 14, "결과", 14, FADE, "400", "start"))
    b.append(bottom(830, "나뉜 자리마다 무엇이 오가는지 드러난다"))
    return base("함수 분해 전후 구조", "같은 처리를 두 가지로 그린 것이다", "\n".join(b))


def fig_direction():
    """3.5 — 의존 방향과 순환."""
    b = []
    # 왼쪽: 한 방향
    b.append(t(212, 250, "한쪽으로 흐르는 의존", 19, INK, "700"))
    layers = ["화면", "업무 처리", "저장 접근", "공통 규칙"]
    lx, lw, lh = 122, 180, 62
    for k, name in enumerate(layers):
        y = 288 + k * 108
        b.append(box(lx, y, lw, lh))
        b.append(t(lx + lw / 2, y + 38, name, 17, INK, "700"))
        if k:
            b.append(arrow(lx + lw / 2, y - 40, lx + lw / 2, y - 8))
    b.append(t(212, 720, "아래쪽은 위쪽을 모른다", 15, FADE))

    # 오른쪽: 순환
    b.append(t(543, 250, "되돌아오는 의존", 19, INK, "700"))
    nodes = {"주문 처리": (467, 300), "알림 발송": (595, 500), "재고 확인": (391, 500)}
    nw, nh = 152, 60
    for name, (x, y) in nodes.items():
        b.append(box(x, y, nw, nh))
        b.append(t(x + nw / 2, y + 37, name, 17, INK, "700"))
    b.append(arrow(577, 366, 643, 492))
    b.append(arrow(587, 542, 551, 542))
    b.append(arrow(445, 496, 497, 370, MARK, 2.6, "mark"))
    b.append(t(543, 620, "고칠 곳을 정하려면", 15, MARK, "700"))
    b.append(t(543, 644, "셋을 함께 열어야 한다", 15, MARK, "700"))
    b.append(bottom(830, "화살표가 돌아오면 경계가 하나로 합쳐진다"))
    return base("의존 방향과 순환", "화살표는 아는 쪽에서 알려진 쪽으로 향한다", "\n".join(b))


def fig_grouping():
    """4.6 — 변경 이유로 묶은 모듈."""
    b = []
    rows = (
        (250, "생김새로 묶은 배치",
         ("화면 조각 모음", "계산 함수 모음", "저장 함수 모음"),
         ((0, 1, 2, 0), (1, 2, 0, 1), (2, 0, 1, 2)),
         "한 가지 요구가 바뀌면 세 네모를 연다", MARK),
        (560, "변경 이유로 묶은 배치",
         ("주문", "재고", "배송"),
         ((0, 0, 0, 0), (1, 1, 1, 1), (2, 2, 2, 2)),
         "한 가지 요구가 바뀌면 한 네모를 연다", "#4c5b64"),
    )
    bw, bh, gap, x0 = 170, 148, 32, 105
    for top, label, names, tones, note, color in rows:
        b.append(t(x0, top, label, 19, INK, "700", "start"))
        for k, name in enumerate(names):
            x = x0 + k * (bw + gap)
            y = top + 22
            b.append(box(x, y, bw, bh))
            b.append(t(x + bw / 2, y + 30, name, 16, INK, "700"))
            for i, tone in enumerate(tones[k]):
                cx = x + 44 + (i % 2) * 84
                cy = y + 74 + (i // 2) * 46
                b.append(f'<circle cx="{cx}" cy="{cy}" r="17" fill="{TONE[tone]}" opacity="0.75"/>')
        b.append(t(x0 + 3 * bw + 2 * gap, top + bh + 52, note, 15, color, "700", "end"))
    b.append(bottom(880, "색은 함께 바뀌는 코드를 뜻한다"))
    return base("변경 이유로 묶은 모듈", "같은 코드를 두 가지 기준으로 나눈 것이다", "\n".join(b))


def fig_split_steps():
    """6.5 — 분리 절차와 확인 지점."""
    b = []
    steps = (
        (("의존", "적기"), ("호출하는", "곳 목록")),
        (("나눌 자리", "고르기"), ("오갈 값의", "수")),
        (("인터페이스", "정하기"), ("조건과", "실패의 형태")),
        (("코드", "옮기기"), ("동작이", "같은지")),
        (("옛 자리", "지우기"), ("남은 참조가", "없는지")),
    )
    sw, sh, gap, x0, y0 = 108, 96, 14, 99, 330
    for k, (name, check) in enumerate(steps):
        x = x0 + k * (sw + gap)
        b.append(box(x, y0, sw, sh))
        b.append(t(x + sw / 2, y0 + 40, name[0], 16, INK, "700"))
        b.append(t(x + sw / 2, y0 + 64, name[1], 16, INK, "700"))
        b.append(box(x, y0 + 150, sw, sh - 12, "#f6f8f8", BOX, 1.0, "4 3"))
        b.append(t(x + sw / 2, y0 + 184, check[0], 13, "#5d6c74"))
        b.append(t(x + sw / 2, y0 + 204, check[1], 13, "#5d6c74"))
        b.append(arrow(x + sw / 2, y0 + sh + 6, x + sw / 2, y0 + 144, FADE, 1.2, "fade"))
        if k:
            b.append(arrow(x - gap - 4, y0 + sh / 2, x - 6, y0 + sh / 2))
    # 넷째에서 셋째로 되돌아가는 길
    x3 = x0 + 2 * (sw + gap) + sw / 2
    x4 = x0 + 3 * (sw + gap) + sw / 2
    b.append(f'<path d="M{x4},{y0 - 8} C{x4},{y0 - 76} {x3},{y0 - 76} {x3},{y0 - 8}" '
             f'fill="none" stroke="{MARK}" stroke-width="1.8" marker-end="url(#mark)"/>')
    b.append(t((x3 + x4) / 2, y0 - 88, "오갈 값이 정리되지 않으면 되돌아간다", 14, MARK, "700"))
    b.append(bottom(690, "각 단계는 확인 항목을 지나야 다음으로 간다"))
    return base("분리 절차와 확인 지점", "코드를 옮기는 일은 넷째 자리에서 시작한다", "\n".join(b))


FIGURES = {
    7: fig_decompose,
    20: fig_direction,
    28: fig_grouping,
    41: fig_split_steps,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild074")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
