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


def sha256_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def reading_seconds(fmt, body):
    if fmt == "IMAGE":
        return IMAGE_PAGE_SECONDS
    seconds = len(body) / READING_CHARS_PER_MINUTE * 60
    return max(60, round(seconds / 5) * 5)


def _section_first_pages(pages):
    """pages: n이 배정된 뒤의 리스트. 절별 첫 페이지 번호를 반환."""
    first_of = {}
    cur_sec, cur_first = None, None
    for p in pages:
        if p["sec"] != cur_sec:
            cur_sec, cur_first = p["sec"], p["n"]
        first_of[p["n"]] = cur_first
    return first_of


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


def build_manifest_book(*, book_id, title, pdf_path, pdf_sha256, total_page_count,
                          ai_route_candidate, ai_external_transfer_allowed, pages):
    return {
        "bookId": book_id,
        "title": title,
        "pdfPath": pdf_path,
        "pdfSha256": pdf_sha256,
        "totalPageCount": total_page_count,
        "aiRouteCandidate": ai_route_candidate,
        "aiExternalTransferAllowed": ai_external_transfer_allowed,
        "pages": pages,
    }


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
