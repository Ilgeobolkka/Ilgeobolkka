package com.example.ilgeobolkka.reading.exception;

public class ReadingSessionNotFoundException extends RuntimeException {

    public ReadingSessionNotFoundException(long readerId) {
        super("현재 열람 세션을 찾을 수 없습니다: " + readerId);
    }
}
