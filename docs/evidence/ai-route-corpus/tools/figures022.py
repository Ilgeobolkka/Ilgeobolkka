#!/usr/bin/env python3
"""book-022 이미지 페이지 6개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 축에 눈금과 숫자를 넣지 않는 것이 이 책의 방침이다 — 도표가
특정 관측의 값이 아니라 양들 사이의 관계를 보여야 한다.
"""
import math
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
BLUE, ORANGE, GRAY = "#31678a", "#a4703c", "#8a9296"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="blue" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{BLUE}"/></marker>
  <marker id="gray" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{GRAY}"/></marker>
  <marker id="grayback" markerWidth="10" markerHeight="10" refX="1" refY="3" orient="auto"><path d="M9,0 L9,6 L0,3 z" fill="{GRAY}"/></marker>
</defs>
{t(W/2, 122, title, 32, '#203238', '700')}
{t(W/2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def note(y, text, size=17):
    return ('<rect x="105" y="%d" width="584" height="72" rx="16" fill="#fff8e8" stroke="#c5a866"/>' % y
            + t(W / 2, y + 44, text, size, "#51462c", "700"))


def span(x1, x2, y, label, color=GRAY, size=14, above=True):
    """양쪽 화살표와 이름표. 이름표 뒤에 흰 바탕을 깔아 선과 겹쳐도 읽히게 한다."""
    mx, my = (x1 + x2) / 2, (y - 9 if above else y + 20)
    width = size * len(label) + 10
    return (f'<line x1="{x1}" y1="{y}" x2="{x2}" y2="{y}" stroke="{color}" stroke-width="2" '
            f'marker-start="url(#grayback)" marker-end="url(#gray)"/>'
            f'<rect x="{mx - width / 2}" y="{my - size}" width="{width}" height="{size + 6}" fill="#ffffff"/>'
            + t(mx, my, label, size, color))


def wave(x0, x1, mid, amp, wavelength, step=2, phase=0.0):
    """사인 곡선 경로."""
    pts = []
    x = x0
    while x <= x1:
        y = mid - amp * math.sin(2 * math.pi * (x - x0) / wavelength + phase)
        pts.append(f"{x:.1f},{y:.1f}")
        x += step
    return "M" + " L".join(pts)


def fig_oscillation():
    """5페이지 — 시간에 따라 오르내리는 흔들림."""
    b = []
    left, right = 150, 660
    for mid, amp, wl, label in ((360, 78, 240, ""), (640, 78, 120, "")):
        b.append(f'<line x1="{left}" y1="{mid}" x2="{right}" y2="{mid}" stroke="#9fb0b6" '
                 f'stroke-width="2" stroke-dasharray="7 6"/>')
        b.append(f'<path d="{wave(left, right, mid, amp, wl)}" fill="none" stroke="{BLUE}" stroke-width="4"/>')
        b.append(t(left - 12, mid + 5, "제자리", 14, "#8a9296", "400", "end"))
        b.append(f'<line x1="{left}" y1="{mid - amp - 40}" x2="{left}" y2="{mid + amp + 40}" '
                 f'stroke="#5b6b70" stroke-width="2"/>')
    # 위 곡선: 마루 사이 간격과 폭
    top_mid, top_amp, top_wl = 360, 78, 240
    crest1, crest2 = left + top_wl / 4, left + top_wl * 5 / 4
    b.append(span(crest1, crest2, top_mid - top_amp - 26, "한 번 오가는 시간"))
    b.append(f'<line x1="{crest1}" y1="{top_mid - top_amp}" x2="{crest1}" y2="{top_mid - top_amp - 30}" '
             f'stroke="{GRAY}" stroke-width="1.5" stroke-dasharray="4 4"/>')
    b.append(f'<line x1="{crest2}" y1="{top_mid - top_amp}" x2="{crest2}" y2="{top_mid - top_amp - 30}" '
             f'stroke="{GRAY}" stroke-width="1.5" stroke-dasharray="4 4"/>')
    top_trough = left + top_wl * 3 / 4
    b.append(f'<line x1="{top_trough}" y1="{top_mid}" x2="{top_trough}" y2="{top_mid + top_amp}" '
             f'stroke="{ORANGE}" stroke-width="2.5" marker-start="url(#grayback)" marker-end="url(#gray)"/>')
    b.append(t(top_trough + 14, top_mid + top_amp / 2 + 5, "오가는 폭", 14, "#8a5a2b", "400", "start"))
    # 아래 곡선: 간격만 절반
    bot_mid, bot_amp, bot_wl = 640, 78, 120
    b.append(span(left + bot_wl / 4, left + bot_wl * 5 / 4, bot_mid - bot_amp - 26, "한 번 오가는 시간"))
    bot_trough = left + bot_wl * 3 / 4
    b.append(f'<line x1="{bot_trough}" y1="{bot_mid}" x2="{bot_trough}" y2="{bot_mid + bot_amp}" '
             f'stroke="{ORANGE}" stroke-width="2.5" marker-start="url(#grayback)" marker-end="url(#gray)"/>')
    b.append(t(bot_trough + 14, bot_mid + bot_amp / 2 + 5, "오가는 폭", 14, "#8a5a2b", "400", "start"))
    b.append(t(right + 12, 360, "시간", 15, "#5b6b70", "400", "start"))
    b.append(t(right + 12, 640, "시간", 15, "#5b6b70", "400", "start"))
    b.append(t(W / 2, 800, "폭은 같고 간격만 다르다", 18, "#3f5560", "700"))
    b.append(note(880, "자주 오가는 정도와 오가는 폭은 서로 다른 양이다"))
    return base("시간에 따라 오르내리는 흔들림", "가로가 시간, 세로가 제자리에서 벗어난 정도", "\n".join(b))


def fig_wavelength():
    """13페이지 — 파장을 재는 자리."""
    b = []
    left, right = 140, 620
    mid, amp, wl = 344, 62, 160
    b.append(t(W / 2, 244, "옆에서 본 모습", 17, "#4a5a5f", "700"))
    b.append(f'<line x1="{left}" y1="{mid}" x2="{right}" y2="{mid}" stroke="#9fb0b6" '
             f'stroke-width="2" stroke-dasharray="7 6"/>')
    b.append(f'<path d="{wave(left, right, mid, amp, wl)}" fill="none" stroke="{BLUE}" stroke-width="4"/>')
    c1, c2 = left + wl / 4, left + wl * 5 / 4
    b.append(span(c1, c2, mid - amp - 24, "파장"))
    g1, g2 = left + wl * 3 / 4, left + wl * 7 / 4
    b.append(span(g1, g2, mid + amp + 34, "파장", above=False))
    b.append(t(left - 12, mid - amp - 6, "마루", 13, GRAY, "400", "end"))
    b.append(t(left - 12, mid + amp + 14, "골", 13, GRAY, "400", "end"))
    # 위에서 본 동심원. 두 칸 모두 원이 칸 밖으로 넘치므로 각각 잘라 낸다.
    b.append(t(W / 2, 480, "위에서 내려다본 모습", 17, "#4a5a5f", "700"))
    step = 44
    for index, (bx, label, color, ratio) in enumerate(
            ((130, "지금", BLUE, 1), (420, "더 자주 흔든 경우", GRAY, 0.5))):
        bw, by, bh = 244, 510, 300
        cx, cy = bx + bw / 2, by + bh / 2
        b.append(f'<clipPath id="top{index}"><rect x="{bx}" y="{by}" width="{bw}" height="{bh}"/></clipPath>')
        b.append(f'<rect x="{bx}" y="{by}" width="{bw}" height="{bh}" fill="#fbfdfd" stroke="#dde3e5"/>')
        b.append(f'<g clip-path="url(#top{index})">')
        gap = step * ratio
        for k in range(1, 7):
            b.append(f'<circle cx="{cx}" cy="{cy}" r="{k * gap}" fill="none" stroke="{color}" stroke-width="2.5"/>')
        b.append('</g>')
        b.append(f'<circle cx="{cx}" cy="{cy}" r="5" fill="#2c3d43"/>')
        b.append(span(cx + gap, cx + 2 * gap, cy, "파장", color, 13))
        b.append(t(cx, by + bh + 26, label, 15, "#66777b"))
    b.append(note(880, "간격을 재는 자리는 마루와 마루, 또는 골과 골이다"))
    return base("파장을 재는 자리", "단면과 평면에서 같은 길이를 어디서 읽는가", "\n".join(b))


def fig_refraction():
    """19페이지 — 경계를 넘는 물결."""
    b = []
    left, right, top, bottom = 120, 560, 250, 720
    boundary = 470
    b.append(f'<rect x="{left}" y="{top}" width="{right - left}" height="{boundary - top}" fill="#f2f7fa"/>')
    b.append(f'<rect x="{left}" y="{boundary}" width="{right - left}" height="{bottom - boundary}" fill="#fbf5ec"/>')
    b.append(f'<line x1="{left}" y1="{boundary}" x2="{right}" y2="{boundary}" stroke="#5b6b70" stroke-width="3"/>')
    b.append(t(left + 8, top + 26, "깊은 곳", 15, "#4a6a7d", "400", "start"))
    b.append(t(left + 8, bottom - 12, "얕은 곳", 15, "#8a6a3c", "400", "start"))
    # 마루선은 느린 쪽에서 경계에 더 가깝게 눕고 수직 간격도 좁아진다. 경계 위의 교점 간격은 양쪽이
    # 같아야 하므로 그것을 고정하고 각도만 바꾼다 — 그러면 수직 간격은 저절로 좁아진다.
    b.append(f'<clipPath id="tank19"><rect x="{left}" y="{top}" width="{right - left}" '
             f'height="{bottom - top}"/></clipPath>')
    b.append('<g clip-path="url(#tank19)">')
    a1, a2 = math.radians(58), math.radians(31)
    for k in range(-3, 8):
        xb = left + 40 + k * 84
        up = (boundary - top) / math.sin(a1)
        down = (bottom - boundary) / math.sin(a2)
        b.append(f'<line x1="{xb - up * math.cos(a1):.1f}" y1="{top}" x2="{xb}" y2="{boundary}" '
                 f'stroke="{BLUE}" stroke-width="3"/>')
        b.append(f'<line x1="{xb}" y1="{boundary}" x2="{xb + down * math.cos(a2):.1f}" y2="{bottom}" '
                 f'stroke="{ORANGE}" stroke-width="3"/>')
    b.append('</g>')
    b.append(f'<rect x="{left}" y="{top}" width="{right - left}" height="{bottom - top}" fill="none" stroke="#dde3e5"/>')
    nx = left + 40 + 2 * 84
    b.append(f'<line x1="{nx}" y1="{top + 20}" x2="{nx}" y2="{bottom - 20}" stroke="#5b6b70" '
             f'stroke-width="2" stroke-dasharray="6 6"/>')
    for x, y, text, color in ((nx + 78, top + 42, "경계에 수직인 선", "#5b6b70"),
                              (nx - 92, boundary - 26, "들어오는 각", BLUE),
                              (nx + 70, boundary + 42, "나가는 각", "#8a5a2b")):
        width = 13 * len(text) + 10
        b.append(f'<rect x="{x - width / 2}" y="{y - 15}" width="{width}" height="21" fill="#ffffff" opacity="0.9"/>')
        b.append(t(x, y, text, 13, color))
    # 곧게 들어가는 경우
    ox, oy = 600, 250
    b.append(f'<rect x="{ox}" y="{oy}" width="94" height="{bottom - top}" fill="#f7f9fa" stroke="#dde3e5"/>')
    b.append(f'<line x1="{ox}" y1="{boundary}" x2="{ox + 94}" y2="{boundary}" stroke="#5b6b70" stroke-width="2"/>')
    for k in range(4):
        b.append(f'<line x1="{ox}" y1="{oy + 40 + k * 52}" x2="{ox + 94}" y2="{oy + 40 + k * 52}" '
                 f'stroke="{BLUE}" stroke-width="3"/>')
    for k in range(6):
        b.append(f'<line x1="{ox}" y1="{boundary + 26 + k * 34}" x2="{ox + 94}" y2="{boundary + 26 + k * 34}" '
                 f'stroke="{ORANGE}" stroke-width="3"/>')
    b.append(t(ox + 47, bottom + 26, "곧게 들어가면", 13, "#66777b"))
    b.append(t(ox + 47, bottom + 46, "꺾이지 않는다", 13, "#66777b"))
    b.append(note(860, "방향이 꺾이는 것은 비스듬함에서, 간격이 좁아지는 것은 빠르기에서 온다"))
    return base("경계를 넘는 물결", "빠르기가 다른 곳으로 넘어갈 때 방향과 간격", "\n".join(b))


def fig_interference():
    """27페이지 — 두 곳에서 퍼진 물결이 만드는 무늬."""
    b = []
    top, cy = 250, 770
    ax, bx = 300, 494
    wl = 54
    # 관측 영역 밖으로 원과 무늬가 넘치지 않도록 잘라 낸다.
    b.append(f'<clipPath id="tank"><rect x="105" y="{top}" width="584" height="{cy - top + 30}"/></clipPath>')
    b.append(f'<g clip-path="url(#tank)">')
    for cx in (ax, bx):
        for k in range(1, 16):
            b.append(f'<circle cx="{cx}" cy="{cy}" r="{k * wl}" fill="none" stroke="{BLUE}" '
                     f'stroke-width="1.5" opacity="0.5"/>')
    # 쌍곡선: 두 점에서의 거리 차가 파장의 정수배(보강) / 반파장 어긋남(상쇄)
    def branch(diff, dash):
        a = diff / 2
        c = (bx - ax) / 2
        if abs(a) >= c:
            return None
        bb = math.sqrt(c * c - a * a)
        mx = (ax + bx) / 2
        pts = []
        y = cy - 6
        while y > 258:
            dy = cy - y
            x = mx + a * math.sqrt(1 + (dy * dy) / (bb * bb))
            pts.append(f"{x:.1f},{y:.1f}")
            y -= 4
        style = f'stroke-dasharray="{dash}" ' if dash else ""
        return (f'<path d="M{" L".join(pts)}" fill="none" stroke="#2c3d43" stroke-width="3" {style}/>',
                pts[-1])
    for m in (-2, -1, 0, 1, 2):
        got = branch(m * wl, "")
        if got:
            b.append(got[0])
    for m in (-1.5, -0.5, 0.5, 1.5):
        got = branch(m * wl, "8 7")
        if got:
            b.append(got[0])
    # 온 거리의 차이 — 잘라 내는 영역 안이므로 여기서 함께 그린다.
    px, py = 596, 470
    b.append(f'<line x1="{ax}" y1="{cy}" x2="{px}" y2="{py}" stroke="{ORANGE}" stroke-width="2" stroke-dasharray="5 5"/>')
    b.append(f'<line x1="{bx}" y1="{cy}" x2="{px}" y2="{py}" stroke="{ORANGE}" stroke-width="2" stroke-dasharray="5 5"/>')
    b.append(f'<circle cx="{px}" cy="{py}" r="5" fill="#a4703c"/>')
    b.append('</g>')
    b.append(f'<rect x="105" y="{top}" width="584" height="{cy - top + 30}" fill="none" stroke="#c9d3d7"/>')
    b.append(f'<circle cx="{ax}" cy="{cy}" r="8" fill="#2c3d43"/>')
    b.append(f'<circle cx="{bx}" cy="{cy}" r="8" fill="#2c3d43"/>')
    b.append(t(ax, cy + 38, "흔드는 곳", 14, "#2c3d43", "700"))
    b.append(t(bx, cy + 38, "흔드는 곳", 14, "#2c3d43", "700"))
    b.append(t(px - 14, py - 10, "온 거리의 차이", 14, "#8a5a2b", "400", "end"))
    # 범례는 무늬와 겹치지 않도록 틀 바깥에 둔다.
    legend_y = cy + 74
    b.append(f'<line x1="150" y1="{legend_y}" x2="196" y2="{legend_y}" stroke="#2c3d43" stroke-width="3"/>')
    b.append(t(206, legend_y + 5, "크게 오르내리는 줄", 14, "#2c3d43", "400", "start"))
    b.append(f'<line x1="410" y1="{legend_y}" x2="456" y2="{legend_y}" stroke="#2c3d43" '
             f'stroke-width="3" stroke-dasharray="8 7"/>')
    b.append(t(466, legend_y + 5, "거의 움직이지 않는 줄", 14, "#5b6b70", "400", "start"))
    b.append(note(880, "무늬의 자리는 두 곳에서 온 거리의 차이로 정해진다"))
    return base("두 곳에서 퍼진 물결", "겹침의 결과가 자리마다 달라진다", "\n".join(b))


def fig_standing_wave():
    """29페이지 — 마디와 배."""
    b = []
    left, right = 140, 660
    for mid, loops, label in ((400, 3, ""), (700, 4, "")):
        wl = (right - left) / loops * 2
        amp = 70
        b.append(f'<line x1="{left}" y1="{mid}" x2="{right}" y2="{mid}" stroke="#9fb0b6" stroke-width="2"/>')
        b.append(f'<path d="{wave(left, right, mid, amp, wl)}" fill="none" stroke="{BLUE}" stroke-width="3.5"/>')
        b.append(f'<path d="{wave(left, right, mid, -amp, wl)}" fill="none" stroke="{BLUE}" stroke-width="3.5"/>')
        for k in range(loops + 1):
            x = left + k * wl / 2
            b.append(f'<circle cx="{x}" cy="{mid}" r="6" fill="#2c3d43"/>')
        if loops == 3:
            bx = left + wl / 4
            b.append(f'<line x1="{bx}" y1="{mid - amp + 6}" x2="{bx}" y2="{mid + amp - 6}" '
                     f'stroke="{ORANGE}" stroke-width="2.5" marker-start="url(#grayback)" marker-end="url(#gray)"/>')
            b.append(t(bx, mid - amp - 16, "가장 크게 움직이는 자리", 13, "#8a5a2b"))
            b.append(f'<line x1="{left}" y1="{mid + 10}" x2="{left + 46}" y2="{mid + amp + 24}" '
                     f'stroke="{GRAY}" stroke-width="1.5"/>')
            b.append(t(left + 54, mid + amp + 38, "움직이지 않는 자리", 13, "#2c3d43", "700", "start"))
            b.append(span(left + wl / 2, left + wl, mid - amp - 30, "반 파장"))
        else:
            b.append(t(W / 2, mid + amp + 46, "같은 줄에서 생기는 다른 모습", 14, "#66777b"))
    b.append(note(880, "마디의 간격이 반 파장이므로 마디를 세면 파장을 알 수 있다"))
    return base("마디와 배", "나아가지 않고 제자리에서 오르내리는 파동", "\n".join(b))


def fig_record_form():
    """36페이지 — 관측 기록 양식."""
    b = []
    # 조건 칸
    b.append(f'<rect x="105" y="250" width="270" height="180" rx="10" fill="#f7f9fa" stroke="#9fb0b6"/>')
    b.append(t(240, 284, "관측 조건", 17, "#33474d", "700"))
    for k, label in enumerate(("물 깊이", "흔드는 방식", "판의 배치")):
        y = 326 + k * 40
        b.append(t(126, y, label, 14, "#4a5a5f", "400", "start"))
        b.append(f'<line x1="228" y1="{y + 4}" x2="356" y2="{y + 4}" stroke="#b7c2c6" stroke-width="1.5"/>')
    # 수조 그림 칸
    b.append(f'<rect x="399" y="250" width="290" height="180" rx="10" fill="#ffffff" stroke="#9fb0b6"/>')
    b.append(f'<rect x="423" y="274" width="242" height="120" fill="#fbfdfd" stroke="#c9d3d7" stroke-dasharray="6 5"/>')
    b.append(t(544, 418, "본 대로 그린다", 14, "#66777b"))
    # 바꾼 것 / 달라진 것 표
    x0, y0, w, rh = 105, 480, 292, 46
    for c, name in enumerate(("바꾼 것", "달라진 것")):
        b.append(f'<rect x="{x0 + c * w}" y="{y0}" width="{w}" height="{rh}" fill="#eef2f4" stroke="#9fb0b6"/>')
        b.append(t(x0 + c * w + w / 2, y0 + 30, name, 17, "#33474d", "700"))
    for r in range(5):
        y = y0 + rh + r * rh
        for c in range(2):
            b.append(f'<rect x="{x0 + c * w}" y="{y}" width="{w}" height="{rh}" fill="#ffffff" stroke="#c9d3d7"/>')
    b.append(t(W / 2, y0 + rh * 6 + 34, "한 번에 하나만 바꾼다", 17, "#51462c", "700"))
    b.append(note(880, "조건과 그림과 변화를 한 장에 함께 남긴다"))
    return base("관측 기록 양식", "무엇을 바꿨고 무엇이 달라졌는지 짝지어 적는다", "\n".join(b))


FIGURES = {
    5: fig_oscillation,
    13: fig_wavelength,
    19: fig_refraction,
    27: fig_interference,
    29: fig_standing_wave,
    36: fig_record_form,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild022")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
