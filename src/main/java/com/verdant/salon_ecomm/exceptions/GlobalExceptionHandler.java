package com.verdant.salon_ecomm.exceptions;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @GraphQlExceptionHandler
    public GraphQLError handleNotFound(ResourceNotFoundException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .errorType(ErrorType.NOT_FOUND)
            .extensions(Map.of("code", "NOT_FOUND", "status", 404))
            .build();
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

    // Catch for all
    @GraphQlExceptionHandler
    public GraphQLError catchAllException(Exception ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("Something went wrong on our server.")
            .errorType(ErrorType.INTERNAL_ERROR)
            .extensions(Map.of("code", "INTERNAL_ERROR", "status", 500))
            .build();
    }
}
