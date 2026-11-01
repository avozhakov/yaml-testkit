package com.yamltestkit.selftest.app;

/**
 * Imitates an OpenAPI-generated client exception: checked, with an HTTP status code. The two constructors are the
 * fixture's point (typed {@code throws.args} vs {@code cause}, and what a generated client throws for a non-2xx
 * status vs an I/O error), so they are written by hand.
 */
public class ApiException extends Exception {

    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(Throwable cause) {
        super(cause);
        this.code = 0;
    }

    public int getCode() {
        return code;
    }
}
