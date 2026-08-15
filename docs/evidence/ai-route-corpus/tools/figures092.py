#!/usr/bin/env python3
"""book-092 이미지 페이지 4개의 SVG 생성. figures091.py의 t()/base() 패턴을 따른다.

네 도표의 형식을 모두 다르게 잡았다. 두 곡선 비교, 판정 흐름도, 두 열 대응표, 문장 도해다.
공통 약속은 하나다. 고른 쪽·지킬 쪽은 굵은 테두리, 버려지는 쪽은 옅은 선이다.

사용: python3 figures092.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"
DROP = "#c3ccd0"
MARK = "#b4703a"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="keep" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{KEEP}"/></marker>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
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


def box(x, y, w, h, label, fill="#ffffff", stroke=INK, size=15, width=1.8, dash=""):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="{fill}" stroke="{stroke}" '
            f'stroke-width="{width}"{dash}/>' + (t(x + w / 2, y + h / 2 + 5, label, size, INK) if label else ""))


def diamond(cx, cy, w, h, label):
    pts = f"{cx},{cy - h / 2} {cx + w / 2},{cy} {cx},{cy + h / 2} {cx - w / 2},{cy}"
    return (f'<polygon points="{pts}" fill="#ffffff" stroke="{INK}" stroke-width="1.8"/>'
            + t(cx, cy + 5, label, 14, INK))


def axes(x0, y0, x1, y1, xlabel, ylabel):
    return (f'<line x1="{x0}" y1="{y0}" x2="{x0}" y2="{y1}" stroke="{INK}" stroke-width="1.8"/>'
            f'<line x1="{x0}" y1="{y1}" x2="{x1}" y2="{y1}" stroke="{INK}" stroke-width="1.8"/>'
            + t((x0 + x1) / 2, y1 + 44, xlabel, 16, MUTED)
            + f'<text x="{x0 - 26}" y="{(y0 + y1) / 2}" text-anchor="middle" font-size="16" '
              f'fill="{MUTED}" transform="rotate(-90 {x0 - 26} {(y0 + y1) / 2})">{ylabel}</text>')


# ── p6 지금 줄고 나중에 는다 ─────────────────────────────────────────
def fig_discount():
    """미룬 경우와 시작한 경우의 불편 곡선. 즉시 줄어드는 쪽이 선택을 정한다."""
    b = []
    x0, x1, top, bot = 175, 645, 285, 590
    b.append(axes(x0, top, x1, bot, "시간", "불편의 크기"))
    b.append(t(x0 + 6, bot + 22, "미루기로 정한 순간", 13, MUTED, anchor="start"))
    b.append(t(x1 - 6, bot + 22, "마감", 13, MUTED, anchor="end"))
    hi, lo = top + 40, bot - 40
    mid = bot - 150
    # 미룬 경우: 높이 시작 → 급락 → 낮게 유지 → 마감에서 치솟음
    delay = [(x0, hi), (x0 + 30, lo), (x0 + 150, lo - 10), (x0 + 300, lo - 20),
             (x0 + 390, mid - 40), (x1, top + 10)]
    b.append('<polyline points="' + " ".join(f"{x},{y}" for x, y in delay)
             + f'" fill="none" stroke="{MARK}" stroke-width="3.2"/>')
    b.append(t(x0 + 190, lo - 26, "미룬 경우", 15, MARK, "700"))
    # 시작한 경우: 조금 올라갔다가 서서히 내려와 마감에 가장 낮음
    start = [(x0, hi), (x0 + 60, hi - 18), (x0 + 200, mid), (x0 + 360, lo),
             (x1, bot - 12)]
    b.append('<polyline points="' + " ".join(f"{x},{y}" for x, y in start)
             + f'" fill="none" stroke="{KEEP}" stroke-width="2.4" stroke-dasharray="7 5"/>')
    b.append(t(x0 + 250, mid - 22, "시작한 경우", 15, KEEP, "700"))
    b.append(f'<line x1="{x0 + 34}" y1="{hi}" x2="{x0 + 34}" y2="{lo}" stroke="{LINE}" '
             f'stroke-width="1.6" marker-end="url(#gray)"/>')
    b.append(t(x0 + 48, hi + 108, "지금 줄어든 만큼", 14, MUTED, anchor="start"))
    b.append(f'<line x1="{x1 - 16}" y1="{bot - 12}" x2="{x1 - 16}" y2="{top + 14}" stroke="{LINE}" '
             f'stroke-width="1.6" marker-end="url(#gray)"/>')
    b.append(t(x1 - 26, top - 6, "나중에 늘어난 만큼", 14, MUTED, anchor="end"))
    b.append(note_box(147, 690, 500, "즉시 줄어드는 쪽이 이긴다"))
    b.append(caption(W / 2, 812, [
        "미루면 불편이 지금 사라지고 시작하면 나중에 사라진다.",
        "저녁의 저울에는 지금 오는 쪽만 올라간다.",
    ], 16))
    return base("지금 줄고 나중에 는다", "미룬 경우와 시작한 경우의 불편", "".join(b))


# ── p12 어느 쪽에서 막혔는가 ─────────────────────────────────────────
def fig_triage():
    """두 번의 물음으로 미루는 원인을 셋으로 가르는 흐름도."""
    b = []
    b.append(box(297, 240, 200, 56, "시작하지 못하고 있다", "#eef1f2", LINE, 16, 1.6))
    b.append(f'<line x1="397" y1="296" x2="397" y2="330" stroke="{LINE}" stroke-width="2" '
             f'marker-end="url(#gray)"/>')
    b.append(diamond(397, 380, 300, 96, "무엇부터 할지 아는가"))
    b.append(f'<line x1="247" y1="380" x2="180" y2="380" stroke="{LINE}" stroke-width="2" '
             f'marker-end="url(#gray)"/>')
    b.append(t(214, 366, "아니오", 13, MUTED))
    b.append(box(60, 352, 120, 56, "모르는 일", "#ffffff", KEEP, 15, 2.6))
    b.append(t(120, 432, "정보", 14, MARK, "700"))
    b.append(f'<line x1="397" y1="428" x2="397" y2="470" stroke="{LINE}" stroke-width="2" '
             f'marker-end="url(#gray)"/>')
    b.append(t(420, 456, "예", 13, MUTED))
    b.append(diamond(397, 520, 320, 96, "잘 해내야 한다고 느끼는가"))
    b.append(f'<line x1="557" y1="520" x2="620" y2="520" stroke="{LINE}" stroke-width="2" '
             f'marker-end="url(#gray)"/>')
    b.append(t(590, 506, "예", 13, MUTED))
    b.append(box(620, 492, 130, 56, "잘하고 싶은 일", "#ffffff", KEEP, 15, 2.6))
    b.append(t(685, 572, "기준", 14, MARK, "700"))
    b.append(f'<line x1="397" y1="568" x2="397" y2="612" stroke="{LINE}" stroke-width="2" '
             f'marker-end="url(#gray)"/>')
    b.append(t(424, 598, "아니오", 13, MUTED))
    b.append(box(332, 612, 130, 56, "하기 싫은 일", "#ffffff", KEEP, 15, 2.6))
    b.append(t(397, 692, "보상", 14, MARK, "700"))
    b.append(note_box(147, 740, 500, "대응은 원인을 따라간다"))
    b.append(caption(W / 2, 862, [
        "같은 미루기라도 필요한 것이 정보인지 기준인지 보상인지가 갈린다.",
    ], 16))
    return base("어느 쪽에서 막혔는가", "미루는 원인을 세 갈래로 가르는 물음", "".join(b))


# ── p24 흥정표 ────────────────────────────────────────────────────────
def fig_bargain():
    """처음 요구와 오늘 합의를 세 행으로 대응시킨 표."""
    b = []
    x0, w, gap = 140, 230, 84
    x1 = x0 + w + gap
    y0, rh = 300, 92
    b.append(t(x0 + w / 2, y0 - 22, "처음 요구", 17, MUTED, "700"))
    b.append(t(x1 + w / 2, y0 - 22, "오늘 합의", 17, KEEP, "700"))
    rows = [("스무 쪽 읽기", "다섯 쪽 읽기", "분량"),
            ("완성된 발표 자료", "글자만 넣은 초안", "완성도"),
            ("두 시간 앉기", "삼십 분 앉기", "시간")]
    for i, (left, right, axis) in enumerate(rows):
        y = y0 + i * (rh + 26)
        b.append(box(x0, y, w, rh, left, "#f2f4f5", DROP, 16, 1.6))
        b.append(box(x1, y, w, rh, right, "#ffffff", KEEP, 16, 2.8))
        b.append(f'<line x1="{x0 + w + 12}" y1="{y + rh / 2}" x2="{x1 - 12}" y2="{y + rh / 2}" '
                 f'stroke="{MARK}" stroke-width="2" marker-end="url(#mark)"/>')
        b.append(t(x0 + w + gap / 2, y + rh / 2 - 14, axis, 13, MARK, "700"))
    b.append(t(W / 2, y0 + 3 * (rh + 26) + 14, "합의한 쪽만 오늘 지킨다", 16, INK, "700"))
    b.append(note_box(147, 700, 500, "한 번에 한 줄만 흥정한다"))
    b.append(caption(W / 2, 822, [
        "분량·완성도·시간 가운데 오늘 조정할 것을 하나만 고른다.",
        "셋을 한꺼번에 낮추면 무엇이 통했는지 알 수 없다.",
    ], 16))
    return base("흥정표", "오늘 저녁에 다시 정한 조건", "".join(b))


# ── p32 언제 무엇을 할지 적힌 문장 ────────────────────────────────────
def fig_sentence():
    """막연한 다짐과 신호·행동으로 나뉜 조건문을 위아래로 대비한다."""
    b = []
    x, w = 120, 554
    b.append(box(x, 270, w, 88, "내일은 열심히 하자", "#f4f5f6", DROP, 20, 1.8,
                 ' stroke-dasharray="8 6"'))
    b.append(t(x + w + 8, 320, "언제·어디·", 13, MUTED, anchor="start"))
    b.append(t(x + w + 8, 340, "무엇이 없다", 13, MUTED, anchor="start"))

    y = 430
    half = w / 2
    b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{110}" rx="8" fill="#ffffff" '
             f'stroke="{KEEP}" stroke-width="3"/>')
    b.append(f'<line x1="{x + half}" y1="{y}" x2="{x + half}" y2="{y + 110}" stroke="{KEEP}" '
             f'stroke-width="1.4"/>')
    b.append(t(x + half / 2, y + 46, "설거지를 끝내면", 18, INK, "600"))
    b.append(t(x + half / 2, y + 78, "신호", 13, MARK, "700"))
    b.append(t(x + half + half / 2, y + 40, "책상에 앉아", 18, INK, "600"))
    b.append(t(x + half + half / 2, y + 64, "다섯 쪽을 읽는다", 18, INK, "600"))
    b.append(t(x + half + half / 2, y + 92, "행동", 13, MARK, "700"))
    b.append(f'<line x1="{x + half - 26}" y1="{y + 55}" x2="{x + half + 20}" y2="{y + 55}" '
             f'stroke="{MARK}" stroke-width="2.4" marker-end="url(#mark)"/>')

    b.append(t(W / 2, 600, "신호로 쓸 수 있는 것", 16, INK, "700"))
    for i, item in enumerate(["시각", "장소", "방금 끝낸 일"]):
        b.append(box(196 + i * 140, 626, 118, 50, item, "#f7f4ec", "#c5a866", 15, 1.6))
    b.append(note_box(147, 720, 500, "신호가 없으면 문장이 아니다"))
    b.append(caption(W / 2, 842, [
        "언제가 없으면 그 순간마다 다시 정해야 하고,",
        "다시 정할 때마다 미루는 마음이 한 번씩 이길 기회를 얻는다.",
    ], 16))
    return base("언제 무엇을 할지 적힌 문장", "막연한 다짐과 조건문", "".join(b))


FIGURES = {6: fig_discount, 12: fig_triage, 24: fig_bargain, 32: fig_sentence}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild092")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
