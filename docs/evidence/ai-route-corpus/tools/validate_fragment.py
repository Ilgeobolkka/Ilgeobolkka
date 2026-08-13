#!/usr/bin/env python3
"""단일 도서 fragment(`_fragments/book-0NN.json`)를 공유 manifest.json/evaluation.json에
병합하기 전에 독립적으로 검증한다. 여러 에이전트가 동시에 다른 카테고리를 작업할 때
공유 파일을 건드리지 않고도 book-041/042와 같은 수준의 검증을 받기 위한 도구다.

사용: python3 validate_fragment.py <_fragments/book-0NN.json경로>

검사 항목은 corpus_lib.validate_book()과 validate_manifest.py의 도서 단위·평가 케이스
단위 검사를 fragment 하나에 맞춰 재구성한 것이다. 공유 manifest.json은 읽지 않는다.
"""
import hashlib
import json
import re
import sys
from pathlib import Path

from corpus_lib import (
    DEPTH_PAGE_LIMITS,
    density_failures,
    duplicate_closure_failures,
    prereq_closure,
)

REPO = Path(__file__).resolve().parents[4]  # docs/evidence/ai-route-corpus/tools/ 기준 저장소 루트

CONTENT_ROLES = {"PREREQUISITE", "CORE", "EXAMPLE", "COUNTERPOINT", "CONCLUSION"}
ALL_ROLES = CONTENT_ROLES | {"FRONT_MATTER"}

fails = []


def chk(cond, msg):
    print(("  OK   " if cond else "  FAIL ") + msg)
    if not cond:
        fails.append(msg)


