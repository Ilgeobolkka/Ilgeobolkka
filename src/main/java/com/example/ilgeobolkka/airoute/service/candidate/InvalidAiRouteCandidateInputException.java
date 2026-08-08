package com.example.ilgeobolkka.airoute.service.candidate;

/** 후보 선택에 넣을 수 없는 입력이다. 페이지 메타데이터나 도서·콘텐츠 버전이 계약과 다르다. */
public class InvalidAiRouteCandidateInputException extends RuntimeException {

    public InvalidAiRouteCandidateInputException(String reason) {
        super("AI 경로 후보 입력이 올바르지 않습니다. " + reason);
    }
}
