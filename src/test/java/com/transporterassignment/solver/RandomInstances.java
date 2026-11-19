package com.transporterassignment.solver;

import com.transporterassignment.TestData;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Transporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Seeded random datasets for the solver tests. Every generator is deterministic for a given {@link Random}. */
public final class RandomInstances {

    private RandomInstances() {
    }

    /**
     * Each transporter quotes each lane with probability {@code density}, at a price from 1 to {@code maxQuote}.
     * A small {@code maxQuote} gives many equal quotes; a low density leaves some lanes unquoted.
     */
    public static Dataset random(Random random, int lanes, int transporters, double density, int maxQuote) {
        List<Transporter> list = new ArrayList<>();
        for (int t = 1; t <= transporters; t++) {
            Map<Long, Long> quotes = new HashMap<>();
            for (long lane = 1; lane <= lanes; lane++) {
                if (random.nextDouble() < density) {
                    quotes.put(lane, 1L + random.nextInt(maxQuote));
                }
            }
            list.add(TestData.transporter(t, quotes));
        }
        return TestData.of(lanes, list.toArray(Transporter[]::new));
    }

    /**
     * A sparse instance with a planted cover: {@code k} chosen transporters split the lanes between them (each gets
     * at least one), so a k-cover is known to exist. Other quotes appear with probability {@code density}.
     */
    public static Dataset plantedCover(Random random, int lanes, int transporters, int k, double density, int maxQuote) {
        List<Map<Long, Long>> quotes = new ArrayList<>();
        for (int t = 0; t < transporters; t++) {
            Map<Long, Long> row = new HashMap<>();
            for (long lane = 1; lane <= lanes; lane++) {
                if (random.nextDouble() < density) {
                    row.put(lane, 1L + random.nextInt(maxQuote));
                }
            }
            quotes.add(row);
        }
        // Pick k distinct planted transporters and deal the lanes out so each one serves at least one lane.
        List<Integer> order = new ArrayList<>();
        for (int t = 0; t < transporters; t++) {
            order.add(t);
        }
        java.util.Collections.shuffle(order, random);
        List<Integer> planted = order.subList(0, k);
        for (int lane = 1; lane <= lanes; lane++) {
            int owner = lane <= k ? planted.get(lane - 1) : planted.get(random.nextInt(k));
            quotes.get(owner).putIfAbsent((long) lane, 1L + random.nextInt(maxQuote));
        }
        List<Transporter> list = new ArrayList<>();
        for (int t = 0; t < transporters; t++) {
            list.add(TestData.transporter(t + 1, quotes.get(t)));
        }
        return TestData.of(lanes, list.toArray(Transporter[]::new));
    }
}
