#!/usr/bin/env python3
"""book-066 이미지 페이지 6개의 SVG 생성. figures064.py의 t()/base() 패턴을 따른다."""
import math
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26303a"
LINE = "#7b8792"
SOFT = "#dee4e8"
BRICK = "#ac5c40"
MUTED = "#56646f"

DOM = "#e6ded1"      # 지배면
SEC = "#bcc9cf"      # 보조면
ACC = "#ac5c40"      # 강조면
OPEN = "#4b5a63"     # 창(개구부)


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="brick" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{BRICK}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <pattern id="hatchA" width="8" height="8" patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
    <line x1="0" y1="0" x2="0" y2="8" stroke="{LINE}" stroke-width="1.6"/></pattern>
  <pattern id="hatchB" width="14" height="14" patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
    <line x1="0" y1="0" x2="0" y2="14" stroke="{LINE}" stroke-width="1.4"/></pattern>
  <pattern id="hatchC" width="9" height="9" patternTransform="rotate(-45)" patternUnits="userSpaceOnUse">
    <line x1="0" y1="0" x2="0" y2="9" stroke="{BRICK}" stroke-width="1.6"/></pattern>
</defs>
{t(W/2, 122, title, 32, '#1d2830', '700')}
{t(W/2, 164, subtitle, 17, '#66757e')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note_box(x, y, w, h, label, size=17):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f6f2ec" stroke="#c8a482"/>'
            + t(x + w / 2, y + h / 2 + 6, label, size, "#4d3a2c", "700"))


def caption(x, y, lines, size=15, color=MUTED, anchor="middle"):
    return "".join(t(x, y + i * 25, line, size, color, anchor=anchor) for i, line in enumerate(lines))


# ── 벽화 도안: 아래 지배면 하나, 위 보조면 셋, 강조면 둘 ─────────────
def mural(x, y, w, h, level):
    """level 0=먼 거리(덩어리만) 1=중간(경계까지) 2=근접(질감까지)."""
    b = []
    split = y + h * 0.40                      # 위 보조면 / 아래 지배면 경계
    edge = f' stroke="{LINE}" stroke-width="1"' if level >= 1 else ""
    if level == 0:
        # 먼 거리대 — 위쪽 세 면이 뭉쳐 하나의 덩어리로 읽힌다
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{split - y:.1f}" fill="{SEC}" opacity="0.82"/>')
    else:
        for i in range(3):
            bx = x + w * i / 3
            b.append(f'<rect x="{bx:.1f}" y="{y}" width="{w/3:.1f}" height="{split - y:.1f}" '
                     f'fill="{SEC}" opacity="{0.75 + 0.08 * i:.2f}"{edge}/>')
    b.append(f'<rect x="{x}" y="{split:.1f}" width="{w}" height="{y + h - split:.1f}" fill="{DOM}"{edge}/>')
    b.append(f'<rect x="{x + w * 0.10:.1f}" y="{split - h * 0.06:.1f}" width="{w * 0.09:.1f}" '
             f'height="{h * 0.12:.1f}" fill="{ACC}"/>')
    b.append(f'<rect x="{x + w * 0.74:.1f}" y="{y + h * 0.70:.1f}" width="{w * 0.07:.1f}" '
             f'height="{h * 0.10:.1f}" fill="{ACC}"/>')
    if level >= 2:
        for i in range(1, 9):
            yy = split + (y + h - split) * i / 9
            b.append(f'<line x1="{x + 4}" y1="{yy:.1f}" x2="{x + w - 4}" y2="{yy:.1f}" '
                     f'stroke="{LINE}" stroke-width="0.7" opacity="0.45"/>')
        for i in range(4):
            sx = x + w * (0.20 + 0.18 * i)
            b.append(f'<line x1="{sx:.1f}" y1="{split + 8:.1f}" x2="{sx + 14:.1f}" y2="{y + h - 10:.1f}" '
                     f'stroke="{LINE}" stroke-width="0.9" opacity="0.5"/>')
    b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="none" stroke="{LINE}" stroke-width="1.6"/>')
    return "".join(b)


