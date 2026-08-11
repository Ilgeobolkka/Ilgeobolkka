#!/usr/bin/env python3
"""book-064 이미지 페이지 6개의 SVG 생성. figures061.py의 t()/base() 패턴을 따른다."""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#27353a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
TAN = "#a8763f"
TAN_SOFT = "#e8d6bf"
MUTED = "#55666b"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="tan" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{TAN}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
</defs>
{t(W/2, 122, title, 32, '#203238', '700')}
{t(W/2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, h, label, size=17):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f7f4ec" stroke="#c5a866"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#51462c", "700"))


def caption(x, y, lines, size=15, color=MUTED):
    return "".join(t(x, y + i * 25, line, size, color) for i, line in enumerate(lines))


def bar(x, y, w, ratio, label):
    """0~1 비율 막대."""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="12" rx="6" fill="{SOFT}"/>'
            f'<rect x="{x}" y="{y}" width="{w * ratio:.1f}" height="12" rx="6" fill="{TAN}"/>'
            + t(x, y - 8, label, 12, "#7a878b", anchor="start"))


def fig_material():
    """p6 — 두께와 결에 따른 종이의 성질."""
    b = []
    x0, y0 = 150, 268
    label_w, col_w, head_h, row_h = 126, 194, 56, 176
    for i, col in enumerate(["결 방향", "결 가로지름"]):
        cx = x0 + label_w + col_w * i
        b.append(f'<rect x="{cx}" y="{y0}" width="{col_w}" height="{head_h}" fill="#f2f5f6"/>')
        b.append(t(cx + col_w / 2, y0 + 35, col, 18, INK, "700"))
    rows = [("얇은 종이", [(0.9, 0.25, "쉽게 접히고 자리도 깨끗"), (0.6, 0.3, "접히지만 자리가 거칢")]),
            ("두꺼운 종이", [(0.45, 0.75, "힘이 들지만 잘 버팀"), (0.15, 0.9, "가장 접기 어렵고 가장 단단")])]
    for r, (name, cells) in enumerate(rows):
        ry = y0 + head_h + row_h * r
        b.append(f'<rect x="{x0}" y="{ry}" width="{label_w}" height="{row_h}" fill="#f2f5f6"/>')
        b.append(t(x0 + label_w / 2, ry + row_h / 2 + 6, name, 18, INK, "700"))
        for c, (ease, hold, desc) in enumerate(cells):
            cx = x0 + label_w + col_w * c
            b.append(f'<rect x="{cx}" y="{ry}" width="{col_w}" height="{row_h}" fill="#ffffff" stroke="{SOFT}"/>')
            b.append(bar(cx + 24, ry + 46, col_w - 48, ease, "접기 쉬움"))
            b.append(bar(cx + 24, ry + 104, col_w - 48, hold, "형태 유지"))
            b.append(t(cx + col_w / 2, ry + row_h - 22, desc, 12, "#7a878b"))
    b.append(f'<rect x="{x0}" y="{y0}" width="{label_w + col_w * 2}" height="{head_h + row_h * 2}" '
             f'fill="none" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(f'<line x1="{x0 + label_w}" y1="{y0 + head_h + row_h}" x2="{x0 + label_w + col_w * 2}" '
             f'y2="{y0 + head_h + row_h}" stroke="{SOFT}"/>')
    b.append(note_box(147, 720, 500, 58, "쉽게 접히는 조건과 오래 서 있는 조건은 반대편에 있다"))
    b.append(caption(W / 2, 838, [
        "왼쪽 위로 갈수록 작업이 수월하고, 오른쪽 아래로 갈수록",
        "세운 뒤에 잘 버틴다. 무엇을 만들지에 따라 자리를 고른다.",
    ], 16))
    return base("두께와 결에 따른 성질", "같은 종이라도 방향과 두께가 결과를 바꾼다", "".join(b))


