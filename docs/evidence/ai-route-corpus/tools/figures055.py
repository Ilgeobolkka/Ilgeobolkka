#!/usr/bin/env python3
"""book-055 이미지 페이지 4개의 SVG 생성. figures051.py의 t()/base() 패턴을 따른다.

네 도표가 모두 '어디까지 닿고 어디서 어긋나는가'를 그린다. 닿는 자리는 진한 선, 닿지 못하는 자리는
물음표, 확인으로 되돌아가는 길은 점선으로 고정해 같은 뜻으로 쓴다.

사용: python3 figures055.py <출력디렉터리>
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
MUTED = "#55666b"
KEEP = "#3f6f66"          # 닿는 자리
DROP = "#c3ccd0"          # 닿지 못하는 자리
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


def eye(x, y, color):
    return (f'<path d="M{x - 22},{y} q22,-18 44,0 q-22,18 -44,0 z" fill="#ffffff" '
            f'stroke="{color}" stroke-width="1.8"/>'
            f'<circle cx="{x}" cy="{y}" r="6" fill="{color}"/>')


# ── p8 두 자리에서 보이는 것이 다르다 ────────────────────────────────
def fig_two_views():
    b = []
    cx, cy, rx, ry = 400, 430, 96, 168
    b.append(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="#f2f5f4" stroke="{INK}" stroke-width="2"/>')
    b.append(t(cx, cy - ry - 20, "한 사람", 17, INK, "700"))
    for k in range(5):
        yy = cy - 116 + k * 58
        b.append(f'<path d="M{cx - 62},{yy} q31,-16 62,0 q31,16 62,0" fill="none" '
                 f'stroke="{DROP}" stroke-width="2" opacity="0.9"/>')

    b.append(eye(178, cy, KEEP))
    b.append(t(178, cy - 34, "안에서", 16, INK, "700"))
    b.append(f'<path d="M204,{cy} L{cx - 30},{cy - 40}" fill="none" stroke="{KEEP}" '
             f'stroke-width="2.4" marker-end="url(#keep)"/>')
    b.append(f'<path d="M204,{cy + 14} L{cx - 40},{cy + 84}" fill="none" stroke="{KEEP}" '
             f'stroke-width="2.4" marker-end="url(#keep)"/>')
    for qx, qy in ((cx + 30, cy - 96), (cx - 6, cy + 128)):
        b.append(t(qx, qy, "?", 24, MARK, "700"))

    b.append(eye(622, cy, LINE))
    b.append(t(622, cy - 34, "밖에서", 16, INK, "700"))
    b.append(f'<line x1="596" y1="{cy}" x2="{cx + rx + 8}" y2="{cy}" stroke="{LINE}" '
             f'stroke-width="2.4" marker-end="url(#line)"/>')
    b.append(t(560, cy + 34, "표정·말·행동", 14, MUTED))
    b.append(f'<line x1="{cx + rx - 10}" y1="{cy - 20}" x2="{cx + 26}" y2="{cy - 20}" stroke="{LINE}" '
             f'stroke-width="1.8" stroke-dasharray="5 4" marker-end="url(#line)"/>')
    b.append(t(cx + 62, cy - 34, "읽어 내기", 13, LINE))

    for x, label in ((178, "틀릴 수 있다"), (622, "먼저 알아채기도 한다")):
        b.append(f'<rect x="{x - 82}" y="{cy + 70}" width="164" height="44" rx="8" fill="#ffffff" '
                 f'stroke="{LINE}" stroke-width="1.4"/>')
        b.append(t(x, cy + 98, label, 14, MUTED))

    b.append(note_box(147, 700, 500, "어느 쪽도 전부를 보지 못한다"))
    b.append(caption(W / 2, 822, [
        "안에서 닿는 방식과 밖에서 보는 방식은 서로 다른 것을 본다.",
        "안쪽에도 닿지 않는 자리가 있고 밖에서만 보이는 것도 있다.",
    ], 16))
    return base("두 자리에서 보이는 것이 다르다", "안에서 보이는 것과 밖에서 보이는 것", "".join(b))


# ── p11 짐작은 두 갈래로 간다 ────────────────────────────────────────
def fig_two_paths():
    b = []
    b.append(f'<rect x="292" y="252" width="216" height="52" rx="8" fill="#ffffff" '
             f'stroke="{INK}" stroke-width="1.8"/>')
    b.append(t(400, 284, "상대의 표정과 행동", 16, INK, "700"))
    b.append(f'<path d="M370,304 L248,368" fill="none" stroke="{LINE}" stroke-width="2" marker-end="url(#line)"/>')
    b.append(f'<path d="M430,304 L552,368" fill="none" stroke="{LINE}" stroke-width="2" marker-end="url(#line)"/>')

    # 왼쪽: 나를 본뜨기
    lx = 236
    b.append(f'<circle cx="{lx - 26}" cy="416" r="18" fill="none" stroke="{INK}" stroke-width="1.8"/>')
    b.append(f'<path d="M{lx - 48},452 q22,-22 44,0" fill="none" stroke="{INK}" stroke-width="1.8"/>')
    b.append(f'<rect x="{lx + 4}" y="392" width="46" height="60" rx="6" fill="#eef2f1" '
             f'stroke="{KEEP}" stroke-width="1.8"/>')
    b.append(t(lx + 27, 428, "나", 15, KEEP, "700"))
    b.append(t(lx, 486, "나라면 어땠을까", 16, INK, "700"))
    b.append(t(lx, 522, "가까운 사람에게 잘 맞는다", 14, MUTED))
    b.append(t(lx, 548, "다를수록 어긋난다", 14, LINE))

    # 오른쪽: 상황 읽기
    rx = 560
    for dx, dy in ((-58, -30), (0, -46), (58, -30), (-58, 26), (0, 42), (58, 26)):
        b.append(f'<rect x="{rx + dx - 13}" y="{416 + dy - 10}" width="26" height="20" rx="4" '
                 f'fill="{DROP}" opacity="0.85"/>')
    b.append(f'<circle cx="{rx}" cy="410" r="16" fill="none" stroke="{INK}" stroke-width="1.8"/>')
    b.append(f'<path d="M{rx - 20},444 q20,-20 40,0" fill="none" stroke="{INK}" stroke-width="1.8"/>')
    b.append(t(rx, 486, "이 상황에서는 대개", 16, INK, "700"))
    b.append(t(rx, 522, "처음 보는 사람에게도 쓴다", 14, MUTED))
    b.append(t(rx, 548, "그 사람만의 사정을 놓친다", 14, LINE))

    b.append(f'<line x1="180" y1="586" x2="620" y2="586" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(f'<rect x="286" y="606" width="228" height="52" rx="10" fill="#f7f4ec" '
             f'stroke="{MARK}" stroke-width="1.8"/>')
    b.append(t(400, 638, "어느 쪽이든 확인이 남는다", 16, "#7d4b21", "700"))

    b.append(note_box(147, 700, 500, "두 갈래는 서로를 대신하지 못한다"))
    b.append(caption(W / 2, 822, [
        "같은 관찰에서 두 가지 다른 방식으로 짐작이 만들어진다.",
        "두 갈래가 다른 답을 내면 그 자리는 물어볼 자리다.",
    ], 16))
    return base("짐작은 두 갈래로 간다", "자기를 본뜨는 짐작과 상황을 읽는 짐작", "".join(b))


# ── p25 어긋남은 네 단계 어디서나 생긴다 ─────────────────────────────
def fig_stages():
    b = []
    labels = ["상대의 상태", "겉으로 나온 것", "내가 본 것", "내가 내린 판정"]
    xs = [118, 268, 418, 568]
    w, y = 128, 300
    for x, label in zip(xs, labels):
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="56" rx="8" fill="#ffffff" '
                 f'stroke="{INK}" stroke-width="1.8"/>')
        b.append(t(x + w / 2, y + 33, label, 14, INK, "700"))
    leaks = ["표현이 상태와 다르다", "보는 사람의 상태가 섞인다", "이미 아는 틀로 정리한다"]
    for i, leak in enumerate(leaks):
        mx = xs[i] + w + 11
        b.append(f'<line x1="{xs[i] + w}" y1="{y + 28}" x2="{xs[i + 1]}" y2="{y + 28}" '
                 f'stroke="{LINE}" stroke-width="1.8" marker-end="url(#line)"/>')
        b.append(f'<line x1="{mx}" y1="{y + 28}" x2="{mx}" y2="{y + 82}" stroke="{DROP}" '
                 f'stroke-width="2" marker-end="url(#soft)"/>')
        b.append(t(mx, y + 108, "?", 20, DROP, "700"))
        b.append(t(mx, y + 136 + (i % 2) * 24, leak, 13, LINE))

    ry = y + 212
    b.append(f'<path d="M{xs[3] + w / 2},{y + 56} v{ry - y - 56} H{xs[0] + w / 2} V{y + 60}" '
             f'fill="none" stroke="{MARK}" stroke-width="1.8" stroke-dasharray="6 5" '
             f'marker-end="url(#mark)"/>')
    b.append(t(W / 2, ry + 26, "확인", 15, MARK, "700"))

    b.append(note_box(147, 620, 500, "되돌리는 화살표가 없으면 어긋남이 그대로 남는다", 15))
    b.append(caption(W / 2, 742, [
        "상대의 상태에서 내 판정까지는 세 번의 옮김을 거친다.",
        "옮길 때마다 새는 곳이 있어 어느 단계에서든 어긋날 수 있다.",
    ], 16))
    return base("어긋남은 네 단계 어디서나 생긴다", "관찰에서 판정까지의 네 단계", "".join(b))


# ── p36 확인에는 단계가 있다 ─────────────────────────────────────────
def fig_ladder():
    b = []
    steps = [("짐작한다", "없음"), ("짐작이라고 적는다", "아주 적음"),
             ("무엇을 보고 그랬는지 적는다", "적음"), ("어긋난 신호를 찾는다", "보통"),
             ("물어본다", "가장 큼")]
    bx, by, sw, sh = 210, 560, 250, 56
    for i, (label, cost) in enumerate(steps):
        x, y = bx + i * 22, by - i * 62
        b.append(f'<rect x="{x}" y="{y}" width="{sw}" height="{sh}" rx="8" fill="#eef2f1" '
                 f'stroke="{KEEP}" stroke-width="1.8"/>')
        b.append(t(x + sw / 2, y + 34, label, 15, INK, "700"))
        b.append(t(x + sw + 14, y + 34, cost, 13, MUTED, anchor="start"))

    b.append(f'<line x1="176" y1="{by + 40}" x2="176" y2="{by - 4 * 62 - 20}" stroke="{MARK}" '
             f'stroke-width="2" marker-end="url(#mark)"/>')
    b.append(f'<text x="152" y="{by - 100}" text-anchor="middle" font-size="14" fill="{MARK}" '
             f'font-weight="700" transform="rotate(-90 152 {by - 100})">틀릴 여지가 줄어든다</text>')

    b.append(f'<line x1="{bx + sw - 40}" y1="{by + sh + 6}" x2="{bx + sw + 40}" y2="{by + sh + 38}" '
             f'stroke="{DROP}" stroke-width="2.2" marker-end="url(#soft)"/>')
    b.append(t(bx + sw + 108, by + sh + 46, "사실처럼 굳는다", 14, LINE))

    b.append(note_box(147, 700, 500, "두 번째 계단만 올라도 굳는 일은 막는다"))
    b.append(caption(W / 2, 822, [
        "확인은 하나의 행동이 아니라 값이 다른 여러 단계다.",
        "가장 값이 큰 물어보기까지 가지 않아도 얻는 것이 있다.",
    ], 16))
    return base("확인에는 단계가 있다", "짐작에서 확인까지의 단계", "".join(b))


FIGURES = {8: fig_two_views, 11: fig_two_paths, 25: fig_stages, 36: fig_ladder}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild055")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
