#!/usr/bin/env python3
"""book-051 이미지 페이지 5개의 SVG 생성. figures041.py/042.py의 head()/svg() 패턴을 따른다."""
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
  <marker id="a2" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
    <path d="M0,0 L0,6 L9,3 z" fill="#1a5fb4"/></marker>
  <marker id="a3" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
    <path d="M0,0 L0,6 L9,3 z" fill="#a33"/></marker>
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
            out.append(f'<text x="{cx+cw[c]/2}" y="{y+32}" text-anchor="middle" font-size="15" '
                       f'font-weight="{"700" if r==0 or (highlight and (r,c) in highlight) else "400"}">{cell}</text>')
            cx += cw[c]
            if c < len(row) - 1:
                out.append(f'<line x1="{cx}" y1="{y}" x2="{cx}" y2="{y+50}" stroke="#889"/>')
    return "\n".join(out)


# ── 1.4: 의심의 두 방향 도표 ──────────────────────────────────
def fig_doubt_directions():
    b = [head("불신과 의심이 향하는 서로 다른 방향", "1장 · 결론을 먼저 정하는가, 미루는가")]
    b.append('<rect x="90" y="280" width="290" height="260" rx="12" fill="#fdecec" stroke="#a33" stroke-width="2"/>')
    b.append('<text x="235" y="330" text-anchor="middle" font-size="22" font-weight="700" fill="#a33">불신</text>')
    b.append('<circle cx="180" cy="420" r="26" fill="#fff" stroke="#a33" stroke-width="2"/>')
    b.append('<text x="180" y="426" text-anchor="middle" font-size="13">주장</text>')
    b.append('<line x1="206" y1="420" x2="300" y2="420" stroke="#a33" stroke-width="2.5" marker-end="url(#a3)"/>')
    b.append('<text x="330" y="426" text-anchor="middle" font-size="13">밀어냄</text>')
    b.append('<text x="235" y="490" text-anchor="middle" font-size="14" fill="#a33">결론을 먼저 정한다</text>')

    b.append('<rect x="414" y="280" width="290" height="260" rx="12" fill="#eaf1fb" stroke="#1a5fb4" stroke-width="2"/>')
    b.append('<text x="559" y="330" text-anchor="middle" font-size="22" font-weight="700" fill="#1a5fb4">의심</text>')
    b.append('<circle cx="504" cy="420" r="26" fill="#fff" stroke="#1a5fb4" stroke-width="2"/>')
    b.append('<text x="504" y="426" text-anchor="middle" font-size="13">주장</text>')
    b.append('<path d="M 530 410 Q 570 380 620 410" fill="none" stroke="#1a5fb4" stroke-width="2.5" marker-end="url(#a2)"/>')
    b.append('<text x="650" y="405" text-anchor="middle" font-size="13">되물음</text>')
    b.append('<path d="M 620 430 Q 570 460 530 430" fill="none" stroke="#1a5fb4" stroke-width="2" stroke-dasharray="4,4"/>')
    b.append('<text x="559" y="490" text-anchor="middle" font-size="14" fill="#1a5fb4">결론을 미루고 다시 묻는다</text>')

    b.append('<line x1="380" y1="410" x2="414" y2="410" stroke="#556" stroke-width="1.5" stroke-dasharray="3,3"/>')
    b.append('<text x="397" y="640" text-anchor="middle" font-size="16" font-weight="700">회의적 태도는 불신이 아니라 결론을 미루는 절차다</text>')
    return svg("\n".join(b))


