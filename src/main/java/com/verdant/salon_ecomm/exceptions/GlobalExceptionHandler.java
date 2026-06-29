package com.verdant.salon_ecomm.exceptions;

import com.verdant.salon_ecomm.dtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;

import java.time.OffsetDateTime;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                404,
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
            ));
    }

    @ExceptionHandler(AccountAlreadyVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleAccountAlreadyVerified(AccountAlreadyVerifiedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        OffsetDateTime.now(),
                        409,
                        "Account already verified",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotVerified(AccountNotVerifiedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                403,
                "Forbidden",
                ex.getMessage(),
                request.getRequestURI()
            ));
    }


    // Email is already registered
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(OffsetDateTime.now(),
                        409,
                        "EMAIL_REGISTERED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // Wrong password or login credentials
    @ExceptionHandler(InvalidCrendetialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCrendetialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(OffsetDateTime.now(),
                        401,
                        "Unauthorized",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // JWT Token is expired
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(OffsetDateTime.now(),
                        401,
                        "JWT Token expired",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // Wrong role accessing endpoint
    @GraphQlExceptionHandler
    public GraphQLError handleAccessPoint(ForbiddenException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .extensions(Map.of("code", "FORBIDDEN", "status", 403))
            .build();
    }

    // Payment Failure
    @GraphQlExceptionHandler
    public GraphQLError handlePaymentFailure(PaymentException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .extensions(Map.of("code", "PAYMENT_FAILED", "status", 402))
            .build();
    }

    // Appointment Slot already booked
    @GraphQlExceptionHandler
    public GraphQLError handleAppointmentConflict(AppointmentConflictException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .extensions(Map.of("code", "APPOINTMENT_CONFLICT", "status", 409))
            .build();
    }

    // Verification Code Expired
    @ExceptionHandler(VerificationCodeExpiredException.class)
    public ResponseEntity<ErrorResponse> handleVerificationCodeExpired(VerificationCodeExpiredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(OffsetDateTime.now(),
                        400,
                        "TOKEN_EXPIRED",
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    // Email Delivery Failure
    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleEmailDelivery(EmailDeliveryException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(OffsetDateTime.now(),
                        503,
                        "Service Unavailable",
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    // Invalid Code Expired
    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVerificationCode(InvalidVerificationCodeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(OffsetDateTime.now(), 400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    // RefreshToken Expired
    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenExpired(RefreshTokenExpiredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(OffsetDateTime.now(), 401, "Unauthorized", ex.getMessage(), request.getRequestURI()));
    }

    // Catch for all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(OffsetDateTime.now(),
                        500,
                        "Internal Server Error",
                        "Something went wrong on our server.",
                        request.getRequestURI()
                ));
    }

    @GraphQlExceptionHandler
    public GraphQLError catchAllException(Exception ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("Something went wrong on our server.")
            .errorType(ErrorType.INTERNAL_ERROR)
            .extensions(Map.of("code", "INTERNAL_ERROR", "status", 500))
            .build();
    }


}
