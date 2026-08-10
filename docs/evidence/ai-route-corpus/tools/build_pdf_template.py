#!/usr/bin/env python3
"""book-041 원본 PDF 조립용 HTML 생성. book-047의 build_pdf.py 패턴을 일반화."""
import base64
import html
import json
from pathlib import Path

HERE = Path(__file__).parent
REPO = Path("/Users/t2025-m0204/Documents/sparta/Ilgeobolkka")
OUT = HERE / "pdfbuild041"
MS = json.loads((REPO / "docs/evidence/ai-route-corpus/book-041-manuscript.json").read_text("utf-8"))
IMAGE_PAGES = {7, 19, 24, 42}

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
    parts = [f"<!doctype html><html lang='ko'><head><meta charset='utf-8'>"
             f"<title>{html.escape(MS['title'])}</title><style>{CSS}</style></head><body>"]
    for page in MS["pages"]:
        n = page["pageNumber"]
        if n in IMAGE_PAGES:
            uri = data_uri(OUT / f"fig-{n:02d}.png")
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
            f"<div class='hd'>{html.escape(MS['title'])}<span class='sec'>{html.escape(page['chapter'])}</span></div>"
            f"<h1 class='sect'>{html.escape(page['section'])}</h1>{paras}"
            f"<div class='pn'>{n}</div></section>")
    parts.append("</body></html>")
    dest = OUT / "book-041.html"
    dest.write_text("".join(parts), encoding="utf-8")
    print(f"{dest}  ({dest.stat().st_size:,} bytes, {len(MS['pages'])} pages)")


if __name__ == "__main__":
    main()
