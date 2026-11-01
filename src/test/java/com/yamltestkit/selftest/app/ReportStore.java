package com.yamltestkit.selftest.app;

/** No bean implements it: the cases create a mock from its type (imitates the test-only {@code @Primary JdbcTemplate}). */
public interface ReportStore {

    int count();
}
