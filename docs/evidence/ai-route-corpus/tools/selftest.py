#!/usr/bin/env python3
"""검증 도구들이 깨진 입력을 실제로 잡아내는지 확인하는 자체 테스트.

검증 도구가 항상 통과만 하면 검증하지 않는 것과 같으므로, 정본 fixture에서 입력을 만들어
한 곳씩 고의로 깨뜨리고 각각이 FAIL로 걸리는지 본다. 세 도구를 모두 덮는다 —
`validate_fragment.py`(병합 전 도서 하나), `validate_manifest.py`(병합된 정본 전체),
`corpus_lib.validate_book()`(생성 즉시). 모든 실행은 저장소 밖 임시 디렉터리를 작업
디렉터리로 삼아, 도구가 실행 위치와 무관하게 저장소 루트를 찾는지도 함께 확인한다.

사용: python3 selftest.py
"""
import copy
import json
import subprocess
import sys
import tempfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
REPO = TOOLS.parents[3]
FIXTURE = REPO / "fixtures/content/ai-route-v2"
# 정상 표본은 선수 폐쇄까지 예산 안에 드는 도서여야 한다. 현재 정본 10권 중 book-041뿐이고
# 나머지 9권은 정답 경로의 선수 페이지가 예산·깊이 상한을 넘어 검증기가 FAIL로 잡는다.
SAMPLE_BOOK_ID = 41
VALID_SUBSET_BOOK_IDS = {41}

sys.path.insert(0, str(TOOLS))
import corpus_lib  # noqa: E402  (경로를 붙인 뒤에만 import할 수 있다)

failures = []


def report(name, ok, detail):
    print(("  OK   " if ok else "  FAIL ") + name)
    if not ok:
        failures.append(name)
        print("    " + detail.strip().replace("\n", "\n    "))


def load_manifest():
    return json.loads((FIXTURE / "manifest.json").read_text("utf-8"))


def load_evaluation():
    return json.loads((FIXTURE / "evaluation.json").read_text("utf-8"))


def load_fragment(book_id):
    """정본 manifest·evaluation에서 도서 하나를 조각 형태로 되돌린다."""
    book = next(b for b in load_manifest()["books"] if b["bookId"] == book_id)
    case = next(c for c in load_evaluation()["cases"] if c["bookId"] == book_id)
    return {"manifestBook": book, "evaluationCase": case}


def load_valid_subset():
    """모든 계약을 만족하는 도서만 남긴 정본의 부분 집합.

    정본 전체는 지금 선수 폐쇄 예산 계약을 만족하지 않고, `validate_manifest.py`가 그것을
    FAIL로 드러내는 것이 정상이다. 도구 자체의 회귀를 보는 양성 검사에는 통과가 보장된 입력이
    필요하므로 부분 집합을 쓴다. 검증이 권수를 세지 않으므로 부분 집합도 정상 입력이다.
    """
    manifest, evaluation = load_manifest(), load_evaluation()
    manifest["books"] = [b for b in manifest["books"] if b["bookId"] in VALID_SUBSET_BOOK_IDS]
    evaluation["cases"] = [c for c in evaluation["cases"] if c["bookId"] in VALID_SUBSET_BOOK_IDS]
    return manifest, evaluation


def closure_only_pages(fragment):
    """정답 경로에는 없고 선수 폐쇄로만 끌려 들어오는 페이지."""
    book = fragment["manifestBook"]
    prereq = {p["pageNumber"]: p["prerequisitePageNumbers"] for p in book["pages"]}
    ref = set(fragment["evaluationCase"]["referencePageNumbers"])
    closed, stack = set(ref), list(ref)
    while stack:
        for q in prereq.get(stack.pop(), []):
            if q not in closed:
                closed.add(q)
                stack.append(q)
    return sorted(closed - ref)


def run_tool(argv, workdir):
    return subprocess.run([sys.executable] + argv, capture_output=True, text=True, cwd=workdir)


