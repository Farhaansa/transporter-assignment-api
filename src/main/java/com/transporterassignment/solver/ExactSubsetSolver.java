package com.transporterassignment.solver;

import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Exhaustive search over every set of exactly {@code min(k, N)} transporters. Returns a proven minimum-cost
 * assignment.
 *
 * <p>Priority (our documented reading of the brief): lowest total cost first; among minimum-cost answers, the
 * most transporters used (up to k); remaining ties broken deterministically by lower transporter ids.
 *
 * <p>Sets of exactly {@code min(k, N)} are enough. For cost: adding a transporter never raises it, because each lane
 * keeps its cheapest quote. For usage: any minimum-cost assignment that uses a set U also fits inside every larger
 * set containing U at the same cost, so the matched usage of that larger set is at least |U|. A set's usage is the
 * maximum matching computed by {@link Assigner}. Among sets of equal cost the higher usage wins, then the first set
 * found in lexicographic order of transporter ids.
 *
 * <p>Time is O(C(N, k) * lanes * k) for the cost scan. The matching runs only for sets whose cost ties the best so
 * far, and stops being needed once the best already uses {@code min(k, lanes)} transporters; with many exact
 * ties it adds O(lanes * lanes * k) per tied set. Memory is O(N * lanes).
 */
@Component
public class ExactSubsetSolver implements AssignmentSolver {

    public static final String METHOD = "EXACT";

    private static final int UNKNOWN = -1;

    @Override
    public Optional<Solution> solve(Dataset data, int k) {
        QuoteMatrix.requirePositive(k);
        QuoteMatrix matrix = QuoteMatrix.of(data);
        int n = matrix.transporterCount();
        int lanes = matrix.laneCount();
        int size = Math.min(k, n);

        // No assignment can use more transporters than the set has, or more than there are lanes.
        int usageCap = Math.min(size, lanes);

        int[] combo = firstCombination(size);
        int[] best = null;
        long bestCost = Long.MAX_VALUE;
        int bestUsage = UNKNOWN;

        while (true) {
            long cost = 0;
            boolean covered = true;
            for (int l = 0; l < lanes; l++) {
                int winner = Assigner.cheapest(matrix, combo, l);
                if (winner < 0) {
                    covered = false;
                    break;
                }
                cost += matrix.quote[winner][l];
            }
            if (covered) {
                if (cost < bestCost) {
                    bestCost = cost;
                    bestUsage = UNKNOWN; // computed only if another set ties this cost
                    best = combo.clone();
                } else if (cost == bestCost) {
                    if (bestUsage == UNKNOWN) {
                        bestUsage = Assigner.maximumUsage(matrix, best);
                    }
                    if (bestUsage < usageCap) {
                        int usage = Assigner.maximumUsage(matrix, combo);
                        if (usage > bestUsage) {
                            bestUsage = usage;
                            best = combo.clone();
                        }
                    }
                }
            }
            if (!nextCombination(combo, n)) {
                break;
            }
        }
        return best == null ? Optional.empty() : Assigner.assign(matrix, best, METHOD);
    }

    @Override
    public String method() {
        return METHOD;
    }

    private static int[] firstCombination(int size) {
        int[] combo = new int[size];
        for (int i = 0; i < size; i++) {
            combo[i] = i;
        }
        return combo;
    }

    /** Advances {@code combo} to the next size-r combination of 0..n-1 in lexicographic order; false at the end. */
    static boolean nextCombination(int[] combo, int n) {
        int r = combo.length;
        int i = r - 1;
        while (i >= 0 && combo[i] == n - r + i) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        combo[i]++;
        for (int j = i + 1; j < r; j++) {
            combo[j] = combo[j - 1] + 1;
        }
        return true;
    }
}
