package com.transporterassignment.solver;

import com.transporterassignment.model.Solution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Turns a chosen set of transporters into a full assignment: each lane goes to its cheapest quoting
 * transporter in the set, and equal quotes go to the lowest transporter id. Shared by all solvers so the
 * final assignment rule lives in one place.
 */
final class Assigner {

    private Assigner() {
    }

    /**
     * @param chosen transporter indexes in ascending order
     * @return the assignment, or empty if some lane has no quote from the chosen set
     */
    static Optional<Solution> assign(QuoteMatrix matrix, int[] chosen, String method) {
        Map<Long, Long> laneToTransporter = new TreeMap<>();
        boolean[] used = new boolean[matrix.transporterCount()];
        long total = 0;
        for (int l = 0; l < matrix.laneCount(); l++) {
            int winner = cheapest(matrix, chosen, l);
            if (winner < 0) {
                return Optional.empty();
            }
            used[winner] = true;
            total = Math.addExact(total, matrix.quote[winner][l]);
            laneToTransporter.put(matrix.laneIds[l], matrix.transporterIds[winner]);
        }
        List<Long> selected = new ArrayList<>();
        for (int t = 0; t < used.length; t++) {
            if (used[t]) {
                selected.add(matrix.transporterIds[t]);
            }
        }
        return Optional.of(new Solution(total, laneToTransporter, selected, method));
    }

    /** Index of the cheapest quoting transporter for lane {@code l}, lowest index on ties; -1 if none quotes it. */
    static int cheapest(QuoteMatrix matrix, int[] chosen, int l) {
        int winner = -1;
        long best = Long.MAX_VALUE;
        for (int t : chosen) {
            long q = matrix.quote[t][l];
            if (q != QuoteMatrix.NO_QUOTE && q < best) {
                best = q;
                winner = t;
            }
        }
        return winner;
    }
}
