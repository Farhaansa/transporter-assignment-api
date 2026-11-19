package com.transporterassignment.solver;

import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Greedy heuristic. Its answers are never returned to clients directly: it supplies the exact search's first upper
 * bound and one of the local search's starting sets. The result may cost more than the minimum, and an empty result
 * does not prove that no covering selection exists (its cover phase is greedy set cover, which can strand lanes).
 *
 * <ol>
 *   <li>Cover: repeatedly add the transporter with the lowest average quote over the lanes it can still cover,
 *       until every lane is covered. Fails if a lane cannot be covered or more than {@code k} are needed.</li>
 *   <li>Improve: while fewer than {@code k} are chosen, add the transporter that lowers the total cost most;
 *       stop when no transporter lowers it.</li>
 *   <li>Balance: while fewer than {@code k} are chosen, add the first transporter (lowest id) that keeps the
 *       total cost unchanged and raises the matched usage; stop when none does.</li>
 * </ol>
 * Ties go to the lowest transporter id. Cover and improve take O(k * N * lanes * k); each balance round adds one
 * cost evaluation and one matching per candidate.
 */
@Component
public class GreedySolver implements AssignmentSolver {

    public static final String METHOD = "GREEDY";

    @Override
    public Optional<Solution> solve(Dataset data, int k) {
        QuoteMatrix matrix = QuoteMatrix.of(data);
        int[] chosen = chosenSet(matrix, k);
        return chosen == null ? Optional.empty() : Assigner.assign(matrix, chosen, METHOD);
    }

    /**
     * Runs cover, improve and balance, and returns the chosen transporter indexes (ascending, at most
     * {@code min(k, N)}), or null when the cover phase fails. Used as a starting point by the other solvers.
     */
    static int[] chosenSet(QuoteMatrix matrix, int k) {
        QuoteMatrix.requirePositive(k);
        int n = matrix.transporterCount();
        int limit = Math.min(k, n);
        boolean[] chosen = new boolean[n];

        int chosenCount = cover(matrix, chosen, limit);
        if (chosenCount < 0) {
            return null;
        }
        chosenCount = improve(matrix, chosen, chosenCount, limit);
        balance(matrix, chosen, chosenCount, limit);
        return indexesOf(chosen);
    }

    @Override
    public String method() {
        return METHOD;
    }

    /** Phase 1. Returns how many transporters were chosen, or -1 if covering failed within {@code limit}. */
    private static int cover(QuoteMatrix matrix, boolean[] chosen, int limit) {
        int lanes = matrix.laneCount();
        boolean[] covered = new boolean[lanes];
        int uncovered = lanes;
        int chosenCount = 0;
        while (uncovered > 0) {
            int bestT = -1;
            long bestSum = 0;
            long bestCount = 0;
            for (int t = 0; t < matrix.transporterCount(); t++) {
                if (chosen[t]) {
                    continue;
                }
                long sum = 0;
                long count = 0;
                for (int l = 0; l < lanes; l++) {
                    long q = matrix.quote[t][l];
                    if (!covered[l] && q != QuoteMatrix.NO_QUOTE) {
                        sum += q;
                        count++;
                    }
                }
                // Compare averages sum/count exactly as sum * bestCount < bestSum * count.
                if (count > 0 && (bestT < 0 || compareProducts(sum, bestCount, bestSum, count) < 0)) {
                    bestT = t;
                    bestSum = sum;
                    bestCount = count;
                }
            }
            if (bestT < 0 || ++chosenCount > limit) {
                return -1;
            }
            chosen[bestT] = true;
            for (int l = 0; l < lanes; l++) {
                if (!covered[l] && matrix.quote[bestT][l] != QuoteMatrix.NO_QUOTE) {
                    covered[l] = true;
                    uncovered--;
                }
            }
        }
        return chosenCount;
    }

    /** Phase 2: spend the remaining budget on transporters that lower the cost. Returns the new chosen count. */
    private static int improve(QuoteMatrix matrix, boolean[] chosen, int chosenCount, int limit) {
        long current = cost(matrix, chosen);
        while (chosenCount < limit) {
            int bestT = -1;
            long bestCost = current;
            for (int t = 0; t < matrix.transporterCount(); t++) {
                if (chosen[t]) {
                    continue;
                }
                chosen[t] = true;
                long candidate = cost(matrix, chosen);
                chosen[t] = false;
                if (candidate < bestCost) {
                    bestCost = candidate;
                    bestT = t;
                }
            }
            if (bestT < 0) {
                break;
            }
            chosen[bestT] = true;
            chosenCount++;
            current = bestCost;
        }
        return chosenCount;
    }

    /**
     * Phase 3: spend any remaining budget on transporters that cost nothing extra but take a lane of their own,
     * so equal quotes are spread over more transporters.
     */
    static void balance(QuoteMatrix matrix, boolean[] chosen, int chosenCount, int limit) {
        long current = cost(matrix, chosen);
        int usage = Assigner.maximumUsage(matrix, indexesOf(chosen));
        int usageCap = Math.min(limit, matrix.laneCount());
        while (chosenCount < limit && usage < usageCap) {
            int added = -1;
            for (int t = 0; t < matrix.transporterCount() && added < 0; t++) {
                if (chosen[t]) {
                    continue;
                }
                chosen[t] = true;
                if (cost(matrix, chosen) == current) {
                    int candidateUsage = Assigner.maximumUsage(matrix, indexesOf(chosen));
                    if (candidateUsage > usage) {
                        usage = candidateUsage;
                        added = t;
                    }
                }
                if (added < 0) {
                    chosen[t] = false;
                }
            }
            if (added < 0) {
                return;
            }
            chosenCount++;
        }
    }

    /** Total cost of a set that covers every lane. */
    private static long cost(QuoteMatrix matrix, boolean[] chosen) {
        int[] indexes = indexesOf(chosen);
        long total = 0;
        for (int l = 0; l < matrix.laneCount(); l++) {
            total += matrix.quote[Assigner.cheapest(matrix, indexes, l)][l];
        }
        return total;
    }

    static int[] indexesOf(boolean[] chosen) {
        int count = 0;
        for (boolean c : chosen) {
            if (c) {
                count++;
            }
        }
        int[] indexes = new int[count];
        int i = 0;
        for (int t = 0; t < chosen.length; t++) {
            if (chosen[t]) {
                indexes[i++] = t;
            }
        }
        return indexes;
    }

    /** Compares a*b with c*d for non-negative longs without overflow, using the full 128-bit products. */
    static int compareProducts(long a, long b, long c, long d) {
        long high1 = Math.multiplyHigh(a, b);
        long high2 = Math.multiplyHigh(c, d);
        if (high1 != high2) {
            return Long.compare(high1, high2);
        }
        return Long.compareUnsigned(a * b, c * d);
    }
}
