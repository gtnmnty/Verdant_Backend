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
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
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
    @GraphQlExceptionHandler
    public GraphQLError handleBadRequest(DuplicateEmailException ex,  DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "EMAIL_ALREADY_EXISTS", "status", 409))
            .build();
    }

    // Wrong password or login credentials
    @GraphQlExceptionHandler
    public GraphQLError handleUnauthorized(InvalidCrendetialsException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .errorType(ErrorType.UNAUTHORIZED)
            .extensions(Map.of("code", "UNAUTHORIZED", "status", 401))
            .build();
    }

    // JWT Token is expired
    @GraphQlExceptionHandler
    public GraphQLError handleTokenExpired(TokenExpiredException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .extensions(Map.of("code", "TOKEN_EXPIRED", "status", 401))
            .build();
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
    @GraphQlExceptionHandler
    public GraphQLError handleVerificationCodeExpired(VerificationCodeExpiredException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .extensions(Map.of("code", "VERFICATION_CODE_EXPIRED", "status", 400))
                .build();
    }

    // Account Not Verified
    @GraphQlExceptionHandler
    public GraphQLError handleAccountNotVerified(AccountNotVerifiedException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .extensions(Map.of("code", "ACCOUNT_NOT_VERIFIED", "status", 403))
                .build();
    }

    // Account Already Verified
    @GraphQlExceptionHandler
    public GraphQLError handleAccountAlreadyVerified(AccountAlreadyVerifiedException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .extensions(Map.of("code", "ALREADY_VERIFIED", "status", 409))
                .build();
    }

    // Email Delivery Failure
    @GraphQlExceptionHandler
    public GraphQLError handleEmailDeliveryFailure(EmailDeliveryException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .extensions(Map.of("code", "EMAIL_DELIVERY_FAILURE", "status", 409))
                .build();
    }

    // Invalid Code Expired
    @GraphQlExceptionHandler
    public GraphQLError handleInvalidVerificationCode(InvalidVerificationCodeException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .extensions(Map.of("code", "INVALID_CREDENTIALS", "status", 400))
                .build();
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
