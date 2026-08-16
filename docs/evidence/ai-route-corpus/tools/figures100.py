#!/usr/bin/env python3
"""book-100 이미지 페이지 4개의 SVG 생성. figures091.py의 t()/base() 패턴을 따른다.

네 도표의 형식을 모두 다르게 잡았다. 두 상자 대비, 봉투와 카드, 전제 사슬, 열두 칸 원이다.
공통 약속은 둘이다. 넘어간 것은 진한 색, 넘어가지 못한 것은 옅은 색이나 끊긴 선이다.

사용: python3 figures100.py <출력디렉터리>
"""
import math
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


def envelope(x, y, w, h, color=INK, width=1.8, fill="#fdfdfb"):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="{fill}" stroke="{color}" '
            f'stroke-width="{width}"/>'
            f'<path d="M{x},{y} L{x + w / 2},{y + h * 0.55} L{x + w},{y}" fill="none" '
            f'stroke="{color}" stroke-width="{width}"/>')


# ── p7 두 사람이 아는 것 ──────────────────────────────────────────────
def fig_two_selves():
    """지금의 나와 한 달 뒤의 나가 가진 정보를 좌우로 대비한다."""
    b = []
    bw, bh, y = 250, 230, 270
    xs = [70, 474]
    rows = ["무엇을 하기로 했는지", "왜 그렇게 정했는지", "무엇을 일부러 뺐는지", "어떤 사정을 전제했는지"]
    b.append(t(xs[0] + bw / 2, y - 18, "지금의 나", 17, INK, "700"))
    b.append(t(xs[1] + bw / 2, y - 18, "한 달 뒤의 나", 17, MUTED, "700"))
    for side, x in enumerate(xs):
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" rx="12" fill="#ffffff" '
                 f'stroke="{INK if side == 0 else DROP}" stroke-width="{2 if side == 0 else 1.6}"/>')
        for i, row in enumerate(rows):
            ry = y + 44 + i * 48
            faded = side == 1 and i > 0
            b.append(t(x + bw / 2, ry, row, 14, DROP if faded else INK,
                       "400" if faded else "600"))
            if faded:
                b.append(t(x + bw / 2, ry + 20, "?", 16, MARK, "700"))
            else:
                b.append(f'<line x1="{x + 24}" y1="{ry + 12}" x2="{x + bw - 24}" y2="{ry + 12}" '
                         f'stroke="#e3e7e8"/>')
    mx0, mx1 = xs[0] + bw + 10, xs[1] - 10
    b.append(f'<line x1="{mx0}" y1="{y + 40}" x2="{mx1}" y2="{y + 40}" stroke="{INK}" '
             f'stroke-width="3" marker-end="url(#gray)"/>')
    b.append(t((mx0 + mx1) / 2, y + 26, "한 달", 15, INK, "700"))
    ex = (mx0 + mx1) / 2 - 42
    b.append(envelope(ex, y + 96, 84, 54, MARK, 2))
    b.append(t(ex + 42, y + 172, "편지", 14, MARK, "700"))
    for i in range(1, 4):
        ry = y + 44 + i * 48
        b.append(f'<line x1="{ex + 84}" y1="{y + 124}" x2="{xs[1] + 10}" y2="{ry - 4}" '
                 f'stroke="{MARK}" stroke-width="1.2" marker-end="url(#mark)"/>')
    b.append(note_box(147, 640, 500, "남기지 않으면 넘어가지 않는다"))
    b.append(caption(W / 2, 762, [
        "한 달 뒤의 나는 무엇을 하기로 했는지만 알고 나머지는 모른다.",
        "계획을 세운 사람과 실행하는 사람이 같은 사람이 아니다.",
    ], 16))
    return base("두 사람이 아는 것", "지금의 나와 한 달 뒤의 나", "".join(b))


