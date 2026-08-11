#!/usr/bin/env python3
"""book-011 이미지 페이지 네 개의 SVG를 생성한다."""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#fffdf9"/>
<defs>
  <marker id="arrow-red" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
    <path d="M0,0 L0,6 L9,3 z" fill="#b8564b"/>
  </marker>
  <marker id="arrow-blue" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
    <path d="M0,0 L0,6 L9,3 z" fill="#466b7a"/>
  </marker>
</defs>
<text x="397" y="112" text-anchor="middle" font-size="34" font-weight="700" fill="#24343a">{title}</text>
<text x="397" y="154" text-anchor="middle" font-size="18" fill="#68777c">{subtitle}</text>
{body}
<text x="397" y="1068" text-anchor="middle" font-size="14" fill="#8f989b">느린 아침을 수집하는 법</text>
</svg>'''


def text(x, y, value, size=17, fill="#24343a", weight="400", anchor="middle"):
    return f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" fill="{fill}" font-weight="{weight}">{value}</text>'


def fig_morning_map():
    items = [("알람", 255), ("휴대전화", 365), ("세면대", 475), ("옷장", 585), ("현관", 695)]
    b = []
    b.append('<rect x="55" y="205" width="320" height="735" rx="22" fill="#fff3ef" stroke="#d89b93" stroke-width="2"/>')
    b.append('<rect x="419" y="205" width="320" height="735" rx="22" fill="#eef5f3" stroke="#8cafaa" stroke-width="2"/>')
    b.append(text(215, 248, "서두르는 아침", 23, "#9c4038", "700"))
    b.append(text(579, 248, "여백이 있는 아침", 23, "#355f5c", "700"))
    for idx, (label, y) in enumerate(items):
        b.append(f'<rect x="130" y="{y-28}" width="170" height="56" rx="15" fill="#ffffff" stroke="#b8564b" stroke-width="2"/>')
        b.append(text(215, y+6, label, 17))
        b.append(f'<rect x="494" y="{y-28}" width="170" height="56" rx="15" fill="#ffffff" stroke="#4f7c78" stroke-width="2"/>')
        b.append(text(579, y+6, label, 17))
        if idx < len(items) - 1:
            ny = items[idx + 1][1]
            b.append(f'<path d="M215 {y+30} C100 {y+55}, 330 {ny-55}, 215 {ny-30}" fill="none" stroke="#b8564b" stroke-width="4" marker-end="url(#arrow-red)"/>')
            b.append(f'<line x1="579" y1="{y+30}" x2="579" y2="{ny-32}" stroke="#466b7a" stroke-width="4" marker-end="url(#arrow-blue)"/>')
    b.append('<circle cx="579" cy="530" r="28" fill="#fffdf9" stroke="#4f7c78" stroke-width="3" stroke-dasharray="6,5"/>')
    b.append(text(579, 535, "멈춤", 12, "#355f5c", "700"))
    b.append(text(650, 535, "감각과 선택", 12, "#4f6d6a"))
    b.append(text(215, 795, "다음 행동이 현재에 겹친다", 15, "#9c4038", "700"))
    b.append(text(215, 827, "되돌아감과 예상이 동선을 조인다", 14, "#7a5955"))
    b.append(text(579, 795, "같은 행동을 하나씩 지난다", 15, "#355f5c", "700"))
    b.append(text(579, 827, "전환의 빈칸이 체감 속도를 바꾼다", 14, "#506d6b"))
    b.append('<line x1="95" y1="875" x2="699" y2="875" stroke="#d8d2c7"/>')
    b.append(text(397, 915, "행동의 수가 같아도 전환 방식은 다른 아침을 만든다", 18, "#24343a", "700"))
    return base("두 종류의 아침 지도", "1장 · 겹치는 서두름과 전환의 여백", "\n".join(b))


def fig_route():
    b = []
    rooms = [
        ("침실", 88, 265, 230, 215),
        ("욕실", 88, 520, 230, 180),
        ("부엌", 474, 265, 230, 260),
        ("현관", 474, 615, 230, 130),
    ]
    for label, x, y, w, h in rooms:
        b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="#f8f6f0" stroke="#78878b" stroke-width="2"/>')
        b.append(text(x+w/2, y+38, label, 20, "#34484e", "700"))
    b.append(text(397, 220, "겹친 이동", 18, "#9c4038", "700"))
    red_points = [(180,420),(185,600),(590,440),(185,600),(190,420),(590,690),(590,430),(590,690)]
    for i in range(len(red_points)-1):
        x1,y1 = red_points[i]; x2,y2 = red_points[i+1]
        b.append(f'<path d="M{x1} {y1} C397 {(y1+y2)/2-55}, 397 {(y1+y2)/2+55}, {x2} {y2}" fill="none" stroke="#c05c52" stroke-width="4" opacity="0.75" marker-end="url(#arrow-red)"/>')
    b.append('<rect x="105" y="802" width="584" height="155" rx="18" fill="#eef5f3" stroke="#8cafaa" stroke-width="2"/>')
    b.append(text(397, 838, "정돈된 한 방향 흐름", 18, "#355f5c", "700"))
    nodes = [("침실",160),("욕실",310),("부엌",470),("현관",630)]
    for i,(label,x) in enumerate(nodes):
        b.append(f'<circle cx="{x}" cy="890" r="40" fill="#ffffff" stroke="#4f7c78" stroke-width="2"/>')
        b.append(text(x, 896, label, 15))
        if i < len(nodes)-1:
            b.append(f'<line x1="{x+42}" y1="890" x2="{nodes[i+1][1]-44}" y2="890" stroke="#466b7a" stroke-width="4" marker-end="url(#arrow-blue)"/>')
    b.append(text(397, 990, "물건을 쓸 자리 가까이에 두면 되돌아가는 선이 펴진다", 16, "#506d6b"))
    return base("겹친 동선을 펴는 법", "2장 · 행동 수보다 전환과 되돌아감을 줄이기", "\n".join(b))


def fig_ritual_card():
    b = []
    colors = ["#f4dfc6", "#dfeceb", "#e4e1ef"]
    rows = [
        ("시작 신호", "물 따르는 소리", "밤에서 아침으로 넘어오기"),
        ("감각 확인", "컵의 무게와 손끝", "지금 몸의 상태 듣기"),
        ("오늘의 선택", "먼저 할 한 가지", "실제 하루로 이어 가기"),
    ]
    for i,(name,example,note) in enumerate(rows):
        y = 230 + i*190
        b.append(f'<rect x="115" y="{y}" width="564" height="145" rx="24" fill="{colors[i]}" stroke="#59696d" stroke-width="2"/>')
        b.append(f'<circle cx="165" cy="{y+50}" r="24" fill="#ffffff" stroke="#59696d"/>')
        b.append(text(165, y+57, str(i+1), 18, "#34484e", "700"))
        b.append(text(215, y+48, name, 22, "#24343a", "700", "start"))
        b.append(text(215, y+82, example, 17, "#40575d", "400", "start"))
        b.append(text(215, y+115, note, 14, "#637378", "400", "start"))
        if i < 2:
            b.append(f'<line x1="397" y1="{y+147}" x2="397" y2="{y+183}" stroke="#466b7a" stroke-width="4" marker-end="url(#arrow-blue)"/>')
    chips = [("바쁜 날", "한 번씩 짧게"), ("피곤한 날", "감각부터"), ("여유 있는 날", "기록을 덧붙임")]
    for i,(name,note) in enumerate(chips):
        x = 115 + i*190
        b.append(f'<rect x="{x}" y="825" width="174" height="94" rx="16" fill="#ffffff" stroke="#9aa5a7"/>')
        b.append(text(x+87, 860, name, 16, "#34484e", "700"))
        b.append(text(x+87, 891, note, 13, "#66777b"))
    b.append(text(397, 970, "기능은 지키고 길이와 순서는 오늘에 맞춘다", 18, "#355f5c", "700"))
    return base("나만의 아침 리츄얼 카드", "4장 · 시작, 감각, 선택을 한 흐름으로", "\n".join(b))


def fig_clocks():
    b = []
    tracks = [
        ("출근자", "해가 뜬 뒤", 285, 0.30, "#c58549"),
        ("돌봄자", "책임 사이", 500, 0.18, "#4f7c78"),
        ("야간 근무자", "해가 질 무렵", 715, 0.72, "#686493"),
    ]
    for name,note,y,pos,color in tracks:
        b.append(text(105, y-35, name, 21, color, "700", "start"))
        b.append(text(689, y-35, note, 14, "#6e797c", "400", "end"))
        b.append(f'<line x1="105" y1="{y}" x2="689" y2="{y}" stroke="#c8cdce" stroke-width="8" stroke-linecap="round"/>')
        x = 105 + 584*pos
        b.append(f'<circle cx="{x}" cy="{y}" r="34" fill="#ffffff" stroke="{color}" stroke-width="5"/>')
        b.append(f'<circle cx="{x}" cy="{y}" r="7" fill="{color}"/>')
        b.append(f'<line x1="{x}" y1="{y-43}" x2="{x}" y2="{y-78}" stroke="{color}" stroke-width="2"/>')
        b.append(text(x, y-92, "나의 시작", 14, color, "700"))
        b.append(text(135, y+45, "멈춤", 14, "#52666b"))
        b.append(text(397, y+45, "감각 확인", 14, "#52666b"))
        b.append(text(659, y+45, "다음 선택", 14, "#52666b"))
    b.append('<rect x="115" y="875" width="564" height="104" rx="18" fill="#f3efe5" stroke="#c7b995"/>')
    b.append(text(397, 918, "시계의 숫자는 달라도 시작의 기능은 같다", 19, "#34484e", "700"))
    b.append(text(397, 953, "아침은 각자의 하루가 움직이기 시작하는 경계", 15, "#66777b"))
    return base("서로 다른 아침 시계", "6장 · 생활표마다 다른 시작 지점", "\n".join(b))


FIGURES = {6: fig_morning_map, 13: fig_route, 27: fig_ritual_card, 41: fig_clocks}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tmp/pdfs/book-011")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        path = out / f"fig-{page:02d}.svg"
        path.write_text(make(), encoding="utf-8")
        print(f"{path} ({path.stat().st_size:,} bytes)")
