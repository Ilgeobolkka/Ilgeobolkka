#!/usr/bin/env python3
"""book-071 이미지 페이지 4개의 SVG 생성."""
import sys
from pathlib import Path

W, H = 794, 1123
FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"


def t(x, y, value, size=16, color="#27353a", weight="400", anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}">{value}</text>')


def base(title, subtitle, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text {{ font-family: {FONT}; }}</style>
<rect width="{W}" height="{H}" fill="#ffffff"/>
<defs>
  <marker id="blue" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="#315f83"/></marker>
  <marker id="teal" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="#36746e"/></marker>
  <marker id="red" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="#a44f47"/></marker>
</defs>
{t(W/2, 122, title, 32, '#203238', '700')}
{t(W/2, 164, subtitle, 17, '#66777b')}
<line x1="105" y1="195" x2="689" y2="195" stroke="#d9dfe1"/>
{body}
</svg>'''


def round_box(x, y, w, h, label, fill="#f5f7f8", stroke="#6e8085", size=18):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="18" fill="{fill}" stroke="{stroke}" stroke-width="2"/>'
            + t(x+w/2, y+h/2+6, label, size, "#273a40", "700"))


def fig_round_trip():
    b = []
    b.append(round_box(80, 340, 180, 130, "클라이언트", "#eef3f7", "#56748a", 21))
    b.append('<rect x="430" y="250" width="280" height="440" rx="26" fill="#f4f8f6" stroke="#538078" stroke-width="3"/>')
    b.append(t(570, 292, "서버 경계", 22, "#32645e", "700"))
    for y, label in [(340, "접수"), (460, "처리"), (580, "응답 만들기")]:
        b.append(round_box(485, y, 170, 72, label, "#ffffff", "#78a19b", 17))
    b.append('<line x1="655" y1="376" x2="655" y2="454" stroke="#36746e" stroke-width="3" marker-end="url(#teal)"/>')
    b.append('<line x1="655" y1="496" x2="655" y2="574" stroke="#36746e" stroke-width="3" marker-end="url(#teal)"/>')
    b.append('<path d="M260 370 C325 300 370 300 430 340" fill="none" stroke="#315f83" stroke-width="5" marker-end="url(#blue)"/>')
    b.append(t(345, 292, "요청", 19, "#315f83", "700"))
    b.append('<path d="M430 620 C365 710 320 710 250 500" fill="none" stroke="#a44f47" stroke-width="5" marker-end="url(#red)"/>')
    b.append(t(335, 706, "응답", 19, "#a44f47", "700"))
    b.append('<circle cx="288" cy="325" r="18" fill="#315f83"/>')
    b.append(t(288, 331, "가", 13, "#ffffff", "700"))
    b.append('<circle cx="300" cy="580" r="18" fill="#a44f47"/>')
    b.append(t(300, 586, "가", 13, "#ffffff", "700"))
    b.append('<rect x="105" y="815" width="584" height="128" rx="18" fill="#fff8e8" stroke="#c5a866"/>')
    b.append(t(397, 860, "요청 식별자는 응답 반환까지 유지된다", 19, "#51462c", "700"))
    b.append(t(397, 900, "외부에는 계약을 노출하고 내부 구현은 서버 경계 안에 둔다", 15, "#6d634c"))
    return base("요청-응답 처리 개요", "클라이언트 요청이 서버 처리 후 응답으로 반환되는 과정", "\n".join(b))


def fig_routing_tree():
    b = []
    b.append(round_box(292, 245, 210, 70, "요청 도착", "#eef3f7", "#56748a", 19))
    b.append('<line x1="397" y1="315" x2="397" y2="370" stroke="#315f83" stroke-width="3" marker-end="url(#blue)"/>')
    b.append('<polygon points="397,380 525,455 397,530 269,455" fill="#f5f1e8" stroke="#967d4d" stroke-width="2"/>')
    b.append(t(397, 451, "경로 모양이", 16, "#55472e", "700")); b.append(t(397, 474, "있는가?", 16, "#55472e", "700"))
    b.append('<line x1="525" y1="455" x2="650" y2="455" stroke="#a44f47" stroke-width="3" marker-end="url(#red)"/>')
    b.append(t(575, 439, "아니요", 13, "#a44f47", "700")); b.append(round_box(610, 410, 130, 90, "경로 없음", "#fbeeed", "#a44f47", 15))
    b.append('<line x1="397" y1="530" x2="397" y2="585" stroke="#36746e" stroke-width="3" marker-end="url(#teal)"/>'); b.append(t(420, 565, "예", 13, "#36746e", "700", "start"))
    b.append('<polygon points="397,595 525,670 397,745 269,670" fill="#eaf3f1" stroke="#4d817a" stroke-width="2"/>')
    b.append(t(397, 666, "동작도", 16, "#32645e", "700")); b.append(t(397, 689, "맞는가?", 16, "#32645e", "700"))
    b.append('<line x1="525" y1="670" x2="650" y2="670" stroke="#a44f47" stroke-width="3" marker-end="url(#red)"/>')
    b.append(t(575, 654, "아니요", 13, "#a44f47", "700")); b.append(round_box(610, 625, 130, 90, "동작 불일치", "#fbeeed", "#a44f47", 14))
    b.append('<line x1="397" y1="745" x2="397" y2="800" stroke="#36746e" stroke-width="3" marker-end="url(#teal)"/>'); b.append(t(420, 783, "예", 13, "#36746e", "700", "start"))
    b.append(round_box(267, 810, 260, 82, "경로 값 추출 → 처리자", "#eaf3f1", "#4d817a", 17))
    b.append(t(397, 956, "라우팅은 업무를 실행하지 않고 하나의 처리자를 선택한다", 17, "#40565c", "700"))
    return base("라우팅 결정 절차", "동작과 경로를 결합해 처리자를 선택하는 과정", "\n".join(b))


def fig_concurrent_lanes():
    b = []
    b.append(t(132, 250, "도착", 15, "#657479", "700")); b.append(t(660, 250, "시간 ↓", 15, "#657479", "700"))
    lanes = [("요청 가", 205, "#315f83"), ("요청 나", 397, "#36746e"), ("요청 다", 589, "#9a6b34")]
    for label, x, color in lanes:
        b.append(t(x, 292, label, 18, color, "700"))
        b.append(f'<line x1="{x}" y1="320" x2="{x}" y2="890" stroke="#d7dcde" stroke-width="6" stroke-linecap="round"/>')
    b.append('<rect x="155" y="350" width="100" height="175" rx="14" fill="#eaf0f5" stroke="#315f83" stroke-width="2"/>'); b.append(t(205, 390, "작업자 하나", 14, "#315f83", "700")); b.append(t(205, 430, "외부 결과", 13, "#526a79")); b.append(t(205, 455, "기다림", 13, "#526a79"))
    b.append('<rect x="347" y="365" width="100" height="120" rx="14" fill="#e8f2ef" stroke="#36746e" stroke-width="2"/>'); b.append(t(397, 405, "작업자 둘", 14, "#36746e", "700")); b.append(t(397, 445, "짧은 처리", 13, "#4d716d"))
    b.append('<rect x="539" y="365" width="100" height="120" rx="14" fill="#fff8e8" stroke="#b28a53" stroke-width="2" stroke-dasharray="6,4"/>'); b.append(t(589, 405, "대기열", 14, "#80602f", "700")); b.append(t(589, 445, "자리 기다림", 13, "#806c4c"))
    b.append('<line x1="397" y1="485" x2="397" y2="560" stroke="#36746e" stroke-width="3" marker-end="url(#teal)"/>'); b.append(t(397, 595, "응답 나", 15, "#36746e", "700"))
    b.append('<path d="M589 485 C560 535 480 535 447 565" fill="none" stroke="#9a6b34" stroke-width="3" marker-end="url(#blue)"/>')
    b.append('<rect x="347" y="575" width="100" height="105" rx="14" fill="#fff8e8" stroke="#b28a53" stroke-width="2"/>'); b.append(t(397, 615, "작업자 둘", 14, "#80602f", "700")); b.append(t(397, 650, "요청 다 처리", 13, "#806c4c"))
    b.append(t(397, 735, "응답 다", 15, "#9a6b34", "700")); b.append('<line x1="397" y1="680" x2="397" y2="710" stroke="#9a6b34" stroke-width="3" marker-end="url(#blue)"/>')
    b.append('<line x1="205" y1="525" x2="205" y2="795" stroke="#315f83" stroke-width="3" stroke-dasharray="7,5"/>'); b.append(t(205, 835, "응답 가", 15, "#315f83", "700"))
    b.append('<rect x="105" y="920" width="584" height="80" rx="16" fill="#f4f6f7" stroke="#a6b0b3"/>'); b.append(t(397, 968, "완료 순서와 관계없이 각 응답은 원래 요청과 연결된다", 16, "#40565c", "700"))
    return base("동시 요청 처리 시간선", "작업자 두 개와 대기열을 사용하는 처리 예시", "\n".join(b))


def fig_stateless():
    b = []
    b.append(round_box(270, 235, 254, 75, "요청 분배자", "#eef3f7", "#56748a", 19))
    for x, label, color in [(290, "서버 가", "#315f83"), (504, "서버 나", "#36746e")]:
        b.append(f'<line x1="397" y1="310" x2="{x}" y2="385" stroke="{color}" stroke-width="4" marker-end="url(#blue)"/>')
        b.append(f'<rect x="{x-90}" y="395" width="180" height="220" rx="22" fill="#f7f9f9" stroke="{color}" stroke-width="3"/>')
        b.append(t(x, 440, label, 20, color, "700"))
        b.append(f'<rect x="{x-62}" y="480" width="124" height="72" rx="12" fill="#ffffff" stroke="{color}" stroke-dasharray="5,4"/>')
        b.append(t(x, 510, "요청 중에만", 13, "#52666b")); b.append(t(x, 535, "임시 맥락", 13, "#52666b", "700"))
        b.append(t(x, 582, "완료 뒤 비움", 12, "#7a8588"))
        b.append(f'<line x1="{x}" y1="615" x2="{x}" y2="745" stroke="{color}" stroke-width="4" marker-end="url(#teal)"/>')
    b.append('<rect x="150" y="755" width="494" height="135" rx="24" fill="#edf4ed" stroke="#5d8061" stroke-width="3"/>')
    b.append(t(397, 805, "외부 상태 저장소", 21, "#3f6844", "700"))
    b.append(t(397, 844, "서버가 바뀌어도 남아야 하는 업무 상태", 15, "#58715b"))
    b.append('<circle cx="150" cy="335" r="17" fill="#315f83"/>'); b.append(t(150, 341, "가", 12, "#fff", "700"))
    b.append('<circle cx="397" cy="335" r="17" fill="#9a6b34"/>'); b.append(t(397, 341, "나", 12, "#fff", "700"))
    b.append('<circle cx="644" cy="335" r="17" fill="#36746e"/>'); b.append(t(644, 341, "다", 12, "#fff", "700"))
    b.append('<rect x="105" y="935" width="584" height="72" rx="16" fill="#fff8e8" stroke="#c5a866"/>'); b.append(t(397, 978, "서버 인스턴스와 업무 상태의 수명을 분리한다", 17, "#51462c", "700"))
    return base("무상태 서버 구성", "여러 서버가 외부 상태 저장소를 공유하는 구조", "\n".join(b))


FIGURES = {6: fig_round_trip, 20: fig_routing_tree, 34: fig_concurrent_lanes, 43: fig_stateless}


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tmp/pdfs/book-071")
    out.mkdir(parents=True, exist_ok=True)
    for page, make in FIGURES.items():
        dest = out / f"fig-{page:02d}.svg"
        dest.write_text(make(), encoding="utf-8")
        print(f"{dest} ({dest.stat().st_size:,} bytes)")
