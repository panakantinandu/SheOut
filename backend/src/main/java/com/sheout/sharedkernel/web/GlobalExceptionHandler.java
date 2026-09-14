package com.sheout.sharedkernel.web;

import com.sheout.sharedkernel.ratelimit.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

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

    /**
     * A constraint on a query parameter or path variable (a search box's
     * @Size, a coordinate's @DecimalMin) rather than on a JSON body.
     * <p>
     * Spring reports these as a different exception from body validation, so
     * without this they fell to the catch-all and a too-long search string
     * came back as a 500 naming the handler method.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException ex,
                                                                      HttpServletRequest request) {
        List<String> details = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
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
     * A path nothing is mapped to. Was a 500 through the catch-all, which
     * would now also log a stack trace for every scanner probing /wp-admin.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "Not Found", "No such endpoint", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * A real path called with a verb it does not support - a POST to a
     * read-only endpoint. Was a 500 through the catch-all, for every endpoint
     * in the app, which reported a caller's mistake as a server fault and
     * logged a stack trace for it.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(), "Method Not Allowed",
                "This endpoint does not accept " + ex.getMethod() + " requests", request.getRequestURI());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null) {
            response.allow(ex.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]));
        }
        return response.body(body);
    }

    /** A 429 always says how long to wait, in the standard header as well as the body. */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyRequests(TooManyRequestsException ex,
                                                                  HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ex.error(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of("retryAfterSeconds: " + ex.retryAfterSeconds())
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(body);
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

    /**
     * Everything nobody anticipated.
     * <p>
     * The message used to be ex.getMessage(), returned to the caller and
     * logged nowhere. That was backwards on both counts. A database
     * constraint failure's message quotes the SQL and the offending value -
     * "Key (phone_number)=(+91...) already exists" - so the response could
     * hand one person's data to another, while the server kept no record that
     * anything had gone wrong at all. The detail now goes to the log, under
     * a reference the caller is given so support can find it.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString();
        log.error("Unhandled exception on {} {} [ref {}]", request.getMethod(), request.getRequestURI(), reference, ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Something went wrong on our side. If it keeps happening, contact support with reference " + reference,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
