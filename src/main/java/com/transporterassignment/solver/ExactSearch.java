package com.transporterassignment.solver;

/**
 * Result of one branch-and-bound run.
 *
 * @param status      how the search ended
 * @param bestSet     transporter indexes (ascending) of the best covering set: the proven optimum when
 *                    {@code status} is {@link Status#OPTIMAL}, the best feasible incumbent known when a budget ran out,
 *                    and null when none is known
 * @param bestCost    cost of {@code bestSet}, or {@link QuoteMatrix#INF} when there is none
 * @param nodes       branch-and-bound nodes evaluated
 */
record ExactSearch(Status status, int[] bestSet, long bestCost, long nodes) {

    enum Status {
        /** Completed: {@code bestSet} is the proven optimum under the full tie-break rules. */
        OPTIMAL,
        /** Completed: no set of {@code min(k, N)} transporters covers every lane. A proof, not a guess. */
        INFEASIBLE,
        /** Stopped at {@code solver.max-search-nodes}; nothing is proven. */
        NODE_BUDGET_EXHAUSTED,
        /** Stopped at {@code solver.max-time-ms}; nothing is proven. */
        TIME_BUDGET_EXHAUSTED;

        boolean completed() {
            return this == OPTIMAL || this == INFEASIBLE;
        }
    }
}
