#!/usr/bin/env python3
"""임의 도서의 원본 PDF 조립용 HTML 생성. 사용: python3 build_pdf_generic.py <bookId> <이미지페이지,쉼표>"""
import base64
import html
import json
import sys
from pathlib import Path

REPO = Path("/Users/t2025-m0204/Documents/sparta/Ilgeobolkka")

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
.img { padding: 0; }
.img img { display: block; width: 100%; height: 100%; object-fit: contain; }
.pn { position: absolute; bottom: 12mm; left: 0; right: 0; text-align: center;
      font-size: 9pt; color: #9aa0a6; }
"""


def data_uri(path):
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")


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
            body = html.escape(page["body"])
            parts.append(f"<section class='page toc'><h1>목차</h1>"
                         f"<pre>{body.split(chr(10), 2)[2]}</pre>"
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
