# 0008. Thymeleaf와 Bootstrap, Vanilla JavaScript로 프런트엔드를 구성한다

- 상태: 승인됨
- 날짜: 2026-07-23

## 맥락

MVP는 백엔드 도메인과 트랜잭션 검증이 중심인 데스크톱 웹이며 프런트엔드와 API를 독립 배포할 요구가
없습니다. 서버 세션과 CSRF 보호를 사용하므로 화면과 API를 같은 출처에서 제공하면 인증·배포 경계를
단순하게 유지할 수 있습니다.

## 고려한 대안

- React 또는 Vue와 Vite — 복잡한 클라이언트 상태에는 유리하지만 Node.js 빌드·라우팅·상태 관리 비용이 커 제외합니다.
- 정적 HTML과 Vanilla JavaScript만 사용 — 의존성은 적지만 공통 레이아웃과 서버 검증 화면을 반복 관리해야 해 제외합니다.
- Thymeleaf와 HTMX — 점진적 개선에는 유리하지만 JSON API 외에 HTML 조각 응답 계약이 추가되어 제외합니다.
- 프런트엔드와 API 별도 배포 — 독립 확장에는 유리하지만 CORS·쿠키 도메인·배포 파이프라인이 추가되어 제외합니다.

## 결정

- 화면은 Spring MVC와 Thymeleaf로 서버 렌더링합니다.
- 데스크톱 레이아웃과 기본 컴포넌트는 Bootstrap 5.3 계열과 최소한의 커스텀 CSS를 사용합니다.
- 동적 브라우저 동작은 표준 JavaScript ES Modules와 `fetch`로 구현합니다.
- React, Vue, HTMX, 별도 Node.js·Vite 빌드 단계를 도입하지 않습니다.
- 템플릿과 정적 자산은 API와 같은 Spring Boot 실행물에 포함해 same-origin으로 제공합니다.
- HTML Controller와 REST Controller는 같은 Facade를 호출하고 서로를 HTTP로 호출하지 않습니다.
- 현재 화면 흐름은 [사용자 흐름](../../prd/user-flows.md), HTTP 계약은 [API 계약](../../api-spec.md)을 따릅니다.

## 결과

- 단일 빌드·배포와 same-origin 세션·CSRF 구성을 유지할 수 있습니다.
- 모바일 브라우저 레이아웃과 모바일 결제 흐름은 제공하지 않습니다.
- 공통 레이아웃은 서버에서 재사용하고 동적 상태가 필요한 화면에만 JavaScript를 사용합니다.
- HTML과 JSON이라는 두 표현 계층 어댑터를 관리해야 합니다.
- 독립 프런트엔드 팀이나 복잡한 클라이언트 상태가 필요해지면 SPA 전환을 다시 결정해야 합니다.
