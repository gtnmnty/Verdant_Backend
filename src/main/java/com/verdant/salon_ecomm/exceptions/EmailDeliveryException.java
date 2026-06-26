package com.verdant.salon_ecomm.exceptions;

public class EmailDeliveryException extends RuntimeException {
  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
