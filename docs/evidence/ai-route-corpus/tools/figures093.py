#!/usr/bin/env python3
"""book-093 이미지 페이지 4개의 SVG 생성. figures091.py의 t()/base() 패턴을 따른다.

네 도표의 형식을 모두 다르게 잡았다. 가로 막대 비교, 화면 목업 대비, 평면도, 기록지다.
공통 약속은 둘이다. 일한 시간과 남긴 자리는 진한 색, 잃은 시간과 치운 것은 옅은 색이다.

사용: python3 figures093.py <출력디렉터리>
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


def box(x, y, w, h, label="", fill="#ffffff", stroke=INK, size=15, width=1.8):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="{fill}" stroke="{stroke}" '
            f'stroke-width="{width}"/>' + (t(x + w / 2, y + h / 2 + 5, label, size, INK) if label else ""))


# ── p8 같은 세 시간, 다른 결과 ────────────────────────────────────────
def fig_bars():
    """방해 횟수만 다른 세 오전을 같은 길이의 막대로 비교한다."""
    b = []
    x0, w, h = 210, 400, 58
    rows = [("방해 없음", 0, "세 시간"), ("네 번", 4, "두 시간 이십 분"), ("열두 번", 12, "한 시간 십 분")]
    b.append(t(x0 + w / 2, 262, "오전 세 시간", 15, MUTED))
    for i, (label, breaks, total) in enumerate(rows):
        y = 290 + i * 110
        b.append(t(x0 - 18, y + h / 2 + 6, label, 16, INK, "600", anchor="end"))
        b.append(f'<rect x="{x0}" y="{y}" width="{w}" height="{h}" fill="{KEEP}" opacity="0.85"/>')
        if breaks:
            gap = w / (breaks + 1)
            bw = 8 if breaks > 6 else 14
            for k in range(1, breaks + 1):
                bx = x0 + gap * k - bw / 2
                b.append(f'<rect x="{bx:.1f}" y="{y}" width="{bw}" height="{h}" fill="{DROP}"/>')
        b.append(f'<rect x="{x0}" y="{y}" width="{w}" height="{h}" fill="none" stroke="{INK}" '
                 f'stroke-width="1.4"/>')
        b.append(t(x0 + w + 16, y + h / 2 + 6, total, 15, MARK, "700", anchor="start"))
    ly = 640
    b.append(f'<rect x="{x0}" y="{ly}" width="20" height="16" fill="{KEEP}" opacity="0.85"/>')
    b.append(t(x0 + 28, ly + 14, "일한 시간", 14, MUTED, anchor="start"))
    b.append(f'<rect x="{x0 + 150}" y="{ly}" width="20" height="16" fill="{DROP}"/>')
    b.append(t(x0 + 178, ly + 14, "방해와 다시 붙는 시간", 14, MUTED, anchor="start"))
    b.append(note_box(147, 700, 500, "옅은 칸은 방해받은 시간보다 길다"))
    b.append(caption(W / 2, 822, [
        "세 막대의 전체 길이는 같지만 진한 구간의 합은 크게 다르다.",
        "방해가 잦아질수록 이어서 일한 시간이 빠르게 줄어든다.",
    ], 16))
    return base("같은 세 시간, 다른 결과", "방해 횟수에 따른 실제 작업 시간", "".join(b))


# ── p16 화면에 남은 것 ────────────────────────────────────────────────
def fig_screens():
    """정리 전후의 화면을 좌우로 대비한다."""
    b = []
    sw, sh, y = 300, 210, 300
    xs = [70, 424]
    b.append(t(xs[0] + sw / 2, y - 20, "정리 전", 17, MUTED, "700"))
    b.append(t(xs[1] + sw / 2, y - 20, "정리 후", 17, KEEP, "700"))
    # 정리 전
    x = xs[0]
    b.append(box(x, y, sw, sh, "", "#fbfbfc", LINE, 15, 1.8))
    for k in range(8):
        b.append(f'<rect x="{x + 6 + k * 36}" y="{y + 6}" width="34" height="16" fill="#e6eaec" '
                 f'stroke="{LINE}" stroke-width="0.8"/>')
    for k, (dx, dy) in enumerate(((28, 46), (52, 68), (76, 90), (100, 112))):
        b.append(f'<rect x="{x + dx}" y="{y + dy}" width="170" height="86" fill="#ffffff" '
                 f'stroke="{LINE}" stroke-width="1.2"/>')
    for k in range(6):
        b.append(f'<rect x="{x + 8}" y="{y + 44 + k * 26}" width="14" height="14" fill="#dfe4e6"/>')
    b.append(f'<rect x="{x + sw - 96}" y="{y + sh - 52}" width="88" height="40" rx="6" fill="#f7f4ec" '
             f'stroke="{MARK}" stroke-width="1.4"/>')
    b.append(t(x + sw - 52, y + sh - 27, "알림", 13, MARK))
    b.append(t(x + sw / 2, y + sh + 30, "눈에 보이는 것 열아홉", 16, MUTED))
    # 정리 후
    x = xs[1]
    b.append(box(x, y, sw, sh, "", "#fbfbfc", KEEP, 15, 2.6))
    for k in range(2):
        b.append(f'<rect x="{x + 6 + k * 36}" y="{y + 6}" width="34" height="16" fill="#e6eaec" '
                 f'stroke="{LINE}" stroke-width="0.8"/>')
    b.append(f'<rect x="{x + 14}" y="{y + 34}" width="{sw - 28}" height="{sh - 52}" fill="#ffffff" '
             f'stroke="{INK}" stroke-width="1.4"/>')
    for k in range(4):
        b.append(f'<line x1="{x + 30}" y1="{y + 58 + k * 18}" x2="{x + sw - 60}" y2="{y + 58 + k * 18}" '
                 f'stroke="#dfe4e6" stroke-width="2"/>')
    b.append(f'<line x1="{x + 40}" y1="{y + 150}" x2="{x + 40}" y2="{y + 170}" stroke="{MARK}" '
             f'stroke-width="1.4" marker-end="url(#mark)"/>')
    b.append(t(x + 92, y + 176, "빈칸", 14, MARK, "700"))
    b.append(t(x + sw / 2, y + sh + 30, "눈에 보이는 것 셋", 16, KEEP, "700"))
    b.append(note_box(147, 620, 500, "보이지 않으면 눌리지 않는다"))
    b.append(caption(W / 2, 742, [
        "탭과 창과 아이콘은 그 자체로 아직 끝나지 않은 일을 가리킨다.",
        "닫으면 지금 하는 일과 나중에 할 일이 구분된다.",
    ], 16))
    return base("화면에 남은 것", "정리 전과 후의 화면", "".join(b))


# ── p27 같은 책상, 두 자리 ────────────────────────────────────────────
def fig_desk():
    """한 책상을 화면 자리와 노트 자리로 나눈 배치를 위에서 본 평면도."""
    b = []
    dx, dy, dw, dh = 150, 300, 500, 260
    b.append(f'<rect x="{dx}" y="{dy}" width="{dw}" height="{dh}" rx="10" fill="#faf9f6" '
             f'stroke="{INK}" stroke-width="2.4"/>')
    split = dx + 330
    b.append(f'<line x1="{split}" y1="{dy}" x2="{split}" y2="{dy + dh}" stroke="{LINE}" '
             f'stroke-width="1.6" stroke-dasharray="6 5"/>')
    b.append(f'<rect x="{dx + 70}" y="{dy + 24}" width="190" height="34" fill="#e6eaec" '
             f'stroke="{INK}" stroke-width="1.4"/>')
    b.append(t(dx + 165, dy + 47, "화면", 14, INK))
    b.append(f'<rect x="{dx + 90}" y="{dy + 74}" width="150" height="30" fill="#ffffff" '
             f'stroke="{INK}" stroke-width="1.4"/>')
    b.append(t(dx + 165, dy + 95, "자판", 13, MUTED))
    b.append(f'<rect x="{dx + 40}" y="{dy + 130}" width="250" height="100" fill="#ffffff" '
             f'stroke="{DROP}" stroke-width="1.4" stroke-dasharray="7 6"/>')
    b.append(t(dx + 165, dy + 186, "빈칸", 16, DROP, "700"))
    b.append(t(dx + 165, dy + dh + 30, "화면 자리", 16, INK, "600"))
    b.append(f'<rect x="{split + 34}" y="{dy + 60}" width="106" height="80" fill="#ffffff" '
             f'stroke="{INK}" stroke-width="1.4"/>')
    b.append(t(split + 87, dy + 105, "노트", 14, INK))
    b.append(f'<circle cx="{split + 87}" cy="{dy + 176}" r="18" fill="#ffffff" stroke="{INK}" '
             f'stroke-width="1.4"/>')
    b.append(t(split + 87, dy + 181, "물", 12, MUTED))
    b.append(t(split + 87, dy + dh + 30, "노트 자리", 16, INK, "600"))
    b.append(f'<rect x="{dx + 40}" y="{dy + dh + 56}" width="90" height="52" rx="8" fill="#f2f4f5" '
             f'stroke="{LINE}" stroke-width="1.4"/>')
    b.append(f'<rect x="{dx + 62}" y="{dy + dh + 70}" width="24" height="30" rx="4" fill="#ffffff" '
             f'stroke="{LINE}" stroke-width="1.2"/>')
    b.append(t(dx + 85, dy + dh + 126, "발밑", 14, MUTED))
    b.append(f'<line x1="{dx - 12}" y1="{dy + 60}" x2="{dx - 70}" y2="{dy + 60}" stroke="{LINE}" '
             f'stroke-width="1.6" marker-end="url(#gray)"/>')
    b.append(t(dx - 76, dy + 44, "나머지는", 13, MUTED, anchor="end"))
    b.append(t(dx - 76, dy + 64, "뒤 선반으로", 13, MUTED, anchor="end"))
    b.append(note_box(147, 700, 500, "앉는 자리가 무엇을 할지 알려 준다"))
    b.append(caption(W / 2, 822, [
        "사무실이 넓지 않아도 자리는 나눌 수 있다.",
        "몸이 알아차릴 만큼만 다르면 그 자리가 신호가 된다.",
    ], 16))
    return base("같은 책상, 두 자리", "위에서 내려다본 배치", "".join(b))


# ── p36 세어 본 오전 ──────────────────────────────────────────────────
def fig_tally():
    """삼십 분 단위로 방해를 표시한 기록지."""
    b = []
    x0, y0, cw, rw, rh = 220, 280, 120, 300, 56
    # 1은 밖에서 온 방해, 0은 스스로 옮긴 것. 본문이 적은 대로 행별 여섯·하나·다섯·넷·둘·하나이고
    # 진한 것 여섯, 옅은 것 아홉이 되게 맞췄다.
    rows = [("아홉 시", [1, 0]), ("아홉 시 반", [0]), ("열 시", [1, 0, 0, 1, 0]),
            ("열 시 반", [0, 1, 0, 0]), ("열한 시", [1, 0]), ("열한 시 반", [1])]
    b.append(f'<rect x="{x0}" y="{y0 - 34}" width="{cw + rw}" height="34" fill="#eef1f2" '
             f'stroke="{LINE}" stroke-width="1.2"/>')
    b.append(t(x0 + cw / 2, y0 - 12, "시각", 15, INK, "600"))
    b.append(t(x0 + cw + rw / 2, y0 - 12, "표시", 15, INK, "600"))
    for i, (label, marks) in enumerate(rows):
        y = y0 + i * rh
        b.append(f'<rect x="{x0}" y="{y}" width="{cw}" height="{rh}" fill="#ffffff" stroke="{LINE}" '
                 f'stroke-width="1.2"/>')
        b.append(f'<rect x="{x0 + cw}" y="{y}" width="{rw}" height="{rh}" fill="#ffffff" '
                 f'stroke="{LINE}" stroke-width="1.2"/>')
        b.append(t(x0 + cw / 2, y + rh / 2 + 6, label, 14, MUTED))
        for k, outer in enumerate(marks):
            mx = x0 + cw + 26 + k * 26
            color = INK if outer else DROP
            b.append(f'<line x1="{mx}" y1="{y + 14}" x2="{mx}" y2="{y + rh - 14}" stroke="{color}" '
                     f'stroke-width="5" stroke-linecap="round"/>')
    bx = x0 + cw + rw + 14
    b.append(f'<path d="M{bx},{y0 + 2 * rh + 6} L{bx + 12},{y0 + 2 * rh + 6} L{bx + 12},'
             f'{y0 + 4 * rh - 6} L{bx},{y0 + 4 * rh - 6}" fill="none" stroke="{MARK}" '
             f'stroke-width="1.8"/>')
    b.append(t(bx + 20, y0 + 3 * rh - 8, "메신저를", 13, MARK, anchor="start"))
    b.append(t(bx + 20, y0 + 3 * rh + 10, "켜 둔 구간", 13, MARK, anchor="start"))
    ly = y0 + 6 * rh + 26
    b.append(f'<line x1="{x0 + 30}" y1="{ly}" x2="{x0 + 30}" y2="{ly + 18}" stroke="{INK}" '
             f'stroke-width="5" stroke-linecap="round"/>')
    b.append(t(x0 + 46, ly + 15, "밖에서 온 방해 여섯", 14, MUTED, anchor="start"))
    b.append(f'<line x1="{x0 + 230}" y1="{ly}" x2="{x0 + 230}" y2="{ly + 18}" stroke="{DROP}" '
             f'stroke-width="5" stroke-linecap="round"/>')
    b.append(t(x0 + 246, ly + 15, "스스로 옮긴 것 아홉", 14, MUTED, anchor="start"))
    b.append(note_box(147, 730, 500, "세기 전에는 많다는 느낌만 있었다"))
    b.append(caption(W / 2, 852, [
        "짐작은 대여섯 번이었고 실제로는 열다섯 번이었다.",
    ], 16))
    return base("세어 본 오전", "삼십 분마다 표시한 방해", "".join(b))


FIGURES = {8: fig_bars, 16: fig_screens, 27: fig_desk, 36: fig_tally}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild093")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
