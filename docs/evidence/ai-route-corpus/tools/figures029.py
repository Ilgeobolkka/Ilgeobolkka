#!/usr/bin/env python3
"""book-029 이미지 페이지 6개의 SVG 생성. figures021.py의 t()/base() 패턴을 따른다.

여섯 도표에서 표기를 고정한다 — 원래 값은 가는 회색 선, 눌러 만든 곡선은 짙은 파랑 굵은 선, 기준선은
주황 점선, 잘못 읽기 쉬운 자리는 붉은색이다. 가로축은 언제나 왼쪽이 이른 해다.

사용: python3 figures029.py <출력디렉터리>
"""
import math
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#26323a"
LINE = "#7d8b90"
SOFT = "#dfe4e6"
MUTED = "#55666b"
RAW = "#9aa7ad"            # 원래 값
CURVE = "#2f5f80"          # 눌러 만든 곡선
BASE = "#c07f3e"           # 기준선
WARN = "#a24f3d"           # 잘못 읽기 쉬운 자리
FAR = "#8b95a1"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{BASE}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <marker id="warn" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{WARN}"/></marker>
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


def arrow(x1, y1, x2, y2, color=BASE, width=3, marker="mark"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


# 여섯 도표가 같은 자료를 쓴다. 그림마다 다른 자료를 쓰면 견주는 뜻이 흐려진다.
N = 60
SERIES = [math.sin(i * 0.9) * 1.5 + math.sin(i * 0.31) * 1.1 + math.cos(i * 2.1) * 0.8 + i * 0.055
          for i in range(N)]


def smooth(values, window):
    out = []
    half = window // 2
    for i in range(len(values)):
        if i < half or i >= len(values) - half:
            out.append(None)
        else:
            chunk = values[i - half:i + half + 1]
            out.append(sum(chunk) / len(chunk))
    return out


def plot(x0, y0, w, h, values, color, width=1.6, dash=None, offset=0.0, scale=None):
    lo, hi = min(SERIES), max(SERIES)
    scale = scale or (hi - lo)
    pts = []
    for i, v in enumerate(values):
        if v is None:
            continue
        x = x0 + w * i / (len(values) - 1)
        y = y0 + h - h * ((v - offset) - lo) / scale
        pts.append(f"{x:.1f},{y:.1f}")
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<polyline points="{" ".join(pts)}" fill="none" stroke="{color}" '
            f'stroke-width="{width}" stroke-linejoin="round"{d}/>')


def axes(x0, y0, w, h, left="기온", bottom="해"):
    return (f'<line x1="{x0}" y1="{y0 + h}" x2="{x0 + w}" y2="{y0 + h}" stroke="{LINE}" stroke-width="2"/>'
            f'<line x1="{x0}" y1="{y0}" x2="{x0}" y2="{y0 + h}" stroke="{LINE}" stroke-width="2"/>'
            + t(x0 - 10, y0 + 10, left, 12, MUTED, anchor="end")
            + t(x0 + w, y0 + h + 24, bottom, 12, MUTED, anchor="end"))


