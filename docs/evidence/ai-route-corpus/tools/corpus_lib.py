#!/usr/bin/env python3
"""90권 확장에 재사용하는 코퍼스 빌더.

book-047의 접근(선수 관계를 원본 페이지 번호로 하드코딩)은 페이지를 끼워 넣을 때마다
전부 다시 계산해야 해서 89권 규모에 맞지 않는다. 이 모듈은 절 식별자(예: "3.4")로
선수 관계를 걸고, 최종 페이지 번호는 리스트 순서에서 자동 계산한다. 페이지를 추가·삭제해도
절 식별자 기반 선수 관계는 그대로 유지된다.
"""
import hashlib
import re

EMBEDDING_MODEL = "text-embedding-3-small"
EMBEDDING_DIMENSIONS = 1536
DATA_POLICY_VERSION = "OPENAI_DEFAULT_RETENTION_V1"
CONTENT_VERSION = "ai-route-v2"
READING_CHARS_PER_MINUTE = 330
IMAGE_PAGE_SECONDS = 45
TOC_SECONDS = 40

# 소장 경로의 깊이별 페이지 수 상한 (PRD "독서 목적과 예산 입력")
DEPTH_PAGE_LIMITS = {"QUICK": 5, "BALANCED": 10, "DEEP": 15}
# 선수 밀도 상한 (코퍼스 정본 "도서 제작 기준"). 시나리오의 최소·최대 상한 5·15에서 온 값이다.
SHALLOW_CLOSURE, SHALLOW_MIN_CHAPTERS = 5, 3
REACHABLE_CLOSURE, REACHABLE_MIN_RATIO = 15, 0.75


def prereq_closure(pages, prereq):
    """경로에 든 페이지의 전이적 선수 페이지까지 펼친다.

    PRD 경로 생성 정책은 최종 경로에 선수 페이지가 모두 포함되고 그 누적 비용이 예산 안이어야
    한다고 정한다. 따라서 경로의 실제 차감량과 분량은 고른 페이지가 아니라 이 폐쇄로 세야 한다.
    """
    closed = set(pages)
    stack = list(closed)
    while stack:
        for q in prereq.get(stack.pop(), []):
            if q not in closed:
                closed.add(q)
                stack.append(q)
    return closed


def density_failures(pages, prereq):
    """선수 밀도 상한 위반 목록. 위반이 없으면 빈 리스트.

    `pages`는 `chapter`·`aiRouteCandidatePage`·`pageNumber`를 가진 manifest 페이지들이다.
    선수를 촘촘히 걸면 어떤 예산으로도 열 수 없는 페이지가 생기므로, 원고를 쓰기 전 구조 단계에서
    막는다.
    """
    cand = [p for p in pages if p["aiRouteCandidatePage"]]
    if not cand:
        return ["후보 페이지가 하나도 없음"]
    size = {p["pageNumber"]: len(prereq_closure([p["pageNumber"]], prereq)) for p in cand}
    fails = []
    chapters = {p["chapter"] for p in cand if size[p["pageNumber"]] <= SHALLOW_CLOSURE}
    if len(chapters) < SHALLOW_MIN_CHAPTERS:
        fails.append(f"선수 폐쇄 {SHALLOW_CLOSURE}p 이하 페이지가 덮는 장 {len(chapters)}개 "
                     f"< {SHALLOW_MIN_CHAPTERS}개 (가장 작은 예산·빠른 깊이로 만들 재료가 없음)")
    reachable = sum(1 for p in cand if size[p["pageNumber"]] <= REACHABLE_CLOSURE)
    ratio = reachable / len(cand)
    if ratio < REACHABLE_MIN_RATIO:
        fails.append(f"선수 폐쇄 {REACHABLE_CLOSURE}p 이하 비율 {ratio:.0%} < {REACHABLE_MIN_RATIO:.0%} "
                     f"(가장 큰 예산으로도 닿지 못하는 페이지가 너무 많음)")
    return fails


def duplicate_closure_failures(pages, prereq):
    """후보와 전이적 선수 폐쇄 안의 중복 그룹 충돌 목록을 반환한다.

    최종 경로는 선수 폐쇄를 모두 포함하면서 중복 그룹에서는 최대 한 페이지만 포함해야 한다.
    한 후보의 폐쇄 자체가 이 두 조건을 동시에 만족하지 못하면 런타임 조립 순서나 예산과 무관하게
    도달할 수 없으므로 콘텐츠 제작 단계에서 막는다.
    """
    pages_by_number = {p["pageNumber"]: p for p in pages}
    failures = []
    for page in pages:
        if not page["aiRouteCandidatePage"]:
            continue
        pages_by_group = {}
        for page_number in prereq_closure([page["pageNumber"]], prereq):
            for group_key in pages_by_number[page_number]["duplicateGroupKeys"]:
                pages_by_group.setdefault(group_key, []).append(page_number)
        conflicts = {
            group_key: sorted(set(page_numbers))
            for group_key, page_numbers in pages_by_group.items()
            if len(set(page_numbers)) > 1
        }
        if conflicts:
            failures.append(
                f"p{page['pageNumber']} 후보와 선수 폐쇄에 같은 중복 그룹 페이지가 둘 이상임: "
                f"{conflicts}"
            )
    return failures


