package com.transporterassignment.solver;

/**
 * Limits for the exact branch-and-bound search.
 *
 * @param maxNodes  most branch-and-bound nodes to evaluate
 * @param maxTimeMs most wall-clock milliseconds to spend, checked every few thousand nodes
 */
public record SearchBudget(long maxNodes, long maxTimeMs) {

    /** No limits: the search always runs to completion. */
    public static final SearchBudget UNLIMITED = new SearchBudget(Long.MAX_VALUE, Long.MAX_VALUE);

    public SearchBudget {
        if (maxNodes < 1 || maxTimeMs < 1) {
            throw new IllegalArgumentException("search budgets must be at least 1");
        }
    }

    /** True once more than {@code maxTimeMs} has passed since {@code startNanos} (a {@link System#nanoTime()} value). */
    boolean timeUp(long startNanos) {
        return timeUp(startNanos, maxTimeMs);
    }

    /** Overflow-safe elapsed-time check shared by the solvers; budgets too large to represent never expire. */
    static boolean timeUp(long startNanos, long budgetMs) {
        if (budgetMs >= Long.MAX_VALUE / 1_000_000) {
            return false;
        }
        return System.nanoTime() - startNanos > budgetMs * 1_000_000;
    }
}
