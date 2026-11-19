package com.transporterassignment.solver;

import com.transporterassignment.TestData;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.transporterassignment.TestData.transporter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExactSubsetSolverTest {

    private final ExactSubsetSolver solver = new ExactSubsetSolver();

    @Test
    @DisplayName("Test Case 1, k=3: minimum 60139 with T1, T4, T5")
    void case1WithThreeTransporters() {
        Solution solution = solver.solve(TestData.case1(), 3).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(60139);
        assertThat(solution.selected()).containsExactly(1L, 4L, 5L);
        assertThat(solution.laneToTransporter()).containsExactly(
                Map.entry(1L, 5L), Map.entry(2L, 1L), Map.entry(3L, 4L), Map.entry(4L, 4L), Map.entry(5L, 4L));
        assertThat(solution.method()).isEqualTo("EXACT");
    }

    @Test
    @DisplayName("Test Case 1 restricted to the sample's set T3, T4, T6 costs 64677")
    void case1SampleSelection() {
        Dataset onlySampleSet = TestData.case1Restricted(Set.of(3L, 4L, 6L));

        Solution solution = solver.solve(onlySampleSet, 3).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(64677);
        assertThat(solution.selected()).containsExactly(3L, 4L, 6L);
    }

    @Test
    @DisplayName("The assignment listed in the specification's sample output adds up to 69001")
    void specificationSampleAssignmentCost() {
        // Lane 1 -> T4, lane 2 -> T6, lane 3 -> T2, lane 4 -> T4, lane 5 -> T3 (T2 is not in its selected list).
        long[][] q = TestData.CASE_1_QUOTES;
        long listed = q[3][0] + q[5][1] + q[1][2] + q[3][3] + q[2][4];

        assertThat(listed).isEqualTo(69001);
        assertThat(solver.solve(TestData.case1(), 3).orElseThrow().totalCost()).isLessThan(listed);
    }

    @Test
    @DisplayName("Test Case 1 with k=2 costs 62938")
    void case1WithTwoTransporters() {
        assertThat(solver.solve(TestData.case1(), 2).orElseThrow().totalCost()).isEqualTo(62938);
    }

    @Test
    @DisplayName("Test Case 1 with k=7 and k>N both reach the unconstrained minimum 59896")
    void case1WithAllTransporters() {
        Solution all = solver.solve(TestData.case1(), 7).orElseThrow();
        Solution moreThanN = solver.solve(TestData.case1(), 50).orElseThrow();

        assertThat(all.totalCost()).isEqualTo(59896);
        assertThat(all.selected()).containsExactly(1L, 3L, 4L, 5L);
        assertThat(moreThanN).isEqualTo(all);
    }

    @Test
    @DisplayName("Test Case 1 with k=1 picks the cheapest single transporter (T4, 96940)")
    void case1WithOneTransporter() {
        Solution solution = solver.solve(TestData.case1(), 1).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(14400 + 44514 + 14316 + 10678 + 13032);
        assertThat(solution.totalCost()).isEqualTo(96940);
        assertThat(solution.selected()).containsExactly(4L);
    }

    @Test
    @DisplayName("Test Case 2, k=3: 7500 with X, Y, Z")
    void case2WithThree() {
        Solution solution = solver.solve(TestData.case2(), 3).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(7500);
        assertThat(solution.selected()).containsExactly(1L, 2L, 3L);
        assertThat(solution.laneToTransporter()).containsExactly(
                Map.entry(1L, 1L), Map.entry(2L, 1L), Map.entry(3L, 2L), Map.entry(4L, 2L), Map.entry(5L, 3L));
    }

    @Test
    @DisplayName("Test Case 2, k=2: no two transporters cover all lanes")
    void case2WithTwoIsInfeasible() {
        assertThat(solver.solve(TestData.case2(), 2)).isEmpty();
    }

    @Test
    @DisplayName("Test Case 2, k=4: 7450, W takes lane 4")
    void case2WithFour() {
        Solution solution = solver.solve(TestData.case2(), 4).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(7450);
        assertThat(solution.selected()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(solution.laneToTransporter()).containsEntry(4L, 4L).containsEntry(1L, 1L);
    }

    @Test
    @DisplayName("Equal cost: the answer that uses more transporters wins")
    void tieBreakPrefersMoreTransportersUsed() {
        // T1 alone covers both lanes for 20; T2 + T3 also cost 20 and use two transporters.
        Dataset data = TestData.of(2,
                transporter(1, Map.of(1L, 10L, 2L, 10L)),
                transporter(2, Map.of(1L, 10L)),
                transporter(3, Map.of(2L, 10L)));

        Solution solution = solver.solve(data, 2).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(20);
        assertThat(solution.selected()).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("Equal cost and equal count: the lexicographically smallest ids win")
    void tieBreakPrefersLowestIds() {
        Dataset data = TestData.of(2,
                transporter(4, Map.of(2L, 5L)),
                transporter(3, Map.of(1L, 5L)),
                transporter(2, Map.of(2L, 5L)),
                transporter(1, Map.of(1L, 5L)));

        Solution solution = solver.solve(data, 2).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(10);
        assertThat(solution.selected()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Equal lane quotes inside the chosen set go to the lowest transporter id")
    void laneTieGoesToLowestId() {
        Dataset data = TestData.of(1,
                transporter(9, Map.of(1L, 7L)),
                transporter(3, Map.of(1L, 7L)));

        Solution solution = solver.solve(data, 2).orElseThrow();

        assertThat(solution.laneToTransporter()).containsExactly(Map.entry(1L, 3L));
        assertThat(solution.selected()).containsExactly(3L);
    }

    @Test
    @DisplayName("Candidate sets have exactly min(k, N) members; equal lane quotes go to the lowest id")
    void candidateSetsHaveSizeMinKN() {
        // T1 quotes 1 everywhere. T2 + T3 also reach the minimum cost 4 without T1.
        Dataset data = TestData.of(4,
                transporter(1, Map.of(1L, 1L, 2L, 1L, 3L, 1L, 4L, 1L)),
                transporter(2, Map.of(2L, 1L, 3L, 2L, 4L, 1L)),
                transporter(3, Map.of(1L, 1L, 2L, 2L, 3L, 1L, 4L, 3L)),
                transporter(4, Map.of(1L, 3L, 2L, 2L, 3L, 2L, 4L, 2L)),
                transporter(5, Map.of(1L, 3L, 2L, 1L, 3L, 3L, 4L, 3L)));

        // k=2: {2,3} costs 4 and uses two transporters, beating {1,x} which uses only T1.
        assertThat(solver.solve(data, 2).orElseThrow().selected()).isEqualTo(List.of(2L, 3L));
        // k=5: the only candidate is all five; T1 wins every tied lane, so only T1 is used.
        Solution all = solver.solve(data, 5).orElseThrow();
        assertThat(all.totalCost()).isEqualTo(4);
        assertThat(all.selected()).isEqualTo(List.of(1L));
    }

    @Test
    void rejectsNonPositiveK() {
        assertThatThrownBy(() -> solver.solve(TestData.case1(), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uncoverableLaneIsInfeasible() {
        Dataset data = TestData.of(2, transporter(1, Map.of(1L, 3L)));

        Optional<Solution> solution = solver.solve(data, 1);

        assertThat(solution).isEmpty();
    }
}
