package com.transporterassignment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LowerBoundGapTest {

    @Test
    @DisplayName("Percent gap is rounded up, so the stated bound is never too optimistic")
    void percentRoundsUp() {
        // (12345 - 11834) / 11834 = 4.318... % -> 4.4 %
        assertThat(AssignmentService.lowerBoundGap(12345, 11834))
                .startsWith("Total cost 12345 is within 4.4% of the theoretical lower bound 11834")
                .contains("does not measure that distance");
    }

    @Test
    void equalToBound() {
        assertThat(AssignmentService.lowerBoundGap(500, 500))
                .startsWith("Total cost 500 matches the theoretical lower bound 500")
                .contains("no cheaper answer exists");
    }

    @Test
    @DisplayName("A zero bound reports the absolute difference instead of a percentage")
    void zeroBound() {
        assertThat(AssignmentService.lowerBoundGap(40, 0))
                .startsWith("Total cost 40 is 40 above the theoretical lower bound 0")
                .doesNotContain("%");
    }
}