def sha256_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def reading_seconds(fmt, body):
    if fmt == "IMAGE":
        return IMAGE_PAGE_SECONDS
    seconds = len(body) / READING_CHARS_PER_MINUTE * 60
    return max(60, round(seconds / 5) * 5)


def assemble(pages, section_edges, *, toc=True):
    """pages에 순서대로 n(1..N 또는 toc 있으면 2..N+1)을 배정하고,
    section_edges({절: [선수 절...]})로 절 첫 페이지의 선수 관계를 만든다.
    같은 절의 두 번째 이후 페이지는 그 절의 바로 앞 페이지를 선수로 갖는다.
    반환: (numbered_pages, prereq_map, toc_entries)
    """
    offset = 1 if toc else 0
    numbered = []
    for i, p in enumerate(pages, start=1 + offset):
        q = dict(p)
        q["n"] = i
        numbered.append(q)

    first_of_sec = {}   # section -> first page number
    prev_of_sec = {}    # section -> previous page number seen
    prereq = {}
    order_seen_sections = []
    for p in numbered:
        sec = p["sec"]
        if sec not in first_of_sec:
            first_of_sec[sec] = p["n"]
            order_seen_sections.append(sec)
            deps = section_edges.get(sec)
            if deps is None:
                raise SystemExit(f"절 '{sec}'의 선수 관계가 section_edges에 없음 (빈 리스트라도 명시 필요)")
            ahead = [d for d in deps if d not in first_of_sec]
            if ahead:
                raise SystemExit(f"절 '{sec}'의 선수 절 {ahead}이 아직 나오지 않음 "
                                 "(선수 절은 페이지 순서상 앞에 있어야 함)")
            prereq[p["n"]] = [first_of_sec[d] for d in deps]
        else:
            prereq[p["n"]] = [prev_of_sec[sec]]
        prev_of_sec[sec] = p["n"]

    unknown = set(section_edges) - set(order_seen_sections)
    if unknown:
        raise SystemExit(f"section_edges에 있지만 실제 페이지에 없는 절: {sorted(unknown)}")

    toc_entries = []
    if toc:
        seen_ch, seen_sec = set(), set()
        for p in numbered:
            if p["ch"] not in seen_ch:
                seen_ch.add(p["ch"])
                toc_entries.append((p["ch"], p["n"], False))
            if p["sec"] not in seen_sec:
                seen_sec.add(p["sec"])
                toc_entries.append((p["sec"], p["n"], True))
    return numbered, prereq, toc_entries


def display_width(text):
    import unicodedata
    return sum(2 if unicodedata.east_asian_width(c) in "WF" else 1 for c in text)


def toc_body(entries, width=60):
    lines = ["목차", ""]
    for label, page, is_section in entries:
        prefix = "  " if is_section else ""
        dots = "." * max(3, width - display_width(prefix + label))
        if not is_section and len(lines) > 2:
            lines.append("")
        lines.append(f"{prefix}{label} {dots} {page}")
    return "\n".join(lines)


def build_pages(numbered, prereq, *, toc_topic=None, toc_analysis=None,
                 toc_body_text=None, toc_chapter="앞부분", toc_section="목차"):
    """manifest pages[] + manuscript pages[] 동시 생성. toc_body_text가 있으면 1페이지를 목차로 끼운다."""
    manifest_pages, manuscript_pages = [], []
    if toc_body_text is not None:
        manifest_pages.append({
            "pageNumber": 1, "chapter": toc_chapter, "section": toc_section,
            "primaryConcepts": ["목차"], "secondaryConcepts": [], "contentRole": "FRONT_MATTER",
            "aiRouteCandidatePage": False,
            "aiAnalysisText": toc_analysis, "aiAnalysisInputSha256": sha256_text(toc_analysis),
            "aiPublicGuideTopic": toc_topic, "estimatedReadingSeconds": TOC_SECONDS,
            "prerequisitePageNumbers": [], "duplicateGroupKeys": [],
        })
        manuscript_pages.append({
            "pageNumber": 1, "chapter": toc_chapter, "section": toc_section,
            "contentFormat": "TEXT", "body": toc_body_text,
        })
    for p in numbered:
        body = p["body"].strip()
        manifest_pages.append({
            "pageNumber": p["n"], "chapter": p["ch"], "section": p["sec"],
            "primaryConcepts": p["pri"], "secondaryConcepts": p["sec_c"],
            "contentRole": p["role"], "aiRouteCandidatePage": True,
            "aiAnalysisText": p["analysis"], "aiAnalysisInputSha256": sha256_text(p["analysis"]),
            "aiPublicGuideTopic": p["topic"], "estimatedReadingSeconds": reading_seconds(p["fmt"], body),
            "prerequisitePageNumbers": prereq[p["n"]], "duplicateGroupKeys": p.get("dup", []),
        })
        manuscript_pages.append({
            "pageNumber": p["n"], "chapter": p["ch"], "section": p["sec"],
            "contentFormat": p["fmt"], "body": body,
        })
    return manifest_pages, manuscript_pages