def _zigzag(x, y, w, seg, amp, alternate):
    """단면 선. alternate=True면 지그재그 주름, False면 한쪽으로 말리는 곡선."""
    pts = []
    if alternate:
        for i in range(seg + 1):
            pts.append((x + w * i / seg, y + (amp if i % 2 else -amp)))
    else:
        import math
        for i in range(seg * 4 + 1):
            s = i / (seg * 4)
            ang = math.pi * 1.15 * s
            r = w / 2.4
            pts.append((x + r * math.sin(ang) * 1.15, y - r + r * math.cos(ang)))
    return "M" + " L".join(f"{px:.1f} {py:.1f}" for px, py in pts)


def fig_fold_section():
    """p11 — 접는 방향의 조합이 만드는 단면."""
    b = []
    fx, fy, fw = 190, 258, 414
    b.append(t(W / 2, fy - 16, "접기 전 — 접선 위치는 두 경우가 같다", 15, MUTED))
    b.append(f'<rect x="{fx}" y="{fy}" width="{fw}" height="52" fill="#fbfcfc" stroke="{LINE}"/>')
    for i in range(1, 6):
        b.append(f'<line x1="{fx + fw * i / 6}" y1="{fy}" x2="{fx + fw * i / 6}" y2="{fy + 52}" '
                 f'stroke="{TAN}" stroke-width="1.6" stroke-dasharray="5,4"/>')

    b.append(f'<path d="{_zigzag(250, 452, 300, 6, 0, False)}" fill="none" stroke="{TAN}" stroke-width="4"/>')
    b.append(t(W / 2, 396, "모두 같은 방향으로 접으면", 17, INK, "700"))
    b.append(t(W / 2, 566, "한쪽으로 말려 원통에 가까워진다", 15, MUTED))

    b.append(f'<path d="{_zigzag(230, 730, 334, 8, 26, True)}" fill="none" stroke="{TAN}" stroke-width="4"/>')
    b.append(t(W / 2, 660, "산접기와 골접기를 번갈아 접으면", 17, INK, "700"))
    b.append(t(W / 2, 800, "지그재그로 오르내리는 주름이 된다", 15, MUTED))

    b.append(note_box(147, 856, 500, 58, "같은 선, 다른 방향, 다른 형태"))
    return base("접는 방향과 단면", "선의 자리는 같아도 방향 조합이 형태를 가른다", "".join(b))


def fig_stiffness():
    """p19 — 단면 형상에 따른 처짐 비교."""
    b = []
    rows = [("평평한 한 장", 0, 96, "크게 처짐"),
            ("한 번 접음", 1, 42, "덜 처짐"),
            ("여러 번 접은 주름", 2, 8, "거의 처지지 않음")]
    for name, kind, drop, desc in rows:
        y = 288 + rows.index((name, kind, drop, desc)) * 218
        b.append(t(196, y - 26, name, 18, INK, "700", anchor="start"))
        x, w = 196, 232
        if kind == 0:
            b.append(f'<line x1="{x}" y1="{y + 40}" x2="{x + w}" y2="{y + 40}" stroke="{TAN}" stroke-width="4"/>')
        elif kind == 1:
            b.append(f'<path d="M{x} {y + 56} L{x + w / 2} {y + 24} L{x + w} {y + 56}" fill="none" stroke="{TAN}" stroke-width="4"/>')
        else:
            b.append(f'<path d="{_zigzag(x, y + 40, w, 8, 18, True)}" fill="none" stroke="{TAN}" stroke-width="4"/>')
        # 누르는 힘과 처진 정도
        b.append(f'<line x1="{x + w + 74}" y1="{y - 4}" x2="{x + w + 74}" y2="{y + 26}" stroke="{LINE}" stroke-width="2.5" marker-end="url(#gray)"/>')
        b.append(t(x + w + 74, y - 14, "같은 힘", 12, "#7a878b"))
        b.append(f'<line x1="{x + w + 130}" y1="{y + 32}" x2="{x + w + 130}" y2="{y + 32 + drop}" stroke="{SOFT}" stroke-width="10" stroke-linecap="round"/>')
        b.append(t(x + w + 176, y + 40 + drop / 2, desc, 14, MUTED, anchor="start"))
    b.append(t(196, 952, "재료의 양은 세 경우 모두 같다", 15, MUTED, anchor="start"))
    b.append(note_box(147, 986, 500, 58, "두께 방향으로 얼마나 벌어졌는지가 버티는 힘을 정한다"))
    return base("단면 형상과 버티는 힘", "재료를 늘리지 않고 형태로 강성을 얻는다", "".join(b))


