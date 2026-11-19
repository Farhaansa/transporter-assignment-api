package com.transporterassignment;

import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Lane;
import com.transporterassignment.model.Transporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The two datasets from the assignment, plus small builders for hand-made cases. */
public final class TestData {

    private TestData() {
    }

    /** Quotes for lanes 1..5, one row per transporter T1..T7. */
    public static final long[][] CASE_1_QUOTES = {
            {20835, 10512, 22105, 42481, 19862},
            {48844, 31326, 18640, 45828, 18297},
            {39020, 20648, 31438, 36447, 12789},
            {14400, 44514, 14316, 10678, 13032},
            {11601, 19760, 40870, 20635, 26421},
            {35095, 12494, 17808, 36210, 39444},
            {26070, 41098, 20932, 16897, 27938},
    };

    public static Dataset case1() {
        return case1Restricted(Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
    }

    /** Test Case 1 with only the given transporter ids. */
    public static Dataset case1Restricted(Set<Long> transporterIds) {
        List<Lane> lanes = List.of(
                new Lane(1, "Mumbai", "Delhi"),
                new Lane(2, "Delhi", "Bangalore"),
                new Lane(3, "Chennai", "Kolkata"),
                new Lane(4, "Pune", "Hyderabad"),
                new Lane(5, "Ahmedabad", "Jaipur"));
        List<Transporter> transporters = new ArrayList<>();
        for (int t = 0; t < CASE_1_QUOTES.length; t++) {
            long id = t + 1;
            if (!transporterIds.contains(id)) {
                continue;
            }
            Map<Long, Long> quotes = new LinkedHashMap<>();
            for (int l = 0; l < CASE_1_QUOTES[t].length; l++) {
                quotes.put((long) l + 1, CASE_1_QUOTES[t][l]);
            }
            transporters.add(new Transporter(id, "Transporter T" + id, quotes));
        }
        return new Dataset(lanes, transporters);
    }

    public static Dataset case2() {
        List<Lane> lanes = List.of(
                new Lane(1, "Chandigarh", "Shimla"),
                new Lane(2, "Agra", "Kanpur"),
                new Lane(3, "Varanasi", "Gorakhpur"),
                new Lane(4, "Amritsar", "Ludhiana"),
                new Lane(5, "Coimbatore", "Madurai"));
        List<Transporter> transporters = List.of(
                new Transporter(1, "X", Map.of(1L, 1000L, 2L, 1500L)),
                new Transporter(2, "Y", Map.of(3L, 2000L, 4L, 1800L)),
                new Transporter(3, "Z", Map.of(5L, 1200L)),
                new Transporter(4, "W", Map.of(1L, 1100L, 4L, 1750L)));
        return new Dataset(lanes, transporters);
    }

    /** Builds a dataset with lanes 1..laneCount and the given transporters. */
    public static Dataset of(int laneCount, Transporter... transporters) {
        List<Lane> lanes = new ArrayList<>();
        for (long id = 1; id <= laneCount; id++) {
            lanes.add(new Lane(id, "Origin " + id, "Destination " + id));
        }
        return new Dataset(lanes, List.of(transporters));
    }

    public static Transporter transporter(long id, Map<Long, Long> quotes) {
        return new Transporter(id, "Transporter " + id, quotes);
    }
}
