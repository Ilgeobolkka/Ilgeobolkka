# 0005. 로컬과 운영 데이터베이스 연결 설정을 분리한다

- 상태: 승인됨
- 날짜: 2026-07-22
- 결정자: 사용자

## 맥락

로컬 개발과 테스트는 Docker Compose MySQL을 사용하지만 실제 배포는 Amazon RDS for MySQL을 사용합니다. 두 환경을 위해 애플리케이션 빌드를 나누거나 접속 정보를 코드에 넣으면 설정이 어긋나고 시크릿이 노출될 위험이 있습니다. 운영 RDS 연결에는 로컬과 다른 네트워크 주소와 TLS 검증이 필요합니다.

## 고려한 대안

- 환경별 Spring 설정 파일에 JDBC URL과 계정을 직접 기록 — 환경 차이는 명확하지만 설정 중복과 시크릿 유출 위험이 있어 제외합니다.
- 로컬 JDBC URL을 운영에서도 재사용 — 가장 단순하지만 RDS 엔드포인트와 TLS 요구사항을 반영할 수 없어 제외합니다.
- 전체 JDBC URL과 계정을 환경변수로 주입 — 같은 빌드를 유지하면서 환경별 연결과 TLS 정책을 외부에서 결정할 수 있어 선택합니다.

## 결정

- Spring datasource는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로만 구성합니다.
- 로컬은 Git에서 제외된 `.env`와 Docker Compose MySQL 8.4를 사용하고 `DB_URL`에 `sslMode=DISABLED`를 명시합니다.
- 운영은 Compose MySQL을 실행하지 않고 Amazon RDS for MySQL 8.x를 사용합니다.
- 운영 `DB_URL`은 RDS 엔드포인트와 `sslMode=VERIFY_IDENTITY`를 포함하고, 런타임 truststore가 Amazon RDS CA를 신뢰하도록 구성합니다.
- 운영 애플리케이션 계정과 비밀번호는 AWS Secrets Manager에서 배포 환경변수로 주입하고 저장소나 배포 이미지에 포함하지 않습니다.
- RDS는 공개하지 않고 MySQL 3306 인바운드를 애플리케이션 보안 그룹에서만 허용합니다.
- 로컬과 운영의 스키마는 같은 Flyway 마이그레이션으로 관리합니다.

## 결과

- 하나의 애플리케이션 빌드가 로컬 Compose와 운영 RDS에 연결됩니다.
- 운영 접속 정보와 TLS 정책을 코드 변경 없이 교체할 수 있습니다.
- 필수 환경변수가 없으면 애플리케이션이 시작 단계에서 실패해 잘못된 기본 접속을 방지합니다.
- 실제 배포 전에는 실행 환경 선택, RDS CA truststore 구성, Secrets Manager 주입 권한, Flyway 의존성 추가가 필요합니다.