def fig_support():
    """p27 — 지지면과 무게중심의 관계."""
    b = []
    cases = [(0.5, 0.5, "안정", "#3f6844"), (1.0, 0.5, "경계", "#8a6a2f"), (1.42, 0.5, "넘어짐", "#9c4a42")]
    for i, (rx, ry, label, color) in enumerate(cases):
        cx, cy, r = 190 + i * 207, 350, 66
        b.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#f2f5f6" stroke="{LINE}" stroke-dasharray="5,4"/>')
        for a in (90, 210, 330):
            import math
            px = cx + r * math.cos(math.radians(a))
            py = cy + r * math.sin(math.radians(a))
            b.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="7" fill="{LINE}"/>')
        gx, gy = cx + r * (rx - 0.5) * 1.4, cy
        b.append(f'<line x1="{gx - 11}" y1="{gy}" x2="{gx + 11}" y2="{gy}" stroke="{color}" stroke-width="4"/>')
        b.append(f'<line x1="{gx}" y1="{gy - 11}" x2="{gx}" y2="{gy + 11}" stroke="{color}" stroke-width="4"/>')
        b.append(t(cx, cy + r + 36, label, 19, color, "700"))
        if i == 2:
            b.append(f'<line x1="{gx + 16}" y1="{gy}" x2="{gx + 44}" y2="{gy}" stroke="{color}" stroke-width="2.5" marker-end="url(#gray)"/>')
    b.append(t(W / 2, 264, "위에서 내려다본 모습 — 점선 안이 바닥과 닿는 자리가 만드는 영역", 15, MUTED))
    b.append(t(W / 2, 470, "굵은 십자는 무게중심이 바닥으로 내려온 자리", 14, "#7a878b"))

    for i, (label, spread) in enumerate([("닿는 자리를 좁게", 34), ("닿는 자리를 넓게", 78)]):
        cx, cy = 258 + i * 278, 640
        b.append(f'<ellipse cx="{cx}" cy="{cy}" rx="{spread}" ry="{spread * 0.62}" fill="#f2f5f6" stroke="{LINE}" stroke-dasharray="5,4"/>')
        for dx in (-spread * 0.8, spread * 0.8):
            b.append(f'<circle cx="{cx + dx:.1f}" cy="{cy + spread * 0.36:.1f}" r="6" fill="{LINE}"/>')
        b.append(f'<circle cx="{cx}" cy="{cy - spread * 0.44:.1f}" r="6" fill="{LINE}"/>')
        b.append(t(cx, cy + spread * 0.62 + 34, label, 16, INK, "700"))
    b.append(note_box(147, 760, 500, 58, "닿는 자리가 벌어질수록 허용 범위가 넓어진다"))
    b.append(caption(W / 2, 872, [
        "무게중심이 영역 가장자리를 넘는 순간부터",
        "조형은 스스로 더 기울어지는 방향으로 힘을 받는다.",
    ], 16))
    return base("지지면과 무게중심", "서 있음을 정하는 것은 강도가 아니라 배치다", "".join(b))