def plan_view(cx, cy, w, mode):
    """위에서 내려다본 배치도. mode: far / oblique / close."""
    b = [f'<rect x="{cx - w/2}" y="{cy - 30}" width="{w}" height="6" fill="{LINE}"/>']
    b.append(t(cx, cy - 38, "벽", 11, "#8b959c"))
    if mode == "far":
        px, py = cx, cy + 42
    elif mode == "oblique":
        px, py = cx - w * 0.42, cy + 18
    else:
        px, py = cx, cy - 4
    b.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="6" fill="{BRICK}"/>')
    b.append(f'<line x1="{px:.1f}" y1="{py:.1f}" x2="{cx if mode != "oblique" else cx + w*0.30:.1f}" '
             f'y2="{cy - 22}" stroke="{BRICK}" stroke-width="1.6" stroke-dasharray="4,3"/>')
    return "".join(b)


# ── p7 벽면 조사표 ────────────────────────────────────────────────
def fig_survey():
    b = []
    x0, tw = 118, 558
    lw = 108                                   # 항목 이름 칸 너비
    rows = [("치수", 178), ("뚫린 자리", 172), ("표면 상태", 196), ("주의 구간", 150)]
    y = 236
    tops = []
    for name, rh in rows:
        tops.append((name, y, rh))
        b.append(f'<rect x="{x0}" y="{y}" width="{lw}" height="{rh}" fill="#eef2f4"/>')
        b.append(t(x0 + lw / 2, y + rh / 2 + 6, name, 17, INK, "700"))
        b.append(f'<rect x="{x0 + lw}" y="{y}" width="{tw - lw}" height="{rh}" fill="#ffffff"/>')
        b.append(f'<line x1="{x0}" y1="{y}" x2="{x0 + tw}" y2="{y}" stroke="{SOFT}"/>')
        y += rh
    total_h = y - 236
    cx0 = x0 + lw + 26                         # 내용 칸 왼쪽 여백

    # 1) 치수 — 오른쪽이 낮은 사다리꼴
    ry = tops[0][1]
    wx, wy, ww, wh = cx0 + 16, ry + 32, 158, 96
    b.append(f'<path d="M{wx} {wy} L{wx + ww} {wy + 14} L{wx + ww} {wy + wh} L{wx} {wy + wh} Z" '
             f'fill="#f7f9fa" stroke="{LINE}" stroke-width="1.6"/>')
    b.append(f'<line x1="{wx}" y1="{wy + wh + 18}" x2="{wx + ww}" y2="{wy + wh + 18}" '
             f'stroke="{BRICK}" stroke-width="1.6" marker-end="url(#brick)"/>')
    b.append(t(wx + ww / 2, wy + wh + 36, "가로", 12, "#8b959c"))
    b.append(f'<line x1="{wx - 18}" y1="{wy}" x2="{wx - 18}" y2="{wy + wh}" '
             f'stroke="{BRICK}" stroke-width="1.6"/>')
    b.append(t(wx - 26, wy + wh / 2, "세로", 12, "#8b959c", anchor="end"))
    b.append(caption(wx + ww + 34, ry + 62, [
        "가로세로 실측값을 적고", "왼쪽과 오른쪽의 높이 차이를", "따로 표시한다"], 14, anchor="start"))

    # 2) 뚫린 자리
    ry = tops[1][1]
    wx, wy, ww, wh = cx0 + 16, ry + 28, 158, 96
    b.append(f'<rect x="{wx}" y="{wy}" width="{ww}" height="{wh}" fill="#f7f9fa" stroke="{LINE}" stroke-width="1.6"/>')
    for i in range(5):
        b.append(f'<rect x="{wx + 12 + i * 28}" y="{wy + 12}" width="17" height="26" fill="{OPEN}"/>')
    b.append(f'<line x1="{wx + ww - 26}" y1="{wy}" x2="{wx + ww - 26}" y2="{wy + wh}" stroke="{BRICK}" stroke-width="3"/>')
    b.append(t(wx + ww / 2, wy + wh - 18, "창 다섯 · 배관 하나", 12, "#6d7a83"))
    b.append(caption(wx + ww + 34, ry + 66, [
        "창·출입구·배관의 자리를", "같은 윤곽 위에 옮겨 적는다"], 14, anchor="start"))

    # 3) 표면 상태 — 구간별 빗금
    ry = tops[2][1]
    bands = [("줄눈이 깊은 구간", "hatchA"), ("미장 결이 거친 구간", "hatchB"), ("보수 자국 구간", "hatchC")]
    for i, (label, pat) in enumerate(bands):
        by = ry + 26 + i * 52
        b.append(f'<rect x="{cx0}" y="{by}" width="118" height="36" fill="url(#{pat})" opacity="0.55"/>')
        b.append(f'<rect x="{cx0}" y="{by}" width="118" height="36" fill="none" stroke="{LINE}"/>')
        b.append(t(cx0 + 134, by + 24, label, 15, MUTED, anchor="start"))

    # 4) 주의 구간
    ry = tops[3][1]
    b.append(caption(cx0, ry + 42, [
        "아래쪽 젖음 · 도장 벗겨짐 · 들뜬 미장처럼",
        "도료가 붙기 어려운 자리를 따로 모아 적는다"], 15, INK, anchor="start"))
    b.append(f'<rect x="{cx0}" y="{ry + 90}" width="336" height="32" rx="8" fill="#f6f2ec" stroke="#c8a482"/>')
    b.append(t(cx0 + 168, ry + 111, "보수가 필요하면 여기에서 걸러진다", 14, "#4d3a2c", "700"))

    b.append(f'<rect x="{x0}" y="236" width="{tw}" height="{total_h}" fill="none" stroke="{LINE}" stroke-width="1.8"/>')
    b.append(f'<line x1="{x0 + lw}" y1="236" x2="{x0 + lw}" y2="{236 + total_h}" stroke="{LINE}"/>')
    b.append(note_box(147, 976, 500, 56, "조사 결과가 곧 이 화면의 조건이다"))
    return base("벽면 조사표", "도안을 시작하기 전에 벽 하나를 적어 둔다", "".join(b))


