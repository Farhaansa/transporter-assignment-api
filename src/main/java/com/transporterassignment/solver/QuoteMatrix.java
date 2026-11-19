package com.transporterassignment.solver;

import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Transporter;

/**
 * Dense, index-based view of a {@link Dataset} for the solvers' inner loops.
 * Transporter index order is ascending id (the dataset is sorted), so "lowest index" means "lowest id".
 */
final class QuoteMatrix {

    static final long NO_QUOTE = -1;

    final long[] laneIds;
    final long[] transporterIds;
    /** {@code quote[t][l]}: price of transporter {@code t} for lane {@code l}, or {@link #NO_QUOTE}. */
    final long[][] quote;

    private QuoteMatrix(long[] laneIds, long[] transporterIds, long[][] quote) {
        this.laneIds = laneIds;
        this.transporterIds = transporterIds;
        this.quote = quote;
    }

    static QuoteMatrix of(Dataset data) {
        long[] laneIds = data.lanes().stream().mapToLong(lane -> lane.id()).toArray();
        long[] transporterIds = data.transporters().stream().mapToLong(Transporter::id).toArray();
        long[][] quote = new long[transporterIds.length][laneIds.length];
        for (int t = 0; t < transporterIds.length; t++) {
            Transporter transporter = data.transporters().get(t);
            for (int l = 0; l < laneIds.length; l++) {
                quote[t][l] = transporter.quotes().getOrDefault(laneIds[l], NO_QUOTE);
            }
        }
        return new QuoteMatrix(laneIds, transporterIds, quote);
    }

    int laneCount() {
        return laneIds.length;
    }

    /** Sentinel for "no quote" in cost arithmetic: larger than any real cost, so {@code Math.min} ignores it. */
    static final long INF = Long.MAX_VALUE;

    /** Quote of transporter {@code t} for lane {@code l}, or {@link #INF} when it did not quote. */
    long quoteOrInf(int t, int l) {
        long q = quote[t][l];
        return q == NO_QUOTE ? INF : q;
    }

    /** Total cost of a set (cheapest quote per lane), or {@link #INF} if some lane has no quote from the set. */
    long costOf(int[] chosen) {
        long total = 0;
        for (int l = 0; l < laneIds.length; l++) {
            int winner = Assigner.cheapest(this, chosen, l);
            if (winner < 0) {
                return INF;
            }
            total += quote[winner][l];
        }
        return total;
    }

    /**
     * Sum over lanes of each lane's cheapest quote from any transporter, ignoring k. No selection can cost less, so
     * this is a proven lower bound on the optimum. {@link #INF} if some lane has no quote at all.
     */
    long lowerBound() {
        long total = 0;
        for (int l = 0; l < laneIds.length; l++) {
            long cheapest = INF;
            for (int t = 0; t < transporterIds.length; t++) {
                cheapest = Math.min(cheapest, quoteOrInf(t, l));
            }
            if (cheapest == INF) {
                return INF;
            }
            total += cheapest;
        }
        return total;
    }

    int transporterCount() {
        return transporterIds.length;
    }

    static void requirePositive(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1, was " + k);
        }
    }
}
