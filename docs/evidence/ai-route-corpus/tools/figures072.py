#!/usr/bin/env python3
"""book-072 이미지 페이지 4개의 SVG 생성.

원고의 [도표] 명세를 그대로 옮긴다. 이 책의 도표에는 건수와 비율을 눈금으로 넣지 않는다 — 자료의
분포는 시스템마다 다르고, 숫자를 넣으면 본문에 없는 기준이 생긴다.

사용: python3 figures072.py [출력디렉터리]
"""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"
INK, FADE = "#2f3d46", "#96a0a6"
BOX = "#c3ccd0"
MARK = "#a8443a"
TONE = ("#4a5c73", "#6f8a6a", "#8a6a44")


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def box(x, y, w, h, fill="#ffffff", stroke=BOX, width=1.4, dash=None):
    extra = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="4" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="{width}"{extra}/>')


def arrow(x1, y1, x2, y2, color=INK, width=1.8, marker="ink", dash=None):
    extra = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{width}" marker-end="url(#{marker})"{extra}/>')


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


def fig_inputs():
    """1.6 — 데이터가 들어오는 자리."""
    b = []
    names = ["화면 입력", "다른 시스템", "파일 읽기", "저장소 수정"]
    bx, bw, bh = 88, 156, 54
    ys = [270, 350, 430, 520]
    for name, y in zip(names, ys):
        b.append(box(bx, y, bw, bh))
        b.append(t(bx + bw / 2, y + 34, name, 16, INK, "700"))
    gate_x = 384
    b.append(f'<line x1="{gate_x}" y1="240" x2="{gate_x}" y2="580" stroke="{INK}" stroke-width="5"/>')
    b.append(t(gate_x, 224, "확인선", 18, INK, "700"))
    for y in ys[:3]:
        b.append(arrow(bx + bw + 6, y + 27, gate_x - 10, 420, FADE, 1.6, "fade"))
    b.append(box(470, 330, 220, 180))
    b.append(t(580, 400, "확인된 값", 19, INK, "700"))
    b.append(t(580, 430, "안쪽 코드는 다시", 14, FADE))
    b.append(t(580, 452, "의심하지 않는다", 14, FADE))
    b.append(f'<path d="M{bx + bw + 6},{ys[3] + 27} C300,660 430,660 466,516" fill="none" '
             f'stroke="{MARK}" stroke-width="1.8" stroke-dasharray="6 4" marker-end="url(#mark)"/>')
    b.append(t(360, 690, "이 통로는 확인선을 지나지 않는다", 15, MARK, "700"))
    b.append(bottom(760, "빠진 통로는 확인선 밖에서 들어온다"))
    return base("데이터가 들어오는 자리", "통로는 여럿이고 확인선은 하나다", "\n".join(b))


def fig_stages():
    """3.3 — 검증 단계의 순서."""
    b = []
    steps = [
        ("있는가", "항목이 비었다", False),
        ("종류가 맞는가", "숫자 자리에 글자", False),
        ("범위에 드는가", "수량이 영보다 작다", False),
        ("업무에서 말이 되는가", "이미 처리된 주문이다", True),
    ]
    bx, bw, bh = 128, 240, 66
    b.append(t(548, 238, "여기서 걸린 예", 15, FADE, "700"))
    for k, (name, example, dashed) in enumerate(steps):
        y = 258 + k * 104
        b.append(box(bx, y, bw, bh, "#ffffff", BOX, 1.4, "5 4" if dashed else None))
        b.append(t(bx + bw / 2, y + 41, name, 17, INK, "700"))
        b.append(arrow(bx + bw + 6, y + bh / 2, 424, y + bh / 2, FADE, 1.4, "fade"))
        b.append(box(432, y + 8, 232, 50, "#f6f8f8", BOX, 1.0))
        b.append(t(548, y + 39, example, 14, "#5d6c74"))
        if k:
            b.append(arrow(bx + bw / 2, y - 32, bx + bw / 2, y - 6))
    b.append(t(248, 660, "이 단계만 저장된", 14, MARK, "700"))
    b.append(t(248, 682, "기록을 함께 본다", 14, MARK, "700"))
    b.append(bottom(740, "앞 세 단계는 값만 보고 판단한다"))
    return base("검증 단계의 순서", "앞 단계를 지나야 다음 조건을 물을 수 있다", "\n".join(b))


def fig_cleanup():
    """4.4 — 정제 전후의 자료."""
    b = []
    lx, rx, cw = 130, 460, 200
    b.append(t(lx + cw / 2, 250, "받은 그대로", 19, INK, "700"))
    b.append(t(rx + cw / 2, 250, "정제한 뒤", 19, INK, "700"))
    b.append(box(lx, 272, cw, 320))
    b.append(box(rx, 272, cw, 320))
    rows = ["전화번호", "날짜", "수량", "비고"]
    for k, name in enumerate(rows):
        y = 300 + k * 78
        b.append(t(lx - 12, y + 34, name, 15, "#5d6c74", "400", "end"))
        if k == 0:  # 붙임표가 섞인 표기 → 이어 붙인 표기
            for i, seg in enumerate((54, 60, 58)):
                b.append(f'<rect x="{lx + 18 + i * 62}" y="{y + 22}" width="{seg}" height="14" '
                         f'rx="3" fill="{TONE[0]}" opacity="0.6"/>')
            b.append(f'<rect x="{rx + 18}" y="{y + 22}" width="146" height="14" rx="3" '
                     f'fill="{TONE[0]}" opacity="0.6"/>')
        elif k == 1:  # 두 가지 차례 → 하나의 차례
            for i, w in enumerate((40, 70, 46)):
                off = (0, 46, 122)[i]
                b.append(f'<rect x="{lx + 18 + off}" y="{y + 22}" width="{w}" height="14" rx="3" '
                         f'fill="{TONE[(i + 1) % 3]}" opacity="0.6"/>')
            for i, w in enumerate((64, 42, 36)):
                off = (0, 70, 118)[i]
                b.append(f'<rect x="{rx + 18 + off}" y="{y + 22}" width="{w}" height="14" rx="3" '
                         f'fill="{TONE[(2 - i) % 3]}" opacity="0.6"/>')
        elif k == 2:  # 앞뒤 공백 → 공백 없음
            b.append(f'<rect x="{lx + 62}" y="{y + 22}" width="76" height="14" rx="3" '
                     f'fill="{TONE[2]}" opacity="0.6"/>')
            b.append(f'<rect x="{rx + 18}" y="{y + 22}" width="76" height="14" rx="3" '
                     f'fill="{TONE[2]}" opacity="0.6"/>')
        else:  # 비고는 그대로
            for x in (lx, rx):
                b.append(f'<rect x="{x + 18}" y="{y + 22}" width="140" height="14" rx="3" '
                         f'fill="{FADE}" opacity="0.5"/>')
        if k < 3:
            b.append(f'<circle cx="{rx + cw - 14}" cy="{y + 29}" r="5" fill="{MARK}"/>')
    b.append(t(rx + cw + 16, 534, "뜻이 갈릴 수 있어", 13, FADE, "400", "start"))
    b.append(t(rx + cw + 16, 554, "그대로 두었다", 13, FADE, "400", "start"))
    b.append(arrow(lx + cw + 14, 432, rx - 14, 432, INK, 2.6))
    b.append(t((lx + cw + rx) / 2, 396, "되돌릴 수 있는", 13, FADE))
    b.append(t((lx + cw + rx) / 2, 414, "것만 옮긴다", 13, FADE))
    b.append(bottom(660, "고친 자리는 표시로 남긴다"))
    return base("정제 전후의 자료", "뜻이 그대로인 것만 고친다", "\n".join(b))


def fig_pipeline():
    """5.4 — 파이프라인 단계와 자료 상태."""
    b = []
    names = ["받기", "형식 확인", "정제", "업무 확인"]
    states = ["받은 그대로", "형식을 확인한 자료", "정제를 마친 자료"]
    bx, bw, bh, gap, y = 60, 120, 74, 36, 300
    for k, name in enumerate(names):
        x = bx + k * (bw + gap)
        b.append(box(x, y, bw, bh))
        b.append(t(x + bw / 2, y + 44, name, 16, INK, "700"))
        if k:
            mx = x - gap
            b.append(arrow(mx + 4, y + bh / 2, x - 6, y + bh / 2, FADE, 1.6, "fade"))
            label = states[k - 1]
            b.append(t(mx + gap / 2 - 14, y - 14, label, 12, "#5d6c74"))
    right = bx + 4 * bw + 3 * gap
    b.append(arrow(right + 6, y + bh / 2, right + 62, y + bh / 2, FADE, 1.6, "fade"))
    b.append(t(right + 34, y - 26, "업무까지", 12, "#5d6c74"))
    b.append(t(right + 34, y - 8, "확인한 자료", 12, "#5d6c74"))
    pool_x, pool_y, pool_w, pool_h = 210, 520, 330, 76
    b.append(box(pool_x, pool_y, pool_w, pool_h, "#f6f8f8", MARK, 1.2, "6 4"))
    b.append(t(pool_x + pool_w / 2, pool_y + 46, "걸린 자료를 모으는 자리", 17, MARK, "700"))
    for k in (1, 3):
        x = bx + k * (bw + gap) + bw / 2
        b.append(f'<path d="M{x},{y + bh + 6} C{x},{pool_y - 40} {pool_x + pool_w / 2},'
                 f'{pool_y - 40} {pool_x + pool_w / 2},{pool_y - 8}" fill="none" '
                 f'stroke="{MARK}" stroke-width="1.6" marker-end="url(#mark)"/>')
    b.append(bottom(680, "걸린 자료는 버리지 않고 한자리에 모은다"))
    return base("파이프라인 단계와 자료 상태", "상태의 이름이 곧 그 단계의 보장이다", "\n".join(b))


FIGURES = {
    7: fig_inputs,
    18: fig_stages,
    26: fig_cleanup,
    33: fig_pipeline,
}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pdfbuild072")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
