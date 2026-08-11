#!/usr/bin/env python3
"""임의 도서의 원본 PDF 조립용 HTML 생성. 사용: python3 build_pdf_generic.py <bookId> <이미지페이지,쉼표>"""
import base64
import html
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]  # docs/evidence/ai-route-corpus/tools/ 기준 저장소 루트

CSS = """
@page { size: A4; margin: 0; }
* { box-sizing: border-box; }
body { margin: 0; font-family: 'Apple SD Gothic Neo','Noto Sans KR',sans-serif;
       -webkit-font-smoothing: antialiased; }
.page { width: 210mm; height: 297mm; page-break-after: always; overflow: hidden;
        position: relative; background: #fff; }
.page:last-child { page-break-after: auto; }
.text { padding: 26mm 24mm 22mm; display: flex; flex-direction: column; }
.hd { font-size: 9.5pt; color: #8a8f96; letter-spacing: .02em;
      border-bottom: .4pt solid #d8dce0; padding-bottom: 3mm; margin-bottom: 8mm; }
.hd .sec { float: right; }
h1.sect { font-size: 15pt; font-weight: 700; margin: 0 0 7mm; letter-spacing: -.01em; }
p { font-size: 11.6pt; line-height: 1.85; margin: 0 0 4.6mm; text-align: justify;
    word-break: keep-all; }
.toc { padding: 30mm 26mm; }
.toc h1 { font-size: 22pt; margin: 0 0 14mm; letter-spacing: .04em; }
.toc pre { font-family: 'SF Mono','Menlo',monospace; font-size: 9.6pt; line-height: 2.0;
           margin: 0; white-space: pre; }
/* 목차 pre의 글자 크기·행간은 줄 수에 맞춰 파이썬이 인라인으로 계산해 넣는다.
   2단(columns) 조판은 white-space:pre 줄이 열 너비를 넘으면 이웃 열 위에 겹쳐
   그려져 쓰지 않는다. */
.img { padding: 0; }
.img img { display: block; width: 100%; height: 100%; object-fit: contain; }
.pn { position: absolute; bottom: 12mm; left: 0; right: 0; text-align: center;
      font-size: 9pt; color: #9aa0a6; }
"""


def data_uri(path):
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")


# 목차 pre가 쓸 수 있는 세로 공간: A4 297mm - 상하 여백 60mm - 제목 블록 22mm
TOC_USABLE_PX = 215 * 3.7795
TOC_MAX_PT = 9.6


def toc_style(entries):
    """목차 줄 수에 맞는 글자 크기·행간을 계산한다.

    절이 많은 책은 목차가 길어져 한 페이지를 넘치므로, 줄 수로 크기를 역산해
    항상 한 페이지에 담는다. 가로는 1단이라 가장 긴 줄도 여유가 있다.
    """
    lines = entries.count("\n") + 1
    line_height = 1.9 if lines <= 30 else 1.5 if lines <= 45 else 1.3
    font_px = min(TOC_MAX_PT * 1.3333, TOC_USABLE_PX / (lines * line_height))
    return f"font-size:{font_px / 1.3333:.2f}pt;line-height:{line_height}"


def main():
    book_id = int(sys.argv[1])
    image_pages = {int(x) for x in sys.argv[2].split(",")}
    scratch = Path(sys.argv[3]) if len(sys.argv) > 3 else Path(f"pdfbuild{book_id:03d}")
    scratch.mkdir(exist_ok=True)

    ms = json.loads((REPO / f"docs/evidence/ai-route-corpus/book-{book_id:03d}-manuscript.json").read_text("utf-8"))
    parts = [f"<!doctype html><html lang='ko'><head><meta charset='utf-8'>"
             f"<title>{html.escape(ms['title'])}</title><style>{CSS}</style></head><body>"]
    for page in ms["pages"]:
        n = page["pageNumber"]
        if n in image_pages:
            uri = data_uri(scratch / f"fig-{n:02d}.png")
            parts.append(f"<section class='page img'><img src='{uri}' alt=''></section>")
            continue
        if page["section"] == "목차":
            entries = html.escape(page["body"]).split(chr(10), 2)[2]
            parts.append(f"<section class='page toc'><h1>목차</h1>"
                         f"<pre style='{toc_style(entries)}'>{entries}</pre>"
                         f"<div class='pn'>{n}</div></section>")
            continue
        paras = "".join(f"<p>{html.escape(t)}</p>" for t in page["body"].split("\n\n") if t.strip())
        parts.append(
            f"<section class='page text'>"
            f"<div class='hd'>{html.escape(ms['title'])}<span class='sec'>{html.escape(page['chapter'])}</span></div>"
            f"<h1 class='sect'>{html.escape(page['section'])}</h1>{paras}"
            f"<div class='pn'>{n}</div></section>")
    parts.append("</body></html>")
    dest = scratch / f"book-{book_id:03d}.html"
    dest.write_text("".join(parts), encoding="utf-8")
    print(f"{dest}  ({dest.stat().st_size:,} bytes, {len(ms['pages'])} pages)")


if __name__ == "__main__":
    main()
