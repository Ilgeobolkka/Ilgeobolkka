#!/usr/bin/env python3
"""book-030 이미지 페이지 6개의 SVG 생성. figures021.py의 t()/base() 패턴을 따른다.

여섯 도표에서 표기를 고정한다 — 햇빛은 왼쪽에서 오는 평행한 주황 화살표, 달의 밝은 절반은 언제나
왼쪽이고 어두운 절반은 짙은 회색, 그림자는 어두운 반투명 영역이다. 어느 그림에서도 밝은 절반의
방향을 바꾸지 않는다.

사용: python3 figures030.py <출력디렉터리>
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
SUN = "#c9922f"            # 햇빛
LIT = "#f0ead9"            # 밝은 절반
DARK = "#4a5259"           # 어두운 절반
EARTH = "#3f6f8c"          # 지구
RED = "#a24f3d"            # 붉은빛·잘못 읽기 쉬운 자리
FAR = "#8b95a1"


def t(x, y, value, size=16, color=INK, weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="sun" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{SUN}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{LINE}"/></marker>
  <marker id="red" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{RED}"/></marker>
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


def arrow(x1, y1, x2, y2, color=SUN, width=2.5, marker="sun"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def sunlight(x, ys, length=52, color=SUN):
    """언제나 왼쪽에서 오는 평행한 햇빛."""
    return "".join(arrow(x, y, x + length, y, color=color) for y in ys)


def moon(cx, cy, r, lit_left=True):
    """달. 밝은 절반은 어느 그림에서나 왼쪽이다."""
    d = -1 if lit_left else 1
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{DARK}"/>'
            f'<path d="M{cx},{cy - r} A{r},{r} 0 0,{0 if lit_left else 1} {cx},{cy + r} Z" '
            f'fill="{LIT}" stroke="none"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{LINE}" stroke-width="1.5"/>')


def phase_disc(cx, cy, r, frac, waxing=True):
    """지구에서 보이는 모양. frac 0=안 보임, 1=다 참."""
    out = [f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{DARK}"/>']
    if frac <= 0.001:
        pass
    elif frac >= 0.999:
        out.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{LIT}"/>')
    else:
        k = (frac - 0.5) * 2
        rx = abs(k) * r
        sweep_outer = 1 if waxing else 0
        sweep_inner = 1 if (k >= 0) == waxing else 0
        out.append(f'<path d="M{cx},{cy - r} A{r},{r} 0 0,{sweep_outer} {cx},{cy + r} '
                   f'A{rx},{r} 0 0,{sweep_inner} {cx},{cy - r} Z" fill="{LIT}"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{LINE}" stroke-width="1.2"/>')
    return "".join(out)


# ── p5 ────────────────────────────────────────────────────────────────
def fig_three_views():
    b = []
    b.append(t(W / 2, 240, "같은 순간을 세 자리에서 보면", 16, MUTED))

    # 첫 칸 — 바깥에서. 달을 궤도 위쪽에 두어 둘째 칸의 반달과 앞뒤가 맞게 한다.
    b.append(f'<rect x="118" y="276" width="180" height="266" rx="12" fill="#fbfcfc" '
             f'stroke="{SOFT}" stroke-width="2"/>')
    b.append(sunlight(126, [356, 392, 428], 30))
    b.append(t(158, 336, "햇빛", 12, SUN, "700"))
    b.append(f'<circle cx="220" cy="410" r="20" fill="{EARTH}"/>')
    b.append(t(220, 446, "지구", 12, MUTED))
    b.append(f'<circle cx="220" cy="410" r="62" fill="none" stroke="{FAR}" stroke-dasharray="4 5"/>')
    b.append(moon(220, 348, 14))
    b.append(f'<circle cx="220" cy="360" r="3" fill="{RED}"/>')
    b.append(t(208, 308, "늘 이 면이 지구를 향한다", 11, RED, "700"))
    b.append(f'<line x1="208" y1="316" x2="218" y2="352" stroke="{RED}" stroke-width="1"/>')
    b.append(t(208, 516, "바깥에서 본 모습", 14, INK, "700"))

    # 둘째 칸 — 지구에서. 위쪽 자리의 달은 보는 사람의 왼쪽이 밝다.
    b.append(f'<rect x="308" y="276" width="180" height="266" rx="12" fill="#1e2a31" '
             f'stroke="{SOFT}" stroke-width="2"/>')
    b.append(phase_disc(398, 392, 54, 0.5, waxing=False))
    b.append(t(398, 516, "지구에서 본 모습", 14, "#e4eaed", "700"))

    # 셋째 칸 — 달 표면에서
    b.append(f'<rect x="498" y="276" width="180" height="266" rx="12" fill="#1e2a31" '
             f'stroke="{SOFT}" stroke-width="2"/>')
    b.append(f'<rect x="500" y="452" width="176" height="60" fill="#6d6a63"/>')
    b.append(f'<circle cx="556" cy="352" r="22" fill="{EARTH}"/>')
    b.append(t(556, 314, "늘 같은 자리에", 11, "#c7d4da", "700"))
    b.append(t(556, 330, "떠 있다", 11, "#c7d4da", "700"))
    b.append(f'<circle cx="638" cy="404" r="14" fill="{SUN}"/>')
    b.append(t(638, 434, "지금은 낮", 11, "#e0c489", "700"))
    b.append(t(588, 516, "달에서 본 모습", 14, "#e4eaed", "700"))

    b.append(note_box(147, 608, 500, "자리를 정하지 않으면 어느 문장도 확인할 수 없다", 15))
    b.append(caption(W / 2, 712, ["세 칸은 같은 순간이다. 배치는 하나이고 그것을 보는 자리가 셋이다."]))
    return base("같은 순간, 세 가지 모습",
                "같은 순간을 세 자리에서 본 모습을 나란히 그린 그림", "".join(b))


# ── p10 ───────────────────────────────────────────────────────────────
def fig_phases():
    b = []
    b.append(t(W / 2, 240, "여덟 자리에서 달의 밝은 절반은 모두 같다", 16, MUTED))

    cx, cy, orb = 400, 560, 178
    b.append(sunlight(112, [470, 520, 570, 620, 670], 62))
    b.append(t(148, 448, "햇빛", 13, SUN, "700"))

    b.append(f'<circle cx="{cx}" cy="{cy}" r="{orb}" fill="none" stroke="{FAR}" stroke-dasharray="5 6"/>')
    b.append(f'<circle cx="{cx}" cy="{cy}" r="30" fill="{EARTH}"/>')
    b.append(t(cx, cy + 52, "지구", 13, MUTED, "600"))

    fracs = [0.0, 0.25, 0.5, 0.75, 1.0, 0.75, 0.5, 0.25]
    # 위쪽 자리에서는 보는 사람의 왼쪽이 해 쪽이고 아래쪽 자리에서는 오른쪽이 해 쪽이다.
    # 그래서 밝은 쪽이 위아래 절반에서 서로 반대로 그려져야 한다.
    waxing = [True, False, False, False, True, True, True, True]
    for i in range(8):
        a = math.radians(180 - i * 45)
        mx = cx + orb * math.cos(a)
        my = cy - orb * math.sin(a)
        b.append(moon(mx, my, 17))
        ox = cx + (orb + 74) * math.cos(a)
        oy = cy - (orb + 74) * math.sin(a)
        b.append(phase_disc(ox, oy, 21, fracs[i], waxing[i]))

    b.append(t(cx, cy - orb - 118, "바깥 원은 그 자리에서 지구가 보는 모양", 13, MUTED))

    b.append(note_box(197, 900, 400, "달의 밝은 절반은 여덟 자리에서 모두 같다", 15))
    return base("배치가 모양을 정한다",
                "배치와 보이는 모양을 한 화면에 나란히 둔 그림", "".join(b))


# ── p18 ───────────────────────────────────────────────────────────────
def fig_lock():
    b = []
    b.append(t(W / 2, 240, "도는 속도가 늦춰져 멈추기까지", 16, MUTED))

    ex, mx = 186, 470
    for cy, deg, label in [(340, 30, "빠르게 돌아 앞서 나갔다"),
                           (548, 13, "느려졌다"),
                           (756, 0, "어긋남이 없어 멈춘다")]:
        b.append(f'<circle cx="{ex}" cy="{cy}" r="20" fill="{EARTH}"/>')
        b.append(t(ex, cy + 40, "지구", 12, MUTED))
        b.append(f'<line x1="{ex + 24}" y1="{cy}" x2="{mx + 92}" y2="{cy}" stroke="{FAR}" '
                 f'stroke-dasharray="5 6"/>')
        # 달은 지구 쪽으로 길쭉해진다. 길쭉한 방향이 앞서 나간 정도를 각도로 보인다.
        b.append(f'<ellipse cx="{mx}" cy="{cy}" rx="88" ry="46" fill="{LIT}" stroke="{LINE}" '
                 f'stroke-width="2" transform="rotate({-deg} {mx} {cy})"/>')
        a = math.radians(deg)
        b.append(f'<line x1="{mx - 88 * math.cos(a):.1f}" y1="{cy + 88 * math.sin(a):.1f}" '
                 f'x2="{mx + 88 * math.cos(a):.1f}" y2="{cy - 88 * math.sin(a):.1f}" '
                 f'stroke="{RED}" stroke-width="2.5"/>')
        b.append(t(mx, cy + 74, "달", 12, MUTED))
        if deg:
            r0 = 118
            b.append(f'<path d="M{mx + r0:.1f},{cy} A{r0},{r0} 0 0,0 '
                     f'{mx + r0 * math.cos(a):.1f},{cy - r0 * math.sin(a):.1f}" '
                     f'fill="none" stroke="{RED}" stroke-width="2"/>')
            hx = mx + r0 * math.cos(a * 0.15)
            hy = cy - r0 * math.sin(a * 0.15)
            b.append(f'<path d="M{hx:.1f},{hy + 2:.1f} L{hx - 4:.1f},{hy - 12:.1f} '
                     f'L{hx + 9:.1f},{hy - 8:.1f} Z" fill="{RED}"/>')
            b.append(t(mx + r0 + 16, cy - r0 * math.sin(a) / 2, "되돌리는 힘", 13, RED, "700",
                       anchor="start"))
        b.append(t(mx, cy + 104, label, 15, INK, "700"))

    b.append(t(ex + 130, 296, "지구를 향한 선", 12, FAR, "600"))
    b.append(t(mx + 110, 756 - 22, "길쭉해진 방향", 12, RED, "700", anchor="start"))

    b.append(note_box(197, 900, 400, "길쭉해진 방향이 지구를 향할 때까지 늦춰진다", 15))
    return base("앞서 나가면 되돌려진다",
                "도는 속도가 늦춰져 멈추는 과정을 세 단계로 그린 그림", "".join(b))


# ── p25 ───────────────────────────────────────────────────────────────
def fig_tilt():
    b = []
    b.append(t(W / 2, 240, "달이 도는 길이 지구가 도는 평면에 조금 기울어 있다", 16, MUTED))

    ex = 300
    rx, ry, tilt = 210, 40, 12
    # 기울어진 타원이 가로선과 만나는 두 자리를 계산해 표시한다. 눈대중으로 찍으면 어긋난다.
    th = math.radians(tilt)
    tpar = math.atan2(-rx * math.tan(th), ry)
    nx = abs(rx * math.cos(tpar) * math.cos(th) - ry * math.sin(tpar) * math.sin(th))

    for cy, mx, my, label in [(400, ex + rx * math.cos(th), -rx * math.sin(th),
                               "위상은 맞지만 그림자를 비껴 지나간다"),
                              (712, ex + nx, 0.0, "만나는 자리에 와서 그림자에 들어간다")]:
        b.append(sunlight(118, [cy - 46, cy, cy + 46], 42))
        b.append(f'<line x1="180" y1="{cy}" x2="716" y2="{cy}" stroke="{FAR}" stroke-dasharray="5 6"/>')
        b.append(f'<path d="M{ex + 26},{cy - 24} L716,{cy - 7} L716,{cy + 7} L{ex + 26},{cy + 24} Z" '
                 f'fill="{DARK}" opacity="0.35"/>')
        b.append(t(640, cy + 34, "지구의 그림자", 12, MUTED))
        b.append(f'<ellipse cx="{ex}" cy="{cy}" rx="{rx}" ry="{ry}" fill="none" stroke="{LINE}" '
                 f'stroke-width="2" transform="rotate({-tilt} {ex} {cy})"/>')
        b.append(t(ex - 84, cy - 62, "달이 도는 길", 12, INK, "600"))
        b.append(t(196, cy + 20, "지구가 도는 평면", 12, FAR, "600", anchor="start"))
        b.append(f'<circle cx="{ex}" cy="{cy}" r="24" fill="{EARTH}"/>')
        b.append(t(ex, cy + 48, "지구", 12, MUTED))
        for sx in (-1, 1):
            b.append(f'<circle cx="{ex + sx * nx:.0f}" cy="{cy}" r="5" fill="{RED}"/>')
        b.append(t(ex + nx, cy + 24, "만나는 자리", 11, RED, "700"))
        b.append(moon(mx, cy + my, 18))
        b.append(t(mx, cy + my - 40, label, 13, INK, "700"))

    b.append(note_box(197, 900, 400, "위상만으로는 모자라고 자리까지 맞아야 한다", 15))
    return base("기울어 있어 대개 빗나간다",
                "달이 도는 길이 기울어 있어 그림자가 빗나가는 모습을 그린 그림", "".join(b))


# ── p36 ───────────────────────────────────────────────────────────────
def fig_month():
    b = []
    b.append(t(W / 2, 240, "한 달치 그림을 날짜 순서로 늘어놓으면", 16, MUTED))

    x0, y0, gap = 168, 300, 76
    missing = {8, 9, 10, 17, 25, 26}
    for i in range(30):
        r_, c = divmod(i, 6)
        cx = x0 + c * gap
        cy = y0 + r_ * gap
        if i in missing:
            b.append(f'<circle cx="{cx}" cy="{cy}" r="24" fill="none" stroke="{FAR}" '
                     f'stroke-dasharray="3 4"/>')
            for k in range(-2, 3):
                b.append(f'<line x1="{cx - 17 + k * 7}" y1="{cy + 17}" x2="{cx + 3 + k * 7}" '
                         f'y2="{cy - 17}" stroke="{SOFT}"/>')
        else:
            f = i / 15 if i <= 15 else (30 - i) / 15
            b.append(phase_disc(cx, cy, 24, max(0.0, min(1.0, f)), waxing=i <= 15))
    for i, lab in [(8, "흐림"), (17, "비"), (25, "새벽")]:
        r_, c = divmod(i, 6)
        b.append(t(x0 + c * gap, y0 + r_ * gap + 42, lab, 11, FAR))

    bx = x0 + 6 * gap - 12
    b.append(f'<rect x="{bx}" y="{y0 - 30}" width="88" height="{4 * gap + 60}" rx="10" '
             f'fill="#fbfcfc" stroke="{SOFT}"/>')
    b.append(t(bx + 44, y0 - 12, "뜨는 시각", 12, MUTED, "700"))
    for r_ in range(5):
        b.append(t(bx + 44, y0 + r_ * gap + 8, f"{6 + r_ * 4}시", 12, INK))

    b.append(caption(W / 2, y0 + 5 * gap + 4,
                     ["빗금 친 자리는 관측하지 못한 날이고 그 아래에 까닭을 적었다.",
                      "빈칸이 새벽에 몰렸다 — 다음 달에는 그 며칠을 나눠 맡는다"]))

    b.append(note_box(197, y0 + 5 * gap + 74, 400, "빈칸도 자료다", 16))
    return base("한 달을 늘어놓으면",
                "한 달치 관측 기록을 늘어놓은 모습을 그린 그림", "".join(b))


# ── p40 ───────────────────────────────────────────────────────────────
def fig_far_side():
    b = []
    b.append(t(W / 2, 240, "밝은 절반과 지구를 향한 절반은 다른 기준이다", 16, MUTED))

    for ox, moon_x, tag1, tag2 in [
            (206, -1, "두 절반이 어긋난다 — 지구에서는 안 보인다", "이때 뒷면은 한낮이다"),
            (588, 1, "두 절반이 겹친다 — 지구에서는 다 찬 달", "이때 뒷면은 한밤이다")]:
        cy = 440
        b.append(f'<rect x="{ox - 186}" y="290" width="372" height="336" rx="12" fill="#fbfcfc" '
                 f'stroke="{SOFT}" stroke-width="2"/>')
        b.append(sunlight(ox - 176, [cy - 40, cy, cy + 40], 34))
        b.append(f'<circle cx="{ox}" cy="{cy}" r="26" fill="{EARTH}"/>')
        b.append(t(ox, cy + 48, "지구", 12, MUTED))
        mx = ox + moon_x * 104
        b.append(moon(mx, cy, 30))
        # 이름표는 달의 위아래에 둔다. 비스듬히 두면 칸 밖으로 나간다.
        b.append(arrow(mx, cy - 52, mx - 12, cy - 28, color=RED, width=2, marker="red"))
        b.append(t(mx, cy - 62, "해를 향한 절반", 11, RED, "700"))
        # 지구를 향한 절반은 언제나 지구가 있는 쪽이다.
        side = -moon_x
        b.append(arrow(mx, cy + 56, mx + side * 12, cy + 30, color=EARTH, width=2, marker="gray"))
        b.append(t(mx, cy + 76, "지구를 향한 절반", 11, EARTH, "700"))
        b.append(t(ox, 570, tag1, 13, INK, "700"))
        b.append(t(ox, 596, tag2, 12, MUTED))

    b.append(note_box(197, 692, 400, "보이지 않는 면은 어두운 면이 아니다", 15))
    b.append(caption(W / 2, 796, ["두 칸에서 달의 밝은 절반은 똑같이 왼쪽이다.",
                                  "달라진 것은 달이 지구의 어느 쪽에 있는가뿐이다."]))
    return base("밝은 절반과 보이는 절반은 다르다",
                "두 절반이 겹치고 어긋나는 두 경우를 나란히 그린 그림", "".join(b))


FIGURES = {5: fig_three_views, 10: fig_phases, 18: fig_lock,
           25: fig_tilt, 36: fig_month, 40: fig_far_side}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    for page, fn in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(fn(), encoding="utf-8")
        print(f"{path}")


if __name__ == "__main__":
    main()
