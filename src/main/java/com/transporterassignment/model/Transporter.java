package com.transporterassignment.model;

import java.util.Map;

/**
 * A transporter and the quotes it gave, keyed by lane id.
 * A lane missing from {@code quotes} cannot be assigned to this transporter.
 */
public record Transporter(long id, String name, Map<Long, Long> quotes) {

    public Transporter {
        quotes = Map.copyOf(quotes);
    }
}
