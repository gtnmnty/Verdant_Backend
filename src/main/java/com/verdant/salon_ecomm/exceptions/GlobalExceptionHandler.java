package com.verdant.salon_ecomm.exceptions;

import com.verdant.salon_ecomm.dtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    // =========================================================================
    // GraphQL exception handlers (@GraphQlExceptionHandler)
    // Ordered most-specific first; catchAllException is the fallback.
    // Messages are static/controlled — never echo ex.getMessage() to the
    // client, since underlying exceptions can carry internal details
    // (field paths, SQL fragments, stack info). Extensions.code stays
    // machine-readable for the frontend to branch on.
    // =========================================================================

    @GraphQlExceptionHandler
    public GraphQLError handleIllegalArgument(IllegalArgumentException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("Invalid request.")
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "INVALID_ARGUMENT", "status", 400))
            .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleConstraintViolation(ConstraintViolationException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("Validation failed.")
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "VALIDATION_ERROR", "status", 400))
            .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleNotFoundGraphQL(ResourceNotFoundException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("The requested resource was not found.")
            .errorType(ErrorType.NOT_FOUND)
            .extensions(Map.of("code", "NOT_FOUND", "status", 404))
            .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleAccessDenied(AccessDeniedException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("You do not have permission to perform this action.")
            .errorType(ErrorType.FORBIDDEN)
            .extensions(Map.of("code", "FORBIDDEN", "status", 403))
            .build();
    }

    // Wrong role accessing endpoint
    @GraphQlExceptionHandler
    public GraphQLError handleAccessPoint(ForbiddenException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("You do not have permission to perform this action.")
            .errorType(ErrorType.FORBIDDEN)
            .extensions(Map.of("code", "FORBIDDEN", "status", 403))
            .build();
    }

    // Payment Failure
    @GraphQlExceptionHandler
    public GraphQLError handlePaymentFailure(PaymentException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("Payment could not be processed.")
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "PAYMENT_FAILED", "status", 402))
            .build();
    }

    // Appointment Slot already booked
    @GraphQlExceptionHandler
    public GraphQLError handleAppointmentConflict(AppointmentConflictException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("This appointment slot is no longer available.")
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "APPOINTMENT_CONFLICT", "status", 409))
            .build();
    }

    @GraphQlExceptionHandler(InvalidAppointmentException.class)
    public GraphQLError handleInvalidAppointment(InvalidAppointmentException ex) {
        return GraphQLError.newError()
            .errorType(ErrorType.BAD_REQUEST)
            .message("Invalid appointment request.")
            .build();
    }

    // Cart item not found (e.g. removeCartItems / getSelectedCart referencing an id
    // that isn't in the user's cart)
    @GraphQlExceptionHandler(CartItemNotFoundException.class)
    public GraphQLError handleCartItemNotFound(CartItemNotFoundException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.BAD_REQUEST)
            .message("That item is not in your cart.")
            .extensions(Map.of("code", "CART_ITEM_NOT_FOUND", "status", 400))
            .build();
    }

    // Invalid cart item quantity (e.g. addToCart / updateCartItemQuantity with qty < 1)
    @GraphQlExceptionHandler(InvalidQuantityException.class)
    public GraphQLError handleInvalidQuantity(InvalidQuantityException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.BAD_REQUEST)
            .message("Invalid item quantity.")
            .extensions(Map.of("code", "INVALID_QUANTITY", "status", 400))
            .build();
    }

    // Insufficient Stock
    @GraphQlExceptionHandler(InsufficientStockException.class)
    public GraphQLError handleInsufficientStockException(InsufficientStockException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.BAD_REQUEST)
            .message("Insufficient stock for this item.")
            .extensions(Map.of("code", "INSUFFICIENT_STOCK", "status", 409))
            .build();
    }

    // Malformed pagination cursor (e.g. NotificationResolver.decodeCursor)
    @GraphQlExceptionHandler(InvalidCursorException.class)
    public GraphQLError handleInvalidCursor(InvalidCursorException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.BAD_REQUEST)
            .message("Invalid pagination cursor.")
            .extensions(Map.of("code", "INVALID_CURSOR", "status", 400))
            .build();
    }

    // Catch-all fallback: keep this last among the GraphQL handlers
    @GraphQlExceptionHandler
    public GraphQLError catchAllException(Exception ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("Something went wrong")
            .errorType(ErrorType.INTERNAL_ERROR)
            .extensions(Map.of("code", "INTERNAL_ERROR", "status", 500))
            .build();
    }

    // =========================================================================
    // REST exception handlers (@ExceptionHandler)
    // Ordered most-specific first; handleGeneric is the fallback.
    // Messages are static/controlled — see note above.
    // =========================================================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                404,
                "Not Found",
                "The requested resource was not found.",
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
                "This account has already been verified.",
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
                "This account has not been verified yet.",
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
                "This email address is already registered.",
                request.getRequestURI()
            ));
    }

    // Wrong password or login credentials
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(OffsetDateTime.now(),
                401,
                "Unauthorized",
                "Invalid email or password.",
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
                "Your session has expired. Please log in again.",
                request.getRequestURI()
            ));
    }

    // Verification Code Expired
    @ExceptionHandler(VerificationCodeExpiredException.class)
    public ResponseEntity<ErrorResponse> handleVerificationCodeExpired(VerificationCodeExpiredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(OffsetDateTime.now(),
                400,
                "TOKEN_EXPIRED",
                "This verification code has expired.",
                request.getRequestURI()));
    }

    // Invalid Verification Code
    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVerificationCode(InvalidVerificationCodeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(OffsetDateTime.now(), 400, "Bad Request", "Invalid verification code.", request.getRequestURI()));
    }

    // RefreshToken Expired
    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenExpired(RefreshTokenExpiredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(OffsetDateTime.now(), 401, "Unauthorized", "Your session has expired. Please log in again.", request.getRequestURI()));
    }

    // Email Delivery Failure
    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleEmailDelivery(EmailDeliveryException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse(OffsetDateTime.now(),
                503,
                "Service Unavailable",
                "We couldn't send this email. Please try again later.",
                request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationRest(ConstraintViolationException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(OffsetDateTime.now(), 400, "Bad Request", "Validation failed.", request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            OffsetDateTime.now(), 413, "Payload Too Large",
            "The uploaded file exceeds the maximum allowed size.", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            OffsetDateTime.now(), 409, "Conflict",
            "The request could not be completed due to a conflict.", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<ErrorResponse> handleInvalidWebhookSignature(InvalidWebhookSignatureException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                400,
                "Invalid webhook signature",
                "The webhook signature could not be verified.",
                request.getRequestURI()
            ));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                409,
                "Insufficient stock",
                "Insufficient stock for this item.",
                request.getRequestURI()
            ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                401,
                "Unauthorized",
                "You are not allowed to perform this action",
                request.getRequestURI()
            ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                403,
                "Forbidden",
                "You do not have permission to perform this action.",
                request.getRequestURI()
            ));
    }

    // Catch-all fallback: keep this last among the REST handlers
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(OffsetDateTime.now(),
                500,
                "Internal Server Error",
                "Something went wrong",
                request.getRequestURI()
            ));
    }
}