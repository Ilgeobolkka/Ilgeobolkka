#!/usr/bin/env python3
"""book-042 이미지 페이지 4개의 SVG 생성."""
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"


def head(title, subtitle):
    return f'''<text x="{W/2}" y="150" text-anchor="middle" font-size="34" font-weight="700">{title}</text>
<text x="{W/2}" y="196" text-anchor="middle" font-size="20" fill="#555">{subtitle}</text>'''


def svg(body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="a" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
    <path d="M0,0 L0,6 L9,3 z" fill="#222"/></marker>
</defs>
{body}
</svg>'''


def table(rows, x0, y0, cw, highlight=None):
    out = []
    for r, row in enumerate(rows):
        y = y0 + r * 50
        out.append(f'<rect x="{x0}" y="{y}" width="{sum(cw)}" height="50" fill="{"#eef2f6" if r==0 else "#fff"}" stroke="#889"/>')
        cx = x0
        for c, cell in enumerate(row):
            fill = "#fff3cd" if (highlight and (r, c) in highlight) else "none"
            out.append(f'<rect x="{cx}" y="{y}" width="{cw[c]}" height="50" fill="{fill}"/>')
            out.append(f'<text x="{cx+cw[c]/2}" y="{y+32}" text-anchor="middle" font-size="16" '
                       f'font-weight="{"700" if r==0 or (highlight and (r,c) in highlight) else "400"}">{cell}</text>')
            cx += cw[c]
            if c < len(row) - 1:
                out.append(f'<line x1="{cx}" y1="{y}" x2="{cx}" y2="{y+50}" stroke="#889"/>')
    return "\n".join(out)


# ── p8: 1.4 거래비용 세 갈래 도표 ────────────────────────────────
def fig_three_costs():
    b = [head("거래비용의 세 단계", "1장 · 거래 전·중·후에 걸친 비용")]
    stages = [("거래 전", "정보 탐색 비용", "값과 품질을 안다"),
              ("거래 중", "상대 확인 비용", "믿을 수 있는지"),
              ("거래 후", "약속 이행 보장 비용", "지킬지 지켜본다")]
    bw, gap = 180, 60
    x0 = (W - (bw * 3 + gap * 2)) / 2
    y = 320
    for i, (top, mid, bot) in enumerate(stages):
        x = x0 + i * (bw + gap)
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="180" rx="10" fill="#f4f6f8" stroke="#334" stroke-width="2"/>')
        b.append(f'<text x="{x+bw/2}" y="{y+34}" text-anchor="middle" font-size="15" fill="#778">{top}</text>')
        b.append(f'<text x="{x+bw/2}" y="{y+80}" text-anchor="middle" font-size="18" font-weight="700">{mid}</text>')
        b.append(f'<text x="{x+bw/2}" y="{y+130}" text-anchor="middle" font-size="14" fill="#556">({bot})</text>')
        if i > 0:
            xp = x0 + (i - 1) * (bw + gap)
            b.append(f'<line x1="{xp+bw+8}" y1="{y+90}" x2="{x-8}" y2="{y+90}" stroke="#222" stroke-width="2.5" marker-end="url(#a)"/>')
    by = y + 220
    b.append(f'<rect x="{x0}" y="{by}" width="{bw*3+gap*2}" height="40" rx="8" fill="#eef2f6" stroke="#889"/>')
    b.append(f'<text x="{W/2}" y="{by+27}" text-anchor="middle" font-size="16">거래 전체의 비용 = 세 비용의 합</text>')
    by2 = by + 90
    b.append(f'<rect x="{x0}" y="{by2}" width="{bw*3+gap*2}" height="70" rx="8" fill="#fff8e6" stroke="#caa44a" stroke-width="2"/>')
    b.append(f'<text x="{W/2}" y="{by2+42}" text-anchor="middle" font-size="17" font-weight="700">이 비용이 물건값보다 크면 거래가 일어나지 않는다</text>')
    return svg("\n".join(b))


# ── p21: 3.3 계약 집행 비용 도표 ─────────────────────────────────
def fig_contract_cost():
    b = [head("거래 규모와 계약의 실효성", "3장 · 계약이 손해가 되는 지점")]
    gx0, gx1, gy0, gy1 = 150, 680, 320, 700
    b.append(f'<line x1="{gx0}" y1="{gy0-20}" x2="{gx0}" y2="{gy1}" stroke="#222" stroke-width="2"/>')
    b.append(f'<line x1="{gx0}" y1="{gy1}" x2="{gx1}" y2="{gy1}" stroke="#222" stroke-width="2"/>')
    b.append(f'<text x="{gx0-14}" y="{gy0-32}" font-size="16">비용</text>')
    b.append(f'<text x="{gx1}" y="{gy1+30}" text-anchor="end" font-size="16">거래 규모</text>')

    fixed_y = 560               # 고정비 수평선의 y
    b.append(f'<line x1="{gx0}" y1="{fixed_y}" x2="{gx1}" y2="{fixed_y}" stroke="#a5490a" stroke-width="3"/>')
    b.append(f'<text x="{gx0}" y="{fixed_y-14}" font-size="15" fill="#a5490a">계약 집행에 드는 고정 비용</text>')

    # 이익선: (gx0, gy1) 에서 시작해 우상향, fixed_y와 만나는 x = cx
    slope = (gy1 - (gy0 - 20)) / (gx1 - gx0)   # 픽셀 기준 상승률
    cx = gx0 + (gy1 - fixed_y) / slope
    b.append(f'<line x1="{gx0}" y1="{gy1}" x2="{gx1}" y2="{gy0-20}" stroke="#1a5fb4" stroke-width="3"/>')
    b.append(f'<text x="{gx0+340}" y="{gy0+90}" font-size="15" fill="#1a5fb4">계약으로 지키는 이익</text>')

    b.append(f'<line x1="{cx}" y1="{gy0-20}" x2="{cx}" y2="{gy1}" stroke="#555" stroke-width="1.5" stroke-dasharray="5,4"/>')
    b.append(f'<text x="{cx+14}" y="{gy0+10}" font-size="14">이 지점부터 계약이 실효적</text>')
    b.append(f'<rect x="{gx0}" y="{gy0-20}" width="{cx-gx0}" height="{gy1-(gy0-20)}" fill="#c00" opacity="0.07"/>')
    b.append(f'<text x="{gx0+10}" y="{fixed_y+40}" font-size="14" fill="#a33">계약 비용 &gt; 이익</text>')
    b.append(f'<text x="{gx0+10}" y="{fixed_y+70}" font-size="14" fill="#a33">→ 평판·신뢰에 의존</text>')
    return svg("\n".join(b))


# ── p28: 4.2 보험이 만드는 도덕적 해이 ────────────────────────────
def fig_insurance():
    b = [head("보험 가입 전후의 행동 변화", "4장 · 부담이 줄면 조심성도 줄 수 있다")]
    rows = [["구분", "사고가 나면", "조심하는 정도"],
            ["보험 없음", "전액 본인 부담", "매우 조심함"],
            ["보험 있음", "보험사가 부담", "조심성이 줄어들 수 있음"]]
    b.append(table(rows, 100, 320, [180, 250, 260], highlight={(2, 2)}))
    b.append(f'<line x1="{100+180+250+130}" y1="470" x2="{100+180+250+130}" y2="530" stroke="#a33" stroke-width="2" marker-end="url(#a)"/>')
    b.append('<text x="120" y="580" font-size="16">부담이 줄어들수록 조심성도 줄어들 수 있다</text>')
    b.append('<rect x="100" y="620" width="690" height="70" rx="8" fill="#fff8e6" stroke="#caa44a" stroke-width="2"/>')
    b.append('<text x="' + str(100 + 345) + '" y="662" text-anchor="middle" font-size="16" font-weight="700">보험사가 자기부담금·할증 제도를 두는 이유</text>')
    return svg("\n".join(b))


# ── p39: 5.4 신뢰 붕괴 그래프 ────────────────────────────────────
def fig_trust_collapse():
    b = [head("남은 거래 횟수와 신뢰 유지 유인", "5장 · 끝이 보이면 신뢰가 흔들린다")]
    gx0, gx1, gy0, gy1 = 150, 680, 320, 680
    b.append(f'<line x1="{gx0}" y1="{gy0-20}" x2="{gx0}" y2="{gy1}" stroke="#222" stroke-width="2"/>')
    b.append(f'<line x1="{gx0}" y1="{gy1}" x2="{gx1}" y2="{gy1}" stroke="#222" stroke-width="2"/>')
    b.append(f'<text x="{gx0-14}" y="{gy0-32}" font-size="15">신뢰 유지 유인</text>')
    b.append(f'<text x="{gx1}" y="{gy1+30}" text-anchor="end" font-size="15">남은 거래 횟수 (많음 → 적음)</text>')
    pts = [(gx0, gy0), (gx0+250, gy0+60), (gx0+400, gy0+110), (gx0+480, gy0+160), (gx0+500, gy1)]
    path = "M " + " L ".join(f"{x},{y}" for x, y in pts)
    b.append(f'<path d="{path}" fill="none" stroke="#1a5fb4" stroke-width="3"/>')
    b.append(f'<text x="{gx0+80}" y="{gy0+40}" font-size="14" fill="#1a5fb4">거래가 많이 남았을 때 — 신뢰 유지가 유리</text>')
    b.append(f'<text x="{gx0+420}" y="{gy1-20}" font-size="14" fill="#a33">마지막 거래 근처 — 배신 유인이 커짐</text>')
    b.append(f'<rect x="{gx0}" y="{gy1+60}" width="{gx1-gx0}" height="60" rx="8" fill="#f4f6f8" stroke="#889"/>')
    b.append(f'<text x="{(gx0+gx1)/2}" y="{gy1+96}" text-anchor="middle" font-size="15">끝이 확실히 보이지 않으면 이 급락이 완화된다</text>')
    return svg("\n".join(b))


FIGURES = {8: fig_three_costs, 21: fig_contract_cost, 28: fig_insurance, 39: fig_trust_collapse}

if __name__ == "__main__":
    out = Path(__file__).parent / "pdfbuild042"
    out.mkdir(exist_ok=True)
    for n, fn in FIGURES.items():
        p = out / f"fig-{n:02d}.svg"
        p.write_text(fn(), encoding="utf-8")
        print(f"{p.name}  {p.stat().st_size:,} bytes")
