package com.yamltestkit.infrastructure.logback;

import com.yamltestkit.domain.model.LogExpectation;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnavailableLogCaptureTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main'";
    private static final LogExpectation WARNING = new LogExpectation("com.example.FeedLoader",
            LogExpectation.LogLevel.WARN, null, null, false);

    private static final String LOG4J_FACTORY = "org.apache.logging.slf4j.Log4jLoggerFactory";

    private final UnavailableLogCapture underTest = new UnavailableLogCapture(LOG4J_FACTORY);

    @Test
    void check_whenTheStepExpectsLogs_shouldFailNamingTheActiveBackend() {
        List<AssertionFailedError> result = underTest.check(List.of(WARNING), WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(WHERE + ": expect.logs needs Logback"
                + " (Spring Boot's default logging backend), but the active SLF4J backend is " + LOG4J_FACTORY);
    }

    @Test
    void check_whenTheStepExpectsNoLogs_shouldPass() {
        List<AssertionFailedError> result = underTest.check(List.of(), WHERE);

        assertThat(result).isEmpty();
    }
}
