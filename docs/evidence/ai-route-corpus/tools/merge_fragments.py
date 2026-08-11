#!/usr/bin/env python3
"""`_fragments/`의 도서 조각을 공유 manifest.json·evaluation.json에 병합한다.

도서를 완성할 때마다 공유 파일을 직접 고치면 여러 카테고리를 동시에 작업할 때 충돌하므로,
한 권 분량을 조각 파일로 떼어 두었다가 이 도구로 한 번에 합친다.

사용:
    python3 merge_fragments.py                 # 조각 전부 병합
    python3 merge_fragments.py 61 71           # 지정한 bookId만 병합
    python3 merge_fragments.py --dry-run       # 무엇이 병합될지만 확인

병합 결과는 임시 디렉터리에서 먼저 `validate_manifest.py`로 검증하고, 통과한 경우에만 정본에 쓴다.
정본에 먼저 쓰고 검증하면 실패했을 때 반쯤 병합된 파일이 남는다.

병합에 성공하면 역할이 끝난 조각 파일을 지운다. 남겨 두면 다음 실행에서 bookId 중복으로
걸리고, 정본과 조각 중 어느 쪽이 최신인지 알 수 없게 된다. 되돌리려면 Git으로 복원한다.
"""
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]  # docs/evidence/ai-route-corpus/tools/ 기준 저장소 루트
CORPUS = REPO / "docs/evidence/ai-route-corpus"
TOOLS = CORPUS / "tools"
# 정본 경로. selftest가 임시 사본을 대신 넣어 병합 실패 경로를 확인할 때만 환경 변수로 바꾼다.
FRAGMENTS = Path(os.environ.get("CORPUS_FRAGMENTS", CORPUS / "_fragments"))
FIXTURE = Path(os.environ.get("CORPUS_FIXTURE", REPO / "fixtures/content/ai-route-v2"))
MANIFEST = FIXTURE / "manifest.json"
EVALUATION = FIXTURE / "evaluation.json"


def die(msg):
    sys.exit(f"✗ {msg}")


def load_json(path):
    return json.loads(path.read_text("utf-8"))


def dump_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def collect(book_ids):
    """병합할 조각을 bookId 순으로 모은다."""
    paths = sorted(FRAGMENTS.glob("book-*.json"))
    if not paths:
        die(f"병합할 조각이 없다: {FRAGMENTS}")
    picked = []
    for path in paths:
        frag = load_json(path)
        for key in ("manifestBook", "evaluationCase"):
            if key not in frag:
                die(f"{path.name}: {key} 없음")
        bid = frag["manifestBook"]["bookId"]
        if book_ids and bid not in book_ids:
            continue
        picked.append((path, frag))
    missing = set(book_ids) - {f["manifestBook"]["bookId"] for _, f in picked}
    if missing:
        die(f"조각을 찾지 못한 bookId: {sorted(missing)}")
    picked.sort(key=lambda x: x[1]["manifestBook"]["bookId"])
    return picked


def validate_each(picked):
    """병합 전에 조각 하나하나를 validate_fragment.py로 검증한다."""
    for path, _ in picked:
        result = subprocess.run(
            [sys.executable, str(TOOLS / "validate_fragment.py"), str(path)],
            capture_output=True, text=True)
        if result.returncode != 0:
            failed = [l for l in result.stdout.split("\n") if l.startswith("  FAIL")]
            die(f"{path.name} 검증 실패\n    " + "\n    ".join(failed or [result.stderr.strip()]))
        print(f"  {path.name}: 검증 통과")


def main():
    args = [a for a in sys.argv[1:] if a != "--dry-run"]
    dry_run = "--dry-run" in sys.argv[1:]
    try:
        book_ids = {int(a) for a in args}
    except ValueError:
        die(f"bookId는 정수여야 한다: {args}")

    picked = collect(book_ids)
    print(f"[대상] {len(picked)}건 — " + ", ".join(str(f['manifestBook']['bookId']) for _, f in picked))

    manifest = load_json(MANIFEST)
    evaluation = load_json(EVALUATION)

    # 정본과 조각 사이, 그리고 조각끼리의 중복을 병합 전에 모두 막는다
    existing_books = {b["bookId"] for b in manifest["books"]}
    existing_cases = {c["caseId"] for c in evaluation["cases"]}
    seen_books, seen_cases = set(), set()
    for path, frag in picked:
        bid = frag["manifestBook"]["bookId"]
        cid = frag["evaluationCase"]["caseId"]
        if bid in existing_books:
            die(f"{path.name}: bookId {bid}가 이미 manifest에 있다")
        if cid in existing_cases:
            die(f"{path.name}: caseId {cid}가 이미 evaluation에 있다")
        if bid in seen_books:
            die(f"{path.name}: bookId {bid}가 조각끼리 중복")
        if cid in seen_cases:
            die(f"{path.name}: caseId {cid}가 조각끼리 중복")
        seen_books.add(bid)
        seen_cases.add(cid)

    print("[검증]")
    validate_each(picked)

    if dry_run:
        print("\n--dry-run: 파일을 바꾸지 않고 끝낸다")
        return

    for _, frag in picked:
        manifest["books"].append(frag["manifestBook"])
        evaluation["cases"].append(frag["evaluationCase"])
    manifest["books"].sort(key=lambda b: b["bookId"])
    evaluation["cases"].sort(key=lambda c: c["bookId"])

    print(f"\n[병합] manifest {len(manifest['books'])}권 · evaluation {len(evaluation['cases'])}건")

    # 병합 결과를 임시 디렉터리에서 먼저 검증한다. 정본에 바로 쓰고 나서 검증하면, 실패했을 때
    # 반쯤 병합된 파일이 남아 사람이 Git으로 되돌려야 한다. PDF는 용량이 크므로 심링크로 잇는다.
    print("[전체 재검증]")
    with tempfile.TemporaryDirectory() as staging:
        staged = Path(staging)
        (staged / "pdfs").symlink_to(FIXTURE / "pdfs")
        dump_json(staged / "manifest.json", manifest)
        dump_json(staged / "evaluation.json", evaluation)
        result = subprocess.run(
            [sys.executable, str(TOOLS / "validate_manifest.py"), str(staged)],
            capture_output=True, text=True)
    if result.returncode != 0:
        failed = [l for l in result.stdout.split("\n") if l.startswith("  FAIL")]
        die("병합 결과가 validate_manifest를 통과하지 못했다. 정본은 그대로다.\n    "
            + "\n    ".join(failed or [result.stderr.strip()]))
    print("  validate_manifest 전체 통과")

    dump_json(MANIFEST, manifest)
    dump_json(EVALUATION, evaluation)

    for path, _ in picked:
        path.unlink()
    print(f"[정리] 병합한 조각 {len(picked)}개 삭제")


if __name__ == "__main__":
    main()
