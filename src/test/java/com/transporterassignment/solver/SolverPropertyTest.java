package com.transporterassignment.solver;

import com.transporterassignment.TestData;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import com.transporterassignment.model.Transporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Seeded random datasets: the exact solver must match brute force and never lose to greedy. */
class SolverPropertyTest {

    private static final long SEED = 20_251_119L;
    private static final int CASES = 500;

    private final ExactSubsetSolver exact = new ExactSubsetSolver();
    private final GreedySolver greedy = new GreedySolver();

    @Test
    @DisplayName("Exact cost <= greedy cost whenever greedy finds a solution")
    void exactNeverWorseThanGreedy() {
        Random random = new Random(SEED);
        int greedyFeasible = 0;
        for (int i = 0; i < CASES; i++) {
            Dataset data = randomDataset(random);
            int k = 1 + random.nextInt(data.transporters().size() + 1);

            Optional<Solution> greedyResult = greedy.solve(data, k);
            Optional<Solution> exactResult = exact.solve(data, k);

            if (greedyResult.isPresent()) {
                greedyFeasible++;
                assertThat(exactResult).as("exact must be feasible when greedy is (case %d)", i).isPresent();
                assertThat(exactResult.get().totalCost()).isLessThanOrEqualTo(greedyResult.get().totalCost());
            }
        }
        assertThat(greedyFeasible).as("the generator should produce many feasible cases").isGreaterThan(CASES / 4);
    }

    @Test
    @DisplayName("Exact cost equals a brute-force minimum over every subset of size 1..k")
    void exactMatchesBruteForce() {
        Random random = new Random(SEED + 1);
        for (int i = 0; i < CASES; i++) {
            Dataset data = randomDataset(random);
            int k = 1 + random.nextInt(data.transporters().size() + 1);

            Optional<Long> expected = bruteForceMinimum(data, k);
            Optional<Solution> actual = exact.solve(data, k);

            assertThat(actual.map(Solution::totalCost)).as("case %d", i).isEqualTo(expected);
            actual.ifPresent(s -> assertThat(s.selected().size()).isLessThanOrEqualTo(k));
        }
    }

    @Test
    @DisplayName("Exact usage equals the brute-force maximum usage among minimum-cost assignments")
    void exactUsageMatchesBruteForce() {
        Random random = new Random(SEED + 2);
        int withTies = 0;
        for (int i = 0; i < CASES; i++) {
            // Quotes from 1..3 make equal quotes common, which is where usage can differ.
            Dataset data = randomDataset(random, 1 + random.nextInt(5), 1 + random.nextInt(6), 3);
            int k = 1 + random.nextInt(data.transporters().size() + 1);

            Optional<long[]> expected = bruteForceCostAndUsage(data, k);
            Optional<Solution> actual = exact.solve(data, k);

            assertThat(actual.isPresent()).as("feasibility, case %d", i).isEqualTo(expected.isPresent());
            if (actual.isPresent()) {
                assertThat(actual.get().totalCost()).as("cost, case %d", i).isEqualTo(expected.get()[0]);
                assertThat((long) actual.get().selected().size()).as("usage, case %d", i).isEqualTo(expected.get()[1]);
                if (expected.get()[1] > 1) {
                    withTies++;
                }
            }
        }
        assertThat(withTies).as("the generator should produce multi-transporter answers").isGreaterThan(CASES / 4);
    }

    private static Dataset randomDataset(Random random) {
        return randomDataset(random, 2 + random.nextInt(5), 2 + random.nextInt(7), 50);
    }

    private static Dataset randomDataset(Random random, int lanes, int transporters, int maxQuote) {
        List<Transporter> list = new ArrayList<>();
        for (int t = 1; t <= transporters; t++) {
            Map<Long, Long> quotes = new HashMap<>();
            for (long lane = 1; lane <= lanes; lane++) {
                if (random.nextDouble() < 0.6) {
                    quotes.put(lane, 1L + random.nextInt(maxQuote));
                }
            }
            list.add(TestData.transporter(t, quotes));
        }
        return TestData.of(lanes, list.toArray(Transporter[]::new));
    }

    /**
     * Tries every way to give each lane to a quoting transporter, keeps those using at most k distinct transporters,
     * and returns {minimum cost, maximum usage at that cost}. Independent of the solver's set enumeration.
     */
    private static Optional<long[]> bruteForceCostAndUsage(Dataset data, int k) {
        List<Transporter> all = data.transporters();
        int lanes = data.lanes().size();
        long[] best = null;
        int[] pick = new int[lanes];
        int[] radix = new int[lanes];
        for (int l = 0; l < lanes; l++) {
            radix[l] = all.size();
        }
        outer:
        while (true) {
            long cost = 0;
            boolean valid = true;
            boolean[] used = new boolean[all.size()];
            int usage = 0;
            for (int l = 0; l < lanes && valid; l++) {
                Long quote = all.get(pick[l]).quotes().get(data.lanes().get(l).id());
                if (quote == null) {
                    valid = false;
                } else {
                    cost += quote;
                    if (!used[pick[l]]) {
                        used[pick[l]] = true;
                        usage++;
                    }
                }
            }
            if (valid && usage <= k
                    && (best == null || cost < best[0] || (cost == best[0] && usage > best[1]))) {
                best = new long[]{cost, usage};
            }
            for (int l = 0; l < lanes; l++) {
                if (++pick[l] < radix[l]) {
                    continue outer;
                }
                pick[l] = 0;
            }
            break;
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Long> bruteForceMinimum(Dataset data, int k) {
        List<Transporter> all = data.transporters();
        Long best = null;
        for (int mask = 1; mask < (1 << all.size()); mask++) {
            if (Integer.bitCount(mask) > k) {
                continue;
            }
            long cost = 0;
            boolean covered = true;
            for (var lane : data.lanes()) {
                long cheapest = Long.MAX_VALUE;
                for (int t = 0; t < all.size(); t++) {
                    Long quote = all.get(t).quotes().get(lane.id());
                    if ((mask & (1 << t)) != 0 && quote != null) {
                        cheapest = Math.min(cheapest, quote);
                    }
                }
                if (cheapest == Long.MAX_VALUE) {
                    covered = false;
                    break;
                }
                cost += cheapest;
            }
            if (covered && (best == null || cost < best)) {
                best = cost;
            }
        }
        return Optional.ofNullable(best);
    }
}