def validate_book(manifest_pages, manuscript_pages, *, min_pages=48, max_pages=72, min_chapters=6):
    """생성 즉시 도서 하나를 검증. 실패 시 상세 이유와 함께 SystemExit."""
    fails = []

    def chk(cond, msg):
        if not cond:
            fails.append(msg)

    n = len(manifest_pages)
    chk(min_pages <= n <= max_pages, f"페이지 수 {n}이 {min_pages}~{max_pages} 밖")
    nums = [p["pageNumber"] for p in manifest_pages]
    chk(nums == list(range(1, n + 1)), f"pageNumber가 1..{n} 연속이 아님: {nums[:5]}...")
    content_roles = {"PREREQUISITE", "CORE", "EXAMPLE", "COUNTERPOINT", "CONCLUSION"}
    chapters = {p["chapter"] for p in manifest_pages if p["contentRole"] != "FRONT_MATTER"}
    chk(len(chapters) >= min_chapters, f"장 수 {len(chapters)}이 최소 {min_chapters} 미달")
    used_roles = {p["contentRole"] for p in manifest_pages}
    chk(content_roles <= used_roles, f"내용 역할 5종 중 누락: {content_roles - used_roles}")
    fm = [p["pageNumber"] for p in manifest_pages if p["contentRole"] == "FRONT_MATTER"]
    cand = {p["pageNumber"] for p in manifest_pages if p["aiRouteCandidatePage"]}
    noncand = {p["pageNumber"] for p in manifest_pages if not p["aiRouteCandidatePage"]}
    chk(set(fm) == noncand, f"FRONT_MATTER({fm})와 후보제외({sorted(noncand)}) 불일치")
    for p in manifest_pages:
        chk(bool(p["primaryConcepts"]), f"p{p['pageNumber']} primaryConcepts 비어있음")
        chk(hashlib.sha256(p["aiAnalysisText"].encode("utf-8")).hexdigest() == p["aiAnalysisInputSha256"],
            f"p{p['pageNumber']} aiAnalysisInputSha256 불일치")
        chk(not re.search(r"\d", p["aiPublicGuideTopic"]), f"p{p['pageNumber']} 주제문에 숫자")
        chk(p["aiPublicGuideTopic"].strip() not in p["aiAnalysisText"], f"p{p['pageNumber']} 주제문이 분석텍스트 그대로")
        chk(p["estimatedReadingSeconds"] > 0, f"p{p['pageNumber']} 독서시간 <= 0")

    prereq = {p["pageNumber"]: p["prerequisitePageNumbers"] for p in manifest_pages}
    all_nums = set(nums)
    for p, qs in prereq.items():
        chk(all(q in all_nums for q in qs), f"p{p} 선수 참조가 범위 밖: {qs}")
        chk(p not in qs, f"p{p} 자기 참조")
        chk(not set(qs) & noncand, f"p{p} 선수에 FRONT_MATTER/비후보 포함: {set(qs)&noncand}")
    indeg = {p: len(qs) for p, qs in prereq.items()}
    dep = {p: [] for p in prereq}
    for p, qs in prereq.items():
        for q in qs:
            dep[q].append(p)
    queue = [p for p, d in indeg.items() if d == 0]
    visited = 0
    while queue:
        p = queue.pop()
        visited += 1
        for m in dep[p]:
            indeg[m] -= 1
            if indeg[m] == 0:
                queue.append(m)
    chk(visited == n, f"위상 정렬 {visited}/{n} — 순환 의심")
    for msg in density_failures(manifest_pages, prereq):
        chk(False, msg)
    for msg in duplicate_closure_failures(manifest_pages, prereq):
        chk(False, msg)

    mp = {p["pageNumber"]: p for p in manuscript_pages}
    chk(set(mp) == all_nums, "원고·manifest 페이지 번호 불일치")
    for p in manifest_pages:
        m = mp[p["pageNumber"]]
        chk(m["chapter"] == p["chapter"] and m["section"] == p["section"], f"p{p['pageNumber']} 장·절 불일치")
        chk(bool(m["body"].strip()), f"p{p['pageNumber']} 본문 비어있음")
        chk(m["contentFormat"] in ("TEXT", "IMAGE"), f"p{p['pageNumber']} 형식 오류")

    if fails:
        raise SystemExit("검증 실패:\n  - " + "\n  - ".join(fails))
    return True
