package com.example.ilgeobolkka.airoute.service.save;

/**
 * 저장 결과. {@code created} 는 이번 요청이 경로를 만들었는지이고, Controller 는 이 값으로만 201 과 200 을
 * 가른다.
 *
 * <p>재시도가 경로를 만들지 않았다는 사실을 상태 코드로 돌려주려면 어딘가에서 한 번은 구분해야 한다.
 * 응답 DTO 에 담으면 저장 성공과 재시도가 같은 바디를 준다는 계약이 깨지므로 서비스 반환 타입에 둔다.
 */
public record SavedAiRoute(long routeId, boolean created) {

    static SavedAiRoute created(long routeId) {
        return new SavedAiRoute(routeId, true);
    }

    static SavedAiRoute existing(long routeId) {
        return new SavedAiRoute(routeId, false);
    }
}