# ── p15 편지에 넣는 것 ────────────────────────────────────────────────
def fig_envelope_cards():
    """편지에 넣는 네 항목을 카드로 늘어놓는다."""
    b = []
    b.append(envelope(96, 340, 220, 150, INK, 2.2))
    b.append(t(206, 520, "한 달 뒤의 나에게", 15, INK, "600"))
    cards = [
        ("이번 달에 할 하나", "대개 이미 적고 있다", False),
        ("그렇게 정한 이유", "다시 판단하지 않게 한다", True),
        ("하지 않기로 한 것", "잊은 것과 정한 것을 가른다", True),
        ("기대고 있는 사정", "어긋났을 때 무엇이 달라졌는지 가리킨다", True),
    ]
    cx, cw, ch, cy0, gap = 392, 300, 76, 268, 18
    for i, (title, sub, pick) in enumerate(cards):
        y = cy0 + i * (ch + gap)
        b.append(f'<rect x="{cx}" y="{y}" width="{cw}" height="{ch}" rx="10" '
                 f'fill="{"#ffffff" if pick else "#f2f4f5"}" '
                 f'stroke="{KEEP if pick else DROP}" stroke-width="{2.6 if pick else 1.4}"/>')
        b.append(t(cx + cw / 2, y + 32, title, 16, INK, "700"))
        b.append(t(cx + cw / 2, y + 56, sub, 12, MUTED))
        b.append(f'<line x1="{cx - 8}" y1="{y + ch / 2}" x2="{330}" y2="{y + ch / 2}" '
                 f'stroke="{KEEP if pick else DROP}" stroke-width="1.6" '
                 f'marker-end="url(#{"keep" if pick else "gray"})"/>')
    by0, by1 = cy0 + (ch + gap), cy0 + 3 * (ch + gap) + ch
    b.append(f'<path d="M{cx + cw + 14},{by0} L{cx + cw + 24},{by0} L{cx + cw + 24},{by1} '
             f'L{cx + cw + 14},{by1}" fill="none" stroke="{MARK}" stroke-width="2"/>')
    b.append(f'<text x="{cx + cw + 44}" y="{(by0 + by1) / 2}" text-anchor="middle" font-size="14" '
             f'fill="{MARK}" font-weight="700" '
             f'transform="rotate(90 {cx + cw + 44} {(by0 + by1) / 2})">빠지는 자리</text>')
    b.append(note_box(147, 660, 500, "한 달 뒤의 나가 모르는 것은 이 셋이다"))
    return base("편지에 넣는 것", "네 항목과 각각의 쓰임", "".join(b))


# ── p31 어디서 갈라졌나 ───────────────────────────────────────────────
def fig_assumption():
    """전제 셋과 계획과 결과를 잇고 어긋난 전제를 표시한다."""
    b = []
    ax, aw, ah = 96, 190, 66
    ys = [300, 400, 500]
    labels = ["사람 둘이 그대로", "녹음실을 쓸 수 있다", "주제가 그대로 필요하다"]
    b.append(t(ax + aw / 2, 268, "적어 둔 전제", 15, MUTED, "700"))
    px, pw, ph, py = 386, 180, 90, 378
    rx, rw, rh, ry = 630, 150, 90, 378
    b.append(f'<rect x="{px}" y="{py}" width="{pw}" height="{ph}" rx="10" fill="#eef1f2" '
             f'stroke="{INK}" stroke-width="2"/>')
    b.append(t(px + pw / 2, py + 44, "이번 달에", 16, INK, "700"))
    b.append(t(px + pw / 2, py + 66, "할 하나", 16, INK, "700"))
    b.append(f'<rect x="{rx}" y="{ry}" width="{rw}" height="{rh}" rx="10" fill="#ffffff" '
             f'stroke="{LINE}" stroke-width="1.8"/>')
    b.append(t(rx + rw / 2, ry + 40, "결과", 16, INK, "700"))
    b.append(t(rx + rw / 2, ry + 64, "절반만 됐다", 14, MUTED))
    for i, (y, label) in enumerate(zip(ys, labels)):
        broken = i == 0
        b.append(f'<rect x="{ax}" y="{y}" width="{aw}" height="{ah}" rx="8" fill="#ffffff" '
                 f'stroke="{MARK if broken else LINE}" stroke-width="{2.6 if broken else 1.4}"/>')
        words = label.split(" ")
        half = (len(words) + 1) // 2
        b.append(t(ax + aw / 2, y + 28, " ".join(words[:half]), 14, INK))
        b.append(t(ax + aw / 2, y + 48, " ".join(words[half:]), 14, INK))
        sx, sy = ax + aw, y + ah / 2
        ex, ey = px - 8, py + ph / 2
        if broken:
            midx = (sx + ex) / 2
            b.append(f'<line x1="{sx}" y1="{sy}" x2="{midx - 14}" y2="{sy + (ey - sy) * 0.35:.0f}" '
                     f'stroke="{MARK}" stroke-width="2" stroke-dasharray="6 5"/>')
            b.append(f'<line x1="{midx - 24}" y1="{sy + 6}" x2="{midx - 4}" y2="{sy + 26}" '
                     f'stroke="{MARK}" stroke-width="2.6"/>')
            b.append(f'<line x1="{midx - 4}" y1="{sy + 6}" x2="{midx - 24}" y2="{sy + 26}" '
                     f'stroke="{MARK}" stroke-width="2.6"/>')
            b.append(t(midx + 14, sy + 4, "한 사람이", 12, MARK, "700"))
            b.append(t(midx + 14, sy + 22, "빠졌다", 12, MARK, "700"))
        else:
            b.append(f'<line x1="{sx}" y1="{sy}" x2="{ex}" y2="{ey}" stroke="{LINE}" '
                     f'stroke-width="1.6" marker-end="url(#gray)"/>')
    b.append(f'<line x1="{px + pw}" y1="{py + ph / 2}" x2="{rx - 8}" y2="{ry + rh / 2}" '
             f'stroke="{LINE}" stroke-width="1.6" marker-end="url(#gray)"/>')
    b.append(note_box(147, 640, 500, "계획을 못 지킨 것이 아니다"))
    b.append(caption(W / 2, 762, [
        "전제 하나가 달라졌고 나머지 둘은 그대로였다.",
        "적어 두지 않았다면 이 자리를 짚을 수 없었다.",
    ], 16))
    return base("어디서 갈라졌나", "전제와 계획과 결과", "".join(b))


