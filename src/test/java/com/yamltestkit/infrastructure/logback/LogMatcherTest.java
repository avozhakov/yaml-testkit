package com.yamltestkit.infrastructure.logback;

import com.yamltestkit.domain.model.LogExpectation.LogLevel;
import com.yamltestkit.domain.model.LogExpectation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LogMatcherTest {

    private static final String LOADER = "com.example.FeedLoader";
    private static final LogEvent FEED_42 = new LogEvent(LOADER, LogLevel.WARN, "unmappable feed 42");
    private static final LogEvent FEED_43 = new LogEvent(LOADER, LogLevel.WARN, "unmappable feed 43");
    private static final LogEvent INFO_LOADED = new LogEvent(LOADER, LogLevel.INFO, "loaded 2 feeds");
    private static final LogEvent CHILD_WARN = new LogEvent(LOADER + ".Rows", LogLevel.WARN, "row feed 42 skipped");
    private static final LogEvent SIBLING_WARN = new LogEvent(LOADER + "Other", LogLevel.WARN, "feed 42");
    private static final String FILTERED = "WARN events of com.example.FeedLoader are never logged: the logger's"
            + " effective level is ERROR; set `logging.level.com.example.FeedLoader: WARN` in the `properties` of"
            + " _context.yaml, or lower the level in the logging configuration";

    @Test
    void failure_whenAMatchingEventWasLogged_shouldPass() {
        Optional<String> result = LogMatcher.failure(warn("feed 42", null, false), List.of(INFO_LOADED, FEED_42),
                null);

        assertThat(result).isEmpty();
    }

    @Test
    void failure_whenADescendantLoggedTheEvent_shouldMatchItByLoggerPrefix() {
        Optional<String> result = LogMatcher.failure(warn("row feed", 1, false), List.of(CHILD_WARN), null);

        assertThat(result).isEmpty();
    }

    @Test
    void failure_whenOnlyALoggerWithTheSameNamePrefixLogged_shouldNotMatchIt() {
        Optional<String> result = LogMatcher.failure(warn("feed 42", null, false), List.of(SIBLING_WARN), null);

        assertThat(result).contains("expected WARN event of com.example.FeedLoader containing \"feed 42\" but none"
                + " was logged; captured events of com.example.FeedLoader: none");
    }

    @Test
    void failure_whenNoEventMatches_shouldListTheCapturedEventsOfTheLogger() {
        Optional<String> result = LogMatcher.failure(warn("feed 99", null, false), List.of(INFO_LOADED, FEED_42),
                null);

        assertThat(result).contains("expected WARN event of com.example.FeedLoader containing \"feed 99\" but none"
                + " was logged; captured events of com.example.FeedLoader: [INFO com.example.FeedLoader: loaded 2"
                + " feeds, WARN com.example.FeedLoader: unmappable feed 42]");
    }

    @Test
    void failure_whenTheLevelDiffers_shouldNotMatch() {
        Optional<String> result = LogMatcher.failure(new LogExpectation(LOADER, LogLevel.ERROR, null, null, false),
                List.of(FEED_42), null);

        assertThat(result).isPresent();
    }

    @Test
    void failure_whenTimesDiffers_shouldFailWithExpectedAndActualCounts() {
        Optional<String> result = LogMatcher.failure(warn("unmappable", 1, false), List.of(FEED_42, FEED_43), null);

        assertThat(result).hasValueSatisfying(failure -> assertThat(failure)
                .startsWith("expected times: 1 of WARN event of com.example.FeedLoader containing \"unmappable\","
                        + " actual: 2; captured events of com.example.FeedLoader: ["));
    }

    @Test
    void failure_whenAbsentAndAMatchingEventWasLogged_shouldFail() {
        Optional<String> result = LogMatcher.failure(warn("feed 42", null, true), List.of(FEED_42), null);

        assertThat(result).hasValueSatisfying(failure -> assertThat(failure)
                .startsWith("expected no WARN event of com.example.FeedLoader containing \"feed 42\" (absent) but 1"
                        + " were logged; "));
    }

    @Test
    void failure_whenAbsentAndNoMatchingEvent_shouldPass() {
        Optional<String> result = LogMatcher.failure(warn("feed 42", null, true), List.of(FEED_43), null);

        assertThat(result).isEmpty();
    }

    @Test
    void failure_whenTheLevelIsFilteredOutAndNothingWasLogged_shouldNameThePropertyToSet() {
        Optional<String> result = LogMatcher.failure(warn("feed 42", null, false), List.of(), "ERROR");

        assertThat(result).contains(FILTERED);
    }

    @Test
    void failure_whenTheLevelIsFilteredOutAndTheEntryExpectsNoEvent_shouldFailInsteadOfPassingVacuously() {
        Optional<String> absent = LogMatcher.failure(warn("feed 42", null, true), List.of(), "ERROR");
        Optional<String> zeroTimes = LogMatcher.failure(warn("feed 42", 0, false), List.of(), "ERROR");

        assertThat(absent).contains(FILTERED);
        assertThat(zeroTimes).contains(FILTERED);
    }

    @Test
    void failure_whenTheLevelIsFilteredForTheLoggerButADescendantLogged_shouldPass() {
        Optional<String> result = LogMatcher.failure(warn("row feed", null, false), List.of(CHILD_WARN), "ERROR");

        assertThat(result).isEmpty();
    }

    private static LogExpectation warn(String contains, Integer times, boolean absent) {
        return new LogExpectation(LOADER, LogLevel.WARN, contains, times, absent);
    }
}
