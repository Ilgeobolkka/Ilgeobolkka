#!/usr/bin/env python3
"""Poppler 없이 PDF의 페이지 수와 페이지별 텍스트/이미지 구성을 확인한다.

Chrome이 만든 PDF의 객체를 직접 파싱한다. 표준 라이브러리만 쓴다.

사용:
    python3 pdfcheck.py <pdf> [페이지수] [이미지페이지,...]
    python3 pdfcheck.py --book 83 [pdf]   # 기대값을 원고에서 읽는다

`--book`을 쓰면 페이지 수와 이미지 페이지를 손으로 적지 않는다. 손으로 적으면 나중에 페이지가
밀렸을 때 PDF와 원고가 어긋난 것을 검사기가 놓친다.
"""
import json
import re
import sys
import zlib
from pathlib import Path

argv = sys.argv[1:]
if argv and argv[0] == "--book":
    book_id = int(argv[1])
    repo = Path(__file__).resolve().parents[4]
    manuscript = json.loads(
        (repo / f"docs/evidence/ai-route-corpus/book-{book_id:03d}-manuscript.json").read_text("utf-8"))
    pages = manuscript["pages"]
    pdf = argv[2] if len(argv) > 2 else repo / f"fixtures/content/ai-route-v2/pdfs/book-{book_id:03d}.pdf"
    argv = [str(pdf), str(len(pages)),
            ",".join(str(p["pageNumber"]) for p in pages if p["contentFormat"] == "IMAGE")]

data = Path(argv[0]).read_bytes()

# ── 객체 수집: "N 0 obj ... endobj" ──────────────────────────────
objs = {}
for m in re.finditer(rb"(\d+)\s+(\d+)\s+obj\b", data):
    num = int(m.group(1))
    end = data.find(b"endobj", m.end())
    if end > 0:
        objs[num] = data[m.end():end]


def stream_of(body):
    m = re.search(rb"stream\r?\n", body)
    if not m:
        return None
    raw = body[m.end():]
    raw = raw[:raw.rfind(b"endstream")] if b"endstream" in raw else raw
    if b"FlateDecode" in body[:m.start()]:
        try:
            return zlib.decompress(raw)
        except zlib.error:
            try:
                return zlib.decompressobj().decompress(raw)
            except zlib.error:
                return None
    return raw


# ── 페이지 트리 ──────────────────────────────────────────────────
page_nums = [n for n, b in objs.items() if re.search(rb"/Type\s*/Page\b(?!s)", b)]
counts = [int(m.group(1)) for n, b in objs.items()
          if re.search(rb"/Type\s*/Pages\b", b) for m in re.finditer(rb"/Count\s+(\d+)", b)]

# 루트 /Pages(가장 큰 /Count)에서 시작해 /Kids를 재귀로 걸어 페이지 순서를 얻는다
def kids_of(num):
    b = objs.get(num, b"")
    km = re.search(rb"/Kids\s*\[(.*?)\]", b, re.S)
    return [int(x) for x in re.findall(rb"(\d+)\s+\d+\s+R", km.group(1))] if km else []


page_set = set(page_nums)
root = None
best = -1
for n, b in objs.items():
    if re.search(rb"/Type\s*/Pages\b", b):
        cm = re.search(rb"/Count\s+(\d+)", b)
        if cm and int(cm.group(1)) > best:
            best, root = int(cm.group(1)), n

ordered, stack = [], [root] if root else []
seen = set()
while stack:
    node = stack.pop(0)
    if node in seen:
        continue
    seen.add(node)
    if node in page_set:
        ordered.append(node)
    else:
        stack = kids_of(node) + stack
if not ordered:
    ordered = sorted(page_nums)

print(f"객체 수: {len(objs)}")
print(f"/Type /Page 객체: {len(page_nums)}개")
print(f"/Type /Pages 의 /Count: {counts}")
print(f"/Kids 순서로 정렬된 페이지: {len(ordered)}개")

# ── 페이지별 콘텐츠 스트림 분석 ─────────────────────────────────
text_pages, image_pages, empty_pages = [], [], []
for idx, pn in enumerate(ordered, start=1):
    body = objs[pn]
    cm = re.search(rb"/Contents\s+(\d+)\s+\d+\s+R", body)
    if not cm:
        empty_pages.append(idx)
        continue
    cs = stream_of(objs.get(int(cm.group(1)), b"")) or b""
    has_text = bool(re.search(rb"\bT[jJ]\b", cs))
    has_image = bool(re.search(rb"\bDo\b", cs))  # XObject를 그리는 연산자
    if has_text:
        text_pages.append(idx)
    elif has_image:
        image_pages.append(idx)
    else:
        empty_pages.append(idx)

print(f"\n텍스트 연산자(Tj/TJ)가 있는 페이지: {len(text_pages)}개")
print(f"이미지만 있는 페이지: {image_pages}")
if empty_pages:
    print(f"내용 없음: {empty_pages}")

expected_total = int(argv[1]) if len(argv) > 1 else len(ordered)
expected_images = [int(x) for x in argv[2].split(",")] if len(argv) > 2 else image_pages
ok = (len(ordered) == expected_total and counts and max(counts) == expected_total
      and image_pages == expected_images and len(text_pages) == expected_total - len(expected_images))
print("\n판정:", "통과" if ok else "불일치")
print(f"  페이지 수 {expected_total}            → {len(ordered)}")
print(f"  IMAGE {expected_images} → {image_pages}")
print(f"  TEXT {expected_total - len(expected_images)}개               → {len(text_pages)}")
sys.exit(0 if ok else 1)
