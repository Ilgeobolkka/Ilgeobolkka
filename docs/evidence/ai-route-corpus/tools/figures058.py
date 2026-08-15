#!/usr/bin/env python3
"""book-058 이미지 페이지 4개의 SVG 생성. figures051.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '규칙이 일하는 구간과 그 밖'을 그린다. 규칙이 일하는 자리는 진한 채움, 무너지는 쪽은
옅은 선, 판단이 놓이는 자리는 저울 모양으로 고정해 같은 뜻으로 쓴다.

사용: python3 figures058.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 규칙이 일하는 자리
DROP = "#c3ccd0"          # 무너지는 쪽
MARK = "#b4703a"          # 표시·강조


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
  <marker id="line" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <marker id="soft" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{DROP}"/></marker>
</defs>
{t(W / 2, 122, title, 29, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


def scale(x, y, s, color):
    return (f'<line x1="{x}" y1="{y - s}" x2="{x}" y2="{y + s * 0.6:.1f}" stroke="{color}" stroke-width="2"/>'
            f'<line x1="{x - s}" y1="{y - s}" x2="{x + s}" y2="{y - s}" stroke="{color}" stroke-width="2"/>'
            f'<path d="M{x - s},{y - s} l-{s * 0.4:.1f},{s * 0.5:.1f} h{s * 0.8:.1f} z" fill="{color}"/>'
            f'<path d="M{x + s},{y - s} l-{s * 0.4:.1f},{s * 0.5:.1f} h{s * 0.8:.1f} z" fill="{color}"/>'
            f'<line x1="{x - s * 0.6:.1f}" y1="{y + s * 0.6:.1f}" x2="{x + s * 0.6:.1f}" '
            f'y2="{y + s * 0.6:.1f}" stroke="{color}" stroke-width="2"/>')


# ── p6 규칙은 판단을 앞으로 옮긴다 ───────────────────────────────────
def fig_before_after():
    b = []
    x0, step = 190, 92
    for y, tag, mode in ((320, "사람마다 달라진다", "each"), (500, "모두 같아진다", "once")):
        b.append(f'<line x1="{x0 - 40}" y1="{y}" x2="{x0 + step * 4 + 60}" y2="{y}" stroke="{LINE}" '
                 f'stroke-width="1.8" marker-end="url(#line)"/>')
        for i in range(5):
            cx = x0 + i * step
            if mode == "each":
                b.append(scale(cx, y - 20, 14, INK))
                b.append(t(cx, y + 24, "?", 17, MARK, "700"))
            else:
                if i == 0:
                    b.append(scale(cx, y - 24, 20, INK))
                else:
                    b.append(f'<rect x="{cx - 15}" y="{y - 38}" width="30" height="30" rx="4" '
                             f'fill="{KEEP}" opacity="0.75"/>')
                    b.append(f'<line x1="{cx - 7}" y1="{y - 23}" x2="{cx + 7}" y2="{y - 23}" '
                             f'stroke="#ffffff" stroke-width="2.4"/>')
        label = "매번 정한다" if mode == "each" else "한 번 정한다"
        b.append(t(x0, y + (48 if mode == "each" else 24), label, 15, INK, "700"))
        b.append(t(x0 + step * 4 + 74, y - 4, tag, 15, INK, "700", anchor="start"))
    b.append(t(W / 2, 268, "시간", 14, MUTED))
    b.append(f'<line x1="656" y1="336" x2="656" y2="470" stroke="{LINE}" stroke-width="1.8" '
             f'marker-start="url(#line)" marker-end="url(#line)"/>')
    b.append(t(646, 408, "그 자리의 사정은", 13, LINE, anchor="end"))
    b.append(t(646, 426, "보이지 않는다", 13, LINE, anchor="end"))

    b.append(note_box(147, 620, 500, "규칙은 판단을 없애지 않고 앞으로 옮긴다"))
    b.append(caption(W / 2, 742, [
        "매번 정하면 그 자리의 사정을 보지만 결과가 사람마다 달라진다.",
        "한 번 정하면 결과가 같아지는 대신 낱낱의 사정이 빠진다.",
    ], 16))
    return base("규칙은 판단을 앞으로 옮긴다", "규칙이 있고 없을 때 판단이 놓이는 자리", "".join(b))


# ── p13 예외는 어느 선을 넘으면 규칙이 된다 ──────────────────────────
def fig_exception_curve():
    b = []
    ox, oy, ow, oh = 160, 560, 480, 280
    b.append(f'<line x1="{ox}" y1="{oy}" x2="{ox + ow}" y2="{oy}" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<line x1="{ox}" y1="{oy}" x2="{ox}" y2="{oy - oh}" stroke="{INK}" stroke-width="2"/>')
    b.append(t(ox + ow, oy + 26, "예외로 처리한 비율", 14, MUTED, anchor="end"))
    b.append(f'<text x="{ox - 32}" y="{oy - oh / 2}" text-anchor="middle" font-size="14" '
             f'fill="{MUTED}" transform="rotate(-90 {ox - 32} {oy - oh / 2})">규칙을 기대하는 정도</text>')

    tx = ox + 200
    path = (f"M{ox + 8},{oy - 236} C{ox + 90},{oy - 232} {tx - 40},{oy - 226} {tx},{oy - 190} "
            f"C{tx + 60},{oy - 130} {tx + 90},{oy - 56} {tx + 150},{oy - 42} "
            f"C{tx + 210},{oy - 34} {ox + ow - 20},{oy - 34} {ox + ow - 8},{oy - 32}")
    b.append(f'<path d="{path}" fill="none" stroke="{MARK}" stroke-width="3.2"/>')
    b.append(f'<line x1="{tx}" y1="{oy}" x2="{tx}" y2="{oy - 200}" stroke="{LINE}" '
             f'stroke-width="1.4" stroke-dasharray="5 5"/>')
    b.append(t(tx, oy + 26, "기대가 뒤집히는 자리", 13, LINE))
    b.append(t((ox + tx) / 2 + 6, oy - 252, "예외가 규칙을 지킬 만하게 한다", 13, KEEP, "700"))
    b.append(t((tx + ox + ow) / 2 + 10, oy - 92, "규칙이 아니라 예외를 기대한다", 13, LINE))

    b.append(f'<rect x="{ox + 2}" y="{oy + 44}" width="98" height="28" rx="6" fill="#ffffff" '
             f'stroke="{DROP}" stroke-width="1.6"/>')
    b.append(t(ox + 51, oy + 63, "예외 없음", 13, LINE))
    b.append(t(ox + 51, oy + 92, "몰래 어긴다", 12, LINE))

    b.append(note_box(147, 660, 500, "양끝이 모두 규칙을 잃는다"))
    b.append(caption(W / 2, 782, [
        "예외가 어느 선까지는 규칙을 지킬 만하게 만든다.",
        "그 선을 넘으면 사람들이 규칙이 아니라 예외를 기대한다.",
    ], 16))
    return base("예외는 어느 선을 넘으면 규칙이 된다", "예외가 늘면서 규칙이 힘을 잃는 과정",
                "".join(b))


# ── p27 두 기준은 대개 같은 답을 낸다 ────────────────────────────────
def fig_two_criteria():
    b = []
    cy, r = 400, 158
    lx, rx = 320, 474
    b.append(f'<clipPath id="cl"><circle cx="{lx}" cy="{cy}" r="{r}"/></clipPath>')
    b.append(f'<circle cx="{lx}" cy="{cy}" r="{r}" fill="#f2f5f4" stroke="{LINE}" stroke-width="1.8"/>')
    b.append(f'<circle cx="{rx}" cy="{cy}" r="{r}" fill="#f2f5f4" stroke="{LINE}" stroke-width="1.8"/>')
    b.append(f'<circle cx="{rx}" cy="{cy}" r="{r}" fill="{KEEP}" opacity="0.32" clip-path="url(#cl)"/>')
    b.append(t(lx - 58, cy - r - 20, "결과로 정하기", 17, INK, "700"))
    b.append(t(rx + 58, cy - r - 20, "원칙으로 정하기", 17, INK, "700"))
    b.append(t((lx + rx) / 2, cy - 4, "같은 답", 26, "#2c4f48", "700"))
    b.append(t((lx + rx) / 2, cy + 26, "거의 모든 자리", 14, "#2c4f48"))
    b.append(t(lx - 96, cy - 16, "원칙을 따르면", 13, LINE))
    b.append(t(lx - 96, cy + 4, "크게 나쁜 결과", 13, LINE))
    b.append(t(rx + 96, cy - 16, "결과를 따르면", 13, LINE))
    b.append(t(rx + 96, cy + 4, "매번 다시 잼", 13, LINE))

    b.append(f'<line x1="180" y1="608" x2="614" y2="608" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(f'<rect x="266" y="626" width="262" height="52" rx="10" fill="#f7f4ec" '
             f'stroke="{MARK}" stroke-width="1.8"/>')
    b.append(t(397, 658, "갈리는 자리에서만 고르면 된다", 16, "#7d4b21", "700"))

    b.append(note_box(147, 706, 500, "다투는 일은 대개 겹친 자리에서 일어난다"))
    b.append(caption(W / 2, 828, [
        "결과로 정하든 원칙으로 정하든 거의 모든 자리에서 답이 같다.",
        "갈리는 자리는 좁고, 그 자리를 미리 종류로 적어 두면 된다.",
    ], 16))
    return base("두 기준은 대개 같은 답을 낸다", "결과와 원칙이 다른 답을 내는 자리", "".join(b))


# ── p36 굳어도 무너지고 풀려도 무너진다 ──────────────────────────────
def fig_two_collapses():
    b = []
    ax, ay, aw = 140, 320, 514
    b.append(f'<line x1="{ax}" y1="{ay}" x2="{ax + aw}" y2="{ay}" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<rect x="{ax + 158}" y="{ay - 9}" width="198" height="18" rx="9" '
             f'fill="{KEEP}" opacity="0.32" stroke="{KEEP}" stroke-width="1.4"/>')
    b.append(t(ax + 257, ay - 24, "규칙이 일하는 구간", 15, "#2c4f48", "700"))
    b.append(t(ax, ay - 24, "예외를 하나도", 14, INK, "700", anchor="start"))
    b.append(t(ax, ay - 6, "두지 않음", 14, INK, "700", anchor="start"))
    b.append(t(ax + aw, ay - 24, "거의 다 예외로", 14, INK, "700", anchor="end"))
    b.append(t(ax + aw, ay - 6, "처리함", 14, INK, "700", anchor="end"))

    for x, title, sub in ((ax + 46, "형식만 남는다", "몰래 어기고 우회한다"),
                          (ax + aw - 46, "예외를 기대한다", "지키는 사람만 손해 본다")):
        b.append(f'<line x1="{x}" y1="{ay + 12}" x2="{x}" y2="{ay + 46}" stroke="{DROP}" '
                 f'stroke-width="2.2" marker-end="url(#soft)"/>')
        b.append(f'<rect x="{x - 106}" y="{ay + 50}" width="212" height="60" rx="10" fill="#ffffff" '
                 f'stroke="{DROP}" stroke-width="1.8"/>')
        b.append(t(x, ay + 76, title, 16, INK, "700"))
        b.append(t(x, ay + 98, sub, 12, LINE))
        b.append(f'<line x1="{x}" y1="{ay + 110}" x2="{ax + 257 + (28 if x < ax + 257 else -28)}" '
                 f'y2="{ay + 168}" stroke="{DROP}" stroke-width="2.2" marker-end="url(#soft)"/>')

    b.append(f'<rect x="{ax + 257 - 138}" y="{ay + 172}" width="276" height="58" rx="10" '
             f'fill="#f4f6f5" stroke="{LINE}" stroke-width="1.8"/>')
    b.append(t(ax + 257, ay + 208, "규칙이 없는 것과 같아진다", 17, INK, "700"))

    b.append(note_box(147, 640, 500, "두 방향은 반대인데 도착지가 같다"))
    b.append(caption(W / 2, 762, [
        "예외를 하나도 두지 않으면 형식만 남고 우회가 늘어난다.",
        "거의 다 예외로 처리하면 지키는 사람만 손해를 본다.",
    ], 16))
    return base("굳어도 무너지고 풀려도 무너진다", "규칙이 양쪽 방향으로 무너지는 모습", "".join(b))


FIGURES = {6: fig_before_after, 13: fig_exception_curve, 27: fig_two_criteria,
           36: fig_two_collapses}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild058")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
