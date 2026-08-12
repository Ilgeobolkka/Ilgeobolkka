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
import os
import subprocess
import sys
import tempfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
REPO = TOOLS.parents[3]
FIXTURE = REPO / "fixtures/content/ai-route-v2"
# 조각 검사의 표본은 예산 0 사례여야 예산·대여를 깨뜨리는 음성 검사를 만들 수 있다.
SAMPLE_BOOK_ID = 11

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


def prerequisite_inside_route(book, case):
    """정답 경로 안에서 다른 경로 페이지의 선수인 페이지 하나.

    정답 경로는 이제 선수 폐쇄로 적으므로, 이 페이지를 정답과 대여에서 함께 빼도 폐쇄가 되살린다.
    폐쇄를 세지 않는 검증기라면 통과시켰을 입력이라 음성 검사로 쓴다.
    """
    route = set(case["referencePageNumbers"])
    prereq = {p["pageNumber"]: p["prerequisitePageNumbers"] for p in book["pages"]}
    return next(q for n in sorted(route) for q in sorted(prereq[n]) if q in route)


def prerequisite_outside_required(book, case):
    """실제 DAG에는 있지만 평가 정답의 필수 선수 간선에는 없는 간선 하나."""
    required = {
        (e["beforePageNumber"], e["afterPageNumber"])
        for e in case["requiredPrerequisites"]
    }
    return next(
        {"beforePageNumber": before, "afterPageNumber": page["pageNumber"]}
        for page in book["pages"]
        for before in page["prerequisitePageNumbers"]
        if (before, page["pageNumber"]) not in required
    )


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


