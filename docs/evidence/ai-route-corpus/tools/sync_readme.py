#!/usr/bin/env python3
"""`fixtures/content/ai-route-v2/README.md`의 수치를 manifest·evaluation·원고에서 다시 계산해 맞춘다.

README에는 권수·페이지·이미지·글자 수·시나리오 분포가 여러 자리에 흩어져 있다. 도서를 하나 넣을
때마다 손으로 고치면 어긋나므로 데이터에서 다시 계산한다. 계산할 수 없는 것은 카테고리 하나뿐이라
새 도서를 넣을 때만 인자로 받는다.

사용:
    python3 sync_readme.py                # 집계 수치를 데이터에 맞추고 어긋난 표 행을 알려 준다
    python3 sync_readme.py --add 90 여행   # 새 도서 행까지 추가한다 (제목·페이지·시나리오는 데이터에서 읽는다)
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
FIXTURE = REPO / "fixtures/content/ai-route-v2"
MANUSCRIPTS = REPO / "docs/evidence/ai-route-corpus"


def manuscript(book_id):
    return json.loads((MANUSCRIPTS / f"book-{book_id:03d}-manuscript.json").read_text("utf-8"))


def body_pages(pages):
    return [p for p in pages if p["contentFormat"] == "TEXT" and p["pageNumber"] > 1]


def scenario_of(case):
    return f"소장 {case['depth']}" if case["owned"] else f"비소장 예산 {case['maxAdditionalInk']}"


def main():
    add_id = add_category = None
    if len(sys.argv) > 1:
        if sys.argv[1] != "--add" or len(sys.argv) != 4:
            raise SystemExit(__doc__)
        add_id, add_category = int(sys.argv[2]), sys.argv[3]

    manifest = json.loads((FIXTURE / "manifest.json").read_text("utf-8"))
    evaluation = json.loads((FIXTURE / "evaluation.json").read_text("utf-8"))
    non = sorted((b for b in manifest["books"] if b["aiRouteCandidate"]), key=lambda b: b["bookId"])
    cases = {c["bookId"]: c for c in evaluation["cases"]}
    total_books = len(manifest["books"])
    remaining = 90 - len(non)

    pages = sum(b["totalPageCount"] for b in non)
    images = text_pages = chars = 0
    lo = hi = None
    averages = {}
    for book in non:
        ms = manuscript(book["bookId"])
        body = body_pages(ms["pages"])
        images += sum(1 for p in ms["pages"] if p["contentFormat"] == "IMAGE")
        text_pages += len(body)
        lengths = [len(p["body"]) for p in body]
        chars += sum(lengths)
        lo = min(lengths) if lo is None else min(lo, min(lengths))
        hi = max(lengths) if hi is None else max(hi, max(lengths))
        averages[book["bookId"]] = sum(lengths) / len(lengths)

    counts = Counter(c["depth"] or f"예산 {c['maxAdditionalInk']}" for c in evaluation["cases"])
    groups = {}
    for name in ["BALANCED", "QUICK", "DEEP", "예산 0", "예산 5", "예산 10", "예산 15"]:
        groups.setdefault(counts[name], []).append(name)
    # 조사는 붙이지 않는다. 'BALANCED이'처럼 라틴 문자·숫자 뒤에서 받침 판정이 틀리기 때문이다.
    distribution = ", ".join(
        f"{'·'.join(names)} 각 {n}건" if len(names) > 1 else f"{names[0]} {n}건"
        for n, names in sorted(groups.items(), reverse=True))

    path = FIXTURE / "README.md"
    text = path.read_text("utf-8")

    def sub(pattern, replacement, flags=0):
        nonlocal text
        text, n = re.subn(pattern, replacement, text, count=1, flags=flags)
        if n != 1:
            raise SystemExit(f"패턴을 한 번 바꾸지 못했다: {pattern}")

    sub(r"## 현재 상태: \d+권 — 비소설 \d+ \+ 소설 10 \([^)\n]*\)",
        f"## 현재 상태: {total_books}권 — 비소설 {len(non)} + 소설 10 "
        f"({'완성' if remaining == 0 else '확장 중'})")
    # 확장이 끝나면 '남은 N권' 문장이 '남은 0권'이 되어 뜻이 어긋난다. 문단째 다시 쓴다.
    if remaining:
        intro = (f"정본은 최종적으로 100권을 요구하지만 지금은 {total_books}권이 들어 있고, "
                 "**이 상태로 적재하는 것이 현재\n목표다.** 평가 시나리오 일곱 가지를 모두 채웠고, "
                 f"남은 비소설 {remaining}권은 같은 절차로 늘린다. 소설 10권은")
    else:
        intro = ("정본이 요구하는 100권을 모두 채웠고, **이 상태로 적재하는 것이 현재\n"
                 "목표다.** 평가 시나리오 일곱 가지를 모두 쓴다. 소설 10권은")
    # `^`로 줄 시작에 묶는다. 앞 문단의 링크 텍스트에도 '정본'이 있어 그냥 찾으면 머리말까지 삼킨다.
    sub(r"^정본.*?(?=\n`initial-v1`의)", intro, flags=re.DOTALL | re.MULTILINE)
    sub(r"\| 도서 수 \| 100권 \(비소설 90 \+ 소설 10\) \| \*\*\d+권\*\* \(비소설 \d+ \+ 소설 10\) \|",
        f"| 도서 수 | 100권 (비소설 90 + 소설 10) | **{total_books}권** (비소설 {len(non)} + 소설 10) |")
    sub(r"\| 평가 케이스 \| 90건, 일곱 시나리오 전부 \| \*\*\d+건\*\*, 일곱 시나리오를 모두 사용 \([^|]*\) \|",
        f"| 평가 케이스 | 90건, 일곱 시나리오 전부 | **{len(evaluation['cases'])}건**, "
        f"일곱 시나리오를 모두 사용 ({distribution}) |")
    # 더 넣을 도서가 없으면 확장 안내 문장 자체를 뺀다.
    sub(r"(?:확장 시 비소설 \d+권을 `books\[\]`에, 평가 \d+건을 `cases\[\]`에 추가한다\. )?"
        r"소설 10권은\n`fixtures/content/manifest\.json`",
        (f"확장 시 비소설 {remaining}권을 `books[]`에, 평가 {remaining}건을 `cases[]`에 추가한다. "
         if remaining else "")
        + "소설 10권은\n`fixtures/content/manifest.json`")
    sub(r"비소설 \d+권 합계는 [\d,]+페이지이며 그중 목차 \d+페이지, 이미지 \d+페이지, 본문 TEXT [\d,]+페이지다\.",
        f"비소설 {len(non)}권 합계는 {pages:,}페이지이며 그중 목차 {len(non)}페이지, "
        f"이미지 {images}페이지, 본문 TEXT {text_pages:,}페이지다.")
    sub(r"manifest 전체는 [\d,]+페이지다\.", f"manifest 전체는 {pages + 40:,}페이지다.")
    sub(r"`TEXT` 페이지당 \d+~\d+자, \d+권 합계 약 [\d.]+만 자다",
        f"`TEXT` 페이지당 {lo}~{hi}자, {len(non)}권 합계 약 {chars / 10000:.1f}만 자다")
    lowest, highest = min(averages, key=averages.get), max(averages, key=averages.get)
    sub(r"\d+자\(book-\d+\)에서 \d+자\(book-\d+\)까지 갈린다",
        f"{round(averages[lowest])}자(book-{lowest:03d})에서 {round(averages[highest])}자(book-{highest:03d})까지 갈린다")

    if add_id is not None:
        book = next(b for b in non if b["bookId"] == add_id)
        ms = manuscript(add_id)
        chapters = len({p["chapter"] for p in ms["pages"]}) - 1  # 앞부분(목차) 제외
        image_count = sum(1 for p in ms["pages"] if p["contentFormat"] == "IMAGE")
        row = (f"| {add_id} | {book['title']} | {add_category} | {book['totalPageCount']} | "
               f"{chapters} | {image_count} | {scenario_of(cases[add_id])} |")
        previous = [b["bookId"] for b in non if b["bookId"] < add_id][-1]
        sub(rf"(\| {previous} \| [^\n]*\|\n)", lambda m: m.group(1) + row + "\n")
        lengths = [len(p["body"]) for p in body_pages(ms["pages"])]
        sub(r"(\n\| 페이지당 글자 수 \| 도서 \|\n\| --- \| --- \|\n)",
            lambda m: (m.group(1) + f"| {min(lengths) // 5 * 5}~{-(-max(lengths) // 5) * 5}자 | "
                       f"{add_id} |\n"))

    path.write_text(text, encoding="utf-8")

    # 한 권만 적힌 글자 수 행이 원고와 어긋나면 알려만 준다. 반올림 관례가 도서마다 달라
    # 자동으로 고치면 남의 행까지 건드리게 된다.
    stale = []
    for match in re.finditer(r"\| (?P<lo>\d+)~(?P<hi>\d+)자 \| (?P<id>\d+) \|", text):
        book_id = int(match.group("id"))
        lengths = [len(p["body"]) for p in body_pages(manuscript(book_id)["pages"])]
        want = (min(lengths) // 5 * 5, -(-max(lengths) // 5) * 5)
        if abs(int(match.group("lo")) - want[0]) > 5 or abs(int(match.group("hi")) - want[1]) > 5:
            stale.append(f"book-{book_id:03d}: 표 {match.group('lo')}~{match.group('hi')}자 "
                         f"↔ 원고 {min(lengths)}~{max(lengths)}자")
    if stale:
        print("글자 수 표가 원고와 어긋난다 (직접 고칠 것):")
        for line in stale:
            print("  " + line)
    print(f"README 갱신: 도서 {total_books}권 · 평가 {len(evaluation['cases'])}건 · "
          f"비소설 {pages:,}p(이미지 {images}) · 본문 {chars:,}자")


if __name__ == "__main__":
    main()
