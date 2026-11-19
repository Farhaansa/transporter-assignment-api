package com.transporterassignment.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A complete assignment.
 *
 * @param totalCost          sum of the assigned quotes
 * @param laneToTransporter  lane id to transporter id, ordered by lane id
 * @param selected           ids of the transporters that received at least one lane, ascending
 * @param method             how the solution was produced, for example {@code EXACT} or {@code HEURISTIC}
 */
public record Solution(long totalCost, Map<Long, Long> laneToTransporter, List<Long> selected, String method) {

    public Solution {
        laneToTransporter = Collections.unmodifiableMap(new TreeMap<>(laneToTransporter));
        selected = List.copyOf(selected);
    }
}
