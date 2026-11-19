package com.transporterassignment.solver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CombinatoricsTest {

    @Test
    void smallValues() {
        assertThat(Combinatorics.combinations(7, 3)).isEqualTo(35);
        assertThat(Combinatorics.combinations(5, 5)).isEqualTo(1);
        assertThat(Combinatorics.combinations(5, 0)).isEqualTo(1);
        assertThat(Combinatorics.combinations(40, 20)).isEqualTo(137_846_528_820L);
    }

    @Test
    void kOutsideRangeIsZero() {
        assertThat(Combinatorics.combinations(3, 4)).isZero();
        assertThat(Combinatorics.combinations(3, -1)).isZero();
    }

    @Test
    void largestExactValueBeforeOverflow() {
        // C(66, 33) = 7219428434016265740 fits in a long; C(68, 34) does not.
        assertThat(Combinatorics.combinations(66, 33)).isEqualTo(7_219_428_434_016_265_740L);
        assertThat(Combinatorics.combinations(68, 34)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void hugeNSaturates() {
        assertThat(Combinatorics.combinations(1_000_000, 500_000)).isEqualTo(Long.MAX_VALUE);
        assertThat(Combinatorics.combinations(Integer.MAX_VALUE, 3)).isEqualTo(Long.MAX_VALUE);
    }
}
