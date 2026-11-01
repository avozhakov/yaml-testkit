package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * What a stubbed method does: exactly one of {@code returns}, {@code returnsArg}, {@code throws}, {@code answers}.
 */
public sealed interface StubAnswer
        permits StubAnswer.Returns, StubAnswer.ReturnsArg, StubAnswer.Throws, StubAnswer.Consecutive {

    record Returns(JsonNode value) implements StubAnswer {
    }

    /** Returns the argument at {@code index} itself, or wrapped into the method's return type. */
    record ReturnsArg(int index) implements StubAnswer {
    }

    record Throws(ThrowSpec exception) implements StubAnswer {
    }

    /** {@code answers}: one answer per consecutive call, the last one repeating; each is a Returns or a Throws. */
    record Consecutive(List<StubAnswer> answers) implements StubAnswer {

        public Consecutive {
            answers = List.copyOf(answers);
        }
    }
}