# ── p11 거리대에 따라 보이는 단위 ─────────────────────────────────
def fig_distance():
    b = []
    cols = [("먼 거리대", "길 건너에서", 0, 0.92),
            ("중간 거리대", "인도를 지나며", 1, 0.46),
            ("근접 거리대", "벽 바로 앞에서", 2, 0.14)]
    x0, colw = 108, 193
    for name, sub, level, unit in cols:
        cx = x0 + colw * level
        b.append(t(cx + colw / 2, 258, name, 19, INK, "700"))
        b.append(t(cx + colw / 2, 282, sub, 13, "#8b959c"))
        b.append(mural(cx + 14, 300, colw - 28, 216, level))
        # 알아볼 수 있는 최소 크기
        bw = (colw - 60) * unit
        b.append(f'<rect x="{cx + 30:.1f}" y="562" width="{bw:.1f}" height="16" rx="4" fill="{BRICK}"/>')
        b.append(f'<rect x="{cx + 30:.1f}" y="562" width="{colw - 60}" height="16" rx="4" '
                 f'fill="none" stroke="{SOFT}"/>')
    b.append(t(W / 2, 546, "그 거리에서 알아볼 수 있는 가장 작은 크기", 14, "#8b959c"))
    b.append(t(W / 2, 610, "멀수록 최소 크기가 커진다", 15, MUTED))

    b.append(f'<line x1="150" y1="650" x2="644" y2="650" stroke="{SOFT}" stroke-width="2"/>')
    items = [("먼 거리대에서 남는 것", "큰 덩어리와 밝고 어두운 배치뿐"),
             ("중간 거리대에서 더해지는 것", "덩어리의 경계와 굵은 요소"),
             ("근접 거리대에서 더해지는 것", "표면의 굴곡과 붓 자국")]
    for i, (k, v) in enumerate(items):
        yy = 694 + i * 62
        b.append(f'<circle cx="168" cy="{yy - 6}" r="6" fill="{BRICK}"/>')
        b.append(t(190, yy, k, 16, INK, "700", anchor="start"))
        b.append(t(190, yy + 24, v, 14, MUTED, anchor="start"))

    b.append(note_box(147, 900, 500, 58, "먼 쪽을 담당하는 요소는 그만큼 크게 잡는다"))
    return base("거리대에 따라 보이는 단위", "같은 벽화가 거리마다 다른 화면이 된다", "".join(b))


