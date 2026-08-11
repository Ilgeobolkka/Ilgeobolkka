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

from corpus_lib import DEPTH_PAGE_LIMITS, density_failures, prereq_closure

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
        chk(pages == [], f"소설/미지원 도서 pages[] 빈 배열")
        chk(bool(re.fullmatch(r"[0-9a-f]{64}", book["pdfSha256"])), "pdfSha256 형식")
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
    pdf = FIXTURE / book["pdfPath"]
    chk(pdf.is_file(), f"PDF 존재: {book['pdfPath']}")
    if pdf.is_file():
        chk(hashlib.sha256(pdf.read_bytes()).hexdigest() == book["pdfSha256"], "PDF SHA-256 일치")

print("\n[evaluation]")
caseids = [c["caseId"] for c in evaluation["cases"]]
chk(len(caseids) == len(set(caseids)), "caseId 중복 없음")
book_by_id = {b["bookId"]: b for b in manifest["books"]}
for c in evaluation["cases"]:
    book = book_by_id.get(c["bookId"])
    chk(book is not None, f"{c['caseId']}: bookId {c['bookId']} manifest에 존재")
    if not book:
        continue
    nums = {p["pageNumber"] for p in book["pages"]}
    noncand = {p["pageNumber"] for p in book["pages"] if not p["aiRouteCandidatePage"]}
    prereq = {p["pageNumber"]: p["prerequisitePageNumbers"] for p in book["pages"]}
    concepts = {x for p in book["pages"] for x in p["primaryConcepts"] + p["secondaryConcepts"]}
    chk(bool(c["requiredConcepts"]), f"{c['caseId']}: requiredConcepts 존재")
    missing = [x for x in c["requiredConcepts"] + c["helpfulConcepts"] if x not in concepts]
    chk(not missing, f"{c['caseId']}: 개념 실재 (누락 {missing})")
    all_ev_pages = (c["irrelevantPageNumbers"] + c["referencePageNumbers"]
                     + c["allowedAlternativePageNumbers"] + [x for g in c["duplicatePageGroups"] for x in g]
                     + (c.get("activeRentalPageNumbers") or []))
    chk(all(x in nums for x in all_ev_pages), f"{c['caseId']}: 평가 페이지가 도서 범위 안")
    chk(not (set(c["referencePageNumbers"]) & set(c["irrelevantPageNumbers"])), f"{c['caseId']}: 정답∩무관=∅")
    # 정본은 비후보 페이지가 정답 경로와 대체 페이지 어디에도 못 나오게 한다. 대체 페이지를 빼면
    # 임베딩이 없는 목차가 대체 정답으로 채점돼 도달할 수 없는 경로를 통과시킨다.
    chk(not (set(c["referencePageNumbers"]) & noncand), f"{c['caseId']}: 정답경로에 비후보 없음")
    chk(not (set(c["allowedAlternativePageNumbers"]) & noncand), f"{c['caseId']}: 대체 페이지에 비후보 없음")
    # 비후보 페이지는 추천될 수 없으므로 무관으로 적어도 채점에 걸리지 않는 죽은 값이다.
    chk(not (set(c["irrelevantPageNumbers"]) & noncand), f"{c['caseId']}: 무관 페이지에 비후보 없음")
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
    bad = [(e["beforePageNumber"], e["afterPageNumber"]) for e in c["requiredPrerequisites"]
           if e["beforePageNumber"] not in prereq.get(e["afterPageNumber"], [])]
    chk(not bad, f"{c['caseId']}: requiredPrerequisites가 실제 DAG (위반 {bad})")
    route = prereq_closure(c["referencePageNumbers"], prereq)
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
