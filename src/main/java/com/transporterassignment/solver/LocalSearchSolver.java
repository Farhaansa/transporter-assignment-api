package com.transporterassignment.solver;

import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Coverage-first local search: the fallback when the exact search runs out of budget. A heuristic: it may return a
 * cost above the minimum, and finding no cover does not prove that none exists.
 *
 * <p>Objective, compared lexicographically: first the number of lanes the set leaves uncovered, then total cost.
 * Putting coverage first lets the search repair sets that strand lanes, which a greedy set cover cannot do.
 *
 * <ol>
 *   <li>Starting sets, in this fixed order: sets passed in by the caller (the exact search's best feasible set),
 *       the greedy solver's set, the union of each lane's globally cheapest transporter, and a few pseudo-random
 *       sets from a fixed seed. Each is resized to exactly {@code min(k, N)} by adding (or, for the union, removing)
 *       the transporter that best improves the objective; adding never uncovers a lane.</li>
 *   <li>Moves: swap one chosen transporter for one unchosen one. Scan in ascending id order and apply the first
 *       swap that improves the objective; repeat until no swap improves it.</li>
 *   <li>Keep the best set across all starts. Every starting set is scored before any search runs, so the result is
 *       never worse than the best starting set.</li>
 *   <li>For a covering result, drop transporters that receive no lane (cost unchanged) and run the greedy solver's
 *       equal-cost balance step, so equal quotes are still spread over as many transporters as possible.</li>
 * </ol>
 *
 * <p>{@code solver.heuristic-time-ms} is checked between improvement passes, never in the middle of one, so the
 * set in hand is always complete and valid. Output is deterministic unless that time budget runs out.
 */
@Component
public class LocalSearchSolver implements AssignmentSolver {

    public static final String METHOD = "HEURISTIC";

    /** Fixed seed for the pseudo-random starting sets, so results are reproducible. */
    static final long RANDOM_SEED = 20_261_120L;
    static final int RANDOM_STARTS = 4;

    private static final long INF = QuoteMatrix.INF;

    private final long timeBudgetMs;

    public LocalSearchSolver(@Value("${solver.heuristic-time-ms:3000}") long timeBudgetMs) {
        if (timeBudgetMs < 1) {
            throw new IllegalArgumentException("solver.heuristic-time-ms must be at least 1");
        }
        this.timeBudgetMs = timeBudgetMs;
    }

    @Override
    public Optional<Solution> solve(Dataset data, int k) {
        QuoteMatrix matrix = QuoteMatrix.of(data);
        return finish(matrix, k, search(matrix, k, List.of()));
    }

    @Override
    public String method() {
        return METHOD;
    }

    public long timeBudgetMs() {
        return timeBudgetMs;
    }

    /**
     * Best set found and its objective.
     *
     * @param set       transporter indexes, ascending, exactly {@code min(k, N)} of them
     * @param uncovered lanes with no quote from the set
     * @param cost      sum of the cheapest quote of every covered lane
     */
    record Outcome(int[] set, int uncovered, long cost) {

        boolean covers() {
            return uncovered == 0;
        }

        boolean betterThan(Outcome other) {
            return other == null || compare(uncovered, cost, other.uncovered, other.cost) < 0;
        }
    }

    /** Runs the search. {@code priorSeeds} are tried first, in order; each must already be a valid index set. */
    Outcome search(QuoteMatrix matrix, int k, List<int[]> priorSeeds) {
        QuoteMatrix.requirePositive(k);
        long start = System.nanoTime();
        int size = Math.min(k, matrix.transporterCount());

        List<int[]> starts = startingSets(matrix, k, size, priorSeeds);
        Outcome best = null;
        for (int[] set : starts) {
            Outcome scored = new Evaluator(matrix, set).outcome();
            if (scored.betterThan(best)) {
                best = scored;
            }
        }
        for (int[] set : starts) {
            if (SearchBudget.timeUp(start, timeBudgetMs)) {
                break;
            }
            Outcome improved = improve(matrix, set, start);
            if (improved.betterThan(best)) {
                best = improved;
            }
        }
        return best;
    }

    /** Turns a covering outcome into the final assignment; empty when the outcome leaves lanes uncovered. */
    Optional<Solution> finish(QuoteMatrix matrix, int k, Outcome outcome) {
        if (outcome == null || !outcome.covers()) {
            return Optional.empty();
        }
        Solution assigned = Assigner.assign(matrix, outcome.set(), METHOD).orElseThrow();
        // Transporters that get no lane can go without changing any lane's price; balance may then add better ones.
        boolean[] chosen = new boolean[matrix.transporterCount()];
        for (long id : assigned.selected()) {
            chosen[Arrays.binarySearch(matrix.transporterIds, id)] = true;
        }
        int limit = Math.min(k, matrix.transporterCount());
        GreedySolver.balance(matrix, chosen, assigned.selected().size(), limit);
        return Assigner.assign(matrix, GreedySolver.indexesOf(chosen), METHOD);
    }

    /** Swap search from one starting set until no single swap improves the objective or time runs out. */
    private Outcome improve(QuoteMatrix matrix, int[] startSet, long start) {
        int n = matrix.transporterCount();
        boolean[] member = new boolean[n];
        for (int t : startSet) {
            member[t] = true;
        }
        Evaluator current = new Evaluator(matrix, startSet);
        while (!SearchBudget.timeUp(start, timeBudgetMs)) {
            boolean improved = false;
            for (int out = 0; out < n && !improved; out++) {
                if (!member[out]) {
                    continue;
                }
                for (int in = 0; in < n; in++) {
                    if (member[in] || !current.swapImproves(out, in)) {
                        continue;
                    }
                    member[out] = false;
                    member[in] = true;
                    current = new Evaluator(matrix, GreedySolver.indexesOf(member));
                    improved = true;
                    break;
                }
            }
            if (!improved) {
                break;
            }
        }
        return current.outcome();
    }

    /** The starting sets in the order they are tried, each of exactly {@code size} transporters. */
    static List<int[]> startingSets(QuoteMatrix matrix, int k, int size, List<int[]> priorSeeds) {
        int n = matrix.transporterCount();
        List<int[]> starts = new ArrayList<>(priorSeeds);

        int[] greedy = GreedySolver.chosenSet(matrix, k);
        if (greedy != null) {
            starts.add(resize(matrix, greedy, size));
        }

        boolean[] cheapest = new boolean[n];
        int[] all = new int[n];
        for (int t = 0; t < n; t++) {
            all[t] = t;
        }
        for (int l = 0; l < matrix.laneCount(); l++) {
            int winner = Assigner.cheapest(matrix, all, l);
            if (winner >= 0) {
                cheapest[winner] = true;
            }
        }
        starts.add(resize(matrix, GreedySolver.indexesOf(cheapest), size));

        Random random = new Random(RANDOM_SEED);
        for (int s = 0; s < RANDOM_STARTS; s++) {
            int[] shuffled = all.clone();
            for (int i = 0; i < size; i++) {
                int j = i + random.nextInt(n - i);
                int swap = shuffled[i];
                shuffled[i] = shuffled[j];
                shuffled[j] = swap;
            }
            int[] chosen = Arrays.copyOf(shuffled, size);
            Arrays.sort(chosen);
            starts.add(chosen);
        }
        return starts;
    }

    /** Grows or shrinks a set to {@code size}, one best-objective addition or removal at a time (lowest id on ties). */
    static int[] resize(QuoteMatrix matrix, int[] set, int size) {
        int n = matrix.transporterCount();
        boolean[] member = new boolean[n];
        for (int t : set) {
            member[t] = true;
        }
        int count = set.length;
        while (count != size) {
            Evaluator current = new Evaluator(matrix, GreedySolver.indexesOf(member));
            boolean grow = count < size;
            int pick = -1;
            for (int t = 0; t < n; t++) {
                if (member[t] == grow) {
                    continue;
                }
                if (pick < 0 || current.better(grow ? -1 : t, grow ? t : -1, grow ? -1 : pick, grow ? pick : -1)) {
                    pick = t;
                }
            }
            member[pick] = grow;
            count += grow ? 1 : -1;
        }
        return GreedySolver.indexesOf(member);
    }

    /** Lexicographic comparison of (uncovered lanes, cost). */
    static int compare(int uncoveredA, long costA, int uncoveredB, long costB) {
        return uncoveredA != uncoveredB ? Integer.compare(uncoveredA, uncoveredB) : Long.compare(costA, costB);
    }

    /**
     * Per-lane best and second-best quote of one set, so a swap is scored in O(lanes) without rebuilding the set.
     */
    private static final class Evaluator {

        private final QuoteMatrix matrix;
        private final long[] best;
        private final int[] bestOwner;
        private final long[] second;
        private final int uncovered;
        private final long cost;
        private final int[] set;

        Evaluator(QuoteMatrix matrix, int[] set) {
            this.matrix = matrix;
            this.set = set;
            int lanes = matrix.laneCount();
            best = new long[lanes];
            bestOwner = new int[lanes];
            second = new long[lanes];
            int missing = 0;
            long total = 0;
            for (int l = 0; l < lanes; l++) {
                long b = INF;
                long s = INF;
                int owner = -1;
                for (int t : set) {
                    long q = matrix.quoteOrInf(t, l);
                    if (q < b) {
                        s = b;
                        b = q;
                        owner = t;
                    } else if (q < s) {
                        s = q;
                    }
                }
                best[l] = b;
                bestOwner[l] = owner;
                second[l] = s;
                if (b == INF) {
                    missing++;
                } else {
                    total += b;
                }
            }
            uncovered = missing;
            cost = total;
        }

        Outcome outcome() {
            return new Outcome(set, uncovered, cost);
        }

        /** True if removing {@code out} and adding {@code in} strictly improves (uncovered, cost). */
        boolean swapImproves(int out, int in) {
            int missing = 0;
            long total = 0;
            for (int l = 0; l < best.length; l++) {
                long q = Math.min(bestOwner[l] == out ? second[l] : best[l], matrix.quoteOrInf(in, l));
                if (q == INF) {
                    missing++;
                } else {
                    total += q;
                }
            }
            return compare(missing, total, uncovered, cost) < 0;
        }

        /** True if the change (removeA, addA) scores strictly better than (removeB, addB); -1 means none. */
        boolean better(int removeA, int addA, int removeB, int addB) {
            long[] a = score(removeA, addA);
            long[] b = score(removeB, addB);
            return compare((int) a[0], a[1], (int) b[0], b[1]) < 0;
        }

        private long[] score(int remove, int add) {
            long missing = 0;
            long total = 0;
            for (int l = 0; l < best.length; l++) {
                long q = bestOwner[l] == remove && remove >= 0 ? second[l] : best[l];
                if (add >= 0) {
                    q = Math.min(q, matrix.quoteOrInf(add, l));
                }
                if (q == INF) {
                    missing++;
                } else {
                    total += q;
                }
            }
            return new long[]{missing, total};
        }
    }
}
