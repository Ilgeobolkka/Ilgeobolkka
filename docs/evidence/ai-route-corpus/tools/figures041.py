#!/usr/bin/env python3
"""book-041 이미지 페이지 4개의 SVG 생성."""
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
</defs>
{body}
</svg>'''


# ── p7: 1.4 분업 생산 흐름도 ────────────────────────────────────
def fig_flow():
    b = [head("혼자 생산 vs 분업 생산", "1장 · 같은 인원과 시간, 다른 결과")]
    steps = ["밀 농사", "제분", "제빵"]

    by = 260
    b.append(f'<rect x="90" y="{by}" width="{W-180}" height="200" rx="12" fill="#f4f6f8" stroke="#334" stroke-width="2"/>')
    b.append(f'<text x="120" y="{by+40}" font-size="20" font-weight="700">혼자 생산</text>')
    b.append(f'<circle cx="150" cy="{by+130}" r="28" fill="#dde3ea" stroke="#556" stroke-width="2"/>')
    b.append(f'<text x="150" y="{by+136}" text-anchor="middle" font-size="14">한 사람</text>')
    for i, s in enumerate(steps):
        x = 280 + i * 150
        b.append(f'<rect x="{x}" y="{by+95}" width="120" height="70" rx="8" fill="#fff" stroke="#889" stroke-width="1.5"/>')
        b.append(f'<text x="{x+60}" y="{by+135}" text-anchor="middle" font-size="17">{s}</text>')
        if i > 0:
            b.append(f'<line x1="{x-30}" y1="{by+130}" x2="{x-4}" y2="{by+130}" stroke="#222" stroke-width="2" marker-end="url(#a)"/>')
    b.append(f'<line x1="182" y1="{by+130}" x2="276" y2="{by+130}" stroke="#222" stroke-width="2" marker-end="url(#a)"/>')
    b.append(f'<text x="{W-140}" y="{by+40}" text-anchor="end" font-size="15" fill="#556">한 사람이 세 가지를 차례로</text>')

    by2 = by + 260
    b.append(f'<rect x="90" y="{by2}" width="{W-180}" height="220" rx="12" fill="#eef4ec" stroke="#2f6b3a" stroke-width="2"/>')
    b.append(f'<text x="120" y="{by2+40}" font-size="20" font-weight="700">분업 생산</text>')
    for i, s in enumerate(steps):
        x = 150 + i * 190
        b.append(f'<circle cx="{x}" cy="{by2+95}" r="26" fill="#d8ecd8" stroke="#2f6b3a" stroke-width="2"/>')
        b.append(f'<text x="{x}" y="{by2+101}" text-anchor="middle" font-size="13">사람{i+1}</text>')
        b.append(f'<rect x="{x-55}" y="{by2+140}" width="110" height="60" rx="8" fill="#fff" stroke="#2f6b3a" stroke-width="1.5"/>')
        b.append(f'<text x="{x}" y="{by2+175}" text-anchor="middle" font-size="16">{s}</text>')
        if i > 0:
            xp = 150 + (i - 1) * 190
            b.append(f'<line x1="{xp+55}" y1="{by2+170}" x2="{x-55}" y2="{by2+170}" stroke="#2f6b3a" stroke-width="2" marker-end="url(#a2)"/>')
    b.append(f'<text x="{W-140}" y="{by2+40}" text-anchor="end" font-size="15" fill="#2f6b3a">세 사람이 한 가지씩 동시에</text>')

    my = by2 + 260
    b.append(f'<line x1="{W/2}" y1="{by+260-40}" x2="{W/2}" y2="{by2-10}" stroke="#a33" stroke-width="3" marker-end="url(#a)"/>')
    b.append(f'<text x="{W/2+18}" y="{by+260-15}" font-size="16" font-weight="700" fill="#a33">같은 인원과 시간으로 더 많이 생산</text>')
    return svg("\n".join(b))


# ── p19: 3.3 비교우위 계산표 ────────────────────────────────────
def fig_comparative_table():
    b = [head("갑과 을의 생산량과 기회비용", "3장 · 절대우위와 비교우위는 다르다")]

    def table(rows, x0, y0, cw, highlight=None):
        out = []
        for r, row in enumerate(rows):
            y = y0 + r * 50
            out.append(f'<rect x="{x0}" y="{y}" width="{sum(cw)}" height="50" fill="{"#eef2f6" if r==0 else "#fff"}" stroke="#889"/>')
            cx = x0
            for c, cell in enumerate(row):
                fill = "none"
                if highlight and (r, c) in highlight:
                    fill = "#fff3cd"
                out.append(f'<rect x="{cx}" y="{y}" width="{cw[c]}" height="50" fill="{fill}"/>')
                out.append(f'<text x="{cx+cw[c]/2}" y="{y+32}" text-anchor="middle" font-size="17" '
                           f'font-weight="{"700" if r==0 or (highlight and (r,c) in highlight) else "400"}">{cell}</text>')
                cx += cw[c]
                if c < len(row) - 1:
                    out.append(f'<line x1="{cx}" y1="{y}" x2="{cx}" y2="{y+50}" stroke="#889"/>')
        return "\n".join(out)

    t1 = [["구분", "밀(포대/일)", "빵(개/일)"], ["갑", "10", "20"], ["을", "4", "4"]]
    b.append('<text x="120" y="260" font-size="18" font-weight="700">하루 생산량</text>')
    b.append(table(t1, 120, 280, [150, 200, 200]))

    t2 = [["구분", "밀 1포대의 기회비용", "빵 1개의 기회비용"],
          ["갑", "빵 2개", "밀 0.5포대"], ["을", "빵 1개", "밀 1포대"]]
    b.append('<text x="120" y="500" font-size="18" font-weight="700">기회비용</text>')
    b.append(table(t2, 120, 520, [150, 230, 230], highlight={(2, 1), (1, 2)}))

    b.append('<text x="120" y="700" font-size="17" fill="#a33" font-weight="700">기회비용이 더 작은 쪽(노란 칸)에 비교우위가 있다</text>')
    b.append('<text x="120" y="735" font-size="16">밀: 을(빵 1개 &lt; 갑의 빵 2개) → 을이 비교우위</text>')
    b.append('<text x="120" y="765" font-size="16">빵: 갑(밀 0.5포대 &lt; 을의 밀 1포대) → 갑이 비교우위</text>')
    return svg("\n".join(b))


# ── p24: 3.6 생산가능곡선 그래프 ─────────────────────────────────
def fig_ppf():
    b = [head("갑과 을의 생산가능곡선", "3장 · 교환은 곡선 밖의 소비를 만든다")]
    gy0, gy1 = 280, 620

    def one(gx0, title, note, ends, prod_pt, cons_pt, qmax, pmax):
        gx1 = gx0 + 280
        out = [f'<text x="{gx0+140}" y="{gy0-40}" text-anchor="middle" font-size="22" font-weight="700">{title}</text>']
        out.append(f'<line x1="{gx0}" y1="{gy0}" x2="{gx0}" y2="{gy1}" stroke="#222" stroke-width="2"/>')
        out.append(f'<line x1="{gx0}" y1="{gy1}" x2="{gx1}" y2="{gy1}" stroke="#222" stroke-width="2"/>')
        out.append(f'<text x="{gx0-6}" y="{gy0-6}" text-anchor="middle" font-size="14">빵(개)</text>')
        out.append(f'<text x="{gx1}" y="{gy1+24}" text-anchor="end" font-size="14">밀(포대)</text>')
        (bx1, by1), (bx2, by2) = ends
        def X(q): return gx0 + q / qmax * 260
        def Y(p): return gy1 - p / pmax * 320
        out.append(f'<line x1="{X(bx1)}" y1="{Y(by1)}" x2="{X(bx2)}" y2="{Y(by2)}" stroke="#333" stroke-width="3"/>')
        px, py_ = prod_pt
        out.append(f'<circle cx="{X(px)}" cy="{Y(py_)}" r="6" fill="#222"/>')
        out.append(f'<text x="{X(px)+10}" y="{Y(py_)-10}" font-size="13">현재 생산</text>')
        cx, cy = cons_pt
        out.append(f'<circle cx="{X(cx)}" cy="{Y(cy)}" r="6" fill="#1a5fb4"/>')
        out.append(f'<text x="{X(cx)+10}" y="{Y(cy)-10}" font-size="13" fill="#1a5fb4">교환 후 소비</text>')
        out.append(f'<line x1="{X(px)}" y1="{Y(py_)}" x2="{X(cx)}" y2="{Y(cy)}" stroke="#1a5fb4" stroke-width="2" stroke-dasharray="5,4"/>')
        out.append(f'<text x="{gx0+140}" y="{gy1+50}" text-anchor="middle" font-size="14" fill="#556">{note}</text>')
        return out

    b += one(90, "갑의 생산가능곡선", "빵 쪽으로 완만 → 제빵에 비교우위", ((0, 20), (10, 0)), (5, 10), (5, 14), 10, 20)
    b += one(450, "을의 생산가능곡선", "밀 쪽으로 완만 → 밀 농사에 비교우위", ((0, 4), (4, 0)), (2, 2), (2, 3.2), 4, 4)
    b.append(f'<text x="{W/2}" y="{gy1+110}" text-anchor="middle" font-size="16" font-weight="700" fill="#a33">교환으로 곡선 밖 소비가 가능해진다</text>')
    return svg("\n".join(b))


# ── p42: 5.4 분업 사슬 도표 ──────────────────────────────────────
def fig_chain():
    b = [head("빵 한 개의 분업 사슬", "5장 · 얼굴 모르는 사람들이 값으로 연결된다")]
    labels = ["농부", "마차꾼", "방앗간", "제빵사", "빵집 손님"]
    notes = ["밀을 기른다", "밀을 옮긴다", "밀을 가루로\n빻는다", "가루로 빵을\n굽는다", "값을 내고\n빵을 산다"]
    y = 380
    bw, gap = 120, 40
    x0 = (W - (bw * 5 + gap * 4)) / 2
    for i, (lab, note) in enumerate(zip(labels, notes)):
        x = x0 + i * (bw + gap)
        b.append(f'<rect x="{x}" y="{y}" width="{bw}" height="100" rx="10" fill="#f4f6f8" stroke="#334" stroke-width="2"/>')
        b.append(f'<text x="{x+bw/2}" y="{y+38}" text-anchor="middle" font-size="18" font-weight="700">{lab}</text>')
        for j, line in enumerate(note.split("\n")):
            b.append(f'<text x="{x+bw/2}" y="{y+62+j*20}" text-anchor="middle" font-size="13" fill="#556">{line}</text>')
        if i > 0:
            xp = x0 + (i - 1) * (bw + gap)
            b.append(f'<line x1="{xp+bw}" y1="{y+50}" x2="{x-4}" y2="{y+50}" stroke="#222" stroke-width="2.5" marker-end="url(#a)"/>')
    fy = y + 180
    b.append(f'<line x1="{x0+bw*5+gap*4}" y1="{fy}" x2="{x0}" y2="{fy}" stroke="#a33" stroke-width="2.5" marker-end="url(#a)"/>')
    b.append(f'<text x="{x0+(bw*5+gap*4)/2}" y="{fy-16}" text-anchor="middle" font-size="15" fill="#a33">돈은 오른쪽에서 왼쪽으로</text>')
    fy2 = fy + 50
    b.append(f'<line x1="{x0}" y1="{fy2}" x2="{x0+bw*5+gap*4}" y2="{fy2}" stroke="#2f6b3a" stroke-width="2.5" marker-end="url(#a2)"/>')
    b.append(f'<text x="{x0+(bw*5+gap*4)/2}" y="{fy2+30}" text-anchor="middle" font-size="15" fill="#2f6b3a">밀·가루·빵은 왼쪽에서 오른쪽으로</text>')
    return svg("\n".join(b))


FIGURES = {7: fig_flow, 19: fig_comparative_table, 24: fig_ppf, 42: fig_chain}

if __name__ == "__main__":
    out = Path(__file__).parent / "pdfbuild041"
    out.mkdir(exist_ok=True)
    for n, fn in FIGURES.items():
        p = out / f"fig-{n:02d}.svg"
        p.write_text(fn(), encoding="utf-8")
        print(f"{p.name}  {p.stat().st_size:,} bytes")
