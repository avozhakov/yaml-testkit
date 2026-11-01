package com.yamltestkit.selftest.app.discover;

import com.yamltestkit.selftest.app.PartnerUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@code /discover} failures to the published error codes: an unreadable body or a missing version header is
 * {@code 400 bad_request}, an unknown version {@code 400 unsupported_version}, a failed partner call
 * {@code 500 internal_error}. The message never echoes the request.
 */
@RestControllerAdvice(assignableTypes = DiscoverController.class)
public class DiscoverExceptionHandler {

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<DiscoverError> handleBadRequest(HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "bad_request", "the request could not be read");
    }

    @ExceptionHandler(UnsupportedVersionException.class)
    public ResponseEntity<DiscoverError> handleUnsupportedVersion(HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "unsupported_version", "the version is not supported");
    }

    @ExceptionHandler(PartnerUnavailableException.class)
    public ResponseEntity<DiscoverError> handlePartnerUnavailable(HttpServletRequest request) {
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "a dependency failed");
    }

    private static ResponseEntity<DiscoverError> error(HttpServletRequest request, HttpStatus status, String code,
                                                       String message) {
        return ResponseEntity.status(status).body(new DiscoverError(request.getHeader(DiscoverController.VERSION_HEADER),
                new DiscoverError.Error(code, message)));
    }
}
