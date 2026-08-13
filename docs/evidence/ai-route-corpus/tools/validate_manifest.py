#!/usr/bin/env python3
"""manifest.json 전체(모든 도서)와 evaluation.json을 검사. 90권 확장에서 book-047 전용
check.py 대신 쓰는 일반화된 버전.

사용: python3 validate_manifest.py [fixture디렉터리]   # 기본값은 fixtures/content/ai-route-v2

검사 항목은 validate_fragment.py의 도서·평가 단위 검사와 같은 집합이어야 한다. 한쪽에만 검사를
넣으면 조각으로 들어온 도서와 정본을 손으로 고친 도서의 기준이 달라진다.
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
FIXTURE = Path(sys.argv[1]) if len(sys.argv) > 1 else REPO / "fixtures/content/ai-route-v2"
manifest = json.loads((FIXTURE / "manifest.json").read_text("utf-8"))
evaluation = json.loads((FIXTURE / "evaluation.json").read_text("utf-8"))

fails = []


def chk(cond, msg):
    print(("  OK   " if cond else "  FAIL ") + msg)
    if not cond:
        fails.append(msg)


CONTENT_ROLES = {"PREREQUISITE", "CORE", "EXAMPLE", "COUNTERPOINT", "CONCLUSION"}
ALL_ROLES = CONTENT_ROLES | {"FRONT_MATTER"}
# 소설은 initial-v1의 PDF·SHA-256·페이지 수를 그대로 쓰므로 그 manifest와 대조한다.
INITIAL = json.loads((REPO / "fixtures/content/manifest.json").read_text("utf-8"))
INITIAL_BOOKS = {b["bookId"]: b for b in INITIAL["books"]}


def check_pdf(book):
    pdf = FIXTURE / book["pdfPath"]
    chk(pdf.is_file(), f"PDF 존재: {book['pdfPath']}")
    if pdf.is_file():
        chk(hashlib.sha256(pdf.read_bytes()).hexdigest() == book["pdfSha256"], "PDF SHA-256 일치")


print("[manifest 최상위]")
chk(manifest["contentVersion"] == "ai-route-v2", f"contentVersion (실제 {manifest['contentVersion']})")
chk(manifest["dataPolicyVersion"] == "OPENAI_DEFAULT_RETENTION_V1",
    f"dataPolicyVersion (실제 {manifest['dataPolicyVersion']})")
chk(manifest["embeddingModel"] == "text-embedding-3-small", "embeddingModel")
chk(manifest["embeddingDimensions"] == 1536, "embeddingDimensions")
chk(evaluation["contentVersion"] == manifest["contentVersion"],
    f"evaluation contentVersion이 manifest와 일치 (실제 {evaluation['contentVersion']})")
ids = [b["bookId"] for b in manifest["books"]]
chk(len(ids) == len(set(ids)), f"bookId 중복 없음 (중복: {[i for i in set(ids) if ids.count(i)>1]})")

for book in manifest["books"]:
    bid = book["bookId"]
    print(f"\n[book {bid}]")
    chk(bool(book.get("title", "").strip()), "title 존재")
    pages = book["pages"]
    n = len(pages)
    nums = [p["pageNumber"] for p in pages]
    if not book["aiRouteCandidate"]:
        chk(pages == [], "소설/미지원 도서 pages[] 빈 배열")
        chk(bool(re.fullmatch(r"[0-9a-f]{64}", book["pdfSha256"])), "pdfSha256 형식")
        check_pdf(book)
        prev = INITIAL_BOOKS.get(bid)
        chk(prev is not None, f"initial-v1에 bookId {bid} 존재")
        if prev:
            chk(book["pdfSha256"] == prev["pdfSha256"], "pdfSha256이 initial-v1과 같음")
            chk(book["totalPageCount"] == prev["totalPageCount"],
                f"totalPageCount가 initial-v1과 같음 (실제 {book['totalPageCount']}, "
                f"initial-v1 {prev['totalPageCount']})")
        continue
    chk(book["aiExternalTransferAllowed"] is True, "aiExternalTransferAllowed=true")
    chk(48 <= n <= 72, f"페이지 수 {n} (48~72)")
    chk(nums == list(range(1, n + 1)), "pageNumber 1..N 연속")
    chapters = {p["chapter"] for p in pages if p["contentRole"] != "FRONT_MATTER"}
    chk(len(chapters) >= 6, f"최소 6개 장 ({len(chapters)})")
    used = {p["contentRole"] for p in pages}
    chk(CONTENT_ROLES <= used, f"내용 역할 5종 (누락 {CONTENT_ROLES-used})")
    chk(used <= ALL_ROLES, f"허용되지 않은 역할 (실제 {used-ALL_ROLES})")
    fm = {p["pageNumber"] for p in pages if p["contentRole"] == "FRONT_MATTER"}
    noncand = {p["pageNumber"] for p in pages if not p["aiRouteCandidatePage"]}
    chk(fm == noncand, f"FRONT_MATTER=후보제외 (FM={sorted(fm)}, 비후보={sorted(noncand)})")
    chk(all(not p["duplicateGroupKeys"] for p in pages if not p["aiRouteCandidatePage"]),
        "비후보 페이지 duplicateGroupKeys 비어 있음")
    chk(book["totalPageCount"] == n, f"totalPageCount({book['totalPageCount']})==pages 길이({n})")
    dupgroups = {}
    for p in pages:
        chk(bool(p["primaryConcepts"]), f"p{p['pageNumber']} primaryConcepts")
        chk(hashlib.sha256(p["aiAnalysisText"].encode()).hexdigest() == p["aiAnalysisInputSha256"],
            f"p{p['pageNumber']} sha256")
        chk(not re.search(r"\d", p["aiPublicGuideTopic"]), f"p{p['pageNumber']} 주제문 숫자없음")
        chk(p["aiPublicGuideTopic"].strip() not in p["aiAnalysisText"],
            f"p{p['pageNumber']} 주제문이 분석텍스트 그대로")
        chk(p["estimatedReadingSeconds"] > 0, f"p{p['pageNumber']} 독서시간 > 0")
        for g in p["duplicateGroupKeys"]:
            dupgroups.setdefault(g, set()).add(p["pageNumber"])
    lone = sorted(g for g, ps in dupgroups.items() if len(ps) < 2)
    chk(not lone, f"중복그룹은 2페이지 이상 (1페이지짜리 {lone})")
    prereq = {p["pageNumber"]: p["prerequisitePageNumbers"] for p in pages}
    allnum = set(nums)
    ok_ref = all(all(q in allnum for q in qs) for qs in prereq.values())
    chk(ok_ref, "선수 참조가 모두 범위 안")
    chk(all(p not in qs for p, qs in prereq.items()), "자기 참조 없음")
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
    chk(not density, f"book-{bid:03d} 선수 밀도 상한" + ("" if not density else " — " + "; ".join(density)))
    duplicate_closures = duplicate_closure_failures(pages, prereq)
    chk(not duplicate_closures,
        f"book-{bid:03d} 후보 선수 폐쇄의 중복 그룹 충돌 없음"
        + ("" if not duplicate_closures else " — " + "; ".join(duplicate_closures)))
    check_pdf(book)

print("\n[evaluation]")
caseids = [c["caseId"] for c in evaluation["cases"]]
chk(len(caseids) == len(set(caseids)), "caseId 중복 없음")
book_by_id = {b["bookId"]: b for b in manifest["books"]}
candidate_book_ids = {b["bookId"] for b in manifest["books"] if b["aiRouteCandidate"]}
case_book_ids = [c["bookId"] for c in evaluation["cases"]]
duplicate_case_book_ids = sorted(
    book_id for book_id in set(case_book_ids) if case_book_ids.count(book_id) > 1
)
chk(not duplicate_case_book_ids,
    f"지원 도서별 평가 case 중복 없음 (중복 {duplicate_case_book_ids})")
missing_case_book_ids = sorted(candidate_book_ids - set(case_book_ids))
unsupported_case_book_ids = sorted(set(case_book_ids) - candidate_book_ids)
chk(not missing_case_book_ids and not unsupported_case_book_ids,
    "AI 경로 지원 도서와 평가 case 1:1 "
    f"(누락 {missing_case_book_ids}, 비지원 {unsupported_case_book_ids})")
for c in evaluation["cases"]:
    book = book_by_id.get(c["bookId"])
    chk(book is not None, f"{c['caseId']}: bookId {c['bookId']} manifest에 존재")
    if not book:
        continue
    nums = {p["pageNumber"] for p in book["pages"]}
    noncand = {p["pageNumber"] for p in book["pages"] if not p["aiRouteCandidatePage"]}
    prereq = {p["pageNumber"]: p["prerequisitePageNumbers"] for p in book["pages"]}
    candidate_pages = [p for p in book["pages"] if p["aiRouteCandidatePage"]]
    candidate_primary = {x for p in candidate_pages for x in p["primaryConcepts"]}
    candidate_concepts = {
        x for p in candidate_pages for x in p["primaryConcepts"] + p["secondaryConcepts"]
    }
    chk(bool(c["requiredConcepts"]), f"{c['caseId']}: requiredConcepts 존재")
    missing_required = [x for x in c["requiredConcepts"] if x not in candidate_primary]
    chk(not missing_required,
        f"{c['caseId']}: requiredConcepts가 후보 primaryConcepts에 존재 (누락 {missing_required})")
    missing_helpful = [x for x in c["helpfulConcepts"] if x not in candidate_concepts]
    chk(not missing_helpful,
        f"{c['caseId']}: helpfulConcepts가 후보 primary/secondaryConcepts에 존재 (누락 {missing_helpful})")
    reference = set(c["referencePageNumbers"])
    reference_primary = {
        x for p in candidate_pages if p["pageNumber"] in reference for x in p["primaryConcepts"]
    }
    uncovered_required = [x for x in c["requiredConcepts"] if x not in reference_primary]
    chk(not uncovered_required,
        f"{c['caseId']}: referencePageNumbers가 requiredConcepts를 덮음 (누락 {uncovered_required})")
    all_ev_pages = (c["irrelevantPageNumbers"] + c["referencePageNumbers"]
                     + c["allowedAlternativePageNumbers"] + [x for g in c["duplicatePageGroups"] for x in g]
                     + (c.get("activeRentalPageNumbers") or []))
    chk(all(x in nums for x in all_ev_pages), f"{c['caseId']}: 평가 페이지가 도서 범위 안")
    chk(not (set(c["referencePageNumbers"]) & set(c["irrelevantPageNumbers"])), f"{c['caseId']}: 정답∩무관=∅")
    chk(not (set(c["allowedAlternativePageNumbers"]) & set(c["irrelevantPageNumbers"])),
        f"{c['caseId']}: 대체∩무관=∅")
    # 정본은 비후보 페이지가 경로 비용·추천·채점 목록 어디에도 못 나오게 한다.
    chk(not (set(c["referencePageNumbers"]) & noncand), f"{c['caseId']}: 정답경로에 비후보 없음")
    chk(not (set(c["allowedAlternativePageNumbers"]) & noncand), f"{c['caseId']}: 대체 페이지에 비후보 없음")
    # 비후보 페이지는 추천될 수 없으므로 무관으로 적어도 채점에 걸리지 않는 죽은 값이다.
    chk(not (set(c["irrelevantPageNumbers"]) & noncand), f"{c['caseId']}: 무관 페이지에 비후보 없음")
    chk(not (set(c.get("activeRentalPageNumbers") or []) & noncand),
        f"{c['caseId']}: activeRental에 비후보 없음")
    dupgroups = {}
    for p in book["pages"]:
        for g in p["duplicateGroupKeys"]:
            dupgroups.setdefault(g, set()).add(p["pageNumber"])
    manifest_groups = {frozenset(ps) for ps in dupgroups.values()}
    case_groups = {frozenset(g) for g in c["duplicatePageGroups"]}
    # 집합으로 비교하면 그룹 안의 같은 페이지 중복이 지워지므로 따로 본다.
    chk(all(len(g) == len(set(g)) for g in c["duplicatePageGroups"]),
        f"{c['caseId']}: duplicatePageGroups 안에 같은 페이지가 두 번 나오지 않음")
    chk(manifest_groups == case_groups,
        f"{c['caseId']}: duplicateGroupKeys 그룹 == duplicatePageGroups "
        f"(manifest {sorted(sorted(g) for g in manifest_groups)}, "
        f"평가 {sorted(sorted(g) for g in case_groups)})")
    same_group = [sorted(set(c["referencePageNumbers"]) & g) for g in case_groups
                  if len(set(c["referencePageNumbers"]) & g) > 1]
    chk(not same_group, f"{c['caseId']}: 정답 경로에 같은 중복 그룹 페이지 둘 이상 없음 (위반 {same_group})")
    required_prerequisites = [
        (e["beforePageNumber"], e["afterPageNumber"])
        for e in c["requiredPrerequisites"]
    ]
    seen_prerequisites = set()
    duplicate_prerequisites = []
    for edge in required_prerequisites:
        if edge in seen_prerequisites:
            duplicate_prerequisites.append(edge)
        seen_prerequisites.add(edge)
    chk(not duplicate_prerequisites,
        f"{c['caseId']}: requiredPrerequisites에 중복 간선 없음 "
        f"(중복 {sorted(duplicate_prerequisites)})")
    bad = [edge for edge in required_prerequisites
           if edge[0] not in prereq.get(edge[1], [])]
    chk(not bad, f"{c['caseId']}: requiredPrerequisites가 실제 DAG (위반 {bad})")
    route = prereq_closure(c["referencePageNumbers"], prereq)
    missing_route_pages = sorted(route - reference)
    chk(not missing_route_pages,
        f"{c['caseId']}: referencePageNumbers가 선수 폐쇄 (누락 페이지 {missing_route_pages})")
    expected_prerequisites = {
        (before, after)
        for after in reference
        for before in prereq.get(after, [])
    }
    actual_prerequisites = set(required_prerequisites)
    missing_prerequisites = sorted(expected_prerequisites - actual_prerequisites)
    unexpected_prerequisites = sorted(actual_prerequisites - expected_prerequisites)
    chk(not missing_prerequisites and not unexpected_prerequisites,
        f"{c['caseId']}: requiredPrerequisites가 referencePageNumbers 선수 간선 전체와 일치 "
        f"(누락 {missing_prerequisites}, 초과 {unexpected_prerequisites})")
    extra = sorted(route - set(c["referencePageNumbers"]))
    if c["owned"] is True:
        chk(c["maxAdditionalInk"] is None, f"{c['caseId']}: 소장 사례는 maxAdditionalInk=null")
        chk(c["depth"] in DEPTH_PAGE_LIMITS, f"{c['caseId']}: 소장 사례는 depth 지정")
        limit = DEPTH_PAGE_LIMITS.get(c["depth"])
        if limit is not None:
            chk(len(route) <= limit,
                f"{c['caseId']}: 소장 경로 {len(route)}p ≤ {c['depth']} 상한 {limit}p "
                f"(정답 {len(c['referencePageNumbers'])}p + 선수 폐쇄 {extra})")
    else:
        chk(c["maxAdditionalInk"] in (0, 5, 10, 15), f"{c['caseId']}: 비소장 사례는 maxAdditionalInk∈{{0,5,10,15}}")
        chk(c["depth"] is None, f"{c['caseId']}: 비소장 사례는 depth=null")
        rented = set(c.get("activeRentalPageNumbers") or [])
        charged = sorted(route - rented)
        chk(len(charged) <= c["maxAdditionalInk"],
            f"{c['caseId']}: 선수 폐쇄 포함 추가 차감 {len(charged)}p ≤ 예산 {c['maxAdditionalInk']} "
            f"(정답 {len(c['referencePageNumbers'])}p + 선수 폐쇄 {extra}, 차감 {charged})")
    if c["owned"] is False and c["maxAdditionalInk"] == 0:
        chk(bool(c["activeRentalPageNumbers"]), f"{c['caseId']}: 예산0은 activeRentalPageNumbers 필요")

print("\n" + (f"실패 {len(fails)}건" if fails else "전체 통과"))
for f in fails:
    print("  - " + f)
sys.exit(1 if fails else 0)
