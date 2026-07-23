# 환경별 데이터베이스 연결과 AWS RDS 배포

## 1. 목적

하나의 Spring Boot 애플리케이션을 로컬에서는 Docker Compose MySQL에, 운영에서는 Amazon RDS for MySQL에 연결하는 설정 계약을 정의합니다. 운영 배포에 `.env`를 복사하거나 MySQL 컨테이너를 함께 실행하지 않습니다.

## 2. 환경별 구성

| 항목 | 로컬 개발·테스트 | AWS 운영 |
| --- | --- | --- |
| 데이터베이스 | Docker Compose MySQL 8.4 | Amazon RDS for MySQL 8.x |
| 호스트 포트 | `localhost:3307` | RDS 엔드포인트의 `3306` |
| 설정 공급자 | Git에서 제외된 `.env` | AWS 배포 환경과 Secrets Manager |
| JDBC TLS | `sslMode=DISABLED` | `sslMode=VERIFY_IDENTITY` |
| DB 공개 접근 | 로컬 호스트에만 노출 | 비공개 RDS, 애플리케이션 보안 그룹만 허용 |

Spring datasource가 요구하는 공통 환경변수는 다음 세 개입니다.

| 변수 | 설명 | 민감정보 |
| --- | --- | --- |
| `DB_URL` | 호스트, 포트, DB 이름, TLS 정책을 포함한 JDBC URL | 아니요 |
| `DB_USERNAME` | 애플리케이션 전용 DB 계정 | 예 |
| `DB_PASSWORD` | 애플리케이션 전용 DB 비밀번호 | 예 |

`DB_PORT`, `DB_NAME`, `DB_ROOT_PASSWORD`는 로컬 Compose 컨테이너를 만드는 데만 사용합니다. 운영 애플리케이션에는 RDS master 계정이나 `DB_ROOT_PASSWORD`를 주입하지 않습니다.

## 3. 로컬 실행

```bash
cp .env.example .env
docker compose up -d --wait
./gradlew test
./gradlew check
```

로컬 `DB_URL` 예시는 다음과 같습니다.

```properties
DB_URL=jdbc:mysql://localhost:3307/ilgeobolkka?allowPublicKeyRetrieval=true&sslMode=DISABLED
```

MySQL을 종료하되 데이터를 유지하려면 `docker compose down`을 실행합니다. 볼륨까지 삭제하는 `docker compose down -v`는 로컬 데이터를 지우므로 명시적으로 초기화할 때만 사용합니다.

## 4. AWS 운영 연결

운영 배포 환경에는 다음 형식으로 값을 주입합니다. 실제 엔드포인트와 계정·비밀번호를 파일이나 저장소에 기록하지 않습니다.

```text
DB_URL=jdbc:mysql://<RDS-ENDPOINT>:3306/ilgeobolkka?sslMode=VERIFY_IDENTITY
DB_USERNAME=<Secrets Manager에서 주입>
DB_PASSWORD=<Secrets Manager에서 주입>
```

운영 구성은 다음 조건을 지켜야 합니다.

1. RDS는 public access를 끄고 애플리케이션과 통신 가능한 VPC의 private subnet에 배치합니다.
2. RDS 보안 그룹은 TCP 3306을 인터넷 전체가 아니라 애플리케이션 보안 그룹에서만 허용합니다.
3. 애플리케이션은 RDS master가 아닌 최소 권한 전용 DB 계정을 사용합니다.
4. 계정과 비밀번호는 Secrets Manager에 저장하고 애플리케이션 실행 역할에 해당 secret 조회 권한만 부여합니다.
5. Amazon RDS CA 인증서를 Java runtime truststore에 등록한 뒤 `sslMode=VERIFY_IDENTITY`로 서버 인증서와 호스트 이름을 검증합니다.
6. 로컬과 운영에 같은 Flyway 마이그레이션을 적용하고 운영에서 Hibernate 자동 스키마 변경을 사용하지 않습니다.

관련 공식 문서:

- [Amazon RDS MySQL SSL/TLS 연결](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/mysql-ssl-connections.html)
- [Amazon RDS 인증서와 truststore](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html)
- [Amazon RDS 보안 그룹](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.RDSSecurityGroups.html)
- [RDS와 AWS Secrets Manager 자격 증명 관리](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html)
- [MySQL Connector/J SSL 모드](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html)

## 5. 배포 전 게이트

- [ ] 애플리케이션 실행 환경(ECS/Fargate, EC2, Elastic Beanstalk 등)을 결정합니다.
- [ ] RDS를 생성하고 애플리케이션 전용 DB 계정을 준비합니다.
- [ ] Secrets Manager secret과 애플리케이션 실행 역할의 조회 권한을 연결합니다.
- [ ] RDS CA truststore를 배포 이미지 또는 런타임에 설치합니다.
- [x] Flyway 의존성과 최초 스키마 마이그레이션을 추가합니다. (SCRUM-26 / ADR-0010, `V1__create_core_domain_tables.sql` — 핵심 도메인 8개 테이블)
- [ ] 운영과 동일한 MySQL 버전에서 전체 테스트를 통과시킵니다.
- [ ] 배포 후 TLS 연결과 DB health를 확인합니다.

현재 애플리케이션 실행 환경은 아직 결정되지 않았습니다. 실행 환경을 선택한 뒤 해당 서비스의 환경변수·Secrets Manager 연결 절차를 이 문서에 구체화합니다.
