package com.transporterassignment.solver;

import com.transporterassignment.model.Solution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Turns a chosen set of transporters into a full assignment. Shared by all solvers so the final assignment rule
 * lives in one place.
 *
 * <p>Every lane pays its cheapest quote within the set, so the total cost is fixed by the set. When several
 * transporters tie for a lane's cheapest quote, the lanes are spread so that as many transporters as possible are
 * used: a maximum bipartite matching between lanes and their cheapest transporters (Kuhn's augmenting paths). A
 * transporter can only be used at minimum cost if it is cheapest for at least one lane of its own, so the matching
 * size is exactly the largest usage any minimum-cost assignment of this set can reach. Lanes left unmatched go to
 * their cheapest transporter with the lowest id.
 *
 * <p>Deterministic: lanes are matched in ascending lane id, each trying its cheapest transporters in ascending id.
 */
final class Assigner {

    private Assigner() {
    }

    /**
     * @param chosen transporter indexes in ascending order
     * @return the assignment, or empty if some lane has no quote from the chosen set
     */
    static Optional<Solution> assign(QuoteMatrix matrix, int[] chosen, String method) {
        int[][] candidates = cheapestCandidates(matrix, chosen);
        if (candidates == null) {
            return Optional.empty();
        }
        int[] laneToTransporter = maximumMatching(candidates, matrix.transporterCount());

        Map<Long, Long> assignment = new TreeMap<>();
        boolean[] used = new boolean[matrix.transporterCount()];
        long total = 0;
        for (int l = 0; l < matrix.laneCount(); l++) {
            int winner = laneToTransporter[l] >= 0 ? laneToTransporter[l] : candidates[l][0];
            used[winner] = true;
            total = Math.addExact(total, matrix.quote[winner][l]);
            assignment.put(matrix.laneIds[l], matrix.transporterIds[winner]);
        }
        List<Long> selected = new ArrayList<>();
        for (int t = 0; t < used.length; t++) {
            if (used[t]) {
                selected.add(matrix.transporterIds[t]);
            }
        }
        return Optional.of(new Solution(total, assignment, selected, method));
    }

    /**
     * Largest number of transporters a minimum-cost assignment of this set can use (the matching size).
     *
     * @param chosen transporter indexes in ascending order; the set must cover every lane
     */
    static int maximumUsage(QuoteMatrix matrix, int[] chosen) {
        int[][] candidates = cheapestCandidates(matrix, chosen);
        if (candidates == null) {
            throw new IllegalArgumentException("the chosen set does not cover every lane");
        }
        int matched = 0;
        for (int t : maximumMatching(candidates, matrix.transporterCount())) {
            if (t >= 0) {
                matched++;
            }
        }
        return matched;
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

    /**
     * For each lane, the chosen transporters offering its cheapest in-set quote, in ascending index order.
     * Returns null if some lane has no quote from the set.
     */
    private static int[][] cheapestCandidates(QuoteMatrix matrix, int[] chosen) {
        int[][] candidates = new int[matrix.laneCount()][];
        int[] buffer = new int[chosen.length];
        for (int l = 0; l < matrix.laneCount(); l++) {
            int first = cheapest(matrix, chosen, l);
            if (first < 0) {
                return null;
            }
            long best = matrix.quote[first][l];
            int count = 0;
            for (int t : chosen) {
                if (matrix.quote[t][l] == best) {
                    buffer[count++] = t;
                }
            }
            candidates[l] = Arrays.copyOf(buffer, count);
        }
        return candidates;
    }

    /**
     * Maximum bipartite matching between lanes and their candidate transporters (Kuhn's algorithm,
     * O(lanes * edges)). Returns, per lane, the matched transporter index or -1.
     */
    private static int[] maximumMatching(int[][] candidates, int transporterCount) {
        int[] laneOfTransporter = new int[transporterCount];
        Arrays.fill(laneOfTransporter, -1);
        boolean[] visited = new boolean[transporterCount];
        for (int l = 0; l < candidates.length; l++) {
            Arrays.fill(visited, false);
            augment(l, candidates, laneOfTransporter, visited);
        }
        int[] transporterOfLane = new int[candidates.length];
        Arrays.fill(transporterOfLane, -1);
        for (int t = 0; t < transporterCount; t++) {
            if (laneOfTransporter[t] >= 0) {
                transporterOfLane[laneOfTransporter[t]] = t;
            }
        }
        return transporterOfLane;
    }

    /** Tries to match lane {@code l}, re-routing earlier lanes along an augmenting path. Depth is at most the set size. */
    private static boolean augment(int l, int[][] candidates, int[] laneOfTransporter, boolean[] visited) {
        for (int t : candidates[l]) {
            if (visited[t]) {
                continue;
            }
            visited[t] = true;
            if (laneOfTransporter[t] < 0 || augment(laneOfTransporter[t], candidates, laneOfTransporter, visited)) {
                laneOfTransporter[t] = l;
                return true;
            }
        }
        return false;
    }
}