# ── p7 ────────────────────────────────────────────────────────────────
def fig_baseline():
    b = []
    b.append(t(W / 2, 240, "같은 자료를 두 기준으로 다시 그리면", 16, MUTED))

    x0, y0, w, h = 150, 276, 500, 150
    b.append(axes(x0, y0, w, h))
    early = sum(SERIES[:20]) / 20
    late = sum(SERIES[-20:]) / 20
    lo, hi = min(SERIES), max(SERIES)
    for label, mean, xa, xb, col in [("이른 삼십 년", early, 0, 20, "#e8dcc6"),
                                     ("늦은 삼십 년", late, 40, 60, "#cfd9de")]:
        bx = x0 + w * xa / (N - 1)
        bw = w * (xb - xa) / (N - 1)
        b.append(f'<rect x="{bx:.0f}" y="{y0}" width="{bw:.0f}" height="{h}" fill="{col}" opacity="0.6"/>')
        my = y0 + h - h * (mean - lo) / (hi - lo)
        b.append(f'<line x1="{bx:.0f}" y1="{my:.1f}" x2="{bx + bw:.0f}" y2="{my:.1f}" '
                 f'stroke="{BASE}" stroke-width="2.5" stroke-dasharray="6 5"/>')
        b.append(t(bx + bw / 2, y0 - 10, label, 13, INK, "600"))
    b.append(plot(x0, y0, w, h, SERIES, RAW))
    b.append(t(x0 + w + 10, y0 + 8, "원래 자료", 13, RAW, "700", anchor="start"))

    for i, (label, mean) in enumerate([("이른 삼십 년 기준", early), ("늦은 삼십 년 기준", late)]):
        sx = 150 + i * 280
        sy, sw, sh = 530, 220, 130
        b.append(axes(sx, sy, sw, sh, left="편차"))
        zero = sy + sh - sh * (mean - lo) / (hi - lo)
        zero = min(max(zero, sy), sy + sh)
        b.append(f'<line x1="{sx}" y1="{zero:.1f}" x2="{sx + sw}" y2="{zero:.1f}" '
                 f'stroke="{BASE}" stroke-width="2" stroke-dasharray="5 4"/>')
        b.append(t(sx - 8, zero + 4, "0", 12, BASE, "700", anchor="end"))
        b.append(plot(sx, sy, sw, sh, SERIES, CURVE, 1.8))
        b.append(t(sx + sw / 2, sy + sh + 46, label, 14, INK, "700"))

    b.append(note_box(197, 736, 400, "자료는 하나인데 그림이 둘이다", 16))
    b.append(caption(W / 2, 840, ["두 아래 그래프는 같은 값을 기준선만 바꿔 다시 그린 것이다."]))
    return base("기준을 옮기면 그림이 달라진다",
                "같은 자료를 서로 다른 기준으로 읽은 결과를 나란히 놓은 그림", "".join(b))


# ── p13 ───────────────────────────────────────────────────────────────
def fig_window():
    b = []
    b.append(t(W / 2, 240, "같은 자료를 서로 다른 길이로 눌러 보면", 16, MUTED))

    x0, y0, w, h = 150, 290, 500, 300
    b.append(axes(x0, y0, w, h))
    b.append(plot(x0, y0, w, h, SERIES, RAW, 1.4))
    b.append(plot(x0, y0, w, h, smooth(SERIES, 5), "#6f9fbe", 3))
    b.append(plot(x0, y0, w, h, smooth(SERIES, 31), CURVE, 4))

    b.append(t(x0 + w + 10, y0 + 26, "원래 자료", 13, RAW, "700", anchor="start"))
    b.append(t(x0 + w + 10, y0 + 96, "다섯 해로 묶음", 13, "#6f9fbe", "700", anchor="start"))
    b.append(t(x0 + w + 10, y0 + 166, "삼십 해로 묶음", 13, CURVE, "700", anchor="start"))

    endx = x0 + w * 15 / (N - 1)
    b.append(f'<rect x="{x0}" y="{y0}" width="{endx - x0:.0f}" height="{h}" fill="{WARN}" opacity="0.07"/>')
    b.append(f'<rect x="{x0 + w - (endx - x0):.0f}" y="{y0}" width="{endx - x0:.0f}" height="{h}" '
             f'fill="{WARN}" opacity="0.07"/>')
    b.append(t(x0 + (endx - x0) / 2, y0 + h + 44, "삼십 해 곡선은", 13, WARN, "700"))
    b.append(t(x0 + (endx - x0) / 2, y0 + h + 64, "여기서 만들 수 없다", 13, WARN, "700"))
    b.append(t(x0 + w - (endx - x0) / 2, y0 + h + 44, "삼십 해 곡선은", 13, WARN, "700"))
    b.append(t(x0 + w - (endx - x0) / 2, y0 + h + 64, "여기서 만들 수 없다", 13, WARN, "700"))

    b.append(t(W / 2, 700, "적게 묶으면 오르내림이 남는다", 15, MUTED))
    b.append(t(W / 2, 726, "많이 묶으면 실제 변화도 눌린다", 15, MUTED))

    b.append(note_box(197, 776, 400, "길이를 밝히지 않은 곡선은 읽을 수 없다", 15))
    return base("몇 해로 묶느냐가 그림을 정한다",
                "같은 자료를 서로 다른 길이로 눌러 본 결과를 겹쳐 놓은 그림", "".join(b))


