# 읽어볼까

읽어볼까는 책 전체를 먼저 구매하지 않고 필요한 원본 PDF 페이지를 잉크로 일정 기간 대여해 읽는 반응형 웹 서비스입니다.
현재는 제품 정책을 갱신한 Spring Boot 골격 단계이며, 구현 범위와 성공 조건은 [PRD 색인](./docs/prd/README.md)에서 확인합니다.

## 문서

- [PRD 색인](./docs/prd/README.md)
- [도메인 용어](./CONTEXT.md)
- [API 명세](./docs/api/api-contract.md)
- [데이터 모델](./docs/data/erd.md) — 목표 모델
- [백엔드 구현 컨벤션](./docs/conventions.md)
- [테스트 전략](./docs/test-strategy.md)
- [환경별 데이터베이스 연결과 AWS RDS 배포](./docs/deployment.md)
- [ADR 주제별 목록](./docs/adr/README.md)

## 로컬 실행

로컬 실행에는 JDK 21이 필요합니다. 민감한 접속값은 Git에서 제외되는 `.env`에 저장합니다.

```bash
cp .env.example .env
docker compose up -d --wait
./gradlew bootRun
```

검증 명령은 다음과 같습니다.

```bash
./gradlew test
./gradlew check
./gradlew build
```

운영에서는 Compose MySQL을 실행하지 않습니다.
배포 환경이 RDS 연결 환경 변수를 주입하며, 세부 계약은 [배포 문서](./docs/deployment.md)를 따릅니다.
