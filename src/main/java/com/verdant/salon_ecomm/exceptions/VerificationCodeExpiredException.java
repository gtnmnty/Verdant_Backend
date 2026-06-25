package com.verdant.salon_ecomm.exceptions;

public class VerificationCodeExpiredException extends RuntimeException {
  public VerificationCodeExpiredException(String message) {
    super(message);
  }
}
