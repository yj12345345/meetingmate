package com.meetingmate.app.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @Test
    void reusesWellFormedIncomingId() {
        assertThat(RequestIdFilter.resolveRequestId("k6-vu3-iter42")).isEqualTo("k6-vu3-iter42");
    }

    @Test
    void replacesMissingOrUnsafeIds() {
        assertThat(RequestIdFilter.resolveRequestId(null)).hasSize(16);
        assertThat(RequestIdFilter.resolveRequestId("short")).hasSize(16);
        // newline would let a client forge extra log lines
        String injected = RequestIdFilter.resolveRequestId("abcdefgh\nFAKE LOG LINE");
        assertThat(injected).doesNotContain("\n").hasSize(16);
        assertThat(RequestIdFilter.resolveRequestId("x".repeat(65))).hasSize(16);
    }
}