# ── 2.4: 전제 검토 구조도 ──────────────────────────────────────
def fig_premise_structure():
    b = [head("주장이 딛고 있는 전제의 층", "2장 · 결론 아래 숨은 전제를 벗겨 내기")]
    b.append('<rect x="240" y="280" width="314" height="70" rx="10" fill="#eef2f6" stroke="#334" stroke-width="2"/>')
    b.append('<text x="397" y="322" text-anchor="middle" font-size="18" font-weight="700">결론</text>')

    b.append('<line x1="397" y1="350" x2="397" y2="390" stroke="#222" stroke-width="2" marker-end="url(#a)"/>')
    b.append('<rect x="180" y="400" width="434" height="70" rx="10" fill="#fff" stroke="#556" stroke-width="1.5"/>')
    b.append('<text x="397" y="442" text-anchor="middle" font-size="16">명시된 근거</text>')

    b.append('<line x1="397" y1="470" x2="397" y2="510" stroke="#a33" stroke-width="2" marker-end="url(#a3)"/>')
    b.append('<rect x="140" y="520" width="514" height="70" rx="10" fill="#fff8e6" stroke="#caa44a" stroke-width="2"/>')
    b.append('<text x="397" y="562" text-anchor="middle" font-size="16" font-weight="700">숨은 전제 (말해지지 않은 것)</text>')

    b.append('<line x1="397" y1="590" x2="397" y2="630" stroke="#a33" stroke-width="2" stroke-dasharray="5,4" marker-end="url(#a3)"/>')
    b.append('<rect x="100" y="640" width="594" height="70" rx="10" fill="#fff8e6" stroke="#caa44a" stroke-width="1.5" stroke-dasharray="6,4"/>')
    b.append('<text x="397" y="682" text-anchor="middle" font-size="15">그 전제를 떠받치는 또 다른 전제 (필요하면 계속 벗겨 냄)</text>')

    b.append('<text x="397" y="760" text-anchor="middle" font-size="15" fill="#556">전제는 한 겹으로 끝나지 않을 수 있다</text>')
    return svg("\n".join(b))


# ── 3.5: 질문의 사슬 도표 ──────────────────────────────────────
def fig_question_chain():
    b = [head("질문이 답을 낳고 답이 새 질문을 낳는 사슬", "3장 · 사슬은 어디에서 멈추는가")]
    y = 330
    items = ["질문 1", "답 1", "질문 2", "답 2", "질문 3"]
    x0 = 90
    bw, gap = 110, 25
    for i, label in enumerate(items):
        x = x0 + i * (bw + gap)
        fill = "#eaf1fb" if i % 2 == 0 else "#fff"
        stroke = "#1a5fb4" if i % 2 == 0 else "#556"
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="70" rx="10" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
        b.append(f'<text x="{x+bw/2}" y="{y+42}" text-anchor="middle" font-size="15">{label}</text>')
        if i > 0:
            xp = x0 + (i - 1) * (bw + gap)
            b.append(f'<line x1="{xp+bw}" y1="{y+35}" x2="{x}" y2="{y+35}" stroke="#222" stroke-width="2" marker-end="url(#a)"/>')
    xlast = x0 + (len(items) - 1) * (bw + gap) + bw
    b.append(f'<line x1="{xlast}" y1="{y+35}" x2="{xlast+40}" y2="{y+35}" stroke="#222" stroke-width="2" marker-end="url(#a)"/>')
    b.append(f'<text x="{xlast+60}" y="{y+42}" font-size="20">…</text>')

    cy = y + 160
    b.append(f'<line x1="{x0+3*(bw+gap)+bw//2}" y1="{cy}" x2="{x0+3*(bw+gap)+bw//2}" y2="{cy+60}" stroke="#a33" stroke-width="2.5" stroke-dasharray="6,5"/>')
    b.append(f'<text x="{x0+3*(bw+gap)+bw//2}" y="{cy+90}" text-anchor="middle" font-size="15" fill="#a33">멈추는 지점 (목적이 정한다)</text>')

    b.append(f'<text x="{W/2}" y="{cy+180}" text-anchor="middle" font-size="16" font-weight="700">사슬은 원리적으로 끝나지 않을 수 있다 — 완전함이 아니라 충분함에서 멈춘다</text>')
    return svg("\n".join(b))


