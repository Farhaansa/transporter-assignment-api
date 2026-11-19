package com.transporterassignment.solver;

import com.transporterassignment.TestData;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.transporterassignment.TestData.transporter;
import static org.assertj.core.api.Assertions.assertThat;

class GreedySolverTest {

    private final GreedySolver solver = new GreedySolver();

    @Test
    @DisplayName("Covers every lane with quoting transporters and respects k")
    void coversAllLanesWithinK() {
        Dataset data = TestData.case1();

        for (int k = 1; k <= 7; k++) {
            Solution solution = solver.solve(data, k).orElseThrow();

            assertThat(solution.laneToTransporter()).containsOnlyKeys(1L, 2L, 3L, 4L, 5L);
            assertThat(solution.selected()).hasSizeLessThanOrEqualTo(k);
            assertThat(solution.method()).isEqualTo("GREEDY");
            solution.laneToTransporter().forEach((lane, transporterId) ->
                    assertThat(quotes(data, transporterId)).containsKey(lane));
            assertThat(solution.totalCost()).isEqualTo(sumOfAssignedQuotes(data, solution));
        }
    }

    @Test
    @DisplayName("Returns empty when more than k transporters are needed")
    void infeasibleWhenKTooSmall() {
        assertThat(solver.solve(TestData.case2(), 2)).isEmpty();
    }

    @Test
    @DisplayName("Returns empty when a lane has no quote at all")
    void infeasibleWhenLaneUnquoted() {
        Dataset data = TestData.of(2, transporter(1, Map.of(1L, 5L)), transporter(2, Map.of(1L, 4L)));

        assertThat(solver.solve(data, 2)).isEmpty();
    }

    @Test
    @DisplayName("Test Case 2 with k=4: phase 2 adds W because it lowers the cost")
    void case2PhaseTwoAddsCheaperTransporter() {
        Solution solution = solver.solve(TestData.case2(), 4).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(7450);
        assertThat(solution.selected()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("Balance: an equal-cost extra transporter is added and used, at unchanged cost")
    void balanceAddsEqualCostTransporter() {
        // Cover picks T1 (covers both lanes for 20). Improve adds nothing (no one lowers the cost).
        // T2 quotes lane 2 at the same 10, so balance adds it and the matching gives it lane 2.
        Dataset data = TestData.of(2,
                transporter(1, Map.of(1L, 10L, 2L, 10L)),
                transporter(2, Map.of(2L, 10L)),
                transporter(3, Map.of(1L, 50L)),
                transporter(4, Map.of(1L, 50L)));

        Solution solution = solver.solve(data, 2).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(20);
        assertThat(solution.selected()).containsExactly(1L, 2L);
        assertThat(solution.laneToTransporter()).containsExactly(Map.entry(1L, 1L), Map.entry(2L, 2L));
    }

    @Test
    @DisplayName("Balance never trades cost for usage")
    void balanceKeepsCost() {
        Dataset data = TestData.of(2,
                transporter(1, Map.of(1L, 10L, 2L, 10L)),
                transporter(2, Map.of(1L, 11L, 2L, 11L)));

        Solution solution = solver.solve(data, 2).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(20);
        assertThat(solution.selected()).containsExactly(1L);
    }

    private static Map<Long, Long> quotes(Dataset data, long transporterId) {
        return data.transporters().stream().filter(t -> t.id() == transporterId).findFirst().orElseThrow().quotes();
    }

    private static long sumOfAssignedQuotes(Dataset data, Solution solution) {
        return solution.laneToTransporter().entrySet().stream()
                .mapToLong(e -> quotes(data, e.getValue()).get(e.getKey()))
                .sum();
    }
}