def _link_stage(x, y, w, h, open_ratio):
    """조작면을 민 정도에 따라 위쪽 면이 열리는 한 단계."""
    b = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#fbfcfc" stroke="{LINE}"/>']
    push = 26 * open_ratio
    base_y = y + h - 46
    b.append(f'<rect x="{x + 18 + push:.1f}" y="{base_y}" width="{w - 70}" height="26" rx="5" fill="{TAN_SOFT}" stroke="{TAN}"/>')
    hinge_x, hinge_y = x + 30, y + 40
    lid_len = w - 74
    import math
    ang = math.radians(-52 * open_ratio)
    ex = hinge_x + lid_len * math.cos(ang)
    ey = hinge_y + lid_len * math.sin(ang)
    b.append(f'<line x1="{hinge_x}" y1="{hinge_y}" x2="{ex:.1f}" y2="{ey:.1f}" stroke="{TAN}" stroke-width="5" stroke-linecap="round"/>')
    b.append(f'<circle cx="{hinge_x}" cy="{hinge_y}" r="6" fill="{LINE}"/>')
    lx1, ly1 = x + 30 + push, base_y
    lx2 = hinge_x + lid_len * 0.52 * math.cos(ang)
    ly2 = hinge_y + lid_len * 0.52 * math.sin(ang)
    b.append(f'<line x1="{lx1:.1f}" y1="{ly1}" x2="{lx2:.1f}" y2="{ly2:.1f}" stroke="{LINE}" stroke-width="3"/>')
    b.append(f'<circle cx="{lx2:.1f}" cy="{ly2:.1f}" r="5" fill="{LINE}"/>')
    return b


def fig_link():
    """p36 — 링크가 움직임을 전달하는 과정."""
    b = []
    for i, ratio in enumerate((0.0, 0.5, 1.0)):
        x, y, w, h = 128 + i * 190, 288, 158, 260
        b += _link_stage(x, y, w, h, ratio)
        label = ["밀기 전", "조금 밀었을 때", "끝까지 밀었을 때"][i]
        b.append(t(x + w / 2, y + h + 34, label, 16, INK, "700"))
        if ratio:
            b.append(f'<line x1="{x + 24}" y1="{y + h - 8}" x2="{x + 24 + 30 * ratio:.1f}" y2="{y + h - 8}" '
                     f'stroke="{TAN}" stroke-width="2.5" marker-end="url(#tan)"/>')
    b.append(t(W / 2, 264, "아래쪽 면을 밀면 띠 모양 링크가 위쪽 면을 들어 올린다", 15, MUTED))
    b.append(note_box(147, 636, 500, 58, "링크가 붙은 자리가 축에서 멀수록 적게 밀어도 크게 움직인다"))
    b.append(caption(W / 2, 748, [
        "힌지는 회전할 축을 정하고, 링크는 떨어진 두 자리를 이어",
        "한쪽의 움직임을 다른 쪽으로 옮긴다.",
    ], 16))
    return base("링크가 움직임을 옮기는 과정", "미는 자리와 열리는 자리를 잇는 구조", "".join(b))