# ── p21 ───────────────────────────────────────────────────────────────
def fig_shift():
    b = []
    b.append(t(W / 2, 240, "분포가 조금 옮겨 갈 때 끝자락에서 생기는 일", 16, MUTED))

    x0, y0, w, h = 140, 300, 520, 260
    b.append(axes(x0, y0, w, h, left="날의 수", bottom="기온"))
    b.append(t(x0 + 6, y0 + h + 24, "낮음", 12, MUTED, anchor="start"))

    shift = 0.10
    cut = 0.70

    def bell(mu):
        pts = []
        for i in range(121):
            f = i / 120
            v = math.exp(-((f - mu) ** 2) / 0.016)
            pts.append((x0 + w * f, y0 + h - h * v * 0.92))
        return pts

    for mu, color, width, dash in [(0.44, RAW, 2.4, "6 5"), (0.44 + shift, CURVE, 3, None)]:
        pts = bell(mu)
        d = f' stroke-dasharray="{dash}"' if dash else ""
        b.append(f'<polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in pts)}" fill="none" '
                 f'stroke="{color}" stroke-width="{width}"{d}/>')
        tail = [(x, y) for x, y in pts if x >= x0 + w * cut]
        area = " ".join(f"{x:.1f},{y:.1f}" for x, y in tail)
        b.append(f'<polygon points="{area} {x0 + w:.1f},{y0 + h} {x0 + w * cut:.1f},{y0 + h}" '
                 f'fill="{color}" opacity="0.35"/>')

    b.append(f'<line x1="{x0 + w * cut:.0f}" y1="{y0 - 10}" x2="{x0 + w * cut:.0f}" y2="{y0 + h}" '
             f'stroke="{WARN}" stroke-width="2" stroke-dasharray="5 4"/>')
    b.append(t(x0 + w * cut + 8, y0 - 16, "아주 더운 날", 13, WARN, "700", anchor="start"))

    b.append(t(x0 + w * 0.44 - 10, y0 - 16, "옮겨 가기 전", 13, RAW, "700", anchor="end"))
    b.append(t(x0 + w * 0.54 + 10, y0 + 12, "옮겨 간 뒤", 13, CURVE, "700", anchor="start"))
    ax = x0 + w * 0.44
    bx = x0 + w * (0.44 + shift)
    b.append(f'<line x1="{ax:.0f}" y1="{y0 + h + 44}" x2="{bx:.0f}" y2="{y0 + h + 44}" stroke="{INK}" stroke-width="2"/>')
    b.append(f'<path d="M{ax:.0f},{y0 + h + 44} L{ax + 10:.0f},{y0 + h + 38} L{ax + 10:.0f},{y0 + h + 50} Z" fill="{INK}"/>')
    b.append(f'<path d="M{bx:.0f},{y0 + h + 44} L{bx - 10:.0f},{y0 + h + 38} L{bx - 10:.0f},{y0 + h + 50} Z" fill="{INK}"/>')
    b.append(t((ax + bx) / 2, y0 + h + 70, "조금 옮겨 갔다", 13, INK, "700"))

    # 끝자락 넓이를 세로 막대로 다시 그린다. 곡선 아래 넓이만으로는 몇 배인지 눈에 들어오지 않는다.
    b.append(t(W / 2, 706, "아주 더운 날의 수", 14, WARN, "700"))
    for i, (label, hgt, color) in enumerate([("옮겨 가기 전", 16, RAW), ("옮겨 간 뒤", 74, CURVE)]):
        bx = 320 + i * 130
        b.append(f'<rect x="{bx}" y="{816 - hgt}" width="70" height="{hgt}" fill="{color}" opacity="0.6"/>')
        b.append(t(bx + 35, 838, label, 12, MUTED))
    b.append(f'<line x1="300" y1="816" x2="530" y2="816" stroke="{LINE}"/>')
    b.append(t(546, 780, "몇 배로 늘었다", 13, WARN, "700", anchor="start"))

    b.append(note_box(197, 876, 400, "평균이 조금 오르면 끝자락은 크게 늘어난다", 15))
    return base("가운데는 조금, 끝은 크게",
                "분포가 조금 옮겨 갈 때 끝자락에서 생기는 변화를 그린 그림", "".join(b))