def candidate_book(manifest):
    """AI 경로 후보 도서 중 첫 권. 정본 앞머리는 pages[]가 빈 소설이라 그대로 쓰면 안 된다."""
    return next(b for b in manifest["books"] if b["aiRouteCandidate"])


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
    front_matter = next(p for p in broken["manifestBook"]["pages"]
                        if not p["aiRouteCandidatePage"])
    front_matter["primaryConcepts"] = ["비후보 전용 필수 개념"]
    broken["evaluationCase"]["requiredConcepts"] = ["비후보 전용 필수 개념"]
    expect("필수 개념이 비후보 페이지에만 있으면 실패", broken, workdir,
           should_pass=False, needle="requiredConcepts가 후보 primaryConcepts")

    broken = copy.deepcopy(base)
    front_matter = next(p for p in broken["manifestBook"]["pages"]
                        if not p["aiRouteCandidatePage"])
    front_matter["secondaryConcepts"] = ["비후보 전용 도움 개념"]
    broken["evaluationCase"]["helpfulConcepts"] = ["비후보 전용 도움 개념"]
    expect("도움 개념이 비후보 페이지에만 있으면 실패", broken, workdir,
           should_pass=False, needle="helpfulConcepts가 후보 primary/secondaryConcepts")

    broken = copy.deepcopy(base)
    case = broken["evaluationCase"]
    outside = next(p for p in broken["manifestBook"]["pages"]
                   if p["aiRouteCandidatePage"]
                   and p["pageNumber"] not in case["referencePageNumbers"])
    outside["primaryConcepts"] = ["정답 경로 밖 필수 개념"]
    case["requiredConcepts"] = ["정답 경로 밖 필수 개념"]
    expect("정답 경로가 필수 개념을 덮지 않으면 실패", broken, workdir,
           should_pass=False, needle="referencePageNumbers가 requiredConcepts")

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

    broken = copy.deepcopy(base)
    broken["evaluationCase"]["irrelevantPageNumbers"] = [fm]
    expect("무관 페이지에 비후보를 넣으면 실패", broken, workdir,
           should_pass=False, needle="무관 페이지에 비후보")

    broken = copy.deepcopy(base)
    case = broken["evaluationCase"]
    outside = next(p["pageNumber"] for p in broken["manifestBook"]["pages"]
                   if p["aiRouteCandidatePage"]
                   and p["pageNumber"] not in case["referencePageNumbers"])
    case["allowedAlternativePageNumbers"] = [outside]
    case["irrelevantPageNumbers"] = [outside]
    expect("대체 페이지와 무관 페이지가 겹치면 실패", broken, workdir,
           should_pass=False, needle="대체∩무관")

    broken = copy.deepcopy(base)
    front_matter = next(p for p in broken["manifestBook"]["pages"]
                        if not p["aiRouteCandidatePage"])
    broken["evaluationCase"]["activeRentalPageNumbers"].append(front_matter["pageNumber"])
    expect("활성 대여에 비후보를 넣으면 실패", broken, workdir,
           should_pass=False, needle="activeRental에 비후보")

    broken = copy.deepcopy(base)
    front_matter = next(p for p in broken["manifestBook"]["pages"]
                        if not p["aiRouteCandidatePage"])
    front_matter["duplicateGroupKeys"] = ["비후보 중복 그룹"]
    expect("비후보 페이지에 중복 그룹을 넣으면 실패", broken, workdir,
           should_pass=False, needle="비후보 페이지 duplicateGroupKeys")

    broken = copy.deepcopy(base)
    group = broken["evaluationCase"]["duplicatePageGroups"][0]
    broken["evaluationCase"]["duplicatePageGroups"][0] = [group[0]] + group
    expect("중복 그룹 안에 같은 페이지를 두 번 넣으면 실패", broken, workdir,
           should_pass=False, needle="같은 페이지가 두 번")

    broken = copy.deepcopy(base)
    broken["evaluationCase"]["requiredPrerequisites"].pop()
    expect("선수 폐쇄에서 필수 간선을 누락하면 실패", broken, workdir,
           should_pass=False, needle="누락")

    broken = copy.deepcopy(base)
    required = broken["evaluationCase"]["requiredPrerequisites"]
    required.append(copy.deepcopy(required[0]))
    expect("requiredPrerequisites에 같은 간선을 중복하면 실패", broken, workdir,
           should_pass=False, needle="requiredPrerequisites에 중복")

    broken = copy.deepcopy(base)
    required = prerequisite_outside_required(
        broken["manifestBook"], broken["evaluationCase"])
    broken["evaluationCase"]["requiredPrerequisites"].append(required)
    expect("선수 폐쇄 밖 실제 DAG 간선을 추가하면 실패", broken, workdir,
           should_pass=False, needle="초과")

    broken = copy.deepcopy(base)
    broken["manifestBook"]["aiExternalTransferAllowed"] = False
    expect("외부 전송 권리가 false면 실패", broken, workdir,
           should_pass=False, needle="aiExternalTransferAllowed")

    # 정답에서 선수 페이지를 빼고 대여에서도 빼면, 폐쇄가 그 페이지를 되살려 예산 0을 넘긴다.
    broken = copy.deepcopy(base)
    hidden = prerequisite_inside_route(broken["manifestBook"], broken["evaluationCase"])
    for field in ("referencePageNumbers", "activeRentalPageNumbers"):
        broken["evaluationCase"][field] = [
            p for p in broken["evaluationCase"][field] if p != hidden]
    expect("정답에서 선수 페이지를 빼도 폐쇄가 되살린다", broken, workdir,
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
    manifest, evaluation = load_manifest(), load_evaluation()
    expect_manifest("정상 정본은 통과", manifest, evaluation, workdir, should_pass=True)

    broken = copy.deepcopy(evaluation)
    broken["cases"].pop()
    expect_manifest("지원 도서의 평가 케이스가 누락되면 실패", manifest, broken, workdir,
                    should_pass=False, needle="평가 case 1:1")

    broken = copy.deepcopy(evaluation)
    duplicate = copy.deepcopy(broken["cases"][0])
    duplicate["caseId"] += "-duplicate"
    broken["cases"].append(duplicate)
    expect_manifest("지원 도서의 평가 케이스가 중복되면 실패", manifest, broken, workdir,
                    should_pass=False, needle="평가 case 중복 없음")

    broken = copy.deepcopy(manifest)
    candidate_book(broken)["totalPageCount"] += 1
    expect_manifest("totalPageCount가 페이지 수와 다르면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="totalPageCount")

    broken = copy.deepcopy(manifest)
    page = next(p for p in candidate_book(broken)["pages"] if p["prerequisitePageNumbers"])
    page["prerequisitePageNumbers"] = [page["pageNumber"]]
    expect_manifest("선수 관계가 자기 자신을 가리키면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="자기 참조 없음")

    broken = copy.deepcopy(manifest)
    for p in candidate_book(broken)["pages"]:
        if p["duplicateGroupKeys"]:
            p["duplicateGroupKeys"] = []
            break
    expect_manifest("중복 표시 한쪽을 지우면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="중복그룹은 2페이지 이상")

    broken = copy.deepcopy(manifest)
    page = candidate_book(broken)["pages"][1]
    page["estimatedReadingSeconds"] = 0
    expect_manifest("독서시간이 0이면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="독서시간 > 0")

    broken_manifest, broken_evaluation = copy.deepcopy(manifest), copy.deepcopy(evaluation)
    book = candidate_book(broken_manifest)
    case = next(c for c in broken_evaluation["cases"] if c["bookId"] == book["bookId"])
    front_matter = next(p for p in book["pages"] if not p["aiRouteCandidatePage"])
    front_matter["primaryConcepts"] = ["비후보 전용 필수 개념"]
    case["requiredConcepts"] = ["비후보 전용 필수 개념"]
    expect_manifest("필수 개념이 비후보 페이지에만 있으면 실패",
                    broken_manifest, broken_evaluation, workdir,
                    should_pass=False, needle="requiredConcepts가 후보 primaryConcepts")

    broken_manifest, broken_evaluation = copy.deepcopy(manifest), copy.deepcopy(evaluation)
    book = candidate_book(broken_manifest)
    case = next(c for c in broken_evaluation["cases"] if c["bookId"] == book["bookId"])
    front_matter = next(p for p in book["pages"] if not p["aiRouteCandidatePage"])
    front_matter["secondaryConcepts"] = ["비후보 전용 도움 개념"]
    case["helpfulConcepts"] = ["비후보 전용 도움 개념"]
    expect_manifest("도움 개념이 비후보 페이지에만 있으면 실패",
                    broken_manifest, broken_evaluation, workdir,
                    should_pass=False, needle="helpfulConcepts가 후보 primary/secondaryConcepts")

    broken_manifest, broken_evaluation = copy.deepcopy(manifest), copy.deepcopy(evaluation)
    book = candidate_book(broken_manifest)
    case = next(c for c in broken_evaluation["cases"] if c["bookId"] == book["bookId"])
    outside = next(p for p in book["pages"] if p["aiRouteCandidatePage"]
                   and p["pageNumber"] not in case["referencePageNumbers"])
    outside["primaryConcepts"] = ["정답 경로 밖 필수 개념"]
    case["requiredConcepts"] = ["정답 경로 밖 필수 개념"]
    expect_manifest("정답 경로가 필수 개념을 덮지 않으면 실패",
                    broken_manifest, broken_evaluation, workdir,
                    should_pass=False, needle="referencePageNumbers가 requiredConcepts")

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

    # 아래 셋은 한동안 조각 검증기에만 있던 검사다. 두 검증기의 기준이 갈리지 않는지 함께 본다.
    broken = copy.deepcopy(manifest)
    candidate_book(broken)["aiExternalTransferAllowed"] = False
    expect_manifest("외부 전송 권리가 false면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="aiExternalTransferAllowed")

    broken = copy.deepcopy(evaluation)
    case = broken["cases"][0]
    book = next(b for b in manifest["books"] if b["bookId"] == case["bookId"])
    case["irrelevantPageNumbers"] = [
        p["pageNumber"] for p in book["pages"] if not p["aiRouteCandidatePage"]]
    expect_manifest("무관 페이지에 비후보를 넣으면 실패", manifest, broken, workdir,
                    should_pass=False, needle="무관 페이지에 비후보")

    broken = copy.deepcopy(evaluation)
    case = broken["cases"][0]
    book = next(b for b in manifest["books"] if b["bookId"] == case["bookId"])
    outside = next(p["pageNumber"] for p in book["pages"] if p["aiRouteCandidatePage"]
                   and p["pageNumber"] not in case["referencePageNumbers"])
    case["allowedAlternativePageNumbers"] = [outside]
    case["irrelevantPageNumbers"] = [outside]
    expect_manifest("대체 페이지와 무관 페이지가 겹치면 실패", manifest, broken, workdir,
                    should_pass=False, needle="대체∩무관")

    broken = copy.deepcopy(evaluation)
    case = broken["cases"][0]
    book = next(b for b in manifest["books"] if b["bookId"] == case["bookId"])
    front_matter = next(p for p in book["pages"] if not p["aiRouteCandidatePage"])
    case["activeRentalPageNumbers"].append(front_matter["pageNumber"])
    expect_manifest("활성 대여에 비후보를 넣으면 실패", manifest, broken, workdir,
                    should_pass=False, needle="activeRental에 비후보")

    broken = copy.deepcopy(manifest)
    book = candidate_book(broken)
    front_matter = next(p for p in book["pages"] if not p["aiRouteCandidatePage"])
    front_matter["duplicateGroupKeys"] = ["비후보 중복 그룹"]
    expect_manifest("비후보 페이지에 중복 그룹을 넣으면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="비후보 페이지 duplicateGroupKeys")

    broken = copy.deepcopy(evaluation)
    group = broken["cases"][0]["duplicatePageGroups"][0]
    broken["cases"][0]["duplicatePageGroups"][0] = [group[0]] + group
    expect_manifest("중복 그룹 안에 같은 페이지를 두 번 넣으면 실패", manifest, broken, workdir,
                    should_pass=False, needle="같은 페이지가 두 번")

    broken = copy.deepcopy(evaluation)
    case = next(c for c in broken["cases"] if c["requiredPrerequisites"])
    case["requiredPrerequisites"].pop()
    expect_manifest("선수 폐쇄에서 필수 간선을 누락하면 실패", manifest, broken, workdir,
                    should_pass=False, needle="누락")

    broken = copy.deepcopy(evaluation)
    case = next(c for c in broken["cases"] if c["requiredPrerequisites"])
    case["requiredPrerequisites"].append(copy.deepcopy(case["requiredPrerequisites"][0]))
    expect_manifest("requiredPrerequisites에 같은 간선을 중복하면 실패", manifest, broken, workdir,
                    should_pass=False, needle="requiredPrerequisites에 중복")

    broken = copy.deepcopy(evaluation)
    case = next(c for c in broken["cases"] if c["requiredPrerequisites"])
    book = next(b for b in manifest["books"] if b["bookId"] == case["bookId"])
    case["requiredPrerequisites"].append(prerequisite_outside_required(book, case))
    expect_manifest("선수 폐쇄 밖 실제 DAG 간선을 추가하면 실패", manifest, broken, workdir,
                    should_pass=False, needle="초과")

    # 소설(비후보 도서)은 initial-v1의 PDF·SHA-256·페이지 수를 그대로 써야 한다.
    broken = copy.deepcopy(manifest)
    novel = next(b for b in broken["books"] if not b["aiRouteCandidate"])
    novel["totalPageCount"] += 1
    expect_manifest("소설 페이지 수가 initial-v1과 다르면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="initial-v1과 같음")

    broken = copy.deepcopy(manifest)
    novel = next(b for b in broken["books"] if not b["aiRouteCandidate"])
    novel["pdfPath"] = "pdfs/book-없는파일.pdf"
    expect_manifest("소설 PDF가 없으면 실패", broken, evaluation, workdir,
                    should_pass=False, needle="PDF 존재")

    broken = copy.deepcopy(evaluation)
    case = next(c for c in broken["cases"] if c["maxAdditionalInk"] == 0)
    book = next(b for b in manifest["books"] if b["bookId"] == case["bookId"])
    hidden = prerequisite_inside_route(book, case)
    for field in ("referencePageNumbers", "activeRentalPageNumbers"):
        case[field] = [p for p in case[field] if p != hidden]
    expect_manifest("정답에서 선수 페이지를 빼도 폐쇄가 되살린다", manifest, broken, workdir,
                    should_pass=False, needle="선수 폐쇄 포함")

    broken = copy.deepcopy(evaluation)
    next(c for c in broken["cases"] if c["owned"] is True)["depth"] = None
    expect_manifest("소장 사례에 depth가 없으면 실패", manifest, broken, workdir,
                    should_pass=False, needle="소장 사례는 depth 지정")

    broken = copy.deepcopy(evaluation)
    next(c for c in broken["cases"] if c["depth"] == "DEEP")["depth"] = "QUICK"
    expect_manifest("소장 경로가 깊이 상한을 넘으면 실패", manifest, broken, workdir,
                    should_pass=False, needle="QUICK 상한")

    broken = copy.deepcopy(manifest)
    chain_prerequisites(candidate_book(broken)["pages"])
    expect_manifest("선수를 한 줄로 길게 이으면 밀도 상한에 걸림", broken, evaluation, workdir,
                    should_pass=False, needle="선수 폐쇄")


def check_merge(workdir):
    """병합이 실패했을 때 정본을 건드리지 않는지 본다.

    정본 사본과 조각을 임시 디렉터리에 만들고, 검증을 통과할 수 없는 상태(평가 파일의
    contentVersion을 어긋나게 둔 정본)에서 병합을 시도한다. 손으로 고친 정본이 이미 깨져 있는
    상황이 실제로 이 경로를 밟는 경우다.
    """
    print("\n[merge_fragments.py]")
    root = Path(workdir) / "merge"
    fixture, fragments = root / "fixture", root / "_fragments"
    fragments.mkdir(parents=True)
    fixture.mkdir(parents=True)
    (fixture / "pdfs").symlink_to(FIXTURE / "pdfs")

    manifest, evaluation = load_manifest(), load_evaluation()
    moved = manifest["books"].pop()
    moved_case = next(c for c in evaluation["cases"] if c["bookId"] == moved["bookId"])
    evaluation["cases"].remove(moved_case)
    evaluation["contentVersion"] = "ai-route-v3"  # 병합 뒤 전체 검증이 반드시 실패하는 조건
    for path, data in ((fixture / "manifest.json", manifest),
                       (fixture / "evaluation.json", evaluation)):
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (fragments / f"book-{moved['bookId']:03d}.json").write_text(
        json.dumps({"manifestBook": moved, "evaluationCase": moved_case}, ensure_ascii=False),
        encoding="utf-8")

    before = {p: p.read_text("utf-8") for p in
              (fixture / "manifest.json", fixture / "evaluation.json")}
    env = dict(os.environ, CORPUS_FIXTURE=str(fixture), CORPUS_FRAGMENTS=str(fragments))
    result = subprocess.run([sys.executable, str(TOOLS / "merge_fragments.py")],
                            capture_output=True, text=True, cwd=workdir, env=env)

    unchanged = all(path.read_text("utf-8") == text for path, text in before.items())
    fragment_kept = any(fragments.iterdir())
    ok = result.returncode != 0 and unchanged and fragment_kept
    report("병합이 실패하면 정본과 조각을 그대로 둔다", ok,
           f"종료코드 {result.returncode} · 정본 무변경 {unchanged} · 조각 보존 {fragment_kept}\n"
           + (result.stdout or result.stderr))


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
        check_merge(workdir)
    check_validate_book()

    print("\n" + (f"실패 {len(failures)}건" if failures else "전체 통과"))
    for name in failures:
        print("  - " + name)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
