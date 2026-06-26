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

    /**
     * Converts an expired verification code exception into a GraphQL error.
     *
     * @param  ex   the exception that triggered the error
     * @param  env  the GraphQL data fetching environment
     * @return      the generated GraphQL error
     */
    @GraphQlExceptionHandler
    public GraphQLError handleVerificationCodeExpired(VerificationCodeExpiredException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .extensions(Map.of("code", "VERFICATION_CODE_EXPIRED", "status", 400))
                .build();
    }

    /**
     * Builds a GraphQL error for an account that has not been verified.
     *
     * @param ex the exception containing the error message
     * @return a GraphQL error with code {@code ACCOUNT_NOT_VERIFIED} and status {@code 403}
     */
    @GraphQlExceptionHandler
    public GraphQLError handleAccountNotVerified(AccountNotVerifiedException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .extensions(Map.of("code", "ACCOUNT_NOT_VERIFIED", "status", 403))
                .build();
    }

    /**
     * Builds a GraphQL error for an email delivery failure.
     *
     * @param  ex  the exception containing the error message
     * @return     the GraphQL error response
     */
    @GraphQlExceptionHandler
    public GraphQLError handleEmailDeliveryFailure(EmailDeliveryException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .extensions(Map.of("code", "EMAIL_DELIVERY_FAILURE", "status", 409))
                .build();
    }

    /**
     * Maps an invalid verification code exception to a GraphQL error.
     *
     * @param ex  the exception to convert
     * @param env the GraphQL data fetching environment
     * @return the generated GraphQL error
     */
    @GraphQlExceptionHandler
    public GraphQLError handleInvalidVerificationCode(InvalidVerificationCodeException ex, DataFetchingEnvironment env){
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .extensions(Map.of("code", "INVALID_CREDENTIALS", "status", 400))
                .build();
    }



    /**
     * Builds a fallback GraphQL error for uncaught exceptions.
     *
     * @return a GraphQL error with an internal server error message, error type, and extensions
     */
    @GraphQlExceptionHandler
    public GraphQLError catchAllException(Exception ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("Something went wrong on our server.")
            .errorType(ErrorType.INTERNAL_ERROR)
            .extensions(Map.of("code", "INTERNAL_ERROR", "status", 500))
            .build();
    }
}