# ── p29 ───────────────────────────────────────────────────────────────
def fig_break():
    b = []
    b.append(t(W / 2, 240, "두 지점의 값과 그 차이를 함께 보면", 16, MUTED))

    step_at = 34
    # 이웃 지점도 완전히 같지는 않다. 작은 차이를 섞어야 차분 그래프가 현실적으로 보인다.
    other = [v + (1.4 if i >= step_at else 0.0) + math.sin(i * 2.7) * 0.12
             for i, v in enumerate(SERIES)]

    x0, y0, w, h = 150, 286, 500, 170
    b.append(axes(x0, y0, w, h))
    b.append(plot(x0, y0, w, h, SERIES, RAW, 1.8))
    b.append(plot(x0, y0, w, h, other, CURVE, 1.8, scale=(max(SERIES) - min(SERIES))))
    b.append(t(x0 + w + 10, y0 + 30, "이 지점", 13, CURVE, "700", anchor="start"))
    b.append(t(x0 + w + 10, y0 + 96, "이웃 지점", 13, RAW, "700", anchor="start"))
    b.append(t(W / 2, y0 + h + 34, "두 선만 보아서는 어느 쪽이 어긋났는지 알기 어렵다", 13, MUTED))

    diff = [other[i] - SERIES[i] for i in range(N)]
    dx, dy, dw, dh = 150, 566, 500, 124
    b.append(axes(dx, dy, dw, dh, left="차이"))
    lo, hi = min(diff) - 0.4, max(diff) + 0.4
    pts = []
    for i, v in enumerate(diff):
        x = dx + dw * i / (N - 1)
        y = dy + dh - dh * (v - lo) / (hi - lo)
        pts.append(f"{x:.1f},{y:.1f}")
    b.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{WARN}" stroke-width="2.6"/>')
    sx = dx + dw * step_at / (N - 1)
    b.append(f'<line x1="{sx:.0f}" y1="{dy - 14}" x2="{sx:.0f}" y2="{dy + dh}" stroke="{WARN}" '
             f'stroke-width="2" stroke-dasharray="5 4"/>')
    b.append(t(sx + 10, dy - 24, "여기서 무언가가 바뀌었다", 13, WARN, "700", anchor="start"))

    bx, by = 430, 730
    b.append(f'<rect x="{bx}" y="{by}" width="230" height="104" rx="10" fill="#fbfcfc" stroke="{LINE}"/>')
    for i, line in enumerate(["장비 교체", "재는 시각 변경", "둘레 변화"]):
        b.append(t(bx + 20, by + 30 + i * 26, line, 13, INK, "600", anchor="start"))
    b.append(t(bx + 115, by + 126, "관측 일지에서 확인한다", 13, MUTED))

    b.append(note_box(134, 738, 260, "차이를 보면 보인다", 15))
    return base("이웃과 견주면 튄 자리가 보인다",
                "두 지점의 차이를 보고 어긋난 자리를 찾는 방법을 그린 그림", "".join(b))


# ── p37 ───────────────────────────────────────────────────────────────
def fig_publish():
    b = []
    b.append(t(W / 2, 240, "공개하는 그림 하나 뒤에 붙는 것들", 16, MUTED))

    x0, y0, w, h = 250, 280, 300, 130
    b.append(f'<rect x="{x0 - 24}" y="{y0 - 22}" width="{w + 48}" height="{h + 70}" rx="12" '
             f'fill="#fbfcfc" stroke="{LINE}" stroke-width="2"/>')
    b.append(axes(x0, y0, w, h))
    b.append(plot(x0, y0, w, h, SERIES, RAW, 1.3))
    b.append(plot(x0, y0, w, h, smooth(SERIES, 21), CURVE, 3))
    b.append(t(x0 + w / 2, y0 + h + 40, "공개하는 그림", 15, INK, "700"))

    b.append(arrow(397, 464, 397, 512, color=LINE, width=2, marker="gray"))

    boxes = [(120, "원자료", ["표 그대로"]),
             (268, "고친 목록", ["지점 · 해 · 이유"]),
             (416, "쓴 선택", ["기준 기간", "묶은 길이", "지점 기준", "빈자리 처리"]),
             (564, "뺀 목록", ["뺀 지점", "버린 회차"])]
    for bx, title, lines in boxes:
        b.append(f'<rect x="{bx}" y="530" width="130" height="150" rx="10" fill="#fbfcfc" '
                 f'stroke="{LINE}" stroke-width="2"/>')
        b.append(t(bx + 65, 558, title, 15, INK, "700"))
        b.append(f'<line x1="{bx + 16}" y1="570" x2="{bx + 114}" y2="570" stroke="{SOFT}"/>')
        for i, line in enumerate(lines):
            b.append(t(bx + 65, 596 + i * 24, line, 12, MUTED))

    b.append(f'<line x1="120" y1="716" x2="674" y2="716" stroke="{SOFT}"/>')
    b.append(t(W / 2, 748, "이 넷이 없으면 그림은 되풀이될 수 없다", 15, MUTED, "700"))

    b.append(note_box(197, 796, 400, "곡선은 결론이 아니라 요약이다", 16))
    return base("곡선 하나 뒤에 붙는 것들",
                "정리 결과와 함께 내는 것들을 한자리에 늘어놓은 그림", "".join(b))


