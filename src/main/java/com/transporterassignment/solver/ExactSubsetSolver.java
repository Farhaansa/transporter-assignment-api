package com.transporterassignment.solver;

import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Exact branch-and-bound search over the sets of exactly {@code min(k, N)} transporters. When it completes, the
 * result is a proven minimum-cost assignment.
 *
 * <p>Priority (our documented reading of the brief): lowest total cost first; among minimum-cost answers, the
 * most transporters used (up to k); remaining ties broken by the lexicographically first set of transporter ids.
 * Sets of exactly {@code min(k, N)} are enough: adding a transporter never raises the cost, and any minimum-cost
 * assignment that uses a set U fits, at the same cost, inside every larger set containing U, so that set's matched
 * usage ({@link Assigner#maximumUsage}) is at least |U|.
 *
 * <p>Search:
 * <ol>
 *   <li>Upper bound first: the greedy solver's set (filled to size) gives an initial cost to beat. It is used only
 *       as a bound, never as the answer, so the tie rules below stay exact.</li>
 *   <li>Branch on transporters in ascending id order, include-first. This visits complete sets in lexicographic
 *       order, the same order as plain enumeration. Each node keeps the per-lane minimum quote of the included
 *       transporters, updated incrementally.</li>
 *   <li>Bound: the cost of the included transporters together with every not-yet-decided one (per lane, the smaller
 *       of the current minimum and the suffix minimum), O(lanes) per node. Prune if some lane stays uncovered, if
 *       the bound exceeds the best cost, or if it equals the best cost while the best set found so far already has
 *       the maximum possible usage {@code min(k, lanes)} (later sets can then win neither on usage nor on ids).</li>
 *   <li>At a complete set: a lower cost replaces the best; an equal cost replaces it only with strictly higher
 *       matched usage, which is computed lazily and skipped once usage is capped.</li>
 * </ol>
 *
 * <p>The problem is NP-hard and the worst case stays exponential. With a {@link SearchBudget}, the search stops at
 * the node or time limit and reports the best feasible set it knows, without claiming optimality. Memory is
 * O(N * lanes).
 */
@Component
public class ExactSubsetSolver implements AssignmentSolver {

    public static final String METHOD = "EXACT";

    private static final long INF = QuoteMatrix.INF;
    private static final int UNKNOWN = -1;
    private static final int CLOCK_CHECK_INTERVAL = 4096;

    /** Unbudgeted search: always runs to completion. */
    @Override
    public Optional<Solution> solve(Dataset data, int k) {
        QuoteMatrix matrix = QuoteMatrix.of(data);
        ExactSearch result = search(matrix, k, SearchBudget.UNLIMITED);
        return result.status() == ExactSearch.Status.OPTIMAL
                ? Assigner.assign(matrix, result.bestSet(), METHOD)
                : Optional.empty();
    }

    @Override
    public String method() {
        return METHOD;
    }

    /** Runs the branch-and-bound search within {@code budget}. */
    ExactSearch search(QuoteMatrix matrix, int k, SearchBudget budget) {
        QuoteMatrix.requirePositive(k);
        return new Search(matrix, Math.min(k, matrix.transporterCount()), budget).run(k);
    }

    /** State of one search; not shared between requests. */
    private static final class Search {

        private final QuoteMatrix matrix;
        private final int n;
        private final int lanes;
        private final int size;
        private final int usageCap;
        private final SearchBudget budget;

        /** {@code suffixMin[i][l]}: cheapest quote for lane l among transporters i..n-1 ({@link #INF} if none). */
        private final long[][] suffixMin;
        /** {@code minAt[c][l]}: cheapest quote for lane l among the first c included transporters. */
        private final long[][] minAt;

        private int[] best;
        private long bestCost = INF;
        private int bestUsage = UNKNOWN;

        Search(QuoteMatrix matrix, int size, SearchBudget budget) {
            this.matrix = matrix;
            this.n = matrix.transporterCount();
            this.lanes = matrix.laneCount();
            this.size = size;
            this.usageCap = Math.min(size, lanes);
            this.budget = budget;
            this.suffixMin = new long[n + 1][lanes];
            Arrays.fill(suffixMin[n], INF);
            for (int i = n - 1; i >= 0; i--) {
                for (int l = 0; l < lanes; l++) {
                    suffixMin[i][l] = Math.min(suffixMin[i + 1][l], matrix.quoteOrInf(i, l));
                }
            }
            this.minAt = new long[size + 1][lanes];
            Arrays.fill(minAt[0], INF);
        }

        ExactSearch run(int k) {
            int[] seed = seedSet(k);
            long seedCost = seed == null ? INF : matrix.costOf(seed);
            bestCost = seedCost; // an upper bound to beat; best stays null until the search finds its own set

            long start = System.nanoTime();
            long nodes = 0;
            int[] pos = new int[size];
            int c = 0;    // transporters included so far
            int next = 0; // next transporter index to consider for slot c

            while (true) {
                if (c == size) {
                    evaluateCompleteSet(pos);
                    if (c == 0) {
                        break;
                    }
                    next = pos[--c] + 1;
                    continue;
                }
                // Not enough transporters left for the open slots, or no completion of this prefix can win.
                if (next > n - (size - c) || prunable(bound(minAt[c], suffixMin[next]))) {
                    if (c == 0) {
                        break;
                    }
                    next = pos[--c] + 1;
                    continue;
                }

                nodes++;
                if (nodes > budget.maxNodes()) {
                    return stopped(ExactSearch.Status.NODE_BUDGET_EXHAUSTED, seed, seedCost, nodes - 1);
                }
                if (nodes % CLOCK_CHECK_INTERVAL == 0 && budget.timeUp(start)) {
                    return stopped(ExactSearch.Status.TIME_BUDGET_EXHAUSTED, seed, seedCost, nodes);
                }

                // Include transporter `next` in slot c.
                long[] parent = minAt[c];
                long[] child = minAt[c + 1];
                for (int l = 0; l < lanes; l++) {
                    child[l] = Math.min(parent[l], matrix.quoteOrInf(next, l));
                }
                // If this fills the last slot, the set is complete: its bound is its exact cost.
                long[] rest = c + 1 == size ? suffixMin[n] : suffixMin[next + 1];
                if (prunable(bound(child, rest))) {
                    next++; // skip including `next`; try the next candidate for this slot
                    continue;
                }
                pos[c++] = next++;
            }

            if (best != null) {
                return new ExactSearch(ExactSearch.Status.OPTIMAL, best, bestCost, nodes);
            }
            if (seed != null) {
                // Cannot happen: the seed set lies in the search tree and is never pruned while it is the bound.
                throw new IllegalStateException("search completed without reaching the greedy bound");
            }
            return new ExactSearch(ExactSearch.Status.INFEASIBLE, null, INF, nodes);
        }

        private void evaluateCompleteSet(int[] set) {
            long cost = 0;
            for (long q : minAt[size]) {
                if (q == INF) {
                    return;
                }
                cost += q;
            }
            if (cost < bestCost || (best == null && cost == bestCost)) {
                best = set.clone();
                bestCost = cost;
                bestUsage = UNKNOWN;
            } else if (cost == bestCost && !usageCapped()) {
                int usage = Assigner.maximumUsage(matrix, set);
                if (usage > bestUsage) {
                    best = set.clone();
                    bestUsage = usage;
                }
            }
        }

        /** Lower bound for every completion: per lane, the cheapest of the included and the still-available quotes. */
        private long bound(long[] included, long[] available) {
            long total = 0;
            for (int l = 0; l < lanes; l++) {
                long q = Math.min(included[l], available[l]);
                if (q == INF) {
                    return INF;
                }
                total += q;
            }
            return total;
        }

        private boolean prunable(long bound) {
            if (bound == INF || bound > bestCost) {
                return true;
            }
            // Equal cost: only a set found by this search (visited earlier, so lexicographically smaller) with
            // maximal usage makes the rest of the subtree unable to win.
            return bound == bestCost && best != null && usageCapped();
        }

        private boolean usageCapped() {
            if (bestUsage == UNKNOWN) {
                bestUsage = Assigner.maximumUsage(matrix, best);
            }
            return bestUsage >= usageCap;
        }

        /** Greedy set filled to exactly {@code size} with the lowest unused indexes, or null if greedy found no cover. */
        private int[] seedSet(int k) {
            int[] greedy = GreedySolver.chosenSet(matrix, k);
            return greedy == null ? null : fillTo(greedy, size, n);
        }

        private ExactSearch stopped(ExactSearch.Status status, int[] seed, long seedCost, long nodes) {
            // The search's own best is never worse than the seed bound, so prefer it when it exists.
            return best != null
                    ? new ExactSearch(status, best, bestCost, nodes)
                    : new ExactSearch(status, seed, seedCost, nodes);
        }
    }

    /** Adds the lowest indexes not yet in {@code set} until it has {@code size} members; returns them ascending. */
    static int[] fillTo(int[] set, int size, int n) {
        boolean[] member = new boolean[n];
        for (int t : set) {
            member[t] = true;
        }
        int count = set.length;
        for (int t = 0; t < n && count < size; t++) {
            if (!member[t]) {
                member[t] = true;
                count++;
            }
        }
        return GreedySolver.indexesOf(member);
    }
}
