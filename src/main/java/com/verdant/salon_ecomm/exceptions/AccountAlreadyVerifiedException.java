package com.verdant.salon_ecomm.exceptions;

public class AccountAlreadyVerifiedException extends RuntimeException {
  public AccountAlreadyVerifiedException(String message) {
    super(message);
  }
}
