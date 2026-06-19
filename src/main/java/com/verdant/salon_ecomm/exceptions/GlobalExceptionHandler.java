package com.verdant.salon_ecomm.exceptions;


import com.verdant.salon_ecomm.dtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(404).body(new ErrorResponse(
            LocalDateTime.now(),
            404,
            "NOT_FOUND",
            ex.getMessage(),
            request.getRequestURI()
        ));
    }

    // Email is already registered
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(new ErrorResponse(
            LocalDateTime.now(),
            409,
            "CONFLICT",
            ex.getMessage(),
            request.getRequestURI()
        ));
    }

    // Wrong password or login credentials
    @ExceptionHandler(InvalidCrendetialsException.class)
    public  ResponseEntity<ErrorResponse> handleUnauthorized(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(401).body(new ErrorResponse(
            LocalDateTime.now(),
            401,
            "UNAUTHORIZED",
            ex.getMessage(),
            request.getRequestURI()
        ));
    }

    // JWT Token is expired
    @ExceptionHandler(TokenExpiredException.class)
    public  ResponseEntity<ErrorResponse> handleInternalServerError(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(401).body(new ErrorResponse(
            LocalDateTime.now(),
            401,
            "TOKEN_EXPIRED",
            ex.getMessage(),
            request.getRequestURI()
        ));
    }

    // Wrong role accessing endpoint
    @ExceptionHandler(ForbiddenException.class)
    public  ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(400).body(new ErrorResponse(
            LocalDateTime.now(),
            400,
            "FORBIDDEN",
            ex.getMessage(),
            request.getRequestURI()
        ));
    }

    // Payment Failure
    @ExceptionHandler(PaymentException.class)
    public  ResponseEntity<ErrorResponse> handleConflict(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(402).body(new ErrorResponse(
            LocalDateTime.now(),
            402,
            "PAYMENT_FAILED",
            ex.getMessage(),
            request.getRequestURI()
        ));
    }

    // Appointment Slot already booked
    @ExceptionHandler(AppointmentConflictException.class)
    public  ResponseEntity<ErrorResponse> handleUnprocessable(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(new ErrorResponse(
            LocalDateTime.now(),
            409,
            "APPOINTMENT_CONFLICT",
            ex.getMessage(),
            request.getRequestURI()
        ));
    }

    // Catch for all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> catchAllException(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(500).body(new ErrorResponse(
            LocalDateTime.now(),
            500,
            "INTERNAL_ERROR",
            "Something went wrong",
            request.getRequestURI()
        ));
    }



}