def fig_assembled():
    """p46 — 완성 조형의 닫힌 자세와 열린 자세."""
    b = []
    import math
    for i, (ratio, label) in enumerate(((0.0, "닫힌 자세"), (1.0, "열린 자세"))):
        x, y, w, h = 132 + i * 328, 300, 200, 300
        top, bot = y + 52, y + h - 54          # 몸통 위·아래 경계
        # 몸통 외곽 — 부분들이 한 조형으로 읽히도록 윤곽을 먼저 그린다
        b.append(f'<rect x="{x}" y="{top}" width="{w}" height="{bot - top}" fill="#fdfbf8" stroke="{TAN}" stroke-width="2.5"/>')
        # 위쪽 성긴 주름, 아래쪽 촘촘한 주름
        b.append(f'<path d="{_zigzag(x + 16, top + 54, w - 32, 4, 16, True)}" fill="none" stroke="{TAN}" stroke-width="2.6" opacity="0.6"/>')
        b.append(f'<path d="{_zigzag(x + 16, bot - 46, w - 32, 11, 13, True)}" fill="none" stroke="{TAN}" stroke-width="2.6"/>')
        # 세 다리와 바닥
        for dx in (14, w / 2, w - 14):
            b.append(f'<line x1="{x + dx}" y1="{bot}" x2="{x + dx + (dx - w / 2) * 0.30:.1f}" y2="{bot + 40}" stroke="{TAN}" stroke-width="4"/>')
        b.append(f'<line x1="{x - 18}" y1="{bot + 42}" x2="{x + w + 18}" y2="{bot + 42}" stroke="{LINE}" stroke-width="3"/>')
        # 열리는 뚜껑
        hinge_x, hinge_y = x + 22, top
        ang = math.radians(-52 * ratio)
        lid = w - 44
        ex, ey = hinge_x + lid * math.cos(ang), hinge_y + lid * math.sin(ang)
        b.append(f'<line x1="{hinge_x}" y1="{hinge_y}" x2="{ex:.1f}" y2="{ey:.1f}" stroke="{TAN}" stroke-width="5" stroke-linecap="round"/>')
        b.append(f'<circle cx="{hinge_x}" cy="{hinge_y}" r="6" fill="{LINE}"/>')
        # 링크 — 뚜껑 중간과 몸통 안 조작면을 잇는다
        push = 20 * ratio
        op_y = bot - 22
        b.append(f'<rect x="{x + 22 + push:.1f}" y="{op_y}" width="{w - 74}" height="14" rx="4" fill="{TAN_SOFT}" stroke="{TAN}"/>')
        lx2 = hinge_x + lid * 0.5 * math.cos(ang)
        ly2 = hinge_y + lid * 0.5 * math.sin(ang)
        b.append(f'<line x1="{x + 40 + push:.1f}" y1="{op_y}" x2="{lx2:.1f}" y2="{ly2:.1f}" stroke="{LINE}" stroke-width="2.5"/>')
        b.append(f'<circle cx="{lx2:.1f}" cy="{ly2:.1f}" r="4.5" fill="{LINE}"/>')
        if ratio:
            b.append(f'<line x1="{x + 30}" y1="{op_y + 26}" x2="{x + 30 + push:.1f}" y2="{op_y + 26}" stroke="{TAN}" stroke-width="2.5" marker-end="url(#tan)"/>')
        # 무게중심과 지지 영역
        gx = x + w / 2 + (18 if ratio else 0)
        gy = bot - 62
        b.append(f'<line x1="{gx - 11}" y1="{gy}" x2="{gx + 11}" y2="{gy}" stroke="#3f6844" stroke-width="4"/>')
        b.append(f'<line x1="{gx}" y1="{gy - 11}" x2="{gx}" y2="{gy + 11}" stroke="#3f6844" stroke-width="4"/>')
        b.append(f'<line x1="{gx}" y1="{gy + 14}" x2="{gx}" y2="{bot + 38}" stroke="#3f6844" stroke-width="1.4" stroke-dasharray="4,4"/>')
        b.append(f'<line x1="{x - 6}" y1="{bot + 50}" x2="{x + w + 6}" y2="{bot + 50}" stroke="#3f6844" stroke-width="5"/>')
        b.append(t(x + w / 2, bot + 84, label, 19, INK, "700"))
        b.append(t(x + w / 2, bot + 110,
                   "중심이 옮겨져도 지지 영역 안" if ratio else "아래쪽 주름이 무게를 낮춘다",
                   14, "#3f6844" if ratio else MUTED))
    b.append(f'<line x1="{348}" y1="{452}" x2="{448}" y2="{452}" stroke="{LINE}" stroke-width="2.5" marker-end="url(#gray)"/>')
    b.append(note_box(147, 750, 500, 58, "열린 자세에서도 중심은 지지 영역 안에 남아야 한다"))
    b.append(caption(W / 2, 862, [
        "움직이는 부분이 자리를 옮기면 무게중심도 함께 옮겨 간다.",
        "닫힌 자세의 안정만 확인해서는 충분하지 않다.",
    ], 16))
    return base("완성 조형의 두 자세", "닫았을 때와 열었을 때의 무게중심", "".join(b))


FIGURES = {6: fig_material, 11: fig_fold_section, 19: fig_stiffness,
           27: fig_support, 36: fig_link, 46: fig_assembled}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tmp/pdfs/book-064")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
