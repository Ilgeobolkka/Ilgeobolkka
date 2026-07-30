package com.example.ilgeobolkka.infra.portone;

public interface PortOnePaymentGateway {

    PortOnePayment getPayment(String paymentId);
}
