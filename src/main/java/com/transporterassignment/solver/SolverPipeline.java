package com.transporterassignment.solver;

import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Runs the solver strategies in order: the exact branch-and-bound search within its budget, then, only if that
 * budget runs out, the coverage-first local search seeded with the exact search's best feasible set.
 */
@Component
public class SolverPipeline {

    private final ExactSubsetSolver exact;
    private final LocalSearchSolver localSearch;
    private final SearchBudget budget;

    public SolverPipeline(ExactSubsetSolver exact, LocalSearchSolver localSearch,
                          @Value("${solver.max-search-nodes:5000000}") long maxSearchNodes,
                          @Value("${solver.max-time-ms:10000}") long maxTimeMs) {
        this.exact = exact;
        this.localSearch = localSearch;
        this.budget = new SearchBudget(maxSearchNodes, maxTimeMs);
    }

    public Outcome solve(Dataset data, int k) {
        QuoteMatrix matrix = QuoteMatrix.of(data);
        long lowerBound = matrix.lowerBound();
        ExactSearch search = exact.search(matrix, k, budget);
        switch (search.status()) {
            case OPTIMAL:
                return new Outcome(Kind.EXACT, Assigner.assign(matrix, search.bestSet(), ExactSubsetSolver.METHOD),
                        search, lowerBound);
            case INFEASIBLE:
                return new Outcome(Kind.PROVEN_INFEASIBLE, Optional.empty(), search, lowerBound);
            default:
                List<int[]> seeds = search.bestSet() == null ? List.of() : List.of(search.bestSet());
                Optional<Solution> found = localSearch.finish(matrix, k, localSearch.search(matrix, k, seeds));
                return new Outcome(found.isPresent() ? Kind.HEURISTIC : Kind.INCONCLUSIVE, found, search, lowerBound);
        }
    }

    public SearchBudget budget() {
        return budget;
    }

    public long heuristicTimeMs() {
        return localSearch.timeBudgetMs();
    }

    /** What the pipeline could establish. */
    public enum Kind {
        /** The exact search completed: the cost is the proven minimum. */
        EXACT,
        /** The exact search completed and no set of at most k transporters covers every lane. */
        PROVEN_INFEASIBLE,
        /** The exact search ran out of budget; local search returned its best covering answer. Nothing proven. */
        HEURISTIC,
        /** The exact search ran out of budget and local search found no cover. Not a proof of infeasibility. */
        INCONCLUSIVE
    }

    /**
     * @param kind       what was established
     * @param solution   the assignment for {@link Kind#EXACT} and {@link Kind#HEURISTIC}; empty otherwise
     * @param search     the exact search's result, including why it stopped
     * @param lowerBound sum of each lane's cheapest quote, ignoring k: a proven lower bound on the optimum
     */
    public record Outcome(Kind kind, Optional<Solution> solution, ExactSearch search, long lowerBound) {

        /** Cost of the best feasible set the exact search knew when it stopped, or empty if it knew none. */
        public Optional<Long> exactIncumbentCost() {
            return search.bestSet() == null ? Optional.empty() : Optional.of(search.bestCost());
        }

        /** True when the exact search stopped at its node budget rather than its time budget. */
        public boolean stoppedAtNodeBudget() {
            return search.status() == ExactSearch.Status.NODE_BUDGET_EXHAUSTED;
        }
    }
}