# ── 4.4: 사고 실험 경로 도식 ──────────────────────────────────
def fig_chair_paths():
    b = [head("의자에 앉은 사람이 거쳐 가는 두 경로", "4장 · 확실성을 요구할 때와 충분함을 받아들일 때")]
    b.append('<circle cx="397" cy="330" r="34" fill="#f4f6f8" stroke="#334" stroke-width="2"/>')
    b.append('<text x="397" y="336" text-anchor="middle" font-size="14">규안</text>')
    b.append('<line x1="397" y1="364" x2="397" y2="400" stroke="#222" stroke-width="2" marker-end="url(#a)"/>')
    b.append('<rect x="290" y="410" width="214" height="60" rx="10" fill="#fff" stroke="#556" stroke-width="1.5"/>')
    b.append('<text x="397" y="446" text-anchor="middle" font-size="15">바닥이 단단한가?</text>')

    b.append('<line x1="330" y1="470" x2="200" y2="530" stroke="#a33" stroke-width="2.5" marker-end="url(#a3)"/>')
    b.append('<rect x="90" y="540" width="240" height="90" rx="10" fill="#fdecec" stroke="#a33" stroke-width="2"/>')
    b.append('<text x="210" y="572" text-anchor="middle" font-size="14" font-weight="700" fill="#a33">절대적 확실성 요구</text>')
    b.append('<text x="210" y="596" text-anchor="middle" font-size="13" fill="#a33">→ 계속 앉아 있음</text>')
    b.append('<path d="M 210 630 Q 150 660 210 630" fill="none" stroke="#a33" stroke-width="2" stroke-dasharray="4,4"/>')
    b.append('<text x="210" y="660" text-anchor="middle" font-size="12" fill="#a33">(같은 질문 반복)</text>')

    b.append('<line x1="464" y1="470" x2="594" y2="530" stroke="#1a5fb4" stroke-width="2.5" marker-end="url(#a2)"/>')
    b.append('<rect x="464" y="540" width="240" height="90" rx="10" fill="#eaf1fb" stroke="#1a5fb4" stroke-width="2"/>')
    b.append('<text x="584" y="572" text-anchor="middle" font-size="14" font-weight="700" fill="#1a5fb4">충분한 근거 인정</text>')
    b.append('<text x="584" y="596" text-anchor="middle" font-size="13" fill="#1a5fb4">→ 의자에서 일어섬</text>')

    b.append('<text x="397" y="750" text-anchor="middle" font-size="16" font-weight="700">같은 불확실성 앞에서 요구하는 확실성의 수준이 경로를 가른다</text>')
    return svg("\n".join(b))


# ── 6.4: 태도 비교 표 ──────────────────────────────────────────
def fig_attitude_table():
    b = [head("성급한 확신·회의적 태도·무한한 지연 비교", "6장 · 결론 속도와 유연성 두 기준")]
    rows = [["구분", "결론 속도", "유연성"],
            ["성급한 확신", "매우 빠름", "낮음"],
            ["회의적 태도", "충분함에서 멈춤", "높음"],
            ["무한한 지연", "결론 없음", "측정 불가"]]
    b.append(table(rows, 90, 300, [190, 250, 254], highlight={(2, 1), (2, 2)}))
    b.append('<rect x="90" y="520" width="694" height="70" rx="8" fill="#fff8e6" stroke="#caa44a" stroke-width="2"/>')
    b.append('<text x="437" y="562" text-anchor="middle" font-size="16" font-weight="700">회의적 태도는 두 극단 사이에서 균형을 잡는다</text>')
    return svg("\n".join(b))


FIGURES = {
    "1.4": fig_doubt_directions,
    "2.4": fig_premise_structure,
    "3.5": fig_question_chain,
    "4.4": fig_chair_paths,
    "6.4": fig_attitude_table,
}

if __name__ == "__main__":
    out = Path(__file__).parent / "pdfbuild051"
    out.mkdir(exist_ok=True)
    for key, fn in FIGURES.items():
        p = out / f"fig-{key}.svg"
        p.write_text(fn(), encoding="utf-8")
        print(f"{p.name}  {p.stat().st_size:,} bytes")
