package com.verdant.salon_ecomm.exceptions;

import com.verdant.salon_ecomm.dtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String CONCURRENT_UPDATE_MESSAGE =
        "Someone else changed this record while you were editing it. Reload and try again.";

    // =========================================================================
    // GraphQL exception handlers (@GraphQlExceptionHandler)
    // Ordered most-specific first; catchAllException is the fallback.
    // Messages are static/controlled — never echo ex.getMessage() to the
    // client, since underlying exceptions can carry internal details
    // (field paths, SQL fragments, stack info). Extensions.code stays
    // machine-readable for the frontend to branch on.
    // Exceptions to that rule (M-9): BusinessRuleException, TooManyRequestsException and bean
    // validation messages are written by us for the user, so their text is passed through.
    // Anything unexpected is logged here, so a real failure leaves a trace in the logs.
    // =========================================================================

    // A rule written for the user (M-9). Message shown as is; status/code come from the exception.
    @GraphQlExceptionHandler
    public GraphQLError handleBusinessRule(BusinessRuleException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", ex.getCode(), "status", ex.getStatus().value()))
            .build();
    }

    // M-5: rate limit hit (PublicFormRateLimiter, GiftCardRedeemLimiter).
    @GraphQlExceptionHandler
    public GraphQLError handleTooManyRequests(TooManyRequestsException ex, DataFetchingEnvironment env) {
        Map<String, Object> extensions = ex.getRetryAfterSeconds() == null
            ? Map.of("code", "TOO_MANY_REQUESTS", "status", 429)
            : Map.of("code", "TOO_MANY_REQUESTS", "status", 429, "retryAfter", ex.getRetryAfterSeconds());
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(extensions)
            .build();
    }

    // A DB constraint (unique, foreign key, not-null) rejected the write. The driver message names
    // tables and columns, so it is logged, not returned.
    @GraphQlExceptionHandler
    public GraphQLError handleDataIntegrity(DataIntegrityViolationException ex, DataFetchingEnvironment env) {
        log.warn("Data integrity violation in {}: {}", env.getField().getName(), ex.getMostSpecificCause().getMessage());
        return GraphqlErrorBuilder.newError(env)
            .message("This change conflicts with existing data.")
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "CONFLICT", "status", 409))
            .build();
    }

    // M-15: someone else saved the same row (product, order, appointment, gift card, promo code) after this
    // request loaded it, so its @Version no longer matched and nothing was written. Not a bug: the user
    // reloads and repeats the change on fresh data.
    @GraphQlExceptionHandler
    public GraphQLError handleOptimisticLock(ObjectOptimisticLockingFailureException ex, DataFetchingEnvironment env) {
        log.info("Concurrent update rejected in {}: {}", env.getField().getName(), ex.getMessage());
        return GraphqlErrorBuilder.newError(env)
            .message(CONCURRENT_UPDATE_MESSAGE)
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "CONCURRENT_MODIFICATION", "status", 409))
            .build();
    }

    // Our own user-facing checks throw BusinessRuleException now, so an IllegalArgumentException
    // here usually comes from a library (UUID.fromString, Enum.valueOf). Its message stays out of the
    // response but goes to the log, so "Invalid request." can still be traced.
    @GraphQlExceptionHandler
    public GraphQLError handleIllegalArgument(IllegalArgumentException ex, DataFetchingEnvironment env) {
        log.warn("Invalid argument in {}: {}", env.getField().getName(), ex.getMessage());
        return GraphqlErrorBuilder.newError(env)
            .message("Invalid request.")
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "INVALID_ARGUMENT", "status", 400))
            .build();
    }

    // @Valid on @Argument (M-10) lands here. Messages come from our constraint annotations, so the
    // user sees which field is wrong ("Validation failed: price: Price can never be lower than 0").
    @GraphQlExceptionHandler
    public GraphQLError handleConstraintViolation(ConstraintViolationException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message(describeViolations(ex))
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
    // H-5: per-customer cap on unpaid orders holding stock. Message is ours, safe to show.
    @GraphQlExceptionHandler(UnpaidOrderLimitException.class)
    public GraphQLError handleUnpaidOrderLimit(UnpaidOrderLimitException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.BAD_REQUEST)
            .message(ex.getMessage())
            .extensions(Map.of("code", "UNPAID_ORDER_LIMIT", "status", 409))
            .build();
    }

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

    // The three below carry messages we write ourselves (no internals), so they're passed through.
    // The email-change flow needs them: without these they fell into the catch-all as "Something went wrong".

    @GraphQlExceptionHandler
    public GraphQLError handleDuplicateEmailGraphQL(DuplicateEmailException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("An account with this email already exists.")
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "EMAIL_TAKEN", "status", 409))
            .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleInvalidCodeGraphQL(InvalidVerificationCodeException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message("Invalid or expired code.")
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(Map.of("code", "INVALID_CODE", "status", 400))
            .build();
    }

    // Wrong or missing current password (e.g. on an email change).
    @GraphQlExceptionHandler
    public GraphQLError handleInvalidCredentialsGraphQL(InvalidCredentialsException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .errorType(ErrorType.UNAUTHORIZED)
            .extensions(Map.of("code", "INVALID_CREDENTIALS", "status", 401))
            .build();
    }

    // Catch-all fallback: keep this last among the GraphQL handlers.
    // Logged with the stack trace (M-9): the client only sees "Something went wrong", so the log is
    // the only place a real 500 can be investigated from.
    @GraphQlExceptionHandler
    public GraphQLError catchAllException(Exception ex, DataFetchingEnvironment env) {
        log.error("Unhandled exception in GraphQL field {}", env.getField().getName(), ex);
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

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleRest(BusinessRuleException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
            ));
    }

    // M-5: 429 instead of the old 409, so clients can tell "slow down" from a real conflict.
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequestsRest(TooManyRequestsException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (ex.getRetryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        return response
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                429,
                "Too Many Requests",
                ex.getMessage(),
                request.getRequestURI()
            ));
    }

    // M-15: same as the GraphQL handler. For the Stripe webhook, a 409 means Stripe retries the event,
    // which then runs on the fresh row (the webhook's idempotency row was rolled back with it).
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockRest(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Concurrent update rejected on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                409,
                "Conflict",
                CONCURRENT_UPDATE_MESSAGE,
                request.getRequestURI()
            ));
    }

    // Login of a suspended account and the password change of a locked one throw this. It had no
    // REST handler, so it fell through to the catch-all as a 500.
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenRest(ForbiddenException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(
                OffsetDateTime.now(),
                403,
                "Forbidden",
                ex.getMessage(),
                request.getRequestURI()
            ));
    }

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

    // A DB constraint (unique, foreign key, not-null) rejected the write and no service translated it.
    // Same as the GraphQL handler: 409, the driver message (table and column names) is logged, not returned.
    // Before, REST had no handler, so this fell through to the catch-all as a 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityRest(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(OffsetDateTime.now(),
                409,
                "Conflict",
                "This change conflicts with existing data.",
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
            .body(new ErrorResponse(OffsetDateTime.now(),
                400,
                "Bad Request",
                "Invalid verification code.",
                request.getRequestURI()));
    }

    // RefreshToken Expired
    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenExpired(RefreshTokenExpiredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(OffsetDateTime.now(),
                401, "Unauthorized",
                "Your session has expired. Please log in again.",
                request.getRequestURI()));
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
            .body(new ErrorResponse(OffsetDateTime.now(),
                400,
                "Bad Request",
                describeViolations(ex),
                request.getRequestURI()));
    }

    // @Valid @RequestBody failures (as opposed to @Validated method-param
    // failures, which throw ConstraintViolationException above).
    // Field names and our annotation messages are listed, e.g. "Validation failed: email: Email is required".
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = Stream.concat(
                ex.getBindingResult().getFieldErrors().stream().map(e -> e.getField() + ": " + e.getDefaultMessage()),
                ex.getBindingResult().getGlobalErrors().stream().map(e -> e.getDefaultMessage()))
            .sorted()
            .collect(Collectors.joining("; "));
        String message = details.isEmpty() ? "Validation failed." : "Validation failed: " + details;
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(OffsetDateTime.now(), 400, "Bad Request", message, request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            OffsetDateTime.now(), 413, "Payload Too Large",
            "The uploaded file exceeds the maximum allowed size.", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    // User-facing state rules throw BusinessRuleException now, so this is logged: it is likely a bug.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("IllegalStateException on {}: {}", request.getRequestURI(), ex.getMessage());
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

    // Catch-all fallback: keep this last among the REST handlers. Logged with the stack trace (M-9).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(OffsetDateTime.now(),
                500,
                "Internal Server Error",
                "Something went wrong",
                request.getRequestURI()
            ));
    }

    // "Validation failed: price: Price can never be lower than 0; name: Service name is required".
    // Only the last path segment is shown: GraphQL argument paths look like "createService.input.price",
    // and the method/argument prefix means nothing to the user.
    private static String describeViolations(ConstraintViolationException ex) {
        String details = ex.getConstraintViolations().stream()
            .map(v -> fieldName(v) + ": " + v.getMessage())
            .sorted()
            .collect(Collectors.joining("; "));
        return details.isEmpty() ? "Validation failed." : "Validation failed: " + details;
    }

    private static String fieldName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getName() != null) name = node.getName();
        }
        return name != null ? name : "value";
    }
}
