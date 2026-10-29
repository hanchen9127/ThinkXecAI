package com.thinkxecai.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.thinkxecai.common.ApiResponse;
import com.thinkxecai.common.LogText;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps every exception to the response envelope; the HTTP status stays 200 and the error is in
 * {@code code}. Client errors become 400 / 404 / 409 with a message naming the field; anything
 * else becomes a generic 500, and its details (exception messages, SQL, class names) go only to
 * the log.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String INTERNAL_ERROR_MESSAGE = "Internal server error";

    @Value("${spring.servlet.multipart.max-request-size}")
    private DataSize maxRequestSize;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize maxFileSize;

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException ex) {
        Integer code = ex.getCode();
        if (code == null || code >= 500) {
            log.error("Internal business exception", ex);
            return ApiResponse.error(INTERNAL_ERROR_MESSAGE);
        }
        log.warn("Business exception {}: {}", code, LogText.safe(ex.getMessage()));
        return ApiResponse.error(code, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleInvalidBody(MethodArgumentNotValidException ex) {
        String message = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + fieldMessage(error)),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(error -> error.getObjectName() + ": " + error.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining("; "));
        return badRequest(message.isEmpty() ? "Invalid request body" : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadableBody(HttpMessageNotReadableException ex) {
        // A well-formed body with a wrong value (e.g. an unknown role or a non-UUID id) names the field;
        // broken JSON or a missing body does not have one.
        if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            return badRequest(fieldPath(mapping) + ": invalid value");
        }
        return badRequest("Malformed or missing JSON request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = UUID.class.equals(ex.getRequiredType()) ? "must be a UUID" : "invalid value";
        return badRequest(ex.getName() + ": " + expected);
    }

    /** Thrown while the multipart request is read, before any controller; names the limit instead of a generic message. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        // Tomcat reports the whole request (all parts together) over max-request-size with its own exception.
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SizeLimitExceededException) {
                return badRequest("request: too large (maximum " + readable(maxRequestSize) + ")");
            }
        }
        return badRequest("file: too large (maximum " + readable(maxFileSize) + ")");
    }

    private static String readable(DataSize size) {
        long kilobytes = size.toKilobytes();
        return kilobytes % 1024 == 0 ? kilobytes / 1024 + " MB" : kilobytes + " KB";
    }

    /** A body that is not valid multipart (MaxUploadSizeExceededException, a subclass, is handled above). */
    @ExceptionHandler(MultipartException.class)
    public ApiResponse<Void> handleMalformedMultipart(MultipartException ex) {
        return badRequest("Malformed multipart request");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        // Spring MVC's own client errors (unknown path, wrong method or content type, missing
        // parameter) carry a 4xx status; they are not server failures.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            if (errorResponse.getStatusCode().value() == 404) {
                log.warn("Not found: {}", LogText.safe(ex.getMessage()));
                return ApiResponse.error(404, "Not found");
            }
            String detail = errorResponse.getBody().getDetail();
            return badRequest(detail != null ? detail : "Invalid request");
        }
        log.error("Unhandled exception", ex);
        return ApiResponse.error(INTERNAL_ERROR_MESSAGE);
    }

    private static ApiResponse<Void> badRequest(String message) {
        log.warn("Bad request: {}", LogText.safe(message));
        return ApiResponse.error(400, message);
    }

    /**
     * A form field that cannot be converted (e.g. a multipart {@code kbId} that is not a UUID) carries
     * a message with Java type names; it is replaced like a path variable type mismatch.
     */
    private static String fieldMessage(FieldError error) {
        if (!error.isBindingFailure()) {
            return error.getDefaultMessage();
        }
        boolean uuid = error.contains(TypeMismatchException.class)
                && UUID.class.equals(error.unwrap(TypeMismatchException.class).getRequiredType());
        return uuid ? "must be a UUID" : "invalid value";
    }

    /** Builds a client-facing path such as {@code metadata.toolCalls[0].id}, without Java class names. */
    private static String fieldPath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : ex.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

}
