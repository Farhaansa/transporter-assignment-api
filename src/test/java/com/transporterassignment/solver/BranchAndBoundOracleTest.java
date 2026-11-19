package com.transporterassignment.solver;

import com.transporterassignment.TestData;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The branch-and-bound search must return exactly what the old plain enumeration ({@link EnumerationOracle})
 * returns: same cost, same usage, same transporter ids, same lane mapping.
 */
class BranchAndBoundOracleTest {

    private static final long SEED = 20_261_120L;

    private final ExactSubsetSolver branchAndBound = new ExactSubsetSolver();
    private final EnumerationOracle oracle = new EnumerationOracle();

    @Test
    @DisplayName("Matches the enumeration oracle on 900 random small instances (dense, tie-heavy, sparse)")
    void matchesOracleOnRandomInstances() {
        Random random = new Random(SEED);
        int feasible = 0;
        int multiUsage = 0;
        int infeasible = 0;
        for (int i = 0; i < 900; i++) {
            Dataset data = switch (i % 3) {
                // Dense with distinct-ish prices.
                case 0 -> RandomInstances.random(random, 1 + random.nextInt(6), 1 + random.nextInt(9), 0.8, 100);
                // Tie-heavy: prices 1..3, so equal quotes and equal-cost sets are common.
                case 1 -> RandomInstances.random(random, 1 + random.nextInt(6), 1 + random.nextInt(9), 0.7, 3);
                // Sparse: many missing quotes, some lanes unquoted, many infeasible k.
                default -> RandomInstances.random(random, 1 + random.nextInt(6), 1 + random.nextInt(9), 0.35, 20);
            };
            int k = 1 + random.nextInt(data.transporters().size() + 1);

            Optional<Solution> expected = oracle.solve(data, k);
            Optional<Solution> actual = branchAndBound.solve(data, k);

            assertThat(actual.isPresent()).as("feasibility, case %d", i).isEqualTo(expected.isPresent());
            if (expected.isPresent()) {
                feasible++;
                assertThat(actual.get().totalCost()).as("cost, case %d", i).isEqualTo(expected.get().totalCost());
                assertThat(actual.get().selected()).as("ids, case %d", i).isEqualTo(expected.get().selected());
                assertThat(actual.get().laneToTransporter()).as("lanes, case %d", i)
                        .isEqualTo(expected.get().laneToTransporter());
                if (expected.get().selected().size() > 1) {
                    multiUsage++;
                }
            } else {
                infeasible++;
            }
        }
        // The generator must exercise every branch of the comparison.
        assertThat(feasible).isGreaterThan(400);
        assertThat(multiUsage).isGreaterThan(200);
        assertThat(infeasible).isGreaterThan(50);
    }

    @Test
    @DisplayName("Sample data: same answers as the oracle for every k")
    void matchesOracleOnSampleData() {
        for (Dataset data : new Dataset[]{TestData.case1(), TestData.case2()}) {
            for (int k = 1; k <= data.transporters().size() + 1; k++) {
                assertThat(branchAndBound.solve(data, k)).as("k=%d", k).isEqualTo(oracle.solve(data, k));
            }
        }
    }

    @Test
    @DisplayName("Beyond the old 200,000-set cap: C(40, 5) = 658,008 sets, same answer, far fewer nodes")
    void reachesBeyondTheOldCap() {
        Dataset data = RandomInstances.random(new Random(SEED + 7), 8, 40, 0.9, 1000);
        QuoteMatrix matrix = QuoteMatrix.of(data);

        ExactSearch search = branchAndBound.search(matrix, 5, SearchBudget.UNLIMITED);

        assertThat(search.status()).isEqualTo(ExactSearch.Status.OPTIMAL);
        assertThat(Assigner.assign(matrix, search.bestSet(), ExactSubsetSolver.METHOD))
                .isEqualTo(oracle.solve(data, 5));
        assertThat(search.nodes()).isLessThan(Combinatorics.combinations(40, 5) / 10);
    }

    @Test
    @DisplayName("A node budget stops the search with the best feasible set known, without claiming optimality")
    void nodeBudgetStopsWithIncumbent() {
        QuoteMatrix matrix = QuoteMatrix.of(TestData.case1());

        ExactSearch search = branchAndBound.search(matrix, 3, new SearchBudget(2, 10_000));

        assertThat(search.status()).isEqualTo(ExactSearch.Status.NODE_BUDGET_EXHAUSTED);
        assertThat(search.status().completed()).isFalse();
        assertThat(search.bestSet()).hasSize(3);
        assertThat(search.bestCost()).isEqualTo(matrix.costOf(search.bestSet()));
    }

    @Test
    @DisplayName("Proven infeasibility needs a completed search")
    void infeasibleOnlyWhenCompleted() {
        QuoteMatrix matrix = QuoteMatrix.of(TestData.case2());

        assertThat(branchAndBound.search(matrix, 2, SearchBudget.UNLIMITED).status())
                .isEqualTo(ExactSearch.Status.INFEASIBLE);
        ExactSearch stopped = branchAndBound.search(matrix, 2, new SearchBudget(1, 10_000));
        assertThat(stopped.status()).isEqualTo(ExactSearch.Status.NODE_BUDGET_EXHAUSTED);
        assertThat(stopped.bestSet()).isNull();
    }
}
