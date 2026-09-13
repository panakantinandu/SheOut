package com.sheout.sharedkernel.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Ensures every unhandled exception still comes back to the client as an
 * {@link ApiErrorResponse}, regardless of which module threw it. Module-
 * specific business exceptions should be handled with a
 * {@link Result}-based flow at the module boundary where possible; this is
 * the fallback for everything else.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                               HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::toString)
                .toList();
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Validation failed",
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * A body that is missing, empty, or not parseable as JSON.
     * <p>
     * This was falling through to the catch-all below and coming back as a
     * 500 with Spring's own message, which named the controller method, its
     * package and its full parameter list. That is the caller's mistake
     * being reported as the server's fault, and reported by handing them a
     * map of the internals. It surfaced the moment cancelling a booking
     * started requiring a reason: any client still posting the old empty
     * cancel got a 500 instead of being told what was missing.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                  HttpServletRequest request) {
        log.debug("Unreadable request body on {}", request.getRequestURI(), ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                // Deliberately says nothing about the handler it failed to
                // bind to. What the caller needs is that the body was the
                // problem; what the exception carries is our class names.
                "This request needs a JSON body, and it was missing or could not be read",
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * A body sent as the wrong content type, or with none at all.
     * <p>
     * Same family as the unreadable body above, and found the same way: the
     * multipart photo upload answered a bodyless request with a 500 saying
     * "Content-Type is not supported". That is the caller's mistake reported
     * as the server's fault, and it makes a genuine server fault impossible
     * to spot in the logs among the noise.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                        HttpServletRequest request) {
        log.debug("Unsupported content type on {}", request.getRequestURI(), ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "Unsupported Media Type",
                ex.getSupportedMediaTypes().isEmpty()
                        ? "This request was sent with a content type this endpoint does not accept"
                        : "This endpoint accepts " + ex.getSupportedMediaTypes(),
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                ex.status().value(),
                ex.error(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(ex.status()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
