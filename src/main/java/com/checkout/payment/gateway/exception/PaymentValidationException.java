package com.checkout.payment.gateway.exception;

public class PaymentValidationException extends RuntimeException {

  public PaymentValidationException(String message) {
    super(message);
  }
}