# ── p14 각도에 따라 달라지는 화면 ─────────────────────────────────
def fig_angle():
    b = []
    centers = [(190, "정면 · 멀리서", "far"), (397, "바로 앞 · 올려다봄", "close"),
               (604, "인도에서 · 비스듬히", "oblique")]
    top, hgt, half = 276, 210, 82
    for cx, label, mode in centers:
        if mode == "far":
            pts = [(cx - half, top), (cx + half, top), (cx + half, top + hgt), (cx - half, top + hgt)]
            rows = [top + hgt * r for r in (0.34, 0.66)]
            widths = [(cx - half, cx + half)] * 2
        elif mode == "close":
            pts = [(cx - half * 0.62, top), (cx + half * 0.62, top),
                   (cx + half, top + hgt), (cx - half, top + hgt)]
            # 벽에서 같은 간격인 두 경계가 위로 갈수록 좁아져 보인다
            rows, widths = [], []
            for r in (0.28, 0.60):
                yy = top + hgt * r
                hw = half * (0.62 + 0.38 * r)
                rows.append(yy)
                widths.append((cx - hw, cx + hw))
        else:
            pts = [(cx - half, top + 16), (cx + half * 0.52, top),
                   (cx + half * 0.52, top + hgt), (cx - half, top + hgt - 16)]
            rows, widths = [], []
            for r in (0.34, 0.66):
                rows.append(top + 16 + (hgt - 32) * r)
                widths.append((cx - half, cx + half * 0.52))
        path = "M" + " L".join(f"{px:.1f} {py:.1f}" for px, py in pts) + " Z"
        b.append(f'<path d="{path}" fill="#f7f9fa" stroke="{LINE}" stroke-width="1.8"/>')
        for yy, (lx, rx) in zip(rows, widths):
            b.append(f'<line x1="{lx:.1f}" y1="{yy:.1f}" x2="{rx:.1f}" y2="{yy:.1f}" '
                     f'stroke="{BRICK}" stroke-width="2.4"/>')
        b.append(t(cx, top + hgt + 34, label, 16, INK, "700"))

    b.append(t(W / 2, 250, "붉은 두 줄은 벽에서 서로 같은 간격으로 그은 가로 경계", 14, "#8b959c"))

    for cx, label, mode in centers:
        b.append(plan_view(cx, 610, 150, mode))
    b.append(t(W / 2, 676, "아래는 각 경우를 위에서 내려다본 자리", 14, "#8b959c"))

    notes = [("정면 · 멀리서", "도안의 비례가 거의 그대로 보인다"),
             ("바로 앞 · 올려다봄", "위로 갈수록 가로 간격이 좁아진다"),
             ("인도에서 · 비스듬히", "가로 폭이 줄어 형태가 옆으로 눌린다")]
    for i, (k, v) in enumerate(notes):
        yy = 730 + i * 60
        b.append(f'<circle cx="168" cy="{yy - 6}" r="6" fill="{BRICK}"/>')
        b.append(t(190, yy, k, 16, INK, "700", anchor="start"))
        b.append(t(190, yy + 22, v, 14, MUTED, anchor="start"))

    b.append(note_box(147, 924, 500, 58, "도안대로 보이는 자리는 셋 중 하나뿐이다"))
    return base("각도에 따라 달라지는 화면", "보는 자리가 비례를 다시 정한다", "".join(b))


# ── p20 표면 굴곡과 사라지는 형태 ─────────────────────────────────
def _profile(x, y, w, amp):
    """벽 단면 — 줄눈으로 파이고 미장 결로 물결치는 윤곽."""
    pts = []
    n = 64
    for i in range(n + 1):
        s = i / n
        px = x + w * s
        py = y - amp * (0.55 * math.sin(s * 11.0) + 0.45 * math.sin(s * 3.1 + 1.2))
        if i % 16 == 8:
            py += amp * 0.9                     # 줄눈
        pts.append((px, py))
    return "M" + " L".join(f"{px:.1f} {py:.1f}" for px, py in pts)