# ── p41 열두 바퀴 ─────────────────────────────────────────────────────
def fig_year_ring():
    """열두 달의 편지를 원형으로 늘어놓고 중점 항목 유형을 색으로 구분한다."""
    b = []
    cx, cy, r = W / 2, 452, 196
    months = ["삼월", "사월", "오월", "유월", "칠월", "팔월",
              "구월", "시월", "십일월", "십이월", "일월", "이월"]
    kinds = ["코너", "사람", "코너", "일정", "사람", "코너",
             "일정", "사람", "코너", "일정", "사람", "코너"]
    fills = {"코너": (KEEP, 0.85), "사람": (KEEP, 0.5), "일정": (KEEP, 0.22)}
    carry = {1, 2, 4, 7, 10}
    b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{DROP}" stroke-width="1.4"/>')
    for i, (m, kind) in enumerate(zip(months, kinds)):
        ang = math.radians(-90 + i * 30)
        x, y = cx + r * math.cos(ang), cy + r * math.sin(ang)
        color, op = fills[kind]
        b.append(f'<g opacity="{op}">' + envelope(x - 26, y - 17, 52, 34, color, 1.8, "#ffffff")
                 + '</g>')
        lx, ly = cx + (r + 52) * math.cos(ang), cy + (r + 52) * math.sin(ang)
        b.append(t(lx, ly + 4, m, 13, MUTED))
        kx, ky = cx + (r - 46) * math.cos(ang), cy + (r - 46) * math.sin(ang)
        b.append(t(kx, ky + 4, kind, 12, INK, "600"))
        if i in carry:
            mid = math.radians(-90 + (i - 0.5) * 30)
            b.append(f'<circle cx="{cx + r * math.cos(mid):.1f}" cy="{cy + r * math.sin(mid):.1f}" '
                     f'r="4.6" fill="{MARK}"/>')
    b.append(t(cx, cy - 10, "전제가 어긋난 달", 15, MUTED))
    b.append(t(cx, cy + 16, "다섯", 20, MARK, "700"))
    ly = 758
    for k, (name, (color, op)) in enumerate(fills.items()):
        b.append(f'<rect x="{200 + k * 140}" y="{ly}" width="18" height="14" fill="{color}" '
                 f'opacity="{op}"/>')
        b.append(t(200 + k * 140 + 26, ly + 13, name, 13, MUTED, anchor="start"))
    b.append(f'<circle cx="{620}" cy="{ly + 7}" r="4.6" fill="{MARK}"/>')
    b.append(t(632, ly + 13, "넘어간 자리", 13, MUTED, anchor="start"))
    b.append(note_box(147, 812, 500, "한 해는 열두 번의 조정이다"))
    return base("열두 바퀴", "한 해의 편지와 중점 항목", "".join(b))


FIGURES = {7: fig_two_selves, 15: fig_envelope_cards, 31: fig_assumption, 41: fig_year_ring}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild100")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
