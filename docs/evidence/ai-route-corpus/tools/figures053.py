#!/usr/bin/env python3
"""book-053 이미지 페이지 4개의 SVG 생성. figures051.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '무엇이 이어지고 무엇이 끊기는가'를 그린다. 이어진 자리는 실선, 끊긴 자리는 빈칸,
지금의 이해로 채워 넣은 자리는 옅은 색과 물음표로 고정해 같은 뜻으로 쓴다.

사용: python3 figures053.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 이어진 자리
DROP = "#c3ccd0"          # 끊기거나 흐려진 자리
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
</defs>
{t(W / 2, 122, title, 30, '#203238', '700')}
{t(W / 2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, label, size=17, h=62):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


# ── p8 세 자는 대개 같은 답을 내지만 ──────────────────────────────────
def fig_rulers():
    b = []
    x0, x1, mid = 196, 660, 428
    rows = [(300, "몸"), (376, "기억"), (452, "성향")]
    for y, label in rows:
        b.append(t(x0 - 16, y + 6, label, 16, INK, "700", anchor="end"))
        b.append(f'<rect x="{x0}" y="{y - 11}" width="{mid - x0}" height="22" rx="5" fill="{KEEP}" opacity="0.85"/>')
    # 몸: 그대로 이어짐
    b.append(f'<rect x="{mid}" y="{rows[0][0] - 11}" width="{x1 - mid}" height="22" rx="5" '
             f'fill="{KEEP}" opacity="0.85"/>')
    # 기억: 중간이 끊김
    b.append(f'<rect x="{mid}" y="{rows[1][0] - 11}" width="60" height="22" rx="5" fill="{KEEP}" opacity="0.85"/>')
    b.append(f'<rect x="{mid + 152}" y="{rows[1][0] - 11}" width="{x1 - mid - 152}" height="22" rx="5" '
             f'fill="{KEEP}" opacity="0.85"/>')
    b.append(f'<rect x="{mid + 60}" y="{rows[1][0] - 11}" width="92" height="22" rx="5" fill="none" '
             f'stroke="{DROP}" stroke-width="1.6" stroke-dasharray="5 4"/>')
    # 성향: 점점 옅어짐
    for k in range(10):
        seg = (x1 - mid) / 10
        b.append(f'<rect x="{mid + k * seg}" y="{rows[2][0] - 11}" width="{seg + 1}" height="22" '
                 f'fill="{KEEP}" opacity="{0.85 - k * 0.085:.2f}"/>')

    b.append(f'<line x1="{mid}" y1="270" x2="{mid}" y2="486" stroke="{LINE}" stroke-width="1.4" '
             f'stroke-dasharray="5 5"/>')
    b.append(t((x0 + mid) / 2, 512, "세 자가 같은 답을 낸다", 15, MUTED))
    b.append(f'<line x1="{x1}" y1="270" x2="{x1}" y2="486" stroke="{LINE}" stroke-width="1.4" '
             f'stroke-dasharray="5 5"/>')
    b.append(t(x1, 512, "같은 사람인가", 15, INK, "700"))

    bx, by = 268, 548
    for i, (label, ans) in enumerate((("몸", "그렇다"), ("기억", "어디까지"), ("성향", "아니다"))):
        b.append(f'<rect x="{bx + i * 88}" y="{by}" width="84" height="52" rx="8" fill="#ffffff" '
                 f'stroke="{LINE}" stroke-width="1.6"/>')
        b.append(t(bx + i * 88 + 42, by + 22, label, 14, MUTED))
        b.append(t(bx + i * 88 + 42, by + 42, ans, 15, INK, "700"))

    b.append(note_box(147, 668, 500, "평소에 갈리지 않아서 고르고 있다는 것을 모른다"))
    b.append(caption(W / 2, 790, [
        "몸·기억·성향은 대개 함께 이어져 같은 답을 낸다.",
        "갈리는 자리에서 어느 자를 쓰느냐가 곧 판정이 된다.",
    ], 16))
    return base("세 자는 대개 같은 답을 내지만", "몸·기억·성향이 갈리는 자리", "".join(b))


# ── p13 꺼낼 때마다 다시 놓인다 ───────────────────────────────────────
def fig_recall():
    b = []
    frag = [(0, 0), (26, -18), (52, 6), (14, 24), (44, 30), (66, -12)]
    b.append(t(160, 268, "남은 조각", 16, INK, "700"))
    for dx, dy in frag:
        b.append(f'<rect x="{128 + dx}" y="{312 + dy}" width="15" height="15" rx="3" fill="{KEEP}" opacity="0.8"/>')

    stages = [(300, "처음 떠올림", 0), (452, "몇 해 뒤", 1), (604, "남에게 말한 뒤", 2)]
    for cx, label, extra in stages:
        b.append(t(cx, 268, label, 15, MUTED))
        pts = []
        for i, (dx, dy) in enumerate(frag):
            px, py = cx - 42 + dx, 312 + dy + (extra * 4 if i % 2 else -extra * 3)
            pts.append((px + 7, py + 7))
            b.append(f'<rect x="{px}" y="{py}" width="15" height="15" rx="3" fill="{KEEP}" opacity="0.8"/>')
        for k in range(extra):
            fx, fy = cx - 30 + k * 28, 368
            b.append(f'<rect x="{fx}" y="{fy}" width="15" height="15" rx="3" fill="{MARK}" opacity="0.28"/>')
            b.append(t(fx + 7, fy + 12, "?", 12, MARK, "700"))
        path = "M" + " L".join(f"{x:.0f},{y:.0f}" for x, y in pts)
        b.append(f'<path d="{path}" fill="none" stroke="{LINE}" stroke-width="1.6" opacity="0.9"/>')
        wave = ("M" + f"{cx - 52},420 " + " ".join(
            f"L{cx - 52 + i * 21},{420 + (10 if (i + extra) % 2 else -10)}" for i in range(1, 6)))
        b.append(f'<path d="{wave}" fill="none" stroke="{KEEP}" stroke-width="2"/>')

    b.append(f'<line x1="150" y1="474" x2="676" y2="474" stroke="{LINE}" stroke-width="1.8" '
             f'marker-end="url(#line)"/>')
    b.append(t(W / 2, 500, "자신은 커지고 정확함은 아니다", 15, MARK, "700"))

    b.append(note_box(147, 590, 500, "조각은 남고 잇는 방식이 바뀐다"))
    b.append(caption(W / 2, 712, [
        "기억은 창고에서 꺼내지는 것이 아니라 그때마다 다시 놓인다.",
        "빠진 자리는 그럴듯한 것으로 채워지고 채운 표시는 남지 않는다.",
    ], 16))
    return base("꺼낼 때마다 다시 놓인다", "같은 조각이 세 번 다르게 이어지는 모습", "".join(b))


# ── p25 둘 다 그 사람일 수는 없다 ─────────────────────────────────────
def fig_branch():
    b = []
    top, ty = 400, 286
    b.append(f'<circle cx="{top}" cy="{ty}" r="46" fill="#eef2f1" stroke="{INK}" stroke-width="2"/>')
    b.append(t(top, ty + 6, "그때의 나", 15, INK, "700"))
    b.append(f'<line x1="{top}" y1="{ty + 46}" x2="{top}" y2="366" stroke="{LINE}" stroke-width="2"/>')
    b.append(f'<path d="M{top},366 L262,412" fill="none" stroke="{LINE}" stroke-width="2" marker-end="url(#line)"/>')
    b.append(f'<path d="M{top},366 L538,412" fill="none" stroke="{LINE}" stroke-width="2" marker-end="url(#line)"/>')
    b.append(t(210, 352, "갈라지지 않았다면 물음이 없다", 14, LINE, anchor="middle"))

    marks = [(-22, -16), (0, -22), (22, -14), (-18, 8), (4, 4), (24, 14)]
    for cx, label in ((250, "왼쪽"), (550, "오른쪽")):
        b.append(f'<circle cx="{cx}" cy="470" r="52" fill="#ffffff" stroke="{INK}" stroke-width="2"/>')
        for dx, dy in marks:
            b.append(f'<rect x="{cx + dx}" y="{470 + dy}" width="11" height="11" rx="2" fill="{KEEP}" opacity="0.8"/>')
        b.append(t(cx, 552, label, 16, INK, "700"))
        b.append(t(cx, 596, "?", 30, MARK, "700"))
    b.append(f'<line x1="308" y1="470" x2="492" y2="470" stroke="{MARK}" stroke-width="1.8" '
             f'marker-start="url(#mark)" marker-end="url(#mark)"/>')
    b.append(t(400, 458, "서로 같지는 않다", 14, MARK, "700"))
    b.append(f'<line x1="196" y1="620" x2="604" y2="620" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(t(400, 646, "한쪽만 고를 근거가 없다", 16, INK, "700"))

    b.append(note_box(147, 690, 500, "같은 기억이 둘이면 기억은 자가 되지 못한다"))
    b.append(caption(W / 2, 812, [
        "두 사람은 서로 다르므로 둘 다 그 사람일 수는 없다.",
        "기억이 같으면 기억이라는 자로는 한쪽을 고를 수 없다.",
    ], 16))
    return base("둘 다 그 사람일 수는 없다", "하나의 기억이 둘로 갈라졌을 때의 판정", "".join(b))


# ── p36 한 줄이 끊겨도 남는 줄이 있다 ────────────────────────────────
def fig_threads():
    b = []
    x0, x1 = 210, 604
    gap0, gap1 = 348, 452
    rows = [(300, "기억", True), (356, "몸", False), (412, "이름과 기록", False), (468, "관계", False)]
    for y, label, broken in rows:
        b.append(t(x0 - 16, y + 5, label, 15, INK, "700", anchor="end"))
        if broken:
            b.append(f'<line x1="{x0}" y1="{y}" x2="{gap0}" y2="{y}" stroke="{KEEP}" stroke-width="4"/>')
            b.append(f'<line x1="{gap1}" y1="{y}" x2="{x1}" y2="{y}" stroke="{KEEP}" stroke-width="4"/>')
        else:
            b.append(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="{KEEP}" stroke-width="4"/>')
            b.append(f'<circle cx="{(gap0 + gap1) / 2}" cy="{y}" r="6" fill="#ffffff" '
                     f'stroke="{MARK}" stroke-width="2.4"/>')
    for x in (gap0, gap1):
        b.append(f'<line x1="{x}" y1="276" x2="{x}" y2="504" stroke="{LINE}" stroke-width="1.4" '
                 f'stroke-dasharray="5 5"/>')
    b.append(t((gap0 + gap1) / 2, 266, "여기서 무엇이 남는가", 15, MARK, "700"))

    b.append(f'<path d="M{x1 + 14},296 q14,0 14,14 v58 q0,14 14,14 q-14,0 -14,14 v58 q0,14 -14,14" '
             f'fill="none" stroke="{LINE}" stroke-width="1.8"/>')
    b.append(t(x1 + 44, 380, "같은 사람", 16, INK, "700", anchor="start"))
    b.append(t(x1 + 44, 404, "기억 줄만 보면 아니다", 12, LINE, anchor="start"))

    b.append(note_box(147, 600, 500, "자를 하나만 쓰면 끊긴 자리에서 답이 하나로 나온다"))
    b.append(caption(W / 2, 722, [
        "기억이 끊긴 구간에서도 몸과 이름과 관계는 이어져 있다.",
        "끊긴 자리에서 어느 줄을 보느냐가 판정을 가른다.",
    ], 16))
    return base("한 줄이 끊겨도 남는 줄이 있다", "기억이 끊긴 자리를 다른 줄이 잇는 모습", "".join(b))


FIGURES = {8: fig_rulers, 13: fig_recall, 25: fig_branch, 36: fig_threads}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild053")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
