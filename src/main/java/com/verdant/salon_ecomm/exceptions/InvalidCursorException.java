package com.verdant.salon_ecomm.exceptions;

public class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String cursor) {
        super("Invalid pagination cursor: " + cursor);
    }
}
