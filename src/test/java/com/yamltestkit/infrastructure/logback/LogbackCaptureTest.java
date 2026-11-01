package com.yamltestkit.infrastructure.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.yamltestkit.domain.model.LogExpectation.LogLevel;
import com.yamltestkit.domain.model.LogExpectation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against the test JVM's real Logback context, on loggers of this test's own namespace, whose levels and additivity
 * are restored afterwards.
 */
class LogbackCaptureTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main'";
    private static final String PARENT = "com.yamltestkit.logs.fixture";
    private static final String LOADER = PARENT + ".FeedLoader";
    private static final LogExpectation ONE_FEED_42_WARNING = new LogExpectation(LOADER, LogLevel.WARN, "feed 42", 1,
            false);

    private final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    private LogCapture underTest;

    @BeforeEach
    void setUp() {
        logger(PARENT).setLevel(Level.INFO);
        underTest = LogbackCapture.of(loggerContext);
    }

    @AfterEach
    void tearDown() {
        logger(PARENT).detachAndStopAllAppenders();
        logger(LOADER).detachAndStopAllAppenders();
        logger(PARENT).setLevel(null);
        logger(PARENT).setAdditive(true);
        logger(LOADER).setLevel(null);
        logger(LOADER).setAdditive(true);
    }

    @Test
    void check_whenTheNamedLoggerLoggedTheEvent_shouldPass() {
        underTest.attach(List.of(LOADER));
        LoggerFactory.getLogger(LOADER).warn("unmappable feed {}", 42);

        List<AssertionFailedError> result = underTest.check(List.of(ONE_FEED_42_WARNING), WHERE);

        assertThat(result).isEmpty();
    }

    @Test
    void check_whenAnAncestorIsNotAdditive_shouldStillCaptureTheEvent() {
        logger(PARENT).setAdditive(false);
        underTest.attach(List.of(LOADER));
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");

        List<AssertionFailedError> result = underTest.check(List.of(ONE_FEED_42_WARNING), WHERE);

        assertThat(result).isEmpty();
    }

    @Test
    void check_whenALoggerAndItsParentAreNamed_shouldCaptureEachEventOnce() {
        underTest.attach(List.of(LOADER));
        underTest.attach(List.of(PARENT));
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");

        List<AssertionFailedError> result = underTest.check(List.of(ONE_FEED_42_WARNING), WHERE);

        assertThat(result).isEmpty();
    }

    @Test
    void check_whenTheNamedChildOfANamedParentIsNotAdditive_shouldCaptureItsEventOnce() {
        logger(LOADER).setAdditive(false);
        underTest.attach(List.of(PARENT, LOADER));
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");

        List<AssertionFailedError> result = underTest.check(List.of(ONE_FEED_42_WARNING), WHERE);

        assertThat(result).isEmpty();
    }

    @Test
    void check_whenTheSameMessageIsLoggedTwice_shouldCaptureBothEvents() {
        underTest.attach(List.of(PARENT, LOADER));
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");

        List<AssertionFailedError> result = underTest.check(List.of(new LogExpectation(LOADER, LogLevel.WARN,
                "feed 42", 2, false)), WHERE);

        assertThat(result).isEmpty();
    }

    @Test
    void attach_whenCalledAgainWithTheSameLoggers_shouldAttachTheAppenderOnce() {
        underTest.attach(List.of(LOADER));

        underTest.attach(List.of(LOADER));
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");

        assertThat(underTest.check(List.of(ONE_FEED_42_WARNING), WHERE)).isEmpty();
    }

    @Test
    void attach_whenTheConfigurationDroppedAndStoppedTheAppender_shouldRestartAndReattachIt() {
        underTest.attach(List.of(LOADER));
        logger(LOADER).detachAndStopAllAppenders();

        underTest.attach(List.of());
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");

        assertThat(underTest.check(List.of(ONE_FEED_42_WARNING), WHERE)).isEmpty();
    }

    @Test
    void clear_whenEventsWereCaptured_shouldForgetThem() {
        underTest.attach(List.of(LOADER));
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");

        underTest.clear();

        assertThat(underTest.check(List.of(ONE_FEED_42_WARNING), WHERE)).extracting(Throwable::getMessage)
                .containsExactly(WHERE + ": logs: expected times: 1 of WARN event of " + LOADER + " containing"
                        + " \"feed 42\", actual: 0; captured events of " + LOADER + ": none");
    }

    @Test
    void check_whenTheConfigurationFiltersTheExpectedLevel_shouldNameTheLoggingLevelProperty() {
        logger(LOADER).setLevel(Level.ERROR);
        underTest.attach(List.of(LOADER));
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");

        List<AssertionFailedError> result = underTest.check(List.of(ONE_FEED_42_WARNING), WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(WHERE + ": logs: WARN events of " + LOADER
                + " are never logged: the logger's effective level is ERROR; set `logging.level." + LOADER
                + ": WARN` in the `properties` of _context.yaml, or lower the level in the logging configuration");
    }

    @Test
    void close_whenAttached_shouldDetachTheAppenderAndForgetTheEvents() {
        underTest.attach(List.of(LOADER));
        LoggerFactory.getLogger(LOADER).warn("unmappable feed 42");

        underTest.close();

        assertThat(logger(LOADER).iteratorForAppenders().hasNext()).isFalse();
        assertThat(underTest.check(List.of(ONE_FEED_42_WARNING), WHERE)).hasSize(1);
    }

    @Test
    void create_whenLogbackIsTheBackend_shouldCaptureWithLogback() {
        LogCapture result = LogCapture.create();

        assertThat(result).isInstanceOf(LogbackCapture.class);
    }

    private Logger logger(String name) {
        return loggerContext.getLogger(name);
    }
}