def fig_texture():
    b = []
    px, py, pw = 150, 320, 494
    b.append(t(W / 2, 244, "벽 단면을 옆에서 본 모습", 15, MUTED))
    b.append(f'<path d="{_profile(px, py, pw, 17)} L{px + pw} {py + 78} L{px} {py + 78} Z" '
             f'fill="#eef1f3" stroke="{LINE}" stroke-width="1.6"/>')
    # 붓이 지나간 자리 — 파인 곳에서 끊긴다
    seg, gap = 0, []
    n = 64
    for i in range(n + 1):
        s = i / n
        xx = px + pw * s
        yy = py - 17 * (0.55 * math.sin(s * 11.0) + 0.45 * math.sin(s * 3.1 + 1.2))
        if i % 16 in (7, 8, 9):
            if seg:
                gap.append(None)
            seg = 0
            continue
        gap.append((xx, yy - 7))
        seg = 1
    run = []
    for pt in gap + [None]:
        if pt is None:
            if len(run) > 1:
                d = "M" + " L".join(f"{a:.1f} {c:.1f}" for a, c in run)
                b.append(f'<path d="{d}" fill="none" stroke="{BRICK}" stroke-width="6" stroke-linecap="round"/>')
            run = []
        else:
            run.append(pt)
    b.append(t(W / 2, 428, "파인 자리에서 도료가 끊기고 도드라진 자리에 두껍게 묻는다", 14, "#8b959c"))

    # 굵기가 다른 네 개의 띠
    b.append(t(150, 486, "같은 굴곡 위에 굵기만 달리한 네 개의 띠", 15, MUTED, anchor="start"))
    widths = [(4, "가장 가는 띠", 5), (9, "조금 굵은 띠", 3), (16, "굵은 띠", 1), (26, "가장 굵은 띠", 0)]
    for i, (tw, label, breaks) in enumerate(widths):
        yy = 536 + i * 74
        b.append(t(150, yy + 6, label, 14, INK, "700", anchor="start"))
        bx, bw = 300, 260
        b.append(f'<rect x="{bx}" y="{yy - tw / 2:.1f}" width="{bw}" height="{tw}" rx="{min(tw/2,6)}" fill="{SOFT}"/>')
        # 끊긴 모습
        parts = breaks + 1
        seg_w = bw / parts
        for k in range(parts):
            sx = bx + seg_w * k + (3 if breaks else 0)
            sw = seg_w - (6 if breaks else 0)
            b.append(f'<rect x="{sx:.1f}" y="{yy - tw / 2:.1f}" width="{sw:.1f}" height="{tw}" '
                     f'rx="{min(tw/2,6)}" fill="{BRICK}"/>')
        desc = {5: "점선처럼 끊긴다", 3: "자주 끊긴다",
                1: "한 곳에서만 끊긴다", 0: "온전히 남는다"}[breaks]
        b.append(t(586, yy + 6, desc, 14, MUTED, anchor="start"))

    b.append(note_box(147, 862, 500, 58, "굴곡의 깊이보다 가는 것은 표면에 지지 못한다"))
    b.append(caption(W / 2, 972, [
        "그래서 화면에서 가장 가는 요소의 굵기는",
        "작가의 취향이 아니라 벽 표면이 정한다.",
    ], 16))
    return base("표면 굴곡과 사라지는 형태", "굵기의 하한을 벽이 먼저 정한다", "".join(b))


# ── p27 색면 분할과 넓이 배분 ─────────────────────────────────────
def _bars(x, y, items, unit=3.4):
    b = []
    for i, (label, pct, color) in enumerate(items):
        yy = y + i * 34
        b.append(f'<rect x="{x}" y="{yy}" width="{pct * unit:.1f}" height="18" rx="4" fill="{color}" stroke="{LINE}"/>')
        b.append(t(x + pct * unit + 10, yy + 15, f"{label} {pct}%", 13, MUTED, anchor="start"))
    return "".join(b)


