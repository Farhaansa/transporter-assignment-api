package com.transporterassignment.model;

import java.util.Comparator;
import java.util.List;

/**
 * One complete input: the lanes and the transporters that quoted them.
 * Both lists are immutable and sorted by id, so every solver sees the same deterministic order.
 */
public record Dataset(List<Lane> lanes, List<Transporter> transporters) {

    public Dataset {
        lanes = lanes.stream().sorted(Comparator.comparingLong(Lane::id)).toList();
        transporters = transporters.stream().sorted(Comparator.comparingLong(Transporter::id)).toList();
    }
}
