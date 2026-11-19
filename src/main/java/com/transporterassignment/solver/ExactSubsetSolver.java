package com.transporterassignment.solver;

import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Exhaustive search over every set of exactly {@code min(k, N)} transporters.
 *
 * <p>Sets of that size are enough for the minimum cost: adding a transporter never raises the cost,
 * because each lane keeps its cheapest quote. Within a set each lane goes to its cheapest quote (lowest id on
 * ties). Among sets of equal cost the one whose assignment uses more transporters wins, and after that the
 * first set found in lexicographic order of transporter ids.
 *
 * <p>Time is O(C(N, k) * lanes * k); memory is O(N * lanes).
 */
@Component
public class ExactSubsetSolver implements AssignmentSolver {

    public static final String METHOD = "EXACT";

    @Override
    public Optional<Solution> solve(Dataset data, int k) {
        QuoteMatrix.requirePositive(k);
        QuoteMatrix matrix = QuoteMatrix.of(data);
        int n = matrix.transporterCount();
        int lanes = matrix.laneCount();
        int size = Math.min(k, n);

        int[] combo = firstCombination(size);
        int[] best = null;
        long bestCost = Long.MAX_VALUE;
        int bestUsed = -1;

        // Marks transporters that win a lane in the current set; a fresh stamp per set avoids clearing the array.
        int[] winnerStamp = new int[n];
        int stamp = 0;

        while (true) {
            stamp++;
            long cost = 0;
            int used = 0;
            boolean covered = true;
            for (int l = 0; l < lanes; l++) {
                int winner = Assigner.cheapest(matrix, combo, l);
                if (winner < 0) {
                    covered = false;
                    break;
                }
                cost += matrix.quote[winner][l];
                if (winnerStamp[winner] != stamp) {
                    winnerStamp[winner] = stamp;
                    used++;
                }
            }
            if (covered && (cost < bestCost || (cost == bestCost && used > bestUsed))) {
                bestCost = cost;
                bestUsed = used;
                best = combo.clone();
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
