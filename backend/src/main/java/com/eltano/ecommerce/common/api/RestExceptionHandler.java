package com.eltano.ecommerce.common.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.eltano.ecommerce.orders.service.payment.MercadoPagoWebhookService.InvalidWebhookSignatureException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final String ADMIN_API_PREFIX = "/api/admin/";

    private static final int MERCADO_PAGO_DETAIL_MAX_LENGTH = 180;
    private static final Pattern JSON_DETAIL_PATTERN = Pattern.compile(
            "\"(?:message|error_message|error|description|status_detail|cause)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();

        if (isAdminRequest(request)) {
            return ResponseEntity.badRequest().body(new AdminApiError(
                    "VALIDATION_ERROR",
                    "Validation failed",
                    correlationId(request),
                    fieldErrors));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI(),
                fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.badRequest().body(new AdminApiError(
                    "VALIDATION_ERROR",
                    ex.getMessage(),
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.badRequest().body(new AdminApiError(
                    "VALIDATION_ERROR",
                    "Malformed request body",
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Malformed request body",
                request.getRequestURI(),
                List.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.badRequest().body(new AdminApiError(
                    "BAD_REQUEST",
                    ex.getMessage(),
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.badRequest().body(new AdminApiError(
                    "BAD_REQUEST",
                    ex.getMessage(),
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AdminApiError(
                    "NOT_FOUND",
                    ex.getMessage(),
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<?> handleConflict(
            ConflictException ex,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AdminApiError(
                    "CONFLICT",
                    ex.getMessage(),
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ResponseEntity<?> handleUnprocessableEntity(
            UnprocessableEntityException ex,
            HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = ex.getFieldErrors().stream()
                .map(fieldError -> new ApiFieldError(fieldError.field(), fieldError.message()))
                .toList();

        if (isAdminRequest(request)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new AdminApiError(
                    "UNPROCESSABLE_ENTITY",
                    ex.getMessage(),
                    correlationId(request),
                    fieldErrors));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(FeatureNotSupportedException.class)
    public ResponseEntity<?> handleFeatureNotSupported(
            FeatureNotSupportedException ex,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new AdminApiError(
                    "NOT_SUPPORTED",
                    ex.getMessage(),
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.NOT_IMPLEMENTED.value(),
                HttpStatus.NOT_IMPLEMENTED.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }

    @ExceptionHandler(CancellationNotSupportedException.class)
    public ResponseEntity<?> handleCancellationNotSupported(
            CancellationNotSupportedException ex,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new AdminApiError(
                    CancellationNotSupportedException.CODE,
                    ex.getMessage(),
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.NOT_IMPLEMENTED.value(),
                HttpStatus.NOT_IMPLEMENTED.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        String message = "Data integrity violation";
        if (ex.getMostSpecificCause() != null
                && ex.getMostSpecificCause().getMessage() != null
                && ex.getMostSpecificCause().getMessage().toLowerCase().contains("duplicate")) {
            message = "Duplicated unique value";
        }

        if (isAdminRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AdminApiError(
                    "CONFLICT",
                    message,
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                message,
                request.getRequestURI(),
                List.of());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<?> handleWebhookSignature(
            InvalidWebhookSignatureException ex,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AdminApiError(
                    "FORBIDDEN",
                    "Invalid webhook signature",
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Invalid webhook signature",
                request.getRequestURI(),
                List.of());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MercadoPagoRequestException.class)
    public ResponseEntity<?> handleMercadoPagoHttpFailure(
            MercadoPagoRequestException ex,
            HttpServletRequest request) {
        HttpStatus status = is4xx(ex.getUpstreamStatusCode())
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;

        String message = "Mercado Pago request failed";
        String detail = sanitizeMercadoPagoDetail(ex.getUpstreamResponseBody());
        if (detail != null) {
            message = message + ": " + detail;
        } else {
            message = message + " (upstream " + ex.getUpstreamStatusCode() + ")";
        }

        if (isAdminRequest(request)) {
            return ResponseEntity.status(status).body(new AdminApiError(
                    status == HttpStatus.BAD_REQUEST ? "BAD_REQUEST" : "UPSTREAM_ERROR",
                    message,
                    correlationId(request),
                    List.of()));
        }

        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                List.of());
        return ResponseEntity.status(status).body(body);
    }

    private boolean is4xx(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }

    private String sanitizeMercadoPagoDetail(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }

        String compact = rawBody
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        Matcher matcher = JSON_DETAIL_PATTERN.matcher(compact);
        String candidate = matcher.find() ? matcher.group(1) : compact;
        if (candidate.isBlank()) {
            return null;
        }

        if (candidate.length() <= MERCADO_PAGO_DETAIL_MAX_LENGTH) {
            return candidate;
        }
        return candidate.substring(0, MERCADO_PAGO_DETAIL_MAX_LENGTH) + "...";
    }

    private ApiFieldError toFieldError(FieldError fieldError) {
        return new ApiFieldError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith(ADMIN_API_PREFIX);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return UUID.randomUUID().toString();
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            List<ApiFieldError> fieldErrors) {
    }

    public record ApiFieldError(String field, String message) {
    }

    public record AdminApiError(
            String code,
            String message,
            String correlationId,
            List<ApiFieldError> fieldErrors) {
    }
}