def expect(name, fragment, workdir, should_pass, needle=None):
    """validate_fragment.py에 조각을 넣고 기대한 판정이 나오는지 본다."""
    path = Path(workdir) / "fragment.json"
    path.write_text(json.dumps(fragment, ensure_ascii=False), encoding="utf-8")
    result = run_tool([str(TOOLS / "validate_fragment.py"), str(path)], workdir)
    ok = (result.returncode == 0) == should_pass
    if ok and needle is not None:
        ok = any(needle in l for l in result.stdout.split("\n") if l.startswith("  FAIL"))
    report(name, ok, result.stdout or result.stderr)


def expect_manifest(name, manifest, evaluation, workdir, should_pass, needle=None):
    """validate_manifest.py에 fixture 디렉터리 하나를 통째로 넣고 판정을 본다.

    PDF는 용량이 크므로 복사하지 않고 정본 디렉터리를 심링크로 잇는다.
    """
    fixture_dir = Path(workdir) / "fixture"
    if not fixture_dir.exists():
        fixture_dir.mkdir()
        (fixture_dir / "pdfs").symlink_to(FIXTURE / "pdfs")
    (fixture_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
    (fixture_dir / "evaluation.json").write_text(json.dumps(evaluation, ensure_ascii=False), encoding="utf-8")
    result = run_tool([str(TOOLS / "validate_manifest.py"), str(fixture_dir)], workdir)
    ok = (result.returncode == 0) == should_pass
    if ok and needle is not None:
        ok = any(needle in l for l in result.stdout.split("\n") if l.startswith("  FAIL"))
    report(name, ok, result.stdout or result.stderr)


def expect_book(name, manifest_pages, manuscript_pages, should_pass, needle=None):
    """corpus_lib.validate_book()이 SystemExit으로 막는지 본다."""
    try:
        corpus_lib.validate_book(manifest_pages, manuscript_pages)
        passed, detail = True, ""
    except SystemExit as exc:
        passed, detail = False, str(exc)
    ok = passed == should_pass
    if ok and needle is not None:
        ok = needle in detail
    report(name, ok, detail or "통과해 버림")


def chain_prerequisites(pages):
    """후보 페이지를 한 줄 사슬로 이어 선수 폐쇄를 페이지 번호만큼 키운다.

    밀도 상한을 거는 가장 단순한 방법이다. 뒤쪽 페이지는 폐쇄가 책 전체만큼 커지고, 폐쇄가
    작은 페이지는 앞쪽 한 장에만 몰린다.
    """
    prev = None
    for p in pages:
        if not p["aiRouteCandidatePage"]:
            continue
        p["prerequisitePageNumbers"] = [] if prev is None else [prev]
        prev = p["pageNumber"]
    return pages


def check_fragment(workdir):
    print("[validate_fragment.py]")
    base = load_fragment(SAMPLE_BOOK_ID)
    expect(f"정상 조각(book-{SAMPLE_BOOK_ID})은 통과", base, workdir, should_pass=True)

    broken = copy.deepcopy(base)
    groups = broken["evaluationCase"]["duplicatePageGroups"]
    # 원래 그룹에 없던 후보 페이지로 갈아끼워야 실제로 깨진다. 마지막 페이지를 쓰면 그 페이지가
    # 이미 그룹에 든 도서(book-041)에서 같은 그룹이 나와 아무것도 깨뜨리지 못한다.
    outsider = next(p["pageNumber"] for p in reversed(broken["manifestBook"]["pages"])
                    if p["aiRouteCandidatePage"] and p["pageNumber"] not in groups[0])
    groups[0] = [groups[0][0], outsider]
    expect("평가의 중복그룹만 바꾸면 실패", broken, workdir,
           should_pass=False, needle="duplicateGroupKeys 그룹 == duplicatePageGroups")

    broken = copy.deepcopy(base)
    for page in broken["manifestBook"]["pages"]:
        if page["duplicateGroupKeys"]:
            page["duplicateGroupKeys"] = []
            break
    expect("manifest의 중복 표시 한쪽을 지우면 실패", broken, workdir,
           should_pass=False, needle="중복그룹은 2페이지 이상")

    broken = copy.deepcopy(base)
    del broken["manifestBook"]["title"]
    expect("title이 없으면 실패", broken, workdir, should_pass=False, needle="title 존재")

    broken = copy.deepcopy(base)
    group = broken["evaluationCase"]["duplicatePageGroups"][0]
    broken["evaluationCase"]["referencePageNumbers"] = sorted(
        set(broken["evaluationCase"]["referencePageNumbers"]) | set(group))
    expect("정답 경로에 같은 중복 그룹 페이지를 둘 넣으면 실패", broken, workdir,
           should_pass=False, needle="같은 중복 그룹 페이지")

    broken = copy.deepcopy(base)
    broken["evaluationCase"]["maxAdditionalInk"] = 0
    broken["evaluationCase"]["activeRentalPageNumbers"] = []
    expect("정답 경로가 예산을 넘으면 실패", broken, workdir,
           should_pass=False, needle="≤ 예산")

    broken = copy.deepcopy(base)
    broken["manifestBook"]["pdfSha256"] = "0" * 64
    expect("PDF SHA-256이 다르면 실패", broken, workdir,
           should_pass=False, needle="PDF SHA-256")

    broken = copy.deepcopy(base)
    fm = next(p["pageNumber"] for p in broken["manifestBook"]["pages"] if not p["aiRouteCandidatePage"])
    broken["evaluationCase"]["allowedAlternativePageNumbers"] = [fm]
    expect("대체 페이지에 비후보를 넣으면 실패", broken, workdir,
           should_pass=False, needle="대체 페이지에 비후보")

    # 정답 페이지 자체는 모두 대여 상태여도, 그 선수 페이지 하나가 대여 밖이면 예산 0으로 열 수 없다.
    broken = copy.deepcopy(base)
    hidden = closure_only_pages(broken)[0]
    broken["evaluationCase"]["activeRentalPageNumbers"] = [
        p for p in broken["evaluationCase"]["activeRentalPageNumbers"] if p != hidden]
    expect("정답 경로의 선수 페이지가 대여 밖이면 실패", broken, workdir,
           should_pass=False, needle="선수 폐쇄 포함")

    broken = copy.deepcopy(base)
    broken["evaluationCase"].update(owned=True, maxAdditionalInk=None, depth="QUICK")
    expect("소장 경로가 깊이 상한을 넘으면 실패", broken, workdir,
           should_pass=False, needle="QUICK 상한")

    broken = copy.deepcopy(base)
    chain_prerequisites(broken["manifestBook"]["pages"])
    expect("선수를 한 줄로 길게 이으면 밀도 상한에 걸림", broken, workdir,
           should_pass=False, needle="선수 폐쇄")


def check_manifest(workdir):
    print("\n[validate_manifest.py]")
    manifest, evaluation = load_valid_subset()
    expect_manifest("계약을 만족하는 부분 집합은 통과", manifest, evaluation, workdir, should_pass=True)

    broken = copy.deepcopy(manifest)
    broken["books"][0]["totalPageCount"] += 1
    expect_manifest("totalPageCount가 페이지 수와 다르면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="totalPageCount")

    broken = copy.deepcopy(manifest)
    page = next(p for p in broken["books"][0]["pages"] if p["prerequisitePageNumbers"])
    page["prerequisitePageNumbers"] = [page["pageNumber"]]
    expect_manifest("선수 관계가 자기 자신을 가리키면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="자기 참조 없음")

    broken = copy.deepcopy(manifest)
    for p in broken["books"][0]["pages"]:
        if p["duplicateGroupKeys"]:
            p["duplicateGroupKeys"] = []
            break
    expect_manifest("중복 표시 한쪽을 지우면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="중복그룹은 2페이지 이상")

    broken = copy.deepcopy(manifest)
    page = broken["books"][0]["pages"][1]
    page["estimatedReadingSeconds"] = 0
    expect_manifest("독서시간이 0이면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="독서시간 > 0")

    broken = copy.deepcopy(evaluation)
    case = broken["cases"][0]
    book = next(b for b in manifest["books"] if b["bookId"] == case["bookId"])
    case["allowedAlternativePageNumbers"] = [
        p["pageNumber"] for p in book["pages"] if not p["aiRouteCandidatePage"]]
    expect_manifest("대체 페이지에 비후보를 넣으면 실패", manifest, broken, workdir,
                    should_pass=False, needle="대체 페이지에 비후보")

    broken = copy.deepcopy(evaluation)
    broken["contentVersion"] = "ai-route-v3"
    expect_manifest("evaluation contentVersion이 어긋나면 실패", manifest, broken, workdir,
                    should_pass=False, needle="evaluation contentVersion")

    broken = copy.deepcopy(evaluation)
    case = broken["cases"][0]
    hidden = closure_only_pages({"manifestBook": manifest["books"][0], "evaluationCase": case})[0]
    case["activeRentalPageNumbers"] = [p for p in case["activeRentalPageNumbers"] if p != hidden]
    expect_manifest("정답 경로의 선수 페이지가 대여 밖이면 실패", manifest, broken, workdir,
                    should_pass=False, needle="선수 폐쇄 포함")

    # 부분 집합에 소장 사례가 없으므로 비소장 사례를 소장으로 바꿔 소장 계약만 따로 확인한다.
    owned_evaluation = copy.deepcopy(evaluation)
    owned_evaluation["cases"][0].update(owned=True, maxAdditionalInk=None, depth="DEEP")
    expect_manifest("소장 사례로 바꾼 부분 집합은 통과", manifest, owned_evaluation, workdir,
                    should_pass=True)

    broken = copy.deepcopy(owned_evaluation)
    broken["cases"][0]["depth"] = None
    expect_manifest("소장 사례에 depth가 없으면 실패", manifest, broken, workdir,
                    should_pass=False, needle="소장 사례는 depth 지정")

    broken = copy.deepcopy(owned_evaluation)
    broken["cases"][0]["depth"] = "QUICK"
    expect_manifest("소장 경로가 깊이 상한을 넘으면 실패", manifest, broken, workdir,
                    should_pass=False, needle="QUICK 상한")

    broken = copy.deepcopy(manifest)
    chain_prerequisites(broken["books"][0]["pages"])
    expect_manifest("선수를 한 줄로 길게 이으면 밀도 상한에 걸림", broken, evaluation, workdir,
                    should_pass=False, needle="선수 폐쇄")


def check_validate_book():
    print("\n[corpus_lib.validate_book()]")
    book = next(b for b in load_manifest()["books"] if b["bookId"] == SAMPLE_BOOK_ID)
    manuscript = json.loads(
        (REPO / f"docs/evidence/ai-route-corpus/book-{SAMPLE_BOOK_ID:03d}-manuscript.json").read_text("utf-8"))
    base_manifest, base_manuscript = book["pages"], manuscript["pages"]
    expect_book(f"정상 도서(book-{SAMPLE_BOOK_ID})는 통과", base_manifest, base_manuscript, should_pass=True)

    broken = copy.deepcopy(base_manifest)
    broken[1]["aiAnalysisInputSha256"] = "0" * 64
    expect_book("분석텍스트 해시가 다르면 실패", broken, base_manuscript,
                should_pass=False, needle="aiAnalysisInputSha256 불일치")

    broken = copy.deepcopy(base_manuscript)
    broken[1]["chapter"] = "없는 장"
    expect_book("원고 장이 manifest와 다르면 실패", base_manifest, broken,
                should_pass=False, needle="장·절 불일치")

    broken = copy.deepcopy(base_manifest)
    a, b = broken[1]["pageNumber"], broken[2]["pageNumber"]
    broken[1]["prerequisitePageNumbers"] = [b]
    broken[2]["prerequisitePageNumbers"] = [a]
    expect_book("선수 관계에 순환이 있으면 실패", broken, base_manuscript,
                should_pass=False, needle="위상 정렬")

    broken = copy.deepcopy(base_manifest)
    broken[1]["primaryConcepts"] = []
    expect_book("primaryConcepts가 비면 실패", broken, base_manuscript,
                should_pass=False, needle="primaryConcepts 비어있음")

    broken = chain_prerequisites(copy.deepcopy(base_manifest))
    expect_book("선수를 한 줄로 길게 이으면 밀도 상한에 걸림", broken, base_manuscript,
                should_pass=False, needle="선수 폐쇄")


def main():
    with tempfile.TemporaryDirectory() as workdir:
        check_fragment(workdir)
        check_manifest(workdir)
    check_validate_book()

    print("\n" + (f"실패 {len(failures)}건" if failures else "전체 통과"))
    for name in failures:
        print("  - " + name)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