# ── p41 ───────────────────────────────────────────────────────────────
def fig_cherry():
    b = []
    b.append(t(W / 2, 240, "같은 자료에서 반대되는 두 문장이 나온다", 16, MUTED))

    x0, y0, w, h = 150, 286, 500, 170
    b.append(axes(x0, y0, w, h))
    b.append(plot(x0, y0, w, h, SERIES, RAW, 1.8))
    b.append(t(x0 + w + 10, y0 + 10, "전체 자료", 13, RAW, "700", anchor="start"))

    peak = max(range(N), key=lambda i: SERIES[i] if 10 <= i <= 22 else -99)
    trough = min(range(N), key=lambda i: SERIES[i] if 24 <= i <= 36 else 99)
    spans = [(peak, peak + 14, "#e9d3cc", WARN), (trough, trough + 14, "#d5e0e6", CURVE)]
    for a, z, fill, col in spans:
        bx = x0 + w * a / (N - 1)
        bw = w * (z - a) / (N - 1)
        b.append(f'<rect x="{bx:.0f}" y="{y0}" width="{bw:.0f}" height="{h}" fill="{fill}" opacity="0.7"/>')

    for i, (a, z, fill, col, note) in enumerate([
            (spans[0][0], spans[0][1], spans[0][2], WARN, "이 구간만 보면 내려간다"),
            (spans[1][0], spans[1][1], spans[1][2], CURVE, "이 구간만 보면 올라간다")]):
        sx = 150 + i * 280
        sy, sw, sh = 540, 220, 140
        b.append(f'<rect x="{sx}" y="{sy}" width="{sw}" height="{sh}" fill="{fill}" opacity="0.55"/>')
        b.append(axes(sx, sy, sw, sh, left="값"))
        seg = SERIES[a:z]
        lo, hi = min(seg) - 0.3, max(seg) + 0.3
        pts = []
        for k, v in enumerate(seg):
            x = sx + sw * k / (len(seg) - 1)
            y = sy + sh - sh * (v - lo) / (hi - lo)
            pts.append((x, y))
        b.append(f'<polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in pts)}" fill="none" '
                 f'stroke="{RAW}" stroke-width="2"/>')
        b.append(f'<line x1="{pts[0][0]:.1f}" y1="{pts[0][1]:.1f}" x2="{pts[-1][0]:.1f}" '
                 f'y2="{pts[-1][1]:.1f}" stroke="{col}" stroke-width="3"/>')
        b.append(t(sx + sw / 2, sy + sh + 46, note, 14, col, "700"))

    b.append(f'<line x1="397" y1="540" x2="397" y2="700" stroke="{SOFT}"/>')
    b.append(t(397, 762, "자료는 하나다", 14, MUTED, "700"))

    b.append(note_box(147, 806, 500, "기간을 고른 이유가 없으면 결론에 맞춰 고른 것이다", 15))
    return base("고르는 방식이 결론을 만든다",
                "같은 자료에서 서로 반대되는 두 문장이 만들어지는 과정을 그린 그림", "".join(b))


FIGURES = {7: fig_baseline, 13: fig_window, 21: fig_shift,
           29: fig_break, 37: fig_publish, 41: fig_cherry}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}")


if __name__ == "__main__":
    main()
