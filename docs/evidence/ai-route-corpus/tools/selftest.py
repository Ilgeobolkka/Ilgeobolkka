#!/usr/bin/env python3
"""`validate_fragment.py`가 깨진 입력을 실제로 잡아내는지 확인하는 자체 테스트.

검증 도구가 항상 통과만 하면 검증하지 않는 것과 같으므로, 정본 fixture에서 조각을 만들어
한 곳씩 고의로 깨뜨리고 각각이 FAIL로 걸리는지 본다. 모든 실행은 저장소 밖 임시 디렉터리를
작업 디렉터리로 삼아, 도구가 실행 위치와 무관하게 저장소 루트를 찾는지도 함께 확인한다.

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
SAMPLE_BOOK_ID = 47

failures = []


def load_fragment(book_id):
    """정본 manifest·evaluation에서 도서 하나를 조각 형태로 되돌린다."""
    manifest = json.loads((FIXTURE / "manifest.json").read_text("utf-8"))
    evaluation = json.loads((FIXTURE / "evaluation.json").read_text("utf-8"))
    book = next(b for b in manifest["books"] if b["bookId"] == book_id)
    case = next(c for c in evaluation["cases"] if c["bookId"] == book_id)
    return {"manifestBook": book, "evaluationCase": case}


def run_validate(fragment, workdir):
    path = Path(workdir) / "fragment.json"
    path.write_text(json.dumps(fragment, ensure_ascii=False), encoding="utf-8")
    return subprocess.run(
        [sys.executable, str(TOOLS / "validate_fragment.py"), str(path)],
        capture_output=True, text=True, cwd=workdir)


def expect(name, fragment, workdir, should_pass, needle=None):
    result = run_validate(fragment, workdir)
    passed = result.returncode == 0
    ok = passed == should_pass
    if ok and needle is not None:
        fails = [l for l in result.stdout.split("\n") if l.startswith("  FAIL")]
        ok = any(needle in l for l in fails)
    print(("  OK   " if ok else "  FAIL ") + name)
    if not ok:
        failures.append(name)
        print("    " + (result.stdout or result.stderr).strip().replace("\n", "\n    "))


def main():
    base = load_fragment(SAMPLE_BOOK_ID)
    with tempfile.TemporaryDirectory() as workdir:
        expect(f"정상 조각(book-{SAMPLE_BOOK_ID})은 통과", base, workdir, should_pass=True)

        broken = copy.deepcopy(base)
        groups = broken["evaluationCase"]["duplicatePageGroups"]
        groups[0] = [groups[0][0], max(p["pageNumber"] for p in broken["manifestBook"]["pages"])]
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

    print("\n" + (f"실패 {len(failures)}건" if failures else "전체 통과"))
    for name in failures:
        print("  - " + name)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