def main():
    frag_path = Path(sys.argv[1])
    frag = json.loads(frag_path.read_text("utf-8"))
    book = frag["manifestBook"]
    case = frag["evaluationCase"]
    bid = book["bookId"]

    print(f"[book {bid}]")
    chk(bool(book.get("title", "").strip()), "title 존재")
    chk(book["aiRouteCandidate"] is True, "aiRouteCandidate=true")
    chk(book["aiExternalTransferAllowed"] is True, "aiExternalTransferAllowed=true")
    pages = book["pages"]
    n = len(pages)
    nums = [p["pageNumber"] for p in pages]
    chk(48 <= n <= 72, f"페이지 수 {n} (48~72)")
    chk(nums == list(range(1, n + 1)), "pageNumber 1..N 연속")
    chapters = {p["chapter"] for p in pages if p["contentRole"] != "FRONT_MATTER"}
    chk(len(chapters) >= 6, f"최소 6개 장 ({len(chapters)})")
    used = {p["contentRole"] for p in pages}
    chk(CONTENT_ROLES <= used, f"내용 역할 5종 (누락 {CONTENT_ROLES - used})")
    chk(used <= ALL_ROLES, f"허용되지 않은 역할 (실제 {used - ALL_ROLES})")
    fm = {p["pageNumber"] for p in pages if p["contentRole"] == "FRONT_MATTER"}
    noncand = {p["pageNumber"] for p in pages if not p["aiRouteCandidatePage"]}
    chk(fm == noncand, f"FRONT_MATTER=후보제외 (FM={sorted(fm)}, 비후보={sorted(noncand)})")
    chk(all(not p.get("duplicateGroupKeys", []) for p in pages
            if not p["aiRouteCandidatePage"]),
        "비후보 페이지 duplicateGroupKeys 비어 있음")
    dupgroups = {}
    for p in pages:
        chk(bool(p["primaryConcepts"]), f"p{p['pageNumber']} primaryConcepts")
        chk(hashlib.sha256(p["aiAnalysisText"].encode()).hexdigest() == p["aiAnalysisInputSha256"],
            f"p{p['pageNumber']} aiAnalysisInputSha256 일치")
        chk(not re.search(r"\d", p["aiPublicGuideTopic"]), f"p{p['pageNumber']} 주제문 숫자없음")
        chk(p["aiPublicGuideTopic"].strip() not in p["aiAnalysisText"],
            f"p{p['pageNumber']} 주제문이 분석텍스트 그대로 포함되지 않음")
        chk(p["estimatedReadingSeconds"] > 0, f"p{p['pageNumber']} 독서시간 > 0")
        for g in p.get("duplicateGroupKeys", []):
            dupgroups.setdefault(g, set()).add(p["pageNumber"])

    lone = sorted(g for g, ps in dupgroups.items() if len(ps) < 2)
    chk(not lone, f"중복그룹은 2페이지 이상 (1페이지짜리 {lone})")

    prereq = {p["pageNumber"]: p["prerequisitePageNumbers"] for p in pages}
    allnum = set(nums)
    ok_ref = all(all(q in allnum for q in qs) for qs in prereq.values())
    chk(ok_ref, "선수 참조가 모두 범위 안")
    ok_self = all(p not in qs for p, qs in prereq.items())
    chk(ok_self, "자기 참조 없음")
    ok_noncand = not any(set(qs) & noncand for qs in prereq.values())
    chk(ok_noncand, "선수에 FRONT_MATTER/비후보 없음")
    indeg = {p: len(q) for p, q in prereq.items()}
    dep = {p: [] for p in prereq}
    for p, qs in prereq.items():
        for q in qs:
            dep[q].append(p)
    queue = [p for p, d in indeg.items() if d == 0]
    seen = 0
    while queue:
        p = queue.pop()
        seen += 1
        for m in dep[p]:
            indeg[m] -= 1
            if indeg[m] == 0:
                queue.append(m)
    chk(seen == n, f"위상 정렬 {seen}/{n} (순환 없음)")
    density = density_failures(pages, prereq)
    chk(not density, "선수 밀도 상한" + ("" if not density else " — " + "; ".join(density)))
    duplicate_closures = duplicate_closure_failures(pages, prereq)
    chk(not duplicate_closures,
        "후보 선수 폐쇄의 중복 그룹 충돌 없음"
        + ("" if not duplicate_closures else " — " + "; ".join(duplicate_closures)))

    chk(bool(re.fullmatch(r"[0-9a-f]{64}", book["pdfSha256"])), "pdfSha256 형식(64자 hex)")
    chk(book["totalPageCount"] == n, f"totalPageCount({book['totalPageCount']})==pages 길이({n})")
    pdf = REPO / "fixtures/content/ai-route-v2" / book["pdfPath"]
    chk(pdf.is_file(), f"PDF 존재: {book['pdfPath']}")
    if pdf.is_file():
        actual = hashlib.sha256(pdf.read_bytes()).hexdigest()
        chk(actual == book["pdfSha256"], f"PDF SHA-256 일치 (실제 {actual})")

    print(f"\n[evaluation case {case['caseId']}]")
    chk(case["bookId"] == bid, "caseId의 bookId가 manifestBook과 일치")
    candidate_pages = [p for p in pages if p["aiRouteCandidatePage"]]
    candidate_primary = {x for p in candidate_pages for x in p["primaryConcepts"]}
    candidate_concepts = {
        x for p in candidate_pages for x in p["primaryConcepts"] + p["secondaryConcepts"]
    }
    chk(bool(case["requiredConcepts"]), "requiredConcepts 비어있지 않음")
    missing_required = [x for x in case["requiredConcepts"] if x not in candidate_primary]
    chk(not missing_required,
        f"requiredConcepts가 후보 primaryConcepts에 존재 (누락 {missing_required})")
    missing_helpful = [x for x in case["helpfulConcepts"] if x not in candidate_concepts]
    chk(not missing_helpful,
        f"helpfulConcepts가 후보 primary/secondaryConcepts에 존재 (누락 {missing_helpful})")
    reference = set(case["referencePageNumbers"])
    reference_primary = {
        x for p in candidate_pages if p["pageNumber"] in reference for x in p["primaryConcepts"]
    }
    uncovered_required = [x for x in case["requiredConcepts"] if x not in reference_primary]
    chk(not uncovered_required,
        f"referencePageNumbers가 requiredConcepts를 덮음 (누락 {uncovered_required})")
    all_ev_pages = (case["irrelevantPageNumbers"] + case["referencePageNumbers"]
                     + case["allowedAlternativePageNumbers"]
                     + [x for g in case["duplicatePageGroups"] for x in g]
                     + case.get("activeRentalPageNumbers", []))
    chk(all(x in allnum for x in all_ev_pages), "평가 페이지가 도서 범위 안")
    chk(not (set(case["referencePageNumbers"]) & set(case["irrelevantPageNumbers"])), "정답∩무관=∅")
    chk(not (set(case["allowedAlternativePageNumbers"]) & set(case["irrelevantPageNumbers"])),
        "대체∩무관=∅")
    # 정본은 비후보 페이지가 경로 비용·추천·채점 목록 어디에도 못 나오게 한다.
    chk(not (set(case["referencePageNumbers"]) & noncand), "정답경로에 비후보 없음")
    chk(not (set(case["allowedAlternativePageNumbers"]) & noncand), "대체 페이지에 비후보 없음")
    # 비후보 페이지는 추천될 수 없으므로 무관으로 적어도 채점에 걸리지 않는 죽은 값이다.
    chk(not (set(case["irrelevantPageNumbers"]) & noncand), "무관 페이지에 비후보 없음")
    chk(not (set(case.get("activeRentalPageNumbers", [])) & noncand), "activeRental에 비후보 없음")
    # 평가의 중복 그룹은 manifest의 duplicateGroupKeys가 실제로 묶은 페이지 집합과 같아야 한다.
    # 한쪽만 고치면 중복 페이지를 걸러내는 평가가 조용히 다른 정답을 채점하게 된다.
    manifest_groups = {frozenset(ps) for ps in dupgroups.values()}
    case_groups = {frozenset(g) for g in case["duplicatePageGroups"]}
    chk(all(len(g) == len(set(g)) for g in case["duplicatePageGroups"]),
        "duplicatePageGroups 안에 같은 페이지가 두 번 나오지 않음")
    chk(manifest_groups == case_groups,
        "duplicateGroupKeys 그룹 == duplicatePageGroups "
        f"(manifest {sorted(sorted(g) for g in manifest_groups)}, "
        f"평가 {sorted(sorted(g) for g in case_groups)})")
    same_group = [sorted(set(case["referencePageNumbers"]) & g) for g in case_groups
                  if len(set(case["referencePageNumbers"]) & g) > 1]
    chk(not same_group, f"정답 경로에 같은 중복 그룹 페이지가 둘 이상 없음 (위반 {same_group})")
    required_prerequisites = [
        (e["beforePageNumber"], e["afterPageNumber"])
        for e in case["requiredPrerequisites"]
    ]
    seen_prerequisites = set()
    duplicate_prerequisites = []
    for edge in required_prerequisites:
        if edge in seen_prerequisites:
            duplicate_prerequisites.append(edge)
        seen_prerequisites.add(edge)
    chk(not duplicate_prerequisites,
        f"requiredPrerequisites에 중복 간선 없음 (중복 {sorted(duplicate_prerequisites)})")
    bad = [edge for edge in required_prerequisites
           if edge[0] not in prereq.get(edge[1], [])]
    chk(not bad, f"requiredPrerequisites가 실제 DAG와 일치 (위반 {bad})")
    route = prereq_closure(case["referencePageNumbers"], prereq)
    missing_route_pages = sorted(route - reference)
    chk(not missing_route_pages,
        f"referencePageNumbers가 선수 폐쇄 (누락 페이지 {missing_route_pages})")
    expected_prerequisites = {
        (before, after)
        for after in reference
        for before in prereq.get(after, [])
    }
    actual_prerequisites = set(required_prerequisites)
    missing_prerequisites = sorted(expected_prerequisites - actual_prerequisites)
    unexpected_prerequisites = sorted(actual_prerequisites - expected_prerequisites)
    chk(not missing_prerequisites and not unexpected_prerequisites,
        "requiredPrerequisites가 referencePageNumbers 선수 간선 전체와 일치 "
        f"(누락 {missing_prerequisites}, 초과 {unexpected_prerequisites})")
    extra = sorted(route - set(case["referencePageNumbers"]))
    if case["owned"] is False and case["maxAdditionalInk"] == 0:
        chk(bool(case["activeRentalPageNumbers"]), "예산0은 activeRentalPageNumbers 필요")
    if case["owned"] is True:
        chk(case["maxAdditionalInk"] is None, "소장 사례는 maxAdditionalInk=null")
        chk(case["depth"] in DEPTH_PAGE_LIMITS, "소장 사례는 depth 지정")
        limit = DEPTH_PAGE_LIMITS.get(case["depth"])
        if limit is not None:
            chk(len(route) <= limit,
                f"소장 경로 {len(route)}p ≤ {case['depth']} 상한 {limit}p "
                f"(정답 {len(case['referencePageNumbers'])}p + 선수 폐쇄 {extra})")
    else:
        chk(case["maxAdditionalInk"] in (0, 5, 10, 15), "비소장 사례는 maxAdditionalInk∈{0,5,10,15}")
        chk(case["depth"] is None, "비소장 사례는 depth=null")
        rented = set(case.get("activeRentalPageNumbers") or [])
        charged = sorted(route - rented)
        chk(len(charged) <= case["maxAdditionalInk"],
            f"선수 폐쇄 포함 추가 차감 {len(charged)}p ≤ 예산 {case['maxAdditionalInk']} "
            f"(정답 {len(case['referencePageNumbers'])}p + 선수 폐쇄 {extra}, 차감 {charged})")

    print("\n" + (f"실패 {len(fails)}건" if fails else "전체 통과"))
    for f in fails:
        print("  - " + f)
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
