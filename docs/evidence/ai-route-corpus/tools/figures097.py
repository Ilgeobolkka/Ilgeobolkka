#!/usr/bin/env python3
"""book-097 이미지 페이지 4개의 SVG 생성. figures091.py의 t()/base() 패턴을 따른다.

네 도표의 형식을 모두 다르게 잡았다. 요일별 점 분포, 묶음 질문지, 주×항목 격자, 목록 대비다.
공통 약속은 둘이다. 남은 것·고른 것은 진한 색, 사라진 것·지운 것은 옅은 색이다.

사용: python3 figures097.py <출력디렉터리>
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


# ── p9 남는 것과 남지 않는 것 ─────────────────────────────────────────
def fig_recall():
    """한 주에 있었던 일과 금요일에 떠오른 일을 요일축 위의 점으로 대비한다."""
    b = []
    x0, x1, top, bot = 170, 660, 280, 560
    days = ["월", "화", "수", "목", "금"]
    total = [5, 4, 6, 4, 3]
    recalled = [0, 1, 1, 2, 3]
    step = (x1 - x0) / (len(days) - 1)
    b.append(f'<line x1="{x0 - 40}" y1="{bot}" x2="{x1 + 40}" y2="{bot}" stroke="{INK}" '
             f'stroke-width="1.8"/>')
    for i, (day, n, r) in enumerate(zip(days, total, recalled)):
        cx = x0 + step * i
        b.append(t(cx, bot + 28, day, 16, INK, "600"))
        for k in range(n):
            dy = bot - 34 - (k * 38) - (14 if k % 2 else 0)
            strong = k < r
            b.append(f'<circle cx="{cx + (12 if k % 2 else -12)}" cy="{dy}" r="9" '
                     f'fill="{KEEP if strong else "#ffffff"}" opacity="{1 if strong else 1}" '
                     f'stroke="{KEEP if strong else DROP}" stroke-width="{2 if strong else 1.4}"/>')
        b.append(t(cx, bot + 54, f"{n} / {r}", 13, MUTED))
    b.append(t(W / 2, bot + 82, "요일마다 있었던 일 수 / 금요일에 떠오른 수", 14, MUTED))
    b.append(t(x1 + 46, top + 40, "가까운 날이", 13, MARK, anchor="start"))
    b.append(t(x1 + 46, top + 58, "크게 남는다", 13, MARK, anchor="start"))
    b.append(t(x1 + 46, top + 96, "큰일만", 13, MARK, anchor="start"))
    b.append(t(x1 + 46, top + 114, "남는다", 13, MARK, anchor="start"))
    b.append(note_box(147, 700, 500, "평범하게 지나간 사흘이 그 주의 대부분이다"))
    b.append(caption(W / 2, 822, [
        "진한 점은 금요일에 떠오른 일이고 빈 점은 떠오르지 않은 일이다.",
        "기억만으로 회고하면 한 주가 실제와 다르게 그려진다.",
    ], 16))
    return base("남는 것과 남지 않는 것", "한 주의 일과 기억에 남은 일", "".join(b))


# ── p13 열 문장 ───────────────────────────────────────────────────────
def fig_sheet():
    """열 문장을 사실·판단·다음 주 세 묶음으로 나눈 질문지."""
    b = []
    x, w, y0, rh = 190, 400, 270, 46
    groups = [
        ("사실", ["이번 주에 끝낸 일은", "가장 오래 걸린 일은", "일을 시작한 시각은",
                  "예정에 없던 일은"]),
        ("판단", ["잘 굴러간 자리는", "막힌 자리는", "지난주와 달라진 것은"]),
        ("다음 주", ["다음 주로 넘기는 것은", "한 가지만 바꾼다면", "미리 확인할 것은"]),
    ]
    b.append(f'<rect x="{x}" y="{y0 - 18}" width="{w}" height="{10 * rh + 36}" rx="8" '
             f'fill="#fdfdfb" stroke="{INK}" stroke-width="2"/>')
    n = 0
    for name, items in groups:
        gy0 = y0 + n * rh
        for item in items:
            y = y0 + n * rh
            b.append(t(x + 22, y + 26, str(n + 1), 13, MUTED, anchor="end"))
            b.append(t(x + 34, y + 26, item, 15, INK, anchor="start"))
            b.append(f'<line x1="{x + 34}" y1="{y + 36}" x2="{x + w - 20}" y2="{y + 36}" '
                     f'stroke="#e3e7e8"/>')
            n += 1
        gy1 = y0 + n * rh
        b.append(f'<path d="M{x - 16},{gy0 + 6} L{x - 26},{gy0 + 6} L{x - 26},{gy1 - 6} '
                 f'L{x - 16},{gy1 - 6}" fill="none" stroke="{KEEP}" stroke-width="2"/>')
        b.append(f'<text x="{x - 40}" y="{(gy0 + gy1) / 2}" text-anchor="middle" font-size="15" '
                 f'fill="{KEEP}" font-weight="700" '
                 f'transform="rotate(-90 {x - 40} {(gy0 + gy1) / 2})">{name}</text>')
    ax = x + w + 34
    b.append(f'<line x1="{ax}" y1="{y0}" x2="{ax}" y2="{y0 + 10 * rh}" stroke="{MARK}" '
             f'stroke-width="2" marker-end="url(#mark)"/>')
    b.append(f'<text x="{ax + 22}" y="{y0 + 5 * rh}" text-anchor="middle" font-size="14" '
             f'fill="{MARK}" transform="rotate(90 {ax + 22} {y0 + 5 * rh})">사실에서 판단으로</text>')
    b.append(note_box(147, 800, 500, "순서가 답의 정확도를 정한다"))
    return base("열 문장", "세 묶음으로 나눈 주간 회고 질문지", "".join(b))


# ── p29 여덟 주를 나란히 ──────────────────────────────────────────────
def fig_weeks():
    """여덟 주치 항목 출현 여부를 격자로 늘어놓는다."""
    b = []
    rows = [
        ("일감 둘 이상", [1, 0, 1, 1, 0, 0, 1, 0]),
        ("화요일 이후 시작", [0, 0, 1, 1, 0, 0, 1, 0]),
        ("마감 사흘 전 야근", [0, 0, 1, 1, 0, 0, 1, 0]),
        ("잘 굴러간 주", [0, 1, 0, 0, 1, 1, 0, 1]),
        ("넘긴 항목 그대로", [0, 0, 1, 1, 1, 0, 0, 0]),
    ]
    x0, y0, cw, rh = 300, 300, 44, 56
    for k in range(8):
        b.append(t(x0 + cw * k + cw / 2, y0 - 14, f"{k + 1}", 14, MUTED))
    b.append(t(x0 + cw * 4, y0 - 40, "주", 14, MUTED))
    for r, (label, marks) in enumerate(rows):
        y = y0 + r * rh
        b.append(t(x0 - 14, y + rh / 2 + 6, label, 15, INK, anchor="end"))
        for c, on in enumerate(marks):
            b.append(f'<rect x="{x0 + c * cw}" y="{y}" width="{cw}" height="{rh}" '
                     f'fill="{KEEP if on else "#ffffff"}" opacity="{0.85 if on else 1}" '
                     f'stroke="{LINE}" stroke-width="1"/>')
    b.append(f'<rect x="{x0 - 6}" y="{y0 + rh - 6}" width="{8 * cw + 12}" height="{2 * rh + 12}" '
             f'fill="none" stroke="{MARK}" stroke-width="2.4"/>')
    b.append(t(x0 + 8 * cw + 16, y0 + 2 * rh + 6, "늘 함께", 13, MARK, "700", anchor="start"))
    b.append(t(x0 + 8 * cw + 16, y0 + 2 * rh + 24, "나타난다", 13, MARK, "700", anchor="start"))
    b.append(note_box(147, 660, 500, "한 주만 보면 매번 다른 이유가 보인다"))
    b.append(caption(W / 2, 782, [
        "같은 질문의 답만 세로로 읽으면 그 주의 사정이 아니라 구조가 드러난다.",
    ], 16))
    return base("여덟 주를 나란히", "주별 항목 출현", "".join(b))


# ── p39 스물에서 열로 ─────────────────────────────────────────────────
def fig_trim():
    """스무 문장에서 열 문장으로 줄인 과정을 좌우 목록으로 보인다."""
    b = []
    left_groups = [("일", 4, 0), ("시간", 4, 2), ("관계", 4, 3), ("몸 상태", 4, 3), ("배운 것", 4, 2)]
    x, y0, rh = 96, 250, 22
    b.append(t(x + 110, y0 - 24, "처음 스무 문장", 16, MUTED, "700"))
    n = 0
    for name, count, dropped in left_groups:
        gy0 = y0 + n * rh
        for i in range(count):
            y = y0 + n * rh
            b.append(f'<line x1="{x + 40}" y1="{y + 18}" x2="{x + 200}" y2="{y + 18}" '
                     f'stroke="#dfe4e6" stroke-width="1.4"/>')
            b.append(f'<rect x="{x + 44}" y="{y + 6}" width="{120 if i % 2 else 140}" height="8" '
                     f'fill="{DROP if i >= count - dropped else LINE}" opacity="0.7"/>')
            if i >= count - dropped:
                b.append(f'<line x1="{x + 40}" y1="{y + 10}" x2="{x + 196}" y2="{y + 10}" '
                         f'stroke="{MARK}" stroke-width="1.6"/>')
            n += 1
        b.append(t(x + 32, (gy0 + y0 + n * rh) / 2 + 4, name, 13, MUTED, anchor="end"))
    x2 = 470
    b.append(t(x2 + 110, y0 - 24, "남은 열 문장", 16, KEEP, "700"))
    right_groups = [("사실", 4), ("판단", 3), ("다음 주", 3)]
    b.append(f'<rect x="{x2 + 30}" y="{y0 - 4}" width="180" height="{10 * rh + 8}" rx="6" '
             f'fill="none" stroke="{KEEP}" stroke-width="2.4"/>')
    m = 0
    for name, count in right_groups:
        gy0 = y0 + m * rh
        for i in range(count):
            y = y0 + m * rh
            b.append(f'<line x1="{x2 + 44}" y1="{y + 18}" x2="{x2 + 196}" y2="{y + 18}" '
                     f'stroke="#dfe4e6" stroke-width="1.4"/>')
            b.append(f'<rect x="{x2 + 48}" y="{y + 6}" width="{116 if i % 2 else 136}" height="8" '
                     f'fill="{KEEP}" opacity="0.75"/>')
            m += 1
        b.append(t(x2 + 24, (gy0 + y0 + m * rh) / 2 + 4, name, 13, KEEP, "700", anchor="end"))
    my = y0 + 5 * rh
    b.append(f'<line x1="{x + 214}" y1="{my}" x2="{x2 + 16}" y2="{my}" stroke="{MARK}" '
             f'stroke-width="2.4" marker-end="url(#mark)"/>')
    b.append(t((x + 214 + x2 + 16) / 2, my - 14, "사십 분", 14, MARK, "700"))
    b.append(t((x + 214 + x2 + 16) / 2, my + 26, "십오 분", 14, MARK, "700"))
    b.append(note_box(147, 730, 500, "다음 주에 쓰인 답만 남겼다"))
    b.append(caption(W / 2, 852, [
        "지운 열 줄은 시간·관계·몸 상태·배운 것 묶음에 몰려 있다.",
    ], 16))
    return base("스물에서 열로", "질문지를 줄인 과정", "".join(b))


FIGURES = {9: fig_recall, 13: fig_sheet, 29: fig_weeks, 39: fig_trim}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild097")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
