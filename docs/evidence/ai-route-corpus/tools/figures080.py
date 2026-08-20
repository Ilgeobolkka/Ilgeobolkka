#!/usr/bin/env python3
"""book-080 이미지 페이지 4개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 시간과 비율을 눈금으로 넣지 않는다 — 알맞은
값은 서비스마다 달라 숫자를 넣으면 본문에 없는 기준이 생긴다.

사용: python3 figures080.py [출력디렉터리]
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
BOX = "#c3ccd0"
MARK = "#a8443a"
SAFE = "#6f8a6a"
STEP = "#4a5c73"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def box(x, y, w, h, fill="#ffffff", stroke=BOX, width=1.4, dash=None):
    extra = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="4" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="{width}"{extra}/>')


def arrow(x1, y1, x2, y2, color=INK, width=1.8, marker="ink"):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"/>')


def base(title, lead, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="fade" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{FADE}"/></marker>
  <marker id="mark" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{MARK}"/></marker>
  <marker id="ink" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="{INK}"/></marker>
</defs>
{t(W / 2, 120, title, 31, '#1e2c33', '700')}
<line x1="105" y1="150" x2="689" y2="150" stroke="#dde2e4"/>
{t(W / 2, 182, lead, 16, '#71818a')}
{body}
</svg>'''


def bottom(y, text, size=16):
    return ('<line x1="105" y1="%d" x2="689" y2="%d" stroke="#dde2e4"/>' % (y, y)
            + t(W / 2, y + 38, text, size, "#4c5b64", "700"))


def fig_stages():
    """1.6 — 한 번의 배포가 지나는 단계."""
    b = []
    steps = [("점검", "점검표"), ("올리기", "올라갔는가"), ("일부 공개", "일부의 반응"),
             ("전체 공개", "전체의 반응"), ("관찰", "지표")]
    x0, y0, bw, bh, gap = 78, 330, 118, 66, 12
    for k, (name, watch) in enumerate(steps):
        x = x0 + k * (bw + gap)
        b.append(t(x + bw / 2, y0 - 16, watch, 12, FADE))
        b.append(box(x, y0, bw, bh, "#f6f8f8"))
        b.append(t(x + bw / 2, y0 + 40, name, 16, INK, "700"))
        if k:
            b.append(arrow(x - gap + 1, y0 + bh / 2, x - 3, y0 + bh / 2, FADE, 1.4, "fade"))
    total = 5 * bw + 4 * gap
    band_y = y0 + bh + 34
    safe_w = total * 0.78
    b.append(f'<rect x="{x0}" y="{band_y}" width="{safe_w}" height="30" rx="4" fill="{SAFE}" '
             f'opacity="0.3"/>')
    b.append(t(x0 + safe_w / 2, band_y + 20, "되돌릴 수 있다", 14, "#3f5c46", "700"))
    b.append(f'<rect x="{x0 + safe_w}" y="{band_y}" width="{total - safe_w}" height="30" rx="4" '
             f'fill="{MARK}" opacity="0.28"/>')
    b.append(t(x0 + safe_w + (total - safe_w) / 2, band_y + 50, "되돌려도", 11, MARK, "700"))
    b.append(t(x0 + safe_w + (total - safe_w) / 2, band_y + 66, "남는 것", 11, MARK, "700"))
    cx = x0 + 2 * (bw + gap) + bw / 2
    b.append(arrow(cx, band_y + 96, cx, band_y + 42, MARK, 1.6, "mark"))
    b.append(t(cx, band_y + 118, "여기서 멈추고 되돌리는 일이", 12, MARK, "700"))
    b.append(t(cx, band_y + 134, "가장 많다", 12, MARK, "700"))
    b.append(bottom(620, "단계를 나누면 멈출 자리가 생긴다"))
    return base("한 번의 배포가 지나는 단계", "되돌릴 수 있는 자리가 어디까지인지 표시했다",
                "\n".join(b))


def fig_checklist():
    """2.5 — 배포 전 점검표."""
    b = []
    rows = [("확인이 모두 통과했는가", "기계"), ("설정이 환경마다 갖춰졌는가", "기계"),
            ("저장 구조 변경이 있는가", "사람"), ("되돌릴 방법이 준비되었는가", "사람"),
            ("바깥과의 약속이 바뀌는가", "사람"), ("올려도 되는 때인가", "사람")]
    x0, y0, w, rh = 150, 270, 400, 54
    b.append(t(x0 + 10, y0 - 14, "점검 항목", 13, FADE, "700", "start"))
    b.append(t(x0 + w + 60, y0 - 14, "확인 주체", 13, FADE, "700"))
    for k, (item, who) in enumerate(rows):
        y = y0 + k * rh
        auto = who == "기계"
        b.append(box(x0, y, w, rh - 6, "#f2f5f5" if auto else "#ffffff"))
        b.append(t(x0 + 20, y + 33, item, 15, INK if not auto else "#6b767c", "700", "start"))
        b.append(box(x0 + w + 16, y, 88, rh - 6, "#ffffff", BOX, 1.0))
        b.append(t(x0 + w + 60, y + 33, who, 14, "#6b767c" if auto else INK, "700"))
    b.append(t(x0 + w + 120, y0 + rh, "사람이 다시", 11, FADE, "400", "start"))
    b.append(t(x0 + w + 120, y0 + rh + 16, "보지 않는다", 11, FADE, "400", "start"))
    b.append(t(397, y0 + 6 * rh + 30, "항목이 늘면 지켜지지 않는다", 12, FADE))
    b.append(bottom(660, "사람의 목록에는 판단이 필요한 것만 남긴다"))
    return base("배포 전 점검표", "기계가 보는 것과 사람이 보는 것을 나눠 적는다", "\n".join(b))


def fig_rollout():
    """3.5 — 단계적 배포의 진행."""
    b = []
    stages = [("내부 사용자", 0.18, "눈에 띄는 오류"), ("고르게 뽑은 일부", 0.38, "지표의 변화"),
              ("절반", 0.62, "부하와 지연"), ("전체", 1.0, "전체 지표")]
    x0, base_y, bw, gap, maxh = 120, 560, 110, 46, 230
    for k, (name, ratio, watch) in enumerate(stages):
        x = x0 + k * (bw + gap)
        h = maxh * ratio
        b.append(f'<rect x="{x}" y="{base_y - h}" width="{bw}" height="{h}" rx="4" '
                 f'fill="{STEP}" opacity="0.5"/>')
        b.append(t(x + bw / 2, base_y + 24, name, 13, INK, "700"))
        b.append(t(x + bw / 2, base_y - h - 14, watch, 11, FADE))
        if k:
            b.append(arrow(x - gap + 4, base_y - 20, x - 6, base_y - 20, FADE, 1.4, "fade"))
            b.append(t(x - gap / 2, base_y - 34, "넓힌다", 10, FADE))
    x2 = x0 + 1 * (bw + gap) + bw / 2
    b.append(arrow(x2, base_y + 46, x2, base_y + 86, MARK, 1.6, "mark"))
    b.append(t(x2 + 8, base_y + 106, "멈추고 되돌린다", 12, MARK, "700", "start"))
    b.append(f'<path d="M{x2},{base_y + 96} C{x2 - 60},{base_y + 130} {x0 + bw / 2 - 40},'
             f'{base_y + 130} {x0 + bw / 2},{base_y + 40}" fill="none" stroke="{MARK}" '
             f'stroke-width="1.4" stroke-dasharray="5 4"/>')
    b.append(bottom(740, "넓히는 길과 되돌리는 길이 모두 있어야 한다"))
    return base("단계적 배포의 진행", "넓히는 중에도 언제든 멈출 수 있어야 한다", "\n".join(b))


def fig_after():
    """4.4 — 배포 직후에 보는 것들."""
    b = []
    rows = [("새 판이 올라갔는가", "곧바로", False),
            ("요청을 받을 준비가 되었는가", "수십 초", False),
            ("대표 흐름이 끝까지 되는가", "수 분", True),
            ("지표가 평소와 같은가", "수십 분", False)]
    x0, y0, bw, bh, gap = 180, 270, 340, 66, 44
    for k, (name, when, warn) in enumerate(rows):
        y = y0 + k * (bh + gap)
        b.append(box(x0, y, bw, bh, "#f6f8f8"))
        b.append(t(x0 + bw / 2, y + 40, name, 15, INK, "700"))
        b.append(t(x0 + bw + 20, y + 40, when, 13, "#5d6c74", "400", "start"))
        if k:
            b.append(arrow(x0 + bw / 2, y - gap + 6, x0 + bw / 2, y - 6, FADE, 1.4, "fade"))
            b.append(t(x0 + bw / 2 + 96, y - gap / 2 + 4, "앞이 되어야 다음을 본다", 11, FADE))
        if warn:
            b.append(f'<rect x="{x0 - 14}" y="{y}" width="8" height="{bh}" rx="3" fill="{MARK}" '
                     f'opacity="0.6"/>')
            b.append(t(x0 - 24, y + 40, "가장 자주 생략된다", 11, MARK, "700", "end"))
    last_y = y0 + 4 * (bh + gap)
    b.append(box(x0, last_y, bw, bh - 12, "#ffffff", MARK, 1.0, "5 4"))
    b.append(t(x0 + bw / 2, last_y + 34, "며칠 뒤에야 보이는 것", 14, MARK, "700"))
    b.append(bottom(806, "보는 순서가 곧 멈출 자리의 순서다"))
    return base("배포 직후에 보는 것들", "올라간 것과 도는 것은 다른 물음이다", "\n".join(b))


FIGURES = {
    7: fig_stages,
    13: fig_checklist,
    20: fig_rollout,
    26: fig_after,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild080")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
