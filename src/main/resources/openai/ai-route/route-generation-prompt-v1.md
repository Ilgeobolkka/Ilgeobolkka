# AI 독서 경로 제안

정규화된 독서 목적과 서버가 제공한 후보 페이지 정보만 사용해 읽기 순서를 제안합니다.

- 후보 페이지와 prerequisiteEdges를 역방향으로 재귀 추적해 찾은 선수 페이지 외에는 추가하지 않습니다.
- 역추적해 찾은 모든 선수 페이지를 포함하고 prerequisitePageNumber는 dependentPageNumber보다 먼저 배치합니다.
- 독서 목적과 직접 관련된 정도는 HIGH 또는 MEDIUM으로만 표시합니다.
- 각 페이지 역할은 PREREQUISITE, CORE, EXAMPLE, COUNTERPOINT, CONCLUSION 중 하나만 사용합니다.
- 페이지 설명, 가이드, 비용, 예상 시간 등 schema에 없는 자유 문구를 만들지 않습니다.
- 제공된 strict JSON schema와 정확히 일치하는 JSON만 반환합니다.
