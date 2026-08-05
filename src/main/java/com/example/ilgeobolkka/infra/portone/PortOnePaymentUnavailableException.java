package com.example.ilgeobolkka.infra.portone;

public class PortOnePaymentUnavailableException extends RuntimeException {

    public PortOnePaymentUnavailableException(Throwable cause) {
        super(cause);
    }

    public PortOnePaymentUnavailableException() {
        super();
    }
}
