# 읽어볼까

읽어볼까는 필요한 페이지부터 읽고 원하는 도서는 온라인으로 소장할 수 있는 데스크톱 웹 MVP입니다.
가격·기간·접근 권한을 포함한 상세 범위는 [PRD 색인](./docs/prd/README.md)을 참고합니다.
결제 연동 범위는 PortOne V2 테스트 채널까지이며 운영 실결제는 활성화하지 않습니다.

## 현재 상태

현재 구현 상태와 문서별 정본 경로는 [PRD 색인](./docs/prd/README.md)에서 관리합니다.

## 로컬 실행

필요한 환경변수, MySQL 실행, 개발 서버와 검증 명령은 [배포 가이드](./docs/deployment.md)를 따릅니다.

### AI 에이전트 GitHub CLI 설정

저장소를 처음 받은 팀원은 저장소 루트에서 사용하는 AI 에이전트에게 다음과 같이 요청합니다.

```text
$github-cli-setup 이 저장소의 GitHub CLI PR 작업 환경을 점검하고 활성화해줘.
```

직접 점검하려면 다음 명령을 실행합니다.

```bash
bash .agents/skills/github-cli-setup/scripts/check.sh
```

## 문서

[PRD 색인](./docs/prd/README.md)에서 서비스 기획, 제품 정책, MVP 목표, 기능 요구사항, API, ERD,
테스트 전략, 구현 컨벤션, 배포 가이드와 관련 ADR로 이동할 수 있습니다.
