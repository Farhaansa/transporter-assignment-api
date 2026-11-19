package com.transporterassignment.solver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Picks the solver strategy for a problem size: exact search while C(N, k) is within
 * {@code solver.max-combinations}, otherwise the greedy heuristic.
 */
@Component
public class SolverSelector {

    private final ExactSubsetSolver exact;
    private final GreedySolver greedy;
    private final long maxCombinations;

    public SolverSelector(ExactSubsetSolver exact, GreedySolver greedy,
                          @Value("${solver.max-combinations:200000}") long maxCombinations) {
        if (maxCombinations < 1) {
            throw new IllegalArgumentException("solver.max-combinations must be at least 1");
        }
        this.exact = exact;
        this.greedy = greedy;
        this.maxCombinations = maxCombinations;
    }

    /**
     * @param transporters N, the number of transporters
     * @param k            the effective limit, already clamped to N
     */
    public SolverChoice select(int transporters, int k) {
        long searchSpace = Combinatorics.combinations(transporters, k);
        boolean exhaustive = searchSpace <= maxCombinations;
        return new SolverChoice(exhaustive ? exact : greedy, searchSpace, maxCombinations, exhaustive);
    }

    /**
     * @param solver          the strategy to run
     * @param searchSpace     C(N, k), saturated at {@link Long#MAX_VALUE}
     * @param maxCombinations the configured limit
     * @param exhaustive      true when the exact solver was chosen
     */
    public record SolverChoice(AssignmentSolver solver, long searchSpace, long maxCombinations, boolean exhaustive) {
    }
}