def fig_areas():
    b = []
    # 위: 넓이 차이가 벌어진 경우
    b.append(t(150, 250, "넓이 차이가 벌어진 분할", 18, INK, "700", anchor="start"))
    x, y, w, h = 150, 268, 232, 208
    split = y + h * 0.36
    for i in range(3):
        b.append(f'<rect x="{x + w * i / 3:.1f}" y="{y}" width="{w/3:.1f}" height="{split - y:.1f}" '
                 f'fill="{SEC}" opacity="{0.7 + 0.1 * i:.2f}" stroke="{LINE}"/>')
    b.append(f'<rect x="{x}" y="{split:.1f}" width="{w}" height="{y + h - split:.1f}" fill="{DOM}" stroke="{LINE}"/>')
    b.append(f'<rect x="{x + 22}" y="{split - 14:.1f}" width="22" height="26" fill="{ACC}"/>')
    b.append(f'<rect x="{x + w - 60}" y="{y + h - 52}" width="18" height="22" fill="{ACC}"/>')
    b.append(t(x + w / 2, split + 62, "지배면", 16, "#6a6055", "700"))
    b.append(t(x + w / 2, y + 32, "보조면 셋", 13, "#4e5c63"))
    b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="none" stroke="{LINE}" stroke-width="1.8"/>')
    b.append(_bars(432, 292, [("지배면", 62, DOM), ("보조면", 13, SEC),
                              ("보조면", 11, SEC), ("강조면", 3, ACC)]))
    b.append(t(432, 448, "막대 길이가 뚜렷하게 갈린다", 14, MUTED, anchor="start"))

    b.append(f'<line x1="150" y1="516" x2="644" y2="516" stroke="{SOFT}" stroke-width="2"/>')

    # 아래: 넓이 차이가 좁은 경우
    b.append(t(150, 562, "넓이 차이가 좁은 분할", 18, INK, "700", anchor="start"))
    x, y = 150, 580
    cells = [(0.0, 0.0, 0.52, 0.48, DOM), (0.52, 0.0, 0.48, 0.48, SEC),
             (0.0, 0.48, 0.48, 0.52, SEC), (0.48, 0.48, 0.52, 0.52, DOM)]
    for fx, fy, fw, fh, color in cells:
        b.append(f'<rect x="{x + w * fx:.1f}" y="{y + h * fy:.1f}" width="{w * fw:.1f}" '
                 f'height="{h * fh:.1f}" fill="{color}" opacity="0.85" stroke="{LINE}"/>')
    b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="none" stroke="{LINE}" stroke-width="1.8"/>')
    b.append(_bars(432, 604, [("면", 28, DOM), ("면", 26, SEC),
                              ("면", 24, SEC), ("면", 22, DOM)]))
    b.append(t(432, 760, "막대 길이가 비슷해 바탕이 정해지지 않는다", 14, MUTED, anchor="start"))

    b.append(note_box(147, 840, 500, 58, "넓이 차이가 벌어질 때 멀리서도 위계가 읽힌다"))
    b.append(caption(W / 2, 950, [
        "면의 개수가 아니라 넓이의 격차가 화면의 뼈대를 만든다.",
        "격차가 좁으면 먼 거리대에서 화면이 잘게 흩어져 보인다.",
    ], 16))
    return base("색면 분할과 넓이 배분", "무엇이 바탕인지가 넓이로 정해진다", "".join(b))


# ── p45 세 지점에서 본 완성 화면 ──────────────────────────────────
def fig_check():
    b = []
    rows = [(272, "먼 거리대 — 네거리 건너편", "far", 0,
             "아래 지배면과 위 보조면, 점으로 남은 강조면 둘"),
            (490, "중간 거리대 — 인도를 지나며", "oblique", 1,
             "왼쪽이 펼쳐지고 시작점 쪽 강조면이 먼저 들어온다"),
            (708, "근접 거리대 — 벽 바로 앞", "close", 2,
             "점선 안이 한 번에 시야에 들어오는 범위 — 위쪽은 눌려 보인다")]
    mx, mw, mh = 286, 330, 126
    for y, label, mode, level, desc in rows:
        b.append(plan_view(186, y + 62, 116, mode))
        if mode == "far":
            b.append(mural(mx, y, mw, mh, level))
        elif mode == "oblique":
            b.append(f'<g transform="translate({mx},{y}) skewY(2.4) scale(1,0.95)">'
                     + mural(0, 0, mw, mh, level) + '</g>')
            b.append(f'<line x1="{mx}" y1="{y + mh + 22}" x2="{mx + 56}" y2="{y + mh + 22}" '
                     f'stroke="{BRICK}" stroke-width="2" marker-end="url(#brick)"/>')
            b.append(t(mx + 68, y + mh + 27, "걸어오는 방향", 12, "#8b959c", anchor="start"))
        else:
            b.append(f'<g transform="translate({mx},{y}) scale(1,0.92)">'
                     + mural(0, 0, mw, mh, level) + '</g>')
            b.append(f'<rect x="{mx + 18}" y="{y + 6}" width="132" height="{mh * 0.92 - 12:.1f}" '
                     f'fill="none" stroke="{BRICK}" stroke-width="2" stroke-dasharray="6,4"/>')
        b.append(t(mx, y - 40, label, 16, INK, "700", anchor="start"))
        b.append(t(mx, y - 18, desc, 13, MUTED, anchor="start"))

    b.append(note_box(147, 906, 500, 58, "세 경우 모두에서 위아래 위계가 유지되는가"))
    b.append(caption(W / 2, 1016, [
        "한 거리에서만 확인한 화면은 나머지 두 거리에서",
        "무너져 있을 수 있다.",
    ], 16))
    return base("세 지점에서 본 완성 화면", "정해 둔 자리로 돌아가 확인한다", "".join(b))


FIGURES = {7: fig_survey, 11: fig_distance, 14: fig_angle,
           20: fig_texture, 27: fig_areas, 45: fig_check}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tmp/pdfs/book-066")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
