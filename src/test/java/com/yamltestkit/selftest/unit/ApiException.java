package com.yamltestkit.selftest.unit;

/**
 * Imitates an OpenAPI-generated client exception: checked, with a status code. The two constructors are the
 * fixture's point (typed {@code throws.args} vs {@code cause}), so they are written by hand.
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
