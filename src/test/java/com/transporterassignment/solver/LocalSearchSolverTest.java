package com.transporterassignment.solver;

import com.transporterassignment.TestData;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static com.transporterassignment.TestData.transporter;
import static org.assertj.core.api.Assertions.assertThat;

class LocalSearchSolverTest {

    private static final long SEED = 20_261_121L;

    private final LocalSearchSolver localSearch = new LocalSearchSolver(3_000);
    private final EnumerationOracle oracle = new EnumerationOracle();

    @Test
    @DisplayName("Whenever a starting set covers, the result covers and costs no more than the best starting set")
    void neverWorseThanBestStartingSet() {
        Random random = new Random(SEED);
        int withFeasibleStart = 0;
        for (int i = 0; i < 400; i++) {
            Dataset data = RandomInstances.random(random, 1 + random.nextInt(8), 2 + random.nextInt(10),
                    i % 2 == 0 ? 0.4 : 0.8, i % 4 < 2 ? 3 : 100);
            int k = 1 + random.nextInt(data.transporters().size());
            QuoteMatrix matrix = QuoteMatrix.of(data);
            int size = Math.min(k, matrix.transporterCount());

            long bestStart = QuoteMatrix.INF;
            for (int[] start : LocalSearchSolver.startingSets(matrix, k, size, List.of())) {
                assertThat(start).as("starting set size, case %d", i).hasSize(size);
                bestStart = Math.min(bestStart, matrix.costOf(start));
            }
            LocalSearchSolver.Outcome outcome = localSearch.search(matrix, k, List.of());
            Optional<Solution> solution = localSearch.finish(matrix, k, outcome);

            if (bestStart != QuoteMatrix.INF) {
                withFeasibleStart++;
                assertThat(outcome.covers()).as("covers, case %d", i).isTrue();
                assertThat(outcome.cost()).as("cost, case %d", i).isLessThanOrEqualTo(bestStart);
                assertThat(solution).isPresent();
                assertThat(solution.get().totalCost()).as("balance keeps cost, case %d", i).isEqualTo(outcome.cost());
                assertThat(solution.get().selected().size()).isLessThanOrEqualTo(k);
            }
            // Never better than the proven optimum (sanity check of the cost arithmetic).
            oracle.solve(data, k).ifPresent(best ->
                    assertThat(solution.map(Solution::totalCost).orElse(Long.MAX_VALUE))
                            .isGreaterThanOrEqualTo(best.totalCost()));
        }
        assertThat(withFeasibleStart).isGreaterThan(150);
    }

    @Test
    @DisplayName("A prior seed is never lost: with the optimum as seed, the result is the optimum's cost")
    void priorSeedIsKept() {
        Random random = new Random(SEED + 1);
        for (int i = 0; i < 100; i++) {
            Dataset data = RandomInstances.random(random, 2 + random.nextInt(6), 3 + random.nextInt(8), 0.6, 50);
            int k = 1 + random.nextInt(data.transporters().size());
            QuoteMatrix matrix = QuoteMatrix.of(data);
            ExactSearch optimum = new ExactSubsetSolver().search(matrix, k, SearchBudget.UNLIMITED);
            if (optimum.status() != ExactSearch.Status.OPTIMAL) {
                continue;
            }

            LocalSearchSolver.Outcome outcome = localSearch.search(matrix, k, List.of(optimum.bestSet()));

            assertThat(outcome.covers()).isTrue();
            assertThat(outcome.cost()).as("case %d", i).isEqualTo(optimum.bestCost());
        }
    }

    @Test
    @DisplayName("Deterministic: the same input twice gives the same set and assignment")
    void deterministic() {
        Dataset data = RandomInstances.plantedCover(new Random(SEED + 2), 20, 200, 10, 0.15, 1000);

        Optional<Solution> first = localSearch.solve(data, 10);
        Optional<Solution> second = localSearch.solve(data, 10);

        assertThat(first).isPresent().isEqualTo(second);
    }

    @Test
    @DisplayName("Sparse planted covers: local search finds a cover where the greedy cover phase fails")
    void findsCoversGreedyMisses() {
        int greedyFailures = 0;
        int localSearchCovers = 0;
        int runs = 0;
        int[][] shapes = {{200, 20, 10}, {500, 30, 15}};
        double[] density = {0.15, 0.10};
        for (int shape = 0; shape < shapes.length; shape++) {
            for (int seed = 0; seed < 4; seed++) {
                int n = shapes[shape][0];
                int lanes = shapes[shape][1];
                int k = shapes[shape][2];
                Dataset data = RandomInstances.plantedCover(new Random(SEED + 100 * shape + seed), lanes, n, k,
                        density[shape], 1000);
                QuoteMatrix matrix = QuoteMatrix.of(data);
                runs++;
                if (GreedySolver.chosenSet(matrix, k) == null) {
                    greedyFailures++;
                }
                Optional<Solution> found = localSearch.solve(data, k);
                if (found.isPresent()) {
                    localSearchCovers++;
                    assertThat(found.get().laneToTransporter()).hasSize(lanes);
                    assertThat(found.get().selected().size()).isLessThanOrEqualTo(k);
                }
            }
        }
        // Measured on these seeded instances: the greedy cover phase fails on all 8, local search covers all 8.
        assertThat(greedyFailures).isEqualTo(runs);
        assertThat(localSearchCovers).isEqualTo(runs);
    }

    @Test
    @DisplayName("No cover exists: the outcome reports uncovered lanes and no solution")
    void noCoverGivesNoSolution() {
        QuoteMatrix matrix = QuoteMatrix.of(TestData.case2());

        LocalSearchSolver.Outcome outcome = localSearch.search(matrix, 2, List.of());

        assertThat(outcome.covers()).isFalse();
        assertThat(localSearch.finish(matrix, 2, outcome)).isEmpty();
    }

    @Test
    @DisplayName("Equal-cost balance still runs on the local search result")
    void balanceSpreadsEqualQuotes() {
        Dataset data = TestData.of(2,
                transporter(1, Map.of(1L, 10L, 2L, 10L)),
                transporter(2, Map.of(1L, 10L, 2L, 10L)),
                transporter(3, Map.of(1L, 50L)));

        Solution solution = localSearch.solve(data, 2).orElseThrow();

        assertThat(solution.totalCost()).isEqualTo(20);
        assertThat(solution.selected()).containsExactly(1L, 2L);
        assertThat(solution.method()).isEqualTo("HEURISTIC");
    }
}
