package com.example.ilgeobolkka.reader.exception;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("이미 가입된 이메일입니다.");
    }

    public EmailAlreadyExistsException(Throwable cause) {
        super("이미 가입된 이메일입니다.", cause);
    }
}
